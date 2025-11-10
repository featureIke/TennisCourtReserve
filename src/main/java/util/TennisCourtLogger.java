package util;

import model.TennisCourt;
import model.TimeSlot;

/// TennisCourtリストをきれいに出力するユーティリティクラス
public class TennisCourtLogger {

    /// List<TennisCourt>をインデント付きで出力する
    public static void printTennisCourts(java.util.List<TennisCourt> courtList) {
//        System.out.println("▶▶▶最終的に予約処理を実施するコート一覧");
        if (courtList.isEmpty()){
            System.out.println("なし");
        }
        for (TennisCourt court : courtList) {
            // コート名・日付を出力
            System.out.println("コート名: " + court.getCourtName());

            // ymd（日付情報）がある場合は出力
            if (court.getYmd() != null) {
                System.out.println("    日付: " + formatYmd(court.getYmd()));
            }else {
                System.out.println("    日付情報が空です。なんでやねん");
            }

            // 時間帯リストを出力
            for (TimeSlot slot : court.getTimeSlotList()) {
                String availableStr = slot.isAvailable() ? "○" : "×";

                // 出力を揃える
                System.out.printf("    時間: %-5s | 時間帯: %-13s | 空き: %s%n",
                        slot.getTime(),         // 時間（5文字幅、左寄せ）
                        slot.getTimeRange(),    // 時間帯（13文字幅、左寄せ）
                        availableStr);          // 空き
                if (slot.getUrl() != null){
                    System.out.println(slot.getUrl());
                }
            }
        }
    }

    /// ymd文字列を「YYYY年MM月DD日」形式に整形する
    private static String formatYmd(String ymd) {
        if (ymd.length() == 8) {
            String year = ymd.substring(0, 4);
            String month = ymd.substring(4, 6);
            String day = ymd.substring(6, 8);
            return year + "年" + month + "月" + day + "日";
        }
        return ymd;
    }
}