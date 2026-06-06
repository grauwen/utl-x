# UTL-X Project Directory Structure

> **Canonical bundle format:** for the **deployable/editable bundle** layout specifically
> (`.utlxp`/`.utlar`, `transformations/<tx>/…`, `transform.yaml`, `schemas/`, test inputs), see
> **[Bundle Format](../bundle-format.md)**. This doc covers the broader **source-repo** structure.

Complete directory structure following best practices for a multi-runtime language implementation.

## Overview

UTL-X uses a **monorepo structure** with separate modules for each runtime while sharing common components.

```
utl-x/
├── .github/                    # GitHub-specific files
├── docs/                       # Documentation (already created)
├── examples/                   # Example transformations (already created)
├── modules/                    # Core modules (multi-runtime)
│   ├── core/                   # Language core (shared)
│   ├── analysis/               # Implements schema handling design-time (shared)
│   ├── jvm/                    # JVM runtime
│   ├── javascript/             # JavaScript runtime
│   ├── native/                 # Native runtime (GraalVM/LLVM)
│   └── cli/                    # Command-line interface
├── formats/                    # Format parsers/serializers
├── stdlib/                     # Standard library
├── tools/                      # Development tools
├── build/                      # Build outputs (gitignored)
├── dist/                       # Distribution packages (gitignored)
└── scripts/                    # Build and utility scripts
```

## Complete Directory Structure

