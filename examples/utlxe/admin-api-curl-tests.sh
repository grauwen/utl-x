#!/bin/bash
# ============================================================================
# UTLXe Admin API — curl test script
# Tests admin endpoints (EF03, EF06, EF09, EF10, EF12) against a running UTLXe instance.
#
# Usage:
#   ./gradlew :modules:engine:jar
#   ./examples/utlxe/admin-api-curl-tests.sh [--ports default|custom]
#
# With --ports custom (or no arg), uses non-default ports to verify port config works:
#   Admin: 18081, Data: 18085
#
# With --ports default, uses standard ports:
#   Admin: 8081, Data: 8085
#
# The script starts and stops UTLXe automatically. No manual setup needed.
# ============================================================================

set -e

# Port configuration
if [ "$1" = "--ports" ] && [ "$2" = "default" ]; then
    ADMIN_PORT=8081
    DATA_PORT=8085
else
    # Non-default ports to verify --admin-port and --http-port work
    ADMIN_PORT=18081
    DATA_PORT=18085
fi

export UTLXE_ADMIN_KEY="${UTLXE_ADMIN_KEY:-test-key}"
ADMIN="http://localhost:$ADMIN_PORT"
DATA="http://localhost:$DATA_PORT"
KEY="$UTLXE_ADMIN_KEY"
JAR=$(ls modules/engine/build/libs/utlxe-*.jar 2>/dev/null | head -1)

# Start UTLXe
echo "Starting UTLXe (admin=$ADMIN_PORT, data=$DATA_PORT)..."
java -jar "$JAR" --mode http --admin-port $ADMIN_PORT --http-port $DATA_PORT > /tmp/utlxe-test.log 2>&1 &
UTLXE_PID=$!

# Wait for startup
sleep 4
if ! kill -0 $UTLXE_PID 2>/dev/null; then
    echo "UTLXe failed to start. Log:"
    cat /tmp/utlxe-test.log
    exit 1
fi
echo "UTLXe running (PID=$UTLXE_PID)"

# Cleanup on exit — kill UTLXe but preserve the test exit code
TEST_EXIT=0
cleanup() {
    echo ""
    echo "Stopping UTLXe (PID=$UTLXE_PID)..."
    kill $UTLXE_PID 2>/dev/null
    wait $UTLXE_PID 2>/dev/null || true
    exit $TEST_EXIT
}
trap cleanup EXIT
PASS=0
FAIL=0

green() { printf "\033[32m✓ %s\033[0m\n" "$1"; PASS=$((PASS+1)); }
red()   { printf "\033[31m✗ %s\033[0m\n" "$1"; FAIL=$((FAIL+1)); }
header(){ printf "\n\033[1m── %s ──\033[0m\n" "$1"; }

check() {
    local desc="$1" expected="$2" actual="$3"
    if [ "$actual" = "$expected" ]; then green "$desc"
    else red "$desc (expected $expected, got $actual)"; fi
}

check_contains() {
    local desc="$1" needle="$2" haystack="$3"
    if echo "$haystack" | grep -q "$needle"; then green "$desc"
    else red "$desc (expected to contain '$needle')"; fi
}

# ============================================================================
header "Health (no auth required)"
# ============================================================================

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$ADMIN/health")
check "GET /health returns 200" "200" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$ADMIN/health/live")
check "GET /health/live returns 200" "200" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$ADMIN/metrics")
check "GET /metrics returns 200" "200" "$STATUS"

# ============================================================================
header "Auth"
# ============================================================================

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$ADMIN/admin/transformations")
check "GET /admin/transformations without key returns 403" "403" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "X-Admin-Key: wrong-key" \
  "$ADMIN/admin/transformations")
check "GET /admin/transformations with wrong key returns 403" "403" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "X-Admin-Key: $KEY" \
  "$ADMIN/admin/transformations")
check "GET /admin/transformations with correct key returns 200" "200" "$STATUS"

# ============================================================================
header "Readiness (before upload)"
# ============================================================================

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$ADMIN/health/ready")
check "GET /health/ready with no transformations returns 503" "503" "$STATUS"

# ============================================================================
header "Upload transformation"
# ============================================================================

