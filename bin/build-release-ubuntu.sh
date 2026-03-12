#!/bin/bash
set -euo pipefail

APP_HOME="$(cd "$(dirname "$0")/.." && pwd)"
DIST_DIR="$APP_HOME/dist"

SKIP_BUILD="${SKIP_BUILD:-false}"
if [[ "${1:-}" == "--skip-build" ]]; then
  SKIP_BUILD="true"
fi

if [[ "$SKIP_BUILD" != "true" ]]; then
  echo "[build-release-ubuntu] mvn -q -DskipTests package"
  (cd "$APP_HOME" && mvn -q -DskipTests package)
fi

JAR_FILE="$(ls -1 "$APP_HOME"/target/proxy-hub-*.jar 2>/dev/null | grep -v '\.original$' | head -n 1)"
if [[ -z "$JAR_FILE" ]]; then
  echo "No runnable jar found in target/"
  exit 1
fi

JAR_NAME="$(basename "$JAR_FILE")"
VERSION="${JAR_NAME#proxy-hub-}"
VERSION="${VERSION%.jar}"
RELEASE_NAME="proxy-hub-${VERSION}-ubuntu"
RELEASE_DIR="$DIST_DIR/$RELEASE_NAME"
ARCHIVE="$DIST_DIR/${RELEASE_NAME}.tar.gz"

rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR"/{bin,conf,target,logs,run}

cp "$JAR_FILE" "$RELEASE_DIR/target/"
cp "$APP_HOME/conf/application-prod.yml" "$RELEASE_DIR/conf/"
cp "$APP_HOME/README.md" "$RELEASE_DIR/"
cp "$APP_HOME/bin/start.sh" "$RELEASE_DIR/bin/"
cp "$APP_HOME/bin/stop.sh" "$RELEASE_DIR/bin/"
cp "$APP_HOME/bin/restart.sh" "$RELEASE_DIR/bin/"
cp "$APP_HOME/bin/status.sh" "$RELEASE_DIR/bin/"
cp "$APP_HOME/bin/install-ubuntu.sh" "$RELEASE_DIR/bin/"
cp "$APP_HOME/bin/proxy-hub-ubuntu.service" "$RELEASE_DIR/bin/"

cat > "$RELEASE_DIR/DEPLOY-UBUNTU.txt" << EOF
Proxy Hub Ubuntu release package

1. Upload and extract:
   tar -xzf ${RELEASE_NAME}.tar.gz -C /opt
   cd /opt/${RELEASE_NAME}

2. Configure:
   vi conf/application-prod.yml

3. Install service (recommended):
   chmod +x bin/*.sh
   sudo bash bin/install-ubuntu.sh

4. Runtime operations:
   systemctl status proxy-hub
   systemctl restart proxy-hub
   journalctl -u proxy-hub -f

5. Optional no-start install:
   sudo bash bin/install-ubuntu.sh --no-start
EOF

chmod +x "$RELEASE_DIR/bin/"*.sh

mkdir -p "$DIST_DIR"
rm -f "$ARCHIVE"
tar -czf "$ARCHIVE" -C "$DIST_DIR" "$RELEASE_NAME"

echo "[build-release-ubuntu] package ready:"
echo "  $ARCHIVE"
