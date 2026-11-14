# UTL-X Version Release Plan

**Generic step-by-step guide for releasing a new version of UTL-X.**  
Replace `X.Y.Z` with the actual version number (e.g., `1.0.2`).  
Replace `PREV` with the previous version (e.g., `1.0.1`).

---

## Pre-release Checklist

Before starting, verify:
- [ ] All feature and fix work is committed on the release branch (1.4.0 was cut from
      `development`, not `main` — the branch is whichever one carries the work)
- [ ] `./gradlew test` passes — **not just** the conformance suite, which only exercises
      the interpreted path
- [ ] Conformance suite passes: `cd conformance-suite && python3 utlx/runners/cli-runner/simple-runner.py`
- [ ] No pending cherry-picks between `development` and `main`
- [ ] Release notes drafted (what changed since vPREV), with breaking changes called out
      first — removals and tightened validation belong at the top

---

## Step 1: Version Bump (Gradle)

There is **one** place to change. The root build sets the version and hands it to every
module, so no module declares its own:

```
build.gradle.kts                                    version = "X.Y.Z"
```

```kotlin
// build.gradle.kts -- the root hands its version to every module
subprojects {
    version = rootProject.version
}
```

Module and format build files used to repeat `version = "PREV"`, and the 1.4.0 release
found all 19 of them still on the previous number. They now carry a comment pointing
back here instead. If you add a module, do **not** give it a `version =` line.

**Quick check** (every artefact should carry the new version, and nothing should be left
on the old one):
```bash
./gradlew :modules:cli:jar :modules:daemon:jar :modules:engine:jar
ls modules/cli/build/libs modules/daemon/build/libs modules/engine/build/libs
```

---

## Step 2: Source Code Version Constants

Six hard-coded constants, one per surface that reports its own version. All six must
match the Gradle version, or `--version` lies:

```
modules/cli/src/main/kotlin/com/glomidco/utlx/cli/Main.kt                 private const val VERSION
modules/engine/src/main/kotlin/com/glomidco/utlx/engine/Main.kt           private const val VERSION
modules/daemon/src/main/kotlin/com/glomidco/utlx/daemon/Main.kt           private const val VERSION
modules/daemon/src/main/kotlin/com/glomidco/utlx/daemon/mcp/McpService.kt private const val VERSION
modules/daemon/src/main/kotlin/com/glomidco/utlx/daemon/DaemonServer.kt   "version" to "X.Y.Z"
modules/daemon/src/main/kotlin/com/glomidco/utlx/daemon/rest/RestApiServer.kt  version = "X.Y.Z"
```

These feed `utlx --version`, `utlxe --version`, `utlxd --version`, the MCP handshake and
the REST `/health` payload.

**One of them is asserted by a conformance fixture** — the LSP suite checks the daemon's
reported version, so it must be bumped in the same commit:
```
conformance-suite/lsp/tests/protocol/initialization/initialize_basic.yaml    version: "X.Y.Z"
conformance-suite/lsp/README.md                                              (same snippet)
```

---

## Step 3: Wrapper Scripts — nothing to do

The launchers no longer name a version. Each resolves the most recently built jar in the
module's `build/libs/` (`cli-*.jar`, `utlxd-*.jar`), so a bump needs no edit:

```
utlx  utlx.bat  utlx.ps1  utlxd
modules/cli/scripts/utlx      modules/cli/scripts/utlx.bat
modules/daemon/scripts/utlxd  modules/daemon/scripts/utlxd.bat
```

This was the Step 3 "Tip" in earlier releases, and the reason it got done: at 1.4.0 the
pinned `./utlx` silently kept running the 1.3.0 jar, which reported a freshly registered
stdlib function as missing and sent the investigation down a blind alley. A stale jar is
indistinguishable from a real regression.

**Consequence worth knowing:** old jars are not deleted by a build, so
`modules/cli/build/libs/` accumulates them. The launchers pick the newest by modification
time, which is what you want after a build -- but run `./gradlew clean` if you ever need
to be certain which jar you are on.

---

## Step 4: README.md

```
README.md
    Badge:          [![Version](https://img.shields.io/badge/version-X.Y.Z-blue)]
    Version output: # UTL-X version X.Y.Z
    Current ver:    **Current Version**: X.Y.Z
```

