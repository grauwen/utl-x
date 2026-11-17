#!/usr/bin/env bash
#
# utlxe-acceptance.sh — deploy / assert / destroy harness for the UTLXe Azure offering
#
# Answers one question: if a customer deployed this offer right now, would it work?
#
# THE THREE PHASES
#
#   DEPLOY   Creates a throwaway resource group (tagged ttl=ephemeral) and deploys
#            into it. With --template that is the Marketplace package's own
#            mainTemplate.json — what a customer actually receives. With --image it
#            is a bare container app, which exercises the engine but NOT the offer,
#            so a green run there says nothing about the Marketplace artifacts.
#            Then it waits for the first healthy response.
#
#   ASSERT   Runs a fixed suite against the live container: that ingress is
#            published, that a fresh engine reports itself empty and not ready,
#            that the admin API is not served unauthenticated over public ingress,
#            that a transformation uploads, compiles, executes and returns the
#            right answer, and what survives a revision restart.
#
#            When startup fails, the container log is classified rather than
#            dumped. From the outside an arm64 image on an amd64 host, an OOM kill
#            during startup and a failed registry pull all look the same — "no
#            healthy response" — and that ambiguity once hid a broken offering for
#            six months.
#
#   DESTROY  Deletes the resource group. An EXIT/INT/TERM trap guarantees this even
#            when an assertion fails or the run is interrupted, because what is
#            being tested bills by the hour. --keep opts out and leaves it running.
#
# RESULT KINDS
#
#   PASS/FAIL  an assertion about the contract
#   SKIP       an assertion that CANNOT run because the feature is absent — the
#              template has no persistence parameter, so restart-survival cannot be
#              proven either way. Reported on every run so a known gap stays
#              visible instead of silently looking like success.
#   WARN       something suspicious that is not itself a contract breach
#
# EXIT CODES
#
#   0  every assertion passed      1  at least one FAIL
#   2  fatal: bad arguments, missing tool, or not logged in to az
#
# USAGE
#
#   ./utlxe-acceptance.sh --template ./starter/mainTemplate.json
#   ./utlxe-acceptance.sh --image ghcr.io/utlx-lang/utlxe:1.4.1
#   ./utlxe-acceptance.sh --template ./starter/mainTemplate.json --keep
#
# Requires: az (logged in), jq, curl.

set -euo pipefail

# ── configuration ────────────────────────────────────────────────────────────
LOCATION="${LOCATION:-westeurope}"
RG_PREFIX="${RG_PREFIX:-rg-utlxe-acceptance}"
DEPLOYMENT_NAME="${DEPLOYMENT_NAME:-utlxe-acceptance}"
SUBSCRIPTION="${SUBSCRIPTION:-}"          # empty = current az context
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-300}"   # seconds to wait for first healthy response

# ARM template parameters actually accepted by starter/mainTemplate.json.
# There is no planType parameter — Starter vs Professional is which package you
# upload, not a value you pass. There is no persistence parameter either; see
# PERSISTENCE GAP in run_suite().
P_APP_NAME="${P_APP_NAME:-namePrefix}"
P_ADMIN_KEY="${P_ADMIN_KEY:-adminKey}"

# Only used by the --image fallback. Ingress targets 8088 (the utlxe-ui nginx
# container), whose nginx.conf proxies /health, /metrics and /admin/ through to
# the engine on 8081.
TARGET_PORT="${TARGET_PORT:-8088}"
ADMIN_KEY_ENV="${ADMIN_KEY_ENV:-UTLXE_ADMIN_KEY}"

# ── internals ────────────────────────────────────────────────────────────────
TEMPLATE="" ; IMAGE="" ; KEEP=0
CURRENT_RG="" ; PASS=0 ; FAIL=0 ; SKIPPED=0 ; WARNED=0 ; declare -a RESULTS=()

C_OK=$'\033[0;32m' ; C_BAD=$'\033[0;31m' ; C_WARN=$'\033[0;33m'
C_DIM=$'\033[0;90m' ; C_OFF=$'\033[0m'
[[ -t 1 ]] || { C_OK="" ; C_BAD="" ; C_WARN="" ; C_DIM="" ; C_OFF="" ; }

