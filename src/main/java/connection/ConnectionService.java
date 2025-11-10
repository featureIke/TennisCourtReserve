package connection;

import constpk.CommonConst;
import constpk.ConConst;
import api.LineNotify;
import log.LogWriter;
import lombok.extern.slf4j.Slf4j;
import model.*;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.io.IOException;

import static constpk.ConConst.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import model.CourtAreaType;
import util.*;

@Slf4j
public class ConnectionService {
    /// エラーが起きているかどうか
    boolean isError = false;
    /// コネクション初期化
    ConnectionUtil connectionUtil = new ConnectionUtil();
    /// セッションID
    String g_sessionid = "";
    /// 今回の処理で予約したリスト
    List<TennisCourt>  successCourtList = new ArrayList<>();
    /// ルートURL（rootUrl.txt > ConConst.ROOT_URL の順に採用）
    private final String rootUrl = initRootUrl();


    //予約親処理
    public List<Exception> reservationParent() {
        System.out.println("処理スタート");
        List<Exception> exceptionList = new ArrayList<>();
        Credential credential = IOUtil.getCredential();
        // 予約済みリスト取得（ログイン処理はgetYykList内で実施される）
        List<TennisCourt> yykzumiCourtList = getYykList(credential);
        try {
            //初期画面接続
            isError = initScreen();
            if (isError) {
                return exceptionList;
            }

            //ログイン
            isError = login(credential);
            if (isError) {
                return exceptionList;
            }

            //分類画面への接続
            isError = accessBunruiScreen();
            if (isError) {
                return exceptionList;
            }

            //第一選択画面への接続
            isError = accessDaiichiScreen();
            if (isError) {
                return exceptionList;
            }

            //dataタブページへのアクセス
            String responseBodyFromDataTab = dataTabAccess();
            if (responseBodyFromDataTab == null) {
                System.out.println("エラー発生");
                return exceptionList;
            }
            //iframe取得
            String iframeUrl = extractIframeSrc(responseBodyFromDataTab);
            if (iframeUrl == null) {
                LogWriter.write("[ERROR] iframeが取得できませんでした: dataTab画面");
                return exceptionList;
            }
            String calendarResponse = accessCalendar(iframeUrl);
            //他の人が予約した情報を追加
            yykzumiCourtList.addAll(IOUtil.loadReservedCourtsFromCsv("reservedCourt.csv"));
            // 除外日リストをロード
            List<String> jogaiDates = IOUtil.loadJogaiDates("jogaibi.csv");

            //アクティブな月のレスポンスボディを取得（すべてのアクティブな月）
            List<String> activeMonthUrlList = getActiveMonthUrls(calendarResponse);
            if (!activeMonthUrlList.isEmpty()) {
                activeMonthUrlList.add(DateUtil.shiftHyojiymOneMonthBack(activeMonthUrlList.get(0)));
            } else {
                LogWriter.write("[ERROR] アクティブ月のURLが取得できませんでした");
                return exceptionList;
            }
            LogWriter.write(" ・アクティブな月の数："+activeMonthUrlList.size());
            for (String activeMonthUrl : activeMonthUrlList) {
                LogWriter.write(" ・URL："+activeMonthUrl);
            }

            int i = 0;
            Collections.shuffle(activeMonthUrlList);
            for (String activeMonthUrl:activeMonthUrlList){
                List<String> targetUrlList = getHidukeUrl(activeMonthUrl);
//            for (String url : targetUrlList) {
//                System.out.println("取得したURL: " + url);
//            }
                checkActiveMonth(targetUrlList, jogaiDates, yykzumiCourtList);
            }

            LogWriter.write("💣️💣️💣️💣️💣️💣️今回の処理で予約した件数: " + successCourtList.size());
        } catch (IOException e) {
            LogWriter.write("[ERROR] reservationParent中にIOException: " + e);
            for (StackTraceElement element : e.getStackTrace()) {
                LogWriter.write("    at " + element.toString());
            }
            exceptionList.add(e);
        }
        // クローズ
        connectionUtil.close();
        return exceptionList;
    }

