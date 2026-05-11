# EF08: .NET SDK, BizTalk Shim, and Logic Apps Integration

**Status:** SDK core + BizTalk shim + Logic Apps helpers implemented  
**Priority:** High (BizTalk SBMP deadline September 30, 2026)  
**Created:** May 2026  
**Depends on:** EF03 (Admin API), EF04 (tracing), EF07 (parallel transports), EF17 (schema pass-through), EF18 (request_id)  
**Source:** `docs/dapr/utlxe-biztalk-replacement.md`

---

## Summary

UTLXe has a layered .NET SDK:
- **UtlxClient** — low-level wire client (proto over stdin/stdout). Updated for EF17 (per-input schemas) and EF18 (`request_id` pipe dispatch).
- **UtlxEngine.Sdk** — enterprise SDK: `IUtlxEngine`, `IBundleStore`, `TransformResult`, OpenTelemetry spans.
- **UtlxEngine.BizTalk** — BizTalk pipeline component: `UtlxTransformComponent` (IComponent pattern), `UtlxEngineHost` (process-wide singleton).
- **UtlxEngine.LogicApps** — Logic Apps Standard: DI registration, `WorkflowActionResult`, sample custom functions.

This feature packages the SDK for two specific Microsoft hosts — BizTalk Server pipelines and Logic Apps Standard custom functions — and addresses the gaps identified in the BizTalk replacement study.

## Timeline driver

- **September 30, 2026** — Azure Service Bus SBMP protocol retires. BizTalk deployments using the default adapter break.
- **April 2030** — BizTalk 2020 extended support ends.

The market is shopping now. The SDK must be ready before September 2026 to capture the SBMP migration wave.

## Deliverables

### 1. NuGet-bundled utlxe native binary

The customer installs the SDK with `dotnet add package UtlxEngine.Sdk` — no JRE, no separate download, no `JAVA_HOME`.

The NuGet package contains the GraalVM native binary per runtime identifier:

```
lib/
  net472/UtlxEngine.Sdk.dll
  net8.0/UtlxEngine.Sdk.dll
runtimes/
  win-x64/native/utlxe.exe
  linux-x64/native/utlxe
  linux-arm64/native/utlxe
  osx-x64/native/utlxe
  osx-arm64/native/utlxe
```

The SDK discovers the native binary from the NuGet package layout at runtime:

```csharp
var utlxePath = Path.Combine(
    Path.GetDirectoryName(typeof(UtlxEngine).Assembly.Location),
    "..", "..", "runtimes", RuntimeInformation.RuntimeIdentifier, "native",
    RuntimeInformation.IsOSPlatform(OSPlatform.Windows) ? "utlxe.exe" : "utlxe"
);
```

**Build pipeline change:** The existing `.github/workflows/native-build.yml` builds `utlx` (CLI) as a native binary. It must also build `utlxe` (engine) as a native binary for the same 5 RIDs. The native binary is the engine in stdio-proto mode — same code, same compilation, different entry point.

### 2. Subprocess pool (not per-call spawn)

The SDK spawns `utlxe` once and multiplexes requests via `request_id` (EF18). Each call gets a unique `request_id` for pipe-level response dispatch — safe under fan-out where multiple messages share the same `correlation_id`.

