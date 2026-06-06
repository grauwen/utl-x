# IF19: Shared Bundle Management — API in utlxd + how the IDE drives it

> **Status:** design / decision record. Explores giving **utlxd** a bundle-management API (shared
> with **utlxe**) and three ways the IDE can present bundle management — to avoid rebuilding a
> large management UI natively in Theia from scratch.
>
> **Pairs with:** IF18 (menu & chrome — which this may *shrink*), IF14 (cloud hardening), IF03
> (bundle project model), EF03 (engine Bundle Management API), EF13 (engine admin web UI), and the
> [Bundle Format](../architecture/bundle-format.md) spec.

## Summary

Building bundle *management* (list/CRUD transformations, config, schemas, messaging, deploy, logs)
**natively in Theia** is a large amount of widget/layout work. Meanwhile utlxe **already has** a
bundle API (EF03) and a static admin UI (EF13). IF19 proposes a **shared, file-level Bundle
Management layer + REST API**, exposed by **utlxd** (for the IDE/workspace) and **utlxe** (for
production), and evaluates **three ways** the IDE consumes it:

- **Option A — Embedded web UI:** reuse an EF13-style static UI inside Theia's (freed) terminal slot.
- **Option B — Halfway: native Theia editors over the shared API** (no direct file writes from the IDE).
- **Option C — Baseline: native Theia editors writing files directly** (the IF18 direction).

A and B share one win: the **API is the single mutation path**, so utlxe and utlxd stay in lockstep
("change both at once") and bundle invariants are enforced in one place.

## Problem

- A rich bundle-management UI in Theia is expensive and Theia's layout/widget model is painful
  (see the IF16 iterations). We'd be re-implementing what EF13 already does.
- **Dev/prod drift risk:** the IDE writing bundle files its own way, while production (utlxe) goes
  through EF03, invites subtle mismatches.

## Key finding (constrains the options)

The modules are **separate** and EF03 is **not** reusable as-is:

- `modules/daemon` (**utlxd**) depends on `core`/`cli`/formats and is a **stateless** preview API
  (`/api/execute|validate|infer-schema|udm/*|parse-schema|functions|operators`). It has **no
  bundle management** and does **not** depend on `engine`.
- `modules/engine` (**utlxe**) holds EF03 (`admin/AdminEndpoint.kt`), but it is **engine-coupled**
  — it imports `UtlxEngine`, `TransformationRegistry`, `TransportHandlers`, `proto.*`, `strategy.*`.
  EF03 manages the **live runtime registry** (upload → compile → register → run), not plain bundle
  files.

→ You **cannot copy EF03 into utlxd**. The enabling step for A and B is to **extract a shared,
file-level Bundle Management layer** (the `.utlxp`/`.utlar` model + CRUD per
[Bundle Format](../architecture/bundle-format.md)) into `core` or a new `modules/bundle`, then:
- **utlxd** drives it over the **workspace files**, and
- **utlxe**'s EF03 is refactored to sit **on top of** it (registry = files + compile).

This extraction is moderate work but improves utlxe too (a clean file model under its registry).

## Two design axes

| Axis | Choices |
|---|---|
| **Mutation path** — who changes the bundle? | (1) the **shared API** (utlxd) · (2) the IDE writes **files directly** |
| **Management UI tech** | (a) **embedded web** (reuse EF13) · (b) **native Theia** editors |

The three viable cells:

| Option | Mutation | UI | One-liner |
|---|---|---|---|
| **A** | shared API | embedded web | reuse EF13 in the terminal slot |
| **B** (halfway) | shared API | native Theia | build editors in Theia, but they call the API |
| **C** (baseline) | direct files | native Theia | the current IF18 direction |

---

## Option A — Embedded web UI (reuse EF13)

Embed an EF13-style static admin page as a **Theia webview** in the **freed terminal slot** (IF18
removes the terminal), pointing at utlxd's shared bundle API.

**Pros**
- **Largest reuse / least new UI work** — dashboard, transformation detail, upload, messaging
  config, schemas, sync, logs already exist.
- **Strongest dev/prod parity** — the *same UI* manages dev (utlxd) and prod (utlxe).
- **Sidesteps Theia's widget/layout pain**; the UI evolves as a standalone web app, decoupled from
  Theia version churn.

**Cons**
- **Two interaction paradigms** in one IDE (native mapper + embedded web app) → boundary must be
  crisp or it feels bolted-on.
- **Webview ↔ localhost-API** is exactly IF14's scrutiny zone (per-tenant auth, jail, network/CSP).
- Cross-surface **state sync** (web UI changes a file the native editor has open).

## Option B — Halfway: native Theia editors over the shared API

Build the management editors **natively in Theia** (forms/trees/tables as React widgets), but they
**mutate the bundle only through the shared API** — the IDE never writes bundle files directly.

**Pros**
- **API is still the single mutation path** → utlxe/utlxd lockstep, invariants enforced centrally,
  dev/prod parity *at the API level* (even though the UI differs).
