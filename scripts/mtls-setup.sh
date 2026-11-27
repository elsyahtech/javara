#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# shellcheck source=scripts/configuration-support.sh
. "$ROOT_DIR/scripts/configuration-support.sh"

TLS_CONFIG="config/tls.yml"
MTLS_CONFIG="config/mtls.yml"
APPLICATION_CONFIG="config/application.yml"

if [[ -f "$MTLS_CONFIG" ]]; then
    read -r -p "Replace existing $MTLS_CONFIG? [y/N] " overwrite
    if [[ "$overwrite" != "y" && "$overwrite" != "Y" ]]; then
        printf '%s\n' "mTLS configuration was not changed."
        exit 0
    fi
fi

mtls_enabled="$(read_setup_yes_no 'Require client certificates for inbound HTTPS connections?' 'N')"

trust_store_path=
trust_store_type=
if [[ "$mtls_enabled" == "true" ]]; then
    if ! grep -Eq '^[[:space:]]*enabled:[[:space:]]*true[[:space:]]*$' "$TLS_CONFIG" 2>/dev/null \
            || ! grep -Eq '^[[:space:]]*key-store:' "$TLS_CONFIG"; then
        printf '%s\n' "Configure application TLS first with 'make tls-setup' before enabling inbound mTLS." >&2
        exit 1
    fi

    raw_trust_store_path="$(read_setup_value 'Absolute or repository-relative PKCS12/JKS truststore path' '')"
    trust_store_path="$(validate_setup_file_path 'Truststore' "$raw_trust_store_path")"
    trust_store_type="$(read_setup_value 'Truststore type (PKCS12/JKS)' 'PKCS12')"
    trust_store_type="${trust_store_type^^}"
    if [[ "$trust_store_type" != "PKCS12" && "$trust_store_type" != "JKS" ]]; then
        printf '%s\n' "Truststore type must be PKCS12 or JKS." >&2
        exit 1
    fi
fi

ensure_runtime_config_imports "$APPLICATION_CONFIG" \
    optional:file:./config/database.yml \
    optional:file:./config/tls.yml \
    optional:file:./config/mtls.yml

mkdir -p config
temp_file="$(mktemp "${MTLS_CONFIG}.XXXXXX")"
if [[ "$mtls_enabled" == "true" ]]; then
    cat > "$temp_file" <<EOF
server:
  ssl:
    client-auth: need
    trust-store: $(yaml_quote "file:$trust_store_path")
    trust-store-type: ${trust_store_type}
    trust-store-password: \${SERVER_SSL_TRUST_STORE_PASSWORD}
EOF
else
    cat > "$temp_file" <<'EOF'
server:
  ssl:
    client-auth: none
EOF
fi
chmod 600 "$temp_file"
mv "$temp_file" "$MTLS_CONFIG"

printf '\n%s\n' "Inbound mTLS configuration saved to $MTLS_CONFIG."
if [[ "$mtls_enabled" == "true" ]]; then
    printf '%s\n' "Set SERVER_SSL_TRUST_STORE_PASSWORD in the runtime environment before starting JAVARA."
    printf '%s\n' "Only clients whose certificates chain to this truststore can connect."
else
    printf '%s\n' "Client certificates are not required."
fi
printf '%s\n' "Restart the application for changes to take effect."
