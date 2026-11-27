#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

read_value() {
    local prompt=$1 default_value=$2 value
    read -r -p "$prompt [$default_value]: " value
    printf '%s' "${value:-$default_value}"
}

read_yes_no() {
    local prompt=$1 default_value=$2 value
    read -r -p "$prompt [$default_value]: " value
    value=${value:-$default_value}
    case "$value" in
        y|Y|yes|YES|Yes) printf 'true' ;;
        n|N|no|NO|No) printf 'false' ;;
        *)
            printf '%s\n' "Enter yes or no." >&2
            exit 1
            ;;
    esac
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

auth_api_enabled="$(read_yes_no 'Enable built-in user authentication API?' 'Y')"
resource_server_enabled="$(read_yes_no 'Enable JWT resource-server token validation?' 'Y')"
service_auth_enabled="$(read_yes_no 'Enable service-to-service authentication?' 'Y')"
rate_limiting_enabled=false
if [[ "$auth_api_enabled" == "true" ]]; then
    rate_limiting_enabled="$(read_yes_no 'Enable authentication rate limiting (requires Redis)?' 'Y')"
fi

if [[ "$auth_api_enabled" == "true" && "$resource_server_enabled" != "true" ]]; then
    printf '%s\n' "The built-in authentication API requires resource-server token validation. Enable both or disable auth-api." >&2
    exit 1
fi
if [[ "$service_auth_enabled" == "true" && "$resource_server_enabled" != "true" ]]; then
    printf '%s\n' "Service-to-service authentication requires resource-server token validation. Enable both or disable service authentication." >&2
    exit 1
fi

redis_host=localhost
redis_port=6379
redis_username=
redis_password=
bootstrap_admin_email=
bootstrap_admin_password=
bootstrap_admin_password_confirm=
smtp_host=
smtp_port=587
smtp_username=
smtp_password=
token_transport=bearer
signing_key=

postgres_host="$(read_value 'PostgreSQL host' 'localhost')"
postgres_port="$(read_value 'PostgreSQL port' '5432')"
postgres_db="$(read_value 'PostgreSQL database' 'javara')"
postgres_user="$(read_value 'PostgreSQL username' 'javara')"
postgres_password="$(read_secret 'PostgreSQL password')"
application_name="$(read_value 'Application name' 'JAVARA')"
powered_by="$(read_value "Email attribution (enter 'none' to disable)" 'JAVARA')"
application_timezone="$(read_value 'Application timezone (IANA)' 'UTC')"

if [[ "$rate_limiting_enabled" == "true" ]]; then
    redis_host="$(read_value 'Redis host' 'localhost')"
    redis_port="$(read_value 'Redis port' '6379')"
    redis_username="$(read_value 'Redis username (blank if unused)' '')"
    redis_password="$(read_secret 'Redis password (blank if unused)')"
fi

if [[ "$auth_api_enabled" == "true" ]]; then
    bootstrap_admin_email="$(read_value 'Initial administrator email' '')"
    bootstrap_admin_password="$(read_secret 'Initial administrator password')"
    bootstrap_admin_password_confirm="$(read_secret 'Confirm initial administrator password')"
    smtp_host="$(read_value 'SMTP host' 'smtp.gmail.com')"
    smtp_port="$(read_value 'SMTP port' '587')"
    read -r -p "SMTP username: " smtp_username
    smtp_password="$(read_secret 'SMTP App Password')"
fi

if [[ "$resource_server_enabled" == "true" ]]; then
    token_transport_default=cookie
    if [[ "$auth_api_enabled" != "true" ]]; then
        token_transport_default=bearer
    fi
    token_transport="$(read_value 'Token transport (cookie/bearer)' "$token_transport_default")"
    signing_key="$(generate_signing_key)"
fi

