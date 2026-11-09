# EB04: nested lambdas emit duplicate method names, so the class never loads

> **EB = engine bug** (`modules/engine` — the execution strategies and the runtime they
> call), the family of EB01–EB03. This is **EB04**.

**Status:** **FIXED.**
**Priority:** Medium. It never produced a wrong answer — it silently disabled compilation for the
affected transformations, so the cost was performance and a false belief about what was tested.
**Component:** `:engine` — `ASTCompiler.CompileContext.lambdaCounter` / `compileLambda`.

> **One-line:** a lambda nested inside another lambda generated a second method called `lambda_0`,
> the JVM rejected the class, and the compiled strategy quietly fell back to the interpreter.

## Root cause

Each lambda becomes a static method on the generated class, named from a counter:

```kotlin
private data class CompileContext(
    …
    var lambdaCounter: Int = 0
) {
    fun nextLambdaName(): String = "lambda_${lambdaCounter++}"
}
```

`compileLambda` then builds a **fresh context** for the lambda body, because the body needs its own
local-variable slots and its own `MethodVisitor`:

```kotlin
val lambdaCtx = CompileContext(lambdaMv, localSlot = 1,
    locals = mutableMapOf(), classWriter = cw, className = className)
```

`lambdaCounter` defaults to `0`, so the counter restarted inside every lambda. Sibling lambdas were
fine — they share the parent context — but a lambda *nested* in another emitted `lambda_0` twice:

```
java.lang.ClassFormatError: Duplicate method name "lambda_0"
    with signature "(Ljava.util.List;)Lcom.glomidco.utlx.core.udm.UDM;"
```

The mistake is a scoping one. One generated class is one method namespace, so the counter belongs to
the **class**, not to a context — and contexts are created per method body. The fix is a small
shared name source passed down to nested contexts.

## Why nobody noticed

`CompiledStrategy` catches a compilation failure and **falls back to the interpreter**. So the
transformation still produced the correct output; it simply never compiled. Nothing was wrong in the
result, nothing appeared in a test, and the only cost — losing the compiled path — is invisible from
outside.

Eight conformance fixtures were affected. All eight were passing, on both the Python suite and the
engine suite, while running interpreted on the strategy whose entire purpose is not to.

**It was found by counting.** The cross-strategy conformance run
([conformance-across-execution-strategies](../architecture/conformance-across-execution-strategies.md))
asks `ASTCompiler.compile` directly how many fixtures reach bytecode, precisely because a silent
fallback is invisible to any assertion about output. That count went from 313 to **321** when this
was fixed — the eight fixtures, exactly.

This is the argument for that count existing at all. A parity test without it can pass while
measuring the interpreter three times over.

## What the fix exposed underneath

Of the eight, only **three** then agreed across all strategies. The other five had a *second*
divergence hiding behind the compilation failure, now visible and reclassified:

| fixture | now |
|---|---|
| `stdlib/array/chunkBy_basic` | agrees — delisted |
| `stdlib/array/mapGroups_basic` | agrees — delisted |
| `tutorial-examples/csv_orders_grouping` | agrees — delisted |
| `formats/jsch/transformation/generate_validation_rules` | `ASTCompiler` invokeinterface defect |
| `stdlib/array/lookupBy_basic` | `ASTCompiler` invokeinterface defect |
| `tutorial-examples/csv_orders_grouping_pipe` | COPY output differs |
| `tutorial-examples/csv_orders_grouping_semicolon` | COPY output differs |
| `tutorial-examples/csv_orders_grouping_tab` | COPY output differs |

Known divergences across the corpus: **101 → 98**, and the `ASTCOMPILER_DUPLICATE_LAMBDA` category
is gone. The invokeinterface defect grew from 5 fixtures to 7, and COPY output from 44 to 47 —
not a regression, just two defects that had been masked by a third.

## Tests

`EB04NestedLambdaTest` (`:engine`) asserts `compile(...) != null` before comparing output, because
*that it compiled* is the property under test — a test that only checked the result would pass on
the fallback and prove nothing. Cases: a lambda in a lambda, three levels of nesting, sibling
lambdas (which were always fine, so a fix that over-shares one counter cannot break them), and
nesting plus siblings together.

Revert-verified: restoring the per-context counter fails 3 of the 4, and the sibling-only case
correctly keeps passing.

The compiled-coverage floor in the conformance parity run was raised 313 → 321, so this cannot
silently regress.

## Related

- [conformance-across-execution-strategies](../architecture/conformance-across-execution-strategies.md)
  — the harness that found it, and why its compiled count is the load-bearing part.
- [EB03](EB03-compiled-comparison-coerces-types-so-strategies-disagree.md) — the gap that harness
  closed; this is the first defect it found on its own rather than being written to catch.
- [B34](B34-logical-operators-do-not-short-circuit.md) — the other `ASTCompiler` defect, and the
  origin of the discipline of asserting that something genuinely compiled.
