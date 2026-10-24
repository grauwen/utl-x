# VB01: `validate.assert` raw `check:` crashes on the malformed data it exists to catch

> **VB = Validation Bug** — the new prefix for `%utlx 1.1` / `validate.*` (UTLXs) defects, kept separate
> from core (`B`), engine (`EB`) and IDE (`IB`) bugs. "Validation" names the feature; "UTLXs" is only the
> deployment mode, so `VB` (not `SB`) is the clearer family. This is **VB01**.

**Status:** **FIXED (2026-10-22, pending commit)** via Option A — `validate.assert` is now an
interpreter-level higher-order builtin taking **lambda** checks `check: (p) => <expr>`, each invoked under
try/catch so a non-evaluable check becomes a reported error issue. Found 2026-10-16 while adding the first
*real* `validate.assert` tests (prior fixtures only used present, well-typed fields). See §Implementation.
**Priority:** **High** — a validator that **crashes on exactly the bad data it is meant to report** is
self-defeating; for a boundary guard this is a hard failure (the transform aborts with a cryptic engine
error instead of emitting a clean `invalid` verdict + routing to dead-letter).
**Component:** `stdlib/.../validate/ValidateFunctions.kt` `assert` + the eager evaluation of a rule's
`check` expression by the `:core` interpreter's comparison operators.

> **One-line:** `$input |> validate.assert([{ check: $input.price > 0, … }])` on input where `price` is
> **missing** (`null > 0`) or **mistyped** (`"abc" > 0`) throws `Error: Cannot compare these types` and
> aborts the whole validation, instead of recording a clean failed issue.

## Evidence (via the built CLI)

Script: `$input |> validate.assert([{ rule:"price-positive", check: $input.price > 0, message:"…", field:"price" }])`

| Input | `check` evaluates | Result |
|---|---|---|
| `{"price": -1}` | `-1 > 0` → false | ✅ clean `valid:false`, one failed issue |
| `{"price": 5}` | `5 > 0` → true | ✅ clean `valid:true` |
| `{}` (missing) | `null > 0` | ❌ **`Error: Cannot compare these types`** — aborts |
| `{"price": "abc"}` (mistyped) | `"abc" > 0` | ❌ **`Error: Cannot compare these types`** — aborts |

## Blast radius — narrow, and important to state

The **named** validators are **robust** (no crash on missing/mistyped):

| | `{}` missing | `{"price":"abc"}` mistyped |
|---|---|---|
| `validate.required(["price"])` | `valid:false` ✅ | `valid:true` (present) |
| `validate.inRange("price",0,100)` | `valid:false` ✅ | `valid:false` ✅ |
| `validate.oneOf("price",[50,60])` | `valid:false` ✅ | `valid:false` ✅ |

So the safe, recommended validation vocabulary degrades gracefully. **Only `assert`'s free-form `check:`
boolean expression is crash-prone** — because it is raw interpreter arithmetic/comparison, not a
null/type-aware validation primitive.

*(Side note, not this bug: `validate.oneOf("price",[50,60])` returned `valid:false` for `price:50`, which
is in the list — a possible `oneOf` numeric-equality quirk. Tracked separately, and since fixed as
[VB03](VB03-validate-oneOf-numeric-membership-fails.md): a JSON Int and a script-literal Double of the
same number compared unequal. Note the table above therefore overstated `oneOf`'s robustness at the
time it was written.)*

## Root cause

`assert` receives **pre-evaluated** `check` booleans:

```kotlin
// ValidateFunctions.assert — the rule's `check` is already a UDM boolean by the time assert sees it
if ((p["check"] as? UDM.Scalar)?.value == true) continue   // pass; else → failed issue
```

The expression `$input.price > 0` is evaluated by the interpreter **while building the rule-array
literal**, i.e. *upstream* of `assert`. When `price` is `null` (missing) or a `String` (mistyped), the
comparison operator throws `Cannot compare these types` there and then — so `assert` never runs and can
isolate nothing. Per-rule error isolation is impossible with eager pre-evaluation: to catch a
non-evaluable check, evaluation must be **deferred into `assert`**.

## Options

### A. Lazy per-rule evaluation (lambda checks) — the "proper" robust fix
`check: () => $input.price > 0` — a thunk `assert` invokes under `try/catch`: `true`→pass, `false`→fail,
**throws→a reported `error` issue** ("rule X could not be evaluated: …"). All rules still evaluated.
- **Pros:** fully robust, per-rule isolation, general (any expression).
- **Cons:** a lambda can't live inside a `UDM.Object`, so `assert` must become an **interpreter-handled
  higher-order builtin** (like `map`/`filter`/`reduce`, which take `RuntimeValue.FunctionValue` and invoke
  it via the owner interpreter). That crosses the `:core`/`:stdlib` boundary, must keep the namespace gate,
  and **changes author syntax** (`check:` must be `() =>`), breaking existing fixtures/docs. Large,
  architecturally sensitive.

