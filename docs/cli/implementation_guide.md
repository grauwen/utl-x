# UTL-X CLI - Step-by-Step Implementation Guide

This guide walks you through implementing and testing the integrated CLI.

## Prerequisites

- Project cloned: `git clone https://github.com/grauwen/utl-x.git`
- Java 17+ installed
- GraalVM installed (optional, for native binary)

## Step 1: Update Project Structure

### 1.1 Update settings.gradle.kts
Replace the content with the new version that includes all modules:

```bash
# Backup current file
cp settings.gradle.kts settings.gradle.kts.backup

# Replace with new version (from artifact "settings_gradle_updated")
```

Ensure it includes:
```kotlin
include(":modules:core")
include(":modules:cli")
include(":formats:xml")
include(":formats:json")
include(":formats:csv")
```

### 1.2 Update CLI build.gradle.kts
Replace `modules/cli/build.gradle.kts` with the new version that includes:
- Dependencies on core and formats modules
- GraalVM native image configuration
- Task definitions

```bash
# Backup
cp modules/cli/build.gradle.kts modules/cli/build.gradle.kts.backup

# Replace with new version (from artifact "cli_build_gradle")
```

## Step 2: Implement CLI Commands

### 2.1 Create/Update Main.kt
Location: `modules/cli/src/main/kotlin/org/apache/utlx/cli/Main.kt`

```bash
# Create directory if needed
mkdir -p modules/cli/src/main/kotlin/org/apache/utlx/cli

# Add the Main.kt file (from artifact "cli_main")
```

### 2.2 Create TransformCommand.kt
Location: `modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/TransformCommand.kt`

```bash
# Create commands directory
mkdir -p modules/cli/src/main/kotlin/org/apache/utlx/cli/commands

# Add TransformCommand.kt (from artifact "transform_command")
```

This is the most important command - it integrates everything:
- Uses core lexer, parser, type checker, interpreter
- Uses format parsers (XML, JSON, CSV)
- Uses format serializers (XML, JSON, CSV)
- Handles CLI options and I/O

### 2.3 Create ValidateCommand.kt
Location: `modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/ValidateCommand.kt`

```bash
# Add ValidateCommand.kt (from artifact "validate_command")
```

### 2.4 Create Other Commands
Location: `modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/`

```bash
# Add all other commands (from artifact "other_commands")
# - CompileCommand.kt
# - FormatCommand.kt  
# - MigrateCommand.kt
# - VersionCommand.kt
```

## Step 3: Add Tests

### 3.1 Create Test Directory
```bash
mkdir -p modules/cli/src/test/kotlin/org/apache/utlx/cli
```

### 3.2 Add Test Files
```bash
# Add test files (from artifact "cli_integration_test")
# - TransformCommandTest.kt
# - ValidateCommandTest.kt
```

## Step 4: Create Build Script

### 4.1 Make build script executable
```bash
# Create scripts directory if needed
mkdir -p scripts

# Add build-cli.sh (from artifact "cli_build_script")
chmod +x scripts/build-cli.sh
```

### 4.2 Test the build script
```bash
# Verify the script works
./scripts/build-cli.sh --help
```

## Step 5: Build and Test

### 5.1 Build JAR only (fastest initial test)
```bash
# Clean first
./gradlew clean

# Build just the JAR
./scripts/build-cli.sh --jar-only
```

Expected output:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  UTL-X CLI Build Script
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
...
✓ JAR built successfully: modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar
✓ Build complete!
```

### 5.2 Verify the JAR works
```bash
# Test version command
java -jar modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar version

# Should output:
# UTL-X v1.0.0-SNAPSHOT
```

### 5.3 Test with examples
The build script creates example files in `examples/cli-test/`. Test them:

```bash
# Transform JSON example
java -jar modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar \
    transform \
    examples/cli-test/input.json \
    examples/cli-test/transform.utlx \
    -o examples/cli-test/output.json

# View output
cat examples/cli-test/output.json
```

### 5.4 Run unit tests
```bash
./gradlew :modules:cli:test

# View test report
open modules/cli/build/reports/tests/test/index.html  # macOS
xdg-open modules/cli/build/reports/tests/test/index.html  # Linux
```

## Step 6: Build Native Binary (Optional)

### 6.1 Install GraalVM if needed
```bash
# Check if GraalVM is installed
native-image --version

# If not installed, run:
./scripts/install-graalvm.sh
source ~/.bashrc  # Or restart terminal

# Verify
echo $JAVA_HOME
native-image --version
```

### 6.2 Build native binary
```bash
./scripts/build-cli.sh --native
```

This takes 2-5 minutes. Expected output:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Step 5: Build Native Binary
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
→ Building native binary with GraalVM...
→ This may take 2-5 minutes...
...
✓ Native binary built: modules/cli/build/native/nativeCompile/utlx
```

### 6.3 Test native binary
```bash
# Test version
./modules/cli/build/native/nativeCompile/utlx version

# Test transformation
./modules/cli/build/native/nativeCompile/utlx \
    transform \
    examples/cli-test/input.json \
    examples/cli-test/transform.utlx
```

### 6.4 Install system-wide (optional)
```bash
# Linux/macOS
sudo cp modules/cli/build/native/nativeCompile/utlx /usr/local/bin/
sudo chmod +x /usr/local/bin/utlx

# Test installation
utlx version
utlx transform examples/cli-test/input.json examples/cli-test/transform.utlx
```

## Step 7: Manual Testing Checklist

### 7.1 Test transform command variations

