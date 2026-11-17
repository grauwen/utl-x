#!/usr/bin/env bash
#
# utlxe-costs.sh — what the UTLXe offering costs to run, estimated or actual
#
# Companion to utlxe-acceptance.sh. Kept separate on purpose: an acceptance run
# lasts minutes, but Azure's billing data does not appear for 8-24 hours, so the
# two can never answer in the same invocation. Separation also keeps the
# acceptance harness free of the costmanagement extension, and keeps a failed
# cost query from returning the acceptance script's exit 1, which means "the
# offering is broken".
#
# TWO MODES
#
#   --estimate   (default) Computes cost from live Azure retail rates and a
#                configuration you describe. Needs no Azure login and no billing
#                access: the Retail Prices API is public. Use it to size a test
#                run, or to answer "how bad is it if I leave --keep running".
#
#   --actual     Queries what you were really billed, for resource groups whose
#                name starts with the acceptance prefix. Needs an az login with
#                billing read access, and only returns data once Azure has
#                processed it — a run from an hour ago will show nothing.
#                Cost records survive the resource group's deletion.
#
# WHAT IT ASSUMES
#
#   The Marketplace template declares no workload profiles, so it creates a
#   Consumption-only Container Apps environment. That matters: Consumption bills
#   purely per vCPU-second, GiB-second and request, with no $0.10/hour
#   environment management charge. If the template ever gains a workload
#   profile, this script's estimate becomes wrong and WORKLOAD_PROFILE below
#   must be set.
#
# USAGE
#
#   ./utlxe-costs.sh                                  # default Starter, 10 min
#   ./utlxe-costs.sh --cpu 4 --memory 8 --minutes 30  # Professional, longer run
#   ./utlxe-costs.sh --hours 24                       # cost of leaving --keep up
#   ./utlxe-costs.sh --region northeurope
#   ./utlxe-costs.sh --actual --days 30
#
# Requires: curl, jq. --actual additionally requires az (logged in).

set -euo pipefail

# ── configuration ────────────────────────────────────────────────────────────
REGION="${REGION:-westeurope}"
RG_PREFIX="${RG_PREFIX:-rg-utlxe-acceptance}"
CURRENCY="${CURRENCY:-USD}"

# Starter plan defaults, matching starter/mainTemplate.json.
CPU="${CPU:-2}"
MEMORY="${MEMORY:-4}"
MINUTES="${MINUTES:-10}"
REQUESTS="${REQUESTS:-50}"        # the acceptance suite makes a few dozen calls

# Set this only if the template gains a workload profile; Consumption has no
# environment charge, Dedicated bills $0.10/hour per environment.
WORKLOAD_PROFILE="${WORKLOAD_PROFILE:-}"

# Azure's monthly free grant per subscription, Consumption plan.
FREE_VCPU_SECONDS=180000
FREE_GIB_SECONDS=360000
FREE_REQUESTS=2000000

# Fallback rates, westeurope, captured 2026-11-17. Used only when the Retail
# Prices API is unreachable — a stale number clearly labelled beats no answer,
# but the script says which it used.
FALLBACK_VCPU_RATE=0.000034       # USD per vCPU-second, active
FALLBACK_MEM_RATE=0.000004        # USD per GiB-second, active
FALLBACK_REQ_RATE=0.56            # USD per 1M requests

MODE=estimate
DAYS=30

C_OK=$'\033[0;32m' ; C_WARN=$'\033[0;33m' ; C_DIM=$'\033[0;90m' ; C_OFF=$'\033[0m'
[[ -t 1 ]] || { C_OK="" ; C_WARN="" ; C_DIM="" ; C_OFF="" ; }

die() { printf 'fatal: %s\n' "$*" >&2; exit 2; }

# ── arguments ────────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --estimate) MODE=estimate; shift ;;
    --actual)   MODE=actual;   shift ;;
    --cpu)      CPU="$2";      shift 2 ;;
    --memory)   MEMORY="$2";   shift 2 ;;
    --minutes)  MINUTES="$2";  shift 2 ;;
    --hours)    MINUTES=$(( $2 * 60 )); shift 2 ;;
    --requests) REQUESTS="$2"; shift 2 ;;
    --region)   REGION="$2";   shift 2 ;;
    --days)     DAYS="$2";     shift 2 ;;
    -h|--help)  sed -n '2,42p' "$0"; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

for b in curl jq; do command -v "$b" >/dev/null || die "$b not on PATH"; done

