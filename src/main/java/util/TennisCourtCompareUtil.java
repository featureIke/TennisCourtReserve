package util;

import model.TennisCourt;
import model.TimeSlot;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/// TennisCourt比較ユーティリティ
public class TennisCourtCompareUtil {

    /// 追加の予約かどうかを判定する
    /// 真なら追加の予約である
    public static boolean isAdditionalReservation(TennisCourt reserved, TennisCourt target) {
        if (reserved == null || target == null) {
            return false;
        }
        if (!isSameDate(reserved, target)) {
            return false;
        }
        if (isSameCourt(reserved, target)) {
            // 同じコートなら「重複または連続していればOK」
            return hasOverlappingTime(reserved, target) || isConsecutiveTime(reserved, target);
        }
        return false;
    }


    /// 予約済みコートとターゲットコートを比較し、除外対象かどうかを判定する
    public static boolean isExcludeTarget(TennisCourt reserved, TennisCourt target) {
        // nullチェック
        if (reserved == null || target == null) { return false;}
        // 日付が違ったら除外対象じゃない
        if (!isSameDate(reserved, target)) { return false;}

        if (isSameCourt(reserved, target)) {
            // 同じコートなら「重複または連続していればOK」
            // ここに合計利用時間制約を追加する
            // 利用開始時間が同じ場合、合計利用時間は１つ分として考える（コートを２面使っているので）
            // 利用開始時間が異なる場合、利用時間は２つ分の合計として考える。利用時間は共通で２時間と考える。
            // 合計利用時間は４時間までとする。要は２枠まで
            // 離れてる → 除外
            return !hasOverlappingTime(reserved, target) && !isConsecutiveTime(reserved, target); // 重複または連続 → OK
        } else {
            // 違う施設の場合
            if (hasOverlappingTime(reserved, target)) {
                return true; // 重複してたら除外
            }
            return !isConsecutiveTime(reserved, target); // 連続してればOK
// 重複も連続もしていない → 除外
        }
    }

    /// 2つのTennisCourtが「同じ日付・時間帯重複・異なるエリア」ならtrueを返す
    public static boolean isSameDateTimeDifferentArea(TennisCourt court1, TennisCourt court2) {
        if (court1 == null || court2 == null) {
            return false;
        }

        return isSameDate(court1, court2)
                && isDifferentArea(court1, court2)
                && hasOverlappingTime(court1, court2);
    }

    /// ２つのテニスコートが同じ日付であり、かつ、連続時間でない場合、trueを返す
    public static boolean isSameDateTimeAndTimeConsecutive(TennisCourt court1, TennisCourt court2) {
        if (court1 == null || court2 == null) {
            return false;
        }

        return isSameDate(court1, court2)
                && !isConsecutiveTime(court1,court2);
    }

    /// 日付が同じかチェック
    public static boolean isSameDate(TennisCourt court1, TennisCourt court2) {
        return Objects.equals(court1.getYmd(), court2.getYmd());
    }

    /// コート名の施設エリア部分が同じか（例：茅ヶ崎公園 vs 茅ヶ崎公園）
    public static boolean isSameCourt(TennisCourt court1, TennisCourt court2) {
        String area1 = extractAreaName(court1.getCourtName());
        String area2 = extractAreaName(court2.getCourtName());
        return area1.equals(area2);
    }

    /// エリア名が異なるかチェック
    private static boolean isDifferentArea(TennisCourt court1, TennisCourt court2) {
        String area1 = extractAreaName(court1.getCourtName());
        String area2 = extractAreaName(court2.getCourtName());
        return !area1.equals(area2);
    }

    /// コート同士で時間帯が重複しているかチェック
    public static boolean hasOverlappingTime(TennisCourt court1, TennisCourt court2) {
        for (TimeSlot slot1 : court1.getTimeSlotList()) {
            for (TimeSlot slot2 : court2.getTimeSlotList()) {
                if (isTimeOverlap(slot1.getTime(), slot2.getTime())) {
                    return true;
                }
            }
        }
        return false;
    }

    /// 2時間幅で時間帯重複を判定
    public static boolean isTimeOverlap(String timeStr1, String timeStr2) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

            LocalTime start1 = LocalTime.parse(timeStr1, formatter);
            LocalTime end1 = start1.plusHours(2);

            LocalTime start2 = LocalTime.parse(timeStr2, formatter);
            LocalTime end2 = start2.plusHours(2);

            // 重なっているかチェック
            return start1.isBefore(end2) && start2.isBefore(end1);

        } catch (Exception e) {
            System.out.println("[ERROR] 時間オーバーラップ判定中に例外発生: " + e.getMessage());
            return false;
        }
    }

    /// コート同士で時間帯が連続しているかチェック
    private static boolean isConsecutiveTime(TennisCourt court1, TennisCourt court2) {
        for (TimeSlot slot1 : court1.getTimeSlotList()) {
            for (TimeSlot slot2 : court2.getTimeSlotList()) {
                if (isTimeConsecutive(slot1.getTime(), slot2.getTime())) {
                    return true;
                }
            }
        }
        return false;
    }

    /// 2時間幅で「連続しているか（30分以内許容）」判定
    public static boolean isTimeConsecutive(String timeStr1, String timeStr2) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

            // 1個目の時間
            LocalTime start1 = LocalTime.parse(timeStr1, formatter);
            LocalTime end1 = start1.plusHours(2);

            // 2個目の時間
            LocalTime start2 = LocalTime.parse(timeStr2, formatter);

            // end1からstart2までの差分を計算
            long minutesBetween = java.time.Duration.between(end1, start2).toMinutes();

            // 0分以上、30分以内なら「連続している」とみなす
            return minutesBetween >= 0 && minutesBetween <= 30;

        } catch (Exception e) {
            System.out.println("[ERROR] 時間連続判定中に例外発生: " + e.getMessage());
            return false;
        }
    }

    /// コート名からエリア名だけを抽出する
    private static String extractAreaName(String courtName) {
        if (courtName.contains("茅ヶ崎公園")) {
            return "茅ヶ崎公園";
        } else if (courtName.contains("芹沢スポーツ広場")) {
            return "芹沢スポーツ広場";
        } else if (courtName.contains("堤スポーツ広場")) {
            return "堤スポーツ広場";
        } else if (courtName.contains("柳島しおさい公園")) {
            return "柳島しおさい公園";
        } else if (courtName.contains("柳島スポーツ公園")) {
            return "柳島スポーツ公園";
        }
        return courtName; // 見つからなければそのまま
    }
}