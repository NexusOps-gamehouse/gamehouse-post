package gg.duo.post.event.consumer;

import gg.duo.common.event.ChatRoomCreatedEvent;
import gg.duo.post.domain.post.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * chat 이 방을 열었다 → posts.chat_room_id 를 채운다.
 *
 * 이 복제가 없으면 목록 조회 한 번에 chat 으로 HTTP 호출이 글 개수만큼 나간다.
 * 방 번호는 한 번 정해지면 바뀌지 않으므로 복제해도 어긋나지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ChatRoomCreatedConsumer {

    private final PostRepository postRepository;

    @EventListener
    @Transactional
    public void on(ChatRoomCreatedEvent event) {
        postRepository.findById(event.postId())
                .ifPresent(post -> post.setChatRoomId(event.roomId()));
    }
}
