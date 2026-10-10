# Parser & Serializer Strictness Profiles

| Field | Value |
|---|---|
| Document | parser-strictness-profiles |
| Status | Proposal — draft (design); sibling to `%utlx 1.1`, feeds Phase 0 hardening |
| Scope | A **named strictness profile** (`lenient \| standard \| strict`) that governs how tolerant each **parser** is of imperfect input, and how canonical each **serializer** is on output. Profile is a **deployment policy**, not language syntax or data; for a content guard it is pinned to `strict` and **locked**. |
| Related | `../proposals/utlx-1_1-build-plan.md` (Phase 0b baseline, 0c hardening-to-a-bar); `utlx-test-corpora.md` (§5.7 malformed/leniency; `expectations/profiles/{strict,lenient}`); `../proposals/utlx-language-versioning-validation.md` §3 (parse-time hardening vs semantic validation); `../proposals/utlx-1_1-semantic-validation.md` (`validate.*`); `civilian-guard.md` (fail-closed, rebuild-clean / no pass-through) |

> **One line:** one engine, three parse/serialize postures. A general mapping deployment needs to be
> *liberal* in what it accepts (real-world interop); a guard needs to be *maximally strict and
> fail-closed*. Strictness is therefore a **profile set by the operator** — and on a guard it is
> **locked** so neither the data nor the mapping can weaken it.

---

## 1. Problem

A parser cannot serve both goals at once:

