package util;

import java.time.LocalDate;

/// 支払期限計算ユーティリティ
public class PaymentDeadlineUtil {

    /**
     * 予約処理日と予約対象日から支払期限を計算する
     *
     * @param reservationDate 予約処理日
     * @param targetDate 予約対象日
     * @return 支払期限日（支払が不要な場合は null）
     */
    public static LocalDate calculatePaymentDeadline(LocalDate reservationDate, LocalDate targetDate) {
        // 予約対象日が予約日から5日以内なら支払い不要（当日払いOK）
        if (!reservationDate.plusDays(4).isBefore(targetDate)) {
            return null; // 支払い不要
        }

        // 事前支払いが必要 → 予約対象日の6日前が支払期限
        return targetDate.minusDays(6);
    }
}
