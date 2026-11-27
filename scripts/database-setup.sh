#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# shellcheck source=scripts/configuration-support.sh
. "$ROOT_DIR/scripts/configuration-support.sh"

DATABASE_CONFIG="config/database.yml"
APPLICATION_CONFIG="config/application.yml"

validate_host() {
    [[ "$1" =~ ^[A-Za-z0-9.-]+$ ]] || {
        printf 'Invalid PostgreSQL host: %s\n' "$1" >&2
        exit 1
    }
}

validate_port() {
    [[ "$1" =~ ^[0-9]{1,5}$ ]] && (( 10#$1 >= 1 && 10#$1 <= 65535 )) || {
        printf '%s\n' "PostgreSQL port must be between 1 and 65535." >&2
        exit 1
    }
}

validate_database_name() {
    [[ "$1" =~ ^[A-Za-z0-9_-]+$ ]] || {
        printf '%s\n' "Database name may contain only letters, numbers, underscores, and hyphens." >&2
        exit 1
    }
}

validate_database_user() {
    [[ "$1" =~ ^[A-Za-z0-9_.-]+$ ]] || {
        printf '%s\n' "Database username may contain only letters, numbers, underscores, dots, and hyphens." >&2
        exit 1
    }
}

validate_integer() {
    local name=$1 value=$2 minimum=$3 maximum=$4
    [[ "$value" =~ ^[0-9]{1,3}$ ]] \
            && (( 10#$value >= minimum && 10#$value <= maximum )) || {
        printf '%s must be an integer between %s and %s.\n' "$name" "$minimum" "$maximum" >&2
        exit 1
    }
}

duration_to_milliseconds() {
    local value=$1 amount unit multiplier
    [[ "$value" =~ ^([1-9][0-9]{0,3})(ms|s|m)$ ]] || {
        printf '%s\n' "Use a positive duration such as 500ms, 30s, or 10m." >&2
        exit 1
    }
    amount="${BASH_REMATCH[1]}"
    unit="${BASH_REMATCH[2]}"
    case "$unit" in
        ms) multiplier=1 ;;
        s) multiplier=1000 ;;
        m) multiplier=60000 ;;
    esac
    printf '%s' "$((10#$amount * multiplier))"
}

validate_duration() {
    local name=$1 value=$2 minimum=$3 maximum=$4 milliseconds
    milliseconds="$(duration_to_milliseconds "$value")"
    (( milliseconds >= minimum && milliseconds <= maximum )) || {
        printf '%s must be between %sms and %sms.\n' "$name" "$minimum" "$maximum" >&2
        exit 1
    }
}

if [[ -f "$DATABASE_CONFIG" ]]; then
    read -r -p "Replace existing $DATABASE_CONFIG? [y/N] " overwrite
    if [[ "$overwrite" != "y" && "$overwrite" != "Y" ]]; then
        printf '%s\n' "Database configuration was not changed."
        exit 0
    fi
fi

postgres_host="$(read_setup_value 'PostgreSQL host' 'localhost')"
postgres_port="$(read_setup_value 'PostgreSQL port' '5432')"
postgres_db="$(read_setup_value 'PostgreSQL database' 'javara')"
postgres_user="$(read_setup_value 'PostgreSQL username' 'javara')"
postgres_password="$(read_setup_secret 'PostgreSQL password')"
maximum_pool_size="$(read_setup_value 'Maximum database connections' '10')"
minimum_idle="$(read_setup_value 'Minimum idle database connections' '2')"
connection_timeout="$(read_setup_value 'Connection timeout' '30s')"
validation_timeout="$(read_setup_value 'Connection validation timeout' '5s')"
idle_timeout="$(read_setup_value 'Idle connection timeout' '10m')"
max_lifetime="$(read_setup_value 'Maximum connection lifetime' '30m')"

validate_host "$postgres_host"
validate_port "$postgres_port"
validate_database_name "$postgres_db"
validate_database_user "$postgres_user"
validate_integer "Maximum pool size" "$maximum_pool_size" 1 100
validate_integer "Minimum idle connections" "$minimum_idle" 0 "$maximum_pool_size"
validate_duration "Connection timeout" "$connection_timeout" 250 120000
validate_duration "Connection validation timeout" "$validation_timeout" 250 120000
validate_duration "Idle connection timeout" "$idle_timeout" 10000 86400000
validate_duration "Maximum connection lifetime" "$max_lifetime" 30000 86400000
if (( $(duration_to_milliseconds "$validation_timeout") \
        >= $(duration_to_milliseconds "$connection_timeout") )); then
    printf '%s\n' "Connection validation timeout must be shorter than the connection timeout." >&2
    exit 1
fi
if [[ -z "$postgres_password" ]]; then
    printf '%s\n' "PostgreSQL password cannot be empty." >&2
    exit 1
fi
connection_timeout_ms="$(duration_to_milliseconds "$connection_timeout")"
validation_timeout_ms="$(duration_to_milliseconds "$validation_timeout")"
idle_timeout_ms="$(duration_to_milliseconds "$idle_timeout")"
max_lifetime_ms="$(duration_to_milliseconds "$max_lifetime")"

ensure_runtime_config_imports "$APPLICATION_CONFIG" \
    optional:file:./config/database.yml \
    optional:file:./config/tls.yml \
    optional:file:./config/mtls.yml

mkdir -p config
temp_file="$(mktemp "${DATABASE_CONFIG}.XXXXXX")"
cat > "$temp_file" <<EOF
spring:
  datasource:
    url: jdbc:postgresql://${postgres_host}:${postgres_port}/${postgres_db}
    username: $(yaml_quote "$postgres_user")
    password: $(yaml_quote "$postgres_password")
    hikari:
      maximum-pool-size: ${maximum_pool_size}
      minimum-idle: ${minimum_idle}
      connection-timeout: ${connection_timeout_ms}
      validation-timeout: ${validation_timeout_ms}
      idle-timeout: ${idle_timeout_ms}
      max-lifetime: ${max_lifetime_ms}
EOF
chmod 600 "$temp_file"
mv "$temp_file" "$DATABASE_CONFIG"

printf '\n%s\n' "Database configuration saved to $DATABASE_CONFIG."
printf 'Pool size: maximum=%s, minimum-idle=%s\n' "$maximum_pool_size" "$minimum_idle"
printf '%s\n' "Restart the application for changes to take effect."
