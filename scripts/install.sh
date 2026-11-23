#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

confirm() {
    local prompt=$1
    local answer
    read -r -p "$prompt [y/N] " answer
    [[ "$answer" == "y" || "$answer" == "Y" ]]
}

yaml_quote() {
    printf "'%s'" "$(printf '%s' "$1" | sed "s/'/''/g")"
}

validate_time_zone() {
    [[ "$1" =~ ^[A-Za-z0-9_+-]+(/[A-Za-z0-9_+-]+)*$ ]] \
            && { [[ -f "/usr/share/zoneinfo/$1" ]] \
                || [[ -f "/var/db/timezone/zoneinfo/$1" ]]; } || {
        printf '%s\n' "Enter a valid IANA timezone available on this system (for example UTC or Asia/Jakarta)." >&2
        exit 1
    }
}

configure_auth_bootstrap() {
    read -r -p "Initial administrator email: " bootstrap_admin_email
    read -r -s -p "Initial administrator password: " bootstrap_admin_password
    printf '\n' >&2
    read -r -s -p "Confirm initial administrator password: " password_confirmation
    printf '\n' >&2
    read -r -p "Token transport (cookie/bearer) [cookie]: " token_transport
    token_transport="${token_transport:-cookie}"
    read -r -p "Application timezone (IANA) [UTC]: " application_timezone
    application_timezone="${application_timezone:-UTC}"
    if [[ ! "$bootstrap_admin_email" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]]; then
        printf '%s\n' "Initial administrator email is invalid." >&2
        exit 1
    fi
    if (( ${#bootstrap_admin_password} < 8 || ${#bootstrap_admin_password} > 72 )) \
            || [[ "$bootstrap_admin_password" != "$password_confirmation" ]]; then
        printf '%s\n' "Initial administrator passwords must match and be between 8 and 72 characters." >&2
        exit 1
    fi
    if [[ "$token_transport" != "bearer" && "$token_transport" != "cookie" ]]; then
        printf '%s\n' "Token transport must be bearer or cookie." >&2
        exit 1
    fi
    validate_time_zone "$application_timezone"
    if command -v openssl >/dev/null 2>&1; then
        signing_key="$(openssl rand -base64 32 | tr -d '\n')"
    else
        signing_key="$(head -c 32 /dev/urandom | base64 | tr -d '\n')"
    fi
}

java_major() {
    local java_command=$1
    "$java_command" -version 2>&1 \
        | sed -nE 's/.*version "([0-9]+).*/\1/p' \
        | head -n 1
}

is_jdk_25_or_newer() {
    local java_command=$1
    local major
    [[ -x "$java_command" && -x "${java_command%/java}/javac" ]] || return 1
    major="$(java_major "$java_command")"
    [[ "$major" =~ ^[0-9]+$ ]] && (( major >= 25 ))
}

has_java_25() {
    if [[ -n "${JAVA_HOME:-}" ]] && is_jdk_25_or_newer "$JAVA_HOME/bin/java"; then
        return 0
    fi
    if command -v java >/dev/null 2>&1 \
            && is_jdk_25_or_newer "$(command -v java)"; then
        return 0
    fi
    if [[ "$(uname -s)" == "Darwin" ]] && command -v brew >/dev/null 2>&1; then
        local brew_prefix
        brew_prefix="$(brew --prefix openjdk@25 2>/dev/null || true)"
        is_jdk_25_or_newer "$brew_prefix/libexec/openjdk.jdk/Contents/Home/bin/java"
        return
    fi
    return 1
}

install_prerequisites() {
    local os
    os="$(uname -s)"

    if [[ "$os" == "Darwin" ]]; then
        if ! command -v brew >/dev/null 2>&1; then
            printf '%s\n' "Homebrew is required to automate macOS dependency installation."
            printf '%s\n' "Install Homebrew from https://brew.sh, then run 'make install' again."
            return 1
        fi

        brew install openjdk@25
        brew install --cask docker
        open -a Docker
        return
    fi

    if [[ "$os" != "Linux" || ! -r /etc/os-release ]]; then
        printf '%s\n' "Automatic installation is supported on macOS and recognized Linux distributions." >&2
        return 1
    fi

    local distro_id distro_like
    # shellcheck disable=SC1091
    . /etc/os-release
    distro_id="${ID:-}"
    distro_like="${ID_LIKE:-}"

    case "$distro_id $distro_like" in
        *debian*|*ubuntu*)
            sudo apt-get update
            sudo apt-get install -y docker.io docker-compose-v2 openjdk-25-jdk
            sudo systemctl enable --now docker
            ;;
        *fedora*)
            sudo dnf install -y docker docker-compose-plugin java-25-openjdk-devel
            sudo systemctl enable --now docker
            ;;
        *rhel*|*centos*|*rocky*|*almalinux*)
            sudo dnf install -y docker docker-compose-plugin java-25-openjdk-devel
            sudo systemctl enable --now docker
            ;;
        *arch*)
            sudo pacman -Sy --needed --noconfirm docker docker-compose jdk-openjdk
            sudo systemctl enable --now docker
            ;;
        *opensuse*|*suse*)
            sudo zypper --non-interactive install docker docker-compose java-25-openjdk-devel
            sudo systemctl enable --now docker
            ;;
        *)
            printf 'Automatic installation is not configured for Linux distribution "%s".\n' \
                "${distro_id:-unknown}" >&2
            printf '%s\n' "Install Docker Engine with Compose v2 and JDK 25 using your distribution's official instructions." >&2
            return 1
            ;;
    esac
}

if [[ -f config/application.yml ]] \
        && ! confirm "Replace the existing config/application.yml with Docker-managed service settings?"; then
    printf '%s\n' "No changes made. Use 'make setup' to configure existing services."
    exit 1
