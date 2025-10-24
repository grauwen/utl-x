# UTL-X Module System Design

**Status:** Design Document (v2.0 Feature - Not Yet Implemented)
**Author:** UTL-X Core Team
**Date:** October 22, 2025
**Target Version:** UTL-X v2.0

---

## Table of Contents

1. [Overview & Motivation](#1-overview--motivation)
2. [Design Goals](#2-design-goals)
3. [Syntax Specification](#3-syntax-specification)
4. [Path Resolution Strategy](#4-path-resolution-strategy)
5. [Compilation Pipeline Integration](#5-compilation-pipeline-integration)
6. [Implementation Architecture](#6-implementation-architecture)
7. [Scoping & Visibility Rules](#7-scoping--visibility-rules)
8. [Error Handling](#8-error-handling)
9. [Security Considerations](#9-security-considerations)
10. [Migration Strategy](#10-migration-strategy)
11. [Examples](#11-examples)
12. [Implementation Roadmap](#12-implementation-roadmap)
13. [Comparison with Other Languages](#13-comparison-with-other-languages)
14. [Open Questions & Future Work](#14-open-questions--future-work)

---

## 1. Overview & Motivation

### 1.1 The Problem

Currently, UTL-X has **no mechanism for code reuse across files**. Every transformation script is a single, self-contained file. This leads to:

**❌ Code Duplication:**
```utlx
// orders-transform.utlx
function CalculateTax(amount: Number, rate: Number): Number {
  amount * rate
};

// invoices-transform.utlx
function CalculateTax(amount: Number, rate: Number): Number {
  amount * rate  // Same function copy-pasted!
};
```

**❌ Maintenance Burden:**
- Bug fixes must be replicated across multiple files
- Changes to business logic require finding all copies
- No single source of truth

**❌ Poor Organization:**
- Large transformations become unwieldy single files
- Related logic cannot be grouped into modules
- No separation of concerns

**❌ Limited Collaboration:**
- Teams cannot share common utilities
- No standard library of custom functions
- Every project reinvents the wheel

### 1.2 The Solution: Module System

A **module system** allows:

✅ **Code Reuse:**
```utlx
// lib/tax-utils.utlx
export function CalculateTax(amount: Number, rate: Number): Number {
  amount * rate
}

// orders-transform.utlx
import { CalculateTax } from "./lib/tax-utils.utlx"
```

✅ **Maintainability:**
- Single source of truth for shared logic
- Fix once, benefit everywhere
- Clear dependency structure

✅ **Organization:**
- Split large transformations into logical modules
- Group related functionality
- Clean separation of concerns

✅ **Collaboration:**
- Share utilities across projects
- Build team-specific libraries
- Reusable transformation patterns

### 1.3 Module System vs stdlib I/O

**⚠️ CRITICAL DISTINCTION:**

The module system is **NOT** about runtime file I/O operations. This is a common confusion:

| Feature | Module System (`import`) | stdlib I/O (`readFile`) |
|---------|-------------------------|------------------------|
| **Execution Time** | Compile-time | Runtime |
| **Purpose** | Code organization & reuse | Data access during transformation |
| **Security** | Safe (controlled, static) | Dangerous (arbitrary file access) |
| **Side Effects** | None (pure linking) | Yes (file reads/writes) |
| **Performance** | Zero overhead (inlined) | I/O cost on every execution |
| **Path Handling** | Compiler handles uniformly | Must handle Windows/Unix differences |

**Example of what the module system IS:**
```utlx
import { TaxRate } from "./config.utlx"  // ✅ Compile-time code import

{ tax: $input.amount * TaxRate }
```

**Example of what the module system IS NOT:**
```utlx
{
  configFile: readFile("./config.json"),  // ❌ Runtime file I/O (security risk!)
  data: parseJson(configFile)
}
```

**Why stdlib I/O is problematic:**
- **Security Risk:** `readFile("/etc/passwd")` could read sensitive files
- **Cross-platform Issues:** Windows (`C:\path`) vs Unix (`/path`)
- **Side Effects:** Breaks functional purity of transformations
- **Performance:** File I/O on every transformation execution
- **Unpredictability:** File contents can change between runs

**The module system avoids these issues** by resolving all imports at **compile time**, before execution begins.

---

## 2. Design Goals

### 2.1 Core Principles

1. **Compile-Time Resolution**
   - All module imports resolved before execution
   - No runtime file system access
   - Deterministic builds

2. **Zero Runtime Overhead**
   - Imported code inlined or linked efficiently
   - No performance penalty vs single-file scripts
   - Optimal code generation

3. **Security First**
   - No arbitrary file access
   - Restricted path resolution
   - Sandboxed compilation

4. **Cross-Platform**
   - Uniform path syntax across Windows/Mac/Linux
   - Compiler handles platform differences
   - No user-visible path issues

5. **Backward Compatible**
   - Existing single-file scripts continue to work
   - No breaking changes to current syntax
   - Gradual adoption path

6. **Developer Friendly**
   - Familiar syntax (like JavaScript/TypeScript)
   - Clear error messages
   - IDE-friendly (autocomplete, jump-to-definition)

### 2.2 Non-Goals (Out of Scope for v1)

- ❌ **Runtime dynamic imports** (`import(variablePath)`)
- ❌ **Package manager** (npm-like registry)
- ❌ **Versioning system** (semver, lock files)
- ❌ **Conditional imports** (platform-specific modules)
- ❌ **Monorepo tooling** (workspaces, linking)

These may be considered for future versions.

---

## 3. Syntax Specification

### 3.1 Export Syntax

#### 3.1.1 Named Exports (Functions)

```utlx
// Exporting a function
export function CalculateTax(amount: Number, rate: Number): Number {
  amount * rate
}

// Exporting multiple functions
export function Add(a: Number, b: Number): Number { a + b }
export function Subtract(a: Number, b: Number): Number { a - b }
```

#### 3.1.2 Named Exports (Constants)

```utlx
// Exporting constants
export let TAX_RATE = 0.08;
export let MAX_DISCOUNT = 0.50;
```

#### 3.1.3 Export Lists

```utlx
// Define privately, export selectively
function Helper(x: Number): Number { x * 2 }
function CalculateTax(amount: Number, rate: Number): Number {
  Helper(amount) * rate
}

// Only export what's public
export { CalculateTax }  // Helper remains private
```

#### 3.1.4 Re-exports

```utlx
// Re-export from another module
export { CalculateTax, CalculateDiscount } from "./tax-utils.utlx"

// Re-export with renaming
export { CalculateTax as ComputeTax } from "./tax-utils.utlx"

// Re-export everything
export * from "./tax-utils.utlx"
```

### 3.2 Import Syntax

#### 3.2.1 Named Imports

```utlx
// Import specific exports
import { CalculateTax } from "./tax-utils.utlx"

// Import multiple
import { CalculateTax, CalculateDiscount } from "./tax-utils.utlx"

// Import with renaming
import { CalculateTax as ComputeTax } from "./tax-utils.utlx"
```

#### 3.2.2 Namespace Imports

```utlx
// Import entire module as namespace
import * as TaxUtils from "./tax-utils.utlx"

// Usage: TaxUtils.CalculateTax(100, 0.08)
{
  tax: TaxUtils.CalculateTax($input.amount, TaxUtils.TAX_RATE)
}
```

#### 3.2.3 Default Exports (Future Consideration)

**Note:** Default exports may be added in v2.1. For v2.0, only named exports are supported.

```utlx
// Potential future syntax (NOT v2.0)
export default function Transform(input) { ... }

import Transform from "./my-transform.utlx"
```

### 3.3 Module Structure

A UTL-X module file has this structure:

```utlx
// 1. Module header (optional imports)
import { Helper } from "./utils.utlx"

// 2. Export declarations
export function PublicFunc(x: Number): Number {
  PrivateFunc(x) + Helper(x)
}

// 3. Private (non-exported) declarations
function PrivateFunc(x: Number): Number {
  x * 2
}

// 4. Export list (optional, alternative to inline exports)
export { PublicFunc }
```

**Note:** If a module file contains transformation logic (header + transformation), it can still export functions:

```utlx
%utlx 1.0
input json
output json

// Exports can come before or after transformation
export function Helper(x: Number): Number { x * 2 }

---
{
  result: Helper($input.value)
}
```

However, when imported, **only the exported declarations are visible**, not the transformation itself.

### 3.4 Grammar Additions

#### 3.4.1 EBNF Grammar

```ebnf
(* Import declarations *)
import-declaration ::= 'import' import-clause 'from' string-literal

import-clause ::= named-imports
                | namespace-import

named-imports ::= '{' import-specifier {',' import-specifier} '}'
import-specifier ::= identifier ['as' identifier]

namespace-import ::= '*' 'as' identifier

(* Export declarations *)
export-declaration ::= 'export' export-clause
                     | 'export' declaration

export-clause ::= '{' export-specifier {',' export-specifier} '}'
                | '{' export-specifier {',' export-specifier} '}' 'from' string-literal
                | '*' 'from' string-literal

export-specifier ::= identifier ['as' identifier]

declaration ::= function-definition
              | let-binding
```

#### 3.4.2 Token Additions

Already defined in lexer:
- `TokenType.IMPORT` - keyword `import`
- `TokenType.AS` - keyword `as`

Needs to be added:
- `TokenType.EXPORT` - keyword `export`
- `TokenType.FROM` - keyword `from`

---

## 4. Path Resolution Strategy

### 4.1 Path Types

#### 4.1.1 Relative Paths

```utlx
import { Foo } from "./utils.utlx"       // Same directory
import { Bar } from "../shared/bar.utlx" // Parent directory
import { Baz } from "./lib/baz.utlx"     // Subdirectory
```

**Resolution:**
- Resolved relative to the **importing file's directory**
- Example: If `/project/transforms/order.utlx` imports `"./lib/tax.utlx"`,
  it resolves to `/project/transforms/lib/tax.utlx`

#### 4.1.2 Absolute Paths (Project Root)

```utlx
import { Foo } from "/lib/utils.utlx"  // From project root
```

**Resolution:**
- Resolved from the **project root directory**
- Project root is determined by:
  1. Directory containing `utlx.config.json` (if exists)
  2. Git repository root (if in git repo)
  3. Current working directory (fallback)

#### 4.1.3 Package Paths (Future - Not v2.0)

```utlx
import { Foo } from "@company/utils"     // Package-style (future)
import { Bar } from "utlx-stdlib/extra"  // Standard library (future)
```

**Not implemented in v2.0** - requires package manager.

### 4.2 File Extension Handling

**Required:** `.utlx` extension must be explicit in import paths.

```utlx
import { Foo } from "./utils.utlx"   // ✅ Correct
import { Foo } from "./utils"        // ❌ Error: Must include .utlx extension
```

**Rationale:**
- Explicit is better than implicit
- Avoids ambiguity with future file types
- Consistent with current file handling

### 4.3 Path Normalization

The compiler normalizes all paths to a canonical form:

**Input Paths (various forms):**
```
./lib/utils.utlx
lib/utils.utlx
./lib/../lib/utils.utlx
```

**Normalized:**
```
lib/utils.utlx
```

**Platform Handling:**
- Windows: `lib\utils.utlx` → `lib/utils.utlx`
- Unix: `lib/utils.utlx` → `lib/utils.utlx`
- All backslashes converted to forward slashes internally

### 4.4 Security Restrictions

**Allowed:**
```utlx
import { Foo } from "./lib/utils.utlx"        // ✅ Within project
import { Bar } from "../shared/bar.utlx"      // ✅ Parent (if in project)
import { Baz } from "/lib/core.utlx"          // ✅ From project root
```

**Disallowed:**
```utlx
import { Evil } from "/etc/passwd"            // ❌ System paths blocked
import { Bad } from "C:/Windows/System32/x"   // ❌ Absolute system paths
import { Nope } from "../../../../../../etc"  // ❌ Escaping project root
```

**Enforcement:**
- All paths must resolve **within the project boundary**
- Project boundary = project root and all subdirectories
- Attempting to escape project root → Compilation error

### 4.5 Circular Dependency Detection

**Example of circular dependency:**

```utlx
// a.utlx
import { FuncB } from "./b.utlx"
export function FuncA() { FuncB() }

// b.utlx
import { FuncA } from "./a.utlx"  // ❌ Circular!
export function FuncB() { FuncA() }
```

**Detection Algorithm:**
1. Build dependency graph during module discovery
2. Perform depth-first search (DFS)
3. Detect back-edges (cycles)
4. Report error with full cycle path

**Error Message:**
```
Circular dependency detected:
  /project/a.utlx
  → /project/b.utlx
  → /project/a.utlx (cycle)
```

---

## 5. Compilation Pipeline Integration

### 5.1 Current Pipeline (Single File)

```
Source File (.utlx)
    ↓
[Lexer] → Tokens
    ↓
[Parser] → AST
    ↓
[Type Checker] → Typed AST
    ↓
[Interpreter] → RuntimeValue
```

### 5.2 New Pipeline (Multi-File)

```
Entry File (.utlx)
    ↓
[Module Discovery] → Dependency Graph
    ↓
[Module Loader] → Load all modules
    ↓
[Circular Check] → Verify no cycles
    ↓
[Compilation Order] → Topological sort
    ↓
For each module (bottom-up):
    [Lexer] → Tokens
    ↓
    [Parser] → AST with imports/exports
    ↓
    [Module Linker] → Resolve imported symbols
    ↓
    [Type Checker] → Check types across modules
    ↓
[Merged AST] → Single unified AST
    ↓
[Interpreter] → RuntimeValue
```

### 5.3 Module Discovery Phase

**Algorithm:**

```kotlin
fun discoverModules(entryFile: Path): DependencyGraph {
    val graph = DependencyGraph()
    val visited = mutableSetOf<Path>()
    val queue = mutableListOf(entryFile)

    while (queue.isNotEmpty()) {
        val currentFile = queue.removeAt(0)

        if (currentFile in visited) continue
        visited.add(currentFile)

        // Parse just enough to find import statements
        val imports = extractImports(currentFile)

        for (importPath in imports) {
            val resolvedPath = resolvePath(currentFile, importPath)
            graph.addEdge(currentFile, resolvedPath)
            queue.add(resolvedPath)
        }
    }

    return graph
}
```

**Optimization:** Cache parsed imports to avoid re-reading files.

### 5.4 Compilation Order

Modules are compiled in **topological order** (dependencies before dependents):

```
Dependency Graph:
  main.utlx
    ├─> utils.utlx
    │     └─> helpers.utlx
    └─> config.utlx

Compilation Order:
  1. helpers.utlx  (no dependencies)
  2. config.utlx   (no dependencies)
  3. utils.utlx    (depends on helpers)
  4. main.utlx     (depends on utils and config)
```

### 5.5 Caching Strategy

**Module Cache Structure:**
```kotlin
data class ModuleCache(
    val path: Path,
    val sourceHash: String,        // SHA-256 of source
    val ast: Expression,            // Parsed AST
    val exports: Map<String, Symbol>, // Exported symbols
    val timestamp: Instant
)
```

**Cache Invalidation:**
- Source file modified (hash changed) → Recompile
- Dependency changed → Recompile dependent modules
- Cache miss → Compile

**Cache Location:**
```
.utlx-cache/
  ├── modules/
  │   ├── lib_utils_utlx_a3f2e1.bin
  │   └── config_utlx_9d4c2a.bin
  └── metadata.json
```

---

## 6. Implementation Architecture

### 6.1 New Components

#### 6.1.1 Module Resolver

```kotlin
class ModuleResolver(
    private val projectRoot: Path,
    private val fileSystem: FileSystem = FileSystems.getDefault()
) {
    fun resolve(importingFile: Path, importPath: String): Path {
        val normalizedPath = normalizePath(importPath)

        return when {
            importPath.startsWith("./") || importPath.startsWith("../") ->
                resolveRelative(importingFile, normalizedPath)

            importPath.startsWith("/") ->
                resolveAbsolute(projectRoot, normalizedPath)

            else ->
                throw ParseException("Invalid import path: $importPath")
        }
    }

    private fun resolveRelative(from: Path, to: String): Path {
        val directory = from.parent
        return directory.resolve(to).normalize()
    }

    private fun resolveAbsolute(root: Path, to: String): Path {
        return root.resolve(to.removePrefix("/")).normalize()
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/')
    }
}
```

#### 6.1.2 Module Loader

```kotlin
class ModuleLoader(
    private val resolver: ModuleResolver,
    private val parser: Parser,
    private val cache: ModuleCache
) {
    fun load(modulePath: Path): Module {
        // Check cache first
        cache.get(modulePath)?.let { return it }

        // Read and parse module
        val source = Files.readString(modulePath)
        val ast = parser.parse(source)

        // Extract exports
        val exports = extractExports(ast)

        // Create module
        val module = Module(
            path = modulePath,
            ast = ast,
            exports = exports,
            imports = extractImports(ast)
        )

        // Cache
        cache.put(modulePath, module)

        return module
    }

    private fun extractExports(ast: Expression): Map<String, Symbol> {
        // Find all export declarations in AST
        // Build symbol table
    }
}
```

#### 6.1.3 Module Linker

```kotlin
class ModuleLinker(
    private val modules: Map<Path, Module>
) {
    fun link(module: Module): LinkedModule {
        val resolvedImports = mutableMapOf<String, Symbol>()

        for ((name, importSpec) in module.imports) {
            val sourceModule = modules[importSpec.modulePath]
                ?: throw LinkError("Module not found: ${importSpec.modulePath}")

            val symbol = sourceModule.exports[importSpec.name]
                ?: throw LinkError("Export '${importSpec.name}' not found in ${importSpec.modulePath}")

            resolvedImports[name] = symbol
        }

        return LinkedModule(module, resolvedImports)
    }
}
```

### 6.2 AST Node Additions

#### 6.2.1 Import Declaration

```kotlin
data class ImportDeclaration(
    val specifiers: List<ImportSpecifier>,
    val modulePath: String,
    override val location: Location
) : Expression()

sealed class ImportSpecifier {
    data class Named(
        val imported: String,      // Original name in module
        val local: String,         // Local alias (may be same)
        val location: Location
    ) : ImportSpecifier()

    data class Namespace(
        val local: String,         // Local namespace name
        val location: Location
    ) : ImportSpecifier()
}
```

#### 6.2.2 Export Declaration

```kotlin
data class ExportDeclaration(
    val declaration: Expression?,      // Inline export: export function Foo() {}
    val specifiers: List<ExportSpecifier>?, // Export list: export { Foo, Bar }
    val source: String?,               // Re-export: export { Foo } from "./mod"
    override val location: Location
) : Expression()

data class ExportSpecifier(
    val local: String,       // Local name
    val exported: String,    // Exported name (may be renamed)
    val location: Location
)
```

### 6.3 Symbol Table Enhancements

```kotlin
data class Symbol(
    val name: String,
    val type: UTLXType,
    val kind: SymbolKind,
    val location: Location,
    val module: Path?  // NEW: Track which module this came from
)

enum class SymbolKind {
    FUNCTION,
    VARIABLE,
    PARAMETER,
    IMPORTED_FUNCTION,  // NEW: Imported from another module
    IMPORTED_VARIABLE   // NEW
}
```

### 6.4 Module Metadata

```kotlin
data class Module(
    val path: Path,
    val ast: Expression,
    val exports: Map<String, Symbol>,
    val imports: Map<String, ImportSpec>,
    val dependencies: Set<Path>
)

data class ImportSpec(
    val name: String,        // Local name
    val modulePath: Path,    // Resolved absolute path
    val sourceName: String   // Original name in source module
)
```

---

## 7. Scoping & Visibility Rules

### 7.1 Module-Level Scope

Each module has its own scope:

```utlx
// module-a.utlx
let SECRET = "private";  // Not exported → private to this module

export function GetSecret(): String {
  SECRET  // Can access within same module
}
```

```utlx
// module-b.utlx
import { GetSecret } from "./module-a.utlx"

{
  result: GetSecret(),  // ✅ Can call exported function
  secret: SECRET        // ❌ Error: SECRET not in scope (not exported)
}
```

### 7.2 Export Visibility

**Only explicitly exported symbols are visible:**

```utlx
// utils.utlx
function PrivateHelper(x: Number): Number { x * 2 }

export function PublicFunc(x: Number): Number {
  PrivateHelper(x) + 10
}
```

```utlx
// main.utlx
import { PublicFunc } from "./utils.utlx"

{
  a: PublicFunc(5),      // ✅ Works
  b: PrivateHelper(5)    // ❌ Error: PrivateHelper not exported
}
```

### 7.3 Namespace Collisions

**Within a module:**

```utlx
import { Foo } from "./a.utlx"
import { Foo } from "./b.utlx"  // ❌ Error: Foo already imported

// Solution: Rename
import { Foo as FooA } from "./a.utlx"
import { Foo as FooB } from "./b.utlx"  // ✅ OK
```

**Imported vs local:**

```utlx
import { Calculate } from "./utils.utlx"

function Calculate(x: Number): Number {  // ❌ Error: Calculate already imported
  x * 2
}

// Solution: Rename import or local
import { Calculate as UtilsCalculate } from "./utils.utlx"

function Calculate(x: Number): Number {  // ✅ OK
  x * 2
}
```

### 7.4 Re-export Visibility

```utlx
// core.utlx
export function CoreFunc() { }

// utils.utlx
export { CoreFunc } from "./core.utlx"  // Re-export

// main.utlx
import { CoreFunc } from "./utils.utlx"  // ✅ Can import re-exported symbol
```

---

## 8. Error Handling

### 8.1 Module Not Found

**Error:**
```
Module not found: ./utils.utlx
  at /project/main.utlx:1:24

  import { Foo } from "./utils.utlx"
                       ^^^^^^^^^^^^^^^

Searched paths:
  - /project/utils.utlx (not found)

Did you mean:
  - ./util.utlx
  - ./lib/utils.utlx
```

### 8.2 Export Not Found

**Error:**
```
Export 'Calculate' not found in module: ./utils.utlx
  at /project/main.utlx:1:10

  import { Calculate } from "./utils.utlx"
           ^^^^^^^^^

Available exports:
  - Compute
  - Transform

Did you mean:
  - Compute
```

### 8.3 Circular Dependency

**Error:**
```
Circular dependency detected:

  /project/a.utlx:1:1
  import { FuncB } from "./b.utlx"

  → /project/b.utlx:1:1
    import { FuncA } from "./a.utlx"

  → /project/a.utlx (cycle detected)

To fix: Remove one of the imports or refactor into a third module.
```

### 8.4 Type Mismatch Across Modules

**Error:**
```
Type mismatch: Function 'Process' expects Number but got String
  at /project/main.utlx:5:15

  result: Process($input.name)
                  ^^^^^^^^^^^ String

Function signature in /project/utils.utlx:3:1:
  function Process(value: Number): Number
                          ^^^^^^ expects Number
```

### 8.5 Invalid Import Path

**Error:**
```
Invalid import path: "../../../etc/passwd"
  at /project/main.utlx:1:24

Path escapes project boundary.
  Project root: /project
  Resolved path: /etc/passwd (outside project)

Security restriction: Imports must stay within project directory.
```

---

## 9. Security Considerations

### 9.1 No Runtime File Access

**Key Principle:** Module imports are resolved at **compile time**, not runtime.

**Implementation:**
- Module resolution happens in `Parser` class (compile phase)
- No file system access in `Interpreter` class (runtime phase)
- All module paths resolved before execution begins

**Verification:**
```kotlin
class Interpreter {
    // Runtime - NO file system access allowed
    fun evaluate(expr: Expression, env: Environment): RuntimeValue {
        // Module imports already resolved - just use symbols
        when (expr) {
            is Expression.Identifier -> env.lookup(expr.name)
            // ... no file operations
        }
    }
}
```

### 9.2 Path Sandbox

**Enforcement:**

```kotlin
class ModuleResolver {
    private fun enforceSecurityBoundary(resolvedPath: Path) {
        if (!resolvedPath.startsWith(projectRoot)) {
            throw SecurityException(
                "Import path escapes project boundary: $resolvedPath\n" +
                "Project root: $projectRoot"
            )
        }

        // Additional checks
        if (isSystemPath(resolvedPath)) {
            throw SecurityException("System paths are forbidden: $resolvedPath")
        }
    }

    private fun isSystemPath(path: Path): Boolean {
        val pathStr = path.toString().toLowerCase()
        return pathStr.startsWith("/etc") ||
               pathStr.startsWith("/sys") ||
               pathStr.startsWith("/proc") ||
               pathStr.startsWith("c:/windows") ||
               pathStr.startsWith("c:/program files")
    }
}
```

### 9.3 Safe vs Unsafe Patterns

**✅ Safe (Module System):**
```utlx
// Compile-time import - safe
import { TaxRate } from "./config.utlx"

export let TAX_RATE = 0.08;
```

**❌ Unsafe (Runtime I/O - NOT in scope):**
```utlx
// If we had readFile (we don't and won't)
let config = readFile("/etc/passwd")  // Security disaster!
let data = parseJson(config)
```

**The module system explicitly avoids this pattern.**

### 9.4 Transitive Security

Security checks apply transitively:

```
main.utlx (✅ safe)
  ↓ imports
utils.utlx (✅ safe)
  ↓ imports
../../../etc/passwd (❌ BLOCKED at compile time)
```

Even if a deeply nested module tries to import a dangerous path, it's caught during compilation.

---

## 10. Migration Strategy

### 10.1 Backward Compatibility

**Guarantee:** All existing single-file UTL-X scripts continue to work without modification.

**How:**
- Modules are **opt-in** via `import`/`export` keywords
- Files without imports/exports work exactly as before
- No breaking changes to existing syntax

**Example:**

```utlx
// existing-script.utlx (no changes needed)
%utlx 1.0
input json
output json
---
{
  result: $input.value * 2
}
```

This continues to work identically in v2.0.

### 10.2 Gradual Adoption Path

**Step 1: Extract Reusable Logic**

Before:
```utlx
// transform.utlx (single file)
function CalculateTax(amount: Number, rate: Number): Number {
  amount * rate
};

function CalculateDiscount(amount: Number, percent: Number): Number {
  amount * (percent / 100)
};

{
  tax: CalculateTax($input.amount, 0.08),
  discount: CalculateDiscount($input.amount, 10)
}
```

After:
```utlx
// lib/financial.utlx (new module)
export function CalculateTax(amount: Number, rate: Number): Number {
  amount * rate
}

export function CalculateDiscount(amount: Number, percent: Number): Number {
  amount * (percent / 100)
}
```

```utlx
// transform.utlx (updated)
%utlx 1.0
input json
output json

import { CalculateTax, CalculateDiscount } from "./lib/financial.utlx"

---
{
  tax: CalculateTax($input.amount, 0.08),
  discount: CalculateDiscount($input.amount, 10)
}
```

**Step 2: Build Module Libraries**

```
project/
├── lib/
│   ├── financial.utlx       # Financial calculations
│   ├── validation.utlx       # Input validation
│   └── formatting.utlx       # Output formatting
└── transforms/
    ├── order-transform.utlx  # Uses lib modules
    └── invoice-transform.utlx
```

**Step 3: Share Across Projects**

Create a shared utilities repository:

```
utlx-shared-utils/
├── src/
│   ├── date-utils.utlx
│   ├── string-utils.utlx
│   └── math-utils.utlx
└── README.md

# Use in projects via relative path or symlink
project/
├── vendor/
│   └── shared-utils -> /path/to/utlx-shared-utils/src
└── transforms/
    └── main.utlx (imports from vendor/shared-utils)
```

### 10.3 Tooling Support

**CLI Flag for Module Mode:**

```bash
# Explicit module mode (v2.0)
./utlx transform --modules main.utlx $input.json

# Auto-detect (if file has imports)
./utlx transform main.utlx $input.json  # Detects imports automatically
```

**Bundler (Future):**

```bash
# Bundle multiple modules into single file
./utlx bundle main.utlx --output bundled.utlx

# Bundled file has no imports (all inlined)
./utlx transform bundled.utlx $input.json  # No module resolution needed
```

---

## 11. Examples

### 11.1 Basic Example: Tax Calculator

**File: lib/tax.utlx**
```utlx
// Tax calculation utilities
export let US_TAX_RATE = 0.08;
export let EU_TAX_RATE = 0.20;

export function CalculateUSTax(amount: Number): Number {
  amount * US_TAX_RATE
}

export function CalculateEUTax(amount: Number): Number {
  amount * EU_TAX_RATE
}

// Private helper (not exported)
function roundToTwoDecimals(value: Number): Number {
  round(value * 100) / 100
}
```

**File: order-transform.utlx**
```utlx
%utlx 1.0
input json
output json

import { CalculateUSTax, US_TAX_RATE } from "./lib/tax.utlx"

---
{
  orderId: $input.id,
  subtotal: $input.amount,
  taxRate: US_TAX_RATE,
  tax: CalculateUSTax($input.amount),
  total: $input.amount + CalculateUSTax($input.amount)
}
```

### 11.2 Multi-Module Example: E-Commerce

**File Structure:**
```
project/
├── lib/
│   ├── pricing.utlx
│   ├── inventory.utlx
│   └── shipping.utlx
└── transforms/
    ├── order-processing.utlx
    └── invoice-generation.utlx
```

**File: lib/pricing.utlx**
```utlx
export function CalculateDiscount(amount: Number, customerType: String): Number {
  match (customerType) {
    "VIP" => amount * 0.20,
    "PREMIUM" => amount * 0.10,
    "REGULAR" => amount * 0.05,
    _ => 0
  }
}

export function CalculateShippingCost(weight: Number, distance: Number): Number {
  let baseCost = 5.00;
  let weightCost = weight * 0.50;
  let distanceCost = distance * 0.10;

  baseCost + weightCost + distanceCost
}
```

**File: lib/inventory.utlx**
```utlx
export function CheckStock(productId: String, quantity: Number): Boolean {
  // In real implementation, would look up in inventory database
  // For now, simulate with simple logic
  quantity <= 100
}

export function ReserveStock(productId: String, quantity: Number): Boolean {
  CheckStock(productId, quantity)
}
```

**File: transforms/order-processing.utlx**
```utlx
%utlx 1.0
input json
output json

import { CalculateDiscount, CalculateShippingCost } from "../lib/pricing.utlx"
import { CheckStock } from "../lib/inventory.utlx"

---
{
  orderId: $input.orderId,

  // Calculate pricing
  subtotal: sum($input.items |> map(item => item.price * item.quantity)),

  let discount = CalculateDiscount(subtotal, $input.customerType);
  let shipping = CalculateShippingCost($input.totalWeight, $input.shippingDistance);

  discount: discount,
  shipping: shipping,
  total: subtotal - discount + shipping,

  // Check inventory
  items: $input.items |> map(item => {
    productId: item.productId,
    quantity: item.quantity,
    inStock: CheckStock(item.productId, item.quantity),
    status: CheckStock(item.productId, item.quantity) ? "available" : "backorder"
  })
}
```

### 11.3 Advanced Example: Namespace Imports

**File: lib/math-utils.utlx**
```utlx
export function Add(a: Number, b: Number): Number { a + b }
export function Subtract(a: Number, b: Number): Number { a - b }
export function Multiply(a: Number, b: Number): Number { a * b }
export function Divide(a: Number, b: Number): Number { a / b }
export function Power(base: Number, exp: Number): Number { base ** exp }

export let PI = 3.14159265359;
export let E = 2.71828182846;
```

**File: calculation.utlx**
```utlx
%utlx 1.0
input json
output json

import * as Math from "./lib/math-utils.utlx"

---
{
  // Use namespace-qualified calls
  sum: Math.Add($input.a, $input.b),
  product: Math.Multiply($input.a, $input.b),
  power: Math.Power($input.a, 2),

  // Access constants via namespace
  circleArea: Math.PI * Math.Power($input.radius, 2),

  // Complex expression
  result: Math.Divide(
    Math.Add($input.x, Math.Multiply($input.y, Math.E)),
    Math.Subtract($input.z, Math.PI)
  )
}
```

### 11.4 Example: Configuration Management

**File: config/environments.utlx**
```utlx
// Environment-specific configuration
export let DEV_API_URL = "http://localhost:8080/api";
export let PROD_API_URL = "https://api.production.com";

export let DEV_TAX_RATE = 0.05;  // Lower tax for dev testing
export let PROD_TAX_RATE = 0.08; // Real tax rate

export function GetEnvironment(env: String) {
  match (env) {
    "development" => {
      apiUrl: DEV_API_URL,
      taxRate: DEV_TAX_RATE,
      debugMode: true
    },
    "production" => {
      apiUrl: PROD_API_URL,
      taxRate: PROD_TAX_RATE,
      debugMode: false
    },
    _ => {
      apiUrl: DEV_API_URL,
      taxRate: DEV_TAX_RATE,
      debugMode: true
    }
  }
}
```

**File: application.utlx**
```utlx
%utlx 1.0
input json
output json

import { GetEnvironment } from "./config/environments.utlx"

---
{
  let env = GetEnvironment($input.environment ?? "production");

  // Use environment config
  apiEndpoint: env.apiUrl + "/orders",

  order: {
    items: $input.items,
    subtotal: sum($input.items |> map(i => i.price * i.quantity)),
    tax: subtotal * env.taxRate,
    total: subtotal + (subtotal * env.taxRate)
  },

  debug: env.debugMode ? {
    rawInput: input,
    environment: $input.environment,
    config: env
  } : null
}
```

---

## 12. Implementation Roadmap

### 12.1 Phase 1: Core Module System (4 weeks)

**Goal:** Basic import/export with relative paths

**Tasks:**
1. **Lexer/Parser Extensions** (1 week)
   - Add `export` keyword token
   - Parse import declarations
   - Parse export declarations
   - Update AST nodes

2. **Module Resolver** (1 week)
   - Implement path resolution (relative, absolute)
   - Path normalization
   - Security boundary enforcement
   - Cross-platform path handling

3. **Module Loader & Linker** (1 week)
   - Module discovery algorithm
   - Dependency graph construction
   - Circular dependency detection
   - Symbol resolution across modules

4. **Interpreter Integration** (1 week)
   - Update environment to handle imported symbols
   - Type checking across module boundaries
   - Error messages with multi-file context

**Deliverables:**
- ✅ Basic import/export working
- ✅ Relative path resolution
- ✅ Circular dependency detection
- ✅ Conformance tests for basic module usage

### 12.2 Phase 2: Advanced Features (2 weeks)

**Goal:** Namespace imports, re-exports, tooling

**Tasks:**
1. **Namespace Imports** (3 days)
   - `import * as Name from "./module"` syntax
   - Namespace symbol resolution
   - Dot notation for namespace access

2. **Re-exports** (3 days)
   - `export { Foo } from "./module"` syntax
   - `export * from "./module"` syntax
   - Transitive export resolution

3. **Error Improvements** (4 days)
   - Better error messages across files
   - "Did you mean" suggestions
   - Full import chain in errors

4. **CLI Integration** (4 days)
   - `--modules` flag
   - Auto-detection of module usage
   - Progress reporting for multi-file compilation

**Deliverables:**
- ✅ Namespace imports working
- ✅ Re-exports working
- ✅ Enhanced error messages
- ✅ CLI support

### 12.3 Phase 3: Optimization & Polish (2 weeks)

**Goal:** Performance, caching, documentation

**Tasks:**
1. **Module Caching** (1 week)
   - Cache parsed modules
   - Incremental compilation
   - Cache invalidation on file changes

2. **Performance Optimization** (3 days)
   - Parallel module parsing
   - Optimized dependency resolution
   - Benchmark suite

3. **Documentation & Examples** (4 days)
   - User guide for modules
   - Migration guide
   - Example projects
   - API reference

**Deliverables:**
- ✅ Module cache working
- ✅ Performance benchmarks meet targets
- ✅ Complete documentation

### 12.4 Success Criteria

**Functionality:**
- ✅ Import and export functions/constants
- ✅ Relative and absolute path resolution
- ✅ Circular dependency detection
- ✅ Namespace imports
- ✅ Re-exports

**Security:**
- ✅ No runtime file access
- ✅ Path sandbox enforced
- ✅ Security tests pass

**Performance:**
- ✅ Module resolution adds <100ms overhead
- ✅ Caching reduces re-compilation by 90%+
- ✅ Large projects (50+ modules) compile in <5s

**Quality:**
- ✅ 100% conformance test coverage
- ✅ Documentation complete
- ✅ Zero regressions in existing tests

### 12.5 Timeline Summary

| Phase | Duration | Deliverables |
|-------|----------|--------------|
| Phase 1: Core | 4 weeks | Basic import/export, path resolution |
| Phase 2: Advanced | 2 weeks | Namespaces, re-exports, CLI |
| Phase 3: Polish | 2 weeks | Caching, optimization, docs |
| **Total** | **8 weeks** | **Full module system** |

---

## 13. Comparison with Other Languages

### 13.1 JavaScript/TypeScript

**Similarities:**
- Similar import/export syntax
- Named imports: `import { Foo } from "./mod"`
- Namespace imports: `import * as Name from "./mod"`
- Re-exports supported

**Differences:**
- UTL-X: File extensions required (`.utlx`)
- UTL-X: No default exports (v2.0)
- UTL-X: Compile-time only (no dynamic `import()`)
- UTL-X: No package manager (yet)

### 13.2 Python

**Similarities:**
- Module-based organization
- Import syntax clear

**Differences:**
- Python: `from module import func`
- UTL-X: `import { func } from "./module.utlx"`
- Python: Directory-based modules
- UTL-X: File-based modules
- Python: `__init__.py` for packages
- UTL-X: No package concept (yet)

### 13.3 DataWeave (MuleSoft)

**Similarities:**
- Transformation language context
- Functional approach

**Differences:**
- DataWeave: Uses `import` with MuleSoft-specific module system
- DataWeave: Package-based (`import * from dw::core::Strings`)
- UTL-X: File-based initially, packages later
- DataWeave: Proprietary, vendor-locked
- UTL-X: Open source, independent

### 13.4 XSLT

**Similarities:**
- Transformation-focused
- Template-based (historical in UTL-X)

**Differences:**
- XSLT: `<xsl:import>` and `<xsl:include>`
- XSLT: Imports are runtime-merged templates
- UTL-X: Imports are compile-time symbol resolution
- XSLT: No module boundaries
- UTL-X: Clear module encapsulation

---

## 14. Open Questions & Future Work

### 14.1 Package Manager (v2.1+)

**Question:** Should UTL-X have a package manager like npm?

**Considerations:**
- **Pros:**
  - Easy sharing of utilities across projects
  - Versioning and dependency management
  - Community contributions

- **Cons:**
  - Large infrastructure requirement
  - Maintenance burden
  - Security concerns (supply chain attacks)

**Possible Approach:**
```bash
# Hypothetical future syntax
utlx install @company/utils
```

```utlx
// Use installed package
import { Foo } from "@company/utils"
```

**Decision:** Defer to v2.1+ based on community demand.

### 14.2 Monorepo Support (v2.2+)

**Question:** Should UTL-X support monorepo-style workspaces?

**Use Case:**
```
monorepo/
├── packages/
│   ├── utils/
│   │   └── src/
│   │       └── index.utlx
│   └── transforms/
│       └── src/
│           └── main.utlx (imports from utils)
└── utlx.workspace.json
```

**Features Needed:**
- Workspace configuration
- Package linking
- Shared dependencies
- Build orchestration

**Decision:** Defer to v2.2+ based on enterprise needs.

### 14.3 Hot Module Reloading (Future)

**Question:** Should UTL-X support hot reloading for development?

**Use Case:**
```bash
# Watch mode - recompile on file changes
utlx watch main.utlx --input data.json
```

**Benefits:**
- Faster development cycle
- Immediate feedback

**Challenges:**
- State management across reloads
- Dependency invalidation
- IDE integration

**Decision:** Nice-to-have for future developer tooling.

### 14.4 Conditional Imports (Future)

**Question:** Should UTL-X support platform-specific imports?

**Hypothetical Syntax:**
```utlx
import { WindowsPath } from "./windows-utils.utlx" if platform == "windows"
import { UnixPath } from "./unix-utils.utlx" if platform != "windows"
```

**Use Case:**
- Platform-specific utilities
- Environment-specific configurations

**Challenges:**
- Complicates static analysis
- Conditional compilation complexity
- May not be needed if transformation is format-agnostic

**Decision:** Probably not needed - UTL-X transformations should be platform-independent.

### 14.5 Module Visibility Levels (Future)

**Question:** Should UTL-X support multiple visibility levels?

**Hypothetical:**
```utlx
public export function PublicApi() { }    // Visible to all importers
internal export function InternalApi() { } // Only visible within same project/package
private function PrivateHelper() { }       // Not exported
```

**Benefits:**
- Better encapsulation
- Prevent misuse of internal APIs

**Challenges:**
- Complexity
- Enforcement across project boundaries

**Decision:** Defer - simple public/private is sufficient for v2.0.

---

## 15. Appendix: FAQ

### Q1: Why not use runtime `require()` like Node.js?

**A:** Compile-time imports provide:
- Better security (no arbitrary file access)
- Better performance (zero overhead)
- Better static analysis (type checking across modules)
- Better tooling support (IDE autocomplete, refactoring)

### Q2: Can I import non-UTL-X files (JSON, CSV)?

**A:** Not in v2.0. Modules must be `.utlx` files.

**Future:** May support importing data files:
```utlx
import config from "./config.json"  // Hypothetical future
```

### Q3: How do I share modules across projects?

**A:**
1. Use relative paths (if projects are in same directory tree)
2. Symlink shared modules into each project
3. Use Git submodules
4. Wait for package manager (v2.1+)

### Q4: What about circular dependencies between files?

**A:** Circular imports are **detected and rejected** at compile time. You must refactor:

```utlx
// BAD: a.utlx ↔ b.utlx cycle

// GOOD: Extract common code to c.utlx
// a.utlx → c.utlx ← b.utlx
```

### Q5: Can I use modules without the CLI?

**A:** Yes, if you're embedding UTL-X:

```kotlin
val compiler = UTLXCompiler(enableModules = true)
val result = compiler.compileAndRun(mainFile, input)
```

### Q6: Do imports add runtime overhead?

**A:** **No.** Imports are resolved at compile time. The final executable code has no difference between:
- Imported function
- Locally defined function

### Q7: Can I import from URLs?

**A:** Not in v2.0. Only local file paths supported.

**Future:** May consider HTTP imports (like Deno):
```utlx
import { Foo } from "https://example.com/utils.utlx"  // Hypothetical
```

### Q8: How do I debug across modules?

**A:** Error messages include full file paths and import chains:

```
Error in /project/transforms/main.utlx:10:15
  → imported from /project/lib/utils.utlx:25:3
  → definition in /project/lib/core.utlx:100:8
```

---

## 16. Conclusion

The UTL-X module system brings **code organization and reuse** to transformation scripts while maintaining:

- ✅ **Security:** Compile-time only, no runtime file access
- ✅ **Performance:** Zero runtime overhead
- ✅ **Simplicity:** Familiar syntax, clear semantics
- ✅ **Safety:** Circular dependency detection, type checking

**Target Timeline:** 8 weeks for full v2.0 implementation

**Next Steps:**
1. Review this design with stakeholders
2. Create detailed technical specification
3. Begin Phase 1 implementation
4. Iterate based on early feedback

---

**Document Version:** 1.0
**Last Updated:** October 22, 2025
**Status:** Design Document - Awaiting Implementation
