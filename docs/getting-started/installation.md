# Installation Guide

This guide will help you install UTL-X on your system.

---

## System Requirements

### Minimum Requirements

- **Java:** JDK 17 or higher (LTS)
- **Memory:** 256 MB RAM minimum
- **Disk Space:** 50 MB for installation

### Recommended Requirements

- **Java:** JDK 17 or higher (LTS version)
- **Memory:** 512 MB RAM or more
- **Disk Space:** 100 MB
- **OS:** Linux, macOS, or Windows

---

## Installation Methods

### Option 1: Build from Source (Recommended)

#### 1. Prerequisites

Ensure you have the following installed:

```bash
# Check Java version (must be 17+)
java -version

# Check Git
git --version
```

If Java is not installed:
- **macOS:** `brew install openjdk@17`
- **Linux (Ubuntu/Debian):** `sudo apt install openjdk-17-jdk`
- **Windows:** Download from [Adoptium](https://adoptium.net/)

#### 2. Clone the Repository

```bash
git clone https://github.com/grauwen/utl-x.git
cd utl-x
```

#### 3. Build the CLI

**macOS / Linux:**
```bash
./gradlew :modules:cli:jar
```

**Windows (Command Prompt):**
```cmd
gradlew.bat :modules:cli:jar
```

**Windows (PowerShell):**
```powershell
.\gradlew.bat :modules:cli:jar
```

#### 4. Verify

```bash
./utlx --version
```

**Output:**
```
UTL-X CLI v1.0.0
Universal Transformation Language Extended
```

The wrapper scripts (`utlx`, `utlx.bat`, `utlx.ps1`) automatically locate and run the compiled JAR file at `modules/cli/build/libs/cli-1.0.0.jar`.

---

### Option 2: GraalVM Native Binary

For instant startup and zero JVM dependency, build a native binary:

```bash
# macOS (Homebrew)
brew install --cask graalvm/tap/graalvm-community-jdk22
export GRAALVM_HOME=/Library/Java/JavaVirtualMachines/graalvm-community-openjdk-22/Contents/Home
export JAVA_HOME=$GRAALVM_HOME

# Build native binary
./gradlew :modules:cli:nativeCompile

# Binary at: modules/cli/build/native/nativeCompile/utlx
```

See [Native Binary Quick Start](native-binary-quickstart.md) for details.

---

### Option 3: Package Managers (Planned)

```bash
# macOS - Homebrew (planned)
brew install utlx

# Windows - Chocolatey (planned)
choco install utlx
```

---

## Quick Test

### Identity Mode (Instant Format Conversion)

The fastest way to test your installation — no script file needed:

```bash
# XML to JSON (auto-detected)
echo '<person><name>Alice</name></person>' | ./utlx

# JSON to XML (auto-detected)
echo '{"greeting":"hello"}' | ./utlx

# With explicit format override
echo '<data><value>42</value></data>' | ./utlx --to yaml
```

### Script-Based Transformation

```bash
# Create test input
echo '<root><message>Hello UTL-X!</message></root>' > test-input.xml

# Create transformation script
cat > test-transform.utlx << 'EOF'
%utlx 1.0
input xml
output json
---
{
  greeting: $input.root.message
}
EOF

# Run transformation
./utlx transform test-transform.utlx test-input.xml
```

**Expected output:**
```json
{
  "greeting": "Hello UTL-X!"
}
```

---

## Supported Formats

| Format | Type | Input | Output |
|--------|------|-------|--------|
| JSON | Data | Yes | Yes |
| XML | Data | Yes | Yes |
| CSV | Data | Yes | Yes |
| YAML | Data | Yes | Yes |
| OData | Data | Yes | Yes |
| XSD | Schema | Yes | Yes |
| JSCH (JSON Schema) | Schema | Yes | Yes |
| Avro | Schema | Yes | Yes |
| Protobuf | Schema | Yes | Yes |
| OSCH (OData/EDMX) | Schema | Yes | Yes |
| TSCH (Table Schema) | Schema | Yes | Yes |

---

## CLI Commands

```bash
./utlx --help                              # Show all commands
./utlx --version                           # Show version

# Identity mode (no script needed)
cat data.xml | ./utlx                      # XML to JSON (smart flip)
cat data.json | ./utlx                     # JSON to XML (smart flip)
cat data.xml | ./utlx --to yaml            # Override output format

# Script-based transformation
./utlx transform script.utlx input.xml     # Transform with script
./utlx script.utlx input.xml               # Implicit transform (same thing)
./utlx transform script.utlx input.xml -o output.json  # Save to file

# Other commands
./utlx validate script.utlx               # Validate script syntax
./utlx functions                           # List all 652 stdlib functions
./utlx functions search xml                # Search functions
./utlx repl                                # Interactive REPL
```

---

## IDE Setup

### IntelliJ IDEA

1. **Open Project:** File -> Open -> Select utl-x directory
2. Wait for Gradle sync to complete
3. **Configure JDK:** File -> Project Structure -> Project SDK -> Select JDK 17

### VS Code

1. **Install Extensions:** Kotlin Language (fwcd), Gradle for Java
2. **Open Project:** File -> Open Folder -> Select utl-x directory
3. **Build:** Open terminal, run `./gradlew :modules:cli:jar`

---

## Troubleshooting

### "java: command not found"

Install Java JDK 17 or higher:
```bash
# macOS
brew install openjdk@17

# Linux (Ubuntu/Debian)
sudo apt install openjdk-17-jdk
```

### "JAVA_HOME is not set"

```bash
# Add to ~/.bashrc or ~/.zshrc
export JAVA_HOME=/path/to/java/home
export PATH=$JAVA_HOME/bin:$PATH
source ~/.bashrc
```

### Build fails with "permission denied"

```bash
chmod +x gradlew
./gradlew :modules:cli:jar
```

### "OutOfMemoryError" during build

```bash
echo "org.gradle.jvmargs=-Xmx2g" >> gradle.properties
./gradlew clean :modules:cli:jar
```

---

## Next Steps

1. **Try identity mode:** `echo '{"name":"world"}' | ./utlx`
2. **First transformation:** [Your First Transformation](your-first-transformation.md)
3. **Core concepts:** [Basic Concepts](basic-concepts.md)
4. **Quick reference:** [Quick Reference](quick-reference.md)
5. **Examples:** [Examples](../examples/)
