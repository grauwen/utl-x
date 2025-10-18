// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/types/AdvancedTypeInference.kt
package org.apache.utlx.analysis.types

import org.apache.utlx.core.ast.*

/**
 * Advanced type inference engine for UTL-X transformations
 * 
 * Analyzes UTL-X AST nodes to infer output types based on:
 * - Input type constraints
 * - Transformation logic
 * - Function signatures
 * - Data flow analysis
 */
class AdvancedTypeInference {
    
    private var inputType: TypeDefinition = TypeDefinition.Any
    private val typeContext = mutableMapOf<String, TypeDefinition>()
    
    /**
     * Infer output type from transformation and input type
     */
    fun inferOutputType(transformation: Program, inputType: TypeDefinition): TypeDefinition {
        this.inputType = inputType
        this.typeContext.clear()
        
        // Add input to context
        typeContext["input"] = inputType
        
        return try {
            analyzeExpression(transformation.body)
        } catch (e: Exception) {
            // If inference fails, return Any type with error comment
            TypeDefinition.Any
        }
    }
    
    /**
     * Analyze an expression and infer its type
     */
    private fun analyzeExpression(expression: Expression): TypeDefinition {
        return when (expression) {
            is Expression.StringLiteral -> TypeDefinition.Scalar(ScalarKind.STRING)
            is Expression.NumberLiteral -> TypeDefinition.Scalar(ScalarKind.NUMBER)
            is Expression.BooleanLiteral -> TypeDefinition.Scalar(ScalarKind.BOOLEAN)
            is Expression.NullLiteral -> TypeDefinition.Scalar(ScalarKind.NULL)
            is Expression.ObjectLiteral -> analyzeObject(expression)
            is Expression.ArrayLiteral -> analyzeArray(expression)
            is Expression.FunctionCall -> analyzeFunctionCall(expression)
            is Expression.BinaryOp -> analyzeBinary(expression)
            is Expression.Identifier -> analyzeVariable(expression)
            is Expression.MemberAccess -> analyzeMemberAccess(expression)
            is Expression.IndexAccess -> analyzeIndexAccess(expression)
            else -> TypeDefinition.Any
        }
    }
    
    /**
     * Analyze member access expressions (e.g., obj.field, input.data)
     */
    private fun analyzeMemberAccess(access: Expression.MemberAccess): TypeDefinition {
        val targetType = analyzeExpression(access.target)
        
        return when (targetType) {
            is TypeDefinition.Object -> {
                val propertyName = if (access.isAttribute) "@${access.property}" else access.property
                targetType.properties[propertyName]?.type ?: TypeDefinition.Any
            }
            else -> TypeDefinition.Any
        }
    }
    
    /**
     * Analyze index access expressions (e.g., array[0], obj["key"])
     */
    private fun analyzeIndexAccess(access: Expression.IndexAccess): TypeDefinition {
        val targetType = analyzeExpression(access.target)
        
        return when (targetType) {
            is TypeDefinition.Array -> targetType.elementType
            is TypeDefinition.Object -> {
                // For object index access, we can't know the exact property type
                // without knowing the index value at compile time
                TypeDefinition.Any
            }
            else -> TypeDefinition.Any
        }
    }
    
    /**
     * Analyze object expressions
     */
    private fun analyzeObject(obj: Expression.ObjectLiteral): TypeDefinition {
        val properties = mutableMapOf<String, PropertyType>()
        val required = mutableSetOf<String>()
        
        obj.properties.forEach { property ->
            val propType = analyzeExpression(property.value)
            properties[property.key] = PropertyType(propType, nullable = false)
            required.add(property.key)
        }
        
        return TypeDefinition.Object(properties, required, additionalProperties = false)
    }
    
    /**
     * Analyze array expressions
     */
    private fun analyzeArray(array: Expression.ArrayLiteral): TypeDefinition {
        if (array.elements.isEmpty()) {
            return TypeDefinition.Array(TypeDefinition.Any)
        }
        
        // Infer element type from first element
        val firstElementType = analyzeExpression(array.elements.first())
        
        // Check if all elements have the same type
        val allSameType = array.elements.all { element ->
            val elementType = analyzeExpression(element)
            elementType.isCompatibleWith(firstElementType)
        }
        
        val elementType = if (allSameType) {
            firstElementType
        } else {
            // Create union of all element types
            val elementTypes = array.elements.map { analyzeExpression(it) }.distinct()
            if (elementTypes.size == 1) elementTypes.first() else TypeDefinition.Union(elementTypes)
        }
        
        return TypeDefinition.Array(elementType, minItems = array.elements.size, maxItems = array.elements.size)
    }
    
