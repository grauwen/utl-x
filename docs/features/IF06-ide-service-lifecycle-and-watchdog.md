# IF06: IDE — Service Lifecycle, Supervision & Die-With-Parent Watchdog

**Status:** Implemented (May 2026) — both directions done; TS supervision has no unit-test harness yet
**Priority:** High
**Created:** May 2026
**Depends on:** existing service-lifecycle-manager (Theia backend); `utlxd`, MCP server
**Effort:** Medium (2-3 weeks)

> **Implemented — die-with-parent watchdog (child-watches-parent):**
> - `utlxd`: `ParentWatchdog.kt` (ProcessHandle PID poll; testable `checkOnce`/`isParentAlive`
>   split from the thread/`exitProcess` plumbing) + `StartCommand --parent-pid` (opt-in).
>   Unit-tested in `ParentWatchdogTest.kt` (4 tests, green).
> - MCP server: `UTLX_PARENT_PID` poll via `process.kill(pid, 0)` in `mcp-server/src/index.ts`.
> - Spawn sites pass the parent PID: `service-lifecycle-manager.ts`, `auto-start-services.ts`,
>   `utlx-daemon-client.ts` (utlxd `--parent-pid`; MCP `UTLX_PARENT_PID`).
> - Verified: child self-exits ~one poll after parent death; no parent-pid ⇒ no watchdog.
>
> **Implemented — supervision (parent-watches-child)** in `service-lifecycle-manager.ts`
> (the active `BackendApplicationContribution`; `auto-start-services.ts` is disabled):
> - `scheduleRestart()`: exponential backoff (1s→30s cap), wired from both child `exit`
>   handlers (replaces the old `// TODO: Implement restart logic`).
> - Crash-loop cap: give up after >5 restarts in a 60s rolling window, with a clear error.
> - Backoff resets once a service is healthy again.
> - `gracefulKill()`: SIGTERM, then SIGKILL after `shutdownTimeout`; `onStop()` awaits both.
> - Idempotent daemon reuse: `startUTLXD`/`startMCPServer` skip if the port is already healthy.
>
> **Note:** the extension has no JS test runner, so the TS supervision logic is covered by
> manual/integration verification only (the Kotlin watchdog has JUnit coverage). Standing up
> jest for the extension is tracked separately, not in IF06.

---

## Summary

The IDE spawns and supervises two backing services — `utlxd` (the language/transform
daemon) and the UTLX MCP server (AI assist) — as children of the Theia backend.
Today supervision is incomplete: restart logic is a TODO, and a hard crash of the
parent (Theia/Electron) leaves **orphaned** child processes holding their ports.
IF06 hardens this: robust Node-side supervision plus a **die-with-parent watchdog**
inside each child so children never outlive the IDE.

This is the "Process Lifecycle" and "Process Watchdog" design in
`theia-extension-design-with-design-time.md`.

## Problem

- `service-lifecycle-manager.ts` starts `utlxd` and the MCP server but has a TODO
  where restart-on-crash should be — a crashed daemon stays down.
- Node's exit handlers only cover *graceful* shutdown. If the Theia/Electron parent
  is `kill -9`'d or crashes, the OS does not reap the children → orphaned `utlxd` /
  MCP processes keep `7779/7777/7780` bound → the next launch hits `EADDRINUSE` and
  may silently keep talking to a stale process. This has recurred repeatedly in dev.

A Java service wrapper (Tanuki / YAJSW / jsvc) is explicitly **not** the answer:
those make a JVM an *independent* OS service that survives reboots — the opposite of
"die with the IDE" — and add a second supervisor competing with Node.

## Goals

- **Restart-with-backoff** for both children: on unexpected exit, respawn with
  exponential backoff, a max-retry cap, and crash-loop detection (stop after N
  failures in a window, surface an error to the user).
- **Graceful shutdown** on backend stop: SIGTERM, then SIGKILL after a timeout.
- **Idempotent daemon start**: reuse a healthy `utlxd` (don't restart the heavy
  JVM on every rebuild); recycle only the cheap MCP server.
- **Die-with-parent watchdog** inside each child: when the parent dies (any way),
  the child exits on its own.
- **Port pre-clean** on start: free a stale port (and matching process) before
  spawning, as a belt-and-suspenders backstop.

## Non-Goals

- An OS-level service wrapper or daemonization — rejected by design.
- Changing what the services do; this is purely lifecycle/supervision.
- The native-`utlxd` question — orthogonal (the watchdog is required regardless;
  see IF07 / design doc).

## Design

**Two complementary directions.**

