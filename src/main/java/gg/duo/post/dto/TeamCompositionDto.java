package gg.duo.post.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "이 모집글의 현재 팀".
 *
 * 매칭 점수의 절반(포지션 30% + 실력 20%)이 이 값에 달려 있다.
 */
public record TeamCompositionDto(
        Long postId,
        /** 방장 + 확정된 신청자 */
        List<Long> memberIds,
        /** 이미 찬 포지션 */
        Set<String> filledRoles,
        /** 팀 평균 실력 지표 (티어를 숫자로 환산한 값) */
        double avgTierIndex,
        /** 6축 성향 분포 */
        Map<String, Integer> styleDist
) {}