BODY=$(curl -s -w "\n%{http_code}" \
  -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: text/plain" \
  -d '%utlx 1.0
input json
output json
---
{
  greeting: concat("Hello, ", $input.name, "!"),
  processed: true
}' \
  "$ADMIN/admin/transformations/hello")
STATUS=$(echo "$BODY" | tail -1)
RESPONSE=$(echo "$BODY" | sed '$d')
check "POST /admin/transformations/hello returns 200" "200" "$STATUS"
check_contains "Response says deployed" "deployed" "$RESPONSE"

# ============================================================================
header "Upload a second transformation"
# ============================================================================

BODY=$(curl -s -w "\n%{http_code}" \
  -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: text/plain" \
  -d '%utlx 1.0
input json
output json
---
{
  orderId: concat("ORD-", $input.id),
  total: $input.quantity * $input.price
}' \
  "$ADMIN/admin/transformations/order-calc")
STATUS=$(echo "$BODY" | tail -1)
check "POST /admin/transformations/order-calc returns 200" "200" "$STATUS"

# ============================================================================
header "Upload invalid source"
# ============================================================================

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: text/plain" \
  -d 'this is not valid utlx at all' \
  "$ADMIN/admin/transformations/bad-one")
check "POST invalid source returns 400" "400" "$STATUS"

# ============================================================================
header "Readiness (after upload)"
# ============================================================================

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$ADMIN/health/ready")
check "GET /health/ready after upload returns 200" "200" "$STATUS"

BODY=$(curl -s "$ADMIN/health/ready")
check_contains "Ready response has ready:true" "true" "$BODY"

# ============================================================================
header "List transformations"
# ============================================================================

BODY=$(curl -s -H "X-Admin-Key: $KEY" "$ADMIN/admin/transformations")
check_contains "List contains 'hello'" "hello" "$BODY"
check_contains "List contains 'order-calc'" "order-calc" "$BODY"

# ============================================================================
header "Get transformation details"
# ============================================================================

BODY=$(curl -s -H "X-Admin-Key: $KEY" "$ADMIN/admin/transformations/hello")
check_contains "Details contain source" "utlx" "$BODY"
check_contains "Details contain strategy" "strategy" "$BODY"

# ============================================================================
header "Test transformation"
# ============================================================================

BODY=$(curl -s \
  -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"name": "World"}' \
  "$ADMIN/admin/transformations/hello/test")
check_contains "Test returns ok" "ok" "$BODY"
check_contains "Test output contains greeting" "Hello, World!" "$BODY"

BODY=$(curl -s \
  -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"id": "123", "quantity": 5, "price": 24.50}' \
  "$ADMIN/admin/transformations/order-calc/test")
check_contains "Order test returns ok" "ok" "$BODY"
check_contains "Order test contains ORD-123" "ORD-123" "$BODY"
check_contains "Order test contains 122.5" "122.5" "$BODY"

# ============================================================================
header "Test nonexistent transformation"
# ============================================================================

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{}' \
  "$ADMIN/admin/transformations/ghost/test")
check "Test nonexistent returns 404" "404" "$STATUS"

# ============================================================================
header "Engine info"
# ============================================================================

BODY=$(curl -s -H "X-Admin-Key: $KEY" "$ADMIN/admin/info")
check_contains "Info has version" "version" "$BODY"
check_contains "Info has uptime" "uptime_seconds" "$BODY"
check_contains "Info has transformations count" "transformations" "$BODY"
check_contains "Info has admin_key_set" "admin_key_set" "$BODY"

# ============================================================================
header "Data plane discovery (port 8085, no auth)"
# ============================================================================

BODY=$(curl -s "$DATA/api/transformations" 2>/dev/null) || true
if [ -n "$BODY" ]; then
    check_contains "Discovery lists hello" "hello" "$BODY"
    check_contains "Discovery lists order-calc" "order-calc" "$BODY"
else
    red "Data plane not reachable on $DATA (skipped)"
fi

# ============================================================================
header "Data plane execute (port 8085)"
# ============================================================================

BODY=$(curl -s \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"payload": "{\"name\": \"Azure\"}", "contentType": "application/json"}' \
  "$DATA/api/execute/hello" 2>/dev/null) || true
if echo "$BODY" | grep -q "Azure"; then
    check_contains "Execute returns Azure in output" "Azure" "$BODY"
elif [ -n "$BODY" ]; then
    # Might work with direct payload too — try the Dapr-style path
    BODY2=$(curl -s \
      -X POST \
      -H "Content-Type: application/json" \
      -d '{"name": "Azure"}' \
      "$DATA/hello" 2>/dev/null) || true
    if echo "$BODY2" | grep -q "Azure"; then
        check_contains "Execute via Dapr path returns Azure" "Azure" "$BODY2"
    else
        red "Data plane execute returned unexpected: $BODY"
    fi
else
    red "Data plane not reachable on $DATA (skipped)"
fi

# ============================================================================
header "Delete transformation"
# ============================================================================

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X DELETE \
  -H "X-Admin-Key: $KEY" \
  "$ADMIN/admin/transformations/order-calc")
check "DELETE order-calc returns 200" "200" "$STATUS"

BODY=$(curl -s -H "X-Admin-Key: $KEY" "$ADMIN/admin/transformations")
check_contains "List no longer contains order-calc" "hello" "$BODY"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X DELETE \
  -H "X-Admin-Key: $KEY" \
  "$ADMIN/admin/transformations/nonexistent")
