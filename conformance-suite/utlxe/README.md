# UTLXe Engine Conformance Suite

End-to-end transformation tests for the UTLXe production engine. Tests run against a real UTLXe subprocess communicating via varint-delimited protobuf (stdio-proto) or line-delimited JSON (stdio-json).

## Running

```bash
# Build the UTLXe JAR first
./gradlew :modules:engine:jar

# Run all tests (stdio-proto mode, default)
export UTLXE_JAR_PATH=modules/engine/build/libs/utlxe-1.0.0-SNAPSHOT.jar
python3 conformance-suite/utlxe/runners/engine-runner.py

# Run specific category
python3 conformance-suite/utlxe/runners/engine-runner.py single-input
python3 conformance-suite/utlxe/runners/engine-runner.py throughput

# Run with multiple workers (concurrent execution)
python3 conformance-suite/utlxe/runners/engine-runner.py throughput --workers 4

# Run specific test (verbose)
python3 conformance-suite/utlxe/runners/engine-runner.py single-input identity_json -v

# Run in stdio-json mode (requires bundle)
python3 conformance-suite/utlxe/runners/engine-runner.py --mode stdio-json --bundle /path/to/bundle
```

## Requirements

- Java 17+ (for UTLXe JVM subprocess)
- Python 3.8+
- `pip install pyyaml`

## Test Categories

| Category | Tests | Description |
|----------|-------|-------------|
| `single-input/` | 5 | JSON identity, restructuring, filter/map, let bindings, conditionals |
| `format-conversion/` | 4 | JSON→XML, JSON→CSV, JSON→YAML, XML→JSON |
| `multi-input/` | 2 | JSON+JSON merge, cross-format JSON+XML→JSON |
| `batch/` | 1 | Batch execution with correlation IDs |
| `error-handling/` | 2 | Invalid source, missing transformation |
| `throughput/` | 5 | Burst tests with timing metrics (25-100 messages) |

**Total: 19 tests**

## Transport Modes Tested

| Mode | How | When |
|------|-----|------|
| `stdio-proto` (default) | Varint-delimited protobuf over stdin/stdout | All tests |
| `stdio-proto` + workers | Same, with `--workers N` for concurrent execution | Throughput tests |
| `stdio-json` | Line-delimited JSON over stdin/stdout (bundle required) | Via `--mode stdio-json --bundle` |

## Throughput Tests

Throughput tests send bursts of messages and report timing metrics:

```
  ✓ burst_identity_25
    25 msgs in 21.16ms (1181.5 msg/s) | p50=0.2ms p95=5.38ms p99=8.53ms | avg=0.85ms
  ✓ burst_batch_100
    100 msgs in 17.9ms (5588.0 msg/s) | avg=0.18ms
  ✓ burst_sequential_50
    50 msgs in 14.0ms (3571.5 msg/s) | p50=0.24ms p95=0.49ms p99=0.76ms | avg=0.28ms
```

Metrics reported:
- **Total time** — wall clock for the entire burst
- **Throughput** — messages per second
- **p50/p95/p99** — latency percentiles (sequential mode)
- **avg/min/max** — per-message timing

### Throughput test format

```yaml
name: "burst_identity_25"
category: "throughput"

transformation: |
  %utlx 1.0
  input json
  output json
  ---
  $input

burst:
  count: 25                    # Number of messages in the burst
  mode: "sequential"           # "sequential" (individual requests) or "batch" (ExecuteBatch)
  payload_template: |          # Template with {{INDEX}}, {{AGE}}, {{QTY}} placeholders
    {"id": {{INDEX}}, "value": "msg-{{INDEX}}"}
  content_type: "application/json"

throughput_limits:             # Optional — fail if exceeded
  max_total_ms: 5000
  max_p95_ms: 100
```

## Test Formats

### Single input
```yaml
input:
  format: json
  data: |
    {"key": "value"}
transformation: |
  %utlx 1.0
  input json
  output json
  ---
  { result: $input.key }
expected:
  format: json
  data: |
    {"result": "value"}
```

### Multi-input
```yaml
inputs:
  customer:
    format: json
    data: |
      {"name": "Contoso"}
  order:
    format: json
    data: |
      {"id": "ORD-001"}
transformation: |
  %utlx 1.0
  input: customer json, order json
  output json
  ---
  { customer: $customer.name, order: $order.id }
```

### Batch
```yaml
batch_items:
  - correlation_id: "msg-001"
    format: json
    data: '{"name": "alice"}'
    expected:
      format: json
      data: '{"name": "ALICE"}'
```

### Error tests
```yaml
transformation: |
  invalid utlx
load_error_expected: true
```

## How It Works

The Python runner (`engine-runner.py`):

1. Spawns UTLXe as subprocess: `java -jar utlxe.jar --mode stdio-proto --workers N`
2. Sends `HealthRequest` to confirm readiness (~2s JVM startup)
3. For each test: `LoadTransformationRequest` + `ExecuteRequest` via varint-delimited protobuf
4. For throughput tests: fires burst of N messages, collects per-message timings
5. Compares outputs (JSON structural, XML normalized, CSV line-by-line, YAML structural)
6. Reports pass/fail/skip with diffs and throughput metrics

The runner implements the protobuf wire format manually — no generated stubs needed.
