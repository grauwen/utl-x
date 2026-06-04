= Why Kotlin and GraalVM

"Why didn't you write it in Go?" "Why not Rust?" "Why not TypeScript?" These are the most common questions about UTL-X's technology choices. This chapter explains the reasoning — and why the answers matter for users, not just developers.

== Why Kotlin

Kotlin was chosen for UTL-X because its language features map directly to the problems a transformation engine must solve:

=== Sealed Classes for the UDM Type Hierarchy

UTL-X's Universal Data Model has a fixed set of types: Scalar, Object, Array, DateTime, Date, Time, Binary, Lambda. Kotlin's sealed classes enforce this at compile time:

```kotlin
sealed class UDM {
    class Scalar(val value: Any?) : UDM()
    class Object(val properties: Map<String, UDM>) : UDM()
    class Array(val elements: List<UDM>) : UDM()
    class DateTime(val instant: Instant) : UDM()
    // ...
}
```

The `when` expression with sealed classes guarantees exhaustive matching — the compiler refuses to compile if you forget a type. When a new UDM type is added, every `when` block that handles UDM types shows a compile error until updated. This prevents an entire class of runtime bugs.

=== Null Safety

Kotlin's type system distinguishes nullable (`String?`) from non-nullable (`String`) at compile time. This maps directly to UTL-X's safe navigation:

```utlx
$input.Order?.Customer?.Name ?? "Unknown"
```

The interpreter's Kotlin code handles this naturally — `?.` and `?:` are native Kotlin operators with the same semantics as UTL-X. The language implementation mirrors the language being implemented.

=== Extension Functions

Add methods to existing types without inheritance or wrapper classes:

```kotlin
fun UDM.asString(): String = when (this) {
    is Scalar -> value?.toString() ?: ""
    else -> throw RuntimeError("Expected string")
}
```

This keeps the UDM classes clean (pure data) while providing rich access methods. 650+ stdlib functions are registered without bloating the core types.

=== Java Ecosystem

100% Java interoperability means access to:
- Jackson (JSON parsing, used internally alongside the custom parser)
- SnakeYAML (YAML parsing)
- ASM (JVM bytecode generation for the COMPILED strategy)
- Apache XML Security (XML canonicalization, C14N)
- Every cryptographic algorithm in the JDK

The JVM ecosystem has 25+ years of battle-tested libraries. UTL-X stands on their shoulders.

=== Functional Style

Kotlin's `map`, `filter`, `reduce`, `flatMap` — and its lambda syntax — are the same paradigm as UTL-X. Writing the interpreter for a functional transformation language in a functional-capable language is natural. The interpreter's Kotlin code reads similarly to the UTL-X code it interprets.

== Why Not Other Languages?

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Language*], [*Why not*],
  [Java], [Too verbose for parser/AST code. No sealed classes until Java 17 (UTL-X started earlier). No null safety. Same JVM, less expressive.],
  [Scala], [Complex type system (implicits, macros). Slow compilation. Smaller community. SBT build tool friction.],
  [Go], [No generics (at the time of decision). No sealed types. No expression-based programming. Great for the controller (Open-M), wrong for the engine.],
  [Rust], [Too low-level for a transformation engine. Ownership/borrowing model adds complexity to tree manipulation. No garbage collector — UDM trees would need manual lifecycle management.],
  [Python], [Too slow for production (86K msg/s not achievable). GIL limits concurrency. Dynamic typing makes the interpreter error-prone. Great for the conformance suite runner, wrong for the engine.],
  [TypeScript], [Single-threaded (Node.js event loop). Float-only numbers (0.1 + 0.2 != 0.3). No native compilation path. Different XML parser in every environment.],
  [C\#], [Would limit to .NET ecosystem. Less Unix tooling. No GraalVM equivalent for native binary. Good for the wrapper SDK, wrong for the engine.],
)

Notice: several "wrong for the engine" languages are used elsewhere in the UTL-X ecosystem — Go for Open-M, Python for the conformance suite, C\# for the wrapper SDK. The right language for the right job.

== Why GraalVM

=== Native Image for the CLI

GraalVM compiles Kotlin/JVM bytecode to a native machine binary — no JVM at runtime:

```
Without GraalVM (JVM JAR):
  $ time java -jar utlx.jar --version
  real 0.245s  ← too slow for shell scripts

With GraalVM (native binary):
  $ time ./utlx --version
  real 0.008s  ← instant, like any native tool
```

This is why `brew install utlx` gives you a native binary, not a JAR. For CLI usage — shell scripts, CI/CD pipelines, interactive development — startup time is everything. A 250ms penalty per invocation makes `for f in *.xml; do utlx ...; done` painfully slow. With native: 100 files/second. With JVM: 4 files/second.

Native binary characteristics:
- Startup: ~7ms (comparable to jq)
- Memory: ~40MB resident (vs ~150MB for JVM)
- Distribution: single file, no JVM installation required
- Size: ~84MB (includes all format parsers, 650+ stdlib functions, and the GraalVM runtime)

=== GraalVM Challenges and How They Were Solved

GraalVM native-image performs ahead-of-time compilation — it strips everything not statically reachable. This creates challenges for a dynamic language runtime like UTL-X:

*Resource bundles:* The Apache XML Security library loads resource bundles dynamically via `ResourceBundle.getBundle()`. GraalVM's static analysis doesn't detect this. The fix: explicitly list resource bundles in `resource-config.json`.

*Reflection:* UTL-X's 650+ stdlib functions were originally loaded via Java reflection (`Class.forName` → `getMethod` → `invoke`). This worked on the JVM but failed silently in native image — GraalVM couldn't predict which classes would be accessed reflectively.

