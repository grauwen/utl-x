#!/usr/bin/env bash
#
# Kick off the UTL-X IDE talk demo (kiosk).  Scenario: scenarios/scenario1.md
#
# Prereq: Theia + utlxd running on :4000  →  run ../rebuild-and-start-mcp.sh first.
#
# Usage:
#   ./run-demo.sh           # loop until Ctrl-C
#   ./run-demo.sh --once    # one pass, then exit (saves a video to recordings/)
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

# 3. Run. --once → single pass (DEMO_ONCE), good for recording the fallback video.
if [ "${1:-}" = "--once" ]; then export DEMO_ONCE=1; fi
exec node kiosk-demo.js
