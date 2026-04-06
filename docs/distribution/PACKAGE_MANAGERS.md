# UTL-X Package Manager Distribution Guide

This guide describes how to distribute UTL-X native binaries (built with GraalVM) via **Homebrew** (macOS/Linux) and **Chocolatey** (Windows).

---

## Prerequisites

Before setting up either package manager, you need the following in place:

### 1. GitHub Releases with Native Binaries

All package managers need a stable, versioned download URL. Create a GitHub Release in `grauwen/utl-x` and attach the following binary tarballs/zips:

| File | Platform |
|---|---|
| `utlx-<version>-macos-arm64.tar.gz` | macOS Apple Silicon |
| `utlx-<version>-macos-x86_64.tar.gz` | macOS Intel |
| `utlx-<version>-linux-x86_64.tar.gz` | Linux x86_64 |
| `utlx-<version>-linux-arm64.tar.gz` | Linux ARM64 |
| `utlx-<version>-windows-x86_64.zip` | Windows x86_64 |

Each archive should contain the `utlx` binary (or `utlx.exe` on Windows) at its root.

### 2. Compute SHA256 Checksums

Both Homebrew and Chocolatey require SHA256 checksums to verify downloads.

**macOS/Linux:**
```bash
shasum -a 256 utlx-<version>-macos-arm64.tar.gz
shasum -a 256 utlx-<version>-macos-x86_64.tar.gz
shasum -a 256 utlx-<version>-linux-x86_64.tar.gz
shasum -a 256 utlx-<version>-linux-arm64.tar.gz
shasum -a 256 utlx-<version>-windows-x86_64.zip
```

**Windows (PowerShell):**
```powershell
Get-FileHash utlx-<version>-windows-x86_64.zip -Algorithm SHA256
```

Keep these hashes ready — you will need them in the steps below.

---

## Part 1: Homebrew (macOS and Linux)

Homebrew distributes software through **taps** — GitHub repositories containing Ruby formula files. The naming convention is strict: the repository must be named `homebrew-<tapname>`.

### Step 1: Create the Tap Repository

