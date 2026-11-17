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
#   ./utlxe-acceptance.sh --image ghcr.io/utlx-lang/utlxe:1.4.2
#   ./utlxe-acceptance.sh --template ./starter/mainTemplate.json --keep
#
#   --param passes extra ARM parameters, repeatable. This matters more than it looks: the
#   template's defaults are not what a customer receives. createUiDefinition supplies every
#   value, and its defaults differ — persistent storage is false in the template and ENABLED
#   in the wizard. Testing without --param tests a configuration nobody is ever given.
#
#   ./utlxe-acceptance.sh --template ./starter/mainTemplate.json \
#       --param enablePersistentStorage=true
#
# CHECKING STATE
#
#   --status answers the two questions worth asking around a run: can one start now,
#   and is anything still deployed that is costing money? It reports the regional
#   environment quota, every Container Apps environment, the acceptance resource
#   groups, and the URL of any deployment left running by --keep.
#
#   ./utlxe-acceptance.sh --status
#
# CLEANING UP
#
#   --keep leaves a deployment running; --teardown removes every resource group this
#   script has ever created, found by the tag it applies (purpose=utlxe-acceptance)
#   rather than by name. Use it after a --keep run, or to clear groups left behind by
#   an interrupted one.
#
#   ./utlxe-acceptance.sh --teardown --dry-run   # list what would be deleted
#   ./utlxe-acceptance.sh --teardown             # delete them
#
#   Deletion is asynchronous, and a Container Apps environment can hold the
#   one-per-region quota slot for 10-20 minutes after the command returns.
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
# Requires: az (logged in), jq, curl.

set -euo pipefail

# ── configuration ────────────────────────────────────────────────────────────
LOCATION="${LOCATION:-westeurope}"
RG_PREFIX="${RG_PREFIX:-rg-utlxe-acceptance}"
DEPLOYMENT_NAME="${DEPLOYMENT_NAME:-utlxe-acceptance}"
SUBSCRIPTION="${SUBSCRIPTION:-}"          # empty = current az context
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-300}"      # wait for the first healthy response
ENV_WAIT_TIMEOUT="${ENV_WAIT_TIMEOUT:-1500}" # wait for the one-per-region environment slot;
                                             # a ScheduledForDelete environment can hold it 20 min

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
TEMPLATE="" ; IMAGE="" ; KEEP=0 ; TEARDOWN=0 ; DRY_RUN=0 ; STATUS=0 ; declare -a EXTRA_PARAMS=()
CURRENT_RG="" ; KEEP_FQDN="" ; KEEP_KEY="" ; PASS=0 ; FAIL=0 ; SKIPPED=0 ; WARNED=0 ; declare -a RESULTS=()

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
    # --keep exists so the deployment can be inspected, and the Web UI needs the admin
    # key to log in. The key is generated per run and otherwise never printed; without
    # it a kept deployment is a running app nobody can get into. Printed ONLY here, and
    # only because this resource group is deliberately being left alive.
    if [[ -n "$KEEP_KEY" ]]; then
      printf '\n%s── kept deployment ──%s\n' "$C_DIM" "$C_OFF"
      [[ -n "$KEEP_FQDN" ]] && printf '  Web UI     https://%s\n' "$KEEP_FQDN"
      printf '  admin key  %s\n' "$KEEP_KEY"
      printf '  resource g %s\n' "$CURRENT_RG"
      printf '  delete it  ./utlxe-acceptance.sh --teardown\n'
      printf '  check it   ./utlxe-acceptance.sh --status\n'
      # This script smoke-tests one transformation so a non --keep run still proves something.
      # The depth — XML, arrays, decimal scale, %utlx 1.1 validation — lives in the admin
      # suite, which needs a running instance and is therefore only useful after --keep.
      printf '  test it    ./utlxe-admin-tests.sh --url https://%s --key %s\n' \
        "$KEEP_FQDN" "$KEEP_KEY"
      printf '  cost       ./utlxe-costs.sh --hours N\n'
    fi
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
    --param)    EXTRA_PARAMS+=("$2"); shift 2 ;;
    --status)   STATUS=1; shift ;;
    --teardown) TEARDOWN=1; shift ;;
    --dry-run)  DRY_RUN=1; shift ;;
    -h|--help)  sed -n '2,108p' "$0"; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

