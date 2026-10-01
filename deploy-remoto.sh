#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'HELP'
Uso:
  ./deploy-remoto.sh [opzioni]

Descrizione:
  Builda il progetto Maven, carica il WAR su server remoto via sshpass/scp
  e lo deploya in Tomcat con staging remoto. Carica anche baileys-service
  in /opt/baileys-service.

Opzioni:
  --host <host>            Host remoto (default: 31.70.74.92)
  --user <user>            Utente SSH (default: root)
  --password <password>    Password SSH (default: da variabile DEPLOY_SSH_PASSWORD)
  --port <port>            Porta SSH (default: 22)
  --remote-path <path>     Cartella deploy remota (default: ~/)
  --tomcat-webapps <path>  Cartella webapps Tomcat (default: /opt/tomcat/webapps)
  --baileys-path <path>    Cartella remota baileys-service (default: /opt/baileys-service)
  --baileys-owner <owner>  Owner remoto baileys-service (default: auto)
  --install-baileys-deps   Esegue npm install per baileys-service sul server remoto
  --with-sql               Carica la cartella migration su server remoto (destinazione: ~/)
  --war <path>             WAR locale da deployare (default: ultimo in fisio-web-legacy/target/)
  --skip-build             Salta mvn clean package
  --help                   Mostra questo aiuto

Esempi:
  ./deploy-remoto.sh
  ./deploy-remoto.sh --with-sql
  ./deploy-remoto.sh --install-baileys-deps
  ./deploy-remoto.sh --password '***'
HELP
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Errore: comando richiesto non trovato: $1" >&2
    exit 1
  fi
}

HOST="31.70.74.92"
USER="root"
PASSWORD="${DEPLOY_SSH_PASSWORD:-}"
PORT="22"
REMOTE_PATH="~/"
TOMCAT_WEBAPPS_PATH="/opt/tomcat/webapps"
BAILEYS_REMOTE_PATH="/opt/baileys-service"
BAILEYS_OWNER="auto"
WAR_PATH=""
SKIP_BUILD="false"
WITH_SQL="false"
INSTALL_BAILEYS_DEPS="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)
      HOST="${2:-}"
      shift 2
      ;;
    --user)
      USER="${2:-}"
      shift 2
      ;;
    --password)
      PASSWORD="${2:-}"
      shift 2
      ;;
    --port)
      PORT="${2:-}"
      shift 2
      ;;
    --remote-path)
      REMOTE_PATH="${2:-}"
      shift 2
      ;;
    --tomcat-webapps)
      TOMCAT_WEBAPPS_PATH="${2:-}"
      shift 2
      ;;
    --baileys-path)
      BAILEYS_REMOTE_PATH="${2:-}"
      shift 2
      ;;
    --baileys-owner)
      BAILEYS_OWNER="${2:-}"
      shift 2
      ;;
    --with-sql)
      WITH_SQL="true"
      shift
      ;;
    --install-baileys-deps)
      INSTALL_BAILEYS_DEPS="true"
      shift
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

require_cmd sshpass
require_cmd ssh
require_cmd scp
require_cmd tar

if [[ -z "$PASSWORD" ]]; then
  echo "Errore: password SSH mancante. Usa --password o DEPLOY_SSH_PASSWORD." >&2
  exit 1
fi

TARGET="${USER}@${HOST}"
SSH_OPTS=(-o StrictHostKeyChecking=accept-new -o ServerAliveInterval=30 -o ServerAliveCountMax=6 -p "$PORT")
SCP_OPTS=(-o StrictHostKeyChecking=accept-new -o ServerAliveInterval=30 -o ServerAliveCountMax=6 -P "$PORT")

echo ">> Verifico cartella remota: $REMOTE_PATH"
sshpass -p "$PASSWORD" ssh "${SSH_OPTS[@]}" "$TARGET" "mkdir -p ${REMOTE_PATH}"

echo ">> Upload WAR verso ${TARGET}:${REMOTE_PATH}/"
sshpass -p "$PASSWORD" scp "${SCP_OPTS[@]}" "$WAR_PATH" "$TARGET:${REMOTE_PATH%/}/$WAR_NAME"

BAILEYS_DIR="baileys-service"
if [[ ! -d "$BAILEYS_DIR" ]]; then
  echo "Errore: cartella non trovata: $BAILEYS_DIR" >&2
  exit 1
fi

BAILEYS_ARCHIVE="/tmp/baileys-service-deploy.tar.gz"
echo ">> Preparo archivio baileys-service"
tar \
  --exclude='node_modules' \
  --exclude='auth-session' \
  --exclude='baileys-service.pid' \
  --exclude='baileys-service.log' \
  -czf "$BAILEYS_ARCHIVE" \
  "$BAILEYS_DIR"

echo ">> Upload baileys-service verso ${TARGET}:${REMOTE_PATH%/}/baileys-service-deploy.tar.gz"
sshpass -p "$PASSWORD" scp "${SCP_OPTS[@]}" "$BAILEYS_ARCHIVE" "$TARGET:${REMOTE_PATH%/}/baileys-service-deploy.tar.gz"

