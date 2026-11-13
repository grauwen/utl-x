# IB08 — the daemon cannot select a parse profile, so a deployment's `strict` is silently ignored

**Status:** Fixed — 2026-11-13
**Component:** `modules/daemon` — `rest/RestApiServer.kt`
**Found:** 2026-11-13, while verifying what remained of
[B31](../../docs/bugs-fixed/B31-duplicate-key-policy-inconsistent-json-vs-yaml.md)
**Severity:** Medium — the IDE preview can accept input the production engine rejects, which
is the preview-≠-production class that B24 was also an instance of

## Summary

A parse profile (`strict` / `standard` / `lenient`) bundles the leniency settings every reader
shares — duplicate keys, comments, trailing commas, DOCTYPE, undefined entities, nesting depth.
Every surface could choose one except this daemon:

| surface | selection |
|---|---|
| CLI | `--parse-profile strict\|standard\|lenient` |
| engine | `UtlxEngine.createStrategy` → `EngineConfig.effectiveParseProfile()` → every strategy |
| **daemon** | **none** — `RestApiServer` built a bare `TransformationService()` |

`TransformationService.TransformOptions` already carried a `parseProfile` field; both call
sites in `RestApiServer` simply never set it, so it defaulted to `STANDARD`.

That is not *wrong* output — standard is also the CLI's default — but a deployment that pins
`strict` was not honoured here, and the IDE preview is exactly where a mapper forms their
belief about whether a document is acceptable. A duplicate-keyed payload previewing cleanly
and then being rejected by a strict production engine is the same preview-≠-production trap as
[B24](../../docs/bugs-fixed/B24-input-silently-aliased-to-first-input.md).

## Fix

`RestApiServer` takes the profile as a constructor parameter and passes it at both call sites:

```kotlin
private val parseProfile: ParseProfile = resolveParseProfile()
…
options = TransformationService.TransformOptions(
    verbose = false, pretty = true,
    parseProfile = parseProfile   // B31/IB08
)
```

Resolved from `UTLX_PARSE_PROFILE` or `-Dutlx.parse.profile`, defaulting to `STANDARD` — the
behaviour before this change, so nothing moves for a deployment that sets nothing. An
unrecognised value logs a warning and falls back to standard rather than failing startup: a
daemon that refuses to boot over a typo'd profile is worse than one that runs at the
documented default and says so.

### Why not `DaemonConfig`

That would be the natural home, but `DaemonConfig.load` is still a stub — it returns defaults
and drops `cliOverrides`, with two `TODO`s saying so. Building profile selection on top of it
would mean fixing configuration loading first, which is a larger, separate piece of work.

The env-var/system-property pair is the pattern this module already uses for its other
deployment setting (`BundleWorkspace.resolve` reads `UTLX_WORKSPACE` /
`utlx.workspace`), so this follows the local convention rather than inventing a third one.
When `DaemonConfig` becomes real, `resolveParseProfile()` is the single place to re-point.

## Classification note

This was initially written up as the tail of B31, which was wrong twice over: a daemon defect
is **IB**, not B, and IB reports live in `devdocs/` because the IDE and daemon do not ship on
`main`. B31's own subject — duplicate-key policy differing between JSON and YAML readers — is
a `:core`/formats concern and stays correctly filed as B.

The rule in `docs/bugs-fixed/README.md` is the one to apply: *look at the `Component:` line you
are about to write.* `modules/daemon` → IB.
