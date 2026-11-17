#!/usr/bin/env bash
#
# utlxe-admin-tests.sh — functional tests against a RUNNING UTLXe instance
#
#   ./utlxe-admin-tests.sh --url https://utlxe-xxx.westeurope.azurecontainerapps.io --key <admin-key>
#   ./utlxe-admin-tests.sh --url http://localhost:8088 --key devkey
#   ./utlxe-admin-tests.sh --url ... --key ... --keep    # leave the test transformation behind
#
# WHY THIS IS SEPARATE FROM utlxe-acceptance.sh
#
#   The acceptance script owns infrastructure: it deploys the Marketplace template, checks the
#   deployment came up, and destroys it. One cycle is ~30 minutes, most of it Azure provisioning
#   a Container Apps environment.
#
#   This script owns BEHAVIOUR. It takes a URL and an admin key and exercises the Admin API in a
#   few seconds, against anything that is running — a --keep deployment, a local container, a
#   future staging instance. That difference matters: a functional bug used to cost a full
#   deployment cycle to retest, and now costs seconds.
#
#   It is also the half that has never run. Every acceptance attempt so far died at the health
#   gate, so upload, compile, transform and restart have never been exercised in Azure at all.
#
# WHAT IT DOES NOT COVER
#
#   The engine exposes two HTTP surfaces, and this script exercises only one:
#
#     port 8081  admin + health + metrics   <- tested here, reached through the UI proxy
#     port 8085  the data plane             <- NOT tested, and not reachable
#
#   The data plane is where real messages arrive for transformation. Container Apps ingress
#   publishes a single port, and that port is the UI's (8088), whose nginx proxies only
#   /admin/, /health and /metrics. Nothing proxies 8085, so no public request can reach the
#   transformation endpoint at all — in this deployment shape, messages arrive via Azure
#   Service Bus or not at all.
#
#   The transform assertions below therefore go through the ADMIN test endpoint
#   (/admin/transformations/<name>/test), which proves the engine compiles and executes
#   correctly but does not exercise the production message path.
#
# WHAT IT ASSUMES
#
#   The engine is in OPEN mode — no .utlar bundle on the data volume. In locked mode every
#   mutating endpoint answers 403 BUNDLE_LOCKED by design; the script detects that and skips
#   the mutation tests rather than reporting failures for correct behaviour.
#
#   Paths are the public ones: the UI container proxies /admin/, /health and /metrics through
#   to the engine's admin port, so the same URL works from a browser.
#
#
# TODO — replace jq with our own CLI once the released build catches up.
#
#   These scripts parse JSON with jq while shipping a JSON transformation engine, which is a
#   quiet vote of no confidence in our own tool. `utlx transform -e` already exists for
#   exactly this and is documented as "One-off query, like jq", with -r for raw scalars and
#   --arg / --argjson / --exit-status for parity:
#
#       utlx transform -e '$input.engine.state' -r -i response.json
#
#   Speed is not the obstacle. Measured 2026-11-17 on the same query:
#       jq                                 0.01s
#       utlx native (GraalVM binary)       0.01s     <- parity
#       ./utlx JVM wrapper                 0.79s     <- 79x, unusable across ~35 calls
#
#   The obstacle is the released CLI. `brew` installs v1.3.0 from main while this repo is on
#   1.4.1, so a resolver preferring PATH would silently run a CLI two releases behind — the
#   same stale-artifact trap that made a correct fix look broken earlier in this work. Full
#   jq parity should be confirmed on the released build before these scripts depend on it.
#
#   When it lands, resolve in this order and print which was chosen:
#       1. modules/cli/build/native/nativeCompile/utlx   this repo, current
#       2. utlx on PATH                                   warn if its version != build.gradle.kts
#       3. ./utlx                                         warn: JVM, ~79x slower
#       4. jq                                             last resort
#
# Requires: curl, jq.

set -uo pipefail

URL="" ; KEY="" ; KEEP=0
TX="apitest-$$"          # unique per run, so a parallel run or a real transformation is safe
TX_SUFFIXES=(a b c d e)  # one uploaded transformation per scenario in section 4

