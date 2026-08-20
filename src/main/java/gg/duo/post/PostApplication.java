package gg.duo.post;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * post 서비스 — 모집글 · 지원.
 *
 * 소유 테이블: posts, post_game_requirements, applications
 */
@SpringBootApplication(scanBasePackages = {"gg.duo.post", "gg.duo.common"})
public class PostApplication {
    public static void main(String[] args) {
        SpringApplication.run(PostApplication.class, args);
    }
}
