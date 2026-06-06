# IF14: IDE — Theia Hardening for a Cloud / Multi-Tenant Offering

> **Resulting menu/toolbar/status-bar surface** is specified in **[IF18: IDE Menu & Chrome
> Structure](IF18-ide-menu-and-chrome-structure.md)**. IF14 owns the *mechanism* (package
> omission, command/preference lockdown, sandboxing); IF18 owns the *layout* it produces.

**Status:** Proposed
**Priority:** High (a prerequisite for offering the UTL-X IDE as a hosted Theia Cloud product)
**Created:** June 2026
**Component:** IDE stack — the Theia **application composition** (browser-app) + a hardening contribution; backend sandboxing
**Depends on:** the existing Theia `browser-app`, the UTLX extension; pairs with IF07 (desktop build) and IF15 (in-product help)
**Effort:** Large

> **Scope note (taxonomy):** "IF" = the IDE stack (Theia + MCP + utlxd). This is about the
> **cloud/hosted** build only; the **local/desktop** build (IF07) is unaffected — it runs on
> the user's own machine and may keep the terminal etc.

> **Design decisions captured here (not yet implemented):**
> - In a **hosted, multi-tenant** offering the Theia backend runs on *our* infrastructure,
>   so the IDE must not give an untrusted user **shell access, host filesystem access beyond
>   their sandbox, arbitrary process/code execution, extension installation, or network
>   exfiltration**. The default Theia app exposes all of these.
> - **Strongest mechanism = don't ship the capability.** Compose a *minimal cloud Theia
>   app* that omits the dangerous `@theia/*` packages entirely (terminal, task, debug,
>   vsx-registry, …). Command/menu pruning is belt-and-suspenders, not the primary control.
> - **Two build profiles, one extension:** `local`/desktop (full) vs `cloud` (hardened).
> - IDE hardening is **in addition to**, not instead of, infra isolation (containers,
>   namespaces, network policy, quotas).

---

## Summary

Offering the UTL-X IDE as a **Theia Cloud** product means the Theia *backend* (a Node
process with full host access: shell, filesystem, child processes) runs on shared
infrastructure that untrusted users drive through the browser. Stock Theia ships an
**integrated terminal, tasks, debug, and extension installation** — each a direct path to
shell/code execution on the host. IF14 defines a **hardened cloud build**: remove those
capabilities at the package level, prune any residual commands/menus, **jail file access to
a per-tenant workspace**, lock preferences, and constrain webview/network — turning the IDE
into a deny-by-default surface that allows only the UTL-X authoring workflow.

## Problem

The current `browser-app` is a full Theia IDE. As a hosted product that is unsafe:

- **Integrated terminal** (`@theia/terminal`) → a shell **on the backend host**. Critical.
- **Tasks / Debug** (`@theia/task`, `@theia/debug`) → run arbitrary processes/debuggers.
- **Extension install** (`@theia/vsx-registry`, plugin host) → load arbitrary code.
- **Open Folder / file dialogs** to arbitrary host paths → read/write outside the tenant.
- **Preferences** can re-enable the above or point tools at host resources.
- **Webviews** to arbitrary URLs → XSS / data exfiltration.
- **SCM/Git** to arbitrary remotes → exfiltration (product-dependent).

Container isolation alone is not enough: a shell inside the tenant container still exposes
secrets, the workspace of mistakes, lateral movement, and a poor security posture for a
SaaS. The IDE itself must not offer these.

## Goals

- A **hardened cloud Theia build** with **no** terminal, tasks, debug, or extension install.
- **File access jailed** to a per-tenant workspace (no traversal, no host paths).
- **Dangerous commands/menus/keybindings absent** from the palette and menu bar.
- **Preferences locked** to a safe allow-list (cannot re-enable removed capabilities).
- **Webview/network constrained** (CSP; no arbitrary outbound from the IDE shell).
- The **local/desktop build is unchanged** (same extension, different composition).

## Non-Goals

