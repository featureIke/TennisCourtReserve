package api;

import log.LogWriter;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
public class LineNotify {
    private static String escapeForJson(String str) {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "");
    }
    /// LINEトークンを外部ファイル（lineToken.txt）から取得
    private static final String TOKEN = util.IOUtil.readSingleLine("lineToken.txt");

    public static void sendNotification(String message) {
        LogWriter.write("[INFO] LINE通知送信開始");
        LogWriter.write("[INFO] 通知内容: " + message);
        try {
            message = message.stripTrailing(); // 空白・改行を末尾から除去
            URL url = new URL("https://api.line.me/v2/bot/message/push");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // 基本設定
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + TOKEN);
            conn.setDoOutput(true);

            // 宛先とメッセージ内容（自分のユーザーIDを入れる）
            String userId = util.IOUtil.readSingleLine("lineUserId.txt");
            String escapedMessage = escapeForJson(message);
            String jsonBody = """
                    {
                      "to": "%s",
                      "messages":[
                        {
                          "type":"text",
                          "text":"%s"
                        }
                      ]
                    }
                    """.formatted(userId, escapedMessage);

            // 送信
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            System.out.println("LINE通知送信: " + responseCode);

        } catch (Exception e) {
            System.out.println("[ERROR] LINE通知送信失敗: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
