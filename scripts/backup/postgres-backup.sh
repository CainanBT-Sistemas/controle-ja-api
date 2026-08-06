#!/usr/bin/env bash

set -euo pipefail

readonly BACKUP_PREFIX="controleja-postgres-"
readonly RETENTION_DAYS="${RETENTION_DAYS:-90}"
readonly RETENTION_DRY_RUN="${RETENTION_DRY_RUN:-false}"

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

require_env AGE_PUBLIC_KEY
require_env BACKUP_REMOTE_PATH
require_env RAILWAY_DB_URL
require_env RCLONE_CONFIG_CONTENT

for required_command in age grep pg_dump pg_restore rclone sha256sum; do
  require_command "$required_command"
done

if [[ "$RETENTION_DAYS" != "90" ]]; then
  printf 'RETENTION_DAYS must remain 90 for OPS-004.\n' >&2
  exit 1
fi

if [[ "$RETENTION_DRY_RUN" != "true" && "$RETENTION_DRY_RUN" != "false" ]]; then
  printf 'RETENTION_DRY_RUN must be true or false.\n' >&2
  exit 1
fi

if [[ "$BACKUP_REMOTE_PATH" != *:* ]]; then
  printf 'BACKUP_REMOTE_PATH must use the rclone remote:path format.\n' >&2
  exit 1
fi

umask 077
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/controleja-backup.XXXXXX")"
readonly WORK_DIR

cleanup() {
  rm -rf -- "$WORK_DIR"
}
trap cleanup EXIT HUP INT TERM

TIMESTAMP="$(date -u +'%Y%m%dT%H%M%SZ')"
readonly TIMESTAMP
readonly BASE_NAME="${BACKUP_PREFIX}${TIMESTAMP}"
readonly DUMP_NAME="${BASE_NAME}.dump"
readonly ENCRYPTED_NAME="${DUMP_NAME}.age"
readonly CHECKSUM_NAME="${ENCRYPTED_NAME}.sha256"
readonly DUMP_FILE="${WORK_DIR}/${DUMP_NAME}"
readonly ENCRYPTED_FILE="${WORK_DIR}/${ENCRYPTED_NAME}"
readonly RCLONE_CONFIG_FILE="${WORK_DIR}/rclone.conf"

printf '%s\n' "$RCLONE_CONFIG_CONTENT" > "$RCLONE_CONFIG_FILE"
chmod 600 "$RCLONE_CONFIG_FILE"
unset RCLONE_CONFIG_CONTENT

export PGDATABASE="$RAILWAY_DB_URL"
export PGCONNECT_TIMEOUT="20"
unset RAILWAY_DB_URL

printf 'Creating PostgreSQL custom-format backup.\n'
pg_dump \
  --format=custom \
  --no-owner \
  --no-privileges \
  --file="$DUMP_FILE"

if [[ ! -s "$DUMP_FILE" ]]; then
  printf 'The generated PostgreSQL backup is empty.\n' >&2
  exit 1
fi

printf 'Validating PostgreSQL backup catalog.\n'
pg_restore --list "$DUMP_FILE" >/dev/null

printf 'Encrypting PostgreSQL backup.\n'
age --encrypt --recipient "$AGE_PUBLIC_KEY" --output "$ENCRYPTED_FILE" "$DUMP_FILE"
unset AGE_PUBLIC_KEY
rm -f -- "$DUMP_FILE"

if [[ ! -s "$ENCRYPTED_FILE" ]]; then
  printf 'The encrypted PostgreSQL backup is empty.\n' >&2
  exit 1
fi

(
  cd "$WORK_DIR"
  sha256sum "$ENCRYPTED_NAME" > "$CHECKSUM_NAME"
)

printf 'Uploading encrypted backup and checksum.\n'
rclone copy "$WORK_DIR" "$BACKUP_REMOTE_PATH" \
  --config "$RCLONE_CONFIG_FILE" \
  --include "$ENCRYPTED_NAME" \
  --include "$CHECKSUM_NAME" \
  --exclude "*" \
  --max-depth 1 \
  --transfers 2 \
  --checkers 4

rclone lsf "$BACKUP_REMOTE_PATH" \
  --config "$RCLONE_CONFIG_FILE" \
  --files-only \
  --max-depth 1 \
  --include "$ENCRYPTED_NAME" | grep -Fxq "$ENCRYPTED_NAME"

rclone lsf "$BACKUP_REMOTE_PATH" \
  --config "$RCLONE_CONFIG_FILE" \
  --files-only \
  --max-depth 1 \
  --include "$CHECKSUM_NAME" | grep -Fxq "$CHECKSUM_NAME"

retention_arguments=(
  delete "$BACKUP_REMOTE_PATH"
  --config "$RCLONE_CONFIG_FILE"
  --min-age "${RETENTION_DAYS}d"
  --max-depth 1
  --include "${BACKUP_PREFIX}*.dump.age"
  --include "${BACKUP_PREFIX}*.dump.age.sha256"
  --exclude "*"
)

if [[ "$RETENTION_DRY_RUN" == "true" ]]; then
  retention_arguments+=(--dry-run)
  printf 'Simulating 90-day retention.\n'
else
  printf 'Applying 90-day retention.\n'
fi

rclone "${retention_arguments[@]}"

printf 'Encrypted PostgreSQL backup completed successfully: %s\n' "$ENCRYPTED_NAME"