log() { printf '%s[%s]%s %s\n' "$C_DIM" "$(date +%H:%M:%S)" "$C_OFF" "$*"; }
die() { printf '%sfatal:%s %s\n' "$C_BAD" "$C_OFF" "$*" >&2; exit 2; }

assert_eq() {
  local name="$1" actual="$2" expected="$3"
  if [[ "$actual" == "$expected" ]]; then
    PASS=$((PASS+1)); RESULTS+=("PASS|$name|")
    printf '  %s✓%s %s\n' "$C_OK" "$C_OFF" "$name"
  else
    FAIL=$((FAIL+1)); RESULTS+=("FAIL|$name|expected '$expected', got '$actual'")
    printf '  %s✗%s %s %s(expected %s, got %s)%s\n' \
      "$C_BAD" "$C_OFF" "$name" "$C_DIM" "$expected" "$actual" "$C_OFF"
  fi
}

# An assertion that cannot run yet. Deliberately NOT deleted: a removed test is
# one nobody remembers to restore. A skip shows up in every run as a standing
# reminder, and becomes a real assertion the day the gap closes.
skip() {
  SKIPPED=$((SKIPPED+1)); RESULTS+=("SKIP|$1|$2")
  printf '  %s⊘%s %s %s(%s)%s\n' "$C_WARN" "$C_OFF" "$1" "$C_DIM" "$2" "$C_OFF"
}

# Observed behaviour that is not a test failure but should not pass unremarked.
warn() {
  WARNED=$((WARNED+1)); RESULTS+=("WARN|$1|$2")
  printf '  %s!%s %s: %s\n' "$C_WARN" "$C_OFF" "$1" "$2"
}

note() { RESULTS+=("INFO|$1|$2"); printf '  %s·%s %s: %s\n' "$C_DIM" "$C_OFF" "$1" "$2"; }

cleanup() {
  local rc=$?
  if [[ -n "$CURRENT_RG" && $KEEP -eq 0 ]]; then
    log "tearing down $CURRENT_RG"
    az group delete --name "$CURRENT_RG" --yes --no-wait 2>/dev/null \
      && log "delete issued (async)" \
      || printf '%swarning:%s could not delete %s — remove it by hand\n' \
           "$C_BAD" "$C_OFF" "$CURRENT_RG" >&2
  elif [[ -n "$CURRENT_RG" ]]; then
    log "--keep set; $CURRENT_RG left running"
  fi
  CURRENT_RG=""
  return $rc
}
trap cleanup EXIT INT TERM

# ── argument parsing ─────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --template) TEMPLATE="$2"; shift 2 ;;
    --image)    IMAGE="$2";    shift 2 ;;
    --location) LOCATION="$2"; shift 2 ;;
    --keep)     KEEP=1; shift ;;
    -h|--help)  sed -n '2,52p' "$0"; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ -n "$TEMPLATE" || -n "$IMAGE" ]] || die "pass --template <mainTemplate.json> or --image <ref>"
[[ -z "$TEMPLATE" || -f "$TEMPLATE" ]] || die "template not found: $TEMPLATE"

# ── preflight ────────────────────────────────────────────────────────────────
preflight() {
  log "preflight"
  for b in az jq curl; do command -v "$b" >/dev/null || die "$b not on PATH"; done
  az account show >/dev/null 2>&1 || die "not logged in — run 'az login'"
  [[ -n "$SUBSCRIPTION" ]] && az account set --subscription "$SUBSCRIPTION"
  log "subscription: $(az account show --query name -o tsv)"

  # A customer's fresh subscription may not have these registered. Yours does,
  # which is exactly why the check belongs here rather than in your head.
  for ns in Microsoft.App Microsoft.OperationalInsights; do
    local state
    state=$(az provider show --namespace "$ns" --query registrationState -o tsv 2>/dev/null || echo "Unknown")
    [[ "$state" == "Registered" ]] || \
      printf '%swarning:%s %s is %s here — a customer hitting this would fail\n' \
        "$C_BAD" "$C_OFF" "$ns" "$state" >&2
  done

  # Confirm the template declares the parameters we are about to pass, rather
  # than discovering it 90 seconds into a failed deployment.
  if [[ -n "$TEMPLATE" ]]; then
    local declared missing=""
    declared=$(jq -r '.parameters | keys[]' "$TEMPLATE" 2>/dev/null || echo "")
    for p in "$P_APP_NAME" "$P_ADMIN_KEY"; do
      grep -qx "$p" <<<"$declared" || missing="${missing} ${p}"
    done
    [[ -z "$missing" ]] || die "template does not declare:${missing}
   declared parameters are: $(tr '\n' ' ' <<<"$declared")"
    log "template parameters verified"
  fi
}

