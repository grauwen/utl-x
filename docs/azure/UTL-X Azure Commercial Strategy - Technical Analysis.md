# UTL-X Azure Commercial Strategy — Technical Analysis

**Glomidco / UTL-X — Realistic Assessment of Azure Integration Options**  
*Version 1.0 — April 2026*  
*Companion to: "UTL-X on Azure: Strategy & Deep Dive"*

---

## 1. Purpose

The strategy document describes four routes to bring UTL-X into the Azure ecosystem. This companion document provides the missing technical analysis: how does UTL-X's architecture actually fit each route, what are the real engineering costs, and what is the recommended commercial path.

---

## 2. The Core Technical Question: .NET or Not?

UTL-X is Kotlin/JVM. Azure's deepest integration points (Logic Apps built-in connectors, Azure Functions custom handlers) are .NET-native. This creates a fundamental tension that shapes every decision.

### Option A: Full .NET Reimplementation

| Factor | Assessment |
|--------|-----------|
| Engineering effort | 12-18 months, 2-3 senior engineers |
| What must be ported | Lexer, parser, AST, type checker, interpreter, 652 stdlib functions, UDM, 12 format parsers/serializers |
| Ongoing maintenance | Two codebases to keep in sync for every language change |
| Risk | Feature drift, subtle behavioral differences, double the bug surface |
| When it makes sense | Only if UTL-X becomes a product where Azure is the primary market and JVM is secondary |

**Verdict: Not advised.** The maintenance burden of two complete implementations would consume the engineering team. UTL-X is a full compiler + runtime — this is not a thin library that ports easily.

### Option B: JVM Subprocess from .NET (The UTLXe Pattern)

This is exactly what Open-M does: a Go wrapper communicates with UTLXe via stdio-proto. The same pattern works for .NET:

```
.NET Azure Function / Logic Apps Connector
    │
    │ stdio-proto (varint-delimited protobuf)
    │
    └── UTLXe subprocess (JVM)
         └── Compiled UTL-X programs in memory
```

| Factor | Assessment |
|--------|-----------|
| Engineering effort | 2-4 weeks (the hard part — UTLXe — is already done) |
| .NET side | Thin C# wrapper: spawn UTLXe, send LoadTransformation + Execute via pipe |
| Proto contract | Already defined (`proto/utlxe/v1/utlxe.proto`) — generate C# stubs with `protoc` |
| Performance | ~10-50μs per transform (same as Open-M) plus one-time JVM startup (~2s) |
| JVM startup | Mitigated by keeping UTLXe alive as long-running subprocess (same as Open-M) |

**Verdict: Recommended.** The UTLXe engine redesign (Phases A-E) was designed for exactly this pattern. The proto contract, transport abstraction, and multiplexed concurrency model are transport-agnostic — a C# wrapper is just another client.

### Option C: GraalVM Native Shared Library

Compile UTL-X to a native shared library (`.dll`/`.so`) callable from .NET via P/Invoke.

| Factor | Assessment |
|--------|-----------|
| Engineering effort | 4-8 weeks + ongoing GraalVM maintenance |
| Advantage | No JVM startup, in-process calls, lowest latency |
| Risk | GraalVM native-image restrictions (reflection, dynamic loading), complex debugging |
| Kotlin reflect + stdlib annotations | Major obstacle — needs extensive reflection configuration |
| gRPC/Netty dependencies | Would need to be stripped for the shared library build |

**Verdict: Interesting for Phase 4 (built-in connector) but premature.** The reflection-heavy UTL-X interpreter is not a natural fit for GraalVM native-image today. Revisit when GraalVM's Kotlin support matures.

---

## 3. How UTL-X I/O Maps to Each Azure Route

### Current UTL-X I/O model

```
utlx (CLI):     stdin → transform → stdout        (one-shot)
utlxe (Engine): protobuf pipe → transform → pipe   (long-running, multiplexed)
utlxe (gRPC):   RPC call → transform → response    (long-running, multiplexed)
```

### Route 4 — Azure Function (Lowest Barrier)

**I/O mapping: HTTP request/response → UTLXe Execute**

