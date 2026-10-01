#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'HELP'
Uso: ./deploy-remoto.sh [--host HOST] [--user USER] [--port PORT] [--config FILE] [--skip-build]

Compila e distribuisce solo fisio-backend su 31.70.74.92 via SSH (root:22).
Installa il JAR in /opt/fisio-backend, aggiorna fisio-backend.service e verifica
http://127.0.0.1:8081/ready sul server. Non tocca Tomcat, WAR, Baileys o SQL.

Usa una chiave SSH oppure imposta DEPLOY_SSH_PASSWORD (richiede sshpass).
--config carica config.properties solo alla prima installazione; se esiste già
sul server, viene conservato e --config non è accettato.
HELP
}

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOST="31.70.74.92"
SSH_USER="root"
SSH_PORT="22"
CONFIG_FILE=""
SKIP_BUILD="false"
REMOTE_DIR="/opt/fisio-backend"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host) HOST="${2:?Host mancante}"; shift 2 ;;
    --user) SSH_USER="${2:?Utente mancante}"; shift 2 ;;
    --port) SSH_PORT="${2:?Porta mancante}"; shift 2 ;;
    --config) CONFIG_FILE="${2:?File mancante}"; shift 2 ;;
    --skip-build) SKIP_BUILD="true"; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Opzione sconosciuta: $1" >&2; usage; exit 1 ;;
  esac
done

[[ "$HOST" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "Host non valido" >&2; exit 1; }
[[ "$SSH_USER" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "Utente non valido" >&2; exit 1; }
[[ "$SSH_PORT" =~ ^[0-9]+$ ]] && (( SSH_PORT >= 1 && SSH_PORT <= 65535 )) || {
  echo "Porta SSH non valida" >&2; exit 1;
}
if [[ -n "$CONFIG_FILE" && ! -f "$CONFIG_FILE" ]]; then
  echo "Configurazione non trovata: $CONFIG_FILE" >&2
  exit 1
fi

for required_command in ssh scp; do
  command -v "$required_command" >/dev/null || { echo "Comando richiesto: $required_command" >&2; exit 1; }
done
if [[ "$SKIP_BUILD" != "true" ]]; then
  command -v mvn >/dev/null || { echo "Maven non trovato" >&2; exit 1; }
  (cd "$PROJECT_DIR" && mvn -pl fisio-backend -am package)
fi

JAR="$PROJECT_DIR/fisio-backend/target/fisio-backend-0.1.0.jar"
[[ -f "$JAR" ]] || { echo "JAR backend non trovato: $JAR" >&2; exit 1; }

SSH=(ssh)
SCP=(scp)
if [[ -n "${DEPLOY_SSH_PASSWORD:-}" ]]; then
  command -v sshpass >/dev/null || { echo "sshpass non trovato" >&2; exit 1; }
  export SSHPASS="$DEPLOY_SSH_PASSWORD"
  SSH=(sshpass -e ssh)
  SCP=(sshpass -e scp)
fi
SSH_OPTS=(-o StrictHostKeyChecking=accept-new -o ServerAliveInterval=30 -p "$SSH_PORT")
SCP_OPTS=(-o StrictHostKeyChecking=accept-new -o ServerAliveInterval=30 -P "$SSH_PORT")
TARGET="$SSH_USER@$HOST"
STAGED_JAR="$REMOTE_DIR/.fisio-backend-$$.jar"

echo ">> Preparo $REMOTE_DIR su $TARGET"
"${SSH[@]}" "${SSH_OPTS[@]}" "$TARGET" "test \$(id -u) -eq 0 && install -d -m 0750 '$REMOTE_DIR'"

echo ">> Carico il JAR backend"
"${SCP[@]}" "${SCP_OPTS[@]}" "$JAR" "$TARGET:$STAGED_JAR"

if [[ -n "$CONFIG_FILE" ]]; then
  echo ">> Carico la configurazione iniziale"
  "${SSH[@]}" "${SSH_OPTS[@]}" "$TARGET" "umask 077; cat > '$REMOTE_DIR/.config.new'" < "$CONFIG_FILE"
fi

echo ">> Installo e avvio fisio-backend.service"
"${SSH[@]}" "${SSH_OPTS[@]}" "$TARGET" \
  "bash -s -- '$REMOTE_DIR' '$STAGED_JAR' '$([[ -n "$CONFIG_FILE" ]] && echo yes || echo no)'" <<'REMOTE'
set -euo pipefail
remote_dir="$1"
staged_jar="$2"
has_config="$3"
trap 'rm -f "$staged_jar" "$remote_dir/.config.new"' EXIT

command -v systemctl >/dev/null || { echo "systemd non disponibile" >&2; exit 1; }
command -v curl >/dev/null || { echo "curl non disponibile" >&2; exit 1; }
java_bin="$(command -v java)"
[[ -n "$java_bin" ]] || { echo "Java non installato sul server" >&2; exit 1; }

if ! id -u fisio-backend >/dev/null 2>&1; then
  useradd --system --no-create-home --shell /usr/sbin/nologin fisio-backend
fi
install -d -o root -g fisio-backend -m 0750 "$remote_dir"

if [[ "$has_config" == yes ]]; then
  if [[ -e "$remote_dir/config.properties" ]]; then
    echo "config.properties esiste già sul server: non lo sovrascrivo" >&2
    exit 1
  fi
  install -o root -g fisio-backend -m 0640 "$remote_dir/.config.new" "$remote_dir/config.properties"
fi
[[ -s "$remote_dir/config.properties" ]] || {
  echo "Manca $remote_dir/config.properties: passa --config per la prima installazione" >&2
  exit 1
}
chown root:fisio-backend "$remote_dir/config.properties"
chmod 0640 "$remote_dir/config.properties"

had_previous=false
if [[ -f "$remote_dir/fisio-backend.jar" ]]; then
  cp -p "$remote_dir/fisio-backend.jar" "$remote_dir/fisio-backend.jar.previous"
  had_previous=true
fi
install -o root -g fisio-backend -m 0644 "$staged_jar" "$remote_dir/fisio-backend.jar.new"
mv -f "$remote_dir/fisio-backend.jar.new" "$remote_dir/fisio-backend.jar"

cat > /etc/systemd/system/fisio-backend.service <<UNIT
[Unit]
Description=Fisio e Sports backend
After=network.target

[Service]
Type=simple
User=fisio-backend
Group=fisio-backend
WorkingDirectory=$remote_dir
Environment=FISIO_BACKEND_PORT=8081
ExecStart=$java_bin -jar $remote_dir/fisio-backend.jar
Restart=on-failure
RestartSec=3
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable fisio-backend.service >/dev/null
if systemctl restart fisio-backend.service; then
  for attempt in {1..15}; do
    if systemctl is-active --quiet fisio-backend.service &&
       curl --silent --fail --max-time 3 http://127.0.0.1:8081/ready | grep -q '"status":"ok"'; then
      echo "Backend pronto su http://127.0.0.1:8081/ready"
      exit 0
    fi
    sleep 2
  done
fi

echo "Backend non pronto; consultare: journalctl -u fisio-backend.service -n 50" >&2
if [[ "$had_previous" == true ]]; then
  cp -p "$remote_dir/fisio-backend.jar.previous" "$remote_dir/fisio-backend.jar.new"
  mv -f "$remote_dir/fisio-backend.jar.new" "$remote_dir/fisio-backend.jar"
  systemctl restart fisio-backend.service
  echo "JAR precedente ripristinato" >&2
fi
exit 1
REMOTE
