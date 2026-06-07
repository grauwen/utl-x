# B25: stdlib functions fail **inside `map`/`filter`/`reduce`/templates** on the native binary — higher-order builtins evaluate lambdas on a throwaway `Interpreter()` that lacks the stdlib lookup

**Status:** Root cause **CONFIRMED & reproduced** on the released native binary. **Fix B IMPLEMENTED** on `development` (higher-order builtins reuse the calling interpreter) — core compiles, JVM verified (`parseNumber`/`length` now work inside `map`), **conformance suite 522/522 (100%)**. Pending: cherry-pick to `main` + native rebuild to confirm on the binary; then the duplicate-builtin cleanup.
**Priority:** Critical — breaks the README example and shipped `.utlx` examples on v1.2.1 native (Windows/macOS/Linux), XML and JSON inputs.
**Created:** June 2026
**Reported:** GitHub issue #3 (@skin27 / Raymond Meester) — Windows 11, UTL-X CLI v1.2.1 (Oracle GraalVM native).
**Related:** B19 (the no-reflection lookup this depends on, and the leftover reflection fallback this hits).

> **Filename note:** kept as `…parseNumber-not-implemented…` for link stability, but the title is the
> accurate description. `parseNumber` *is* implemented and registered — see below.

---

## TL;DR

`parseNumber` works at the **top level** but throws on the **native** binary when called **inside a
higher-order function** (`map`, `filter`, `reduce`, templates, …). Those builtins evaluate their
lambda body on a **freshly constructed `Interpreter()`** that never received the stdlib lookup map, so
the call falls through to a reflection path that is broken in the native image. On the JVM the
reflection fallback succeeds, which is why this was invisible until someone ran the native binary.

## Reproduction (PowerShell, against the native `utlx.exe`)

**Top-level call — WORKS:**
```powershell
'%utlx 1.0','input json','output json','---','{ n: parseNumber("123.45") }' | Set-Content t.utlx; '{}' | Set-Content i.json; .\utlx.exe transform t.utlx i.json
# → { "n": 123.45 }
```

**Same function inside `map` — FAILS:**
```powershell
'%utlx 1.0','input json','output json','---','{ r: $input.items |> map(x => parseNumber(x.q)) }' | Set-Content t3.utlx; '{"items":[{"q":"2"},{"q":"3"}]}' | Set-Content i3.json; .\utlx.exe transform t3.utlx i3.json
```
```
DEBUG: Failed to execute stdlib function 'parseNumber':
  NoSuchMethodException: org.apache.utlx.stdlib.UTLXFunction.execute(java.util.List)
        at ...Interpreter.tryLoadStdlibFunction(interpreter.kt:1093)
        at ...Interpreter.evaluateFunctionCall(interpreter.kt:1058)
        at ...Interpreter.evaluate(interpreter.kt:455)
        at ...StandardLibraryImpl$registerAll$14.invoke(interpreter.kt:1628)   ← the map builtin
        at ...StandardLibraryImpl$registerAll$14.invoke(interpreter.kt:1606)
        at ...Interpreter.evaluateFunctionCall(interpreter.kt:1038)            ← the map(...) call
        ...
        at ...cli.service.TransformationService.transform(TransformationService.kt:137)
Error: Undefined function: parseNumber
```
This is byte-for-byte the reporter's stack trace. **JVM build (`cli-1.2.1.jar`) works for both.**

(Scope probe: `{ up: upper("hi"), s: sum([1,2,3]) }` at top level works; the same functions inside
`map` fail the same way — confirming it's *any non-builtin stdlib function inside a higher-order
construct*, not parseNumber-specific.)

## Root cause (confirmed)

1. `Interpreter` holds the stdlib lookup as **instance state**:
   `private var stdlibLookup … = emptyMap()` (`interpreter.kt:153`). It is populated only by
   `TransformationService.ensureStdlibLookup()` → `interpreter.setStdlibLookup(...)` on the **one**
   interpreter the service creates.
