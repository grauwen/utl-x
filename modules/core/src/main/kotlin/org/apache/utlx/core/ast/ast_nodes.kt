package org.apache.utlx.core.ast

import org.apache.utlx.core.lexer.Token

/**
 * Base class for all AST nodes
 */
sealed class Node {
    abstract val location: Location
}

/**
 * Source location for error reporting
 */
data class Location(val line: Int, val column: Int) {
    companion object {
        fun from(token: Token) = Location(token.line, token.column)
    }
}

/**
 * Root program node
 */
data class Program(
    val header: Header,
    val body: Expression,
    override val location: Location
) : Node()

/**
 * Program header (metadata section before ---)
 */
data class Header(
    val version: String,
    val inputs: List<Pair<String, FormatSpec>>,   // Named inputs: [(name, formatSpec), ...]
    val outputs: List<Pair<String, FormatSpec>>,  // Named outputs: [(name, formatSpec), ...]
    override val location: Location
) : Node() {
    /**
     * Backward compatibility: Get primary input format
     */
    val inputFormat: FormatSpec
        get() = inputs.firstOrNull()?.second ?: FormatSpec(FormatType.AUTO, emptyMap(), location)

    /**
     * Backward compatibility: Get primary output format
     */
    val outputFormat: FormatSpec
        get() = outputs.firstOrNull()?.second ?: FormatSpec(FormatType.JSON, emptyMap(), location)

    /**
     * Check if header has multiple inputs
     */
    val hasMultipleInputs: Boolean
        get() = inputs.size > 1

    /**
     * Check if header has multiple outputs
     */
    val hasMultipleOutputs: Boolean
        get() = outputs.size > 1
}

/**
 * Format specification
 */
data class FormatSpec(
    val type: FormatType,
    val options: Map<String, Any> = emptyMap(),
    override val location: Location
) : Node()

enum class FormatType {
    AUTO, XML, JSON, CSV, YAML, CUSTOM
}

/**
 * Base class for expressions
 */
sealed class Expression : Node() {
    /**
     * Object literal: { key: value, ... }
     */
    data class ObjectLiteral(
        val properties: List<Property>,
        override val location: Location
    ) : Expression()
    
    /**
     * Array literal: [element1, element2, ...]
     */
    data class ArrayLiteral(
        val elements: List<Expression>,
        override val location: Location
    ) : Expression()
    
    /**
     * String literal: "hello"
     */
    data class StringLiteral(
        val value: String,
        override val location: Location
    ) : Expression()
    
    /**
     * Number literal: 42, 3.14
     */
    data class NumberLiteral(
        val value: Double,
        override val location: Location
    ) : Expression()
    
    /**
     * Boolean literal: true, false
     */
    data class BooleanLiteral(
        val value: Boolean,
        override val location: Location
    ) : Expression()
    
    /**
     * Null literal
     */
    data class NullLiteral(
        override val location: Location
    ) : Expression()
    
    /**
     * Identifier reference: variableName, functionName
     */
    data class Identifier(
        val name: String,
        override val location: Location
    ) : Expression()
    
    /**
     * Member access: object.property
     */
    data class MemberAccess(
        val target: Expression,
        val property: String,
        val isAttribute: Boolean = false, // true for @attribute
        override val location: Location
    ) : Expression()
    
    /**
     * Index access: array[0]
     */
    data class IndexAccess(
        val target: Expression,
        val index: Expression,
        override val location: Location
    ) : Expression()
    
    /**
     * Function call: functionName(arg1, arg2)
     */
    data class FunctionCall(
        val function: Expression,
        val arguments: List<Expression>,
        override val location: Location
    ) : Expression()
    
    /**
     * Binary operation: a + b, a == b, a && b
     */
    data class BinaryOp(
        val left: Expression,
        val operator: BinaryOperator,
        val right: Expression,
        override val location: Location
    ) : Expression()
    
    /**
     * Unary operation: -x, !condition
     */
    data class UnaryOp(
        val operator: UnaryOperator,
        val operand: Expression,
        override val location: Location
    ) : Expression()
    
    /**
     * Conditional: if (condition) thenExpr else elseExpr
     */
    data class Conditional(
        val condition: Expression,
        val thenBranch: Expression,
        val elseBranch: Expression?,
        override val location: Location
    ) : Expression()
    
    /**
     * Let binding: let x = value
     */
    data class LetBinding(
        val name: String,
        val value: Expression,
        override val location: Location
    ) : Expression()
    
    /**
     * Lambda: (param1, param2) => expression
     */
    data class Lambda(
        val parameters: List<Parameter>,
        val body: Expression,
        override val location: Location
    ) : Expression()
    
    /**
     * Pipe operation: expr |> function
     */
    data class Pipe(
        val source: Expression,
        val target: Expression,
        override val location: Location
    ) : Expression()
    
    /**
     * Match expression: match value { pattern => expr, ... }
     */
    data class Match(
        val value: Expression,
        val cases: List<MatchCase>,
        override val location: Location
    ) : Expression()

    /**
     * Template application: apply(selector)
     */
    data class TemplateApplication(
        val selector: Expression,
        override val location: Location
    ) : Expression()
    
    /**
     * Block with multiple expressions (last one is returned)
     */
    data class Block(
        val expressions: List<Expression>,
        override val location: Location
    ) : Expression()
}

/**
 * Object property
 */
data class Property(
    val key: String,
    val value: Expression,
    val location: Location,
    val isAttribute: Boolean = false  // True if property represents an XML attribute (@key)
)

/**
 * Function parameter
 */
data class Parameter(
    val name: String,
    val type: Type?,
    val location: Location
)

/**
 * Match case: pattern => expression
 */
data class MatchCase(
    val pattern: Pattern,
    val expression: Expression,
    val location: Location
)

/**
 * Patterns for match expressions
 */
sealed class Pattern {
    data class Literal(val value: Any?, val location: Location) : Pattern()
    data class Wildcard(val location: Location) : Pattern()
    data class Variable(val name: String, val location: Location) : Pattern()
}

/**
 * Binary operators
 */
enum class BinaryOperator {
    // Arithmetic
    PLUS, MINUS, MULTIPLY, DIVIDE, MODULO,
    
    // Comparison
    EQUAL, NOT_EQUAL, LESS_THAN, LESS_EQUAL, GREATER_THAN, GREATER_EQUAL,
    
    // Logical
    AND, OR
}

/**
 * Unary operators
 */
enum class UnaryOperator {
    MINUS, NOT
}

/**
 * Type annotations
 */
sealed class Type {
    object String : Type()
    object Number : Type()
    object Boolean : Type()
    object Any : Type()
    data class Array(val elementType: Type) : Type()
    data class Object(val properties: Map<kotlin.String, Type>) : Type()
    data class Function(val parameters: List<Type>, val returnType: Type) : Type()
    data class Union(val types: List<Type>) : Type()
}

/**
 * Statements (top-level declarations)
 */
sealed class Statement : Node() {
    /**
     * Function definition
     */
    data class FunctionDef(
        val name: String,
        val parameters: List<Parameter>,
        val returnType: Type?,
        val body: Expression,
        override val location: Location
    ) : Statement()
    
    /**
     * Template definition
     */
    data class TemplateDef(
        val matchPattern: String,
        val body: Expression,
        override val location: Location
    ) : Statement()
}

/**
 * Visitor pattern for AST traversal
 */
interface ASTVisitor<R> {
    fun visitProgram(program: Program): R
    fun visitExpression(expression: Expression): R
    fun visitStatement(statement: Statement): R
}
