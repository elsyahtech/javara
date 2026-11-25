#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

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

validate_host() {
    [[ "$1" =~ ^[A-Za-z0-9.-]+$ ]] || {
        printf 'Invalid host: %s\n' "$1" >&2
        exit 1
    }
}

validate_port() {
    [[ "$1" =~ ^[0-9]{1,5}$ ]] && (( 10#$1 >= 1 && 10#$1 <= 65535 )) || {
        printf 'Invalid port: %s\n' "$1" >&2
        exit 1
    }
}

validate_database_name() {
    [[ "$1" =~ ^[A-Za-z0-9_-]+$ ]] || {
        printf '%s\n' "Database name may contain only letters, numbers, underscores, and hyphens." >&2
        exit 1
    }
}

validate_time_zone() {
    [[ "$1" =~ ^[A-Za-z0-9_+-]+(/[A-Za-z0-9_+-]+)*$ ]] \
            && { [[ -f "/usr/share/zoneinfo/$1" ]] \
                || [[ -f "/var/db/timezone/zoneinfo/$1" ]]; } || {
        printf '%s\n' "Enter a valid IANA timezone available on this system (for example UTC or Asia/Jakarta)." >&2
        exit 1
    }
}

validate_admin_credentials() {
    [[ "$1" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]] || {
        printf '%s\n' "Initial administrator email is invalid." >&2
        exit 1
    }
    (( ${#2} >= 8 && ${#2} <= 72 )) || {
        printf '%s\n' "Initial administrator password must be between 8 and 72 characters." >&2
        exit 1
    }
    [[ "$2" == "$3" ]] || {
        printf '%s\n' "Initial administrator passwords do not match." >&2
        exit 1
    }
}

generate_signing_key() {
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -base64 32 | tr -d '\n'
    else
        head -c 32 /dev/urandom | base64 | tr -d '\n'
    fi
}

yaml_quote() {
    printf "'%s'" "$(printf '%s' "$1" | sed "s/'/''/g")"
}

postgres_host="$(read_value 'PostgreSQL host' 'localhost')"
postgres_port="$(read_value 'PostgreSQL port' '5432')"
postgres_db="$(read_value 'PostgreSQL database' 'javara')"
postgres_user="$(read_value 'PostgreSQL username' 'javara')"
postgres_password="$(read_secret 'PostgreSQL password')"
redis_host="$(read_value 'Redis host' 'localhost')"
redis_port="$(read_value 'Redis port' '6379')"
redis_username="$(read_value 'Redis username (blank if unused)' '')"
redis_password="$(read_secret 'Redis password (blank if unused)')"
bootstrap_admin_email="$(read_value 'Initial administrator email' '')"
bootstrap_admin_password="$(read_secret 'Initial administrator password')"
bootstrap_admin_password_confirm="$(read_secret 'Confirm initial administrator password')"
token_transport="$(read_value 'Token transport (cookie/bearer)' 'cookie')"
application_timezone="$(read_value 'Application timezone (IANA)' 'UTC')"
smtp_host="$(read_value 'SMTP host' 'smtp.gmail.com')"
smtp_port="$(read_value 'SMTP port' '587')"
signing_key="$(generate_signing_key)"

validate_host "$postgres_host"
validate_port "$postgres_port"
validate_database_name "$postgres_db"
validate_time_zone "$application_timezone"
validate_host "$redis_host"
validate_port "$redis_port"
validate_host "$smtp_host"
validate_port "$smtp_port"
validate_admin_credentials \
    "$bootstrap_admin_email" \
    "$bootstrap_admin_password" \
    "$bootstrap_admin_password_confirm"
if [[ "$token_transport" != "bearer" && "$token_transport" != "cookie" ]]; then
    printf '%s\n' "Token transport must be bearer or cookie." >&2
    exit 1
fi

if command -v nc >/dev/null 2>&1; then
    if ! nc -z -w 2 "$postgres_host" "$postgres_port"; then
        printf '%s\n' "Cannot reach PostgreSQL at the supplied host and port." >&2
        exit 1
    fi
    if ! nc -z -w 2 "$redis_host" "$redis_port"; then
        printf '%s\n' "Cannot reach Redis at the supplied host and port." >&2
        exit 1
    fi
else
    printf '%s\n' "Warning: netcat is unavailable; service connectivity was not checked." >&2
fi

mkdir -p config
if [[ -f config/application.yml ]]; then
    read -r -p "Replace existing config/application.yml? [y/N] " overwrite
    if [[ "$overwrite" != "y" && "$overwrite" != "Y" ]]; then
        printf '%s\n' "Configuration was not changed."
        exit 0
    fi
fi

temp_file="$(mktemp config/application.yml.XXXXXX)"
cat > "$temp_file" <<EOF
server:
  address: 127.0.0.1
  port: 8080
  request:
    header:
      forwarded:
        strategy: none
  shutdown: graceful
  compression:
    enabled: false
    mime-types:
      - application/json
      - application/problem+json
      - text/plain
    min-response-size: 1KB
spring:
  profiles:
    active: local
  mail:
    host: $(yaml_quote "$smtp_host")
    port: ${smtp_port}
    username: \${SMTP_USERNAME}
    password: \${SMTP_APP_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
      mail.smtp.connectiontimeout: 5000
      mail.smtp.timeout: 5000
      mail.smtp.writetimeout: 5000
  application:
    name: javara-postgres
  lifecycle:
    timeout-per-shutdown-phase: 20s
  datasource:
    url: jdbc:postgresql://${postgres_host}:${postgres_port}/${postgres_db}
    username: $(yaml_quote "$postgres_user")
    password: $(yaml_quote "$postgres_password")
  data:
    redis:
      host: $(yaml_quote "$redis_host")
      port: ${redis_port}
      username: $(yaml_quote "$redis_username")
      password: $(yaml_quote "$redis_password")
      connect-timeout: 2s
      timeout: 2s
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: none
    open-in-view: false
app:
  time-zone: $(yaml_quote "$application_timezone")
  email:
    enabled: true
    verification-token-ttl: 24h
    public-base-url: http://localhost:8080
  web:
    cors:
      enabled: false
      allowed-origins: []
      allowed-methods:
        - GET
        - HEAD
        - POST
        - PUT
        - PATCH
        - DELETE
        - OPTIONS
      allowed-headers:
        - Authorization
        - Content-Type
        - X-Request-ID
        - X-XSRF-TOKEN
      exposed-headers:
        - X-Request-ID
        - Retry-After
      allow-credentials: false
      max-age-seconds: 300
    request-id:
      enabled: true
      header-name: X-Request-ID
  security:
    headers:
      enabled: true
    password-policy:
      enabled: true
      sample-data: true
      common-passwords-file: ./config/security/common-passwords.txt
      breached-password-hashes-file: ./config/security/breached-password-hashes.txt
    password:
      algorithm: ARGON2
    jwt:
      signing-key: $(yaml_quote "$signing_key")
      transport: $(printf '%s' "$token_transport" | tr '[:lower:]' '[:upper:]')
      access-token-ttl: 15m
      refresh-token-ttl: 30d
      cookie-secure: true
      cookie-same-site: Strict
      access-cookie-name: ACCESS_TOKEN
      refresh-cookie-name: REFRESH_TOKEN
    bootstrap-admin:
      enabled: true
      email: $(yaml_quote "$bootstrap_admin_email")
      password: $(yaml_quote "$bootstrap_admin_password")
  middleware:
    rate-limit:
      login:
        enabled: true
        max-requests: 10
        window: 1m
        key-prefix: javara:external:login
      register:
        enabled: true
        max-requests: 5
        window: 1h
        key-prefix: javara:external:register
      refresh:
        enabled: true
        max-requests: 30
        window: 1m
        key-prefix: javara:external:refresh
EOF
chmod 600 "$temp_file"
mv "$temp_file" config/application.yml

bash scripts/seed-password-policy-samples.sh

printf '\n%s\n' "Configuration saved locally to config/application.yml."
printf '%s\n' "The file is excluded from Git. Run 'make run' to start the API."
