# IB09: the daemon and the IDE reported stale versions, and the extension launched a stale jar

> **IB = IDE/daemon**, which do not ship on `main`, so this lives in `devdocs/`. The same defect in
> `modules/cli` is [CB14](../../docs/bugs-fixed/CB14-a-release-version-written-down-in-eleven-places.md)
> — read that first; it carries the shared analysis, the three earlier fixes that did not stop it,
> and the check that now prevents a fourth.

**Status:** Fixed — 2026-11-26.
**Priority:** Medium. One of these made a correct diagnosis look wrong and cost real debugging
time; another meant the IDE could only ever work on one developer's machine; a third meant a
documented dev script could never work at all.
**Component:** `modules/daemon` — `Main.kt`, `mcp/McpService.kt`, `rest/RestApiServer.kt`,
`DaemonServer.kt`, `build.gradle.kts`; `theia-extension` — `utlx-daemon-client.ts`,
`dev-start-daemon.sh`
**Found:** 2026-11-25/26, while tracing *"functions in the IDE no longer see any functions"*.

## Four defects, one root cause

### 1. `/api/health` reported 1.4.0 while serving 1.4.4

```
$ curl localhost:7779/api/health
{"status":"ok","version":"1.4.0","uptime":528398}
```

This is the one that cost time. Chasing the IDE's empty function list, the health endpoint said
1.4.0 — which made "the daemon is a stale build" look like a confirmed diagnosis. It was not: the
running process was `utlxd-1.4.4.jar` and was serving the correct 724-entry catalogue. The literal
was simply old.

### 2. The LSP handshake reported 1.4.0 to every editor

`DaemonServer.kt` answered `"serverInfo" to mapOf("name" to …, "version" to "1.4.0")` in the LSP
`initialize` response — so every connected editor was told the wrong language-server version.

### 3. The extension hardcoded one developer's absolute path, pinned to a stale jar

```ts
daemonJarPath: options.daemonJarPath ||
    '/Users/magr/data/mapping/github-git/utl-x/modules/daemon/build/libs/utlxd-1.4.0.jar',
```

Both halves were defects. The absolute path meant the extension could spawn a daemon on exactly one
machine. The pinned version meant that whenever it *did* spawn one, it launched the **Nov-15**
build — which carries a 705-entry function catalogue in which `createQname` does not appear,
while the repository had 724 and `createQname` was callable. An IDE showing a function list that
disagrees with the language is precisely the symptom that started this.

The ports in the very same object were already `process.env.UTLXD_LSP_PORT || '7777'` — the
convention for making this configurable existed three lines away.

### 4. `dev-start-daemon.sh` could never work

```bash
DAEMON_JAR="../modules/server/build/libs/utlxd-1.3.0.jar"
```

There is no `modules/server` — the module is `modules/daemon` — and 1.3.0 was two releases stale.
Wrong directory *and* wrong version, so the script always exited with "UTLXD daemon not found".
Its error message then advised `./gradlew :modules:server:build`, a task that does not exist.

## Fix

**The three daemon literals read the jar manifest**, following CB08/EB19.
`modules/daemon/build.gradle.kts:95` already writes `"Implementation-Version" to project.version`:

```kotlin
private val SERVER_VERSION: String =
    RestApiServer::class.java.`package`?.implementationVersion ?: "dev"
```

applied to `/api/health`, the LSP `serverInfo`, `Main.kt`'s `--version` and `McpService`'s
handshake. Result: `utlxd --version` → `UTL-X Daemon v1.4.4`.

**The extension resolves the jar** instead of naming it — `UTLXD_JAR` override first (matching the
port convention), then the newest `utlxd-*.jar` found by walking up from `__dirname` to locate the
repo, then `UTLX_REPO` for a packaged extension outside the tree. A missing jar is now reported as
such, rather than spawning `java -jar ''` and failing with a message about java:

```
No utlxd jar found. Build it with "./gradlew :modules:daemon:jar", or point UTLXD_JAR at one.
```

> A detail worth recording: the first attempt inserted that resolver function between
> `@injectable()` and the class it decorates, and `tsc` rejected it — *"TS1206: Decorators are not
> valid here"*. That is the same mistake as [B68](../../docs/bugs-fixed/B68-working-functions-are-absent-from-the-catalogue.md):
> an annotation binds to the **next** declaration, so inserting anything between them silently
> reassigns it. Kotlin bound it to the wrong function and said nothing; TypeScript refused to
> compile. Same defect, louder language.

**`dev-start-daemon.sh` resolves the newest jar** from `modules/daemon/build/libs`, honours
`UTLXD_JAR`, guards against an empty resolution, and its error message names the task that exists.

## Verification

- `utlxd --version` → `UTL-X Daemon v1.4.4`; `/api/health` reports 1.4.4
- `scripts/check-no-pinned-versions.py` → `ok`, which covers the daemon source and its launcher
  scripts as well as the CLI's
- `npx tsc --noEmit` clean on the extension (typecheck only — the extension is built by hand here)
- `dev-start-daemon.sh` resolves `utlxd-1.4.4.jar` rather than failing

## Not fixed here

The extension's `start()` spawns the daemon with `--lsp-transport http`, while the daemon that was
actually running had been started with `--lsp --api`. The two flag sets are not obviously the same
contract, and nothing tests the extension's spawn path. That is a separate question from the
version staleness and is left open deliberately.