The README carries no release download URLs or "What's in X.Y.Z" section -- earlier
revisions of this plan listed both, and neither exists. The wrapper-script sentence is
version-agnostic now too (see Step 3); leave it alone.

**Quick find/replace:** search for `PREV`, then verify every hit by eye — historical
references to older versions must stay as they are.

---

## Step 5: Installation Documentation

```
docs/getting-started/installation.md
    Version output: UTL-X CLI vX.Y.Z
```

The release download URLs this step used to list are not in the file; the JAR reference
is version-agnostic as of 1.4.0. Nothing else to change here.

```
docs/cli/cli_quickstart.md            cli-X.Y.Z.jar (x4, one of them a Windows .bat example)
docs/reference/language-spec.md       **Version:** X.Y.Z (x2)
test-corpora/scripts/HARNESS.md       cli-X.Y.Z.jar
conformance-suite/CONFORMANCE-SUITES.md   utlxd-X.Y.Z.jar (x2)
conformance-suite/utlxe/README.md         utlxe-X.Y.Z.jar
conformance-suite/daemon-rest-api/README.md   utlxd-X.Y.Z.jar (x3)
conformance-suite/mcp-server/README.md        utlxd-X.Y.Z.jar (x3)
conformance-suite/lsp/README.md               version: "X.Y.Z"
wrappers/go/utlxclient/README.md          utlxe-X.Y.Z.jar
wrappers/dotnet/README.md                 utlxe-X.Y.Z.jar (x2)
tools/gradle-plugin/.../UTLXSchemaPlugin.kt   KDoc plugin-version example
dockerfile.txt                            LABEL version="X.Y.Z" (x2)
```

---

## Step 6: The Book and the Website Guide

The book (`books/language/chapters/*.typ`) and the website guide
(`website/guide/*.md`) are parallel copies of the same chapters — **every edit here is
two edits**, and they drift silently if you do only one.

```
ch00-preface            "This book covers UTL-X vX.Y.Z."
ch04-getting-started    # UTL-X CLI vX.Y.Z  +  three release download/tag URLs (vX.Y.Z)
ch49-appendices         ADD a row to the version-history table -- do not overwrite the
                        previous release's row. Say what changed, and lead with
                        **Breaking** when something was removed or tightened.
```

The release URLs in ch04 point at a tag that does not exist until Step 9d, so they are
briefly dead. That is expected; do not "fix" them by reverting the version.

```
docs/comparison/vs-cel.md
    Footer:         *Last updated: <month> <year> — UTL-X vX.Y.Z*
```
Only touch that footer if you actually re-read the document — it is a review date, not a
build number. (It still reads v1.0.1.)

---

## Step 7: Scripts and CI with Hardcoded JAR Paths

Still pinned — update `PREV` to `X.Y.Z` in each:

```
scripts/test_stdlib_integration.sh          cli-X.Y.Z.jar (x4)
scripts/test-cli-comprehensive.sh           cli-X.Y.Z.jar  AND  "UTL-X vX.Y.Z"
scripts/cli_build_script.sh                 cli-X.Y.Z.jar
scripts/benchmark-cli.sh                    cli-X.Y.Z.jar
examples/utlxe/run-all-tests.sh             utlxe-X.Y.Z.jar
examples/utlxe/*.utlxp/run-test.sh          utlxe-X.Y.Z.jar (x3)
```

### CI workflows
```
.github/workflows/cli-ci.yml                cli-X.Y.Z.jar
.github/workflows/go-ci.yml                 utlxe-X.Y.Z.jar  (UTLXE_JAR_PATH)
.github/workflows/dotnet-ci.yml             utlxe-X.Y.Z.jar  (UTLXE_JAR_PATH)
.github/workflows/schema-validation.yml     cli-X.Y.Z.jar (x4)
```

### Conformance suite and daemon runners
```
conformance-suite/daemon-rest-api/runners/python-runner/run-daemon-rest-api-tests.sh
conformance-suite/daemon-rest-api/runners/python-runner/daemon-rest-api-runner.py
conformance-suite/lsp/runners/python-runner/run-lsp-tests.sh
conformance-suite/lsp/runners/python-runner/lsp-runner.py
conformance-suite/mcp-server/runners/python-runner/run-mcp-server-tests.sh
```

