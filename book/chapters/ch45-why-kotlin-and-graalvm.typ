= Why Kotlin and GraalVM (and Why Not Other Languages)

== Why Kotlin Was the Right Choice

=== Kotlin's Strengths for UTL-X
// - **Sealed classes**: perfect for UDM type hierarchy (Scalar, Object, Array, DateTime, ...)
//   sealed class UDM → when(udm) { is Scalar → ..., is Object → ... }
//   Compiler enforces exhaustive matching — can't forget a type
// - **Extension functions**: add methods to existing types without inheritance
//   fun UDM.asString(): String = ... (clean API, no wrapper classes)
// - **Null safety**: ?. and ?: built into the language
//   $input.Order?.Customer?.Name ?? "Unknown" maps directly to Kotlin's type system
// - **Data classes**: UDM.Object, UDM.Scalar — automatic equals/hashCode/toString
// - **Coroutines**: async I/O for HTTP transport (Ktor uses coroutines natively)
// - **Java interop**: 100% compatible — use any Java library (ANTLR, Jackson, Netty)
// - **ANTLR integration**: Gradle ANTLR plugin works seamlessly for grammar → parser
// - **Functional style**: map, filter, reduce built into stdlib — natural fit for UTL-X semantics
// - **JVM ecosystem**: access to 100,000+ Java libraries (XML parsers, crypto, networking)

=== Kotlin vs Alternatives Considered
//
// | Language | Considered? | Why not |
// |----------|------------|---------|
// | Java | Yes | Too verbose for parser/AST code, no sealed classes (until Java 17), no null safety |
// | Scala | Yes | Complex type system, slow compilation, smaller community, SBT build tool |
// | Go | Yes | No generics (at the time), no sealed types, manual memory management distracts |
// | Rust | No | Too low-level for a transformation engine, ownership model adds complexity |
// | Python | No | Too slow for production engine (86K msg/s not achievable), GIL limits concurrency |
// | TypeScript | Yes | Single-threaded (Node.js), no native compilation path (at the time), limited stdlib |
// | C\# | Yes | Would limit to .NET ecosystem, less Unix tooling, no GraalVM equivalent |

== Why GraalVM on Top of Kotlin

=== GraalVM Native Image
// - Compiles Kotlin/JVM bytecode → native machine code (no JVM at runtime)
// - Result: single binary, <10ms startup, 40MB memory
// - Essential for: CLI tool (utlx), shell pipelines, CI/CD, developer experience
//
// Without GraalVM:
//   $ time java -jar utlx.jar --version
//   real 0.245s  ← too slow for shell scripts
//
// With GraalVM:
//   $ time ./utlx --version
//   real 0.008s  ← instant, like any native CLI tool
//
// This is why `brew install utlx` gives you a NATIVE binary, not a JAR.

=== GraalVM for Cloud (UTLXe)
// - Scale-to-zero: GCP Cloud Run restarts containers from zero
//   JVM: 5-15s cold start (unacceptable for real-time)
//   GraalVM native: <1s cold start (viable for serverless)
// - Memory: 40MB vs 150MB (saves 73% — direct cost reduction on cloud)
// - Currently: UTLXe runs on JVM (native image is Phase 7, deferred)
// - Why deferred: reflection challenges (Kotlin reflection, ANTLR parser, dynamic class loading)
// - GraalVM native for UTLXe is the path to true scale-to-zero on Cloud Run

== Why NOT a JavaScript/TypeScript Version

=== The Temptation
// - "JavaScript runs everywhere — browser, Node.js, Deno, Bun, edge functions"
// - "TypeScript adds types — it's modern and popular"
// - Many developers know JS/TS — largest potential contributor pool

