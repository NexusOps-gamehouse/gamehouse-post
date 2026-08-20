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
}
