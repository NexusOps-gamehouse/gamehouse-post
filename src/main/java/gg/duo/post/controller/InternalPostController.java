package gg.duo.post.controller;

import gg.duo.post.dto.PostPartyView;
import gg.duo.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 서비스 간 호출 전용 API. Ingress 에 노출하지 않는다.
 * user 서비스의 InternalUserController 와 같은 규칙이다 — /internal/** 은
 * 클러스터 안에서만 닿고, SecurityConfig 에서 permitAll 이다.
 */
@RestController
@RequestMapping("/internal/posts")
@RequiredArgsConstructor
public class InternalPostController {

    private final PostService postService;

    /** 글 묶음 → 글별 확정(CONFIRMED) 파티원 id. match 의 Team Fit 계산 전용. */
    @GetMapping("/party")
    public List<PostPartyView> party(@RequestParam List<Long> ids) {
        return postService.confirmedPartyMembers(ids);
    }
}
