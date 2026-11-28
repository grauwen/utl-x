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
# Second argument is the file's ROLE, and it is not optional in practice: a bare filename leaves
# the viewer asking whether what follows is the rules, the input or the output. `bad.xml` appearing
# between two runs was the case that made it obvious. Same helper and same labels as cli-demo.sh.
show()  { local r="${2:-}"; printf "${c_dim}# %s%s${c_off}\n" "${r:+$r  ·  }" "$(basename "$1")"
          awk '{print "    " $0}' "$1"; sleep "$SAY_PAUSE"; }

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
# The header line is the point, not decoration: `%utlx 1.1` is what makes validate.* available at
# all. Step 1 says the namespace arrived in 1.1, but a viewer seeing the file for the first time
# will not connect the first line of the script to that claim unless it is said here.
say "Look at the first line: %utlx 1.1. That version is what unlocks the validate.* namespace — rules are a LANGUAGE feature, not a library bolted on. And they are DATA: a list of objects, each with an id. Not scattered if/else. Here are five."
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
show "policy.utlx"     "THE RULES"
printf '{"orderId":"A-1","qty":5,"ccy":"EUR"}' > "clean.json"
say "A clean order. Note the shape that comes back: a verdict, counts, and the payload."
show "clean.json"      "THE INPUT DATA"
run "$UTLX transform policy.utlx clean.json"

banner "3. A bad order — and WHICH rule rejected it"
printf '{"orderId":"bad id","qty":500,"ccy":"GBP","legacyRef":"OLD-7"}' > "bad.json"
# A data file must never appear unannounced. This section used to open straight onto bad.json, so
# the last thing the viewer had read was about clean.json and a NEW file arrived unexplained.
# House order throughout: narration, then the thing it describes, then the command.
say "Same policy.utlx, unchanged. What changes is the order — here is a new one, bad.json, with real problems:"
show "bad.json"        "THE INPUT DATA"
say "Three rules fail and one warns. Every issue carries 'rule' — the id YOU wrote."
run "$UTLX transform policy.utlx bad.json"
say "That is the whole point. 'failed: 3' is a number; 'ccy-allowed' is an answer."

banner "4. A warning does not make it invalid"
say "legacy-field is severity warning: counted separately, reported, but 'valid' ignores it. Three severities: error, warning, notice."
# The verdict is DATA, so UTL-X can narrow it — no grep. `grep '"valid"'` would also match the
# nested "valid" inside issues/payload, and it cannot tell a missing field from a false one: it
# just prints nothing, which looks identical. Projecting by name asks the structure instead.
run "$UTLX transform policy.utlx bad.json | $UTLX -e '{valid: \$input.valid, failed: \$input.failed, warnings: \$input.warnings}'"

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
show "when.utlx"       "THE RULES"
printf '{"channel":"RETAIL","qty":5}' > "retail.json"
printf '{"channel":"WHOLESALE","qty":5}' > "wholesale.json"
say "First a RETAIL order. The guard is false, so the rule never applies — and that counts as PASSED:"
show "retail.json"     "THE INPUT DATA"
run "$UTLX transform when.utlx retail.json | $UTLX -e '{valid: \$input.valid, passed: \$input.passed}'"
say "The SAME order marked WHOLESALE. Now the guard is true, the rule applies, and 5 is below the minimum:"
show "wholesale.json"  "THE INPUT DATA"
run "$UTLX transform when.utlx wholesale.json | $UTLX -e '{valid: \$input.valid, failed: \$input.failed}'"

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
show "allow.utlx"      "THE RULES"
printf '{"orderId":"A-1","qty":5,"ccy":"EUR","isAdmin":true,"debug":"x"}' > "smuggled.json"
say "Now an order carrying two fields nobody asked for. Each will get its own issue, naming the field:"
show "smuggled.json"   "THE INPUT DATA"
run "$UTLX transform allow.utlx smuggled.json"

banner "7. THE point: one rule set, any format"
# Narration must land ON the thing it describes. This line used to promise "XML in" and was then
# followed by another say and two JSON steps before any XML appeared — so the viewer was told to
# look for something that was not there yet, twice over.
say "The rules run on the Universal Data Model, not on the wire format — so one rule list covers every format. To compare two formats we first need the issues in a shape you can read at a glance."
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
show "report.utlx"     "THE RULES"
say "The same five rules as step 2, plus two lines projecting each issue. Run it on the JSON order from step 3, with --format table to show it:"
run "$UTLX transform report.utlx bad.json --format table"
show "bad.xml"         "THE SAME INPUT DATA, AS XML"
say "NOW the XML — the same order on the wire differently. Only the header and the XML root differ; the five rules are untouched:"
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
show "broken.utlx"     "THE RULES  (deliberately malformed)"
say "Note it says MALFORMED RULE and is invalid — it does not report success."
run "$UTLX transform broken.utlx clean.json | $UTLX -e '{valid: \$input.valid, messages: \$input.issues |> map(i => i.message)}'"

printf "\n${c_say}Rules as data. Every failure attributable. Any format.${c_off}\n"
printf "${c_dim}scratch files: %s${c_off}\n\n" "$W"
