package util;
import constpk.CommonConst;
import model.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IOUtil {
    /// 指定パスから除外日リストを取得する
    public static List<String> loadJogaiDates(String filePath) {
        List<String> jogaiDates = new ArrayList<>();
        int currentYear = LocalDate.now().getYear(); // 現在の年

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // カンマで分割
                String[] dates = line.split(",");
                for (String date : dates) {
                    date = date.trim();
                    if (date.length() == 8) {
                        jogaiDates.add(date); // 20250504みたいな8桁はそのまま
                    } else if (date.length() == 4) {
                        // 0504みたいな4桁は、今年を頭につける
                        jogaiDates.add(currentYear + date);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[ERROR] 除外日ファイル読み込みエラー: " + e.getMessage());
        }

        return jogaiDates;
    }


    /// ファイルパスを受け取り、TennisCourtリストを返す
    public static List<TennisCourt> loadReservedCourtsFromCsv(String filePath) {
        List<TennisCourt> courtList = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");

                if (parts.length != 3) {
                    // 期待通りじゃない行はスキップ
                    continue;
                }

                String courtAreaName = parts[0].trim();
                String ymd = parts[1].trim();
                String time = parts[2].trim();

                // コート名（例：「茅ヶ崎公園 1コート」みたいに仮設定）
                String courtName = courtAreaName + " 1コート";

                TennisCourt court = new TennisCourt(courtName);
                court.setYmd(ymd);

                TimeSlot slot = new TimeSlot();
                slot.setTime(time);
                slot.setTimeRange(TimeRange.from(time));
                slot.setAvailable(true); // 予約済みなので "空き" 扱い

                court.addTimeSlot(slot);

                courtList.add(court);
            }

        } catch (Exception e) {
            System.out.println("[ERROR] CSVファイル読み込み中に例外発生: " + e.getMessage());
            e.printStackTrace();
        }

        return courtList;
    }

    /// 指定パスから期待日程（ymd＋TimeRange＋CourtAreaType）のリストを取得する
    public static List<ExpectedYmdTimeRange> loadExpectedYmdAndTimeRanges(String filePath) throws IOException {
        List<ExpectedYmdTimeRange> expectedList = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] entries = line.split(",");
                for (String entry : entries) {
                    entry = entry.trim();
                    if (entry.isEmpty()) continue;

                    String[] parts = entry.split("/");
                    if (!(parts.length == 3 || parts.length == 4)) {
                        throw new IllegalArgumentException("[ERROR] 期待日程CSVの形式が不正です: entry='" + entry + "'");
                    }

                    String ymd = parts[0].trim();
                    String code = parts[1].trim();

                    TimeRange timeRange = TimeRange.fromCode(code);
                    if (timeRange == null) {
                        throw new IllegalArgumentException("[ERROR] 無効な時間帯コード: code='" + code + "' entry='" + entry + "'");
                    }

                    int maxCount = 100;
                    String areaRaw = parts[2].trim();
                    boolean isChokkinOk = false;
                    String areaPart = areaRaw;
                    String upper = areaRaw.toUpperCase();
                    if (upper.endsWith("-OK")) {
                        isChokkinOk = true;
                        areaPart = areaRaw.substring(0, areaRaw.length() - 3).trim();
                    } else if (upper.contains("-OK")) {
                        isChokkinOk = true;
                        areaPart = areaRaw.replaceAll("(?i)-OK", "").trim();
                    }
                    if (areaPart.endsWith(",")) {
                        areaPart = areaPart.substring(0, areaPart.length() - 1).trim();
                    }

                    if (parts.length == 4) {
                        String suffix = parts[3].trim();
                        if (suffix.endsWith(",")) {
                            suffix = suffix.substring(0, suffix.length() - 1).trim();
                        }
                        try {
                            maxCount = Integer.parseInt(suffix);
                        } catch (NumberFormatException ex) {
                            throw new IllegalArgumentException("[ERROR] 件数が数値ではありません: suffix='" + suffix + "' entry='" + entry + "'", ex);
                        }
                    }

                    ExpectedYmdTimeRange e = new ExpectedYmdTimeRange(ymd, timeRange, areaPart, maxCount, isChokkinOk);
                    expectedList.add(e);
                }
            }
        } catch (Exception e) {
            throw new IOException("[ERROR] expectDayファイル読み込みエラー: " + e.getMessage(), e);
        }

        return expectedList;
    }

    /// ログイン情報を読み込む
    public static Credential getCredential() {
        Credential account = null;

        try (FileReader reader = new FileReader(CommonConst.CREDENTIAL_FILE_NAME)) {
            // JSONをMap<String, List<Credential>>に変換
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, List<Credential>>>() {}.getType();
            Map<String, List<Credential>> credentialsMap = gson.fromJson(reader, type);

            // 対象サービスのアカウントを取得
            List<Credential> accounts = credentialsMap.getOrDefault(CommonConst.CREDENTIAL_HEAD, new ArrayList<>());
            if (!accounts.isEmpty()) {
                account = accounts.get(0);
            }

        } catch (Exception e) {
            System.out.println("[ERROR] 認証情報の読み込みに失敗しました: " + e.getMessage());
            e.printStackTrace();
        }

        return account;
    }

    /// ファイル名を受け取り、一行の文字列を取得する
    public static String readSingleLine(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line = reader.readLine();
            if (line != null) {
                return line.trim();
            }
        } catch (Exception e) {
            System.out.println("[ERROR] ファイル読み込みに失敗: " + e.getMessage());
        }
        return "";
    }
}
