package gg.duo.post.reconcile;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보정 배치가 쓰는 부분 인덱스를 기동 시 만든다.
 *
 * [왜 코드로 만드나]
 * 이 레포에는 Flyway·Liquibase 가 없고 ddl-auto: update 로 스키마를 맞춘다.
 * 그런데 Hibernate 는 **부분 인덱스(WHERE 절이 붙은 인덱스)를 만들 수 없다.**
 * @Table(indexes=...) 로는 조건을 표현할 방법이 없다.
 *
 * db/init.sql 에 넣을 수도 없다. 그 파일은 앱이 뜨기 **전에** 스키마와 계정을
 * 만드는 용도라, 그 시점에는 posts 테이블 자체가 없다.
 *
 * 그래서 기동 시 한 번 실행한다. ddl-auto 가 이미 DDL 을 돌리고 있으므로
 * 새로운 방식을 들이는 것은 아니다.
 *
 * [왜 부분 인덱스인가]
 * 보정 배치의 조건이 `chat_room_id IS NULL` 이다. 일반 인덱스를 걸면 모든 글이
 * 들어가지만, 부분 인덱스는 **조건에 맞는 행만** 담는다. 정상이면 거의 비어 있어
 * 크기도 조회 비용도 사실상 0 이다. 없으면 2분마다 posts 전체를 훑는다.
 *
 * [CONCURRENTLY 를 안 쓰는 이유]
 * CREATE INDEX CONCURRENTLY 는 트랜잭션 안에서 실행할 수 없어 기동 경로에
 * 넣기 까다롭다. 부분 인덱스라 대상 행이 적어 잠금 시간이 짧고, 기동 시점에는
 * 아직 트래픽을 받기 전이다.
 *
 * 운영 테이블이 이미 커진 뒤에 도입한다면 이 실행을 건너뛰고(프로퍼티로 끄고)
 * 손으로 CONCURRENTLY 를 돌리는 편이 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissingChatRoomIndex implements ApplicationRunner {

    private static final String DDL = """
            CREATE INDEX IF NOT EXISTS idx_posts_missing_chat_room
                ON posts (created_at)
             WHERE chat_room_id IS NULL
            """;

    private final EntityManager em;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            em.createNativeQuery(DDL).executeUpdate();
            log.info("부분 인덱스 확인 완료: idx_posts_missing_chat_room");
        } catch (Exception e) {
            // 인덱스가 없어도 보정 배치는 동작한다. 느려질 뿐이다.
            // 권한 문제나 수동으로 이미 만든 경우가 있을 수 있으므로 기동을 막지 않는다.
            log.warn("부분 인덱스를 만들지 못했다. 보정 배치가 느려질 수 있다. "
                    + "손으로 적용한다: CREATE INDEX CONCURRENTLY IF NOT EXISTS "
                    + "idx_posts_missing_chat_room ON post_svc.posts (created_at) "
                    + "WHERE chat_room_id IS NULL", e);
        }
    }
}
