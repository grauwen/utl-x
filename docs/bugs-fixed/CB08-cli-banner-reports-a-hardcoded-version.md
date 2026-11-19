# CB08: the CLI banner reports a hardcoded version, not the build's

> **CB = CLI bug** (`modules/cli` — the `utlx` binary and the tooling shipped with it).

**Status:** **FIXED (2026-11-19)** — the banner now reads `Implementation-Version` from the jar
manifest, which already carried the right value.
**Priority:** Low in effect, higher in cost — it makes a field report ambiguous about which build
was tested, which is expensive to unpick after the fact.
**Component:** `:modules:cli` `Main.kt`
**Found:** 2026-11-19, in a field report that noted *"the CLI banner prints v1.4.0 although the jar
is cli-1.4.3.jar"*, alongside [B63](B63-document-null-is-not-equal-to-the-null-literal.md).

## Summary

```kotlin
// Main.kt:14
private const val VERSION = "1.4.0"
```

while `build.gradle.kts` declared `version = "1.4.4"`. The constant was last touched at 1.4.0 and
every release since has reported itself as 1.4.0 from:

```
utlx version              ->  UTL-X CLI v1.4.0
utlx --help               ->  UTL-X CLI v1.4.0 - Universal Transformation Language Extended
```

The `./utlx` wrapper is **not** implicated: it deliberately resolves the most recently built jar
(`ls -t "$LIB_DIR"/cli-*.jar | head -n 1`) precisely so a version bump cannot leave it running a
stale artefact. The jar being executed was the right one; only the string it printed was wrong.

## Why it matters more than it looks

A reporter running `cli-1.4.3.jar` and seeing `v1.4.0` cannot tell whether they built what they
thought they built. In the report that surfaced this, the same message also described a defect
whose first plausible explanation was "you are running an older jar" — so the banner cost a round
of diagnosis before the real bug (B63) could be isolated. A version string exists to answer that
question and this one could not.

## Root cause

The version was restated in Kotlin source instead of being derived from the build. `AGENTS.md`
names this class of thing directly — a document that claimed 456 tests in one place and 465 in
another while the real figure was 571 — and the same reasoning applies to a version constant: any
value transcribed by hand drifts from its source the first time someone forgets.

The information was never missing. `modules/cli/build.gradle.kts:66` already writes it into the
manifest:

```kotlin
manifest {
    attributes(
        …
        "Implementation-Version" to project.version
    )
}
```

## Fix

```kotlin
private val VERSION: String =
    Main::class.java.`package`?.implementationVersion ?: "dev"
```

`const val` becomes `val`, since the value is now read at class-initialisation time. The `"dev"`
fallback covers running from classes rather than a jar — tests and IDE runs, where there is no
manifest — and reads as a deliberate answer rather than a stale number.

This is the same mechanism the engine already uses for its own `/admin/info` version
(`AdminEndpoint.kt`, via `UtlxEngine::class.java.package?.implementationVersion`), so the two
components now report their versions the same way.

## Verification, and why it is not a unit test

```
$ ./gradlew :modules:cli:jar          # produces cli-1.4.4.jar
$ ./utlx version
UTL-X CLI v1.4.4
Universal Transformation Language Extended
```

**A unit test cannot protect this.** In a test run there is no jar, so
`implementationVersion` is `null` and only the `"dev"` fallback is reachable — a test would pin the
fallback rather than the behaviour, and would stay green if someone re-hardcoded the constant
tomorrow. (`EB19ReadinessAndVersionTest` has a test of this shape for the engine's version; it
asserts the reported value is not the old literal, which in a test run compares `"unknown"` against
`"1.0.1"` and therefore passes without exercising anything. Worth knowing before copying it.)

The only thing that catches the actual regression is running the artefact and reading what it
prints, so `modules/cli/build.gradle.kts` gains a `verifyVersionBanner` task wired to
`finalizedBy(jar)` — a check nobody runs is theatre:

```
$ ./gradlew :modules:cli:jar
  CB08 ok — banner reports 1.4.4
BUILD SUCCESSFUL
```

Confirmed load-bearing by re-introducing the defect:

```
$ # private val VERSION: String = "1.4.0"
$ ./gradlew :modules:cli:jar
> CB08: the jar's banner does not report 1.4.4.
    expected the output to contain: 1.4.4
    actual first line:              UTL-X CLI v1.4.0
BUILD FAILED
```

`:modules:core:test` 441 tests and `:modules:engine:test` 1029 tests both pass, 0 failures — the
change is in `:modules:cli` and touches no transformation behaviour.

## Note

Three places now derive the version from the build and none restate it: the jar manifest, this
banner, and the engine's `/admin/info`. `deploy/azure-marketplace/build-zips.sh` reads its version
off the image pin in `mainTemplate.json` for the same reason. Worth keeping that way — the one
remaining hand-maintained version surface is the container image tag in the Marketplace templates,
and `build-zips.sh` fails the build when those disagree.