### Already version-agnostic — do not edit
```
conformance-suite/utlx/runners/validation-runner.py   globs cli-*.jar, newest first
```

### IDE extension defaults (devdocs-only, not shipped on main)
```
theia-extension/utlx-theia-extension/src/node/daemon/utlx-daemon-client.ts
theia-extension/utlx-theia-extension/src/node/services/auto-start-services.ts
theia-extension/utlx-theia-extension/src/node/services/service-lifecycle-manager.ts
```
All three are `UTLXD_JAR_PATH`-overridable defaults. Typecheck with
`npx tsc --noEmit -p .` after editing; the extension build itself is a separate step.

**Finding the stragglers.** `grep` here may be ugrep, which skips a file containing a NUL
byte without saying so, and most `PREV` hits in the tree belong to third-party
dependencies (`yarn.lock`, `package-lock.json`, `node_modules`, .NET `obj/`). Sweep with
Python and match on *our* artefact names rather than the bare version:

```bash
python3 - <<'EOF'
import os, re
SKIP = {'.git','build','.gradle','obj','bin','node_modules'}
PREV = '1.3.0'   # set to the version you are replacing
ours = re.compile(rf'(cli|utlxd|utlxe)-{re.escape(PREV)}|UTL-X v{re.escape(PREV)}')
for root, dirs, files in os.walk('.'):
    dirs[:] = [d for d in dirs if d not in SKIP]
    for fn in files:
        if fn in {'yarn.lock','package-lock.json'}: continue
        p = os.path.join(root, fn)
        try: txt = open(p, encoding='utf-8', errors='replace').read()
        except Exception: continue
        for i, line in enumerate(txt.splitlines(), 1):
            if ours.search(line): print(f'{p}:{i}  {line.strip()[:100]}')
EOF
```

**Deliberately left on their historical version:** `devdocs/archive/**` (an archive is a
record, not a live document), `docs/bugs-fixed/**` and `docs/features/**` (each report
describes the version it was written against), and `docs/community/roadmap.md` (its
`### v1.3.0` heading is a roadmap milestone, not a build reference).

---

## Step 8: Regenerate the Function Catalogue and IDE Index

The stdlib catalogue is generated from `@UTLXFunction` annotations, **keyed on the Kotlin
function name**. It is not derived from the dispatch table, so a function can be callable
and absent from the catalogue at the same time — which is exactly what happened at 1.4.0
when an annotated declaration was deleted while its name stayed registered as an alias.

```bash
./gradlew :stdlib:generateFunctionRegistry --rerun-tasks
python3 scripts/generate-function-docs.py      # writes devdocs/functions/
```

The first writes `stdlib/build/generated/function-registry/utlx-functions.json`, which
`tasks.jar` bundles into the stdlib jar as `/function-registry/utlx-functions.json` --
that resource is what `utlx functions` reads. The second refreshes the two checked-in IDE
artefacts, `devdocs/functions/api-reference.json` and `utlx-snippets.json`.

**These live in `devdocs/`, never `docs/`** (see commit f324ea0b). The generator wrote to
`docs/functions/` until 1.4.0, so running an older copy of it re-creates a directory the
docs-boundary rule forbids.

**Verify a removed function is gone and an aliased one is present:**
```bash
./utlx functions --format json | python3 -c "
import json,sys
d=json.load(sys.stdin); f=d if isinstance(d,list) else d.get('functions',[])
names={x['name'] for x in f if isinstance(x,dict) and 'name' in x}
print(len(names),'functions')
for n in ['<a function you removed>','<a function you aliased>']:
    print(n, 'present' if n in names else 'ABSENT')"
```

A name registered with `register(...)` is callable but **uncatalogued**; use
`registerAlias(alias, canonical, reason)` for a second name so it reaches the catalogue
and the IDE's completion list.

---

## Step 8b: Build and Test

