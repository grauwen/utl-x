// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/types/AdvancedTypeInference.kt
package org.apache.utlx.analysis.types

import org.apache.utlx.core.ast.*

/**
 * Advanced type inference engine for UTL-X transformations
 * 
 * Analyzes UTL-X AST nodes to infer output types based on:
 * - Input type constraints
 * - Transformation logic
 * - Function signatures from stdlib (188+ functions)
 * - Data flow analysis
 */
class AdvancedTypeInference : TypeInferenceContext {
    
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
    override fun analyzeExpression(expression: Expression): TypeDefinition {
        return when (expression) {
            is Expression.StringLiteral -> TypeDefinition.Scalar(ScalarKind.STRING)
            is Expression.NumberLiteral -> TypeDefinition.Scalar(ScalarKind.NUMBER)
            is Expression.BooleanLiteral -> TypeDefinition.Scalar(ScalarKind.BOOLEAN)
            is Expression.NullLiteral -> TypeDefinition.Scalar(ScalarKind.NULL)
            is Expression.ObjectLiteral -> analyzeObject(expression)
            is Expression.ArrayLiteral -> analyzeArray(expression)
            is Expression.FunctionCall -> analyzeFunctionCall(expression)
            is Expression.BinaryOp -> analyzeBinary(expression)
            is Expression.Ternary -> {
                // Analyze both branches and return union type
                val thenType = analyzeExpression(expression.thenExpr)
                val elseType = analyzeExpression(expression.elseExpr)
                if (thenType == elseType) thenType else TypeDefinition.Union(listOf(thenType, elseType))
            }
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
            if (property.isSpread) {
                // Handle spread property
                val spreadType = analyzeExpression(property.value)
                when (spreadType) {
                    is TypeDefinition.Object -> {
                        // Merge properties from spread object
                        properties.putAll(spreadType.properties)
                        required.addAll(spreadType.required)
                    }
                    else -> {
                        // Can't spread non-object - ignore
                    }
                }
            } else {
                val key = property.key ?: error("Non-spread property must have a key")
                val propType = analyzeExpression(property.value)
                properties[key] = PropertyType(propType, nullable = false)
                required.add(key)
            }
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

        // Collect element types, handling spread elements
        val elementTypes = array.elements.map { element ->
            when (element) {
                is Expression.SpreadElement -> {
                    // For spread, get the element type of the array being spread
                    val spreadType = analyzeExpression(element.expression)
                    when (spreadType) {
                        is TypeDefinition.Array -> spreadType.elementType
                        else -> TypeDefinition.Any // Can't spread non-array
                    }
                }
                else -> analyzeExpression(element)
            }
        }

        // Infer element type from first element
        val firstElementType = elementTypes.first()

        // Check if all elements have the same type
        val allSameType = elementTypes.all { it.isCompatibleWith(firstElementType) }

        val elementType = if (allSameType) {
            firstElementType
        } else {
            // Create union of all element types
            val distinctTypes = elementTypes.distinct()
            if (distinctTypes.size == 1) distinctTypes.first() else TypeDefinition.Union(distinctTypes)
        }
        
        return TypeDefinition.Array(elementType, minItems = array.elements.size, maxItems = array.elements.size)
    }
    
    /**
     * Analyze function call expressions using the function type registry
     */
    private fun analyzeFunctionCall(call: Expression.FunctionCall): TypeDefinition {
        // Get function name from the function expression
        val functionName = when (val func = call.function) {
            is Expression.Identifier -> func.name
            else -> null
        }
        
        if (functionName == null) {
            return TypeDefinition.Any
        }
        
        // Look up function signature in registry
        val signature = FunctionTypeRegistry.getFunctionSignature(functionName)
        if (signature != null) {
            return evaluateReturnType(signature.returnType, call.arguments)
        }
        
        // Fallback to legacy function analysis for unregistered functions
        return when (functionName) {
            // Legacy fallbacks (most should be covered by registry now)
            else -> TypeDefinition.Any
        }
    }
    
    /**
     * Evaluate return type based on return type logic and actual arguments
     */
    private fun evaluateReturnType(logic: ReturnTypeLogic, arguments: List<Expression>): TypeDefinition {
        return when (logic) {
            is ReturnTypeLogic.Fixed -> logic.type
            
            is ReturnTypeLogic.PreserveFirstArgument -> {
                if (arguments.isNotEmpty()) {
                    analyzeExpression(arguments[0])
                } else TypeDefinition.Any
            }
            
            is ReturnTypeLogic.ArrayElementType -> {
                if (arguments.isNotEmpty()) {
                    val arrayType = analyzeExpression(arguments[0])
                    if (arrayType is TypeDefinition.Array) {
                        arrayType.elementType
                    } else TypeDefinition.Any
                } else TypeDefinition.Any
            }
            
            is ReturnTypeLogic.ArrayFlatten -> {
                if (arguments.isNotEmpty()) {
                    val arrayType = analyzeExpression(arguments[0])
                    if (arrayType is TypeDefinition.Array) {
                        // If array of arrays, flatten one level
                        if (arrayType.elementType is TypeDefinition.Array) {
                            arrayType.elementType
                        } else {
                            arrayType // Already flat
                        }
                    } else TypeDefinition.Any
                } else TypeDefinition.Any
            }
            
            is ReturnTypeLogic.ThirdArgumentOrAny -> {
                if (arguments.size >= 3) {
                    analyzeExpression(arguments[2])
                } else TypeDefinition.Any
            }
            
            is ReturnTypeLogic.ArrayTransform -> {
                if (arguments.isNotEmpty()) {
                    val sourceType = analyzeExpression(arguments[0])
                    val argumentTypes = arguments.map { analyzeExpression(it) }
                    logic.transformer(sourceType, argumentTypes)
                } else TypeDefinition.Any
            }
            
            is ReturnTypeLogic.Custom -> {
                logic.analyzer(arguments, this)
            }
        }
    }
    
    /**
     * Analyze binary expressions
     */
    private fun analyzeBinary(binary: Expression.BinaryOp): TypeDefinition {
        val leftType = analyzeExpression(binary.left)
        val rightType = analyzeExpression(binary.right)
        
        return when (binary.operator) {
            BinaryOperator.PLUS, BinaryOperator.MINUS, BinaryOperator.MULTIPLY, BinaryOperator.DIVIDE, BinaryOperator.MODULO, BinaryOperator.EXPONENT -> {
                // Arithmetic operations
                when {
                    leftType is TypeDefinition.Scalar && leftType.kind.isNumeric() &&
                    rightType is TypeDefinition.Scalar && rightType.kind.isNumeric() -> {
                        if (leftType.kind == ScalarKind.INTEGER && rightType.kind == ScalarKind.INTEGER &&
                            binary.operator != BinaryOperator.EXPONENT) {
                            // Exponentiation can produce non-integers even with integer inputs (e.g., 2^-1 = 0.5)
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
            BinaryOperator.NULLISH_COALESCE -> {
                // Nullish coalescing returns right if left is null, otherwise left
                // Result type is union of non-nullable left and right
                TypeDefinition.Union(listOf(leftType, rightType))
            }
        }
    }
    
    /**
     * Analyze variable expressions (identifiers)
     */
    private fun analyzeVariable(variable: Expression.Identifier): TypeDefinition {
        return typeContext[variable.name] ?: TypeDefinition.Any
    }
}