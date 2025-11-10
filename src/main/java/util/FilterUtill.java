package util;


import model.*;

import java.awt.geom.FlatteningPathIterator;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import log.LogWriter;
import static util.DateUtil.extractDateFromUrl;
import static util.DateUtil.extractDateUrl;

public class FilterUtill {

    /// URLが除外対象かを判定する
    public static boolean isTargetUrl(String url, List<String> jogaiDates) {
        String dateStr = extractDateUrl(url);
        if (dateStr == null) {
            return false; // 日付が取れない場合は除外する
        }
        return !jogaiDates.contains(dateStr); // リストに含まれていなければOK
    }

    /// コートリストから「空きあり（○）」のみを残す
    private static List<TennisCourt> filterAvailableCourts(List<TennisCourt> courtList) {
        int startListCount = courtList.size();
        List<TennisCourt> availableCourts = new ArrayList<>();

        for (TennisCourt court : courtList) {
            List<TimeSlot> availableSlots = new ArrayList<>();
            boolean hasUnavailable = false;

            for (TimeSlot slot : court.getTimeSlotList()) {
                if (slot.isAvailable()) {
                    availableSlots.add(slot);
                } else {
                    hasUnavailable = true;
                }
            }

            if (!availableSlots.isEmpty()) {
                TennisCourt filteredCourt = new TennisCourt(court.getCourtName());
                filteredCourt.setYmd(court.getYmd());
                filteredCourt.setTimeSlotList(availableSlots);
                availableCourts.add(filteredCourt);
            }
        }


        int excludedCount = startListCount - availableCourts.size();
        if (excludedCount > 0) {
            FilterStatTracker.count("空きがないため", excludedCount);
        }

        return availableCourts;
    }

    /// 今日基準で、事前支払い猶予が2日未満なら除外するかどうか
    private static boolean shouldExcludeDueToPrepaymentDeadline(LocalDate today, LocalDate targetDate) {
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(today, targetDate);

        // 5日以内なら当日支払いOKなので除外しない
        if (daysBetween <= 5) {
            return false;
        }

        // それより先なら、事前支払い必要
        LocalDate paymentDeadline = targetDate.minusDays(6);

        long daysUntilPaymentDeadline = java.time.temporal.ChronoUnit.DAYS.between(today, paymentDeadline);

        // 2日未満しか猶予がないなら除外する
        return daysUntilPaymentDeadline < 4;
    }

    /// 予約対象日が今日から2日以内であるかどうか（2日以内の場合は予約対象外とする）
    public static boolean isLessThanSpecifiedExtensionDate(LocalDate today, LocalDate targetDate) {
        if (today == null || targetDate == null) {
            return false;
        }
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(today, targetDate);
        // 今日(0日後)・1日後・2日後を「2日以内」とみなす。過去日は除外（false）。
        return daysBetween >= 0 && daysBetween <= 2;
    }

