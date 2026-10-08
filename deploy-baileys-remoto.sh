#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'HELP'
Uso: ./deploy-baileys-remoto.sh [--host HOST] [--user USER] [--port PORT]

Distribuisce solo il gateway Baileys su 31.70.74.92 via SSH (root:22),
lo installa come fisio-baileys.service e conserva la sessione in
/var/lib/fisio-baileys/auth-session. Non modifica backend, WAR o database.
Prima installazione: se esiste una sessione legacy, seguire
docs/operations/whatsapp-baileys.md per trasferirla a servizio fermo.
HELP
}

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
remote_host="31.70.74.92"
remote_user="root"
remote_port="22"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --host) remote_host="${2:?Host mancante}"; shift 2 ;;
    --user) remote_user="${2:?Utente mancante}"; shift 2 ;;
    --port) remote_port="${2:?Porta mancante}"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Opzione sconosciuta: $1" >&2; usage; exit 1 ;;
  esac
done
[[ "$remote_host" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "Host non valido" >&2; exit 1; }
[[ "$remote_user" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "Utente non valido" >&2; exit 1; }
[[ "$remote_port" =~ ^[0-9]+$ ]] && (( remote_port >= 1 && remote_port <= 65535 )) || {
  echo "Porta SSH non valida" >&2; exit 1;
}
for command_name in tar ssh scp; do
  command -v "$command_name" >/dev/null || { echo "Comando richiesto: $command_name" >&2; exit 1; }
done

ssh_command=(ssh)
scp_command=(scp)
if [[ -n "${DEPLOY_SSH_PASSWORD:-}" ]]; then
  command -v sshpass >/dev/null || { echo "sshpass non trovato" >&2; exit 1; }
  export SSHPASS="$DEPLOY_SSH_PASSWORD"
  ssh_command=(sshpass -e ssh)
  scp_command=(sshpass -e scp)
fi
ssh_options=(-o StrictHostKeyChecking=accept-new -o ServerAliveInterval=30 -p "$remote_port")
scp_options=(-o StrictHostKeyChecking=accept-new -o ServerAliveInterval=30 -P "$remote_port")
target="$remote_user@$remote_host"
archive="$(mktemp /tmp/fisio-baileys.XXXXXX.tgz)"
remote_archive="/tmp/fisio-baileys-deploy-$$.tgz"
trap 'rm -f "$archive"' EXIT
tar -C "$project_dir/baileys-service" -czf "$archive" package.json package-lock.json server.js
"${scp_command[@]}" "${scp_options[@]}" "$archive" "$target:$remote_archive"

"${ssh_command[@]}" "${ssh_options[@]}" "$target" "bash -s -- '$remote_archive'" <<'REMOTE'
set -euo pipefail
archive="$1"
code_dir=/opt/fisio-baileys
stage_dir=/opt/fisio-baileys.new
previous_dir=/opt/fisio-baileys.previous
state_dir=/var/lib/fisio-baileys
trap 'rm -f "$archive"' EXIT
[[ "$(id -u)" -eq 0 ]] || { echo "Serve SSH come root" >&2; exit 1; }
for command_name in node npm systemctl curl tar; do
  command -v "$command_name" >/dev/null || { echo "Comando richiesto sul server: $command_name" >&2; exit 1; }
done
node -e 'const [major,minor]=process.versions.node.split(".").map(Number); if(major<20 || (major===20 && minor<9)) process.exit(1)' || {
  echo "Serve Node.js 20.9 o successivo sul server" >&2; exit 1;
}
if [[ ! -f /etc/systemd/system/fisio-baileys.service ]] &&
   curl --silent --fail --max-time 2 http://127.0.0.1:3001/api/status >/dev/null; then
  echo "Porta 3001 già occupata: fermare il vecchio gateway prima della prima installazione" >&2
  exit 1
fi
if [[ -d /opt/baileys-service/auth-session && ! -d "$state_dir/auth-session" ]]; then
  echo "Sessione legacy rilevata: copiarla in $state_dir/auth-session a gateway fermo (vedere la guida)" >&2
  exit 1
fi
[[ ! -e "$stage_dir" && ! -e "$previous_dir" ]] || {
  echo "Directory di deploy temporanea già presente: controllare $stage_dir e $previous_dir" >&2; exit 1;
}
if ! id -u fisio-baileys >/dev/null 2>&1; then
  useradd --system --no-create-home --shell /usr/sbin/nologin fisio-baileys
fi
install -d -o fisio-baileys -g fisio-baileys -m 0700 "$state_dir"
install -d -m 0755 "$stage_dir"
trap 'rm -f "$archive"; rm -rf "$stage_dir"' EXIT
tar -xzf "$archive" -C "$stage_dir"
(cd "$stage_dir" && npm ci --omit=dev)
node_bin="$(command -v node)"

was_installed=false
if [[ -d "$code_dir" ]]; then
  systemctl stop fisio-baileys.service
  mv "$code_dir" "$previous_dir"
  was_installed=true
fi
mv "$stage_dir" "$code_dir"
cat > /etc/systemd/system/fisio-baileys.service <<UNIT
[Unit]
Description=Fisio e Sports WhatsApp gateway
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=fisio-baileys
Group=fisio-baileys
WorkingDirectory=$code_dir
Environment=NODE_ENV=production
Environment=BAILEYS_PRINT_QR_TERMINAL=0
Environment=BAILEYS_PORT=3001
Environment=BAILEYS_SESSION_DIR=$state_dir/auth-session
ExecStart=$node_bin $code_dir/server.js
Restart=always
RestartSec=5
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=$state_dir

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable fisio-baileys.service >/dev/null
systemctl restart fisio-baileys.service
ready=false
for attempt in {1..20}; do
  if systemctl is-active --quiet fisio-baileys.service &&
     curl --silent --fail --max-time 2 http://127.0.0.1:3001/api/status >/dev/null; then
    ready=true
    break
  fi
  sleep 1
done
if [[ "$ready" != true ]]; then
  systemctl stop fisio-baileys.service || true
  if [[ "$was_installed" == true ]]; then
    rm -rf "$code_dir"
    mv "$previous_dir" "$code_dir"
    systemctl start fisio-baileys.service || true
  fi
  echo "Gateway non pronto; vedere journalctl -u fisio-baileys.service" >&2
  exit 1
fi
if [[ "$was_installed" == true ]]; then rm -rf "$previous_dir"; fi
echo "Gateway pronto su http://127.0.0.1:3001/api/status"
REMOTE
