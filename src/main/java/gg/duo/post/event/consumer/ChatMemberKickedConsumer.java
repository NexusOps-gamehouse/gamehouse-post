package gg.duo.post.event.consumer;

import gg.duo.common.event.ChatMemberKickedEvent;
import gg.duo.post.domain.application.Application;
import gg.duo.post.domain.application.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅방에서 내보내진 사람의 참가 신청을 거절 처리한다.
 *
 * 예전에는 ChatService 가 ApplicationRepository 를 직접 잡고 상태를 바꿨다.
 * applications 는 post 소유이므로 이제 chat 이 사실만 알리고 여기서 처리한다.
 */
@Component
@RequiredArgsConstructor
public class ChatMemberKickedConsumer {

    private final ApplicationRepository applicationRepository;

    @EventListener
    @Transactional
    public void on(ChatMemberKickedEvent event) {
        applicationRepository
                .findByPostIdAndApplicantId(event.postId(), event.targetUserId())
                .ifPresent(a -> a.setStatus(Application.Status.REJECTED));
    }
}