```bash
# Rebuild everything the release tags
./gradlew :modules:cli:jar :modules:daemon:jar :modules:engine:jar

# Each must report the new version -- these read the Step 2 constants, not Gradle
./utlx --version          # UTL-X CLI vX.Y.Z
./utlxd --version         # UTL-X Daemon vX.Y.Z
java -jar modules/engine/build/libs/utlxe-X.Y.Z.jar --version   # utlxe vX.Y.Z

# Full JVM test suite -- the interpreted conformance run below does NOT cover
# the COPY and COMPILED strategies; :modules:engine:test is what does.
./gradlew test

# Conformance suite (interpreted path)
cd conformance-suite && python3 utlx/runners/cli-runner/simple-runner.py

# Quick smoke test
echo '{"name":"Alice"}' | ./utlx -e '.name' -r
# Expected: Alice

echo '<Order><Customer>Alice</Customer></Order>' | ./utlx --from xml --to json -e '$input'
# Expected: {"Order":{"Customer":"Alice"}}
```

If the native binary is part of the release, build and smoke-test it too — a change can
pass on the JVM and fail under GraalVM (reflection, resources, locale):
```bash
./gradlew :modules:cli:nativeCompile
```

---

## Step 9: Review, Commit and Tag

**Important: The commit and tag MUST be done by the person who initiated the release — not by an automated tool or AI assistant. This is a two-eyes verification pattern: the human reviews every changed file before committing.**

### 9a. Review all changes before committing

```bash
# List all changed files -- all of them should be version, catalogue or doc changes
git diff --stat

# Review actual content — verify only version strings changed, nothing else
git diff

# Check for anything unexpected
git status
```

**What you should see:** Only version string replacements (`PREV` → `X.Y.Z`) in gradle files, source code, wrapper scripts, docs, and CI scripts. No logic changes, no new features, no deletions.

**If anything looks wrong:** Fix it before proceeding. Do NOT commit a release with unexpected changes.

### 9b. Commit

```bash
git add -A
git commit -m "vX.Y.Z release — version bump, docs, and download URLs updated"
```

**Proposed commit message format:**
```
vX.Y.Z release — version bump, docs, and download URLs updated

Changes:
- Version bumped from PREV to X.Y.Z in the root build (every module inherits it)
- Six in-source VERSION constants bumped (cli, engine, daemon x4 surfaces)
- Function catalogue and IDE index regenerated
- README, installation, CLI quickstart, language spec, book and website guide updated
- Scripts, CI workflows and conformance runners updated with the new JAR filename
```

### 9c. Run conformance suite (post-commit verification)

```bash
cd conformance-suite && python3 utlx/runners/cli-runner/simple-runner.py
```

All tests must pass. If any fail, fix and amend the commit before tagging.

### 9d. Tag and push

```bash
git tag -a vX.Y.Z -m "UTL-X vX.Y.Z"
git push
git push --tags
```

**The tag triggers nothing automatically** — the release workflow is manually dispatched (Step 10). This gives you a chance to verify the tag is on the right commit before building binaries.

---

## Step 10: Trigger GitHub Actions Release Workflow

1. Go to: https://github.com/grauwen/utl-x/actions/workflows/release.yml
2. Click **"Run workflow"**
3. Enter version: `vX.Y.Z`
4. Click **"Run workflow"**

This builds GraalVM native binaries for:
- Linux x64 (`utlx-linux-x64`)
- macOS ARM64 (`utlx-macos-arm64`)
- Windows x64 (`utlx-windows-x64.exe`)

Takes ~15-20 minutes.

---

## Step 11: Download, Verify and Compute SHA256 Checksums

After the GitHub Actions workflow completes, download **all three binaries** and compute their SHA256 checksums. These are needed for Homebrew, Chocolatey, and integrity verification.

```bash
# Download all binaries
mkdir -p /tmp/utlx-release
gh release download vX.Y.Z -D /tmp/utlx-release/

# Compute SHA256 for ALL binaries
shasum -a 256 /tmp/utlx-release/utlx-linux-x64
shasum -a 256 /tmp/utlx-release/utlx-macos-arm64
shasum -a 256 /tmp/utlx-release/utlx-windows-x64.exe
```

Save the output — you'll need these hashes for Homebrew and Chocolatey:
```
<HASH_LINUX>   utlx-linux-x64
<HASH_MACOS>   utlx-macos-arm64
<HASH_WINDOWS>  utlx-windows-x64.exe
```

### Test each binary

```bash
# macOS (if on macOS)
chmod +x /tmp/utlx-release/utlx-macos-arm64
UTLX=/tmp/utlx-release/utlx-macos-arm64

# Linux (if on Linux)
# chmod +x /tmp/utlx-release/utlx-linux-x64
# UTLX=/tmp/utlx-release/utlx-linux-x64
```

