package gg.duo.post.service;

import gg.duo.common.dto.UserDto;
import gg.duo.post.client.UserClient;
import gg.duo.post.domain.application.Application;
import gg.duo.post.domain.application.ApplicationRepository;
import gg.duo.post.domain.post.Post;
import gg.duo.post.domain.post.PostRepository;
import gg.duo.post.dto.PostDto;
import gg.duo.post.event.publisher.PostEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final ApplicationRepository applicationRepository;

    /**
     * chat 리포지토리 세 개(ChatRoom / ChatRoomMember / ChatMessage)가 사라졌다.
     *
     * 그것들이 하던 일은 두 갈래로 나뉘었다.
     *   - 읽기("이 글의 방 번호") → posts.chat_room_id 복제본
     *   - 쓰기("방 만들기 / 정리") → 이벤트 발행 (chat 이 처리)
     * 이 두 의존이 post ↔ chat 순환을 만들고 있었다. Gradle 모듈로 나누는
     * 순간 순환 참조로 빌드가 아예 안 된다 — 그래서 여기가 첫 절단 지점이었다.
     */
    private final UserClient userClient;
    private final PostEventPublisher events;

    /** 목록 한 페이지의 최대 크기 — 클라이언트가 size 를 크게 보내도 여기서 막는다. */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 목록 + 검색(제목/닉네임) + 필터(게임/모드/모집상태) + 페이징.
     *
     * 검색·필터 조건은 전부 쿼리에서 처리한다. 자바에서 거르면 DB 가 자른 뒤에
     * 거르는 순서가 되어 페이지마다 결과 개수가 들쭉날쭉해진다.
     */
    @Transactional(readOnly = true)
    public PostDto.ListResponse list(Long meId, String searchType, String keyword,
                                     String game, String gameMode, String status,
                                     int page, int size) {
        String kw = blankToNull(keyword);
        String g = blankToNull(game);
        String gm = blankToNull(gameMode);

        Post.Status st = null;
        if (blankToNull(status) != null) {
            try {
                st = Post.Status.valueOf(status.trim());
            } catch (IllegalArgumentException e) {
                // 알 수 없는 상태값 — 이전과 같이 결과 없음으로 처리한다.
                return new PostDto.ListResponse(List.of(), page, size, 0, false);
            }
        }

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

        Page<Post> found;
        if ("nickname".equals(searchType) && kw != null) {
            // 닉네임 검색은 user 서비스에 id 를 먼저 물어본다. (조인이 불가능해졌다)
            List<Long> authorIds = userClient.findIdsByNicknameContaining(kw);
            if (authorIds.isEmpty()) {
                // in () 에 빈 목록을 넘기면 안 된다. 결과 없음으로 바로 끊는다.
                return new PostDto.ListResponse(List.of(), pageable.getPageNumber(),
                        pageable.getPageSize(), 0, false);
            }
            found = postRepository.searchByAuthorIds(g, gm, st, authorIds, pageable);
        } else {
            found = postRepository.searchByTitle(g, gm, st, likePattern(kw), pageable);
        }

        List<PostDto.Summary> items = toSummaries(found.getContent(), meId);

        return new PostDto.ListResponse(items, found.getNumber(), found.getSize(),
                found.getTotalElements(), found.hasNext());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /**
     * 검색어를 like 패턴으로 바꾼다.
     *
     * 검색어가 없어도 null 이 아니라 '%' 를 넘긴다. like 에 null 을 바인딩하면
     * 드라이버가 그 파라미터를 bytea 로 보내 Postgres 가 거부한다.
     *   ERROR: operator does not exist: character varying ~~ bytea
     *
     * 사용자가 친 %, _ 는 와일드카드가 아니라 글자 그대로 찾아야 하므로
     * 이스케이프 문자(!)를 앞에 붙인다. 쿼리 쪽에 escape '!' 가 선언돼 있다.
     * (이스케이프 문자 자신인 ! 를 가장 먼저 치환해야 이중 치환이 안 생긴다)
     */
    private static String likePattern(String keyword) {
        if (keyword == null) return "%";
        String escaped = keyword
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    @Transactional(readOnly = true)
    public PostDto get(Long postId, Long meId) {
        Post post = postRepository.findById(postId).orElseThrow();
        return toDto(post, meId, authorsOf(List.of(post)));
    }

    @Transactional(readOnly = true)
    public List<PostDto> myPosts(Long meId) {
        List<Post> posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(meId);
        Map<Long, UserDto> authors = authorsOf(posts);
        return posts.stream().map(p -> toDto(p, meId, authors)).toList();
    }

    @Transactional
    public PostDto create(Long meId, PostDto.WriteRequest req) {
        validate(req);
        Post post = new Post();
        post.setAuthorId(meId);
        applyFields(post, req);
        postRepository.save(post);

        // chat 이 이 글의 파티 채팅방을 미리 연다. 방 번호는 ChatRoomCreatedEvent 로
        // 돌아와 posts.chat_room_id 에 채워진다. (PostEventPublisher 주석 참고)
        events.postCreated(post);

        return toDto(post, meId, authorsOf(List.of(post)));
    }

    @Transactional
    public PostDto update(Long postId, Long meId, PostDto.WriteRequest req) {
        validate(req);
        Post post = postRepository.findById(postId).orElseThrow();
        if (!post.getAuthorId().equals(meId))
            throw new SecurityException("본인이 작성한 글만 수정할 수 있습니다.");
        applyFields(post, req);
        events.postUpdated(post);
        return toDto(post, meId, authorsOf(List.of(post)));
    }

    /** 모집 완료 처리 — 이후 참가 신청 차단 */
    @Transactional
    public PostDto close(Long postId, Long meId) {
        Post post = postRepository.findById(postId).orElseThrow();
        if (!post.getAuthorId().equals(meId))
            throw new SecurityException("본인 글만 모집 완료할 수 있습니다.");
        post.setStatus(Post.Status.CLOSED);
        events.postUpdated(post);
        return toDto(post, meId, authorsOf(List.of(post)));
    }

    @Transactional
    public void delete(Long postId, Long meId) {
        Post post = postRepository.findById(postId).orElseThrow();
        if (!post.getAuthorId().equals(meId))
            throw new SecurityException("본인이 작성한 글만 삭제할 수 있습니다.");

        // 예전에는 여기서 메시지 → 멤버 → 채팅방을 FK 순서대로 직접 지웠다.
        // 그 테이블들은 이제 chat 이 소유하므로 "지웠다"는 사실만 알린다.
        //
        // ⚠️ 트랜잭션이 하나로 묶이지 않는다. post 는 지워졌는데 chat 정리가
        //    실패하면 방이 남는다. 그래서 chat 쪽 소비자는 몇 번을 받아도
        //    같은 결과가 나오도록(멱등) 작성돼 있고, 실패하면 재시도된다.
        //    분산 트랜잭션을 쓰지 않는 대신 감수하는 비용이다.
        events.postDeleted(postId);

        applicationRepository.deleteByPostId(postId);
        postRepository.delete(post);
    }

    private void applyFields(Post post, PostDto.WriteRequest req) {
        post.setTitle(req.title());
        post.setContent(req.content());
        post.setGame(req.game());
        post.setGameMode(req.gameMode());
        post.setPlayTime(req.playTime());
        post.setMicRequired(req.micRequired());
        post.setPositions(req.positions());
        int target = req.targetMembers() == null ? 2 : req.targetMembers();
        post.setTargetMembers(Math.max(2, Math.min(target, 10)));
    }

    private void validate(PostDto.WriteRequest req) {
        if (req.title() == null || req.title().isBlank())
            throw new IllegalArgumentException("제목을 입력해주세요.");
        if (req.content() == null || req.content().isBlank())
            throw new IllegalArgumentException("내용을 입력해주세요.");
    }

    /**
     * 작성자 정보를 한 번에 가져온다.
     *
     * 글마다 부르면 목록 한 페이지에 HTTP 왕복이 20번이다. 같은 프로세스일 때의
     * N+1 은 쿼리 20개였지만, 서비스가 나뉘면 네트워크 왕복 20번이 된다.
     */
    private Map<Long, UserDto> authorsOf(List<Post> posts) {
        return userClient.findAllByIds(posts.stream().map(Post::getAuthorId).distinct().toList());
    }

    /** 목록과 상세가 공통으로 쓰는 파생값 (글 자체에는 없고 조회해야 나오는 값들) */
    private record Extras(String myStatus, boolean mine, long pending,
                          long currentMembers, Long myRoomId) {}

    private Extras extras(Post p, Long meId) {
        boolean mine = meId != null && p.getAuthorId().equals(meId);

        String myStatus = null;
        if (meId != null && !mine) {
            myStatus = applicationRepository.findByPostIdAndApplicantId(p.getId(), meId)
                    .map(a -> a.getStatus().name()).orElse(null);
        }

        long pending = applicationRepository.countByPostIdAndStatus(p.getId(), Application.Status.PENDING);

        // 모집 현황(n/m)은 "확정된 인원"으로 센다.
        //
        // 예전에는 채팅방 인원(countByRoomId)을 셌는데, 채팅방에는 승인만 받고 아직
        // 확정되지 않은 사람도 들어와 있다. 그래서 자리가 남았는데도 정원이 찬 것처럼
        // 보이거나, 반대로 4/3 처럼 정원을 넘겨 표시되는 문제가 있었다.
        //
        // 지금은 그 계산에 chat 이 필요 없다는 점이 더 중요하다. 확정 여부는
        // applications 에 있고, 그건 post 가 소유한 테이블이다.
        long currentMembers = 1 + applicationRepository.countByPostIdAndStatus(
                p.getId(), Application.Status.CONFIRMED);

        // 예전에는 chatRoomMemberRepository.existsByRoomIdAndUserId 로 "내가 이 방
        // 멤버인가"를 물었다. 그 테이블은 이제 chat 소유다.
        //
        // 대신 post 가 아는 사실로 판정한다: 방장이거나, 승인/확정된 신청자면
        // 채팅방에 들어가 있다. 이 조건이 곧 chat 이 멤버를 넣는 조건과 같다
        // (ApplicationApprovedEvent 소비자 참고) — 두 서비스가 같은 규칙을 본다.
        Long myRoomId = null;
        if (p.getChatRoomId() != null && meId != null
                && (mine || "APPROVED".equals(myStatus) || "CONFIRMED".equals(myStatus))) {
            myRoomId = p.getChatRoomId();
        }

        return new Extras(myStatus, mine, pending, currentMembers, myRoomId);
    }

    private List<PostDto.Summary> toSummaries(List<Post> posts, Long meId) {
        Map<Long, UserDto> authors = authorsOf(posts);
        return posts.stream().map(p -> toSummary(p, meId, authors)).toList();
    }

    /** 목록용 — content 를 싣지 않는다. */
    private PostDto.Summary toSummary(Post p, Long meId, Map<Long, UserDto> authors) {
        Extras e = extras(p, meId);
        return new PostDto.Summary(p.getId(), p.getTitle(), p.getCreatedAt(),
                authors.get(p.getAuthorId()), e.pending(), e.myStatus(), e.mine(),
                p.getGame(), p.getGameMode(), p.getPlayTime(), p.isMicRequired(),
                p.getPositions(), p.getTargetMembers(), e.currentMembers(),
                p.getStatus().name(), e.myRoomId());
    }

    private PostDto toDto(Post p, Long meId, Map<Long, UserDto> authors) {
        Extras e = extras(p, meId);
        return new PostDto(p.getId(), p.getTitle(), p.getContent(), p.getCreatedAt(),
                authors.get(p.getAuthorId()), e.pending(), e.myStatus(), e.mine(),
                p.getGame(), p.getGameMode(), p.getPlayTime(), p.isMicRequired(),
                p.getPositions(), p.getTargetMembers(), e.currentMembers(),
                p.getStatus().name(), e.myRoomId());
    }
}