C_OK=$'\033[0;32m' ; C_BAD=$'\033[0;31m' ; C_WARN=$'\033[0;33m'
C_DIM=$'\033[0;90m' ; C_OFF=$'\033[0m'
[[ -t 1 ]] || { C_OK="" ; C_BAD="" ; C_WARN="" ; C_DIM="" ; C_OFF="" ; }

PASS=0 ; FAIL=0 ; SKIP=0
declare -a RESULTS=()

die()  { printf '%sfatal:%s %s\n' "$C_BAD" "$C_OFF" "$*" >&2; exit 2; }
ok()   { PASS=$((PASS+1)); RESULTS+=("PASS|$1|");        printf '  %s✓%s %s\n' "$C_OK" "$C_OFF" "$1"; }
bad()  { FAIL=$((FAIL+1)); RESULTS+=("FAIL|$1|$2");      printf '  %s✗%s %s %s(%s)%s\n' "$C_BAD" "$C_OFF" "$1" "$C_DIM" "$2" "$C_OFF"; }
skip() { SKIP=$((SKIP+1)); RESULTS+=("SKIP|$1|$2");      printf '  %s⊘%s %s %s(%s)%s\n' "$C_WARN" "$C_OFF" "$1" "$C_DIM" "$2" "$C_OFF"; }
note() {                   RESULTS+=("INFO|$1|$2");      printf '  %s·%s %s: %s\n' "$C_DIM" "$C_OFF" "$1" "$2"; }

check() {  # check <name> <actual> <expected>
  [[ "$2" == "$3" ]] && ok "$1" || bad "$1" "expected $3, got $2"
}

# Every request goes through here so the status code and body are always both available —
# a test that only knows "it failed" costs another round trip to explain itself.
BODY="" ; CODE=""
req() {  # req <method> <path> [--key] [data-file]
  local method="$1" path="$2" use_key="${3:-}" data="${4:-}"
  local args=(-s -X "$method" --max-time 30 -w '\n%{http_code}')
  [[ "$use_key" == "key" ]] && args+=(-H "X-Admin-Key: ${KEY}")
  [[ "$use_key" == "badkey" ]] && args+=(-H "X-Admin-Key: not-the-right-key")
  [[ -n "$data" ]] && args+=(-H "Content-Type: text/plain" --data-binary "@${data}")
  local out; out=$(curl "${args[@]}" "${URL}${path}" 2>/dev/null)
  CODE="${out##*$'\n'}"; BODY="${out%$'\n'*}"
  [[ -n "$CODE" ]] || CODE="000"
}

jqv() { jq -r "$1" <<<"$BODY" 2>/dev/null || echo ""; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url)  URL="${2%/}"; shift 2 ;;
    --key)  KEY="$2";     shift 2 ;;
    --keep) KEEP=1;       shift ;;
    -h|--help) sed -n '2,75p' "$0"; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done
[[ -n "$URL" ]] || die "pass --url <base url>"
[[ -n "$KEY" ]] || die "pass --key <admin key>"
for b in curl jq; do command -v "$b" >/dev/null || die "$b not on PATH"; done

cleanup() {
  if (( KEEP == 0 )) && [[ -n "${TX:-}" ]]; then
    local sfx
    for sfx in ${TX_SUFFIXES[@]+"${TX_SUFFIXES[@]}"}; do
      req DELETE "/admin/transformations/${TX}-${sfx}" key >/dev/null 2>&1 || true
    done
    req DELETE "/admin/transformations/${TX}-bad" key >/dev/null 2>&1 || true
  fi
  rm -f /tmp/${TX}-*.utlx /tmp/${TX}-*.json /tmp/${TX}-*.xml 2>/dev/null || true
}
trap cleanup EXIT INT TERM

printf '\n%s── UTLXe API tests ──%s\n' "$C_DIM" "$C_OFF"
printf '  target %s\n\n' "$URL"

