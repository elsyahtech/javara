#!/usr/bin/env bash
set -euo pipefail

is_jdk_25_or_newer() {
    local java_command=$1
    local compiler_command="${java_command%/java}/javac"
    local major
    [[ -x "$compiler_command" ]] || return 1
    major="$("$java_command" -version 2>&1 \
        | sed -nE 's/.*version "([0-9]+).*/\1/p' \
        | head -n 1)"
    [[ "$major" =~ ^[0-9]+$ ]] && (( major >= 25 ))
}

java_bin=""
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]] \
        && is_jdk_25_or_newer "$JAVA_HOME/bin/java"; then
    java_bin="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1 \
        && is_jdk_25_or_newer "$(command -v java)"; then
    java_bin="$(command -v java)"
    unset JAVA_HOME
elif [[ "$(uname -s)" == "Darwin" ]] && command -v brew >/dev/null 2>&1; then
    brew_prefix="$(brew --prefix openjdk@25 2>/dev/null || true)"
    java_home="$brew_prefix/libexec/openjdk.jdk/Contents/Home"
    if [[ -x "$java_home/bin/java" ]] && is_jdk_25_or_newer "$java_home/bin/java"; then
        export JAVA_HOME="$java_home"
        export PATH="$JAVA_HOME/bin:$PATH"
        java_bin="$JAVA_HOME/bin/java"
    fi
fi

if [[ -z "$java_bin" ]]; then
    printf '%s\n' "Java 25 is required. Run 'make install' to install the prerequisites." >&2
    exit 1
fi

spring_boot_run=false
for argument in "$@"; do
    if [[ "$argument" == "spring-boot:run" ]]; then
        spring_boot_run=true
        break
    fi
done

if [[ "$spring_boot_run" == "true" ]]; then
    unset SPRING_CONFIG_LOCATION
    export SPRING_CONFIG_ADDITIONALLOCATION="optional:file:./config/"
else
    unset SPRING_CONFIG_ADDITIONALLOCATION
    export SPRING_CONFIG_LOCATION="classpath:/"
fi

exec ./mvnw "$@"
