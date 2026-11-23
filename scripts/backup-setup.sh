#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
CONFIG_DIR="$ROOT_DIR/config"
CONFIG_FILE="$CONFIG_DIR/backup.env"
CRON_MARKER="# JAVARA_BACKUP_SCHEDULER"

shell_quote() {
    local escaped
    escaped="$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"
    printf "'%s'" "$escaped"
}

remove_managed_cron() {
    local old_crontab crontab_file
    old_crontab="$(crontab -l 2>/dev/null || true)"
    if [[ "$old_crontab" != *"$CRON_MARKER"* ]]; then
        return
    fi
    crontab_file="$(mktemp)"
    printf '%s\n' "$old_crontab" | sed "\|$CRON_MARKER\$|d" > "$crontab_file"
    crontab "$crontab_file"
    rm -f "$crontab_file"
}

read_value() {
    local prompt=$1 default_value=$2 value
    read -r -p "$prompt [$default_value]: " value
    printf '%s' "${value:-$default_value}"
}

read_secret() {
    local prompt=$1 value
    read -r -s -p "$prompt: " value
    printf '\n' >&2
    printf '%s' "$value"
}

confirm() {
    local prompt=$1 answer
    read -r -p "$prompt [y/N]: " answer
    [[ "$answer" == "y" || "$answer" == "Y" ]]
}

