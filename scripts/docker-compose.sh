#!/usr/bin/env bash
set -euo pipefail

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    exec docker compose "$@"
fi

if command -v sudo >/dev/null 2>&1 && sudo -v; then
    exec sudo docker compose "$@"
fi

printf '%s\n' "Docker is unavailable or the current user cannot access its daemon." >&2
exit 1
