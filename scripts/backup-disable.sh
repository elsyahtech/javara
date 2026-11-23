#!/usr/bin/env bash
set -euo pipefail

CRON_MARKER="# JAVARA_BACKUP_SCHEDULER"

if ! command -v crontab >/dev/null 2>&1; then
    printf '%s\n' "crontab is unavailable; disable the backup job in your external scheduler." >&2
    exit 1
fi

old_crontab="$(crontab -l 2>/dev/null || true)"
if [[ "$old_crontab" != *"$CRON_MARKER"* ]]; then
    printf '%s\n' "No JAVARA-managed cron backup job was found."
    printf '%s\n' "Backup configuration and backup files were left unchanged."
    exit 0
fi

crontab_file="$(mktemp)"
trap 'rm -f "$crontab_file"' EXIT
printf '%s\n' "$old_crontab" | sed "\|$CRON_MARKER\$|d" > "$crontab_file"
crontab "$crontab_file"
printf '%s\n' "JAVARA cron backup job disabled. Configuration and backup files were left unchanged."
