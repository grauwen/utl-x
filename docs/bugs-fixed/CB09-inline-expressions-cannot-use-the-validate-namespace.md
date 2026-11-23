# CB09: an inline expression cannot use `validate.*`

> **CB = `modules/cli`** — the synthesized header in expression and identity mode. The namespace
> itself is [F21](../features/F21-utlx-1-1-semantic-validation.md); the gate is
> [EB15](EB15-version-gate-not-enforced-on-the-compiled-strategies.md)/[EB20](EB20-namespaced-calls-never-compile-and-the-gate-depended-on-it.md).

**Status:** **FIXED (2026-11-23)** — the synthesized header declares the resolved language mode.
**Priority:** High for adoption — it makes the whole of `%utlx 1.1` unreachable from a one-liner,
which is how people try a language feature first.
**Component:** `modules/cli` — `TransformCommand`, the two places that synthesize a script for
`-e` / identity mode
**Found:** 2026-11-23, writing a `validate.*` demo. Every step had to become a file on disk,
because no step could be a pipe.

## Reproduction

```
$ echo '{"qty":5}' | utlx -e '$input |> validate.required(["qty"])'
Error: validate.required requires %utlx 1.1 (script declares %utlx 1.0);
       the validate.* namespace is a 1.1 language feature.
```

The user declared nothing. And the obvious remedy does not work either:

```
$ echo '{"qty":5}' | utlx -e '$input |> validate.required(["qty"])' --language-mode 1.1
Error: validate.required requires %utlx 1.1 (script declares %utlx 1.0)
```

## Cause

Both synthesis sites hardcode the version:

```kotlin
fun wrap(body: String) = """%utlx 1.0
input auto
output $effectiveOutputFormat
---
$argBindings$body"""
```

So an expression always *declares* 1.0, and `LanguageNamespaces` correctly refuses `validate.*` for
a 1.0 script. `--language-mode` does not help because it is a **cap** on what the engine will
accept, not a declaration of what the script is — two different things, which the error message
unhelpfully conflates by saying "script declares %utlx 1.0" about a script the user never wrote.

## Why it matters more than it looks

`-e` is how a language feature gets tried. F27 exists precisely to make one-liners good
(`-r`, `--arg`, `--argjson`, sequence output), and the CLI's own help advertises jq-like use. The
entire 1.1 surface — the namespace the Guard is built on — is the one thing that cannot be reached
that way. Writing the `validate.*` demo, this forced every single step onto disk.

## Fix

Synthesize the header at the **resolved language mode** rather than a literal `1.0`, so the default
is the latest the build understands and `--language-mode 1.0` still produces a 1.0 expression (and
still refuses `validate.*`, correctly, with a message that is then true).

Two details to get right:

- the resolution of `--language-mode` currently happens *after* synthesis in the call order, so it
  has to be hoisted or recomputed;
- the error message should distinguish "this script declares 1.0" from "this engine is capped at
  1.0", because for an inline expression the first is never the user's doing.

## Fix as applied

The `--language-mode` resolution was **hoisted above** script synthesis — it previously ran after
it — and both synthesis sites now interpolate it:

```kotlin
fun wrap(body: String) = """%utlx $resolvedLanguageMode
```

One definition of the mapping, used twice; the later duplicate was removed. Note the error message
needed no change after all: with the mode resolved first, a `--language-mode 1.0` run genuinely
*does* synthesize a 1.0 script, so "script declares %utlx 1.0" became true rather than misleading.

```
$ echo '{"qty":5}' | utlx -e '$input |> validate.required(["qty"])'
{ "valid": true, "passed": 1, … }

$ … --language-mode 1.0
Error: validate.required requires %utlx 1.1 (script declares %utlx 1.0)
```

## Verification

`modules/cli/src/test/kotlin/.../CB09CB10InlineExpressionTest.kt` — 7 tests shared with CB10. The
boundary ones matter most:

- `an inline expression can call validate star` — the defect itself;
- `a 1_0 engine cap still refuses the 1_1 namespace` — the cap must keep working, or the fix would
  have traded a false negative for a false positive;
- `a plain 1_0 expression is unaffected` — no behaviour change for existing one-liners.
