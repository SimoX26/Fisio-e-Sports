#!/usr/bin/env bash
set -euo pipefail
umask 077

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
host="${BACKUP_SSH_HOST:-31.70.74.92}"
ssh_user="${BACKUP_SSH_USER:-root}"
ssh_port="${BACKUP_SSH_PORT:-22}"
db_name="${BACKUP_DB_NAME:-fisio_e_sport}"
db_user="${BACKUP_DB_USER:-root}"
output_dir="${BACKUP_OUTPUT_DIR:-$project_dir/backups}"

usage() {
  cat <<'HELP'
Uso: ./backup-db.sh [opzioni]

Scarica via SSH un dump SQL compresso del database remoto.
Il dump viene salvato solo in locale; sul server non crea file temporanei.

Opzioni:
  --host <host>         Host SSH (default: 31.70.74.92)
  --user <user>         Utente SSH (default: root)
  --port <port>         Porta SSH (default: 22)
  --db-name <name>      Database (default: fisio_e_sport)
  --db-user <user>      Utente DB locale al server (default: root)
  --output-dir <dir>    Directory locale (default: backups/ nel progetto)
  --help                Mostra questo aiuto

Il database è raggiunto dal server tramite socket, senza password DB.
Per SSH usa una chiave, la password richiesta da ssh oppure
DEPLOY_SSH_PASSWORD con sshpass.
HELP
}

error() {
  echo "Errore: $*" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host|--user|--port|--db-name|--db-user|--output-dir)
      [[ $# -ge 2 && -n "$2" ]] || error "manca il valore per $1"
      case "$1" in
        --host) host="$2" ;;
        --user) ssh_user="$2" ;;
        --port) ssh_port="$2" ;;
        --db-name) db_name="$2" ;;
        --db-user) db_user="$2" ;;
        --output-dir) output_dir="$2" ;;
      esac
      shift 2
      ;;
    --help|-h) usage; exit 0 ;;
    *) error "opzione non riconosciuta: $1" ;;
  esac
done

[[ "$host" =~ ^[A-Za-z0-9._:-]+$ && "$host" != -* ]] || error "host SSH non valido"
[[ "$ssh_user" =~ ^[A-Za-z0-9._-]+$ && "$ssh_user" != -* ]] || error "utente SSH non valido"
[[ "$ssh_port" =~ ^[0-9]+$ && "$ssh_port" -ge 1 && "$ssh_port" -le 65535 ]] || error "porta SSH non valida"
[[ "$db_name" =~ ^[A-Za-z0-9_]+$ ]] || error "nome database non valido"
[[ "$db_user" =~ ^[A-Za-z0-9_]+$ ]] || error "utente database non valido"

command -v ssh >/dev/null 2>&1 || error "ssh non trovato"
command -v gzip >/dev/null 2>&1 || error "gzip non trovato"

ssh_command=(ssh)
if [[ -n "${DEPLOY_SSH_PASSWORD:-}" ]]; then
  command -v sshpass >/dev/null 2>&1 || error "sshpass non trovato"
  export SSHPASS="$DEPLOY_SSH_PASSWORD"
  ssh_command=(sshpass -e ssh)
fi

mkdir -p -- "$output_dir"
timestamp="$(date +%Y-%m-%d_%H-%M-%S)"
backup_file="${output_dir%/}/${db_name}_${timestamp}.sql.gz"
[[ ! -e "$backup_file" ]] || error "il backup esiste già: $backup_file"
partial_file="$(mktemp "${output_dir%/}/.${db_name}.XXXXXXXX")"
trap 'rm -f -- "$partial_file"' EXIT

remote_command='IFS= read -r db_user && IFS= read -r db_name && export BACKUP_DB_USER="$db_user" BACKUP_DB_NAME="$db_name" && exec bash -e -u -o pipefail -s'

echo "Connessione SSH a $ssh_user@$host:$ssh_port..." >&2
echo "Se richiesto, inserisci la password SSH; il dump inizierà dopo l'accesso." >&2
{
  printf '%s\n%s\n' "$db_user" "$db_name"
  cat <<'REMOTE_SCRIPT'
if command -v mariadb-dump >/dev/null 2>&1; then
  dump_tool=mariadb-dump
elif command -v mysqldump >/dev/null 2>&1; then
  dump_tool=mysqldump
else
  echo "mariadb-dump o mysqldump non trovato sul server" >&2
  exit 1
fi

echo "Connessione SSH stabilita. Dump del database in corso..." >&2
options=(--single-transaction --quick --routines --events --triggers
         --hex-blob --default-character-set=utf8mb4 --add-drop-database)
help_text="$("$dump_tool" --help 2>/dev/null)"
[[ "$help_text" != *--column-statistics* ]] || options+=(--column-statistics=0)
[[ "$help_text" != *--set-gtid-purged* ]] || options+=(--set-gtid-purged=OFF)
"$dump_tool" "${options[@]}" --protocol=socket --user="$BACKUP_DB_USER" --databases "$BACKUP_DB_NAME"
echo "Dump remoto completato. Verifica del file locale..." >&2
REMOTE_SCRIPT
} | "${ssh_command[@]}" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 -o ServerAliveInterval=15 -o ServerAliveCountMax=3 -p "$ssh_port" "$ssh_user@$host" "$remote_command" | gzip -c > "$partial_file"

[[ -s "$partial_file" ]] || error "dump vuoto"
gzip -t -- "$partial_file" || error "dump compresso non valido"
gzip -dc -- "$partial_file" | awk -v db="$db_name" '
  index($0, "CREATE DATABASE") && index($0, "`" db "`") { create = 1 }
  index($0, "USE `" db "`;") { use_db = 1 }
  END { exit !(create && use_db) }
' || error "dump incompleto: mancano CREATE DATABASE o USE"

mv -- "$partial_file" "$backup_file"
trap - EXIT
echo "Backup verificato: $backup_file"
echo "Dimensione: $(du -h -- "$backup_file" | awk '{print $1}')"