# ── 1. the public surface, unauthenticated ──────────────────────────────────
printf '%s[1] public endpoints%s\n' "$C_DIM" "$C_OFF"
req GET /health;      check "/health is 200" "$CODE" "200"
note "engine state" "$(jqv '.engine.state // .status')"
req GET /health/live; check "/health/live is 200" "$CODE" "200"
req GET /metrics;     check "/metrics is 200" "$CODE" "200"
# EB18/EB19: this runs BEFORE anything is uploaded. A 503 here is the deadlock that made every
# Azure deployment unreachable, so it is worth asserting on an empty engine specifically.
req GET /health/ready; check "/health/ready is 200 on an empty engine" "$CODE" "200"
[[ "$BODY" == *"utlxe_up"* ]] && ok "/metrics exposes utlxe_up" || bad "/metrics exposes utlxe_up" "gauge missing"

# ── 2. admin authentication ─────────────────────────────────────────────────
# The Admin API is reachable over the public ingress; the key is the only thing in front of
# it. These three are the security contract.
printf '\n%s[2] admin authentication%s\n' "$C_DIM" "$C_OFF"
req GET /admin/info;        check "no key is rejected"    "$CODE" "403"
req GET /admin/info badkey; check "wrong key is rejected" "$CODE" "403"
req GET /admin/info key;    check "correct key accepted"  "$CODE" "200"
ENGINE_MODE=$(jqv '.mode // "unknown"')
note "engine version" "$(jqv '.version // "?"')"
note "bundle mode"    "$ENGINE_MODE"

LOCKED=0
[[ "$ENGINE_MODE" == "locked" ]] && LOCKED=1

# ── 3. starting state ───────────────────────────────────────────────────────
printf '\n%s[3] starting state%s\n' "$C_DIM" "$C_OFF"
req GET /admin/transformations key
check "transformations list is readable" "$CODE" "200"
BEFORE=$(jqv 'if type=="array" then length else (.transformations|length) end // 0')
note "transformations present" "${BEFORE:-0}"

# ── 4. upload, compile, execute ─────────────────────────────────────────────
# Four transformations, each able to fail for a different reason. Breadth of language
# coverage belongs in the conformance suite (582 fixtures, three strategies); what is
# unique here is running against the CI-built image, in Azure, through the proxy.
#
# TWO THINGS THE HANDLER DOES THAT ARE EASY TO GET WRONG, both read from AdminEndpoint.kt:
#
#   "output" is execResp.output.toStringUtf8() — a STRING holding the serialized result,
#   not nested JSON. `.output.greeting` is therefore always null; the string has to be
#   parsed before it can be queried.
#
#   A runtime failure answers HTTP 200 with {"status":"error"}. Asserting on the status
#   code alone passes on a failed transformation, so every check below tests .status too.

# upload <name> <file>  -> PASS/FAIL on compile
upload_tx() {
  local name="$1" file="$2"
  req POST "/admin/transformations/${name}" key "$file"
  [[ "$CODE" == "200" || "$CODE" == "201" ]]
}

# run_tx <name> <input-file> <content-type> -> sets TX_STATUS and TX_OUT (the raw output text)
TX_STATUS="" ; TX_OUT=""
run_tx() {
  local name="$1" input="$2" ctype="$3" out
  out=$(curl -s --max-time 30 -w '\n%{http_code}' -X POST \
          -H "X-Admin-Key: ${KEY}" -H "Content-Type: ${ctype}" \
          --data-binary "@${input}" \
          "${URL}/admin/transformations/${name}/test" 2>/dev/null)
  CODE="${out##*$'\n'}"; BODY="${out%$'\n'*}"
  TX_STATUS=$(jqv '.status // "no-response"')
  TX_OUT=$(jqv '.output // ""')
}

# field <json-text> <path> — the output is a string of JSON, so parse it separately
field() { jq -r "$2" <<<"$1" 2>/dev/null || echo ""; }

printf '\n%s[4] transformations%s\n' "$C_DIM" "$C_OFF"
if (( LOCKED )); then
  skip "upload / transform / delete" "engine is in locked mode — mutations return 403 by design"
else

  # ---- 4a. JSON -> JSON, stdlib functions -------------------------------------
  cat > /tmp/${TX}-a.utlx <<'UTLX'
