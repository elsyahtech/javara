#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG_FILE="${JAVARA_BACKUP_CONFIG:-$ROOT_DIR/config/backup.env}"
export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
umask 077

if [[ "$(uname -s)" == "Darwin" ]] && command -v brew >/dev/null 2>&1; then
    libpq_bin="$(brew --prefix libpq 2>/dev/null || true)"
    if [[ -x "$libpq_bin/bin/pg_dump" ]]; then
        export PATH="$libpq_bin/bin:$PATH"
    fi
fi

if [[ "${1:-}" == "--scheduled" ]]; then
    mkdir -p "$ROOT_DIR/config"
    if [[ -L "$ROOT_DIR/config/backup.log" ]]; then
        printf '%s\n' "Scheduled backup log must not be a symbolic link." >&2
        exit 1
    fi
    : >> "$ROOT_DIR/config/backup.log"
    chmod 600 "$ROOT_DIR/config/backup.log"
    exec >> "$ROOT_DIR/config/backup.log" 2>&1
fi

if [[ ! -f "$CONFIG_FILE" || -L "$CONFIG_FILE" || ! -O "$CONFIG_FILE" ]]; then
    printf 'Backup configuration is missing or not owned by the current user: %s\n' \
        "$CONFIG_FILE" >&2
    exit 1
fi

if [[ "$(uname -s)" == "Darwin" ]]; then
    config_mode="$(stat -f '%Lp' "$CONFIG_FILE")"
else
    config_mode="$(stat -c '%a' "$CONFIG_FILE")"
fi
if [[ "$config_mode" != "600" ]]; then
    printf '%s\n' "Backup configuration must have file mode 600." >&2
    exit 1
fi

# The file is generated with shell-escaped values by backup-setup.sh and kept
# owner-readable only.
# shellcheck disable=SC1090
source "$CONFIG_FILE"

for required_setting in DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD DB_SSLMODE \
        BACKUP_FORMAT BACKUP_DESTINATION BACKUP_DIRECTORY S3_URI AWS_PROFILE \
        S3_ENDPOINT_URL S3_SSE BACKUP_RETENTION_DAYS TOKEN_GRACE_DAYS; do
    if ! declare -p "$required_setting" >/dev/null 2>&1; then
        printf 'Backup configuration is missing %s.\n' "$required_setting" >&2
        exit 1
    fi
done
if [[ ! "$DB_PORT" =~ ^[0-9]{1,5}$ ]] || (( 10#$DB_PORT < 1 || 10#$DB_PORT > 65535 )); then
    printf '%s\n' "Backup configuration has an invalid PostgreSQL port." >&2
    exit 1
fi
for retention_value in "$BACKUP_RETENTION_DAYS" "$TOKEN_GRACE_DAYS"; do
    if [[ ! "$retention_value" =~ ^[0-9]+$ ]] \
            || (( 10#$retention_value < 1 || 10#$retention_value > 3650 )); then
        printf '%s\n' "Backup retention values must be between 1 and 3650 days." >&2
        exit 1
    fi
done
case "$DB_SSLMODE" in
    disable|allow|prefer|require|verify-ca|verify-full) ;;
    *)
        printf '%s\n' "Backup configuration has an invalid PostgreSQL SSL mode." >&2
        exit 1
        ;;
esac
case "$BACKUP_DESTINATION" in
    filesystem|s3|both) ;;
    *)
        printf '%s\n' "Backup configuration has an invalid destination." >&2
        exit 1
        ;;