### Native binary validation checklist

Run ALL of these against the native binary. GraalVM native-image can silently exclude
resources, reflection targets, or serialization config that the JVM version handles fine.
A partial failure (like B17 — missing XML Security bundle) blocks ALL transformations.

```bash
# 1. Version check
$UTLX --version
# Expected: UTL-X CLI vX.Y.Z

# 2. Basic JSON transformation (tests core interpreter)
echo '{"name": "Alice"}' | $UTLX -e 'concat("Hello, ", $input.name)'
# Expected: "Hello, Alice"

# 3. XML input (tests XML parser)
echo '<Order><Customer>Alice</Customer></Order>' | $UTLX --from xml --to json -e '$input'
# Expected: {"Order":{"Customer":"Alice"}}

# 4. CSV input (tests CSV parser)
printf 'name,age\nAlice,30' | $UTLX --from csv -e '$input'
# Expected: [{"name":"Alice","age":30}]

# 5. YAML input (tests YAML parser)
echo 'name: Alice' | $UTLX --from yaml -e '$input.name'
# Expected: "Alice"

# 6. XML canonicalization (tests Apache XML Security — B17 regression)
echo '<doc><b/><a/></doc>' | $UTLX --from xml -e 'c14n($input)'
# Expected: canonical XML string (not null, not crash)

# 7. Hash function (tests crypto libraries in native image)
echo '{"data": "hello"}' | $UTLX -e 'sha256($input.data)'
# Expected: SHA-256 hex string

# 8. Date function (tests java.time in native image)
echo '{"date": "2026-05-01"}' | $UTLX -e 'parseDate($input.date, "yyyy-MM-dd")'
# Expected: date value (not crash)

# 9. Base64 (tests encoding in native image)
echo '{"msg": "Hello"}' | $UTLX -e 'base64Encode($input.msg)'
# Expected: "SGVsbG8="

# 10. Schema format input — XSD (tests F08 enrichment in native)
echo '<?xml version="1.0"?><xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"><xs:element name="test" type="xs:string"/></xs:schema>' | $UTLX --input-format xsd -e '$input["%_diagnostics"]["%_status"]'
# Expected: "complete"

# 11. Newline separators work (tests F02 parser change)
echo '{"x": 10}' | $UTLX -e '{
let a = $input.x
let b = a * 2
result: a + b
}'
# Expected: {"result":30}

# 12. User-defined function (tests function definition in native)
echo '{"x": 5}' | $UTLX -e 'function Double(n) { n * 2 }
Double($input.x)'
# Expected: 10
```

**If ANY test fails:** the native binary has a GraalVM configuration gap. Do NOT release until fixed.
Common causes: missing resource bundles, missing reflection config, missing serialization config.
The JVM version (`java -jar cli-X.Y.Z.jar`) is the fallback — it always works.

### Verify release page

```bash
gh release view vX.Y.Z

# Expected: 3 assets listed
# - utlx-linux-x64
# - utlx-macos-arm64
# - utlx-windows-x64.exe
```

---

## Step 12: Update Homebrew Tap

The Homebrew formula needs SHA256 hashes for **both macOS and Linux** binaries.

### If tap repo exists (`github.com/grauwen/homebrew-utlx`):

1. Update `Formula/utlx.rb` with:
   - New version number
   - New download URLs (vX.Y.Z)
   - New SHA256 hashes (both platforms)

2. Push to tap repo

3. Test:
```bash
brew update
brew upgrade utlx
utlx --version
```

### If tap repo does NOT exist:

Create `github.com/grauwen/homebrew-utlx` with a `Formula/utlx.rb` file:

```ruby
class Utlx < Formula
  desc "Format-agnostic data transformation language"
  homepage "https://github.com/grauwen/utl-x"
  version "X.Y.Z"

  on_macos do
    on_arm do
      url "https://github.com/grauwen/utl-x/releases/download/vX.Y.Z/utlx-macos-arm64"
      sha256 "<HASH_MACOS>"
    end
  end

  on_linux do
    on_intel do
      url "https://github.com/grauwen/utl-x/releases/download/vX.Y.Z/utlx-linux-x64"
      sha256 "<HASH_LINUX>"
    end
  end

  def install
    binary_name = "utlx-macos-arm64"
    binary_name = "utlx-linux-x64" if OS.linux?
    bin.install binary_name => "utlx"
  end

  test do
    assert_match "UTL-X v#{version}", shell_output("#{bin}/utlx --version")
  end
end
```

