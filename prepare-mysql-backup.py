#!/usr/bin/env python3
"""Prepare a MariaDB backup for the Windows MySQL installer."""

import argparse
import gzip
import os
from pathlib import Path
import re
import tempfile


ROOT = Path(__file__).resolve().parent
MIGRATION = ROOT / "fisio-web-legacy/src/main/resources/migrations/2026-10-01_patient_therapist_ownership.sql"
SANDBOX_HEADER = "/*M!999999\\- enable the sandbox mode */"


def prepare(source: Path, destination: Path) -> None:
    if not source.is_file() or not source.name.endswith(".sql.gz"):
        raise ValueError("Il backup di origine deve essere un file .sql.gz esistente.")
    if source.resolve() == destination.resolve():
        raise ValueError("Il backup originale non può essere sovrascritto.")
    if destination.exists():
        raise ValueError(f"Il file di destinazione esiste già: {destination}")

    migration = MIGRATION.read_bytes()
    if b"SET @fallback_therapist_username = 'marco';" not in migration:
        raise ValueError("La migrazione non assegna più le schede senza riferimenti a marco.")

    fd, temp_name = tempfile.mkstemp(prefix=".mysql-backup-", suffix=".sql.gz", dir=destination.parent)
    os.close(fd)
    temp = Path(temp_name)
    try:
        database = False
        selected = False
        patients = False
        patients_schema = False
        already_migrated = False
        migration_present = False
        with gzip.open(source, "rb") as original, gzip.open(temp, "wb") as prepared:
            for number, line in enumerate(original, 1):
                if number == 1 and line.decode("ascii", errors="replace").strip() == SANDBOX_HEADER:
                    continue  # MariaDB client directive rejected by the MySQL client.
                if re.search(rb"CREATE DATABASE\b[^\n]*`fisio_e_sport`", line):
                    database = True
                if re.match(rb"USE `fisio_e_sport`;", line):
                    selected = True
                if line.startswith(b"SET @fallback_therapist_username = 'marco';"):
                    migration_present = True
                if line.startswith(b"CREATE TABLE `patients` ("):
                    patients = True
                    patients_schema = True
                elif patients_schema:
                    if b"`therapist_id`" in line:
                        already_migrated = True
                    if line.startswith(b") ENGINE="):
                        patients_schema = False
                prepared.write(line)

            if not (database and selected and patients):
                raise ValueError("Il backup non contiene lo schema fisio_e_sport atteso.")
            if already_migrated or migration_present:
                raise ValueError("Il backup contiene già la migrazione: non applicabile una seconda volta.")
            prepared.write(b"\n-- Migrazione proprieta pazienti per la versione locale Windows\n")
            prepared.write(b"USE `fisio_e_sport`;\n")
            prepared.write(migration)
            prepared.write(b"\n")
        with gzip.open(temp, "rb") as check:
            while check.read(1024 * 1024):
                pass
        os.link(temp, destination)  # Fail if another process created the destination.
    finally:
        temp.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("backup", type=Path, help="Backup originale .sql.gz")
    parser.add_argument("output", nargs="?", type=Path, help="Nuovo backup .sql.gz")
    args = parser.parse_args()
    source = args.backup
    destination = args.output or source.with_name(source.name.removesuffix(".sql.gz") + ".mysql-ready.sql.gz")
    try:
        prepare(source, destination)
    except (OSError, ValueError) as error:
        parser.exit(1, f"Errore: {error}\n")
    print(f"Backup pronto per l'installer Windows: {destination}")


if __name__ == "__main__":
    main()
