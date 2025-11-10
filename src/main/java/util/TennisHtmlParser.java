package util;

import model.TennisCourt;
import model.TimeSlot;
import model.TimeRange;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

/// テニスコート情報をHTMLからパースするユーティリティ
public class TennisHtmlParser {

    /// テニスコート一覧をHTML文字列からパースして返す
    public static List<TennisCourt> parseCourtTable(String html) {
        List<TennisCourt> courts = new ArrayList<>();
        try {
            // HTMLをパース
            Document doc = Jsoup.parse(html);

            // 全table要素を取得
            Elements tables = doc.select("table.link-table");

            // 各テーブルを処理
            for (Element table : tables) {

                // 時間帯のヘッダー（thタグの中）を取得
                List<String> timeHeaders = new ArrayList<>();
                Elements thElements = table.select("tr").first().select("th");
                for (int i = 1; i < thElements.size(); i++) {
                    timeHeaders.add(thElements.get(i).text());
                }

                // コート情報を取得
                Elements trElements = table.select("tr");
                for (int i = 1; i < trElements.size(); i++) {
                    Element tr = trElements.get(i);
                    Elements tdElements = tr.select("td");

                    // コート名（施設名＋コート番号）を取得
                    String courtName = tdElements.get(0).text();

                    // TennisCourtオブジェクトを作成
                    TennisCourt court = new TennisCourt(courtName);

                    // 各時間帯列をループしてパース
                    for (int j = 0; j < timeHeaders.size(); j++) {
                        // 時間文字列を取得
                        String timeStr = timeHeaders.get(j);

                        // 時間帯(TimeRange)を判定
                        TimeRange timeRange = TimeRange.from(timeStr);

                        // 各コートの空き状況セルを取得
                        Element slotCell = tdElements.get(j + 1);
                        boolean available = slotCell.selectFirst("a") != null || slotCell.text().equals("○");

                        // TimeSlotオブジェクトを作成
                        TimeSlot timeSlot = new TimeSlot();
                        timeSlot.setTime(timeStr);
                        timeSlot.setTimeRange(timeRange);
                        timeSlot.setAvailable(available);

                        // テニスコートに時間帯情報を追加
                        court.addTimeSlot(timeSlot);
                    }

                    // リストに追加
                    courts.add(court);
                }
            }
        } catch (Exception e) {
            // エラー処理（今は空でも可）
        }
        return courts;
    }
}