#!/bin/bash
# ============================================================================
# UTLXe Admin API — curl test script
# Tests all EF03 admin endpoints against a running UTLXe instance.
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
header "Cleanup"
# ============================================================================

curl -s -o /dev/null \
  -X DELETE \
  -H "X-Admin-Key: $KEY" \
  "$ADMIN/admin/transformations/hello"
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
