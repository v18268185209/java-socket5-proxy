#!/bin/bash
set -euo pipefail

APP_HOME="$(cd "$(dirname "$0")/.." && pwd)"
"$APP_HOME/bin/stop.sh"
"$APP_HOME/bin/start.sh"
