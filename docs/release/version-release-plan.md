# UTL-X Version Release Plan

**Generic step-by-step guide for releasing a new version of UTL-X.**  
Replace `X.Y.Z` with the actual version number (e.g., `1.0.2`).  
Replace `PREV` with the previous version (e.g., `1.0.1`).

---

## Pre-release Checklist

Before starting, verify:
- [ ] All changes committed and pushed to `main`
- [ ] Conformance suite passes: `cd conformance-suite && python3 utlx/runners/cli-runner/simple-runner.py`
- [ ] No pending cherry-picks from `development`
- [ ] Release notes drafted (what changed since vPREV)

---

## Step 1: Version Bumps (Gradle Build Files)

All modules must have the same version number.

### Root project
```
build.gradle.kts                                    version = "X.Y.Z"
```

### CLI module
```
modules/cli/build.gradle.kts                        version = "X.Y.Z"
```

### Core and library modules (all must match)
```
modules/core/build.gradle.kts                       version = "X.Y.Z"
modules/analysis/build.gradle.kts                   version = "X.Y.Z"
stdlib/build.gradle.kts                             version = "X.Y.Z"
stdlib-security/build.gradle.kts                    version = "X.Y.Z"
schema/build.gradle.kts                             version = "X.Y.Z"
```

### Format modules (all must match)
```
formats/xml/build.gradle.kts                        version = "X.Y.Z"
formats/json/build.gradle.kts                       version = "X.Y.Z"
formats/csv/build.gradle.kts                        version = "X.Y.Z"
formats/yaml/build.gradle.kts                       version = "X.Y.Z"
formats/xsd/build.gradle.kts                        version = "X.Y.Z"
formats/jsch/build.gradle.kts                       version = "X.Y.Z"
formats/avro/build.gradle.kts                       version = "X.Y.Z"
formats/protobuf/build.gradle.kts                   version = "X.Y.Z"
formats/odata/build.gradle.kts                      version = "X.Y.Z"
formats/osch/build.gradle.kts                       version = "X.Y.Z"
formats/tsch/build.gradle.kts                       version = "X.Y.Z"
```

**Quick check** (all should show the same version):
```bash
grep -r 'version = "' --include="build.gradle.kts" | grep -v kotlin | grep -v dokka | grep -v graalvm | grep -v native
```

---

## Step 2: Source Code Version Constant

```
modules/cli/src/main/kotlin/org/apache/utlx/cli/Main.kt
    Line: private const val VERSION = "X.Y.Z"
```

This is what `utlx --version` displays.

---

## Step 3: Wrapper Scripts (JAR filename references)

These scripts reference the JAR by exact filename. Update `cli-PREV.jar` to `cli-X.Y.Z.jar`:

### Root-level wrappers
```
utlx                    CLI_JAR="$SCRIPT_DIR/modules/cli/build/libs/cli-X.Y.Z.jar"
utlx.bat                set "JAR_PATH=%SCRIPT_DIR%modules\cli\build\libs\cli-X.Y.Z.jar"
utlx.ps1                $JarPath = Join-Path $ScriptDir "modules\cli\build\libs\cli-X.Y.Z.jar"
```

### Module-level wrappers
```
modules/cli/scripts/utlx         JAR="$SCRIPT_DIR/../build/libs/cli-X.Y.Z.jar"
modules/cli/scripts/utlx.bat     set JAR=%SCRIPT_DIR%..\build\libs\cli-X.Y.Z.jar
```

**Tip:** Consider changing these to use wildcards (`cli-*.jar`) to avoid updating every release. But if multiple JARs exist, wildcards may pick the wrong one.

---

## Step 4: README.md

Update all version references:

```
README.md
    Badge:          [![Version](https://img.shields.io/badge/version-X.Y.Z-green)]
    Download URL:   curl -L https://github.com/grauwen/utl-x/releases/download/vX.Y.Z/utlx-linux-x64.bin -o utlx
    Release link:   https://github.com/grauwen/utl-x/releases/tag/vX.Y.Z
    Heading:        # UTL-X CLI vX.Y.Z
    Current ver:    **Current Version**: X.Y.Z
    What's new:     ### What's in X.Y.Z
    Release status: **X.Y.Z Released**
```

**Quick find/replace:** Search for `PREV` (e.g., `1.0.1`) and replace with `X.Y.Z` (e.g., `1.0.2`). Then verify manually — not all instances should be replaced (e.g., historical references to older versions).

---

## Step 5: Installation Documentation