```
utl-x/
│
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                      # CI/CD pipeline
│   │   ├── release.yml                 # Release automation
│   │   ├── docs.yml                    # Documentation build
│   │   └── security.yml                # Security scanning
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.md
│   │   └── feature_request.md
│   ├── PULL_REQUEST_TEMPLATE.md
│   ├── CODEOWNERS                      # Code ownership
│   └── dependabot.yml                  # Dependency updates
│
├── docs/                               # Documentation (as created)
│   ├── getting-started/
│   ├── language-guide/
│   ├── formats/
│   ├── examples/
│   ├── reference/
│   ├── architecture/
│   ├── comparison/
│   ├── community/
│   └── README.md
│
├── examples/                           # Example .utlx files (as created)
│   ├── basic/
│   ├── intermediate/
│   └── advanced/
│
├── modules/                            # Core modules
│   │
│   ├── core/                           # Language core (format-agnostic)
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   └── kotlin/
│   │   │   │       └── org/apache/utlx/core/
│   │   │   │           ├── ast/            # Abstract Syntax Tree
│   │   │   │           │   ├── Node.kt
│   │   │   │           │   ├── Expression.kt
│   │   │   │           │   ├── Statement.kt
│   │   │   │           │   └── Program.kt
│   │   │   │           ├── lexer/          # Tokenization
│   │   │   │           │   ├── Lexer.kt
│   │   │   │           │   ├── Token.kt
│   │   │   │           │   └── TokenType.kt
│   │   │   │           ├── parser/         # Parsing
│   │   │   │           │   ├── Parser.kt
│   │   │   │           │   ├── ParseError.kt
│   │   │   │           │   └── ParserContext.kt
│   │   │   │           ├── types/          # Type system
│   │   │   │           │   ├── Type.kt
│   │   │   │           │   ├── TypeChecker.kt
│   │   │   │           │   ├── TypeInference.kt
│   │   │   │           │   └── TypeError.kt
│   │   │   │           ├── optimizer/      # Optimization
│   │   │   │           │   ├── Optimizer.kt
│   │   │   │           │   ├── ConstantFolder.kt
│   │   │   │           │   ├── DeadCodeEliminator.kt
│   │   │   │           │   └── CommonSubexpressionEliminator.kt
│   │   │   │           ├── codegen/        # Code generation
│   │   │   │           │   ├── CodeGenerator.kt
│   │   │   │           │   ├── JVMCodeGen.kt
│   │   │   │           │   ├── JSCodeGen.kt
│   │   │   │           │   └── NativeCodeGen.kt
│   │   │   │           └── udm/            # Universal Data Model
│   │   │   │               ├── UDM.kt
│   │   │   │               ├── Scalar.kt
│   │   │   │               ├── Array.kt
│   │   │   │               ├── Object.kt
│   │   │   │               └── Navigator.kt
│   │   │   └── test/
│   │   │       └── kotlin/
│   │   │           └── org/apache/utlx/core/
│   │   │               ├── lexer/
│   │   │               │   └── LexerTest.kt
│   │   │               ├── parser/
│   │   │               │   └── ParserTest.kt
│   │   │               └── types/
│   │   │                   └── TypeCheckerTest.kt
│   │   ├── build.gradle.kts                # Gradle build file
│   │   └── README.md
│   │
│   ├── jvm/                            # JVM Runtime
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   └── kotlin/
│   │   │   │       └── org/apache/utlx/jvm/
│   │   │   │           ├── runtime/        # Runtime execution
│   │   │   │           │   ├── JVMRuntime.kt
│   │   │   │           │   ├── ExecutionContext.kt
│   │   │   │           │   ├── StackMachine.kt
│   │   │   │           │   └── BytecodeInterpreter.kt
│   │   │   │           ├── compiler/       # JVM-specific compilation
│   │   │   │           │   ├── JVMCompiler.kt
│   │   │   │           │   ├── BytecodeGenerator.kt
│   │   │   │           │   └── ClassBuilder.kt
│   │   │   │           ├── api/            # Public API
│   │   │   │           │   ├── UTLXEngine.kt
│   │   │   │           │   ├── Transform.kt
│   │   │   │           │   └── UTLXException.kt
│   │   │   │           └── integration/    # Framework integrations
│   │   │   │               ├── spring/
│   │   │   │               │   └── UTLXConfiguration.kt
│   │   │   │               ├── camel/
│   │   │   │               │   └── UTLXComponent.kt
│   │   │   │               └── kafka/
│   │   │   │                   └── UTLXTransformer.kt
│   │   │   └── test/
│   │   │       └── kotlin/
│   │   │           └── org/apache/utlx/jvm/
│   │   │               ├── runtime/
│   │   │               │   └── JVMRuntimeTest.kt
│   │   │               └── integration/
│   │   │                   └── IntegrationTest.kt
│   │   ├── build.gradle.kts
│   │   └── README.md
│   │
│   ├── javascript/                     # JavaScript/Node.js Runtime
│   │   ├── src/
│   │   │   ├── runtime/
│   │   │   │   ├── JSRuntime.ts
│   │   │   │   ├── ExecutionContext.ts
│   │   │   │   └── Interpreter.ts
│   │   │   ├── compiler/
│   │   │   │   ├── JSCompiler.ts
│   │   │   │   └── CodeGenerator.ts
│   │   │   ├── api/
│   │   │   │   ├── index.ts            # Main export
│   │   │   │   ├── Engine.ts
│   │   │   │   └── Transform.ts
│   │   │   ├── browser/
│   │   │   │   └── browser.ts          # Browser-specific
│   │   │   └── node/
│   │   │       └── node.ts             # Node.js-specific
│   │   ├── test/
│   │   │   ├── runtime.test.ts
│   │   │   ├── compiler.test.ts
│   │   │   └── integration.test.ts
│   │   ├── dist/                       # Build output (gitignored)
│   │   ├── package.json
│   │   ├── tsconfig.json
│   │   ├── webpack.config.js           # For browser bundle
│   │   ├── rollup.config.js            # Alternative bundler
│   │   └── README.md
│   │
│   ├── native/                         # Native Runtime (GraalVM/LLVM)
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   └── kotlin/
│   │   │   │       └── org/apache/utlx/native/
│   │   │   │           ├── compiler/
│   │   │   │           │   ├── NativeCompiler.kt
│   │   │   │           │   ├── LLVMCodeGen.kt
│   │   │   │           │   └── GraalVMCompiler.kt
│   │   │   │           ├── runtime/
│   │   │   │           │   └── NativeRuntime.kt
│   │   │   │           └── ffi/        # Foreign Function Interface
│   │   │   │               ├── CBindings.kt
│   │   │   │               └── NativeAPI.kt
│   │   │   └── test/
│   │   ├── build.gradle.kts
│   │   └── README.md
│   │
│   └── cli/                            # Command-line Interface
│       ├── src/
│       │   ├── main/
│       │   │   └── kotlin/
│       │   │       └── org/apache/utlx/cli/
│       │   │           ├── Main.kt
│       │   │           ├── commands/
│       │   │           │   ├── TransformCommand.kt
│       │   │           │   ├── ValidateCommand.kt
│       │   │           │   ├── CompileCommand.kt
│       │   │           │   ├── FormatCommand.kt
│       │   │           │   ├── MigrateCommand.kt
│       │   │           │   └── VersionCommand.kt
│       │   │           ├── options/
│       │   │           │   └── GlobalOptions.kt
│       │   │           └── utils/
│       │   │               ├── ColorOutput.kt
│       │   │               └── ProgressBar.kt
│       │   └── test/
│       │       └── kotlin/
│       │           └── org/apache/utlx/cli/
│       │               └── CommandTest.kt
│       ├── build.gradle.kts
│       └── README.md
│
├── formats/                            # Format parsers/serializers
│   ├── xml/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   └── kotlin/
│   │   │   │       └── org/apache/utlx/formats/xml/
│   │   │   │           ├── XMLParser.kt
│   │   │   │           ├── XMLSerializer.kt
│   │   │   │           ├── NamespaceHandler.kt
│   │   │   │           └── SAXStreamingParser.kt
│   │   │   └── test/
│   │   ├── build.gradle.kts
│   │   └── README.md
│   │
│   ├── json/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   └── kotlin/
│   │   │   │       └── org/apache/utlx/formats/json/
│   │   │   │           ├── JSONParser.kt
│   │   │   │           ├── JSONSerializer.kt
│   │   │   │           └── StreamingJSONParser.kt
│   │   │   └── test/
│   │   ├── build.gradle.kts
│   │   └── README.md
│   │
│   ├── csv/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   └── kotlin/
│   │   │   │       └── org/apache/utlx/formats/csv/
│   │   │   │           ├── CSVParser.kt
│   │   │   │           ├── CSVSerializer.kt
│   │   │   │           └── Dialect.kt
│   │   │   └── test/
│   │   ├── build.gradle.kts
│   │   └── README.md
│   │
│   ├── yaml/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   └── kotlin/
│   │   │   │       └── org/apache/utlx/formats/yaml/
│   │   │   │           ├── YAMLParser.kt
│   │   │   │           └── YAMLSerializer.kt
│   │   │   └── test/
│   │   ├── build.gradle.kts
│   │   └── README.md
│   │
│   └── plugin/                         # Plugin API for custom formats
│       ├── src/
│       │   ├── main/
│       │   │   └── kotlin/
│       │   │       └── org/apache/utlx/formats/plugin/
│       │   │           ├── FormatParser.kt
│       │   │           ├── FormatSerializer.kt
│       │   │           └── FormatRegistry.kt
│       │   └── test/
│       ├── build.gradle.kts
│       └── README.md
│
├── stdlib/                             # Standard library
│   ├── src/
│   │   ├── main/
│   │   │   └── kotlin/
│   │   │       └── org/apache/utlx/stdlib/
│   │   │           ├── Functions.kt
│   │   │           ├── string/
│   │   │           │   ├── StringFunctions.kt
│   │   │           │   └── RegexFunctions.kt
│   │   │           ├── array/
│   │   │           │   ├── ArrayFunctions.kt
│   │   │           │   └── Aggregations.kt
│   │   │           ├── math/
│   │   │           │   └── MathFunctions.kt
│   │   │           ├── date/
│   │   │           │   └── DateFunctions.kt
│   │   │           ├── object/
│   │   │           │   └── ObjectFunctions.kt
│   │   │           └── type/
│   │   │               └── TypeFunctions.kt
│   │   └── test/
│   │       └── kotlin/
│   │           └── org/apache/utlx/stdlib/
│   │               ├── StringFunctionsTest.kt
│   │               ├── ArrayFunctionsTest.kt
│   │               └── DateFunctionsTest.kt
│   ├── build.gradle.kts
│   └── README.md
│
├── tools/                              # Development tools
│   ├── vscode-extension/               # VS Code extension
│   │   ├── src/
│   │   │   ├── extension.ts
│   │   │   ├── language/
│   │   │   │   ├── syntax.ts
│   │   │   │   └── completions.ts
│   │   │   └── debugger/
│   │   │       └── debugAdapter.ts
│   │   ├── syntaxes/
│   │   │   └── utlx.tmLanguage.json
│   │   ├── package.json
│   │   └── README.md
│   │
│   ├── intellij-plugin/                # IntelliJ IDEA plugin
│   │   ├── src/
│   │   │   └── main/
│   │   │       ├── kotlin/
│   │   │       └── resources/
│   │   ├── build.gradle.kts
│   │   └── README.md
│   │
│   ├── maven-plugin/                   # Maven plugin
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── org/apache/utlx/maven/
│   │   │               └── UTLXMojo.java
│   │   ├── pom.xml
│   │   └── README.md
│   │
│   ├── gradle-plugin/                  # Gradle plugin
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── kotlin/
│   │   │           └── org/apache/utlx/gradle/
│   │   │               └── UTLXPlugin.kt
│   │   ├── build.gradle.kts
│   │   └── README.md
│   │
│   └── benchmarks/                     # Performance benchmarks
│       ├── src/
│       │   └── main/
│       │       └── kotlin/
│       │           └── org/apache/utlx/benchmarks/
│       │               ├── ParserBenchmark.kt
│       │               ├── RuntimeBenchmark.kt
│       │               └── TransformBenchmark.kt
│       ├── build.gradle.kts
│       └── README.md
│
├── scripts/                            # Build and utility scripts
│   ├── build.sh                        # Main build script
│   ├── test.sh                         # Run all tests
│   ├── release.sh                      # Release automation
│   ├── install.sh                      # Installation script
│   ├── benchmark.sh                    # Run benchmarks
│   ├── generate-docs.sh                # Generate documentation
│   └── update-version.sh               # Version bump utility
│
├── build/                              # Build outputs (gitignored)
│   ├── libs/                           # Compiled JARs
│   ├── distributions/                  # Distribution packages
│   └── reports/                        # Test/coverage reports
│
├── dist/                               # Distribution packages (gitignored)
│   ├── utlx-1.0.0-jvm.jar
│   ├── utlx-1.0.0-cli.tar.gz
│   ├── utlx-1.0.0-native-linux-x64
│   ├── utlx-1.0.0-native-macos-arm64
│   └── utlx-1.0.0-windows-x64.exe
│
├── .idea/                              # IntelliJ IDEA settings (gitignored)
├── .vscode/                            # VS Code settings
│   ├── settings.json
│   ├── launch.json
│   └── extensions.json
│
├── .gradle/                            # Gradle cache (gitignored)
├── node_modules/                       # npm dependencies (gitignored)
│
├── gradle/                             # Gradle wrapper
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
│
├── .gitignore
├── .gitattributes
├── .editorconfig                       # Editor configuration
├── README.md                           # Main README (improved)
├── LICENSE.md                          # AGPL-3.0 license
├── CONTRIBUTING.md                     # Contribution guidelines
├── CLA.md                              # Contributor License Agreement
├── CONTRIBUTORS.md                     # Contributors list
├── CHANGELOG.md                        # Version history
├── CODE_OF_CONDUCT.md                  # Code of conduct
│
├── build.gradle.kts                    # Root Gradle build
├── settings.gradle.kts                 # Gradle settings
├── gradle.properties                   # Gradle properties
├── gradlew                             # Gradle wrapper (Unix)
├── gradlew.bat                         # Gradle wrapper (Windows)
│
├── package.json                        # Root npm package (for JS tooling)
├── lerna.json                          # Lerna monorepo config (optional)
├── tsconfig.json                       # TypeScript root config
│
└── Dockerfile                          # Container image
```