(( TEARDOWN || STATUS )) || [[ -n "$TEMPLATE" || -n "$IMAGE" ]] || \
  die "pass --template <mainTemplate.json>, --image <ref>, --status, or --teardown"
[[ -z "$TEMPLATE" || -f "$TEMPLATE" ]] || die "template not found: $TEMPLATE"

# ── preflight ────────────────────────────────────────────────────────────────
preflight() {
  log "preflight"
  for b in az jq curl; do command -v "$b" >/dev/null || die "$b not on PATH"; done
  az account show >/dev/null 2>&1 || die "not logged in — run 'az login'"
  [[ -n "$SUBSCRIPTION" ]] && az account set --subscription "$SUBSCRIPTION"
  log "subscription: $(az account show --query name -o tsv)"

  # Azure allows ONE Container Apps environment per region per subscription by default
  # (MaxNumberOfRegionalEnvironmentsInSubExceeded). Teardown is async, so a back-to-back
  # run collides with the previous environment while its resource group is still being
  # deleted. Wait for that rather than failing with an error that looks like a template
  # defect but is really a quota and a race.
  local existing deadline=$((SECONDS + ENV_WAIT_TIMEOUT)) announced=0
  while :; do
    existing=$(az containerapp env list -o json 2>/dev/null | jq -r --arg loc \
      "$(printf '%s' "$LOCATION" | tr -d ' ' | tr 'A-Z' 'a-z')" \
      '[.[] | select(((.location // "") | ascii_downcase | gsub(" ";"")) == $loc)] | length' \
      2>/dev/null || echo 0)
    [[ "$existing" == "0" ]] && break
    if (( announced == 0 )); then
      log "an existing Container Apps environment occupies ${LOCATION} (limit is 1 per region)"
      log "waiting up to ${ENV_WAIT_TIMEOUT}s for it to go away"
      announced=1
    fi
    if (( SECONDS >= deadline )); then
      die "still ${existing} Container Apps environment(s) in ${LOCATION} after ${ENV_WAIT_TIMEOUT}s.
  Azure permits one per region per subscription. Delete the leftover resource group, pick
  another region with --location, or request a quota increase."
    fi
    sleep 15
  done

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
      --parameters "$P_APP_NAME=$app" "$P_ADMIN_KEY=$key" ${EXTRA_PARAMS[@]+"${EXTRA_PARAMS[@]}"} \
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

# Poll /health, but KEEP the evidence. The first version used `curl -fsS … >/dev/null`
# and returned only success/failure, so a 502 from nginx, a TLS failure and "nothing is
# listening" were indistinguishable — a whole 30-minute cycle produced the single word
# "timeout". Every attempt now records the HTTP status and curl's own exit code, and the
# last one is reported.
LAST_HTTP="" ; LAST_CURL_EXIT="" ; LAST_BODY=""
probe() {
  local url="$1" body code
  body=$(curl -s --max-time 10 -w '\n%{http_code}' "$url" 2>/dev/null); LAST_CURL_EXIT=$?
  code="${body##*$'\n'}"
  LAST_BODY="${body%$'\n'*}"
  LAST_HTTP="${code:-000}"
  [[ "$LAST_HTTP" =~ ^2 ]]
}

wait_for_health() {
  local fqdn="$1" deadline=$((SECONDS + HEALTH_TIMEOUT)) n=0
  while (( SECONDS < deadline )); do
    if probe "https://${fqdn}/health"; then return 0; fi
    n=$((n+1))
    # Report early rather than only at the end: if nginx is answering 502 we want to know
    # at 15 seconds, not at 300.
    if (( n == 3 )); then
      log "still unhealthy — HTTP ${LAST_HTTP}, curl exit ${LAST_CURL_EXIT}"
      [[ -n "${LAST_BODY// }" ]] && log "body: $(head -c 200 <<<"$LAST_BODY")"
    fi
    sleep 5
  done
  return 1
}

# Which layer is broken? Each path exercises a different part of the chain:
#   /          nginx static only          -> nginx is up and serving
#   /health    nginx -> engine:8081       -> the sidecar proxy works
#   /metrics   nginx -> engine:8081       -> same, different route
# A 200 on / with a 502 on /health localises the fault to the proxy hop; failures on all
# three mean nothing is listening on 8088.
probe_surface() {
  local fqdn="$1" path
  printf '%s--- public surface ---%s\n' "$C_DIM" "$C_OFF"
  for path in "/" "/health" "/metrics" "/admin/info"; do
    probe "https://${fqdn}${path}" || true
    printf '  %-14s HTTP %-4s curl_exit=%-3s %s\n' \
      "$path" "$LAST_HTTP" "$LAST_CURL_EXIT" "$(head -c 80 <<<"${LAST_BODY//$'\n'/ }")"
  done
}

# What the platform believes it published, and what it is routing to.
dump_ingress() {
  local app="$1"
  printf '%s--- ingress ---%s\n' "$C_DIM" "$C_OFF"
  az containerapp show --name "$app" --resource-group "$CURRENT_RG" \
    --query "properties.configuration.ingress.{external:external,targetPort:targetPort,transport:transport,fqdn:fqdn}" \
    -o json 2>/dev/null || true
  printf '%s--- containers as deployed ---%s\n' "$C_DIM" "$C_OFF"
  az containerapp show --name "$app" --resource-group "$CURRENT_RG" \
    --query "properties.template.containers[].{name:name,image:image,cpu:resources.cpu,memory:resources.memory}" \
    -o table 2>/dev/null || true
}

# Probe from INSIDE the replica. This is the only way to tell "nginx cannot reach the
# engine" from "the engine is fine but ingress is not routing to nginx".
probe_internal() {
  local app="$1"
  printf '%s--- from inside utlxe-ui (nginx) ---%s\n' "$C_DIM" "$C_OFF"
  az containerapp exec --name "$app" --resource-group "$CURRENT_RG" --container utlxe-ui \
    --command "sh -c 'echo ---listening---; (netstat -ltn 2>/dev/null || ss -ltn 2>/dev/null); echo ---config---; cat /etc/nginx/conf.d/default.conf 2>/dev/null | head -30; echo ---engine---; (wget -qO- -T5 http://localhost:8081/health || echo UNREACHABLE)'" \
    2>&1 | head -60 || printf '  exec unavailable\n'
}

# A health timeout is a slow, ambiguous signal. Name the actual cause so that a
# wrong-architecture image reads as "wrong architecture" and not as 300 seconds
# of silence.
diagnose_startup_failure() {
  local app="$1" logs ui_logs sys_logs
  log "container never became healthy — diagnosing"

  # Order matters: what the platform published, what answers from outside, what the
  # containers say, then what is visible from inside the replica. Each narrows the fault
  # to one hop, and all of it comes from the SAME deployment — a second run costs 30 min.
  dump_ingress "$app"
  probe_surface "$KEEP_FQDN"

  # A Container App replica here runs TWO containers, and only one of them is on the
  # path the health check uses:
  #
  #     ingress :8088  ->  utlxe-ui (nginx)  ->  proxy_pass localhost:8081  ->  utlxe
  #
  # The first version of this function fetched the default container's logs only and
  # concluded "engine started but never reported healthy" on a run where the engine had
  # logged `Health endpoint started on port 8081`. The engine was fine; nothing had
  # looked at the proxy in front of it. Collect every container, and say which layer
  # actually failed.
  logs=$(az containerapp logs show --name "$app" --resource-group "$CURRENT_RG" \
          --container utlxe --tail 80 2>/dev/null || echo "")
  ui_logs=$(az containerapp logs show --name "$app" --resource-group "$CURRENT_RG" \
          --container utlxe-ui --tail 40 2>/dev/null || echo "")
  sys_logs=$(az containerapp logs show --name "$app" --resource-group "$CURRENT_RG" \
          --type system --tail 40 2>/dev/null || echo "")

  local all="${logs}
${ui_logs}
${sys_logs}"

  local engine_up=1
  grep -qiE "Health endpoint started|Engine .* is RUNNING" <<<"$logs" || engine_up=0

  if grep -qi "exec format error" <<<"$all"; then
    warn "startup diagnosis" "exec format error — image architecture mismatch (arm64 image on an amd64 host)"
  elif grep -qiE "oomkill|out of memory|killed process" <<<"$all"; then
    warn "startup diagnosis" "OOM-killed during startup — container memory below requirement"
  elif grep -qiE "manifest unknown|unauthorized|pull access denied|ImagePull|ErrImage" <<<"$all"; then
    warn "startup diagnosis" "image pull failed — check registry path, tag and visibility"
  elif [[ -z "${logs// }" && -z "${ui_logs// }" ]]; then
    warn "startup diagnosis" "no logs from either container — image pull almost certainly failed"
  elif (( engine_up == 1 )) && [[ -z "${ui_logs// }" ]]; then
    warn "startup diagnosis" \
      "ENGINE IS HEALTHY on 8081, but the utlxe-ui proxy produced no logs. Ingress targets 8088 (nginx), so the public path is broken in front of a working engine."
  elif (( engine_up == 1 )); then
    warn "startup diagnosis" \
      "ENGINE IS HEALTHY on 8081 — the failure is the public path through utlxe-ui (nginx on 8088), not the engine."
  else
    warn "startup diagnosis" "engine did not report healthy; logs follow"
  fi

  local section
  for section in "utlxe:$logs" "utlxe-ui:$ui_logs" "system:$sys_logs"; do
    local name="${section%%:*}" body="${section#*:}"
    [[ -z "${body// }" ]] && { printf '%s--- %s: no output ---%s\n' "$C_DIM" "$name" "$C_OFF"; continue; }
    printf '%s--- %s ---%s\n' "$C_DIM" "$name" "$C_OFF"
    tail -30 <<<"$body"
  done

  printf '%s--- replicas ---%s\n' "$C_DIM" "$C_OFF"
  az containerapp replica list --name "$app" --resource-group "$CURRENT_RG" \
    --query "[].{replica:name, containers:properties.containers[].{name:name, started:started, ready:ready, restarts:restartCount}}" \
    -o json 2>/dev/null || true

  probe_internal "$app"
}

# ── the suite ────────────────────────────────────────────────────────────────
run_suite() {
  local stamp prefix app key
  stamp=$(date +%Y%m%d-%H%M%S)
  prefix="utlxe-${stamp}"          # what we pass as namePrefix
  app=""                           # discovered after deployment — see below
  key=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen)
  KEEP_KEY="$key"
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
  KEEP_FQDN="$fqdn"
  log "fqdn: $fqdn"

  # Print the URL and key as soon as they exist, not only under --keep. The deployment is
  # live for the whole health-poll window, and that window is exactly when someone wants to
  # open the UI by hand and see what the script is seeing. Withholding the key until the end
  # protected nothing: the resource group — and the key with it — is deleted either way.
  printf '\n%s── reachable now (until teardown) ──%s\n' "$C_DIM" "$C_OFF"
  printf '  Web UI     https://%s\n' "$fqdn"
  printf '  admin key  %s\n' "$key"
  printf '  probe      curl -s -o /dev/null -w "%%{http_code}\\n" https://%s/health\n' "$fqdn"
  printf '  deeper     ./utlxe-admin-tests.sh --url https://%s --key %s\n\n' "$fqdn" "$key"

  if ! wait_for_health "$fqdn"; then
    assert_eq "/health reachable within ${HEALTH_TIMEOUT}s" "timeout" "ok"
    diagnose_startup_failure "$app"
    return
  fi
  assert_eq "/health reachable within ${HEALTH_TIMEOUT}s" "ok" "ok"

  # ---- empty-engine state --------------------------------------------------
  local health; health=$(curl -fsS "https://${fqdn}/health")
  assert_eq "fresh container reports 0 transformations" \
    "$(jq -r '.engine.transformations // "missing"' <<<"$health")" "0"
  # Readiness lives on /health/ready, not /health. Since EB19 it reports 200 for a RUNNING
  # engine regardless of how much work is loaded — a container that starts empty must still be
  # routable, or the Admin API that would load something is unreachable (EB18).
  ready_code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "https://${fqdn}/health/ready")
  assert_eq "fresh container is ready before anything is loaded" "$ready_code" "200"

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
  tx_out=$(jq -r '.output // ""' <<<"$res")
  assert_eq "transformation output is correct" \
    "$(jq -r '.greeting // "none"' <<<"$tx_out" 2>/dev/null || echo "unparseable")" "Hello, acceptance!"
  note "transform latency" "$(jq -r '.duration_ms // "?"' <<<"$res") ms"

  # ---- loaded state --------------------------------------------------------
  health=$(curl -fsS "https://${fqdn}/health")
  ready_code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "https://${fqdn}/health/ready")
  assert_eq "container becomes ready after upload" "$ready_code" "200"
  assert_eq "health reports 1 transformation" \
    "$(jq -r '.engine.transformations // "missing"' <<<"$health")" "1"

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



