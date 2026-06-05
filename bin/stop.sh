#!/bin/bash
set -euo pipefail

APP_HOME="$(cd "$(dirname "$0")/.." && pwd)"
PID_FILE="$APP_HOME/run/proxy-hub.pid"

if [[ ! -f "$PID_FILE" ]]; then
  echo "No pid file found"
  exit 0
fi

PID="$(cat "$PID_FILE")"
if ! kill -0 "$PID" >/dev/null 2>&1; then
  echo "Process already stopped"
  rm -f "$PID_FILE"
  exit 0
fi

kill "$PID"
for i in {1..20}; do
  if ! kill -0 "$PID" >/dev/null 2>&1; then
    rm -f "$PID_FILE"
    echo "Proxy Hub stopped"
    exit 0
  fi
  sleep 1
done

kill -9 "$PID" || true
rm -f "$PID_FILE"
echo "Proxy Hub force stopped"
