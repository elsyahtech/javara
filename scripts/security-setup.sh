#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SECURITY_CONFIG="config/security.yml"
APPLICATION_CONFIG="config/application.yml"
SECURITY_CONFIG_IMPORT="optional:file:./config/security.yml"

read_value() {
    local prompt=$1 default_value=$2 value
    read -r -p "$prompt [$default_value]: " value
    printf '%s' "${value:-$default_value}"
}

validate_boolean() {
    [[ "$1" == "true" || "$1" == "false" ]] || {
        printf '%s\n' "Value must be true or false." >&2
        exit 1
    }
}

validate_positive_integer() {
    [[ "$1" =~ ^[0-9]{1,4}$ ]] && (( 10#$1 >= 1 && 10#$1 <= 1000 )) || {
        printf '%s\n' "Value must be an integer between 1 and 1000." >&2
        exit 1
    }
}

validate_duration() {
    local value=$1 max_millis=$2 amount unit multiplier millis
    [[ "$value" =~ ^([1-9][0-9]{0,3})(ms|s|m|h|d)$ ]] || {
        printf '%s\n' "Duration must use a positive value such as 30s, 10m, or 1h." >&2
        exit 1
    }
    amount="${BASH_REMATCH[1]}"
    unit="${BASH_REMATCH[2]}"
    case "$unit" in
        ms) multiplier=1 ;;
        s) multiplier=1000 ;;
        m) multiplier=60000 ;;
        h) multiplier=3600000 ;;
        d) multiplier=86400000 ;;
    esac
    millis=$((amount * multiplier))
    if (( millis > max_millis )); then
        printf 'Duration must not exceed %s milliseconds.\n' "$max_millis" >&2
        exit 1
    fi
}

validate_action() {
    [[ "$1" == "NOTIFY" || "$1" == "TEMPORARY_LOCK" \
            || "$1" == "LOCK_AND_NOTIFY" ]] || {
        printf '%s\n' "Action must be NOTIFY, TEMPORARY_LOCK, or LOCK_AND_NOTIFY." >&2
        exit 1
    }
}