# ── live retail rates ────────────────────────────────────────────────────────
# The Retail Prices API is public and unauthenticated. Meter names are matched
# exactly rather than by substring: "Standard vCPU Active Usage" and "Standard
# vCPU Idle Usage" differ by ~8x, and picking the wrong one silently understates
# the bill.
RATE_SOURCE="live retail rates"
fetch_rates() {
  local url body
  url="https://prices.azure.com/api/retail/prices?\$filter=serviceName%20eq%20'Azure%20Container%20Apps'%20and%20armRegionName%20eq%20'${REGION}'"
  body=$(curl -s --max-time 20 "$url" 2>/dev/null) || return 1
  jq -e '.Items | length > 0' <<<"$body" >/dev/null 2>&1 || return 1

  local v m r
  v=$(jq -r '[.Items[] | select(.meterName == "Standard vCPU Active Usage")   | .retailPrice] | first // empty' <<<"$body")
  m=$(jq -r '[.Items[] | select(.meterName == "Standard Memory Active Usage") | .retailPrice] | first // empty' <<<"$body")
  r=$(jq -r '[.Items[] | select(.meterName == "Standard Requests")            | .retailPrice] | first // empty' <<<"$body")
  [[ -n "$v" && -n "$m" && -n "$r" ]] || return 1

  VCPU_RATE="$v" ; MEM_RATE="$m" ; REQ_RATE="$r"
  CURRENCY=$(jq -r '[.Items[] | .currencyCode] | first // "USD"' <<<"$body")
  return 0
}

if ! fetch_rates; then
  VCPU_RATE="$FALLBACK_VCPU_RATE" ; MEM_RATE="$FALLBACK_MEM_RATE" ; REQ_RATE="$FALLBACK_REQ_RATE"
  RATE_SOURCE="FALLBACK rates captured 2026-11-17 (prices API unreachable)"
fi

