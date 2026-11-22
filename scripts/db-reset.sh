#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' "This permanently deletes the local PostgreSQL Docker volume."
read -r -p "Type DELETE DATA to continue: " confirmation
if [[ "$confirmation" != "DELETE DATA" ]]; then
    printf '%s\n' "Database volume was left unchanged."
    exit 0
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
bash scripts/docker-compose.sh down -v
