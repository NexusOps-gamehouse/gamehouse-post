package gg.duo.post.service;

import gg.duo.common.dto.UserDto;
import gg.duo.post.client.UserClient;
import gg.duo.post.domain.application.Application;
import gg.duo.post.domain.application.ApplicationRepository;
import gg.duo.post.domain.post.Post;
import gg.duo.post.domain.post.PostRepository;
import gg.duo.post.dto.ApplicationDto;
import gg.duo.post.event.publisher.PostEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private static final Duration PENDING_TTL = Duration.ofHours(1);

    private final ApplicationRepository applicationRepository;
    private final PostRepository postRepository;

    /**
     * 사라진 의존 세 개:
     *   UserRepository            → UserClient (users 는 user 소유)
     *   ChatRoom/MemberRepository → 이벤트 발행 (chat 이 방을 만든다)
     *   NotificationService       → 이벤트 발행 (notifications 는 user 소유)
     */
    private final UserClient userClient;
    private final PostEventPublisher events;

    @Transactional
    public void apply(Long postId, Long meId) {
        Post post = postRepository.findById(postId).orElseThrow();
        if (post.getStatus() == Post.Status.CLOSED)
            throw new IllegalStateException("모집이 완료된 글입니다.");
        if (post.getAuthorId().equals(meId))
            throw new IllegalStateException("본인 글에는 참가 신청할 수 없습니다.");
        if (applicationRepository.existsByPostIdAndApplicantId(postId, meId))
            throw new IllegalStateException("이미 참가 신청한 글입니다.");

        Application app = new Application();
        app.setPost(post);
        app.setApplicantId(meId);
        applicationRepository.save(app);

        events.notify(post.getAuthorId(),
                nicknameOf(meId) + "님이 '" + post.getTitle() + "' 글에 참가 신청했습니다.",
                "/post/" + post.getId());
    }

    /** 신청자: 대기 중인 참가 신청 취소 */
    @Transactional
    public void cancel(Long postId, Long meId) {
        Application app = applicationRepository.findByPostIdAndApplicantId(postId, meId)
                .orElseThrow(() -> new IllegalArgumentException("취소할 참가 신청이 없습니다."));
        if (app.getStatus() != Application.Status.PENDING)
            throw new IllegalStateException("대기 중인 신청만 취소할 수 있습니다.");

        applicationRepository.delete(app);
    }

    /** 방장용: 신청자 목록 (만료된 대기 신청 제외) */
    @Transactional(readOnly = true)
    public List<ApplicationDto> listForPost(Long postId, Long meId) {
        Post post = postRepository.findById(postId).orElseThrow();
        if (!post.getAuthorId().equals(meId))
            throw new SecurityException("본인 글의 신청자만 볼 수 있습니다.");
        return toDtos(applicationRepository.findByPostIdOrderByCreatedAtDesc(postId)
                .stream()
                .filter(a -> !isExpired(a))
                .toList());
    }

    /** 신청자용: 내 신청 현황 (거절/만료 제외) */
    @Transactional(readOnly = true)
    public List<ApplicationDto> myApplications(Long meId) {
        return toDtos(applicationRepository.findByApplicantIdOrderByCreatedAtDesc(meId)
                .stream()
                .filter(a -> a.getStatus() != Application.Status.REJECTED)
                .filter(a -> !isExpired(a))
                .toList());
    }

    /**
     * 승인 → 신청자를 파티 채팅방 멤버로 넣는다.
     *
     * 예전에는 여기서 ChatRoom 과 ChatRoomMember 를 직접 만들었다. 그 테이블은
     * chat 소유가 되었으므로 ApplicationApprovedEvent 만 발행한다.
     *
     * 응답의 chatRoomId 는 posts.chat_room_id 복제본에서 읽는다. 방은 글이
     * 만들어질 때 미리 열리므로(PostCreatedEvent) 이 시점에는 이미 값이 있다.
     */
    @Transactional
    public Map<String, Long> approve(Long applicationId, Long meId) {
        Application app = applicationRepository.findById(applicationId).orElseThrow();
        Post post = app.getPost();
        if (!post.getAuthorId().equals(meId))
            throw new SecurityException("본인 글의 신청만 처리할 수 있습니다.");
        if (app.getStatus() != Application.Status.PENDING)
            throw new IllegalStateException("이미 처리된 신청입니다.");

        app.setStatus(Application.Status.APPROVED);
        events.applicationApproved(app);

        Long roomId = ensureChatRoomId(post);

        events.notify(app.getApplicantId(),
                "'" + post.getTitle() + "' 참가 신청이 승인되었습니다. 파티 채팅방에 입장하세요!",
                roomId == null ? null : "/chat/" + roomId);

        // roomId 가 아직 null 일 수 있다 — 아래 ensureChatRoomId 주석 참고.
        // Map.of 는 null 값을 허용하지 않으므로 HashMap 을 쓴다.
        Map<String, Long> body = new HashMap<>();
        body.put("chatRoomId", roomId);
        return body;
    }

    /**
     * 이 글의 채팅방 번호.
     *
     * 정상 흐름에서는 글 생성 시 이미 채워져 있다. 비어 있는 경우는 두 가지다.
     *   1) 이 변경 이전에 만들어진 글 (chat_room_id 컬럼이 없던 시절)
     *   2) chat 이 잠깐 죽어 PostCreatedEvent 를 아직 처리하지 못함
     * 둘 다 "방을 다시 열어달라"고 알리면 해결된다. chat 쪽 소비자는 멱등이라
     * 이미 방이 있으면 아무 일도 하지 않는다.
     *
     * ⚠️ 3단계에서 이벤트가 RabbitMQ 로 나가면 이 호출은 비동기가 된다.
     *    그때는 여기서 null 이 돌아올 수 있고, 프론트가 "채팅방 준비 중"을
     *    처리해야 한다. 지금은 프로세스 내 이벤트라 즉시 채워진다.
     */
    private Long ensureChatRoomId(Post post) {
        if (post.getChatRoomId() != null) return post.getChatRoomId();
        events.postCreated(post);
        return post.getChatRoomId();
    }

    /** 방장: 파티원 모집 확정 */
    @Transactional
    public void confirm(Long applicationId, Long meId) {
        Application app = applicationRepository.findById(applicationId).orElseThrow();
        Post post = app.getPost();
        if (!post.getAuthorId().equals(meId))
            throw new SecurityException("본인 글의 신청만 처리할 수 있습니다.");
        if (app.getStatus() != Application.Status.APPROVED)
            throw new IllegalStateException("승인된(채팅 참여 중인) 신청만 확정할 수 있습니다.");

        // 정원은 확정 단계에서만 강제한다. 채팅방 입장(approve)은 인원 제한이 없다.
        //
        // targetMembers 는 방장을 포함한 수이므로, 방장이 확정해 줄 수 있는 인원은
        // 그보다 하나 적다. (예: 4명짜리 방 → 방장 제외 3명까지 확정)
        int capacity = post.getTargetMembers() - 1;
        long confirmed = applicationRepository.countByPostIdAndStatus(
                post.getId(), Application.Status.CONFIRMED);
        if (confirmed >= capacity)
            throw new IllegalStateException(
                    "확정 인원이 모두 찼습니다. (최대 " + capacity + "명)");

        app.setStatus(Application.Status.CONFIRMED);

        // chat 이 받아 ChatRoomMember.confirmed 를 올린다.
        // 예전에는 여기서 그 플래그를 직접 세팅했다.
        events.applicationConfirmed(app);

        // 확정 인원이 다 차도 모집글을 자동으로 닫지 않는다.
        // 마감 여부는 방장이 "모집 완료"(PostService.close)로 직접 정한다.
        // 자리가 찬 것은 목록의 n/m 표시로 드러나면 충분하다.

        events.notify(app.getApplicantId(),
                "'" + post.getTitle() + "' 파티에 확정되었습니다!",
                post.getChatRoomId() == null ? null : "/chat/" + post.getChatRoomId());
    }

    @Transactional
    public void reject(Long applicationId, Long meId) {
        Application app = applicationRepository.findById(applicationId).orElseThrow();
        Post post = app.getPost();
        if (!post.getAuthorId().equals(meId))
            throw new SecurityException("본인 글의 신청만 처리할 수 있습니다.");
        if (app.getStatus() != Application.Status.PENDING)
            throw new IllegalStateException("이미 처리된 신청입니다.");

        app.setStatus(Application.Status.REJECTED);
        events.notify(app.getApplicantId(),
                "'" + post.getTitle() + "' 참가 신청이 거절되었습니다.", null);
    }

    private boolean isExpired(Application a) {
        return a.getStatus() == Application.Status.PENDING
                && a.getCreatedAt().isBefore(Instant.now().minus(PENDING_TTL));
    }

    private String nicknameOf(Long userId) {
        UserDto user = userClient.findAllByIds(List.of(userId)).get(userId);
        return user == null ? "알 수 없는 사용자" : user.nickname();
    }

    /**
     * 목록 변환.
     *
     * 신청마다 연관을 하나씩 끌어오지 않는다. 필요한 id 를 먼저 모아 한 번에
     * 조회하고 Map 에서 꺼내 쓴다.
     *
     * 서비스가 나뉜 뒤로 이 원칙이 더 중요해졌다. 예전에는 신청 20건에 쿼리가
     * 20번 더 나가는 문제였지만, 지금은 user 서비스로 HTTP 왕복이 20번이다.
     *
     * 채팅방 번호는 chat 에 묻지 않는다 — posts.chat_room_id 복제본에서 읽는다.
     */
    private List<ApplicationDto> toDtos(List<Application> applications) {
        if (applications.isEmpty()) return List.of();

        Set<Long> postIds = applications.stream()
                .map(a -> a.getPost().getId()).collect(Collectors.toSet());

        Map<Long, Post> postById = postRepository.findAllById(postIds).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));

        Map<Long, UserDto> applicantById = userClient.findAllByIds(
                applications.stream().map(Application::getApplicantId).distinct().toList());

        return applications.stream()
                .map(a -> {
                    Post post = postById.get(a.getPost().getId());
                    return new ApplicationDto(
                            a.getId(), a.getStatus().name(), a.getCreatedAt(),
                            a.getPost().getId(),
                            post == null ? null : post.getTitle(),
                            applicantById.get(a.getApplicantId()),
                            hasChatRoom(a) && post != null ? post.getChatRoomId() : null);
                })
                .toList();
    }

    /** 승인된 신청만 채팅방을 갖는다 */
    private static boolean hasChatRoom(Application a) {
        return a.getStatus() == Application.Status.APPROVED
                || a.getStatus() == Application.Status.CONFIRMED;
    }
}
