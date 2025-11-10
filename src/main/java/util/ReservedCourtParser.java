package util;

import model.TennisCourt;
import model.TimeRange;
import model.TimeSlot;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.ArrayList;
import java.util.List;

/// 予約済みTennisCourtリストを作成するユーティリティ
public class ReservedCourtParser {

    public static List<TennisCourt> parseReservedTennisCourtsFromHtml(String html) {
        List<TennisCourt> reservedCourtList = new ArrayList<>();

        try {
            Document document = Jsoup.parse(html);
            Element table = document.selectFirst("table");

            if (table == null) {
                System.out.println("テーブルが見つかりませんでした");
                return reservedCourtList;
            }

            Elements rows = table.select("tr");

            for (int i = 1; i < rows.size(); i++) { // ヘッダーはスキップ
                Element row = rows.get(i);
                Elements cells = row.select("td");

                if (cells.size() < 2) {
                    continue;
                }

                // 利用日時
                String dateTimeText = cells.get(0).text().trim();
                String ymd = extractYmdFromCell(cells.get(0));
                String timeStr = extractTimeFromDateTime(dateTimeText);

                // 施設名
                String courtName = cells.get(1).text().trim();

                // TimeRangeを取得
                TimeRange timeRange = TimeRange.from(timeStr);

                // コートオブジェクト
                TennisCourt court = new TennisCourt(courtName);
                court.setYmd(ymd); // ★日付セット

                // タイムスロット作成（予約済み＝Availableはtrue）
                TimeSlot slot = new TimeSlot();
                slot.setTime(timeStr);
                slot.setTimeRange(timeRange);
                slot.setAvailable(true);

                court.addTimeSlot(slot);

                reservedCourtList.add(court);
            }

        } catch (Exception e) {
            System.out.println("[ERROR] 予約コートパース中に例外発生: " + e.getMessage());
            e.printStackTrace();
        }

        return reservedCourtList;
    }

    private static String extractYmdFromCell(Element cell) {
        try {
            Element link = cell.selectFirst("a");
            if (link != null) {
                String href = link.attr("href");
                int ymdIndex = href.indexOf("ymd=");
                if (ymdIndex != -1) {
                    int start = ymdIndex + 4;
                    int end = href.indexOf("&", start);
                    if (end == -1) end = href.length();
                    return href.substring(start, end);
                }
            }
        } catch (Exception e) {
            System.out.println("[ERROR] 日付抽出失敗: " + e.getMessage());
        }
        return null;
    }

    private static String extractTimeFromDateTime(String dateTimeText) {
        try {
            int start = dateTimeText.indexOf(' ') + 1;
            int end = dateTimeText.indexOf('〜');
            if (start > 0 && end > start) {
                return dateTimeText.substring(start, end).trim();
            }
        } catch (Exception e) {
            System.out.println("[ERROR] 時間抽出失敗: " + e.getMessage());
        }
        return null;
    }
}