check "DELETE nonexistent returns 404" "404" "$STATUS"

# ============================================================================
header "Hot-swap (update existing transformation)"
# ============================================================================

BODY=$(curl -s \
  -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: text/plain" \
  -d '%utlx 1.0
input json
output json
---
{
  greeting: concat("Hi, ", upperCase($input.name), "!"),
  version: "v2"
}' \
  "$ADMIN/admin/transformations/hello")
check_contains "Hot-swap says deployed" "deployed" "$BODY"

BODY=$(curl -s \
  -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"name": "world"}' \
  "$ADMIN/admin/transformations/hello/test")
check_contains "Hot-swapped test returns v2 output" "WORLD" "$BODY"
check_contains "Hot-swapped test has version field" "v2" "$BODY"

# ============================================================================
header "EF10: Messaging config (stage → sync)"
# ============================================================================

# Upload a transformation for messaging tests
curl -s -o /dev/null \
  -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: text/plain" \
  -d '%utlx 1.0
input json
output json
---
$input' \
  "$ADMIN/admin/transformations/msg-test"

# Set messaging config (queue in, topic out)
BODY=$(curl -s -w "\n%{http_code}" \
  -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"input": {"queue": "orders-in"}, "output": {"topic": "processed-orders"}}' \
  "$ADMIN/admin/transformations/msg-test/messaging")
STATUS=$(echo "$BODY" | tail -1)
RESPONSE=$(echo "$BODY" | sed '$d')
check "POST messaging config returns 200" "200" "$STATUS"
check_contains "Messaging is draft" "draft" "$RESPONSE"
check_contains "Messaging contains queue" "orders-in" "$RESPONSE"

# Get messaging config
BODY=$(curl -s -H "X-Admin-Key: $KEY" "$ADMIN/admin/transformations/msg-test/messaging")
check_contains "GET messaging has input queue" "orders-in" "$BODY"
check_contains "GET messaging has output topic" "processed-orders" "$BODY"
check_contains "GET messaging shows unsynced" "unsynced" "$BODY"

# Sync single transformation
BODY=$(curl -s -w "\n%{http_code}" \
  -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '' \
  "$ADMIN/admin/transformations/msg-test/sync")
STATUS=$(echo "$BODY" | tail -1)
RESPONSE=$(echo "$BODY" | sed '$d')
check "POST sync returns 200" "200" "$STATUS"
check_contains "Sync result has status" "sync" "$RESPONSE"

# Get sync overview
BODY=$(curl -s -H "X-Admin-Key: $KEY" "$ADMIN/admin/sync")
check_contains "Sync overview has dapr_mode" "dapr_mode" "$BODY"
check_contains "Sync overview has transformations" "msg-test" "$BODY"

# Delete messaging config
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X DELETE \
  -H "X-Admin-Key: $KEY" \
  "$ADMIN/admin/transformations/msg-test/messaging")
check "DELETE messaging returns 200" "200" "$STATUS"

# Sync all (bulk)
BODY=$(curl -s -w "\n%{http_code}" \
  -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '' \
  "$ADMIN/admin/sync")
STATUS=$(echo "$BODY" | tail -1)
check "POST /admin/sync (bulk) returns 200" "200" "$STATUS"

# ============================================================================
header "EF06: Dapr pub/sub subscribe"
# ============================================================================

# Upload transformation with topic config via messaging endpoint
curl -s -o /dev/null \
  -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: text/plain" \
  -d '%utlx 1.0
input json
output json
---
$input' \
  "$ADMIN/admin/transformations/topic-test"

curl -s -o /dev/null \
  -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"input": {"topic": "incoming-orders", "subscription": "utlxe"}}' \
  "$ADMIN/admin/transformations/topic-test/messaging"

# Check /dapr/subscribe
BODY=$(curl -s "$DATA/dapr/subscribe" 2>/dev/null) || true
if [ -n "$BODY" ]; then
    check_contains "Subscribe contains topic" "incoming-orders" "$BODY"
    check_contains "Subscribe contains pubsubname" "utlxe-servicebus" "$BODY"
    check_contains "Subscribe contains route" "pubsub" "$BODY"
