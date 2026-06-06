# EF22: Polyglot SDK & Embedding Strategy (one proto, four languages)

> **Status:** strategy / design. Defines how UTLXe is embedded and called from other languages —
> a coordinated set of SDKs (**.NET, Go, Java/Kotlin, Python**) over the **single proto wire
> contract**, complementing the async Dapr offering with a synchronous/embedded path.
>
> **Depends on / pairs with:** the proto contract `proto/utlxe/v1/utlxe.proto`
> (`docs/sdk/proto-reference.md`), **EF08** (.NET SDK + BizTalk/Logic Apps — already implemented),
> **EF17** (proto schema pass-through), **EF18** (`request_id` pipe dispatch), **EF07** (parallel
> transports), **EF03** (Bundle Management API), **EF06/EF10** (Dapr messaging), and Open-M (ch44,
> the controller that consumes these SDKs).

## Summary

UTLXe already exposes a **single proto wire contract** as the source of truth for **all**
transports (gRPC, stdio-proto, HTTP/JSON, in-process JVM). One SDK — **.NET** (EF08) — is shipped.
EF22 makes the strategy explicit: **finish a coordinated four-language SDK set**, all **generated
from the one proto** plus a thin transport and a consistent ergonomic facade, kept in the `utlx`
repo (shared with Open-M). This turns utlxe from "a message processor" into a **polyglot-embeddable
transformation engine**, and is the *synchronous/embedded complement* to the async Dapr offering.

Because the contract is proto, the marginal cost of each additional SDK is **"thin transport +
idiomatic facade,"** not "design a protocol + build a client" — which is what makes "all four"
affordable rather than a sprawling effort.

## Problem

- UTLXe must be callable from the languages enterprises actually integrate in — **Go** (Open-M
  controller, cloud-native), **C#/.NET** (BizTalk/Logic Apps), **Java/Kotlin** (enterprise JVM),
  **Python** (data/ML, the conformance runner).
- Today only the **.NET** SDK exists (`wrappers/dotnet/UtlxEngine.Sdk`). Go / Java / Python are
  absent, so non-.NET teams must hand-roll CLI/HTTP plumbing or round-trip through messaging.
- Even for the JVM, **in-process** embedding has drawbacks (classloader/version conflicts, no crash
  isolation, GraalVM-native friction); a piped/gRPC SDK gives **process isolation** — often the
  better default even when in-process is technically possible.

## The foundation (already in place)

`proto/utlxe/v1/utlxe.proto` is the **single wire contract**; per `docs/sdk/proto-reference.md`:

> *"the single wire-protocol contract for all UTLXe transports: gRPC, stdio-proto, HTTP (JSON
> derived from proto), and in-process JVM calls. The proto is the source of truth — all transports
> produce identical request/response shapes."*

- **stdio-proto** (the piped-I/O path): messages wrapped in `StdioEnvelope` with **varint-delimited
  framing** — a *formalized* embedding contract, not ad-hoc.
- **EF08 .NET SDK** is the reference: `UtlxClient` (low-level proto-over-stdin/stdout) +
  `UtlxEngine.Sdk` (enterprise facade: `IUtlxEngine`, `IBundleStore`, `TransformResult`,
  OpenTelemetry), packaged as `dotnet add package UtlxEngine.Sdk` (no JRE).

This foundation is exactly what makes a coordinated multi-language set cheap.

## Goals
- A **coordinated four-language SDK set** (.NET, Go, Java/Kotlin, Python), generated from the one
  proto, with a **consistent conceptual API** across languages.
- **Process-isolated embedding** via stdio-proto as the default transport; gRPC/HTTP where demanded.
- SDKs live **in `utlx`** (`wrappers/<lang>/`), shared with Open-M and available to external users.
- The proto treated as a **versioned product contract**, conformance-tested across all transports.

## Non-Goals
- The async **Dapr/messaging** offering itself (EF06/EF10) — EF22 is the *synchronous/embedded*
  complement, not a replacement.
- Bundle *management* surfaces (EF03) and the IDE's use of a daemon API (IF19) — separate.
- Language-specific build tooling beyond what each package registry requires.

## Why "all four" is affordable

Each SDK is three layers, and only the third is real per-language work:

1. **Generated message types** — `protoc` emits Go, Java, C#, Python from `utlxe.proto`. Free.
2. **Thin transport** — the stdio-proto envelope (varint framing), or a gRPC client, or HTTP. Small;
   the .NET `UtlxClient` is the reference implementation to mirror.
