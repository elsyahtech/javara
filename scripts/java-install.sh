#!/usr/bin/env bash
set -euo pipefail

java_is_ready() {
    local java_command=$1
    local compiler="${java_command%/java}/javac"
    local major
    [[ -x "$java_command" && -x "$compiler" ]] || return 1
    major="$("$java_command" -version 2>&1 \
        | sed -nE 's/.*version "([0-9]+).*/\1/p' \
        | head -n 1)"
    [[ "$major" =~ ^[0-9]+$ ]] && (( major >= 25 ))
}

if [[ -n "${JAVA_HOME:-}" ]] && java_is_ready "$JAVA_HOME/bin/java"; then
    exit 0
fi
if command -v java >/dev/null 2>&1 && java_is_ready "$(command -v java)"; then
    exit 0
fi

read -r -p "JDK 25 is required to run the API. Install it now? [y/N] " answer
if [[ "$answer" != "y" && "$answer" != "Y" ]]; then
    printf '%s\n' "Install JDK 25 or newer, then run 'make setup' again." >&2
    exit 1
fi

case "$(uname -s)" in
    Darwin)
        if ! command -v brew >/dev/null 2>&1; then
            printf '%s\n' "Install Homebrew from https://brew.sh, then run 'make setup' again." >&2
            exit 1
        fi
        brew install openjdk@25
        ;;
    Linux)
        if [[ ! -r /etc/os-release ]]; then
            printf '%s\n' "Cannot identify this Linux distribution. Install JDK 25 manually." >&2
            exit 1
        fi
        # shellcheck disable=SC1091
        . /etc/os-release
        case "${ID:-} ${ID_LIKE:-}" in
            *debian*|*ubuntu*)
                sudo apt-get update
                sudo apt-get install -y openjdk-25-jdk
                ;;
            *fedora*|*rhel*|*centos*|*rocky*|*almalinux*)
                sudo dnf install -y java-25-openjdk-devel
                ;;
            *arch*)
                sudo pacman -Sy --needed --noconfirm jdk-openjdk
                ;;
            *opensuse*|*suse*)
                sudo zypper --non-interactive install java-25-openjdk-devel
                ;;
            *)
                printf 'Automatic JDK installation is not configured for "%s". Install JDK 25 manually.\n' \
                    "${ID:-unknown}" >&2
                exit 1
                ;;
        esac
        ;;
    *)
        printf '%s\n' "Automatic JDK installation is supported on macOS and recognized Linux distributions." >&2
        exit 1
        ;;
esac

if command -v java >/dev/null 2>&1 && java_is_ready "$(command -v java)"; then
    exit 0
fi

if [[ "$(uname -s)" == "Darwin" ]] && command -v brew >/dev/null 2>&1; then
    brew_prefix="$(brew --prefix openjdk@25 2>/dev/null || true)"
    if java_is_ready "$brew_prefix/libexec/openjdk.jdk/Contents/Home/bin/java"; then
        exit 0
    fi
fi

if ! command -v java >/dev/null 2>&1; then
    printf '%s\n' "JDK 25 was installed but is not available on PATH. Configure JAVA_HOME, then run 'make setup' again." >&2
    exit 1
fi

printf '%s\n' "JDK 25 was installed, but the selected Java on PATH is older. Set JAVA_HOME to JDK 25 before running the API." >&2
exit 1
