= SDKs and Wrapper Libraries

UTLXe is a JVM process written in Kotlin. But not every application is JVM — your service might be C\#, Python, Go, Rust, or Node.js. This chapter covers how non-JVM applications embed UTL-X transformation capabilities.

== The Wrapper Pattern

The wrapper pattern runs UTLXe as a subprocess of your application, communicating via protobuf-encoded messages over stdin/stdout:

```
┌──────────────────────┐     ┌──────────────────────┐
│  Your Application    │     │  UTLXe Subprocess    │
│  (C#, Python, Go)    │     │  (JVM)               │
│                      │     │                      │
│  wrapper.Execute()   │────→│  stdin (protobuf)    │
│                      │←────│  stdout (protobuf)   │
└──────────────────────┘     └──────────────────────┘
```

The wrapper library handles process lifecycle (start, health-check, restart), message framing (varint-delimited protobuf), and correlation IDs (concurrent Execute calls multiplexed on one process).

=== Why Stdio Instead of HTTP?

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Aspect*], [*Stdio (protobuf)*], [*HTTP*],
  [Latency], [Sub-millisecond], [~1ms (TCP overhead)],
  [Port management], [None — no port conflicts], [Requires available port],
  [Sandboxed environments], [Works (no network)], [May be blocked by firewall],
  [Process lifecycle], [Natural binding — parent kills child], [Requires separate process management],
  [Concurrency], [Multiplexed via correlation IDs], [Native HTTP concurrency],
  [Complexity], [Requires SDK], [Any HTTP client works],
)

Stdio is faster and simpler for embedded use. HTTP is simpler for polyglot/shared-service use. Both are available — choose based on your architecture.

== Available SDKs

=== C\# / .NET Wrapper

*Status:* Built and tested. Targets .NET 9 and .NET 10.

```csharp
// Install: dotnet add package UtlxClient

var client = new UtlxeClient();

// Load a transformation
await client.LoadAsync("order-to-invoice", utlxSource);

// Execute a transformation
var result = await client.ExecuteAsync("order-to-invoice", jsonPayload);
Console.WriteLine(result);

// Batch processing
var results = await client.BatchAsync("order-to-invoice", payloads);

// Pipeline (multi-step)
var output = await client.PipelineAsync(new[] {
    ("normalize", null),
    ("enrich", enrichmentData),
    ("generate-invoice", null)
}, inputPayload);

// Cleanup
await client.UnloadAsync("order-to-invoice");
client.Dispose();
```

*Features:*
- `UtlxeClient` — high-level async API: Load, Execute, Batch, Pipeline, Unload
- `UtlxeProcess` — JVM subprocess lifecycle management (start, health, restart)
- `VarintCodec` — varint-delimited protobuf framing
- Correlation ID multiplexing — concurrent Execute calls on one subprocess
- Automatic process recovery — restarts UTLXe if it crashes

*Use cases:*
- Azure Functions: HTTP trigger → UTL-X transform → response
- ASP.NET Web API: transformation middleware
- Console applications: batch file conversion
- Dynamics 365 plugins: transform OData payloads

=== Go Wrapper

*Status:* Built and tested. 12 integration tests.

```go
import "github.com/utlx-lang/utlxclient"

// Create client (starts UTLXe subprocess)
client, err := utlxclient.New()
defer client.Close()

// Load transformation
err = client.Load("order-to-invoice", utlxSource)

// Execute
result, err := client.Execute("order-to-invoice", jsonPayload)
fmt.Println(result)

// Batch
results, err := client.Batch("order-to-invoice", payloads)

// Health check
status, err := client.Health()
```

*Features:*
- Full client API: New, Close, Load, Execute, Batch, Pipeline, Unload, Health
- Protobuf over stdio communication
- Thread-safe — safe for concurrent goroutines

*Use cases:*
- Go microservices requiring data transformation
- Open-M controller integration
- CLI tools written in Go

=== Java / Kotlin

Java has two integration options — each with different trade-offs:

==== Option 1: Direct API (In-Process)

UTLXe IS Java/Kotlin — import the library and call it directly:

```kotlin
val engine = UtlxEngine()
engine.initialize(bundlePath)
val result = engine.execute("order-to-invoice", inputPayload)
```

*Advantage:* zero latency, zero IPC overhead, direct method calls.

*Disadvantage:* compile-time binding. Your application depends on UTLXe's JAR and all its transitive dependencies (SnakeYAML, Jackson, ASM, Apache XML Security). Upgrading UTLXe means recompiling and redeploying your application. Dependency conflicts — your app uses Jackson 2.15, UTLXe needs 2.17 — can break the build. Your app and UTLXe share the same JVM heap, the same classpath, the same lifecycle.

==== Option 2: Java Stdio Wrapper (Decoupled)

The same subprocess pattern as C\# and Go — but from Java:

```java
var client = new UtlxeClient();

// Same interface as C# and Go
client.load("order-to-invoice", utlxSource);
var result = client.execute("order-to-invoice", jsonPayload);
var results = client.batch("order-to-invoice", payloads);
client.unload("order-to-invoice");
client.close();
```

*Advantage:* no compile-time binding. UTLXe runs as a separate process — upgrade it by swapping the JAR without recompiling your app. No dependency conflicts. No shared heap. Hot-swappable: stop the subprocess, start a new version, resume processing. And critically: the Java SDK uses the *same interface and protocol* as C\#, Go, and all future SDKs.

*Disadvantage:* ~0.5ms IPC overhead per call (protobuf serialization + stdio). Negligible for most use cases, but measurable at 100K+ calls per second.

