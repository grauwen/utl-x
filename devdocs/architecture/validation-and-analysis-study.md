# UTL-X Validation & Analysis: From Syntax to Semantics

**Document Version:** 1.0
**Last Updated:** 2025-10-29
**Status:** Design Study & Implementation Guide
**Author:** UTL-X Architecture Team

---

## Executive Summary

This document presents a comprehensive analysis of **validation and static analysis** for the UTL-X transformation language. We explore four distinct levels of validation—from syntactic correctness to logical soundness—and propose CLI tooling strategies for developers.

**Key Insights:**

1. **Parse errors ARE syntactic errors** - These terms are synonymous and represent the first level of validation
2. **Four validation levels** exist: Syntactic → Semantic → Schema → Logical
3. **Dependency graph construction** is essential for semantic validation and optimization
4. **Separate `validate` and `lint` commands** provide clearer developer experience (following industry patterns)

---

## Table of Contents

1. [Introduction & Validation Hierarchy](#1-introduction--validation-hierarchy)
2. [Syntactic Validation (Level 1)](#2-syntactic-validation-level-1)
3. [Semantic Validation (Level 2)](#3-semantic-validation-level-2)
4. [Schema Validation (Level 3)](#4-schema-validation-level-3)
5. [Logical Validation (Level 4)](#5-logical-validation-level-4)
6. [Dependency Graph Construction](#6-dependency-graph-construction)
7. [CLI Design: validate vs lint](#7-cli-design-validate-vs-lint)
8. [Comparison with Other Languages](#8-comparison-with-other-languages)
9. [Error Message Design](#9-error-message-design)
10. [Implementation Roadmap](#10-implementation-roadmap)
11. [Integration Points](#11-integration-points)

---

## 1. Introduction & Validation Hierarchy

### 1.1 What is Validation?

**Validation** in the context of programming languages refers to the process of checking whether source code (or data) conforms to defined rules at various levels of abstraction.

For UTL-X, validation occurs at multiple stages:

```
┌─────────────────────────────────────────────────────┐
│  SOURCE CODE                                        │
│  %utlx 1.0                                         │
│  input xml                                         │
│  output json                                       │
│  ---                                               │
│  { result: $input.Order.Items }                   │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│  LEVEL 1: SYNTACTIC VALIDATION                     │
│  ✓ Lexer: Valid tokens?                           │
│  ✓ Parser: Valid grammar?                         │
│  ✓ AST: Well-formed tree?                         │
│  → OUTPUT: AST or Parse Errors                    │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│  LEVEL 2: SEMANTIC VALIDATION                      │
│  ✓ Types: Type-safe operations?                   │
│  ✓ Variables: All defined before use?             │
│  ✓ Functions: Valid signatures and calls?         │
│  ✓ Scopes: Proper variable scoping?               │
│  → OUTPUT: Typed AST or Semantic Errors           │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│  LEVEL 3: SCHEMA VALIDATION                        │
│  ✓ Input: Data matches input schema?              │
│  ✓ Output: Result matches output schema?          │
│  ✓ Paths: All paths exist in schema?              │
│  → OUTPUT: Valid or Schema Errors                 │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│  LEVEL 4: LOGICAL VALIDATION (LINT)                │
│  ✓ Dead code: Unreachable expressions?            │
│  ✓ Unused: Unused variables/functions?            │
│  ✓ Best practices: Style violations?              │
│  ✓ Complexity: Too complex expressions?           │
│  → OUTPUT: Warnings and Suggestions               │
└─────────────────────────────────────────────────────┘
```

### 1.2 Terminology Clarification

**Q: Is a parse error a syntactic error?**

**A: YES - they are synonymous terms.**

- **Parse error** = **Syntactic error** = Grammar violation
- Both refer to violations of the language's syntactic rules
- Example: Missing closing brace, unexpected token, invalid expression

**Other terminology:**

- **Lexical error**: Invalid character sequence (e.g., `@#$%` where identifier expected)
- **Semantic error**: Type mismatch, undefined variable (AST is valid, meaning is not)
- **Runtime error**: Division by zero, null reference (occurs during execution)
- **Lint warning**: Style issue, potential bug, best practice violation

### 1.3 Validation in the UTL-X Pipeline

```kotlin
/**
 * Complete UTL-X pipeline with validation at each stage
 */
fun compileAndValidate(source: String): Result<CompiledTransform> {
    // LEVEL 1: Syntactic validation
    val parseResult = parser.parse(source)
    if (parseResult is ParseResult.Failure) {
        return Result.Failure(SyntacticErrors(parseResult.errors))
    }
    val ast = (parseResult as ParseResult.Success).program

    // LEVEL 2: Semantic validation
    val typeCheckResult = typeChecker.check(ast)
    if (typeCheckResult.hasErrors()) {
        return Result.Failure(SemanticErrors(typeCheckResult.errors))
    }

    // LEVEL 3: Schema validation (if schemas provided)
    if (hasInputSchema()) {
        val schemaCheckResult = schemaValidator.validatePaths(ast, inputSchema)
        if (schemaCheckResult.hasErrors()) {
            return Result.Failure(SchemaErrors(schemaCheckResult.errors))
        }
    }

    // LEVEL 4: Logical validation (optional, warnings only)
    val lintResult = linter.analyze(ast)
    if (lintResult.hasWarnings()) {
        logger.warn("Lint warnings: ${lintResult.warnings}")
    }

    // Compile to bytecode
    return Result.Success(compiler.compile(ast))
}
```

---

## 2. Syntactic Validation (Level 1)

### 2.1 Definition

**Syntactic validation** ensures source code conforms to the language grammar. This is the first stage of validation and produces an Abstract Syntax Tree (AST) if successful.

**Components:**
1. **Lexical analysis** (tokenization)
2. **Syntactic analysis** (parsing)
3. **AST construction**

### 2.2 Lexical Errors

**Lexer** converts character stream into tokens. Errors occur when character sequences don't form valid tokens.

**Examples:**

```utlx
// ❌ Invalid character
%utlx 1.0
input xml
output json
---
{ result: @invalid }
#         ^ Lexical error: '@' is not a valid character in this context
```

```utlx
// ❌ Unterminated string
{ name: "John Doe }
#                ^ Lexical error: Unterminated string literal
```

```kotlin
/**
 * Lexer error detection (simplified)
 */
class Lexer(private val source: String) {
    private var current = 0
    private val errors = mutableListOf<LexicalError>()

    fun scanToken(): Token {
        skipWhitespace()

        val start = current
        val c = advance()

        return when (c) {
            '"' -> scanString()
            in '0'..'9' -> scanNumber()
            in 'a'..'z', in 'A'..'Z', '_' -> scanIdentifier()
            '{', '}', '[', ']', '(', ')', ':', ',' -> Token(/*...*/)
            '@' -> {
                // @ is only valid in specific contexts (XML attributes)
                // Report error if used incorrectly
                errors.add(LexicalError(
                    message = "Unexpected character '@'",
                    location = Location(line, column)
                ))
                Token.Error
            }
            else -> {
                errors.add(LexicalError(
                    message = "Illegal character '${c}'",
                    location = Location(line, column)
                ))
                Token.Error
            }
        }
    }

    private fun scanString(): Token {
        val start = current
        while (!isAtEnd() && peek() != '"') {
            advance()
        }

        if (isAtEnd()) {
            errors.add(LexicalError(
                message = "Unterminated string literal",
                location = Location(line, start)
            ))
            return Token.Error
        }

        advance() // Closing "
        return Token.String(/*...*/)
    }
}
```

### 2.3 Parse Errors (Syntactic Errors)

**Parser** builds AST from tokens according to grammar rules. Errors occur when token sequence doesn't match grammar.

**Common parse errors:**

```utlx
// ❌ Missing closing brace
{
  name: "Alice",
  age: 30
# ^ Parse error: Expected '}' at end of object
```

```utlx
// ❌ Unexpected token
{ result: $input.Order. }
#                       ^ Parse error: Expected identifier after '.'
```

```utlx
// ❌ Invalid expression
let x = + 5
#       ^ Parse error: Expected expression before '+'
```

**Current parser implementation** (from `parser_impl.kt`):

```kotlin
/**
 * Parser with error recovery
 */
class Parser(private val tokens: List<Token>) {
    private var current = 0
    private val errors = mutableListOf<ParseError>()

    fun parse(): ParseResult {
        return try {
            val program = parseProgram()
            if (errors.isEmpty()) {
                ParseResult.Success(program)
            } else {
                ParseResult.Failure(errors)
            }
        } catch (e: ParseException) {
            ParseResult.Failure(errors + ParseError(e.message, e.location))
        }
    }

    private fun parseObjectLiteral(): Expression {
        val startToken = previous()
        consume(TokenType.LBRACE, "Expected '{'")

        val properties = mutableListOf<Pair<String, Expression>>()

        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            // Parse property: key: value
            val key = when {
                check(TokenType.IDENTIFIER) -> advance().lexeme
                check(TokenType.STRING) -> advance().literal as String
                else -> {
                    error("Expected property name")
                    synchronize() // Error recovery
                    continue
                }
            }

            consume(TokenType.COLON, "Expected ':' after property name")
            val value = parseExpression()
            properties.add(key to value)

            if (!check(TokenType.RBRACE)) {
                consume(TokenType.COMMA, "Expected ',' or '}' after property")
            }
        }

        consume(TokenType.RBRACE, "Expected '}' after object properties")
        return Expression.ObjectLiteral(properties, Location.from(startToken))
    }

    private fun error(message: String) {
        val location = if (isAtEnd()) {
            Location(previous().line, previous().column)
        } else {
            Location(peek().line, peek().column)
        }

        errors.add(ParseError(message, location))
        throw ParseException(message, location)
    }

    /**
     * Error recovery: Skip tokens until we reach a synchronization point
     */
    private fun synchronize() {
        advance()

        while (!isAtEnd()) {
            // Synchronization points
            when (peek().type) {
                TokenType.LET -> return
                TokenType.FUNCTION -> return
                TokenType.IF -> return
                TokenType.MATCH -> return
                TokenType.LBRACE -> return
                else -> advance()
            }
        }
    }
}
```

### 2.4 AST Construction Errors

After parsing, AST must be **well-formed** (structurally valid).

**Examples:**

```kotlin
// ❌ Invalid AST: Function with no body
Expression.FunctionDefinition(
    name = "MyFunc",
    parameters = listOf("x", "y"),
    body = null  // Error: Body cannot be null
)

// ❌ Invalid AST: Let binding with no value
Expression.LetBinding(
    name = "x",
    value = null,  // Error: Value cannot be null
    body = Expression.Variable("x")
)
```

### 2.5 Syntactic Validation Summary

| Error Type | Example | Detection Phase |
|------------|---------|-----------------|
| Lexical | `@invalid`, `"unterminated` | Lexer |
| Parse/Syntactic | `{ x: }`, `let x = + 5` | Parser |
| AST Structural | Null body, invalid node | AST construction |

**Output of Level 1:**
- **Success**: Valid AST ready for semantic analysis
- **Failure**: List of parse errors with locations

---

## 3. Semantic Validation (Level 2)

### 3.1 Definition

**Semantic validation** checks whether a syntactically correct program has **valid meaning**. The AST is well-formed, but we need to verify:

1. **Type correctness**: Operations are type-safe
2. **Variable resolution**: All variables defined before use
3. **Function signatures**: Calls match definitions
4. **Scope rules**: Variables accessed in correct scopes

### 3.2 Type Checking

**Type system** (from `type_system.kt`) provides static type checking.

**Examples:**

```utlx
// ❌ Type error: String + Number
let name = "Alice"
let result = name + 42
#            ^^^^^^^^^ Semantic error: Cannot add String and Number
```

```utlx
// ❌ Type error: Calling non-function
let x = 5
let result = x(10)
#            ^^^^^ Semantic error: 'x' is not a function
```

```utlx
// ✅ Type correct
let name = "Alice"
let age = 30
let greeting = "Hello, " + name
let doubled = age * 2
```

**Type checker implementation:**

```kotlin
/**
 * Type checker using type_system.kt
 */
class TypeChecker {
    private val env = TypeEnvironment()
    private val errors = mutableListOf<SemanticError>()

    fun check(program: Program): TypeCheckResult {
        // Check header
        checkHeader(program.header)

        // Check body with environment
        val bodyType = checkExpression(program.body, env)

        return TypeCheckResult(
            type = bodyType,
            errors = errors,
            environment = env
        )
    }

    private fun checkExpression(expr: Expression, env: TypeEnvironment): UTLXType {
        return when (expr) {
            is Expression.Literal -> {
                when (expr.value) {
                    is String -> UTLXType.String
                    is Number -> UTLXType.Number
                    is Boolean -> UTLXType.Boolean
                    null -> UTLXType.Null
                    else -> UTLXType.Unknown
                }
            }

            is Expression.Variable -> {
                env.lookup(expr.name) ?: run {
                    errors.add(SemanticError(
                        message = "Undefined variable '${expr.name}'",
                        location = expr.location
                    ))
                    UTLXType.Unknown
                }
            }

            is Expression.BinaryOp -> {
                val leftType = checkExpression(expr.left, env)
                val rightType = checkExpression(expr.right, env)

                checkBinaryOp(expr.operator, leftType, rightType, expr.location)
            }

            is Expression.FunctionCall -> {
                val funcType = checkExpression(expr.function, env)

                if (funcType !is UTLXType.Function) {
                    errors.add(SemanticError(
                        message = "Cannot call non-function type: $funcType",
                        location = expr.location
                    ))
                    return UTLXType.Unknown
                }

                // Check argument count
                if (expr.arguments.size != funcType.parameterTypes.size) {
                    errors.add(SemanticError(
                        message = "Expected ${funcType.parameterTypes.size} arguments, got ${expr.arguments.size}",
                        location = expr.location
                    ))
                }

                // Check argument types
                expr.arguments.zip(funcType.parameterTypes).forEach { (arg, expectedType) ->
                    val argType = checkExpression(arg, env)
                    if (!argType.isAssignableTo(expectedType)) {
                        errors.add(SemanticError(
                            message = "Type mismatch: expected $expectedType, got $argType",
                            location = expr.location
                        ))
                    }
                }

                funcType.returnType
            }

            is Expression.LetBinding -> {
                val valueType = checkExpression(expr.value, env)
                env.define(expr.name, valueType)
                checkExpression(expr.body, env)
            }

            // ... other expression types
        }
    }

    private fun checkBinaryOp(
        op: BinaryOperator,
        leftType: UTLXType,
        rightType: UTLXType,
        location: Location
    ): UTLXType {
        return when (op) {
            BinaryOperator.PLUS -> {
                when {
                    leftType == UTLXType.String || rightType == UTLXType.String -> {
                        UTLXType.String  // String concatenation
                    }
                    leftType == UTLXType.Number && rightType == UTLXType.Number -> {
                        UTLXType.Number  // Numeric addition
                    }
                    else -> {
                        errors.add(SemanticError(
                            message = "Cannot add $leftType and $rightType",
                            location = location
                        ))
                        UTLXType.Unknown
                    }
                }
            }

            BinaryOperator.MINUS, BinaryOperator.MULTIPLY, BinaryOperator.DIVIDE -> {
                if (leftType != UTLXType.Number || rightType != UTLXType.Number) {
                    errors.add(SemanticError(
                        message = "Arithmetic operation requires numbers, got $leftType and $rightType",
                        location = location
                    ))
                    UTLXType.Unknown
                } else {
                    UTLXType.Number
                }
            }

            // ... other operators
        }
    }
}
```

### 3.3 Variable Resolution

**Undefined variables** are semantic errors:

```utlx
// ❌ Undefined variable
let x = 10
let y = z + x
#       ^ Semantic error: Undefined variable 'z'
```

```utlx
// ❌ Variable used before definition
let y = x + 10
let x = 5
#       ^ Semantic error: 'x' used before defined (in some analysis modes)
```

```utlx
// ✅ Correct variable resolution
let x = 5
let y = x + 10  // OK: x is defined
```

**Implementation:**

```kotlin
/**
 * Variable resolution with scopes
 */
class VariableResolver {
    private val scopes = mutableListOf<MutableSet<String>>()
    private val errors = mutableListOf<SemanticError>()

    fun resolve(program: Program): ResolutionResult {
        beginScope()

        // Resolve body
        resolveExpression(program.body)

        endScope()

        return ResolutionResult(errors)
    }

    private fun resolveExpression(expr: Expression) {
        when (expr) {
            is Expression.Variable -> {
                if (!isDefined(expr.name)) {
                    errors.add(SemanticError(
                        message = "Undefined variable '${expr.name}'",
                        location = expr.location
                    ))
                }
            }

            is Expression.LetBinding -> {
                // Resolve value first
                resolveExpression(expr.value)

                // Define variable in current scope
                define(expr.name)

                // Resolve body
                resolveExpression(expr.body)
            }

            is Expression.Lambda -> {
                beginScope()

                // Define parameters
                expr.parameters.forEach { param ->
                    define(param.name)
                }

                // Resolve body
                resolveExpression(expr.body)

                endScope()
            }

            // ... other expressions
        }
    }

    private fun beginScope() {
        scopes.add(mutableSetOf())
    }

    private fun endScope() {
        scopes.removeAt(scopes.size - 1)
    }

    private fun define(name: String) {
        if (scopes.isNotEmpty()) {
            scopes.last().add(name)
        }
    }

    private fun isDefined(name: String): Boolean {
        // Search scopes from innermost to outermost
        return scopes.reversed().any { name in it }
    }
}
```

### 3.4 Function Signature Validation

**Function definition rules** (from CLAUDE.md):

1. **User-defined functions MUST start with uppercase** (PascalCase)
2. **Stdlib functions use lowercase/camelCase**
3. **Arity must match at call site**

```utlx
// ❌ Lowercase user function
function calculateTax(amount, rate) {
#        ^^^^^^^^^^^^ Semantic error: User-defined functions must be PascalCase
  amount * rate
}
```

```utlx
// ✅ Correct PascalCase
function CalculateTax(amount: Number, rate: Number): Number {
  amount * rate
}
```

```utlx
// ❌ Arity mismatch
function CalculateTax(amount: Number, rate: Number): Number {
  amount * rate
}

let tax = CalculateTax(100)
#         ^^^^^^^^^^^^^^^^^ Semantic error: Expected 2 arguments, got 1
```

**Implementation:**

```kotlin
/**
 * Function signature validator
 */
class FunctionValidator {
    private val errors = mutableListOf<SemanticError>()

    fun validateFunctionDefinition(func: Expression.FunctionDefinition): List<SemanticError> {
        // Check PascalCase naming
        if (!func.name[0].isUpperCase()) {
            errors.add(SemanticError(
                message = "User-defined functions must start with uppercase letter (PascalCase). " +
                          "Got: '${func.name}'. Try: '${func.name.capitalize()}'. " +
                          "This prevents collisions with stdlib functions which use lowercase/camelCase.",
                location = func.location
            ))
        }

        // Check parameter names are unique
        val paramNames = func.parameters.map { it.name }
        val duplicates = paramNames.groupingBy { it }.eachCount().filter { it.value > 1 }
        if (duplicates.isNotEmpty()) {
            errors.add(SemanticError(
                message = "Duplicate parameter names: ${duplicates.keys}",
                location = func.location
            ))
        }

        return errors
    }

    fun validateFunctionCall(call: Expression.FunctionCall, funcDef: Expression.FunctionDefinition): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()

        // Check arity
        if (call.arguments.size != funcDef.parameters.size) {
            errors.add(SemanticError(
                message = "Function '${funcDef.name}' expects ${funcDef.parameters.size} arguments, got ${call.arguments.size}",
                location = call.location
            ))
        }

        return errors
    }
}
```

### 3.5 Scope Analysis

**Scope rules:**

```utlx
// ✅ Nested scopes
let x = 10
let outer = map([1, 2, 3], item => {
  let inner = item * x  // OK: x visible from outer scope
  inner + 1
})
```

```utlx
// ❌ Variable not visible outside scope
let result = map([1, 2, 3], item => {
  let temp = item * 2
  temp
})
let x = temp
#       ^^^^ Semantic error: 'temp' not defined in this scope
```

### 3.6 Semantic Validation Summary

| Error Type | Example | Detected By |
|------------|---------|-------------|
| Type mismatch | `"text" + 5` | Type checker |
| Undefined variable | `let x = y` (y not defined) | Variable resolver |
| Wrong arity | `func(1)` expects 2 args | Function validator |
| Invalid scope | Using lambda var outside | Scope analyzer |
| Naming violation | `function myFunc` (lowercase) | Function validator |

---

## 4. Schema Validation (Level 3)

### 4.1 Definition

**Schema validation** ensures that:
1. **Input data** matches declared input schema (XSD, JSON Schema)
2. **Output data** matches declared output schema
3. **Transformation paths** exist in input schema

This validation bridges design-time (transformation) and runtime (data).

### 4.2 Runtime Data Validation

From the **three-phase-runtime-validation-first.md** document, schema validation is a **mandatory first step** in middleware:

```kotlin
/**
 * Runtime message processing with validation
 */
class CopyBasedExecutor(
    private val transform: SchemaAwareCompiledTransform,
    private val config: ExecutorConfig
) : TransformExecutor {
    private val inputValidator: Validator?
    private val outputValidator: Validator?

    override fun transform(input: String): String {
        // STEP 1: Validate input (mandatory in middleware)
        val validationResult = inputValidator?.validate(input)
            ?: ValidationResult.Skipped("No schema")

        when (validationResult) {
            is ValidationResult.Valid -> {
                logger.debug("Validation passed")
            }
            is ValidationResult.Invalid -> {
                handleInvalidInput(validationResult, transform.validationPolicy)
            }
            is ValidationResult.Skipped -> {
                logger.debug("Validation skipped")
            }
        }

        // STEP 2-5: Copy model, fill, transform, serialize
        // ... (always continues, even if invalid)
    }

    private fun handleInvalidInput(
        result: ValidationResult.Invalid,
        policy: ValidationPolicy
    ) {
        when (policy) {
            ValidationPolicy.STRICT -> {
                throw ValidationException(result.errors)
            }
            ValidationPolicy.WARN_AND_CONTINUE -> {
                logger.warn("Invalid input, continuing: ${result.errors}")
            }
            ValidationPolicy.SILENT -> {
                // No action
            }
            ValidationPolicy.CUSTOM -> {
                config.customHandler?.handle(result)
            }
        }
    }
}
```

**Validation policies:**

```kotlin
enum class ValidationPolicy {
    /**
     * Stop processing on invalid data
     * Throw exception, fail transformation
     */
    STRICT,

    /**
     * Log warning, continue processing
     * DEFAULT for middleware
     */
    WARN_AND_CONTINUE,

    /**
     * Silently continue (not recommended)
     */
    SILENT,

    /**
     * User-defined handler
     */
    CUSTOM
}
```

### 4.3 Design-Time Path Validation

**At compilation**, validate that transformation paths exist in schema:

```utlx
%utlx 1.0
input xml schema "order.xsd"
output json
---
{
  customer: $input.Order.Customer.Name,
  #                ^^^^^^^^^^^^^^^^^ Check: Does this path exist in order.xsd?
  total: $input.Order.NonExistentField
  #              ^^^^^^^^^^^^^^^^^ Schema error: Path not found in XSD
}
```

**Implementation:**

```kotlin
/**
 * Schema path validator
 */
class SchemaPathValidator {

    fun validatePaths(ast: Program, schema: Schema): List<SchemaError> {
        val errors = mutableListOf<SchemaError>()

        // Extract all input paths from AST
        val pathCollector = PathCollector()
        val paths = pathCollector.collectPaths(ast)

        // Validate each path against schema
        paths.forEach { (path, location) ->
            if (!schema.hasPath(path)) {
                errors.add(SchemaError(
                    message = "Path '$path' does not exist in input schema",
                    location = location,
                    suggestion = schema.findSimilarPath(path)
                ))
            }
        }

        return errors
    }
}

/**
 * Collect all input access paths from AST
 */
class PathCollector {
    fun collectPaths(program: Program): List<Pair<String, Location>> {
        val paths = mutableListOf<Pair<String, Location>>()
        collectFromExpression(program.body, paths)
        return paths
    }

    private fun collectFromExpression(expr: Expression, paths: MutableList<Pair<String, Location>>) {
        when (expr) {
            is Expression.FieldAccess -> {
                if (expr.base is Expression.Variable && expr.base.name == "input") {
                    // This is $input.Order.Customer.Name
                    val path = buildPath(expr)
                    paths.add(path to expr.location)
                }
                collectFromExpression(expr.base, paths)
            }

            is Expression.BinaryOp -> {
                collectFromExpression(expr.left, paths)
                collectFromExpression(expr.right, paths)
            }

            // ... other expression types
        }
    }

    private fun buildPath(fieldAccess: Expression.FieldAccess): String {
        val parts = mutableListOf<String>()
        var current: Expression = fieldAccess

        while (current is Expression.FieldAccess) {
            parts.add(0, current.field)
            current = current.base
        }

        return parts.joinToString(".")
    }
}
```

### 4.4 XML Schema (XSD) Validation

Using **javax.xml.validation** for XSD validation:

```kotlin
/**
 * XSD validator (from three-phase-runtime doc)
 */
class XMLValidator(
    private val validator: javax.xml.validation.Validator
) {
    fun validate(xml: String): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        try {
            val errorHandler = CollectingErrorHandler(errors)

            synchronized(validator) {
                validator.errorHandler = errorHandler
                validator.validate(StreamSource(StringReader(xml)))
            }

            return if (errors.isEmpty()) {
                ValidationResult.Valid
            } else {
                ValidationResult.Invalid(errors)
            }
        } catch (e: SAXException) {
            errors.add(ValidationError(
                line = 0,
                column = 0,
                message = e.message ?: "Unknown error",
                severity = Severity.ERROR
            ))
            return ValidationResult.Invalid(errors)
        }
    }
}
```

### 4.5 JSON Schema Validation

Using **everit-org/json-schema** library:

```kotlin
/**
 * JSON Schema validator
 */
class JSONSchemaValidator(
    private val schema: org.everit.json.schema.Schema
) {
    fun validate(json: String): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        try {
            val jsonObject = JSONObject(JSONTokener(json))
            schema.validate(jsonObject)

            return ValidationResult.Valid
        } catch (e: org.everit.json.schema.ValidationException) {
            e.causingExceptions.forEach { cause ->
                errors.add(ValidationError(
                    line = 0,
                    column = 0,
                    message = "${cause.pointerToViolation}: ${cause.message}",
                    severity = Severity.ERROR
                ))
            }

            if (errors.isEmpty()) {
                errors.add(ValidationError(
                    line = 0,
                    column = 0,
                    message = e.message ?: "Validation failed",
                    severity = Severity.ERROR
                ))
            }

            return ValidationResult.Invalid(errors)
        }
    }
}
```

### 4.6 USDL Schema Validation

For **proto/xsd/jsch** schemas, validate USDL structure:

```kotlin
/**
 * USDL schema validator
 */
class USDLValidator {

    fun validateProtobufSchema(usdl: UDM): List<SchemaError> {
        val errors = mutableListOf<SchemaError>()

        // Check %types directive exists
        if (!usdl.hasProperty("%types")) {
            errors.add(SchemaError(
                message = "USDL schema must have '%types' directive"
            ))
            return errors
        }

        val types = usdl.getProperty("%types") as? UDM.Object
            ?: run {
                errors.add(SchemaError(message = "'%types' must be an object"))
                return errors
            }

        // Validate each type
        types.properties.forEach { (typeName, typeValue) ->
            if (typeValue !is UDM.Object) {
                errors.add(SchemaError(
                    message = "Type '$typeName' must be an object"
                ))
                return@forEach
            }

            val kind = typeValue.getProperty("%kind")?.toString()

            when (kind) {
                "structure" -> validateStructureType(typeName, typeValue, errors)
                "enum" -> validateEnumType(typeName, typeValue, errors)
                else -> errors.add(SchemaError(
                    message = "Type '$typeName' has invalid '%kind': $kind"
                ))
            }
        }

        return errors
    }

    private fun validateStructureType(
        typeName: String,
        typeValue: UDM.Object,
        errors: MutableList<SchemaError>
    ) {
        val fields = typeValue.getProperty("%fields")

        if (fields == null) {
            errors.add(SchemaError(
                message = "Structure '$typeName' missing '%fields'"
            ))
            return
        }

        if (fields !is UDM.Array) {
            errors.add(SchemaError(
                message = "Structure '$typeName' '%fields' must be an array"
            ))
            return
        }

        // Validate each field has required properties
        fields.elements.forEachIndexed { index, field ->
            if (field !is UDM.Object) {
                errors.add(SchemaError(
                    message = "Field $index in '$typeName' must be an object"
                ))
                return@forEachIndexed
            }

            // Check required field properties
            if (!field.hasProperty("%name")) {
                errors.add(SchemaError(
                    message = "Field $index in '$typeName' missing '%name'"
                ))
            }

            if (!field.hasProperty("%type")) {
                errors.add(SchemaError(
                    message = "Field $index in '$typeName' missing '%type'"
                ))
            }

            // For protobuf, field number is required
            if (!field.hasProperty("%fieldNumber")) {
                errors.add(SchemaError(
                    message = "Field $index in '$typeName' missing '%fieldNumber' (required for protobuf)"
                ))
            }
        }
    }
}
```

### 4.7 Schema Validation Summary

| Validation Type | When | What |
|----------------|------|------|
| **Runtime input** | During transformation | Data matches input schema (XSD, JSON Schema) |
| **Runtime output** | After transformation | Result matches output schema |
| **Design-time paths** | During compilation | Transformation paths exist in schema |
| **USDL correctness** | During schema operations | USDL structure valid for target format |

---

## 5. Logical Validation (Level 4)

### 5.1 Definition

**Logical validation** (also called **static analysis** or **linting**) checks for code quality issues that don't prevent execution but may indicate bugs or poor practices.

**Categories:**
1. **Dead code detection**
2. **Unused variables/functions**
3. **Unreachable code**
4. **Code complexity**
5. **Best practices**
6. **Style guidelines**

### 5.2 Dead Code Detection

**Dead code** is code that can never be executed:

```utlx
// ❌ Dead code after return
function Calculate(x: Number): Number {
  return x * 2
  let y = x + 10  // Warning: Unreachable code
  return y        // Warning: Unreachable code
}
```

```utlx
// ❌ Constant condition (always false)
if (false) {
  // Warning: Dead code, condition always false
  doSomething()
}
```

**Implementation:**

```kotlin
/**
 * Dead code analyzer
 */
class DeadCodeAnalyzer {

    fun analyze(program: Program): List<LintWarning> {
        val warnings = mutableListOf<LintWarning>()
        analyzeExpression(program.body, warnings)
        return warnings
    }

    private fun analyzeExpression(expr: Expression, warnings: MutableList<LintWarning>) {
        when (expr) {
            is Expression.Block -> {
                var foundReturn = false

                expr.expressions.forEach { subExpr ->
                    if (foundReturn) {
                        warnings.add(LintWarning(
                            message = "Unreachable code",
                            location = subExpr.location,
                            severity = Severity.WARNING
                        ))
                    }

                    if (subExpr is Expression.Return) {
                        foundReturn = true
                    }

                    analyzeExpression(subExpr, warnings)
                }
            }

            is Expression.Conditional -> {
                // Check for constant conditions
                if (expr.condition is Expression.Literal) {
                    val value = (expr.condition as Expression.Literal).value
                    if (value is Boolean) {
                        if (value == true && expr.elseBranch != null) {
                            warnings.add(LintWarning(
                                message = "Else branch unreachable (condition always true)",
                                location = expr.elseBranch.location,
                                severity = Severity.WARNING
                            ))
                        } else if (value == false) {
                            warnings.add(LintWarning(
                                message = "Then branch unreachable (condition always false)",
                                location = expr.thenBranch.location,
                                severity = Severity.WARNING
                            ))
                        }
                    }
                }

                analyzeExpression(expr.condition, warnings)
                analyzeExpression(expr.thenBranch, warnings)
                expr.elseBranch?.let { analyzeExpression(it, warnings) }
            }

            // ... other expressions
        }
    }
}
```

### 5.3 Unused Variables

**Unused bindings** waste memory and indicate potential bugs:

```utlx
// ❌ Unused variable
let x = 10
let y = 20  // Warning: Variable 'y' is defined but never used
x * 2
```

```utlx
// ✅ All variables used
let x = 10
let y = 20
x + y
```

**Implementation:**

```kotlin
/**
 * Unused variable analyzer
 */
class UnusedVariableAnalyzer {

    fun analyze(program: Program): List<LintWarning> {
        val defined = mutableSetOf<String>()
        val used = mutableSetOf<String>()

        collectDefinitions(program.body, defined)
        collectUsages(program.body, used)

        val unused = defined - used

        return unused.map { varName ->
            LintWarning(
                message = "Variable '$varName' is defined but never used",
                location = Location.unknown, // Would need to track locations
                severity = Severity.WARNING
            )
        }
    }

    private fun collectDefinitions(expr: Expression, defined: MutableSet<String>) {
        when (expr) {
            is Expression.LetBinding -> {
                defined.add(expr.name)
                collectDefinitions(expr.value, defined)
                collectDefinitions(expr.body, defined)
            }

            is Expression.FunctionDefinition -> {
                defined.add(expr.name)
                // Don't collect parameters as top-level definitions
            }

            // ... other expressions
        }
    }

    private fun collectUsages(expr: Expression, used: MutableSet<String>) {
        when (expr) {
            is Expression.Variable -> {
                if (expr.name != "input") {  // $input is special
                    used.add(expr.name)
                }
            }

            is Expression.BinaryOp -> {
                collectUsages(expr.left, used)
                collectUsages(expr.right, used)
            }

            is Expression.LetBinding -> {
                collectUsages(expr.value, used)
                collectUsages(expr.body, used)
            }

            // ... other expressions
        }
    }
}
```

### 5.4 Code Complexity

**Cyclomatic complexity** measures code complexity:

```utlx
// ❌ High complexity (nested conditionals)
function ProcessOrder(order: Object): Object {
  if (order.status == "pending") {
    if (order.amount > 1000) {
      if (order.customer.type == "VIP") {
        if (order.region == "US") {
          // Warning: High complexity (4 nested ifs)
          applyVIPDiscount()
        }
      }
    }
  }
  order
}
```

**Recommendation: Refactor to reduce nesting:**

```utlx
// ✅ Reduced complexity
function ProcessOrder(order: Object): Object {
  let isEligible = order.status == "pending" &&
                   order.amount > 1000 &&
                   order.customer.type == "VIP" &&
                   order.region == "US"

  if (isEligible) {
    applyVIPDiscount()
  }

  order
}
```

### 5.5 Best Practices

**Lint rules for best practices:**

1. **Prefer `let` bindings over nested expressions**
   ```utlx
   // ❌ Hard to read
   $input.items |> filter(i => i.price > 100) |> map(i => i.name)

   // ✅ Clear with let bindings
   let expensive = filter($input.items, i => i.price > 100)
   let names = map(expensive, i => i.name)
   names
   ```

2. **Avoid magic numbers**
   ```utlx
   // ❌ Magic number
   let tax = amount * 0.08

   // ✅ Named constant
   let TAX_RATE = 0.08
   let tax = amount * TAX_RATE
   ```

3. **Prefer early returns**
   ```utlx
   // ❌ Nested conditionals
   function Calculate(x: Number): Number {
     if (x > 0) {
       if (x < 100) {
         return x * 2
       } else {
         return x
       }
     } else {
       return 0
     }
   }

   // ✅ Early returns
   function Calculate(x: Number): Number {
     if (x <= 0) return 0
     if (x >= 100) return x
     return x * 2
   }
   ```

### 5.6 Style Guidelines

**UTL-X style conventions:**

1. **Function names**: PascalCase for user functions
2. **Variable names**: camelCase
3. **Constants**: UPPER_SNAKE_CASE (by convention)
4. **Indentation**: 2 spaces
5. **Line length**: Max 120 characters

**Example linter:**

```kotlin
/**
 * Style checker
 */
class StyleChecker {

    fun check(program: Program): List<LintWarning> {
        val warnings = mutableListOf<LintWarning>()
        checkExpression(program.body, warnings)
        return warnings
    }

    private fun checkExpression(expr: Expression, warnings: MutableList<LintWarning>) {
        when (expr) {
            is Expression.LetBinding -> {
                // Check variable naming (camelCase)
                if (!isValidVariableName(expr.name)) {
                    warnings.add(LintWarning(
                        message = "Variable '${expr.name}' should use camelCase",
                        location = expr.location,
                        severity = Severity.INFO
                    ))
                }

                checkExpression(expr.value, warnings)
                checkExpression(expr.body, warnings)
            }

            // ... other expressions
        }
    }

    private fun isValidVariableName(name: String): Boolean {
        // camelCase: starts lowercase, no underscores (except constants)
        return name[0].isLowerCase() && !name.contains('_')
    }
}
```

### 5.7 Logical Validation Summary

| Warning Type | Example | Severity |
|--------------|---------|----------|
| Dead code | Code after return | WARNING |
| Unused variable | Defined but never used | WARNING |
| High complexity | Deeply nested ifs | INFO |
| Magic numbers | Unnamed constants | INFO |
| Style violation | Wrong naming convention | INFO |

---

## 6. Dependency Graph Construction

### 6.1 What is a Dependency Graph?

A **dependency graph** represents relationships between variables and expressions in a program. Nodes are variables/expressions, edges represent "depends on" relationships.

**Uses:**
1. **Variable ordering**: Determine execution order for let bindings
2. **Circular dependency detection**: Find cycles (invalid programs)
3. **Dead code elimination**: Remove unused definitions
4. **Incremental compilation**: Recompile only changed dependencies
5. **Optimization**: Inline simple bindings, constant propagation

### 6.2 Building the Dependency Graph

**Example program:**

```utlx
let a = 10
let b = a + 5
let c = b * 2
let d = a + b
let result = c + d
result
```

**Dependency graph:**

```
     a
    / \
   b   d
   |   |
   c   |
    \ /
  result
```

**Edges:**
- `b` depends on `a`
- `c` depends on `b`
- `d` depends on `a` and `b`
- `result` depends on `c` and `d`

### 6.3 Algorithm

```kotlin
/**
 * Dependency graph builder
 */
class DependencyGraphBuilder {

    /**
     * Build dependency graph from AST
     */
    fun build(program: Program): DependencyGraph {
        val graph = DependencyGraph()

        // Collect all let bindings and their dependencies
        collectBindings(program.body, graph)

        return graph
    }

    private fun collectBindings(expr: Expression, graph: DependencyGraph) {
        when (expr) {
            is Expression.LetBinding -> {
                // Add node for this binding
                graph.addNode(expr.name)

                // Find variables used in the value expression
                val dependencies = findDependencies(expr.value)

                // Add edges from dependencies to this binding
                dependencies.forEach { dep ->
                    graph.addEdge(from = dep, to = expr.name)
                }

                // Recursively process body
                collectBindings(expr.body, graph)
            }

            is Expression.Block -> {
                expr.expressions.forEach { collectBindings(it, graph) }
            }

            // ... other expressions
        }
    }

    private fun findDependencies(expr: Expression): Set<String> {
        val deps = mutableSetOf<String>()

        when (expr) {
            is Expression.Variable -> {
                if (expr.name != "input") {  // $input is not a dependency
                    deps.add(expr.name)
                }
            }

            is Expression.BinaryOp -> {
                deps.addAll(findDependencies(expr.left))
                deps.addAll(findDependencies(expr.right))
            }

            is Expression.FunctionCall -> {
                deps.addAll(findDependencies(expr.function))
                expr.arguments.forEach { deps.addAll(findDependencies(it)) }
            }

            is Expression.Lambda -> {
                // Lambda parameters shadow outer variables
                val lambdaDeps = findDependencies(expr.body)
                val paramNames = expr.parameters.map { it.name }.toSet()
                deps.addAll(lambdaDeps - paramNames)
            }

            // ... other expressions
        }

        return deps
    }
}

/**
 * Dependency graph data structure
 */
class DependencyGraph {
    private val nodes = mutableSetOf<String>()
    private val edges = mutableMapOf<String, MutableSet<String>>()

    fun addNode(name: String) {
        nodes.add(name)
    }

    fun addEdge(from: String, to: String) {
        edges.getOrPut(from) { mutableSetOf() }.add(to)
    }

    fun getDependencies(name: String): Set<String> {
        return edges[name] ?: emptySet()
    }

    fun getAllNodes(): Set<String> {
        return nodes
    }

    /**
     * Topological sort: Order variables by dependencies
     */
    fun topologicalSort(): List<String> {
        val sorted = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val inProgress = mutableSetOf<String>()

        fun visit(node: String) {
            if (node in visited) return

            if (node in inProgress) {
                throw CyclicDependencyException("Circular dependency detected involving '$node'")
            }

            inProgress.add(node)

            // Visit dependencies first
            getDependencies(node).forEach { dep ->
                visit(dep)
            }

            inProgress.remove(node)
            visited.add(node)
            sorted.add(node)
        }

        nodes.forEach { visit(it) }

        return sorted
    }

    /**
     * Find cycles in the graph
     */
    fun findCycles(): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(node: String) {
            if (node in path) {
                // Found cycle
                val cycleStart = path.indexOf(node)
                cycles.add(path.subList(cycleStart, path.size) + node)
                return
            }

            if (node in visited) return

            visited.add(node)
            path.add(node)

            getDependencies(node).forEach { dep ->
                dfs(dep)
            }

            path.removeAt(path.size - 1)
        }

        nodes.forEach { dfs(it) }

        return cycles
    }
}
```

### 6.4 Circular Dependency Detection

**Invalid program with circular dependency:**

```utlx
let a = b + 1
let b = a + 1  // Error: Circular dependency between 'a' and 'b'
a + b
```

**Detection:**

```kotlin
/**
 * Circular dependency validator
 */
class CircularDependencyValidator {

    fun validate(graph: DependencyGraph): List<SemanticError> {
        val errors = mutableListOf<SemanticError>()

        val cycles = graph.findCycles()

        cycles.forEach { cycle ->
            errors.add(SemanticError(
                message = "Circular dependency detected: ${cycle.joinToString(" -> ")}",
                location = Location.unknown  // Would need to track locations
            ))
        }

        return errors
    }
}
```

### 6.5 Variable Ordering

**Use topological sort to determine execution order:**

```utlx
// Original (order may be wrong)
let c = b * 2
let a = 10
let b = a + 5
c
```

**After dependency analysis and reordering:**

```utlx
// Reordered by dependencies
let a = 10
let b = a + 5
let c = b * 2
c
```

**Implementation:**

```kotlin
/**
 * Variable orderer using dependency graph
 */
class VariableOrderer {

    fun reorder(program: Program): Program {
        val graph = DependencyGraphBuilder().build(program)

        // Get topological order
        val order = graph.topologicalSort()

        // Extract let bindings from program
        val bindings = extractLetBindings(program.body)

        // Reorder bindings according to topological order
        val orderedBindings = order.mapNotNull { name ->
            bindings.find { it.name == name }
        }

        // Reconstruct program with ordered bindings
        return reconstructProgram(program, orderedBindings)
    }
}
```

### 6.6 Dead Code Elimination

**Use dependency graph to find unused variables:**

```utlx
let a = 10
let b = 20     // Unused
let c = a + 5
let d = 30     // Unused
c
```

**Dependency graph shows:**
- `c` depends on `a`
- Final result depends on `c`
- **`b` and `d` are not in any dependency chain → dead code**

```kotlin
/**
 * Dead code eliminator
 */
class DeadCodeEliminator {

    fun eliminate(program: Program): Program {
        val graph = DependencyGraphBuilder().build(program)

        // Find the final expression
        val finalExpr = findFinalExpression(program.body)

        // Trace backwards from final expression
        val reachable = mutableSetOf<String>()
        traceDependencies(finalExpr, graph, reachable)

        // Remove unreachable bindings
        val filtered = filterUnreachable(program.body, reachable)

        return program.copy(body = filtered)
    }

    private fun traceDependencies(
        expr: Expression,
        graph: DependencyGraph,
        reachable: MutableSet<String>
    ) {
        val deps = findDependencies(expr)

        deps.forEach { dep ->
            if (dep !in reachable) {
                reachable.add(dep)
                // Recursively trace this dependency's dependencies
                graph.getDependencies(dep).forEach { transitiveDep ->
                    traceDependencies(Expression.Variable(transitiveDep), graph, reachable)
                }
            }
        }
    }
}
```

### 6.7 Dependency Graph Summary

| Use Case | Description | Algorithm |
|----------|-------------|-----------|
| **Variable ordering** | Determine let binding order | Topological sort |
| **Circular dependency** | Detect invalid cycles | Cycle detection (DFS) |
| **Dead code** | Remove unused bindings | Reachability analysis |
| **Optimization** | Inline/constant propagation | Data flow analysis |

**Visual example:**

```
┌─────────────────────────────────────────┐
│  let a = 10                             │
│  let b = a + 5                          │
│  let c = b * 2                          │
│  let d = 30  // unused                  │
│  let e = c + a                          │
│  e                                      │
└─────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  Dependency Graph:                      │
│                                         │
│       a ─────→ b ─────→ c               │
│       │                  │              │
│       └──────────────────┼─→ e          │
│                          │              │
│       d (isolated)       │              │
│                          ▼              │
│                      [result]           │
└─────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  Optimized (d removed):                 │
│                                         │
│  let a = 10                             │
│  let b = a + 5                          │
│  let c = b * 2                          │
│  let e = c + a                          │
│  e                                      │
└─────────────────────────────────────────┘
```

---

## 7. CLI Design: validate vs lint

### 7.1 Industry Patterns

**How other languages handle validation vs linting:**

| Language | Syntax/Semantics | Linting | Pattern |
|----------|------------------|---------|---------|
| **TypeScript** | `tsc` (compiler) | `eslint` | Separate tools |
| **Python** | `python` (interpreter) | `pylint`, `flake8` | Separate tools |
| **Rust** | `rustc` | `clippy` | Separate tools |
| **Go** | `go build` | `golint`, `go vet` | Separate tools |
| **JavaScript** | Node.js | `eslint`, `jshint` | Separate tools |
| **Java** | `javac` | `checkstyle`, `PMD` | Separate tools |

**Industry standard**: **Separate tools** for compilation/validation vs linting.

### 7.2 Option A: Separate Commands

```bash
# VALIDATION (Correctness checking)
utlx validate transform.utlx

# LINTING (Style and best practices)
utlx lint transform.utlx
```

**`utlx validate` checks:**
- ✅ Syntax errors (Level 1)
- ✅ Type errors (Level 2)
- ✅ Undefined variables (Level 2)
- ✅ Schema mismatches (Level 3)
- ❌ Does NOT check style/best practices

**`utlx lint` checks:**
- ✅ Dead code (Level 4)
- ✅ Unused variables (Level 4)
- ✅ Code complexity (Level 4)
- ✅ Best practices (Level 4)
- ✅ Style guidelines (Level 4)
- ❌ Does NOT fail on correctness errors (assumes `validate` passed)

**Command implementations:**

```kotlin
/**
 * Validate command (correctness)
 */
class ValidateCommand : Command {

    @Option(names = ["--schema"], description = "Input schema file (XSD, JSON Schema)")
    var schemaFile: String? = null

    @Option(names = ["--strict"], description = "Fail on any error")
    var strict: Boolean = false

    @Parameters(index = "0", description = "UTL-X file to validate")
    lateinit var inputFile: String

    override fun call(): Int {
        val source = File(inputFile).readText()

        // Level 1: Syntax
        val parseResult = parser.parse(source)
        if (parseResult is ParseResult.Failure) {
            printErrors("Syntax Errors", parseResult.errors)
            return 1
        }

        val ast = (parseResult as ParseResult.Success).program

        // Level 2: Semantics
        val typeCheckResult = typeChecker.check(ast)
        if (typeCheckResult.hasErrors()) {
            printErrors("Semantic Errors", typeCheckResult.errors)
            return 1
        }

        // Level 3: Schema (if provided)
        if (schemaFile != null) {
            val schema = loadSchema(schemaFile!!)
            val schemaErrors = schemaValidator.validate(ast, schema)
            if (schemaErrors.isNotEmpty()) {
                printErrors("Schema Errors", schemaErrors)
                return if (strict) 1 else 0  // Warn but don't fail unless --strict
            }
        }

        println("✓ Validation passed")
        return 0
    }
}

/**
 * Lint command (style and best practices)
 */
class LintCommand : Command {

    @Option(names = ["--fix"], description = "Auto-fix issues where possible")
    var fix: Boolean = false

    @Option(names = ["--rules"], description = "Lint rules file")
    var rulesFile: String? = null

    @Parameters(index = "0", description = "UTL-X file to lint")
    lateinit var inputFile: String

    override fun call(): Int {
        val source = File(inputFile).readText()

        // Parse (lint assumes code is valid)
        val parseResult = parser.parse(source)
        if (parseResult is ParseResult.Failure) {
            println("⚠️  Cannot lint: File has syntax errors. Run 'utlx validate' first.")
            return 1
        }

        val ast = (parseResult as ParseResult.Success).program

        // Run linters
        val warnings = mutableListOf<LintWarning>()

        warnings.addAll(DeadCodeAnalyzer().analyze(ast))
        warnings.addAll(UnusedVariableAnalyzer().analyze(ast))
        warnings.addAll(ComplexityAnalyzer().analyze(ast))
        warnings.addAll(StyleChecker().check(ast))

        // Print warnings
        if (warnings.isEmpty()) {
            println("✓ No lint issues found")
            return 0
        }

        printWarnings(warnings)

        // Auto-fix if requested
        if (fix) {
            val fixed = autoFix(ast, warnings)
            File(inputFile).writeText(fixed)
            println("✓ Fixed ${warnings.count { it.fixable }} issue(s)")
        }

        return 0  // Lint never fails (warnings only)
    }
}
```

**Pros:**
- ✅ Clear separation of concerns
- ✅ Follows industry standard pattern
- ✅ Users can run validation in CI, linting locally
- ✅ Linting doesn't fail builds (warnings only)

**Cons:**
- ❌ Two commands to remember
- ❌ Need to run both for complete checking

### 7.3 Option B: Unified Command

```bash
# UNIFIED COMMAND
utlx validate transform.utlx --lint --schema order.xsd
```

**Single command with flags:**

```kotlin
/**
 * Unified validate command
 */
class ValidateCommand : Command {

    @Option(names = ["--lint"], description = "Also run linting checks")
    var lint: Boolean = false

    @Option(names = ["--schema"], description = "Input schema file")
    var schemaFile: String? = null

    @Option(names = ["--strict"], description = "Treat warnings as errors")
    var strict: Boolean = false

    @Option(names = ["--fix"], description = "Auto-fix lint issues")
    var fix: Boolean = false

    @Parameters(index = "0", description = "UTL-X file to validate")
    lateinit var inputFile: String

    override fun call(): Int {
        val source = File(inputFile).readText()

        // Level 1-2: Syntax and Semantics
        val (ast, errors) = validateCorrectness(source)
        if (errors.isNotEmpty()) {
            printErrors(errors)
            return 1
        }

        // Level 3: Schema
        if (schemaFile != null) {
            val schemaErrors = validateSchema(ast!!, schemaFile!!)
            if (schemaErrors.isNotEmpty()) {
                printErrors(schemaErrors)
                if (strict) return 1
            }
        }

        // Level 4: Linting
        if (lint) {
            val warnings = runLinting(ast!!)
            if (warnings.isNotEmpty()) {
                printWarnings(warnings)
                if (strict) return 1

                if (fix) {
                    autoFix(inputFile, ast, warnings)
                }
            }
        }

        println("✓ Validation passed")
        return 0
    }
}
```

**Pros:**
- ✅ Single command to learn
- ✅ Composable with flags
- ✅ Can run all checks at once

**Cons:**
- ❌ Flag combinations can be confusing
- ❌ Harder to separate concerns in CI/CD
- ❌ Mixing errors (fail) and warnings (don't fail) can be unclear

### 7.4 Option C: Both Approaches

**Provide both for flexibility:**

```bash
# Short form (separate commands)
utlx validate transform.utlx
utlx lint transform.utlx

# Long form (unified)
utlx validate transform.utlx --lint

# Alias
utlx check transform.utlx  # Runs both validate + lint
```

**Implementation:**

```kotlin
// validate command (no lint by default)
class ValidateCommand { /* ... */ }

// lint command (separate)
class LintCommand { /* ... */ }

// check command (alias for validate --lint)
class CheckCommand : Command {
    override fun call(): Int {
        return ValidateCommand().apply {
            lint = true
        }.call()
    }
}
```

### 7.5 Recommendation

**RECOMMEND: Option A (Separate Commands)**

**Rationale:**
1. **Follows industry standards** (TypeScript, Python, Rust all use separate tools)
2. **Clear mental model**: `validate` = correctness, `lint` = style
3. **Better CI/CD integration**: Can fail on validation, warn on linting
4. **Gradual adoption**: Teams can adopt linting later without breaking builds

**Example workflow:**

```bash
# Development (check everything)
utlx validate transform.utlx && utlx lint transform.utlx

# CI/CD (fail on errors, warn on style)
utlx validate transform.utlx || exit 1
utlx lint transform.utlx  # Warnings only, doesn't fail build

# Auto-fix before commit
utlx lint transform.utlx --fix
```

### 7.6 CLI Output Examples

**Validate output:**

```bash
$ utlx validate transform.utlx

Validating: transform.utlx
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✓ Syntax validation passed
✓ Semantic validation passed
✓ Schema validation passed (order.xsd)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✓ Validation successful

0 errors found
```

**Validate with errors:**

```bash
$ utlx validate transform.utlx

Validating: transform.utlx
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✗ Syntax validation failed

Error: Expected '}' after object properties
  --> transform.utlx:12:34
   |
12 |   result: { name: "Alice", age: 30
   |                                   ^ Expected '}'

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✗ Validation failed

1 error found
```

**Lint output:**

```bash
$ utlx lint transform.utlx

Linting: transform.utlx
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

⚠️  3 warnings found:

Warning: Variable 'temp' is defined but never used
  --> transform.utlx:8:5
   |
 8 |   let temp = x * 2
   |       ^^^^ Consider removing this binding

Warning: Unreachable code detected
  --> transform.utlx:15:3
   |
15 |   return x
   |   ^^^^^^^^ Code after return statement

Info: Consider using camelCase for variable name
  --> transform.utlx:20:5
   |
20 |   let my_var = 10
   |       ^^^^^^ Use 'myVar' instead

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
⚠️  Linting complete

3 warnings (2 fixable)

Run with --fix to automatically fix issues.
```

---

## 8. Comparison with Other Languages

### 8.1 TypeScript

**Tools:**
- `tsc` (TypeScript compiler) - Syntax + type checking
- `eslint` - Linting

**Example:**

```bash
# Validation
$ tsc --noEmit
src/index.ts:12:5 - error TS2322: Type 'string' is not assignable to type 'number'.

# Linting
$ eslint src/
/src/index.ts
  10:1  warning  Unexpected console statement  no-console
  15:3  error    'temp' is assigned but never used  no-unused-vars
```

**Lessons for UTL-X:**
- Separate compilation and linting
- Type errors fail build, lint warnings don't
- Clear error messages with source locations

### 8.2 Python

**Tools:**
- `python -m py_compile` - Syntax checking
- `mypy` - Type checking
- `pylint` / `flake8` - Linting

**Example:**

```bash
# Syntax
$ python -m py_compile script.py
  File "script.py", line 10
    if x = 5:
         ^
SyntaxError: invalid syntax

# Type checking
$ mypy script.py
script.py:15: error: Incompatible types in assignment

# Linting
$ pylint script.py
script.py:20:0: C0103: Variable name "X" doesn't conform to snake_case naming style
script.py:25:0: W0612: Unused variable 'temp'
```

**Lessons for UTL-X:**
- Three-tier approach: syntax → types → lint
- Gradual typing (mypy optional)
- Multiple linter options (pylint, flake8, black)

### 8.3 Rust

**Tools:**
- `rustc` - Compilation (syntax + types + borrow checking)
- `cargo clippy` - Linting

**Example:**

```bash
# Compilation
$ cargo build
error[E0425]: cannot find value `y` in this scope
 --> src/main.rs:5:13
  |
5 |     let x = y + 1;
  |             ^ not found in this scope

# Linting
$ cargo clippy
warning: this variable does not need to be mutable
 --> src/main.rs:10:9
  |
10|     let mut x = 5;
  |         ----^
  |         help: remove this `mut`
```

**Lessons for UTL-X:**
- Excellent error messages (with suggestions)
- Compiler enforces correctness, clippy suggests improvements
- Warning levels (allow, warn, deny)

### 8.4 XSLT

**Tools:**
- Saxon (XSLT processor) - Well-formedness + schema validation

**Example:**

```bash
# Validation
$ saxon-ee -xsl:transform.xsl -s:input.xml
Error at xsl:variable on line 15:
  XTTE0570: Required type of variable $temp is xs:string; supplied value has type xs:integer

# No separate linting tool for XSLT
```

**Lessons for UTL-X:**
- XSLT lacks modern linting (opportunity for UTL-X to improve)
- Schema validation integrated with transformation
- Strong typing catches errors early

### 8.5 Comparison Table

| Language | Syntax/Types | Linting | Separate Tools? | Error Quality |
|----------|--------------|---------|-----------------|---------------|
| TypeScript | tsc | eslint | ✅ Yes | ⭐⭐⭐⭐ Excellent |
| Python | python/mypy | pylint | ✅ Yes | ⭐⭐⭐ Good |
| Rust | rustc | clippy | ✅ Yes | ⭐⭐⭐⭐⭐ Outstanding |
| Go | go build | golint | ✅ Yes | ⭐⭐⭐ Good |
| Java | javac | checkstyle | ✅ Yes | ⭐⭐ Fair |
| XSLT | Saxon | N/A | ❌ No | ⭐⭐ Fair |

**UTL-X Goal:** ⭐⭐⭐⭐ Excellent (Rust-quality errors, TypeScript familiarity)

---

## 9. Error Message Design

### 9.1 Severity Levels

```kotlin
enum class Severity {
    /**
     * Fatal error: Prevents compilation/execution
     * Examples: Syntax error, type error, undefined variable
     */
    ERROR,

    /**
     * Warning: Potential bug, should be reviewed
     * Examples: Unused variable, unreachable code
     */
    WARNING,

    /**
     * Info: Style suggestion, best practice
     * Examples: Naming convention, code complexity
     */
    INFO,

    /**
     * Hint: Helpful suggestion
     * Examples: "Did you mean...", optimization opportunity
     */
    HINT
}
```

### 9.2 Error Message Components

**Well-designed error message includes:**

1. **Error code** (optional, for documentation lookup)
2. **Primary message** (what went wrong)
3. **Source location** (file, line, column)
4. **Code snippet** (visual context)
5. **Explanation** (why it's wrong)
6. **Suggestion** (how to fix)

**Example:**

```
error[E0425]: Undefined variable 'customr'
  --> transform.utlx:12:15
   |
12 |   name: $input.Order.customr.Name,
   |                      ^^^^^^^ Variable not found
   |
   = help: Did you mean 'customer'?
   = note: Available fields: customer, items, total
```

### 9.3 Implementation

```kotlin
/**
 * Error message formatter (Rust-inspired)
 */
class ErrorFormatter {

    fun format(error: CompilationError, source: String): String {
        return buildString {
            // Header with severity and code
            append(formatHeader(error))
            append("\n")

            // Location
            append(formatLocation(error.location))
            append("\n")

            // Code snippet with highlighting
            append(formatCodeSnippet(error.location, source))
            append("\n")

            // Explanation
            if (error.explanation != null) {
                append("  = explanation: ${error.explanation}")
                append("\n")
            }

            // Suggestion
            if (error.suggestion != null) {
                append("  = help: ${error.suggestion}")
                append("\n")
            }

            // Additional notes
            error.notes.forEach { note ->
                append("  = note: $note")
                append("\n")
            }
        }
    }

    private fun formatHeader(error: CompilationError): String {
        val severity = when (error.severity) {
            Severity.ERROR -> "\u001B[31merror\u001B[0m"      // Red
            Severity.WARNING -> "\u001B[33mwarning\u001B[0m"  // Yellow
            Severity.INFO -> "\u001B[36minfo\u001B[0m"        // Cyan
            Severity.HINT -> "\u001B[32mhint\u001B[0m"        // Green
        }

        val code = if (error.code != null) "[$error.code]" else ""

        return "$severity$code: ${error.message}"
    }

    private fun formatLocation(location: Location): String {
        return "  --> ${location.file}:${location.line}:${location.column}"
    }

    private fun formatCodeSnippet(location: Location, source: String): String {
        val lines = source.lines()
        val lineIndex = location.line - 1

        if (lineIndex < 0 || lineIndex >= lines.size) {
            return ""
        }

        val lineNumber = location.line
        val lineNumberWidth = lineNumber.toString().length

        return buildString {
            // Line separator
            append("   ${" ".repeat(lineNumberWidth)}|\n")

            // Line with error
            append(String.format("%${lineNumberWidth}d", lineNumber))
            append(" | ")
            append(lines[lineIndex])
            append("\n")

            // Pointer to error column
            append("   ${" ".repeat(lineNumberWidth)}| ")
            append(" ".repeat(location.column - 1))
            append("\u001B[31m^\u001B[0m")  // Red caret

            // Error label on same line as caret
            if (location.length > 1) {
                append("\u001B[31m${"^".repeat(location.length - 1)}\u001B[0m")
            }
            append(" ${location.label ?: ""}")
        }
    }
}

/**
 * Compilation error with rich information
 */
data class CompilationError(
    val code: String? = null,
    val message: String,
    val location: Location,
    val severity: Severity,
    val explanation: String? = null,
    val suggestion: String? = null,
    val notes: List<String> = emptyList()
)

data class Location(
    val file: String,
    val line: Int,
    val column: Int,
    val length: Int = 1,
    val label: String? = null
)
```

### 9.4 "Did You Mean?" Suggestions

**Use Levenshtein distance** to suggest corrections:

```kotlin
/**
 * Suggestion generator
 */
class SuggestionGenerator {

    /**
     * Find similar names using Levenshtein distance
     */
    fun findSimilar(target: String, candidates: List<String>): String? {
        if (candidates.isEmpty()) return null

        val scored = candidates.map { candidate ->
            candidate to levenshteinDistance(target.lowercase(), candidate.lowercase())
        }

        // Only suggest if distance is small (< 3)
        val best = scored.minByOrNull { it.second }

        return if (best != null && best.second <= 2) {
            best.first
        } else {
            null
        }
    }

    /**
     * Levenshtein distance (edit distance) algorithm
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1) { it }

        for (i in 1..s1.length) {
            costs[0] = i
            var lastValue = i - 1

            for (j in 1..s2.length) {
                if (s1[i - 1] == s2[j - 1]) {
                    costs[j] = lastValue
                } else {
                    costs[j] = 1 + minOf(costs[j], costs[j - 1], lastValue)
                }
                lastValue = costs[j]
            }
        }

        return costs[s2.length]
    }
}
```

**Example usage:**

```utlx
let customr = $input.Order.Customer.Name
#   ^^^^^^^ error: Undefined variable 'customr'
#           help: Did you mean 'customer'?
```

### 9.5 Contextual Help

**Provide context-specific help messages:**

```kotlin
/**
 * Context-aware error messages
 */
fun createUndefinedVariableError(
    varName: String,
    location: Location,
    availableVars: List<String>
): CompilationError {
    val suggestion = SuggestionGenerator().findSimilar(varName, availableVars)

    val help = if (suggestion != null) {
        "Did you mean '$suggestion'?"
    } else if (availableVars.isEmpty()) {
        "No variables are currently in scope"
    } else {
        "Available variables: ${availableVars.take(5).joinToString(", ")}"
    }

    return CompilationError(
        code = "E0425",
        message = "Undefined variable '$varName'",
        location = location,
        severity = Severity.ERROR,
        explanation = "This variable is not defined in the current scope",
        suggestion = help
    )
}
```

---

## 10. Implementation Roadmap

### Phase 1: Enhanced Syntactic Validation (Weeks 1-2)

**Goals:**
- Improve parser error messages
- Add error recovery
- Implement better location tracking

**Tasks:**
1. Week 1: Error recovery in parser
   - Synchronization points
   - Panic mode recovery
   - Better error reporting

2. Week 2: Rich error messages
   - Implement ErrorFormatter
   - Add color-coded output
   - Source location highlighting

**Deliverables:**
- Enhanced parser with recovery
- Rust-quality error messages

### Phase 2: Semantic Validation (Weeks 3-5)

**Goals:**
- Integrate type checker
- Implement variable resolution
- Add function signature validation

**Tasks:**
1. Week 3: Type checking integration
   - Enhance existing type_system.kt
   - Add type inference
   - Type error reporting

2. Week 4: Variable resolution
   - Scope analysis
   - Undefined variable detection
   - "Did you mean?" suggestions

3. Week 5: Function validation
   - PascalCase enforcement
   - Arity checking
   - Parameter validation

**Deliverables:**
- Complete semantic validator
- Type-checked AST

### Phase 3: Dependency Graph (Weeks 6-7)

**Goals:**
- Build dependency graph from AST
- Circular dependency detection
- Variable ordering

**Tasks:**
1. Week 6: Graph construction
   - DependencyGraphBuilder
   - Topological sort
   - Cycle detection

2. Week 7: Applications
   - Variable reordering
   - Dead code elimination
   - Optimization opportunities

**Deliverables:**
- DependencyGraph module
- Reachability analyzer

### Phase 4: Logical Validation (Weeks 8-9)

**Goals:**
- Implement linting framework
- Dead code analyzer
- Style checker

**Tasks:**
1. Week 8: Core linters
   - Dead code detector
   - Unused variable finder
   - Complexity analyzer

2. Week 9: Style and best practices
   - Style checker
   - Best practices rules
   - Fixable issue detection

**Deliverables:**
- Linting framework
- 10+ lint rules

### Phase 5: CLI Commands (Weeks 10-11)

**Goals:**
- Implement `validate` command
- Implement `lint` command
- Integration and testing

**Tasks:**
1. Week 10: Command implementation
   - ValidateCommand
   - LintCommand
   - Flag processing

2. Week 11: Integration
   - End-to-end testing
   - Documentation
   - Example workflows

**Deliverables:**
- `utlx validate`
- `utlx lint`
- User documentation

### Phase 6: Schema Validation (Weeks 12-13)

**Goals:**
- Design-time path validation
- Runtime data validation integration
- USDL validation

**Tasks:**
1. Week 12: Path validation
   - SchemaPathValidator
   - XSD/JSON Schema integration
   - Path existence checking

2. Week 13: Runtime integration
   - Link to three-phase-runtime
   - Validation policy support
   - Error transformation

**Deliverables:**
- Schema validation module
- Runtime integration

### Phase 7: Production Ready (Weeks 14-15)

**Goals:**
- Performance optimization
- Comprehensive testing
- Documentation

**Tasks:**
1. Week 14: Performance
   - Benchmark validation pipeline
   - Optimize hotspots
   - Caching strategies

2. Week 15: Polish
   - Documentation
   - Examples
   - Release preparation

**Deliverables:**
- Production-ready validation system
- Complete documentation

---

## 11. Integration Points

### 11.1 IDE Integration (LSP)

**Language Server Protocol** enables real-time validation in IDEs:

```kotlin
/**
 * UTL-X Language Server
 */
class UTLXLanguageServer : LanguageServer {

    /**
     * Triggered when document changes
     */
    override fun didChange(params: DidChangeTextDocumentParams) {
        val source = params.contentChanges[0].text

        // Run validation asynchronously
        GlobalScope.launch {
            val diagnostics = validate(source)
            publishDiagnostics(params.textDocument.uri, diagnostics)
        }
    }

    /**
     * Validate and return diagnostics
     */
    private fun validate(source: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()

        // Syntax validation
        val parseResult = parser.parse(source)
        if (parseResult is ParseResult.Failure) {
            diagnostics.addAll(parseResult.errors.map { toDiagnostic(it, DiagnosticSeverity.Error) })
            return diagnostics  // Can't continue without valid AST
        }

        val ast = (parseResult as ParseResult.Success).program

        // Semantic validation
        val semanticErrors = semanticValidator.validate(ast)
        diagnostics.addAll(semanticErrors.map { toDiagnostic(it, DiagnosticSeverity.Error) })

        // Linting
        val lintWarnings = linter.analyze(ast)
        diagnostics.addAll(lintWarnings.map { toDiagnostic(it, DiagnosticSeverity.Warning) })

        return diagnostics
    }

    /**
     * Convert error to LSP Diagnostic
     */
    private fun toDiagnostic(error: CompilationError, severity: DiagnosticSeverity): Diagnostic {
        return Diagnostic(
            range = Range(
                Position(error.location.line - 1, error.location.column - 1),
                Position(error.location.line - 1, error.location.column + error.location.length)
            ),
            severity = severity,
            source = "utlx",
            message = error.message,
            code = error.code
        )
    }
}
```

**IDE Features:**
- 🔴 Real-time error highlighting
- 💡 Quick fixes and suggestions
- 📖 Hover for type information
- 🔍 Go to definition
- 🔄 Refactoring support

### 11.2 CI/CD Integration

**GitHub Actions example:**

```yaml
name: UTL-X Validation

on: [push, pull_request]

jobs:
  validate:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Install UTL-X
        run: |
          curl -fsSL https://utl-x.dev/install.sh | bash
          echo "$HOME/.utlx/bin" >> $GITHUB_PATH

      - name: Validate transformations
        run: |
          find . -name "*.utlx" -exec utlx validate {} \;

      - name: Lint (warnings only)
        run: |
          find . -name "*.utlx" -exec utlx lint {} \;
        continue-on-error: true  # Don't fail on warnings
```

**GitLab CI example:**

```yaml
validate:
  stage: test
  script:
    - utlx validate **/*.utlx
  allow_failure: false

lint:
  stage: test
  script:
    - utlx lint **/*.utlx
  allow_failure: true  # Warnings only
```

### 11.3 Pre-commit Hooks

**Git hook for validation:**

```bash
#!/bin/bash
# .git/hooks/pre-commit

echo "Running UTL-X validation..."

# Find staged .utlx files
STAGED_FILES=$(git diff --cached --name-only --diff-filter=ACM | grep ".utlx$")

if [ -z "$STAGED_FILES" ]; then
  echo "No UTL-X files to validate"
  exit 0
fi

# Validate each file
FAILED=0
for FILE in $STAGED_FILES; do
  utlx validate "$FILE"
  if [ $? -ne 0 ]; then
    FAILED=1
  fi
done

# Auto-fix lint issues
if [ $FAILED -eq 0 ]; then
  for FILE in $STAGED_FILES; do
    utlx lint "$FILE" --fix
    git add "$FILE"  # Re-stage fixed files
  done
fi

exit $FAILED
```

### 11.4 Build System Integration

**Gradle plugin:**

```kotlin
/**
 * UTL-X Gradle plugin
 */
class UTLXPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("validateUTLX") {
            group = "verification"
            description = "Validate UTL-X transformations"

            doLast {
                val utlxFiles = project.fileTree("src") {
                    include("**/*.utlx")
                }

                var failed = false
                utlxFiles.forEach { file ->
                    val result = project.exec {
                        commandLine("utlx", "validate", file.absolutePath)
                        isIgnoreExitValue = true
                    }

                    if (result.exitValue != 0) {
                        failed = true
                    }
                }

                if (failed) {
                    throw GradleException("UTL-X validation failed")
                }
            }
        }

        // Add to check task
        project.tasks.named("check") {
            dependsOn("validateUTLX")
        }
    }
}
```

### 11.5 Continuous Validation

**Watch mode for development:**

```bash
# Watch for changes and validate automatically
utlx validate --watch transform.utlx

# Output:
# Watching transform.utlx for changes...
#
# [12:34:56] File changed, validating...
# ✓ Validation passed
#
# [12:35:12] File changed, validating...
# ✗ Syntax error on line 15...
```

**Implementation:**

```kotlin
/**
 * Watch mode for validation
 */
class WatchValidator(private val filePath: String) {

    fun start() {
        val watchService = FileSystems.getDefault().newWatchService()
        val path = Paths.get(filePath).parent

        path.register(
            watchService,
            StandardWatchEventKinds.ENTRY_MODIFY
        )

        println("Watching $filePath for changes...")

        while (true) {
            val key = watchService.take()

            for (event in key.pollEvents()) {
                val changed = event.context() as Path

                if (changed.toString() == Paths.get(filePath).fileName.toString()) {
                    println("\n[${LocalTime.now()}] File changed, validating...")

                    val result = validate(filePath)
                    printResult(result)
                }
            }

            key.reset()
        }
    }
}
```

---

## Conclusion

This document has presented a comprehensive analysis of validation and static analysis for UTL-X, covering:

1. **Four validation levels**: Syntactic → Semantic → Schema → Logical
2. **Parse errors ARE syntactic errors** - clear terminology
3. **Dependency graph construction** - essential for optimization and analysis
4. **Separate `validate` and `lint` commands** - following industry standards
5. **Rust-quality error messages** - with suggestions and context
6. **Integration points** - IDE, CI/CD, pre-commit hooks

**Key Takeaways:**

✅ **Validation hierarchy** provides clear structure for implementation
✅ **Dependency graphs** enable advanced analysis and optimization
✅ **Separate tools** (validate/lint) align with industry best practices
✅ **Rich error messages** improve developer experience
✅ **Multiple integration points** support various workflows

**Next Steps:**

1. **Review this document** with architecture team
2. **Choose CLI design** (recommend separate commands)
3. **Begin Phase 1** (Enhanced syntactic validation)
4. **Iterate based on feedback** from early adopters

---

**Document Status:** Ready for Review
**Author:** UTL-X Architecture Team
**Last Updated:** 2025-10-29
**Version:** 1.0
