= Quality Assurance and Testing

A transformation that produces wrong output is worse than one that crashes — because wrong output travels downstream silently. The order with a missing VAT field gets invoiced without tax. The patient record with a swapped first/last name enters the hospital system. The payment with a wrong decimal position moves 100x the intended amount.

UTL-X's testing strategy is designed to catch these errors before they reach production, with five layers from unit tests to black-box conformance testing.

== The Five Testing Layers

UTL-X uses layered testing — each layer catches different classes of bugs:

```
Layer 5: Conformance Suite (Python, 500+ tests)
  └── Layer 4: Integration Tests (Kotlin)
       └── Layer 3: Module Tests (Kotlin)
            └── Layer 2: Format Tests (Kotlin)
                 └── Layer 1: Unit Tests (Kotlin)
```

Each outer layer depends on the inner layers passing. If a unit test fails, everything above it is suspect.

== Layer 1: Unit Tests

Test individual functions and classes in isolation:

- UDM type construction, access, and mutation
- Scalar operations (asString, asNumber, asBoolean, type coercion)
- Parser edge cases (special characters, Unicode, empty input, deeply nested structures)
- Each stdlib function (652 functions, each with at least one test)

```bash
./gradlew :modules:core:test
```

These run in milliseconds and catch logic errors in individual components. If `toNumber("1.234,56")` returns the wrong value, a unit test catches it instantly.

== Layer 2: Format Tests

Test each format parser and serializer independently. Every format module has its own test suite:

- *JSON:* parser (BOM, precision, special characters), serializer (pretty/compact, writeAttributes)
- *XML:* parser (namespaces, attributes, CDATA, encoding), serializer (element ordering, escaping)
- *CSV:* parser (delimiters, quoting, headers, regional numbers), serializer (regional formats, BOM output)
- *YAML:* parser (anchors, aliases, multi-document, timestamps), serializer (block/flow style)
- *OData:* parser (annotation extraction, collection unwrapping), serializer (metadata levels)
- *Schema formats:* XSD (pattern detection), JSON Schema (draft versions), Avro, Protobuf, EDMX, Table Schema — each with parse/serialize/round-trip tests

```bash
./gradlew :formats:json:test    # one format
./gradlew :formats:test          # all formats
```

These catch format-specific regressions. When the XML `_text` serialization leak was fixed, format tests caught the behavior change immediately.

== Layer 3: Module Tests

Test cross-cutting concerns within a module:

- *Core:* interpreter tests (expression evaluation, function calls, let bindings, closures), type system tests, parser tests (grammar edge cases)
- *CLI:* command parsing, expression mode (`-e`), identity mode (format flip), multi-input handling
- *Engine:* strategy tests (TEMPLATE, COPY, COMPILED produce identical results), worker pool tests, back-pressure tests
- *Daemon:* LSP protocol tests, live preview tests

```bash
./gradlew :modules:cli:test
./gradlew :modules:engine:test
```

These catch interaction bugs — where two components work individually but fail together.

== Layer 4: Integration Tests

Test end-to-end flows through multiple modules:

- Input parsing → transformation → output serialization (the full pipeline)
- Multi-format tests: XML in → JSON out, CSV in → YAML out, OData in → XML out
- Schema validation integration: load schema → validate → transform → validate
- Multi-input tests: two inputs with different formats → merged output
- UTLXe transport tests: HTTP endpoint, gRPC, stdio-proto — same transformation, same result

```bash
./gradlew test    # all modules
```

These catch end-to-end regressions — the parser produces valid UDM, the interpreter evaluates correctly, but the serializer mishandles a specific UDM shape.

== Layer 5: Conformance Suite

The most important testing layer. The conformance suite is an external test runner (Python) that tests the CLI as a *black box* — exactly like a user would.

=== Why Python, Not Kotlin?

The conformance suite deliberately runs outside the JVM:

- Tests the *binary* (native or JAR) — the actual artifact users install
- No internal access — tests what the user sees, not internal state
- Language-independent — could test a future WASM or Rust implementation of UTL-X
- Easy to extend — add a YAML file, no compilation needed

=== Test Format

Each test is a YAML file with input, transformation, and expected output:

```yaml
name: "xml_to_json_passthrough"
input:
  format: xml
  data: "<Order><Customer>Alice</Customer></Order>"
transformation: |
  %utlx 1.0
  input xml
  output json
  ---
  $input
expected:
  format: json
  data: '{"Order":{"Customer":"Alice"}}'
```

