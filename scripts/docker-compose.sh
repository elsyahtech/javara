#!/usr/bin/env bash
set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
    printf '%s\n' "Docker CLI was not found. Install Docker Engine and Compose v2 first." >&2
    exit 1
fi

if docker info >/dev/null 2>&1; then
    exec docker compose "$@"
fi

if (( EUID == 0 )); then
    printf '%s\n' "Docker daemon is not reachable. Start Docker and retry." >&2
    exit 1
fi

if command -v sudo >/dev/null 2>&1; then
    exec sudo docker compose "$@"
fi

printf '%s\n' "Docker daemon is unavailable or inaccessible; install sudo or grant access to the Docker socket." >&2
exit 1