- **Native Theia integration** — one interaction model, no embedded-webview, no two-paradigm feel;
  fits the existing panels/commands (IF18) and the mapper.
- **Editors are simpler than full file management** — the API owns structure/validation/naming, so
  the Theia side is "render + call endpoints," not "implement the bundle format."
- Easier to **harden** than a webview (no cross-origin surface; normal Theia security model).

**Cons**
- **More UI to build than A** (you don't reuse EF13's pages) — though less than C (the API does the
  heavy lifting).
- Still some **Theia widget work** (the thing we're wary of), just thinner.
- Requires the **shared API** (same extraction as A).

## Option C — Baseline: native Theia editors writing files directly (IF18 as-is)

The IDE reads/writes the `.utlxp` files itself (the save/load model in Bundle Format §7, IF18).

**Pros**
- **No new daemon API**; simplest dependency graph; works offline against the workspace.
- Full control in the IDE; no API round-trips.

**Cons**
- **Dev/prod drift risk** — two independent writers of the bundle (IDE files vs EF03), invariants
  enforced twice.
- **Most native Theia UI** for management (the expensive path we want to avoid).
- Bundle-format rules (naming, ordering, `transform.yaml` required-by-loader, etc.) must be
  re-implemented in TypeScript and kept in sync with the Kotlin engine.

---

## Comparison

| Criterion | A — embedded web | B — native over API | C — native + files |
|---|---|---|---|
| New management UI work | **lowest** (reuse) | medium | **highest** |
| Dev/prod parity | UI **and** API | **API** | weakest (two writers) |
| Theia integration / single UX | weakest (2 paradigms) | **strongest** | strong |
| Invariants enforced once | ✅ (API) | ✅ (API) | ❌ (TS + Kotlin) |
| Cloud hardening (IF14) effort | highest (webview) | medium | lowest (no API) |
| Needs shared bundle API in utlxd | yes | yes | no |
| utlxd becomes stateful | yes | yes | no (IDE owns files) |

## Responsibility split (all options)

Regardless of option, keep the **authoring vs management** boundary:
- **Native mapper (Theia, Monaco + 3 panels)** = *author one transformation* (Input \| Editor \|
  Output + live preview) — Theia's strength; unchanged.
- **Bundle management** (list/CRUD/config/schemas/messaging/deploy/logs) = the surface chosen above.

This makes the **IF18 menu bar even leaner** in A/B (bundle CRUD/deploy move out of `File → Bundle ▸`
into the management surface), and unchanged in C.

## Recommendation (phased, low-regret)

1. **Extract the shared file-level Bundle layer** (`modules/bundle` or `core`) per Bundle Format —
   valuable under *every* option that uses the API, and it cleans up utlxe.
2. **Expose it on utlxd** as a small REST surface (CRUD over the workspace `.utlxp`).
3. **Prototype Option B** first (native editors over the API): it gives API-level parity and single
   UX without committing to a webview, and reuses our IF18 chrome. Build one editor (e.g. the
   transformation list + config) against the API.
4. **Spike Option A** (embed an EF13 page in the terminal slot) in parallel to *feel* the reuse vs
   the two-paradigm cost.
5. Pick A or B per how the spikes feel; **refactor EF03 onto the shared layer** so utlxe/utlxd
   truly share. Revise IF18 accordingly.

> B is the recommended default: it captures most of the "don't rebuild the world" benefit (the API
> owns structure/validation) while staying a single, hardenable, native Theia experience. A wins if
> the reuse of EF13's full page set proves decisive and the two-paradigm UX is acceptable.

## Risks / considerations
- **State sync:** files are the single source of truth; need a watcher → IDE refresh + UI re-fetch;
  define concurrent-edit rules.
- **utlxd scope creep:** it becomes stateful (owns the workspace bundle) — appropriate but real.
- **Cloud hardening (IF14):** the shared API and any embedded UI inherit per-tenant auth, workspace
  jail, and webview/network limits.
- **Extraction cost & migration:** refactoring EF03 onto the shared layer must not regress
  production behavior (locked mode, registry, compile).

## Open decisions
1. **Shared layer home:** `core` vs new `modules/bundle`; refactor EF03 onto it now or converge later?
2. **A vs B** (after spikes) — embedded web reuse vs native-editors-over-API.
3. **Timing:** prototype this next on `feature/ide`, or land the IF18 native save/load slice first?
4. **Boundary:** does the management surface also do *editing/fine-tuning* that overlaps the native
   mapper, or strictly management?

## References
- **IF18** — IDE Menu & Chrome Structure (this may shrink the native menu in A/B).
- **IF14** — Theia hardening (auth, jail, webview/network).
- **IF03** — Bundle Project Model & Explorer.
- **EF03** — engine Bundle Management API (to refactor onto the shared layer).
- **EF13** — engine Admin Web UI (the reuse candidate for Option A).
- **[Bundle Format](../architecture/bundle-format.md)** — the on-disk model the shared layer implements.