=== Why It Would Fail
// 1. **Performance ceiling**: Node.js is single-threaded (event loop)
//    - 86K msg/s requires 8+ concurrent workers
//    - Node.js Worker Threads exist but share no memory (serialization overhead)
//    - V8 is fast for single-threaded work, but UTLXe needs CONCURRENT throughput
//
// 2. **No sealed types**: TypeScript discriminated unions are a workaround, not a language feature
//    - UDM type hierarchy needs exhaustive matching at compile time
//    - TypeScript's type system is erased at runtime — no runtime type checking
//
// 3. **Float precision**: JavaScript has only `number` (IEEE 754 double)
//    - Financial calculations: 0.1 + 0.2 ≠ 0.3 in JavaScript
//    - UTL-X handles currencies, tax calculations, invoice totals
//    - Kotlin/JVM has BigDecimal, Long, Double — proper numeric types
//
// 4. **XML parsing**: browser DOM API is different from Node.js parsers
//    - No single XML parser works across all JS environments
//    - Namespace handling is inconsistent across implementations
//    - UTL-X's XML parser is 450 lines of Kotlin — the JS equivalent would be 3x larger
//
// 5. **Maintenance burden**: two codebases = two sets of bugs
//    - Every bug fix in Kotlin must be replicated in JS
//    - Every new function (652 stdlib!) must be implemented twice
//    - Behavioral divergence is inevitable — conformance tests would pass differently

== Why NOT WebAssembly (WASM)

=== The Promise
// - "Compile Kotlin to WASM — run UTL-X in the browser, edge functions, everywhere"
// - Kotlin/WASM exists (experimental)
// - WASM runs in: browsers, Cloudflare Workers, Fastly Edge, Wasmer, Wasmtime

=== Why It Doesn't Work (Today)
// 1. **Date/Time functions fail**: WASM has no system clock access
//    - now(), today(), parseDate() — 68 date functions depend on platform time
//    - WASM sandboxes I/O — no file system, no network, no clock
//    - Would need WASI (WebAssembly System Interface) — not yet standardized across runtimes
//
// 2. **Kotlin/WASM is experimental**:
//    - Reflection: not supported (UTL-X interpreter uses reflection for stdlib dispatch)
//    - Coroutines: limited support
//    - Binary size: ~20MB WASM module (too large for edge deployment)
//    - Compilation: slow, produces unoptimized code
//
// 3. **String handling**: WASM uses linear memory — no garbage collector (until WasmGC)
//    - UTL-X processes thousands of strings per transformation
//    - Manual memory management for strings in WASM is a nightmare
//    - WasmGC (garbage collection proposal) helps but is not yet universal
//
// 4. **632 stdlib functions**: each needs WASM-compatible implementation
//    - Crypto functions (md5, sha256): need WASM crypto libraries
//    - XML parsing: no DOM in WASM, need pure-WASM parser
//    - File I/O: not available in WASM sandbox
//
// Verdict: WASM is 2-3 years away from being viable for UTL-X.
// When Kotlin/WASM matures + WasmGC is universal → revisit.

== Why Multiple Language Implementations Lead to Deviation

=== The N-Implementation Problem
// If UTL-X were implemented in Kotlin + JavaScript + Python + Go:
// - 4 parsers: each handles edge cases differently
// - 4 interpreters: subtle behavioral differences
// - 4 × 652 = 2,608 stdlib implementations: guaranteed inconsistency
// - 4 × 11 format parsers: XML namespace handling WILL differ
// - 4 conformance suites: tests pass differently on each platform
//
// Real-world example: XSLT has multiple implementations
// (Saxon, Xalan, libxslt, MSXML) — they produce DIFFERENT output
// for the same stylesheet. This has plagued XML developers for 25 years.
//
// UTL-X's design: ONE implementation (Kotlin), accessed via:
// - Native binary (GraalVM) for CLI
// - JVM for engine and daemon
// - Subprocess + protobuf for C#, Go, Python wrappers
// - HTTP API for any language
//
// One implementation = one behavior = one conformance suite = zero deviation.

=== The Wrapper Pattern Avoids Deviation
// Instead of reimplementing UTL-X in each language:
// ┌─────────┐     ┌─────────────┐
// │ C# app  │────→│ UTLXe (JVM) │  ← ONE implementation
// └─────────┘     │ via stdio   │
// ┌─────────┐     │ or HTTP     │
// │ Go app  │────→│             │
// └─────────┘     │             │
// ┌─────────┐     │             │
// │ Python  │────→│             │
// └─────────┘     └─────────────┘
//
// The wrapper is 200-300 lines of thin client code.
// The engine (100,000+ lines of transformation logic) exists ONCE.
// All languages get identical behavior — verified by 453+ conformance tests.
