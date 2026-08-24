package gg.duo.post.dto;

import gg.duo.common.dto.UserDto;
import java.time.Instant;
import java.util.List;

public record PostDto(
        Long id,
        String title,
        String content,
        Instant createdAt,
        UserDto author,
        long pendingCount,
        String myApplicationStatus, // null | PENDING | APPROVED | CONFIRMED | REJECTED
        boolean mine,
        // 모집 조건 (공통)
        String game,                // GameCode 이름. "LOL" | "VALORANT"
        String gameMode,
        String playTime,
        String voiceChat,           // REQUIRED | PREFERRED | ANY
        int targetMembers,
        // 모집 조건 (게임별) — PostGameRequirement 에서 온다. 조건을 안 걸었으면 null
        String roles,               // 포지션(LOL) 또는 역할(VALORANT), 콤마 구분
        String tier,
        String playStyle,           // 빡겜 | 즐겜
        long currentMembers,        // 파티 채팅방 인원 (방장 포함, 방 없으면 1)
        String status,              // RECRUITING | CLOSED
        Long chatRoomId             // 내가 멤버인 경우에만 세팅
) {
    /**
     * 작성·수정 요청.
     *
     * roles / tier / playStyle 은 고른 게임에 따라 유효한 값이 달라진다.
     * 검증은 서버(GameOptions)가 한다 — 프론트만 막으면 API 를 직접 부르는 쪽에서
     * 발로란트 글에 '정글'이 들어가고, 그 글은 어떤 추천에도 안 걸린다.
     */
    public record WriteRequest(String title, String content, String game, String gameMode,
                               String playTime, String voiceChat, Integer targetMembers,
                               String roles, String tier, String playStyle) {}

    /**
     * 목록 전용 응답. PostDto 에서 content 만 뺀 형태다.
     *
     * 본문은 상세 화면에서만 쓰는데(프론트 PostDetailPage), 목록 응답에 실리면
     * 글 개수만큼 TEXT 컬럼이 그대로 JSON 에 붙는다. 목록 화면(MainPage)은
     * content 를 아예 참조하지 않으므로 빼도 화면은 그대로다.
     */
    public record Summary(
            Long id,
            String title,
            Instant createdAt,
            UserDto author,
            long pendingCount,
            String myApplicationStatus,
            boolean mine,
            String game,
            String gameMode,
            String playTime,
            String voiceChat,
            int targetMembers,
            String roles,
            String tier,
            String playStyle,
            long currentMembers,
            String status,
            Long chatRoomId
    ) {}

    /**
     * 페이징 응답.
     *
     * Spring 의 Page 를 그대로 직렬화하면 pageable/sort 같은 내부 구조가
     * 응답에 노출되고 프론트가 그 형태에 묶인다. 화면에 필요한 것만 담는다.
     */
    public record ListResponse(
            List<Summary> items,
            int page,
            int size,
            long totalElements,
            boolean hasNext
    ) {}
}
