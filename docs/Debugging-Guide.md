# UTL-X Debugging Guide

This guide explains how to use the UTL-X logging and debugging system to troubleshoot transformations and understand the internal execution flow.

## Overview

UTL-X includes a comprehensive logging system built on industry-standard frameworks:
- **Kotlin Logging (μlog)**: Idiomatic Kotlin wrapper with lazy evaluation
- **SLF4J**: Standard logging API
- **Logback**: Modern, performant logging implementation

## Quick Start

### Enable Debug Logging

**Via CLI flags** (recommended for interactive use):
```bash
# Enable DEBUG for all components
./utlx transform script.utlx input.json --debug

# Enable DEBUG for specific component
./utlx transform script.utlx input.json --debug-parser
./utlx transform script.utlx input.json --debug-lexer
./utlx transform script.utlx input.json --debug-interpreter

# Combine multiple components
./utlx transform script.utlx input.json --debug-parser --debug-lexer

# Enable TRACE level (most verbose)
./utlx transform script.utlx input.json --trace
```

**Via environment variables** (recommended for scripts/automation):
```bash
# Set global log level
export UTLX_LOG_LEVEL=DEBUG
./utlx transform script.utlx input.json

# Enable specific component
export UTLX_DEBUG_PARSER=true
./utlx transform script.utlx input.json

# Multiple components
export UTLX_DEBUG_LEXER=true
export UTLX_DEBUG_PARSER=true
export UTLX_DEBUG_INTERPRETER=true
./utlx transform script.utlx input.json
```

**Via configuration file** (recommended for persistent settings):
```properties
# Create: ./utlx-logging.properties or ~/.utlx/logging.properties
utlx.log.level=DEBUG
utlx.log.parser=DEBUG
utlx.log.lexer=INFO
utlx.log.interpreter=TRACE
```

## Log Levels

UTL-X supports standard log levels, from least to most verbose:

1. **ERROR** - Only errors (default for production)
2. **WARN** - Warnings and errors
3. **INFO** - Informational messages, warnings, and errors (default)
4. **DEBUG** - Detailed execution flow (recommended for debugging)
5. **TRACE** - Very detailed internal state (for deep troubleshooting)
6. **OFF** - Disable logging completely

## Components

UTL-X logging is organized by component, allowing fine-grained control:

### LEXER
Tokenization and lexical analysis.

**When to enable:**
- Investigating syntax errors
- Understanding how source code is tokenized
- Debugging string parsing or special character issues

**Example output:**
```
DEBUG o.apache.utlx.core.lexer.Lexer_impl - Starting tokenization, source length: 102
DEBUG o.apache.utlx.core.lexer.Lexer_impl - Tokenization complete, generated 24 tokens
```

**CLI flag:** `--debug-lexer`
**Environment variable:** `UTLX_DEBUG_LEXER=true`

### PARSER
Abstract Syntax Tree (AST) construction.

**When to enable:**
- Understanding parse errors
- Investigating expression evaluation order
- Debugging complex nested structures

**Example output:**
```
DEBUG o.a.utlx.core.parser.Parser_impl - Starting parse, 24 tokens
DEBUG o.a.utlx.core.parser.Parser_impl - Parse completed successfully
```

**CLI flag:** `--debug-parser`
**Environment variable:** `UTLX_DEBUG_PARSER=true`

### INTERPRETER
Runtime execution and evaluation.

**When to enable:**
- Tracking transformation execution
- Understanding data flow
- Debugging runtime errors

**Example output:**
```
DEBUG o.a.u.core.interpreter.Interpreter - Starting execution with 1 input(s): input
DEBUG o.a.u.core.interpreter.Interpreter - Evaluating transformation body
DEBUG o.a.u.core.interpreter.Interpreter - Execution completed, result type: ObjectValue
```

**CLI flag:** `--debug-interpreter`
**Environment variable:** `UTLX_DEBUG_INTERPRETER=true`

### TYPE_SYSTEM
Type checking and inference.