# ── estimate ─────────────────────────────────────────────────────────────────
estimate() {
  local seconds=$(( MINUTES * 60 ))

  python3 - "$CPU" "$MEMORY" "$seconds" "$REQUESTS" \
             "$VCPU_RATE" "$MEM_RATE" "$REQ_RATE" \
             "$FREE_VCPU_SECONDS" "$FREE_GIB_SECONDS" "$FREE_REQUESTS" \
             "$CURRENCY" "$REGION" "$RATE_SOURCE" "$MINUTES" "$WORKLOAD_PROFILE" <<'PY'
import sys
cpu, mem, secs, reqs = float(sys.argv[1]), float(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
rv, rm, rr = float(sys.argv[5]), float(sys.argv[6]), float(sys.argv[7])
fv, fg, fr = int(sys.argv[8]), int(sys.argv[9]), int(sys.argv[10])
cur, region, src, minutes, profile = sys.argv[11], sys.argv[12], sys.argv[13], int(sys.argv[14]), sys.argv[15]

vcpu_s = cpu * secs
gib_s  = mem * secs
cost_v, cost_m = vcpu_s * rv, gib_s * rm
cost_r = reqs / 1_000_000 * rr
env    = (secs / 3600) * 0.10 if profile else 0.0
total  = cost_v + cost_m + cost_r + env

print(f"  configuration     {cpu:g} vCPU, {mem:g} GiB, {minutes} min, {reqs} requests  [{region}]")
print(f"  rates             {src}")
print()
print(f"  vCPU              {vcpu_s:>12,.0f} vCPU-s  x {rv:<10} = {cost_v:>8.4f} {cur}")
print(f"  memory            {gib_s:>12,.0f} GiB-s   x {rm:<10} = {cost_m:>8.4f} {cur}")
print(f"  requests          {reqs:>12,}         x {rr}/1M     = {cost_r:>8.4f} {cur}")
if profile:
    print(f"  environment       workload profile {profile}        = {env:>8.4f} {cur}")
print(f"  {'-'*58}")
print(f"  at retail rates   {total:>46.4f} {cur}")
print()

# The free grant is what actually determines whether anything is billed.
pv, pg, pr = vcpu_s / fv * 100, gib_s / fg * 100, reqs / fr * 100
print(f"  monthly free grant usage (per subscription, Consumption plan):")
print(f"    vCPU-seconds    {vcpu_s:>12,.0f} of {fv:,}   ({pv:.2f}%)")
print(f"    GiB-seconds     {gib_s:>12,.0f} of {fg:,}   ({pg:.2f}%)")
print(f"    requests        {reqs:>12,} of {fr:,}   ({pr:.4f}%)")
print()
if max(pv, pg, pr) < 100:
    runs = int(min(fv / vcpu_s, fg / gib_s)) if vcpu_s and gib_s else 0
    # The grant is per SUBSCRIPTION per month and shared with everything else
    # running there, so "free" is conditional and must be stated as such.
    print(f"  -> fits inside the monthly grant: {0.0:.2f} {cur}, IF the grant is otherwise unused.")
    print(f"     the grant is subscription-wide and resets monthly — anything else")
    print(f"     running on Container Apps consumes the same allowance.")
    print(f"     room for about {runs} runs of this size if nothing else competes.")
else:
    print(f"  -> EXCEEDS the free grant; expect close to {total:.4f} {cur}.")
print()
hourly = (cpu * 3600 * rv) + (mem * 3600 * rm)
print(f"  if left running   {hourly:.4f} {cur}/hour  ({hourly*24:.2f}/day, {hourly*24*30:.2f}/30 days)")
print(f"                    — this is what --keep on the acceptance script costs")
PY

  printf '\n  %sEstimate at retail rates. Your actual rate depends on your agreement%s\n' "$C_DIM" "$C_OFF"
  printf '  %s(MPN, EA and CSP all differ). Use --actual for what you were billed.%s\n' "$C_DIM" "$C_OFF"
}

# ── actual ───────────────────────────────────────────────────────────────────
# NOTE: unlike the estimate, this path has not been exercised against a live
# subscription from this repository. Treat the first run as a check of the query
# itself, not only of the numbers.
actual() {
  command -v az >/dev/null || die "az not on PATH (required for --actual)"
  az account show >/dev/null 2>&1 || die "not logged in — run 'az login'"

  local sub_id from to
  sub_id=$(az account show --query id -o tsv)
  from=$(date -u -v-"${DAYS}"d +%Y-%m-%d 2>/dev/null || date -u -d "${DAYS} days ago" +%Y-%m-%d)
  to=$(date -u +%Y-%m-%d)

  printf '  resource groups matching %s*, %s to %s\n\n' "$RG_PREFIX" "$from" "$to"

  if ! az extension show --name costmanagement >/dev/null 2>&1; then
    printf '  %sthe costmanagement extension is not installed:%s\n' "$C_WARN" "$C_OFF"
    printf '      az extension add --name costmanagement\n\n'
    die "cannot query actual cost without it"
  fi

  local out
  out=$(az costmanagement query \
          --type ActualCost \
          --scope "/subscriptions/${sub_id}" \
          --timeframe Custom --time-period from="$from" to="$to" \
          --dataset-granularity None \
          --dataset-aggregation '{"total":{"name":"Cost","function":"Sum"}}' \
          --dataset-grouping name=ResourceGroupName type=Dimension \
          -o json 2>/dev/null) || die "cost query failed — check billing read access on the subscription"

  local rows
  rows=$(jq -r --arg p "$(echo "$RG_PREFIX" | tr 'A-Z' 'a-z')" '
      (.properties.rows // [])
      | map(select((.[1] // "" | ascii_downcase) | startswith($p)))
      | .[] | "  \(.[1])  \(.[0] | tonumber | . * 10000 | round / 10000) \(.[2] // "")"
    ' <<<"$out" 2>/dev/null || true)

  if [[ -z "$rows" ]]; then
    printf '  no billed cost recorded for %s* in that window.\n\n' "$RG_PREFIX"
    printf '  %sThat is the expected result when runs stayed inside the monthly free%s\n' "$C_DIM" "$C_OFF"
    printf '  %sgrant, and also when a run is too recent — billing data lags 8-24h.%s\n' "$C_DIM" "$C_OFF"
    return 0
  fi

  echo "$rows"
  local total
  total=$(jq -r --arg p "$(echo "$RG_PREFIX" | tr 'A-Z' 'a-z')" '
      [(.properties.rows // [])[] | select((.[1] // "" | ascii_downcase) | startswith($p)) | .[0]]
      | add // 0 | . * 10000 | round / 10000' <<<"$out")
  printf '\n  total  %s\n' "$total"
}

# ── main ─────────────────────────────────────────────────────────────────────
printf '\n%s── UTLXe cost: %s ──%s\n\n' "$C_DIM" "$MODE" "$C_OFF"
case "$MODE" in
  estimate) estimate ;;
  actual)   actual ;;
esac
echo