2. The higher-order builtins in `StandardLibraryImpl` — `map` (`interpreter.kt:1627`), plus `filter`,
   `reduce`, and friends (**10 `val interpreter = Interpreter()` sites**: 1627, 1650, 1664, 1679,
   1693, 1711, 1726, 1761, 1794, 1835) — evaluate their lambda body on a **brand-new `Interpreter()`**.
   They do this because `StandardLibraryImpl` is a separate class with no reference back to the owning
   interpreter (contrast `interpreter.kt:1283`, which correctly uses `this@Interpreter` from *inside*
   `Interpreter`).
3. That throwaway interpreter starts with `stdlibLookup = emptyMap()`. So inside the lambda:
   `parseNumber` not in env → `tryLoadStdlibFunction` (`interpreter.kt:1067`) →
   `tryRegisterFromLookup` reads the **empty** lookup → `false` →
   `tryDirectFunctionInvocation` (a hardcoded `when` that does **not** include `parseNumber`) →
   **reflection fallback** (`Class.forName(...).getMethod("execute", List)`, `interpreter.kt:1093`) →
   `NoSuchMethodException` on the native image (no reflection metadata) → `Undefined function`.

### Why the symptoms line up
- **Top level works** — the original interpreter has the lookup.
- **Inside `map` fails** — fresh interpreter, empty lookup, native-broken reflection.
- **JVM works everywhere** — the reflection fallback succeeds on the JVM; only native lacks the metadata.
- **`toNumber` works as a workaround, even nested** — it is a hardcoded interpreter builtin re-registered
  by `registerAll` in *every* interpreter (including the throwaway), so it never needs the lookup.
  `parseNumber` is **not** a builtin (it lives only in the stdlib module), so nested calls depend on the
  lookup the throwaway interpreter lacks.

### Correction to earlier theories in this doc's history
- ❌ "parseNumber has no runtime implementation" — wrong; it's implemented + registered
  (`stdlib/.../Functions.kt:785`, called from `init`) and works on the JVM.
- ❌ "the whole lookup is empty in native (build-time `initialize-at-build-time` snapshot)" — wrong; the
  lookup is fine on the real interpreter (top-level calls prove it). The defect is the **throwaway
  interpreters**, not the snapshot.

## Impact

- Any shipped/example transform that calls a stdlib-module function inside `map`/`filter`/`reduce`/
  templates breaks on the native binary (`order-to-invoice.utlx`, `template-matching.utlx`,
  `recursive-templates.utlx`, `02-xml-to-json.utlx`, `xml-to-json-simple.utlx`).
- Top-level stdlib calls are unaffected, which made it easy to miss.

## Fix

**A. Shared-lookup patch — CONSIDERED, then REJECTED.** Making `stdlibLookup` a companion/static field
(so every `new Interpreter()` sees it) is a one-line change that fixes #3. It was prototyped and
verified to compile + pass on the JVM, **but reverted** — it's a band-aid that papers over the real
defect (the throwaway interpreters) and leaves the performance/correctness problems below in place.
Decision: do not ship the band-aid; fix the root cause (B).

**B. Chosen fix — eliminate the `new Interpreter()`-per-lambda pattern.** Have the higher-order builtins
(`map`/`filter`/`reduce`/`find`/`findIndex`) reuse the **calling** interpreter instead of constructing a
fresh one (give `StandardLibraryImpl` an interpreter/evaluate reference, or move these into `Interpreter`
where `this@Interpreter` is available — cf. line 1283). This makes the populated lookup reachable from
inside lambdas (fixing #3), and *also* removes a real **performance** bug (a fresh interpreter —
re-running `registerAll` — *per array element*) and a correctness gap (lambda bodies currently lose input
metadata and other instance state). Bigger change than A, so it is the **patched release**, not a hotfix.
See "the two-tier builtin problem" below for the companion cleanup (removing the duplicated builtins) that
should follow B.

**C. Fallback hardening (low priority).** The reflection branch in `tryLoadStdlibFunction` cannot work
on native and only turns a clean "Undefined function" into a misleading `NoSuchMethodException`. Either
remove it, or at least fix the stale `reflect-config.json` entry
(`org.apache.utlx.stdlib.Functions$UTLXFunction` → the real top-level `org.apache.utlx.stdlib.UTLXFunction`).
After fix A this path should no longer be reached.

