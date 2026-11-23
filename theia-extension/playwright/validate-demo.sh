#!/usr/bin/env bash
#
# UTL-X — `validate.*` demo (self-narrating), the %utlx 1.1 semantic-validation namespace.
#
# Companion to cli-demo.sh, same shape and same pacing knobs. That one shows the LANGUAGE;
# this one shows VALIDATION: a rule list as data, every failure naming its own rule, and the
# same rules applied to more than one format.
#
# Use it two ways:
#   1. Run it yourself:   bash validate-demo.sh
#   2. From the IDE:      Help → "UTL-X: Demo validate (terminal)"
#
# Pacing knobs (env): SAY_PAUSE (after explanation), TYPE_PAUSE (before running), READ_PAUSE
# (after output). Binary: set UTLX=... to override; otherwise resolved below.
#
# NOTE for anyone extending this: `validate.*` cannot be demonstrated with `utlx -e '...'`.
# A header-less inline expression is treated as `%utlx 1.0`, and the namespace is a 1.1 feature —
# `--language-mode 1.1` does NOT help, because that caps the engine rather than declaring the
# script's version. So every step here writes a real .utlx file with a `%utlx 1.1` header, which
# is better for an audience anyway: they see the rules.
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
  local r
  for r in "${UTLX_REPOS[@]}"; do
    [ -n "$r" ] || continue
    [ -x "$r/modules/cli/build/native/nativeCompile/utlx" ] \
      && { printf '%s' "$r/modules/cli/build/native/nativeCompile/utlx"; return; }
    [ -x "$r/utlx" ] && { printf '%s' "$r/utlx"; return; }
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

# ── presentation helpers (identical to cli-demo.sh) ──────────────────────────
c_say='\033[1;36m'; c_cmd='\033[1;32m'; c_dim='\033[2m'; c_off='\033[0m'
say()   { printf "\n${c_say}# %s${c_off}\n" "$1"; sleep "$SAY_PAUSE"; }
# Echo the command as a human would TYPE it: the resolved binary may be a 70-character absolute
# path, which is correct to execute and unreadable on a slide. Executed verbatim, displayed short.
run()   { printf "${c_cmd}\$ ${c_off}%s\n" "${1//$UTLX/utlx}"; sleep "$TYPE_PAUSE"; eval "$1"; sleep "$READ_PAUSE"; }
banner(){ printf "\n${c_dim}── %s ──${c_off}\n" "$1"; }
# awk, not sed: these fixtures are written with printf and have no trailing newline, and sed
# passes an unterminated last line straight through — so the next line printed (run()'s "$ ...",
# which has no leading newline) ran onto the end of the data. awk terminates every record.
show()  { printf "${c_dim}# %s${c_off}\n" "$(basename "$1")"; awk '{print "    " $0}' "$1"; sleep "$SAY_PAUSE"; }

W=/tmp/utlx-validate-demo; mkdir -p "$W"
# Work from there, so every command on screen reads "policy.utlx" not "/tmp/…/policy.utlx".
cd "$W"

clear 2>/dev/null || true
printf "${c_say}UTL-X 1.1 — validation as DATA.\n"
printf "A rule list you can read, review and audit. Every failure names its own rule.${c_off}\n"
printf "${c_dim}binary: %s${c_off}\n" "$UTLX"
[ -n "$UTLX_STALE" ] && {
  printf "${c_dim}note: this native build is OLDER than the jar and may lack recent changes.${c_off}\n"
  printf "${c_dim}      rebuild: ./gradlew :modules:cli:nativeCompile   or run with UTLX=%s/utlx${c_off}\n" "$UTLX_STALE"
}
sleep "$READ_PAUSE"

banner "1. Which build are we on"
say "validate.* arrived in %utlx 1.1, so the version matters more than usual here."
run "$UTLX --version"

banner "2. One rule list, one verdict"
say "Rules are DATA — a list of objects, each with an id. Not scattered if/else. Here are five."
cat > "policy.utlx" <<'EOF'
%utlx 1.1
input json
output json
---
$input |> validate.assert([
  { rule: "order-fields-present", required: ["orderId", "qty", "ccy"] },
  { rule: "qty-in-range",         inRange: { field: "qty", min: 1, max: 100 } },
  { rule: "ccy-allowed",          oneOf: { field: "ccy", values: ["EUR", "USD"] } },
  { rule: "id-pattern",           matches: { field: "orderId", pattern: "^[A-Z]-[0-9]+$" } },
  { rule: "legacy-field",         absent: ["legacyRef"], severity: "warning" }
])
EOF
show "policy.utlx"
printf '{"orderId":"A-1","qty":5,"ccy":"EUR"}' > "clean.json"
say "A clean order. Note the shape that comes back: a verdict, counts, and the payload."
show "clean.json"
run "$UTLX transform policy.utlx clean.json"

banner "3. A bad order — and WHICH rule rejected it"
printf '{"orderId":"bad id","qty":500,"ccy":"GBP","legacyRef":"OLD-7"}' > "bad.json"
show "bad.json"
say "Three rules fail and one warns. Every issue carries 'rule' — the id YOU wrote."
run "$UTLX transform policy.utlx bad.json"
say "That is the whole point. 'failed: 3' is a number; 'ccy-allowed' is an answer."

