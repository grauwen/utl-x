# CB10: a positional input file with `-e` is read as a script

**Status:** **FIXED (2026-11-23)** — with `-e`, a lone non-`.utlx` positional is read as input.
**Priority:** Low-medium — a one-line workaround exists (`-i`), but the error misdirects.
**Component:** `modules/cli` — `TransformCommand` argument parsing
**Found:** 2026-11-23, while testing `--format table`.

## Reproduction

```
$ utlx transform -e '$input' rows.json
Error: Cannot use -e/--expression with a script file. Use one or the other.
```

There is no script file. `rows.json` can only be input, because `-e` *is* the script — but the
positional slot is interpreted as a script regardless, and the error then describes a conflict the
user did not create.

`-i` works:

```
$ utlx transform -e '$input' -i rows.json
```

## Cause

The positional argument is classified before `-e` is taken into account: the first non-flag
argument becomes the script, the second the input. With `-e` present the first positional can only
be the input, and nothing uses that.

## Fix

When `-e` / `--expression` is given, treat the first positional as the **input** file rather than a
script. The CLI's own help already documents the intended shape —
`utlx transform -e '<expression>' [input-file] [options]` — so the parser is out of step with the
documentation, which is the clearest sign of which side is wrong.

Keep the existing error for the genuine conflict: `-e` **plus** a `.utlx` script. A simple
discriminator is available — the help's own examples never pass a script with `-e`.

## Fix as applied

The decision cannot be made inside the argument loop — flag order is not fixed, so when the first
positional is seen the loop may not yet know `-e` is coming. It is therefore made **after** the
loop, in the expression branch: a single positional that is not a `.utlx` becomes the input.

Three outcomes, each with its own message:

| arguments | result |
|---|---|
| `-e 'x' data.json` | `data.json` is the **input** |
| `-e 'x' script.utlx` | error — the real conflict |
| `-e 'x' a.json b.json` | error — *"only one positional file is allowed (the input)"* |

The third message was added deliberately: calling `a.json` "a script file" would have repeated the
very habit this fix is about.

## Verification

`modules/cli/src/test/kotlin/.../CB09CB10InlineExpressionTest.kt` — 7 tests shared with CB09:

- `with -e a single positional file is the input` — the documented usage line, now true;
- `with -e a utlx positional is still the real conflict`;
- `with -e two positionals are refused, and neither is called a script` — asserts the message does
  **not** contain "with a script file";
- `without -e the first positional is still the script` — the regression that matters.
