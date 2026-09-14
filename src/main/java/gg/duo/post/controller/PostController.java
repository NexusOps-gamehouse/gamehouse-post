package gg.duo.post.controller;

import gg.duo.post.domain.requirement.GameOptions;
import gg.duo.post.dto.PostDto;
import gg.duo.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    private Long userId(Authentication auth) {
        return auth == null ? null : (Long) auth.getPrincipal();
    }

    /**
     * 게임별 선택지 목록.
     *
     * [FR-02] 화면이 그리는 목록과 서버가 검증하는 목록이 어긋나면, 사용자는
     * 고를 수 있는데 저장은 400 이 나는 항목이 생긴다. 프론트가 이 응답을 쓰면
     * 목록이 한 곳에서만 관리된다.
     */
    @GetMapping("/api/posts/game-options")
    public List<Map<String, Object>> gameOptions() {
        return GameOptions.catalogAll();
    }

    /**
     * 배포 확인용 엔드포인트.
     *
     * GitOps 파이프라인(GitHub Actions -> ECR -> infra newTag -> Argo CD)이 실제로
     * 클러스터까지 도달했는지 밖에서 확인하려고 둔다. 읽기 전용이고 어떤 상태도
     * 바꾸지 않으며 비즈니스 로직을 타지 않는다.
     *
     * 경로는 반드시 한 단계로 유지한다 - SecurityConfig 가 permitAll 로 여는 것은
     * GET /api/posts/* 이고 이건 세그먼트 하나만 매칭한다. /api/posts/meta/check 처럼
     * 한 단계 더 내리면 permitAll 이 걸리지 않아 401 이 된다.
     *
     * /api/posts/{id} 와 겹쳐 보이지만 리터럴 경로가 경로 변수보다 우선한다 -
     * 같은 컨트롤러의 /api/posts/game-options 가 이미 같은 방식으로 동작한다.
     */
    @GetMapping("/api/posts/deploy-check")
    public Map<String, String> deployCheck() {
        return Map.of(
                "service", "post",
                "status", "ok",
                "message", "GitOps 자동 배포 확인용 엔드포인트입니다."
        );
    }

    /**
     * 모집글 목록.
     *
     * page/size 를 주지 않으면 최신 20개를 돌려준다. 예전에는 전체를 돌려줬는데,
     * 글이 늘수록 요청 하나가 쓰는 메모리와 쿼리 수가 그대로 커지는 구조였다.
     */
    @GetMapping("/api/posts")
    public PostDto.ListResponse list(Authentication auth,
                                     @RequestParam(required = false) String searchType,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String game,
                                     @RequestParam(required = false) String gameMode,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return postService.list(userId(auth), searchType, keyword, game, gameMode, status,
                page, size);
    }

    /** 모집 완료 처리 */
    @PostMapping("/api/posts/{id}/close")
    public PostDto close(@PathVariable Long id, Authentication auth) {
        return postService.close(id, userId(auth));
    }

    @GetMapping("/api/posts/{id}")
    public PostDto get(@PathVariable Long id, Authentication auth) {
        return postService.get(id, userId(auth));
    }

    @PostMapping("/api/posts")
    public PostDto create(Authentication auth, @RequestBody PostDto.WriteRequest req) {
        return postService.create(userId(auth), req);
    }

    @PutMapping("/api/posts/{id}")
    public PostDto update(@PathVariable Long id, Authentication auth,
                          @RequestBody PostDto.WriteRequest req) {
        return postService.update(id, userId(auth), req);
    }

    @DeleteMapping("/api/posts/{id}")
    public void delete(@PathVariable Long id, Authentication auth) {
        postService.delete(id, userId(auth));
    }

    @GetMapping("/api/my/posts")
    public List<PostDto> myPosts(Authentication auth) {
        return postService.myPosts(userId(auth));
    }
}
