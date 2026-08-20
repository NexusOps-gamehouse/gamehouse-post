package gg.duo.post.controller;

import gg.duo.post.dto.PostDto;
import gg.duo.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    private Long userId(Authentication auth) {
        return auth == null ? null : (Long) auth.getPrincipal();
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
