package gg.duo.post.domain.application;

import gg.duo.post.domain.post.Post;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "applications",
        uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "applicant_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Application {

    public enum Status { PENDING, APPROVED, CONFIRMED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 이 연관은 남는다. posts 와 applications 둘 다 post 서비스가 소유하므로
     * 경계를 넘지 않는다. 서비스 경계에서만 연관을 끊는다 — 모든 연관을 다
     * id 로 바꾸는 것은 MSA 가 아니라 그냥 불편한 JPA 다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    /** 신청자 — users.id. (경계를 넘으므로 연관을 끊었다) */
    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
