# CB14: a release version written down in eleven places, and three earlier fixes that did not stop it

> **CB = `modules/cli`** for the wrapper scripts and the REPL banner, which is where most of this
> lives. In `modules/engine` the same defect is
> [EB23](EB23-utlxe-version-reports-a-build-two-releases-old.md). For `modules/daemon` and the
> Theia extension it is **IB09**, which lives in `devdocs/` because the daemon and IDE do not ship
> on `main` — deliberately named rather than linked, since a link from `docs/` into `devdocs/`
> is broken on `main`.
>
> Numbers CB11–CB13 are **not** reused: they were filed, found to be misprefixed, and renumbered
> to [B68](B68-working-functions-are-absent-from-the-catalogue.md)–[B70](B70-the-catalogue-advertised-if-which-the-parser-can-never-reach.md).
> Skipping them keeps any stale reference from resolving to the wrong document.

**Status:** Fixed — 2026-11-26, with a check so there is no fourth time.
**Priority:** Medium. Nothing computed a wrong answer, but a launcher that silently runs the
*previous* release is a trap during a release, and a version banner that lies costs real debugging
time — it already did, twice in one session.
**Component:** `modules/cli` — `build.gradle.kts` (`createScripts`), `commands/ReplCommand.kt`
**Found:** 2026-11-26, by writing `scripts/check-no-pinned-versions.py` after noticing the same
defect for the third time.

## Symptom

`build.gradle.kts` declares `version = "1.4.4"`. Eleven other places restated it, and disagreed:

```
$ utlx repl
UTL-X REPL v1.0.0          ← running a 1.4.4 build

$ curl localhost:7779/api/health
{"status":"ok","version":"1.4.0", …}     ← serving 1.4.4 code

$ cat modules/cli/scripts/utlx
JAR="$SCRIPT_DIR/../build/libs/cli-1.4.4.jar"     ← will run cli-1.4.4 forever
```

The launcher pin is the one that bites hardest, and only at the worst moment. Cut 1.4.5, and
Gradle writes `cli-1.4.5.jar` while the script keeps running the `cli-1.4.4.jar` still sitting in
`build/libs` — no error, no warning, yesterday's behaviour.

## Cause

Two mechanisms, and the first is the interesting one.

**1. Generated output carried the version.** The scripts are not hand-maintained — they are written
by a Gradle task, `createScripts`, which interpolated the version into its own output:

```kotlin
JAR="${'$'}SCRIPT_DIR/../build/libs/cli-${project.version}.jar"
```

and is wired to run on every build (`tasks.jar { … finalizedBy("createScripts") }`). So the
committed scripts were generated artefacts, pinned to whichever release was current when the task
last ran. This also made the defect *self-healing in the wrong direction*: restoring the scripts by
hand worked, and the next `./gradlew build` silently pinned them again.

> That mechanism was missed on the first pass. Searching the build files for `scripts/utlx` and
> `startScripts` found nothing, which led to the confident and wrong conclusion that *"nothing in
> Gradle generates them — they were hand-edited and staged"*. The path is built with
> `File(projectDir, "scripts")`, so no literal to grep for. Reading the task, rather than grepping
> for the filename, was what found it.

**2. Version literals restated in source.** `private const val VERSION = "1.0.0"` in
`ReplCommand.kt`, plus five more in the daemon and engine (IB09, EB23). Each was correct when typed
and wrong from the next release onward.

## This is the third fix of the same defect

| | where | fixed |
|---|---|---|
| [EB19](EB19-admin-info-reports-a-hardcoded-version.md) | `/admin/info` answered the literal `"1.0.1"` | reads the jar manifest |
| [CB08](CB08-cli-banner-reports-a-hardcoded-version.md) | the CLI banner said 1.4.0 on a `cli-1.4.3.jar` | reads the jar manifest |
| **CB14** | the REPL banner, four launcher scripts, five more literals | reads the jar manifest |

Each earlier fix was correct, and neither prevented the next, because **nothing checked**. CB08 even
added a `verifyVersionBanner` task — for the CLI's main banner only, so the REPL banner three files
away kept saying `1.0.0`.

The information was never missing. Every jar manifest already carries `Implementation-Version`,
stamped from `project.version` by each module's `build.gradle.kts`. The rule is simply that nobody
restates it.

## Fix

**The generators no longer emit a version.** `createScripts` in both `modules/cli` and
`modules/daemon` now writes a script that resolves the newest jar:

```bash
LIB_DIR="$SCRIPT_DIR/../build/libs"
JAR="$(ls -t "$LIB_DIR"/cli-*.jar 2>/dev/null | head -n 1)"
```

with the Windows equivalent using `dir /b /o-d`. Because the generator is the source, the committed
scripts stay correct across every future bump instead of being re-pinned by the next build.

**Version literals read the manifest**, following CB08:

```kotlin
private val VERSION: String =
    ReplCommand::class.java.`package`?.implementationVersion ?: "dev"
```

The `"dev"` fallback covers running from classes rather than a jar (tests, IDE), where there is no
manifest.

## Verification — the part that makes it stick

`scripts/check-no-pinned-versions.py`, in the style of the existing `check-docs-boundary.py`:

- fails on any `= "x.y.z"` or `to "x.y.z"` in `modules/*/src/main/kotlin`, skipping comments, which
  may legitimately cite a release;
- fails on any `cli-x.y.z.jar` / `utlxd-…` / `utlxe-…` named inside `modules/*/scripts`.

It is deliberately shaped to catch the *class* rather than the current number — searching for
`1.4.x` would have missed `ReplCommand`'s `1.0.0`, which is exactly how that one survived two
earlier fixes. On the eleven original sites it reports:

```
FAIL  5 place(s) restate a release version
  …/ReplCommand.kt:40: restates a version — read it from the jar manifest
  …/scripts/utlx:3: pins a jar by version — resolve the newest instead
  …
```

and after the fix:

```
ok    no module source or launcher script restates a release version
```

Worth wiring into CI next to the docs-boundary workflow; until then it is a one-line check before a
release, and the release checklist is the right place for it.