```csharp
public class UtlxEngine : IUtlxEngine, IDisposable
{
    private Process _process;
    private readonly ConcurrentDictionary<string, TaskCompletionSource<ExecuteResponse>> _pending;
    private readonly SemaphoreSlim _writeLock = new(1, 1);

    public UtlxEngine(UtlxEngineOptions options)
    {
        _process = StartProcess(options);
        _pending = new();
        Task.Run(ReadLoop);  // background thread reads responses, routes by correlation_id
    }

    public async Task<TransformResult> TransformAsync(
        byte[] input, string transformationId, string contentType,
        IDictionary<string, string>? parameters = null,
        CancellationToken ct = default)
    {
        var correlationId = Guid.CreateVersion7().ToString();
        var tcs = new TaskCompletionSource<ExecuteResponse>();
        _pending[correlationId] = tcs;

        var request = new ExecuteRequest
        {
            TransformationId = transformationId,
            Payload = ByteString.CopyFrom(input),
            ContentType = contentType,
            CorrelationId = correlationId,
            MessageId = Guid.CreateVersion7().ToString(),
        };
        if (parameters != null)
            foreach (var (k, v) in parameters)
                request.Parameters.Add(k, v);

        await WriteFrameAsync(request, ct);
        var response = await tcs.Task.WaitAsync(ct);
        return ToTransformResult(response);
    }

    private async Task ReadLoop()
    {
        while (!_disposed)
        {
            var response = await ReadFrameAsync<ExecuteResponse>();
            if (_pending.TryRemove(response.CorrelationId, out var tcs))
                tcs.SetResult(response);
        }
    }

    // Subprocess death → automatic respawn + bundle reload
    private void OnProcessExited(object sender, EventArgs e)
    {
        if (!_disposed)
        {
            _process = StartProcess(_options);
            Task.Run(ReloadBundles);
            Task.Run(ReadLoop);
        }
    }
}
```

Key properties:
- One subprocess per `UtlxEngine` instance (typically one per application)
- Multiplexed via `request_id` (EF18) — thousands of concurrent requests on one pipe, fan-out safe
- Automatic respawn on subprocess death, with bundle reload
- Write lock prevents interleaved frames on stdout

### 3. IBundleStore abstraction

The SDK needs to load bundles from different sources depending on the host:

```csharp
public interface IBundleStore
{
    Task<BundleData> FetchAsync(string bundleId, CancellationToken ct = default);
    Task<BundleVersion> GetVersionAsync(string bundleId, CancellationToken ct = default);
}

// Implementations
public class EmbeddedBundleStore : IBundleStore { ... }    // Bundle in deployment package
public class BlobBundleStore : IBundleStore { ... }        // Azure Blob Storage
public class HttpBundleStore : IBundleStore { ... }        // UTLXe Admin API (sidecar)
public class DaprBundleStore : IBundleStore { ... }        // Dapr binding
public class FileBundleStore : IBundleStore { ... }        // Local filesystem
```

| Implementation | Use case |
|---|---|
| `EmbeddedBundleStore` | Bundle shipped inside the deployment package (BizTalk MSI, Logic Apps zip). Loaded from assembly resources or a relative path. |
| `BlobBundleStore` | Bundle fetched from Azure Blob Storage at first call. Uses Azure.Storage.Blobs SDK with managed identity. |
| `HttpBundleStore` | Bundle fetched from a running UTLXe Admin API (sidecar pattern). `GET /admin/bundle`. |
| `DaprBundleStore` | Bundle fetched via Dapr binding (Pattern C from bootstrap doc). |
| `FileBundleStore` | Bundle loaded from a local file path. For development and testing. |

The `UtlxEngine` constructor accepts an `IBundleStore`:

```csharp
var engine = new UtlxEngine(new UtlxEngineOptions
{
    BundleStore = new BlobBundleStore("https://myaccount.blob.core.windows.net/bundles"),
    BundleId = "orders-v3"
});
```

### 4. BizTalk shim assembly (`UtlxEngine.BizTalk`)

A ready-made `IComponent` implementation for BizTalk pipeline stages:

