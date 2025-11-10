package model;

public class ExpectedYmdTimeRange {
    /// 希望日付
    private final String ymd;
    ///  希望時間帯
    private final TimeRange timeRange;
    ///  コートエリア名(比較には要変換)
    private final String courtAreaName;
    /// 取得制限数
    private final int maxCount;
    /// 直前でも予約する（対抗戦などに対応）
    private final boolean isChokkinOk;

    public ExpectedYmdTimeRange(String ymd, TimeRange timeRange, String courtAreaName, int maxCount, boolean isChokkinOk) {
        this.ymd = ymd;
        this.timeRange = timeRange;
        this.courtAreaName = courtAreaName;
        this.maxCount = maxCount;
        this.isChokkinOk = isChokkinOk;
    }

    public String getYmd() {
        return ymd;
    }

    public TimeRange getTimeRange() {
        return timeRange;
    }

    public String getCourtAreaName() {
        return courtAreaName;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public boolean getIsChokkinOk(){
        return isChokkinOk;
    }

    public boolean isChokkinOk() {
        return isChokkinOk;
    }

    // プロパティを出力するメソッド
    public void printProperties() {
        System.out.println("ymd: " + ymd);
        System.out.println("timeRange: " + timeRange);
        System.out.println("courtAreaName: " + courtAreaName);
        System.out.println("maxCount: " + maxCount);
        System.out.println("isChokkinOk: " + isChokkinOk);
    }
}