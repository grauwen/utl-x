# Compiler Pipeline

Detailed documentation of the UTL-X compilation process.

## Overview

The compiler transforms UTL-X source code into executable form through multiple stages:

```
Source → Lexer → Parser → Type Checker → Optimizer → Code Generator → Executable
```

## Stage 1: Lexical Analysis (Lexer)

### Purpose

Convert source code into tokens.

### Input

```utlx
let x = 42
```

### Output (Tokens)

```
[
  Token(LET, "let"),
  Token(IDENTIFIER, "x"),
  Token(EQUALS, "="),
  Token(NUMBER, "42")
]
```

### Implementation

```kotlin
class Lexer(private val source: String) {
    private var position = 0
    private var current: Char? = source.getOrNull(0)
    
    fun nextToken(): Token {
        skipWhitespace()
        
        return when {
            current == null -> Token(EOF)
            current!!.isLetter() -> scanIdentifierOrKeyword()
            current!!.isDigit() -> scanNumber()
            current == '"' || current == '\'' -> scanString()
            current == '=' && peek() == '=' -> scanDoubleChar(EQUALS_EQUALS)
            current == '=' -> Token(EQUALS)
            // ... more cases
        }
    }
}
```

### Token Types

- **Keywords**: `let`, `function`, `if`, `else`, etc.
- **Identifiers**: Variable/function names
- **Literals**: Strings, numbers, booleans
- **Operators**: `+`, `-`, `*`, `==`, etc.
- **Punctuation**: `(`, `)`, `{`, `}`, etc.

## Stage 2: Syntax Analysis (Parser)

### Purpose

Build Abstract Syntax Tree (AST) from tokens.

### Input (Tokens)

```
[Token(LET), Token(IDENTIFIER, "x"), Token(EQUALS), Token(NUMBER, "42")]
```

### Output (AST)

```
LetBinding(
  name = "x",
  value = NumberLiteral(42)
)
```

### Parser Implementation

```kotlin
class Parser(private val tokens: List<Token>) {
    private var position = 0
    
    fun parseProgram(): Program {
        val header = parseHeader()
        val config = parseConfiguration()
        expect(TRIPLE_DASH)
        val expression = parseExpression()
        return Program(header, config, expression)
    }
    
    fun parseExpression(): Expression {
        return parsePipeExpression()
    }
    
    fun parsePipeExpression(): Expression {
        var left = parseConditionalExpression()
        while (match(PIPE)) {
            val right = parsePipeExpression()
            left = PipeExpression(left, right)
        }
        return left
    }
    
    // ... more parsing methods
}
```

### AST Node Types

```kotlin
sealed class ASTNode

data class Program(
    val header: Header,
    val config: Configuration,
    val expression: Expression
) : ASTNode()

sealed class Expression : ASTNode()

data class LetBinding(
    val name: String,
    val value: Expression
) : Expression()

data class FunctionCall(
    val function: Expression,
    val arguments: List<Expression>
) : Expression()

data class BinaryOp(
    val left: Expression,
    val operator: String,
    val right: Expression
) : Expression()

// ... more node types
```

## Stage 3: Type Checking

### Purpose

Infer types and validate type correctness.

### Type Inference

```kotlin
class TypeChecker {
    private val typeEnv = mutableMapOf<String, Type>()
    
    fun inferType(expr: Expression): Type {
        return when (expr) {
            is NumberLiteral -> Type.Number
            is StringLiteral -> Type.String
            is BooleanLiteral -> Type.Boolean
            is LetBinding -> {
                val valueType = inferType(expr.value)
                typeEnv[expr.name] = valueType
                valueType
            }
            is FunctionCall -> {
                val funcType = inferType(expr.function)
                when (funcType) {
                    is Type.Function -> funcType.returnType
                    else -> error("Not a function")
                }
            }
            // ... more cases
        }
    }
    
    fun checkTypes(program: Program) {
        inferType(program.expression)
    }
}
```

### Type System

```kotlin
sealed class Type {
    object String : Type()
    object Number : Type()
    object Boolean : Type()
    object Null : Type()
    object Date : Type()
    object Any : Type()
    
    data class Array(val elementType: Type) : Type()
    object Object : Type()
    data class Function(
        val paramTypes: List<Type>,
        val returnType: Type
    ) : Type()
    data class Union(val types: List<Type>) : Type()
}
```

### Type Errors

```kotlin
sealed class TypeError : Exception() {
    data class TypeMismatch(
        val expected: Type,
        val actual: Type,
        val location: SourceLocation
    ) : TypeError()
    
    data class UndefinedVariable(
        val name: String,
        val location: SourceLocation
    ) : TypeError()
    
    data class InvalidOperation(
        val operation: String,
        val type: Type,
        val location: SourceLocation
    ) : TypeError()
}
```

## Stage 4: Optimization

### Purpose

Improve performance through code transformations.

### Optimizations

#### 1. Constant Folding

```kotlin
// Before
2 + 3 * 4

// After
14
```

#### 2. Dead Code Elimination

```kotlin
// Before
let x = 42  // x never used
{result: input.value}

// After
{result: input.value}
```

#### 3. Template Inlining

```kotlin
// Before
template match="Item" { {sku: @sku} }
apply(Items/Item)

// After (if small enough)
Items.Item |> map(item => {sku: item.@sku})
```

