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

## Step 9: Commit and Tag

```bash
git add -A
git commit -m "vX.Y.Z release"
git tag -a vX.Y.Z -m "UTL-X vX.Y.Z"
git push
git push --tags
```

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

## Step 11: Verify Release

```bash
# Check release was created
gh release view vX.Y.Z

# Expected: 3 assets listed
# - utlx-linux-x64
# - utlx-macos-arm64
# - utlx-windows-x64.exe
```

### Download and test each binary

```bash
# macOS
gh release download vX.Y.Z -p "utlx-macos-arm64" -D /tmp/
chmod +x /tmp/utlx-macos-arm64
/tmp/utlx-macos-arm64 --version
echo '<Order><Customer>Alice</Customer></Order>' | /tmp/utlx-macos-arm64 --from xml --to json -e '$input'
```

---

## Step 12: Update Homebrew Tap

### If tap repo exists (`github.com/grauwen/homebrew-utlx`):

1. Download the macOS binary and compute SHA256:
```bash
shasum -a 256 /tmp/utlx-macos-arm64
```

2. Update the formula in the tap repo with:
   - New version number
   - New download URL
   - New SHA256 hash

3. Push to tap repo

4. Test:
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
      sha256 "<SHA256_HASH>"
    end
  end

  on_linux do
    on_intel do
      url "https://github.com/grauwen/utl-x/releases/download/vX.Y.Z/utlx-linux-x64"
      sha256 "<SHA256_HASH>"
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

### If package exists:

1. Update `utlx.nuspec` version
2. Update download URL and checksum
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

With `tools/chocolateyinstall.ps1`:
```powershell
$url = "https://github.com/grauwen/utl-x/releases/download/vX.Y.Z/utlx-windows-x64.exe"
$checksum = "<SHA256_HASH>"
Install-ChocolateyPackage 'utlx' 'exe' '/S' $url -Checksum $checksum -ChecksumType 'sha256'
```

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

*Release plan created April 2026. Update this document if new version-dependent files are added.*
