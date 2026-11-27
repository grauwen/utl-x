# EB23: `utlxe --version` reports a build two releases old

> **EB = `modules/engine`.** One line of engine source; the same defect in the CLI and daemon is
> [CB14](CB14-a-release-version-written-down-in-eleven-places.md), which carries the shared
> analysis and the check that now prevents recurrence.

**Status:** Fixed — 2026-11-26.
**Priority:** Low in isolation, Medium in context — the engine is what ships to customers, and its
version is the first thing asked for in a support exchange.
**Component:** `modules/engine` — `Main.kt`
**Found:** 2026-11-26, by `scripts/check-no-pinned-versions.py` (see CB14).

## Symptom

```
$ java -jar modules/engine/build/libs/utlxe-1.4.4.jar --version
utlxe v1.4.0
```

The jar is 1.4.4. The banner says 1.4.0.

## Cause

```kotlin
private const val VERSION = "1.4.0"
```

A file-level constant in `Main.kt`, correct when typed at 1.4.0 and wrong from 1.4.1 onward.

This is the **second** time the engine has had this defect: [EB19](EB19-admin-info-reports-a-hardcoded-version.md)
fixed `/admin/info`, which answered the literal `"1.0.1"` and showed `v1.0.1` in the Admin Web UI
while running a 1.4.1 image. That fix was correct and local, and left this one standing eleven
lines from the top of `Main.kt`.

## Fix

The pattern EB19 and CB08 both landed on — read the manifest, which has always known:

```kotlin
/**
 * Anchor for reading this jar's manifest. A top-level `val` has no class of its own to reflect
 * over, so one is named explicitly rather than reaching for `object {}`.
 */
private object VersionAnchor

private val VERSION: String =
    VersionAnchor::class.java.`package`?.implementationVersion ?: "dev"
```

`modules/engine/build.gradle.kts:111` already writes `"Implementation-Version" to project.version`,
so nothing new was needed in the build. The explicit anchor object is the only wrinkle: Kotlin
compiles top-level declarations into a synthetic `MainKt` class that the file cannot name, so a
named object is clearer than reflecting over an anonymous one.

## Verification

```
$ java -jar modules/engine/build/libs/utlxe-1.4.4.jar --version
utlxe v1.4.4
```

and `scripts/check-no-pinned-versions.py` reports no remaining version literals under
`modules/*/src/main/kotlin`. CB14 explains why that check sweeps for *any* version shape rather
than the current release number.