**When to enable:**
- Investigating type errors
- Understanding type inference

**CLI flag:** `--debug-types`
**Environment variable:** `UTLX_DEBUG_TYPE_SYSTEM=true`

### UDM
Universal Data Model operations.

**When to enable:**
- Debugging format conversions
- Understanding internal data representation

**Environment variable:** `UTLX_DEBUG_UDM=true`

### AST
Abstract Syntax Tree operations.

**When to enable:**
- Understanding AST structure
- Debugging AST transformations

**Environment variable:** `UTLX_DEBUG_AST=true`

### FORMATS
Format parsers and serializers (XML, JSON, CSV, YAML).

**When to enable:**
- Debugging input parsing
- Investigating output serialization

**Environment variable:** `UTLX_DEBUG_FORMATS=true`

## Common Debugging Scenarios

### 1. Parse Error - Can't Find the Problem

```bash
# Enable lexer and parser debugging
./utlx transform script.utlx input.json --debug-lexer --debug-parser
```

This shows:
1. How the source is tokenized
2. Where parsing fails

### 2. Transformation Produces Wrong Output

```bash
# Enable interpreter debugging
./utlx transform script.utlx input.json --debug-interpreter
```

This shows:
1. Execution flow
2. Input bindings
3. Result types

### 3. Performance Issues

```bash
# Enable all components with timestamps
./utlx transform script.utlx input.json --debug --verbose
```

Log timestamps show execution timing for each phase.

### 4. Complete Execution Trace

```bash
# Enable TRACE level for maximum verbosity
./utlx transform script.utlx input.json --trace
```

**Warning:** TRACE level generates significant output. Use for small test cases only.

## Configuration Priority

When multiple configuration methods are used, this is the priority order (highest to lowest):

1. **CLI flags** (`--debug`, `--debug-parser`, etc.)
2. **Environment variables** (`UTLX_LOG_LEVEL`, `UTLX_DEBUG_PARSER`, etc.)
3. **Local config file** (`./utlx-logging.properties`)
4. **User config file** (`~/.utlx/logging.properties`)
5. **Default** (INFO level for all components)

## Programmatic Configuration

For tests or embedded usage:

```kotlin
import org.apache.utlx.core.debug.DebugConfig

// Set global level
DebugConfig.setGlobalLogLevel(DebugConfig.LogLevel.DEBUG)

// Enable specific component
DebugConfig.enableComponent(DebugConfig.Component.PARSER)

// Set component to specific level
DebugConfig.setComponentLogLevel(
    DebugConfig.Component.LEXER,
    DebugConfig.LogLevel.TRACE
)

// Check if debug is enabled
if (DebugConfig.isDebugEnabled(DebugConfig.Component.PARSER)) {
    // ... expensive debug operation
}

// Print current configuration
DebugConfig.printConfiguration()

// Reset to defaults
DebugConfig.reset()
```

## Log Output Format

Logs use colored output for readability:

```
HH:mm:ss.SSS [LEVEL] logger-name - message
```

Example:
```
11:35:09.793 DEBUG o.a.utlx.core.parser.Parser_impl - Starting parse, 24 tokens
```

Colors:
- **ERROR**: Red
- **WARN**: Yellow
- **INFO**: Blue
- **DEBUG**: Default
- **TRACE**: Gray

## Logging to File

To also log to a file, uncomment the FILE appender in logback.xml:

```xml
<root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="FILE"/>  <!-- Uncomment this line -->
</root>
```

Logs are written to: `logs/utlx-debug.log` with 7-day rotation.

## Troubleshooting the Logging System

### Logs Not Appearing

**Check 1:** Verify the log level
```bash
# Ensure DEBUG is enabled
./utlx transform script.utlx input.json --debug 2>&1 | grep DEBUG
```

**Check 2:** Ensure stderr isn't redirected
```bash
# Logs go to stderr, not stdout
./utlx transform script.utlx input.json --debug 2>&1
```

