# RENAME: Package namespace migration off `org.apache.*` (UTL-X does not belong to the Apache Software Foundation)

> **Status:** proposal / decision needed. Cross-cutting build & packaging refactor (not an EF/IF/F
> language feature — hence the descriptive name rather than a numbered prefix).
>
> **Type:** breaking change (package names + published Maven coordinates). Must ship with a
> minor/major version bump, **not** a patch release.
>
> **Pairs with / overlaps:** B25 (native-image stdlib bug — its `--initialize-at-build-time`,
> `reflect-config.json`, and `Class.forName` strings all hardcode `org.apache.utlx` and get rewritten
> here anyway). The B25 **bug fix must ship first** as a patch; this rename follows separately.

## Summary

Every UTL-X module currently lives under the **`org.apache.utlx.*`** package namespace and is
published with Maven `group = "org.apache.utlx"` (`build.gradle.kts:6`). `org.apache.*` is the
reverse-DNS namespace of the **Apache Software Foundation (ASF)**. UTL-X is **not** an ASF project,
so this namespace is incorrect and should be migrated to a namespace derived from a domain the
project actually controls.

## Problem

Using `org.apache.*` for a non-ASF project is:

1. **Misleading.** It implies ASF stewardship/affiliation that does not exist. (A user raised exactly
   this on inspecting the package names.)
2. **A trademark/branding concern.** The ASF actively protects the "Apache" mark and its namespace;
   shipping public binaries and Maven artifacts under `org.apache.utlx` is the kind of usage the ASF
   asks projects to stop.
3. **Incorrect published coordinates.** `group = "org.apache.utlx"` claims a Maven groupId the project
   does not own, which also blocks ever publishing to Maven Central under a verified namespace.

## Decision 1 — target namespace (apache → glomidco) — **DECIDED**

**Chosen: `com.glomidco.utlx`** (reverse-DNS of **glomidco.com**, the steward company).

The full migration is therefore `org.apache.utlx` → **`com.glomidco.utlx`**, with the published
Maven `group` becoming **`com.glomidco.utlx`**.

Rationale: Glomidco is the named owner/steward; the company domain is owned and provable (which also
satisfies Maven Central groupId verification), and there is **no legal risk** in naming the project
under a domain the company controls — it removes the Apache-namespace exposure rather than adding any.

Considered and not chosen: `org.utlxlang` (from utlx-lang.org) — brand-neutral and equally valid,
but the decision is to assert Glomidco ownership directly.

> This is the one effectively-irreversible choice (it becomes the published Maven
> groupId/artifact coordinates). Now locked to **`com.glomidco.utlx`**.

The rest of this document uses **`com.glomidco.utlx`** as the target root (directory path
`com/glomidco/utlx`).

## Scope (measured)

- **511 files**, **~1,976 occurrences** of the exact string `org.apache.utlx`.
  - 409 `.kt`, 66 `.md`, 21 `.kts`, plus `.sh` / `.xml` / `.proto` / `.json` / `.go` / `.py`.
- **Directory tree moves** in every module: `src/main/kotlin/org/apache/utlx/…` →
  `src/main/kotlin/com/glomidco/utlx/…` (and `src/test/…`).
- **Build coordinates:** `group = "org.apache.utlx"` in the root and per-module `build.gradle.kts`.
- **Native-image config (CLI):**
  - `--initialize-at-build-time=…,org.apache.utlx.stdlib` (`modules/cli/build.gradle.kts:88`).
  - `reflect-config.json`, `resource-config.json`, `serialization-config.json` under
    `modules/cli/src/main/resources/META-INF/native-image/` (every `org.apache.utlx.*` entry).
- **Reflection strings:** `Class.forName("org.apache.utlx.…")` / `getMethod(...)` call sites in the
  interpreter and elsewhere (these are string literals — they will *not* be caught by an
  import-only rewrite; they must be migrated explicitly).
- **Docs & book:** 66 `.md` plus any `book/` references.

### Hard constraint — do NOT touch genuine Apache dependencies

