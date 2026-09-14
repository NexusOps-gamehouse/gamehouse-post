package gg.duo.post.event.consumer;

import gg.duo.common.event.ChatRoomCreatedEvent;
import gg.duo.post.domain.post.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * chat 이 방을 열었다 → posts.chat_room_id 를 채운다.
 *
 * 이 복제가 없으면 목록 조회 한 번에 chat 으로 HTTP 호출이 글 개수만큼 나간다.
 * 방 번호는 한 번 정해지면 바뀌지 않으므로 복제해도 어긋나지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomCreatedConsumer {

    private final PostRepository postRepository;

    @EventListener
    @Transactional
    public void on(ChatRoomCreatedEvent event) {
        postRepository.findById(event.postId())
                .ifPresentOrElse(
                        post -> post.setChatRoomId(event.roomId()),
                        // 못 찾으면 그대로 사라진다. 브리지가 예외를 삼키도록
                        // 되어 있어 되돌아오지도 DLQ 로 가지도 않기 때문이다.
                        // 조용히 넘어가면 다음에 또 새도 알 수 없으므로 남긴다.
                        () -> log.warn("채팅방 번호를 채울 글을 찾지 못했다. postId={} roomId={}",
                                event.postId(), event.roomId()));
    }
}
