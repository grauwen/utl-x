# Installation Guide

This guide will help you install UTL-X on your system.

---

## System Requirements

### Minimum Requirements

- **Java:** JDK 11 or higher
- **Memory:** 256 MB RAM minimum
- **Disk Space:** 50 MB for installation

### Recommended Requirements

- **Java:** JDK 17 or higher (LTS version)
- **Memory:** 512 MB RAM or more
- **Disk Space:** 100 MB
- **OS:** Linux, macOS, or Windows

---

## Installation Methods

### Option 1: Build from Source (Recommended for Alpha)

Currently, UTL-X is in alpha development. The best way to try it is to build from source.

#### 1. Prerequisites

Ensure you have the following installed:

```bash
# Check Java version (must be 11+)
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
# Clone UTL-X repository
git clone https://github.com/grauwen/utl-x.git
cd utl-x
```

#### 3. Build the Project

```bash
# Build using Gradle wrapper (includes all dependencies)
./gradlew build

# This will:
# - Download dependencies
# - Compile the code
# - Run tests
# - Create distribution packages
```

Build output location:
```
utl-x/
└── build/
    └── distributions/
        ├── utlx-0.1.0.tar
        └── utlx-0.1.0.zip
```

#### 4. Install CLI Tool (Optional)

```bash
# Create symlink for easy access
sudo ln -s $(pwd)/build/install/utlx/bin/utlx /usr/local/bin/utlx

# Verify installation
utlx --version
```

**Output:**
```
UTL-X version 0.1.0-SNAPSHOT
Build: 2026-01-15
```

---

### Option 2: Download Pre-Built Binary (Coming Soon)

Once UTL-X reaches beta, pre-built binaries will be available.

**Planned platforms:**
- macOS (Intel and Apple Silicon)
- Linux (x64, ARM64)
- Windows (x64)

**Installation will be:**
```bash
# macOS/Linux
curl -fsSL https://utl-x.com/install.sh | bash

# Windows
# Download installer from GitHub Releases
```

---

### Option 3: Using a Package Manager (Planned)

Future package manager support:

```bash
# macOS - Homebrew (planned)
brew install utlx

# Linux - apt (planned)
sudo apt install utlx

# Windows - Chocolatey (planned)
choco install utlx

# Any OS - SDKMAN! (planned)
sdk install utlx
```

---

## Verify Installation

### Check Version

```bash
utlx --version
```

**Expected output:**
```
UTL-X version 0.1.0
JVM: 17.0.2
Kotlin: 1.9.21
```

### Run Test Transformation

Create a test file:

```bash
# Create test input
echo '<root><message>Hello UTL-X!</message></root>' > test-$input.xml

# Create test transformation
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
utlx transform test-$input.xml test-transform.utlx
```

**Expected output:**
```json
{
  "greeting": "Hello UTL-X!"
}
```

✅ **If you see the JSON output above, installation successful!**

---

## IDE Setup

### IntelliJ IDEA

#### 1. Open Project

```
File → Open → Select utl-x directory
```

Wait for Gradle sync to complete.

#### 2. Install Kotlin Plugin

Kotlin plugin should be installed by default. If not:
```
Settings → Plugins → Search "Kotlin" → Install
```

#### 3. Configure JDK

```
File → Project Structure → Project SDK → Select JDK 17
```

#### 4. Run Configurations

IntelliJ will auto-detect Gradle tasks. To create a run configuration:

```
Run → Edit Configurations → + → Gradle
Name: Build UTL-X
Gradle project: utl-x
Tasks: build
```

### VS Code

#### 1. Install Extensions

```
- Kotlin Language (fwcd)
- Gradle for Java
- Extension Pack for Java
```

#### 2. Open Project

```
File → Open Folder → Select utl-x directory
```

#### 3. Configure Java

Press `Ctrl+Shift+P` (or `Cmd+Shift+P` on macOS):
```
Java: Configure Java Runtime → Select JDK 17
```

#### 4. Build Project