**D. Regression guard.** Add a test that runs a stdlib function **inside `map`** end-to-end (ideally
against the native binary in CI), plus an assertion that a `new Interpreter()` can resolve a lookup-only
function. The top-level-only tests passed throughout — the gap was specifically nested evaluation.

## Why it looked like "only `parseNumber`" — the two-tier builtin problem

The deeper reason this was so confusing: the interpreter keeps **~24 functions hardcoded as builtins**
(registered into *every* interpreter via `StandardLibraryImpl.registerAll`, including the throwaway
`new Interpreter()` that `map` creates). Those are immune to this bug — they resolve without the
lookup, everywhere (top-level, nested, JVM, native). Everything else in the stdlib (~650 functions)
depends on the lookup and therefore fails nested on native.

Full hardcoded-builtin set:
```
abs ceil count distance filter find findIndex first floor last lower
map pow reduce round sha256 sqrt sum toNumber toString trim upper
```

These split into two groups with very different justifications:

| Group | Functions | Needs interpreter? | In stdlib too? | Verdict |
|---|---|---|---|---|
| **1 — higher-order** | `map`, `filter`, `reduce`, `find`, `findIndex` | **Yes** — must call `interpreter.evaluate(lambda.body)` | yes (but the *working* impl is the interpreter's) | **Keep** (necessary) |
| **2 — pure value fns** | `abs ceil count distance first floor pow round sha256 sqrt sum toNumber toString trim` (15, all duplicated in stdlib) + `upper`/`lower` (interpreter-only) | No | 15 = yes; `upper`/`lower` = no | **Legacy duplication → remove** (after the fix) |

**This duplication is the bug's source of confusion.** `toNumber` is a Group-2 builtin (immune when
nested); `parseNumber` is stdlib-only (not). Two near-identical functions, opposite behavior inside
`map` — an accident of which conversions someone hardcoded years ago. As long as a hand-picked subset
is duplicated as builtins, there is a **two-tier stdlib**: some functions work inside `map`, some
don't, arbitrarily. `parseNumber` is simply the most common stdlib-only function used inside a `map`
(string→number over rows from XML/CSV), so it's the one everyone hits — but `length`, `parseDouble`,
`parseBoolean`, and hundreds more fail identically.

### Recommendation (architecture, beyond the immediate fix)
1. **Keep Group 1** (the 5 higher-order functions) — they are legitimately interpreter-bound. Fix per
   **Fix B** (reuse the calling interpreter so the lookup is reachable from inside lambdas).
2. **Remove Group 2** (the 15 value-function duplicates) so each function has a single implementation
   (the stdlib module), served uniformly by the lookup. This permanently eliminates the
   "works at top level, fails in `map`" class of bugs and the `toNumber`/`parseNumber` inconsistency.
3. **Ordering constraint:** do step 1 **first**. Removing the duplicates *before* the lookup works
   nested would *break* those functions inside `map` instead of fixing them.
4. **Caveat — `upper`/`lower`:** these appear to be interpreter-*only* (not registered in the stdlib by
   those names — possibly present as `uppercase`/`lowercase`). Do not delete them until the stdlib has
   verified equivalents, or you remove the only implementation.

This consolidation is a real architectural item ("single source of truth for stdlib: consolidate
interpreter builtins into the stdlib module") and belongs in the **patched-release-then-cleanup**
sequence, not the hotfix.

## Code pointers
- `modules/core/.../interpreter/interpreter.kt`: `stdlibLookup` (153), `setStdlibLookup` (155),
  `tryRegisterFromLookup` (168), `tryLoadStdlibFunction` + reflection branch (1067–1130),
  `tryDirectFunctionInvocation` (1135, no `parseNumber`), `map` builtin + `new Interpreter()` (1627),
  the other 9 `new Interpreter()` sites, and the correct `this@Interpreter` usage at 1283.
- `modules/cli/.../service/TransformationService.kt`: `ensureStdlibLookup` / `setStdlibLookup` call (135).
- `stdlib/.../Functions.kt`: `parseNumber` registration (785, via `registerConversionFunctions()` ← `init`).

## Notes
- The native release prints a `DEBUG:` line + full stack trace to the user on this failure — noisy for
  a shipped binary; clean up alongside (C).
- Workaround for users on v1.2.1: replace `parseNumber(x)` with `toNumber(x)` (works nested).
