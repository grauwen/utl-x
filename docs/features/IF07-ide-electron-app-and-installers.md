# IF07: IDE — Electron Desktop App & Per-Platform Installers

**Status:** Proposed
**Priority:** Medium
**Created:** May 2026
**Depends on:** IF06 (service lifecycle & watchdog); existing `browser-app` + extension
**Effort:** Large (4-6 weeks)

---

## Summary

Ship UTL-X as a full desktop IDE: a Theia **Electron** application with `utlxd`,
the MCP server, and a bundled Java runtime embedded, packaged into **signed,
double-click installers** for macOS, Windows, and Linux. The end user gets the
complete IDE experience with **no external prerequisites** — no separate Java,
Node, or daemon install.

This is the "Electron Desktop Application" and "Per-Platform Installers" design in
`theia-extension-design-with-design-time.md`.

## Problem

Today the IDE runs only as `browser-app` (Theia over HTTP, opened in a browser),
which assumes a server environment with `java`/`node` available and the services
started by scripts. There is no shippable product a non-technical user can install
and run. To distribute UTL-X as an IDE, we need a desktop shell that bundles and
self-manages its services.

## Goals

- An **`electron-app`** package alongside `browser-app`, sharing the same extension
  and backend code.
- Bundle `utlxd.jar` + a trimmed **`jlink` JRE** + the MCP server inside the app;
  spawn them by absolute path from app resources (never relying on `PATH`).
- Produce **per-platform installers** from one config (electron-builder):
  - macOS: `.dmg`/`.pkg`, universal arm64 + x64, **code-signed + notarized**,
    hardened runtime.
  - Windows: NSIS `.exe`/`.msi`, **Authenticode-signed** (EV cert to avoid
    SmartScreen), Squirrel auto-update.
  - Linux: `AppImage` + `.deb`/`.rpm`.
- Single-instance lock so two windows don't fight over `7777/7779/7780`.
- (Optional) auto-update feeds; daemon/JRE update atomically with the app.

## Non-Goals

- Native-image `utlxd` — a separate future optimization (this ships the jar + JRE);
  the installer pipeline is forward-compatible with swapping in a native binary later.
- Cloud/hosted deployment — that remains the `browser-app` path.
- Re-architecting services — lifecycle/supervision comes from IF06 unchanged.

## Design

**Two shells, one codebase.** `electron-app` and `browser-app` share the extension
and Theia backend. The web path serves over HTTP (frontend talks to services only
via backend JSON-RPC — never service ports directly). The Electron path runs the
same backend locally inside the app.

**Theia-pinned Electron.** Add `electron-app` with every `@theia/*` dep pinned to
the same version the extension already uses (mixing Theia versions across the two
apps is the #1 breakage). Do **not** pick an Electron version directly — take the
one Theia pins via `@theia/electron` and pin that exact value. Build on the matching
Node LTS.

**Native modules & ASAR.** `node-pty` (terminal) and any spawned binaries must be
rebuilt for Electron's ABI (`@electron/rebuild`). In packaging, `asarUnpack` the
JRE, `utlxd.jar`, the MCP server, and `node-pty` — ASAR archives break process
spawning and file execution.

**Bundled runtime per platform.** Each installer carries the matching `jlink` JRE
for that OS/arch (only the JDK modules `utlxd` needs — tens of MB). Resolve and
spawn `<resources>/jre/bin/java -jar <resources>/utlxd.jar …` by absolute path.
Build on each target OS / per arch — native modules and the JRE are not portable.

**Lifecycle in Electron.** The Electron *main* process owns app startup; the Theia
*backend* (Node) remains the supervisor of `utlxd` + MCP (IF06). The
die-with-parent watchdog (IF06) matters most here, since users quit/kill the
desktop app directly.

## Implementation Notes

- New `theia-extension/electron-app/` (mirror `browser-app`), Theia electron target.
- Packaging via **electron-builder** with a per-platform matrix (CI on macOS,
  Windows, Linux runners); config covers dmg/nsis/AppImage + signing + notarization.
- `jlink` step in the build to produce per-OS JREs; place under app resources and
  mark `asarUnpacked`.
- Service paths resolved relative to `app.getAppPath()` / resources dir, fed into
  the existing `MCP_SERVER_PATH` / `UTLXD_JAR_PATH` env the lifecycle manager reads.
- Single-instance lock via Electron `requestSingleInstanceLock()`.
- Secrets for signing/notarization in CI secrets, never in the repo.

## Acceptance Criteria

- `electron-app` launches the full IDE locally with `utlxd` + MCP auto-started and
  no `java`/`node` on the host `PATH`.
- Installers build for macOS (signed + notarized), Windows (signed), and Linux.
- Quitting the app cleanly stops the services; a hard kill triggers the IF06
  watchdog (no orphans).
- Two launches don't collide on the service ports (single-instance lock).
- A clean machine (no JDK) can install and run from the installer.

## Testing

- Build matrix produces all three platforms in CI.
- Smoke test each installer on a clean VM: install → launch → generate/validate a
  transformation → quit → assert services stopped.
- macOS: verify Gatekeeper accepts the notarized build; Windows: verify SmartScreen
  behavior with the signing cert.

## Related

- Design: `theia-extension-design-with-design-time.md` §"Electron Desktop
  Application" and §"Per-Platform Installers"
- IF06 (service lifecycle & watchdog — prerequisite)
- `cli-daemon-split-architecture.md` (native CLI; future native `utlxd`)
- `docs/distribution/PACKAGE_MANAGERS.md` (CLI distribution — related but separate)

## Effort Estimate

Large (4-6 weeks): `electron-app` scaffold + native-module rebuild (~1.5 wk),
`jlink` JRE bundling + path-based spawn (~1 wk), electron-builder per-platform
config + signing/notarization (~2 wk), CI matrix + clean-VM smoke tests (~1 wk).
