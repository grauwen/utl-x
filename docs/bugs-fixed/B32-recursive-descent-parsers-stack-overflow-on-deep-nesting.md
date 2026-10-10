# B32: recursive-descent parsers stack-overflow on deeply-nested input (uncaught `StackOverflowError` — a DoS, not a clean reject)

**Status:** **OPEN** — found 2026-10-10 by the `test-corpora` security/bounds baseline (Phase 0b).
**Priority:** **High** — a **crash / denial-of-service** on crafted untrusted input; violates the
`bounds.yaml` rule *"terminate early with a structured error — never crash, hang, or OOM."* Worse than
a silent-accept bug (B29/B30): the process dies.
**Created:** October 2026
**Component:** the hand-written **recursive-descent** readers — demonstrated on
`formats/json/src/main/kotlin/com/glomidco/utlx/formats/json/json_parser.kt` (`parseValue` →
`parseArray`/`parseObject` → `parseValue` …, **no depth counter**). The same recursion pattern (and so
almost certainly the same defect) is in the XML and YAML readers.

> **One-line:** a deeply-nested but well-formed document (`[[[ … ]]]`) recurses once per level with **no
> depth limit**, overflowing the JVM stack → **uncaught `java.lang.StackOverflowError`**, crashing the
> process, instead of being rejected cleanly at a bounded depth.

## Problem

The readers recurse per nesting level and never check a depth bound. Deep input therefore exhausts the
call stack. Because `StackOverflowError` is a `java.lang.Error` (not an `Exception`), the CLI's
top-level `catch (e: Exception)` does **not** catch it — it propagates as an uncaught
`Exception in thread "main" java.lang.StackOverflowError` and the process exits abnormally.

`expectations/bounds.yaml` already specifies the intended limits — `max_depth` 64 (strict) / 256
(lenient) — and the doctrine (§5.7) is "exceeding a bound must terminate early with a **structured
error**, never crash." Neither the bound nor the clean-termination holds today.

## Reproduction

Generated case `test-corpora/corpora/security/json/deep_nesting_50000.json`
(`'['×50000 + '1' + ']'×50000`, via `scripts/make-bounds-seeds.sh`):

```
python3 test-corpora/scripts/harness.py --profile strict -v
#   corpora/security/json/deep_nesting_50000.json
#       expected=reject  actual=error
#       !! crash: Exception in thread "main" java.lang.StackOverflowError
```

All three profiles record it as a **robustness `error`** (`errors=1` each) in `test-corpora/baseline.json`.
The moderate case `deep_nesting_100.json` (depth 100) is simply **accepted** — confirming there is **no
depth bound at all** (strict wants it rejected at 64).

> The harness was improved alongside this finding to classify `StackOverflowError`/`OutOfMemoryError`/
> uncaught top-level exceptions as `error`, not `reject` — otherwise a crash is masked as a desirable
> rejection (non-zero exit). This bug was initially hidden by that; the detection now surfaces it.

## Impact

- **DoS:** a small crafted input (100 KB here) crashes the parser process. On a **guard** this is both a
  denial-of-service and a **fail-open risk** (a crash mid-processing is not a controlled `REJECT`).
- Applies to any recursive-descent reader — JSON confirmed; XML/YAML almost certainly share it.

## Fix

- Thread a **depth counter** through the recursive descent in every reader; on exceeding the profile's
  `max_depth` (`bounds.yaml`: strict 64 / lenient 256), **throw a structured, caught parse error**
  (clean `REJECT`) — do **not** recurse further.
- The bound is the fix; catching `StackOverflowError` would be a band-aid (and unreliable).
- Add `deep_nesting_100` + `deep_nesting_50000` as regressions (clean reject, **no crash**).

**Bounds-config gap (do alongside):** `expectations/bounds.yaml` defines only `strict` and `lenient`
profiles — there is **no `standard` row**. Bounds are the always-on axis for *every* profile; add a
`standard` section (wider than strict, still finite) so deep/abusive input is bounded under `standard` too.

## Related
- `test-corpora/expectations/bounds.yaml` (`max_depth`); `docs/architecture/parser-strictness-profiles.md`
  §4 (resource-bounds row) + §11.5 (bounds as a DoS axis, separate from the leniency dial).
- `docs/architecture/utlx-test-corpora.md` §5.7 (bounds always-on; "never crash, hang, or OOM").
- `test-corpora/baseline.json`; `test-corpora/scripts/make-bounds-seeds.sh`.
- **B29/B30/B31** — the parser-hardening theme; B32 is the first **crash** (vs silent-accept) in it.
