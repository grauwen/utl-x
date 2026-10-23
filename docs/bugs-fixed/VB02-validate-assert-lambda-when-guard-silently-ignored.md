# VB02: a lambda `when` guard on a `validate.assert` rule is silently ignored

> **VB = Validation bug** (`%utlx 1.1` / `validate.*`), the family opened by
> [VB01](VB01-validate-assert-raw-check-crashes-on-missing-or-mistyped-data.md). This is **VB02**.

**Status:** **FIXED (2026-10-23, pending commit)** — `when` is now resolved in the interpreter
alongside `check`, with the same lambda contract. Found 2026-10-23 while writing the
[Validation Rules](../language-guide/validation-rules.md) guide: documenting `when` meant probing it,
and none of its three spellings turned out to be safe. See §Implementation.
**Priority:** **High** — this is a **silent-wrong**, not a crash. A rule the author believes is
scoped to a subset of records is evaluated against **all** of them, with no diagnostic: a
false-positive generator in a boundary guard.
**Component:** `:core` `interpreter.kt` `evaluateValidateAssert` (the VB01 resolver) +
`stdlib/.../validate/ValidateFunctions.kt` `assert`'s eager read of `when`.

> **One-line:** VB01 migrated a rule's `check` to a lambda but left `when` reading an **eagerly
> pre-evaluated** boolean, so `when: (p) => p.type == "WHOLESALE"` was a `FunctionValue` the stdlib
> could not read — it never matched `== false`, the rule was never skipped, and the guard silently
> did nothing.

## Evidence (via the built CLI, before the fix)

Rule: `{ rule: "qty-positive", check: (p) => p.qty > 0, …, when: <form> }`, payload
`{"name":"ok","type":"RETAIL"}` (no `qty`).

| `when` form | Expected | Actual (pre-fix) |
|---|---|---|
| `(p) => false` | rule skipped | ❌ **rule evaluated** — guard silently ignored |
| `(p) => p.type == "WHOLESALE"` | skipped on a RETAIL record | ❌ **rule evaluated** |
| `$input.qty > 0` (eager) | skipped / applied | ❌ **`Error: Cannot compare these types`** — aborts (the VB01 crash class, un-migrated) |
| `false` (literal) | rule skipped | ✅ skipped, counts as passed |

So the guard had **no safe spelling**: the lambda form was silently inert, the eager form crashed on
exactly the data a guard exists for, and only a useless literal worked.

## Root cause

Two halves, both inherited from the VB01 fix being scoped to `check` only.

**1. The stdlib reads `when` eagerly.** `ValidateFunctions.assert`:

```kotlin
// `when` guard: a rule whose `when` is explicitly false is skipped and counts as passed.
if ((p["when"] as? UDM.Scalar)?.value == false) continue
```

A lambda is a `RuntimeValue.FunctionValue`, never a `UDM.Scalar`, so the cast yields null, the
comparison is false, and the rule proceeds — **silently**. An eager `when` expression, meanwhile, is
evaluated while the rule-array literal is built, upstream of `assert`, which is precisely the VB01
crash.

**2. The VB01 resolver bailed out before looking at `when`.** `evaluateValidateAssert`:

```kotlin
val checkVal = rule.properties["check"]
if (checkVal !is RuntimeValue.FunctionValue) return@map rule  // ← leaves `when` unresolved
```

The early return keyed solely on `check`, so `when` was never resolved even when `check` *was* a
lambda, and a rule pairing a lambda `when` with a legacy boolean `check` was skipped entirely.

## Fix

`when` becomes a lambda with the same contract as `check`, resolved **first** so that a
non-applicable rule never evaluates its check:

| The `when` lambda | Outcome |
|---|---|
| returns `true` | the rule applies; `check` is then evaluated |
| returns `false` | the rule is **skipped** and counts as passed; `check` is **never** evaluated |
| **throws**, or returns a non-boolean | its own **error**-severity issue, `"guard '<rule>' could not be evaluated: …"`, with `when` forced **true** |

### Design decision — a broken guard must not suppress its rule

The one real choice here is what a non-evaluable guard means. Treating it as "not applicable" would
be **fail-open**: malformed data could silence the very rule meant to catch it, which is the failure
mode VB01 was fixed to prevent. So a broken guard forces `when = true` and fails the rule, carrying
a guard-specific message that distinguishes it from a failed `check`. As with VB01, this **escalates
over a declared `severity: warning`**.