esac
if [[ "$BACKUP_DESTINATION" == "filesystem" || "$BACKUP_DESTINATION" == "both" ]] \
        && [[ -z "$BACKUP_DIRECTORY" || "$BACKUP_DIRECTORY" != /* ]]; then
    printf '%s\n' "Backup configuration requires an absolute filesystem destination." >&2
    exit 1
fi
if [[ "$BACKUP_DESTINATION" == "s3" || "$BACKUP_DESTINATION" == "both" ]] \
        && [[ ! "$S3_URI" =~ ^s3://[A-Za-z0-9.-]+(/[A-Za-z0-9._/-]+)?$ \
            || "$S3_URI" =~ (^|/)\.\.?(/|$) ]]; then
    printf '%s\n' "Backup configuration has an invalid S3 destination URI." >&2
    exit 1
fi
if [[ "$S3_SSE" != "AES256" && "$S3_SSE" != "bucket-default" ]]; then
    printf '%s\n' "Backup configuration has an invalid S3 encryption mode." >&2
    exit 1
fi

required_tools=(pg_dump psql)
if [[ "$BACKUP_FORMAT" == "custom" ]]; then
    required_tools+=(pg_restore)
fi
for tool in "${required_tools[@]}"; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        printf 'Required PostgreSQL client tool is missing: %s\n' "$tool" >&2
        exit 1
    fi
done
if [[ "$BACKUP_DESTINATION" == "s3" || "$BACKUP_DESTINATION" == "both" ]] \
        && ! command -v aws >/dev/null 2>&1; then
    printf '%s\n' "AWS CLI is required for an S3 backup destination." >&2
    exit 1
fi

if [[ "$BACKUP_FORMAT" == "custom" ]]; then
    extension="dump"
    dump_format="custom"
elif [[ "$BACKUP_FORMAT" == "plain" ]]; then
    extension="sql"
    dump_format="plain"
else
    printf 'Unsupported backup format: %s\n' "$BACKUP_FORMAT" >&2
    exit 1
fi

backup_cutoff="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
backup_stamp="$(date -u +'%Y%m%dT%H%M%SZ')"
filename="javara-backup-${backup_stamp}-$$.${extension}"
checksum_filename="${filename}.sha256"
temporary_root="${TMPDIR:-/tmp}"
if [[ "$BACKUP_DESTINATION" == "filesystem" || "$BACKUP_DESTINATION" == "both" ]]; then
    mkdir -p "$BACKUP_DIRECTORY"
    temporary_root="$BACKUP_DIRECTORY"
fi
temporary_directory="$(mktemp -d "$temporary_root/.javara-backup.XXXXXX")"
local_publish_directory=""
trap 'rm -rf "$temporary_directory"; if [[ -n "$local_publish_directory" ]]; then rm -f "$local_publish_directory"; fi' EXIT

dump_file="$temporary_directory/$filename"
checksum_file="$temporary_directory/$checksum_filename"
printf 'Creating %s backup of database %s...\n' "$BACKUP_FORMAT" "$DB_NAME"

PGPASSWORD="$DB_PASSWORD" \
    PGCONNECT_TIMEOUT=10 \
    PGSSLMODE="$DB_SSLMODE" \
    pg_dump \
    --no-password \
    --host="$DB_HOST" \
    --port="$DB_PORT" \
    --username="$DB_USER" \
    --dbname="$DB_NAME" \
    --format="$dump_format" \
    --file="$dump_file"

if [[ ! -s "$dump_file" ]]; then
    printf '%s\n' "pg_dump completed without producing a backup file." >&2
    exit 1
fi
if [[ "$BACKUP_FORMAT" == "custom" ]]; then
    pg_restore --list "$dump_file" >/dev/null
elif ! grep -q '^-- PostgreSQL database dump$' "$dump_file"; then
    printf '%s\n' "The plain SQL dump did not contain the expected PostgreSQL dump header." >&2
    exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
    sha256_file() { sha256sum "$1" | awk '{print $1}'; }
else
    sha256_file() { shasum -a 256 "$1" | awk '{print $1}'; }
fi
checksum="$(sha256_file "$dump_file")"
printf '%s  %s\n' "$checksum" "$filename" > "$checksum_file"
dump_size="$(wc -c < "$dump_file" | tr -d '[:space:]')"

if [[ "$BACKUP_DESTINATION" == "filesystem" || "$BACKUP_DESTINATION" == "both" ]]; then
    local_publish_directory="$BACKUP_DIRECTORY/.${filename}.tmp"
    mkdir -p "$BACKUP_DIRECTORY"
    cp "$dump_file" "$local_publish_directory"
    chmod 600 "$local_publish_directory"
    mv "$local_publish_directory" "$BACKUP_DIRECTORY/$filename"
    local_publish_directory="$BACKUP_DIRECTORY/.${checksum_filename}.tmp"
    cp "$checksum_file" "$local_publish_directory"
    chmod 600 "$local_publish_directory"
    mv "$local_publish_directory" "$BACKUP_DIRECTORY/$checksum_filename"
    local_publish_directory=""
    if [[ "$(sha256_file "$BACKUP_DIRECTORY/$filename")" != "$checksum" ]]; then
        printf '%s\n' "Local backup checksum verification failed; cleanup will not run." >&2
        exit 1
    fi
    printf 'Verified local backup: %s/%s\n' "$BACKUP_DIRECTORY" "$filename"
fi

if [[ "$BACKUP_DESTINATION" == "s3" || "$BACKUP_DESTINATION" == "both" ]]; then
    aws_options=()
    if [[ -n "$AWS_PROFILE" ]]; then
        aws_options+=(--profile "$AWS_PROFILE")
    fi
    if [[ -n "$S3_ENDPOINT_URL" ]]; then
        aws_options+=(--endpoint-url "$S3_ENDPOINT_URL")
    fi
    s3_copy_options=("${aws_options[@]+"${aws_options[@]}"}")
    if [[ "$S3_SSE" == "AES256" ]]; then
        s3_copy_options+=(--sse AES256)
    fi
    destination="${S3_URI%/}/$filename"
    checksum_destination="${S3_URI%/}/$checksum_filename"
    aws s3 cp --only-show-errors \
        "${s3_copy_options[@]+"${s3_copy_options[@]}"}" "$dump_file" "$destination"
    aws s3 cp --only-show-errors \
        "${s3_copy_options[@]+"${s3_copy_options[@]}"}" "$checksum_file" "$checksum_destination"
    s3_bucket="${S3_URI#s3://}"
    s3_bucket="${s3_bucket%%/*}"
    s3_key="${destination#s3://$s3_bucket/}"
    remote_size="$(aws s3api head-object \
        --bucket "$s3_bucket" \
        --key "$s3_key" \
        --query ContentLength \
        --output text \
        "${aws_options[@]+"${aws_options[@]}"}")"
    if [[ "$remote_size" != "$dump_size" ]]; then
        printf '%s\n' "S3 backup size verification failed; cleanup will not run." >&2
        exit 1
    fi
    if command -v sha256sum >/dev/null 2>&1; then
        remote_checksum="$(aws s3 cp --only-show-errors \
            "${aws_options[@]+"${aws_options[@]}"}" "$destination" - \
            | sha256sum | awk '{print $1}')"
    else
        remote_checksum="$(aws s3 cp --only-show-errors \
            "${aws_options[@]+"${aws_options[@]}"}" "$destination" - \
            | shasum -a 256 | awk '{print $1}')"
    fi
    if [[ "$remote_checksum" != "$checksum" ]]; then
        printf '%s\n' "S3 backup checksum verification failed; cleanup will not run." >&2
        exit 1
    fi
    printf 'Verified S3 backup: %s\n' "$destination"
fi

retention_cutoff_epoch() {
    if [[ "$(uname -s)" == "Darwin" ]]; then
        date -u -v-"$BACKUP_RETENTION_DAYS"d +%s
    else
        date -u -d "$BACKUP_RETENTION_DAYS days ago" +%s
    fi
}

date_to_epoch() {
    if [[ "$(uname -s)" == "Darwin" ]]; then
        date -u -j -f '%Y-%m-%d %H:%M:%S' "$1 $2" +%s
    else
        date -u -d "$1 $2" +%s
    fi
}

prune_local_backups() {
    local cutoff_epoch file modified_epoch
    cutoff_epoch="$(retention_cutoff_epoch)"
    for file in "$BACKUP_DIRECTORY"/javara-backup-*.dump \
            "$BACKUP_DIRECTORY"/javara-backup-*.sql \
            "$BACKUP_DIRECTORY"/javara-backup-*.sha256; do
        [[ -f "$file" ]] || continue
        if [[ "$(uname -s)" == "Darwin" ]]; then
            modified_epoch="$(stat -f '%m' "$file")"
        else
            modified_epoch="$(stat -c '%Y' "$file")"
        fi
        if (( modified_epoch < cutoff_epoch )); then
            rm "$file"
        fi
    done
}

prune_s3_backups() {
    local cutoff_epoch date_value time_value size_value key object_epoch s3_bucket s3_listing
    cutoff_epoch="$(retention_cutoff_epoch)"
    s3_bucket="${S3_URI#s3://}"
    s3_bucket="${s3_bucket%%/*}"
    s3_listing="$(aws s3 ls --recursive "${S3_URI%/}/" \
        "${aws_options[@]+"${aws_options[@]}"}")"
    while read -r date_value time_value size_value key; do
        [[ "$date_value" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || continue
        case "$key" in
            javara-backup-*.dump|javara-backup-*.sql|javara-backup-*.dump.sha256|javara-backup-*.sql.sha256)
                ;;
            */javara-backup-*.dump|*/javara-backup-*.sql|*/javara-backup-*.dump.sha256|*/javara-backup-*.sql.sha256)
                ;;
            *)
                continue
                ;;
        esac
        object_epoch="$(date_to_epoch "$date_value" "$time_value")"
        if (( object_epoch < cutoff_epoch )); then
            aws s3 rm --only-show-errors \
                "${aws_options[@]+"${aws_options[@]}"}" "s3://$s3_bucket/$key"
        fi
    done <<< "$s3_listing"
}

