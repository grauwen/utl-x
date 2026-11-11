#!/usr/bin/env bash
# Release-time check: docs/ must be byte-identical on main and development.
#
# This is the guarantee the docs/devdocs split buys. It replaces the old
# "merge then subtract 400 files" reconcile, which could never be verified
# and which left 22 broken links on main.
#
# Usage:  scripts/check-docs-in-sync.sh [main] [development]
set -euo pipefail
A="${1:-main}"
B="${2:-development}"

git rev-parse --verify --quiet "$A" >/dev/null || { echo "no such ref: $A"; exit 2; }
git rev-parse --verify --quiet "$B" >/dev/null || { echo "no such ref: $B"; exit 2; }

if git diff --quiet "$A" "$B" -- docs/; then
    echo "ok    docs/ is identical on $A and $B"
    exit 0
fi

echo "FAIL  docs/ differs between $A and $B:"
echo
git diff --stat "$A" "$B" -- docs/
echo
echo "      docs/ is meant to be the same on both branches."
echo "      Anything that should exist only on $B belongs in devdocs/."
exit 1