- **Interop (mapping):** real-world JSON/XML/CSV is messy — trailing commas, comments, duplicate keys,
  BOMs, loose encodings, quirky numbers. A mapping engine that rejects all of it is useless in
  practice (**Postel's Law** — be liberal in what you accept).
- **Security (guard):** a content guard must reject anything not provably well-formed and clean, with
  **no repair and no best-effort** (**fail-closed**). Here liberality is a vulnerability.

One fixed parser behaviour can only sit at one point on that spectrum. So strictness must be a
**parameter** — but how it is modelled, and *who controls it*, is the real design.

## 2. What this is *not*

- **Not `validate.*` (1.1).** This governs **parse-time well-formedness** ("is this even clean
  JSON/XML?"), *below* the UDM. `validate.*` is **semantic** validation ("is this value in range /
  required / matching?") *on* the UDM. A guard uses **both**, in sequence. Keep the layers separate
  (see `utlx-language-versioning-validation.md` §3).
- **Not a per-call / per-message data knob.** Strictness must never be settable by the incoming
  message or (on a guard) by the mapping — see §5.
- **Not a single `strict` boolean.** Strictness is multi-dimensional; a boolean can't express the
  pragmatic middle most deployments want (§3).

## 3. The model — a profile, not a boolean

A named profile, each a bundle of per-feature toggles, with the profile as the primary knob and
(where policy allows) individual overrides:

| Profile | Posture | Intended consumer |
|---|---|---|
| **`lenient`** | Accept and **normalise** common benign deviations; maximise "it parses." | Ingest from messy/legacy sources; migration; best-effort tooling. |
| **`standard`** | **Spec-compliant**: accept what the format spec allows, reject what it forbids. Pragmatic default. | General mapping (UTLXe) default. |
| **`strict`** | Spec-compliant **plus a hardening overlay**: reject even spec-*allowed* but risky/ambiguous constructs; tightest bounds; **reject, never repair**; canonical output only. | **Content guard** (locked) and any untrusted boundary. |

`strict` is deliberately **stricter than the spec** — it's an allow-list posture (minimal attack
surface), not merely "valid per RFC."

## 4. Dimension × profile matrix (illustrative)

Defaults per profile; format-specific toggles live in the format reader. (Indicative — the exact set
is finalised during Phase 0.)

| Dimension | `lenient` | `standard` | `strict` |
|---|---|---|---|
| Duplicate object keys (JSON) | last-wins, accept | reject (ambiguous) | **reject** |
| Comments / trailing commas (JSON) | strip & accept | reject | **reject** |
| BOM | strip & accept | strip & accept | **reject** (require declared encoding) |
| Encoding | sniff / best-effort | enforce declared | **enforce + reject invalid byte sequences** |
| Control chars in strings | accept | accept | **reject** (`noControlChars`) |
| XML DOCTYPE / DTD / external entities | **never resolve externally** (XXE off in *all* profiles) | DOCTYPE rejected; no entities | **DOCTYPE rejected outright** |
| Number quirks (leading zeros, NaN/Inf, oversized ints) | coerce | spec rules | **reject non-conformant** |
| Unknown / undeclared fields | accept | accept | **reject** (where a shape/allow-list is known) |
| Resource bounds (depth / size / node-count) | bounded | bounded | **tightest configured bounds** (`bounds.yaml`) |
| **Behaviour on any deviation** | repair / coerce / continue | **reject with reason** (no repair) | **reject with reason, no recovery, no partial output** |

Note the one rule that holds across **every** profile: **external entity resolution (XXE) is always
off** — that's a hard security floor, not a strictness dial.

## 5. Policy model — the security-critical part

> Strictness is a **policy set by the deployment/operator**. On a guard it is **locked to `strict`**
> and cannot be influenced by the incoming message *or* by the mapping author.

If anything derived from untrusted input — or a mapping — could dial strictness *down* on a guard,
the fail-closed posture collapses. Therefore:

- **Where it's set:** engine/deployment configuration (e.g. `engine.yaml: parse.profile`), optionally
  **per input format** (e.g. JSON `standard`, XML `strict`). **Not** the `%utlx` header, **not** a
  runtime argument reachable from data.
- **Guard = locked:** the guard/UTLXS profile pins `strict` and forbids override. A mapping that
  *requests* a weaker profile on a guard is itself a policy violation (reject).
- **General mapping (UTLXe):** operator picks the deployment default (`standard` recommended),
  overridable per deployment — never per message.
- **Always logged:** the active profile is recorded with every parse (see §8).

## 6. The serializer side — strictness means *canonicalisation*

Serializer "strictness" is a real axis, but it means **canonical output**:

| Profile | Serializer behaviour |
|---|---|
| `lenient` / `standard` | readable / conventional output (pretty, flexible ordering) |
| `strict` | **canonical form** — deterministic key order, fixed encoding, no insignificant whitespace, c14n where defined |

This is exactly what the guard's **rebuild-clean / no-pass-through** doctrine already requires: every
message is rebuilt and written **canonically**, never echoed. So "strict serializer" ≡ "canonical
writer," and the guard is already pointed there.

## 7. Fail-closed semantics at `strict`

At `strict` the contract on any deviation is **reject + a reason code, never repair, never
best-effort, never partial output**. That reason plugs directly into the guard's verdict policy
(a REJECT with a category). `lenient` may coerce/normalise/continue; `standard` rejects spec
violations but doesn't harden further. The **behaviour on deviation is itself part of the profile**,
not an afterthought.

## 8. Determinism & reproducibility

Because the profile is **deployment configuration — not data-dependent** — the four guarantees hold:
same input **+ same profile** → the same result, deterministically. The corollary: a parse result is
only meaningful **paired with the profile that produced it**. So:

- The active profile (and any per-format overrides) is **logged with each parse**.
- Test-corpus expectations are **keyed by profile** (an input may be *accepted* under `lenient`,
  *rejected* under `strict`).

## 9. Where it lives (not language syntax)

- **Configuration, not `%utlx`.** Expressed in engine/deployment config, resolvable per input format.
  Keeping it out of the language header is deliberate — it must not be author- or data-reachable
  (§5).
- **Build/profile alignment:** `standard` is the UTLXe default; `strict`-locked is part of the
  **UTLXS / guard** profile. (See `utlx-language-versioning-validation.md` for UTLXe vs UTLXS.)

## 10. Fit with the 1.1 plan & test-corpora

This is the operational expression of **Phase 0c hardening** and the **`expectations/profiles/`**
already scaffolded under `test-corpora/`:

- **Measure the Phase 0b baseline *per profile*** — not one number. `lenient`/`standard`/`strict`
  will show very different accept/reject rates against the same corpus; that spread *is* the signal.
- The **`strict` profile is the bar** the guard hardening (0c) targets; `known-deviations.yaml` +
  `bounds.yaml` encode what each profile accepts/rejects.
- The corpus becomes the **conformance definition** of each profile: a change to a profile's toggles
  is a change to its expected corpus verdicts.

## 11. Parsing theory & prior art (why these choices)

This section records the theoretical and industry grounding, so "guard = strict" is a cited design
decision, not a preference.

### 11.1 Two parser layers — different threat models
UTL-X parses at two layers, and strictness policy means different things for each:

- **Language parser** (the `%utlx` *script*) — hand-written lexer/parser in `:core`
  (`lexer_impl.kt`, `parser_impl.kt`), with ANTLR (`UDMLang.g4`) for the narrower UDM sub-grammar.
  Input is a **trusted, authored artifact**; strictness here is mainly a *developer-experience*
  concern (recover to report many errors with good messages).
- **Format/data parsers** (`formats/*`, input data → UDM) — input is **untrusted, at a boundary**.
  This is where the `lenient|standard|strict` profiles apply, and where the guard hardens.

Keeping boundary-parsing and trusted-parsing distinct is itself best practice: they warrant
different postures.

### 11.2 The recognition → recovery → repair ladder
Classical compiler theory (Aho–Lam–Sethi–Ullman, *Compilers*, §4.1.3–4) frames strictness as **how
far past pure recognition you go**:

1. **Recognition** — accept/reject membership in the language. **`strict` stops here**: a pure
   recognizer that halts on the first non-member.
2. **Error recovery** — continue past an error to report more (panic-mode, phrase-level, **error
   productions**, global/least-cost correction).
3. **Error repair** — mutate the token stream (insert/delete/substitute) to force a parse — this is
   literally "be liberal in what you accept."

Strictness is the dial over (2)+(3): **`strict` = none; `lenient` = aggressive recovery/repair.** It
applies at **both** the lexer level (encoding, control chars, token shape) and the parser level
(structure, duplicates, nesting). **Error productions** — grammar rules that explicitly accept a
tolerated mistake (e.g. a trailing comma) and flag it — are the principled way to express a specific
leniency, and map 1:1 to a per-feature toggle (§4).

### 11.3 One grammar, swappable policy (generators vs hand-written)
- **ANTLR** separates the grammar from a **pluggable error strategy**: `DefaultErrorStrategy`
  (single-token insert/delete + resync → lenient-ish) vs `BailErrorStrategy` (throw on first error,
  abort → **strict/fail-fast**). You vary the *strategy*, not the grammar.
- **Hand-written recursive descent** (UTL-X's language parser): the policy is explicit in code per
  construct (error-and-bail vs warn-and-continue vs coerce).

**Best practice:** keep **one strict grammar as the single source of truth** and vary policy *around*
it (error strategy / opt-in error productions / a pre-normalisation pass). **Do not fork** a "lenient
grammar" and a "strict grammar" — divergent grammars are the classic source of **parser
differentials** (§11.4). This is why §3 models profiles as toggles over one strict definition.

### 11.4 Security literature — the justification for "guard = strict"
- **LANGSEC (language-theoretic security)** — Sassaman, Patterson, Bratus et al. Keep input languages
  as simple as possible; drive parsing from an **explicit grammar**; the parser must be a **full
  recognizer that completely accepts-or-rejects *before* any processing**; ad-hoc/lenient "**shotgun
  parsers**" create **weird machines** attackers exploit. This is the formal basis for the guard's
  maximum-strictness, reject-never-recover posture.
- **Postel's-Law critique** — "be liberal in what you accept" is now treated as a **trust-boundary
  anti-pattern**. Leniency breeds divergence; the resolution is **liberal *inside* a trusted system,
  strict *at* the boundary** — exactly the lenient-interior / strict-guard split.
- **Parser differentials** — when two parsers (or two *modes*) disagree on the same bytes you get
  smuggling/evasion (HTTP request smuggling; XML signature-wrapping; the JSON duplicate-key
  divergences catalogued in Bray, *"Parsing JSON is a Minefield"*). Hence the guard must be the
  **strictest parser in the chain**, and we measure cross-parser agreement (the `parser-differentials`
  metric in Phase 0b).

### 11.5 Industry precedent — profiles are composed feature flags
Mainstream parsers expose **fine-grained flags composed into modes**, validating §3's model:

- **Jackson** — `JsonReadFeature` (`ALLOW_JAVA_COMMENTS`, `ALLOW_TRAILING_COMMA`,
  `ALLOW_UNQUOTED_FIELD_NAMES`), `STRICT_DUPLICATE_DETECTION`, and `StreamReadConstraints`
  (max depth / number / string length).
- **JAXP / XML** — `FEATURE_SECURE_PROCESSING`, `disallow-doctype-decl`, external-entity disabling
  (OWASP XXE Prevention) = our **XXE-always-off** floor.
- **Go `encoding/json`** — `DisallowUnknownFields`, `UseNumber`.

Two axes the literature keeps **separate** from the leniency dial (as §2/§4 do):
- **Resource bounds** (depth/size, entity-expansion / *billion-laughs*) — a **DoS/termination** axis,
  not grammar strictness.
- **Well-formed vs valid** — XML's own split = our **parse-strictness vs `validate.*`** layering.

### 11.6 Serializer canonicalisation — the standards
"`strict` serialize = canonical" rests on real specs: **W3C XML Canonicalisation (C14N)** and the
**JSON Canonicalization Scheme (RFC 8785, JCS)**. The guard's "rebuild canonical" write should
conform to these per format (§12, open question 5).

### 11.7 References
- Aho, Lam, Sethi, Ullman — *Compilers: Principles, Techniques, and Tools* (error recovery, §4.1).
- Sassaman, Patterson, Bratus, Locasto — *The Halting Problems of Network Stack Insecurity* / LANGSEC
  corpus (langsec.org): full-recognition, weird machines, shotgun parsers.
- Bray — *"Parsing JSON is a Minefield"* (parser differentials, duplicate keys, number edge cases).
- OWASP — *XML External Entity (XXE) Prevention Cheat Sheet*.
- ANTLR 4 reference — `DefaultErrorStrategy` vs `BailErrorStrategy` (pluggable error handling).
- W3C — *Canonical XML 1.1 (C14N)*; IETF — *RFC 8785, JSON Canonicalization Scheme*.
- Jackson `StreamReadConstraints` / `JsonReadFeature`; JAXP `FEATURE_SECURE_PROCESSING`.

---

## 12. Open questions

1. **Toggle granularity** — final per-feature toggle set per format; which are overridable under
   `standard` vs frozen under `strict`.
2. **Per-format defaults** — do we ship sensible per-format profile defaults (e.g. XML leans stricter
   than CSV by nature)?
3. **`strict` vs "canonical-only" input** — should `strict` *require* input already in canonical form,
   or accept non-canonical-but-clean and canonicalise on write? (Leaning: accept clean, canonicalise
   on write; reject unclean.)
4. **Reason-code taxonomy** — align `strict` rejection reasons with the guard verdict categories so a
   parse rejection and a policy rejection read consistently.
5. **Serializer canonical spec per format** — pin the canonical form for each output format (JSON key
   order & number format, XML c14n flavour, etc.).
6. **Naming** — `lenient/standard/strict` vs `tolerant/spec/hardened`; confirm the triad and whether a
   4th (e.g. `paranoid`) is ever warranted beyond `strict`.
