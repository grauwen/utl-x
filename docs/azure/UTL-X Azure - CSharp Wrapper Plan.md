# Plan: C# Wrapper for UTLXe (stdio-proto)

## Context

UTLXe's engine redesign (Phases A-E) created a transport-agnostic engine with a stable protobuf contract (`proto/utlxe/v1/utlxe.proto`). The Open-M Go wrapper uses this contract via stdio-proto. For the Azure commercial strategy, a thin C# wrapper is needed so .NET applications (Azure Functions, Logic Apps built-in connectors) can call UTLXe transformations. The wrapper spawns UTLXe as a long-running subprocess and communicates via varint-delimited protobuf over stdin/stdout — the same protocol the Go wrapper uses.

## Location in Monorepo

```
wrappers/dotnet/                    # NOT under modules/ — not a Gradle project
├── UtlxClient.sln
├── src/
│   └── UtlxClient/
│       ├── UtlxClient.csproj       # net8.0 class library, NuGet-packable
│       ├── UtlxeProcess.cs         # JVM subprocess lifecycle
│       ├── UtlxeClient.cs          # Public API: Load, Execute, Batch, Unload, Health
│       ├── VarintCodec.cs          # Varint framing (C# protobuf lacks writeDelimitedTo)
│       └── UtlxeException.cs       # Typed exceptions with ErrorClass/ErrorPhase
├── tests/
│   └── UtlxClient.Tests/
│       ├── UtlxClient.Tests.csproj # xUnit
│       ├── VarintCodecTests.cs
│       └── UtlxeClientIntegrationTests.cs
└── samples/
    └── AzureFunctionSample/
        ├── AzureFunctionSample.csproj
        ├── Program.cs
        └── TransformFunction.cs
```

**Why `wrappers/dotnet/` not `modules/dotnet/`:** The repo already has non-Gradle top-level dirs (vscode-extension/, conformance-suite/, mcp-server/). A `wrappers/` directory signals "language-specific clients that call UTLXe" and naturally accommodates a future Go wrapper too. Gradle doesn't know about it — builds are fully separate.

## Proto Integration

The csproj references the existing proto file by relative path — no copy:

```xml
<Protobuf Include="../../../proto/utlxe/v1/utlxe.proto"
          GrpcServices="None" ProtoRoot="../../../proto" />
```

`GrpcServices="None"` — we use stdio-proto, not gRPC. Only message classes are generated.

## Core Classes (~300 lines total)

### `VarintCodec.cs` (~40 lines)
Static helper for base-128 varint read/write. Required because C# protobuf (unlike Java) has no `parseDelimitedFrom`/`writeDelimitedTo`.

### `UtlxeProcess.cs` (~80 lines)
- Spawns `java -jar <utlxe.jar> --mode stdio-proto --workers <n>`
- Exposes raw `Stream` for stdin/stdout (via `Process.StandardInput.BaseStream` — critical: NOT the text StreamReader)
- Pipes stderr to `ILogger`
- `IAsyncDisposable`: sends SIGTERM, waits 5s, force-kills

### `UtlxeClient.cs` (~150 lines)
Public API surface:
- `StartAsync()` — spawns process, sends HealthRequest, waits for HealthResponse (absorbs ~2s JVM startup)
- `LoadTransformationAsync(id, source, strategy)`
- `ExecuteAsync(id, payload, contentType, correlationId?)`
- `ExecuteBatchAsync(id, items)`
- `UnloadTransformationAsync(id)`
- `HealthAsync()`
- `DisposeAsync()` — shuts down subprocess

Internally: write semaphore for stdin serialization, background reader task on stdout, `Channel<StdioEnvelope>` for sequential response matching.

### `UtlxeClientOptions`
```csharp
record UtlxeClientOptions {
    string JarPath;          // required
    string? JavaHome;        // optional, falls back to JAVA_HOME env
    int Workers = 1;         // sequential by default
    TimeSpan StartupTimeout = 30s;
    ILoggerFactory? LoggerFactory;
}
```

JAR resolution order: explicit path → `UTLXE_JAR_PATH` env → sibling directory of calling assembly.

## JAR Bundling

The NuGet package contains **only the C# library** (small). The UTLXe JAR (~50MB) is distributed separately:
- In Azure Functions: deployed alongside the function app
- In Docker: added as a layer
- In CI/testing: built from source via `./gradlew :modules:engine:jar`

## Build & CI

Builds are **completely separate** from Gradle. No changes to `settings.gradle.kts`.

New `.github/workflows/dotnet-ci.yml`:
- Triggers on `wrappers/dotnet/**` and `proto/**` changes
- Sets up Java 17 + .NET 8
- Builds the UTLXe fat JAR first (`./gradlew :modules:engine:jar`)
- `dotnet build` + `dotnet test` with `UTLXE_JAR_PATH` pointing to the built JAR

## Implementation Sequence

1. Scaffold solution, projects, directory structure
2. `VarintCodec.cs` + unit tests (no UTLXe dependency)
3. Proto generation — configure csproj, verify C# stubs compile
4. `UtlxeProcess.cs` — subprocess management, binary streams, disposal
5. `UtlxeClient.cs` — envelope send/receive, all 5 API methods
6. Integration tests (require built `utlxe.jar`) — mirror the Kotlin StdioProtoTransportTest patterns
7. Azure Function sample
8. NuGet packaging metadata
9. CI workflow

## Key Files Referenced

- `proto/utlxe/v1/utlxe.proto` — the proto contract (source of truth)
- `modules/engine/src/main/kotlin/.../transport/StdioProtoTransport.kt` — server-side reference for wire compatibility
- `modules/engine/src/main/kotlin/.../Main.kt` — CLI args the process must match
- `modules/engine/build.gradle.kts` — fat JAR output path
- `modules/engine/src/test/kotlin/.../transport/StdioProtoTransportTest.kt` — test patterns to mirror

## Verification

1. `dotnet build` succeeds with generated proto stubs
2. `VarintCodecTests` pass (roundtrip encoding)
3. Integration tests pass: Load identity transform → Execute with JSON → verify output → Health → Unload
4. Azure Function sample runs locally (`func start`) and handles a curl POST
5. `dotnet pack` produces valid NuGet package