500+ tests cover:
- *Formats:* every format pair (XML→JSON, JSON→CSV, YAML→XML, etc.)
- *Stdlib:* function behavior (string operations, math, date/time, arrays, objects)
- *Examples:* real-world transformations (orders, invoices, IDoc, FHIR)
- *Multi-input:* named inputs from different formats
- *Error handling:* parse errors, validation failures, edge cases
- *Schema:* XSD, JSON Schema, Avro, Protobuf parse/serialize/round-trip
- *Regional:* CSV with European, French, Swiss number formatting

=== Running the Suite

```bash
cd conformance-suite
python3 utlx/runners/cli-runner/simple-runner.py
```

Output shows pass/fail per test with details on failures:

```
Running 470 tests...
[PASS] xml_to_json_passthrough
[PASS] csv_orders_grouping_semicolon
[FAIL] json_to_csv_regional_french
  Expected: "1 234,56"
  Got:      "1234.56"
...
467/470 passed, 3 failed
```

== Auto-Capture: Recording Tests from Usage

UTL-X can automatically record test cases from real CLI usage:

```bash
utlx --capture transform order-transform.utlx order.xml
```

This runs the transformation normally AND saves a conformance test YAML file with the input, transformation, and actual output. The test is saved to `conformance-suite/utlx/tests/auto-captured/`.

#block(
  fill: rgb("#FFF3E0"),
  inset: 12pt,
  radius: 4pt,
  width: 100%,
)[
  *Warning: auto-captured tests cement current behavior.* They validate what IS, not what SHOULD BE. If the output has a bug, the test captures the bug as "expected" — and the test passes, hiding the bug. Always review auto-captured tests manually before trusting them. This was learned the hard way: auto-captured tests had `_text` in expected output, which cemented the bug as "correct" behavior.
]

Auto-capture is valuable for:
- Building regression tests from production data (sanitized)
- Documenting existing behavior before refactoring
- Onboarding: capture a senior developer's transformations as test cases

== UTLXe Engine Tests

The engine has its own conformance suite testing production-specific behavior:

- *Transport parity:* same test against HTTP, gRPC, and stdio-proto — must produce identical results
- *Strategy parity:* same test against TEMPLATE, COPY, and COMPILED — must produce identical results
- *Throughput tests:* verify 86K+ messages/second under sustained load
- *Batch tests:* 100+ instances per schema to catch edge cases in real data
- *Validation tests:* pre/post validation with all 7 schema validators

Transport parity and strategy parity are the most important engine tests. If COMPILED produces a different result than TEMPLATE for the same transformation, it's a bytecode generation bug — and these are the hardest bugs to find without automated testing.

== Test-Driven Transformation Development

For production transformations, write the test first:

+ *Define the contract:* write the expected output for a representative input
+ *Write the transformation:* make the test pass
+ *Add edge cases:* null fields, empty arrays, missing properties, wrong types
+ *Add error cases:* invalid input, schema violations, boundary values
+ *Run the conformance suite:* verify no regressions in other transformations

```yaml
# Step 1: Write this test file FIRST
name: "dynamics365_order_to_ubl_invoice"
input:
  format: json
  data: '{"ordernumber": "ORD-001", "totalamount": 299.99, ...}'
transformation: |
  %utlx 1.0
  input odata
  output xml
  ---
  ...
expected:
  format: xml
  data: '<Invoice xmlns="urn:oasis:..."><cbc:ID>ORD-001</cbc:ID>...</Invoice>'
```

Then write the `.utlx` file to make it pass. This inverts the common "write code, then test" pattern — the test IS the specification.

== Continuous Integration

UTL-X uses GitHub Actions for automated testing on every push and PR:

=== CLI Pipeline (`cli-ci.yml`)

+ Build JAR (Kotlin/Gradle)
+ Run Kotlin tests (layers 1-4)
+ Build native binary (GraalVM)
+ Run conformance suite against native binary (layer 5)
+ Performance benchmark (detect regressions)

=== Release Pipeline (`release.yml`)

+ All CI steps above
+ Build native binaries for 3 platforms (macOS, Linux, Windows)
+ Build Docker image
+ Run conformance suite against Docker image
+ Publish artifacts (GitHub Releases, Homebrew, Chocolatey, Docker registry)

=== Pull Request Checks

Every PR must pass:
- All Kotlin tests (layers 1-4)
- Full conformance suite (layer 5)
- No new test failures (regressions blocked)

This ensures that no code reaches the main branch without passing 500+ tests across all formats, functions, and patterns.
