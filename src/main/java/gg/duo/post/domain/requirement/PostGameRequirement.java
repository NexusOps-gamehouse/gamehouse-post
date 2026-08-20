package gg.duo.post.domain.requirement;

import gg.duo.common.constant.GameCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 게임별 모집 조건.
 *
 * 지금 Post 에는 game / gameMode / positions 가 한 벌씩만 있다. 한 글에서
 * "롤 정글 구함 + 발로란트도 가능" 같은 조건을 표현할 수 없다.
 *
 * ★ 1단계에서는 Post 의 기존 필드를 그대로 두고 병행 신설한다.
 */
@Entity
@Table(name = "post_game_requirements")
@Getter
@Setter
@NoArgsConstructor
public class PostGameRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_code", nullable = false, length = 32)
    private GameCode gameCode;

    /** 찾는 포지션, 콤마 구분 */
    private String positions;

    private String minTier;
    private String maxTier;
    private String gameModes;
    private boolean micRequired;
}
