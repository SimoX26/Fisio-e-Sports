#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven non trovato. Installa Maven e un JDK 21 o successivo." >&2
  exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "curl non trovato: serve per verificare il backend locale." >&2
  exit 1
fi
if [[ "$(curl --silent --max-time 3 http://127.0.0.1:8081/ready || true)" != '{"status":"ok"}' ]]; then
  echo "Backend non pronto su http://127.0.0.1:8081/ready." >&2
  echo "Avvialo in un altro terminale con ./run-backend-locale.sh." >&2
  exit 1
fi

cd "$project_dir"
export GDK_SCALE=2
exec mvn -pl fisio-desktop javafx:run
