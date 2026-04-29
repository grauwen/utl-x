= SDKs and Wrapper Libraries

== The Wrapper Pattern
// - UTLXe is a JVM process (Kotlin)
// - Not every application is JVM — C\#, Python, Go, Rust, Node.js need access
// - Solution: UTLXe runs as a subprocess, communicates via protobuf over stdio
// - The wrapper library handles: process lifecycle, message framing, correlation IDs
//
// ┌──────────────────────┐     ┌──────────────────────┐
// │  Your Application    │     │  UTLXe Subprocess    │
// │  (C#, Python, Go)    │     │  (JVM)               │
// │                      │     │                      │
// │  wrapper.Execute()   │────→│  stdin (protobuf)    │
// │                      │←────│  stdout (protobuf)   │
// └──────────────────────┘     └──────────────────────┘
//
// Why not HTTP? stdio is faster (no TCP overhead), no port management,
// works in sandboxed environments, natural process lifecycle binding.

== Available SDKs

=== C\# / .NET Wrapper (Built)
// - Repository: wrappers/dotnet/
// - NuGet package: UtlxClient
// - Features:
//   - UtlxeClient: high-level API (Load, Execute, Batch, Pipeline, Unload)
//   - UtlxeProcess: JVM subprocess lifecycle management
//   - VarintCodec: varint-delimited protobuf framing
//   - Correlation ID multiplexing: concurrent Execute calls on one process
//   - Multi-target: .NET 9 and .NET 10
// - Use cases:
//   - Azure Functions (HTTP trigger → UTL-X transform → response)
//   - ASP.NET Web API (middleware transformation)
//   - Console applications (batch processing)
// - Example:
//   var client = new UtlxeClient();
//   await client.LoadAsync("order-transform", utlxSource);
//   var result = await client.ExecuteAsync("order-transform", jsonPayload);

=== Go Wrapper (Built)
// - Repository: wrappers/go/utlxclient/
// - Package: utlxclient
// - Features:
//   - Full client: New, Close, Load, Execute, Batch, Pipeline, Unload, Health
//   - Protobuf over stdio communication
//   - 12 integration tests
// - Use cases:
//   - Open-M integration (transformation within the Open-M controller)
//   - Go microservices requiring data transformation
//   - CLI tools written in Go

=== Python Wrapper (Planned)
// - Not yet built — high demand expected
// - Design: same pattern as C# and Go
// - Would enable:
//   - Jupyter notebook transformations
//   - Django/Flask middleware
//   - Data science pipelines (pandas → UTL-X → parquet)
//   - AWS Lambda (Python runtime + UTLXe subprocess)
// - Effort: medium (protobuf support via protobuf library, subprocess management)

=== Java / Kotlin Wrapper (Direct)
// - No wrapper needed — UTLXe IS Java/Kotlin
// - Direct API access: TransformationService, UtlxEngine
// - Use cases:
//   - Spring Boot microservice with embedded transformation
//   - Apache Camel custom processor
//   - Kafka Streams transformation step
// - Advantage: no subprocess overhead, direct method calls
// - Disadvantage: JVM dependency in the host application

=== Node.js / TypeScript Wrapper (Planned)
// - Not yet built
// - Design: child_process.spawn + protobuf over stdio
// - Would enable:
//   - Express/Fastify middleware
//   - Serverless (AWS Lambda Node.js runtime)
//   - VS Code extension backend (alternative to utlxd)

== Alternative: HTTP API (No SDK Needed)
// - UTLXe --mode http exposes a REST API
// - ANY language can call it via HTTP POST
// - Endpoints: /api/transform, /api/load, /api/execute/{id}
// - Trade-off: HTTP overhead (~1ms latency) vs simplicity (no SDK, no subprocess)
// - Best for: polyglot environments where multiple languages need transformation
//
// No SDK needed — just HTTP:
// curl -X POST http://localhost:8085/api/transform \
//   -H "Content-Type: application/json" \
//   -d '{"transformationId":"test","utlxSource":"...","payload":"..."}'

== Alternative: Dapr Sidecar (Cloud-Native)
// - Dapr provides language-agnostic integration via HTTP/gRPC sidecar
// - UTLXe listens on Dapr input binding
// - Any application publishes to Service Bus/Kafka → Dapr → UTLXe → Dapr → output
// - No direct UTLXe connection needed — Dapr handles message routing
// - Best for: Azure Container Apps, Kubernetes with Dapr

== SDK Comparison

// | SDK | Language | Protocol | Latency | Subprocess | Status |
// |-----|---------|----------|---------|-----------|--------|
// | C# wrapper | .NET | stdio-proto | <1ms | Yes | Built |
// | Go wrapper | Go | stdio-proto | <1ms | Yes | Built |
// | Python | Python | stdio-proto | <1ms | Yes | Planned |
// | Java/Kotlin | JVM | Direct API | 0ms | No | Native |
// | Node.js | JS/TS | stdio-proto | <1ms | Yes | Planned |
// | HTTP API | Any | HTTP REST | ~1ms | No (shared) | Built |
// | Dapr | Any | HTTP/gRPC | ~2ms | Sidecar | Built |
