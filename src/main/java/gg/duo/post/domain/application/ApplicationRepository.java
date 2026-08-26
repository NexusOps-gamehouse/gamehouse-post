package gg.duo.post.domain.application;

import gg.duo.post.domain.application.Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    /**
     * 목록 조회 2건.
     * post·applicant 를 여기서 조인해 끌어오지 않는다. ApplicationService.toDtos 가
     * id 를 모아 IN 으로 한 번에 가져간다 — 이유는 그쪽 주석에 적었다.
     */
    List<Application> findByPostIdOrderByCreatedAtDesc(Long postId);
    List<Application> findByApplicantIdOrderByCreatedAtDesc(Long applicantId);

    Optional<Application> findByPostIdAndApplicantId(Long postId, Long applicantId);
    boolean existsByPostIdAndApplicantId(Long postId, Long applicantId);
    long countByPostIdAndStatus(Long postId, Application.Status status);
    void deleteByPostId(Long postId);

    /**
     * 글 묶음의 확정(CONFIRMED) 파티원. match 서비스가 Team Fit 계산에 쓰는
     * "현재 방에 들어가 있는 팀원"의 기준이다 — 승인만 되고 아직 확정 안 된
     * 신청자(APPROVED)는 여기 포함하지 않는다(모집 현황 n/m 과 같은 기준).
     *
     * post_id 로 바로 못 거는 이유: Application.post 가 연관(@ManyToOne)이라
     * JPA 파생 쿼리는 "post_Id"로 중첩 프로퍼티를 타야 한다.
     */
    List<Application> findByPost_IdInAndStatus(List<Long> postIds, Application.Status status);
}
