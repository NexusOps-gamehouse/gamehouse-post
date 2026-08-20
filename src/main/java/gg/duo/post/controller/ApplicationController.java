package gg.duo.post.controller;

import gg.duo.post.dto.ApplicationDto;
import gg.duo.post.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    private Long userId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }

    /** 참가 신청 */
    @PostMapping("/api/posts/{postId}/apply")
    public void apply(@PathVariable Long postId, Authentication auth) {
        applicationService.apply(postId, userId(auth));
    }

    /** 신청자: 참가 신청 취소 */
    @DeleteMapping("/api/posts/{postId}/apply")
    public void cancel(@PathVariable Long postId, Authentication auth) {
        applicationService.cancel(postId, userId(auth));
    }

    /** 방장: 신청자 목록 */
    @GetMapping("/api/posts/{postId}/applications")
    public List<ApplicationDto> listForPost(@PathVariable Long postId, Authentication auth) {
        return applicationService.listForPost(postId, userId(auth));
    }

    /** 방장: 승인 → 채팅방 생성 */
    @PostMapping("/api/applications/{id}/approve")
    public Map<String, Long> approve(@PathVariable Long id, Authentication auth) {
        return applicationService.approve(id, userId(auth));
    }

    /** 방장: 거절 */
    @PostMapping("/api/applications/{id}/reject")
    public void reject(@PathVariable Long id, Authentication auth) {
        applicationService.reject(id, userId(auth));
    }

    /** 방장: 파티원 모집 확정 */
    @PostMapping("/api/applications/{id}/confirm")
    public void confirm(@PathVariable Long id, Authentication auth) {
        applicationService.confirm(id, userId(auth));
    }

    /** 내 신청 현황 */
    @GetMapping("/api/my/applications")
    public List<ApplicationDto> myApplications(Authentication auth) {
        return applicationService.myApplications(userId(auth));
    }
}