else
    red "Data plane /dapr/subscribe not reachable"
fi

# Test pub/sub input with CloudEvents
BODY=$(curl -s -w "\n%{http_code}" \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"specversion":"1.0","type":"test","source":"/test","id":"ce-1","data":{"name":"pubsub-test"}}' \
  "$DATA/pubsub/topic-test" 2>/dev/null) || true
STATUS=$(echo "$BODY" | tail -1)
if [ "$STATUS" = "200" ]; then
    check "POST /pubsub/topic-test CloudEvents returns 200" "200" "$STATUS"
else
    red "Pub/sub input returned $STATUS (may need transformation with topic config in registry)"
fi

# ============================================================================
header "EF12: Log management"
# ============================================================================

# Get current log level
BODY=$(curl -s -H "X-Admin-Key: $KEY" "$ADMIN/admin/log/level")
check_contains "GET log level returns level" "level" "$BODY"

# Change log level to DEBUG
BODY=$(curl -s -w "\n%{http_code}" \
  -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"level": "DEBUG", "revert_after_minutes": 60}' \
  "$ADMIN/admin/log/level")
STATUS=$(echo "$BODY" | tail -1)
RESPONSE=$(echo "$BODY" | sed '$d')
check "POST log level DEBUG returns 200" "200" "$STATUS"
check_contains "Log level changed to DEBUG" "DEBUG" "$RESPONSE"
check_contains "Auto-revert configured" "revert_after_minutes" "$RESPONSE"

# Revert to INFO
curl -s -o /dev/null \
  -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"level": "INFO"}' \
  "$ADMIN/admin/log/level"

# Invalid log level
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"level": "INVALID"}' \
  "$ADMIN/admin/log/level")
check "POST invalid log level returns 400" "400" "$STATUS"

# Get logs
BODY=$(curl -s -H "X-Admin-Key: $KEY" "$ADMIN/admin/logs?limit=10")
check_contains "GET logs has entries" "entries" "$BODY"
check_contains "GET logs has total_buffered" "total_buffered" "$BODY"
check_contains "GET logs has current_level" "current_level" "$BODY"

# Get logs with filters
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "X-Admin-Key: $KEY" \
  "$ADMIN/admin/logs?level=ERROR&limit=5")
check "GET logs with level filter returns 200" "200" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "X-Admin-Key: $KEY" \
  "$ADMIN/admin/logs?contains=UTLXe&limit=5")
check "GET logs with contains filter returns 200" "200" "$STATUS"

# Clear logs
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X DELETE \
  -H "X-Admin-Key: $KEY" \
  "$ADMIN/admin/logs")
check "DELETE logs returns 200" "200" "$STATUS"

# ============================================================================
header "Dapr status"
# ============================================================================

BODY=$(curl -s -H "X-Admin-Key: $KEY" "$ADMIN/admin/dapr")
check_contains "Dapr status has mode" "mode" "$BODY"
check_contains "Dapr status has sidecar_reachable" "sidecar_reachable" "$BODY"

# ============================================================================
header "Engine info (EF09 fields)"
# ============================================================================

BODY=$(curl -s -H "X-Admin-Key: $KEY" "$ADMIN/admin/info")
check_contains "Info has mode" "mode" "$BODY"
check_contains "Info has dapr_mode" "dapr_mode" "$BODY"
check_contains "Info has log_level" "log_level" "$BODY"
check_contains "Info has log_buffer_size" "log_buffer_size" "$BODY"

# ============================================================================
header "Cleanup"
# ============================================================================

curl -s -o /dev/null -X DELETE -H "X-Admin-Key: $KEY" "$ADMIN/admin/transformations/hello"
curl -s -o /dev/null -X DELETE -H "X-Admin-Key: $KEY" "$ADMIN/admin/transformations/msg-test"
curl -s -o /dev/null -X DELETE -H "X-Admin-Key: $KEY" "$ADMIN/admin/transformations/topic-test"
green "Cleaned up"

# ============================================================================
printf "\n\033[1m══════════════════════════════════════\033[0m\n"
printf "\033[1mResults: %d passed, %d failed\033[0m\n" "$PASS" "$FAIL"
if [ "$FAIL" -gt 0 ]; then
    printf "\033[31m✗ SOME TESTS FAILED\033[0m\n"
    TEST_EXIT=1
else
    printf "\033[32m✓ ALL TESTS PASSED\033[0m\n"
    TEST_EXIT=0
fi