```
docs/getting-started/installation.md
    Release link:   https://github.com/grauwen/utl-x/releases/tag/vX.Y.Z
    macOS URL:      curl -L https://github.com/grauwen/utl-x/releases/download/vX.Y.Z/utlx-macos-arm64.bin -o utlx
    Linux URL:      curl -L https://github.com/grauwen/utl-x/releases/download/vX.Y.Z/utlx-linux-x64.bin -o utlx
    Windows link:   https://github.com/grauwen/utl-x/releases/tag/vX.Y.Z
    Version output: UTL-X CLI vX.Y.Z
    JAR reference:  modules/cli/build/libs/cli-X.Y.Z.jar
```

---

## Step 6: Other Documentation with Version References

```
docs/comparison/vs-cel.md
    Footer:         *Last updated: <month> <year> — UTL-X vX.Y.Z*

docs/getting-started/native-binary-quickstart.md
    JAR reference:  cli-X.Y.Z.jar
```

---

## Step 7: Scripts with Hardcoded JAR Paths

These test/build scripts reference the JAR filename. Update or make them version-agnostic:

```
scripts/test_stdlib_integration.sh          cli-X.Y.Z.jar
scripts/test-cli-comprehensive.sh           cli-X.Y.Z.jar  AND  "UTL-X vX.Y.Z"
scripts/cli_build_script.sh                 cli-X.Y.Z.jar
scripts/benchmark-cli.sh                    cli-X.Y.Z.jar
```

### CI workflow
```
.github/workflows/cli-ci.yml               cli-X.Y.Z.jar (line ~75)
```

### Conformance suite runner
```
conformance-suite/utlx/runners/validation-runner.py     cli-X.Y.Z.jar (line ~347)
```

---

## Step 8: Build and Test

```bash
# Rebuild CLI
./gradlew :modules:cli:jar

# Test version output
./utlx --version
# Expected: UTL-X vX.Y.Z

# Run conformance suite
cd conformance-suite && python3 utlx/runners/cli-runner/simple-runner.py
# Expected: All tests passed

# Quick smoke test
echo '{"name":"Alice"}' | ./utlx -e '.name' -r
# Expected: Alice

echo '<Order><Customer>Alice</Customer></Order>' | ./utlx --from xml --to json -e '$input'
# Expected: {"Order":{"Customer":"Alice"}}
```

---

## Step 9: Review, Commit and Tag

**Important: The commit and tag MUST be done by the person who initiated the release — not by an automated tool or AI assistant. This is a two-eyes verification pattern: the human reviews every changed file before committing.**

### 9a. Review all changes before committing

```bash
# List all changed files — should be ~34 files, all version-related
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
- Version bumped from PREV to X.Y.Z across all modules
- README badge, download URLs, and release notes updated
- Installation docs updated with new download URLs
- Wrapper scripts updated with new JAR filename
- CI scripts updated with new JAR filename
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
# macOS
chmod +x /tmp/utlx-release/utlx-macos-arm64
/tmp/utlx-release/utlx-macos-arm64 --version
echo '<Order><Customer>Alice</Customer></Order>' | /tmp/utlx-release/utlx-macos-arm64 --from xml --to json -e '$input'

# Linux (if on Linux, or skip)
chmod +x /tmp/utlx-release/utlx-linux-x64
/tmp/utlx-release/utlx-linux-x64 --version
```

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

The Chocolatey package needs the SHA256 hash for the **Windows** binary (computed in Step 11).

### If package exists:

1. Update `utlx.nuspec` version
2. Update `tools/chocolateyinstall.ps1` with new download URL and `<HASH_WINDOWS>` from Step 11
3. Pack and push:
```powershell
choco pack
choco push utlx.X.Y.Z.nupkg --source https://push.chocolatey.org/ --api-key <KEY>
```

### If package does NOT exist:

Create `utlx.nuspec`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://schemas.chocolatey.org/2010/06/nuspec">
  <metadata>
    <id>utlx</id>
    <version>X.Y.Z</version>
    <title>UTL-X</title>
    <authors>Marcel Grauwen</authors>
    <projectUrl>https://github.com/grauwen/utl-x</projectUrl>
    <description>Format-agnostic data transformation language — JSON, XML, CSV, YAML, OData</description>
    <tags>transformation json xml csv yaml data-mapping etl</tags>
  </metadata>
  <files>
    <file src="tools\**" target="tools" />
  </files>
</package>
```

With `tools/chocolateyinstall.ps1` (use `<HASH_WINDOWS>` from Step 11):
```powershell
$url = "https://github.com/grauwen/utl-x/releases/download/vX.Y.Z/utlx-windows-x64.exe"
$checksum = "<HASH_WINDOWS>"
Install-ChocolateyPackage 'utlx' 'exe' '/S' $url -Checksum $checksum -ChecksumType 'sha256'
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
| 6 | `stdlib-security/build.gradle.kts` | `version = "X.Y.Z"` |
| 7 | `schema/build.gradle.kts` | `version = "X.Y.Z"` |
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

### Should update (scripts, CI — or make version-agnostic)