```
HTTP POST /api/transform
  Body: { template, content, inputFormat, outputFormat }
         │
         ▼
Azure Function (C# or Java)
         │
         │  Option A: Java Azure Function → call UTL-X TransformationService directly (in-process)
         │  Option B: C# Azure Function → UTLXe subprocess via stdio-proto
         │  Option C: C# Azure Function → UTLXe via gRPC (localhost)
         │
         ▼
HTTP 200
  Body: { output, format }
```

**Recommended: Option A (Java Azure Function).** Azure Functions supports Java natively. UTL-X is already a JVM library. No subprocess, no proto — just a direct in-process call to `TransformationService.transform()`. This is the simplest possible integration:

```java
@FunctionName("transform")
public HttpResponseMessage run(
    @HttpTrigger(name = "req", methods = {HttpMethod.POST}) HttpRequestMessage<TransformRequest> request) {
    
    var result = transformationService.transform(
        request.getBody().content,
        request.getBody().template,
        request.getBody().inputFormat,
        request.getBody().outputFormat
    );
    
    return request.createResponseBuilder(HttpStatus.OK)
        .body(new TransformResponse(result))
        .build();
}
```

**This can be deployed in days, not weeks.** Azure Functions Java runtime handles JVM lifecycle. No .NET needed.

### Route 2 — SaaS API (Fastest Revenue)

**I/O mapping: same as Route 4, but Glomidco-hosted**

The SaaS API is architecturally identical to Route 4, but deployed in Glomidco's Azure subscription behind API Management. The engine can be:

- A Java Azure Function App (simplest)
- UTLXe in a container (Azure Container Apps) with an HTTP gateway
- UTLXe running as a long-lived process with gRPC, fronted by an API gateway

**Recommended: Java Azure Function App behind Azure API Management (APIM).** APIM provides authentication (Entra ID / API key), rate limiting, usage metering, and the API endpoint that the SaaS Fulfillment API needs.

### Route 3 — Certified Power Platform Connector

**I/O mapping: OpenAPI wrapper around the SaaS API**

This is a pure API-definition exercise — no new engine work. The connector is just an OpenAPI spec pointing at the SaaS API from Route 2. The UTL-X engine is not involved in certification; only the API contract matters.

**Prerequisite: Route 2 must be live first** (the connector needs a stable API to point to).

### Route 1 — Logic Apps Built-in Connector (Deepest)

**I/O mapping: in-process .NET call → UTLXe subprocess**

This is the only route that requires .NET code. The built-in connector runs inside the Logic Apps .NET runtime. Options:

1. **C# wrapper → UTLXe subprocess via stdio-proto** (recommended)
   - Spawn UTLXe once at Logic App startup
   - Send LoadTransformation with the UTL-X template
   - For each workflow execution, send ExecuteRequest via the pipe
   - Performance: ~10-50μs per transform after warmup

2. **C# wrapper → UTLXe via gRPC on localhost**
   - Same as above but over gRPC instead of pipe
   - Slightly more overhead, but easier to debug and monitor

3. **GraalVM native shared library via P/Invoke** (future)
   - True in-process, no subprocess management
   - Requires solving GraalVM reflection issues

**The C# wrapper is thin** — ~200 lines: spawn process, send/receive protobuf, expose as IServiceOperationsProvider. The UTLXe engine does all the heavy lifting.

---

## 4. The IDE Question

### How Azure does XSLT mapping today

- **Data Mapper**: VS Code extension, visual drag-and-drop, generates XSLT 3.0
- **Liquid templates**: No visual editor — plain text editing, upload to Integration Account
- **Raw XSLT**: No IDE support — write in any XML editor, upload

Key observation: **Microsoft provides a visual IDE only for XSLT.** Everything else (Liquid, raw XSLT, expressions) is text-editor-based with no visual tooling.

### UTL-X IDE strategy for Azure

| Approach | Effort | Reach | Quality |
|----------|--------|-------|---------|
| VS Code extension for UTL-X | 4-8 weeks | All VS Code/Azure users | Good (syntax highlighting, diagnostics, snippets) |
| Existing UTL-X IDE (Theia) as SaaS | Already exists | Separate URL | Excellent (full IDE with live preview) |
| Data Mapper integration | Not feasible | N/A | N/A — Microsoft controls this |
| Azure Portal inline editor | 2-4 weeks | Portal users | Basic (Monaco editor + language server) |