3. **Ergonomic facade** — the idiomatic, hand-written API (modeled on `UtlxEngine.Sdk`). The only
   substantive work, and it should be **consistent** across languages.

→ The cost of SDK #2–4 is "thin transport + facade," not "invent a protocol."

## How it underpins the Dapr offering

| Mode | Transport | Topology |
|---|---|---|
| **Dapr offering** (EF06/EF10) | Service Bus / Event Hub | **async**, decoupled, cloud-native messaging |
| **EF22 SDKs** | stdio-proto / gRPC / HTTP | **synchronous**, **embedded** in a host process |

Together they cover the **full integration spectrum**: a Go controller (Open-M), a .NET BizTalk
pipeline, a Python job, or a Java microservice can **embed** utlxe synchronously, while the *same*
engine also runs as a Dapr message processor. utlxe becomes a polyglot-embeddable engine, not only
a message consumer — broadening the addressable market. (Dapr ships SDKs in the same four
languages, so these sit naturally in that stack.)

Market pull is already concrete: **.NET** rides the BizTalk/SBMP-retirement wave (EF08, Sep 2026);
**Go** is required for Open-M. Three of the four have independent demand drivers.

## Sequencing

| # | SDK | Driver | Status |
|---|---|---|---|
| 1 | **.NET** | BizTalk/Logic Apps; SBMP retirement (Sep 30 2026) | **done** (EF08) — keep hardening |
| 2 | **Go** | Open-M controller; cloud-native embedding | next — highest leverage |
| 3 | **Java/Kotlin** | enterprise JVM (piped/gRPC; in-process as advanced option) | after Go |
| 4 | **Python** | data/ML; reuse for the conformance runner | last |

## The consistent facade API (design once, mirror everywhere)

Define **one conceptual SDK API** and mirror it idiomatically per language (the .NET facade is the
template):
- **Engine** — `transform(name, inputs…) -> TransformResult` (sync), plus streaming where supported.
- **Bundle store** — list/get transformations & schemas (read; write where the host allows).
- **Result** — output payload + diagnostics + timing; errors as typed codes (the proto enum,
  serialized as strings over HTTP).
- **Observability** — OpenTelemetry spans + `request_id` (EF18) propagation.
- **Transport selection** — stdio-proto (default, embedded) · gRPC (service) · HTTP (simple).

Keeping these aligned prevents four divergent SDK designs.

## Costs & risks (the honest part)
- **The load is facades + packaging, not the protocol.** Four registries (NuGet, Maven/Gradle, Go
  modules, PyPI), four CI lanes, four release cadences, four doc/example sets — ongoing.
- **Proto stability is now a product contract.** It is `v1`-namespaced; breaking changes ripple to
  *all* SDKs. Version deliberately and **conformance-test every transport against the proto**
  (reuse the existing conformance infra).
- **API consistency** across languages must be actively maintained (one conceptual API, above).
- **Transport scope creep** — don't make every SDK do stdio + gRPC + HTTP on day one; start with
  stdio-proto (embed) and add per demand.

## Layout
```
wrappers/
├─ dotnet/   UtlxEngine.Sdk            (EF08 — done)
├─ go/       (new)
├─ jvm/      (new — Java/Kotlin)
└─ python/   (new)
```
All generated from `proto/utlxe/v1/utlxe.proto`; each = generated stubs + thin transport + facade.

## Open decisions
1. **Resourcing** — can the team sustain four package lanes? (generation helps; facades/packaging
   are the recurring load.)
2. **Transport scope per SDK** — stdio-proto first for all; gRPC/HTTP only where demanded?
3. **Facade API freeze** — design and approve the single conceptual API before building Go/Java/Python.
4. **Proto governance** — who owns `utlxe.proto` versioning; what's the deprecation policy.

## References
- `proto/utlxe/v1/utlxe.proto` · `docs/sdk/proto-reference.md` — the wire contract.
- **EF08** — .NET SDK, BizTalk shim, Logic Apps (the reference SDK).
- **EF17** — proto schema pass-through · **EF18** — `request_id` dispatch · **EF07** — parallel transports.
- **EF03** — Bundle Management API · **EF06/EF10** — Dapr messaging (the async complement).
- **ch34** — SDKs & Wrapper Libraries (book) · **ch44** — Open-M (the controller consuming these SDKs).