Then:
```bash
brew tap grauwen/utlx
brew install utlx
```

---

## Step 13: Update Chocolatey (Windows)

The Chocolatey package repo is at `github.com/grauwen/chocolatey-utlx`.
It contains a GitHub Actions workflow that packs and pushes to Chocolatey.org automatically.

### 13a. Update two files in the Chocolatey repo

**`utlx.nuspec`** — update version and release notes:
```xml
<version>X.Y.Z</version>
<releaseNotes>https://github.com/grauwen/utl-x/releases/tag/vX.Y.Z</releaseNotes>
```

**`tools/chocolateyinstall.ps1`** — update URL, checksum, and version message:
```powershell
$url = 'https://github.com/grauwen/utl-x/releases/download/vX.Y.Z/utlx-windows-x64.exe'
$checksum = '<HASH_WINDOWS>'   # SHA256 from Step 11
...
Write-Host "UTL-X vX.Y.Z installed successfully!"
```

Both files can be updated via the GitHub API (no need to clone):
```bash
# Update nuspec
SHA=$(gh api repos/grauwen/chocolatey-utlx/contents/utlx.nuspec --jq '.sha')
gh api repos/grauwen/chocolatey-utlx/contents/utlx.nuspec \
  -X PUT -f message="Update to vX.Y.Z" -f content="$(base64 < nuspec.xml)" -f sha="$SHA"

# Update install script
SHA=$(gh api repos/grauwen/chocolatey-utlx/contents/tools/chocolateyinstall.ps1 --jq '.sha')
gh api repos/grauwen/chocolatey-utlx/contents/tools/chocolateyinstall.ps1 \
  -X PUT -f message="Update install script to vX.Y.Z" -f content="$(base64 < install.ps1)" -f sha="$SHA"
```

### 13b. Trigger the publish workflow

```bash
gh workflow run publish.yml --repo grauwen/chocolatey-utlx
```

This runs on `windows-latest` and:
1. `choco pack utlx.nuspec` → creates `utlx.X.Y.Z.nupkg`
2. `choco push` → uploads to chocolatey.org using `CHOCOLATEY_API_KEY` secret

### 13c. Verify

The Chocolatey moderation is automated for existing packages (~1 hour). Check status at:
- https://community.chocolatey.org/packages/utlx
- Or: `gh run list --repo grauwen/chocolatey-utlx --limit 3`

After approval:
```powershell
choco upgrade utlx
utlx --version
# Expected: UTL-X CLI vX.Y.Z
```

### SHA256 usage summary

| Binary | Hash variable | Used by |
|--------|--------------|---------|
| `utlx-linux-x64` | `<HASH_LINUX>` | Homebrew (Linux) |
| `utlx-macos-arm64` | `<HASH_MACOS>` | Homebrew (macOS) |
| `utlx-windows-x64.exe` | `<HASH_WINDOWS>` | Chocolatey |

---

## Step 14: Update Docker Image (if UTLXe changed)

Only needed if the engine code changed (not just CLI):

```bash
docker build --platform linux/amd64 -f deploy/docker/Dockerfile.engine -t utlxe:latest .
docker tag utlxe ghcr.io/utlx-lang/utlxe:latest
docker push ghcr.io/utlx-lang/utlxe:latest
```

---

## Step 15: Post-Release Verification

```bash
# GitHub Release
gh release view vX.Y.Z

# Homebrew (if updated)
brew tap grauwen/utlx
brew install utlx
utlx --version

# Chocolatey (if updated)
choco install utlx
utlx --version

# Docker (if updated)
docker pull ghcr.io/utlx-lang/utlxe:latest
docker run --rm ghcr.io/utlx-lang/utlxe:latest --version

# Direct download
curl -L https://github.com/grauwen/utl-x/releases/download/vX.Y.Z/utlx-macos-arm64 -o /tmp/utlx
chmod +x /tmp/utlx
/tmp/utlx --version
```

---

## Step 16: Announce

