# UTL-X WASM Feasibility Analysis

## Why WASM?

A WASM target would enable running UTL-X transformations outside the JVM: in browsers, edge runtimes (Cloudflare Workers, Fastly), serverless (Wasmtime, WasmEdge), and embedded environments.

## Current Architecture

- **Build:** Gradle 8.5, Kotlin 1.9.21, JVM-only (no Kotlin Multiplatform)
- **Modules:** core, cli, daemon, analysis, stdlib, stdlib-security, schema, 12 format modules
- **Entry points:** CLI (GraalVM native image), Daemon (Ktor REST + LSP)

## Good News: The Codebase Is More Portable Than Expected

| Component | Status |
|---|---|
| Core lexer/parser | Hand-written recursive descent, pure Kotlin |
| Core interpreter | Pure Kotlin, minimal JVM deps |
| JSON parser/serializer | Hand-written, only uses `java.io.Reader/Writer` as String wrappers |
| XML parser/serializer | Hand-written, same pattern |
| CSV parser/serializer | Hand-written, same pattern |
| ANTLR grammar | Exists (`UDMLang.g4`) but **unused at runtime** -- hand-written `UDMLanguageParser.kt` is used instead |
| CldrDataProvider | Already abstracted with `expect/actual`-ready interface |

## JVM Dependencies to Resolve

### Critical (blocks compilation)

| Dependency | Where | Replacement |
|---|---|---|
| `java.time.Instant/LocalDate/LocalDateTime` | `udm_core.kt`, `UDMLanguageParser.kt` | `kotlinx.datetime` (already in project) |
| `java.time.LocalTime` | `udm_core.kt` | Custom `expect/actual` |
| `mu.KotlinLogging` | `interpreter.kt`, `lexer_impl.kt`, `parser_impl.kt` | Simple `expect/actual` Logger |
| `java.io.Reader/Writer/StringReader/StringWriter` | All format parsers/serializers | Refactor to `String`/`StringBuilder` |
| `String.format("\\u%04x")` | `json_serializer.kt` | `padStart(4, '0')` |
| `System.identityHashCode` | `udm_core.kt` (Lambda) | Kotlin default hashCode |

### JVM-only modules (stay on JVM, not ported)