# ── status ───────────────────────────────────────────────────────────────────
# Answers the two questions worth knowing before and after a run: can I start one
# right now, and is anything still running that I am paying for?
#
# The regional environment quota is the binding constraint — one per region on some
# subscription types — and a Container Apps environment holds that slot for 10-20
# minutes after its resource group is deleted. A run started in that window fails with
# MaxNumberOfRegionalEnvironmentsInSubExceeded, which reads like a template defect.
status() {
  local used limit envs groups apps

  printf '\n%s── quota: %s ──%s\n' "$C_DIM" "$LOCATION" "$C_OFF"
  local usage
  usage=$(az rest --method get --url \
    "https://management.azure.com/subscriptions/$(az account show --query id -o tsv)/providers/Microsoft.App/locations/${LOCATION}/usages?api-version=2024-03-01" \
    2>/dev/null || echo '{}')
  used=$(jq -r '[.value[]? | select(.name.value=="ManagedEnvironmentCount")][0].currentValue // "?"' <<<"$usage")
  limit=$(jq -r '[.value[]? | select(.name.value=="ManagedEnvironmentCount")][0].limit // "?"' <<<"$usage")
  printf '  managed environments  %s of %s' "$used" "$limit"
  if [[ "$used" == "0" ]]; then
    printf '   %s-> a run can start now%s\n' "$C_OK" "$C_OFF"
  elif [[ "$used" == "$limit" ]]; then
    printf '   %s-> FULL; a run will fail until a slot frees%s\n' "$C_BAD" "$C_OFF"
  else
    printf '\n'
  fi

  printf '\n%s── Container Apps environments ──%s\n' "$C_DIM" "$C_OFF"
  envs=$(az containerapp env list \
          --query "[].{name:name,rg:resourceGroup,loc:location,state:properties.provisioningState}" \
          -o tsv 2>/dev/null || true)
  if [[ -z "${envs// }" ]]; then printf '  none\n'; else
    while IFS=$'\t' read -r n rg loc st; do
      [[ -z "$n" ]] && continue
      printf '  %-30s %-36s %-14s %s\n' "$n" "$rg" "$loc" "$st"
    done <<<"$envs"
  fi

  printf '\n%s── acceptance resource groups ──%s\n' "$C_DIM" "$C_OFF"
  groups=$(az group list --tag purpose=utlxe-acceptance \
            --query "[].{name:name,state:properties.provisioningState}" -o tsv 2>/dev/null || true)
  if [[ -z "${groups// }" ]]; then printf '  none\n'; else
    while IFS=$'\t' read -r n st; do
      [[ -z "$n" ]] && continue
      printf '  %-44s %s\n' "$n" "$st"
    done <<<"$groups"
  fi

  # A --keep deployment is the one that quietly costs money. Surface its URL so it can be
  # used, and make the teardown command obvious.
  printf '\n%s── running acceptance deployments ──%s\n' "$C_DIM" "$C_OFF"
  local found=0
  while IFS=$'\t' read -r rg st; do
    [[ -z "$rg" || "$st" == "Deleting" ]] && continue
    apps=$(az containerapp list -g "$rg" \
            --query "[].{name:name,fqdn:properties.configuration.ingress.fqdn}" -o tsv 2>/dev/null || true)
    while IFS=$'\t' read -r an af; do
      [[ -z "$an" ]] && continue
      found=1
      printf '  %s\n    https://%s\n' "$an" "$af"
    done <<<"$apps"
  done <<<"$groups"
  if (( found )); then
    printf '\n  still billing — remove with:  ./utlxe-acceptance.sh --teardown\n'
    printf '  estimate the cost with:       ./utlxe-costs.sh --hours N\n'
  else
    printf '  none\n'
  fi
  echo
}