```csharp
[ComponentCategory(CategoryTypes.CATID_PipelineComponent)]
[ComponentCategory(CategoryTypes.CATID_Any)]
[Guid("A1B2C3D4-5678-9ABC-DEF0-111111111111")]
public class UtlxTransformComponent
    : IBaseComponent, IComponent, IComponentUI, IPersistPropertyBag
{
    // Design-time properties (set in BizTalk Administration Console)
    public string BundleId { get; set; }
    public string TransformationId { get; set; }

    public IBaseMessage Execute(IPipelineContext ctx, IBaseMessage inMsg)
    {
        var engine = UtlxEngineHost.Shared;  // process-wide singleton
        using var input = inMsg.BodyPart.GetOriginalDataStream();
        var inputBytes = ReadStream(input);
        var contentType = DetectContentType(inMsg);

        var result = engine.TransformAsync(
            inputBytes,
            TransformationId,
            contentType,
            ContextToParameters(inMsg.Context)  // BizTalk context → $params.*
        ).GetAwaiter().GetResult();

        if (!result.Success)
            throw new PipelineComponentException(
                $"UTLXe transformation failed: {result.ErrorCode} — {result.Error}");

        inMsg.BodyPart.Data = new MemoryStream(result.Output);
        ApplyResultToContext(inMsg.Context, result);
        return inMsg;
    }

    private IDictionary<string, string> ContextToParameters(IBaseMessageContext ctx)
    {
        var parameters = new Dictionary<string, string>();
        for (int i = 0; i < ctx.CountProperties; i++)
        {
            string name, ns;
            var value = ctx.ReadAt(i, out name, out ns);
            if (value != null)
                parameters[$"{ns}#{name}"] = value.ToString();
        }
        return parameters;
    }
}
```

BizTalk customer experience:
1. Install `UtlxEngine.BizTalk` NuGet into GAC
2. Open BizTalk Administration → Pipeline → Add Stage → Select `UtlxTransformComponent`
3. Set `BundleId` = "orders-v3", `TransformationId` = "to-canonical"
4. Deploy — XSLT map replaced with UTLXe transformation

### 5. Logic Apps Standard helper package (`UtlxEngine.LogicApps`)

DI registration and workflow-friendly result types:

```csharp
// In Startup.cs (or Program.cs for .NET 8)
services.AddUtlxEngine(options =>
{
    options.BundleStore = new BlobBundleStore(blobConnectionString);
    options.BundleId = "orders-v3";
});

// Custom function
public class TransformOrder
{
    private readonly IUtlxEngine _engine;

    public TransformOrder(IUtlxEngine engine) => _engine = engine;

    [FunctionName("TransformOrder")]
    public async Task<WorkflowActionResult> Run(
        [WorkflowActionTrigger] string inputXml,
        string transformationId)
    {
        var result = await _engine.TransformAsync(
            Encoding.UTF8.GetBytes(inputXml),
            transformationId,
            "application/xml");

        return new WorkflowActionResult
        {
            Success = result.Success,
            Output = Encoding.UTF8.GetString(result.Output),
            ContentType = result.OutputContentType,
            ErrorCode = result.ErrorCode?.ToString(),
            DurationMs = result.DurationMs
        };
    }
}
```

`WorkflowActionResult` is serializable into the Logic Apps designer — flat JSON with string fields, no proto types leaking.

### 6. Dual target framework (.NET 4.7.2 + .NET 8)

BizTalk runs on .NET Framework. Logic Apps Standard supports both .NET Framework 4.7.2 and .NET 8.

```xml
<!-- UtlxEngine.Sdk.csproj -->
<PropertyGroup>
  <TargetFrameworks>net472;net8.0</TargetFrameworks>
</PropertyGroup>
```

```xml
<!-- UtlxEngine.BizTalk.csproj -->
<PropertyGroup>
  <TargetFramework>net472</TargetFramework>  <!-- BizTalk is .NET Framework only -->
</PropertyGroup>
```

```xml
<!-- UtlxEngine.LogicApps.csproj -->
<PropertyGroup>
  <TargetFrameworks>net472;net8.0</TargetFrameworks>  <!-- Both hosts supported -->
</PropertyGroup>
```

The SDK core (`UtlxEngine.Sdk`) must work on both frameworks. Proto serialization uses `Google.Protobuf` which supports both. Subprocess management uses `System.Diagnostics.Process` which works on both.

### 7. OpenTelemetry integration

Each `TransformAsync` call is a span:

```csharp
using var activity = ActivitySource.StartActivity("utlxe.transform");
activity?.SetTag("utlxe.transformation_id", transformationId);
activity?.SetTag("utlxe.bundle_id", bundleId);
activity?.SetTag("utlxe.content_type", contentType);

var result = await ExecuteAsync(request);

activity?.SetTag("utlxe.success", result.Success);
activity?.SetTag("utlxe.duration_us", result.Metrics?.ExecuteDurationUs);
activity?.SetTag("utlxe.error_code", result.ErrorCode.ToString());
```

