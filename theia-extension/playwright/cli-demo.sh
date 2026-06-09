#!/usr/bin/env bash
#
# UTL-X — command-line demo (self-narrating).
#
# Use it two ways:
#   1. Run it yourself:           bash cli-demo.sh
#   2. Drive it from a terminal (e.g. the Theia integrated terminal in Playwright scenario 1):
#      open a terminal and type:  bash theia-extension/playwright/cli-demo.sh
#
# Each step PRINTS a plain-English explanation, then shows the command (like it was typed),
# runs it, and pauses so the audience can read the result.
#
# Pacing knobs (env): SAY_PAUSE (after explanation), TYPE_PAUSE (before running), READ_PAUSE (after
# output). Binary: set UTLX=... (defaults to `utlx` on PATH; e.g. UTLX=./utlx for the JVM wrapper).
#
set -u
UTLX="${UTLX:-utlx}"
SAY_PAUSE="${SAY_PAUSE:-1.2}"
TYPE_PAUSE="${TYPE_PAUSE:-0.8}"
READ_PAUSE="${READ_PAUSE:-2.5}"

# ── presentation helpers ─────────────────────────────────────────────────────
c_say='\033[1;36m'; c_cmd='\033[1;32m'; c_dim='\033[2m'; c_off='\033[0m'
say()  { printf "\n${c_say}# %s${c_off}\n" "$1"; sleep "$SAY_PAUSE"; }
run()  { printf "${c_cmd}\$ ${c_off}%s\n" "$1"; sleep "$TYPE_PAUSE"; eval "$1"; sleep "$READ_PAUSE"; }
banner(){ printf "\n${c_dim}── %s ──${c_off}\n" "$1"; }

clear 2>/dev/null || true
printf "${c_say}UTL-X — one transformation language for XML, JSON, CSV, YAML & more.\n"
printf "Write your logic ONCE; run it against any format.${c_off}\n"
sleep "$READ_PAUSE"

banner "1. What it is"
say "First, which version are we running."
run "$UTLX --version"

banner "2. Instant format conversion — no code at all"
say "Pipe data in, name the format you want out. UTL-X detects the input and converts. A JSON order → XML:"
run "echo '{\"order\":{\"id\":\"A-1\",\"customer\":\"Alice\",\"total\":59.98}}' | $UTLX --to xml"
say "...and the other way around — XML in, JSON out:"
run "echo '<order id=\"A-1\"><customer>Alice</customer></order>' | $UTLX"

banner "3. Familiar: jq-style expressions"
say "If you know jq, you know this. '.name' pulls a field; -r prints it raw (no quotes)."
run "echo '{\"name\":\"Alice\",\"age\":30}' | $UTLX -e '.name' -r"

banner "4. A real functional language"
say "Take the items, map each to its price, sum them. Pipelines (|>) read left-to-right."
run "echo '{\"items\":[{\"price\":10},{\"price\":20},{\"price\":30}]}' | $UTLX -e 'sum(\$input.items |> map(i => i.price))'"

banner "5. 650+ built-in functions"
say "parseNumber understands locale formatting — thousands separators and all — and returns a real number."
run "echo '\"1,234.56\"' | $UTLX -e 'parseNumber(\$input)'"

banner "6. Any format IN"
say "A CSV with a header row becomes a clean array of JSON objects — types inferred."
run "printf 'name,age\\nAlice,30\\nBob,25\\n' | $UTLX --from csv"

banner "7. The 'convert' command — quick, script-free"
say "For a one-off format change there's no need for a script. 'convert' does it straight from the pipe — XML → YAML:"
run "echo '<a><b>x</b></a>' | $UTLX convert --to yaml"

banner "8. THE point: write once, emit any format"
say "Here's the payoff. We write ONE transformation, then ask for three different output formats — same expression."
T='{ invoiceId: "INV-" + $input.order.id, customer: $input.order.customer, total: $input.order.amount, taxed: round($input.order.amount * 1.08, 2) }'
IN='{"order":{"id":"A-1","customer":"Alice Co","amount":59.98}}'
say "→ as JSON:"
run "echo '$IN' | $UTLX -e '$T'"
say "→ the SAME transform as XML:"
run "echo '$IN' | $UTLX --to xml -e '$T'"
say "→ and as YAML. That's what 'format-agnostic' means."
run "echo '$IN' | $UTLX --to yaml -e '$T'"

banner "9. A real script: order → invoice"
say "A transformation file: compute each line's total and the grand total with map + sum."
cat > /tmp/order-to-invoice.utlx <<'EOF'
%utlx 1.0
input json
output json
---
{
  invoice: "INV-" + $input.order.id,
  lines: $input.order.lines |> map(l => { sku: l.sku, total: round(l.qty * l.price, 2) }),
  grandTotal: round(sum($input.order.lines |> map(l => l.qty * l.price)), 2)
}
EOF
printf "${c_dim}# /tmp/order-to-invoice.utlx${c_off}\n"; sed 's/^/    /' /tmp/order-to-invoice.utlx; sleep "$SAY_PAUSE"
run "echo '{\"order\":{\"id\":\"A-9\",\"lines\":[{\"sku\":\"X\",\"qty\":2,\"price\":10},{\"sku\":\"Y\",\"qty\":1,\"price\":29.99}]}}' | $UTLX transform /tmp/order-to-invoice.utlx"

banner "10. The library goes deep (optional flourish)"
say "Crypto, dates, encoding, financial… e.g. a SHA-256 hash:"
run "echo '\"hello\"' | $UTLX -e 'sha256(\$input)'"

printf "\n${c_say}One language. Any format. That's UTL-X.${c_off}\n\n"
