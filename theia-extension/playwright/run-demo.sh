#!/usr/bin/env bash
#
# Kick off the UTL-X IDE talk demo (kiosk).  Scenario: scenarios/scenario1.md
#
# Prereq: Theia + utlxd running on :4000  →  run ../rebuild-and-start-mcp.sh first.
#
# Usage (default = one pass, then leave the browser open — best for a talk):
#   ./run-demo.sh           # one pass, then LEAVE THE BROWSER OPEN (Ctrl-C to close + flush video)
#   ./run-demo.sh --loop    # repeat until Ctrl-C (kiosk attract mode)
#   ./run-demo.sh --once    # one pass, then close (flush the video to recordings/ — for recording)
set -euo pipefail
cd "$(dirname "$0")"

# 1. Theia must be up on :4000 (the demo drives the IDE there).
if ! curl -sf http://localhost:4000 >/dev/null 2>&1; then
  echo "✗ Theia is not responding on http://localhost:4000"
  echo "  Start it first:  ../rebuild-and-start-mcp.sh"
  exit 1
fi
echo "✓ Theia is up on :4000"

# 2. Resolve Playwright: prefer this dir's node_modules, else reuse playwright-mcp-server's.
if [ -d node_modules/playwright ]; then
  : # local install — node resolves it automatically
elif [ -d ../../playwright-mcp-server/node_modules/playwright ]; then
  export NODE_PATH="$(cd ../../playwright-mcp-server/node_modules && pwd)"
  echo "ℹ using Playwright from playwright-mcp-server/node_modules"
else
  echo "✗ Playwright not found. Install it once:  npm i && npx playwright install chromium"
  exit 1
fi

# 3. Run modes (DEFAULT = one pass, then leave the browser open):
#    (no flag)            → one pass, then stay open (Ctrl-C to close + flush video)
#    --loop               → repeat until Ctrl-C (DEMO_LOOP; kiosk attract mode)
#    --once               → single pass, then close (DEMO_ONCE; flushes the video — for recording)
case "${1:-}" in
  --loop)                export DEMO_LOOP=1 ;;
  --once)                export DEMO_ONCE=1 ;;
  --stay-open|--open|"")  : ;;  # default behaviour (one pass, stay open) — accept the explicit alias too
  *) echo "Unknown option: $1 (use --loop or --once; default = one pass, stay open)"; exit 1 ;;
esac
exec node kiosk-demo.js