banner "4. A warning does not make it invalid"
say "legacy-field is severity warning: counted separately, reported, but 'valid' ignores it. Three severities: error, warning, notice."
run "$UTLX transform policy.utlx bad.json | grep -E '\"valid\"|\"failed\"|\"warnings\"'"

banner "5. Rules that only apply sometimes"
say "A 'when' guard makes a rule conditional. A guard that is false counts as PASSED — the rule simply did not apply."
cat > "when.utlx" <<'EOF'
%utlx 1.1
input json
output json
---
$input |> validate.assert([
  { rule: "wholesale-min-qty", inRange: { field: "qty", min: 50, max: 10000 },
    when: (p) => p.channel == "WHOLESALE" }
])
EOF
show "when.utlx"
printf '{"channel":"RETAIL","qty":5}' > "retail.json"
printf '{"channel":"WHOLESALE","qty":5}' > "wholesale.json"
show "retail.json"
say "RETAIL: the rule does not apply, so it passes."
run "$UTLX transform when.utlx retail.json | grep -E '\"valid\"|\"passed\"'"
show "wholesale.json"
say "The SAME order marked WHOLESALE: now it applies, and 5 is below the minimum."
run "$UTLX transform when.utlx wholesale.json | grep -E '\"valid\"|\"failed\"'"

banner "6. The field allow-list — only what is expected may pass"
say "onlyFields says which fields are permitted; anything else is an error, named individually."
cat > "allow.utlx" <<'EOF'
%utlx 1.1
input json
output json
---
$input |> validate.assert([
  { rule: "no-unknown-fields", onlyFields: ["orderId", "qty", "ccy"] }
])
EOF
show "allow.utlx"
printf '{"orderId":"A-1","qty":5,"ccy":"EUR","isAdmin":true,"debug":"x"}' > "smuggled.json"
show "smuggled.json"
say "Two fields nobody asked for. Each gets its own issue, naming the field."
run "$UTLX transform allow.utlx smuggled.json"

banner "7. THE point: one rule set, any format"
say "The rules run on the Universal Data Model, not on the wire format. Same five rules — XML in."
# Selecting from a verdict is the LANGUAGE's job, not the shell's. This report carries the same
# five rules plus two lines that project the issues and ask for csv, so the comparison below needs
# no grep, no sed and no pipe — and it shows the verdict is data you can re-serialize.
cat > "report.utlx" <<'EOF'
%utlx 1.1
input json
output json
---
let v = $input |> validate.assert([
  { rule: "order-fields-present", required: ["orderId", "qty", "ccy"] },
  { rule: "qty-in-range",         inRange: { field: "qty", min: 1, max: 100 } },
  { rule: "ccy-allowed",          oneOf: { field: "ccy", values: ["EUR", "USD"] } },
  { rule: "id-pattern",           matches: { field: "orderId", pattern: "^[A-Z]-[0-9]+$" } },
  { rule: "legacy-field",         absent: ["legacyRef"], severity: "warning" }
])
v.issues |> map(i => { rule: i.rule, field: i.field, severity: i.severity })
EOF
sed 's/^input json$/input xml/; s/^let v = \$input |>/let v = $input.order |>/' "report.utlx" > "report-xml.utlx"
# Must carry the SAME fields as bad.json, legacyRef included — otherwise the two tables differ by
# one row and the "identical" claim is visibly false.
printf '<order><orderId>bad id</orderId><qty>500</qty><ccy>GBP</ccy><legacyRef>OLD-7</legacyRef></order>' > "bad.xml"
say "The same five rules, plus two lines that project the issues. Then --format table to SHOW it."
show "report.utlx"
say "Run it on the JSON order from step 3:"
run "$UTLX transform report.utlx bad.json --format table"
show "bad.xml"
say "Now the SAME order written as XML, through the same rules:"
run "$UTLX transform report-xml.utlx bad.xml --format table"
say "Identical table. And the messages prove the VALUES were read, not just the field names:"
cat > "messages-xml.utlx" <<'EOF'
%utlx 1.1
input xml
output json
---
let v = $input.order |> validate.assert([
  { rule: "qty-in-range", inRange: { field: "qty", min: 1, max: 100 } },
  { rule: "ccy-allowed",  oneOf: { field: "ccy", values: ["EUR", "USD"] } },
  { rule: "id-pattern",   matches: { field: "orderId", pattern: "^[A-Z]-[0-9]+$" } }
])
v.issues |> map(i => i.message)
EOF
run "$UTLX transform messages-xml.utlx bad.xml -r"

banner "8. Fail-closed: a broken rule is an ERROR, not a silent pass"
say "Forget the brackets and you have asked for nothing. A validator that quietly passed would wave the data through."
cat > "broken.utlx" <<'EOF'
%utlx 1.1
input json
output json
---
$input |> validate.required("orderId")
EOF
show "broken.utlx"
say "Note it says MALFORMED RULE and is invalid — it does not report success."
run "$UTLX transform broken.utlx clean.json | grep -E '\"valid\"|message'"

printf "\n${c_say}Rules as data. Every failure attributable. Any format.${c_off}\n"
printf "${c_dim}scratch files: %s${c_off}\n\n" "$W"
