#!/bin/zsh
# エラー即終了(-e)、未定義変数でエラー(-u)、パイプ途中の失敗も検知(pipefail)
set -euo pipefail
export PATH="/usr/bin:/bin:/usr/sbin:/sbin:/opt/homebrew/bin"

# 1) スクリプトのある場所を基準にする
SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"

# 2) WORKDIR の決定
#    - 環境変数 RESV_WORKDIR があればそれを優先
#    - なければ SCRIPT_DIR/dist が存在すれば dist を採用
#    - それもなければ SCRIPT_DIR 自体を採用
if [ "${RESV_WORKDIR:-}" != "" ]; then
  WORKDIR="$RESV_WORKDIR"
elif [ -d "$SCRIPT_DIR/dist" ]; then
  WORKDIR="$SCRIPT_DIR/dist"
else
  WORKDIR="$SCRIPT_DIR"
fi

# 3) Java 実行ファイル（Homebrew を優先、無ければ macOS 標準）
JAVA="/opt/homebrew/opt/openjdk/bin/java"
[ -x "$JAVA" ] || JAVA="/usr/bin/java"

# 4) ログパス
STDOUT_LOG="$WORKDIR/stdout.log"
STDERR_LOG="$WORKDIR/stderr.log"
LOCKFILE="$WORKDIR/.start-with-log.lock"

# 5) 必要ディレクトリ
mkdir -p "$WORKDIR" "$WORKDIR/log"

# 6) 起動マーカー（plist→sh 到達確認）
echo "$(date '+%Y-%m-%d %H:%M:%S') [LAUNCHD] script STARTED (WORKDIR=$WORKDIR)" >> "$STDOUT_LOG"
echo "==== 起動 $(date '+%Y-%m-%d %H:%M:%S') ====" >> "$STDOUT_LOG"
echo "==== 起動 $(date '+%Y-%m-%d %H:%M:%S') ====" >> "$STDERR_LOG"

# 7) 二重起動ガード（任意）
if [ -e "$LOCKFILE" ]; then
  if pkill -0 -F "$LOCKFILE" 2>/dev/null; then
    echo "$(date '+%Y-%m-%d %H:%M:%S') [WARN] already running (lock present)" >> "$STDOUT_LOG"
    exit 0
  fi
fi
echo "$$" > "$LOCKFILE" 2>/dev/null || true

# 8) 最新 JAR を探す（with-dependencies を最優先）
find_latest_with_deps() { ls -t "$WORKDIR"/*-jar-with-dependencies.jar 2>/dev/null | head -n 1 || true; }
find_latest_any()       { ls -t "$WORKDIR"/*.jar                     2>/dev/null | head -n 1 || true; }

# current.jar を最新 with-deps に張り直す（あれば）
latest_jar="$(find_latest_with_deps)"
if [ -n "$latest_jar" ]; then
  rm -f "$WORKDIR/current.jar"
  ln -s "$latest_jar" "$WORKDIR/current.jar"
  echo "$(date '+%Y-%m-%d %H:%M:%S') [INFO] linked current.jar → $(basename "$latest_jar")" >> "$STDOUT_LOG"
fi

# 実行対象 JAR の確定
JAR="$(find_latest_with_deps)"
if [ -z "${JAR:-}" ]; then
  JAR="$(find_latest_any)"
  if [ -z "${JAR:-}" ]; then
    echo "$(date '+%Y-%m-%d %H:%M:%S') [ERROR] JAR not found in $WORKDIR" >> "$STDERR_LOG"
    rm -f "$LOCKFILE" 2>/dev/null || true
    exit 2
  fi
  echo "$(date '+%Y-%m-%d %H:%M:%S') [WARN] using fallback JAR: $(basename "$JAR")" >> "$STDOUT_LOG"
fi

# 9) 念のため隔離属性除去（無ければ無視）
xattr -d com.apple.quarantine "$JAR" 2>/dev/null || true

# 10) 実行前ログ
echo "$(date '+%Y-%m-%d %H:%M:%S') [INFO] JAVA: $JAVA" >> "$STDOUT_LOG"
echo "$(date '+%Y-%m-%d %H:%M:%S') [INFO] JAR : $JAR"  >> "$STDOUT_LOG"

# 11) Java 実行可能チェック
if [ ! -x "$JAVA" ]; then
  echo "$(date '+%Y-%m-%d %H:%M:%S') [ERROR] JAVA not executable: $JAVA" >> "$STDERR_LOG"
  rm -f "$LOCKFILE" 2>/dev/null || true
  exit 3
fi

# 12) 作業ディレクトリへ移動（相対参照対策）
cd "$WORKDIR"

# 13) 実行（exec でプロセス置換：終了コードがそのまま launchd に伝わる）
exec "$JAVA" -jar "$JAR" >> "$STDOUT_LOG" 2>> "$STDERR_LOG"

# 14) 通常到達しないが保険
rm -f "$LOCKFILE" 2>/dev/null || true