#### 4. Common Subexpression Elimination

```kotlin
// Before
{
  a: input.items.*.price |> sum(),
  b: input.items.*.price |> sum()
}

// After
let temp = input.items.*.price |> sum()
{
  a: temp,
  b: temp
}
```

### Optimizer Implementation

```kotlin
class Optimizer {
    fun optimize(expr: Expression): Expression {
        return when (expr) {
            is BinaryOp -> optimizeBinaryOp(expr)
            is LetBinding -> optimizeLetBinding(expr)
            else -> expr
        }
    }
    
    private fun optimizeBinaryOp(expr: BinaryOp): Expression {
        val left = optimize(expr.left)
        val right = optimize(expr.right)
        
        // Constant folding
        if (left is NumberLiteral && right is NumberLiteral) {
            return when (expr.operator) {
                "+" -> NumberLiteral(left.value + right.value)
                "*" -> NumberLiteral(left.value * right.value)
                // ... more operators
                else -> BinaryOp(left, expr.operator, right)
            }
        }
        
        return BinaryOp(left, expr.operator, right)
    }
}
```

## Stage 5: Code Generation

### Purpose

Generate runtime-specific executable code.

### JVM Code Generation

```kotlin
class JVMCodeGenerator {
    fun generate(program: Program): ByteCode {
        val bytecode = ByteCodeBuilder()
        
        // Generate class
        bytecode.startClass("GeneratedTransform")
        
        // Generate transform method
        bytecode.startMethod("transform", "(LUDM;)LUDM;")
        generateExpression(program.expression, bytecode)
        bytecode.returnValue()
        bytecode.endMethod()
        
        bytecode.endClass()
        return bytecode.build()
    }
    
    private fun generateExpression(expr: Expression, bytecode: ByteCodeBuilder) {
        when (expr) {
            is NumberLiteral -> {
                bytecode.loadConst(expr.value)
            }
            is BinaryOp -> {
                generateExpression(expr.left, bytecode)
                generateExpression(expr.right, bytecode)
                bytecode.invokeOperator(expr.operator)
            }
            // ... more cases
        }
    }
}
```

### JavaScript Code Generation

```kotlin
class JavaScriptCodeGenerator {
    fun generate(program: Program): String {
        return buildString {
            appendLine("(function(input) {")
            appendLine("  'use strict';")
            appendExpression(program.expression, this, indent = 2)
            appendLine("})")
        }
    }
    
    private fun appendExpression(expr: Expression, builder: StringBuilder, indent: Int) {
        when (expr) {
            is NumberLiteral -> builder.append(expr.value)
            is StringLiteral -> builder.append("\"${expr.value}\"")
            is BinaryOp -> {
                builder.append("(")
                appendExpression(expr.left, builder, indent)
                builder.append(" ${expr.operator} ")
                appendExpression(expr.right, builder, indent)
                builder.append(")")
            }
            // ... more cases
        }
    }
}
```

## Compilation Modes

### 1. JIT (Just-In-Time)

Compile on first execution, cache compiled code.

```kotlin
class JITCompiler {
    private val cache = mutableMapOf<String, CompiledTransform>()
    
    fun compile(source: String): CompiledTransform {
        return cache.getOrPut(source) {
            val tokens = Lexer(source).tokenize()
            val ast = Parser(tokens).parse()
            TypeChecker().check(ast)
            val optimized = Optimizer().optimize(ast)
            CodeGenerator().generate(optimized)
        }
    }
}
```

### 2. AOT (Ahead-Of-Time)

Compile before deployment.

```bash
utlxc compile transform.utlx -o transform.class
```

## Error Reporting

### Compilation Errors

```kotlin
data class CompilationError(
    val message: String,
    val location: SourceLocation,
    val severity: Severity
) {
    enum class Severity { ERROR, WARNING, INFO }
}

data class SourceLocation(
    val line: Int,
    val column: Int,
    val file: String
)
```

### Error Formatting

```
Error: Type mismatch
  --> transform.utlx:5:10
   |
 5 | let x = "hello" + 42
   |         ^^^^^^^^^^^^ Cannot add String and Number
   |
Help: Convert one operand to match the other type
```

## Performance

### Compilation Time

- **Small scripts (<1KB)**: <50ms
- **Medium scripts (1-10KB)**: 50-200ms
- **Large scripts (>10KB)**: 200-500ms

### Optimizations Impact

- **Constant folding**: 10-30% speedup
- **Dead code elimination**: 5-15% speedup
- **CSE**: 5-20% speedup
- **Combined**: 20-50% overall speedup

## Testing

### Unit Tests

```kotlin
class ParserTest {
    @Test
    fun `parse let binding`() {
        val source = "let x = 42"
        val tokens = Lexer(source).tokenize()
        val ast = Parser(tokens).parse()
        
        assertTrue(ast is LetBinding)
        assertEquals("x", (ast as LetBinding).name)
    }
}
```

### Integration Tests

```kotlin
class CompilerTest {
    @Test
    fun `compile and execute simple transform`() {
        val source = """
            %utlx 1.0
            input json
            output json
            ---
            {result: input.value * 2}
        """
        
        val compiled = Compiler().compile(source)
        val result = compiled.transform("{\"value\": 21}")
        assertEquals("{\"result\": 42}", result)
    }
}
```

---