%utlx 1.0
input json
output json
---
{
  greeting: concat("Hello, ", $input.name, "!"),
  shouted: upperCase($input.name)
}
UTLX
  printf '{"name":"acceptance"}' > /tmp/${TX}-a.json
  if upload_tx "${TX}-a" /tmp/${TX}-a.utlx; then
    ok "4a json->json uploads and compiles"
    run_tx "${TX}-a" /tmp/${TX}-a.json "application/json"
    check "4a executes without error" "$TX_STATUS" "ok"
    check "4a concat() is correct"    "$(field "$TX_OUT" '.greeting')" "Hello, acceptance!"
    check "4a upperCase() is correct" "$(field "$TX_OUT" '.shouted')"  "ACCEPTANCE"
    note  "4a duration" "$(jqv '.duration_ms // "?"') ms"
  else
    bad "4a json->json uploads and compiles" "HTTP $CODE: $(head -c 120 <<<"$BODY")"
  fi

  # ---- 4b. XML -> JSON --------------------------------------------------------
  # The product's premise is format-agnostic transformation. Everything else here is
  # JSON->JSON, which exercises none of the parsers that make that claim true.
  cat > /tmp/${TX}-b.utlx <<'UTLX'
%utlx 1.0
input xml
output json
---
{
  id: $input.Order.@id,
  customer: $input.Order.Customer.Name,
  lines: count($input.Order.Lines.Line)
}
UTLX
  printf '<Order id="A-1"><Customer><Name>Acme</Name></Customer><Lines><Line>x</Line><Line>y</Line></Lines></Order>' \
    > /tmp/${TX}-b.xml
  if upload_tx "${TX}-b" /tmp/${TX}-b.utlx; then
    ok "4b xml->json uploads and compiles"
    run_tx "${TX}-b" /tmp/${TX}-b.xml "application/xml"
    check "4b executes without error"   "$TX_STATUS" "ok"
    check "4b reads an XML attribute"   "$(field "$TX_OUT" '.id')"       "A-1"
    check "4b reads nested XML element" "$(field "$TX_OUT" '.customer')" "Acme"
    check "4b counts repeated elements" "$(field "$TX_OUT" '.lines')"    "2"
  else
    bad "4b xml->json uploads and compiles" "HTTP $CODE: $(head -c 120 <<<"$BODY")"
  fi

  # ---- 4c. arrays, projection, aggregation ------------------------------------
  cat > /tmp/${TX}-c.utlx <<'UTLX'
%utlx 1.0
input json
output json
---
{
  count: count($input.items),
  total: sum($input.items[*].price),
  names: $input.items[*].name
}
UTLX
  printf '{"items":[{"name":"a","price":10},{"name":"b","price":32}]}' > /tmp/${TX}-c.json
  if upload_tx "${TX}-c" /tmp/${TX}-c.utlx; then
    ok "4c arrays uploads and compiles"
    run_tx "${TX}-c" /tmp/${TX}-c.json "application/json"
    check "4c executes without error" "$TX_STATUS" "ok"
    check "4c count()"                "$(field "$TX_OUT" '.count')" "2"
    check "4c sum() over a projection" "$(field "$TX_OUT" '.total')" "42"
    check "4c projection yields a sequence" "$(field "$TX_OUT" '.names | join(",")')" "a,b"
  else
    bad "4c arrays uploads and compiles" "HTTP $CODE: $(head -c 120 <<<"$BODY")"
  fi

  # ---- 4d. decimal scale, JSON -> CSV ------------------------------------------
  # B35 exists because exact decimals are easy to lose. The assertion is on the RAW output
  # text: parsing it as JSON would turn 10.50 into 10.5 and the check would pass vacuously,
  # which is the same trap the conformance fixtures document.
  cat > /tmp/${TX}-d.utlx <<'UTLX'
