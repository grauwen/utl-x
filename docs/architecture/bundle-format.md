# UTL-X Bundle Format (canonical)

**Status:** canonical specification. This is the **single source of truth** for the
UTL-X bundle on-disk layout, naming rules, and the IDE save/load model. Other docs
(EF09, EF03, IF03, IF04, IF05, IF16, IF17) **reference** this file for structure and
should not restate it; they keep their own behaviour (deploy, API, IDE UX, …).

> Verified against the engine (`BundleLoader.kt`, `TransformConfig.kt`), the config
> JSON Schemas (`docs/api/config/{transform,engine}-config.schema.json`), and the real
> example bundles under `examples/utlxe/*.utlxp`.

---

## 1. What a bundle is

A **bundle** is UTL-X's project *and* deployment unit: an integration project containing
**N transformations**, their **schemas** (contracts), and **engine config**. It has two
forms and one lifecycle:

| Form | What | Used for | Editable |
|---|---|---|---|
| **`<name>.utlxp/`** | the open, editable **project directory** | dev / test (IDE) | yes |
| **`<name>.utlar`** | the locked, deployable **ZIP archive** (+ manifest) | acc / prd (CI/CD) | no — read-only |

**Lifecycle:** edit the `.utlxp` project → **Build/Export** → `.utlar` → deploy (CI/CD).
In production, finding a `.utlar` puts UTLXe in **locked mode** (Admin API read-only) — see
EF09. The Admin REST API (upload/list/update transformations & schemas) is EF03.

**Recognition.** The engine keys off the **`transformations/`** directory, *not* the
suffix. The `.utlxp/` suffix is the **IDE's project-folder convention** (a `.utlxp` IS a
plain directory); `.utlar` is the **locked ZIP** form. The deployable manifest
(`manifest.json`, checksums) lives **inside** the `.utlar` (EF09) — generated on Build,
not required in the open `.utlxp`.

---

## 2. Directory structure

```
<name>.utlxp/                          ← open project dir  (or <name>.utlar when zipped)
├─ engine.yaml                         ← ENGINE runtime config (threads, memory, monitoring,
│                                          defaultStrategy). Open `.utlxp` only — NOT packaged in
│                                          the .utlar. (Different concern from manifest.json, NOT a
│                                          substitute for it — see note below.) engine-config.schema.json.
├─ schemas/                            ← shared CONTRACTS, referenced from transform.yaml
│  ├─ order.json                          (JSON Schema / XSD / EDMX / Avro / proto / …)
│  └─ invoice.xsd
└─ transformations/
   └─ <tx-name>/                       ← one directory per transformation
      ├─ <tx-name>.utlx                ← the transformation source
      ├─ transform.yaml                ← REQUIRED per-tx config (engine SKIPS a dir without it)
      ├─ test-input-<slot>.<ext>       ← test INSTANCES — one per input slot (see §5)
      └─ tests/<case>/…                ← OPTIONAL, future — named multi-scenario cases (§7)
```

**`engine.yaml` vs `manifest.json` — two different files, two different jobs** (don't conflate
them; both verified in the engine):

- **`engine.yaml`** — *engine runtime config* (threads, memory, monitoring, `defaultStrategy`).
  Hand-authored; loaded from the bundle root by the engine (`Main.kt` → `EngineConfig.load`, or
  defaults if absent). Present in the open **`.utlxp`**. In production the engine config is a
  **deployment concern** (container/env), so it is deliberately **not packaged inside the
  `.utlar`**.
- **`manifest.json`** — the **`.utlar` package manifest** (`format`, `format_version`, `version`,
  sha256 `checksum`, `created`, and a `transformations[]` index). A **generated build artifact**
  (checksums), read from the ZIP in locked mode (`BundleMode.kt::readManifestFromUtlar`) for
  integrity + fast-startup version compare. Only the packaged **`.utlar`** has it — an open
  editable directory has nothing to "manifest."

So the two forms genuinely differ in their *non-shared* file:
**`.utlxp` = `engine.yaml` + `transformations/` + `schemas/`**;
**`.utlar` = `manifest.json` + `transformations/` + `schemas/`**. But `engine.yaml` and
`manifest.json` are **different artifacts with different jobs** (runtime config vs. a generated
package manifest) — **not** two forms of the same file, and **not** substitutes for each other.

