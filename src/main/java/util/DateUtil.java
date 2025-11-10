package util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import model.TennisCourt;

public class DateUtil {
    public static String extractDateFromUrl(String url) {
        try {
            int ymdIndex = url.indexOf("ymd=");
            if (ymdIndex != -1) {
                int start = ymdIndex + 4;
                int end = url.indexOf("&", start);
                if (end == -1) {
                    end = url.length();
                }
                String ymd = url.substring(start, end);

                if (ymd.length() == 8) {
                    int year = Integer.parseInt(ymd.substring(0, 4));
                    int month = Integer.parseInt(ymd.substring(4, 6));
                    int day = Integer.parseInt(ymd.substring(6, 8));
                    return year + "年" + month + "月" + day + "日";
                }
            }
        } catch (Exception e) {
            System.out.println("[ERROR] URLから日付抽出中に例外発生: " + e.getMessage());
        }
        return null;
    }

    public static String extractDateUrl(String url) {
        try {
            int ymdIndex = url.indexOf("ymd=");
            if (ymdIndex != -1) {
                int start = ymdIndex + 4;
                int end = url.indexOf("&", start);
                if (end == -1) {
                    end = url.length();
                }
                String ymd = url.substring(start, end);
                return ymd; // ここはそのまま
            }
        } catch (Exception e) {
            System.out.println("[ERROR] URLから日付抽出中に例外発生: " + e.getMessage());
        }
        return null;
    }

    /// 水曜日を取得する
    public static List<Integer> getWednesdayDays(int year, int month) {
        List<Integer> wednesdayDays = new ArrayList<>();

        // 月初と月末の日付を取得
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate firstDay = yearMonth.atDay(1);
        LocalDate lastDay = yearMonth.atEndOfMonth();

        // 水曜日をリストアップ
        for (LocalDate date = firstDay; !date.isAfter(lastDay); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.WEDNESDAY) {
                wednesdayDays.add(date.getDayOfMonth());
            }
        }

        return wednesdayDays;
    }

    public static String getJapaneseWeekday(String ymd) {
        LocalDate date = LocalDate.parse(ymd, DateTimeFormatter.ofPattern("yyyyMMdd"));
        DayOfWeek dow = date.getDayOfWeek();

        return switch (dow) {
            case MONDAY -> "月";
            case TUESDAY -> "火";
            case WEDNESDAY -> "水";
            case THURSDAY -> "木";
            case FRIDAY -> "金";
            case SATURDAY -> "土";
            case SUNDAY -> "日";
        };
    }

    /// テニスコート情報から曜日（DayOfWeek）を取得する
    public static DayOfWeek getDayOfWeek(TennisCourt court) {
        if (court == null || court.getYmd() == null) {
            return null;
        }
        LocalDate date = LocalDate.parse(court.getYmd(), DateTimeFormatter.ofPattern("yyyyMMdd"));
        return date.getDayOfWeek();
    }

    /**
     * 指定された年と月の土日の日付を計算するヘルパーメソッド。
     *
     * @param year  年
     * @param month 月
     * @return 土日の日付リスト
     */
    public static List<Integer> getWeekendDays(int year, int month) {
        List<Integer> weekendDays = new ArrayList<>();

        // 月初と月末の日付を取得
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate firstDay = yearMonth.atDay(1);
        LocalDate lastDay = yearMonth.atEndOfMonth();

        // 土日をリストアップ
        for (LocalDate date = firstDay; !date.isAfter(lastDay); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                weekendDays.add(date.getDayOfMonth());
            }
        }

        return weekendDays;
    }

    /// u_hyojiym=の年月を1ヶ月前にしたURLを返す
    public static String shiftHyojiymOneMonthBack(String url) {
        try {
            // u_hyojiym=の値を抽出
            String currentYm = url.split("u_hyojiym=")[1].substring(0, 6);
            int year = Integer.parseInt(currentYm.substring(0, 4));
            int month = Integer.parseInt(currentYm.substring(4, 6));

            // LocalDateで1ヶ月前にする
            LocalDate ymDate = LocalDate.of(year, month, 1).minusMonths(1);
            String newYm = String.format("%04d%02d", ymDate.getYear(), ymDate.getMonthValue());

            // 置き換えて返す
            return url.replaceFirst("u_hyojiym=\\d{6}", "u_hyojiym=" + newYm);
        } catch (Exception e) {
            return url; // 万一エラーがあった場合は元のURLを返す
        }
    }

    /**
     * 指定された年・月において、指定された曜日リストに一致する日（1〜31）を返す
     * @param year 年（例：2025）
     * @param month 月（1〜12）
     * @param weekdays 対象の曜日リスト
     * @return 条件に一致する日付のリスト
     */
    public static List<Integer> getDaysOfWeek(int year, int month, List<DayOfWeek> weekdays) {
        List<Integer> matchedDays = new ArrayList<>();
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate date = yearMonth.atDay(1);
        while (date.getMonthValue() == month) {
            if (weekdays.contains(date.getDayOfWeek())) {
                matchedDays.add(date.getDayOfMonth());
            }
            date = date.plusDays(1);
        }
        return matchedDays;
    }
}
