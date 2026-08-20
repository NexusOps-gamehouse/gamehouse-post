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
        // 모집 조건
        String game,
        String gameMode,
        String playTime,
        boolean micRequired,
        String positions,
        int targetMembers,
        long currentMembers,        // 파티 채팅방 인원 (방장 포함, 방 없으면 1)
        String status,              // RECRUITING | CLOSED
        Long chatRoomId             // 내가 멤버인 경우에만 세팅
) {
    public record WriteRequest(String title, String content, String game, String gameMode,
                               String playTime, boolean micRequired, String positions,
                               Integer targetMembers) {}

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
            boolean micRequired,
            String positions,
            int targetMembers,
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