validate_port() {
    [[ "$1" =~ ^[0-9]{1,5}$ ]] && (( 10#$1 >= 1 && 10#$1 <= 65535 )) || {
        printf 'Invalid PostgreSQL port: %s\n' "$1" >&2
        exit 1
    }
}

validate_count() {
    [[ "$1" =~ ^[0-9]+$ ]] && (( 10#$1 >= 1 && 10#$1 <= 3650 )) || {
        printf '%s must be between 1 and 3650 days.\n' "$2" >&2
        exit 1
    }
}

validate_cron() {
    local expression=$1 field extra
    read -r -a fields <<< "$expression"
    if (( ${#fields[@]} != 5 )); then
        printf '%s\n' "Enter a five-field cron expression (minute hour day-of-month month day-of-week)." >&2
        exit 1
    fi
    for field in "${fields[@]}"; do
        if [[ ! "$field" =~ ^[0-9*/,-]+$ ]]; then
            printf 'Unsupported cron field: %s\n' "$field" >&2
            exit 1
        fi
    done
}

validate_s3_uri() {
    [[ "$1" =~ ^s3://[A-Za-z0-9.-]+(/[A-Za-z0-9._/-]+)?$ ]] \
            && [[ ! "$1" =~ (^|/)\.\.?(/|$) ]] || {
        printf '%s\n' "Enter an S3 URI such as s3://bucket/javara-backups." >&2
        exit 1
    }
}

aws_options=()
db_host="$(read_value 'JAVARA PostgreSQL host' 'localhost')"
db_port="$(read_value 'JAVARA PostgreSQL port' '5432')"
db_name="$(read_value 'JAVARA PostgreSQL database' 'javara')"
db_user="$(read_value 'JAVARA PostgreSQL username' 'javara')"
db_password="$(read_secret 'JAVARA PostgreSQL password')"
db_sslmode="$(read_value 'PostgreSQL SSL mode (disable/allow/prefer/require)' 'prefer')"
backup_format="$(read_value 'Backup format (custom/plain)' 'custom')"
backup_destination="$(read_value 'Destination (filesystem/s3/both)' 'filesystem')"

case "$backup_format" in
    custom|plain) ;;
    *)
        printf '%s\n' "Backup format must be custom or plain." >&2
        exit 1
        ;;
esac
case "$backup_destination" in
    filesystem|s3|both) ;;
    *)
        printf '%s\n' "Backup destination must be filesystem, s3, or both." >&2
        exit 1
        ;;
esac

backup_directory=""
if [[ "$backup_destination" == "filesystem" || "$backup_destination" == "both" ]]; then
    backup_directory="$(read_value 'Absolute backup directory (use an encrypted volume)' '')"
    [[ "$backup_directory" == /* ]] || {
        printf '%s\n' "Backup directory must be an absolute path." >&2
        exit 1
    }
    mkdir -p "$backup_directory"
    backup_directory="$(cd "$backup_directory" && pwd -P)"
    [[ "$backup_directory" != "/" && "$backup_directory" != "$ROOT_DIR" \
            && "$backup_directory" != "$ROOT_DIR/"* ]] || {
        printf '%s\n' "Use a dedicated backup directory outside the JAVARA repository." >&2
        exit 1
    }
    confirm "Confirm that this backup directory is protected by storage encryption" || {
        printf '%s\n' "Choose an encrypted volume or use encrypted S3 storage." >&2
        exit 1
    }
fi

s3_uri=""
s3_profile=""
s3_endpoint_url=""
s3_sse="bucket-default"
if [[ "$backup_destination" == "s3" || "$backup_destination" == "both" ]]; then
    s3_uri="$(read_value 'S3 destination URI' '')"
    validate_s3_uri "$s3_uri"
    s3_profile="$(read_value 'AWS profile (blank uses the standard AWS credential chain)' '')"
    s3_endpoint_url="$(read_value 'S3-compatible endpoint URL (blank for AWS)' '')"
    s3_sse="$(read_value 'S3 server-side encryption (AES256/bucket-default)' 'AES256')"
    if [[ "$s3_sse" != "AES256" && "$s3_sse" != "bucket-default" ]]; then
        printf '%s\n' "S3 encryption must be AES256 or bucket-default." >&2
        exit 1
    fi
    if [[ "$s3_sse" == "bucket-default" ]] \
            && ! confirm "Confirm that the bucket enforces server-side encryption"; then
        printf '%s\n' "Select AES256 or configure encryption on the bucket before continuing." >&2
        exit 1
    fi
    if ! command -v aws >/dev/null 2>&1; then
        printf '%s\n' "AWS CLI is required for S3 destinations; install it and rerun make backup-setup." >&2
        exit 1
    fi
    [[ -z "$s3_profile" || "$s3_profile" =~ ^[A-Za-z0-9._-]+$ ]] || {
        printf '%s\n' "AWS profile contains unsupported characters." >&2
        exit 1
    }
    [[ -z "$s3_endpoint_url" || "$s3_endpoint_url" =~ ^https?://[A-Za-z0-9._:/-]+$ ]] || {
        printf '%s\n' "Invalid S3-compatible endpoint URL." >&2
        exit 1
    }
    if [[ "$s3_endpoint_url" == http://* ]]; then
        endpoint_host="${s3_endpoint_url#http://}"
        endpoint_host="${endpoint_host%%/*}"
        endpoint_host="${endpoint_host%%:*}"
        if [[ "$endpoint_host" != "localhost" && "$endpoint_host" != "127.0.0.1" ]]; then
            printf '%s\n' "Use HTTPS for remote S3-compatible endpoints; HTTP is allowed only on localhost." >&2
            exit 1
        fi
    fi
    if [[ -n "$s3_profile" ]]; then
        aws_options+=(--profile "$s3_profile")
    fi
    if [[ -n "$s3_endpoint_url" ]]; then
        aws_options+=(--endpoint-url "$s3_endpoint_url")
    fi
fi

scheduler_mode="$(read_value 'Scheduler (cron/external)' 'cron')"
if [[ "$scheduler_mode" != "cron" && "$scheduler_mode" != "external" ]]; then
    printf '%s\n' "Scheduler must be cron or external." >&2
    exit 1
fi
cron_schedule=""
if [[ "$scheduler_mode" == "cron" ]]; then
    cron_schedule="$(read_value 'Five-field schedule, in the host timezone (examples: 0 2 * * * daily; 0 2 * * 1 Mondays; 0 2 1 * * monthly)' '0 2 * * *')"
    validate_cron "$cron_schedule"
fi
backup_retention_days="$(read_value 'Backup-file retention in days' '30')"
token_grace_days="$(read_value 'Expired-token retention after expiry in days' '7')"
validate_count "$backup_retention_days" "Backup-file retention"
validate_count "$token_grace_days" "Expired-token retention"

validate_port "$db_port"
[[ "$db_host" =~ ^[A-Za-z0-9.-]+$ ]] || {
    printf '%s\n' "PostgreSQL host contains unsupported characters." >&2
    exit 1
}
[[ "$db_name" =~ ^[A-Za-z0-9_-]+$ ]] || {
    printf '%s\n' "PostgreSQL database name contains unsupported characters." >&2
    exit 1
}
[[ -n "$db_user" && "$db_user" != *$'\n'* && "$db_password" != *$'\n'* ]] || {
    printf '%s\n' "Database username must not be empty; username and password must not contain newlines." >&2
    exit 1
}
case "$db_sslmode" in
    disable|allow|prefer|require|verify-ca|verify-full) ;;
    *)
        printf '%s\n' "Invalid PostgreSQL SSL mode." >&2
        exit 1
        ;;
esac

for tool in pg_dump psql; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        printf 'Required PostgreSQL client tool is missing: %s\n' "$tool" >&2
        printf '%s\n' "Install the PostgreSQL client tools for your server version, then rerun make backup-setup." >&2
        exit 1
    fi
done
if [[ "$backup_format" == "custom" ]] && ! command -v pg_restore >/dev/null 2>&1; then
    printf '%s\n' "pg_restore is required to verify custom-format backups." >&2
    exit 1
fi
if [[ "$scheduler_mode" == "cron" ]] && ! command -v crontab >/dev/null 2>&1; then
    printf '%s\n' "crontab is unavailable; choose external scheduling or install cron." >&2
    exit 1
fi

database_check="$(
    PGPASSWORD="$db_password" \
        PGCONNECT_TIMEOUT=10 \
        PGSSLMODE="$db_sslmode" \
        psql \
            --no-password \
            --no-psqlrc \
            --set ON_ERROR_STOP=1 \
            --tuples-only \
            --no-align \
            --host="$db_host" \
            --port="$db_port" \
            --username="$db_user" \
            --dbname="$db_name" \
            --command="SELECT to_regclass('public.refresh_tokens') IS NOT NULL AND to_regclass('public.flyway_schema_history') IS NOT NULL"
)"
if [[ "$database_check" != "t" ]]; then
    printf '%s\n' "The selected database is not a migrated JAVARA database (refresh_tokens/Flyway history missing)." >&2
    exit 1
fi

if [[ "$backup_destination" == "s3" || "$backup_destination" == "both" ]]; then
    s3_bucket="${s3_uri#s3://}"
    s3_bucket="${s3_bucket%%/*}"
    aws s3api head-bucket --bucket "$s3_bucket" \
        "${aws_options[@]+"${aws_options[@]}"}"
fi

if [[ -f "$CONFIG_FILE" ]] \
        && ! confirm "Replace the existing backup configuration and scheduler"; then
    printf '%s\n' "Existing backup configuration was left unchanged."
    exit 0
fi

mkdir -p "$CONFIG_DIR"
temporary_config="$(mktemp "$CONFIG_DIR/backup.env.XXXXXX")"
trap 'rm -f "$temporary_config"' EXIT
write_setting() {
    printf '%s=' "$1" >> "$temporary_config"
    printf '%q\n' "$2" >> "$temporary_config"
}
{
    : > "$temporary_config"
    write_setting DB_HOST "$db_host"
    write_setting DB_PORT "$db_port"
    write_setting DB_NAME "$db_name"
    write_setting DB_USER "$db_user"
    write_setting DB_PASSWORD "$db_password"
    write_setting DB_SSLMODE "$db_sslmode"
    write_setting BACKUP_FORMAT "$backup_format"
    write_setting BACKUP_DESTINATION "$backup_destination"
    write_setting BACKUP_DIRECTORY "$backup_directory"
    write_setting S3_URI "$s3_uri"
    write_setting AWS_PROFILE "$s3_profile"
    write_setting S3_ENDPOINT_URL "$s3_endpoint_url"
    write_setting S3_SSE "$s3_sse"
    write_setting BACKUP_SCHEDULER "$scheduler_mode"
    write_setting BACKUP_CRON "$cron_schedule"
    write_setting BACKUP_RETENTION_DAYS "$backup_retention_days"
    write_setting TOKEN_GRACE_DAYS "$token_grace_days"
}
chmod 600 "$temporary_config"
mv "$temporary_config" "$CONFIG_FILE"
temporary_config=""

if [[ "$scheduler_mode" == "cron" ]]; then
    old_crontab="$(crontab -l 2>/dev/null || true)"
    crontab_file="$(mktemp)"
    trap 'rm -f "$temporary_config" "$crontab_file"' EXIT
    printf '%s\n' "$old_crontab" \
        | sed "\|$CRON_MARKER\$|d" > "$crontab_file"
    quoted_script="$(shell_quote "$ROOT_DIR/scripts/backup-run.sh")"
    printf '%s /bin/bash %s --scheduled %s\n' \
        "$cron_schedule" "$quoted_script" "$CRON_MARKER" >> "$crontab_file"
    crontab "$crontab_file"
    rm -f "$crontab_file"
    printf 'Backup scheduler installed with cron schedule: %s\n' "$cron_schedule"
else
    if command -v crontab >/dev/null 2>&1; then
        remove_managed_cron
    fi
    printf 'Configuration saved. Register /bin/bash %s/scripts/backup-run.sh --scheduled with your external scheduler.\n' \
        "$(shell_quote "$ROOT_DIR")"
fi

printf '%s\n' "Backup setup completed. It will run on the next scheduled time; use 'make backup-run' to run it now."