## Build Configuration Files

### Root `build.gradle.kts`

```kotlin
plugins {
    kotlin("jvm") version "1.9.20" apply false
    id("org.jetbrains.dokka") version "1.9.10" apply false
}

allprojects {
    group = "org.apache.utlx"
    version = "1.0.0-SNAPSHOT"
    
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "kotlin")
    apply(plugin = "org.jetbrains.dokka")
    
    dependencies {
        testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
        testImplementation("io.mockk:mockk:1.13.8")
    }
    
    tasks.test {
        useJUnitPlatform()
    }
}
```

### Root `settings.gradle.kts`

```kotlin
rootProject.name = "utl-x"

// Core modules
include("modules:core")
include("modules:jvm")
include("modules:javascript")
include("modules:native")
include("modules:cli")

// Format modules
include("formats:xml")
include("formats:json")
include("formats:csv")
include("formats:yaml")
include("formats:plugin")

// Standard library
include("stdlib")

// Tools
include("tools:vscode-extension")
include("tools:intellij-plugin")
include("tools:maven-plugin")
include("tools:gradle-plugin")
include("tools:benchmarks")
```

### JavaScript `package.json`

```json
{
  "name": "@apache/utlx",
  "version": "1.0.0",
  "description": "Universal Transformation Language Extended",
  "main": "dist/index.js",
  "types": "dist/index.d.ts",
  "scripts": {
    "build": "tsc && webpack",
    "test": "jest",
    "lint": "eslint src/**/*.ts",
    "format": "prettier --write src/**/*.ts"
  },
  "keywords": ["transformation", "xml", "json", "data"],
  "author": "UTL-X Contributors",
  "license": "AGPL-3.0",
  "repository": {
    "type": "git",
    "url": "https://github.com/grauwen/utl-x.git"
  },
  "devDependencies": {
    "@types/node": "^20.0.0",
    "typescript": "^5.2.0",
    "webpack": "^5.88.0",
    "jest": "^29.6.0",
    "eslint": "^8.47.0",
    "prettier": "^3.0.0"
  }
}
```