    /**
     * Analyze function call expressions
     */
    private fun analyzeFunctionCall(call: Expression.FunctionCall): TypeDefinition {
        // Get function name from the function expression
        val functionName = when (val func = call.function) {
            is Expression.Identifier -> func.name
            else -> null
        }
        
        return when (functionName) {
            // Array functions
            "map" -> {
                if (call.arguments.isNotEmpty()) {
                    val sourceType = analyzeExpression(call.arguments[0])
                    when (sourceType) {
                        is TypeDefinition.Array -> {
                            // Map returns array with potentially different element type
                            // For now, return array of Any
                            TypeDefinition.Array(TypeDefinition.Any)
                        }
                        else -> TypeDefinition.Any
                    }
                } else TypeDefinition.Any
            }
            "filter" -> {
                if (call.arguments.isNotEmpty()) {
                    analyzeExpression(call.arguments[0]) // Filter preserves type
                } else TypeDefinition.Any
            }
            "sum" -> TypeDefinition.Scalar(ScalarKind.NUMBER)
            "count" -> TypeDefinition.Scalar(ScalarKind.INTEGER)
            "length" -> TypeDefinition.Scalar(ScalarKind.INTEGER)
            
            // String functions
            "upper", "lower", "trim" -> TypeDefinition.Scalar(ScalarKind.STRING)
            "split" -> TypeDefinition.Array(TypeDefinition.Scalar(ScalarKind.STRING))
            
            // Date functions
            "now" -> TypeDefinition.Scalar(ScalarKind.DATETIME)
            "parseDate" -> TypeDefinition.Scalar(ScalarKind.DATE)
            
            // Type functions
            "typeOf" -> TypeDefinition.Scalar(ScalarKind.STRING)
            "isEmpty" -> TypeDefinition.Scalar(ScalarKind.BOOLEAN)
            "isArray" -> TypeDefinition.Scalar(ScalarKind.BOOLEAN)
            
            // Default
            else -> TypeDefinition.Any
        }
    }
    
    /**
     * Analyze binary expressions
     */
    private fun analyzeBinary(binary: Expression.BinaryOp): TypeDefinition {
        val leftType = analyzeExpression(binary.left)
        val rightType = analyzeExpression(binary.right)
        
        return when (binary.operator) {
            BinaryOperator.PLUS, BinaryOperator.MINUS, BinaryOperator.MULTIPLY, BinaryOperator.DIVIDE, BinaryOperator.MODULO -> {
                // Arithmetic operations
                when {
                    leftType is TypeDefinition.Scalar && leftType.kind.isNumeric() &&
                    rightType is TypeDefinition.Scalar && rightType.kind.isNumeric() -> {
                        if (leftType.kind == ScalarKind.INTEGER && rightType.kind == ScalarKind.INTEGER) {
                            TypeDefinition.Scalar(ScalarKind.INTEGER)
                        } else {
                            TypeDefinition.Scalar(ScalarKind.NUMBER)
                        }
                    }
                    else -> TypeDefinition.Any
                }
            }
            BinaryOperator.EQUAL, BinaryOperator.NOT_EQUAL, 
            BinaryOperator.LESS_THAN, BinaryOperator.LESS_EQUAL,
            BinaryOperator.GREATER_THAN, BinaryOperator.GREATER_EQUAL -> TypeDefinition.Scalar(ScalarKind.BOOLEAN)
            BinaryOperator.AND, BinaryOperator.OR -> TypeDefinition.Scalar(ScalarKind.BOOLEAN)
        }
    }
    
    /**
     * Analyze variable expressions (identifiers)
     */
    private fun analyzeVariable(variable: Expression.Identifier): TypeDefinition {
        return typeContext[variable.name] ?: TypeDefinition.Any
    }
}