**Recommended: VS Code extension (Phase 1) + hosted IDE link (Phase 2)**

**Phase 1: VS Code Extension** — This is the natural fit for Azure developers. They already use VS Code for Logic Apps authoring. A UTL-X VS Code extension would provide:
- Syntax highlighting for `.utlx` files
- Diagnostics/errors via Language Server Protocol (LSP) — reuse utlxd's existing LSP
- Snippets for common patterns (JSON→XML, CSV→JSON, etc.)
- "Test transformation" command (run selected UTL-X against sample input)
- Integration with Logic Apps project structure (`.utlx` files alongside workflow definitions)

The LSP server (utlxd) already exists. Wrapping it in a VS Code extension is bounded work — the language server protocol is the hard part, and it's done.

**Phase 2: Hosted IDE link** — For users who want the full IDE experience (live preview, format detection, multi-file projects), link to the existing UTL-X IDE as a hosted SaaS offering. This is analogous to how Azure Portal links out to the Azure Data Studio or VS Code for complex editing tasks.

**Do NOT try to integrate into the Data Mapper.** Microsoft's Data Mapper is tightly coupled to XSLT generation. Replacing or extending it would require Microsoft's cooperation and fundamental architectural changes to their tooling. It's a dead end.

---

## 5. Should You Build a .NET Implementation?

**No. Not now, possibly not ever.**

The reasoning:

1. **Azure Functions supports Java natively.** Routes 2 and 4 (the first revenue-generating routes) don't need .NET at all. A Java Azure Function calling UTL-X directly is the fastest path to market.

2. **Route 1 (built-in connector) needs .NET but not a .NET UTL-X.** It needs a thin C# wrapper (~200 lines) that talks to UTLXe via protobuf. This is the same architecture as Open-M: the smart wrapper pattern. The transformation engine stays in Kotlin; the integration shim is in the target platform's language.

3. **The maintenance cost is prohibitive.** UTL-X has 652 stdlib functions, 12 format parsers, a full compiler pipeline, and the UDM. Porting this to .NET and keeping it in sync would consume 50%+ of the engineering team's capacity indefinitely.

4. **A .NET port doesn't unlock any capability that the subprocess approach doesn't.** The only advantage is "true in-process" performance — which saves ~10μs per call. For a transformation that takes 50-500μs, the subprocess overhead is noise.

5. **If a .NET runtime becomes strategically necessary** (e.g., Microsoft requires it for a co-sell partnership), the GraalVM native library approach (Option C) is a better path than a full rewrite — it preserves the single Kotlin codebase while producing a .NET-callable binary.

---

## 6. Recommended Commercial Execution Plan

### Phase 1 — Weeks 1-4: Java Azure Function (Route 4)

**What:** Package UTL-X as a Java Azure Function App. One HTTP endpoint: `POST /api/transform`.

**Engineering:**
- Create `azure-function/` module in utl-x repo
- Thin Java wrapper around `TransformationService`
- ARM template / Bicep for one-click deployment
- Docker image for Azure Container Apps alternative
- README with Logic Apps integration example

**Revenue:** Free tier (AGPL) or BYOL with license key. Gets UTL-X into Azure customers' hands immediately.

**Why first:** Zero new infrastructure. Proves the integration works. Creates reference customers. Can be done in days.

### Phase 2 — Months 1-3: SaaS API + Marketplace (Route 2)

**What:** Host the Java Azure Function behind Azure API Management. Publish as SaaS offer on Microsoft Marketplace.

**Engineering:**
- Azure API Management configuration (auth, rate limiting, metering)
- SaaS Fulfillment API implementation (use Microsoft SaaS Accelerator)
- Tenant management + usage tracking
- Landing page with Entra ID SSO

**Revenue:** Tiered subscriptions (Developer €199/mo, Professional €799/mo, Enterprise custom) + metered overage. Microsoft takes 3%.

**Why second:** First paid revenue. MACC-eligible (customers can use Azure pre-committed spend). Builds the API that Route 3 needs.