1. Go to [github.com/new](https://github.com/new) and create a new **public** repository under the `grauwen` account.
2. Name it exactly: **`homebrew-utlx`**
3. Add a short description, e.g. _"Homebrew tap for UTL-X"_
4. Initialize with a `README.md`

This naming means users can tap it with:
```bash
brew tap grauwen/utlx
```

### Step 2: Create the Formula Directory

Clone the new repository locally and create the required directory structure:

```bash
git clone https://github.com/grauwen/homebrew-utlx.git
cd homebrew-utlx
mkdir Formula
```

### Step 3: Write the Formula

Create the file `Formula/utlx.rb` with the following content. Replace `<version>` and each `<sha256-...>` with the actual values from your release.

```ruby
class Utlx < Formula
  desc "Universal Transformation Language Extended - format-agnostic transformation language"
  homepage "https://github.com/grauwen/utl-x"
  version "<version>"
  license "AGPL-3.0"

  on_macos do
    on_arm do
      url "https://github.com/grauwen/utl-x/releases/download/v#{version}/utlx-#{version}-macos-arm64.tar.gz"
      sha256 "<sha256-macos-arm64>"
    end
    on_intel do
      url "https://github.com/grauwen/utl-x/releases/download/v#{version}/utlx-#{version}-macos-x86_64.tar.gz"
      sha256 "<sha256-macos-x86_64>"
    end
  end

  on_linux do
    on_arm do
      url "https://github.com/grauwen/utl-x/releases/download/v#{version}/utlx-#{version}-linux-arm64.tar.gz"
      sha256 "<sha256-linux-arm64>"
    end
    on_intel do
      url "https://github.com/grauwen/utl-x/releases/download/v#{version}/utlx-#{version}-linux-x86_64.tar.gz"
      sha256 "<sha256-linux-x86_64>"
    end
  end

  def install
    bin.install "utlx"
  end

  test do
    assert_match "UTL-X version", shell_output("#{bin}/utlx --version")
  end
end
```

**Key fields explained:**

- `url` — direct link to the release tarball on GitHub
- `sha256` — checksum of the tarball (computed in Prerequisites)
- `bin.install "utlx"` — copies the binary into Homebrew's bin directory and makes it executable
- `test do` — a sanity check Homebrew runs after install; adjust the expected output to match your actual `--version` output

### Step 4: Test the Formula Locally

Before publishing, test your formula against a local copy:

```bash
# Install from the local file directly
brew install --build-from-source ./Formula/utlx.rb

# Or audit the formula for common issues
brew audit --new Formula/utlx.rb

# Run the test block
brew test utlx
```

Fix any warnings or errors reported by `brew audit` before continuing.

### Step 5: Publish the Tap

Commit and push the formula to GitHub:

```bash
git add Formula/utlx.rb
git commit -m "Add utlx formula v<version>"
git push origin main
```

### Step 6: Verify End-to-End Installation

On a clean machine (or after `brew untap grauwen/utlx` to reset), test the full user flow:

```bash
# Add the tap
brew tap grauwen/utlx

# Install UTL-X
brew install utlx

# Verify
utlx --version
```

Users can also install in a single command without tapping first:
```bash
brew install grauwen/utlx/utlx
```

### Step 7: Updating for New Releases

Each time you publish a new version of UTL-X:

1. Upload the new binaries to a new GitHub Release (e.g. `v0.10.0`)
2. Compute the new SHA256 checksums
3. Edit `Formula/utlx.rb` — update `version` and all `sha256` values
4. Commit and push to `homebrew-utlx`

Users already on a previous version can upgrade with:
```bash
brew update
brew upgrade utlx
```

---

## Part 2: Chocolatey (Windows)

Chocolatey distributes software through **packages** — directories containing a NuSpec file (package metadata) and a PowerShell install script.

### Step 1: Install Chocolatey and Required Tools

On a Windows machine, open an **elevated** PowerShell prompt (Run as Administrator) and install Chocolatey:

```powershell
Set-ExecutionPolicy Bypass -Scope Process -Force
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
```

Then install the Chocolatey package creation tools:

```powershell
choco install chocolatey-cli -y
```

### Step 2: Create the Package Directory Structure

Create the following directory layout (replace `<version>` with your actual version number):

```
utlx/
├── utlx.nuspec
└── tools/
    ├── chocolateyInstall.ps1
    └── chocolateyUninstall.ps1
```

```powershell
mkdir utlx
mkdir utlx\tools
```

### Step 3: Write the NuSpec File

Create `utlx/utlx.nuspec` — this is the package manifest:

```xml
<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://schemas.microsoft.com/packaging/2015/06/nuspec.xsd">
  <metadata>
    <id>utlx</id>
    <version><version></version>
    <title>UTL-X</title>
    <authors>Marcel A. Grauwen</authors>
    <projectUrl>https://github.com/grauwen/utl-x</projectUrl>
    <licenseUrl>https://github.com/grauwen/utl-x/blob/main/LICENSE.md</licenseUrl>
    <requireLicenseAcceptance>false</requireLicenseAcceptance>
    <description>
      Universal Transformation Language Extended (UTL-X) is an open-source,
      format-agnostic functional transformation language for data transformation.
      Works with XML, JSON, CSV, and YAML. Write your transformation logic once
      and apply it to any supported format.
    </description>
    <summary>Format-agnostic data transformation language</summary>
    <tags>utlx transformation xml json csv yaml data etl</tags>
    <releaseNotes>https://github.com/grauwen/utl-x/releases/tag/v<version></releaseNotes>
  </metadata>
</package>
```

### Step 4: Write the Install Script

Create `utlx/tools/chocolateyInstall.ps1`:

```powershell
$ErrorActionPreference = 'Stop'

$packageName  = 'utlx'
$toolsDir     = "$(Split-Path -parent $MyInvocation.MyCommand.Definition)"
$url64        = 'https://github.com/grauwen/utl-x/releases/download/v<version>/utlx-<version>-windows-x86_64.zip'
$checksum64   = '<sha256-windows-x86_64>'

$packageArgs = @{
  packageName    = $packageName
  unzipLocation  = $toolsDir
  url64bit       = $url64
  checksum64     = $checksum64
  checksumType64 = 'sha256'
}

Install-ChocolateyZipPackage @packageArgs
```

Replace `<version>` and `<sha256-windows-x86_64>` with your actual values.

### Step 5: Write the Uninstall Script

Create `utlx/tools/chocolateyUninstall.ps1`:

```powershell
$ErrorActionPreference = 'Stop'

$packageName = 'utlx'
$toolsDir    = "$(Split-Path -parent $MyInvocation.MyCommand.Definition)"

# Remove the binary from the tools directory
$exePath = Join-Path $toolsDir 'utlx.exe'
if (Test-Path $exePath) {
  Remove-Item $exePath -Force
}
```

### Step 6: Pack the Package

From inside the `utlx/` directory, run:

```powershell
cd utlx
choco pack
```

This produces a file named `utlx.<version>.nupkg`.

### Step 7: Test the Package Locally

Install the package from the local `.nupkg` file to verify it works before publishing:

```powershell
choco install utlx -y --source="'\.'" --version="<version>"

# Verify
utlx --version

# Uninstall when done testing
choco uninstall utlx -y
```

### Step 8: Publish to the Chocolatey Community Repository

1. Create an account at [community.chocolatey.org](https://community.chocolatey.org)
2. Get your API key from your account profile page
3. Set the API key locally:
   ```powershell
   choco apikey --key "<your-api-key>" --source "https://push.chocolatey.org/"
   ```
4. Push the package:
   ```powershell
   choco push utlx.<version>.nupkg --source "https://push.chocolatey.org/"
   ```

> **Note:** The Chocolatey community repository has a moderation process. New packages are reviewed by volunteer moderators before becoming publicly available. This typically takes 1–3 business days. You will receive an email when your package is approved or if changes are requested.

### Step 9: Verify End-to-End Installation

Once approved, test the full user flow:

```powershell
choco install utlx -y
utlx --version
```

### Step 10: Updating for New Releases

For each new release:

1. Update the `version` in `utlx.nuspec`
2. Update the `url64` and `checksum64` in `chocolateyInstall.ps1`
3. Run `choco pack` again
4. Push the new `.nupkg` with `choco push`

Users can upgrade with:
```powershell
choco upgrade utlx -y
```

---

## Keeping Both in Sync

When you release a new version of UTL-X, the update checklist is:

| Step | Homebrew | Chocolatey |
|---|---|---|
| Build Graal native binaries | ✅ | ✅ |
| Create GitHub Release & upload artifacts | ✅ | ✅ |
| Compute SHA256 checksums | ✅ | ✅ |
| Update version + checksums | `Formula/utlx.rb` in `homebrew-utlx` repo | `utlx.nuspec` + `chocolateyInstall.ps1` |
| Publish | `git push` to `homebrew-utlx` | `choco pack` + `choco push` |

### Automating with GitHub Actions

You can automate all of the above using a GitHub Actions workflow in the `grauwen/utl-x` repository. On every tagged release, the workflow can:

1. Build the Graal native binaries for all platforms
2. Create a GitHub Release and upload the artifacts
3. Compute SHA256 checksums
4. Open a PR in `homebrew-utlx` with updated formula values
5. Pack and push the Chocolatey package automatically

A starter workflow trigger:

```yaml
on:
  push:
    tags:
      - 'v*'
```

Refer to the `grauwen/utl-x` repository's `.github/workflows/` directory for the full release automation workflow.

---

## Summary: User Installation Commands

Once everything is set up, users install UTL-X with a single command per platform:

**macOS (Apple Silicon or Intel):**
```bash
brew tap grauwen/utlx
brew install utlx
```

**Linux:**
```bash
brew tap grauwen/utlx
brew install utlx
```

**Windows:**
```powershell
choco install utlx -y
```