A guard returning a non-boolean is treated the same way rather than coerced by truthiness: silently
trusting `when: (p) => "yes"` is how VB02 happened in the first place.

## Implementation (2026-10-23)

- **`:core` `interpreter.kt`** — new `invokeRulePredicate(fn, payload)` helper shared by `check` and
  `when` (returns the boolean produced, or null for a non-boolean). `evaluateValidateAssert` now:
  - keys its early return on **either** predicate being a lambda, so a lambda `when` is resolved
    even beside a legacy boolean `check`;
  - resolves `when` before `check`, returning early on a false guard with `check` set to a
    placeholder `true` (the stdlib skips the rule without reading it — kept UDM-safe regardless);
  - isolates the guard in its own try/catch, emitting the guard-specific error issue above.
- **`:stdlib`** `ValidateFunctions.assert` is **unchanged** — it still reads a boolean `when`, which
  is what the resolver now hands it. `:core ↛ :stdlib` preserved.
- The `@UTLXFunction` annotation on `assert` was also corrected in the same pass: its `example`
  still showed the eager `check: $.unitPrice > 0` form removed by VB01, and `parameters` now
  documents `check: (p) => boolean, when?: (p) => boolean`. That annotation feeds
  `scripts/generate-function-docs.py`, so it was teaching the removed idiom into generated docs.

## Tests

- `conformance .../validate/assert_when_lambda.yaml` — a false guard skips its rule (counts as
  passed) while a true guard applies it, in one script. ✅
- `conformance .../validate/assert_when_guard_non_evaluable.yaml` — a guard that cannot be
  evaluated becomes an error issue; `when` cannot be used to skip the rule. ✅
- `VB02WhenGuardTest` (`:core`, 9 tests) — asserts on what the interpreter *hands to*
  `validate.assert` via a synthetic `validate.assert` that captures the resolved rules, which is
  where VB02 lived (the aggregation never changed). A `record()` call inside the `check` lambda
  proves **laziness**: a skipped rule must not evaluate its check. Covers both guard directions, a
  field-comparing guard, the throwing and non-boolean guards, severity escalation, the legacy
  literal passthrough, and the lambda-`when`-with-boolean-`check` case from root cause (2).
  Verified to be a true regression guard: against the pre-fix interpreter, **7 of the 9 fail**, and
  the 2 that pass are exactly the vacuity guard and the literal passthrough — the parts VB02 never
  broke.
- Full conformance **539/539** on both JVM and the rebuilt GraalVM native binary;
  `language/validate` 10/10, `core/operators` 4/4.
- `:modules:core:test` green (208 tests); `:modules:engine:test` green (451 tests).

### Two traps when testing through the stdlib lookup

`Interpreter.tryRegisterFromLookup` registers into the **global** `StandardLibraryImpl.nativeFunctions`
and skips a name that is already present:

```kotlin
if (StandardLibraryImpl.nativeFunctions.containsKey(name)) return true   // first writer wins
...
StandardLibraryImpl.nativeFunctions[name] = wrapper                      // closes over the lambda
```

Both consequences bit during development, and both produce **vacuously passing** tests rather than
visible errors:

1. **Per-instance recorder state.** JUnit builds a fresh test instance per method, so the first
   method to trigger registration pins the wrapper to its own then-dead state and every later
   method observes nothing. Fix: companion state plus a `@BeforeEach` reset.
2. **A recorder name shared between test classes.** `VB02WhenGuardTest` first used a helper called
   `record`, the same name `B34LogicalShortCircuitTest` uses. Each class passes **alone** — and
   both fail in a full-suite run, because whichever registers first owns the name. Fix: a unique
   name per class (`vb02Record`).

Trap 2 was caught only because the test opens by asserting the recorder is actually reached. Any
test that drives the interpreter through this lookup should keep that guard and pick a unique
function name.

## Related

- [VB01](VB01-validate-assert-raw-check-crashes-on-missing-or-mistyped-data.md) — the `check`
  migration this completes. VB01's decision section argued the lambda is "the language's own and
  only condition idiom"; `when` was the last place still deviating from it.
- [B34](B34-logical-operators-do-not-short-circuit.md) — found in the same documentation pass.
  Until B34 was fixed, `&&` could not guard a comparison at all, which is why the guide briefly
  recommended `if`/`else` as the only defensive form inside a check.
