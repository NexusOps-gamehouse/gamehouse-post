package gg.duo.post.service;

import gg.duo.common.dto.UserDto;
import gg.duo.post.client.UserClient;
import gg.duo.post.domain.application.Application;
import gg.duo.post.domain.application.ApplicationRepository;
import gg.duo.post.domain.post.Post;
import gg.duo.post.domain.post.PostRepository;
import gg.duo.post.dto.TeamCompositionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "이 방의 현재 팀"의 유일한 정답 소스.
 *
 * [왜 필요한가]
 * 지금 이 정보가 두 군데에 흩어져 있다.
 *   - Application(status = CONFIRMED) : 방장이 확정한 사람
 *   - ChatRoomMember(confirmed = true) : 채팅방에 있는 확정 멤버
 * 둘은 같아야 하지만 강제되는 곳이 없다. 강퇴는 ChatRoomMember 를 지우고
 * Application 을 REJECTED 로 바꾸는데, 한쪽만 실패하면 조용히 어긋난다.
 * 서비스가 나뉘면 두 값이 서로 다른 DB 에 있으므로 어긋날 여지가 더 커진다.
 *
 * [정답을 post 로 정한 이유]
 * 정원(targetMembers)과 확정 권한(방장)이 전부 post 에 있다. 채팅방 멤버십은
 * "누가 대화에 참여 중인가"이지 "누가 팀인가"가 아니다 — 승인만 받고 아직
 * 확정되지 않은 사람도 방에는 들어와 있다.
 *
 * ★ 1단계 범위: memberIds 까지 실제로 계산한다.
 *   filledRoles / avgTierIndex / styleDist 는 게임별 프로필(UserGameProfile)과
 *   성향 설문 환산이 끝나야 의미가 생기므로 TODO 로 둔다.
 */
@Service
@RequiredArgsConstructor
public class TeamCompositionService {

    private final PostRepository postRepository;
    private final ApplicationRepository applicationRepository;
    private final UserClient userClient;

    @Transactional(readOnly = true)
    public TeamCompositionDto of(Long postId) {
        Post post = postRepository.findById(postId).orElseThrow();

        // 방장은 글을 만든 시점에 이미 한 자리를 차지한다.
        List<Long> memberIds = new ArrayList<>();
        memberIds.add(post.getAuthorId());
        applicationRepository.findByPostIdOrderByCreatedAtDesc(postId).stream()
                .filter(a -> a.getStatus() == Application.Status.CONFIRMED)
                .map(Application::getApplicantId)
                .forEach(memberIds::add);

        Map<Long, UserDto> members = userClient.findAllByIds(memberIds);

        // 포지션은 지금 users.position(단일 값)에만 있다.
        // UserGameProfile 로 옮기면 "롤에서는 정글, 발로란트에서는 컨트롤러"가
        // 표현되고, 그때 이 계산이 게임별로 갈라진다.
        Set<String> filledRoles = new LinkedHashSet<>();
        members.values().stream()
                .map(UserDto::position)
                .filter(p -> p != null && !p.isBlank())
                .forEach(filledRoles::add);

        // TODO(2단계): 티어 문자열 → 숫자 환산표가 필요하다.
        //   users.tier(사용자가 고른 한글)와 users.riot_tier(검증된 영문)가
        //   섞여 있어 어느 쪽을 신뢰할지부터 정해야 한다.
        double avgTierIndex = 0.0;

        // TODO(2단계): SurveyService 의 6축 환산이 끝나야 채울 수 있다.
        Map<String, Integer> styleDist = Map.of();

        return new TeamCompositionDto(postId, List.copyOf(memberIds), filledRoles,
                avgTierIndex, styleDist);
    }
}
