#!/usr/bin/env bash
#
# Exercise UTLXe's Dapr integration against a REAL daprd sidecar, locally.
#
#   ./scripts/dapr-local-test.sh            # build the jar if needed, run, tear down
#   ./scripts/dapr-local-test.sh --keep     # leave the engine and sidecar running
#
# WHY THIS EXISTS
# ---------------
# The Dapr path had NO automated coverage of any kind. `utlxe-acceptance.sh` has zero Service Bus
# assertions and `enableDapr` has never been true in any deployment we have made. The standing
# assumption was that testing it needed an Azure Service Bus namespace.
#
# That assumption was wrong, and it was expensive. The ROUTING logic is broker-agnostic, and two
# Dapr component types need no infrastructure at all -- no cloud account, no credentials, not even
# a Docker daemon:
#
#     pubsub.in-memory    a pub/sub component
#     bindings.cron       an input binding that fires on a timer
#
# Between them they exercise the subscription contract, delivery routing, CloudEvents unwrapping
# and the component-name agreement between the engine and whatever creates its components. Had
# this existed earlier it would have caught, on the day it was introduced, that the engine's
# entire pub/sub path addresses a component (`utlxe-servicebus`) that the Azure templates never
# created -- a dead code path nobody noticed for releases. See docs/features/EF26-*.md.
#
# WHAT IT DOES NOT COVER
# ----------------------
# Service Bus semantics -- lock renewal, retry, dead-lettering -- the connection-string and
# managed-identity paths, and whether a broker surfaces message properties as `metadata.*`
# headers. Those need a real namespace. This harness proves the ENGINE's half of the contract.

set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$(pwd)

KEEP=0
[[ "${1:-}" == "--keep" ]] && KEEP=1

C_OK=$'\e[32m'; C_BAD=$'\e[31m'; C_DIM=$'\e[2m'; C_OFF=$'\e[0m'
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); printf '  %s✓%s %s\n' "$C_OK" "$C_OFF" "$1"; }
bad()  { FAIL=$((FAIL+1)); printf '  %s✗%s %s %s(%s)%s\n' "$C_BAD" "$C_OFF" "$1" "$C_DIM" "${2:-}" "$C_OFF"; }
note() { printf '  %s·%s %s\n' "$C_DIM" "$C_OFF" "$1"; }

DAPRD="${DAPRD:-$HOME/.dapr/bin/daprd}"
[[ -x "$DAPRD" ]] || { echo "daprd not found at $DAPRD — install the Dapr CLI and run 'dapr init --slim'" >&2; exit 2; }

JAR=$(ls -t "$ROOT"/modules/engine/build/libs/utlxe-*.jar 2>/dev/null | head -1 || true)
if [[ -z "$JAR" ]]; then
  echo "building the engine jar…"
  ./gradlew :modules:engine:jar -q
  JAR=$(ls -t "$ROOT"/modules/engine/build/libs/utlxe-*.jar | head -1)
fi
note "jar    $(basename "$JAR")"
note "daprd  $("$DAPRD" --version 2>&1 | head -1)"

# The component name is a CONTRACT with the engine. Read it from the source rather than
# repeating it here, so this harness cannot drift from what the engine addresses.
PUBSUB=$(grep -o 'DEFAULT_PUBSUB_NAME = "[^"]*"' \
  modules/engine/src/main/kotlin/com/glomidco/utlx/engine/config/TransformConfig.kt \
  | sed 's/.*"\(.*\)"/\1/')
[[ -n "$PUBSUB" ]] || { echo "could not read DEFAULT_PUBSUB_NAME from the engine source" >&2; exit 2; }
note "engine pub/sub component: $PUBSUB"

D=$(mktemp -d); mkdir -p "$D/components"
freeport() { python3 -c "import socket;s=socket.socket();s.bind(('',0));print(s.getsockname()[1]);s.close()"; }
APP=$(freeport); ADM=$(freeport); DHTTP=$(freeport); DGRPC=$(freeport)
KEY="dapr-local-$RANDOM"

cleanup() {
  if (( KEEP )); then
    printf '\n%skept:%s engine :%s  admin :%s  daprd :%s  dir %s\n' "$C_DIM" "$C_OFF" "$APP" "$ADM" "$DHTTP" "$D"
    return
  fi
  [[ -n "${DAPRD_PID:-}" ]] && kill "$DAPRD_PID" 2>/dev/null || true
  [[ -n "${ENGINE_PID:-}" ]] && kill "$ENGINE_PID" 2>/dev/null || true
  sleep 1
  [[ -n "${DAPRD_PID:-}" ]] && kill -9 "$DAPRD_PID" 2>/dev/null || true
  [[ -n "${ENGINE_PID:-}" ]] && kill -9 "$ENGINE_PID" 2>/dev/null || true
  rm -rf "$D"
}
trap cleanup EXIT

cat > "$D/components/pubsub.yaml" <<EOF
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: $PUBSUB
spec:
  type: pubsub.in-memory
  version: v1
EOF

# An input binding whose name matches NO transformation's name. One transformation declares it as
# its input queue instead — which is the EF26 behaviour under test.
# TWO input bindings, because rules 3 and 4 are mutually exclusive on a single component:
# the exact-name match is deliberately more specific, so if a transformation were named after
# this component the declared-input path could never be reached. One component per rule.
for c in orders-in-queue legacy-binding; do
cat > "$D/components/cron-$c.yaml" <<EOF
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: $c
spec:
  type: bindings.cron
  version: v1
  metadata:
  - name: schedule
    value: "@every 2s"
EOF
done

