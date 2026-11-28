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
# output). Binary: set UTLX=... to force one; otherwise resolved below (PATH, then the repo's
# native build, then its jar wrapper) and printed before step 1.
#
set -u
SAY_PAUSE="${SAY_PAUSE:-1.2}"
TYPE_PAUSE="${TYPE_PAUSE:-0.8}"
READ_PAUSE="${READ_PAUSE:-2.5}"

# ── which binary? ────────────────────────────────────────────────────────────
# PATH first (that is the normal install), then the repo builds. A demo must never fail with
# "command not found" in front of an audience, and it must never silently run a STALE binary: an
# older build lacks the whole validate.* namespace, and one merely predating F32 reports the SAME
# verdict for a different reason. So the chosen binary is printed before anything runs.
#
# The repo is resolved TWO ways on purpose: relative to this script (correct in the repo) and by
# absolute path (correct for a copy kept elsewhere — e.g. the Theia workspace, where the relative
# walk lands outside the repo and would silently fall through to a missing `utlx`). The absolute
# path mirrors DEMO_DIR in utlx-frontend-contribution.ts, which already hardcodes it.
UTLX_REPOS=(
  "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." 2>/dev/null && pwd)"
  "/Users/magr/data/mapping/github-git/utl-x"
)
pick_utlx() {
  [ -n "${UTLX:-}" ] && { printf '%s' "$UTLX"; return; }
  command -v utlx >/dev/null 2>&1 && { printf 'utlx'; return; }
  local r native jar newest_jar
  for r in "${UTLX_REPOS[@]}"; do
    [ -n "$r" ] || continue
    native="$r/modules/cli/build/native/nativeCompile/utlx"
    jar="$r/utlx"

    # Prefer whichever is NEWER, rather than always the native binary. `nativeCompile` takes ~3.5
    # minutes and does not track source changes, so the native build lags by days in normal use --
    # on 2026-11-28 it was five days old and carried a 705-entry function catalogue against the
    # jar's 726, missing arrayConcat entirely and still advertising the phantom createQName that
    # B68 removed. The old order picked that binary and merely PRINTED a warning, which is not a
    # control: a warning nobody reads is how a demo ends up showing a plausible wrong answer. The
    # jar wrapper always resolves the newest cli-*.jar, so preferring the fresher of the two is
    # both safe and automatic. Set UTLX=... to force either.
    if [ -x "$native" ] && [ -x "$jar" ]; then
      newest_jar="$(ls -t "$r"/modules/cli/build/libs/cli-*.jar 2>/dev/null | head -n 1)"
      if [ -n "$newest_jar" ] && [ "$newest_jar" -nt "$native" ]; then
        printf '%s' "$jar"; return
      fi
      printf '%s' "$native"; return
    fi
    [ -x "$native" ] && { printf '%s' "$native"; return; }
    [ -x "$jar" ] && { printf '%s' "$jar"; return; }
  done
  printf 'utlx'
}
UTLX="$(pick_utlx)"
UTLX_STALE=""

# A stale NATIVE build is the trap this guards. `nativeCompile` takes ~3 minutes and does not
# track source changes, so the binary silently lags behind the jar — and a demo then fails on a
# flag or function that exists in the source. (It has happened twice: once missing a validator,
# once missing a flag, each time reporting something plausible instead of "out of date".) The jar
# wrapper has no such problem: it picks the newest cli-*.jar every run.
case "$UTLX" in
  */nativeCompile/utlx)
    for r in "${UTLX_REPOS[@]}"; do
      [ -n "$r" ] || continue
      newest_jar="$(ls -t "$r"/modules/cli/build/libs/cli-*.jar 2>/dev/null | head -n 1)"
      # Collected, not printed: `clear` below would wipe it. Shown with the binary line.
      [ -n "$newest_jar" ] && [ "$newest_jar" -nt "$UTLX" ] && UTLX_STALE="$r"
      break
    done
    ;;
esac

# ── presentation helpers ─────────────────────────────────────────────────────
c_say='\033[1;36m'; c_cmd='\033[1;32m'; c_dim='\033[2m'; c_off='\033[0m'
say()  { printf "\n${c_say}# %s${c_off}\n" "$1"; sleep "$SAY_PAUSE"; }
# Echo the command as a human would TYPE it: the resolved binary may be a 70-character absolute
# path, which is correct to execute and unreadable on a slide. Executed verbatim, displayed short.
run()  { printf "${c_cmd}\$ ${c_off}%s\n" "${1//$UTLX/utlx}"; sleep "$TYPE_PAUSE"; eval "$1"; sleep "$READ_PAUSE"; }
banner(){ printf "\n${c_dim}── %s ──${c_off}\n" "$1"; }
# awk, not sed: a file with no trailing newline would otherwise run into the next line printed.
show()  { local r="${2:-}"; printf "${c_dim}# %s%s${c_off}\n" "${r:+$r  ·  }" "$(basename "$1")"
          awk '{print "    " $0}' "$1"; sleep "$SAY_PAUSE"; }

clear 2>/dev/null || true
printf "${c_say}UTL-X — one transformation language for XML, JSON, CSV, YAML & more.\n"
printf "Write your logic ONCE; run it against any format.${c_off}\n"
printf "${c_dim}binary: %s${c_off}\n" "$UTLX"
[ -n "$UTLX_STALE" ] && {
  printf "${c_dim}note: this native build is OLDER than the jar and may lack recent changes.${c_off}\n"
  printf "${c_dim}      rebuild: ./gradlew :modules:cli:nativeCompile   or run with UTLX=%s/utlx${c_off}\n" "$UTLX_STALE"
}
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
W=/tmp/utlx-cli-demo; mkdir -p "$W"; cd "$W"
cat > order-to-invoice.utlx <<'EOF'
%utlx 1.0
input json
output json
---
{
  invoice: "INV-" + $input.order.id,
  lines: $input.order.lines |> map(l => { sku: l.sku, total: l.qty * l.price }),
  grandTotal: sum($input.order.lines |> map(l => l.qty * l.price))
}
EOF
cat > order.json <<'EOF'
{"order": {"id": "A-9", "lines": [
  {"sku": "X", "qty": 2, "price": 10.00},
  {"sku": "Y", "qty": 1, "price": 29.99}
]}}
EOF
show order-to-invoice.utlx "THE TRANSFORMATION"
say "The input Data"
show order.json            "THE INPUT DATA"
say "Both totals keep two decimals — the arithmetic is exact, not rounded and reformatted."
# No "THE OUTPUT" label: run() prints the command and then evaluates it, so a label would land
# ABOVE the `$` line and read as if it described the command. The `$` already tells a viewer that
# what follows is the result — that convention holds in every other step, so leave it alone. The
# two blocks ABOVE needed labels because nothing else distinguishes a transformation from its data.
run "$UTLX transform order-to-invoice.utlx order.json"

banner "10. The library goes deep (optional flourish)"
say "Crypto, dates, encoding, financial… e.g. a SHA-256 hash:"
run "echo '\"hello\"' | $UTLX -e 'sha256(\$input)'"

printf "\n${c_say}One language. Any format. That's UTL-X.${c_off}\n\n"