### Phase 3 — Months 3-6: Power Platform Connector (Route 3) + VS Code Extension

**What:** Certified connector pointing at the SaaS API. VS Code extension for UTL-X authoring.

**Engineering:**
- OpenAPI spec (already sketched in strategy doc)
- Connector artifacts (icon, swagger, properties)
- VS Code extension wrapping utlxd LSP server
- Certification submission

**Revenue:** Indirect — increases SaaS API usage. Connector appears in Logic Apps / Power Automate gallery for 6M+ monthly Marketplace visitors.

**Why third:** Multiplies visibility of the SaaS API. The VS Code extension gives Azure developers a first-class authoring experience — better than what Microsoft offers for Liquid templates.

### Phase 4 — Months 6-12: Logic Apps Built-in Connector (Route 1)

**What:** NuGet package with C# wrapper → UTLXe subprocess. Runs in-process in Logic Apps Standard.

**Engineering:**
- C# IServiceOperationsProvider implementation (~200 lines)
- UTLXe bundled as a sidecar binary (JVM + fat JAR)
- NuGet packaging
- License validation at startup

**Revenue:** Commercial license per Logic Apps Standard instance (annual). Natural enterprise upsell — any organization using Logic Apps Standard with UTL-X needs this license.

**Why last:** Highest effort, smallest initial audience (Logic Apps Standard only). But highest long-term value — positions UTL-X as a peer of Microsoft's own Data Mapper.

---

## 7. Competitive Positioning: UTL-X vs Azure Data Mapper

| Capability | Azure Data Mapper | UTL-X |
|-----------|-------------------|-------|
| Visual IDE | VS Code drag-and-drop | VS Code extension + hosted IDE |
| Execution engine | XSLT 3.0 | UTL-X native (format-agnostic) |
| JSON native | No (converted to XML infoset) | Yes |
| CSV native | No | Yes |
| YAML native | No | Yes |
| XML native | Yes (via XSLT) | Yes |
| OData native | No | Yes |
| Multi-format in one transform | No | Yes (N-input mapping) |
| Schema validation | XSD only | JSON Schema, XSD, Avro, TSCH, OSCH, Protobuf |
| Stdlib functions | ~50 (XSLT 3.0 built-ins) | 652 across 18 categories |
| Learning curve | XSLT (steep for non-XML) | UTL-X (functional, consistent across formats) |
| Deployment | Logic Apps only | Any platform (Azure, AWS, GCP, on-premises) |

**Key messaging:** "UTL-X does everything Data Mapper does, plus JSON, CSV, YAML, and OData — natively, without converting to XML first."

---

## 8. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Microsoft builds native JSON/CSV mapper | Medium (2-3 years) | High | Move fast — establish installed base before Microsoft catches up |
| JVM cold start in Azure Functions | Low (Java runtime is mature) | Medium | Use Premium plan (pre-warmed instances) or Container Apps |
| AGPL deters enterprise adoption | Medium | Medium | Dual license: AGPL for open source, commercial for embedded use |
| Low initial marketplace traction | High (first 6 months) | Low | Expected — use co-sell motions and direct sales alongside |
| Logic Apps Standard adoption slower than expected | Medium | Low | Routes 2-3 work with Consumption plan too |

---

## 9. Summary

| Question | Answer |
|----------|--------|
| Should I build a .NET implementation? | **No.** Use Java Azure Functions (Routes 2, 4) and C# wrapper → UTLXe subprocess (Route 1). |
| How does UTL-X I/O fit Azure Functions? | **Perfectly.** Java Azure Function calls `TransformationService` directly. No .NET needed. |
| How should the IDE fit? | **VS Code extension** wrapping the existing utlxd LSP server. Not Data Mapper integration. |
| What about the existing UTL-X IDE? | Offer it as a **hosted SaaS** alongside the VS Code extension. Two tiers of editing experience. |
| What's the fastest path to revenue? | **Route 4 (Java Function) → Route 2 (SaaS + Marketplace).** Weeks, not months. |
| What's the highest-value long-term play? | **Route 1 (built-in connector)** — positions UTL-X as a Data Mapper alternative inside Logic Apps. |

---

*Analysis prepared for Glomidco. April 2026.*
