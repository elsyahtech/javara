#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# shellcheck source=scripts/configuration-support.sh
. "$ROOT_DIR/scripts/configuration-support.sh"

TLS_CONFIG="config/tls.yml"
MTLS_CONFIG="config/mtls.yml"
APPLICATION_CONFIG="config/application.yml"

if [[ -f "$TLS_CONFIG" ]]; then
    read -r -p "Replace existing $TLS_CONFIG? [y/N] " overwrite
    if [[ "$overwrite" != "y" && "$overwrite" != "Y" ]]; then
        printf '%s\n' "TLS configuration was not changed."
        exit 0
    fi
fi

tls_enabled="$(read_setup_yes_no 'Terminate HTTPS in the JAVARA application?' 'N')"

key_store_path=
key_store_type=
if [[ "$tls_enabled" != "true" ]] \
        && grep -Eq '^[[:space:]]*client-auth:[[:space:]]*need[[:space:]]*$' "$MTLS_CONFIG" 2>/dev/null; then
    printf '%s\n' \
        "Inbound mTLS is currently required. Run 'make mtls-setup' and disable it before disabling TLS." >&2
    exit 1
fi
if [[ "$tls_enabled" == "true" ]]; then
    raw_key_store_path="$(read_setup_value 'Absolute or repository-relative PKCS12/JKS keystore path' '')"
    key_store_path="$(validate_setup_file_path 'Keystore' "$raw_key_store_path")"
    key_store_type="$(read_setup_value 'Keystore type (PKCS12/JKS)' 'PKCS12')"
    key_store_type="${key_store_type^^}"
    if [[ "$key_store_type" != "PKCS12" && "$key_store_type" != "JKS" ]]; then
        printf '%s\n' "Keystore type must be PKCS12 or JKS." >&2
        exit 1
    fi
fi

ensure_runtime_config_imports "$APPLICATION_CONFIG" \
    optional:file:./config/database.yml \
    optional:file:./config/tls.yml \
    optional:file:./config/mtls.yml

mkdir -p config
temp_file="$(mktemp "${TLS_CONFIG}.XXXXXX")"
if [[ "$tls_enabled" == "true" ]]; then
    cat > "$temp_file" <<EOF
server:
  ssl:
    enabled: true
    key-store: $(yaml_quote "file:$key_store_path")
    key-store-type: ${key_store_type}
    key-store-password: \${SERVER_SSL_KEY_STORE_PASSWORD}
EOF
else
    cat > "$temp_file" <<'EOF'
server:
  ssl:
    enabled: false
EOF
fi
chmod 600 "$temp_file"
mv "$temp_file" "$TLS_CONFIG"

printf '\n%s\n' "TLS configuration saved to $TLS_CONFIG."
if [[ "$tls_enabled" == "true" ]]; then
    printf '%s\n' "Set SERVER_SSL_KEY_STORE_PASSWORD in the runtime environment before starting JAVARA."
else
    printf '%s\n' "TLS termination remains the responsibility of a trusted reverse proxy or load balancer."
fi
printf '%s\n' "Restart the application for changes to take effect."
