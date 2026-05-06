# Local Dapr Testing for UTLXe

Test UTLXe with Dapr on your local machine — no Azure subscription needed.

## Prerequisites

```bash
# Install Dapr CLI
brew install dapr/tap/dapr-cli

# Initialize Dapr (starts Redis + Zipkin in Docker)
dapr init

# Verify
dapr version
docker ps   # should show dapr_redis and dapr_zipkin containers
```

## Step 1: Build UTLXe

```bash
cd /Users/magr/data/mapping/github-git/utl-x
./gradlew :modules:engine:jar
```

## Step 2: Start UTLXe with Dapr sidecar

```bash
dapr run \
  --app-id utlxe \
  --app-port 8085 \
  --app-protocol http \
  --dapr-http-port 3500 \
  --resources-path deploy/dapr/local \
  -- java -jar modules/engine/build/libs/utlxe-*.jar --mode http
```

This starts:
- UTLXe on port 8085 (data) and 8081 (admin)
- Dapr sidecar on port 3500 (HTTP API)
- Redis bindings for `orders-in` and `orders-out`

## Step 3: Upload a transformation

In another terminal:

```bash
# Set admin key (if UTLXE_ADMIN_KEY is set)
export KEY="test"

# Upload the test transformation
curl -X POST \
  -H "X-Admin-Key: $KEY" \
  -F "source=@deploy/dapr/local/test-transformation.utlx" \
  http://localhost:8081/admin/transformations/orders-in
```

## Step 4: Test the transformation

```bash
curl -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-001","customerName":"Acme Corp","quantity":5,"unitPrice":24.50}' \
  http://localhost:8081/admin/transformations/orders-in/test
```

Expected:
```json
{
  "status": "ok",
  "output": {
    "processedOrderId": "PROC-ORD-001",
    "customer": "ACME CORP",
    "total": 122.5,
    "processedAt": "2026-05-06T..."
  }
}
```

## Step 5: Send a message via Dapr (simulates Service Bus)

```bash
# Send via Dapr's binding API — simulates a message arriving on a queue
curl -X POST \
  http://localhost:3500/v1.0/bindings/orders-in \
  -H "Content-Type: application/json" \
  -d '{
    "data": {"orderId":"ORD-002","customerName":"Contoso","quantity":3,"unitPrice":50.00},
    "operation": "create"
  }'
```

This sends a message through the Dapr input binding → UTLXe transforms it → result goes to the `orders-out` output binding.

## Step 6: Direct HTTP (no Dapr)

```bash
# Bypass Dapr — call UTLXe directly
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-003","customerName":"Fabrikam","quantity":10,"unitPrice":15.00}' \
  http://localhost:8085/api/transform/orders-in
```

## Step 7: Check health and bindings

```bash
# UTLXe health
curl http://localhost:8081/health

# Dapr sidecar health
curl http://localhost:3500/v1.0/healthz

# List transformations
curl -H "X-Admin-Key: $KEY" http://localhost:8081/admin/transformations

# Check Dapr binding matches (after EF05 is implemented)
curl -H "X-Admin-Key: $KEY" http://localhost:8081/admin/dapr/bindings
```

## Step 8: View traces (optional)

Dapr starts Zipkin at http://localhost:9411. Open it in a browser to see distributed traces across the Dapr → UTLXe → Dapr flow.

## Cleanup

```bash
# Stop UTLXe + Dapr
Ctrl+C in the dapr run terminal

# Or stop by app-id
dapr stop utlxe
```

## What this tests

| Scenario | How to test |
|----------|-------------|
| Dapr OPTIONS startup probe | Automatic — check dapr run logs for "app]" entries |
| Input binding delivery | Step 5 — send via Dapr binding API |
| Output binding forwarding | Step 5 — check Redis for output (or add logging) |
| Direct HTTP (no Dapr) | Step 6 — call UTLXe directly |
| Admin API upload | Step 3 — upload transformation |
| Test endpoint | Step 4 — test with sample input |
| Health probes | Step 7 |
| Distributed tracing | Step 8 — Zipkin UI |

## Switching to Azure Service Bus

Replace the Redis component YAMLs with Azure Service Bus YAMLs:

```yaml
# Replace input-orders.yaml with:
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: orders-in
spec:
  type: bindings.azure.servicebusqueues
  version: v1
  metadata:
    - name: connectionString
      value: "Endpoint=sb://..."
    - name: queueName
      value: "incoming-orders"
    - name: route
      value: "/orders-in"
```

Same UTLXe, same transformation, different infrastructure — this is the Dapr portability story.