    /// 希望の日時とコート名のキーワードに一致するコートがあれば抽出する
    public static List<TennisCourt> getExpectedCourt(List<TennisCourt> tennisCourtList, ExpectedYmdTimeRange expectedData,String ymd, TimeRange timeRange, String courtNameKeyword, int maxCount,List<TennisCourt> yykCourtList) {
        List<TennisCourt> extractedCourtList = new ArrayList<>();

        // 予約不可コートを除外
        tennisCourtList = FilterUtill.filterAvailableCourts(tennisCourtList);

        // courtNameKeyword が空または null の場合はスキップ（理由をコメント）
        if (courtNameKeyword == null || courtNameKeyword.isBlank()) {
            LogWriter.write("[除外理由] コート名キーワード未指定のため抽出処理をスキップ");
            return extractedCourtList;
        }
        // ymd が空または null の場合はスキップ（理由をコメント）
        if (ymd == null || ymd.isBlank()) {
            LogWriter.write("[除外理由] 希望日(ymd)未指定のため抽出処理をスキップ");
            return extractedCourtList;
        }

        boolean chokkinOk = expectedData.isChokkinOk();
        if (!chokkinOk) {
            LocalDate today = LocalDate.now();
            tennisCourtList.removeIf(court -> {
                String ymdStr = court.getYmd(); // YYYYMMDD
                try {
                    if (ymdStr == null || ymdStr.length() != 8) {
                        LogWriter.write("[WARN] ymd形式不正のため対象外扱いにしない: court=" + court.getCourtName() + ", ymd=" + ymdStr);
                        return false; // 不正値は除外対象にしない
                    }
                    LocalDate ymdDate = LocalDate.parse(ymdStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
                    return FilterUtill.isLessThanSpecifiedExtensionDate(today, ymdDate);
                } catch (Exception e) {
                    LogWriter.write("[ERROR] ymd日付パースに失敗: court=" + court.getCourtName() + ", ymd=" + ymdStr + ", err=" + e);
                    return false; // パース失敗時は念のため残す
                }
            });
        }


        // YMD・時間帯・コート名キーワードでフィルター
        for (TennisCourt court : tennisCourtList) {
            // 希望日と不一致
            if (!ymd.equals(court.getYmd())) {
//                LogWriter.writeSlotExclusionReason("希望日と不一致", court, new TimeSlot());
                continue;
            }
            // コートエリアタイプで比較（Enum基準）。期待エリアと実コートの推定エリアが一致しない場合は除外
            CourtAreaType expectedArea = resolveExpectedArea(courtNameKeyword);
            CourtAreaType actualArea   = CourtAreaType.fromCourtName(court.getCourtName());
            if (expectedArea != actualArea) {
//                LogWriter.writeSlotExclusionReason(
//                        "[除外理由] コートエリア不一致: expected=" + expectedArea + " / actual=" + actualArea,
//                        court,
//                        new TimeSlot()
//                );
                continue;
            }

            List<TimeSlot> matchingSlots = new ArrayList<>();
            for (TimeSlot slot : court.getTimeSlotList()) {
// 時間帯処理: 取得スロットのTimeRangeが希望と厳密一致するものだけ残す
                    if (TimeRange.timeRangeMatches(slot.getTimeRange(), timeRange)) {
                        matchingSlots.add(slot);
                    }
            }
            if (!matchingSlots.isEmpty()) {
                // 既存予約数をカウント（同じ日付・同じコート・同じ時間帯）
                int existingCount = 0;
// 件数処理: 同一時間帯のみ既存件数としてカウント
                for (TennisCourt yyk : yykCourtList) {
                    if (!ymd.equals(yyk.getYmd())) continue;
                    if (!TennisCourtCompareUtil.isSameCourt(yyk, court)) continue;
                    for (TimeSlot ys : yyk.getTimeSlotList()) {
                        if (ys.getTimeRange() == timeRange) {
                            existingCount++;
                        }
                    }
                }
                int remain = Math.max(0, maxCount - existingCount);

                if (remain <= 0) {
                    // 既存予約で上限に達しているため除外
                    for (TimeSlot slot : matchingSlots) {
                        LogWriter.writeSlotExclusionReason("[除外理由] 既存予約によりmaxCount超過（追加0件）", court, slot);
                    }
                } else {
                    // 追加可能分だけ詰め替えて追加（通常は1件想定）
                    List<TimeSlot> toAdd = matchingSlots.size() > remain ? matchingSlots.subList(0, remain) : matchingSlots;
                    TennisCourt filteredCourt = new TennisCourt(court.getCourtName());
                    filteredCourt.setYmd(court.getYmd());
                    filteredCourt.setTimeSlotList(new ArrayList<>(toAdd));
                    extractedCourtList.add(filteredCourt);

                    // もし余剰があるならログ（任意）
                    if (matchingSlots.size() > remain) {
                        for (int i = remain; i < matchingSlots.size(); i++) {
                            LogWriter.writeSlotExclusionReason("[除外理由] maxCount制限により追加見送り", court, matchingSlots.get(i));
                        }
                    }
                }
            } else {
                // 指定時間帯が存在しない場合の除外理由をコメント
//                LogWriter.writeSlotExclusionReason("[除外理由] 指定時間帯(" + timeRange + ")が存在しない", court, new TimeSlot());
            }
        }

        return extractedCourtList;
    }


    // CourtAreaType をキーワード（日本語）または enum 名から解決する
    private static CourtAreaType resolveExpectedArea(String keywordOrEnum) {
        if (keywordOrEnum == null || keywordOrEnum.isBlank()) {
            return CourtAreaType.OTHER;
        }
        // 1) enum 名での一致（例: YANAGISHIMA_SPORTS_PARK）
        try {
            return CourtAreaType.valueOf(keywordOrEnum.trim());
        } catch (IllegalArgumentException ex) {
            // 2) 日本語キーワードでの一致（たとえば「柳島スポーツ公園」）
            for (CourtAreaType t : CourtAreaType.values()) {
                String kw = t.getKeyword();
                if (kw != null && !kw.isEmpty() && keywordOrEnum.contains(kw)) {
                    return t;
                }
            }
        }
        return CourtAreaType.OTHER;
    }
}
    //    /// 取得コートをフィルタリングする(平日)
//    public static List<TennisCourt> filterWeekDayCourt(List<TennisCourt> courtList,List<TennisCourt> yykCourtList){
//        LogWriter.write("⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️");
//        LocalDate date = LocalDate.parse(courtList.get(0).getYmd(), DateTimeFormatter.ofPattern("yyyyMMdd"));
//        LogWriter.write(date +"："+DateUtil.getJapaneseWeekday(courtList.get(0).getYmd()));
//
//        // フィルター前のコート数をログ出力
//        LogWriter.write("[フィルタ開始前] コート数: " + courtList.size());
//
//        // 予約不可コートを除外
//        courtList = FilterUtill.filterAvailableCourts(courtList);
//        LogWriter.write("[予約不可コート除外後] コート数: " + courtList.size());
//
//        // コート２と３を除外
////        courtList = filterCourtNameContainsTwoOrThree(courtList);
//
//        // ５日より先の予約の場合（事前支払いの場合）、支払い猶予が２日以内のものは除外
////        courtList = FilterUtill.filterCourtsByPrepaymentRule(courtList);
////        LogWriter.write("[支払期限短過ぎをフィルタ後] コート数: " + courtList.size());
//
//        // ５日以内の場合（現地支払の場合）、茅ヶ崎公園以外を除外
//        // ただし、既存の予約に重複・連続する場合、いずれのコートでも許容する
//        courtList = FilterUtill.filterExcludeByDateAndArea(courtList,yykCourtList,List.of());
//        LogWriter.write("[現地支払（５日前予約）フィルタ後] コート数: " + courtList.size());
//        // 指定時間帯を除外(詳細は内側記載)
//        courtList = FilterUtill.filterByTimeRangesPerMonth(courtList);
//        LogWriter.write("[指定時間帯フィルタ後] コート数: " + courtList.size());
//
//        // 指定を除外
//        courtList = FilterUtill.filterByExcludeCourtAreas(
//                courtList,
//                List.of(CourtAreaType.SERIZAWA_SPORTS_PARK,
//                        CourtAreaType.TSUTSUMI_SPORTS_PARK,
//                        CourtAreaType.YANAGISHIMA_SPORTS_PARK,
//                        CourtAreaType.YANAGISHIMA_SHIOSAI_PARK
//                ),
//                List.of());
//
//
//        //バッティングによるフィルター
//        courtList = filterBySchedulingConflict(courtList,yykCourtList);
//        LogWriter.write("[バッティングフィルター後] コート数: " + courtList.size());
//
//        //４時間を超える予約を除外
//        courtList = filterByMaxHoursPerDay(courtList,yykCourtList);
//        // フィルター後のコート数をログ出力
////        LogWriter.write("[フィルター後] コート数: " + courtList.size());
//        FilterStatTracker.exportToCsv();
//        FilterStatTracker.clear();
//        return courtList;
//    }

