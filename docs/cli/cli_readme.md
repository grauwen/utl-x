# UTL-X CLI - Command Line Interface

The UTL-X CLI is a practical command-line tool for transforming data between formats using UTL-X transformation scripts.

## Features

- **Transform**: Convert data between XML, JSON, and CSV formats
- **Validate**: Check UTL-X scripts for syntax and type errors
- **Format**: Pretty-print and standardize UTL-X scripts
- **Multiple output formats**: JAR (JVM) and native binary (GraalVM)
- **Pipe-friendly**: Read from stdin, write to stdout
- **Auto-detection**: Automatically detect input/output formats

## Building

### Build JAR (requires JDK 17+)
```bash
./gradlew :modules:cli:jar
```

The JAR will be created at `modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar`

### Build Native Binary (requires GraalVM)
```bash
# Install GraalVM first (see scripts/install-graalvm.sh)
./gradlew :modules:cli:nativeCompile
```

The native binary will be created at `modules/cli/build/native/nativeCompile/utlx`

### Build Both
```bash
./gradlew :modules:cli:buildAll
```

## Running

### Using JAR
```bash
java -jar modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar transform input.xml script.utlx
```

### Using Helper Script
```bash
# After building, scripts are created automatically
./modules/cli/scripts/utlx transform input.xml script.utlx
```

### Using Native Binary
```bash
./modules/cli/build/native/nativeCompile/utlx transform input.xml script.utlx
```

### Install Native Binary (Unix/Linux/macOS)
```bash
sudo ./gradlew :modules:cli:installNative
# Now you can use 'utlx' from anywhere
utlx transform input.xml script.utlx
```

## Usage Examples

### Transform XML to JSON
```bash
utlx transform input.xml transform.utlx -o output.json
```

### Read from stdin, write to stdout
```bash
cat input.xml | utlx transform script.utlx > output.json
```

### Force output format
```bash
utlx transform input.json script.utlx --output-format xml -o output.xml
```

### Validate scripts
```bash
utlx validate script.utlx
utlx validate script.utlx --verbose
utlx validate script.utlx --strict  # Warnings are errors
```

### Format scripts
```bash
utlx format script.utlx              # Format in-place
utlx format script.utlx --check      # Check if formatted (CI)
```

### Verbose mode
```bash
utlx transform input.xml script.utlx -v -o output.json
```

## Commands

### transform (t)
Transform data using a UTL-X script

```bash
utlx transform [input-file] <script-file> [options]
```

Options:
- `-o, --output FILE`: Write output to FILE (default: stdout)
- `-i, --input FILE`: Read input from FILE
- `--input-format FORMAT`: Force input format (xml, json, csv)
- `--output-format FORMAT`: Force output format (xml, json, csv)
- `-v, --verbose`: Enable verbose output
- `--no-pretty`: Disable pretty-printing

### validate (v)
Validate UTL-X scripts

```bash
utlx validate <script-file>... [options]
```

Options:
- `-v, --verbose`: Show detailed validation information
- `--strict`: Treat warnings as errors

### format (f)
Format/pretty-print UTL-X scripts

```bash
utlx format <script-file>... [options]
```

Options:
- `--check`: Check if files are formatted (exit 1 if not)
- `-v, --verbose`: Show all files processed

### version
Show version information

```bash
utlx version
utlx version --verbose  # Show detailed build info
```

## Performance

### JAR vs Native

| Metric | JAR (JVM) | Native (GraalVM) |
|--------|-----------|------------------|
| Startup time | ~200ms | ~10ms |
| Memory usage | ~150MB | ~20MB |
| Transform speed | Same | Same |
| Distribution size | 15MB | 40MB |

**Recommendation:**
- Use **native binary** for CLI usage (faster startup)
- Use **JAR** for library integration or when GraalVM is not available

## Integration with Core and Formats

The CLI integrates with:
- `modules/core`: Lexer, parser, type checker, interpreter
- `formats/xml`: XML parsing and serialization
- `formats/json`: JSON parsing and serialization
- `formats/csv`: CSV parsing and serialization

## Development

### Add dependencies
Edit `modules/cli/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":modules:core"))
    implementation(project(":formats:xml"))
    implementation(project(":formats:json"))
    implementation(project(":formats:csv"))
}
```

### Run tests
```bash
./gradlew :modules:cli:test
```

### Debug mode
```bash
java -Dutlx.debug=true -jar modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar transform input.xml script.utlx
```

## Troubleshooting

### "Error: Could not find or load main class"
Make sure you built the JAR with dependencies:
```bash
./gradlew :modules:cli:jar
```

### Native image build fails
Ensure GraalVM is properly installed:
```bash
./scripts/install-graalvm.sh
echo $JAVA_HOME  # Should point to GraalVM
```

### "Unsupported input format"
Specify the format explicitly:
```bash
utlx transform input.dat script.utlx --input-format xml
```

## Contributing

See [CONTRIBUTING.md](../../CONTRIBUTING.md) for guidelines.

## License

UTL-X is dual-licensed under:
- **AGPL-3.0** for open source projects
- **Commercial License** for proprietary use

See [LICENSE.md](../../LICENSE.md) for details.