The solution: *eliminate reflection entirely*. Instead of reflective dispatch, the CLI registers all stdlib functions as direct function references at startup — using a lazy registration pattern:

- A lookup map is built once (on first stdlib call, not at startup — zero startup cost)
- When a function is called for the first time, the map provides a direct reference (HashMap lookup, no reflection)
- Subsequent calls go through the registered reference directly

This approach is faster than reflection on JVM too, and works identically on native image. The `Interpreter.setStdlibLookup(map, eager)` API supports both lazy (CLI) and eager (engine) modes — the engine can pre-register all functions at startup to avoid first-call latency in production.

The 10-point native binary validation checklist (embedded in the GitHub Actions build workflow) runs automatically on all three platforms (Linux, macOS, Windows) before release — preventing broken binaries from being published.

=== JVM for the Engine

UTLXe runs on the standard JVM, not as a native image. This is deliberate — the JVM is actually *faster* for long-running engine workloads:

- *HotSpot JIT:* compiles hot paths to native code at runtime, optimizing based on actual execution patterns
- *COMPILED strategy:* generates JVM bytecode via ASM — this bytecode benefits from JIT, a native image would not
- *G1GC:* handles large heaps efficiently for sustained throughput with thousands of concurrent messages
- *Eager stdlib loading:* the engine calls `setStdlibLookup(map, eager = true)` at startup, pre-registering all 650+ functions — no first-message latency penalty

Native image would save startup time (~250ms → ~10ms), but UTLXe starts once and runs for days. Startup time is irrelevant; sustained throughput matters. The JVM wins.

=== GraalVM for Cloud (Future)

GraalVM native for UTLXe would enable true scale-to-zero on serverless platforms:
- JVM cold start: 5-15 seconds (too slow for Cloud Run scale-from-zero)
- Native cold start: under 1 second (viable for serverless)
- Memory: 40MB vs 150MB (direct cost reduction)

This is deferred due to reflection challenges (Kotlin reflection, dynamic class loading, serialization libraries). When GraalVM's reachability metadata improves, UTLXe native becomes practical.

== Why NOT JavaScript/TypeScript

The most frequently suggested alternative. Here's why it would fail:

=== Performance Ceiling

Node.js is single-threaded (event loop). UTLXe achieves 86K msg/s with 8 concurrent workers processing simultaneously. Node.js Worker Threads exist but share no memory — serialization overhead between threads eliminates the concurrency benefit.

=== Number Precision

JavaScript has only `number` (IEEE 754 double-precision float). Financial calculations break:

```javascript
0.1 + 0.2 === 0.3  // false (JavaScript)
```

UTL-X handles currencies, tax calculations, invoice totals. Kotlin/JVM has `Long` (64-bit integer), `Double`, and `BigDecimal` — proper numeric types for financial data.

=== XML Inconsistency

The browser DOM API is different from Node.js XML parsers (xml2js, fast-xml-parser, libxmljs). No single XML parser works across all JS environments. Namespace handling is inconsistent across implementations. UTL-X's XML parser is 450 lines of Kotlin — the JS equivalent would be 3x larger and platform-dependent.

== Why NOT WebAssembly (WASM)

=== The Promise

"Compile Kotlin to WASM — run UTL-X in browsers, edge functions, everywhere." Kotlin/WASM exists (experimental).

=== Why It Doesn't Work Today

- *No system clock:* WASM sandboxes I/O. `now()`, `today()`, `parseDate()` — 68 date functions depend on platform time. Would need WASI (not yet standardized across runtimes).
- *No reflection:* UTL-X's interpreter uses reflection for stdlib dispatch. Kotlin/WASM doesn't support reflection.
- *String handling:* WASM uses linear memory with manual allocation. UTL-X processes thousands of strings per transformation. WasmGC (garbage collection) helps but is not universal.
- *Binary size:* ~20MB WASM module — too large for edge deployment.
- *650+ stdlib functions:* each needs WASM-compatible implementation. Crypto, XML parsing, file I/O — none available in WASM sandbox.

WASM is 2-3 years away from being viable for UTL-X. When Kotlin/WASM matures and WasmGC is universal — revisit.

== One Implementation, Zero Deviation

The most important architectural decision: UTL-X has ONE implementation (Kotlin/JVM), accessed through multiple interfaces.

If UTL-X were implemented in Kotlin + JavaScript + Python + Go:
- 4 parsers handling edge cases differently
- 4 interpreters with subtle behavioral differences
- 4 x 650+ = 2,600+ stdlib implementations with guaranteed inconsistency
- 4 x 11 format parsers where XML namespace handling WILL differ

Real-world precedent: XSLT has multiple implementations (Saxon, Xalan, libxslt, MSXML). They produce DIFFERENT output for the same stylesheet. This has plagued XML developers for 25 years.

UTL-X's design: one implementation, multiple access paths:

```
┌─────────┐
│ C# app  │──→┐
└─────────┘   │     ┌─────────────┐
┌─────────┐   │     │             │
│ Go app  │──→├────→│ UTLXe (JVM) │  ← ONE implementation
└─────────┘   │     │             │     100,000+ lines
┌─────────┐   │     │ via stdio,  │     650+ functions
│ Python  │──→┤     │ HTTP, or    │     11 format parsers
└─────────┘   │     │ direct API  │     500+ conformance tests
┌─────────┐   │     │             │
│ Any HTTP│──→┘     └─────────────┘
└─────────┘
```

The wrapper is 200-300 lines of thin client code. The engine (100,000+ lines of transformation logic) exists ONCE. All languages get identical behavior — verified by 500+ conformance tests running against the same binary.

One implementation = one behavior = one conformance suite = zero deviation.