- Hardening the desktop/Electron build (IF07) — that's the user's own machine.
- Replacing infrastructure isolation (containers/namespaces/network policy/quotas) — IF14
  is defense-in-depth on top of those, not a substitute.
- Per-tenant authn/authz / billing — separate platform concerns.

## Design

### 1. Minimal cloud app composition (primary control)
Build a separate **cloud `browser-app`** that bundles only the `@theia/*` packages the
UTL-X workflow needs — e.g. `core`, `editor`, `monaco`, `filesystem`, `workspace`,
`markers`, `messages`, `output`, plus the **UTLX extension** — and **omits**:
`@theia/terminal`, `@theia/task`, `@theia/debug`, `@theia/vsx-registry` (+ plugin
marketplace), and anything else exposing process/host. *If the code isn't bundled, it
can't be invoked* — the strongest guarantee.

### 2. Hardening contribution (belt-and-suspenders)
A `CloudHardeningContribution` that, at startup in the cloud profile:
- **Unregisters / hides** residual dangerous commands (terminal:new, workbench tasks/debug,
  extension install, `workspace:openFolder` to arbitrary paths, file open-dialog to host).
- **Prunes menus** (Terminal menu, Run/Debug, parts of File) and **removes keybindings**.
- Driven by an **allow-list** of UTL-X commands, not a deny-list (deny-by-default).

### 3. Backend sandboxing
- A **fixed, per-tenant workspace root**; `FileService`/backend file access **jailed** to it
  (reject path traversal / absolute host paths).
- Resource limits (file count/size, memory) coordinated with infra quotas.
- The daemon (utlxd) + MCP run per-tenant or behind a gateway — never exposing host paths.

### 4. Preference lockdown
Ship **non-overridable** preferences in the cloud profile (no `terminal.integrated.*`, no
extension-install, no settings that point tools at the host); hide the preference UI for
locked keys.

### 5. Webview / network constraints
Strict **CSP** for webviews; restrict outbound from the IDE shell to the product's own
services; no arbitrary URL opening.

### 6. Two profiles, one extension
The UTLX extension is identical; the **app** differs: `browser-app` (local, full) vs
`browser-app-cloud` (hardened composition + hardening contribution + locked prefs). A build
flag/profile selects which.

## Implementation Notes
- New `browser-app-cloud` (or a build profile) with a curated dependency set.
- `CloudHardeningContribution` (CommandContribution/MenuContribution filtering, keybinding
  removal) gated by a `UTLX_PROFILE=cloud` flag.
- Backend: workspace-jail wrapper around file access; reject escapes.
- Locked preferences JSON shipped with the cloud app.
- A test that asserts the cloud build exposes **none** of the deny-listed commands.

## Acceptance Criteria
- The cloud build has **no integrated terminal, tasks, debug, or extension install** (absent
  from palette, menus, keybindings).
- File operations cannot escape the per-tenant workspace (traversal/host paths rejected).
- Locked preferences cannot re-enable removed capabilities.
- Webviews are CSP-restricted; no arbitrary outbound.
- The **desktop/local build is unchanged** and still has full capabilities.
- An automated check fails if a deny-listed command/menu reappears in the cloud build.

## Testing
- **Build test:** assert the cloud bundle does not include `@theia/terminal|task|debug|vsx-registry`.
- **Command audit:** enumerate registered commands in the cloud profile; assert the
  deny-list is absent and only the allow-list (+ safe core) remains.
- **Sandbox test:** attempt path traversal / host-path open via the file APIs → rejected.
- **Manual:** no Terminal/Run/Debug menus; no marketplace; preferences locked.

## Related
- **IF15** — in-product Help (the cloud user's reference once external tools are removed).
- **IF07** — desktop/Electron build (the *un*-hardened counterpart; full capabilities).
- **IF03–IF13** — the UTL-X features that remain the allowed surface in the cloud build.

## Effort Estimate
Large. The minimal-app composition + hardening contribution + preference lockdown is the
core; backend workspace-jailing and the CSP/network work are sizable and should be staged
(app composition first — it removes the biggest risks — then sandboxing, then audits/tests).