    /// 取得コートをフィルタリングする
//    public static List<TennisCourt> filterCourt(List<TennisCourt> courtList,List<TennisCourt> yykCourtList){
//        LogWriter.write("⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️⭐️");
//        LocalDate date = LocalDate.parse(courtList.get(0).getYmd(), DateTimeFormatter.ofPattern("yyyyMMdd"));
//        LogWriter.write(date +"："+DateUtil.getJapaneseWeekday(courtList.get(0).getYmd()));
//
//        // フィルター前のコート数をログ出力
//        LogWriter.write("[フィルタ開始前] コート数: " + courtList.size());
//
//        // 予約不可コートを除外
//        courtList = FilterUtill.filterAvailableCourts(courtList);
//        LogWriter.write("[予約不可コート除外後] コート数: " + courtList.size());
//
//        // 指定時間帯を除外(詳細は内側記載)
//        courtList = FilterUtill.filterByTimeRangesPerMonth(courtList);
//        LogWriter.write("[指定時間帯フィルタ後] コート数: " + courtList.size());
//
//        // ５日より先の予約の場合（事前支払いの場合）、支払い猶予が２日以内のものは除外
//        courtList = FilterUtill.filterCourtsByPrepaymentRule(courtList);
//        LogWriter.write("[支払期限短過ぎをフィルタ後] コート数: " + courtList.size());
//
//        // ５日以内の場合（現地支払の場合）、茅ヶ崎公園以外を除外
//        // ただし、既存の予約に重複・連続する場合、いずれのコートでも許容する
//        courtList = FilterUtill.filterExcludeByDateAndArea(courtList,yykCourtList,List.of(CourtAreaType.CHIGASAKI_PARK,CourtAreaType.YANAGISHIMA_SPORTS_PARK));
//        LogWriter.write("[現地支払（５日前予約）フィルタ後] コート数: " + courtList.size());
//
//        //土日・水曜に分けてフィルター
//        courtList = filterByWednesAndWeekEnd(courtList,yykCourtList);
//
//        //バッティングによるフィルター
//        courtList = filterBySchedulingConflict(courtList,yykCourtList);
//        LogWriter.write("[バッティングフィルター後] コート数: " + courtList.size());
//
//        //４時間を超える予約を除外
//        courtList = filterByMaxHoursPerDay(courtList,yykCourtList);
//        // フィルター後のコート数をログ出力
////        LogWriter.write("[フィルター後] コート数: " + courtList.size());
//        FilterStatTracker.exportToCsv();
//        FilterStatTracker.clear();
//        return courtList;
//    }

