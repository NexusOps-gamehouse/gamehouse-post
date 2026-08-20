package gg.duo.post.config;

import gg.duo.common.security.SecurityBaseConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends SecurityBaseConfig {

    @Override
    protected void configurePublicEndpoints(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        // 게시글 조회는 로그인 없이 허용
        auth.requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/*").permitAll();
        // 서비스 간 호출. Ingress 에 /internal 규칙이 없어 클러스터 밖에서는 닿지 않는다.
        auth.requestMatchers("/internal/**").permitAll();
    }
}