validate_host "$postgres_host"
validate_port "$postgres_port"
validate_database_name "$postgres_db"
if [[ -z "${application_name//[[:space:]]/}" ]] \
        || (( ${#application_name} > 80 )) \
        || [[ "$application_name" =~ [[:cntrl:]] ]]; then
    printf '%s\n' "Application name must contain 1 to 80 printable characters." >&2
    exit 1
fi
if (( ${#powered_by} > 80 )) || [[ "$powered_by" =~ [[:cntrl:]] ]]; then
    printf '%s\n' "Email attribution must contain at most 80 printable characters." >&2
    exit 1
fi
if [[ "$powered_by" == "none" ]]; then
    powered_by=""
fi
validate_time_zone "$application_timezone"
if [[ "$rate_limiting_enabled" == "true" ]]; then
    validate_host "$redis_host"
    validate_port "$redis_port"
fi
if [[ "$auth_api_enabled" == "true" ]]; then
    validate_host "$smtp_host"
    validate_port "$smtp_port"
    if [[ -z "$smtp_username" ]]; then
        printf '%s\n' "SMTP username is required when the authentication API is enabled." >&2
        exit 1
    fi
    if [[ -z "$smtp_password" ]]; then
        printf '%s\n' "SMTP App Password is required when the authentication API is enabled." >&2
        exit 1
    fi
    validate_admin_credentials \
        "$bootstrap_admin_email" \
        "$bootstrap_admin_password" \
        "$bootstrap_admin_password_confirm"
fi
if [[ "$resource_server_enabled" == "true" \
        && "$token_transport" != "bearer" && "$token_transport" != "cookie" ]]; then
    printf '%s\n' "Token transport must be bearer or cookie." >&2
    exit 1
fi

if command -v nc >/dev/null 2>&1; then
    if ! nc -z -w 2 "$postgres_host" "$postgres_port"; then
        printf '%s\n' "Cannot reach PostgreSQL at the supplied host and port." >&2
        exit 1
    fi
    if [[ "$rate_limiting_enabled" == "true" ]] \
            && ! nc -z -w 2 "$redis_host" "$redis_port"; then
        printf '%s\n' "Cannot reach Redis at the supplied host and port." >&2
        exit 1
    fi
else
    printf '%s\n' "Warning: netcat is unavailable; service connectivity was not checked." >&2
fi

spring_mail_config=
if [[ "$auth_api_enabled" == "true" ]]; then
    spring_mail_config="$(cat <<EOF
  mail:
    host: $(yaml_quote "$smtp_host")
    port: ${smtp_port}
    username: $(yaml_quote "$smtp_username")
    password: $(yaml_quote "$smtp_password")
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
      mail.smtp.connectiontimeout: 5000
      mail.smtp.timeout: 5000
      mail.smtp.writetimeout: 5000
EOF
)"
fi

redis_config=
if [[ "$rate_limiting_enabled" == "true" ]]; then
    redis_config="$(cat <<EOF
  data:
    redis:
      host: $(yaml_quote "$redis_host")
      port: ${redis_port}
      username: $(yaml_quote "$redis_username")
      password: $(yaml_quote "$redis_password")
      connect-timeout: 2s
      timeout: 2s
EOF
)"
fi

jwt_config=
if [[ "$resource_server_enabled" == "true" ]]; then
    jwt_config="$(cat <<EOF
    jwt:
      signing-key: $(yaml_quote "$signing_key")
      transport: $(printf '%s' "$token_transport" | tr '[:lower:]' '[:upper:]')
      access-token-ttl: 15m
      refresh-token-ttl: 30d
      cookie-secure: true
      cookie-same-site: Strict
      access-cookie-name: ACCESS_TOKEN
      refresh-cookie-name: REFRESH_TOKEN
EOF
)"
fi

bootstrap_config=
password_policy_config=
if [[ "$auth_api_enabled" == "true" ]]; then
    bootstrap_config="$(cat <<EOF
    bootstrap-admin:
      enabled: true
      email: $(yaml_quote "$bootstrap_admin_email")
      password: $(yaml_quote "$bootstrap_admin_password")
EOF
)"
    password_policy_config="$(cat <<'EOF'
    password-policy:
      enabled: true
      sample-data: true
      common-passwords-file: ./config/security/common-passwords.txt
      breached-password-hashes-file: ./config/security/breached-password-hashes.txt
EOF
)"
fi

rate_limit_config=
if [[ "$rate_limiting_enabled" == "true" ]]; then
    rate_limit_config="$(cat <<'EOF'
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
)"
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
  config:
    import: optional:file:./config/security.yml,optional:file:./config/database.yml,optional:file:./config/tls.yml,optional:file:./config/mtls.yml
  profiles:
    active: local
$(printf '%s\n' "$spring_mail_config")
  application:
    name: \${app.name}
  lifecycle:
    timeout-per-shutdown-phase: 20s
  datasource:
    url: jdbc:postgresql://${postgres_host}:${postgres_port}/${postgres_db}
    username: $(yaml_quote "$postgres_user")
    password: $(yaml_quote "$postgres_password")
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
      validation-timeout: 5000
      idle-timeout: 600000
      max-lifetime: 1800000
$(printf '%s\n' "$redis_config")
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: none
    open-in-view: false
app:
  name: $(yaml_quote "$application_name")
  branding:
    powered-by: $(yaml_quote "$powered_by")
  time-zone: $(yaml_quote "$application_timezone")
  features:
    auth-api: ${auth_api_enabled}
    resource-server: ${resource_server_enabled}
    rate-limiting: ${rate_limiting_enabled}
  email:
    enabled: ${auth_api_enabled}
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
    service-auth:
      enabled: ${service_auth_enabled}
$(printf '%s\n' "$password_policy_config")
    password:
      algorithm: ARGON2
$(printf '%s\n' "$jwt_config")
$(printf '%s\n' "$bootstrap_config")
$(printf '%s\n' "$rate_limit_config")
EOF
chmod 600 "$temp_file"
mv "$temp_file" config/application.yml

if [[ "$auth_api_enabled" == "true" ]]; then
    bash scripts/seed-password-policy-samples.sh
fi

printf '\n%s\n' "Configuration saved locally to config/application.yml."
printf 'Modules: auth-api=%s, resource-server=%s, rate-limiting=%s, service-auth=%s\n' \
    "$auth_api_enabled" \
    "$resource_server_enabled" \
    "$rate_limiting_enabled" \
    "$service_auth_enabled"
printf '%s\n' "The file is excluded from Git. Run 'make run' to start the API."