==== Which to Choose?

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Criterion*], [*Direct API*], [*Java Wrapper*],
  [Latency], [Zero], [Sub-millisecond],
  [Dependency coupling], [Tight (shared classpath)], [None (separate process)],
  [UTLXe upgrades], [Recompile + redeploy app], [Swap JAR, restart subprocess],
  [Dependency conflicts], [Possible (shared JVM)], [Impossible (separate JVM)],
  [Hot-swap], [No (same process)], [Yes (restart subprocess)],
  [Memory isolation], [Shared heap], [Separate heaps],
  [Best for], [Embedded, maximum throughput], [Microservices, production ops],
)

For production microservices, the wrapper is usually the better choice — the sub-millisecond overhead is negligible, and the operational benefits (decoupled upgrades, no dependency conflicts, hot-swap) are significant. The direct API is for specialized cases where you need absolute maximum throughput and control the entire dependency tree.

== The Uniform SDK Interface

All SDKs — C\#, Go, Java wrapper, and future Python and Node.js — implement the same interface contract:

```
Load(id, source)            → ok / error
Execute(id, payload)        → result / error
Batch(id, payloads[])       → results[]
Pipeline(steps[])           → result
Unload(id)                  → ok
Health()                     → status
```

This uniformity is deliberate:

- *Same mental model:* a C\# team and a Java team use the same API. Documentation covers all SDKs at once.
- *Portable knowledge:* learning the Go wrapper means you already know the C\# wrapper. The function names, parameters, and behavior are identical.
- *Consistent behavior:* every SDK communicates via the same protobuf protocol. The error codes, timeout handling, and correlation ID multiplexing are identical across languages.
- *Testable:* integration tests can be written once (as protocol-level expectations) and verified across all SDKs.

The direct Java API is the exception — it bypasses the protocol layer for zero-overhead access. But it exposes a different API surface (engine internals rather than the SDK interface). For teams that want consistency across their polyglot stack, the Java wrapper is preferred even when the host is JVM.

=== Python Wrapper (Planned)

Not yet built — expected to follow the same stdio-protobuf pattern.

*Would enable:*
- Jupyter notebook transformations (explore data, transform inline)
- Django/Flask middleware
- Data science pipelines (pandas DataFrame → UTL-X → JSON/CSV)
- AWS Lambda (Python runtime + UTLXe subprocess)

=== Node.js / TypeScript Wrapper (Planned)

Not yet built — would use `child_process.spawn` + protobuf over stdio.

*Would enable:*
- Express/Fastify middleware
- Serverless functions (AWS Lambda, Vercel)
- VS Code extension backend

== Alternative: HTTP API (No SDK Needed)

UTLXe in HTTP mode exposes a REST API that any language can call — no SDK required:

```bash
# Load a transformation
curl -X POST http://localhost:8085/api/load \
  -H "Content-Type: application/json" \
  -d '{"id": "order-transform", "source": "..."}'

# Execute a transformation
curl -X POST http://localhost:8085/api/execute/order-transform \
  -H "Content-Type: application/json" \
  -d '{"orderId": "ORD-001", "customer": "Acme"}'

# One-shot transform (load + execute in one call)
curl -X POST http://localhost:8085/api/transform \
  -H "Content-Type: application/json" \
  -d '{"utlxSource": "...", "payload": "..."}'
```

Any language with an HTTP client works — PHP, Ruby, Rust, shell scripts. The trade-off: ~1ms HTTP latency per call vs sub-millisecond stdio. For most use cases, this latency is negligible.

== Alternative: Dapr Sidecar (Cloud-Native)

Dapr provides language-agnostic integration without any direct connection to UTLXe:

```
Your App → publishes to Service Bus → Dapr → UTLXe → Dapr → result topic
```

Your application publishes messages to a broker (Service Bus, Kafka, RabbitMQ). Dapr routes them to UTLXe. UTLXe transforms and publishes the result via Dapr. Your application never talks to UTLXe directly.

Best for: Azure Container Apps, Kubernetes with Dapr, event-driven architectures where the broker is the integration backbone.

== SDK Comparison

#table(
  columns: (auto, auto, auto, auto, auto),
  align: (left, left, left, left, left),
  [*Integration*], [*Language*], [*Protocol*], [*Latency*], [*Status*],
  [C\# wrapper], [.NET], [Stdio protobuf], [Sub-ms], [Built],
  [Go wrapper], [Go], [Stdio protobuf], [Sub-ms], [Built],
  [Java wrapper], [JVM], [Stdio protobuf], [Sub-ms], [Built],
  [Java direct], [JVM], [In-process API], [Zero], [Native],
  [HTTP API], [Any], [HTTP REST], [~1ms], [Built],
  [Dapr sidecar], [Any], [HTTP/gRPC], [~2ms], [Built],
  [Python wrapper], [Python], [Stdio protobuf], [Sub-ms], [Planned],
  [Node.js wrapper], [JS/TS], [Stdio protobuf], [Sub-ms], [Planned],
)

=== Choosing the Right Integration

- *Your app is JVM and you want decoupled upgrades:* use the Java wrapper — same interface as C\#/Go, hot-swappable, no dependency conflicts
- *Your app is JVM and you need absolute maximum throughput:* use the direct API — zero overhead, but tight coupling
- *Your app is C\# or Go:* use the wrapper SDK — sub-millisecond, typed API
- *Your app is any other language:* use the HTTP API — universal, no SDK needed
- *Your architecture is event-driven:* use Dapr — decoupled, broker-agnostic
- *You want consistency across a polyglot stack:* use wrappers everywhere (same interface in every language)
- *You need maximum simplicity:* HTTP API — `curl` is your SDK
