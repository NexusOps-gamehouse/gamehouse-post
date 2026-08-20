package gg.duo.post.event.publisher;

import gg.duo.common.event.ApplicationApprovedEvent;
import gg.duo.common.event.ApplicationConfirmedEvent;
import gg.duo.common.event.DomainEventPublisher;
import gg.duo.common.event.NotificationRequestedEvent;
import gg.duo.common.event.PostCreatedEvent;
import gg.duo.common.event.PostDeletedEvent;
import gg.duo.common.event.PostUpdatedEvent;
import gg.duo.post.domain.application.Application;
import gg.duo.post.domain.post.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * post 가 바깥에 알리는 사실들.
 *
 * 발행하는 쪽은 누가 듣는지 모른다. 지금은 chat 과 user 가 듣지만,
 * match 가 생겨도 post 코드는 한 줄도 바뀌지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PostEventPublisher {

    private final DomainEventPublisher publisher;

    /**
     * 글이 만들어졌다 → chat 이 파티 채팅방을 미리 연다.
     *
     * 승인 시점이 아니라 생성 시점에 방을 여는 이유: approve() 응답이 채팅방
     * 번호를 즉시 돌려줘야 하는데(프론트가 그 번호로 이동한다), 방 생성이
     * 비동기 이벤트면 응답 시점에 아직 방이 없을 수 있다. 글과 함께 열어두면
     * 그 경합 자체가 사라진다. 지원자가 없는 방이 하나 남는 비용이 전부다.
     */
    public void postCreated(Post post) {
        publisher.publish(new PostCreatedEvent(
                post.getId(), post.getAuthorId(), post.getTitle(), post.getStatus().name()));
    }

    /** 제목·상태가 바뀌었다 → chat 이 방의 스냅샷을 맞춘다. */
    public void postUpdated(Post post) {
        publisher.publish(new PostUpdatedEvent(
                post.getId(), post.getTitle(), post.getStatus().name()));
    }

    /** 글이 지워졌다 → chat 이 딸린 방·메시지를 정리한다. */
    public void postDeleted(Long postId) {
        publisher.publish(new PostDeletedEvent(postId));
    }

    /** 승인 → chat 이 신청자를 방 멤버로 넣는다. */
    public void applicationApproved(Application app) {
        Post post = app.getPost();
        publisher.publish(new ApplicationApprovedEvent(
                app.getId(), post.getId(), post.getTitle(),
                post.getAuthorId(), app.getApplicantId()));
    }

    /** 확정 → chat 이 멤버의 confirmed 플래그를 올린다. */
    public void applicationConfirmed(Application app) {
        Post post = app.getPost();
        publisher.publish(new ApplicationConfirmedEvent(
                app.getId(), post.getId(), post.getTitle(),
                post.getAuthorId(), app.getApplicantId()));
    }

    /**
     * 알림 요청.
     *
     * notifications 테이블은 user 가 소유하므로 post 는 직접 INSERT 할 수 없다.
     * 예전 코드의 notificationService.notify(...) 가 전부 이 자리로 온다.
     */
    public void notify(Long userId, String message, String link) {
        publisher.publish(new NotificationRequestedEvent(userId, message, link));
    }
}
