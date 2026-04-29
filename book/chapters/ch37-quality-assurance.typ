= Quality Assurance and Testing

== UTL-X's Layered Testing Strategy
// Like a matryoshka (Russian nesting doll), UTL-X tests are layered:
//
// ┌─────────────────────────────────────────────┐
// │ Layer 5: Conformance Suite (Python, 453+ tests) │
// │  ┌─────────────────────────────────────────┐ │
// │  │ Layer 4: Integration Tests (Kotlin)      │ │
// │  │  ┌─────────────────────────────────────┐ │ │
// │  │  │ Layer 3: Module Tests (Kotlin)       │ │ │
// │  │  │  ┌─────────────────────────────────┐ │ │ │
// │  │  │  │ Layer 2: Format Tests (Kotlin)   │ │ │ │
// │  │  │  │  ┌─────────────────────────────┐ │ │ │ │
// │  │  │  │  │ Layer 1: Unit Tests (Kotlin) │ │ │ │ │
// │  │  │  │  └─────────────────────────────┘ │ │ │ │
// │  │  │  └─────────────────────────────────┘ │ │ │
// │  │  └─────────────────────────────────────┘ │ │
// │  └─────────────────────────────────────────┘ │
// └─────────────────────────────────────────────┘

== Layer 1: Unit Tests (Kotlin)
// - Test individual functions in isolation
// - Located: each module's src/test/kotlin/
// - Examples:
//   - UDM type construction and access
//   - Scalar operations (asString, asNumber, asBoolean)
//   - Parser edge cases (special characters, unicode, empty input)
// - Run: ./gradlew :modules:core:test

== Layer 2: Format Tests (Kotlin)
// - Test each format parser and serializer independently
// - Located: formats/*/src/test/kotlin/
// - Each format module has its own test suite:
//   - formats/json: JSONParser, JSONSerializer, writeAttributes tests
//   - formats/xml: XMLParser, XMLSerializer, namespace tests
//   - formats/yaml: YAMLParser, YAMLSerializer, multi-document tests
//   - formats/csv: CSVParser, CSVSerializer, regional format tests
//   - formats/xsd: XSD parser, pattern detection tests
//   - formats/avro, protobuf, odata, jsch, osch, tsch: each with own tests
// - Run: ./gradlew :formats:json:test (per format)

== Layer 3: Module Tests (Kotlin)
// - Test cross-cutting concerns within a module
// - modules/core: interpreter tests, type system tests
// - modules/cli: command parsing, expression mode, identity mode
// - modules/engine: strategy tests (TEMPLATE, COPY, COMPILED)
// - modules/daemon: LSP protocol tests
// - Run: ./gradlew :modules:cli:test

== Layer 4: Integration Tests (Kotlin)
// - Test end-to-end flows through multiple modules
// - Input parsing → transformation → output serialization
// - Multi-format tests: XML in → JSON out, CSV in → YAML out
// - Schema validation integration: load schema → validate → transform → validate
// - UTLXe transport tests: HTTP endpoint, gRPC, Dapr input
// - Run: ./gradlew test (all modules)

== Layer 5: Conformance Suite (Python)
// - External test runner (Python, not Kotlin)
// - Tests the CLI as a BLACK BOX (no internal access)
// - 453+ test cases in YAML format:
//   - Input data + transformation script + expected output
//   - Categories: formats, stdlib, examples, multi-input, error-handling
// - WHY Python (not Kotlin)?
//   - Tests the BINARY (native or JAR) — exactly what users run
//   - No Kotlin/JVM dependency in the test runner
//   - Can test against ANY UTL-X implementation (future: WASM, native)
//   - Easy to add tests (YAML files, no compilation needed)
//
// Run: cd conformance-suite && python3 utlx/runners/cli-runner/simple-runner.py
//
// Each test is a YAML file:
//   name: "xml_to_json_passthrough"
//   input:
//     format: xml
//     data: "<Order><Customer>Alice</Customer></Order>"
//   transformation: |
//     %utlx 1.0
//     input xml
//     output json
//     ---
//     $input
//   expected:
//     format: json
//     data: '{"Order":{"Customer":"Alice"}}'

== Auto-Capture: Recording Tests from CLI Usage
// - UTL-X CLI can automatically capture test cases from real usage
// - Enable: export UTLX_CAPTURE=true (environment variable)
// - Or: utlx --capture transform script.utlx input.xml
// - What it records:
//   - Input data (format + content)
//   - Transformation source
//   - Output (format + content)
//   - Performance (duration, memory)
// - Saved as: YAML test file in conformance-suite/utlx/tests/auto-captured/
// - WARNING: auto-captured tests cement the current behavior (see B14 lesson)
//   - ALWAYS review auto-captured tests manually
//   - They validate what IS, not what SHOULD BE
//   - Bug in output → captured as "expected" → test passes → bug hidden
// - Disable: export UTLX_CAPTURE=false (or utlx --no-capture)

== UTLXe Engine Tests
// - Separate conformance suite for the engine (development branch)
// - Tests all transport modes: HTTP, gRPC, stdio-proto
// - Tests all strategies: TEMPLATE, COPY, COMPILED
// - Transport parity: same test run against all transports → same result
// - Strategy parity: same test run against all strategies → same result
// - Throughput tests: verify 86K+ msg/s under load
// - Batch tests: 100+ instances per schema

== Test-Driven Transformation Development
// - Write the test FIRST (input + expected output)
// - Write the .utlx transformation to make the test pass
// - Add edge cases: null fields, empty arrays, missing properties
// - Add error cases: invalid input, schema violations
// - Run conformance suite to verify no regressions
// - This is how production transformations should be developed.

== Continuous Integration
// - GitHub Actions: cli-ci.yml
//   - Build JAR
//   - Run Kotlin tests (all layers 1-4)
//   - Build native binary
//   - Run integration tests against native binary
//   - Performance benchmark
// - Conformance suite: runs on every PR
// - Native build: runs on release (3 platforms)
