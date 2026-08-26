package gg.duo.post.dto;

import java.util.List;

/**
 * 글 하나의 확정(CONFIRMED) 파티원 id 목록 — 서비스 간 내부 호출 전용 응답.
 *
 * 닉네임·나이를 여기 담지 않는 이유: 그 값은 이미 user 서비스의 /internal/users
 * (묶음 조회)가 공개 안전한 형태로 내려주고 있다. 여기서 또 만들면 "누가 이 글에
 * 신청/확정했는가"라는, 지금은 방장만 볼 수 있는 정보(ApplicationController 참고)를
 * post 가 스스로 넓히는 셈이라 최소한만 내려준다 — user id 만.
 */
public record PostPartyView(Long postId, List<Long> memberIds) {}