- [ ] Update Azure Marketplace listing description (if version mentioned)
- [ ] Update GCP Marketplace listing (when live)
- [ ] Post on GitHub Discussions (if enabled)

---

## Summary: All Files to Update

### Must update every release (version number changes)

| # | File | What to change |
|---|------|---------------|
| 1 | `build.gradle.kts` | `version = "X.Y.Z"` |
| 2 | `modules/cli/build.gradle.kts` | `version = "X.Y.Z"` |
| 3 | `modules/core/build.gradle.kts` | `version = "X.Y.Z"` |
| 4 | `modules/analysis/build.gradle.kts` | `version = "X.Y.Z"` |
| 5 | `stdlib/build.gradle.kts` | `version = "X.Y.Z"` |
| 6 | `schema/build.gradle.kts` | `version = "X.Y.Z"` |
| 8 | `formats/xml/build.gradle.kts` | `version = "X.Y.Z"` |
| 9 | `formats/json/build.gradle.kts` | `version = "X.Y.Z"` |
| 10 | `formats/csv/build.gradle.kts` | `version = "X.Y.Z"` |
| 11 | `formats/yaml/build.gradle.kts` | `version = "X.Y.Z"` |
| 12 | `formats/xsd/build.gradle.kts` | `version = "X.Y.Z"` |
| 13 | `formats/jsch/build.gradle.kts` | `version = "X.Y.Z"` |
| 14 | `formats/avro/build.gradle.kts` | `version = "X.Y.Z"` |
| 15 | `formats/protobuf/build.gradle.kts` | `version = "X.Y.Z"` |
| 16 | `formats/odata/build.gradle.kts` | `version = "X.Y.Z"` |
| 17 | `formats/osch/build.gradle.kts` | `version = "X.Y.Z"` |
| 18 | `formats/tsch/build.gradle.kts` | `version = "X.Y.Z"` |
| 19 | `modules/cli/src/main/kotlin/.../Main.kt` | `VERSION = "X.Y.Z"` |
| 20 | `utlx` (shell wrapper) | `cli-X.Y.Z.jar` |
| 21 | `utlx.bat` (Windows wrapper) | `cli-X.Y.Z.jar` |
| 22 | `utlx.ps1` (PowerShell wrapper) | `cli-X.Y.Z.jar` |
| 23 | `modules/cli/scripts/utlx` | `cli-X.Y.Z.jar` |
| 24 | `modules/cli/scripts/utlx.bat` | `cli-X.Y.Z.jar` |
| 25 | `README.md` | Badge, URLs, heading, version text |
| 26 | `docs/getting-started/installation.md` | URLs, version output, JAR path |
| 27 | `docs/comparison/vs-cel.md` | Footer version |
| 28 | `QUICKSTART.md` | Heading version |
| 29 | `docs/examples/jq-to-utlx-examples.md` | Download URL |

### Should update (scripts, CI — or make version-agnostic)

| # | File | What to change |
|---|------|---------------|
| 30 | `.github/workflows/cli-ci.yml` | `cli-X.Y.Z.jar` (line ~75) |
| 31 | `scripts/test_stdlib_integration.sh` | `cli-X.Y.Z.jar` |
| 32 | `scripts/test-cli-comprehensive.sh` | `cli-X.Y.Z.jar` + `"UTL-X vX.Y.Z"` |
| 33 | `scripts/cli_build_script.sh` | `cli-X.Y.Z.jar` |
| 34 | `scripts/benchmark-cli.sh` | `cli-X.Y.Z.jar` |
| 35 | `conformance-suite/utlx/runners/validation-runner.py` | `cli-X.Y.Z.jar` |
| 36 | `docs/getting-started/native-binary-quickstart.md` | `cli-X.Y.Z.jar` |

---

## Future Improvement: Reduce Manual Steps

Consider:
1. **Single version source**: Define version once in `gradle.properties` (`utlxVersion=X.Y.Z`) and reference from all `build.gradle.kts` files
2. **Wrapper scripts use wildcards**: `cli-*.jar` instead of exact version (validate only one JAR exists)
3. **Release script**: Automate the find/replace of version strings across all files
4. **Changelog generation**: `git log vPREV..HEAD --oneline` for auto-generated release notes

---

*Release plan created April 2026. Update this document if new version-dependent files are added.*