    /// 有効な時間帯（TimeSlot）が1件以上あるコートのみを残す
//    public static List<TennisCourt> filterCourtsWithTimeSlot(List<TennisCourt> courtList) {
//        List<TennisCourt> result = new ArrayList<>();
//        for (TennisCourt court : courtList) {
//            if (court.getTimeSlotList() != null && !court.getTimeSlotList().isEmpty()) {
//                result.add(court);
//            }
//        }
//        return result;
//    }
//
//    /// バッティングによるフィルター
//    private static List<TennisCourt> filterBySchedulingConflict(List<TennisCourt> courtList, List<TennisCourt> yykCourtList) {
//        // 既存予約から日付ごとのエリアマップを作成
//        Map<String, Set<CourtAreaType>> existingAreaMap = new HashMap<>();
//        for (TennisCourt yykCourt : yykCourtList) {
//            String ymd = yykCourt.getYmd();
//            CourtAreaType area = CourtAreaType.fromCourtName(yykCourt.getCourtName());
//            if (area == null) {
//                LogWriter.write("[警告] 既存予約のコートエリアが不明: " + yykCourt.getCourtName());
//                continue;
//            }
//            existingAreaMap.computeIfAbsent(ymd, k -> new HashSet<>()).add(area);
//        }
//
//        // 除外対象コートを一意に集めるSet
//        Set<TennisCourt> removeSet = new HashSet<>();
//        // コート単位で重複・連続チェック
//        for (TennisCourt targetCourt : courtList) {
//            // 対象日に既存予約または同日追加候補がなければスキップ（全スロット許容）
//            boolean hasReservationOnThatDay =
//                    yykCourtList.stream().anyMatch(yyk -> yyk.getYmd().equals(targetCourt.getYmd())) ||
//                    courtList.stream()
//                        .filter(c -> c != targetCourt) // 自分自身を除外
//                        .anyMatch(c -> c.getYmd().equals(targetCourt.getYmd()));
//            if (!hasReservationOnThatDay) {
//                continue;
//            }
//            // 既存予約エリアと異なるエリアで同日予約しようとしていれば除外
//            String ymd = targetCourt.getYmd();
//            CourtAreaType targetArea = CourtAreaType.fromCourtName(targetCourt.getCourtName());
//            if (targetArea == null) {
//                LogWriter.write("[警告] コートエリアが特定できないためスキップ: " + targetCourt.getCourtName());
//                continue;
//            }
//            Set<CourtAreaType> reservedAreas = existingAreaMap.getOrDefault(ymd, Set.of());
//
//            if (!reservedAreas.isEmpty() && !reservedAreas.contains(targetArea)) {
//                LogWriter.write("       [除外理由] 同一日に複数のコートエリア予約: " + targetCourt.getCourtName() + "（" + ymd + "）");
//                FilterStatTracker.count("同一日に複数のコートエリア予約");
//                removeSet.add(targetCourt);
//                continue;
//            }
//
//            List<TimeSlot> extractedTimeSlotList = new ArrayList<>();
//            // 各スロットごとに重複・連続チェック
//            for (TimeSlot slot : targetCourt.getTimeSlotList()) {
//                boolean conflict = true; // 初期値をtrue: 除外前提
//                for (TennisCourt yykCourt : yykCourtList) {
//                    // 日付が異なればスキップ
//                    if (!yykCourt.getYmd().equals(targetCourt.getYmd())) continue;
//                    // コートが異なればスキップ
//                    if (!TennisCourtCompareUtil.isSameCourt(yykCourt, targetCourt)) continue;
//                    for (TimeSlot yykSlot : yykCourt.getTimeSlotList()) {
//                        if (TennisCourtCompareUtil.isTimeOverlap(yykSlot.getTime(), slot.getTime()) ||
//                                TennisCourtCompareUtil.isTimeConsecutive(yykSlot.getTime(), slot.getTime())) {
//                            conflict = false; // 重複または連続していればOK（許容）
//                            break;
//                        }
//                    }
//                    if (!conflict) break;
//                }
//                if (!conflict) {
//                    extractedTimeSlotList.add(slot);
//                }
//            }
//            targetCourt.setTimeSlotList(extractedTimeSlotList);
//            if (extractedTimeSlotList.isEmpty()) {
//                removeSet.add(targetCourt);
//            }
//        }
//        // courtListからremoveSetに含まれるコートをまとめて削除する
//        courtList.removeAll(removeSet);
//        // 除外理由付きでログ出力
//        for (TennisCourt removed : removeSet) {
//            LogWriter.write("       [除外理由] 既存予約との不整合: " + removed.getCourtName() + "（" + removed.getYmd() + "）");
//        }
//        return courtList;
//    }
//
//    /// 場所・時間帯によるフィルター（水曜・日曜を分けて処理）
//    private static List<TennisCourt> filterByWednesAndWeekEnd(List<TennisCourt> courtList,List<TennisCourt> yykList){
//
//        //週末リスト
//        List<TennisCourt> weekendYesList = new ArrayList<>();
//        //水曜リスト
//        List<TennisCourt> wednesdayList = new ArrayList<>();
//
//        for (TennisCourt court : courtList) {
//            LocalDate date = LocalDate.parse(court.getYmd(), DateTimeFormatter.ofPattern("yyyyMMdd"));
//            DayOfWeek dow = date.getDayOfWeek();
//
//            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
//                weekendYesList.add(court);
//            } else if (dow == DayOfWeek.WEDNESDAY) {
//                wednesdayList.add(court);
//            }
//        }
//
//        LogWriter.write("処理前土日：" + weekendYesList.size());
//        LogWriter.write("処理前水曜：" + wednesdayList.size());
//        // 除外前件数を取得
//        int beforeWed = wednesdayList.size();
//        int beforeWeekend = weekendYesList.size();
//
//        //週末リストから指定コートを除外
//        weekendYesList = FilterUtill.filterByExcludeCourtAreas(
//                weekendYesList,
//                List.of(
//                        CourtAreaType.TSUTSUMI_SPORTS_PARK),
//                List.of(DayOfWeek.SATURDAY,DayOfWeek.SUNDAY));
//        //水曜リストから指定コートを除外
//        wednesdayList = FilterUtill.filterByExcludeCourtAreas(wednesdayList,
//                List.of(
//                        CourtAreaType.TSUTSUMI_SPORTS_PARK),
//                List.of(DayOfWeek.WEDNESDAY));
//
//        //水曜リストから指定時間帯を除外
//        wednesdayList = filterByTimeRangesForWednesday(wednesdayList);
//
//        // コート２と３を除外
//        wednesdayList = filterCourtNameContainsTwoOrThree(wednesdayList);
//
//        //水曜の場合、時間帯が重複していたら除外
//        List<TennisCourt> newWedList = new ArrayList<>();
//        for(TennisCourt targetCourt:wednesdayList){
//            for(TennisCourt yykCourt:yykList){
//                if(!TennisCourtCompareUtil.hasOverlappingTime(targetCourt,yykCourt)){
//                    newWedList.add(targetCourt);
//                }else{
//                    LogWriter.writeSlotExclusionReason("[除外理由] 水曜時間帯重複により除外: ",targetCourt, new TimeSlot());
//                    FilterStatTracker.count("水曜時間帯重複のため");
//                }
//            }
//        }
//        wednesdayList = newWedList;
//
//
//
//
//
//        int excludedWed = beforeWed - wednesdayList.size();
//        int excludedWeekend = beforeWeekend - weekendYesList.size();
//
//        if (excludedWed > 0) {
////            FilterStatTracker.count("水曜指定コートor時間帯or重複により除外", excludedWed);
//        }
//        if (excludedWeekend > 0) {
////            FilterStatTracker.count("土日指定コートor時間帯or重複により除外", excludedWeekend);
//        }
//
//        List<TennisCourt> filtered = new ArrayList<>();
//        filtered.addAll(weekendYesList);
//        filtered.addAll(wednesdayList);
//        LogWriter.write("[水曜・土日のフィルタ後] コート数: " + filtered.size());
//        return filtered;
//    }
//
//    /// 水曜向け時間帯フィルター
//    private static List<TennisCourt> filterByTimeRangesForWednesday(List<TennisCourt> courtList) {
//        for (TennisCourt court : courtList) {
//            List<TimeRange> excludeList = new ArrayList<>();
//            excludeList.add(TimeRange.EARLY_MORNING);
//            excludeList.add(TimeRange.MORNING);
//            excludeList.add(TimeRange.EARLY_AFTERNOON);
//            excludeList.add(TimeRange.NIGHT);
//
//            List<TimeSlot> remainingSlots = new ArrayList<>();
//            for (TimeSlot slot : court.getTimeSlotList()) {
//                if (excludeList.contains(slot.getTimeRange())) {
////                    LogWriter.writeSlotExclusionReason("水曜の時間帯に該当しないためスロット除外", court, slot);
//                    LogWriter.writeSlotExclusionReason("[除外理由] 水曜時間帯により除外: ",court,slot);
//                } else {
//                    remainingSlots.add(slot);
//                }
//            }
//
//            court.setTimeSlotList(remainingSlots);
//
//            if (remainingSlots.isEmpty()) {
//                FilterStatTracker.count("水曜の時間帯に該当しないため");
//            }
//        }
//        return courtList;
//    }
//
//    private static List<TennisCourt> filterByTimeRangesPerMonth(List<TennisCourt> courtList) {
//        for (TennisCourt court : courtList) {
//            String ymd = court.getYmd();
//            if (ymd == null || ymd.length() < 6) continue;
//            int month = Integer.parseInt(ymd.substring(4, 6));
//
//            List<TimeRange> excludeList = new ArrayList<>();
//            excludeList.add(TimeRange.EARLY_MORNING);
//            excludeList.add(TimeRange.MORNING);
//            excludeList.add(TimeRange.NIGHT);
//            if (month == 7 || month == 8 || month == 9) {
//                excludeList.add(TimeRange.EARLY_MORNING);
//                excludeList.add(TimeRange.MORNING);
//                excludeList.add(TimeRange.LATE_MORNING);
//                excludeList.add(TimeRange.EARLY_AFTERNOON);
//                excludeList.add(TimeRange.AFTERNOON);
//            }
//
//            List<TimeSlot> remainingSlots = new ArrayList<>();
//            for (TimeSlot slot : court.getTimeSlotList()) {
//                if (excludeList.contains(slot.getTimeRange())) {
//                    LogWriter.writeSlotExclusionReason("月別時間帯に該当しないためスロット除外", court, slot);
//                } else {
//                    remainingSlots.add(slot);
//                }
//            }
//
//            court.setTimeSlotList(remainingSlots);
//
//            if (remainingSlots.isEmpty()) {
//                FilterStatTracker.count("月別時間帯に該当しないため");
//            }
//        }
//
//        courtList = filterCourtsWithTimeSlot(courtList);
//        return courtList;
//    }

