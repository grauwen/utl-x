# Bug reports — how they are numbered

Every report in this folder is `<PREFIX><NUMBER>-<slug>.md`. The **prefix says which component the
defect was in**, not who found it or how it was reported. Getting that wrong is easy — nine reports
were misfiled as `B` in one sitting before anyone noticed — so the rule is written down here.

## Prefixes

| prefix | component | example |
|---|---|---|
| **B** | the shared language stack: `:core`, `formats/*`, `:stdlib` | [B35](B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md) — the numeric model |
| **EB** | `modules/engine` — the execution strategies and the runtime they call | [EB07](EB07-compiled-index-access-ignores-string-keys.md) — `ASTCompiler` index access |
| **CB** | `modules/cli` — the `utlx` binary and the tooling shipped with it | [CB01](CB01-auto-capture-emits-unparseable-yaml.md) — the capture writer |
| **IB** | the IDE and the `utlxd` daemon | [IB02](IB02-ide-execute-hardcoded-input-name.md) |
| **VB** | `validate.*` semantic validation | [VB03](VB03-validate-oneOf-numeric-membership-fails.md) |

Features use the parallel set in [`../features`](../features): **F** (language), **EF** (engine),
**IF** (IDE).

**How to choose.** Look at the `Component:` line you are about to write. If it says
`modules/engine`, the prefix is `EB` — even when the symptom first appeared somewhere else, and even
when the fix turns out to be shared. A header that says "B = core bug" above a `Component:` of
`:engine` is the mistake to watch for.

## Choosing the next number

Take the highest number **already used anywhere** for that prefix and add one. "Used anywhere"
includes numbers whose files are not in your current branch — see the branch note below — and
numbers that were retired.

**Numbers are never reused.** A retired number keeps meaning what it meant in `git log`, in code
comments and in cross-references. Recycling it makes an ID mean two different things depending on
where you read it, which is worse than a gap.

### Retired

| number | what happened |
|---|---|
| **B36** | filed as a core bug, then reclassified as [EB03](EB03-compiled-comparison-coerces-types-so-strategies-disagree.md) once it was clear the defect was in `modules/engine`. Commit `a4b4ae85` still refers to it as B36. The number is left unused. |

## A gap is usually a branch, not a deletion

Prefixes and numbers were introduced over time, so a branch carries only what its history contains.
Measured counts:

| branch | B | EB | CB | IB | VB |
|---|---|---|---|---|---|
| `main` | 27 | 0 | 0 | 0 | 0 |
| `development` | 26 | 2 | 0 | 7 | 0 |
| this feature branch | 32 | 13 | 3 | 7 | 7 |

So **B02–B08 are missing from feature branches but present on `main`** — they were added there by
`ca735747` ("major reshuffle of doc files"), which is not an ancestor of the feature line. Nothing
was deleted. Likewise the `EB`, `CB` and `VB` prefixes are newer than `main`, so a reader standing
on `main` will not see them at all.

Before concluding that a number is free, check the other branches:

```bash
git ls-tree -r --name-only main -- docs/bugs-fixed/ | sed 's|.*/||' | grep -E '^B[0-9]+'
```

## What a report should contain

The existing reports vary, but the ones that stayed useful share a shape:

- **Status / Priority / Component** at the top, so the prefix can be checked against the component.
- **A one-line summary** of the defect, not of the fix.
- **Root cause** with the actual code, and **why it was not noticed** — a defect that hid behind a
  silent fallback or a passing test is worth saying so explicitly, because that is the part that
  recurs.
- **Behaviour changes callers see**, if any, and which fixtures or tests changed and why.
- **Tests**, including how the fix was **revert-verified**: break it again, name which tests fail. A
  revert that changes nothing observable proves nothing about the fix.
- **What is still open**, separated from what was fixed, so it is not mistaken for completeness.