### B. Null/type-tolerant comparison operators
Make `>`/`<`/`>=`/`<=` return `false` (or null-propagate) instead of throwing on null/mismatch.
- **Pros:** keeps the ergonomic `check: $input.price > 0`; fixes the crash with no API change.
- **Cons:** changes **core operator semantics globally** — also affects *mapping*, where a throw on bad
  data may be the desired early-failure. Weakens mapping's error detection. Risky.

### C. Named-vocabulary-first + defensive-check guidance (lightest)
Treat the **named validators as the safe path** (they're robust); document that raw `check:` is a
power-user escape hatch requiring defensive expressions (e.g. `isNumber($input.price) and $input.price > 0`),
and/or grow the named vocabulary (a type guard, `positive`, …) so raw checks are rarely needed on untyped
data.
- **Pros:** cheap, no risky engine change, steers authors to the robust path.
- **Cons:** raw `check:` still crashes if misused; relies on docs + discipline.

### D. Deferred implicit context (bare condition, no lambda) — *added 2026-10-24*

Keep the author-facing syntax `check: price > 0` — no lambda — but stop evaluating it eagerly.
`validate.assert` already receives **unevaluated AST** (`evaluateValidateAssert(arguments:
List<Expression>, …)`), so it could pull each rule's `check` Expression out of the rules-array
literal and evaluate it **per rule** in a child environment where the payload's fields are bound as
variables, parent being the enclosing scope. Ordinary lexical lookup then resolves `price` from the
payload *and* an outer `let` binding from the enclosing scope, with no AST rewriting
(`price` → `p.price`) needed.

**Why this option exists at all:** it corrects a flaw in this document's own reasoning. A and D are
equally robust. The crash was caused by **eager evaluation at object-construction time**, not by the
absence of a lambda — so "robustness" never distinguished A from a deferred *implicit* form. The
original A/B/C set simply missed D. (Raised by the author 2026-10-24; the gap was real.)

- **Pros:** the terse syntax most authors reach for first; no `p.` noise; would also make the legacy
  eager `check: $input.price > 0` form robust as a side effect, since it too would be deferred and
  wrapped per rule.
- **Cons, in the order that decided it:**
  1. **Inline-only — the deciding defect.** Deferral needs the AST, and a rules array that has been
     stored in a variable no longer has one; it was evaluated at the `let`. Verified:
     ```utlx
     let rules = [{ check: price > 0, … }]        in $input |> validate.assert(rules)
         → Error: Undefined variable: price            ✗
     let rules = [{ check: (p) => p.price > 0, … }] in $input |> validate.assert(rules)
         → valid:true                                  ✓
     ```
     So D would work **only** when rules are written inline at the call site. Rule sets grow, and the
     first thing done with a growing set is to name it and share it — at which point every rule
     breaks with a message that does not hint that inlining was the requirement. A sugar that stops
     working when the code is refactored is worse than no sugar.
  2. **Shadowing has no non-surprising answer.** `let price = 100 in … check: price > 0` — field or
     variable? Either resolution order silently surprises someone. `p.price` raises no question.
  3. **It re-opens the `[condition]` question.** D *is* an implicit current item, scoped to one
     property. Accepting it here badly weakens the case for rejecting `$input.items[price > 100]`
     (see `docs/language-guide/selectors.md`): the language would then have implicit context in one
     construct and not the other — harder to teach than either consistent answer.
  4. **It taxes the IDE work.** With `(p) => p.` completion is mechanical. With a bare `price` the
     language server needs a bespoke rule — inside a `check:` property, complete from the payload
     schema *and* lexical scope, applying the shadowing rule — landing in precisely the Phase 5
     LSP work that is next.

## Decision — Option A (lambda checks), for functional-consistency, not just robustness

**Updated 2026-10-22 after verifying how UTL-X expresses conditions.** The deciding fact: a *deferred
condition over a value* has exactly **one working form in the language — a lambda**. Verified empirically
on the built CLI:

| Form | Result |
|---|---|
| `filter($input.items, x => x.price > 100)` | ✅ works — the only form that does |
| `filter($input.items, price > 100)` (bare condition as arg) | ❌ `Undefined variable: price` |
| `$input.items[price > 100]` (predicate syntax **documented** in `selectors.md`) | ❌ `Undefined variable: price` — the doc is **inaccurate**; the feature is unimplemented (no `parsePredicate` in the parser, no conformance test) |

So `validate.assert`'s eager `check: $input.price > 0` is an **anomaly**: it is the *only* place in the
language that accepts a raw condition expression as data, and it only "works" because it is **eagerly
pre-evaluated to a boolean upstream of `assert`** — which is precisely what makes it crash on absent/
mistyped data (this bug). Every other condition in UTL-X is a lambda, deferred and function-controlled
(`map`/`filter`/`reduce`). Earlier this doc leaned to Option **C** (treat A as a heavy bolt-on). That was
wrong framing: **A is not a bolt-on — it brings `validate.assert` back in line with the language's own
and only condition idiom.** The eager `check:` was the deviation; a lambda `check` is the native form,
and it is *why* it becomes robust (deferred → `assert` wraps each eval in try/catch → non-evaluable check
becomes a reported `error` issue instead of a process-aborting throw).

**Chosen: A.** `check` becomes a lambda `(p) => p.price > 0`; `assert` becomes an interpreter-handled
higher-order builtin (same mechanism as `map`/`filter`/`reduce` — takes a `RuntimeValue.FunctionValue`,
invokes it via the owner interpreter), preserving the namespace gate. The **named validators remain the
primary, declarative vocabulary** (already robust); lambda-`assert` is the power-user escape hatch done
*functionally*. The eager inline `check: <expr>` boolean form is removed (it was the anti-pattern).
**B rejected** (global blast radius on mapping). **C rejected** as the end state (keeps the anomaly).

**D rejected (2026-10-24), on ergonomics-vs-reach rather than robustness.** D is as robust as A —
that is the correction above — so the choice turns on reach and consistency instead: A works wherever
a rule list is written, D only inline (§Options D cons 1). The remaining complaint D was trying to
answer — that `p.` is noise — is better addressed by **growing the named validators**, which are
shorter than either lambda form *and* type-safe rather than merely crash-resistant. If the terse
condition syntax is still wanted, it should be decided once, language-wide, together with the
`[condition]` selector — one answer about whether UTL-X has an implicit current item, applied
consistently — not introduced in a single property where it works inline and nowhere else.

## Implementation (2026-10-22)

- **`:core` interpreter** (`interpreter.kt`): `tryLoadStdlibFunction` intercepts `functionName ==
  "validate.assert"` **after the version gate** and calls a new `evaluateValidateAssert(arguments, …)`.
  It evaluates the payload and the rules array at the **RuntimeValue** level (so a lambda `check` stays a
  `FunctionValue` instead of failing UDM conversion), then for each rule invokes the `check` lambda with
  the payload under `try/catch`:
  - `true` → pass · `false` → fail (message/field/severity as given) · **throws → error-severity issue**
    `"check '<rule>' could not be evaluated: <msg>"` (**fail-closed**: a non-evaluable check escalates to
    `error` even if the rule declared `severity: warning`, so malformed data can't slip through as a
    mere warning).
  - All rules always evaluated (no short-circuit). Each resolved `check` becomes a plain boolean, so the
    rules are now UDM-safe and dispatch to the **unchanged** stdlib `validate.assert` for ValidationResult
    aggregation. `:core ↛ :stdlib` preserved (dispatch via the `nativeFunctions` map, no import).
  - Works on **all strategies** (COMPILED/TEMPLATE fall back to the interpreter for namespaced calls).
- **`:stdlib`** `ValidateFunctions.assert` is **unchanged** (still reads a boolean `check`); its UDM-level
  unit tests remain valid.
- A legacy non-lambda `check` is passed through untouched (but the eager inline form is no longer the
  documented idiom; fixtures migrated to lambdas).

## Tests (added / migrated)
- `conformance .../validate/assert_lambda_missing_field.yaml` — missing field → clean error issue (no crash). ✅
- `conformance .../validate/assert_lambda_no_short_circuit.yaml` — all rules evaluated; non-evaluable
  checks escalate to error (fail-closed). ✅
- `assert_severity.yaml` — migrated eager `check: $input.x` → lambda `check: (p) => p.x`; output unchanged. ✅
- Named validators (`required`/`inRange`/`oneOf`/…) already robust on missing/mistyped — verified.
- Still open (separate, not this bug): the `oneOf` numeric-equality quirk; the `selectors.md` predicate-doc
  inaccuracy (flagged in-doc 2026-10-22; core/docs gap — predicate syntax is unimplemented).
