package gg.duo.post.domain.requirement;

import gg.duo.common.constant.GameCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 게임별 모집 조건. 글 하나당 한 행이다.
 *
 * [FR-02] 게임을 고르면 조건 입력 필드가 바뀐다. 롤은 '포지션'과 8단계 티어를,
 * 발로란트는 '역할'과 9단계 티어를 쓴다. 이걸 posts 테이블에 다 펼치면
 * lol_position / valorant_role / lol_tier / valorant_tier ... 처럼 게임을 하나
 * 추가할 때마다 컬럼이 늘고, 그중 대부분이 항상 NULL 인 표가 된다.
 *
 * 그래서 "게임마다 선택지가 다른 것"만 이쪽으로 떼어 놓는다. 반대로 목록 화면이
 * 조인 없이 그려야 하는 값(게임·모드·음성채팅 정도·인원·시간)은 posts 에 남는다.
 * 모드는 게임마다 목록이 다르지만 저장되는 건 문자열 하나뿐이고, 목록 필터가
 * 매 페이지 이 값으로 거르기 때문에 조인 밖에 두는 편이 낫다.
 * (어떤 모드가 유효한지는 GameOptions 가 저장 시점에 검사한다)
 *
 * roles 한 칸에 롤의 포지션과 발로란트의 역할이 같이 들어간다. 화면 라벨만
 * 다르고 "이 게임에서 내가 맡는 자리"라는 의미가 같아서, 칸을 나누면 추천
 * 로직이 게임마다 다른 필드를 봐야 한다.
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

    /** 찾는 포지션(LOL) 또는 역할(VALORANT), 콤마 구분. 비어 있으면 상관없음. */
    private String roles;

    /**
     * 찾는 티어 — 사용자가 고른 한 개. "이 티어인 사람을 찾는다"는 뜻이다.
     * null 이면 상관없음.
     *
     * 범위(min~max)로 두지 않은 이유: 작성자가 실제로 아는 건 "나랑 비슷한 사람"
     * 하나뿐이고, 하한·상한을 각각 고르게 하면 대부분 아무거나 넣는다.
     * 허용 폭은 추천 쪽(FR-05)이 이 값 기준으로 정한다.
     */
    private String tier;

    /** 빡겜 / 즐겜. null 이면 상관없음. users.play_style 과 같은 값이어야 한다. */
    private String playStyle;
}
