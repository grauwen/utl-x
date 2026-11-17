# UTL-X 1.4.2 — Release Notes

**An engine release.** Where [1.4.1](release-notes-1.4.1.md) fixed the Azure Marketplace
deployment artifact, this fixes the engine defects that the first real deployment exposed —
each one invisible to code review, 4956 unit tests and 582 conformance tests, and each found by
reading the output of a running container.

1.4.1 was never submitted to Partner Center; it is superseded here so the offering takes one
certification cycle rather than two.

## Readiness no longer requires loaded work

[EB18](../bugs-fixed/EB18-readiness-requires-a-transformation-so-no-deployment-can-serve.md) /
[EB19](../bugs-fixed/EB19-admin-info-reports-a-hardcoded-version.md)

```kotlin
val isReady = engine.state == EngineState.RUNNING && txCount > 0   // before
val isReady = engine.state == EngineState.RUNNING                  // after
```

The engine ships empty by design, so requiring a loaded transformation made a freshly started
instance **permanently unready**. Azure Container Apps routes nothing to a replica with a
not-ready container, so the Admin API needed to upload a transformation was itself unreachable:
a deadlock in which no deployment of the offering could ever serve traffic.

The same conflation existed in two places — `HealthEndpoint.kt` and `/admin/info` — and both are
fixed. The transformation count remains in each response body, so "has work loaded" is still
available as a separate fact.

1.4.1 worked around this in the Marketplace template by pointing the readiness probe at
`/health`. That remains correct and is now equivalent either way.

## `/admin/info` reports the real version

[EB19](../bugs-fixed/EB19-admin-info-reports-a-hardcoded-version.md)

The endpoint answered the literal `"1.0.1"` — unchanged since it was typed, and therefore wrong
on every release since. The Admin Web UI showed `v1.0.1` while running an image built from
1.4.1. It now reads `Implementation-Version` from the jar manifest, which
`modules/engine/build.gradle.kts` already stamps from `project.version`.

A running engine can finally be asked what it is.

## The `%utlx` namespace gate no longer depends on a compiler limitation

[EB20](../bugs-fixed/EB20-namespaced-calls-never-compile-and-the-gate-depended-on-it.md)

`LanguageVersionStaticGate.check()` — which enforces that `validate.*` requires `%utlx 1.1` —
was called only from the interpreter. It held in practice only because no `validate.*` call
could be compiled, so every script using one fell back to the interpreter that enforced it. The
gate's enforcement was a side effect of an unrelated limitation in `ASTCompiler`.

It now runs in `requireSupportedLanguageVersion`, which all three strategies already call, so
TEMPLATE, COPY and COMPILED enforce it alike.

**Still open in the same report:** `validate.*` does not compile, so `%utlx 1.1` validation runs
interpreted on every strategy. Enabling compilation was attempted and reverted — six
`ConformanceStrategyParityTest` fixtures under `language/validate/` then disagreed with
TEMPLATE on rules carrying lambdas, `when` guards and per-element `every`. A wrong verdict is
worse than a slow one; EB20 names those six fixtures as the acceptance criteria for a real fix.

## Marketplace packages

```
deploy/azure-marketplace/zips/utlxe-starter-1.4.2.zip
deploy/azure-marketplace/zips/utlxe-professional-1.4.2.zip
```

Unchanged from 1.4.1 apart from the image pins. The 1.4.1 packages stay in `zips/` as the
archive record, marked superseded before submission.

## Images

```
ghcr.io/utlx-lang/utlxe:1.4.2      ghcr.io/utlx-lang/utlxe:latest
ghcr.io/utlx-lang/utlxe-ui:1.4.2   ghcr.io/utlx-lang/utlxe-ui:latest
```

## Verification

- **1028 engine tests, 0 failures**, including `ConformanceStrategyParityTest`
- `:modules:core:test` passes
- One existing test asserted the defect — `AdminEndpointTest` expected 503 from an empty
  engine. It was rewritten to assert the contract, with the reason recorded in the test.
- The deployment scripts under `deploy/azure-marketplace/` were updated to the corrected
  contract: the acceptance suite now expects a fresh container to be **ready**, and the admin
  suite asserts readiness on an empty engine before anything is uploaded — the exact state that
  deadlocked.