Open integrated terminal:
```bash
./gradlew build
```

---

## Using UTL-X as a Library

### Maven

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.glomidco.utlx</groupId>
    <artifactId>utlx-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Gradle (Kotlin DSL)

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.glomidco.utlx:utlx-core:0.1.0")
}
```

### Gradle (Groovy)

Add to your `build.gradle`:

```groovy
dependencies {
    implementation 'com.glomidco.utlx:utlx-core:0.1.0'
}
```

---

## Configuration

### Environment Variables

Optional environment variables for UTL-X:

```bash
# Set default output format
export UTLX_DEFAULT_OUTPUT=json

# Set maximum memory
export UTLX_MAX_MEMORY=512m

# Enable debug logging
export UTLX_DEBUG=true

# Set custom plugins directory
export UTLX_PLUGINS_DIR=/path/to/plugins
```

### Configuration File

Create `~/.utlx/config.yaml`:

```yaml
# UTL-X Configuration
defaults:
  output_format: json
  pretty_print: true
  validate_input: true

performance:
  max_memory: 512m
  parallel_processing: true
  cache_enabled: true

logging:
  level: info
  file: ~/.utlx/logs/utlx.log
```

---

## Updating UTL-X

### From Source

```bash
# Navigate to UTL-X directory
cd utl-x

# Pull latest changes
git pull origin main

# Rebuild
./gradlew clean build
```

### Using Package Manager (Future)

```bash
# Homebrew
brew upgrade utlx

# apt
sudo apt update && sudo apt upgrade utlx

# SDKMAN
sdk upgrade utlx
```

---

## Uninstalling

### If Installed from Source

```bash
# Remove symlink (if created)
sudo rm /usr/local/bin/utlx

# Remove cloned repository
rm -rf ~/utl-x
```

### Using Package Manager (Future)

```bash
# Homebrew
brew uninstall utlx

# apt
sudo apt remove utlx

# Chocolatey
choco uninstall utlx
```

---

## Troubleshooting

### Issue: "java: command not found"

**Solution:** Install Java JDK 11 or higher.

```bash
# macOS
brew install openjdk@17

# Linux (Ubuntu/Debian)
sudo apt install openjdk-17-jdk

# Verify
java -version
```

### Issue: "JAVA_HOME is not set"

**Solution:** Set JAVA_HOME environment variable.

```bash
# Find Java installation
which java

# Set JAVA_HOME (add to ~/.bashrc or ~/.zshrc)
export JAVA_HOME=/path/to/java/home
export PATH=$JAVA_HOME/bin:$PATH

# Reload shell
source ~/.bashrc  # or source ~/.zshrc
```

### Issue: Build fails with "permission denied"

**Solution:** Make gradlew executable.

```bash
chmod +x gradlew
./gradlew build
```

### Issue: "OutOfMemoryError" during build

**Solution:** Increase Gradle memory.

```bash
# Create or edit gradle.properties
echo "org.gradle.jvmargs=-Xmx2g" >> gradle.properties

# Rebuild
./gradlew clean build
```

### Issue: Tests fail during build

**Solution:** Skip tests temporarily (not recommended for development).

```bash
./gradlew build -x test
```

To investigate test failures:
```bash
./gradlew test --info
```

---

## Getting Help

If you encounter issues during installation:

- 📖 Check the [FAQ](../community/faq.md)
- 💬 Ask in [GitHub Discussions](https://github.com/grauwen/utl-x/discussions)
- 🐛 Report bugs in [GitHub Issues](https://github.com/grauwen/utl-x/issues)
- 📧 Email: support@glomidco.com

---

## Next Steps

Now that UTL-X is installed:

1. ✅ **Learn the basics:** [Your First Transformation](your-first-transformation.md)
2. 📖 **Understand concepts:** [Basic Concepts](basic-concepts.md)
3. 💡 **Try examples:** [Examples](../examples/)
4. 📚 **Deep dive:** [Language Guide](../language-guide/)

---

**Happy Transforming! 🚀**