| Module/Feature | JVM Dependency | Notes |
|---|---|---|
| YAML format | SnakeYAML | No multiplatform YAML parser available |
| Avro format | Apache Avro | Deeply JVM-specific |
| Protobuf format | protobuf-java | Could use kotlinx-protobuf in future |
| XSD format | javax.xml | Deeply JVM-specific |
| CLI | JLine, GraalVM | Terminal-specific |
| Daemon | Ktor Server, coroutines | Server-specific |
| stdlib: date/* | java.time.* | Could port via kotlinx.datetime later |
| stdlib: encoding/* | java.util.Base64, java.security.MessageDigest | Could port with pure-Kotlin impls later |
| stdlib: binary/* | java.nio.ByteBuffer | N/A for WASM |
| stdlib: compression/* | java.util.zip | N/A for WASM |
| stdlib: finance | java.math.BigDecimal | No multiplatform equivalent |
| stdlib: regional | java.text.DecimalFormat | No multiplatform equivalent |
| kotlin-reflect | `exportRegistry()` in Functions.kt | Build-time only, not needed at runtime |

### Stdlib functions portable to WASM (~120 functions)

- string/* (all categories: case, regex, character, pluralization, etc.)
- array/* (aggregations, joins, unzip, enhanced)
- math/MathFunctions, StatisticalFunctions, AdvancedMathFunctions
- type/TypeFunctions
- objects/* (all categories)
- logical/LogicalFunctions
- xml/QNameFunctions, XmlUtilityFunctions, CDATAFunctions
- geo/GeospatialFunctions
- csv/CSVFunctions
- serialization/SerializationFunctions, PrettyPrintFunctions
- util/TreeFunctions, ValueFunctions, DiffFunctions, CoercionFunctions
- core/CoreFunctions (UUID needs `kotlin.random.Random` replacement)

## Prerequisites

### 1. Kotlin 2.0+ Upgrade (Required)

Kotlin/Wasm is experimental in 1.9.x and Beta in 2.0+. The project must upgrade from 1.9.21 to 2.0.21+.

This is the **highest-risk prerequisite** because:
- K2 compiler may require code adjustments
- All modules are affected
- Plugin compatibility must be verified (ANTLR, GraalVM, serialization, Dokka)

Gradle must also upgrade from 8.5 to 8.9+ for full KMP support.

### 2. Kotlin/Wasm Status (2026)

- **Stability:** Beta (not yet stable, but production-usable for early adopters)
- **Browser support:** Chrome 119+, Firefox 120+, Safari 18.2+ (all support WasmGC)
- **Limitations:**
  - No true multi-threading (single-threaded only)
  - Very limited reflection (`KClass.qualifiedName` needs compiler flag)
  - No direct JVM library reuse -- must use multiplatform libs or `expect/actual`
- **Available multiplatform libraries:** kotlinx.serialization, kotlinx.coroutines, kotlinx.datetime

## WASM Target Options

### Option A: wasmWasi (Standalone)

Produces a `.wasm` file runnable on WASI-compatible runtimes.

| Aspect | Details |
|---|---|
| **Runtimes** | Wasmtime, WasmEdge, Node.js (WASI polyfill), Deno |
| **Use cases** | Edge computing, serverless functions, embedded in other applications, CLI alternative |
| **WASI version** | 0.1 (Preview 1) supported; 0.2 planned |
| **Gradle target** | `wasmWasi { nodejs() }` |
| **Test command** | `./gradlew wasmWasiNodeRun` |
| **Pros** | No browser complexity, closest to current JVM usage pattern |
| **Cons** | WASI ecosystem still maturing |

### Option B: wasmJs (Browser)

Produces a WASM module loadable in browsers via JavaScript.

| Aspect | Details |
|---|---|
| **Runtimes** | Chrome, Firefox, Safari, any browser with WasmGC |
| **Use cases** | Client-side data transformation, browser-based mapping editor, playground |
| **Gradle target** | `wasmJs { browser() }` |
| **Test command** | `./gradlew wasmJsBrowserTest` |
| **Pros** | Largest reach, enables browser-based UTL-X tools |
| **Cons** | Needs JS interop layer, larger binary size concerns |

### Option C: Both

Target both `wasmWasi` and `wasmJs`. Shared code goes to a common WASM source set.

| Aspect | Details |
|---|---|
| **Additional effort** | ~2-3 days beyond single target |
| **Gradle structure** | `wasmWasi()` + `wasmJs()` targets in KMP modules |
| **Pros** | Maximum flexibility |
| **Cons** | More build complexity, more testing surface |

## MVP Scope (any option)

**What ships:** Core interpreter + JSON + XML + CSV + ~120 stdlib functions

**API:**
```kotlin
object UtlxWasm {
    fun transform(script: String, input: String,
                  inputFormat: String = "json",
                  outputFormat: String = "json"): String
    fun version(): String
    fun supportedFormats(): List<String>
}
```

**What doesn't ship (initially):** YAML, Avro, Protobuf, XSD, date functions, encoding, crypto, compression, CLI, daemon

## Implementation Phases

| Phase | What | Est. days | Risk |
|---|---|---|---|
| 0 | Kotlin 1.9 -> 2.0 upgrade, Gradle upgrade | 2-3 | High (K2 compiler) |
| 1 | Convert `modules:core` to KMP (jvm + wasm) | 3-5 | Medium |
| 2 | Convert JSON, XML, CSV format modules to KMP | 2-3 | Low |
| 3 | Split stdlib into common + jvm source sets | 3-5 | Medium |
| 4 | Create `modules/wasm` entry point | 1-2 | Low |
| 5 | Distribution (WASI binary, optional npm package) | 2-3 | Low |
| **Total** | | **13-21 days** | |

## Future Enhancements (Post-MVP)

- Port date functions via `kotlinx.datetime`
- Pure-Kotlin Base64 and URL encoding for WASM
- `wasmJs` npm package with TypeScript declarations
- WASM binary size optimization
- Performance benchmarking (WASM vs JVM vs GraalVM native)
- Compose Multiplatform playground (browser-based UTL-X editor)