1. **Parent-watches-child (supervision)** — in `service-lifecycle-manager.ts`:
   the existing `.on('exit')` hooks gain restart-with-backoff + crash-loop cap;
   `waitForUTLXD()` already gates readiness; shutdown sends SIGTERM then SIGKILL
   after a timeout.

2. **Child-watches-parent (watchdog)** — the robust orphan fix, independent of how
   the parent dies:
   - At spawn, the parent passes its PID and/or an inherited pipe FD to the child.
   - The child runs a small watchdog: if the pipe closes (EOF) or the parent PID
     disappears, it `exit()`s immediately.
   - Portable: Linux has `PR_SET_PDEATHSIG`; macOS has no equivalent, so the
     pipe/PID-watch is the cross-platform mechanism (~30 lines in `utlxd`, a few
     lines in the MCP server, a few on the Node side).

**Runtime/packaging agnostic.** The watchdog is about *process topology*, not JVM
vs. native or Electron vs. web. A native `utlxd` spawned by Node is still an
orphan-able child, so this work stands regardless of any future GraalVM decision.

## Implementation Notes

- Node side (`service-lifecycle-manager.ts`): backoff/retry state machine; pass
  `process.pid` (and/or a pipe) into the child's spawn `env`/`stdio`; SIGTERM→SIGKILL
  shutdown; port pre-clean using the existing kill helpers.
- `utlxd` (Kotlin): a daemon watchdog thread — poll the parent PID / read the pipe;
  on parent death, `exit()`. Wire into the existing `start`/`stop` lifecycle.
- MCP server (Node child): same watchdog via `process.on` of the inherited pipe /
  PID poll.
- Config: env-driven (`UTLX_PARENT_PID`, retry/backoff knobs) consistent with the
  existing `UTLXD_*` / `UTLX_MCP_*` variables.

## Acceptance Criteria

- Killing the Theia/Electron backend with `kill -9` results in `utlxd` and the MCP
  server exiting on their own within a short bound (no orphaned port holders).
- A child that crashes is restarted with backoff; a crash loop stops after the cap
  and surfaces a clear error.
- Backend graceful stop terminates both children (SIGTERM, then SIGKILL on timeout).
- A healthy pre-existing `utlxd` is reused, not restarted, across IDE rebuilds.
- No `EADDRINUSE` on a normal restart cycle.

## Testing

### Implemented unit tests + how to run them

**Kotlin — die-with-parent watchdog** (`ParentWatchdogTest.kt`, JUnit5, 4 tests):

```bash
# from the repo root
./gradlew :modules:daemon:test --tests 'org.apache.utlx.daemon.ParentWatchdogTest'
# or run the whole daemon suite
./gradlew :modules:daemon:test
```

**TypeScript — restart-supervision policy** (`restart-policy.test.ts`, jest, 5 tests).
jest + ts-jest are already installed (hoisted) at the workspace root, so no install
is needed. Run from the extension package dir:

```bash
cd theia-extension/utlx-theia-extension
# jest is hoisted to the workspace root node_modules:
../../node_modules/.bin/jest --config jest.config.js
# or target one file:
../../node_modules/.bin/jest --config jest.config.js src/node/services/restart-policy.test.ts
```

Notes:
- The jest config (`jest.config.js`) is scoped to `src/node` (node env). Test files
  match `**/*.test.ts` and run from `src` — they are **excluded from the `tsc` build**
  (`tsconfig.json`), so nothing test-related is compiled into `lib/` or shipped.
- Tests run only when jest/gradle is invoked — never at compile or at IDE/daemon startup.
- There is intentionally no `test` script in the extension `package.json` yet: its
  `prepare`/`clean`/`build` lifecycle conflicts with a plain `npm test` in this yarn
  workspace. Invoke jest via the root binary as above (or wire a workspace-level
  `yarn test` later).

### Further (manual / integration) verification

- Integration: spawn → `kill -9` the parent → assert children exit (ports freed).
  (Verified manually: MCP self-exits ~one poll after a dead parent PID.)
- Integration: kill a child → assert restart-with-backoff; force a crash loop →
  assert cap + error surfaced.
- Cross-platform: verify the pipe/PID watchdog on macOS and Linux (and Windows for
  the Electron path).

## Related

- Design: `theia-extension-design-with-design-time.md` §"Process Lifecycle" and
  §"Process Watchdog: Die-With-Parent"
- IF07 (Electron app & installers — where die-with-parent matters most)

## Effort Estimate

Medium (2-3 weeks): Node supervision/backoff + shutdown (~1 wk), `utlxd` + MCP
watchdog threads (~1 wk), cross-platform tests (~0.5 wk).
