#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
config_file="$project_dir/fisio-backend/config.properties"

if [[ ! -f "$config_file" ]]; then
  echo "Configurazione locale non trovata: $config_file" >&2
  exit 1
fi

cd "$project_dir"
mvn -pl fisio-backend -am -DskipTests package

# Usa la configurazione privata del backend; le variabili DB non la sovrascrivono.
unset FISIO_DB_URL FISIO_DB_USER FISIO_DB_PASSWORD FISIO_DB_DRIVER
export FISIO_DB_CONFIG_FILE="$config_file"
export FISIO_BACKEND_PORT="${FISIO_BACKEND_PORT:-8081}"
exec java -jar fisio-backend/target/fisio-backend-0.1.0.jar