# ── deploy ───────────────────────────────────────────────────────────────────
deploy() {
  local app="$1" key="$2"
  if [[ -n "$TEMPLATE" ]]; then
    log "deploying marketplace template"
    az deployment group create \
      --resource-group "$CURRENT_RG" \
      --name "$DEPLOYMENT_NAME" \
      --template-file "$TEMPLATE" \
      --parameters "$P_APP_NAME=$app" "$P_ADMIN_KEY=$key" \
      --only-show-errors -o none
  else
    log "deploying container image directly"
    printf '%snote:%s --image bypasses the marketplace artifacts. It tests the\n' \
      "$C_DIM" "$C_OFF" >&2
    printf '      engine, not the offer.\n' >&2
    local env="${app}-env"
    az containerapp env create --name "$env" --resource-group "$CURRENT_RG" \
      --location "$LOCATION" --only-show-errors -o none
    az containerapp create \
      --name "$app" --resource-group "$CURRENT_RG" --environment "$env" \
      --image "$IMAGE" --target-port "$TARGET_PORT" --ingress external \
      --cpu 1 --memory 4Gi \
      --env-vars "${ADMIN_KEY_ENV}=secretref:adminkey" \
      --secrets "adminkey=$key" \
      --only-show-errors -o none
  fi
}

wait_for_health() {
  local fqdn="$1" deadline=$((SECONDS + HEALTH_TIMEOUT))
  while (( SECONDS < deadline )); do
    curl -fsS --max-time 10 "https://${fqdn}/health" >/dev/null 2>&1 && return 0
    sleep 5
  done
  return 1
}

# A health timeout is a slow, ambiguous signal. Name the actual cause so that a
# wrong-architecture image reads as "wrong architecture" and not as 300 seconds
# of silence.
diagnose_startup_failure() {
  local app="$1" logs
  log "container never became healthy — diagnosing"
  logs=$(az containerapp logs show --name "$app" --resource-group "$CURRENT_RG" \
          --tail 60 2>/dev/null || echo "")

  if grep -qi "exec format error" <<<"$logs"; then
    warn "startup diagnosis" "exec format error — image architecture mismatch (arm64 image on an amd64 host)"
  elif grep -qiE "oomkill|out of memory|killed process" <<<"$logs"; then
    warn "startup diagnosis" "OOM-killed during startup — container memory below engine requirement"
  elif grep -qiE "manifest unknown|unauthorized|pull access denied|not found" <<<"$logs"; then
    warn "startup diagnosis" "image pull failed — check registry path, tag and visibility"
  elif [[ -z "${logs// }" ]]; then
    warn "startup diagnosis" "no logs produced at all — image pull almost certainly failed"
  else
    warn "startup diagnosis" "engine started but never reported healthy; log tail follows"
  fi
  [[ -z "${logs// }" ]] || { printf '%s--- log tail ---%s\n' "$C_DIM" "$C_OFF"; tail -40 <<<"$logs"; }
}