%utlx 1.0
input json
output csv
---
[ { sku: $input.sku, price: $input.price } ]
UTLX
  printf '{"sku":"S1","price":10.50}' > /tmp/${TX}-d.json
  if upload_tx "${TX}-d" /tmp/${TX}-d.utlx; then
    ok "4d json->csv uploads and compiles"
    run_tx "${TX}-d" /tmp/${TX}-d.json "application/json"
    check "4d executes without error" "$TX_STATUS" "ok"
    [[ "$TX_OUT" == *"sku,price"* ]] \
      && ok "4d emits a CSV header" || bad "4d emits a CSV header" "got: $(head -c 80 <<<"$TX_OUT")"
    [[ "$TX_OUT" == *"10.50"* ]] \
      && ok "4d preserves decimal scale (10.50, not 10.5)" \
      || bad "4d preserves decimal scale" "got: $(head -c 80 <<<"$TX_OUT")"
  else
    bad "4d json->csv uploads and compiles" "HTTP $CODE: $(head -c 120 <<<"$BODY")"
  fi

  # ---- 4e. %utlx 1.1 validation -------------------------------------------------
  # The headline 1.1 feature, and the one CB07 and EF23 are about. A valid and an invalid
  # payload through the same script: the verdict must differ, and both must execute cleanly
  # — validation reports, it does not throw.
  cat > /tmp/${TX}-e.utlx <<'UTLX'
%utlx 1.1
input json
output json
---
$input |> validate.assert([
  { rule: "amount-range", inRange: { field: "amount", min: 1, max: 1000 } }
])
UTLX
  if upload_tx "${TX}-e" /tmp/${TX}-e.utlx; then
    ok "4e validate (%utlx 1.1) uploads and compiles"
    printf '{"amount":500}' > /tmp/${TX}-e-ok.json
    run_tx "${TX}-e" /tmp/${TX}-e-ok.json "application/json"
    check "4e in-range payload executes" "$TX_STATUS" "ok"
    check "4e in-range verdict is valid" "$(field "$TX_OUT" '.valid')" "true"

    printf '{"amount":99999}' > /tmp/${TX}-e-bad.json
    run_tx "${TX}-e" /tmp/${TX}-e-bad.json "application/json"
    check "4e out-of-range still executes"  "$TX_STATUS" "ok"
    check "4e out-of-range verdict invalid" "$(field "$TX_OUT" '.valid')"  "false"
    check "4e failure is counted"           "$(field "$TX_OUT" '.failed')" "1"
    check "4e offending rule is named"      "$(field "$TX_OUT" '.issues[0].rule')" "amount-range"
  else
    bad "4e validate uploads and compiles" "HTTP $CODE: $(head -c 120 <<<"$BODY")"
  fi

  # ── 5. the engine reflects the new state ──────────────────────────────────
  printf '\n%s[5] state after upload%s\n' "$C_DIM" "$C_OFF"
  req GET /health
  AFTER=$(jqv '.engine.transformations // 0')
  [[ "${AFTER:-0}" -gt "${BEFORE:-0}" ]] \
    && ok "health reports the new transformations" \
    || bad "health reports the new transformations" "before=$BEFORE after=$AFTER"

  # EB18/EB19: readiness is about the instance, not its workload. It answered 503 until a
  # transformation was loaded, which deadlocked every Azure deployment; it now reports 200 for
  # a RUNNING engine either way. Checked here with work loaded, and in section 1 without.
  req GET /health/ready
  check "/health/ready is 200 with transformations loaded" "$CODE" "200"

  # ── 6. operational control ────────────────────────────────────────────────
  printf '\n%s[6] operational control%s\n' "$C_DIM" "$C_OFF"
  req POST "/admin/transformations/${TX}-a/pause" key
  check "pause accepted" "$CODE" "200"
  req POST "/admin/transformations/${TX}-a/resume" key
  check "resume accepted" "$CODE" "200"
  req GET "/admin/transformations/${TX}-a/config" key
  check "per-transformation config readable" "$CODE" "200"
  req GET "/admin/transformations/${TX}-a/errors" key
  check "error history readable" "$CODE" "200"
fi