# ── teardown ─────────────────────────────────────────────────────────────────
# --keep leaves a deployment running, and until now the script had no way to remove it:
# cleanup lived in an EXIT trap belonging to the run that created the group, so anything
# kept — or left behind by an interrupted run — could only be deleted by hand-typing an
# `az group delete` with a timestamped name. The script even printed that command, which
# is a fair sign it was missing one of its own.
#
# Groups are found by the tag the script applies at creation, not by name pattern, so a
# group that was renamed is still found and an unrelated group that merely looks similar
# is not touched.
teardown() {
  local groups count
  groups=$(az group list --tag purpose=utlxe-acceptance \
             --query "[].{name:name,state:properties.provisioningState}" -o tsv 2>/dev/null || true)

  if [[ -z "${groups// }" ]]; then
    log "no acceptance resource groups found (tag purpose=utlxe-acceptance)"
    return 0
  fi

  count=$(wc -l <<<"$groups" | tr -d ' ')
  printf '\n%s── acceptance resource groups ──%s\n' "$C_DIM" "$C_OFF"
  while IFS=$'\t' read -r name state; do
    [[ -z "$name" ]] && continue
    printf '  %-44s %s\n' "$name" "$state"
  done <<<"$groups"
  echo

  if (( DRY_RUN )); then
    log "--dry-run: nothing deleted ($count group(s) would be)"
    return 0
  fi

  while IFS=$'\t' read -r name state; do
    [[ -z "$name" ]] && continue
    if [[ "$state" == "Deleting" ]]; then
      log "already deleting: $name"
      continue
    fi
    log "deleting $name"
    az group delete --name "$name" --yes --no-wait 2>/dev/null \
      && log "  delete issued (async)" \
      || printf '%swarning:%s could not delete %s\n' "$C_BAD" "$C_OFF" "$name" >&2
  done <<<"$groups"

  echo
  log "deletion is asynchronous; the Container Apps environment can hold the"
  log "one-per-region quota slot for 10-20 minutes after this returns."
}

# ── main ─────────────────────────────────────────────────────────────────────
if (( STATUS )); then
  for b in az jq; do command -v "$b" >/dev/null || die "$b not on PATH"; done
  az account show >/dev/null 2>&1 || die "not logged in — run 'az login'"
  log "subscription: $(az account show --query name -o tsv)"
  status
  exit 0
fi

if (( TEARDOWN )); then
  for b in az jq; do command -v "$b" >/dev/null || die "$b not on PATH"; done
  az account show >/dev/null 2>&1 || die "not logged in — run 'az login'"
  log "subscription: $(az account show --query name -o tsv)"
  teardown
  exit 0
fi

preflight
run_suite

printf '\n%s── summary ──%s\n' "$C_DIM" "$C_OFF"
for r in ${RESULTS[@]+"${RESULTS[@]}"}; do
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
