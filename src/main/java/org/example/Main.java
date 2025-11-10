package org.example;

import api.LineNotify;
import connection.ConnectionService;
import log.LogWriter;
import log.LoggerUtil;
import log.TeeOutputStream;
import java.util.List;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

//TIP コードを<b>実行</b>するには、<shortcut actionId="Run"/> を押すか
// ガターの <icon src="AllIcons.Actions.Execute"/> アイコンをクリックします。
public class Main {
    public static void main(String[] args) {
        // 現在時刻を取得してフォーマットする
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        // ログファイルに実行開始時刻を出力
        LogWriter.write("");
        LogWriter.write("▼▼▼ 実行開始: " + now + " ▼▼▼");

        //予約Service親処理実行
        ConnectionService service = new ConnectionService();
        List<Exception> errors = service.reservationParent();
        // 時間取得
        int hour = LocalDateTime.now().getHour();
        if (!errors.isEmpty()) {
            LogWriter.write("⚠️ 例外が発生しました（" + errors.size() + "件）");
            StringBuilder sb = new StringBuilder();
            sb.append("⚠️ 自動予約で例外が発生しました\\n");
            sb.append("件数: ").append(errors.size()).append("\\n");
            int idx = 1;
            for (Exception e : errors) {
                // 1行で読みやすく。長すぎる本文は適度に切り詰め
                String msg = (e.getMessage() == null) ? e.toString() : e.getMessage();
                if (msg.length() > 120) msg = msg.substring(0, 120) + "...";
                LogWriter.write("[ERROR] (#" + idx + ") " + e.getClass().getSimpleName() + ": " + msg);
                sb.append("#").append(idx).append(" ")
                  .append(e.getClass().getSimpleName()).append(": ")
                  .append(msg).append("\\n");
                idx++;
                if (idx > 5) { // LINE通知は5件までに抑制
                    sb.append("他 ").append(errors.size() - 5).append(" 件");
                    break;
                }
            }
            // LINE通知送信
            if (hour == 10 || hour == 12 || hour == 15 || hour == 18)
            LineNotify.sendNotification(sb.toString());
        }else{
            // 正常動作確認用LINE通知
            if (hour == 12) {
                LineNotify.sendNotification("正常動作👌");
            }
        }
        LogWriter.write("▲▲▲ 実行完了: " + now + " ▲▲▲");


    }
}