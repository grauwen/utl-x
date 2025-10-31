# Package Manager Distribution Guide for utl-x

## Installation Methods

### macOS - Homebrew (planned)
```bash
brew tap grauwen/utlx
brew install utlx
```

### Linux - APT (planned)
```bash
sudo apt install utlx
```

### Windows - Chocolatey (planned)
```bash
choco install utlx
```

### Any OS - SDKMAN! (planned)
```bash
sdk install utlx
```

---

## Deployment Plan

### 1. Homebrew (macOS/Linux)

**Repository Setup:**
- Create repository: `github.com/grauwen/homebrew-utlx`
- Structure:
  ```
  homebrew-utlx/
  ├── Formula/
  │   └── utlx.rb
  └── README.md
  ```

**Formula File (`Formula/utlx.rb`):**
```ruby
class Utlx < Formula
  desc "Description of your utl-x tool"
  homepage "https://github.com/grauwen/utl-x"
  url "https://github.com/grauwen/utl-x/archive/v1.0.0.tar.gz"
  sha256 "SHA256_HASH_HERE"
  license "LICENSE_TYPE"

  def install
    bin.install "utlx"
    # For compiled binaries:
    # system "make", "install", "PREFIX=#{prefix}"
  end

  test do
    system "#{bin}/utlx", "--version"
  end
end
```

**Steps:**
1. Create `homebrew-utlx` repository
2. Add formula file
3. Create GitHub releases with versioned tarballs
4. Calculate SHA256: `shasum -a 256 utlx-v1.0.0.tar.gz`
5. Users tap with: `brew tap grauwen/utlx`

---

### 2. APT (Debian/Ubuntu)

**Repository Setup:**
- Host a Debian repository or use a PPA service
- Options:
  - **Option A:** Personal Package Archive (PPA) on Launchpad
  - **Option B:** Self-hosted apt repository
  - **Option C:** Use packagecloud.io or similar service

**Option A: PPA (Ubuntu - Recommended for simplicity)**

**Steps:**
1. Create account on Launchpad.net
2. Create PPA (e.g., `ppa:grauwen/utlx`)
3. Build Debian packages (.deb):
   ```bash
   # Create debian/ directory structure
   mkdir -p debian/DEBIAN
   mkdir -p debian/usr/local/bin
   
   # Copy binary
   cp utlx debian/usr/local/bin/
   
   # Create control file
   cat > debian/DEBIAN/control << EOF
   Package: utlx
   Version: 1.0.0
   Section: utils
   Priority: optional
   Architecture: amd64
   Maintainer: Your Name <email@example.com>
   Description: Description of utlx
   EOF
   
   # Build package
   dpkg-deb --build debian utlx_1.0.0_amd64.deb
   ```
4. Upload to PPA using `dput`
5. Users add PPA:
   ```bash
   sudo add-apt-repository ppa:grauwen/utlx
   sudo apt update
   sudo apt install utlx
   ```

**Option B: Self-hosted Repository**

**Steps:**
1. Create directory structure on web server:
   ```
   /var/www/apt/
   ├── dists/
   │   └── stable/
   │       └── main/
   │           └── binary-amd64/
   └── pool/
       └── main/
   ```
2. Place .deb files in `pool/main/`
3. Generate repository metadata:
   ```bash
   cd /var/www/apt
   dpkg-scanpackages pool/ /dev/null | gzip -9c > dists/stable/main/binary-amd64/Packages.gz
   ```
4. Host at `apt.yourdomain.com`
5. Users add repository:
   ```bash
   echo "deb [trusted=yes] https://apt.yourdomain.com stable main" | sudo tee /etc/apt/sources.list.d/utlx.list
   sudo apt update
   sudo apt install utlx
   ```

---

### 3. Chocolatey (Windows)

**Package Structure:**
```
chocolatey-package/
├── utlx.nuspec
└── tools/
    ├── chocolateyinstall.ps1
    └── chocolateyuninstall.ps1
```

**Files to Create:**

**`utlx.nuspec`:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://schemas.microsoft.com/packaging/2015/06/nuspec.xsd">
  <metadata>
    <id>utlx</id>
    <version>1.0.0</version>
    <title>UTL-X</title>
    <authors>grauwen</authors>
    <projectUrl>https://github.com/grauwen/utl-x</projectUrl>
    <licenseUrl>https://github.com/grauwen/utl-x/blob/main/LICENSE</licenseUrl>
    <requireLicenseAcceptance>false</requireLicenseAcceptance>
    <description>Description of your utl-x tool</description>
    <summary>Short summary</summary>
    <tags>utility tools cli</tags>
    <projectSourceUrl>https://github.com/grauwen/utl-x</projectSourceUrl>
    <packageSourceUrl>https://github.com/grauwen/utl-x</packageSourceUrl>
  </metadata>
</package>
```

**`tools/chocolateyinstall.ps1`:**
```powershell
$ErrorActionPreference = 'Stop'
$packageName = 'utlx'
$toolsDir = "$(Split-Path -parent $MyInvocation.MyCommand.Definition)"
$url64 = 'https://github.com/grauwen/utl-x/releases/download/v1.0.0/utlx-windows-x64.zip'
$checksum64 = 'SHA256_HASH_HERE'

$packageArgs = @{
  packageName   = $packageName
  unzipLocation = $toolsDir
  url64bit      = $url64
  checksum64    = $checksum64
  checksumType64= 'sha256'
}

Install-ChocolateyZipPackage @packageArgs
```

**`tools/chocolateyuninstall.ps1`:**
```powershell
$ErrorActionPreference = 'Stop'
$packageName = 'utlx'
$toolsDir = "$(Split-Path -parent $MyInvocation.MyCommand.Definition)"