    //    /// 日付が5日以内かつ茅ヶ崎公園以外なら除外
//    private static List<TennisCourt> filterExcludeByDateAndArea(List<TennisCourt> courtList,List<TennisCourt> yykList,List<CourtAreaType> areaList) {
//        List<TennisCourt> remainingCourts = new ArrayList<>();
//        LocalDate today = LocalDate.now();
//        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
//
//        // 日付が5日以内かつ茅ヶ崎公園以外なら除外
//        for (TennisCourt court : courtList) {
//            try {
//                // ymdは "yyyyMMdd" 形式
//                LocalDate courtDate = LocalDate.parse(court.getYmd(), formatter);
//
//                // エリアタイプを取得
//                CourtAreaType areaType = CourtAreaType.fromCourtName(court.getCourtName());
//
//                // 5日以内であっても、追加予約なら許容する
//                if (isWithinFiveDays(today, courtDate)) {
//                    for (TennisCourt yykCourt : yykList) {
//                        if (TennisCourtCompareUtil.isAdditionalReservation(yykCourt,court)) {
//                            remainingCourts.add(court);
//                        }
//                    }
//                    // 今日から5日以内かつ茅ヶ崎公園以外なら「除外対象」
//                    if (!areaList.contains(areaType)){
//                        if (court.getTimeSlotList() != null && !court.getTimeSlotList().isEmpty()) {
//                            LogWriter.writeSlotExclusionReason("日付が5日以内かつ茅ヶ崎公園以外", court, court.getTimeSlotList().get(0));
//                        }
//                        FilterStatTracker.count("日付が5日以内かつ茅ヶ崎公園以外");
//                        continue;
//                    }
//                }else{
//                    // 5日いないじゃない場合、許容する
//                    remainingCourts.add(court);
//                }
//            } catch (Exception e) {
//                LogWriter.write("[ERROR] 日付判定エラー: " + e.getMessage());
//                LogWriter.write("[除外理由] 例外により除外: " + court.getCourtName() + "（" + court.getYmd() + "）");
//                continue;
//            }
//        }
//        // 上記処理で時間帯を除外したことにより、対象時間帯が空になったコートを除外
//        remainingCourts = filterCourtsWithTimeSlot(remainingCourts);
//        return remainingCourts;
//    }
//
//    /// 今日から5日以内かどうか判定
//    private static boolean isWithinFiveDays(LocalDate today, LocalDate targetDate) {
//        return !targetDate.isBefore(today) && !targetDate.isAfter(today.plusDays(5));
//    }
//
//    /// 指定のコートエリア（CourtAreaTypeリスト）に該当するコートを除外する
//    private static List<TennisCourt> filterByExcludeCourtAreas(
//            List<TennisCourt> courtList,
//            List<CourtAreaType> excludeAreaList,
//            List<DayOfWeek> dowList
//    ) {
//        List<TennisCourt> remainingCourts = new ArrayList<>();
//
//        for (TennisCourt court : courtList) {
//            String dow = DateUtil.getDayOfWeek(court).toString();
//            boolean exclude = false;
//
//            for (CourtAreaType area : excludeAreaList) {
//                if (court.getCourtName().contains(area.getKeyword())) {
//                    exclude = true;
//                    break;
//                }
//            }
//
//            if (!exclude) {
//                remainingCourts.add(court);
//            } else {
//                FilterStatTracker.count(court,"指定エリア除外", 1);
//                LogWriter.writeSlotExclusionReason("指定エリア除外:" + dow + " ",court,new TimeSlot());
//            }
//        }
//
//
//        return remainingCourts;
//    }

//
//    /// コートリストを受け取り、支払いリスクのあるコートを除外して返す
//    private static List<TennisCourt> filterCourtsByPrepaymentRule(List<TennisCourt> courtList) {
//        List<TennisCourt> remainingCourts = new ArrayList<>();
//        LocalDate today = LocalDate.now();
//
//        for (TennisCourt court : courtList) {
//            // コートに日付情報が入っていなかったらスキップ
//            if (court.getYmd() == null || court.getYmd().isEmpty()) {
//                if (court.getTimeSlotList() != null && !court.getTimeSlotList().isEmpty()) {
//                    LogWriter.writeSlotExclusionReason("支払い猶予が2日未満", court, court.getTimeSlotList().get(0));
//                }
//                FilterStatTracker.count("支払い猶予が2日未満");
//                continue;
//            }
//
//            // ymd（例: "20250503"）をLocalDateに変換
//            LocalDate courtDate = parseYmdToDate(court.getYmd());
//
//            if (courtDate == null) {
//                if (court.getTimeSlotList() != null && !court.getTimeSlotList().isEmpty()) {
//                    LogWriter.writeSlotExclusionReason("支払い猶予が2日未満", court, court.getTimeSlotList().get(0));
//                }
//                FilterStatTracker.count("支払い猶予が2日未満");
//                continue; // 変換できなければスキップ
//            }
//
//            // ルールに従って除外判定
//            if (!shouldExcludeDueToPrepaymentDeadline(today, courtDate)) {
//                remainingCourts.add(court); // 除外対象でなければ追加
//            } else {
//                if (court.getTimeSlotList() != null && !court.getTimeSlotList().isEmpty()) {
//                    LogWriter.writeSlotExclusionReason("支払い猶予が2日未満", court, court.getTimeSlotList().get(0));
//                }
//                FilterStatTracker.count("支払い猶予が2日未満");
//            }
//        }
//        // 上記処理で時間帯を除外したことにより、対象時間帯が空になったコートを除外
//        courtList = filterCourtsWithTimeSlot(courtList);
//        return remainingCourts;
//    }

