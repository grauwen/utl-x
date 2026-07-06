#!/usr/bin/env bash
#
# migrate-namespace.sh — org.apache.utlx  →  com.glomidco.utlx
#
# Deterministic, run-once package-namespace migration.
# See docs/features/RENAME-namespace-org-apache-to-owned.md (Decision 1 & 3).
#
# This script is the SOURCE OF TRUTH for the rename. Run it identically on:
#   1. uat/namespace     (cut from main)         — rehearse here FIRST
#   2. feature/namespace (cut from development)   — then run the SAME script
# Do NOT hand-edit renames. If a manual fix is needed, fold it back INTO this
# script so the transform stays deterministic and reproducible across branches.
#
# Anchored on the EXACT strings 'org.apache.utlx' / 'org/apache/utlx' — it must
# NEVER touch genuine Apache deps (org.apache.{avro,arrow,camel,hadoop,parquet,
# santuario,tomcat,velocity,xerces,xml}, org.xml.sax).
#
# Usage:
#   scripts/migrate-namespace.sh            # DRY RUN (default) — shows the plan
#   scripts/migrate-namespace.sh --apply    # execute: git mv dirs + rewrite text
#   scripts/migrate-namespace.sh --verify   # post-apply checks only
#
# Compatible with macOS bash 3.2 (no mapfile / associative arrays).

set -euo pipefail

MODE="${1:---dry-run}"
cd "$(git rev-parse --show-toplevel)"

OLD_DOT="org.apache.utlx";  NEW_DOT="com.glomidco.utlx"
OLD_PATH="org/apache/utlx"; NEW_PATH="com/glomidco/utlx"

# Real Apache deps that must remain org.apache.* — used only for the sanity check.
GENUINE='org\.apache\.\(avro\|arrow\|camel\|hadoop\|parquet\|santuario\|tomcat\|velocity\|xerces\|xml\)'

exclude_re='(^|/)(build|node_modules|\.gradle)/'
# Meta files that legitimately CONTAIN the old string (this tool + the migration doc):
# never rewrite them (they'd self-corrupt), never flag them in verify.
meta_re='(scripts/migrate-namespace\.sh|RENAME-namespace)'

verify() {
  echo "== VERIFY: remaining '$OLD_DOT' (expect 0 matches) =="
  if git grep -n "$OLD_DOT" -- . ':(exclude)build' ':(exclude)node_modules' ':(exclude)scripts/migrate-namespace.sh' ':(exclude)*RENAME-namespace*' >/tmp/ns-left.txt 2>/dev/null && [ -s /tmp/ns-left.txt ]; then
    cat /tmp/ns-left.txt; echo "  !! leftovers above — investigate before building"
  else
    echo "  none ✓"
  fi
  echo "== VERIFY: genuine Apache deps still present (expect matches) =="
  git grep -l "$GENUINE" | head || echo "  (none found — double-check)"
  echo "== Done. Next: clean-build all modules + native binary + 467-test conformance suite. =="
}

if [ "$MODE" = "--verify" ]; then verify; exit 0; fi

# ── Guard: clean working tree (renames must be an atomic, isolated commit) ──
# The migration script itself is allowed to be dirty (it is the tool, not the codebase).
if [ -n "$(git status --porcelain | grep -vF 'scripts/migrate-namespace.sh')" ]; then
  echo "ERROR: working tree not clean. Commit or stash first (the rename must be one atomic change)."
  exit 1
fi

echo "== Branch: $(git branch --show-current)   Mode: $MODE =="

# ── 1. Package directory moves (git mv preserves history) ──
git ls-files | grep -oE ".*/$OLD_PATH" | sort -u > /tmp/ns-dirs.txt
echo "== $(wc -l < /tmp/ns-dirs.txt | tr -d ' ') package directories to move =="
while IFS= read -r d; do
  [ -n "$d" ] || continue
  tgt="${d%$OLD_PATH}$NEW_PATH"
  if [ "$MODE" = "--apply" ]; then
    mkdir -p "$(dirname "$tgt")"
    git mv "$d" "$tgt"
  else
    echo "  git mv $d -> $tgt"
  fi
done < /tmp/ns-dirs.txt

# ── 2. Anchored text rewrite across tracked text files (exclude build artifacts) ──
git ls-files | grep -vE "$exclude_re" | grep -vE "$meta_re" > /tmp/ns-files.txt
echo "== scanning $(wc -l < /tmp/ns-files.txt | tr -d ' ') tracked files for the anchored strings =="
count=0
while IFS= read -r f; do
  [ -f "$f" ] || continue                       # skip paths git-mv'd away in --apply
  grep -Iq . "$f" 2>/dev/null || continue        # skip binary
  if grep -q -e "$OLD_DOT" -e "$OLD_PATH" "$f"; then
    count=$((count + 1))
    if [ "$MODE" = "--apply" ]; then
      LC_ALL=C perl -pi -e "s{\\Q$OLD_DOT\\E}{$NEW_DOT}g; s{\\Q$OLD_PATH\\E}{$NEW_PATH}g" "$f"
    else
      echo "  would rewrite: $f"
    fi
  fi
done < /tmp/ns-files.txt
echo "== $count files contain '$OLD_DOT' / '$OLD_PATH' =="

if [ "$MODE" = "--apply" ]; then
  echo ""
  verify
else
  echo ""
  echo "DRY RUN complete. Re-run with --apply to execute."
  echo "After --apply: review 'git status', then build (you) + I help fix + fold fixes back here."
fi
