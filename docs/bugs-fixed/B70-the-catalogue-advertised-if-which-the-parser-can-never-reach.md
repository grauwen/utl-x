# B70: the catalogue advertised `if`, which the parser can never reach

> **B = core/formats/stdlib** — the cause is `:stdlib`'s registration list, with `modules/core`'s
> lexer and parser owning the name. No CLI code is involved at all, which makes the original CB13
> filing especially plain; see B68's header. Found while answering a question about
> [B68](B68-working-functions-are-absent-from-the-catalogue.md).

**Status:** Fixed — 2026-11-24. The conditional is now callable as `conditional`.
**Priority:** Medium — the function was never reachable, so nothing computed a wrong answer. It
rates Medium because of *how* it survived: it passed a test written specifically to catch this
class of bug.
**Component:** `stdlib` — `Functions.kt` registration list; `modules/core` lexer/parser own the name
**Found:** 2026-11-24, by the user asking "`if` as registered function — is that correct? I was
under the impression we have an if-then-else implemented."

## Symptom

```
$ utlx functions info if
if - Inline if-then-else conditional          ← documented

$ utlx -e 'if(true, 1, 2)'
Error: Parse errors:
  Expected ')' after condition at Location(line=5, column=8)
```

The conditional **is** implemented — as syntax, and it works:

```
$ utlx -e 'if (true) 1 else 2'
1
```

## Cause

`if` is a lexer keyword (`modules/core/.../lexer/token_types.kt`: `"if" to TokenType.IF`), and the
parser's if-expression rule claims the token before any call can be parsed — hence the error is
about a *condition*, not about a function. `register("if", CoreFunctions::ifThenElse)` therefore put
`if` in the live function map where it could never be dispatched to. It had been dead for its whole
life.

A keyword is not automatically unusable as a function name, which is what makes this subtle:
`map`, `filter` and `reduce` are keywords too and parse perfectly well as calls — `map($input.items,
i => i.p)` is in every demo. Only `if` has a competing *expression* rule. So the defect cannot be
found by comparing names against the keyword list; only the parser can answer it.

## Why the B68 test did not catch it

B68 added `every catalogued function is callable`, and it passed. The test defined "callable" as
**present in `StandardLibrary.getFunctionNames()`** — that is, *registered*. `if` is registered.

That is the same methodology error B68 records twice already, in a third form:

| round | authority I used | authority that was right |
|-------|------------------|--------------------------|
| first count (51) | `register(` lines in source | the running program |
| second count (14) | source minus `//` comments | the running program |
| **this one** | the live function map | **the parser** |

Registration makes a name *dispatchable*; it does not make it *reachable*. Between them sits a
lexer and a parser, and they get the last word. Worse, the B68 fix made the entry more convincing:
before, the catalogue advertised `ifThenElse` (also uncallable, but obviously an internal name);
after, it advertised `if`, which looks exactly like something you should be able to call.

## Fix

The registration is removed, with the reason recorded where someone would otherwise re-add it:

```kotlin
// `if` is NOT registerable: it is a lexer keyword (token_types.kt) and the parser's
// if-expression rule claims it, so `if(c, a, b)` fails with "Expected ')' after
// condition" — the conditional is syntax (`if (c) a else b`), which is the
// implementation. …
```

The capability is kept, under a name the parser can reach. The first pass simply deleted the
registration and its annotation; that was the wrong trade — it threw away a working function to
fix a naming problem. It is now:

```kotlin
register("conditional", CoreFunctions::conditional)
```

`conditional` is the exact term for the construct, avoids the word `if` entirely (which would
re-raise the association with the keyword), and does not imply the many-branch behaviour that
`choose` or `switch` would. The Kotlin method was renamed to match, so there is no name mapping at
all — the point [F34](../features/F34-the-registration-should-be-the-only-place-a-function-name-is-written.md)
argues for.

The old metadata was wrong independently of the name and was rewritten: it declared
`minArgs = 1, maxArgs = 1` for a function that calls `requireArgs(args, 3, …)`, and its one
documented parameter was a copy-paste, `"array: Input array to process"`.

### It is not a drop-in for the syntax

Worth stating in the docs because it is a trap: the branches are **arguments**, so both are
evaluated before the call. Measured:

| with `d = 0` | result |
|---|---|
| `conditional($input.d != 0, 1 / $input.d, -1)` | **Error: Division by zero** |
| `if ($input.d != 0) 1 / $input.d else -1` | `-1` |

So the syntax short-circuits and the function cannot. The annotation's `notes` say so, and point
readers at the syntax whenever a branch must not run.

## Verification

A fourth invariant in `CoreFunctionsAreDiscoverableTest`, which asks the parser instead of the
registry:

```kotlin
val unparseable = catalogue.distinct().filterNot { name ->
    val src = "%utlx 1.0\ninput json\noutput json\n---\n$name(1)"
    runCatching { Parser(Lexer(src).tokenize(), src).parse() is ParseResult.Success }
        .getOrDefault(false)
}
```

Mutation-proven: adding `"if"` back to the compared set fails with

```
the catalogue advertises these 1 names, but a script calling one does not parse — the name
collides with language syntax, so it can never be called no matter what it is registered as: [if]
```

and `map`, `filter`, `reduce` pass, so the check discriminates the real defect from a keyword
coincidence. All 732 other catalogue entries parse.
