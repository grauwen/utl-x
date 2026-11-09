# EB12: a compiled try/catch emits bytecode the verifier rejects

> **EB = engine bug** (`modules/engine` — the execution strategies and the runtime they
> call), the family of EB01–EB11. This is **EB12**.

**Status:** **FIXED** — by refusing to compile it where it cannot be compiled correctly. The real
fix is recorded below as a follow-up.
**Priority:** High. The generated class failed to load, so the transformation failed on the engine's
default strategy while working on the CLI.
**Component:** `:engine` — `ASTCompiler.isCompilable` / `compileTryCatch`.

> **One-line:** `try`/`catch` was compiled inline wherever it appeared, but at an exception handler
> the JVM discards the operand stack — so a try region entered with a half-built object on the
> stack produced a class the verifier threw out.

```
java.lang.VerifyError: Instruction type does not match stack map
  Location: utlx/compiled/Transform_1.execute(…) @72: aastore
  Current Frame:   stack: { UDM$Scalar }
  Stackmap Frame:  stack: { Object, [UDM, [UDM, integer, UDM }
```

## Root cause

At an exception handler the JVM clears the operand stack down to the exception object. So a try
region is only safe to enter when the stack holds nothing that the code after the handler still
needs.

`compileTryCatch` emitted the region inline, with no regard for what the enclosing expression had
already pushed. Compiling

```utlx
{ r: try { $input.v / $input.d } catch { -1 }, m: "x" }
```

enters the try while the object literal's key array, value array and index are on the stack. The
handler then rejoins with a different stack shape than the stackmap declares, and the class never
loads.

**A bare top-level `try { … } catch { … }` compiled and ran correctly** — the stack is empty there —
which is exactly why this survived: it was only wrong in the position people actually write it.

## The fix, and what it costs

`isCompilable` is now **positional**. It carries an `atMethodRoot` flag that is true only where the
operand stack is provably empty: the root of `execute`, and the root of each generated `lambda_N`
(a lambda is its own method, so the stack is empty again there). `try`/`catch` is compilable only at
such a root; anywhere else the whole transformation is handed to the interpreter.

That is a **refusal, not a repair**: it converts invalid bytecode into a correct interpreted run. The
cost is losing compilation for any transformation with a nested try/catch — which is most of them —
and it is the right trade only because the alternative was a class that does not load.

**The real fix** is to hoist a member value containing a try/catch into a local *before* the
enclosing container starts building its stack, then load it back. Evaluation order is unchanged
because member values are evaluated in order anyway, and it costs one local per hoisted member. It
was not done here because it touches every container emitter and this session had already changed
several, and a verifier error is a bad thing to be wrong about twice.

## Tests

`EB12TryCatchAndNullArithmeticTest` (`:engine`) asserts the *compilability decision* directly, not
just the output — `assertNull(compile(...))` for the nested positions and `assertNotNull` for the
two that must stay compilable. Output-only assertions would pass either way, because the fallback
produces the right answer.

Cases: a bare top-level try/catch still compiles; inside an object literal it is **not** compiled;
inside an array literal; as an operator operand; and at a lambda-body root, where it is still
compilable.

Revert-verified: making `try`/`catch` compilable anywhere again fails 3 of the tests.

## Related

- [EB13](EB13-compiled-arithmetic-turns-a-non-numeric-operand-into-zero.md) — found in the same two
  fixtures and hiding this one: the catch never fired, so the broken try never ran.
- [EB04](EB04-nested-lambdas-emit-duplicate-method-names.md) — the other `ASTCompiler` defect that
  produced an unloadable class, also invisible because the failure was swallowed.
