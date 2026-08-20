package gg.duo.post.client;

import gg.duo.common.dto.UserDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * user 서비스 조회 — 클러스터 내부 전용.
 *
 * 조회가 전부 '묶음' 단위인 이유: 목록 한 페이지가 글 20개면 작성자 조회도
 * 20건이다. 단건 API 를 20번 부르면 HTTP 왕복이 20번 — 같은 프로세스일 때의
 * N+1 보다 훨씬 비싸다. id 를 모아 한 번에 가져온다.
 *
 * 실패해도 예외를 던지지 않고 빈 결과로 흘리는 이유: 작성자 닉네임을 못 가져와도
 * 모집글 목록 자체는 보여줄 수 있다. user 가 잠깐 죽었다고 글 목록까지 500 이
 * 되면 서비스를 나눈 의미가 없다 — 그게 분산 모놀리스의 증상이다.
 */
@Slf4j
@Component
public class UserClient {

    private static final ParameterizedTypeReference<List<UserDto>> USER_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Long>> ID_LIST =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public UserClient(RestClient.Builder builder,
                      @Value("${services.user.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /** id 묶음 → id 로 찾을 수 있는 Map. 못 찾은 id 는 그냥 빠진다. */
    public Map<Long, UserDto> findAllByIds(Iterable<Long> ids) {
        List<Long> idList = toList(ids);
        if (idList.isEmpty()) return Map.of();
        try {
            List<UserDto> users = restClient.get()
                    .uri(uri -> uri.path("/internal/users")
                            .queryParam("ids", idList)
                            .build())
                    .retrieve()
                    .body(USER_LIST);
            if (users == null) return Map.of();
            return users.stream().collect(Collectors.toMap(UserDto::id, Function.identity()));
        } catch (RestClientException e) {
            log.warn("user 서비스 조회 실패 — 작성자 정보 없이 응답한다. ids={}", idList, e);
            return Map.of();
        }
    }

    /** 닉네임 부분 일치 → 사용자 id 목록. 검색에만 쓴다. */
    public List<Long> findIdsByNicknameContaining(String keyword) {
        try {
            List<Long> ids = restClient.get()
                    .uri(uri -> uri.path("/internal/users/ids-by-nickname")
                            .queryParam("keyword", keyword)
                            .build())
                    .retrieve()
                    .body(ID_LIST);
            return ids == null ? List.of() : ids;
        } catch (RestClientException e) {
            log.warn("user 서비스 닉네임 검색 실패 — 결과 없음으로 처리한다. keyword={}", keyword, e);
            return List.of();
        }
    }

    private static List<Long> toList(Iterable<Long> ids) {
        if (ids == null) return List.of();
        if (ids instanceof List<Long> list) return list;
        List<Long> out = new java.util.ArrayList<>();
        ids.forEach(out::add);
        return out;
    }
}
