# Plan: B20 — Non-UTF-8 Encoding Support (Core + Formats)

## Context

The entire UTL-X payload pipeline uses Java `String` as internal representation. The original byte encoding is lost at the entry point. All parsing, transformation, and serialization works with UTF-16 String and re-encodes to UTF-8 on output. This silently corrupts non-UTF-8 content (UTF-16 SAP, ISO-8859-1 legacy CSV, Shift-JIS). Blocks SAP/BizTalk integration.

**Branch:** `uat/B20-binary` (off main)
**Scope:** Core + format parsers/serializers only (engine changes are EB01, separate)

## Current call chain (String-based)

```
bytes → String (encoding lost) → Parser(String) → UDM → Interpreter → UDM → Serializer → String → bytes
```

## Target call chain (ByteArray-based)

```
bytes → Parser(ByteArray, charset?) → UDM → Interpreter → UDM → Serializer(encoding?) → ByteArray
```

## Changes — 5 phases

### Phase 1: PayloadBytes data class (core)

**File:** `modules/core/src/main/kotlin/org/apache/utlx/core/udm/udm_core.kt`

Add:
```kotlin
data class PayloadBytes(
    val bytes: ByteArray,
    val detectedCharset: Charset? = null,  // from Content-Type, BOM, or format-specific detection
    val contentType: String = ""           // "application/xml", "application/json", etc.
)
```

### Phase 2: Format parsers — add ByteArray constructors

All parsers currently accept `String` or `Reader`. Add `ByteArray`/`InputStream` constructors that do charset detection per format rules.

| Parser | File | Current | Add |
|--------|------|---------|-----|
| **JSONParser** | `formats/json/.../json_parser.kt` (line 24) | `JSONParser(json: String)` | `JSONParser(bytes: ByteArray)` — verify UTF-8 per RFC 8259, strip BOM |
| **XMLParser** | `formats/xml/.../xml_parser.kt` (line 43) | `XMLParser(xml: String, ...)` | `XMLParser(bytes: ByteArray, ...)` — pass `ByteArrayInputStream` to SAX, auto-detects encoding from BOM + declaration |
| **CSVParser** | `formats/csv/.../csv_parser.kt` (line 47) | `CSVParser(csv: String, dialect)` | `CSVParser(bytes: ByteArray, charset: Charset?, dialect)` — use provided charset or default UTF-8 |
| **YAMLParser** | `formats/yaml/.../YAMLParser.kt` (line 56) | `parse(yamlString: String)` already has `parse(input: InputStream)` | Already has InputStream overload — just needs BOM-aware charset detection before creating reader |

| **ODataJSONParser** | `formats/odata/.../ODataJSONParser.kt` (line 22) | `ODataJSONParser(content: String, options)` | `ODataJSONParser(bytes: ByteArray, options)` — JSON-based, always UTF-8 |

Schema parsers (XSD, JSON Schema, Avro, Protobuf) delegate to XML/JSON parsers — they'll get ByteArray support for free.

**Key insight for XML:** Java's SAX/DOM parser auto-detects UTF-8, UTF-16LE, UTF-16BE from BOM and `<?xml encoding="...">` when given a `ByteArrayInputStream`. This is the whole point — pass raw bytes, let the XML parser do what it's done for 25 years.

### Phase 3: Format serializers — add ByteArray output + encoding option

| Serializer | File | Current | Add |
|------------|------|---------|-----|
| **JSONSerializer** | `formats/json/.../json_serializer.kt` | `serialize(udm): String` | `serializeToBytes(udm): ByteArray` — always UTF-8 per RFC 8259 |
| **XMLSerializer** | `formats/xml/.../xml_serializer.kt` | `serialize(udm): String`, already has `outputEncoding` constructor param | `serializeToBytes(udm): ByteArray` — use `outputEncoding` (from `{encoding: "UTF-16"}` format option) to encode |
| **CSVSerializer** | `formats/csv/.../csv_serializer.kt` | `serialize(udm): String` | `serializeToBytes(udm, charset): ByteArray` — use charset or default UTF-8 |
| **YAMLSerializer** | `formats/yaml/.../YAMLSerializer.kt` | Already has `serialize(udm, output: OutputStream)` | Already supports OutputStream — add charset-aware wrapper |
| **ODataJSONSerializer** | `formats/odata/.../ODataJSONSerializer.kt` | `serialize(udm): String` | `serializeToBytes(udm): ByteArray` — always UTF-8 |