**Engine load requirements.** A `transformations/` directory must exist; each `<tx>/` needs a
`.utlx` source (the engine prefers `<tx-name>.utlx`, else the first `*.utlx`). `schemas/`,
`test-input-*`, `tests/`, and `engine.yaml` are *not* required to run a transformation
(validation defaults to `SKIP` — EF02).

> ⚠️ **`transform.yaml` requirement differs by loader** — a *deliberate* behavioral
> difference (each path is pinned by a test/decision), not an accidental bug:
> - **`.utlxp` directory** (`BundleLoader.kt`, used by `UtlxEngine`): `transform.yaml` is
>   **required** — a `<tx>/` without it is **skipped** (`return null`; pinned by
>   `BundleLoaderTest::load skips directory without transform yaml`). Rationale: a live editable
>   folder may hold incomplete/scratch transformations, so only "complete" ones are loaded.
> - **`.utlar` ZIP** (`BundleMode.kt`) and the **Admin API** (EF03, "`.utlx` alone is enough"):
>   `transform.yaml` is **optional** — config **defaults** to `TransformConfig()` when absent.
>   Rationale: a sealed archive / explicit deploy call is intentional, so `.utlx`-alone is safe.
>
> Practical consequence (real footgun): a `.utlx`-only transformation deploys fine via the
> API/`.utlar` but is **silently skipped** when scanned as a `.utlxp` directory. So the **IDE
> authoring path must write a `transform.yaml`** (execution-save included). Whether to unify the
> two loaders is a product decision (§10), not a clear bugfix.

---

## 3. Three naming namespaces (do not mix them)

This is the single biggest source of past confusion. There are **three** distinct naming
rules:

| # | Thing | Rule | Example |
|---|---|---|---|
| 1 | **Transformation name** (the `<tx>/` dir & `<tx>.utlx` filename) | **Free filesystem string.** No validation, no regex, **no stripping** (`BundleLoader.kt`: `val name = txDir.name`). Leading digits/dots/hyphens are legal and **meaningful** (ordering). | `00-enterprise-order.utlx` (the `00-` is kept verbatim) |
| 2 | **Input / output names** (`transform.yaml inputs[].name`, header names, the `$name` binding) | **In-language identifiers**: `[a-zA-Z_][a-zA-Z0-9_-]*`, enforced by the lexer/parser. A loaded filename is normalized to an identifier — leading digits are stripped. | file `00-input-order.json` → input name `input-order` |
| 3 | **Schema references** (`transform.yaml` `schema:`, header `{schema:…}`) | **Free strings**, resolved **relative to the bundle root** (usually `schemas/…`); no stripping (see IF17). | `schemas/order.json` |

Consequence: the same source file contributes a **transformation name** (kept verbatim)
*or* an **input name** (stripped to an identifier) depending on its role — and its original
filename is otherwise lost (see §6, provenance).

---

## 4. `transform.yaml` — the spine

Per-transformation config. Authoritative schema: `docs/api/config/transform-config.schema.json`;
Kotlin model: `TransformConfig.kt`. Fields:

```yaml
strategy: COMPILED            # TEMPLATE (default) | COMPILED | …
validationPolicy: SKIP        # SKIP (default) | WARN | FAIL   (EF02)
inputs:                       # matched to the header's inputs BY NAME (not by position)
  - name: order               #   identifier; bound as $order in the .utlx
    schema: schemas/order.json#   optional; the input CONTRACT (MC mode)
  - name: customer
    schema: schemas/customer.json
output:
  schema: schemas/invoice.json#   optional; the output CONTRACT (MC mode)
maxConcurrent: 16             # EF21: 0 = unlimited
maxInputSize: 25MB            # optional per-tx cap
# input: / output_messaging:  # optional Dapr messaging endpoints (queue/topic/eventhub) — see §4b
```

**`transform.yaml inputs[]` binds config (the `schema:` ref) to inputs *by name*, not by
position.** It does **not** own the input set or their order — see §4a. **Do not** derive order
from the filesystem either (a directory listing is alphabetical, not authoring order).

### 4a. Two different "orders" — don't conflate them

