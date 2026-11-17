# EB19 — `/admin/info` reports a hardcoded version, wrong since 1.0.1

**Status:** Fixed — 2026-11-17
**Priority:** Medium — the one authoritative statement of what is running is wrong, on every
deployment, in a way that silently misdirects support and release verification
**Component:** `:modules:engine` — `admin/AdminEndpoint.kt`
**Found:** 2026-11-17, reading the output of the first functional test run against Azure

## Summary

`/admin/info` answers with a string literal:

```kotlin
// AdminEndpoint.kt:1144
get("/info") {
    call.respond(HttpStatusCode.OK, mapOf<String, Any?>(
        "version" to "1.0.1",
```

It has reported `1.0.1` through 1.1.0, 1.2.0, 1.2.1, 1.3.0, 1.3.1, 1.4.0 and 1.4.1. The Admin
Web UI renders this value in its header, so a running engine displays `v1.0.1` regardless of
the image it was built from.

Observed against `ghcr.io/utlx-lang/utlxe:1.4.1` in Azure:

```
· engine version: 1.0.1
```

## Why it matters more than a cosmetic string

`/admin/info` is the first thing anyone queries when diagnosing a deployment.

- A customer reporting a problem reports `1.0.1`, whatever they are running.
- There is no way to confirm from a running engine that a fix reached it. Every verification
  of "is the new engine live?" has to go through the image digest instead, which the operator
  usually cannot see.
- It defeats the purpose of the OCI version label added to the images in 1.4.1: the container
  is correctly labelled `1.4.1` while the process inside announces `1.0.1`.

## The correct value is already present and ignored

`modules/engine/build.gradle.kts` stamps the manifest from the build:

```kotlin
manifest {
    attributes(
        "Implementation-Title"   to "UTL-X Engine (UTLXE)",
        "Implementation-Version" to project.version
    )
}
```

and the shipped jar carries it:

```
Implementation-Title: UTL-X Engine (UTLXE)
Implementation-Version: 1.4.0
```

So the build already knows the version, writes it into the artifact, and the endpoint answers
with something else.

## Fixed

```kotlin
"version" to (AdminEndpoint::class.java.`package`?.implementationVersion ?: "unknown"),
```

`implementationVersion` is null when the classes are loaded from a plain classpath rather than
a jar — in tests, or when running from a build directory. `"unknown"` is the right answer
there: it is honest about not knowing, where a literal is confidently wrong.

Anchored on `UtlxEngine::class.java` — the file declares a top-level `configureAdmin`, not a
class, so there is no `AdminEndpoint` type to reference — and `EB19ReadinessAndVersionTest`
asserts the reported value is **not** the old literal rather than asserting an exact number, so
it keeps working across releases.

## Related — the same handler repeats EB18

Two lines below the version, `/admin/info` computes readiness the same way
[EB18](EB18-readiness-requires-a-transformation-so-no-deployment-can-serve.md) describes:

```kotlin
"ready" to (engine.state == EngineState.RUNNING && registry.list().isNotEmpty()),
```

That is the identical conflation of *ready to accept requests* with *already has work loaded*,
duplicated from `HealthEndpoint.kt:145` (`isReady = engine.state == RUNNING && txCount > 0`).
EB18 was worked around in the Marketplace template by pointing the readiness probe elsewhere.
**Both sites are now fixed**: readiness is `engine.state == RUNNING` in `HealthEndpoint.kt` and
in `/admin/info`, and the transformation count remains in each body for anything that wants the
other signal. That closes EB18's "Still open" section as well as this report.

One existing test asserted the defect — `AdminEndpointTest.readiness returns not ready when no
transformations` expected 503 on an empty engine. It was rewritten to assert the contract, with
the reason recorded in the test, since a test that pins a bug is worse than no test.

### Verified

- `:modules:engine:test` — **1028 tests, 0 failures**, including `ConformanceStrategyParityTest`
- `EB19ReadinessAndVersionTest` — an empty RUNNING engine reports ready, the count is still
  reported separately, and the version is not the literal
- `deploy/azure-marketplace/` scripts updated: the acceptance suite now expects a fresh
  container to be **ready**, and the admin suite asserts readiness on an empty engine *before*
  anything is uploaded — the exact state that deadlocked

## How it was found

Not by review. The functional suite prints `/admin/info`'s version as context rather than
asserting on it, and the number simply did not match the image it had just been run against.
Both this and the readiness duplication belong to the same class as the admin key stored as a
plain environment variable while the wizard promised it could not be retrieved: a claim and a
reality that were never checked against each other, and that only diverge visibly once
something is actually deployed and someone reads the output.
