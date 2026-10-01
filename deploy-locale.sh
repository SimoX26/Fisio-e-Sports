#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'HELP'
Uso:
  ./deploy-locale.sh [opzioni]

Descrizione:
  Builda il progetto Maven e deploya il WAR in Tomcat locale.

Opzioni:
  --tomcat-webapps <path>  Cartella webapps Tomcat locale (default: /home/simone/apache-tomcat-9.0.112/webapps)
  --local-server <host>    Host locale per URL finale (default: localhost)
  --local-port <port>      Porta locale Tomcat per URL finale (default: 8080)
  --war <path>             WAR locale da deployare (default: ultimo in fisio-web-legacy/target/)
  --skip-build             Salta mvn clean package
  --help                   Mostra questo aiuto

Esempi:
  ./deploy-locale.sh
  ./deploy-locale.sh --tomcat-webapps /home/simone/apache-tomcat-9.0.112/webapps
  ./deploy-locale.sh --skip-build
HELP
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Errore: comando richiesto non trovato: $1" >&2
    exit 1
  fi
}

TOMCAT_WEBAPPS_PATH="/home/simone/apache-tomcat-9.0.112/webapps"
LOCAL_SERVER="localhost"
LOCAL_PORT="8080"
WAR_PATH=""
SKIP_BUILD="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tomcat-webapps)
      TOMCAT_WEBAPPS_PATH="${2:-}"
      shift 2
      ;;
    --local-server)
      LOCAL_SERVER="${2:-}"
      shift 2
      ;;
    --local-port)
      LOCAL_PORT="${2:-}"
      shift 2
      ;;
    --war)
      WAR_PATH="${2:-}"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD="true"
      shift
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

require_cmd mvn

if [[ "$SKIP_BUILD" != "true" ]]; then
  echo ">> Build Maven: mvn clean package"
  mvn clean package
fi

if [[ -z "$WAR_PATH" ]]; then
  WAR_PATH="$(ls -t fisio-web-legacy/target/*.war 2>/dev/null | head -n 1 || true)"
fi

if [[ -z "$WAR_PATH" || ! -f "$WAR_PATH" ]]; then
  echo "Errore: nessun file WAR trovato. Specifica --war oppure esegui la build." >&2
  exit 1
fi

WAR_NAME="$(basename "$WAR_PATH")"
APP_CONTEXT="${WAR_NAME%.war}"

echo ">> WAR selezionato: $WAR_PATH"
echo ">> Deploy locale in Tomcat webapps: $TOMCAT_WEBAPPS_PATH"
mkdir -p "$TOMCAT_WEBAPPS_PATH"
cp -f "$WAR_PATH" "${TOMCAT_WEBAPPS_PATH%/}/$WAR_NAME"
find "$TOMCAT_WEBAPPS_PATH" -maxdepth 1 -type f -name "${APP_CONTEXT}*.war" ! -name "$WAR_NAME" -delete

echo ">> Cartella esplosa ${TOMCAT_WEBAPPS_PATH%/}/${APP_CONTEXT} non rimossa manualmente (gestita da Tomcat)."
echo ">> Deploy locale completato."
echo ">> URL applicativo: http://${LOCAL_SERVER}:${LOCAL_PORT}/${APP_CONTEXT}/"
echo ">> Servizio WhatsApp: avvia ./baileys-service/start-baileys.sh e apri http://localhost:3001/api/qr"
if [[ "$APP_CONTEXT" == "ROOT" ]]; then
  echo ">> URL applicativo: http://${LOCAL_SERVER}:${LOCAL_PORT}/"
fi