if [[ "$BACKUP_DESTINATION" == "filesystem" || "$BACKUP_DESTINATION" == "both" ]]; then
    prune_local_backups
fi
if [[ "$BACKUP_DESTINATION" == "s3" || "$BACKUP_DESTINATION" == "both" ]]; then
    prune_s3_backups
fi

printf '%s\n' "Backup verified at all selected destinations; cleaning expired refresh tokens..."
deleted_total=0
while true; do
    deleted_batch="$(
        PGPASSWORD="$DB_PASSWORD" \
            PGCONNECT_TIMEOUT=10 \
            PGSSLMODE="$DB_SSLMODE" \
            psql \
                --no-password \
                --no-psqlrc \
                --set ON_ERROR_STOP=1 \
                --tuples-only \
                --no-align \
                --host="$DB_HOST" \
                --port="$DB_PORT" \
                --username="$DB_USER" \
                --dbname="$DB_NAME" \
                --set backup_cutoff="$backup_cutoff" \
                --set token_grace_days="$TOKEN_GRACE_DAYS" \
                --file="$ROOT_DIR/scripts/backup-cleanup.sql"
    )"
    if [[ ! "$deleted_batch" =~ ^[0-9]+$ ]]; then
        printf 'Cleanup returned an unexpected row count: %s\n' "$deleted_batch" >&2
        exit 1
    fi
    (( deleted_batch == 0 )) && break
    deleted_total=$((deleted_total + deleted_batch))
done
printf 'Removed %s expired refresh-token rows after verified backup.\n' "$deleted_total"
