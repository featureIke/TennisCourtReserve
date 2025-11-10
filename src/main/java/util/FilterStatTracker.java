package util;

import log.LogWriter;
import model.TennisCourt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * フィルタ除外理由ごとの件数を集計・CSV出力するユーティリティ
 */
class FilterStatTracker {
    private static final Map<String, Integer> filterCounts = new HashMap<>();

    public static void count(String reason) {
        filterCounts.merge(reason, 1, Integer::sum);
    }

    public static void count(TennisCourt court, String reason, int amount) {
        String dow = DateUtil.getDayOfWeek(court).toString();
        filterCounts.merge(reason+dow, amount, Integer::sum);
    }

    public static void count(String reason, int amount) {
        filterCounts.merge(reason, amount, Integer::sum);
    }

    public static void exportToCsv() {
        Map<String, Integer> totalCounts = new HashMap<>();

        File file = new File("log/filter_stats.csv");

        // 既存ファイルを読み込んで加算
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                reader.readLine(); // ヘッダーをスキップ
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",", -1);
                    if (parts.length == 2) {
                        String reason = parts[0];
                        int count = Integer.parseInt(parts[1]);
                        totalCounts.put(reason, count);
                    }
                }
            } catch (IOException e) {
                LogWriter.write("[ERROR] 統計読み込み失敗: " + e.getMessage());
            }
        }

        // 今回のカウントを合算
        for (Map.Entry<String, Integer> entry : filterCounts.entrySet()) {
            totalCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }

        // 出力ディレクトリが無ければ作成
        file.getParentFile().mkdirs();

        // 上書き保存
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.println("理由,件数");
            totalCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry -> writer.printf("%s,%d%n", entry.getKey(), entry.getValue()));
        } catch (IOException e) {
            LogWriter.write("[ERROR] 統計出力失敗: " + e.getMessage());
        }
    }

    public static void clear() {
        filterCounts.clear();
    }
}