| # | File | What to change |
|---|------|---------------|
| 28 | `.github/workflows/cli-ci.yml` | `cli-X.Y.Z.jar` (line ~75) |
| 29 | `scripts/test_stdlib_integration.sh` | `cli-X.Y.Z.jar` |
| 30 | `scripts/test-cli-comprehensive.sh` | `cli-X.Y.Z.jar` + `"UTL-X vX.Y.Z"` |
| 31 | `scripts/cli_build_script.sh` | `cli-X.Y.Z.jar` |
| 32 | `scripts/benchmark-cli.sh` | `cli-X.Y.Z.jar` |
| 33 | `conformance-suite/utlx/runners/validation-runner.py` | `cli-X.Y.Z.jar` |
| 34 | `docs/getting-started/native-binary-quickstart.md` | `cli-X.Y.Z.jar` |

---

## Future Improvement: Reduce Manual Steps

Consider:
1. **Single version source**: Define version once in `gradle.properties` (`utlxVersion=X.Y.Z`) and reference from all `build.gradle.kts` files
2. **Wrapper scripts use wildcards**: `cli-*.jar` instead of exact version (validate only one JAR exists)
3. **Release script**: Automate the find/replace of version strings across all files
4. **Changelog generation**: `git log vPREV..HEAD --oneline` for auto-generated release notes

---

## UTLXe Engine Release (separate from CLI)

The UTLXe production engine and utlxd daemon live on the `development` branch only. They are NOT part of the CLI release on `main`. Engine releases are tagged on `development` with a `utlxe-` prefix.

### Branch and tag strategy

```
main branch         → CLI releases      → v1.0.2, v1.0.3, ...
development branch  → UTLXe releases    → utlxe-v1.0.2, utlxe-v1.0.3, ...
                      (tagged, not merged to main)
```

### When to release UTLXe

- When engine code changes (transport, strategies, validation, health)
- When serializer fixes affect the Docker image (e.g., B14)
- When Azure/GCP Marketplace needs an updated image
- Version numbers should align with CLI when possible (both v1.0.2)

### UTLXe release steps

**1. Ensure development branch is clean and tested**

```bash
git switch development

# Run conformance suite (engine tests)
cd conformance-suite && python3 utlx/runners/cli-runner/simple-runner.py

# Run Kotlin tests
./gradlew test
```

**2. Tag on development (human reviews, human tags — two-eyes pattern)**

```bash
git tag -a utlxe-vX.Y.Z -m "UTLXe vX.Y.Z"
git push --tags
```

**3. Rebuild all JARs**

```bash
./gradlew :modules:cli:jar :modules:daemon:jar :modules:engine:jar
```

**4. Rebuild and push Docker image**

```bash
docker build --platform linux/amd64 -f deploy/docker/Dockerfile.engine -t utlxe:latest .
docker tag utlxe ghcr.io/utlx-lang/utlxe:latest
docker tag utlxe ghcr.io/utlx-lang/utlxe:vX.Y.Z
docker push ghcr.io/utlx-lang/utlxe:latest
docker push ghcr.io/utlx-lang/utlxe:vX.Y.Z
```

Note: push both `latest` and a version-specific tag. Azure Marketplace pulls `latest`, but the versioned tag provides reproducibility.

**5. Rebuild Marketplace ZIPs (if Bicep/Terraform templates changed)**

```bash
./deploy/build.sh marketplace
```

**6. Verify**

```bash
# Test Docker image
docker run --rm -p 8085:8085 ghcr.io/utlx-lang/utlxe:latest --mode http --workers 8 &
sleep 3
curl http://localhost:8085/api/health
docker stop $(docker ps -q --filter ancestor=ghcr.io/utlx-lang/utlxe:latest)
```

### Files on development that have engine-specific versions

| File | What |
|------|------|
| `modules/engine/build.gradle.kts` | `version = "X.Y.Z-SNAPSHOT"` (or release) |
| `modules/daemon/build.gradle.kts` | `version = "X.Y.Z-SNAPSHOT"` (or release) |
| `deploy/docker/Dockerfile.engine` | No version (builds from source) |

**SNAPSHOT convention:** Development uses `X.Y.Z-SNAPSHOT` for day-to-day work. When tagging a release, the SNAPSHOT suffix stays — the tag itself marks the release point. This avoids version-bump churn on the development branch.

### Docker image versioning

| Tag | When to use | Pulled by |
|-----|------------|-----------|
| `ghcr.io/utlx-lang/utlxe:latest` | Always pushed (current release) | Azure Marketplace deployments |
| `ghcr.io/utlx-lang/utlxe:vX.Y.Z` | Versioned tag for reproducibility | Customers who pin versions |

---

*Release plan created April 2026. Update this document if new version-dependent files are added.*
