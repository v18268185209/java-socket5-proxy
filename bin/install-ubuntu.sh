#!/bin/bash
set -euo pipefail

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "Please run as root: sudo bash bin/install-ubuntu.sh"
  exit 1
fi

NO_START="false"
if [[ "${1:-}" == "--no-start" ]]; then
  NO_START="true"
fi

CURRENT_HOME="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_HOME="/opt/proxy-hub"
SERVICE_SRC="$CURRENT_HOME/bin/proxy-hub-ubuntu.service"
SERVICE_DST="/etc/systemd/system/proxy-hub.service"
ENV_FILE="/etc/default/proxy-hub"
APP_USER="proxyhub"
APP_GROUP="proxyhub"

if [[ ! -f "$SERVICE_SRC" ]]; then
  echo "Missing service file: $SERVICE_SRC"
  exit 1
fi

if ! getent group "$APP_GROUP" >/dev/null 2>&1; then
  groupadd --system "$APP_GROUP"
fi

if ! id "$APP_USER" >/dev/null 2>&1; then
  useradd --system --gid "$APP_GROUP" --home-dir "$TARGET_HOME" --shell /usr/sbin/nologin "$APP_USER"
fi

mkdir -p "$CURRENT_HOME/logs" "$CURRENT_HOME/run"

if [[ "$CURRENT_HOME" != "$TARGET_HOME" ]]; then
  ln -sfn "$CURRENT_HOME" "$TARGET_HOME"
fi

cp -f "$SERVICE_SRC" "$SERVICE_DST"

if [[ ! -f "$ENV_FILE" ]]; then
  cat > "$ENV_FILE" << 'EOF'
JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError"
EOF
fi

chown -R "$APP_USER:$APP_GROUP" "$CURRENT_HOME"
if [[ -L "$TARGET_HOME" || -d "$TARGET_HOME" ]]; then
  chown -h "$APP_USER:$APP_GROUP" "$TARGET_HOME" || true
fi

chmod +x "$CURRENT_HOME"/bin/*.sh

systemctl daemon-reload
systemctl enable proxy-hub

if [[ "$NO_START" == "true" ]]; then
  echo "Install finished (no-start mode)."
  echo "You can start manually: systemctl start proxy-hub"
else
  systemctl restart proxy-hub
  systemctl --no-pager --full status proxy-hub || true
fi

echo "Install complete."
echo "Service: proxy-hub"
echo "App home: $CURRENT_HOME"
echo "Symlink : $TARGET_HOME -> $CURRENT_HOME"