### Phase 4: TransformationService — wire ByteArray through

**File:** `modules/cli/src/main/kotlin/org/apache/utlx/cli/service/TransformationService.kt`

Changes:
- `InputData` (line 76): add `bytes: ByteArray?` field alongside existing `content: String`
- `parseInput()` (line 212): if `bytes` is set, pass to parser's ByteArray constructor; if only `content` is set, use existing String path (backward compat)
- `parseInputPublic()`: add overload accepting `ByteArray`
- `serializeOutput()` (line 293): add `serializeOutputToBytes()` variant that returns `ByteArray` with encoding from format spec
- Format options already support `{encoding: "UTF-16"}` — `formatSpec.options["encoding"]` is already read by XML serializer (line 297)

### Phase 5: CLI — read raw bytes

**File:** `modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/TransformCommand.kt`

Changes:
- `File.readText()` (lines 209, 284) → `File.readBytes()` when format supports non-UTF-8
- Add `--charset` flag for explicit charset override on stdin/file input
- Default behavior unchanged: UTF-8 assumed unless charset detected or specified

## Files to modify (complete list)

| File | Change |
|------|--------|
| `modules/core/.../udm/udm_core.kt` | Add `PayloadBytes` data class |
| `formats/json/.../json_parser.kt` | Add `ByteArray` constructor |
| `formats/xml/.../xml_parser.kt` | Add `ByteArray` constructor (pass to SAX as InputStream) |
| `formats/csv/.../csv_parser.kt` | Add `ByteArray` + `Charset` constructor |
| `formats/yaml/.../YAMLParser.kt` | Enhance existing `InputStream` path with charset detection |
| `formats/json/.../json_serializer.kt` | Add `serializeToBytes()` |
| `formats/xml/.../xml_serializer.kt` | Add `serializeToBytes()` using `outputEncoding` |
| `formats/csv/.../csv_serializer.kt` | Add `serializeToBytes()` with charset |
| `formats/yaml/.../YAMLSerializer.kt` | Add charset-aware `serializeToBytes()` |
| `formats/odata/.../ODataJSONParser.kt` | Add `ByteArray` constructor |
| `formats/odata/.../ODataJSONSerializer.kt` | Add `serializeToBytes()` |
| `modules/cli/.../service/TransformationService.kt` | `InputData` gets `bytes` field, `parseInput` uses ByteArray path, `serializeOutputToBytes()` added |
| `modules/cli/.../commands/TransformCommand.kt` | `readBytes()` option, `--charset` flag |

**NOT in scope (EB01):**
- Engine strategies (`CompiledStrategy`, `TemplateStrategy`, `CopyStrategy`)
- Transport handlers (`TransportHandlers.kt`)
- `HttpTransport`, `StdioProtoTransport`, `GrpcTransport`
- `ValidationOrchestrator`

## Backward compatibility

- All existing `String` constructors/methods stay — ByteArray is additive
- `InputData.content` stays for callers using String
- Default encoding remains UTF-8 everywhere
- All 517 conformance tests must pass unchanged (they use UTF-8)

## Verification

1. `./gradlew build -x test` — compiles (all modules)
2. `./gradlew test` — all existing tests pass (no regressions)
3. New tests per format:
   - XML: parse UTF-16LE bytes with BOM → correct UDM
   - XML: parse UTF-16BE bytes → correct UDM
   - XML: serialize with `{encoding: "UTF-16"}` → UTF-16 bytes output
   - CSV: parse ISO-8859-1 bytes with charset hint → correct UDM (ä, ö, ü preserved)
   - JSON: parse UTF-8 bytes → same as String path
   - JSON: reject non-UTF-8 bytes → error
   - Round-trip: UTF-16 XML in → transform → UTF-16 XML out
4. Conformance suite: `cd conformance-suite && python3 utlx/runners/cli-runner/simple-runner.py` — 517/517 pass