The `traceparent` from the current `Activity` is passed to `utlxe` via the proto field (EF04). The engine propagates it to output bindings. End-to-end trace: caller → SDK → utlxe subprocess → output.

## NuGet packages

| Package | Target | Contains |
|---------|--------|----------|
| `UtlxEngine.Sdk` | net472 + net8.0 | Core SDK: `IUtlxEngine`, subprocess management, proto framing, `IBundleStore` |
| `UtlxEngine.Sdk.Runtime.win-x64` | — (native) | `utlxe.exe` native binary for Windows x64 |
| `UtlxEngine.Sdk.Runtime.linux-x64` | — (native) | `utlxe` native binary for Linux x64 |
| `UtlxEngine.Sdk.Runtime.linux-arm64` | — (native) | `utlxe` native binary for Linux ARM64 |
| `UtlxEngine.Sdk.Runtime.osx-x64` | — (native) | `utlxe` native binary for macOS x64 |
| `UtlxEngine.Sdk.Runtime.osx-arm64` | — (native) | `utlxe` native binary for macOS ARM64 |
| `UtlxEngine.BizTalk` | net472 | BizTalk `IComponent` shim |
| `UtlxEngine.LogicApps` | net472 + net8.0 | Logic Apps DI helpers, `WorkflowActionResult`, sample functions |

The runtime packages are separated so the customer only downloads the binary for their platform. The SDK package references the appropriate runtime package via NuGet's RID-specific dependency mechanism.

## Wire-protocol parity

The SDK uses stdin/stdout protobuf. The HTTP API uses REST/JSON. Both must produce identical response shapes (architecture principle, documented in `utlxe-engine-architecture.md`).

