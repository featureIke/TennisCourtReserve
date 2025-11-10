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

/// HTMLからTennisCourtリストを作成するユーティリティ
public class ParserUtil {

    /// HTMLと日付を受け取り、TennisCourtリストを作成する
    public static List<TennisCourt> parseTennisCourtsFromHtml(String html, String ymd) {
        List<TennisCourt> tennisCourtList = new ArrayList<>();

        try {
            // コメントアウトを削除してパース
            html = html.replaceAll("(?s)<!--.*?-->", "").trim();
            Document document = Jsoup.parse(html);

            // テーブル取得
            Element table = document.selectFirst("table.link-table");
            if (table == null) {
                System.out.println("テーブルが見つかりませんでした");
                return tennisCourtList;
            }

            Elements rows = table.select("tr");
            List<String> currentHeaders = new ArrayList<>();

            for (Element row : rows) {
                Elements cells = row.select("td, th");

                if (cells.isEmpty()) {
                    continue;
                }

                String firstCellText = cells.get(0).text().trim();

                // 時間帯ヘッダー行（"施設"）なら
                if (firstCellText.equals("施設")) {
                    currentHeaders.clear();
                    for (int i = 1; i < cells.size(); i++) {
                        currentHeaders.add(cells.get(i).text().trim());
                    }
                    continue;
                }

                // データ行
                String courtName = firstCellText;
                TennisCourt court = new TennisCourt(courtName);
                court.setYmd(ymd);

                for (int i = 1; i < cells.size() && (i - 1) < currentHeaders.size(); i++) {
                    String timeStr = currentHeaders.get(i - 1);
                    TimeRange timeRange = TimeRange.from(timeStr);

                    Element slotCell = cells.get(i);
                    boolean available = slotCell.selectFirst("a") != null || "○".equals(slotCell.text().trim());

                    TimeSlot timeSlot = new TimeSlot();
                    timeSlot.setTime(timeStr);
                    timeSlot.setTimeRange(timeRange);
                    timeSlot.setAvailable(available);

                    // 「○」かつリンクが存在するならURLをセット
                    Element link = slotCell.selectFirst("a");
                    if (link != null) {
                        String url = link.attr("href").trim();
                        if (!url.isEmpty()) {
                            // フルパスにしておきたければここでドメイン結合
                            timeSlot.setUrl("https://yoyaku.city.chigasaki.kanagawa.jp" + url);
                        }
                    }

                    court.addTimeSlot(timeSlot);
                }

                tennisCourtList.add(court);
            }

        } catch (Exception e) {
            System.out.println("[ERROR] コートパース中に例外発生: " + e.getMessage());
            e.printStackTrace();
        }

        return tennisCourtList;
    }
}