echo ">> Deploy WAR in Tomcat webapps: $TOMCAT_WEBAPPS_PATH"
sshpass -p "$PASSWORD" ssh "${SSH_OPTS[@]}" "$TARGET" "
  set -e
  BAILEYS_PARENT_DIR=\$(dirname '${BAILEYS_REMOTE_PATH%/}')
  BAILEYS_OWNER='${BAILEYS_OWNER}'
  echo '   - preparo Tomcat webapps'
  mkdir -p '$TOMCAT_WEBAPPS_PATH'
  cp -f ${REMOTE_PATH%/}/$WAR_NAME '${TOMCAT_WEBAPPS_PATH%/}/$WAR_NAME'
  find '$TOMCAT_WEBAPPS_PATH' -maxdepth 1 -type f -name '${APP_CONTEXT}*.war' ! -name '$WAR_NAME' -delete
  echo '   - estraggo baileys-service'
  rm -rf '${BAILEYS_REMOTE_PATH%/}'
  mkdir -p \"\$BAILEYS_PARENT_DIR\"
  tar -xzf ${REMOTE_PATH%/}/baileys-service-deploy.tar.gz -C \"\$BAILEYS_PARENT_DIR\"
  chmod +x '${BAILEYS_REMOTE_PATH%/}/start-baileys.sh'
  chmod +x '${BAILEYS_REMOTE_PATH%/}/stop-baileys.sh'
  echo '   - preparo permessi baileys-service'
  if [ \"\$BAILEYS_OWNER\" = 'auto' ]; then
    TOMCAT_USER=\$(ps -eo user,args | awk '/[o]rg.apache.catalina.startup.Bootstrap/ {print \$1; exit}')
    if [ -z \"\$TOMCAT_USER\" ] && [ -d '$TOMCAT_WEBAPPS_PATH' ]; then
      TOMCAT_USER=\$(stat -c '%U' '$TOMCAT_WEBAPPS_PATH')
    fi
    if [ -n \"\$TOMCAT_USER\" ] && id \"\$TOMCAT_USER\" >/dev/null 2>&1; then
      TOMCAT_GROUP=\$(id -gn \"\$TOMCAT_USER\")
      BAILEYS_OWNER=\"\$TOMCAT_USER:\$TOMCAT_GROUP\"
      BAILEYS_RUN_USER=\"\$TOMCAT_USER\"
    else
      BAILEYS_OWNER=''
      BAILEYS_RUN_USER=''
    fi
  else
    BAILEYS_RUN_USER=\${BAILEYS_OWNER%%:*}
  fi
  if [ -n \"\$BAILEYS_OWNER\" ]; then
    chown -R \"\$BAILEYS_OWNER\" '${BAILEYS_REMOTE_PATH%/}'
  fi
  chmod -R u+rwX,go+rX,go-w '${BAILEYS_REMOTE_PATH%/}'
  if [ '${INSTALL_BAILEYS_DEPS}' = 'true' ] && command -v npm >/dev/null 2>&1; then
    echo '   - installo dipendenze npm baileys-service'
    if [ -n \"\$BAILEYS_RUN_USER\" ] && id \"\$BAILEYS_RUN_USER\" >/dev/null 2>&1 && command -v runuser >/dev/null 2>&1; then
      if command -v timeout >/dev/null 2>&1; then
        timeout 300 runuser -u \"\$BAILEYS_RUN_USER\" -- bash -lc \"cd '${BAILEYS_REMOTE_PATH%/}' && if [ -f package-lock.json ]; then npm ci --omit=dev; else npm install --omit=dev; fi\"
      else
        runuser -u \"\$BAILEYS_RUN_USER\" -- bash -lc \"cd '${BAILEYS_REMOTE_PATH%/}' && if [ -f package-lock.json ]; then npm ci --omit=dev; else npm install --omit=dev; fi\"
      fi
    else
      if command -v timeout >/dev/null 2>&1; then
        timeout 300 bash -lc 'cd '"'"'${BAILEYS_REMOTE_PATH%/}'"'"' && if [ -f package-lock.json ]; then npm ci --omit=dev; else npm install --omit=dev; fi'
      else
        bash -lc 'cd '"'"'${BAILEYS_REMOTE_PATH%/}'"'"' && if [ -f package-lock.json ]; then npm ci --omit=dev; else npm install --omit=dev; fi'
      fi
    fi
  elif [ '${INSTALL_BAILEYS_DEPS}' = 'true' ]; then
    echo 'ATTENZIONE: npm non trovato sul server. Installa nodejs/npm prima di avviare WhatsApp.'
  else
    echo '   - salto installazione dipendenze npm baileys-service (usa --install-baileys-deps se necessario)'
  fi
  echo '   - pulizia staging baileys-service'
  rm -f ${REMOTE_PATH%/}/baileys-service-deploy.tar.gz
"

if [[ "$WITH_SQL" == "true" ]]; then
  MIGRATION_DIR="fisio-web-legacy/src/main/resources/migrations"

  if [[ ! -d "$MIGRATION_DIR" ]]; then
    echo ">> Cartella migration non trovata: $MIGRATION_DIR"
  else
    echo ">> Upload cartella migration verso ${TARGET}:~/"
    sshpass -p "$PASSWORD" scp -r "${SCP_OPTS[@]}" "$MIGRATION_DIR" "$TARGET:~/"
    echo ">> Cartella migration caricata: ~/migrations"
  fi
fi

echo ">> Cartella esplosa ${TOMCAT_WEBAPPS_PATH%/}/${APP_CONTEXT} non rimossa manualmente (gestita da Tomcat)."

APP_URL="http://${HOST}:8080/${APP_CONTEXT}/"
if [[ "$APP_CONTEXT" == "ROOT" ]]; then
  APP_URL="http://${HOST}:8080/"
fi

echo ">> Deploy remoto completato."
echo ">> URL applicativo: $APP_URL"
