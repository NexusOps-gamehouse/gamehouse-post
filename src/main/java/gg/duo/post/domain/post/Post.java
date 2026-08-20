package gg.duo.post.domain.post;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
public class Post {

    public enum Status { RECRUITING, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 작성자 — users.id.
     *
     * 예전에는 @ManyToOne User author 였다. user 가 별도 서비스가 되면서
     * 이 연관을 유지할 수 없다. JPA 연관은 "같은 DB, 같은 트랜잭션"을 전제로
     * 조인을 만드는데, users 는 이제 post 가 볼 수 없는 스키마에 있다.
     *
     * 컬럼 이름(author_id)은 그대로다 — 기존 데이터가 그대로 살아 있다.
     * 바뀌는 것은 "post 가 users 를 조인할 수 있는가"뿐이다.
     * 작성자 정보가 필요하면 UserClient 로 묶음 조회한다.
     */
    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    // 모집 조건
    private String game;        // 어떤 게임
    private String gameMode;    // 게임 모드 (일반/랭크/칼바람 등)
    private String playTime;    // 같이 할 시간 (자유 입력, 예: "오늘 21시")
    private boolean micRequired;
    private String positions;   // 찾는 포지션, 콤마 구분 (예: "정글,서폿")

    @Column(nullable = false)
    private int targetMembers = 2; // 희망 파티원 수 (방장 포함)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.RECRUITING;

    /**
     * 이 글의 파티 채팅방 번호. chat 서비스가 방을 만들면
     * ChatRoomCreatedEvent 로 알려주고 여기에 복제해 둔다.
     *
     * 왜 복제하는가: 목록 화면 하나가 글 20개를 그린다. 이 값이 없으면
     * "이 글의 방 번호"를 알기 위해 chat 서비스로 HTTP 호출이 20번 나간다.
     * 방 번호는 한 번 정해지면 바뀌지 않으므로 복제해도 어긋날 일이 없다.
     */
    @Column(name = "chat_room_id")
    private Long chatRoomId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
