
# UTL-X vs Nango

This guide compares UTL-X with [Nango](https://github.com/NangoHQ/nango). Unlike
the other documents in this folder (which compare UTL-X with peer *transformation
languages* such as DataWeave, jq, JSONata, XSLT, and CEL), this one compares UTL-X
with a tool in an **adjacent category**.

## Overview — they solve different problems

> **TL;DR:** Nango and UTL-X are **complementary, not competitors.** Nango is an
> API-integration *platform* (managed auth, proxying, and syncs to 800+ SaaS APIs).
> UTL-X is a *transformation language* (map any data between XML/JSON/CSV/YAML/…
> declaratively). Nango gets the data to/from third-party APIs; UTL-X reshapes it.
> Many systems would use **both**.

| | Nango | UTL-X |
|---|---|---|
| **Category** | API-integration platform | Data-transformation language |
| **One-liner** | "Build product integrations with AI" | "Format-agnostic functional transformation language" |
| **Primary job** | Connect to & sync third-party APIs (managed OAuth, token refresh, proxy, retries, webhooks) | Convert/restructure data between formats with one transformation |
| **Unit of work** | A connection + sync/action to a specific API | A `.utlx` transformation |
| **How you define logic** | TypeScript functions (hand-written or AI-generated) | Declarative UTL-X transformation |
| **Runtime** | TypeScript / Node.js | JVM, JavaScript, Native (GraalVM) |
| **Deployment** | Cloud (Nango Cloud) or self-hosted | Embedded library, CLI, or daemon (utlxd / UTLXe) |
| **Scale metric** | 800+ supported APIs | All formats via one language + plugin formats |
| **License** | Elastic License (open core; paid tiers) | AGPL-3.0 (fully open source) |
| **Used by** | Replit, Ramp, Mercor, "hundreds more" (per Nango) | Open-source / self-hosted |

*(Nango facts above are from its public README; verify against
https://github.com/NangoHQ/nango for the current state.)*

## What each one is

### Nango

Per its README, Nango's pitch is **"Build product integrations with AI"**, built
around three pillars:

- **Auth** — "Managed OAuth, API keys, and token refresh for 800+ APIs."
- **Proxy** — authenticated requests with credential injection and rate-limit handling.
- **Functions** — deploy TypeScript integration logic with built-in observability
  and retries.

It targets product teams that need to ship and maintain **many SaaS integrations**
(syncs, actions, webhooks, API unification, tool-calling for AI agents) without
hand-rolling auth and infrastructure for each provider. Integration logic is
written **as TypeScript code** (or generated from natural language by its AI
builder) — "readable code you can review, edit, and version control."

### UTL-X

UTL-X is a **format-agnostic functional transformation language**: write a
transformation once and run it against XML, JSON, CSV, YAML, and other formats.
It is declarative (with optional XSLT-style template matching), strongly typed
with inference, and runs on multiple runtimes (JVM, JavaScript, GraalVM native).
Its concern is purely the **shape of data** — parsing an input into a Universal
Data Model (UDM), transforming it, and serializing to a target format. It does not
do connectivity, auth, scheduling, or API state.

## The key distinction: connectivity vs. transformation

```
            ┌──────────── Nango ────────────┐        ┌──── UTL-X ────┐
SaaS API ──▶│ OAuth · token refresh · proxy │──data──▶│ transform/map │──▶ your model
(Salesforce,│ sync · webhook · retries      │  (JSON) │ XML/JSON/CSV… │    (any format)
 HubSpot,…) └───────────────────────────────┘        └───────────────┘
            "get the data, reliably"                  "reshape the data, correctly"
```

- **Nango owns the edge**: talking to the third-party API, keeping credentials
  fresh, handling rate limits and webhooks, and persisting sync state.
- **UTL-X owns the shape**: turning whatever JSON/XML/CSV came back into the exact
  structure your system needs — and back again on the way out.

Nango *can* transform data inside its TypeScript functions (it's just code). UTL-X
*cannot* connect to APIs. So the overlap is narrow and one-directional: Nango's
"transform" step is general-purpose TypeScript; UTL-X is a specialized language
for exactly that step.

## Where they overlap (the transformation step)

Inside a Nango sync, you map a provider's payload to your model in TypeScript:

**Nango (TypeScript, inside a sync):**
```typescript
// Map a HubSpot contact to your unified model
const mapped = records.map(r => ({
  id: r.id,
  fullName: `${r.properties.firstname} ${r.properties.lastname}`,
  email: r.properties.email,
  createdAt: r.createdAt,
}));
await nango.batchSave(mapped, 'Contact');
```

**UTL-X (declarative, same mapping):**
```utlx
%utlx 1.0
input json
output json
---
$input.records |> map(r => {
  id: r.id,
  fullName: r.properties.firstname + " " + r.properties.lastname,
  email: r.properties.email,
  createdAt: r.createdAt
})
```

For simple field-mapping, TypeScript is perfectly fine. UTL-X earns its place when
the transformation is **complex, multi-format, or contract-driven**: XML/CSV/YAML
in the mix, XSLT-style template matching, schema-validated output, or the same
mapping reused across many integrations and runtimes.

## Feature comparison

| Capability | Nango | UTL-X |
|---|---|---|
| Managed OAuth / API keys / token refresh | ✅ (core) | ❌ (out of scope) |
| Authenticated proxy, rate-limit handling | ✅ | ❌ |
| Sync state, incremental sync, webhooks | ✅ | ❌ |
| 800+ prebuilt API connectors | ✅ | ❌ |
| AI-generated integration code | ✅ | AI-assisted UTL-X generation (IDE) |
| Multi-format (XML/JSON/CSV/YAML) transform | ⚠️ via hand-written TS | ✅ (one language) |
| Declarative / template-matching transforms | ❌ (imperative TS) | ✅ |
| Strong typing + compile-time checks of the mapping | ⚠️ TS types | ✅ (type inference, schema validation) |
| Schema-to-schema / contract validation | ❌ | ✅ (Message Contract mode, USDL) |
| Runs embedded / CLI / non-Node runtimes | ❌ (Node) | ✅ (JVM, JS, Native) |
| Fully open source (no paid tier for features) | ⚠️ Elastic License, open core | ✅ AGPL-3.0 |

## When to choose which

✅ **Choose Nango when** the hard part is **connectivity**:
- You need to integrate with many SaaS APIs (Salesforce, HubSpot, Slack, …).
- You want managed OAuth/token-refresh, proxying, syncs, and webhooks out of the box.
- You're a product team shipping per-customer integrations and want it as TypeScript.

✅ **Choose UTL-X when** the hard part is the **data shape**:
- You transform between multiple formats (XML ⇄ JSON ⇄ CSV ⇄ YAML).
- Mappings are complex, declarative, contract-driven, or schema-validated.
- You need the same transformation across runtimes (JVM service, browser, native CLI),
  or embedded in a pipeline that has nothing to do with SaaS APIs (EDI, ERP, files).
- You want fully open-source, no vendor lock-in, no per-feature licensing.

✅ **Use both when** you ingest SaaS data *and* must reshape it precisely:
- **Nango** fetches and authenticates against the third-party API.
- **UTL-X** transforms the returned payload into your canonical model (and target
  payloads on the way out) — especially valuable when the canonical model is XML,
  EDI, or a validated schema rather than ad-hoc JSON.

## A combined architecture

```
  SaaS APIs ──(OAuth, proxy, sync)──▶  Nango  ──raw JSON──▶  UTL-X  ──▶  canonical model
                                          ▲                   │            (XML/JSON/EDI,
  outbound ◀──(proxy, actions)────────────┘◀──target payload──┘             schema-valid)
```

Nango handles *that we can talk to the API at all*; UTL-X handles *that the data is
in exactly the right shape*. They sit next to each other in the pipeline rather
than competing for the same box.

## Conclusion

Nango and UTL-X are **not alternatives** — they address different halves of an
integration. Nango is the right tool when your problem is *connecting to and
syncing third-party APIs at scale*; UTL-X is the right tool when your problem is
*transforming data between formats correctly and declaratively*. The most powerful
setup for SaaS-heavy data pipelines uses **Nango for connectivity and UTL-X for
transformation**.

If you are instead comparing UTL-X against another *transformation* technology,
see the peer comparisons in this folder: [DataWeave](vs-dataweave.md),
[jq](vs-jq.md), [JSONata](vs-jsonata.md), [XSLT](vs-xslt.md), [CEL](vs-cel.md).

---

*Nango details sourced from its public GitHub README and are subject to change;
confirm specifics at https://github.com/NangoHQ/nango. UTL-X details reflect this
repository.*
