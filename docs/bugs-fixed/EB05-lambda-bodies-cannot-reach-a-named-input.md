# EB05: a lambda body cannot reach a named input, so the compiled class dies at runtime

> **EB = engine bug** (`modules/engine` — the execution strategies and the runtime they
> call), the family of EB01–EB04. This is **EB05**.

**Status:** **FIXED.**
**Priority:** Medium-high. Unlike [EB04](EB04-nested-lambdas-emit-duplicate-method-names.md) this one
*throws* — the class loads, the method runs, and it fails part-way through. It is caught and the
strategy falls back, so the transformation still returns the right answer, but the failure happens
after work has started rather than at compile time.
**Component:** `:engine` — `ASTCompiler.compileIdentifier` / `compileLambda`, and
`RuntimeOps.makeLambda`.

> **One-line:** `compileIdentifier` loads the named-input map from local slot 1, which is the map in
> `execute` and the first *lambda parameter* in a lambda — so a lambda that referenced an input
> called `Map.get` on a `UDM`.

## Root cause

A name that is not a local is assumed to be a named input:

```kotlin
} else {
    // Input variable — look up in inputs map (arg 1)
    ctx.mv.visitVarInsn(ALOAD, 1)          // ← hardcoded
    ctx.mv.visitLdcInsn(name)
    ctx.mv.visitMethodInsn(INVOKEINTERFACE, MAP_TYPE, "get", …)
}
```

Slot 1 is right in the generated entry point, `execute(Map<String, UDM>)`: slot 0 is `this`, slot 1
is the map. But a lambda becomes a **static** method, `lambda_N(List<UDM> args)` — slot 0 is the
args list and slot 1 was the **first lambda parameter**, a `UDM`. So the emitted code loaded a UDM
and invoked a `java.util.Map` method on it:

```
java.lang.IncompatibleClassChangeError: Class com.glomidco.utlx.core.udm.UDM$Object
    does not implement the requested interface java.util.Map
        at utlx.compiled.Transform_1.lambda_0(Unknown Source)
```

The triggering idiom is ordinary — a callback that reaches back to a second input, which is how a
lookup join is written:

```utlx
map($input.orders, (order) -> {
  let customer = lookupBy(order.customerId, $input.customers, (c) -> c.id),
  customerName: customer?.name ?? "Unknown"
})
```

## The fix, and why this shape

The lambda method now takes the map as a second parameter:

```
static UDM lambda_N(List<UDM> args, Map<String, UDM> inputs)
```

which puts the inputs at **slot 1 in both method shapes**. `compileIdentifier` therefore needs no
knowledge of which kind of method it is emitting into, and `ALOAD 1` is uniformly correct —
including inside a lambda created *within* another lambda, where the enclosing frame is itself a
lambda method. Lambda parameters moved to slot 2 and up.

Two alternatives were rejected:

- **A static field on the generated class.** Simpler to emit, and wrong: the compiled class is
  cached and reused, so concurrent executions of the same transformation would overwrite each
  other's inputs. A per-invocation parameter cannot race.
- **Teaching `compileIdentifier` which method it is in.** That spreads the knowledge rather than
  removing the need for it, and would have to be revisited for every future method shape.

`RuntimeOps.makeLambda` gained the map and captures it per construction, so the closure holds it for
exactly as long as the lambda value lives.

## What the fix exposed underneath

As with EB04, fixing the outer failure revealed what it was masking. Of the seven affected fixtures
the `Map` crash is gone from all seven, but only **one** then agreed across strategies:

| fixture | now |
|---|---|
| `stdlib/array/lookupBy_basic` | agrees — delisted |
| `formats/jsch/.../generate_api_documentation` | COPY output differs |
| `formats/jsch/.../generate_validation_rules` | COPY output differs |
| `formats/jsch/.../generate_typescript_interface` | **unresolved name** |
| `stdlib/array/groupBy_lookup_basic` | **unresolved name** |
| `stdlib/array/groupBy_lookup_missing_key` | **unresolved name** |
| `stdlib/array/groupBy_lookup_with_transform` | **unresolved name** |

Corpus divergences: **98 → 97**, and `ASTCOMPILER_BAD_INVOKEINTERFACE` is gone as a category.

## The remaining defect this isolated — not yet fixed

Four fixtures share one root cause, now classified as `COMPILED_UNRESOLVED_NAME`: **the compiled
path cannot resolve a name declared outside the expression it is compiling** — a user-defined
function, or a `let` bound in an enclosing scope. Because `compileIdentifier` treats any unbound
name as a named input, the lookup returns `null` and the generated code then fails in a Kotlin
non-null check:

```
Parameter specified as non-null is null: method RuntimeOps.getIndex, parameter target
Undefined function: jsonTypeToTS
```

This is a **missing capability**, not bad addressing, and it should not be fixed by widening name
resolution first. The right first move is for `isCompilable` to **reject** an expression with a free
name it cannot resolve, so the strategy falls back cleanly instead of emitting code that throws. That
trades a runtime failure for a lower compiled-coverage count — which is the honest trade, and the
count is now measured, so the cost would be visible rather than guessed at.

## Tests

`EB05LambdaInputAccessTest` (`:engine`) asserts `compile(...) != null` and then compares against the
interpreter — the symptom was a runtime failure on bytecode that compiled cleanly, so checking the
output alone would be satisfied by the interpreter fallback and prove nothing. Cases: a lambda
reading a scalar input, reading a collection input (the lookup-join idiom), a parameter and an input
both reachable and distinct (the direct slot-collision regression), a nested lambda reading an input,
a two-parameter lambda (parameters now start at slot 2, so an inconsistent shift fails here), and a
`let` inside a lambda still resolving as a local.

Revert-verified: putting lambda parameters back at slot 1 fails 4 of the 6.

Full suite after the ABI change: **4437 tests, 0 failed** (`:engine` 845).

## Related

- [EB04](EB04-nested-lambdas-emit-duplicate-method-names.md) — the other `ASTCompiler` defect found by
  the same harness, in the same `compileLambda`. Both were invisible because compilation failure
  falls back silently.
- conformance-across-execution-strategies *(design notes — development branch)*
  — the harness, and the inventory this moved.
- [B34](B34-logical-operators-do-not-short-circuit.md) — origin of the discipline of asserting that
  something genuinely compiled.