ensure_security_config_import() {
    local temp_file spring_config_exists
    if grep -Fq "$SECURITY_CONFIG_IMPORT" "$APPLICATION_CONFIG"; then
        return
    fi

    if awk '
        /^spring:[[:space:]]*(#.*)?$/ { in_spring = 1; next }
        in_spring && /^[^[:space:]#]/ { in_spring = 0 }
        in_spring && /^  config:[[:space:]]*(#.*)?$/ { found = 1 }
        END { exit !found }
    ' "$APPLICATION_CONFIG"; then
        spring_config_exists=true
    else
        spring_config_exists=false
    fi

    if awk '
        /^spring:[[:space:]]*(#.*)?$/ { in_spring = 1; next }
        in_spring && /^[^[:space:]#]/ { in_spring = 0 }
        in_spring && /^  config:.*\{/ { found = 1 }
        END { exit !found }
    ' "$APPLICATION_CONFIG"; then
        printf '%s\n' \
            "spring.config uses inline YAML and cannot be safely updated automatically." \
            "Add $SECURITY_CONFIG_IMPORT to spring.config.import, then run make security-setup again." >&2
        exit 1
    fi

    if awk '
        /^spring:[[:space:]]*(#.*)?$/ { in_spring = 1; next }
        in_spring && /^[^[:space:]#]/ { in_spring = 0 }
        in_spring && /^  config:[[:space:]]*(#.*)?$/ { in_config = 1; next }
        in_spring && in_config && /^  [^[:space:]#]/ { in_config = 0 }
        in_spring && in_config && /^    import:/ { found = 1 }
        END { exit !found }
    ' "$APPLICATION_CONFIG"; then
        printf '%s\n' \
            "spring.config.import is already configured without the JAVARA security file." \
            "Add $SECURITY_CONFIG_IMPORT to that import list, then run make security-setup again." >&2
        exit 1
    fi

    temp_file="$(mktemp config/application.yml.XXXXXX)"
    if [[ "$spring_config_exists" == true ]]; then
        awk -v import="$SECURITY_CONFIG_IMPORT" '
            /^spring:[[:space:]]*(#.*)?$/ { in_spring = 1 }
            in_spring && /^[^[:space:]#]/ { in_spring = 0 }
            in_spring && /^  config:[[:space:]]*(#.*)?$/ {
                print
                print "    import: " import
                next
            }
            { print }
        ' "$APPLICATION_CONFIG" > "$temp_file"
    else
        awk -v import="$SECURITY_CONFIG_IMPORT" '
            /^spring:[[:space:]]*(#.*)?$/ {
                print
                print "  config:"
                print "    import: " import
                next
            }
            { print }
        ' "$APPLICATION_CONFIG" > "$temp_file"
    fi
    chmod 600 "$temp_file"
    mv "$temp_file" "$APPLICATION_CONFIG"
}

if [[ ! -f "$APPLICATION_CONFIG" ]]; then
    printf '%s\n' "Run 'make setup' or 'make install' before 'make security-setup'." >&2
    exit 1
fi

if [[ -f "$SECURITY_CONFIG" ]]; then
    read -r -p "Replace existing $SECURITY_CONFIG? [y/N] " overwrite
    if [[ "$overwrite" != "y" && "$overwrite" != "Y" ]]; then
        printf '%s\n' "Security configuration was not changed."
        exit 0
    fi
fi

printf '%s\n' "Configure authentication threat detection and response."
printf '%s\n' "Choose NOTIFY, TEMPORARY_LOCK, or LOCK_AND_NOTIFY for each signal."

suspicious_auth_enabled="$(read_value 'Enable all suspicious-authentication controls (true/false)' 'true')"
failed_login_enabled="$(read_value 'Enable failed-login detection (true/false)' 'true')"
failed_login_threshold="$(read_value 'Failed-login threshold' '5')"
failed_login_window="$(read_value 'Failed-login window' '10m')"
failed_login_lock_duration="$(read_value 'Failed-login lock duration' '15m')"
failed_login_action="$(read_value 'Failed-login action' 'LOCK_AND_NOTIFY')"
new_source_enabled="$(read_value 'Enable new-source detection (true/false)' 'true')"
new_source_action="$(read_value 'New-source action' 'NOTIFY')"
new_source_lock_duration="$(read_value 'New-source lock duration' '15m')"
failed_mfa_enabled="$(read_value 'Enable failed-MFA detection (true/false)' 'true')"
failed_mfa_threshold="$(read_value 'Failed-MFA threshold' '5')"
failed_mfa_window="$(read_value 'Failed-MFA window' '10m')"
failed_mfa_lock_duration="$(read_value 'Failed-MFA lock duration' '15m')"
failed_mfa_action="$(read_value 'Failed-MFA action' 'LOCK_AND_NOTIFY')"
security_change_enabled="$(read_value 'Enable security-change detection (true/false)' 'true')"
security_change_action="$(read_value 'Security-change action' 'NOTIFY')"
security_change_lock_duration="$(read_value 'Security-change lock duration' '15m')"

validate_boolean "$suspicious_auth_enabled"
validate_boolean "$failed_login_enabled"
validate_positive_integer "$failed_login_threshold"
validate_duration "$failed_login_window" 86400000
validate_duration "$failed_login_lock_duration" 604800000
validate_action "$failed_login_action"
validate_boolean "$new_source_enabled"
validate_action "$new_source_action"
validate_duration "$new_source_lock_duration" 604800000
validate_boolean "$failed_mfa_enabled"
validate_positive_integer "$failed_mfa_threshold"
validate_duration "$failed_mfa_window" 86400000
validate_duration "$failed_mfa_lock_duration" 604800000
validate_action "$failed_mfa_action"
validate_boolean "$security_change_enabled"
validate_action "$security_change_action"
validate_duration "$security_change_lock_duration" 604800000

printf '\n%s\n' "Security configuration summary:"
printf '  Controls enabled: %s\n' "$suspicious_auth_enabled"
printf '  Failed login: %s, threshold %s in %s, %s, lock %s\n' \
    "$failed_login_enabled" "$failed_login_threshold" "$failed_login_window" \
    "$failed_login_action" "$failed_login_lock_duration"
printf '  New source: %s, %s, lock %s\n' \
    "$new_source_enabled" "$new_source_action" "$new_source_lock_duration"
printf '  Failed MFA: %s, threshold %s in %s, %s, lock %s\n' \
    "$failed_mfa_enabled" "$failed_mfa_threshold" "$failed_mfa_window" \
    "$failed_mfa_action" "$failed_mfa_lock_duration"
printf '  Security changes: %s, %s, lock %s\n' \
    "$security_change_enabled" "$security_change_action" "$security_change_lock_duration"
read -r -p "Save this security configuration? [y/N] " confirm
if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    printf '%s\n' "Security configuration was not changed."
    exit 0
fi

ensure_security_config_import

mkdir -p config
temp_file="$(mktemp config/security.yml.XXXXXX)"
cat > "$temp_file" <<EOF
app:
  security:
    suspicious-authentication:
      enabled: ${suspicious_auth_enabled}
      failed-login:
        enabled: ${failed_login_enabled}
        threshold: ${failed_login_threshold}
        window: ${failed_login_window}
        lock-duration: ${failed_login_lock_duration}
        action: ${failed_login_action}
      new-source:
        enabled: ${new_source_enabled}
        action: ${new_source_action}
        lock-duration: ${new_source_lock_duration}
      failed-mfa:
        enabled: ${failed_mfa_enabled}
        threshold: ${failed_mfa_threshold}
        window: ${failed_mfa_window}
        lock-duration: ${failed_mfa_lock_duration}
        action: ${failed_mfa_action}
      security-change:
        enabled: ${security_change_enabled}
        action: ${security_change_action}
        lock-duration: ${security_change_lock_duration}
EOF
chmod 600 "$temp_file"
mv "$temp_file" "$SECURITY_CONFIG"

printf '\n%s\n' "Security configuration saved to $SECURITY_CONFIG."
printf '%s\n' "Restart the application for changes to take effect."
