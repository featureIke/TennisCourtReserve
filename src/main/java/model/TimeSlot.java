package model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
//import util.TimeZoneType;

///親（CourtArea）
///            └ 子（TennisCourt）
///                            └ 孫（TimeSlot）
@Getter
@Setter
/// 各コートの時間帯ごとの情報を表すクラス
@Data
public class TimeSlot {
    private String time; // 例: "09:00"
    private TimeRange timeRange; // 午前中、午後など（Enum）
    private boolean available; // ○ならtrue、×ならfalse
    /// URL（予約確認ページなど）※空きがある場合のみ設定
    private String url;
}