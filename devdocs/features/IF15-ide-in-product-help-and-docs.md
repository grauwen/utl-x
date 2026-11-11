# IF15: IDE — In-Product Help & Documentation (Help menu, Getting Started)

**Status:** Proposed
**Priority:** Medium-High (essential for a hosted offering, where users can't see the repo/book)
**Created:** June 2026
**Component:** IDE stack (Theia extension) — Help menu, a Welcome/Getting-Started view, a Help content view
**Depends on:** existing docs/book as content source; pairs with IF14 (cloud build) and the feature set IF08–IF13
**Effort:** Medium

> **Scope note (taxonomy):** "IF" = the IDE stack. This is **user-facing help content +
> surfaces inside the IDE**, distinct from IF14 (security hardening). They pair: in the
> hardened cloud build the in-product Help becomes the user's *primary* reference.

> **Design decisions captured here (not yet implemented):**
> - Users — especially **cloud** users who never see the GitHub repo or the PDF book — need
>   **in-IDE help**: what UTL-X is, how the panels / Message Contract mode / AI assist /
>   bundles work, a language reference, and runnable examples.
> - Surfaces: a product **Help menu**, a **Getting Started / Welcome** view, and an in-IDE
>   **Help content** view (offline-bundled markdown), plus **contextual** links from existing
>   `?`/info affordances.
> - **Reuse the existing corpus** (the book *UTL-X One Language All Formats* + `docs/`) as the
>   help source; curate a bundled subset; keep in sync rather than re-authoring.

---

## Summary

The IDE today has no first-class help: knowledge lives in the repo, the `docs/` tree, and
the PDF books — none reachable from inside the product, and entirely invisible to a hosted
user. IF15 adds **in-product help**: a branded **Help menu**, a **Getting Started** welcome
page, and a **Help view** that renders curated, offline-bundled docs (UTL-X language,
panels, Message Contract mode, AI assist, bundles), with **contextual** entry points from
the existing `?`/info buttons. It turns the IDE into something a new user — local or cloud —
can learn without leaving it.

## Problem

- New users have **no in-IDE guidance** — they must find the repo/book externally.
- **Cloud users can't** reach the repo or PDFs at all; the hardened build (IF14) removes
  external escape hatches, making in-product help the *only* reference.
- The default Theia **Help menu** points at Eclipse Theia (e.g. "Report Issue"), not the
  UTL-X product.
- Rich existing content (the book, `docs/features`, `examples/`) is **not surfaced** in the
  product.

## Goals

- A **product Help menu**: Getting Started, UTL-X Language Reference, Examples, Keyboard
  Shortcuts, About, Support/Docs — with Theia's default (Theia-branded) items replaced.
- A **Getting Started / Welcome** view (quick start, open an example, key concepts, links).
- A **Help content view** rendering curated **offline-bundled** markdown (works in cloud).
- **Contextual help**: existing `?`/info affordances (AI assist, panels, coverage) deep-link
  to the relevant help topic.
- Content **sourced from the book + `docs/`**, curated and kept in sync (not re-authored).

## Non-Goals

- Re-writing the language documentation — IF15 *surfaces/curates* existing content.
- A full LMS/tutorial engine — Getting Started is a curated page, not interactive courseware
  (could come later).
- Security hardening — that's IF14.

## Design

### Help menu (product-branded)
Replace/augment Theia's Help menu with product entries (CommandContribution + MenuContribution):
- **Getting Started** → opens the Welcome view.
- **UTL-X Language Reference** → Help view (or the bundled language reference the MCP already
  uses, `utlx-language-reference.md`).
- **Examples** → opens an example bundle / the MCM examples (`examples/mcm`, `examples/utlxe`).
- **Keyboard Shortcuts** → Theia's keybindings view.
- **About UTL-X** → product/version/about.
- **Documentation / Support** → in-IDE docs (cloud) or external links (desktop).
- Remove Theia-branded defaults (e.g. "Report Issue" → Eclipse) in favor of product links.

### Getting Started / Welcome view
A curated landing page (like VS Code's welcome): what UTL-X is, the 3-panel flow,
Execution vs **Message Contract** mode, AI assist, **bundles** (IF03), and "Open an example"
actions. Shown on first run; reachable from Help.

### Help content view (offline-bundled)
A `HelpViewWidget` (Webview or ReactWidget) that renders **curated markdown bundled with the
IDE** (so it works offline / in the locked cloud build). Topics: language basics, panels,
MCM + coverage (IF11), AI assist (IF08/IF10), bundles + `.utlxp`/`.utlar` (IF03/EF09),
config (`transform.yaml`/`engine.yaml`, the `docs/api/config` schemas).

### Contextual help
The existing `?`/info buttons (AI dialog, coverage panel, input "What is this?", etc.)
deep-link into the Help view at the relevant topic — turning scattered affordances into a
coherent help system.

### Content sourcing & sync
Curate a **subset** of the book + `docs/` into a bundled help corpus (a build step copies
selected markdown into the extension, like `mcp-server` copies the language reference). Keep
a manifest of sourced files so updates can be re-synced; avoid forking the prose.

### Cloud vs desktop (ties to IF14)
- **Cloud:** Help is the primary reference; all content **bundled/offline**; external links
  (GitHub/issues) replaced with product support.
- **Desktop:** may additionally link out to the hosted docs / repo / book.

## Implementation Notes
- `HelpMenuContribution` (commands + Help menu items), `WelcomeViewWidget`, `HelpViewWidget`.
- A build step copying curated `docs/`+book markdown into the extension (mirrors
  `mcp-server` copy-assets); a small content manifest for sync.
- Contextual deep-links: a `openHelp(topicId)` command the `?`/info affordances call.
- About/version surfaced from package metadata.

## Acceptance Criteria
- A product **Help menu** with Getting Started, Language Reference, Examples, Keyboard
  Shortcuts, About, Docs/Support; Theia-branded defaults removed.
- A **Getting Started** view with "open an example" actions, shown on first run.
- A **Help view** renders bundled docs **offline** (works in the IF14 cloud build).
- `?`/info affordances deep-link to the relevant help topic.
- Help content is **bundled** (no repo/network needed) and traceable to its `docs/`/book source.

## Testing
- **Manual:** Help menu items open the right surfaces; Getting Started actions work; Help
  view renders offline; contextual links land on the right topic.
- **Build test:** the curated help corpus is bundled into the extension (present in `dist`).
- **Cloud:** verify Help works with no network / in the hardened build (IF14).

## Related
- **IF14** — hardened cloud build; makes in-product Help the primary reference.
- **IF08 / IF10 / IF11** — features whose `?`/info affordances deep-link into Help.
- **IF03 / EF09** — bundles + `.utlxp`/`.utlar`, a key Getting-Started topic.
- `docs/` + the book (*UTL-X One Language All Formats*) — the help content source of truth.

## Effort Estimate
Medium. Help menu + Getting Started view + a markdown Help view + the content-bundling build
step (~2 wk); contextual deep-links + curation/sync pass (~1 wk). Curating *which* content
to bundle is the main ongoing effort.