Specifically:
- `ErrorCode` enum values are used as strings in HTTP JSON (`"TRANSFORMATION_NOT_FOUND"`) and as proto enum values in the SDK (`ErrorCode.TransformationNotFound` in C#)
- `parameters` map is available on both transports — HTTP via JSON body field, proto via `map<string, string>`
- `TransformResult` in the SDK maps 1:1 to `ExecuteResponse` in the proto and to the JSON response body in HTTP
- `OutputMetadata` is a typed object in C# (`result.OutputMetadata.ApplicationId`) and a nested JSON object in HTTP (`{"output_metadata": {"application_id": "PO-12345"}}`)
- `MetadataForwarding` is an enum in C# and a string in HTTP
- `tracestate` is passed alongside `traceparent` on all transports (W3C Trace Context completeness)

### SDK `TransformResult` — full field mapping

```csharp
public class TransformResult
{
    public bool Success { get; set; }
    public byte[] Output { get; set; }
    public string OutputContentType { get; set; }
    public string Error { get; set; }
    public ErrorClass ErrorClass { get; set; }
    public ErrorPhase ErrorPhase { get; set; }
    public ErrorCode ErrorCode { get; set; }
    public List<ValidationError> ValidationErrors { get; set; }
    public long DurationUs { get; set; }

    // Messaging triad
    public string MessageId { get; set; }        // new UUIDv7 for output
    public string CorrelationId { get; set; }    // echoed from request
    public string CausationId { get; set; }      // = input's MessageId

    // Forwarded properties
    public Dictionary<string, string> Metadata { get; set; }

    // Rule-emitted business metadata (from OutputMetadata proto message)
    public OutputMetadataResult OutputMetadata { get; set; }
}

public class OutputMetadataResult
{
    public string ApplicationId { get; set; }       // PO number, IDoc number
    public string MessageType { get; set; }         // "CanonicalOrder.v3"
    public string CustomStatus { get; set; }        // "FALLBACK_RULE_USED"
    public Dictionary<string, string> CustomIdentifiers { get; set; }
    public string Sender { get; set; }
    public string Receiver { get; set; }
}
```

The BizTalk shim maps `OutputMetadata` to BizTalk context properties:

```csharp
// In UtlxTransformComponent.Execute()
if (result.OutputMetadata != null)
{
    if (result.OutputMetadata.ApplicationId != null)
        inMsg.Context.Write("ApplicationId", "utlxe", result.OutputMetadata.ApplicationId);
    foreach (var (key, value) in result.OutputMetadata.CustomIdentifiers)
        inMsg.Context.Write(key, "utlxe", value);
}
```

The CPI adapter maps `OutputMetadata` to SAP exchange properties:

```java
// In CPI custom adapter
if (response.hasOutputMetadata()) {
    exchange.setProperty("SAP_ApplicationID", response.getOutputMetadata().getApplicationId());
    exchange.setProperty("SAP_MessageType", response.getOutputMetadata().getMessageType());
    for (var entry : response.getOutputMetadata().getCustomIdentifiersMap().entrySet()) {
        messageLog.addCustomHeaderProperty(entry.getKey(), entry.getValue());
    }
}
```

See [Proto Reference](../../docs/sdk/proto-reference.md) for the complete proto documentation.

## Build pipeline changes

| Change | File |
|--------|------|
| Build `utlxe` native binary for 5 RIDs | `.github/workflows/native-build.yml` |
| Package native binaries into NuGet runtime packages | New: `.github/workflows/nuget-publish.yml` |
| Build SDK + BizTalk + LogicApps packages | New: `sdk/dotnet/UtlxEngine.sln` |
| Publish to NuGet.org | New: `.github/workflows/nuget-publish.yml` |

## Effort estimate

| Task | Effort |
|------|--------|
| **Native binary** | |
| Build `utlxe` native for 5 RIDs (extend existing pipeline) | 1 day |
| NuGet runtime package structure | 1 day |
| **SDK core** | |
| Subprocess pool with correlation multiplexing | 2 days |
| IBundleStore abstraction + 5 implementations | 2 days |
| OpenTelemetry instrumentation | 1 day |
| Dual target (net472 + net8.0) build + testing | 1 day |
| **BizTalk shim** | |
| IComponent implementation | 1 day |
| BizTalk context → parameters mapping | 0.5 day |
| GAC installation + BizTalk admin integration testing | 1 day |
| **Logic Apps** | |
| DI registration + WorkflowActionResult | 0.5 day |
| Sample functions (Transform, Validate) | 0.5 day |
| Logic Apps Standard deployment testing | 1 day |
| **Testing + documentation** | |
| SDK integration tests (all platforms) | 2 days |
| NuGet package verification (install + run on clean machine) | 1 day |
| Documentation (SDK guide, BizTalk migration guide, Logic Apps guide) | 2 days |
| **Total** | **~17 days** |

## Relationship to other features

- **EF03** (Admin API): `HttpBundleStore` calls the Admin API to fetch bundles
- **EF04** (tracing): `traceparent` passed from SDK → proto → engine → output
- **EF07** (parallel transports): if the customer runs UTLXe as a shared service (not subprocess), the SDK can switch from stdio-proto to gRPC as the transport
- **Proto changes**: `parameters` (field 10), `ErrorCode` enum, `LoadBundle`/`UnloadBundle` RPCs — all added in this session, ready for SDK consumption

## Implementation status

### Layered architecture (implemented)

The SDK is split into four NuGet-ready projects layered on top of the existing low-level client:

```
wrappers/dotnet/src/
  UtlxClient/                          ← low-level wire client (pre-existing, updated for EF17+EF18)
  UtlxEngine.Sdk/                      ← enterprise SDK (NEW)
  UtlxEngine.BizTalk/                  ← BizTalk pipeline shim (NEW)
  UtlxEngine.LogicApps/                ← Logic Apps Standard helpers (NEW)
```

**UtlxClient** (low-level, pre-existing) — direct proto access, subprocess management, varint framing. Updated for EF17 (schema pass-through on `LoadTransformationAsync`) and EF18 (`request_id` for pipe dispatch instead of `correlation_id` — fixes fan-out bug).

**UtlxEngine.Sdk** (enterprise, new) — wraps UtlxClient:
- `IUtlxEngine` interface: `TransformAsync`, `TransformMultiAsync`, `LoadBundleAsync`, `HealthAsync`
- `TransformResult` / `OutputMetadataResult` / `HealthResult` — plain .NET types, no proto leakage
- `IBundleStore` abstraction with 3 implementations:
  - `FileBundleStore` — local filesystem (dev/test)
  - `HttpBundleStore` — UTLXe Admin API (sidecar pattern)
  - `EmbeddedBundleStore` — assembly resources or relative path (BizTalk MSI, Logic Apps zip)
- OpenTelemetry `Activity` spans on every transform call
- JSON envelope builder for multi-input (`TransformMultiAsync`)
- Auto-bundle-load on startup

**UtlxEngine.BizTalk** (host shim, new):
- `UtlxEngineHost` — process-wide singleton, auto-init from env vars (`UTLXE_JAR_PATH`, `UTLXE_BUNDLE_PATH`, `UTLXE_BUNDLE_ID`), `AppDomain.DomainUnload` shutdown
- `UtlxTransformComponent` — BizTalk `IComponent` pattern: design-time properties (`BundleId`, `TransformationId`, `ContentType`, `PassContextAsParameters`), context→parameters mapping, output→properties mapping

**UtlxEngine.LogicApps** (host helpers, new):
- `AddUtlxEngine()` — DI registration extension method
- `WorkflowActionResult` — flat JSON result type for Logic Apps designer (no proto, no binary)
- `UtlxWorkflowFunctions` — sample custom functions: `TransformJsonAsync`, `TransformXmlAsync`, `TransformAsync`

### What changed from the original design

| Aspect | Original design | Implementation | Why |
|---|---|---|---|
| Target frameworks | net472 + net8.0 | net9.0 + net10.0 | net472 support requires netstandard2.0 on SDK — deferred to NuGet packaging step |
| Subprocess pool | ConcurrentDictionary + correlation dispatch | UtlxeClient already does this | EF18 switched dispatch from `correlationId` to `request_id` — fan-out safe |
| IBundleStore | 5 implementations | 3 implemented (File, Http, Embedded) | BlobBundleStore and DaprBundleStore deferred — need Azure.Storage.Blobs and Dapr.Client dependencies |
| NuGet native binary | GraalVM native binary per RID | Deferred | Requires GraalVM native-image build pipeline for utlxe |
| BizTalk IComponent | Full BizTalk SDK interfaces | Pattern class (no BizTalk SDK dependency) | BizTalk SDK references added at customer's build time from their BizTalk installation |

### Remaining work

| Task | Status | Notes |
|---|---|---|
| SDK core (`UtlxEngine.Sdk`) | **Done** | IUtlxEngine, TransformResult, IBundleStore, OTel |
| BizTalk shim (`UtlxEngine.BizTalk`) | **Done** | UtlxEngineHost, UtlxTransformComponent |
| Logic Apps helpers (`UtlxEngine.LogicApps`) | **Done** | DI, WorkflowActionResult, sample functions |
| `BlobBundleStore` | Pending | Needs `Azure.Storage.Blobs` dependency |
| `DaprBundleStore` | Pending | Needs `Dapr.Client` dependency |
| GraalVM native binary build | Pending | `.github/workflows/native-build.yml` for 5 RIDs |
| NuGet runtime packages | Pending | Per-RID native binary packaging |
| net472 dual-target | Pending | Add `netstandard2.0` to SDK, test on .NET Framework |
| Integration tests | Pending | End-to-end: SDK → subprocess → transform → result |
| NuGet publish pipeline | Pending | `.github/workflows/nuget-publish.yml` |

---

*Feature EF08. May 2026. SDK core, BizTalk shim, and Logic Apps helpers implemented.*
*Key insight: the SDK already exists. The work is packaging it for two specific Microsoft hosts (BizTalk pipelines and Logic Apps Standard) and making the install experience zero-friction (NuGet + native binary, no JRE).*