# Logs carry ANSI colour codes, which makes `grep` (ugrep with -I) report "Binary file matches"
# and silently find nothing — the trap AGENTS.md documents. Always scan them through this.
logscan() { python3 -c "
import re,sys
t=open(sys.argv[1],'rb').read().decode('utf-8','replace')
t=re.sub(r'\x1b\[[0-9;]*m','',t)
sys.exit(0 if re.search(sys.argv[2], t) else 1)" "$1" "$2"; }

printf '\n%s── starting engine ──%s\n' "$C_DIM" "$C_OFF"
UTLXE_ADMIN_KEY="$KEY" java -jar "$JAR" --mode http --http-port "$APP" --admin-port "$ADM" \
  > "$D/engine.log" 2>&1 &
ENGINE_PID=$!
disown "$ENGINE_PID" 2>/dev/null || true
for _ in $(seq 1 60); do curl -fsS -o /dev/null "http://127.0.0.1:$APP/api/health" 2>/dev/null && break; sleep 0.5; done
curl -fsS -o /dev/null "http://127.0.0.1:$APP/api/health" || { echo "engine did not start; see $D/engine.log" >&2; exit 1; }

load() { # name, utlx source
  python3 -c "import json,sys;print(json.dumps({'transformationId':sys.argv[1],'utlxSource':sys.argv[2],'strategy':'TEMPLATE'}))" "$1" "$2" \
  | curl -fsS -X POST -H 'Content-Type: application/json' --data-binary @- \
      "http://127.0.0.1:$APP/api/load" -o /dev/null
}
messaging() { # name, json
  curl -fsS -X POST -H "X-Admin-Key: $KEY" -H 'Content-Type: application/json' \
    -d "$2" "http://127.0.0.1:$ADM/admin/transformations/$1/messaging" -o /dev/null
}

SRC='%utlx 1.0
input json
output json
---
{ handledBy: "HANDLER", sku: $input.sku }'

load orders          "${SRC/HANDLER/orders}"
load invoices        "${SRC/HANDLER/invoices}"
load legacy-binding  "${SRC/HANDLER/named-after-component}"
messaging orders  '{"input":{"topic":"orders-in","subscription":"utlxe"}}'
messaging invoices '{"input":{"queue":"orders-in-queue"}}'

printf '\n%s── assertions ──%s\n' "$C_DIM" "$C_OFF"

SUBS=$(curl -fsS "http://127.0.0.1:$APP/dapr/subscribe")
grep -q "\"pubsubname\":\"$PUBSUB\"" <<<"$SUBS" \
  && ok "subscriptions name the component the engine is configured with ($PUBSUB)" \
  || bad "subscriptions name the configured component" "$SUBS"
grep -q '"route":"/pubsub/orders"' <<<"$SUBS" \
  && ok "subscription routes by TRANSFORMATION name, not component name" \
  || bad "subscription routes by transformation name" "$SUBS"

printf '\n%s── starting daprd ──%s\n' "$C_DIM" "$C_OFF"
"$DAPRD" --app-id utlxe-app --app-port "$APP" --dapr-http-port "$DHTTP" \
  --dapr-grpc-port "$DGRPC" --resources-path "$D/components" --log-level info \
  > "$D/daprd.log" 2>&1 &
DAPRD_PID=$!
disown "$DAPRD_PID" 2>/dev/null || true
for _ in $(seq 1 60); do curl -fsS -o /dev/null "http://127.0.0.1:$DHTTP/v1.0/healthz" 2>/dev/null && break; sleep 0.5; done

logscan "$D/daprd.log" "Component loaded: $PUBSUB" \
  && ok "daprd loaded the pub/sub component" \
  || bad "daprd loaded the pub/sub component" "see $D/daprd.log"
logscan "$D/daprd.log" "subscribed to the following topics.*orders-in.*$PUBSUB" \
  && ok "daprd consumed /dapr/subscribe and subscribed to the declared topic" \
  || bad "daprd subscribed to the declared topic" "see $D/daprd.log"

curl -fsS -X POST -H 'Content-Type: application/json' -d '{"sku":"A-1"}' -o /dev/null \
  "http://127.0.0.1:$DHTTP/v1.0/publish/$PUBSUB/orders-in"
sleep 3
logscan "$D/engine.log" "\[orders\] PubSub MessageId" \
  && ok "a published message was delivered to /pubsub/orders and transformed" \
  || bad "published message delivered" "see $D/engine.log"
logscan "$D/engine.log" "ceType=com.dapr.event.sent" \
  && ok "CloudEvents envelope was unwrapped" \
  || bad "CloudEvents unwrapped" "see $D/engine.log"

# EF26 rule 3. The cron component is 'orders-in-queue'; the transformation that must receive it is
# 'invoices', which only DECLARES that queue. Before EF26 this was a 503.
sleep 3
logscan "$D/engine.log" "\[invoices\].*binding=orders-in-queue" \
  && ok "binding routed to the transformation that DECLARES the queue (name is free)" \
  || bad "binding routed by declared input" "see $D/engine.log"

# EF26 rule 4 must still hold: a transformation NAMED after its component keeps receiving.
# Delivered by the second cron binding, so this does not compete with the rule 3 assertion.
logscan "$D/engine.log" "\[legacy-binding\].*binding=legacy-binding" \
  && ok "a transformation named after its component still receives (rule 4 kept)" \
  || bad "rule 4 compatibility" "see $D/engine.log"

printf '\n%s──%s %s%d passed%s' "$C_DIM" "$C_OFF" "$C_OK" "$PASS" "$C_OFF"
(( FAIL )) && printf ', %s%d failed%s' "$C_BAD" "$FAIL" "$C_OFF"
printf '\n\n'
exit $(( FAIL > 0 ? 1 : 0 ))
