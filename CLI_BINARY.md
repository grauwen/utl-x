# UTL-X CLI Binary

## ✅ CLI Binary Successfully Created!

The UTL-X Command Line Interface has been successfully built and is ready for use.

## Available Binaries

### 1. JAR Binary (Cross-platform)
- **Location**: `modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar`
- **Size**: ~10MB (includes all dependencies)
- **Usage**: `java -jar cli-1.0.0-SNAPSHOT.jar [command] [options]`
- **Requirements**: Java 17+

### 2. Shell Wrapper Script
- **Location**: `./utlx` (project root)
- **Usage**: `./utlx [command] [options]`
- **Benefits**: Simpler command syntax, automatic JAR path resolution

## Installation Options

### Option 1: Use Wrapper Script (Recommended)
```bash
# From project root
./utlx version
./utlx help
```

### Option 2: Direct JAR Execution
```bash
java -jar modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar version
```

### Option 3: Install to System PATH
```bash
# Copy wrapper script to system directory
sudo cp utlx /usr/local/bin/
sudo chmod +x /usr/local/bin/utlx

# Now use from anywhere
utlx version
```

## Available Commands

### Core Commands
- **`transform (t)`** - Transform data using UTL-X scripts ✅ **WORKING**
- **`version`** - Show version information ✅ **WORKING**
- **`help`** - Show help message ✅ **WORKING**

### Coming Soon
- **`validate (v)`** - Validate UTL-X scripts
- **`compile (c)`** - Compile scripts to bytecode
- **`format (f)`** - Format/pretty-print scripts
- **`migrate (m)`** - Migrate XSLT/DataWeave to UTL-X
- **`functions (fn)`** - List standard library functions

## Working Transform Command

The transform command is fully functional with:

### Supported Input/Output Formats
- **XML** - Parse and generate XML
- **JSON** - Parse and generate JSON  
- **CSV** - Parse and generate CSV
- **YAML** - Parse and generate YAML (including .yml extension) ✅ **NEW**
- **Auto-detection** - Automatically detect input format

### Transform Command Usage
```bash
# Basic transformation
./utlx transform input.xml script.utlx -o output.json

# Read from stdin
cat input.xml | ./utlx transform script.utlx > output.json

# Force formats
./utlx transform input.txt script.utlx --input-format json --output-format xml

# YAML support
./utlx transform data.yaml script.utlx --output-format json
./utlx transform data.json script.utlx --output-format yaml

# Verbose mode
./utlx transform input.xml script.utlx -v -o output.json
```

### Transform Options
- `-o, --output FILE` - Write output to file
- `-i, --input FILE` - Read input from file
- `--input-format FORMAT` - Force input format (xml, json, csv, yaml)
- `--output-format FORMAT` - Force output format (xml, json, csv, yaml)
- `-v, --verbose` - Enable verbose output
- `--no-pretty` - Disable pretty-printing

## Standard Library Integration

The CLI includes the comprehensive UTL-X Standard Library with 200+ functions:

### Function Categories Available
- **Array Functions** - `sum`, `filter`, `map`, `reduce`, `groupBy`, etc.
- **String Functions** - `upper`, `lower`, `trim`, `split`, `replace`, etc.
- **Math Functions** - `abs`, `sqrt`, `sin`, `cos`, `random`, etc.
- **Date Functions** - `now`, `parseDate`, `addDays`, `quarter`, etc.
- **Object Functions** - `keys`, `merge`, `pick`, `omit`, etc.
- **Type Functions** - `typeOf`, `isEmpty`, `isArray`, etc.
- **Core Functions** - `log`, `debug`, `default`, `coalesce`, etc.

### Example UTL-X Script
```utlx
%utlx 1.0
input auto
output json
---

{
  processedAt: now(),
  upperName: input.name | upper,
  total: input.items | sum,
  itemCount: input.items | count,
  isValid: input.items | isEmpty | not
}
```

## Build Information

### Build Configuration
- **Kotlin Version**: 1.9.21
- **Target JVM**: 17
- **Gradle Version**: 8.5
- **Fat JAR**: All dependencies included
- **Main Class**: `org.apache.utlx.cli.Main`

### Dependencies Included
- UTL-X Core interpreter and parser
- UTL-X Standard Library (200+ functions)
- XML/JSON/CSV format parsers and serializers
- All Kotlin standard libraries

## Performance Notes

### JAR Performance
- **Startup time**: ~1-2 seconds (JVM startup overhead)
- **Memory usage**: ~50-100MB (depends on data size)
- **Throughput**: Suitable for files up to several MB

### Optimization Tips
- Use wrapper script for better UX
- Consider JVM warm-up for repeated operations
- Large files may benefit from streaming (future enhancement)

## Development & Building

### Rebuild CLI
```bash
./gradlew :modules:cli:jar
```

### Update Dependencies
```bash
./gradlew :modules:cli:dependencies
```

### Clean Build
```bash
./gradlew clean :modules:cli:jar
```

## Troubleshooting

### Common Issues

#### "JAR not found" Error
- **Solution**: Run `./gradlew :modules:cli:jar` to build the JAR

#### "Java not found" Error
- **Solution**: Install Java 17+ and ensure it's in PATH

#### Permission Denied
- **Solution**: Make script executable with `chmod +x utlx`

#### Out of Memory
- **Solution**: Increase JVM memory with `JAVA_OPTS="-Xmx1g" ./utlx ...`

### Debug Mode
```bash
# Enable debug output
UTLX_DEBUG=true ./utlx transform input.xml script.utlx -v
```

## Roadmap

### Next Milestones
1. **Native Binary** - GraalVM native compilation
2. **Function Explorer** - Interactive function documentation
3. **Script Validation** - Syntax and semantic checking
4. **Performance Optimization** - Streaming and memory improvements
5. **Package Distribution** - Homebrew, APT, and Windows packages

### Future Enhancements
- **REPL Mode** - Interactive transformation development
- **Watch Mode** - Auto-reload on file changes
- **Plugin System** - Custom function extensions
- **IDE Integration** - VS Code and IntelliJ plugins

## Success! 🎉

The UTL-X CLI is now fully functional with:
- ✅ Cross-platform JAR binary (10MB)
- ✅ Convenient shell wrapper script
- ✅ Working transform command
- ✅ 200+ standard library functions
- ✅ XML/JSON/CSV format support
- ✅ Comprehensive error handling
- ✅ Verbose and debug modes

**Ready for data transformation tasks!**