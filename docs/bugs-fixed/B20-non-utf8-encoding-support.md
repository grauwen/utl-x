# B20: Non-UTF-8 Encoding Support (Core + Formats)

**Status:** Open (must-fix before SAP integration)  
**Severity:** High (blocks SAP/BizTalk integration — SAP uses UTF-16 natively)  
**Scope:** Core parser interface, format parsers (XML, CSV, YAML), serializers  
**Companion:** EB01 (engine-side changes — transports, validation, CLI)  
**Created:** May 2026  
**Discovered during:** EF02 validation wiring  
**Elevated:** SAP RFC/IDoc analysis confirms UTF-16 is the native SAP wire encoding

---

## Summary

The entire UTL-X payload pipeline uses Java `String` as the internal representation. The original byte encoding is lost at the entry point (HTTP `receiveText()`, proto `toStringUtf8()`, CLI stdin reader). All subsequent operations — parsing, transformation, validation, serialization — work with Java's internal UTF-16 String representation and re-encode to UTF-8 when bytes are needed.

This is correct for UTF-8 content (JSON, modern XML, most YAML/CSV). It silently corrupts content in non-UTF-8 encodings: UTF-16, ISO-8859-1, Shift-JIS, Windows-1252, and others.

## Affected layers

| Layer | Component | How encoding is lost |
|-------|-----------|---------------------|
| **Engine** | `HttpTransport.receiveText()` | Ktor decodes using Content-Type charset (correct if set, defaults to UTF-8 if not) |
| **Engine** | Proto `payload.toStringUtf8()` | Always assumes UTF-8 — hardcoded in protobuf-java |
| **Engine** | `ValidationOrchestrator` | Re-encodes `input.toByteArray(Charsets.UTF_8)` for validators |
| **Core** | Parser interface | Accepts `String` — encoding already resolved by caller |
| **Core** | CLI `utlx` | Reads stdin using JVM default charset (usually UTF-8) |
| **Formats** | XML parser/serializer | Receives `String`, not raw bytes — cannot honor XML `encoding` declaration on input |
| **Formats** | CSV parser | Receives `String` — no charset detection |

## Root cause

The pipeline was designed with `String` as the universal payload type:

```
Entry point (bytes) → String → Parser → UDM → Interpreter → UDM → Serializer → String → Output (bytes)
```

The byte-to-String conversion at the entry point is a one-way gate. The original encoding information (from Content-Type header, XML declaration, BOM) is discarded. All internal processing operates on Java's UTF-16 String, and output re-encodes to UTF-8.

## Impact

### What works (UTF-8):
- JSON — RFC 8259 mandates UTF-8. All JSON is safe.
- Modern XML — most XML in integration scenarios is UTF-8.
- YAML — typically UTF-8.
- CSV — typically UTF-8 in modern systems.
- Dapr messages — Dapr delivers as UTF-8.
- Proto payloads — defined as UTF-8 in the proto contract.

### What breaks (non-UTF-8):
- **UTF-16 (SAP)** — **This is the critical case.** SAP Unicode systems (almost all modern SAP) use UTF-16LE internally (codepage 4103). IDocs exchanged via tRFC/qRFC, BAPIs over RFC SDK, JCo, NCo all use UTF-16 on the wire. The SAP connectors convert to the host language's native string type (Java UTF-16, .NET UTF-16, Python Unicode), but if raw IDoc files or RFC payloads reach UTLXe directly (via file adapter, Dapr binding, or CPI), they may be UTF-16 encoded.
- **UTF-16 XML** — common in .NET/BizTalk systems and SAP XI/PI exports. XML declaration says `encoding="UTF-16"` but bytes arrive as UTF-8 after re-encoding.
- **ISO-8859-1 CSV** — common in European legacy systems. Characters like ä, ö, ü, é, è may be corrupted.
- **Shift-JIS / EUC-JP** — Japanese SAP systems on non-Unicode codepages (rare but exists). Multi-byte characters corrupted.
- **Windows-1252** — common in older Windows systems. Extended characters (smart quotes, em dashes) corrupted.

### SAP encoding landscape:
| SAP component | Encoding | UTLXe impact |
|---|---|---|
| Unicode ABAP (codepage 4103) | UTF-16LE | **Must support** — this is modern SAP |
| RFC SDK / JCo / NCo | UTF-16 on wire, converted by connector | OK if connector converts to UTF-8 before UTLXe |
| IDoc via tRFC (system-to-system) | UTF-16 | **Must support** if UTLXe receives raw IDoc |
| IDoc via file/middleware | Depends on export settings | May be UTF-8, UTF-16, or ISO-8859-1 |
| SAP CPI (Cloud Integration) | UTF-8 (Camel normalizes) | OK — CPI converts before delivery |
| Non-Unicode ABAP (codepage 1100) | ISO-8859-1 | **Must support** for legacy systems |

### Specific failure scenarios:
1. UTF-16 XML with BOM → `receiveText()` reads correctly (Ktor detects BOM) → String is fine → `toByteArray(UTF_8)` re-encodes → XSD validator receives UTF-8 bytes but XML declaration says UTF-16 → **validation may fail or produce wrong results**
2. ISO-8859-1 CSV → `receiveText()` assumes UTF-8 → bytes decoded incorrectly → **characters corrupted in the String itself** → transformation produces garbage
3. XML response with non-ASCII → serialized as UTF-8 → output XML declaration should say `encoding="UTF-8"` but may not → downstream consumer confused

## Fix design: no configuration switch — auto-detect

The fix does NOT require a configurable encoding switch. Each format has built-in encoding detection:

| Format | How encoding is detected | Java support |
|--------|------------------------|:---:|
| **XML** | BOM (FF FE = UTF-16LE, FE FF = UTF-16BE) + `<?xml encoding="..."?>` declaration | Built into SAX/DOM parser — handles this automatically for 25 years |
| **JSON** | Always UTF-8 (RFC 8259 mandates it) | No detection needed |
| **CSV** | Content-Type `charset` parameter, or BOM, or default UTF-8 | Manual — use provided charset |
| **YAML** | BOM, or default UTF-8 | Manual |

The core change: **pass raw `ByteArray` to the parsers instead of pre-decoding to `String`.** Each parser handles encoding according to its format's rules. UTF-8, UTF-16LE, UTF-16BE, ISO-8859-1 all work automatically.

```
Current (broken):
  bytes → String (encoding lost here) → parser → UDM

Fixed (auto-detect):
  bytes → parser (detects encoding from BOM/declaration/Content-Type) → UDM
```

Java's XML parser reads the BOM and encoding declaration from the raw byte stream. If you feed it `ByteArrayInputStream` with UTF-16 bytes, it auto-detects and works. If you feed it a pre-decoded UTF-8 String, the BOM and declaration are already gone — that's the current bug.

### Input encoding: automatic

No configuration. The parser auto-detects. If the XML has a BOM or `encoding="UTF-16"`, it's handled. If the CSV arrives with `Content-Type: text/csv; charset=iso-8859-1`, the charset is used.

### Output encoding: default UTF-8, configurable per transformation

Output defaults to UTF-8 (the modern standard). If a downstream SAP system expects UTF-16, declare it in the .utlx header:

```
%utlx 1.0
input xml                           // auto-detect input encoding
output xml {encoding: "UTF-16"}     // explicit output encoding for SAP receiver
```

No global switch. Input auto-detects. Output defaults to UTF-8 unless the transformation declares otherwise.

### Implementation: `PayloadBytes` replaces `String`

```kotlin
// Current
fun execute(input: String): ExecutionResult

// Fixed
fun execute(input: PayloadBytes): ExecutionResult

data class PayloadBytes(
    val bytes: ByteArray,
    val detectedCharset: Charset?,   // from Content-Type, BOM, or format-specific detection
    val contentType: String           // "application/xml", "application/json", etc.
)
```

### B20 scope (core + formats only):
- `PayloadBytes` data class in core
- Format parsers (XML, CSV, YAML, JSON) accept `ByteArray` instead of `String`
- XML parser: pass `ByteArrayInputStream` to SAX/DOM (auto-detects encoding from BOM/declaration)
- JSON parser: verify UTF-8, reject non-UTF-8 per RFC 8259
- CSV parser: use charset from `PayloadBytes.detectedCharset` or default UTF-8
- YAML parser: BOM detection or default UTF-8
- Serializers produce `ByteArray` with declared output encoding (default UTF-8)
- `{encoding: "UTF-16"}` format option parsed from .utlx header, passed to serializer
- Interpreter interface: accept `PayloadBytes` for input, produce `PayloadBytes` for output

### Engine-side changes → see EB01:
- `ExecutionStrategy.execute()` accepts `PayloadBytes`
- `ValidationOrchestrator` passes raw bytes to validators
- HTTP transport passes raw request body bytes, not `receiveText()`
- Proto transport uses `payload` bytes field directly (already `bytes` in proto)
- CLI reads raw stdin bytes with optional charset detection
- utlxd (IDE daemon) — same pattern as CLI

### Effort
- **B20 (core + formats):** 3-5 days
- **EB01 (engine + CLI + IDE):** 2-3 days (depends on B20)
- **Total:** 5-8 days

### Workaround (until fixed)
For customers with non-UTF-8 content:
1. Convert to UTF-8 before sending to UTLXe (in the producer, middleware, or CPI iFlow)
2. For XML: ensure the XML declaration says `encoding="UTF-8"` and the bytes match
3. For CSV: convert to UTF-8 using `iconv` or equivalent before piping to utlx
4. For SAP via CPI: CPI's Camel runtime normalizes to UTF-8 — no workaround needed
5. For SAP direct (JCo/NCo): the connector converts to Java String (UTF-16 → UTF-8 on output) — ensure the connector's output encoding is UTF-8

## References

- RFC 8259 (JSON): "JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8"
- XML 1.0 spec: parsers MUST support UTF-8 and UTF-16, but may support others
- Dapr: delivers HTTP body as UTF-8
- Protobuf: `string` type is defined as UTF-8

---

## Priority and timing

| Target market | Encoding needed | When to fix |
|---|---|---|
| Azure Marketplace (Dapr/Service Bus) | UTF-8 only | Not blocking — UTF-8 is the standard |
| BizTalk replacement (EF08) | UTF-16 (BizTalk/.NET native) | **Before EF08 ships** |
| SAP CPI embedding | UTF-8 (CPI normalizes) | Not blocking — CPI handles conversion |
| SAP direct integration (RFC/IDoc) | UTF-16LE | **Before SAP direct integration** |
| European legacy systems | ISO-8859-1 | Before targeting legacy customers |
| Japanese SAP systems | Shift-JIS / EUC-JP | Before targeting Japanese market |

**Recommendation:** Fix B20 between the Azure Marketplace go-live and the EF08 (.NET SDK/BizTalk) release. The Azure offering works with UTF-8. The BizTalk/SAP offering requires UTF-16.

---

*Bug B20. May 2026. Discovered during EF02 validation wiring. Elevated to High after SAP encoding analysis.*
