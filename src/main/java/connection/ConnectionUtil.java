package connection;

import log.LogWriter;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;


public class ConnectionUtil {
    private final CloseableHttpClient httpClient;
    private final BasicHttpContext httpContext;

    // コンストラクタ
    public ConnectionUtil() {
        this.httpClient = HttpClients.createDefault();
        this.httpContext = new BasicHttpContext();
    }


    /**
     * 任意のURLに接続してレスポンスを取得し表示するメソッド。
     *
     * @param url 接続先のURL
     * @return レスポンスボディ（文字列）
     */
    public String sendGetRequest(String url) throws IOException {
//        System.out.println("    ・接続URL: " + url);
        try {
            HttpGet httpGet = new HttpGet(url);

            // GETリクエスト送信
            try (CloseableHttpResponse response = httpClient.execute(httpGet, httpContext)) {
                // ステータスコードの表示
                int statusCode = response.getCode();
//                System.out.print("    ・HTTPステータスコード: " + statusCode);

                // レスポンスボディの取得と表示
                String responseBody = "";
                if (response.getEntity() != null) {
                    // Content-Typeから文字エンコーディングを判定
                    Charset charset = StandardCharsets.UTF_8; // デフォルトエンコーディング
                    if (response.getFirstHeader("Content-Type") != null) {
                        String contentType = response.getFirstHeader("Content-Type").getValue();
                        if (contentType.contains("charset=")) {
                            String encoding = contentType.split("charset=")[1];
                            charset = Charset.forName(encoding);
                        }
                    }
                    responseBody = new String(response.getEntity().getContent().readAllBytes(), charset);
                }
                responseBody = removeEmptyLines(responseBody);
                //                System.out.println("レスポンスボディ:");
                //                System.out.println(responseBody);
                return responseBody;
            }
        } catch (IOException e) {
            LogWriter.write("[ERROR] 接続中にエラー発生: " + e);
            for (StackTraceElement element : e.getStackTrace()) {
                LogWriter.write("    at " + element.toString());
            }
            // 呼び出し元に伝播させる
            throw e;
        }
    }

    /**
     * レスポンスボディから g_sessionid を抽出
     *
     * @param responseBody レスポンスボディ（HTML文字列）
     * @return g_sessionid の値（見つからない場合は null）
     */
    public String extractGSessionId(String responseBody) {
        try {
            if (responseBody.contains("g_sessionid=")) {
                // g_sessionid= の後ろを取得し、余分な部分をトリム
                String src = responseBody.split("g_sessionid=")[1].split("[\"&]")[0].trim();
//                System.out.print("\n    ・抽出した g_sessionid: " + src);
                return src;
            } else {
                LogWriter.write("[ERROR] エラー発生:g_sessionid が見つかりませんでした。");
                System.out.print("\n    ・g_sessionid が見つかりませんでした。");
                return null;
            }
        } catch (Exception e) {
            LogWriter.write("[ERROR] エラー発生: g_sessionid の抽出に失敗しました: " + e);
            for (StackTraceElement element : e.getStackTrace()) {
                LogWriter.write("    at " + element.toString());
            }
            e.printStackTrace();
            System.out.print("\n    ・エラー発生: g_sessionid の抽出に失敗しました。");
            return null;
        }
    }

    public String sendPostRequest(
            String baseUrl,
            List<NameValuePair> postBodyParams
    ) {
//        System.out.println("    ・接続URL: " + baseUrl);
        try {

            // POSTリクエストを作成
            HttpPost post = new HttpPost(baseUrl);

            // POSTボディにフォームデータを追加
            if (postBodyParams != null) {
                post.setEntity(new UrlEncodedFormEntity(postBodyParams, StandardCharsets.UTF_8));
            }

            // 必要なヘッダーを追加
            post.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Safari/537.36");

            // リクエスト送信
            try (CloseableHttpResponse response = httpClient.execute(post, httpContext)) {
                int statusCode = response.getCode();
//                System.out.print("    ・HTTPステータスコード: " + statusCode);

                // レスポンス内容を取得（必要に応じて解析）
                String responseBody = new String(response.getEntity().getContent().readAllBytes(), Charset.forName("Shift_JIS"));
                //    	            System.out.println("レスポンス内容: " + responseBody);

                return responseBody; // ステータスコード200を成功とみなす
            }
        } catch (Exception e) {
            e.printStackTrace();
            LogWriter.write("[ERROR] 接続中にエラー発生: " + e);
            for (StackTraceElement element : e.getStackTrace()) {
                LogWriter.write("    at " + element.toString());
            }
            return null;
        }
    }



    /**
     * HTTPクライアントを閉じる。
     */
    public void close() {
        try {
            httpClient.close();
        } catch (Exception e) {
            e.printStackTrace();
            LogWriter.write("[ERROR] HTTPクライアントクローズ時にエラー発生: " + e);
            for (StackTraceElement element : e.getStackTrace()) {
                LogWriter.write("    at " + element.toString());
            }
        }
    }

    /**
     * 文字列から空行を削除するメソッド
     *
     * @param input 対象の文字列
     * @return 空行が削除された文字列
     */
    private String removeEmptyLines(String input) {
        if (input == null || input.isEmpty()) {
            return input; // 入力が空の場合はそのまま返す
        }

        StringBuilder result = new StringBuilder();
        String[] lines = input.split("\n"); // 行単位で分割

        for (String line : lines) {
            if (!line.trim().isEmpty()) { // 空行（空白のみ含む行）をスキップ
                result.append(line.trim()).append("\n"); // 各行をトリムして追加
            }
        }

        result.append("\n");

        return result.toString(); // 最後の余分な改行もトリム
    }

    /**
     * レスポンスボディからエラーを判定するメソッド
     *
     * @param responseBody レスポンスボディ（HTML文字列）
     * @return エラーが含まれている場合は true、それ以外は false
     */
    public boolean isErrorResponse(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            System.out.println("    ・レスポンスが空です。");
            return true; // 空のレスポンスはエラーとして扱う
        }

        // エラー判定の条件
        boolean containsErrorScript = responseBody.contains("   ・dspErr()");
        boolean containsErrorMessage = responseBody.contains("  ・無効なパラメータを受信しました");

        // 条件のいずれかを満たしている場合はエラー
        if (containsErrorScript || containsErrorMessage) {
            System.out.println("    ・エラー検出！");
            return true;
        }

        System.out.print(" / エラーなし");
        return false;
    }

}
