# EB17 — a captured binding is readable but not callable

**Status:** Fixed — 2026-11-12
**Component:** `:modules:engine` — `strategy/compiled/ASTCompiler.kt`
**Found:** 2026-11-12, by `ConformanceStrategyParityTest` on a fixture added for
[B52](B52-type-checker-reports-every-stdlib-function-as-an-undefined-variable.md)
**Severity:** High — ordinary UTL-X threw at runtime on the engine's **default** strategy
while working on the CLI

## Summary

Two defects in the compiled path, both reached by the same fixture.

**(1) A `let`-bound lambda invoked from inside another lambda threw.**

```utlx
{ let dbl = (x) -> x * 2, r: map($input.ns, (n) -> dbl(n)) }
```

| strategy | result |
|---|---|
| TEMPLATE (= the CLI's interpreter) | `{"r":[2,4,6]}` |
| COPY | **throws** `Undefined function: dbl` |
| COMPILED | **throws** `Undefined function: dbl` |

It compiled cleanly, so `CompiledStrategy` did not fall back — the exception reached the
caller.

**(2) A nested object's `let` overwrote an outer binding of the same name.**

```utlx
{ let dbl = (x) -> x * 2,
  shadowed: { let dbl = (x) -> x + 100, v: dbl(1) },
  stillOuter: dbl(1) }
```

| property | TEMPLATE | COPY / COMPILED |
|---|---|---|
| `shadowed.v` | 101 | 101 |
| `stillOuter` | **2** | **101** |

Shadowing worked *inside* the nested object; what leaked was the binding afterwards. Only a
property placed **after** the nested object can observe this, which is why nothing had.

## Root cause

**(1) Call position never learned what identifier position knows.** EB06 gave each lambda
method a third parameter carrying the enclosing frame's bindings, and taught
`compileIdentifier` to read a name from that map when it is not a local slot.
`compileFunctionCall` was left behind:

```kotlin
val localSlot = ctx.locals[funcName]
...
if (localSlot != null) {
    // local holds a UDM.Lambda → RuntimeOps.invokeLambda
} else {
    // → StdlibDispatch.call(funcName, args)
}
```

Inside a lambda, `dbl` is not a local — it is an entry in the captured map. So the `else`
branch emitted a stdlib dispatch for a name the stdlib has never heard of, and
`StdlibDispatch.call` threw at runtime.

Worth being precise about what was *not* wrong: an object-literal `let` does get a real local
slot (`compileObjectLiteral` calls `ctx.allocLocal`), and the captured map is built from
`ctx.locals.keys + ctx.capturedNames`, so the binding was present and readable the whole
time. Only the call site could not see it.

**(2) `ctx.locals` has no scope stack.** `allocLocal` writes `locals[name] = slot` directly,
and `compileObjectLiteral` never unwound it, so a nested object's binding replaced the outer
entry permanently. The slot *numbers* never collide — `localSlot` only increments — so
nothing was corrupted; the name simply resolved to the wrong slot from then on.

## The fix

**(1)** A captured branch in `compileFunctionCall`, mirroring `compileIdentifier`:

```kotlin
} else if (ctx.capturedSlot != null && funcName in ctx.capturedNames) {
    ctx.mv.visitVarInsn(ALOAD, ctx.capturedSlot)
    ctx.mv.visitLdcInsn(funcName)
    ctx.mv.visitMethodInsn(INVOKEINTERFACE, MAP_TYPE, "get", "(…)…", true)
    ctx.mv.visitTypeInsn(CHECKCAST, UDM_TYPE)
    argList()
    ctx.mv.visitMethodInsn(INVOKESTATIC, RUNTIME_OPS, "invokeLambda", "(…)…", false)
} else { /* stdlib dispatch */ }
```

Precedence is **locals, then captured, then stdlib**, matching the interpreter, where a `let`
shadows a stdlib function of the same name: `{ let upper = (x) -> "S", r: upper("a") }`
yields `"S"` on every strategy. Had the captured branch gone *after* the stdlib branch, that
script would have called the real `upper` and the paths would have disagreed silently —
which is the worse failure, so the ordering is pinned by a test.

**(2)** `compileObjectLiteral` now records what its own bindings shadow and restores it in a
`finally` (the segment loop has an early return for an empty object), delegating the body to
`compileObjectLiteralBody`:

```kotlin
val shadowed = mutableMapOf<String, Int?>()
for (binding in expr.letBindings) {
    compileExpression(ctx, binding.value)
    if (binding.name !in shadowed) shadowed[binding.name] = ctx.locals[binding.name]
    ctx.mv.visitVarInsn(ASTORE, ctx.allocLocal(binding.name))
}
try { compileObjectLiteralBody(ctx, expr) } finally {
    for ((name, previous) in shadowed) {
        if (previous == null) ctx.locals.remove(name) else ctx.locals[name] = previous
    }
}
```

A name with no previous binding is **removed**, not rewound — a binding introduced inside a
nested object must not survive it.

## How it was found, and why that matters

Not by probing. The B52 fix needed object-literal `let` scoping in the type checker, so a
conformance fixture was added for the **runtime** counterpart —
`language/let-bindings/b52_object_let_lambda_scope.yaml` — purely because
`ConformanceStrategyParityTest` auto-discovers the corpus and runs every fixture through all
three strategies for free. It failed on the first run.

This is the fifth bug from the interpreted/compiled gap (B34, B35, EB03, B48, EB17) and the
first found by a fixture rather than by someone suspecting the compiled path. Nothing in the
573-test corpus had ever called a `let`-bound lambda from inside another lambda.

### It also emptied a baseline class

`KNOWN_DIVERGENCES` carried three fixtures under `COMPILED_UNDEFINED_FUNCTION`
(`examples/intermediate/function_composition`,
`examples/intermediate/function_definitions_in_objects`,
`formats/jsch/transformation/generate_typescript_interface`), labelled:

> `compile()` compiles `program.body` and nothing else, so the call reports
> `Undefined function`. **A missing capability, not a coding error.**

That diagnosis was wrong, and the wrong label is why the three sat untouched: they described
a design limit nobody would attempt, when the actual cause was one missing `else if`. All
three started agreeing the moment EB17 was fixed, and the parity test's second direction — a
listed fixture that *stops* diverging also fails — is what forced them out of the list rather
than letting them rot in it. The enum value is kept, with the diagnosis corrected in place
and a note that anything landing there again needs a fresh one.

## Verification

| suite | before | after |
|---|---|---|
| `:modules:engine:test` | 999 | **1013**, 0 failures |
| `:modules:core:test` | 342 | 342, 0 failures |
| `:modules:cli:test` | 136 | 136, 0 failures |
| `:stdlib:test` | 2326 | 2326, 0 failures |
| conformance (JVM) | 573 | **574**, 100% |
| conformance (native) | — | **574**, 100% |
| parity baseline | 3 × `COMPILED_UNDEFINED_FUNCTION` | **0** |

The native run is coverage for B51 rather than for EB17 — `ASTCompiler` generates bytecode at
runtime and so is not reachable from a native image at all, which is itself worth stating: a
native binary can never exercise COPY or COMPILED, so it is not a substitute for
`:modules:engine:test` under any circumstances.

`EB17CapturedCallPositionTest` (13 tests) covers both defects directly, asserting COMPILED
against the interpreter and — crucially — that the program **actually compiled**: both
`CopyStrategy` and `CompiledStrategy` fall back to the interpreter without complaint when
`ASTCompiler.compile` returns null, so a parity assertion on an uncompilable program measures
the interpreter twice and proves nothing.

Covered there: an object-literal and a script-level `let` lambda called inside `map`; an
outer object's lambda called from a nested object's lambda; a capture two lambdas deep; a
`let` shadowing a stdlib name through the capture; a plain stdlib call inside a lambda
(unaffected); a lambda parameter shadowing a captured binding; the leak into a later property
and into a later lambda; a fresh nested name not surviving its object; and the three cases
that already worked, pinned so the fix cannot regress them.
