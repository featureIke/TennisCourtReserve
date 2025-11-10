package model;

/// 時間帯分類を表すEnum（細分化＆判定ロジック付き）
public enum TimeRange {
    EARLY_MORNING,
    MORNING,
    LATE_MORNING,
    EARLY_AFTERNOON,
    AFTERNOON,
    EVENING,
    NIGHT,
    LATE_NIGHT,
    UNKNOWN;

    /// 時間文字列（例："15:00"）からTimeRangeを判定
    public static TimeRange from(String timeStr) {
        try {
            int hour = Integer.parseInt(timeStr.split(":")[0]);
            if (hour >= 6 && hour < 8) {
                return EARLY_MORNING;
            } else if (hour >= 8 && hour < 10) {
                return MORNING;
            } else if (hour >= 10 && hour < 12) {
                return LATE_MORNING;
            } else if (hour >= 12 && hour < 15) {
                return EARLY_AFTERNOON;
            } else if (hour >= 15 && hour < 16) {
                return AFTERNOON;
            } else if (hour >= 16 && hour < 19) {
                return EVENING;
            } else if (hour >= 19 && hour < 21) {
                return NIGHT;
            } else if (hour >= 21 && hour < 22) {
                return LATE_NIGHT;
            }
        } catch (Exception e) {
            // ログ出力など必要であればここに
        }
        return UNKNOWN;
    }

    /**
     * Converts a code String to the corresponding TimeRange enum value.
     * @param code the time range code (e.g., "EAMO", "LAMO", etc.)
     * @return the corresponding TimeRange enum value
     * @throws IllegalArgumentException if the code is unknown
     */
    public static TimeRange fromCode(String code) {
        switch (code) {
            case "EAMO":
                return TimeRange.EARLY_MORNING;
            case "LAMO":
                return TimeRange.LATE_MORNING;
            case "MO":
                return TimeRange.MORNING;
            case "EAAF":
                return TimeRange.EARLY_AFTERNOON;
            case "AF":
                return TimeRange.AFTERNOON;
            case "EV":
                return TimeRange.EVENING;
            case "NI":
                return TimeRange.NIGHT;
            case "LANI":
                return TimeRange.LATE_NIGHT;
            default:
                throw new IllegalArgumentException("Unknown time range code: " + code);
        }
    }

    // 時間帯の厳密一致判定（互換扱いなし）
    public static boolean timeRangeMatches(TimeRange slotRange, TimeRange expected) {
        return slotRange != null && expected != null && slotRange == expected;
    }
}