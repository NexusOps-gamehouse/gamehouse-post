package gg.duo.post.domain.requirement;

import gg.duo.common.constant.GameCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 게임별로 고를 수 있는 값의 목록.
 *
 * 왜 서버가 이걸 갖고 있어야 하는가: FR-02 는 "모든 선택 항목은 추천 필터에
 * 실제로 반영되는 값이어야 한다"고 못 박는다. 프론트만 목록을 갖고 있으면
 * API 를 직접 호출해 발로란트 글에 '정글'을 넣을 수 있고, 그 글은 조용히
 * 어떤 추천에도 걸리지 않는 글이 된다. (에러가 아니라 '결과 0건'으로 나타나
 * 원인을 찾기가 가장 어려운 종류의 버그다)
 *
 * 목록이 프론트에도 있는 건 중복이지만 역할이 다르다. 한쪽은 화면을 그리기
 * 위한 것이고 한쪽은 저장을 막기 위한 것이다. 어긋나면 저장 시점에 400 으로
 * 즉시 드러난다 — 조용히 틀리지 않는다.
 *
 * 순서를 지키려고 List 로 둔다. Set 은 순서가 없어서 화면 목록으로 그대로
 * 쓰면 티어가 뒤죽박죽 나온다.
 */
public final class GameOptions {

    private GameOptions() {}

    /** 포지션(LOL) · 역할(VALORANT) — 화면 라벨은 다르지만 저장되는 칸은 같다. */
    private static final Map<GameCode, List<String>> ROLES = Map.of(
            GameCode.LOL,      List.of("탑", "정글", "미드", "원딜", "서폿"),
            GameCode.VALORANT, List.of("타격대", "척후대", "전략가", "감시자"));

    private static final Map<GameCode, List<String>> TIERS = Map.of(
            GameCode.LOL,      List.of("아이언", "브론즈", "실버", "골드", "플래티넘",
                                       "에메랄드", "다이아몬드", "마스터 이상"),
            GameCode.VALORANT, List.of("아이언", "브론즈", "실버", "골드", "플래티넘",
                                       "다이아몬드", "초월", "불멸", "레디언트"));

    private static final Map<GameCode, List<String>> MODES = Map.of(
            GameCode.LOL,      List.of("신속", "랭크", "칼바람"),
            GameCode.VALORANT, List.of("일반", "경쟁전", "데스매치", "기타"));

    /** 플레이스타일은 게임과 무관하게 같다. 사용자 프로필(users.play_style)과 같은 값이어야 한다. */
    private static final List<String> PLAY_STYLES = List.of("빡겜", "즐겜");

    /**
     * 게임을 가리키는 여러 표기를 코드 하나로 모은다.
     *
     * [왜 필요한가] posts.game 은 FR-02 에서 표시명("리그오브레전드")에서 코드("LOL")로
     * 바뀌었다. 그런데 목록 검색을 부르는 쪽이 하나가 아니다. match 서비스는 프론트에서
     * 받은 한글 표시명을 그대로 GET /api/posts 의 game 파라미터로 넘긴다
     * (match/client/PostClient). 저장 값만 바꾸고 조회를 그대로 두면, 그쪽 호출은
     * 에러가 아니라 "결과 0건"으로 돌아온다 — 스마트 매칭이 아무것도 못 찾는데
     * 어디가 잘못됐는지는 아무 데도 안 남는다.
     *
     * 그래서 저장은 코드로 하되, 조회는 옛 표기도 알아듣게 한다. 부르는 쪽을 전부
     * 고치는 것보다 받는 쪽 한 곳이 흡수하는 편이 안전하다.
     */
    private static final Map<String, GameCode> ALIASES = Map.ofEntries(
            Map.entry("LOL", GameCode.LOL),
            Map.entry("리그오브레전드", GameCode.LOL),
            Map.entry("리그 오브 레전드", GameCode.LOL),
            Map.entry("롤", GameCode.LOL),
            Map.entry("LEAGUEOFLEGENDS", GameCode.LOL),
            Map.entry("VALORANT", GameCode.VALORANT),
            Map.entry("발로란트", GameCode.VALORANT),
            Map.entry("발로", GameCode.VALORANT));

    /**
     * 저장용 — 어떤 표기로 와도 코드로 바꾼다. 못 알아보면 예외.
     *
     * 조회와 달리 여기서는 조용히 넘어가면 안 된다. 잘못된 게임으로 저장된 글은
     * 어떤 검색에도 걸리지 않는 채로 DB 에 남는다.
     */
    public static GameCode parse(String raw) {
        if (raw == null || raw.isBlank())
            throw new IllegalArgumentException("게임을 선택해주세요.");
        GameCode found = ALIASES.get(raw.trim().toUpperCase(Locale.ROOT));
        if (found == null) found = ALIASES.get(raw.trim());
        if (found == null)
            throw new IllegalArgumentException("지원하지 않는 게임입니다: " + raw);
        return found;
    }

    /**
     * 조회 필터용 — 아는 표기는 코드로, 빈 값은 null(필터 없음)로 바꾼다.
     *
     * 모르는 값은 예외를 던지지 않고 그대로 돌려준다. 목록 조회는 사용자가 URL 을
     * 직접 만질 수 있는 자리라, 오타 하나로 500 이 나는 것보다 "결과 0건"이 낫다.
     * (저장 경로는 위 parse 가 막는다)
     */
    public static String normalizeFilter(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        GameCode found = ALIASES.get(trimmed.toUpperCase(Locale.ROOT));
        if (found == null) found = ALIASES.get(trimmed);
        return found == null ? trimmed : found.name();
    }

    public static List<String> roles(GameCode game) { return ROLES.getOrDefault(game, List.of()); }

    public static List<String> tiers(GameCode game) { return TIERS.getOrDefault(game, List.of()); }

    public static List<String> modes(GameCode game) { return MODES.getOrDefault(game, List.of()); }

    public static List<String> playStyles() { return PLAY_STYLES; }

    /**
     * 값 하나를 검증한다. null·빈 값은 "상관없음"이라 통과시킨다.
     * 조건을 걸지 않은 것과 잘못된 조건을 건 것은 다른 일이다.
     */
    public static void requireIn(String value, List<String> allowed, String fieldLabel) {
        if (value == null || value.isBlank()) return;
        if (!allowed.contains(value))
            throw new IllegalArgumentException(fieldLabel + " 값이 올바르지 않습니다: " + value);
    }

    /** 콤마 구분 값 전체를 검증한다. */
    public static void requireAllIn(String csv, List<String> allowed, String fieldLabel) {
        if (csv == null || csv.isBlank()) return;
        for (String v : csv.split(",")) requireIn(v.trim(), allowed, fieldLabel);
    }

    /** 화면이 그대로 그릴 수 있는 전체 목록. 프론트가 하드코딩 대신 이걸 받아 갈 수도 있다. */
    public static Map<String, Object> catalog(GameCode game) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("game", game.name());
        m.put("displayName", game.displayName());
        m.put("roles", roles(game));
        m.put("tiers", tiers(game));
        m.put("modes", modes(game));
        m.put("playStyles", playStyles());
        return m;
    }

    public static List<Map<String, Object>> catalogAll() {
        return java.util.Arrays.stream(GameCode.values()).map(GameOptions::catalog).toList();
    }
}
