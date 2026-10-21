#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$project_root"
version="${1:-1.0.4}"
if [[ ! "$version" =~ ^[0-9]+(\.[0-9]+){0,3}$ ]]; then
    echo 'Versione non valida. Esempio: ./build-desktop-exe-linux.sh 1.0.1' >&2
    exit 2
fi
if ! command -v gh >/dev/null 2>&1; then
    echo 'Installa GitHub CLI (gh) e accedi con: gh auth login' >&2
    exit 1
fi

branch="$(git -C "$project_root" branch --show-current)"
if [[ -z "$branch" ]]; then
    echo 'La build richiede un branch Git, non un checkout detached.' >&2
    exit 1
fi
if [[ -n "$(git -C "$project_root" status --porcelain)" ]]; then
    echo 'Ci sono modifiche locali: crea un commit e fai push prima della build remota.' >&2
    exit 1
fi
head_commit="$(git -C "$project_root" rev-parse HEAD)"
remote_commit="$(git -C "$project_root" ls-remote --heads origin "$branch" | cut -f1)"
if [[ "$head_commit" != "$remote_commit" ]]; then
    echo "Il commit corrente non è su origin/$branch. Esegui git push prima della build." >&2
    exit 1
fi

gh auth status --hostname github.com >/dev/null
request_id="$(date -u +%Y%m%dT%H%M%SZ)-$$-$RANDOM"
gh workflow run build-desktop-windows.yml --ref "$branch" \
    -f "version=$version" -f "request_id=$request_id"

run_id=''
for attempt in {1..30}; do
    run_id="$(gh run list --workflow build-desktop-windows.yml --branch "$branch" \
        --event workflow_dispatch --limit 30 --json databaseId,displayTitle \
        --jq ".[] | select(.displayTitle == \"Desktop Windows $version ($request_id)\") | .databaseId")"
    if [[ -n "$run_id" ]]; then break; fi
    sleep 2
done
if [[ -z "$run_id" ]]; then
    echo 'Build avviata, ma non ancora visibile. Controlla GitHub Actions.' >&2
    exit 1
fi

gh run watch "$run_id" --exit-status
output_dir="$project_root/fisio-desktop/target/windows-dist/$version/github-$run_id"
mkdir -p "$output_dir"
gh run download "$run_id" --name "fisio-desktop-windows-$version" --dir "$output_dir"

manifest="$output_dir/desktop-update.properties"
installer="$output_dir/Fisio e Sports - installer.exe"
database_installer="$output_dir/Fisio e Sports - database.exe"
if [[ ! -f "$manifest" || ! -f "$installer" || ! -f "$database_installer" ]]; then
    echo 'Artefatti della build incompleti.' >&2
    exit 1
fi
manifest_version="$(sed -n 's/^version=//p' "$manifest" | tr -d '\r')"
expected_hash="$(sed -n 's/^sha256=//p' "$manifest" | tr -d '\r')"
actual_hash="$(sha256sum "$installer" | cut -d' ' -f1)"
database_hash="$(sed -n 's/^databaseSha256=//p' "$manifest" | tr -d '\r')"
actual_database_hash="$(sha256sum "$database_installer" | cut -d' ' -f1)"
if [[ "$manifest_version" != "$version" || ! "$expected_hash" =~ ^[0-9a-f]{64}$ || "$actual_hash" != "$expected_hash" || ! "$database_hash" =~ ^[0-9a-f]{64}$ || "$actual_database_hash" != "$database_hash" ]]; then
    echo 'Versione o SHA-256 degli artefatti non corrispondente.' >&2
    exit 1
fi
echo "Installer verificato: $installer"
downloads_dir="$HOME/Scaricati"
mkdir -p "$downloads_dir"
downloads_installer="$downloads_dir/Fisio e Sports - installer.exe"
cp -f "$installer" "$downloads_installer"
if [[ "$(sha256sum "$downloads_installer" | cut -d' ' -f1)" != "$expected_hash" ]]; then
    echo 'SHA-256 della copia in Scaricati non corrispondente.' >&2
    exit 1
fi
downloads_database_installer="$downloads_dir/Fisio e Sports - database.exe"
cp -f "$database_installer" "$downloads_database_installer"
if [[ "$(sha256sum "$downloads_database_installer" | cut -d' ' -f1)" != "$database_hash" ]]; then
    echo 'SHA-256 dell’installer database in Scaricati non corrispondente.' >&2
    exit 1
fi
echo "Installer copiato in: $downloads_installer"
echo "Installer database copiato in: $downloads_database_installer"
