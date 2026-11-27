# UTL-X 1.4.5 — Release Notes

**DRAFT — not yet released.** Written as the work landed so the behaviour changes are not
reconstructed from commit messages later. Verify the counts below against a real run before
publishing.

**A stdlib and tooling release.** Where [1.4.2](release-notes-1.4.2.md) fixed what the first real
deployment exposed, this fixes what *measuring the function catalogue* exposed: a library that
could not describe itself accurately. Of 724 callable names, 36 worked but were undocumented, 11
were documented but uncallable, 9 were documented twice, and 1 was documented but unparseable.

> **Why 1.4.5 and not 1.5.0.** Two of the changes below are breaking, which semver would make a
> minor bump. The version line is capped at 1.4.x while `main` is on 1.3.x — see
> *Version ceiling* in [the release plan](version-release-plan.md). So the number does not warn
> you; this section does.

## Two behaviour changes to read before upgrading

### `findIndex` and `findLastIndex` answer `null`, not `-1`

[B73](../bugs-fixed/B73-the-interpreter-reimplements-seven-stdlib-functions-and-findindex-disagrees.md)

```utlx
findIndex([1, 2, 3], x => x > 100)      // was -1 on the CLI, null in the engine; now null in both
findIndex([1, 2, 3], x => x > 100) ?? -1  // the old value, if you want it
```

The two execution strategies **disagreed**: the CLI's interpreter answered `-1` while the engine's
compiled path answered `null`, for the same script. So a transformation verified at the prompt could
behave differently in production. `null` is the house convention — `indexOf`, `lastIndexOf` and
`find` all answer `null` — and it composes: `?? -1` recovers the old value, whereas recovering
`null` from `-1` needs a comparison and searches twice.

**If you test for "not found":** `findIndex(…) >= 0` and `== -1` need to become `!= null`, or add
`?? -1` to keep the old shape. Nothing in this repository relied on `-1`.

### `concat` is strings only

[B71](../bugs-fixed/B71-concat-was-four-operations-behind-one-name.md)

```utlx
concat("ORD-", 42)        // "ORD-42"   unchanged — 68 of 70 call sites looked like this
concat(42, 58)            // "4258"     was 100
concat([1,2], [3,4])      // error, naming arrayConcat
```

One function was performing four unrelated operations, dispatching on its first argument's type —
including **adding numbers**, so `concat(42, 58)` returned `100`. In XPath and XSLT, which
`docs/comparison/vs-xslt.md` maps `concat()` straight onto, the answer is `"4258"`.

The other three operations already had names, and the errors now name them:

| was | now |
|---|---|
| `concat([1,2], [3,4])` | `arrayConcat([1,2], [3,4])` — new, named for the existing `binaryConcat` |
| `concat({"a":1}, {"b":2})` | `merge(…)` |
| `concat(42, 58)` | `sum(…)` if you wanted arithmetic |

## New functions

| name | what it does |
|---|---|
| `arrayConcat(a, b, …)` | joins arrays, keeping duplicates — unlike `union`, which de-duplicates |
| `parseIntOr(text, fallback)` | parses, returning the fallback instead of throwing |
| `parseDateOr(text, fallback, pattern?)` | the same for dates, yielding a typed date |
| `conditional(cond, then, else)` | the function form of the conditional |

`parseIntOr`/`parseDateOr` fill a real gap: `parseInt` throws, and a thrown error cannot be caught
in UTL-X — `parseInt(x) ?? 0` and `coalesce(parseInt(x), 0)` both fail — so for a column that is
mostly numeric with the occasional `"N/A"`, the fallback has to be part of the call.

`conditional` replaces a registration of `if` that was **never reachable**: `if` is a keyword the
parser claims, so `if(c, a, b)` had always been a parse error
([B70](../bugs-fixed/B70-the-catalogue-advertised-if-which-the-parser-can-never-reach.md)). Note it
evaluates both branches, since they are arguments — use the `if (c) a else b` syntax when a branch
must not run.

## The catalogue now describes the library

[B68](../bugs-fixed/B68-working-functions-are-absent-from-the-catalogue.md) ·
[B69](../bugs-fixed/B69-one-name-several-signatures-in-the-catalogue.md) ·
[B72](../bugs-fixed/B72-contains-searched-for-empty-text-when-given-null.md)

`utlx functions info <name>` is generated from `@UTLXFunction` annotations, while `register(…)` is
what makes a name callable. Nothing compared the two lists, and they had drifted both ways. Now:
every callable name is listed, every listed name is callable *and* parses, and no name is listed
twice — four invariants, each mutation-proven, so the catalogue is a control rather than a
document.

QName functions are also spelled consistently: `createQName`, `resolveQName`, `matchesQName`
(TIBCO BusinessWorks' spelling, and what the reference already documented). The old
lower-case-n spellings remain callable as deprecated aliases.

## Tooling

[CB14](../bugs-fixed/CB14-a-release-version-written-down-in-eleven-places.md) ·
[EB23](../bugs-fixed/EB23-utlxe-version-reports-a-build-two-releases-old.md)

A release version was written down in eleven places besides `build.gradle.kts`, and they disagreed:
the REPL said `v1.0.0`, `utlxe --version` said 1.4.0 on a 1.4.4 jar, `/api/health` said 1.4.0 while
serving 1.4.4, and the four launcher scripts were pinned to `cli-1.4.4.jar` — so cutting this
release would have left them running the *previous* build, silently.

All of them now read `Implementation-Version` from the jar manifest, which every module already
stamped. `scripts/check-no-pinned-versions.py` fails the build on a reintroduction; this defect had
already been fixed twice (EB19, CB08) without anything stopping the third.

## Upgrade notes

- `%utlx 1.0` scripts are unaffected unless they test `findIndex`/`findLastIndex` for `-1`, or call
  `concat` on arrays, objects or numbers.
- **Run `scripts/check-no-pinned-versions.py` before tagging**, or the launcher scripts will need
  regenerating after the bump.
