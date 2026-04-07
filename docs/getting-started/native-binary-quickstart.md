# UTL-X Native Binary Quick Start Guide

## What You Get

Using **GraalVM Native Image**, UTL-X compiles to a single native binary with instant startup:

| Feature | Kotlin + GraalVM | JVM |
|---------|------------------|-----|
| Startup Time | <10ms | 100-500ms |
| Binary Size | 12-20MB | 50-100MB+ |
| Memory Usage | 30-50MB | 150-300MB |
| Code Reuse | Full (same Kotlin codebase) | Full |
| Distribution | Single binary | Requires JVM |

---

## Building the Native Binary

### macOS (Homebrew)

```bash
# Install GraalVM
brew install --cask graalvm/tap/graalvm-community-jdk22

# Set environment variables (add to ~/.zshrc for persistence)
export GRAALVM_HOME=/Library/Java/JavaVirtualMachines/graalvm-community-openjdk-22/Contents/Home
export JAVA_HOME=$GRAALVM_HOME
export PATH=$GRAALVM_HOME/bin:$PATH

# Clone and build
git clone https://github.com/grauwen/utl-x.git
cd utl-x
./gradlew :modules:cli:nativeCompile

# Binary at: modules/cli/build/native/nativeCompile/utlx
```

GraalVM JDK 21+ bundles `native-image` out of the box.

### Linux

```bash
git clone https://github.com/grauwen/utl-x.git
cd utl-x

# Install GraalVM (if not already installed)
./scripts/install-graalvm.sh

# Build native binary
./gradlew :modules:cli:nativeCompile
```

### Install the Binary

```bash
# Copy to your PATH
sudo cp modules/cli/build/native/nativeCompile/utlx /usr/local/bin/

# Verify
utlx --version
```

---

## Quick Start

### 1. Identity Mode (No Script Needed)

```bash
# XML to JSON
echo '<person><name>Alice</name></person>' | utlx

# JSON to XML
echo '{"greeting":"hello"}' | utlx

# With format override
echo '<data><value>42</value></data>' | utlx --to yaml
```

### 2. Script-Based Transformation

```bash
# Create transformation
cat > transform.utlx << 'EOF'
%utlx 1.0
input xml
output json
---
{
  orderId: $input.Order.@id,
  customer: $input.Order.Customer,
  items: $input.Order.Items.Item |> map(item => {
    sku: item.@sku,
    price: parseNumber(item.@price),
    quantity: parseNumber(item.@quantity),
    subtotal: parseNumber(item.@price) * parseNumber(item.@quantity)
  }),
  total: sum($input.Order.Items.Item.(
    parseNumber(@price) * parseNumber(@quantity)
  ))
}
EOF

# Run
utlx transform transform.utlx input.xml -o output.json
```

### 3. Validate Script Syntax

```bash
utlx validate transform.utlx
```

---

## Performance

### Startup Time

```bash
# Native binary
time utlx --version
# Real: 0.008s

# JVM JAR
time java -jar cli-1.0.0.jar --version
# Real: 0.245s

# ~30x faster startup
```

### Memory

```bash
# Native: ~42MB peak
# JVM: ~185MB peak
# ~77% less memory
```

---

## Docker Usage

### Minimal Image

```dockerfile
FROM scratch
COPY utlx /utlx
ENTRYPOINT ["/utlx"]
```

Image size: **~15MB** (just the binary) vs **300-500MB** for JVM-based images.

```bash
docker build -t utlx:native .
docker run -v $(pwd):/data utlx:native transform /data/transform.utlx /data/input.xml
```

---

## CI/CD Integration

### GitHub Actions

```yaml
- name: Setup UTL-X
  run: |
    curl -L https://github.com/grauwen/utl-x/releases/latest/download/utlx-linux-x64 -o utlx
    chmod +x utlx
    sudo mv utlx /usr/local/bin/

- name: Transform data
  run: utlx transform transform.utlx input.xml -o output.json
```

---

## When to Use Native vs JVM

### Use Native Binary

- CLI tools and one-off transformations
- CI/CD pipelines
- Container-based deployments
- Developer workstations

### Use JVM Version

- Long-running services with existing JVM infrastructure
- Integration with Spring, Camel, or other JVM frameworks
- Hot-reload during development

---

## Troubleshooting

### Binary won't execute: "cannot execute binary file"

Wrong architecture. Check with `uname -m` and download the matching binary (x64 vs arm64).

### Permission denied

```bash
chmod +x utlx
```

### Slow first run on macOS

macOS Gatekeeper scans new binaries on first run. Subsequent runs are fast.

---

## License

Dual-licensed:
- **Open Source:** GNU AGPL v3.0
- **Commercial:** Contact licensing@glomidco.com
