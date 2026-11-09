# EB07: compiled index access ignores string keys

> **EB = engine bug** (`modules/engine` — the execution strategies and the runtime they
> call), the family of EB01–EB06. This is **EB07**.

**Status:** **FIXED.**
**Priority:** **High — the largest single divergence in the corpus.** 46 of 101 known divergences
were this one defect, and it produced wrong answers silently rather than failing.
**Component:** `:engine` — `ASTCompiler.compileIndexAccess`, `RuntimeOps.getIndex`.

> **One-line:** the compiled path called `asNumber()` on the index expression, so a string key
> became `0` and `obj["name"]` read *element 0 of an object* — returning null, with no error.

## Root cause

The emitter said so itself:

```kotlin
compileExpression(ctx, expr.target)
// For now, only support integer index literals
if (expr.index is Expression.NumberLiteral) {
    ctx.mv.visitLdcInsn((expr.index as Expression.NumberLiteral).value.toInt())
} else {
    compileExpression(ctx, expr.index)
    ctx.mv.visitMethodInsn(INVOKEVIRTUAL, UDM_TYPE, "asNumber", "()D", false)   // ← a string key becomes 0.0
    ctx.mv.visitInsn(D2I)
}
```

and the runtime only knew arrays:

```kotlin
fun getIndex(target: UDM, index: Int): UDM = when (target) {
    is UDM.Array -> if (index in target.elements.indices) target.elements[index] else UDM.Scalar.nullValue()
    else -> UDM.Scalar.nullValue()        // an object by any key: null
}
```

So every object subscript with a computed key silently produced null on the compiled path. Not an
exception, not a fallback — a wrong answer.

## It corrected a wrong diagnosis

37 fixtures had been filed as `COMPILED_INPUT_SHAPE`, with the explanation that COPY and COMPILED
parse input through `parseInputPublic`, which does not reproduce the full `transform()` pipeline, and
that this mattered for the schema-describing formats whose UDM carries synthetic `%types` and
`%namespace` keys.

**That was wrong.** The input UDM was fine. Those transformations address it as
`$input["%types"]` — a *string-keyed subscript* — which returned null, and the failure then surfaced
as "requires an object as first argument, but got null" one call later. The symptom pointed at
parsing; the cause was the subscript.

The lesson is the one the harness exists for: a plausible explanation that fits the error text is
not a diagnosis. Fixing the subscript moved 46 fixtures at once, which no amount of work on
`parseInputPublic` would have done.

## The second half: error parity

The interpreter **raises** on an out-of-range or negative index, and on indexing an object with a
number or an array with a string. The compiled path returned null for all of it.

Two strategies disagreeing about whether something is an error is as much a divergence as
disagreeing about a value — arguably worse, because a null propagates and surfaces somewhere else,
as it did here. The runtime now raises in each of those cases, with the interpreter's messages.
`edge-cases/array_index_bounds` agrees as a result.

## The fix

Index access hands the key over as a **UDM** and the runtime decides what it means: a number
indexes an array, a string addresses an object, and `@`/`^` prefixes reach attributes and metadata
exactly as the interpreter does. An integer *literal* keeps a fast path that skips the boxing.

## Effect

| | before | after |
|---|---|---|
| known divergences | 101 | **47** |
| `COMPILED_INPUT_SHAPE` | 37 | **eliminated** |
| `COPY_OUTPUT` | 49 | 39 |
| `RUNS_ONLY_ON_TEMPLATE` | 7 | 5 |
| `COMPILED_OUTPUT` | 0 | **0** |

46 of those came from EB07 and EB06 together, and `COMPILED_OUTPUT` has never had an entry: where
COMPILED runs to completion, it agrees with the interpreter on every one of the 361 fixtures.

## Tests

`EB07IndexAccessTest` (`:engine`) — a literal string key, a **computed** string key (the case that
was wrong), a sigil-prefixed key as the schema formats use, a missing key yielding null, an array by
literal and computed number, and four error-parity cases asserting that *both* strategies raise:
out-of-range, a computed out-of-range, an object indexed by a number, an array indexed by a string.

Revert-verified: making a string key fall back to index 0 fails 3 of `EB06ClosureCaptureTest`'s cases;
the dedicated tests above cover the rest.

## Related

- [EB06](EB06-compiled-lambdas-cannot-see-enclosing-bindings.md) — fixed first, and necessary to make
  this one visible.
- [EB03](EB03-compiled-comparison-coerces-types-so-strategies-disagree.md) — the other place the
  compiled path silently reinterpreted a value rather than failing.
- [conformance-across-execution-strategies](../architecture/conformance-across-execution-strategies.md)
