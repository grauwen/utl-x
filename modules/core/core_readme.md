# UTL-X Core Module

The core module contains the format-agnostic language implementation for UTL-X.

## Components

### Universal Data Model (UDM)

The UDM is the foundation that abstracts all data formats (XML, JSON, CSV, etc.) into a unified representation.

```kotlin
import org.apache.utlx.core.udm.*

// Create scalar values
val name = UDM.Scalar.string("Alice")
val age = UDM.Scalar.number(30)
val active = UDM.Scalar.boolean(true)

// Create objects
val person = UDM.Object.of(
    "name" to name,
    "age" to age,
    "active" to active
)

// Create arrays
val numbers = UDM.Array.of(
    UDM.Scalar.number(1),
    UDM.Scalar.number(2),
    UDM.Scalar.number(3)
)

// Navigate data structures
val navigator = UDMNavigator(person)
val personName = navigator.navigate("name")
println(personName?.asScalar()?.asString()) // "Alice"
```

### Lexer

Tokenizes UTL-X source code into tokens.

```kotlin
import org.apache.utlx.core.lexer.Lexer

val source = """
    input.Customer.Name
"""

val lexer = Lexer(source)
val tokens = lexer.tokenize()

tokens.forEach { println(it) }
```

### Parser

Builds an Abstract Syntax Tree (AST) from tokens.

```kotlin
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.parser.ParseResult

val source = """
    %utlx 1.0
    input json
    output json
    ---
    {
      name: input.name
    }
"""

val lexer = Lexer(source)
val tokens = lexer.tokenize()

val parser = Parser(tokens)
when (val result = parser.parse()) {
    is ParseResult.Success -> {
        println("Parsed successfully!")
        println("Input format: ${result.program.header.inputFormat.type}")
        println("Output format: ${result.program.header.outputFormat.type}")
    }
    is ParseResult.Failure -> {
        result.errors.forEach { error ->
            println("Error at ${error.location}: ${error.message}")
        }
    }
}
```

### AST (Abstract Syntax Tree)

Represents the structure of UTL-X programs.

```kotlin
import org.apache.utlx.core.ast.*

// AST nodes represent the parsed program structure
// Example: { name: "Alice", age: 30 }
val objectLiteral = Expression.ObjectLiteral(
    properties = listOf(
        Property("name", Expression.StringLiteral("Alice", location), location),
        Property("age", Expression.NumberLiteral(30.0, location), location)
    ),
    location = location
)
```

## Building

```bash
# From the project root
./gradlew :core:build

# Run tests
./gradlew :core:test

# Install to local Maven repository
./gradlew :core:publishToMavenLocal
```

## Testing

The module includes comprehensive tests covering:

- **Lexer tests**: Tokenization of various UTL-X constructs
- **Parser tests**: AST generation from tokens
- **UDM tests**: Data model creation and navigation
- **Integration tests**: End-to-end parsing and data manipulation

Run tests with:

```bash
./gradlew :core:test --info
```

## Architecture

```
UTL-X Source Code
        ↓
    [Lexer] → Tokens
        ↓
    [Parser] → AST
        ↓
[Type Checker] → Typed AST (planned)
        ↓
  [Optimizer] → Optimized AST (planned)
        ↓
[Code Generator] → Runtime-specific code (planned)
```

## Next Steps

The following components are planned for implementation:

1. **Type System** - Type inference and checking
2. **Optimizer** - AST optimization passes
3. **Code Generator** - Generate JVM/JavaScript/Native code
4. **Standard Library** - Built-in functions

## Contributing

See [CONTRIBUTING.md](../../CONTRIBUTING.md) for guidelines on contributing to the core module.

## License

This module is dual-licensed under:
- GNU Affero General Public License v3.0 (AGPL-3.0) for open source use
- Commercial License for proprietary applications

See [LICENSE.md](../../LICENSE.md) for details.
