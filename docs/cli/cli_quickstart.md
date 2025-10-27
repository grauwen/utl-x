# UTL-X CLI Quick Start

Get started with UTL-X CLI in 5 minutes!

## Prerequisites

- **Java 17+** (for JAR build)
- **GraalVM 23+** (for native binary, optional)
- **Gradle** (included via wrapper)

## Quick Build

### Build JAR only (fastest)
```bash
chmod +x scripts/build-cli.sh
./scripts/build-cli.sh --jar-only
```

### Build everything including native binary
```bash
./scripts/build-cli.sh --native
```

This will:
1. ✓ Clean previous builds
2. ✓ Build core and format modules
3. ✓ Run tests
4. ✓ Build JAR with dependencies
5. ✓ Build native binary (if --native)
6. ✓ Create example files

## First Transformation

After building, try this example:

```bash
# Using JAR
java -jar modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar \
    transform \
    examples/cli-test/input.json \
    examples/cli-test/transform.utlx \
    -o output.json

# Or using native binary (if built)
./modules/cli/build/native/nativeCompile/utlx \
    transform \
    examples/cli-test/input.json \
    examples/cli-test/transform.utlx \
    -o output.json
```

## Create Your First Transform

### 1. Create input data (input.xml)
```xml
<person>
  <name>John Doe</name>
  <age>30</age>
  <email>john@example.com</email>
</person>
```

### 2. Create transform script (transform.utlx)
```utlx
%utlx 1.0
input xml
output json
---
{
  fullName: input.person.name,
  yearsOld: input.person.age,
  contact: {
    email: input.person.email
  }
}
```

### 3. Run transformation
```bash
# Using JAR
java -jar modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar \
    transform input.xml transform.utlx -o output.json

# Using helper script (after build)
./modules/cli/scripts/utlx transform input.xml transform.utlx -o output.json
```

### 4. View output (output.json)
```json
{
  "fullName": "John Doe",
  "yearsOld": 30,
  "contact": {
    "email": "john@example.com"
  }
}
```

## Common Use Cases

### XML to JSON
```bash
utlx transform order.xml to-json.utlx --output-format json -o order.json
```

### JSON to XML
```bash
utlx transform data.json to-xml.utlx --output-format xml -o data.xml
```

### CSV to JSON
```bash
utlx transform customers.csv to-json.utlx -o customers.json
```

### Pipe-friendly processing
```bash
curl https://api.example.com/data.xml | utlx transform script.utlx | jq .
```

### Batch processing
```bash
for file in input/*.xml; do
    utlx transform "$file" transform.utlx -o "output/$(basename $file .xml).json"
done
```

## Validate Scripts

Before running transformations, validate your scripts:

```bash
utlx validate transform.utlx
utlx validate transform.utlx --verbose
utlx validate *.utlx --strict  # CI mode
```

## Format Scripts

Keep your scripts clean:

```bash
utlx format transform.utlx              # Format in-place
utlx format transform.utlx --check      # Check only (CI)
```

## Performance Tips

### Use native binary for CLI
- **Native binary**: ~10ms startup, ~20MB memory
- **JAR**: ~200ms startup, ~150MB memory

For one-off commands, use native. For library use, use JAR.

### Enable verbose mode for debugging
```bash
utlx transform input.xml script.utlx -v -o output.json
```

### Disable pretty-printing for speed
```bash
utlx transform input.xml script.utlx --no-pretty -o output.json
```

## Install System-Wide

### Linux/macOS
```bash
# Build native binary first
./scripts/build-cli.sh --native

# Install to /usr/local/bin
sudo cp modules/cli/build/native/nativeCompile/utlx /usr/local/bin/
sudo chmod +x /usr/local/bin/utlx

# Now use from anywhere
utlx --version
utlx transform mydata.xml script.utlx
```

### Windows
1. Build JAR: `gradlew :modules:cli:jar`
2. Create batch file in PATH:
```batch
@echo off
java -jar C:\path\to\utl-x\modules\cli\build\libs\cli-1.0.0-SNAPSHOT.jar %*
```

## Troubleshooting

### "Error: Could not find or load main class"
```bash
# Rebuild with dependencies
./gradlew :modules:cli:jar
```

### Native build fails
```bash
# Install GraalVM
./scripts/install-graalvm.sh

# Verify installation
echo $JAVA_HOME
native-image --version
```

### "Unsupported format"
```bash
# Specify format explicitly
utlx transform input.dat script.utlx --input-format xml
```

### Debug mode
```bash
java -Dutlx.debug=true -jar modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar \
    transform input.xml script.utlx
```

## Next Steps

1. **Read the docs**: `docs/getting-started/`
2. **Explore examples**: `examples/`
3. **Check language guide**: `docs/language-guide/`
4. **Join community**: GitHub Discussions

## Getting Help

- **Documentation**: `docs/README.md`
- **Examples**: `examples/`
- **Issues**: https://github.com/grauwen/utl-x/issues
- **Discussions**: https://github.com/grauwen/utl-x/discussions

## What's Next?

Now that you have a working CLI:

1. ✓ Try the examples in `examples/cli-test/`
2. ✓ Read the language guide: `docs/language-guide/overview.md`
3. ✓ Create your own transformations
4. ✓ Contribute to the project!

Happy transforming! 🚀