Remove-Item "$toolsDir\utlx.exe" -ErrorAction SilentlyContinue
```

**Steps:**
1. Install Chocolatey locally for testing
2. Create package structure
3. Test locally: `choco pack` and `choco install utlx -s .`
4. Create account at https://community.chocolatey.org/
5. Get API key from your account
6. Push package:
   ```bash
   choco apikey --key YOUR_API_KEY --source https://push.chocolatey.org/
   choco push utlx.1.0.0.nupkg --source https://push.chocolatey.org/
   ```
7. Wait for moderation approval (first-time packages are manually reviewed)

---

### 4. SDKMAN! (Cross-platform)

**Repository Setup:**
SDKMAN! requires becoming a vendor partner.

**Steps:**

1. **Apply as vendor:**
   - Email: https://sdkman.io/vendors
   - Provide project details
   - Wait for approval

2. **Prepare releases:**
   - Create platform-specific binaries:
     - `utlx-linux-x64.zip`
     - `utlx-darwin-x64.zip` (Intel Mac)
     - `utlx-darwin-arm64.zip` (Apple Silicon)
     - `utlx-windows-x64.zip`
   - Host on GitHub Releases

3. **After approval, announce releases:**
   ```bash
   # SDKMAN provides API for vendors to announce releases
   curl -X POST \
     -H "Consumer-Key: YOUR_KEY" \
     -H "Consumer-Token: YOUR_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"candidate": "utlx", "version": "1.0.0", "url": "https://github.com/grauwen/utl-x/releases/download/v1.0.0/utlx-VERSION-PLATFORM.zip"}' \
     https://vendors.sdkman.io/release
   ```

4. **Users install:**
   ```bash
   sdk install utlx
   ```

**Alternative - Self-hosted (if not approved as vendor):**
Users can add custom candidates:
```bash
sdk install utlx 1.0.0 https://github.com/grauwen/utl-x/releases/download/v1.0.0/utlx-platform.zip
```

---

## Implementation Priority

### Phase 1: Immediate (Low barrier to entry)
1. **Homebrew Tap** - Easiest to implement, immediate availability
   - Create `homebrew-utlx` repository
   - Add formula
   - Test with `brew tap grauwen/utlx && brew install utlx`

### Phase 2: Short-term (1-2 weeks)
2. **Chocolatey** - Windows support
   - Create package
   - Submit to community repository
   - Wait for approval (1-3 days for first package)

### Phase 3: Medium-term (2-4 weeks)
3. **APT Repository** - Linux support
   - Option A: Set up PPA (simpler, Ubuntu only)
   - Option B: Self-host repository (more control, all Debian-based distros)

### Phase 4: Long-term (1-2 months)
4. **SDKMAN!** - Cross-platform support
   - Apply as vendor
   - Wait for approval
   - Implement release automation

---

## Prerequisites for All Package Managers

### Required GitHub Repository Structure
```
utl-x/
├── LICENSE
├── README.md
├── CHANGELOG.md
├── .github/
│   └── workflows/
│       └── release.yml
└── src/
```

### Release Automation (GitHub Actions)

**`.github/workflows/release.yml`:**
```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: ${{ matrix.os }}
    strategy:
      matrix:
        os: [ubuntu-latest, macos-latest, windows-latest]
    steps:
      - uses: actions/checkout@v3
      
      - name: Build
        run: |
          # Your build commands here
          
      - name: Create Release Archives
        run: |
          # Create platform-specific archives
          
      - name: Upload Release Assets
        uses: softprops/action-gh-release@v1
        with:
          files: |
            utlx-*.zip
            utlx-*.tar.gz
```

### Version Management
- Use semantic versioning: `v1.0.0`, `v1.1.0`, etc.
- Create GitHub releases for each version
- Maintain CHANGELOG.md

### Binary Distribution
Each release should include:
- `utlx-linux-x64.tar.gz` / `.zip`
- `utlx-darwin-x64.tar.gz` (Intel Mac)
- `utlx-darwin-arm64.tar.gz` (Apple Silicon)
- `utlx-windows-x64.zip`
- SHA256 checksums file

---

## Testing Checklist

Before going public with each package manager:

- [ ] **Homebrew:**
  - [ ] Formula installs successfully
  - [ ] `brew test utlx` passes
  - [ ] Binary works on macOS and Linux
  
- [ ] **Chocolatey:**
  - [ ] Package installs without errors
  - [ ] Binary is accessible from PATH
  - [ ] Uninstall works cleanly
  
- [ ] **APT:**
  - [ ] Repository is accessible via apt
  - [ ] Package installs on Ubuntu/Debian
  - [ ] Dependencies are correct
  
- [ ] **SDKMAN:**
  - [ ] All platform binaries work
  - [ ] Installation script handles all OSes
  - [ ] Version switching works

---

## Maintenance

### Updating Packages
When releasing a new version:

1. **Tag release** in GitHub: `git tag v1.1.0 && git push --tags`
2. **Update Homebrew formula** with new URL and SHA256
3. **Push new Chocolatey package** with updated version
4. **Update APT repository** with new .deb package
5. **Announce to SDKMAN** (if vendor partner)

### Automation Opportunities
- GitHub Actions to auto-update Homebrew formula on release
- Automated Chocolatey package generation and push
- APT repository updates via CI/CD
- Unified release script to update all package managers

---

## Resources

- **Homebrew:** https://docs.brew.sh/Formula-Cookbook
- **Chocolatey:** https://docs.chocolatey.org/en-us/create/create-packages
- **Debian Packaging:** https://www.debian.org/doc/manuals/maint-guide/
- **SDKMAN:** https://sdkman.io/vendors
- **Launchpad PPA:** https://help.launchpad.net/Packaging/PPA