There are two distinct orderings, and they are independent (they don't even point the same way):

| | Owner | What it governs |
|---|---|---|
| **Input display order** (IDE tab order) + the **input set** | the **`.utlx` header** | which inputs exist, and the order their tabs appear in the IDE — it follows the header's source declaration order |
| **Config precedence** (which source's value wins) | **EF02** | the *effective* schema = `runtimeOverride ?? transform.yaml(config) ?? header` |

So the three sources play distinct roles:

1. **`.utlx` header** — *declares* the inputs (names, formats) → it is the source of the **input set and the display/tab order**. It may also *bind a schema* per input (IF17), at the **lowest** precedence.
2. **`transform.yaml inputs[]`** — *binds config* (schema ref) to those inputs **by name**; it supplements the header and **supersedes** the header's schema (EF02). Its list order is not authoritative for display.
3. **Runtime override** — highest precedence for the effective schema (EF02).

Note the deliberate asymmetry: for **display** the header is primary; for the **schema value** the header is *last* (transform.yaml and a runtime override supersede it). "Order of showing" ≠ "order of supersession."

> ⚠️ **`additionalProperties: false`.** `transform-config.schema.json` forbids unknown keys
> at the top level *and* per input slot (required: `name`; allowed: `schema`). The **engine**
> tolerates unknowns (`FAIL_ON_UNKNOWN_PROPERTIES = false`), but schema validators (and the
> IF04 config editor) will reject extra fields. Any new `transform.yaml` field (e.g. a
> `sample:`/`origin:` per input) therefore requires updating **both** the JSON Schema and the
> Kotlin model. This is why IDE-only metadata goes in a **sidecar** by default (§6).

### 4b. Messaging endpoints — Dapr bindings (EF06 / EF10)

A transformation can be driven by **messaging** instead of (or in addition to) HTTP. Two optional
`transform.yaml` keys bind an **input** source and an **output** sink, per transformation:

```yaml
input:                       # messaging INPUT  (EF10) — one of queue / topic / eventhub
  topic: raw-invoices
  subscription: utlxe-invoice-routing   # required for a Service Bus TOPIC input
output_messaging:            # messaging OUTPUT (note the key is output_messaging, not output)
  queue: routed-invoices
```

Each endpoint binds an Azure resource **by name** and the engine **auto-generates the matching
Dapr component** (EF10, `DaprIntegration.kt`) — you supply the name, not the component YAML:

| Field | Azure resource | Dapr component type | Mode |
|---|---|---|---|
| `queue: <name>` | Service Bus **queue** | `bindings.azure.servicebusqueues` | binding |
| `topic: <name>` (+ `subscription:` for input) | Service Bus **topic** | `pubsub.azure.servicebus.topics` | pub/sub |
| `eventhub: <name>` | **Event Hub** | `bindings.azure.eventhubs` | binding |
| `eventhub: <name>` + `consumerGroup:` | **Event Hub** | `pubsub.azure.eventhubs` | pub/sub |

Rules (from `MessagingEndpoint` in `TransformConfig.kt`):
- **Exactly one** of `queue` / `topic` / `eventhub` per endpoint; `resourceName = queue ?? topic ?? eventhub`.
- **`subscription`** is **required for a Service Bus topic *input*** (the subscription name).
- **`consumerGroup`** flips an Event Hub endpoint into **pub/sub** mode (binding mode otherwise).
- On deploy/sync the engine writes `binding-<name>.yaml` / `pubsub-<name>.yaml` components and
  reports *"N Dapr component(s) synced; bindings activate within ~1 second."*

`engine.yaml` configures the engine and Dapr **runtime**; these per-transformation endpoints
configure **which queues/topics/hubs each transformation listens to and emits to**. Both keys are
optional — a transformation with neither is HTTP-only. Full detail: **EF06** (pub/sub & binding
strategy) and **EF10** (dynamic Dapr bindings).

---

## 5. Instances, schemas, and test inputs (terminology)

| Term | Meaning | Where | Analogy |
|---|---|---|---|
| **Schema** | the *contract* — a type describing a whole class of valid documents | `schemas/<name>.<ext>` (shared) | the **class** |
| **Instance** | a *concrete document* conforming to a schema | — (the general concept) | an **object** |
| **Test input** | a concrete input **instance** stored as a test fixture | `transformations/<tx>/test-input-<slot>.<ext>` | an object on disk |

- A **test input** is design-time/test data (used by the IDE preview, the daemon run, and
  automated tests). It is **not** a runtime input (production messages arrive over the wire)
  and **not** a work product.
- The word **“sample”** is the legacy term used by `examples/mcm/` (`sample.<slot>.<ext>`).
  **Retire it** in new material — the bundle term is **test input**.
- The **output result** is never persisted as a work product; it is re-derived by running.
  MC-save persists the output **schema** (the contract), not the result.

### The `test-input` rule (precise)
```
test-input-<exact input name>.<ext>
```
- `<slot>` = the input's **exact name** as declared in the `.utlx` **header** (mirrored in
  `transform.yaml inputs[].name`), **character-for-character, no case conversion** (so
  `pricingResponse` → `test-input-pricingResponse.json`, *not* kebab-cased). It is **not** a
  number. (Numbers like `input1.`/`input2.` are an `examples/mcm/` convention, not this one.)
- `<ext>` carries the **data format**. **Multiple formats are fully supported** — a slot's
  instance may be JSON / XML / CSV / YAML / … per the input's declared format. Mixed formats
  across slots in one transformation are normal.
- Name slots by **role** (`order`), not `input-order` — otherwise you get the redundant
  `test-input-input-order.json`.
- **One file per slot = one default scenario.** For multiple scenarios, see §7.

---

## 6. Provenance & IDE metadata (sidecar)

Deriving an identifier loses information (`00-input-order.json` → `input-order`: the `00-`
ordering and original name are gone). To keep provenance, follow the **portability rule**:

> **Never store machine-specific absolute paths in a shared/committed bundle.**

Three tiers:

| What | Portable? | Where |
|---|---|---|
| slot name, order, schema ref | yes (shared) | `transform.yaml` |
| original **basename** (`00-input-order.json`), import time, per-tab UI state | yes-ish (provenance) | **sidecar** `transformations/<tx>/.utlx-ide.json` |
| absolute **import path** (`/Users/…/Downloads/…`) | **no** | sidecar (gitignorable) / IDE-local only |

**Sidecar** (IDE-only; safe to gitignore; the engine never reads it):
```jsonc
// transformations/<tx>/.utlx-ide.json
{
  "inputs": {
    "input-order": {
      "instance":   "test-input-input-order.json",  // explicit slot→file link
      "origin":     "00-input-order.json",           // original basename (provenance)
      "originPath": "/Users/.../Downloads/00-input-order.json", // local-only hint
      "format":     "json"
    }
  }
}
```
The explicit **`instance` link** also removes filename-guessing fragility (it survives the
casing drift in §8). The `test-input-<slot>.<ext>` name remains the **default** on write;
the sidecar's `instance` is authoritative on read when present.

*Alternative (deliberate, heavier):* promote `instance:`/`origin:` into `transform.yaml`
`inputs[]` — but that requires extending `transform-config.schema.json` (`additionalProperties:
false`) and `InputSlot`. Default is the sidecar.

---

## 7. IDE save/load model

### Two save modes — the difference is *whether the schema/contract layer is saved*
Both modes save the `.utlx` (whose **header** declares the inputs and their order) **and** the
**input instances**. **Message-Contract-save additionally** saves the **schemas** (the contracts).

| Mode | `.utlx` | input instances | schemas (contracts) | output |
|---|---|---|---|---|
| **Execution-save** | ✓ | ✓ `test-input-<slot>.<ext>` per slot | — | not saved — derived by running |
| **Message-Contract-save** | ✓ | ✓ `test-input-<slot>.<ext>` per slot | ✓ input + output schemas → `schemas/`, referenced from the header / `transform.yaml` | output **schema** persisted (the contract); result still derived |

- So **MC-save = Execution-save + the schema/contract layer.** Execution-save is therefore a
  strict *subset* — a single transformation with instances but no schemas. It is fully runnable
  (the engine needs only `transformations/<tx>/{<tx>.utlx, transform.yaml}`; schemas optional).
- `transform.yaml inputs[]` carries the per-input **schema ref** (bound by name); the **header**
  carries the input set + display order (and may itself bind a schema, at lowest precedence — §4a).
- **The mode is dictated by context** (IF03): inside the **Bundle editor** it is **always
  Message-Contract**; the **standalone / ad-hoc** run is **Execution**. There is no per-action
  mode toggle.

### Saving — names & the minimal unit

A save (either mode) always requires a **transformation name** — it *is* the on-disk location
`transformations/<tx-name>/<tx-name>.utlx`. There are only **two nameable things**:

| # | Name | Becomes |
|---|---|---|
| **A** | **project / bundle name** | `<project>.utlxp/` |
| **B** | **transformation name** | `transformations/<B>/` **and** `<B>.utlx` |

- **The `.utlx` filename is *not* a separate choice** — it follows the transformation name
  (`<tx-name>.utlx`), because the engine resolves the source by name-match first
  (`BundleLoader.findUtlxSource`). So #2 (dir) and #3 (file) are one name.
- **The input/output names are not chosen here** — they are identifiers declared in the `.utlx`
  **header** (§3, a separate namespace). "Naming the save" = naming the transformation, not the inputs.

**Minimal unit = one transformation, wrapped in a `.utlxp`.** You save a single transformation
(not all N in a bundle), but a transformation cannot exist loose — the minimal on-disk product is a
`.utlxp` containing exactly one `transformations/<name>/`.

**First-save naming:**
- **A bundle is already open** (MC / Bundle editor) → supply only the **transformation name (B)**;
  it is added/updated inside the open project.
- **No bundle open** (quick Execution save) → ask **one** name and **default the project (A) to the
  transformation name (B)** — i.e. `order-ack` → `order-ack.utlxp/transformations/order-ack/order-ack.utlx`.
  This matches the usual "save sets one name" expectation.

**Caveat — project ⊇ transformations.** Bundle-name = transformation-name is only a
*single-transformation default*. A bundle is a project that can hold many transformations, so the
project name is independent and may be renamed (and *will* differ from the tx names once a second
transformation is added, e.g. `sales.utlxp` holding `order-ack` **and** `invoice-to-ubl`).

> A plain **`.utlx`-only** save (the middle editor) writes just the source file — *not* the
> constellation and *not* a bundle. The two constellation modes (Execution / MC) are bundle saves;
> their minimum is one complete transformation as above.

### Load is unified (mode-agnostic)
- Opening/selecting a transformation reads back **whatever it carries** — `.utlx`, test inputs
  if present, schemas if present — and re-derives the output by running. There is **no**
  separate “execution-load” vs “design-load”.
- **Manual load** (the user explicitly picks a file via the Load dialog) is fully
  **name- and format-agnostic** — the chosen file drops into the active panel regardless of
  naming convention. Only **auto-load** (populate panels from a bundle) needs the conventions
  above: the **header** gives the input set + display/tab order, and each slot's instance is the
  sidecar `instance` link (or the `test-input-<slot>` rule), its schema resolved by EF02 precedence.

So: **one load, two saves.** Add an Execution-save action; reuse the existing constellation
load. See IF03/IF16 for the surrounding shell (navigators + persistent Mapping editor).

---

## 8. Multiple scenarios / golden tests (future, additive)

Today: one instance per slot ⇒ one scenario. For multiple examples, use **named cases** — do
**not** put multiple files per slot (a multi-input transform would have no way to *pair* the
slots into a coherent scenario):

```
transformations/<tx>/
├─ test-input-<slot>.<ext>          ← the DEFAULT scenario (unchanged)
└─ tests/
   ├─ happy-path/
   │  ├─ <slot>.<ext> …             ← one file per slot, paired
   │  └─ expected-output.<ext>      ← optional golden output (assertion)
   └─ missing-vat/ …
```
Each `tests/<case>/` is one complete, coherent scenario; the engine ignores `tests/`. This
ties into QA/testing (book ch39). `examples/mcm/`'s **output variants**
(`output.<variant>.<name>.schema.json`) are a related but separate concept.

---

## 9. Responsibilities

| Actor | Reads | Writes |
|---|---|---|
| **Engine** | `transformations/`, `transform.yaml` (name/schema/strategy/…), `.utlx`, `schemas/`, `engine.yaml` | — (runtime) |
| **IDE** | the full constellation + `.utlx-ide.json` sidecar | `.utlx`, `transform.yaml`, `schemas/`, `test-input-*`, sidecar |
| **Admin API (EF03) / Web UI (EF13)** | `transformations/<name>/` (`.utlx` + optional config), `schemas/` | the same — deploy/update transformations, schemas, config |
| **CI/CD** | the `.utlxp` project | Build → `.utlar` (EF09) |

The engine **ignores** `test-input-*`, `tests/`, and `.utlx-ide.json`.

### Consistency with the Admin API & Web UI (EF03 / EF13)
The REST API and the static web UI create/maintain bundles with the **same model** — verified
consistent with this spec:
- **Same layout & naming:** `transformations/<name>/` (name = identity = directory = data-plane
  URL `/api/transform/{name}`) and a shared **`schemas/`**. ✅
- **Same precedence:** the engine states `runtime override > transform.yaml > .utlx header >
  default` (`ValidationOverrideStore.kt`) — identical to §4a / EF02. ✅
- **Scope difference (not a contradiction):** the API/UI manage **transformations + schemas +
  config** only. They do **not** create or require **test instances** (`test-input-*`), the
  `tests/` cases, or the `.utlx-ide.json` sidecar — those are **IDE/dev artifacts** the engine
  ignores. A bundle uploaded via the API simply has no test data; one authored in the IDE may,
  and the extra files are harmless on the production path.
- **`transform.yaml` optional here:** the API allows `.utlx`-only deploys (defaults) — consistent
  with the `.utlar`/`BundleMode` loader, but **not** with the `.utlxp`/`BundleLoader` path (see
  the requirement note in §2).

---

## 10. Known drift to reconcile (cleanup backlog)

1. **`examples/mcm/`** is **not drift — it is a deliberate, separate artifact type:** *AI-assist
   GAP test fixtures.* Each example is a flat dir of **input schema(s)** (`input.<slot>.schema.<ext>`,
   or `input1./input2./input3.` when multi-input), **sample instance(s)** (`sample.<slot>.<ext>`),
   and **output schema(s)** (`output.<variant>.<name>.schema.json`), plus a `README.md`. It has
   **no `.utlx` on purpose** — the transformation is what the **AI assist generates**, and the
   `clean` / `small-gap` / `large-gap` names are the schema gaps under test. So these are **not
   bundles and must not be migrated to `.utlxp`.** *(Audited 2026-06: all 11 examples are
   internally consistent — every input schema has a name-matching `sample.<slot>` instance, and
   each has a README + output schema(s).)*
2. **`examples/utlxe/fulfillment-pipeline.utlxp`** is **inconsistent**: slots are camelCase
   (`pricingResponse`) but files are kebab-case (`test-input-pricing-response.json`), and the
   file set ≠ the slot set. Fix to conform to §5.
3. **IF03** historically said instances go to a bundle-root **`samples/<name>.*`** (wrong →
   `transformations/<tx>/test-input-<slot>.<ext>`) and mentioned **`manifest.json`** for the
   open form (the open `.utlxp` uses **`engine.yaml`**; `manifest.json` is the `.utlar`). This
   doc supersedes those statements.
4. **`transform.yaml` requirement diverges by loader** (intentional today, test-pinned):
   `BundleLoader` (`.utlxp`) **requires** it and skips a `<tx>/` without it; `BundleMode`
   (`.utlar`) and the Admin API **default** it. Each is defensible (strict editable dir vs.
   sealed archive), but a `.utlx`-only tx loads in one path and not the other. **Decision to
   make:** unify (and how) or document the difference as intended. See §2.

---

## 11. References

- **EF09** — Production Bundle Mode (`.utlar`, locked mode, manifest) — *deploy form of this layout.*
- **EF03** — Bundle Management API (REST upload/list/update).
- **IF03** — IDE Bundle Project Model & Explorer (the `.utlxp` UX, save modes, navigator→editor).
- **IF04** — Transform Config Editor (`transform.yaml`).
- **IF05** — Bundle Operations & Messaging Topology.
- **IF16** — IDE Mapping Workbench (shell layout).
- **IF17** — Header-level schema binding (schema-reference naming).
- **EF01** — Pipeline / multi-input per stage. **EF02** — Engine schema validation (`validationPolicy`).
- **Contracts:** `docs/api/config/transform-config.schema.json`, `docs/api/config/engine-config.schema.json`.
