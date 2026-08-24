package gg.duo.post.domain.post;

import gg.duo.common.constant.VoiceChat;
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

    // 모집 조건 — 게임과 무관하거나, 목록 화면이 조인 없이 써야 하는 값들.
    //   게임마다 선택지가 갈리는 조건(포지션/역할·티어·플레이스타일)은
    //   PostGameRequirement 로 나갔다.

    /**
     * 어떤 게임 — GameCode 이름("LOL" / "VALORANT")을 담는다.
     *
     * [FR-02] 예전에는 "리그오브레전드" 같은 한글 표시명이 그대로 들어갔다.
     * 표시명은 언제든 바뀌고(줄임말, 띄어쓰기), 바뀌는 순간 이미 저장된 글들이
     * 필터에서 사라진다. 화면에 뭐라고 쓸지는 프론트가 정한다.
     *
     * enum 타입으로 두지 않은 건 목록 필터 쿼리가 이 컬럼을 문자열로 비교하기
     * 때문이다. 유효성은 저장 시점에 GameCode.valueOf 로 검사한다.
     */
    private String game;

    /** 게임 모드. 어떤 값이 유효한지는 게임마다 다르다 — GameOptions 참고. */
    private String gameMode;

    private String playTime;    // 같이 할 시간 (자유 입력, 예: "오늘 21시")

    /**
     * 음성채팅 정도. 예전의 mic_required(boolean)를 대체한다.
     * boolean 은 "마이크는 있는데 말은 별로 안 하고 싶다"를 표현하지 못했다.
     */
    /*
     * DDL 상으로는 nullable 이다. ddl-auto: update 로 NOT NULL 컬럼을 추가하면
     * 이미 행이 있는 테이블에서 실패한다 — 마이그레이션 전에 앱이 먼저 뜨는
     * 순서가 실제로 발생한다. 값을 항상 채우는 책임은 자바 쪽(기본값 ANY)이
     * 지고, DB 의 NOT NULL 은 백필이 끝난 뒤 V4 가 건다.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private VoiceChat voiceChat = VoiceChat.ANY;

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
