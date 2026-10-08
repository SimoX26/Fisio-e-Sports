#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
PID_FILE="$SCRIPT_DIR/baileys-service.pid"
export PATH="/usr/local/bin:/usr/bin:/bin:${PATH:-}"

if [ -f "$PID_FILE" ]; then
  EXISTING_PID="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [ -n "${EXISTING_PID}" ] && kill -0 "$EXISTING_PID" 2>/dev/null; then
    exit 0
  fi
  rm -f "$PID_FILE"
fi

if [ "${BAILEYS_RESET_SESSION:-0}" = "1" ]; then
  rm -rf auth-session
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "[baileys-service] npm non trovato nel PATH: $PATH" >&2
  exit 127
fi

if ! command -v node >/dev/null 2>&1; then
  echo "[baileys-service] node non trovato nel PATH: $PATH" >&2
  exit 127
fi
if ! node -e 'const [major,minor]=process.versions.node.split(".").map(Number); if(major<20 || (major===20 && minor<9)) process.exit(1)'; then
  echo "[baileys-service] serve Node.js 20.9 o successivo" >&2
  exit 1
fi

install_dependencies() {
  if [ -f package-lock.json ]; then
    npm ci --omit=dev
  else
    npm install --omit=dev
  fi
}

if [ ! -d node_modules ] || [ ! -f node_modules/@whiskeysockets/baileys/package.json ]; then
  echo "[baileys-service] Dipendenze mancanti o incomplete, reinstallazione in corso..."
  install_dependencies
fi

echo $$ > "$PID_FILE"
trap 'rm -f "$PID_FILE"' EXIT
exec node server.js
