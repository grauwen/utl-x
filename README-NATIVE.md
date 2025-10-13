# UTL-X Native Binary Build Guide

## Overview

UTL-X CLI is compiled to native binaries using GraalVM Native Image, providing:

- ⚡ **Instant startup** - <10ms vs 100-500ms for JVM
- 📦 **Single binary** - No JVM installation required
- 🪶 **Small size** - 10-20MB vs 50-100MB+ with bundled JVM
- 💾 **Low memory** - 20-50MB vs 100-300MB for JVM
- 🚀 **Native performance** - Optimized machine code

## Prerequisites

### Install GraalVM

#### Automated Installation
```bash
./scripts/install-graalvm.sh
```

#### Manual Installation

**Using SDKMAN (Recommended)**
```bash
curl -s "https://get.sdkman.io" | bash
sdk install java 21.0.1-graalce
sdk use java 21.0.1-graalce
gu install native-image
```

**Direct Download**
1. Download from https://github.com/graalvm/graalvm-ce-builds/releases
2. Extract to `/opt/graalvm` or `$HOME/.graalvm`
3. Set environment variables:
```bash
export GRAALVM_HOME=/path/to/graalvm
export JAVA_HOME=$GRAALVM_HOME
export PATH=$GRAALVM_HOME/bin:$PATH
```
4. Install native-image: `gu install native-image`

### Platform-Specific Requirements

**Linux**
```bash
# Ubuntu/Debian
sudo apt-get install build-essential zlib1g-dev

# Fedora/RHEL
sudo dnf install gcc glibc-devel zlib-devel
```

**macOS**
```bash
xcode-select --install
```

**Windows**
- Install Visual Studio 2022 with "Desktop development with C++"
- Or install Windows SDK

## Building

### Quick Build
```bash
./scripts/build-native.sh
```

### Gradle Build
```bash
# Build native binary
./gradlew :modules:cli:nativeCompile

# Binary location
./modules/cli/build/native/nativeCompile/utlx
```

### Multi-Platform Build
```bash
./scripts/build-native-multiplatform.sh
```

## Configuration

### Generate Reflection Config

If you add classes that use reflection, generate updated config:

```bash
# Run tests with agent
./gradlew test -Pagent

# Config files generated in:
# src/main/resources/META-INF/native-image/
```

### Custom Build Options

Edit `modules/cli/build.gradle.kts`:

```kotlin
buildArgs.addAll(
    "--static",              // Fully static binary (Linux only)
    "-Ob",                   // Optimize for binary size
    "--gc=serial",           // Smaller GC (for small workloads)
    "-march=x86-64-v3"       // Target specific CPU
)
```

## Performance Benchmarks

### Startup Time
```bash
# Native binary
time ./utlx --version
# Real: 0.008s

# JAR (for comparison)
time java -jar utlx-all.jar --version
# Real: 0.245s
```

### Memory Usage
```bash
# Native binary
/usr/bin/time -v ./utlx transform input.xml transform.utlx
# Maximum resident set size: 45MB

# JAR
/usr/bin/time -v java -jar utlx-all.jar transform input.xml transform.utlx
# Maximum resident set size: 180MB
```

### Binary Size
```bash
# Native binary (stripped)
strip utlx
ls -lh utlx
# 12MB

# JAR with dependencies
ls -lh utlx-all.jar
# 85MB (+ JVM ~300MB)
```

## Distribution

### Single Binary Distribution
```bash
# Create tarball
tar czf utlx-linux-x64.tar.gz utlx
tar czf utlx-macos-arm64.tar.gz utlx
zip utlx-windows-x64.zip utlx.exe
```

### Installation Script
```bash
# Create installer
cat > install.sh << 'EOF'
#!/bin/bash
set -e
INSTALL_DIR="${INSTALL_DIR:-/usr/local/bin}"
curl -L https://github.com/grauwen/utl-x/releases/latest/download/utlx-$(uname -s | tr '[:upper:]' '[:lower:]')-$(uname -m) -o utlx
chmod +x utlx
sudo mv utlx "$INSTALL_DIR/"
echo "✅ UTL-X installed to $INSTALL_DIR/utlx"
EOF
chmod +x install.sh
```

### Homebrew Formula
```ruby
class Utlx < Formula
  desc "Universal Transformation Language Extended"
  homepage "https://github.com/grauwen/utl-x"
  url "https://github.com/grauwen/utl-x/releases/download/v1.0.0/utlx-macos-arm64"
  sha256 "..."
  version "1.0.0"

  def install
    bin.install "utlx-macos-arm64" => "utlx"
  end

  test do
    system "#{bin}/utlx", "--version"
  end
end
```

## Troubleshooting

### Build Fails with ClassNotFoundException
Add missing classes to `reflect-config.json`:
```json
{
  "name": "com.example.MissingClass",
  "allDeclaredConstructors": true,
  "allDeclaredMethods": true
}
```

### Binary Crashes at Runtime
1. Run with debug info: `utlx --verbose`
2. Check initialization: Ensure classes are initialized at build time
3. Generate config with agent: `./gradlew test -Pagent`

### Large Binary Size
Optimize for size:
```kotlin
buildArgs.add("-Ob")           // Optimize for size
buildArgs.add("--gc=serial")   // Smaller GC
```

### Slow Build Time
Increase build memory:
```kotlin
jvmArgs.add("-Xmx8g")  // More memory for builder
```

## CI/CD Integration

### GitHub Actions
See `.github/workflows/native-build.yml` for automated builds

### Docker Build
```dockerfile
FROM ghcr.io/graalvm/graalvm-ce:java21 AS builder
RUN gu install native-image
WORKDIR /build
COPY . .
RUN ./gradlew :modules:cli:nativeCompile

FROM scratch
COPY --from=builder /build/modules/cli/build/native/nativeCompile/utlx /utlx
ENTRYPOINT ["/utlx"]
```

## Best Practices

1. **Always test native binary** - Behavior can differ from JVM
2. **Profile memory usage** - Adjust heap size if needed
3. **Use static analysis** - GraalVM catches many issues at build time
4. **Keep config updated** - Regenerate when adding reflection
5. **Document limitations** - Some JVM features unavailable

## Resources

- [GraalVM Native Image Docs](https://www.graalvm.org/latest/reference-manual/native-image/)
- [Native Image Compatibility Guide](https://www.graalvm.org/latest/reference-manual/native-image/metadata/)
- [UTL-X GitHub Issues](https://github.com/grauwen/utl-x/issues)

---

**Result:** Native binary with JVM startup time eliminated, providing Go-like UX with Kotlin's maintainability! 🚀
