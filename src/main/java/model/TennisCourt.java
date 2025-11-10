package model;

import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

///親（CourtArea）
///            └ 子（TennisCourt）
///                            └ 孫（TimeSlot）
///
/// コート単体を表すクラス
@Getter
@Setter
public class TennisCourt {

    /// コート名（例：庭球場１コート）
    private String courtName;
    /// 日付情報
    private String ymd;
    /// 時間枠ごとの空き情報リスト
    private List<TimeSlot> timeSlotList = new ArrayList<>();
    /// 直前でも予約する（対抗戦などに対応）
    private boolean isChokkinOk;

    /// コンストラクタ
    public TennisCourt(String courtName) {
        this.courtName = courtName;
    }

    /// 時間枠追加
    public void addTimeSlot(TimeSlot timeSlot) {
        timeSlotList.add(timeSlot);
    }

    /// コート名が同一のTennisCourtを統合するユーティリティ
    public static List<TennisCourt> mergeDuplicateCourts(List<TennisCourt> courtList) {
        Map<String, TennisCourt> mergedMap = new HashMap<>();
        for (TennisCourt court : courtList) {
            String name = court.getCourtName();
            String ymd = court.getYmd();
            List<TimeSlot> slots = court.getTimeSlotList();

            if (ymd == null || slots == null) {
                System.err.println("欠損データをスキップ: courtName=" + name + ", ymd=" + ymd + ", timeSlotList=" + slots);
                continue;
            }

            if (!mergedMap.containsKey(name)) {
                TennisCourt newCourt = new TennisCourt(name);
                newCourt.setYmd(ymd); // ymdをセット
                newCourt.setTimeSlotList(new ArrayList<>(slots)); // timeSlotListもセット
                mergedMap.put(name, newCourt);
            }
        }
        return new ArrayList<>(mergedMap.values());
    }
}