fi

if [[ ! -f .env ]] && command -v nc >/dev/null 2>&1; then
    for port in 5432 6379; do
        if nc -z -w 1 127.0.0.1 "$port"; then
            printf 'Local port %s is already in use. Use make setup for existing services, or configure another port before installing.\n' \
                "$port" >&2
            exit 1
        fi
    done
fi

if ! has_java_25 || ! command -v docker >/dev/null 2>&1 \
        || ! docker compose version >/dev/null 2>&1; then
    printf '%s\n' "This setup can install JDK 25 and Docker with Docker Compose."
    printf '%s\n' "Package installation may request administrator permission."
    if ! confirm "Install the missing prerequisites now?"; then
        printf '%s\n' "Install JDK 25 and Docker Compose manually, then run 'make install' again."
        exit 1
    fi
    install_prerequisites
fi

if ! has_java_25; then
    printf '%s\n' "JDK 25 was not found after the installation attempt." >&2
    exit 1
fi

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
    printf '%s\n' "Docker with the Compose v2 plugin is required." >&2
    exit 1
fi

if [[ "$(uname -s)" == "Darwin" ]] && ! docker info >/dev/null 2>&1; then
    if [[ -d /Applications/Docker.app ]]; then
        open -a Docker
    else
        printf '%s\n' "Docker Desktop is installed but its application could not be found in /Applications." >&2
    fi
    printf '%s\n' "Waiting for Docker Desktop. Complete any first-run prompts if shown."
fi

if [[ "$(uname -s)" == "Linux" ]] && command -v systemctl >/dev/null 2>&1 \
        && ! systemctl is-active --quiet docker; then
    sudo systemctl enable --now docker
fi

ready=false
for _ in $(seq 1 60); do
    if [[ "$(uname -s)" == "Darwin" ]]; then
        if docker info >/dev/null 2>&1; then
            ready=true
            break
        fi
    elif docker info >/dev/null 2>&1 || sudo docker info >/dev/null 2>&1; then
        ready=true
        break
    fi
    sleep 2
done

if [[ "$ready" != true ]]; then
    printf '%s\n' "Docker daemon did not become ready. Start Docker and retry 'make install'." >&2
    exit 1
fi

create_local_env() {
    if [[ -f .env ]]; then
        return
    fi

    local postgres_password redis_password
    if command -v openssl >/dev/null 2>&1; then
        postgres_password="$(openssl rand -hex 24)"
        redis_password="$(openssl rand -hex 24)"
    else
        postgres_password="$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')"
        redis_password="$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')"
    fi

    umask 077
    cat > .env <<EOF
POSTGRES_USER=javara
POSTGRES_PASSWORD=$postgres_password
POSTGRES_DB=javara
POSTGRES_PORT=5432
REDIS_PASSWORD=$redis_password
REDIS_PORT=6379
EOF
    chmod 600 .env
}

env_value() {
    local name=$1
    local value
    value="$(sed -n "s/^${name}=//p" .env | tail -n 1)"
    if [[ -z "$value" || "$value" =~ [^A-Za-z0-9._-] ]]; then
        printf 'The .env value %s is missing or contains unsupported characters.\n' "$name" >&2
        exit 1
    fi
    if [[ "$name" == *_PORT ]] \
            && (( 10#$value < 1 || 10#$value > 65535 )); then
        printf 'The .env value %s must be between 1 and 65535.\n' "$name" >&2
        exit 1
    fi
    printf '%s' "$value"
}

write_managed_config() {
    local postgres_user postgres_password postgres_db postgres_port redis_password redis_port
    postgres_user="$(env_value POSTGRES_USER)"
    postgres_password="$(env_value POSTGRES_PASSWORD)"
    postgres_db="$(env_value POSTGRES_DB)"
    postgres_port="$(env_value POSTGRES_PORT)"
    redis_password="$(env_value REDIS_PASSWORD)"
    redis_port="$(env_value REDIS_PORT)"

    if [[ ! "$postgres_port" =~ ^[0-9]+$ || ! "$redis_port" =~ ^[0-9]+$ ]]; then
        printf '%s\n' "POSTGRES_PORT and REDIS_PORT must be numeric." >&2
        exit 1
    fi

    mkdir -p config
    local temp_file
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
  application:
    name: javara-postgres
  lifecycle:
    timeout-per-shutdown-phase: 20s
  datasource:
    url: jdbc:postgresql://localhost:${postgres_port}/${postgres_db}
    username: '${postgres_user}'
    password: '${postgres_password}'
  data:
    redis:
      host: localhost
      port: ${redis_port}
      password: '${redis_password}'
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
        key-prefix: javara:local:login
      register:
        enabled: true
        max-requests: 5
        window: 1h
        key-prefix: javara:local:register
      refresh:
        enabled: true
        max-requests: 30
        window: 1m
        key-prefix: javara:local:refresh
EOF
    chmod 600 "$temp_file"
    mv "$temp_file" config/application.yml
}

create_local_env
configure_auth_bootstrap
write_managed_config
if command -v nc >/dev/null 2>&1; then
    for port in "$(env_value POSTGRES_PORT)" "$(env_value REDIS_PORT)"; do
        if nc -z -w 1 127.0.0.1 "$port"; then
            printf 'Local port %s is already in use. Use make setup for existing services or change the port in .env and retry.\n' \
                "$port" >&2
            exit 1
        fi
    done
fi
bash scripts/docker-compose.sh up -d --wait --wait-timeout 120

printf '\n%s\n' "PostgreSQL and Redis are running in Docker."
printf '%s\n' "The REST API has not been started. Run 'make run' when you are ready."
