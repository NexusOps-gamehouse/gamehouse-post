package gg.duo.post.reconcile;

import gg.duo.post.domain.post.Post;
import gg.duo.post.domain.post.PostRepository;
import gg.duo.post.event.publisher.PostEventPublisher;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * chat_room_id 가 비어 있는 글을 찾아 PostCreatedEvent 를 다시 보낸다.
 *
 * [왜 필요한가]
 * 방 번호는 ChatRoomCreatedEvent 로만 채워진다. 그 이벤트가 한 번 유실되면
 * 되살릴 길이 없었다. 운영 실측이다.
 *
 *   조치 전  22% 누락 — 커밋 전에 발행해서 post 의 consumer 가 아직 없는 글을
 *            찾고 조용히 넘어갔다
 *   조치 후  6.2% 누락 — 커밋 뒤 발행이라 경합은 사라졌지만, 브로커가 죽으면
 *            발행이 실패하고 글만 남는다(dual write)
 *
 * 두 경우 모두 재발행으로 복구된다. chat 의 ensureRoom 이 멱등하고
 * chat_rooms.post_id 에 유니크 인덱스가 있어 방이 중복 생기지 않는다.
 *
 * [왜 아웃박스가 아닌가]
 * 아웃박스가 정석이다. 그런데 발행 지점이 common 라이브러리라 고치면 GitHub
 * Packages 재배포와 7개 서비스 버전 올리기가 따라온다. 보정 배치는 post 한 곳만
 * 건드리고, "무엇이 정상인가" 를 쿼리로 쓸 수 있어서 성립한다 — 글에
 * chat_room_id 가 있어야 한다는 조건이 명확하기 때문이다.
 *
 * 대신 아웃박스와 달리 **즉시 일관이 아니다.** 최대 한 주기만큼 늦게 메워진다.
 *
 * [파드가 여러 개일 때]
 * 운영 post 는 2~4 파드이고 전부 이 스케줄러를 돈다. 같은 행을 집어 중복
 * 발행할 수 있는데, 그래도 안전하다 — 받는 쪽이 둘 다 멱등이다(ensureRoom 은
 * post_id 로 찾고, setChatRoomId 는 같은 값을 다시 쓴다).
 *
 * 비용은 메시지 몇 건이고, 얻는 것은 리더 선출이나 분산 락 없이 도는 단순함이다.
 * 먼저 도는 파드가 채우면 다음 파드는 대상이 줄어 자연히 수렴한다.
 * 중복이 부담이 될 만큼 밀린다면 그때 SELECT ... FOR UPDATE SKIP LOCKED 로 간다.
 *
 * [한계]
 * 글 자체가 롤백된 경우는 대상이 아니다(글이 없으므로). 그건 유실이 아니라
 * 정상적인 실패다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gamehouse.reconcile.chat-room.enabled",
                       havingValue = "true", matchIfMissing = true)
public class ChatRoomReconciler {

    private final PostRepository postRepository;
    private final PostEventPublisher events;
    private final MeterRegistry registry;

    /** 갓 만들어진 글은 아직 왕복 중일 수 있다. 이만큼 지난 것만 본다. */
    @Value("${gamehouse.reconcile.chat-room.min-age-seconds:300}")
    private long minAgeSeconds;

    /** 한 주기에 재발행할 최대 건수. 브로커가 오래 죽어 수천 건이 밀렸을 때의 상한이다. */
    @Value("${gamehouse.reconcile.chat-room.batch-size:200}")
    private int batchSize;

    @PostConstruct
    void registerMetrics() {
        // 누락 건수를 지표로 낸다. 이 문제가 지금까지 조용했던 이유가 관측 수단이
        // 없어서였다 — 오류 로그도, 큐 적체도, 실패 응답도 없었다. 운영에서 22%가
        // 사라지고 있었는데 아무도 몰랐고, 세 숫자를 손으로 대조해서야 찾았다.
        //
        // 이 선 하나가 있으면 다음에 또 새도 사람이 세어보지 않아도 알고,
        // "> 0 for 10m" 같은 알람을 걸 수 있다.
        Gauge.builder("gamehouse_posts_missing_chat_room",
                      postRepository, PostRepository::countByChatRoomIdIsNull)
                .description("채팅방 번호가 연결되지 않은 모집글 수")
                .register(registry);
    }

    /**
     * ⚠️ 트랜잭션을 열지 않는다.
     *
     * 조회는 Spring Data 리포지토리 메서드가 자기 트랜잭션으로 처리하고 바로 닫는다.
     * 이 메서드에 @Transactional 을 걸면 발행이 느릴 때 DB 커넥션을 그만큼 쥐게 되는데,
     * 그것이 애초에 이번 실험에서 찾은 문제다 — 브로커 39초 공백에 커넥션 풀이 13초 만에
     * 고갈되고, 브로커와 무관한 조회까지 막혔다. 보정 배치가 같은 실수를 반복하면 안 된다.
     */
    @Scheduled(
            fixedDelayString = "${gamehouse.reconcile.chat-room.interval-ms:120000}",
            initialDelayString = "${gamehouse.reconcile.chat-room.initial-delay-ms:60000}")
    public void reconcile() {
        Instant cutoff = Instant.now().minus(minAgeSeconds, ChronoUnit.SECONDS);

        List<Post> stale = postRepository.findByChatRoomIdIsNullAndCreatedAtBefore(
                cutoff, PageRequest.of(0, batchSize));
        if (stale.isEmpty()) return;

        log.info("채팅방 번호가 비어 있는 글 {}건을 재발행한다. cutoff={}", stale.size(), cutoff);

        int sent = 0;
        for (Post post : stale) {
            try {
                events.postCreated(post);
                sent++;
            } catch (Exception e) {
                // 브로커가 아직 죽어 있으면 여기서 걸린다. 다음 주기에 다시 시도하므로
                // 남은 건을 포기하고 빠져나온다 — 수백 건을 붙잡고 실패를 반복할 이유가 없고,
                // 그 사이 커넥션과 스레드를 계속 묶어 두는 것이 더 나쁘다.
                log.warn("재발행 실패. 다음 주기에 다시 시도한다. postId={} 이번 회 성공={}",
                        post.getId(), sent, e);
                break;
            }
        }
        log.info("재발행 완료 {}/{}", sent, stale.size());
    }
}
