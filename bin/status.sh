#!/bin/bash
set -euo pipefail

APP_HOME="$(cd "$(dirname "$0")/.." && pwd)"
PID_FILE="$APP_HOME/run/proxy-hub.pid"

if [[ ! -f "$PID_FILE" ]]; then
  echo "STOPPED"
  exit 0
fi

PID="$(cat "$PID_FILE")"
if kill -0 "$PID" >/dev/null 2>&1; then
  echo "RUNNING pid=$PID"
else
  echo "STOPPED (stale pid file)"
fi
