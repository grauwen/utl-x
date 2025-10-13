# UTL-X Native Binary Build Guide

**For Contributors & Advanced Users**

This guide covers building UTL-X CLI as a native binary using GraalVM Native Image.

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Quick Build](#quick-build)
- [Detailed Build Process](#detailed-build-process)
- [Configuration](#configuration)
- [Multi-Platform Builds](#multi-platform-builds)
- [Performance Tuning](#performance-tuning)
- [Troubleshooting](#troubleshooting)
- [CI/CD Integration](#cicd-integration)

---

## Overview

UTL-X CLI is compiled to native binaries using GraalVM Native Image, providing:

- ⚡ **Instant startup** - <10ms vs 100-500ms for JVM
- 📦 **Single binary** - No JVM installation required
- 🪶 **Small size** - 10-20MB vs 50-100MB+ with bundled JVM
- 💾 **Low memory** - 20-50MB vs 100-300MB for JVM
- 🚀 **Native performance** - Optimized machine code

**Architecture:**
```
Kotlin Source Code
    ↓
GraalVM Native Image Compiler
    ↓
Native Binary (Linux/macOS/Windows)
```

---

## Prerequisites

### 1. GraalVM Installation

#### Option A: Automated Installation (Recommended)

```bash
./scripts/install-graalvm.sh
```

This script will:
- Detect your OS and architecture
- Download GraalVM 21.0.1 (Java 21)
- Install to `$HOME/.graalvm`
- Install `native-image` component
- Provide environment variable setup instructions

#### Option B: SDKMAN (Easy)

```bash
# Install SDKMAN
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install GraalVM
sdk install java 21.0.1-graalce
sdk use java 21.0.1-graalce

# Install native-image
gu install native-image
```

#### Option C: Manual Installation

1. **Download GraalVM:**
   - Visit: https://github.com/graalvm/graalvm-ce-builds/releases
   - Download: `graalvm-community-jdk-21.0.1_[OS]-[ARCH]_bin.tar.gz`

2. **Extract:**
   ```bash
   mkdir -p $HOME/.graalvm
   tar xzf graalvm-*.tar.gz -C $HOME/.graalvm --strip-components=1
   ```

3. **Set Environment Variables:**
   ```bash
   export GRAALVM_HOME=$HOME/.graalvm
   export JAVA_HOME=$GRAALVM_HOME
   export PATH=$GRAALVM_HOME/bin:$PATH
   ```

4. **Install native-image:**
   ```bash
   gu install native-image
   ```

5. **Verify Installation:**
   ```bash
   java -version
   native-image --version
   ```

### 2. Platform-Specific Requirements

#### Linux (Ubuntu/Debian)
```bash
sudo apt-get update
sudo apt-get install -y build-essential zlib1g-dev
```

#### Linux (Fedora/RHEL)
```bash
sudo dnf install -y gcc glibc-devel zlib-devel libstdc++-static
```

#### macOS
```bash
xcode-select --install
```

#### Windows
- Install **Visual Studio 2022** with "Desktop development with C++"
- Or install **Windows SDK** (minimum version 10.0.19041.0)
- Run builds from **x64 Native Tools Command Prompt**

### 3. Build Tools

```bash
# Gradle (included via wrapper)
./gradlew --version

# Git
git --version
```

---

## Quick Build

### Build Native Binary

```bash
# Clone repository
git clone https://github.com/grauwen/utl-x.git
cd utl-x

# Build native binary (automated script)
./scripts/build-native.sh
```

The script will:
1. Check for GraalVM installation
2. Clean previous builds
3. Compile with Gradle
4. Test the binary
5. Report size and performance

**Output Location:**
```
./modules/cli/build/native/nativeCompile/utlx
```

### Test Binary

```bash
# Run version check
./modules/cli/build/native/nativeCompile/utlx --version

# Test transformation
./modules/cli/build/native/nativeCompile/utlx transform \
    examples/basic/01-xml-to-json/input.xml \
    examples/basic/01-xml-to-json/transform.utlx \
    -o output.json
```

### Install System-Wide

```bash
sudo cp ./modules/cli/build/native/nativeCompile/utlx /usr/local/bin/
```

---

## Detailed Build Process

### Gradle Build

```bash
# Full build with native compilation
./gradlew :modules:cli:nativeCompile

# Build with specific optimization
./gradlew :modules:cli:nativeCompile -Doptimization=O3

# Clean and rebuild
./gradlew clean :modules:cli:nativeCompile

# Build with verbose output
./gradlew :modules:cli:nativeCompile --info
```

### Build Configuration

The native image configuration is in `modules/cli/build.gradle.kts`:

```kotlin
graalvmNative {
    binaries {
        named("main") {
            imageName.set("utlx")
            mainClass.set("org.apache.utlx.cli.MainKt")
            
            buildArgs.addAll(
                "--no-fallback",                    // No JVM fallback
                "--install-exit-handlers",          // Better error messages
                "-H:+ReportExceptionStackTraces",   // Debug info
                "-H:+AddAllCharsets",               // All encodings
                "--initialize-at-build-time=kotlin", // Kotlin runtime
                "-O3",                              // Max optimization
                "--gc=G1",                          // G1 GC
                "-march=native"                     // CPU optimization
            )
        }
    }
}
```

### Build Time

| Build Type | Time | Notes |
|------------|------|-------|
| First build | 3-5 min | Full compilation |
| Incremental | 30-60s | After code changes |
| CI/CD | 2-4 min | With caching |

---

## Configuration

### Reflection Configuration

GraalVM requires explicit configuration for reflection. Auto-generated configs are in:

```
modules/cli/src/main/resources/META-INF/native-image/
├── reflect-config.json          # Reflection metadata
├── resource-config.json         # Resource inclusion
├── proxy-config.json            # Dynamic proxies
├── serialization-config.json    # Serialization
└── native-image.properties      # Build properties
```

### Generate/Update Reflection Config

When adding new classes that use reflection:

```bash
# Run tests with tracing agent
./gradlew test -Pagent

# Agent generates configs automatically
# Review and commit updated configs
git diff src/main/resources/META-INF/native-image/
```

### Custom Build Options

Edit `modules/cli/build.gradle.kts` to customize:

**Optimize for Size:**
```kotlin
buildArgs.add("-Ob")              // Size optimization
buildArgs.add("--gc=serial")      // Smaller GC
```

**Static Binary (Linux only):**
```kotlin
buildArgs.add("--static")         // Fully static
buildArgs.add("--libc=musl")      // Use musl libc
```

**Debug Build:**
```kotlin
buildArgs.addAll(
    "-H:GenerateDebugInfo=1",     // Debug symbols
    "-H:+PreserveFramePointer",   // Stack traces
    "-g"                          // GDB support
)
```

---

## Multi-Platform Builds

### Build for All Platforms

```bash
./scripts/build-native-multiplatform.sh
```

This creates binaries for:
- `linux-x64`
- `linux-arm64`
- `macos-x64`
- `macos-arm64`
- `windows-x64`

Output in: `dist/`

### Cross-Compilation

GraalVM native-image does not support true cross-compilation. Options:

**Option 1: Use GitHub Actions** (Recommended)
```yaml
# .github/workflows/native-build.yml handles all platforms
```

**Option 2: Docker for Linux targets**
```bash
# Build Linux x64 binary in Docker
docker run --rm -v $(pwd):/build -w /build \
    ghcr.io/graalvm/graalvm-ce:java21 \
    ./gradlew :modules:cli:nativeCompile

# Build Linux ARM64 binary
docker run --rm -v $(pwd):/build -w /build \
    --platform linux/arm64 \
    ghcr.io/graalvm/graalvm-ce:java21 \
    ./gradlew :modules:cli:nativeCompile
```

**Option 3: Multiple VMs/Machines**
- Use separate machines for each target platform
- Share built binaries via artifact repository

---

## Performance Tuning

### Optimization Levels

```bash
# No optimization (fastest build, slowest runtime)
./gradlew :modules:cli:nativeCompile -Doptimization=O0

# Balanced (default)
./gradlew :modules:cli:nativeCompile -Doptimization=O2

# Maximum optimization (slowest build, fastest runtime)
./gradlew :modules:cli:nativeCompile -Doptimization=O3
```

### PGO (Profile-Guided Optimization)

```bash
# 1. Build with instrumentation
./gradlew :modules:cli:nativeCompile -Dpgo=instrument

# 2. Run typical workloads
./modules/cli/build/native/nativeCompile/utlx transform test1.xml t.utlx
./modules/cli/build/native/nativeCompile/utlx transform test2.json t.utlx
# ... more representative workloads

# 3. Rebuild with profile
./gradlew :modules:cli:nativeCompile -Dpgo=use
```

### Memory Configuration

```bash
# Increase build-time memory (if compilation fails)
export GRADLE_OPTS="-Xmx8g"
./gradlew :modules:cli:nativeCompile
```

### Build Time Optimization

```bash
# Use build cache
./gradlew :modules:cli:nativeCompile --build-cache

# Parallel compilation (if multiple modules)
./gradlew :modules:cli:nativeCompile --parallel
```

---

## Troubleshooting

### Build Fails with OutOfMemoryError

**Problem:** Not enough memory for native-image compiler

**Solution:**
```bash
export GRADLE_OPTS="-Xmx8g"
./gradlew :modules:cli:nativeCompile
```

### Missing Reflection Configuration

**Problem:** `ClassNotFoundException` at runtime

**Solution:**
```bash
# Regenerate configs with agent
./gradlew test -Pagent

# Or add manually to reflect-config.json
```

### Binary Crashes at Runtime

**Problem:** Incompatible initialization

**Solution:**
```kotlin
// Add to build.gradle.kts
buildArgs.add("--initialize-at-build-time=com.yourpackage.ProblematicClass")
```

### Slow First Run on macOS

**Problem:** Gatekeeper security scanning

**Solution:**
```bash
# Sign the binary (for distribution)
codesign --force --sign - ./utlx

# Or wait ~30 seconds on first run
```

### Linux: "No such file or directory"

**Problem:** Missing dynamic libraries

**Solution:**
```bash
# Check dependencies
ldd ./utlx

# Install missing libraries
sudo apt-get install libc6 zlib1g
```

### Windows: "VCRUNTIME140.dll not found"

**Problem:** Missing Visual C++ runtime

**Solution:**
- Install Visual C++ Redistributable
- Or build with static linking: `buildArgs.add("--static")`

---

## CI/CD Integration

### GitHub Actions

See `.github/workflows/native-build.yml`:

```yaml
- name: Setup GraalVM
  uses: graalvm/setup-graalvm@v1
  with:
    java-version: '21'
    distribution: 'graalvm-community'

- name: Build native binary
  run: ./gradlew :modules:cli:nativeCompile

- name: Upload artifact
  uses: actions/upload-artifact@v3
  with:
    name: utlx-${{ matrix.platform }}
    path: modules/cli/build/native/nativeCompile/utlx
```

### GitLab CI

```yaml
build-native:
  image: ghcr.io/graalvm/graalvm-ce:java21
  before_script:
    - gu install native-image
  script:
    - ./gradlew :modules:cli:nativeCompile
  artifacts:
    paths:
      - modules/cli/build/native/nativeCompile/utlx
```

### Docker Build

```dockerfile
# Build stage
FROM ghcr.io/graalvm/graalvm-ce:java21 AS builder
RUN gu install native-image

WORKDIR /build
COPY . .
RUN ./gradlew :modules:cli:nativeCompile

# Runtime stage
FROM scratch
COPY --from=builder /build/modules/cli/build/native/nativeCompile/utlx /utlx
ENTRYPOINT ["/utlx"]
```

---

## Benchmarking

### Build Performance

```bash
# Measure build time
time ./gradlew :modules:cli:nativeCompile
```

### Runtime Performance

```bash
# Startup time
time ./utlx --version

# Transformation performance
./utlx transform input.xml transform.utlx -b

# Memory profiling (Linux)
/usr/bin/time -v ./utlx transform input.xml transform.utlx
```

### Binary Size Analysis

```bash
# Unstripped size
ls -lh ./utlx

# Strip debug symbols
strip ./utlx
ls -lh ./utlx

# Analyze binary contents
nm -S ./utlx | sort -n -k2
```

---

## Best Practices

1. **Always test native binary** - Behavior can differ from JVM
2. **Profile memory usage** - Adjust heap if needed
3. **Use agent for config** - Don't manually write reflection configs
4. **Cache builds in CI** - Speeds up pipeline significantly
5. **Test on target platform** - Cross-platform issues can occur
6. **Monitor binary size** - Add new dependencies carefully
7. **Document limitations** - Some JVM features unavailable in native

---

## Advanced Topics

### Building Shared Libraries

```kotlin
// In build.gradle.kts
graalvmNative {
    binaries {
        create("shared") {
            sharedLibrary.set(true)
            imageName.set("libutlx")
        }
    }
}
```

### Custom Entry Points

```kotlin
buildArgs.add("-H:Name=utlx-custom")
buildArgs.add("-H:Class=org.apache.utlx.CustomMain")
```

### Resource Bundles

```json
// resource-config.json
{
  "bundles": [
    {"name": "org.apache.utlx.messages"}
  ]
}
```

---

## Resources

- **GraalVM Native Image Docs:** https://www.graalvm.org/native-image/
- **Compatibility Guide:** https://www.graalvm.org/native-image/metadata/
- **Build Configuration:** https://www.graalvm.org/native-image/options/
- **Kotlin Native:** https://kotlinlang.org/docs/native-overview.html

---

## Contributing

Found a build issue? Please report:
- **Issues:** https://github.com/grauwen/utl-x/issues
- **Discussions:** https://github.com/grauwen/utl-x/discussions

---

**Project Lead:** Ir. Marcel A. Grauwen  
**License:** Dual-licensed (AGPL-3.0 / Commercial)  
**Website:** https://github.com/grauwen/utl-x