    /// ymd文字列 (例: "20250503") を LocalDate に変換
//    private static LocalDate parseYmdToDate(String ymd) {
//        try {
//            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
//            return LocalDate.parse(ymd, formatter);
//        } catch (Exception e) {
//            LogWriter.write("[ERROR] ymdパース中に例外発生: " + e.getMessage());
//            return null;
//        }
//    }

//
//    /// 1日・1エリアあたり最大4時間までに制限し、それを超える予約候補を除外
//    public static List<TennisCourt> filterByMaxHoursPerDay(List<TennisCourt> courtList, List<TennisCourt> yykCourtList) {
//        List<TennisCourt> resultList = new ArrayList<>();
//
//        // 日付とエリアでグループ化: Map<ymd + エリア, List<TennisCourt>>
//        Map<String, List<TennisCourt>> groupedMap = new HashMap<>();
//
//        for (TennisCourt court : courtList) {
//            String key = court.getYmd() + "::" + CourtAreaType.fromCourtName(court.getCourtName()).name();
//            groupedMap.computeIfAbsent(key, k -> new ArrayList<>()).add(court);
//        }
//
//        for (Map.Entry<String, List<TennisCourt>> entry : groupedMap.entrySet()) {
//            String ymdAreaKey = entry.getKey();
//            String ymd = ymdAreaKey.split("::")[0];
//            CourtAreaType areaType = CourtAreaType.valueOf(ymdAreaKey.split("::")[1]);
//
//            // 既存予約・対象日の同一エリアを抽出
//            List<TimeSlot> existingSlots = new ArrayList<>();
//            for (TennisCourt yyk : yykCourtList) {
//                if (yyk.getYmd().equals(ymd) &&
//                        CourtAreaType.fromCourtName(yyk.getCourtName()) == areaType) {
//                    existingSlots.addAll(yyk.getTimeSlotList());
//                }
//            }
//
//            // 現在の候補コート群
//            List<TennisCourt> candidates = entry.getValue();
//
//            // タイムスロットを統一的に扱うためにすべて LocalTime に変換し、時間単位でのユニーク化
//            Set<String> reservedHours = new HashSet<>();
//            for (TimeSlot slot : existingSlots) {
//                reservedHours.add(slot.getTime()); // 例: "15:00"
//            }
//
//            // 候補コートを時刻順にソート（時刻の早い順）
//            candidates.sort(Comparator.comparing(c ->
//                    c.getTimeSlotList().get(0).getTime()
//            ));
//
//            for (TennisCourt candidate : candidates) {
//                List<TimeSlot> newSlotList = new ArrayList<>();
//                for (TimeSlot slot : candidate.getTimeSlotList()) {
//                    if (!reservedHours.contains(slot.getTime())) {
//                        reservedHours.add(slot.getTime());
//                        newSlotList.add(slot);
//                    }
//                }
//
//                if (!newSlotList.isEmpty()) {
//                    // 新しい TimeSlot を反映して追加
//                    TennisCourt copy = new TennisCourt(candidate.getCourtName());
//                    copy.setYmd(candidate.getYmd());
//                    copy.setTimeSlotList(newSlotList);
//                    resultList.add(copy);
//                } else {
//                    // ここから追加: 各スロットごとに除外理由を詳細ログ出力
//                    for (TimeSlot slot : candidate.getTimeSlotList()) {
//                        LogWriter.writeSlotExclusionReason("1日4時間を超えるため除外", candidate, slot);
//                    }
//                    FilterStatTracker.count("1日4時間を超えるため");
//                }
//
//                if (reservedHours.size() >= 4) {
//                    break; // すでに4時間に達していれば、それ以降は無条件で除外
//                }
//            }
//        }
//
//        LogWriter.write("[最大4時間フィルタ後] コート数: " + resultList.size());
//        // 上記処理で時間帯を除外したことにより、対象時間帯が空になったコートを除外
//        resultList = filterCourtsWithTimeSlot(resultList);
//        return resultList;
//    }
//    /// コート名に「2」または「3」（半角・全角）が含まれている場合に除外
//    private static List<TennisCourt> filterCourtNameContainsTwoOrThree(List<TennisCourt> courtList) {
//        List<TennisCourt> filtered = new ArrayList<>();
//
//        for (TennisCourt court : courtList) {
//            String name = court.getCourtName();
//            if ((!name.contains(CourtAreaType.CHIGASAKI_PARK.getKeyword()))&(name.contains("2") || name.contains("３") || name.contains("3") || name.contains("２"))) {
//                LogWriter.write("[除外理由] コート名に2または3が含まれる: " + name + "（" + court.getYmd() + "）");
//                FilterStatTracker.count("コート名に2または3が含まれる");
//                continue;
//            }
//            filtered.add(court);
//        }
//
//        return filtered;
//    }