```bash
JAR="modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar"

# Basic transform
java -jar $JAR transform examples/cli-test/input.json examples/cli-test/transform.utlx

# Output to file
java -jar $JAR transform examples/cli-test/input.json examples/cli-test/transform.utlx -o output.json

# Verbose mode
java -jar $JAR transform examples/cli-test/input.json examples/cli-test/transform.utlx -v

# From stdin
cat examples/cli-test/input.json | java -jar $JAR transform examples/cli-test/transform.utlx

# Force output format
java -jar $JAR transform examples/cli-test/input.json examples/cli-test/transform.utlx --output-format xml
```

### 7.2 Test validate command

```bash
# Validate single file
java -jar $JAR validate examples/cli-test/transform.utlx

# Validate with verbose
java -jar $JAR validate examples/cli-test/transform.utlx -v

# Validate invalid script (create one first)
echo "invalid syntax!!!" > /tmp/invalid.utlx
java -jar $JAR validate /tmp/invalid.utlx  # Should fail
```

### 7.3 Test format command

```bash
# Format script
java -jar $JAR format examples/cli-test/transform.utlx

# Check format (CI mode)
java -jar $JAR format examples/cli-test/transform.utlx --check
```

### 7.4 Test help and version

```bash
# Main help
java -jar $JAR --help

# Command help
java -jar $JAR transform --help

# Version
java -jar $JAR version
java -jar $JAR version --verbose
```

## Step 8: Create Documentation

### 8.1 Add CLI README
Create `modules/cli/README.md` with content from artifact "cli_readme"

### 8.2 Add Quick Start Guide
Create `CLI-QUICKSTART.md` in project root with content from artifact "cli_quickstart"

### 8.3 Update main README
Add a section about the CLI to the main `README.md`:

```markdown
## Using the CLI

Build and run the CLI:

```bash
./scripts/build-cli.sh
java -jar modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar transform input.xml script.utlx
```

See [CLI-QUICKSTART.md](CLI-QUICKSTART.md) for detailed usage.
```

## Step 9: Verify Everything Works

### 9.1 Full build test
```bash
# Clean everything
./gradlew clean
rm -rf build/ */build/

# Full build
./scripts/build-cli.sh --native

# This should:
# ✓ Clean previous builds
# ✓ Build core module
# ✓ Build format modules  
# ✓ Run tests
# ✓ Build JAR
# ✓ Build native binary
# ✓ Create examples
# ✓ Test CLI
```

### 9.2 Integration test
Create a real-world test:

```bash
# Create XML input
cat > /tmp/test-input.xml << 'EOF'
<?xml version="1.0"?>
<Order id="ORD-999">
  <Customer>
    <Name>Test User</Name>
    <Email>test@example.com</Email>
  </Customer>
  <Items>
    <Item sku="TEST-001" price="10.00" quantity="2"/>
    <Item sku="TEST-002" price="20.00" quantity="1"/>
  </Items>
</Order>
EOF

# Create transform script
cat > /tmp/test-transform.utlx << 'EOF'
%utlx 1.0
input xml
output json
---
{
  orderId: input.Order.@id,
  customer: input.Order.Customer.Name,
  total: sum(input.Order.Items.Item.(parseFloat(@price) * parseInt(@quantity)))
}
EOF

# Run transformation
java -jar modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar \
    transform /tmp/test-input.xml /tmp/test-transform.utlx -v

# Should output:
# {
#   "orderId": "ORD-999",
#   "customer": "Test User",
#   "total": 40.0
# }
```

## Step 10: Commit and Push

### 10.1 Review changes
```bash
git status
git diff
```

### 10.2 Commit changes
```bash
# Add all CLI files
git add modules/cli/
git add scripts/build-cli.sh
git add CLI-QUICKSTART.md
git add settings.gradle.kts

# Commit
git commit -m "Phase 3: Implement functional CLI with core/formats integration

- Add complete TransformCommand with XML/JSON/CSV support
- Add ValidateCommand for script checking
- Add build automation script
- Add comprehensive tests
- Add documentation and quick start guide
- Support both JAR and native binary builds
- CLI now fully functional and production-ready"

# Push
git push origin main
```

## Troubleshooting

### Build fails with "module not found"
```bash
# Ensure settings.gradle.kts includes all modules
./gradlew projects

# Should show:
# :modules:core
# :modules:cli
# :formats:xml
# :formats:json
# :formats:csv
```

### Tests fail
```bash
# Run tests with debug output
./gradlew :modules:cli:test --info

# Check test report
cat modules/cli/build/reports/tests/test/index.html
```

### Native build fails
```bash
# Verify GraalVM installation
echo $JAVA_HOME  # Should point to GraalVM
native-image --version

# Check native image config
cat modules/cli/src/main/resources/META-INF/native-image/native-image.properties
```

### CLI throws exceptions
```bash
# Enable debug mode
java -Dutlx.debug=true -jar modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar \
    transform input.xml script.utlx
```

## Success Criteria

You know it's working when:

- ✅ `./scripts/build-cli.sh` completes successfully
- ✅ `java -jar modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar version` works
- ✅ Example transformations produce correct output
- ✅ All tests pass (`./gradlew :modules:cli:test`)
- ✅ Native binary works (if built)
- ✅ Help commands show proper usage
- ✅ Validation catches errors in scripts

## Next Steps

After completing this implementation:

1. **Test in production**: Use CLI on real data
2. **Performance tuning**: Profile and optimize hot paths
3. **Phase 4**: Start implementing tooling (VS Code extension)
4. **Community**: Share CLI with users for feedback
5. **Documentation**: Add more examples and use cases

## Getting Help

If you encounter issues:

1. Check the test reports: `modules/cli/build/reports/tests/test/`
2. Enable debug mode: `-Dutlx.debug=true`
3. Review the build output carefully
4. Check GitHub issues: https://github.com/grauwen/utl-x/issues
5. Consult the documentation in `docs/`

---

**You're ready to implement!** Follow these steps, and you'll have a fully functional CLI integrated with your core and formats modules. 🚀
