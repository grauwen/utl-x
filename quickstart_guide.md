# UTL-X Quick Start Guide

## Prerequisites

- JDK 17 or higher
- Git

## Clone and Build

```bash
# Clone the repository
git clone https://github.com/grauwen/utl-x.git
cd utl-x

# Build all modules
./gradlew build

# Run tests
./gradlew test

# See project structure
./gradlew projectStructure
```

## Running Your First Transformation

### 1. Create a UTL-X transformation file

Create `hello.utlx`:

```utlx
%utlx 1.0
input json
output json
---
{
  message: "Hello, " + input.name,
  timestamp: now()
}
```

### 2. Use the Core Components Programmatically

```kotlin
import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.parser.ParseResult

fun main() {
    val source = """
        %utlx 1.0
        input json
        output json
        ---
        {
          message: "Hello, " + input.name
        }
    """.trimIndent()
    
    // Tokenize
    val lexer = Lexer(source)
    val tokens = lexer.tokenize()
    println("Tokens: ${tokens.size}")
    
    // Parse
    val parser = Parser(tokens)
    when (val result = parser.parse()) {
        is ParseResult.Success -> {
            println("✓ Parsed successfully!")
            println("  Input: ${result.program.header.inputFormat.type}")
            println("  Output: ${result.program.header.outputFormat.type}")
        }
        is ParseResult.Failure -> {
            println("✗ Parse failed:")
            result.errors.forEach { 
                println("  - ${it.message} at ${it.location}")
            }
        }
    }
}
```

### 3. Working with the Universal Data Model (UDM)

```kotlin
import org.apache.utlx.core.udm.*

fun main() {
    // Create an order structure (simulating parsed XML)
    val order = UDM.Object.withAttributes(
        properties = mapOf(
            "Customer" to UDM.Object.of(
                "Name" to UDM.Scalar.string("Alice Johnson"),
                "Email" to UDM.Scalar.string("alice@example.com")
            ),
            "Items" to UDM.Array.of(
                UDM.Object.of(
                    "sku" to UDM.Scalar.string("WIDGET-001"),
                    "price" to UDM.Scalar.number(75.00),
                    "quantity" to UDM.Scalar.number(2)
                ),
                UDM.Object.of(
                    "sku" to UDM.Scalar.string("GADGET-002"),
                    "price" to UDM.Scalar.number(150.00),
                    "quantity" to UDM.Scalar.number(1)
                )
            ),
            "Total" to UDM.Scalar.number(300.00)
        ),
        attributes = mapOf(
            "id" to "ORD-001",
            "date" to "2025-10-01"
        )
    )
    
    // Navigate like in UTL-X transformations
    val navigator = UDMNavigator(order)
    
    println("Order ID: ${navigator.navigate("@id")?.asScalar()?.asString()}")
    println("Customer: ${navigator.navigate("Customer.Name")?.asScalar()?.asString()}")
    println("Total: ${navigator.navigate("Total")?.asScalar()?.asNumber()}")
    
    // Get all SKUs using wildcard
    val skus = navigator.navigate("Items[*].sku") as? UDM.Array
    println("\nAll SKUs:")
    skus?.elements?.forEach { 
        println("  - ${it.asScalar()?.asString()}")
    }
}
```

## Module Overview

### Currently Implemented

- ✅ **modules/core** - Core language components
  - UDM (Universal Data Model)
  - Lexer (tokenization)
  - Parser (AST generation)
  - AST node definitions

### Planned Implementation

- ⏳ **modules/core** - Additional core features
  - Type system and type checker
  - Optimizer
  - Code generator
  
- 🔜 **modules/jvm** - JVM runtime
  - Runtime execution engine
  - JVM bytecode generation
  - Java API

- 🔜 **modules/javascript** - JavaScript runtime
  - JS code generation
  - Node.js integration
  - Browser support

- 🔜 **formats/** - Format parsers
  - XML parser/serializer
  - JSON parser/serializer
  - CSV parser/serializer

- 🔜 **stdlib** - Standard library functions
  - String functions
  - Array functions
  - Math functions
  - Date functions

## Development Workflow

### Running Tests

```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :core:test

# Run tests with detailed output
./gradlew test --info

# Generate test report
./gradlew aggregateTestReports
# Open build/reports/tests/index.html
```

### Building

```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :core:build

# Build without tests (faster)
./gradlew build -x test

# Clean build
./gradlew clean build
```

### Code Style

```bash
# Check code style (when configured)
./gradlew ktlintCheck

# Auto-format code (when configured)
./gradlew ktlintFormat
```

## Project Structure

```
utl-x/
├── modules/
│   └── core/                    ✅ Implemented
│       ├── src/main/kotlin/
│       │   └── org/apache/utlx/core/
│       │       ├── ast/         # AST nodes
│       │       ├── lexer/       # Tokenization
│       │       ├── parser/      # Parsing
│       │       └── udm/         # Universal Data Model
│       └── src/test/kotlin/     # Tests
├── docs/                        ✅ Documentation exists
├── examples/                    ✅ Examples exist
└── build.gradle.kts            ✅ Build configured
```

## Next Steps for Contributors

### Phase 1: Complete Core Module (Current Phase)

1. **Type System Implementation**
   - Define type inference rules
   - Implement type checker
   - Add type error reporting

2. **Optimizer**
   - Constant folding
   - Dead code elimination
   - Common subexpression elimination

3. **Code Generator**
   - Generate JVM bytecode
   - Support for standard library calls

### Phase 2: Runtime & Formats

4. **JVM Runtime Module**
   - Execution engine
   - Java API
   - Framework integrations (Spring, Camel)

5. **Format Parsers**
   - XML (with namespace support)
   - JSON
   - CSV (with dialect support)

### Phase 3: Tooling

6. **CLI Tool**
   - Command-line interface
   - File watching
   - Interactive mode

7. **IDE Plugins**
   - VS Code extension
   - IntelliJ IDEA plugin

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for detailed contribution guidelines.

### Quick Contribution Tips

1. **Fork and clone** the repository
2. **Create a feature branch**: `git checkout -b feature/my-feature`
3. **Write tests** for your changes
4. **Run tests**: `./gradlew test`
5. **Submit a pull request**

## Getting Help

- 📖 [Full Documentation](docs/README.md)
- 💬 [Discussions](https://github.com/grauwen/utl-x/discussions)
- 🐛 [Issue Tracker](https://github.com/grauwen/utl-x/issues)
- 📧 info@utlx-lang.org

## License

Dual-licensed under:
- GNU Affero General Public License v3.0 (AGPL-3.0) - Open source
- Commercial License - For proprietary use

See [LICENSE.md](LICENSE.md) for details.
