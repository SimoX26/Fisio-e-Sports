#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven non trovato. Installa Maven e un JDK 21 o successivo." >&2
  exit 1
fi
cd "$project_dir"
export GDK_SCALE=2
exec mvn -pl fisio-desktop javafx:run