    /// rootUrl.txt からルートURLを読み込む。読み込めなければ定数を使用
    private String initRootUrl() {
        String path = "rootUrl.txt";
        String s = "";
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(path);
            if (java.nio.file.Files.exists(p)) {
                s = java.nio.file.Files.readString(p).trim();
                if (!s.isEmpty()) {
                    LogWriter.write("ROOT_URL loaded from " + path + ": " + s);
                    return s;
                }
            }
        } catch (Exception e) {
            LogWriter.write("[WARN] rootUrl.txtの読み込みに失敗: " + e);
        }
        // フォールバック：定義済みの定数を利用
        return s;
    }
    /// 予約済みリストを取得する
    private List<TennisCourt> getYykListSingle(Credential credential) {
        List<TennisCourt> yykzumiCourt = new ArrayList<>();

            try {
                //初期画面接続
                isError = initScreen();
                if (isError) {
                    LogWriter.write("[ERROR] 初期画面接続に失敗しました");
                    System.exit(1);
                }

                //ログイン
                isError = login(credential);
                if (isError) {
                    LogWriter.write("[ERROR] ログインに失敗しました");
                    System.exit(1);
                }

                //予約済みリストへの接続
                String yykResponse = accessYykList();
                if (yykResponse == null || yykResponse.isEmpty()) {
                    LogWriter.write("[ERROR] 予約済みリストの取得に失敗しました");
                    System.exit(1);
                }

                //予約済みリストの作成
                yykzumiCourt.addAll(ReservedCourtParser.parseReservedTennisCourtsFromHtml(yykResponse));
            } catch (Exception e) {
                LogWriter.write("[ERROR] getYykList中に例外が発生しました: " + e);
                for (StackTraceElement element : e.getStackTrace()) {
                    LogWriter.write("    at " + element.toString());
                }
                System.exit(1);
            }
        return yykzumiCourt;
    }

    /// 予約済みリストを取得する
    private List<TennisCourt> getYykList(Credential credential) {
        List<TennisCourt> yykzumiCourt = new ArrayList<>();
            try {
                //初期画面接続
                isError = initScreen();
                if (isError) {
                    LogWriter.write("[ERROR] 初期画面接続に失敗しました");
                    System.exit(1);
                }

                //ログイン
                isError = login(credential);
                if (isError) {
                    LogWriter.write("[ERROR] ログインに失敗しました");
                    System.exit(1);
                }

                //予約済みリストへの接続
                String yykResponse = accessYykList();
                if (yykResponse == null || yykResponse.isEmpty()) {
                    LogWriter.write("[ERROR] 予約済みリストの取得に失敗しました");
                    System.exit(1);
                }

                //予約済みリストの作成
                yykzumiCourt.addAll(ReservedCourtParser.parseReservedTennisCourtsFromHtml(yykResponse));
            } catch (Exception e) {
                LogWriter.write("[ERROR] getYykList中に例外が発生しました: " + e);
                for (StackTraceElement element : e.getStackTrace()) {
                    LogWriter.write("    at " + element.toString());
                }
                System.exit(1);
            }
        return yykzumiCourt;
    }




    //初期画面接続
    private boolean initScreen() throws IOException {
        System.out.println("▶PrivateM：初期画面接続処理");
        String initUrl = "https://yoyaku.city.chigasaki.kanagawa.jp/cultos/reserve/gin_init2";
        String initResponseBody = connectionUtil.sendGetRequest(initUrl);
        if (isError(initResponseBody)) {
            return true;
        }
        g_sessionid = connectionUtil.extractGSessionId(initResponseBody);
        return false;
    }

    //ログイン処理
    private boolean login(Credential credential) {
        System.out.println("/n");
        System.out.println("▶PrivateM：ログイン処理");
        //ポストのネームバリューリスト
        List<NameValuePair> postBodyParams = new ArrayList<>();
        //ネームバリューリスト作成
        postBodyParams.add(new BasicNameValuePair(constpk.ConConst.G_KINONAIYO_KEY, String.valueOf(g_kinonaiyo)));
        postBodyParams.add(new BasicNameValuePair(ConConst.USER_ID_KEY, credential.getId()));
        postBodyParams.add(new BasicNameValuePair(ConConst.PASS_KEY, credential.getPass()));
        postBodyParams.add(new BasicNameValuePair(ConConst.G_SESSION_ID_KEY, g_sessionid));
        String loginUrl = "https://yoyaku.city.chigasaki.kanagawa.jp/cultos/reserve/gin_login";
        String loginResponseBody = connectionUtil.sendPostRequest(loginUrl, postBodyParams);
        return isError(loginResponseBody);
    }

    /// 分類画面への接続
    private boolean accessBunruiScreen() {
        System.out.println("\n▶PrivateM：分類画面への接続");
        String bunruiUrl = "https://yoyaku.city.chigasaki.kanagawa.jp/cultos/reserve/gin_z_bunrui" + "?" + ConConst.G_SESSION_ID_KEY + "=" + g_sessionid;
        List<NameValuePair> postBodyParamsForBunruiScreen = new ArrayList<>();
        postBodyParamsForBunruiScreen.add(new BasicNameValuePair(ConConst.G_KINONAIYO_KEY, String.valueOf(g_kinonaiyo + 1)));
        postBodyParamsForBunruiScreen.add(new BasicNameValuePair(ConConst.U_GENZAI_IDX_KEY, String.valueOf(u_genzai_idx)));
        String bunruiScreenResponseBody = connectionUtil.sendPostRequest(bunruiUrl, postBodyParamsForBunruiScreen);
        return isError(bunruiScreenResponseBody);
    }

    /// 第一条件選択画面への接続
    private boolean accessDaiichiScreen() throws IOException {
        System.out.println("\n▶PrivateM：第一選択画面への接続");

        //第一条件選択画面への接続
        String daiichiScreenUrl = "https://yoyaku.city.chigasaki.kanagawa.jp/cultos/reserve/gin_z_first";
        List<NameValuePair> getBodyParamsForDaiichiScreen = new ArrayList<>();
        getBodyParamsForDaiichiScreen.add(new BasicNameValuePair(U_GENZAI_IDX_KEY, String.valueOf(u_genzai_idx += 1)));
        getBodyParamsForDaiichiScreen.add(new BasicNameValuePair(G_BUNRUICD_KEY, String.valueOf(g_bunruicd)));
        getBodyParamsForDaiichiScreen.add(new BasicNameValuePair(G_SESSION_ID_KEY, g_sessionid));
        try {
            daiichiScreenUrl = buildQueryUrl(daiichiScreenUrl, getBodyParamsForDaiichiScreen);
        } catch (Exception e) {
            LogWriter.write("[ERROR] 例外発生: " + e);
            for (StackTraceElement element : e.getStackTrace()) {
                LogWriter.write("    at " + element.toString());
            }
            return true;
        }
        String daiichiScreenResponseBody = connectionUtil.sendGetRequest(daiichiScreenUrl);
        return isError(daiichiScreenResponseBody);
    }


    /// 日付選択タブへの接続
    private String dataTabAccess() throws IOException {
        System.out.println("\n▶PrivateM：日付選択タブへの接続");
        String url = "https://yoyaku.city.chigasaki.kanagawa.jp/cultos/reserve/gin_z_first";
        List<NameValuePair> getBodyParams = new ArrayList<>();
        getBodyParams.add(new BasicNameValuePair(G_KINONAIYO_KEY, String.valueOf(g_kinonaiyo)));
        getBodyParams.add(new BasicNameValuePair(U_GENZAI_IDX_KEY, String.valueOf(u_genzai_idx)));
        getBodyParams.add(new BasicNameValuePair(U_TAB_KEY, u_tab));
        getBodyParams.add(new BasicNameValuePair(G_SESSION_ID_KEY, g_sessionid));
        try {
            url = buildQueryUrl(url, getBodyParams);
        } catch (Exception e) {
            LogWriter.write("[ERROR] 例外発生: " + e);
            for (StackTraceElement element : e.getStackTrace()) {
                LogWriter.write("    at " + element.toString());
            }
        }
        String responseBody = connectionUtil.sendGetRequest(url);
        if (isError(responseBody)) {
            return null;
        }
        return responseBody;
    }

    /// カレンダーiframeへの接続
    private String accessCalendar(String iframeUrl) throws IOException {
        System.out.println("▶PrivateM：//カレンダーiframeへの接続");
        String calendarUrl = rootUrl + iframeUrl;
        return connectionUtil.sendGetRequest(calendarUrl);
    }

    /// 予約済みリストへの接続
    private String accessYykList() throws IOException {
        System.out.println("▶PrivateM：//予約済みiframeへの接続");
        String calendarUrl = rootUrl + "/cultos/reserve/gin_s_yyklist_in?g_sessionid=" + g_sessionid;
        return connectionUtil.sendGetRequest(calendarUrl);
    }


    /// エラーチェック処理
    private boolean isError(String responseBody) {
        if (connectionUtil.isErrorResponse(responseBody)) {
            LogWriter.write("[ERROR] リダイレクトされました。処理を終了します");
            return true;
        } else if (responseBody == null) {
            LogWriter.write("[ERROR]レスポンスボディがnullです。処理を終了します");
            return true;
        } else {
            System.out.print(" / 接続成功・処理続行。");
//            System.out.print(responseBody);
            return false;
        }
    }

    /**
     * 元となるURLとクエリパラメータを結合してクエリ付きURLを生成するメソッド
     *
     * @param baseUrl     元となるURL
     * @param queryParams クエリパラメータ（キーと値のリスト）
     * @return クエリ付きのURL（文字列）
     * @throws Exception URLの構築に失敗した場合の例外
     */
    private String buildQueryUrl(String baseUrl, List<NameValuePair> queryParams) throws Exception {
        URIBuilder uriBuilder = new URIBuilder(baseUrl);
        if (queryParams != null && !queryParams.isEmpty()) {
            uriBuilder.addParameters(queryParams); // クエリパラメータを追加
        }
        return uriBuilder.build().toString(); // 完成したURLを文字列として返す
    }

    /// 複数のアクティブな月のURLを取得する
    private List<String> getActiveMonthUrls(String html) {
        List<String> activeMonthUrls = new ArrayList<>();
        try {
            // HTMLをパース
            Document document = Jsoup.parse(html);

            // アクティブな月を探す（<a> 要素を持つ月）
            Elements monthCells = document.select("table#MonthTbl td:has(a)");
            if (monthCells.isEmpty()) {
                System.out.println("/ アクティブな月が見つかりませんでした。");
                return activeMonthUrls;
            }

            // 全リンクをリストアップ
            for (Element monthCell : monthCells) {
                Element monthLink = monthCell.selectFirst("a");
                if (monthLink != null) {
                    String url = monthLink.attr("href");
                    System.out.println("\n ・アクティブな月のURL: " + url);
                    activeMonthUrls.add(url);
                }
            }

            return activeMonthUrls;

        } catch (Exception e) {
            LogWriter.write("[ERROR] 例外発生: " + e);
            for (StackTraceElement element : e.getStackTrace()) {
                LogWriter.write("    at " + element.toString());
            }
            return activeMonthUrls;
        }
    }

    /**
     * 指定された年と月から指定曜日のリンクを取得するメソッド。
     *
     * @param html HTMLソース
     * @param year 年
     * @param month 月
     * @param targetWeekdays 対象の曜日リスト（例: List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)）
     * @return 対象曜日に対応するリンクのリスト
     */
    private List<String> getTargetDayUrlsByWeekdays(String html, int year, int month, List<DayOfWeek> targetWeekdays) {
        List<String> resultUrls = new ArrayList<>();
        try {
            // DateUtilの新しいユーティリティメソッドを利用
            List<Integer> targetDays = DateUtil.getDaysOfWeek(year, month, targetWeekdays);

            Document document = Jsoup.parse(html);
            Elements cells = document.select("table.link-table td");

            for (Element cell : cells) {
                String cellText = cell.text().trim();
                if (cellText.matches("\\d+") && targetDays.contains(Integer.parseInt(cellText))) {
                    Element link = cell.selectFirst("a");
                    if (link != null) {
                        resultUrls.add(link.attr("href"));
                    }
                }
            }
            return resultUrls;
        } catch (Exception e) {
            LogWriter.write("[ERROR] 例外発生: " + e);
            for (StackTraceElement element : e.getStackTrace()) {
                LogWriter.write("    at " + element.toString());
            }
            return resultUrls;
        }
    }

    /// テニスのULRを取得する
    private String getTennisUrl(String html) {
        // HTMLをパース
        Document document = Jsoup.parse(html);
        // 全リンクを取得
        Elements links = document.select("a");

        for (Element link : links) {
            String linkText = link.text().trim();

            // 完全一致または「硬式テニス」ときっちり比較する
            if (linkText.equals("テニス")) {
                String tennisUrl = link.attr("href");
//                System.out.println("\n    ・テニスのURL: " + tennisUrl);
                return tennisUrl;
            }
        }
        System.out.println("テニスのリンクが見つかりませんでした。");
        return null;
    }

    /**
     * HTMLソースから最初のiframeのsrc属性を取得するメソッド。
     *
     * @param html HTML文字列
     * @return iframeのsrc属性の値（見つからない場合はnull）
     */
    private String extractIframeSrc(String html) {
        try {
            // HTMLをパース
            Document document = Jsoup.parse(html);

            // iframeタグを検索
            Element iframe = document.selectFirst("iframe");

            if (iframe != null) {
                // src属性を取得
                String src = iframe.attr("src");
//                System.out.println("\n    ・抽出したiframeのsrc: " + src);
                return src;
            } else {
                System.out.println("iframeが見つかりませんでした。");
            }
        } catch (Exception e) {
            LogWriter.write("[ERROR] 例外発生: " + e);
            for (StackTraceElement element : e.getStackTrace()) {
                LogWriter.write("    at " + element.toString());
            }
        }
        return null;
    }

    /// 日付ごとのURLを取得する
    private List<String> getHidukeUrl(String activeMonthUrl) throws IOException {
        String activeMonthHtml = connectionUtil.sendGetRequest(rootUrl + activeMonthUrl);
        String uHyojiym = activeMonthUrl.split("u_hyojiym=")[1].split("&")[0]; // "202501"を取得
        int year = Integer.parseInt(uHyojiym.substring(0, 4)); // 年を取得
        int month = Integer.parseInt(uHyojiym.substring(4));   // 月を取得
        List<DayOfWeek> weekdays = List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY,DayOfWeek.TUESDAY,DayOfWeek.WEDNESDAY,DayOfWeek.THURSDAY,DayOfWeek.FRIDAY);
        List<String> hidukeUrlList = getTargetDayUrlsByWeekdays(activeMonthHtml, year, month, weekdays);
        return hidukeUrlList;
    }

    /// 日付ごとのURLを取得する(平日)
    private List<String> getWeekDayUrl(String activeMonthUrl) throws IOException {
        String activeMonthHtml = connectionUtil.sendGetRequest(rootUrl + activeMonthUrl);
        String uHyojiym = activeMonthUrl.split("u_hyojiym=")[1].split("&")[0]; // "202501"を取得
        int year = Integer.parseInt(uHyojiym.substring(0, 4)); // 年を取得
        int month = Integer.parseInt(uHyojiym.substring(4));   // 月を取得
        List<DayOfWeek> weekdays = List.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        List<String> hidukeUrlList = getTargetDayUrlsByWeekdays(activeMonthHtml, year, month, weekdays);
        return hidukeUrlList;
    }

    private boolean isJogaibi(String hidukeUrl,List<String> jogaibiCsv){
        if (!FilterUtill.isTargetUrl(hidukeUrl, jogaibiCsv)) {
            String dateStr = DateUtil.extractDateFromUrl(hidukeUrl);
            System.out.println();
            System.out.println("📅📅📅 " + dateStr + "除外対象日の為、処理をスキップします");
            return true;
        }
        return false;
    }

    private boolean hasHtmlErr(String hidukeHtml,String hidukeUrl){
        if (hidukeHtml == null) {
            System.out.println("エラーの日付URL："+hidukeUrl);
            System.out.println("エラー発生");
            return true;
        }
        return false;
    }
    private boolean hasHidukeIframeErr(String hidukeIframeResponse){
        if (hidukeIframeResponse == null) {
            System.out.println("エラー発生");
            return true;
        }
        return false;
    }



    /// アクティブな月を探索する
    private void checkActiveMonth(List<String> hidukeUrlList, List<String> jogaibiCsv, List<TennisCourt> yykCourtList) throws IOException {

        //▼▼▼取得した日付ループ▼▼▼

        for (String hidukeUrl : hidukeUrlList) {
            hidukeUrl = convertToSecondUrl(hidukeUrl);
            //除外日対象ならコンティニュー
            if (isJogaibi(hidukeUrl,jogaibiCsv)) continue;
            //取得した日付ページのレスポンスを取得
            String hidukeHtml = connectionUtil.sendGetRequest(rootUrl + hidukeUrl);
            if (hasHtmlErr(hidukeHtml,hidukeUrl)) continue;
            //日付のiframe取得
            String hidukeIframeResponse = connectionUtil.sendGetRequest(rootUrl + extractIframeSrc(hidukeHtml));
            if(hasHidukeIframeErr(hidukeIframeResponse)) continue;
            //利用目的：テニスURLの取得
            String tennisUrl = getTennisUrl(hidukeIframeResponse);
            String tennisResponse = connectionUtil.sendGetRequest(rootUrl + tennisUrl);
            //テニスのiframe取得
            String tennisIframeUrl = extractIframeSrc(tennisResponse);
            String tennisIframeResponse = connectionUtil.sendGetRequest(rootUrl + tennisIframeUrl);

            //コートリストをModelに変換
//            System.out.print("\n");
            String ymd = DateUtil.extractDateUrl(hidukeUrl);
            if (tennisIframeResponse == null) {
                LogWriter.write("[ERROR] テニスiframeレスポンスがnullです: " + tennisIframeUrl);
                continue;
            }
            List<TennisCourt> courtList = ParserUtil.parseTennisCourtsFromHtml(tennisIframeResponse, ymd);

            // フィルタリング対象外の希望日コートを取得
            List<ExpectedYmdTimeRange> expectedDateList = new ArrayList<>();
            expectedDateList = IOUtil.loadExpectedYmdAndTimeRanges(CommonConst.EXPECTED_DATE_PATH);
            List<TennisCourt> expectedCourtList = new ArrayList<>();
//            System.out.print("希望日の件数:");
//            System.out.println(expectedDateList.size());
//            System.out.println("内容");
            for(ExpectedYmdTimeRange expectedData:expectedDateList){
//                expectedData.printProperties();
                expectedCourtList.addAll(
                FilterUtill.getExpectedCourt(courtList,expectedData,
                        expectedData.getYmd(),expectedData.getTimeRange(),expectedData.getCourtAreaName(),expectedData.getMaxCount(),yykCourtList));
            }
            //重複削除（HTML時点で同一コートが３つ取得されてしまうため）
//            courtList = TennisCourt.mergeDuplicateCourts(courtList);


            //フィルタリング
//            courtList = FilterUtill.filterCourt(courtList,yykCourtList);
            //ほしいコートのリストを追加
            // ※※※※※※※※※※※※
            // ※※※※※※※※※※※※
            // ※※※※※※※※※※※※
            // ※※※※※※※※※※※※
            // ※※※※※※※※※※※※
            // 希望日のみ予約する仕様に変更中
            // ※※※※※※※※※※※※
            // ※※※※※※※※※※※※
            // ※※※※※※※※※※※※
            // ※※※※※※※※※※※※
            // ※※※※※※※※※※※※
            courtList.clear();
            courtList=expectedCourtList;

            // コート予約日候補
//            System.out.println("▶▶▶最終的に予約処理を実施するコート一覧");
//            TennisCourtLogger.printTennisCourts(courtList);

            // コート優先度順にソート
            courtList.sort(Comparator.comparingInt(
                    court -> CourtAreaType.fromCourtName(court.getCourtName()).getPriority()
            ));

            // 最終的に予約処理を実施するコート一覧
//            System.out.println("▶▶▶最終的に予約処理を実施するコート一覧");
//            TennisCourtLogger.printTennisCourts(courtList);

            for (TennisCourt court : courtList) {
                for (TimeSlot timeSlot : court.getTimeSlotList()) {
                    String timePageResponse = connectionUtil.sendGetRequest(timeSlot.getUrl());
                    if (extractFormDetails(timePageResponse)) {
                        // 成功したときだけ
                       String msg = createLineMessage(court,timeSlot,yykCourtList);
                        // ▼ LINE通知
                        LogWriter.write("[DEBUG] LINE通知直前");
                        LineNotify.sendNotification(msg);
                    }
                }
            }
        }
        //▲▲▲日付ループ終了▲▲▲
    }

    /// 予約済みコートの取得
    private TennisCourt findReservedCourt(List<TennisCourt> list, String courtName, String ymd) {
        for (TennisCourt court : list) {
            if (court.getCourtName().equals(courtName) && court.getYmd().equals(ymd)) {
                return court;
            }
        }
        return null;
    }


    /// LINE送信用メッセージを作成
    public String createLineMessage(TennisCourt court, TimeSlot timeSlot, List<TennisCourt> yykCourtList) {
        // まず同じcourtName, ymdの予約済みオブジェクトがすでに存在するか探す
        TennisCourt reservedCourt = findReservedCourt(yykCourtList, court.getCourtName(), court.getYmd());

        if (reservedCourt == null) {
            // なければ新しく作る
            reservedCourt = new TennisCourt(court.getCourtName());
            reservedCourt.setYmd(court.getYmd());
            yykCourtList.add(reservedCourt);
        }

        // 成功したTimeSlotだけ追加
        reservedCourt.addTimeSlot(timeSlot);

        // ▼ グローバル変数（今回成功したコートリスト）にも追加
        TennisCourt successCourt = new TennisCourt(court.getCourtName());
        successCourt.setYmd(court.getYmd());

        TimeSlot successSlot = new TimeSlot();
        successSlot.setTime(timeSlot.getTime());
        successSlot.setTimeRange(timeSlot.getTimeRange());
        successSlot.setAvailable(true);
        successSlot.setUrl(timeSlot.getUrl());
        successCourt.addTimeSlot(successSlot);

        successCourtList.add(successCourt); // ← グローバル変数リストに追加

// ▼ ログ・LINE共通メッセージ作成
        LocalDate targetDate = LocalDate.parse(court.getYmd(), DateTimeFormatter.ofPattern("yyyyMMdd"));
        DayOfWeek dow = targetDate.getDayOfWeek();
        String dowJa = switch (dow) {
            case MONDAY -> "月";
            case TUESDAY -> "火";
            case WEDNESDAY -> "水";
            case THURSDAY -> "木";
            case FRIDAY -> "金";
            case SATURDAY -> "土";
            case SUNDAY -> "日";
        };

        StringBuilder msg = new StringBuilder();
        String nowStr = java.time.format.DateTimeFormatter.ofPattern("M月d日（E）", java.util.Locale.JAPANESE).format(java.time.LocalDateTime.now());
        String ymdStr = java.time.format.DateTimeFormatter.ofPattern("M月d日", java.util.Locale.JAPANESE).format(targetDate);
//        msg.append("処理日: ").append(nowStr).append("\n");
//        msg.append("🎾 コート名:").append("\n");
        // 日付
        msg.append(ymdStr).append("（").append(dowJa);
        // 時間
        msg.append(successSlot.getTime()).append("\n");
        // コート名
        msg.append(successCourt.getCourtName()).append("\n");
//        msg.append("日付:");

//        msg.append("🕑️ 時間: ").append("\n");


        LocalDate deadline = PaymentDeadlineUtil.calculatePaymentDeadline(LocalDate.now(), targetDate);
        if (deadline == null) {
            msg.append("・当日支払いOK");
        } else {
            DayOfWeek limitDow = deadline.getDayOfWeek();
            String limitDowJa = switch (limitDow) {
                case MONDAY -> "月";
                case TUESDAY -> "火";
                case WEDNESDAY -> "水";
                case THURSDAY -> "木";
                case FRIDAY -> "金";
                case SATURDAY -> "土";
                case SUNDAY -> "日";
            };
            String deadlineStr = java.time.format.DateTimeFormatter.ofPattern("M月d日", java.util.Locale.JAPANESE).format(deadline);
            msg.append("⌛️ 支払期限: ").append("\n");
            msg.append(deadlineStr).append("（").append(limitDowJa).append("）");
        }

// ▼ ログ出力
        LogWriter.write("[DEBUG] ログ出力toString直前");
        LogWriter.write(msg.toString());
        return msg.toString();
    }


    //フォーム情報取得AND送信
    private boolean extractFormDetails(String html) {
        // HTMLをパース
        Document document = Jsoup.parse(html);

        // フォームを取得 (name="form_nm" のみ)
        Elements forms = document.select("form[name=form_nm]");

        // フォームが見つからない場合の処理
        if (forms.isEmpty()) {
            LogWriter.write("[ERROR] フォーム name=\"form_nm\" が見つかりませんでした。");
            return false;
        }

        // フォーム情報を処理
        String actionUrl;
        List<NameValuePair> postBodyParams = new ArrayList<>();

        for (Element form : forms) {
            // フォーム送信先URL
            actionUrl = form.attr("action");
            System.out.println("フォーム送信先: " + actionUrl);

            // キーバリューのペアを生成
            Elements inputs = form.select("input");
            for (Element input : inputs) {
                String key = input.attr("name");
                String value = input.attr("value");
                if (!key.isEmpty()) {
                    postBodyParams.add(new BasicNameValuePair(key, value));
                }
            }

            // 結果を出力
            System.out.println("POSTパラメータ:");
            for (NameValuePair pair : postBodyParams) {
                System.out.println("  " + pair.getName() + ": " + pair.getValue());
            }

            // 必要であればここで POST リクエストを送信
            String result =
            connectionUtil.sendPostRequest(rootUrl + actionUrl, postBodyParams);

            // 予約成功した場合、trueを返す
            return !isError(result);

        }
        return false;
    }

    /**
     * gin_z_kaisi_smk_rsp → gin_z_second、
     * u_genzai_idx=4 → u_genzai_idx=2 に変換するメソッド
     *
     * @param originalUrl 元のURL
     * @return 変換後のURL
     */
    public static String convertToSecondUrl(String originalUrl) {
        if (originalUrl == null) return null;

        String replacedUrl = originalUrl;

        // パス部分の置換
        replacedUrl = replacedUrl.replace("gin_z_kaisi_smk_rsp", "gin_z_second");

        // u_genzai_idx の置換（パラメータとして正確に一致した場合のみ）
        replacedUrl = replacedUrl.replaceAll("(?<=u_genzai_idx=)4(?!\\d)", "2");

        return replacedUrl;
    }

    /// LINE通知処理のスタブ
    private void lineStab(){
        TennisCourt tennisCourt = new TennisCourt("nya");
        tennisCourt.setYmd("20250809");

        TimeSlot timeSlot = new TimeSlot();
        timeSlot.setTime("09:00");
        timeSlot.setTimeRange(TimeRange.AFTERNOON);
        timeSlot.setAvailable(true);
        timeSlot.setUrl("nya");
        tennisCourt.setTimeSlotList(List.of(new TimeSlot()));

        List<TennisCourt> yykCourtList = new ArrayList<>();


        createLineMessage(tennisCourt,timeSlot,yykCourtList);

        if (true){
        }
    }
}