The codebase legitimately depends on real ASF libraries that **must remain `org.apache.*`**:
`org.apache.{arrow, avro, camel, hadoop, parquet, santuario, tomcat, velocity, xerces, xml}` (and
`org.xml.sax`). The migration must anchor on the **exact** string **`org.apache.utlx`** — never a
blanket `org.apache` replace.

## Migration plan (mechanical, scripted)

1. **Namespace frozen** (Decision 1): `com.glomidco.utlx`.
2. **Scripted text replace** of `org.apache.utlx` → `com.glomidco.utlx` across tracked source
   (anchored on the full string; exclude `build/`, `node_modules/`). Note the `utlx` segment is
   shared, so this is a direct one-to-one swap of the `org.apache` prefix. Use `git mv` for directory
   moves so history is preserved.
3. **Move package directories** to match (`org/apache/utlx` → `com/glomidco/utlx`) in `main` and
   `test` source roots of every module.
4. **Update build coordinates** (`group`) and any artifact-name references.
5. **Update native-image config** (the four `META-INF/native-image/*.json` + `native-image.properties`
   + the `initialize-at-build-time` arg) — and fold in the B25 fixes while here (drop
   `org.apache.utlx.stdlib` from build-time init or make it reachable; fix the `UTLXFunction`
   reflect-config entry).
6. **Update reflection string literals** (`Class.forName`, `getMethod`) explicitly.
7. **Update docs/book** references.
8. **Clean build all modules** + JVM smoke test + **run the 467-test conformance suite** (it must stay
   green). Rebuild the **native binary** and verify `parseNumber`/`upper`/`sum` resolve.

## Decision 2 — branching & release strategy (main vs development)

**Yes — sequence this deliberately; do not do it twice or inline.**

- **Patch first, rename second.** Ship the **B25 parseNumber native fix** as a patch (cherry-pick to
  `main`, release) *before* starting the rename, so a critical bug fix isn't entangled with ~2,000
  lines of churn.
- **One rename branch, cut from the integration branch** (today: `feature/utlxd`; long-lived
  integration → `main`). Do the rename **once** on that branch, then promote — do **not** perform the
  rename separately on `main` and on the dev branch (that guarantees divergent, conflicting renames).
- **It collides with every open branch.** Because nearly every file's import lines change, any
  in-flight feature branch will conflict massively. Therefore:
  1. Merge/land outstanding feature branches into the integration branch **first**.
  2. Apply the rename in **one atomic commit/branch**.
  3. **Rebase** any unavoidable remaining branches onto the post-rename state immediately (don't let
     them age).
- **Release coupling.** The rename is breaking for consumers (imports + groupId). Land it with a
  **minor/major version bump** and call it out in the changelog/migration notes. Provide a short
  consumer migration guide (find/replace `org.apache.utlx` → `com.glomidco.utlx`, update the Maven
  groupId to `com.glomidco.utlx`).
- **Tooling note.** Downstream artifacts that reference the coordinates (VS Code / Theia extensions,
  Gradle plugin `tools/gradle-plugin`, SDKs under EF22, Dapr/proto packaging) must bump to the new
  groupId in the same release.

## Risks

- **String-literal reflection misses** → runtime `ClassNotFoundException`. Mitigate: grep for the old
  string across *all* file types after the rewrite; expect **zero** `org.apache.utlx` hits outside
  genuine-Apache-dep contexts.
- **Native-image breakage** if config files aren't migrated in lockstep (this is also the B25 surface).
- **Merge-conflict storm** with open branches (see Decision 2).
- **Accidental rename of real Apache deps** (mitigated by anchoring on `org.apache.utlx`).

## Verification / done criteria

- `grep -rn 'org\.apache\.utlx' .` (excluding `build/`, `node_modules/`) → **0** matches.
- Genuine `org.apache.{arrow,avro,…,xml}` references → **unchanged**.
- All modules build clean; **conformance suite 467/467 green**.
- Native binary rebuilt; `parseNumber`, `upper`, `sum` all resolve (also closes B25 (i)/(ii)).
- Published artifacts carry the new groupId; migration note in the release.

## Out of scope

- B25's *behavioural* fix is its own patch; this doc only inherits the config/string edits that the
  rename touches anyway.
- No functional/API changes — names only.
