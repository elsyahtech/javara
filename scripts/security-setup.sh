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

validate_session_limit() {
    [[ "$1" =~ ^[0-9]{1,3}$ ]] && (( 10#$1 >= 1 && 10#$1 <= 100 )) || {
        printf '%s\n' "Maximum concurrent sessions must be an integer between 1 and 100." >&2
        exit 1
    }
}

duration_to_millis() {
    local value=$1 amount unit multiplier
    [[ "$value" =~ ^([1-9][0-9]{0,3})(ms|s|m|h|d)$ ]] || return 1
    amount="${BASH_REMATCH[1]}"
    unit="${BASH_REMATCH[2]}"
    case "$unit" in
        ms) multiplier=1 ;;
        s) multiplier=1000 ;;
        m) multiplier=60000 ;;
        h) multiplier=3600000 ;;
        d) multiplier=86400000 ;;
    esac
    printf '%s' "$((10#$amount * multiplier))"
}

validate_duration() {
    local value=$1 max_millis=$2 millis
    millis="$(duration_to_millis "$value")" || {
        printf '%s\n' "Duration must use a positive value such as 30s, 10m, or 1h." >&2
        exit 1
    }
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

printf '%s\n' "Configure session policy and authentication threat detection."
printf '%s\n' "Choose NOTIFY, TEMPORARY_LOCK, or LOCK_AND_NOTIFY for each signal."
printf '%s\n' "The maximum session duration is counted from sign-in and is not extended by refresh."
printf '%s\n' "If disabled, refresh-token-ttl sets the fixed maximum session duration instead."

idle_timeout_enabled="$(read_value 'Enable session idle timeout (true/false)' 'true')"
validate_boolean "$idle_timeout_enabled"
if [[ "$idle_timeout_enabled" == true ]]; then
    idle_timeout="$(read_value 'Session idle timeout' '15m')"
else
    idle_timeout='15m'
fi
absolute_lifetime_enabled="$(read_value 'Set a maximum session duration from sign-in (true/false)' 'true')"
validate_boolean "$absolute_lifetime_enabled"
if [[ "$absolute_lifetime_enabled" == true ]]; then
    absolute_lifetime="$(read_value 'Maximum session duration from sign-in (up to 12h)' '12h')"
else
    absolute_lifetime='12h'
fi
refresh_token_rotation_enabled="$(read_value 'Rotate refresh token on each successful refresh (true/false)' 'true')"
validate_boolean "$refresh_token_rotation_enabled"
max_concurrent_sessions="$(read_value 'Maximum concurrent sessions per user (1-100)' '5')"

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

validate_duration "$idle_timeout" 43200000
if [[ "$absolute_lifetime_enabled" == true ]]; then
    validate_duration "$absolute_lifetime" 43200000
fi
if [[ "$idle_timeout_enabled" == true && "$absolute_lifetime_enabled" == true ]] \
        && (( $(duration_to_millis "$absolute_lifetime") <= $(duration_to_millis "$idle_timeout") )); then
    printf '%s\n' "Maximum session duration must exceed the enabled idle timeout." >&2
    exit 1
fi
validate_session_limit "$max_concurrent_sessions"

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
if [[ "$idle_timeout_enabled" == true ]]; then
    printf '  Session idle timeout: enabled, %s\n' "$idle_timeout"
else
    printf '  Session idle timeout: disabled\n'
    if [[ "$absolute_lifetime_enabled" == true ]]; then
        printf '  Sessions still end after the maximum session duration: %s\n' "$absolute_lifetime"
    else
        printf '%s\n' "  Sessions still expire at refresh-token-ttl measured from login."
    fi
    printf '%s\n' "  Review the security impact before disabling idle expiry."
fi
if [[ "$absolute_lifetime_enabled" == true ]]; then
    printf '  Maximum session duration from sign-in: enabled, %s\n' "$absolute_lifetime"
else
    printf '%s\n' "  Maximum session duration setting: disabled"
    printf '%s\n' "  Session maximum lifetime follows refresh-token-ttl from login (default 30d)."
    printf '%s\n' "  Review the security impact before disabling this limit."
fi
if [[ "$refresh_token_rotation_enabled" == true ]]; then
    printf '%s\n' "  Refresh-token rotation: enabled; the old token is revoked after refresh."
else
    printf '%s\n' "  Refresh-token rotation: disabled; refresh keeps the same token."
    printf '%s\n' "  A stolen refresh token can be reused until its fixed expiry or session revocation."
fi
printf '  Maximum concurrent sessions: %s\n' "$max_concurrent_sessions"
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
    jwt:
      refresh-token-rotation-enabled: ${refresh_token_rotation_enabled}
    session:
      idle-timeout-enabled: ${idle_timeout_enabled}
      idle-timeout: ${idle_timeout}
      absolute-lifetime-enabled: ${absolute_lifetime_enabled}
      absolute-lifetime: ${absolute_lifetime}
      max-concurrent-sessions: ${max_concurrent_sessions}
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
