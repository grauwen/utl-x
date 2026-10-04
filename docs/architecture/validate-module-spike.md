# Spike — the `:validate` module (calibrate before committing)

| Field | Value |
|---|---|
| Document | validate-module-spike |
| Status | Proposed spike (~half a day) |
| Purpose | Settle three things the architecture decisions *can't eyeball*, before building `%utlx 1.1` for real |
| Related | `docs/proposals/utlx-language-versioning-validation.md` §2.1, §3; `docs/architecture/utlx-test-corpora.md` §5.5, §5.7 |

> **What this spike is *not* for.** The **module boundary is already decided**: `validate.*` is its own
> module (`:validate`), separate from core stdlib, so UTLXe can pack with/without it (`:core ↛ :validate`).
> That is requirement-driven, not size-driven — the spike does **not** reopen it. The spike only
> *calibrates* the details below.

---

## 1. Questions to answer

1. **Does `validate.*` require any change to the per-format parsers/serializers?** (Claim: no — it is
   UDM-layer. Confirm empirically.)
2. **How big is it?** jar KB and GraalVM native-image delta of UTLXe vs UTLXS. (Decides whether a
   "minimal UTLXe" is worth *marketing* on size grounds, and whether Tier 1 / Tier 2 need separate
   modules or one.)
3. **`ValidationResult` as a UDM `Object` vs a new node type** — which is clean in practice? (Favoured:
   `Object`, for "core unchanged".)

## 2. Scope — build the minimum that answers them

- A new Gradle module **`:validate`** depending on `:core` (never the reverse — add the ArchUnit /
  module-dependency test that enforces `:core ↛ :validate`).
- **Three Tier-1 functions only:** `validate.required(fields)`, `validate.inRange(field, min, max)`,
  `validate.oneOf(field, values)`.
- **`ValidationResult` modelled as a conventional UDM `Object`** (fields: `valid`, `passed`, `failed`,
  `errors[]`, `payload`) — produced by the functions, serialized by the *existing* Object serializer.
- **One routing example:** a `%utlx 1.1` script that does `required |> inRange |> oneOf` then branches on
  `valid` (pass → payload, fail → the errors) — the UTLXS shape.
- **Two build targets from the one codebase:**
  - `utlxe` = `:core` (+ JSON/XML readers only) — must **reject** the `%utlx 1.1` script (version gate).
  - `utlxs` = `:core` + `:validate` — runs it.

Explicitly **out of scope:** the Tier-2 guard profile, the full function set, FEEL routing integration,
performance work, any parser/serializer edits (if you find yourself editing one, stop — that falsifies
claim #1 and is the most important finding).

## 3. Steps

1. Create `:validate`, add the `:core ↛ :validate` architecture test (prove it fails if violated, then
   passes).
2. Implement the three functions + `ValidationResult` as an `Object`.
3. Write the routing example script; run it on `utlxs` (passes) and `utlxe` (rejected at the gate).
4. Build both native images with identical format sets.
5. Record the measurements below.

## 4. Measurements to report

| Metric | How | Decides |
|---|---|---|
| **Parser/serializer edits** | `git diff --stat` on `:core` readers/writers | claim #1 — expect **zero** |
| **`:validate` size** | jar bytes; LOC | Tier-1/Tier-2 granularity |
| **Native-image delta** | `size(utlxs) − size(utlxe)`, same formats | is a "minimal UTLXe" worth marketing on *size*? |
| **`ValidationResult` ergonomics** | note friction in `Object` vs new-type | settle the data-model choice |
| **Gate behaviour** | `utlxe` rejects `%utlx 1.1` with a clear error | confirms the profile = version-gate mapping |

## 5. Decision gates (what the numbers mean)

- **Any `:core` reader/writer edit needed** → claim #1 is wrong; escalate (the format-independence thesis
  and the §5.5 proof depend on it).
- **Native-image delta small** (expected, pure Kotlin) → keep **one** `:validate` module; the UTLXe/UTLXS
  split stays justified by *multiplicity/cadence/assurance*, not size — don't market it as a size win.
- **Native-image delta surprisingly large** → investigate why (likely an accidental heavy dependency);
  consider splitting Tier 1 / Tier 2, and it *does* become a size argument too.
- **`Object` modelling awkward** → reconsider an open-hierarchy node type (and accept the small core
  change that implies).

## 6. Outcome

A one-page result appended here (or linked), feeding the final numbers back into
`utlx-language-versioning-validation.md` §2.1/§3. The architecture does not change on the result; only
the *granularity* (one validate module vs Tier-1/Tier-2 split) and the *marketing* ("minimal build" on
size grounds, yes/no) are settled by it.
