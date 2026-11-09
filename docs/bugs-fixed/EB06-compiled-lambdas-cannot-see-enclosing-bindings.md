# EB06: compiled lambdas cannot see enclosing bindings

> **EB = engine bug** (`modules/engine` — the execution strategies and the runtime they
> call), the family of EB01–EB05. This is **EB06**.

**Status:** **FIXED.**
**Priority:** High. It threw at **runtime** on the engine's default strategy, for an idiom that is
ordinary UTL-X — so a correct transformation failed in production and worked on the CLI.
**Component:** `:engine` — `ASTCompiler.compileIdentifier` / `compileLambda`, `RuntimeOps.makeLambda`.

> **One-line:** a `let` in `execute` is a real local slot and a lambda is a *static* method, so the
> group-then-look-up idiom compiled cleanly and then died in a non-null parameter check.

## The idiom that broke

```utlx
let byOrder = groupBy($input.lines, (l) -> l.orderId)
map($input.orders, (o) -> { id: o.id, lines: byOrder[o.id] ?? [] })
```

`byOrder` lives in a local slot of `execute`. The lambda becomes `static UDM lambda_0(...)`, which
cannot reach that slot, so `compileIdentifier` found no local of that name, fell through to the
named-input lookup, and got `null`:

```
Parameter specified as non-null is null:
    method RuntimeOps.getIndex, parameter target
```

Unlike a compilation failure — which `CompiledStrategy` catches and silently falls back from — this
one reached the caller. That is the difference between a *lost optimisation* and a *broken
transformation*.

## The fix

The lambda method takes the enclosing bindings as a third parameter:

```
static UDM lambda_N(List<UDM> args, Map<String, UDM> inputs, Map<String, UDM> captured)
```

Slots are then uniform — `0` args, `1` inputs, `2` captured, `3+` parameters — so
`compileIdentifier` resolves **local slot → captured environment → named input** with no knowledge
of which kind of method it is emitting into. This is the same strategy as
[EB05](EB05-lambda-bodies-cannot-reach-a-named-input.md): put the thing at a fixed slot in every
method shape rather than teaching the emitter to special-case.

Two details carry the design:

- **The environment is snapshotted where the lambda *value* is created**, not shared as a frame.
  That is what lets a lambda outlive the expression that made it, and it avoids a mutable frame
  being read after it has moved on.
- **The capturable set accumulates through nesting** — a lambda can capture its enclosing method's
  locals *plus* whatever that method had itself captured — so arbitrary nesting works without the
  compiler tracking depth. A name not found as a local is read from the enclosing captured map.

## What it did not fix

Three of the four fixtures still diverged afterwards, and the remaining cause turned out to be a
separate and much larger defect: **index access with a string key**
([EB07](EB07-compiled-index-access-ignores-string-keys.md)). `byOrder[o.id]` was returning null for a
reason that had nothing to do with capture. Fixing capture was necessary to see it.

The fourth, `generate_typescript_interface`, is a genuine missing capability: a user-defined
function is not visible to the compiled path, because `compile()` compiles `program.body` and
nothing else. Now classified as `COMPILED_UNDEFINED_FUNCTION`.

## Tests

`EB06ClosureCaptureTest` (`:engine`), built as a progression so a failure localises itself rather
than just saying "wrong": a captured scalar, a program-level `let`, the groupBy-index idiom, the
same binding read twice in one lambda, a captured lookup feeding another call, two levels of
nesting, an inner lambda reading the *outer lambda's* own `let`, a parameter shadowing an enclosing
binding of the same name, an enclosing binding not shadowing a named input, and both reachable at
once.

Every case asserts `compile(...) != null` before comparing against the interpreter — the symptom was
a runtime failure on cleanly-compiled bytecode, so an output-only check would be satisfied by the
fallback and prove nothing.

Revert-verified: removing the captured set fails 7 of the 10.

## Related

- [EB07](EB07-compiled-index-access-ignores-string-keys.md) — what this fix exposed, and the larger
  defect of the two.
- [EB05](EB05-lambda-bodies-cannot-reach-a-named-input.md) — the same shape one level out: the inputs
  map was unreachable for the same reason.
- [EB04](EB04-nested-lambdas-emit-duplicate-method-names.md) — the third defect in `compileLambda`.
- [conformance-across-execution-strategies](../architecture/conformance-across-execution-strategies.md)
