#!/usr/bin/env bash

set -euo pipefail

require_env() {
  local variable_name="$1"
  if [[ -z "${!variable_name:-}" ]]; then
    printf 'Required environment variable is missing: %s\n' "$variable_name" >&2
    exit 1
  fi
}

require_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    printf 'Required command is not available: %s\n' "$command_name" >&2
    exit 1
  fi
}

require_env AGE_SECRET_KEY_FILE
require_env BACKUP_CHECKSUM_FILE
require_env BACKUP_FILE

for required_command in age awk pg_restore sha256sum; do
  require_command "$required_command"
done

for required_file in "$AGE_SECRET_KEY_FILE" "$BACKUP_CHECKSUM_FILE" "$BACKUP_FILE"; do
  if [[ ! -f "$required_file" ]]; then
    printf 'Required restore file was not found.\n' >&2
    exit 1
  fi
done

umask 077
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/controleja-restore.XXXXXX")"
readonly WORK_DIR

cleanup() {
  rm -rf -- "$WORK_DIR"
}
trap cleanup EXIT HUP INT TERM

EXPECTED_CHECKSUM="$(awk 'NR == 1 { print $1 }' "$BACKUP_CHECKSUM_FILE")"
readonly EXPECTED_CHECKSUM
ACTUAL_CHECKSUM="$(sha256sum "$BACKUP_FILE" | awk '{ print $1 }')"
readonly ACTUAL_CHECKSUM

if [[ ! "$EXPECTED_CHECKSUM" =~ ^[[:xdigit:]]{64}$ ]]; then
  printf 'The backup checksum file is invalid.\n' >&2
  exit 1
fi

if [[ "$EXPECTED_CHECKSUM" != "$ACTUAL_CHECKSUM" ]]; then
  printf 'The encrypted backup checksum does not match.\n' >&2
  exit 1
fi

readonly DECRYPTED_DUMP="${WORK_DIR}/controleja-restore.dump"
printf 'Decrypting backup into an isolated temporary directory.\n'
age --decrypt --identity "$AGE_SECRET_KEY_FILE" --output "$DECRYPTED_DUMP" "$BACKUP_FILE"

if [[ ! -s "$DECRYPTED_DUMP" ]]; then
  printf 'The decrypted PostgreSQL backup is empty.\n' >&2
  exit 1
fi

printf 'Validating PostgreSQL backup catalog.\n'
pg_restore --list "$DECRYPTED_DUMP" >/dev/null

if [[ -z "${RESTORE_DB_URL:-}" ]]; then
  printf 'Backup checksum, decryption and catalog validation completed successfully.\n'
  exit 0
fi

if [[ "${RESTORE_CONFIRMATION:-}" != "ISOLATED_TEMPORARY_DATABASE" ]]; then
  printf 'Refusing restore without explicit isolated database confirmation.\n' >&2
  exit 1
fi

require_command psql

export PGDATABASE="$RESTORE_DB_URL"
export PGCONNECT_TIMEOUT="20"
unset RESTORE_DB_URL

USER_TABLE_COUNT="$(psql -v ON_ERROR_STOP=1 -Atqc "
  SELECT count(*)
  FROM pg_catalog.pg_class c
  JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
  WHERE c.relkind = 'r'
    AND n.nspname NOT IN ('pg_catalog', 'information_schema');
")"
readonly USER_TABLE_COUNT

if [[ "$USER_TABLE_COUNT" != "0" ]]; then
  printf 'Refusing restore because the target database is not empty.\n' >&2
  exit 1
fi

printf 'Restoring into the confirmed isolated database.\n'
pg_restore \
  --exit-on-error \
  --no-owner \
  --no-privileges \
  "$DECRYPTED_DUMP" | psql -v ON_ERROR_STOP=1

readonly REQUIRED_TABLES=(
  users accounts category credit_cards vehicles gas_stations
  gas_station_rankings recurrence_rules transactions invoicess installment_plan
  closed_test_testers flyway_schema_history
)

for table_name in "${REQUIRED_TABLES[@]}"; do
  if [[ "$(psql -v ON_ERROR_STOP=1 -Atqc "SELECT to_regclass('public.${table_name}') IS NOT NULL;")" != "t" ]]; then
    printf 'Restore validation failed because a required table is missing.\n' >&2
    exit 1
  fi
done

if [[ "$(psql -v ON_ERROR_STOP=1 -Atqc "SELECT EXISTS (SELECT 1 FROM flyway_schema_history WHERE success IS NOT TRUE);")" != "f" ]]; then
  printf 'Restore validation found an unsuccessful Flyway migration.\n' >&2
  exit 1
fi

if [[ "$(psql -v ON_ERROR_STOP=1 -Atqc "SELECT EXISTS (SELECT 1 FROM pg_constraint WHERE contype = 'f' AND convalidated IS NOT TRUE);")" != "f" ]]; then
  printf 'Restore validation found an unvalidated foreign key.\n' >&2
  exit 1
fi

printf 'Isolated restore and structural validation completed successfully.\n'