# ── the suite ────────────────────────────────────────────────────────────────
run_suite() {
  local stamp prefix app key
  stamp=$(date +%Y%m%d-%H%M%S)
  prefix="utlxe-${stamp}"          # what we pass as namePrefix
  app=""                           # discovered after deployment — see below
  key=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen)
  CURRENT_RG="${RG_PREFIX}-${stamp}"

  printf '\n%s── acceptance suite ──%s\n' "$C_DIM" "$C_OFF"
  log "resource group: $CURRENT_RG"

  az group create --name "$CURRENT_RG" --location "$LOCATION" \
    --tags purpose=utlxe-acceptance ttl=ephemeral --only-show-errors -o none

  local t0=$SECONDS
  deploy "$prefix" "$key"
  note "deploy duration" "$((SECONDS - t0))s"

  if [[ -n "$TEMPLATE" ]]; then
    local dstate
    dstate=$(az deployment group show --resource-group "$CURRENT_RG" \
               --name "$DEPLOYMENT_NAME" --query "properties.provisioningState" \
               -o tsv 2>/dev/null || echo "unknown")
    assert_eq "ARM deployment succeeded" "$dstate" "Succeeded"
  fi

  # The template does NOT name the app after namePrefix: it builds
  # "[format('{0}-app', parameters('namePrefix'))]". The first run of this script
  # assumed otherwise, queried the wrong name, found no FQDN and reported a failed
  # deployment that had in fact succeeded — a false negative on the single assertion
  # the whole run existed to make. So discover the name rather than reconstructing
  # the template's convention here; only fall back to the convention if discovery
  # turns up nothing.
  app=$(az containerapp list --resource-group "$CURRENT_RG" \
          --query "[0].name" -o tsv 2>/dev/null || true)
  if [[ -z "$app" ]]; then
    app="${prefix}-app"
    note "container app name" "not discoverable; assuming ${app}"
  else
    note "container app" "$app"
  fi

  local fqdn
  fqdn=$(az containerapp show --name "$app" --resource-group "$CURRENT_RG" \
          --query "properties.configuration.ingress.fqdn" -o tsv 2>/dev/null || true)
  if [[ -z "$fqdn" ]]; then
    assert_eq "ingress FQDN published" "none" "an fqdn"; return
  fi
  assert_eq "ingress FQDN published" "yes" "yes"
  log "fqdn: $fqdn"

  if ! wait_for_health "$fqdn"; then
    assert_eq "/health reachable within ${HEALTH_TIMEOUT}s" "timeout" "ok"
    diagnose_startup_failure "$app"
    return
  fi
  assert_eq "/health reachable within ${HEALTH_TIMEOUT}s" "ok" "ok"

  # ---- empty-engine state --------------------------------------------------
  local health; health=$(curl -fsS "https://${fqdn}/health")
  assert_eq "fresh container reports 0 transformations" \
    "$(jq -r '.transformations' <<<"$health")" "0"
  assert_eq "fresh container reports ready=false" \
    "$(jq -r '.ready' <<<"$health")" "false"

  # ---- admin API auth ------------------------------------------------------
  # nginx on 8088 proxies /admin/ to the engine on 8081, so the admin API *is*
  # publicly reachable. The key is therefore the only control on it.
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 \
           "https://${fqdn}/admin/info" || echo "000")
  case "$code" in
    403|401) assert_eq "admin API rejects unauthenticated request" "$code" "$code"
             note "admin API exposure" "public ingress via nginx, key-protected ($code)" ;;
    200)     assert_eq "admin API rejects unauthenticated request" "200 OPEN" "403"
             warn "admin API exposure" "SERVING UNAUTHENTICATED ON PUBLIC INGRESS" ;;
    000|404) note "admin API exposure" "not reachable on public ingress ($code)" ;;
    *)       assert_eq "admin API rejects unauthenticated request" "$code" "403" ;;
  esac

  # ---- upload --------------------------------------------------------------
  local utlx; utlx=$(mktemp); cat >"$utlx" <<'UTLX'
