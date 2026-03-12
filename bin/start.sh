#!/bin/bash
set -euo pipefail

APP_HOME="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$APP_HOME/logs"
RUN_DIR="$APP_HOME/run"
CONF_FILE="$APP_HOME/conf/application-prod.yml"
JAR_FILE="$(ls -1 "$APP_HOME"/target/proxy-hub-*.jar 2>/dev/null | head -n 1)"
PID_FILE="$RUN_DIR/proxy-hub.pid"

if [[ -z "${JAR_FILE}" ]]; then
  echo "No jar found under $APP_HOME/target. Run: mvn clean package -DskipTests"
  exit 1
fi

mkdir -p "$LOG_DIR" "$RUN_DIR"

if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" >/dev/null 2>&1; then
  echo "Proxy Hub already running, pid=$(cat "$PID_FILE")"
  exit 0
fi

JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError}"

nohup java $JAVA_OPTS \
  -DLOG_HOME="$LOG_DIR" \
  -jar "$JAR_FILE" \
  --spring.config.location="file:$CONF_FILE" \
  > "$LOG_DIR/console.out" 2>&1 &

PID=$!
echo "$PID" > "$PID_FILE"
sleep 1

if kill -0 "$PID" >/dev/null 2>&1; then
  echo "Proxy Hub started, pid=$PID"
else
  echo "Proxy Hub start failed"
  rm -f "$PID_FILE"
  exit 1
fi