**Check 3:** Check for conflicting environment variables
```bash
# Unset all UTL-X env vars
unset UTLX_LOG_LEVEL UTLX_DEBUG_PARSER UTLX_DEBUG_LEXER
```

### Too Much Output

```bash
# Enable only specific components
./utlx transform script.utlx input.json --debug-parser

# Or reduce to INFO level
export UTLX_LOG_LEVEL=INFO
```

### Performance Impact

Debug logging has minimal performance impact due to:
- Lazy evaluation (lambdas in Kotlin Logging)
- Level checks before expensive operations
- Efficient Logback implementation

**Typical overhead:** <5% for DEBUG level, <10% for TRACE level

## Best Practices

1. **Start specific, then broaden**
   ```bash
   # Start with one component
   ./utlx transform script.utlx input.json --debug-parser

   # If needed, add more
   ./utlx transform script.utlx input.json --debug-parser --debug-lexer
   ```

2. **Use environment variables for CI/CD**
   ```bash
   # In your CI script
   export UTLX_LOG_LEVEL=WARN
   ./run-tests.sh
   ```

3. **Use config files for development**
   ```bash
   # Create ./utlx-logging.properties with your preferred settings
   # No need to remember CLI flags
   ./utlx transform script.utlx input.json
   ```

4. **Disable logging in production**
   ```bash
   export UTLX_LOG_LEVEL=ERROR
   # Or remove config files
   ```

## Examples

### Debug a Parse Error

```bash
$ ./utlx transform bad-script.utlx input.json --debug-parser

11:35:09.793 DEBUG o.a.utlx.core.parser.Parser_impl - Starting parse, 24 tokens
11:35:09.802 ERROR o.a.utlx.core.parser.Parser_impl - Parse exception: Expected ':' after property name
org.apache.utlx.core.parser.ParseException: Expected ':' after property name
    at org.apache.utlx.core.parser.Parser.consume(parser_impl.kt:790)
    ...
```

### Debug Slow Transformation

```bash
$ time ./utlx transform script.utlx large-file.xml --debug

11:35:09.787 DEBUG o.apache.utlx.core.lexer.Lexer_impl - Starting tokenization, source length: 1024000
11:35:09.792 DEBUG o.apache.utlx.core.lexer.Lexer_impl - Tokenization complete, generated 52000 tokens
11:35:09.793 DEBUG o.a.utlx.core.parser.Parser_impl - Starting parse, 52000 tokens
11:35:10.205 DEBUG o.a.utlx.core.parser.Parser_impl - Parse completed successfully
11:35:10.206 DEBUG o.a.u.core.interpreter.Interpreter - Starting execution with 1 input(s)
11:35:15.819 DEBUG o.a.u.core.interpreter.Interpreter - Execution completed

real    0m6.045s
```
*Shows that execution (5.6s) is the bottleneck, not parsing (0.4s)*

### Verify Configuration

```bash
$ cat > /tmp/test-debug.kts <<'EOF'
import org.apache.utlx.core.debug.DebugConfig
DebugConfig.printConfiguration()
EOF

$ kotlin -cp modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar /tmp/test-debug.kts

=== UTL-X Debug Configuration ===
  LEXER               : INFO
  PARSER              : DEBUG
  INTERPRETER         : INFO
  TYPE_SYSTEM         : INFO
  UDM                 : INFO
  AST                 : INFO
  FORMATS             : INFO
  ROOT                : INFO
=================================
```

## See Also

- [Enhanced Function Annotations](Enhanced-Function-Annotations.md) - Function documentation system
- [UTL-X Language Specification](../README.md) - Language syntax and semantics
- [Contributing Guidelines](../CONTRIBUTING.md) - How to contribute with proper logging

## Support

If you encounter issues with the logging system:
1. Check this guide for troubleshooting steps
2. Verify your configuration with `DebugConfig.printConfiguration()`
3. Report issues at: https://github.com/grauwen/utl-x/issues