%utlx 1.0
input json
output json
---
{
  greeting: concat("Hello, ", $input.name, "!"),
  timestamp: now()
}
UTLX

  local up
  up=$(curl -fsS -X POST \
        -H "X-Admin-Key: ${key}" -H "Content-Type: text/plain" \
        --data-binary "@${utlx}" \
        "https://${fqdn}/admin/transformations/hello" 2>/dev/null || echo '{}')
  rm -f "$utlx"
  assert_eq "transformation uploads and compiles" \
    "$(jq -r '.status // "no-response"' <<<"$up")" "deployed"

  # ---- transform -----------------------------------------------------------
  local res
  res=$(curl -fsS -X POST \
        -H "X-Admin-Key: ${key}" -H "Content-Type: application/json" \
        -d '{"name": "acceptance"}' \
        "https://${fqdn}/admin/transformations/hello/test" 2>/dev/null || echo '{}')
  assert_eq "test endpoint reports ok" \
    "$(jq -r '.status // "no-response"' <<<"$res")" "ok"
  assert_eq "transformation output is correct" \
    "$(jq -r '.output.greeting // "none"' <<<"$res")" "Hello, acceptance!"
  note "transform latency" "$(jq -r '.duration_ms // "?"' <<<"$res") ms"

  # ---- loaded state --------------------------------------------------------
  health=$(curl -fsS "https://${fqdn}/health")
  assert_eq "container becomes ready after upload" \
    "$(jq -r '.ready' <<<"$health")" "true"
  assert_eq "health reports 1 transformation" \
    "$(jq -r '.transformations' <<<"$health")" "1"

  local info persistence
  info=$(curl -fsS -H "X-Admin-Key: ${key}" "https://${fqdn}/admin/info" 2>/dev/null || echo '{}')
  note "engine version" "$(jq -r '.version // "?"' <<<"$info")"
  persistence=$(jq -r '.persistence // "?"' <<<"$info")
  note "persistence mode reported by engine" "$persistence"

  # ---- PERSISTENCE GAP -----------------------------------------------------
  # starter/mainTemplate.json contains no Azure Files mount and no toggle, so
  # there is nothing for transformations to survive on. The book documents the
  # toggle (ch02), the mount (ch06, ch10.2), crash safety (ch10.3), a
  # troubleshooting entry (ch14.7) and a volume-backed value for
  # /admin/info.persistence (ch04.12). Until the template catches up, this is
  # the assertion that cannot run.
  skip "transformations survive restart (persistent)" \
       "template exposes no Azure Files mount — see ch10.2"

  # What we can still prove: the container comes back, and what it loses.
  local rev
  rev=$(az containerapp revision list --name "$app" --resource-group "$CURRENT_RG" \
         --query "[?properties.active].name | [0]" -o tsv 2>/dev/null || true)
  if [[ -n "$rev" ]]; then
    log "restarting revision $rev"
    az containerapp revision restart --name "$app" --resource-group "$CURRENT_RG" \
      --revision "$rev" --only-show-errors -o none 2>/dev/null || true
    sleep 15
    if wait_for_health "$fqdn"; then
      assert_eq "container recovers after restart" "ok" "ok"
      local after
      after=$(jq -r '.transformations' <<<"$(curl -fsS "https://${fqdn}/health")")
      if [[ "$after" == "0" ]]; then
        warn "state after restart" \
             "all transformations lost on restart — customers on the consumption plan hit this whenever the app scales to zero"
      else
        note "state after restart" "$after transformation(s) retained"
      fi
    else
      assert_eq "container recovers after restart" "timeout" "ok"
    fi
  fi

  # ---- teardown ------------------------------------------------------------
  if [[ $KEEP -eq 0 ]]; then
    local t1=$SECONDS
    az group delete --name "$CURRENT_RG" --yes --only-show-errors -o none
    assert_eq "resource group deletes cleanly" "ok" "ok"
    note "teardown duration" "$((SECONDS - t1))s"
    CURRENT_RG=""
  fi
}

# ── main ─────────────────────────────────────────────────────────────────────
preflight
run_suite

printf '\n%s── summary ──%s\n' "$C_DIM" "$C_OFF"
for r in "${RESULTS[@]}"; do
  IFS='|' read -r kind name detail <<<"$r"
  case "$kind" in
    PASS) printf '  %s✓%s %s\n'       "$C_OK"   "$C_OFF" "$name" ;;
    FAIL) printf '  %s✗%s %s — %s\n'  "$C_BAD"  "$C_OFF" "$name" "$detail" ;;
    SKIP) printf '  %s⊘%s %s — %s\n'  "$C_WARN" "$C_OFF" "$name" "$detail" ;;
    WARN) printf '  %s!%s %s: %s\n'   "$C_WARN" "$C_OFF" "$name" "$detail" ;;
    INFO) printf '  %s·%s %s: %s\n'   "$C_DIM"  "$C_OFF" "$name" "$detail" ;;
  esac
done
printf '\n%d passed, %d failed, %d skipped, %d warnings\n' \
  "$PASS" "$FAIL" "$SKIPPED" "$WARNED"
[[ $FAIL -eq 0 ]]
