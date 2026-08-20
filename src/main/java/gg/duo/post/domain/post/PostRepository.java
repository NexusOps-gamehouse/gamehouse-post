package gg.duo.post.domain.post;

import gg.duo.post.domain.post.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findByAuthorIdOrderByCreatedAtDesc(Long authorId);

    // -----------------------------------------------------------------------
    // 목록 조회
    //
    // 예전에는 findAllByOrderByCreatedAtDesc() 로 글을 전부 가져온 뒤
    // 서비스의 자바 스트림에서 걸렀다. 결과는 맞았지만 두 가지 문제가 있었다.
    //
    //  1) 글 개수만큼 엔티티를 힙에 올린다. content 가 TEXT 라 글이 늘수록
    //     요청 하나가 쓰는 메모리가 그대로 커진다.
    //  2) 페이징을 붙일 수 없다. LIMIT 은 DB 만 할 수 있는데, 거르기가
    //     자바에 있으면 "자르고 → 거르는" 순서가 되어 결과가 틀린다.
    //     (최신 20개 중 발로란트만 남기면 2개, 다음 페이지는 3개 …)
    //
    // 그래서 조건을 전부 where 절로 내렸다. 이제 DB 안에서
    // 거르기 → 정렬 → 자르기가 올바른 순서로 한 번에 끝난다.
    //
    // ':param is null or ...' 은 동적 조건을 쿼리 하나로 표현하는 관용구다.
    // 파라미터를 넘기지 않으면(null) 그 줄이 항상 참이 되어 조건에서 빠진다.
    //
    // 다만 keyword 만은 이 방식을 쓰지 않고 항상 값을 넘긴다(검색어가 없으면 '%').
    // like 에 null 을 바인딩하면 드라이버가 그 파라미터를 bytea 로 보내고,
    // Postgres 에는 'character varying ~~ bytea' 연산자가 없어 실행 시점에 터진다.
    //   ERROR: operator does not exist: character varying ~~ bytea
    // '=' 는 컬럼 타입으로 추론이 되지만 like/|| 는 안 되기 때문에 생기는 차이다.
    //
    // escape '!' 를 둔 이유: 사용자가 입력한 %, _ 는 와일드카드가 아니라
    // 글자 그대로 찾아야 한다. 이스케이프 문자는 서비스에서 붙인다.
    //
    // 정렬에 id 를 덧붙인 이유: createdAt 만으로 정렬하면 같은 시각에 작성된
    // 글이 페이지 경계에서 중복되거나 누락될 수 있다.
    //
    // countQuery 를 직접 적은 이유: 자동 생성에 맡기면 order by 가 섞인
    // 쿼리에서 실패할 수 있고, 명시해두면 의도가 드러난다.
    // -----------------------------------------------------------------------

    /** 목록 — 제목 검색 (검색어가 없으면 서비스가 '%' 를 넘긴다) */
    @Query(value = """
            select p
              from Post p
             where (:game     is null or p.game     = :game)
               and (:gameMode is null or p.gameMode = :gameMode)
               and (:status   is null or p.status   = :status)
               and p.title like :keyword escape '!'
             order by p.createdAt desc, p.id desc
            """,
            countQuery = """
            select count(p)
              from Post p
             where (:game     is null or p.game     = :game)
               and (:gameMode is null or p.gameMode = :gameMode)
               and (:status   is null or p.status   = :status)
               and p.title like :keyword escape '!'
            """)
    Page<Post> searchByTitle(@Param("game") String game,
                            @Param("gameMode") String gameMode,
                            @Param("status") Post.Status status,
                            @Param("keyword") String keyword,
                            Pageable pageable);

    /**
     * 목록 — 글쓴이 닉네임 검색.
     *
     * 예전에는 `join p.author a where a.nickname like ...` 였다. users 가
     * post 서비스 밖으로 나가면서 이 조인은 더 이상 불가능하다.
     *
     * 대신 두 걸음으로 나눈다.
     *   1) user 서비스에 "이 닉네임을 포함하는 사용자 id" 를 물어본다 (UserClient)
     *   2) 그 id 목록으로 posts 를 거른다 (아래 쿼리)
     *
     * 왕복이 한 번 늘지만, 검색어가 있을 때만 나가고 결과가 id 목록이라 가볍다.
     * 반대로 posts 를 전부 가져와 자바에서 거르면 페이징이 깨진다 —
     * LIMIT 은 DB 만 할 수 있는데 거르기가 자바에 있으면 "자르고 → 거르는"
     * 순서가 되어 페이지마다 결과 개수가 들쭉날쭉해진다.
     *
     * authorIds 가 빈 목록이면 호출하지 않는다 (서비스에서 빈 페이지로 끊는다).
     * JPA 의 in () 은 빈 컬렉션에서 문법 오류가 나는 DB 가 있다.
     */
    @Query(value = """
            select p
              from Post p
             where (:game     is null or p.game     = :game)
               and (:gameMode is null or p.gameMode = :gameMode)
               and (:status   is null or p.status   = :status)
               and p.authorId in :authorIds
             order by p.createdAt desc, p.id desc
            """,
            countQuery = """
            select count(p)
              from Post p
             where (:game     is null or p.game     = :game)
               and (:gameMode is null or p.gameMode = :gameMode)
               and (:status   is null or p.status   = :status)
               and p.authorId in :authorIds
            """)
    Page<Post> searchByAuthorIds(@Param("game") String game,
                                 @Param("gameMode") String gameMode,
                                 @Param("status") Post.Status status,
                                 @Param("authorIds") List<Long> authorIds,
                                 Pageable pageable);
}