# ── 7. error handling ───────────────────────────────────────────────────────
# A service that returns 200 for nonsense is worse than one that fails.
printf '\n%s[7] error handling%s\n' "$C_DIM" "$C_OFF"
req GET "/admin/transformations/definitely-not-here-$$" key
[[ "$CODE" == "404" || "$CODE" == "400" ]] \
  && ok "unknown transformation is rejected" \
  || bad "unknown transformation is rejected" "expected 404/400, got $CODE"

if (( LOCKED == 0 )); then
  printf '%%utlx 1.0\ninput json\noutput json\n---\n{ broken: }\n' > /tmp/${TX}-bad.utlx
  req POST "/admin/transformations/${TX}-bad" key /tmp/${TX}-bad.utlx
  if [[ "$CODE" == "400" || "$CODE" == "422" ]]; then
    ok "invalid UTL-X is rejected at upload"
  else
    bad "invalid UTL-X is rejected at upload" "expected 400/422, got $CODE"
    req DELETE "/admin/transformations/${TX}-bad" key >/dev/null 2>&1 || true
  fi
fi

# ── 8. supporting endpoints ─────────────────────────────────────────────────
printf '\n%s[8] supporting endpoints%s\n' "$C_DIM" "$C_OFF"
req GET /admin/schemas key;      check "schemas list readable"  "$CODE" "200"
req GET /admin/config key;       check "engine config readable" "$CODE" "200"
req GET /admin/log/level key;    check "log level readable"     "$CODE" "200"
req GET /admin/backpressure key; check "backpressure readable"  "$CODE" "200"
req GET /admin/bundle key;       check "bundle status readable" "$CODE" "200"
req GET /admin/dapr key
[[ "$CODE" == "200" ]] && { ok "dapr status readable"; note "dapr" "$(jqv '.status // .enabled // "?"')"; } \
                       || skip "dapr status readable" "HTTP $CODE — expected when Dapr is disabled"

# ── 9. cleanup ──────────────────────────────────────────────────────────────
if (( LOCKED == 0 )); then
  printf '\n%s[9] cleanup%s\n' "$C_DIM" "$C_OFF"
  if (( KEEP )); then
    skip "transformations delete cleanly" "--keep: ${TX}-* left in place"
  else
    deleted=0
    for sfx in ${TX_SUFFIXES[@]+"${TX_SUFFIXES[@]}"}; do
      req DELETE "/admin/transformations/${TX}-${sfx}" key
      [[ "$CODE" == "200" || "$CODE" == "204" ]] && deleted=$((deleted+1))
    done
    check "all transformations delete cleanly" "$deleted" "${#TX_SUFFIXES[@]}"

    # Deleting is not the same as being gone; assert the registry agrees.
    req GET "/admin/transformations/${TX}-a" key
    [[ "$CODE" == "404" ]] && ok "deleted transformation is gone" \
                           || bad "deleted transformation is gone" "expected 404, got $CODE"

    # And the count should be back where it started — a delete that half-worked shows here.
    req GET /health
    FINAL=$(jqv '.engine.transformations // 0')
    check "transformation count returns to its starting value" "${FINAL:-0}" "${BEFORE:-0}"

    TX_SUFFIXES=()   # already removed; stop the trap retrying
  fi
fi

# ── summary ─────────────────────────────────────────────────────────────────
printf '\n%s── summary ──%s\n' "$C_DIM" "$C_OFF"
for r in ${RESULTS[@]+"${RESULTS[@]}"}; do
  IFS='|' read -r kind name detail <<<"$r"
  case "$kind" in
    PASS) printf '  %s✓%s %s\n'      "$C_OK"   "$C_OFF" "$name" ;;
    FAIL) printf '  %s✗%s %s — %s\n' "$C_BAD"  "$C_OFF" "$name" "$detail" ;;
    SKIP) printf '  %s⊘%s %s — %s\n' "$C_WARN" "$C_OFF" "$name" "$detail" ;;
    INFO) printf '  %s·%s %s: %s\n'  "$C_DIM"  "$C_OFF" "$name" "$detail" ;;
  esac
done
printf '\n%d passed, %d failed, %d skipped\n' "$PASS" "$FAIL" "$SKIP"
(( FAIL == 0 ))