## Key Design Decisions

### 1. **Monorepo Structure**
- All runtimes in one repository
- Shared core modules
- Consistent versioning
- Easier cross-module refactoring

### 2. **Module Separation**
- **core**: Platform-agnostic language implementation
- **jvm/javascript/native**: Runtime-specific code
- **formats**: Pluggable format support
- **stdlib**: Reusable standard library
- **cli**: User-facing command-line tool

### 3. **Language Choice**
- **Kotlin** for JVM (concise, safe, interoperable with Java)
- **TypeScript** for JavaScript (type safety, better tooling)
- **Gradle** for JVM builds (flexible, Kotlin DSL)
- **npm/webpack** for JavaScript builds (standard ecosystem)

### 4. **Testing Strategy**
- Unit tests alongside source code
- Integration tests in dedicated modules
- Benchmarks in separate module
- Test coverage reporting

### 5. **Documentation Location**
- `/docs` for user documentation
- `/modules/*/README.md` for developer documentation
- Inline KDoc/JSDoc for API documentation

## Development Workflow

### Initial Setup

```bash
# Clone repository
git clone https://github.com/grauwen/utl-x.git
cd utl-x

# Build all modules
./gradlew build

# Build JavaScript runtime
cd modules/javascript
npm install
npm run build

# Run tests
./gradlew test
```

### Working on Specific Module

```bash
# Work on JVM runtime
cd modules/jvm
./gradlew build test

# Work on CLI
cd modules/cli
./gradlew run --args="transform test.utlx $input.json"
```

### Creating Distribution

```bash
# Build all distributions
./scripts/build.sh --release

# Output in dist/
ls dist/
# utlx-1.0.0-jvm.jar
# utlx-1.0.0-cli.tar.gz
# utlx-1.0.0-native-linux-x64
```

## Next Steps

1. **Create directory structure**: Use provided scripts
2. **Set up build files**: Gradle, npm configurations
3. **Implement core modules**: Lexer, parser, type checker
4. **Add format support**: XML, JSON parsers
5. **Build runtimes**: JVM first, then JavaScript
6. **Create CLI**: User-facing tool
7. **Write tests**: Comprehensive test coverage
8. **Build tools**: IDE plugins, build plugins
9. **Package distributions**: Release artifacts

This structure provides a solid foundation for building a production-ready, multi-runtime transformation language!
