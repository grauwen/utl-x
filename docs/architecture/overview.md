# Architecture Overview

High-level architecture of the UTL-X transformation engine.

## System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        UTL-X System                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐      ┌──────────────┐      ┌──────────┐   │
│  │   CLI Tool   │      │  Library API │      │   IDE    │   │
│  └──────┬───────┘      └──────┬───────┘      └────┬─────┘   │
│         │                     │                   │         │
│         └─────────────────────┼───────────────────┘         │
│                               │                             │
│                        ┌──────▼───────┐                     │
│                        │   Compiler   │                     │
│                        │   Pipeline   │                     │
│                        └──────┬───────┘                     │
│                               │                             │
│         ┌─────────────────────┼─────────────────────┐       │
│         │                     │                     │       │
│    ┌────▼────┐         ┌─────▼─────┐        ┌─────▼─────┐   │
│    │   JVM   │         │JavaScript │        │  Native   │   │
│    │ Runtime │         │  Runtime  │        │  Runtime  │   │
│    └────┬────┘         └─────┬─────┘        └─────┬─────┘   │
│         │                     │                     │       │
│         └─────────────────────┼─────────────────────┘       │
│                               │                             │
│                        ┌──────▼───────┐                     │
│                        │ Format Layer │                     │
│                        └──────┬───────┘                     │
│                               │                             │
│         ┌─────────────────────┼─────────────────────┐       │
│         │                     │                     │       │
│    ┌────▼────┐         ┌─────▼─────┐        ┌─────▼─────┐   │
│    │   XML   │         │   JSON    │        │    CSV    │   │
│    │ Parser  │         │  Parser   │        │  Parser   │   │
│    └─────────┘         └───────────┘        └───────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. Compiler Pipeline

Transforms UTL-X source code into executable form.

**Stages:**
1. **Lexer** - Tokenization
2. **Parser** - AST generation
3. **Type Checker** - Type inference and validation
4. **Optimizer** - Performance optimizations
5. **Code Generator** - Runtime-specific code

### 2. Universal Data Model (UDM)

Internal representation of all data formats.

**Purpose:**
- Format abstraction
- Uniform transformations
- Type safety

**Structure:**
```
UDM
├── Scalar (String, Number, Boolean, Null, Date)
├── Array (ordered collection)
└── Object (key-value pairs)
```

### 3. Runtime Engines

Execute compiled transformations.

**Variants:**
- **JVM**: Kotlin-based, high performance
- **JavaScript**: Node.js and browser support
- **Native**: Compiled to machine code (via GraalVM/LLVM)

### 4. Format Layer

Parsers and serializers for different data formats.

**Supported Formats:**
- XML (with namespace support)
- JSON
- CSV (various dialects)
- YAML (v1.1+)
- Custom (via plugin system)

## Data Flow

```
Input File
    │
    ▼
┌──────────────┐
│Format Parser │ → Parse XML/JSON/CSV → UDM
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  Compiled    │
│Transformation│ → Execute transformation → UDM'
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  Serializer  │ → Serialize to format → Output File
└──────────────┘
```

## Key Design Principles

### 1. Separation of Concerns

- **Parsing** is separate from **transformation**
- **Compilation** is separate from **execution**
- **Format handling** is pluggable

### 2. Immutability

- All data structures are immutable
- Transformations create new values
- No side effects

### 3. Type Safety

- Strong static typing
- Type inference
- Compile-time checks

### 4. Performance

- Ahead-of-time compilation
- Lazy evaluation where possible
- Streaming support for large files

### 5. Extensibility

- Plugin architecture for formats
- Custom function support
- Multiple runtime targets

## Compilation Process

```
Source Code
    │
    ▼
┌──────────┐
│  Lexer   │ → Tokens
└────┬─────┘
     │
     ▼
┌──────────┐
│  Parser  │ → Abstract Syntax Tree (AST)
└────┬─────┘
     │
     ▼
┌──────────────┐
│ Type Checker │ → Typed AST
└──────┬───────┘
       │
       ▼
┌──────────┐
│Optimizer │ → Optimized AST
└────┬─────┘
     │
     ▼
┌─────────────────┐
│ Code Generator  │ → Runtime Code
└─────────────────┘
```

## Runtime Execution

```
Runtime Code
    │
    ▼
┌───────────────┐
│ Load  & Init  │ → Load compiled transform
└───────┬───────┘
        │
        ▼
┌───────────────┐
│ Parse Input   │ → Input UDM
└───────┬───────┘
        │
        ▼
┌───────────────┐
│   Execute     │ → Output UDM
└───────┬───────┘
        │
        ▼
┌───────────────┐
│Serialize Out  │ → Output File
└───────────────┘
```

## Module Structure

```
utlx/
├── core/
│   ├── lexer/          # Tokenization
│   ├── parser/         # AST generation
│   ├── types/          # Type system
│   ├── optimizer/      # Optimizations
│   └── codegen/        # Code generation
├── runtime/
│   ├── jvm/            # JVM runtime
│   ├── javascript/     # JS runtime
│   └── native/         # Native runtime
├── formats/
│   ├── xml/            # XML parser/serializer
│   ├── json/           # JSON parser/serializer
│   ├── csv/            # CSV parser/serializer
│   └── plugin/         # Plugin system
├── stdlib/             # Standard library
└── cli/                # Command-line tool
```

## Performance Characteristics

### Time Complexity

- **Compilation**: O(n) where n = source code size
- **Execution**: O(m) where m = input data size
- **Memory**: O(m) - proportional to data size

### Optimization Strategies

1. **Constant Folding** - Evaluate constants at compile time
2. **Dead Code Elimination** - Remove unused code
3. **Template Inlining** - Inline small templates
4. **Tail Call Optimization** - Optimize recursive functions
5. **Streaming** - Process large files without loading entirely

## Scalability

### Horizontal Scaling

- Stateless transformations
- Parallel processing support
- Distributed execution (planned v2.0)

### Vertical Scaling

- Efficient memory usage
- Streaming for large files
- Native runtime for maximum performance

## Security Considerations

### Input Validation

- Schema validation (XSD, JSON Schema)
- Size limits
- Timeout limits

### Sandboxing

- No file system access from transforms
- No network access
- Limited memory allocation

### License Compliance

- AGPL-3.0 source disclosure for network services
- Clear license headers
- Dependency tracking

## Next Sections

- [Compiler Pipeline](compiler-pipeline.md) - Detailed compilation process
- [Universal Data Model](universal-data-model.md) - UDM internals
- [Runtime](runtime.md) - Runtime execution details
- [Performance](performance.md) - Optimization techniques

---
