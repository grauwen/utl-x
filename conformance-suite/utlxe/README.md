# UTLXe Engine Conformance Suite

End-to-end transformation tests for the UTLXe production engine. Tests are run against a real UTLXe subprocess communicating via varint-delimited protobuf (stdio-proto mode).

## Running

```bash
# Build the UTLXe JAR first
./gradlew :modules:engine:jar

# Run all tests
export UTLXE_JAR_PATH=modules/engine/build/libs/utlxe-1.0.0-SNAPSHOT.jar
python3 conformance-suite/utlxe/runners/engine-runner.py

# Run specific category
python3 conformance-suite/utlxe/runners/engine-runner.py single-input

# Run specific test (verbose)
python3 conformance-suite/utlxe/runners/engine-runner.py single-input identity_json -v
```

## Requirements

- Java 17+ (for UTLXe JVM subprocess)
- Python 3.8+
- `pip install pyyaml` (protobuf library optional — runner uses raw wire format)

## Test Categories

| Category | Tests | Description |
|----------|-------|-------------|
| `single-input/` | 5 | JSON identity, restructuring, filter/map, let bindings, conditionals |
| `format-conversion/` | 4 | JSON→XML, JSON→CSV, JSON→YAML, XML→JSON |
| `multi-input/` | 2 | Two JSON inputs merged, cross-format (JSON+XML) |
| `batch/` | 1 | Batch execution with correlation IDs |
| `error-handling/` | 2 | Invalid source, missing transformation |

## Test Format (YAML)

Same format as the CLI conformance suite (`conformance-suite/utlx/`):

```yaml
name: "test_name"
category: "single-input"
description: "What this test validates"
tags: ["json", "stdlib"]

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

### Multi-input tests

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

### Batch tests

```yaml
transformation: |
  %utlx 1.0
  input json
  output json
  ---
  { name: upperCase($input.name) }

batch_items:
  - correlation_id: "msg-001"
    format: json
    data: |
      {"name": "alice"}
    expected:
      format: json
      data: |
        {"name": "ALICE"}
```

### Error tests

```yaml
transformation: |
  invalid utlx
load_error_expected: true
```

### Skipped tests

```yaml
skip: true
skip_reason: "Feature not yet implemented"
```

## How It Works

The Python runner (`engine-runner.py`):

1. Spawns UTLXe as subprocess: `java -jar utlxe.jar --mode stdio-proto --workers 1`
2. Sends `HealthRequest` to confirm readiness
3. For each test: sends `LoadTransformationRequest` + `ExecuteRequest` via varint-delimited protobuf
4. Compares actual output against expected output (JSON structural, XML normalized, CSV line-by-line, YAML structural)
5. Reports pass/fail/skip with diffs on failure

The runner implements the protobuf wire format manually (no generated stubs needed) — it only requires the `pyyaml` pip package.
