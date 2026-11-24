#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

target_dir="${1:-config/security}"
mkdir -p "$target_dir"

for dataset in common-passwords.txt breached-password-hashes.txt; do
    target="$target_dir/$dataset"
    if [[ -e "$target" || -L "$target" ]]; then
        printf 'Preserving existing password dataset: %s\n' "$target"
        continue
    fi

    cp "scripts/password-policy-samples/$dataset" "$target"
    chmod 600 "$target"
    printf 'Created illustrative password-policy sample: %s\n' "$target"
done

printf '%s\n' \
    "These examples are for local development only; provide approved datasets before production."
