#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'HELP'
Uso:
  ./deploy-apk.sh [opzioni]

Descrizione:
  Genera APK Android debug (senza installazione).
  URL predefinito: IP LAN rilevato sul computer, porta 8081

Opzioni:
  --android-url <url>     URL API backend per build APK (FISIO_API_BASE_URL)
  --apk-output-dir <dir>  Cartella output APK locale (default: /home/simone/Scaricati)
  --help                  Mostra questo aiuto

Esempi:
  ./deploy-apk.sh
  ./deploy-apk.sh --android-url http://192.168.1.50:8081
HELP
}

ANDROID_DIR="android-app"
APK_DIR="${ANDROID_DIR}/app/build/outputs/apk/debug"
BASE_APK_NAME="FisioESport.apk"

ANDROID_URL=""
APK_OUTPUT_DIR="/home/simone/Scaricati"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --android-url)
      ANDROID_URL="${2:-}"
      shift 2
      ;;
    --apk-output-dir)
      APK_OUTPUT_DIR="${2:-}"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Argomento non riconosciuto: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ ! -d "$ANDROID_DIR" ]]; then
  echo "Errore: cartella Android non trovata: $ANDROID_DIR" >&2
  exit 1
fi

if [[ -z "$ANDROID_URL" ]]; then
  if command -v ip >/dev/null 2>&1; then
    LAN_IP="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{ for (i = 1; i <= NF; i++) if ($i == "src") { print $(i + 1); exit } }')"
  fi
  if [[ -z "${LAN_IP:-}" ]]; then
    echo 'IP LAN non rilevato. Usa --android-url http://<IP_LAN>:8081' >&2
    exit 1
  fi
  ANDROID_URL="http://${LAN_IP}:8081"
fi
echo ">> Build APK con backend URL: $ANDROID_URL"
(
  cd "$ANDROID_DIR"
  ./gradlew assembleDebug "-PFISIO_API_BASE_URL=${ANDROID_URL}"
)

APK_FILE="${APK_DIR}/${BASE_APK_NAME}"
if [[ -z "$APK_FILE" || ! -f "$APK_FILE" ]]; then
  echo "Errore: APK non trovato dopo la build in: $APK_FILE" >&2
  exit 1
fi

mkdir -p "$APK_OUTPUT_DIR"

DEST_APK="${APK_OUTPUT_DIR%/}/${BASE_APK_NAME}"
cp -f "$APK_FILE" "$DEST_APK"
APK_FILE="$DEST_APK"
APK_ABS_PATH="$(cd "$(dirname "$APK_FILE")" && pwd)/$(basename "$APK_FILE")"

echo ">> APK generato: $APK_FILE"
echo ">> Percorso filesystem: $APK_ABS_PATH"
