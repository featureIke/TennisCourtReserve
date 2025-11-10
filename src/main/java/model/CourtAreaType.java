package model;

/// コートエリア（施設）を表すEnum
public enum CourtAreaType {
    CHIGASAKI_PARK("茅ヶ崎公園", 1),
    SERIZAWA_SPORTS_PARK("芹沢スポーツ広場", 4),
    TSUTSUMI_SPORTS_PARK("堤スポーツ広場", 4),
    YANAGISHIMA_SHIOSAI_PARK("柳島しおさい公園", 2),
    YANAGISHIMA_SPORTS_PARK("柳島スポーツ公園", 3),
    OTHER("その他", 5); // 見つからない場合

    /// キーワード（施設名）
    private final String keyword;

    /// 優先順位（小さいほど優先）
    private final int priority;

    /// コンストラクタ
    CourtAreaType(String keyword, int priority) {
        this.keyword = keyword;
        this.priority = priority;
    }

    /// キーワード取得
    public String getKeyword() {
        return keyword;
    }

    /// 優先順位取得
    public int getPriority() {
        return priority;
    }

    /// コート名からエリアタイプを推定する
    public static CourtAreaType fromCourtName(String courtName) {
        if (courtName.contains(CHIGASAKI_PARK.keyword)) {
            return CHIGASAKI_PARK;
        } else if (courtName.contains(SERIZAWA_SPORTS_PARK.keyword)) {
            return SERIZAWA_SPORTS_PARK;
        } else if (courtName.contains(TSUTSUMI_SPORTS_PARK.keyword)) {
            return TSUTSUMI_SPORTS_PARK;
        } else if (courtName.contains(YANAGISHIMA_SHIOSAI_PARK.keyword)) {
            return YANAGISHIMA_SHIOSAI_PARK;
        } else if (courtName.contains(YANAGISHIMA_SPORTS_PARK.keyword)) {
            return YANAGISHIMA_SPORTS_PARK;
        }
        return OTHER;
    }
}