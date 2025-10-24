# UTL-X Standard Library Integration

This document describes the integration of the comprehensive UTL-X Standard Library into the CLI and interpreter runtime.

## Overview

The UTL-X Standard Library has been successfully integrated into the core interpreter and CLI, providing access to over 200+ functions across multiple domains:

- **Array Functions**: Manipulation, aggregation, joins, transformations
- **String Functions**: Text processing, regex, case conversion, formatting
- **Math Functions**: Basic math, advanced math, statistical functions
- **Date/Time Functions**: Parsing, formatting, calculations, timezones
- **Object Functions**: Manipulation, merging, key operations
- **Binary Functions**: Encoding, hashing, encryption, compression
- **CSV Functions**: Parsing and generation
- **JSON Functions**: Canonicalization and manipulation
- **JWT/JWS Functions**: Token creation and verification
- **XML Functions**: Canonicalization, serialization options
- **YAML Functions**: Processing and conversion
- **Type Functions**: Type checking and conversion
- **Core Functions**: Utility and runtime functions

## Architecture

### StandardLibraryRegistry

The `StandardLibraryRegistry` class provides:

1. **Function Registration**: Automatically registers all stdlib functions
2. **Metadata Management**: Provides function descriptions and module information
3. **Runtime Integration**: Converts between UDM and RuntimeValue types
4. **Module Organization**: Groups functions by domain/module

### Integration Points

1. **Interpreter**: Uses `StandardLibraryRegistry` instead of basic `StandardLibraryImpl`
2. **CLI**: New `functions` command for exploring available functions
3. **Runtime**: Native function call optimization for stdlib functions

## Usage

### CLI Function Discovery

```bash
# List all functions
utlx functions

# Functions in specific module
utlx functions --module string

# Search functions
utlx functions --search date

# Detailed descriptions
utlx functions --detailed
```

### UTL-X Scripts

All stdlib functions are now available in UTL-X scripts:

```utlx
// String functions
input.name | upper | trim

// Array functions  
input.numbers | sum

// Math functions
input.value | abs | round

// Date functions
now() | formatDate("yyyy-MM-dd")

// Object functions
input | merge(defaults) | pick(["name", "id"])

// JWT functions
{sub: "user123", name: "John"} | createJWT(secret, "HS256", 3600)
```

## Function Categories

### Core Functions (10+)
- `typeOf`, `isEmpty`, `isNull`, `isArray`, `isObject`
- `default`, `coalesce`, `log`, `debug`, `assert`

### String Functions (15+)
- `upper`, `lower`, `trim`, `length`, `substring`
- `indexOf`, `replace`, `split`, `join`, `contains`
- `startsWith`, `endsWith`, `repeat`, `padLeft`, `padRight`

### Array Functions (25+)
- `append`, `prepend`, `concat`, `flatten`, `reverse`
- `sort`, `unique`, `zip`, `chunk`, `partition`
- `filter`, `map`, `reduce`, `find`, `findIndex`
- `sum`, `avg`, `min`, `max`, `count`, `median`

### Math Functions (20+)
- `abs`, `ceil`, `floor`, `round`, `sqrt`, `pow`
- `sin`, `cos`, `tan`, `asin`, `acos`, `atan`
- `log`, `log10`, `exp`, `random`, `randomInt`

### Date Functions (15+)
- `now`, `today`, `parseDate`, `formatDate`
- `addDays`, `addMonths`, `addYears`, `daysBetween`
- `startOfWeek`, `endOfWeek`, `quarter`, `age`

### Object Functions (10+)
- `keys`, `values`, `entries`, `hasKey`, `get`, `set`
- `delete`, `merge`, `pick`, `omit`

### Binary/Crypto Functions (8+)
- `toBase64`, `fromBase64`, `toHex`, `fromHex`
- `hash`, `hmac`, `encrypt`, `decrypt`

### JWT/Security Functions (4+)
- `createJWT`, `verifyJWT`, `decodeJWT`, `parseJWT`

## Testing

The integration includes comprehensive tests:

```bash
# Run integration tests
./gradlew :modules:core:test --tests "*StandardLibraryIntegrationTest*"

# Test full integration
./test_stdlib_integration.sh
```

## Development

### Adding New Functions

1. Implement function in appropriate stdlib module
2. Add registration in `StandardLibraryRegistry.kt`
3. Update function metadata (name, description, module)
4. Add tests for the new function

### Function Signature

All stdlib functions follow this pattern:

```kotlin
fun functionName(args: List<UDM>): UDM {
    // Validate arguments
    // Process using UDM types
    // Return UDM result
}
```

## Error Handling

The integration provides robust error handling:

- **Argument Validation**: Type and count checking
- **Runtime Errors**: Descriptive error messages with function context
- **Graceful Degradation**: Missing functions don't crash the interpreter

## Performance

- **Native Function Calls**: Direct invocation without AST overhead
- **Type Conversion**: Efficient UDM ↔ RuntimeValue conversion
- **Lazy Registration**: Functions registered only once at startup

## Compatibility

The stdlib integration maintains full compatibility with:

- **DataWeave Functions**: Common function names and behaviors
- **UTL-X Language**: All language features continue to work
- **Existing Scripts**: No breaking changes to existing code

## Future Enhancements

- **Module Imports**: Selective function loading
- **Custom Functions**: User-defined function registration
- **Function Overloading**: Multiple signatures per function
- **Async Functions**: Support for asynchronous operations
- **REPL Integration**: Interactive function exploration

## Examples

### Data Transformation Pipeline

```utlx
input.customers
| filter(c => c.active == true)
| map(c => c | pick(["id", "name", "email"]))
| groupBy(c => c.email | split("@")[1])
| entries
| map(([domain, users]) => {
    domain: domain,
    count: users | count,
    users: users | sort("name")
})
```

### JWT Token Processing

```utlx
{
  header: token | parseJWT | get("header"),
  payload: token | verifyJWT(secret) | get("claims"),
  isValid: token | verifyJWT(secret) | get("verified")
}
```

### Date Processing

```utlx
{
  processedAt: now(),
  dueDate: $input.createdAt | parseDate | addDays(30),
  daysSinceCreated: daysBetween($input.createdAt | parseDate, now()),
  quarter: $input.createdAt | parseDate | quarter
}
```

This integration makes UTL-X a comprehensive data transformation platform with enterprise-grade functionality available through both programmatic APIs and command-line interface.