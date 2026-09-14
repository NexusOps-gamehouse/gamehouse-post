package gg.duo.post;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * post 서비스 — 모집글 · 지원.
 *
 * 소유 테이블: posts, post_game_requirements, applications
 */
// ChatRoomReconciler 가 @Scheduled 로 돈다. 끄려면 프로퍼티로 끈다
// (gamehouse.reconcile.chat-room.enabled=false) — 어노테이션을 빼지 않는다.
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"gg.duo.post", "gg.duo.common"})
public class PostApplication {
    public static void main(String[] args) {
        SpringApplication.run(PostApplication.class, args);
    }
}
