// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/types/FunctionTypeRegistry.kt
package org.apache.utlx.analysis.types

/**
 * Registry for function signatures and return types for design-time type inference
 * 
 * Maps UTL-X stdlib functions (188+ functions) to their type signatures for accurate
 * schema generation and validation.
 */
object FunctionTypeRegistry {
    
    private val functionSignatures = mutableMapOf<String, FunctionSignature>()
    
    init {
        registerAllFunctionTypes()
        // Register all complete function signatures
        CompleteFunctionRegistry.registerRemainingFunctions()
        integrateCompleteFunctions()
    }
    
    fun getFunctionSignature(name: String): FunctionSignature? = functionSignatures[name]
    
    fun hasFunctionSignature(name: String): Boolean = functionSignatures.containsKey(name)
    
    fun getAllFunctionNames(): Set<String> = functionSignatures.keys
    
    fun getAllRegisteredFunctions(): Set<String> = functionSignatures.keys
    
    fun getRegisteredFunctionCount(): Int = functionSignatures.size
    
    private fun registerAllFunctionTypes() {
        registerArrayFunctionTypes()
        registerStringFunctionTypes()
        registerObjectFunctionTypes()
        registerMathFunctionTypes()
        registerDateFunctionTypes()
        registerTypeFunctionTypes()
        registerEncodingFunctionTypes()
        registerLogicalFunctionTypes()
        registerBinaryFunctionTypes()
        registerFinancialFunctionTypes()
        registerGeospatialFunctionTypes()
        registerXmlFunctionTypes()
        registerCoreFunctionTypes()
    }
    
    private fun registerArrayFunctionTypes() {
        // Core functional operations
        register("map", FunctionSignature(
            parameters = listOf(
                ParameterSignature("array", TypePattern.Array()),
                ParameterSignature("mapper", TypePattern.Function())
            ),
            returnType = ReturnTypeLogic.ArrayTransform { _, _ ->
                // Map preserves array but element type becomes Any (would need lambda analysis)
                TypeDefinition.Array(TypeDefinition.Any)
            }
        ))
        
        register("filter", FunctionSignature(
            parameters = listOf(
                ParameterSignature("array", TypePattern.Array()),
                ParameterSignature("predicate", TypePattern.Function())
            ),
            returnType = ReturnTypeLogic.PreserveFirstArgument
        ))
        
        register("reduce", FunctionSignature(
            parameters = listOf(
                ParameterSignature("array", TypePattern.Array()),
                ParameterSignature("accumulator", TypePattern.Function()),
                ParameterSignature("initial", TypePattern.Any, optional = true)
            ),
            returnType = ReturnTypeLogic.ThirdArgumentOrAny
        ))
        
        register("find", FunctionSignature(
            parameters = listOf(
                ParameterSignature("array", TypePattern.Array()),
                ParameterSignature("predicate", TypePattern.Function())
            ),
            returnType = ReturnTypeLogic.ArrayElementType
        ))
        
        register("findIndex", FunctionSignature(
            parameters = listOf(
                ParameterSignature("array", TypePattern.Array()),
                ParameterSignature("predicate", TypePattern.Function())
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.INTEGER))
        ))
        
        // Array manipulation
        register("flatten", FunctionSignature(
            parameters = listOf(ParameterSignature("array", TypePattern.Array())),
            returnType = ReturnTypeLogic.ArrayFlatten
        ))
        
        register("reverse", FunctionSignature(
            parameters = listOf(ParameterSignature("array", TypePattern.Array())),
            returnType = ReturnTypeLogic.PreserveFirstArgument
        ))
        
        register("sort", FunctionSignature(
            parameters = listOf(ParameterSignature("array", TypePattern.Array())),
            returnType = ReturnTypeLogic.PreserveFirstArgument
        ))
        
        register("sortBy", FunctionSignature(
            parameters = listOf(
                ParameterSignature("array", TypePattern.Array()),
                ParameterSignature("keySelector", TypePattern.Function())
            ),
            returnType = ReturnTypeLogic.PreserveFirstArgument
        ))
        
        register("first", FunctionSignature(
            parameters = listOf(ParameterSignature("array", TypePattern.Array())),
            returnType = ReturnTypeLogic.ArrayElementType
        ))
        
        register("head", FunctionSignature(
            parameters = listOf(ParameterSignature("array", TypePattern.Array())),
            returnType = ReturnTypeLogic.ArrayElementType
        ))
        
        register("last", FunctionSignature(
            parameters = listOf(ParameterSignature("array", TypePattern.Array())),
            returnType = ReturnTypeLogic.ArrayElementType
        ))
        
        register("take", FunctionSignature(
            parameters = listOf(
                ParameterSignature("array", TypePattern.Array()),
                ParameterSignature("count", TypePattern.Scalar(ScalarKind.INTEGER))
            ),
            returnType = ReturnTypeLogic.PreserveFirstArgument
        ))
        
        register("drop", FunctionSignature(
            parameters = listOf(
                ParameterSignature("array", TypePattern.Array()),
                ParameterSignature("count", TypePattern.Scalar(ScalarKind.INTEGER))
            ),
            returnType = ReturnTypeLogic.PreserveFirstArgument
        ))
        
        register("unique", FunctionSignature(
            parameters = listOf(ParameterSignature("array", TypePattern.Array())),
            returnType = ReturnTypeLogic.PreserveFirstArgument
        ))
        
        register("distinct", FunctionSignature(
            parameters = listOf(ParameterSignature("array", TypePattern.Array())),
            returnType = ReturnTypeLogic.PreserveFirstArgument
        ))
        
        register("distinctBy", FunctionSignature(
            parameters = listOf(
                ParameterSignature("array", TypePattern.Array()),
                ParameterSignature("keySelector", TypePattern.Function())
            ),
            returnType = ReturnTypeLogic.PreserveFirstArgument
        ))
        
        register("groupBy", FunctionSignature(
            parameters = listOf(
                ParameterSignature("array", TypePattern.Array()),
                ParameterSignature("keySelector", TypePattern.Function())
            ),
            returnType = ReturnTypeLogic.Custom { args, context ->
                if (args.isNotEmpty()) {
                    val arrayType = context.analyzeExpression(args[0])
                    if (arrayType is TypeDefinition.Array) {
                        // groupBy returns an object where values are arrays of the original element type
                        TypeDefinition.Object(
                            properties = mapOf(),
                            required = setOf(),
                            additionalProperties = true
                        )
                    } else TypeDefinition.Any
                } else TypeDefinition.Any
            }
        ))
        
        register("partition", FunctionSignature(
            parameters = listOf(
                ParameterSignature("array", TypePattern.Array()),
                ParameterSignature("predicate", TypePattern.Function())
            ),
            returnType = ReturnTypeLogic.Custom { args, context ->
                if (args.isNotEmpty()) {
                    val arrayType = context.analyzeExpression(args[0])
                    // partition returns array of two arrays
                    TypeDefinition.Array(arrayType, minItems = 2, maxItems = 2)
                } else TypeDefinition.Any
            }
        ))
        
        register("zip", FunctionSignature(
            parameters = listOf(
                ParameterSignature("array1", TypePattern.Array()),
                ParameterSignature("array2", TypePattern.Array())
            ),
            returnType = ReturnTypeLogic.Custom { args, context ->
                if (args.size >= 2) {
                    val array1Type = context.analyzeExpression(args[0])
                    val array2Type = context.analyzeExpression(args[1])
                    if (array1Type is TypeDefinition.Array && array2Type is TypeDefinition.Array) {
                        // zip returns array of arrays with two elements
                        val tupleType = TypeDefinition.Array(
                            TypeDefinition.Union(listOf(array1Type.elementType, array2Type.elementType)),
                            minItems = 2, maxItems = 2
                        )
                        TypeDefinition.Array(tupleType)
                    } else TypeDefinition.Any
                } else TypeDefinition.Any
            }
        ))
        
        // Aggregation functions
        register("sum", FunctionSignature(
            parameters = listOf(ParameterSignature("array", TypePattern.Array())),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.NUMBER))
        ))
        
        register("avg", FunctionSignature(
            parameters = listOf(ParameterSignature("array", TypePattern.Array())),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.NUMBER))
        ))
        
        register("min", FunctionSignature(
            parameters = listOf(ParameterSignature("array", TypePattern.Array())),
            returnType = ReturnTypeLogic.ArrayElementType
        ))
        
        register("max", FunctionSignature(
            parameters = listOf(ParameterSignature("array", TypePattern.Array())),
            returnType = ReturnTypeLogic.ArrayElementType
        ))
        
        register("count", FunctionSignature(
            parameters = listOf(ParameterSignature("array", TypePattern.Array())),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.INTEGER))
        ))
        
        register("size", FunctionSignature(
            parameters = listOf(ParameterSignature("array", TypePattern.Array())),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.INTEGER))
        ))
        
        register("length", FunctionSignature(
            parameters = listOf(ParameterSignature("arrayOrString", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.INTEGER))
        ))
        
        register("every", FunctionSignature(
            parameters = listOf(
                ParameterSignature("array", TypePattern.Array()),
                ParameterSignature("predicate", TypePattern.Function())
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
        
        register("some", FunctionSignature(
            parameters = listOf(
                ParameterSignature("array", TypePattern.Array()),
                ParameterSignature("predicate", TypePattern.Function())
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
    }
    
    private fun registerStringFunctionTypes() {
        register("upper", FunctionSignature(
            parameters = listOf(ParameterSignature("str", TypePattern.Scalar(ScalarKind.STRING))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("lower", FunctionSignature(
            parameters = listOf(ParameterSignature("str", TypePattern.Scalar(ScalarKind.STRING))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("trim", FunctionSignature(
            parameters = listOf(ParameterSignature("str", TypePattern.Scalar(ScalarKind.STRING))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("substring", FunctionSignature(
            parameters = listOf(
                ParameterSignature("str", TypePattern.Scalar(ScalarKind.STRING)),
                ParameterSignature("start", TypePattern.Scalar(ScalarKind.INTEGER)),
                ParameterSignature("end", TypePattern.Scalar(ScalarKind.INTEGER), optional = true)
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("split", FunctionSignature(
            parameters = listOf(
                ParameterSignature("str", TypePattern.Scalar(ScalarKind.STRING)),
                ParameterSignature("delimiter", TypePattern.Scalar(ScalarKind.STRING))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Array(TypeDefinition.Scalar(ScalarKind.STRING)))
        ))
        
        register("joinBy", FunctionSignature(
            parameters = listOf(
                ParameterSignature("array", TypePattern.Array()),
                ParameterSignature("separator", TypePattern.Scalar(ScalarKind.STRING))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("replace", FunctionSignature(
            parameters = listOf(
                ParameterSignature("str", TypePattern.Scalar(ScalarKind.STRING)),
                ParameterSignature("search", TypePattern.Scalar(ScalarKind.STRING)),
                ParameterSignature("replacement", TypePattern.Scalar(ScalarKind.STRING))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("contains", FunctionSignature(
            parameters = listOf(
                ParameterSignature("str", TypePattern.Scalar(ScalarKind.STRING)),
                ParameterSignature("search", TypePattern.Scalar(ScalarKind.STRING))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
        
        register("startsWith", FunctionSignature(
            parameters = listOf(
                ParameterSignature("str", TypePattern.Scalar(ScalarKind.STRING)),
                ParameterSignature("prefix", TypePattern.Scalar(ScalarKind.STRING))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
        
        register("endsWith", FunctionSignature(
            parameters = listOf(
                ParameterSignature("str", TypePattern.Scalar(ScalarKind.STRING)),
                ParameterSignature("suffix", TypePattern.Scalar(ScalarKind.STRING))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
        
        register("matches", FunctionSignature(
            parameters = listOf(
                ParameterSignature("str", TypePattern.Scalar(ScalarKind.STRING)),
                ParameterSignature("pattern", TypePattern.Scalar(ScalarKind.STRING))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
        
        // Case conversion functions
        register("camelize", FunctionSignature(
            parameters = listOf(ParameterSignature("str", TypePattern.Scalar(ScalarKind.STRING))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("pascalCase", FunctionSignature(
            parameters = listOf(ParameterSignature("str", TypePattern.Scalar(ScalarKind.STRING))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("kebabCase", FunctionSignature(
            parameters = listOf(ParameterSignature("str", TypePattern.Scalar(ScalarKind.STRING))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("snakeCase", FunctionSignature(
            parameters = listOf(ParameterSignature("str", TypePattern.Scalar(ScalarKind.STRING))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        // Character and validation functions
        register("charAt", FunctionSignature(
            parameters = listOf(
                ParameterSignature("str", TypePattern.Scalar(ScalarKind.STRING)),
                ParameterSignature("index", TypePattern.Scalar(ScalarKind.INTEGER))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("isBlank", FunctionSignature(
            parameters = listOf(ParameterSignature("str", TypePattern.Scalar(ScalarKind.STRING))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
        
        register("pluralize", FunctionSignature(
            parameters = listOf(ParameterSignature("str", TypePattern.Scalar(ScalarKind.STRING))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("singularize", FunctionSignature(
            parameters = listOf(ParameterSignature("str", TypePattern.Scalar(ScalarKind.STRING))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
    }
    
    private fun registerObjectFunctionTypes() {
        register("keys", FunctionSignature(
            parameters = listOf(ParameterSignature("object", TypePattern.Object())),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Array(TypeDefinition.Scalar(ScalarKind.STRING)))
        ))
        
        register("values", FunctionSignature(
            parameters = listOf(ParameterSignature("object", TypePattern.Object())),
            returnType = ReturnTypeLogic.Custom { args, context ->
                if (args.isNotEmpty()) {
                    val objType = context.analyzeExpression(args[0])
                    if (objType is TypeDefinition.Object) {
                        val valueTypes = objType.properties.values.map { it.type }.distinct()
                        val elementType = if (valueTypes.size == 1) valueTypes.first() 
                                        else TypeDefinition.Union(valueTypes)
                        TypeDefinition.Array(elementType)
                    } else TypeDefinition.Array(TypeDefinition.Any)
                } else TypeDefinition.Any
            }
        ))
        
        register("entries", FunctionSignature(
            parameters = listOf(ParameterSignature("object", TypePattern.Object())),
            returnType = ReturnTypeLogic.Custom { args, context ->
                if (args.isNotEmpty()) {
                    val objType = context.analyzeExpression(args[0])
                    if (objType is TypeDefinition.Object) {
                        val entryType = TypeDefinition.Object(
                            properties = mapOf(
                                "key" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING), false),
                                "value" to PropertyType(TypeDefinition.Any, false)
                            ),
                            required = setOf("key", "value"),
                            additionalProperties = false
                        )
                        TypeDefinition.Array(entryType)
                    } else TypeDefinition.Array(TypeDefinition.Any)
                } else TypeDefinition.Any
            }
        ))
        
        register("merge", FunctionSignature(
            parameters = listOf(
                ParameterSignature("object1", TypePattern.Object()),
                ParameterSignature("object2", TypePattern.Object())
            ),
            returnType = ReturnTypeLogic.Custom { args, context ->
                if (args.size >= 2) {
                    val obj1Type = context.analyzeExpression(args[0])
                    val obj2Type = context.analyzeExpression(args[1])
                    if (obj1Type is TypeDefinition.Object && obj2Type is TypeDefinition.Object) {
                        // Merge properties, obj2 overrides obj1
                        val mergedProperties = obj1Type.properties.toMutableMap()
                        mergedProperties.putAll(obj2Type.properties)
                        TypeDefinition.Object(
                            properties = mergedProperties,
                            required = obj1Type.required + obj2Type.required,
                            additionalProperties = obj1Type.additionalProperties || obj2Type.additionalProperties
                        )
                    } else TypeDefinition.Any
                } else TypeDefinition.Any
            }
        ))
        
        register("pick", FunctionSignature(
            parameters = listOf(
                ParameterSignature("object", TypePattern.Object()),
                ParameterSignature("keys", TypePattern.Array())
            ),
            returnType = ReturnTypeLogic.Custom { args, context ->
                if (args.size >= 2) {
                    val objType = context.analyzeExpression(args[0])
                    if (objType is TypeDefinition.Object) {
                        // Return object with only picked properties (would need key analysis)
                        TypeDefinition.Object(
                            properties = mapOf(), // Would need static key analysis
                            required = setOf(),
                            additionalProperties = true
                        )
                    } else TypeDefinition.Any
                } else TypeDefinition.Any
            }
        ))
        
        register("omit", FunctionSignature(
            parameters = listOf(
                ParameterSignature("object", TypePattern.Object()),
                ParameterSignature("keys", TypePattern.Array())
            ),
            returnType = ReturnTypeLogic.PreserveFirstArgument // Simplified
        ))
        
        register("containsKey", FunctionSignature(
            parameters = listOf(
                ParameterSignature("object", TypePattern.Object()),
                ParameterSignature("key", TypePattern.Scalar(ScalarKind.STRING))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
        
        register("deepMerge", FunctionSignature(
            parameters = listOf(
                ParameterSignature("object1", TypePattern.Object()),
                ParameterSignature("object2", TypePattern.Object())
            ),
            returnType = ReturnTypeLogic.PreserveFirstArgument // Simplified
        ))
    }
    
    private fun registerMathFunctionTypes() {
        register("abs", FunctionSignature(
            parameters = listOf(ParameterSignature("number", TypePattern.Scalar(ScalarKind.NUMBER))),
            returnType = ReturnTypeLogic.PreserveFirstArgument
        ))
        
        register("round", FunctionSignature(
            parameters = listOf(ParameterSignature("number", TypePattern.Scalar(ScalarKind.NUMBER))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.INTEGER))
        ))
        
        register("ceil", FunctionSignature(
            parameters = listOf(ParameterSignature("number", TypePattern.Scalar(ScalarKind.NUMBER))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.INTEGER))
        ))
        
        register("floor", FunctionSignature(
            parameters = listOf(ParameterSignature("number", TypePattern.Scalar(ScalarKind.NUMBER))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.INTEGER))
        ))
        
        register("pow", FunctionSignature(
            parameters = listOf(
                ParameterSignature("base", TypePattern.Scalar(ScalarKind.NUMBER)),
                ParameterSignature("exponent", TypePattern.Scalar(ScalarKind.NUMBER))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.NUMBER))
        ))
        
        register("sqrt", FunctionSignature(
            parameters = listOf(ParameterSignature("number", TypePattern.Scalar(ScalarKind.NUMBER))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.NUMBER))
        ))
        
        register("random", FunctionSignature(
            parameters = listOf(),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.NUMBER))
        ))
        
        // Trigonometric functions
        register("sin", FunctionSignature(
            parameters = listOf(ParameterSignature("angle", TypePattern.Scalar(ScalarKind.NUMBER))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.NUMBER))
        ))
        
        register("cos", FunctionSignature(
            parameters = listOf(ParameterSignature("angle", TypePattern.Scalar(ScalarKind.NUMBER))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.NUMBER))
        ))
        
        register("tan", FunctionSignature(
            parameters = listOf(ParameterSignature("angle", TypePattern.Scalar(ScalarKind.NUMBER))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.NUMBER))
        ))
        
        // Statistical functions
        register("median", FunctionSignature(
            parameters = listOf(ParameterSignature("numbers", TypePattern.Array())),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.NUMBER))
        ))
        
        register("stdDev", FunctionSignature(
            parameters = listOf(ParameterSignature("numbers", TypePattern.Array())),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.NUMBER))
        ))
        
        register("variance", FunctionSignature(
            parameters = listOf(ParameterSignature("numbers", TypePattern.Array())),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.NUMBER))
        ))
    }
    
    private fun registerDateFunctionTypes() {
        register("now", FunctionSignature(
            parameters = listOf(),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.DATETIME))
        ))
        
        register("parseDate", FunctionSignature(
            parameters = listOf(
                ParameterSignature("dateString", TypePattern.Scalar(ScalarKind.STRING)),
                ParameterSignature("format", TypePattern.Scalar(ScalarKind.STRING), optional = true)
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.DATE))
        ))
        
        register("formatDate", FunctionSignature(
            parameters = listOf(
                ParameterSignature("date", TypePattern.Scalar(ScalarKind.DATE)),
                ParameterSignature("format", TypePattern.Scalar(ScalarKind.STRING))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("addDays", FunctionSignature(
            parameters = listOf(
                ParameterSignature("date", TypePattern.Scalar(ScalarKind.DATE)),
                ParameterSignature("days", TypePattern.Scalar(ScalarKind.INTEGER))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.DATE))
        ))
        
        register("addHours", FunctionSignature(
            parameters = listOf(
                ParameterSignature("date", TypePattern.Scalar(ScalarKind.DATETIME)),
                ParameterSignature("hours", TypePattern.Scalar(ScalarKind.INTEGER))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.DATETIME))
        ))
        
        register("diffDays", FunctionSignature(
            parameters = listOf(
                ParameterSignature("date1", TypePattern.Scalar(ScalarKind.DATE)),
                ParameterSignature("date2", TypePattern.Scalar(ScalarKind.DATE))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.INTEGER))
        ))
        
        register("day", FunctionSignature(
            parameters = listOf(ParameterSignature("date", TypePattern.Scalar(ScalarKind.DATE))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.INTEGER))
        ))
        
        register("month", FunctionSignature(
            parameters = listOf(ParameterSignature("date", TypePattern.Scalar(ScalarKind.DATE))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.INTEGER))
        ))
        
        register("year", FunctionSignature(
            parameters = listOf(ParameterSignature("date", TypePattern.Scalar(ScalarKind.DATE))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.INTEGER))
        ))
        
        register("isLeapYear", FunctionSignature(
            parameters = listOf(ParameterSignature("year", TypePattern.Scalar(ScalarKind.INTEGER))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
        
        register("isBefore", FunctionSignature(
            parameters = listOf(
                ParameterSignature("date1", TypePattern.Scalar(ScalarKind.DATE)),
                ParameterSignature("date2", TypePattern.Scalar(ScalarKind.DATE))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
        
        register("isAfter", FunctionSignature(
            parameters = listOf(
                ParameterSignature("date1", TypePattern.Scalar(ScalarKind.DATE)),
                ParameterSignature("date2", TypePattern.Scalar(ScalarKind.DATE))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
    }
    
    private fun registerTypeFunctionTypes() {
        register("typeOf", FunctionSignature(
            parameters = listOf(ParameterSignature("value", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("isString", FunctionSignature(
            parameters = listOf(ParameterSignature("value", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
        
        register("isNumber", FunctionSignature(
            parameters = listOf(ParameterSignature("value", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
        
        register("isBoolean", FunctionSignature(
            parameters = listOf(ParameterSignature("value", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
        
        register("isArray", FunctionSignature(
            parameters = listOf(ParameterSignature("value", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
        
        register("isObject", FunctionSignature(
            parameters = listOf(ParameterSignature("value", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
        
        register("isNull", FunctionSignature(
            parameters = listOf(ParameterSignature("value", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
        
        register("isEmpty", FunctionSignature(
            parameters = listOf(ParameterSignature("value", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
        
        register("isDefined", FunctionSignature(
            parameters = listOf(ParameterSignature("value", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
    }
    
    private fun registerEncodingFunctionTypes() {
        register("base64Encode", FunctionSignature(
            parameters = listOf(ParameterSignature("data", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("base64Decode", FunctionSignature(
            parameters = listOf(ParameterSignature("encoded", TypePattern.Scalar(ScalarKind.STRING))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("urlEncode", FunctionSignature(
            parameters = listOf(ParameterSignature("text", TypePattern.Scalar(ScalarKind.STRING))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("urlDecode", FunctionSignature(
            parameters = listOf(ParameterSignature("encoded", TypePattern.Scalar(ScalarKind.STRING))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("md5", FunctionSignature(
            parameters = listOf(ParameterSignature("data", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("sha256", FunctionSignature(
            parameters = listOf(ParameterSignature("data", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("sha512", FunctionSignature(
            parameters = listOf(ParameterSignature("data", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
    }
    
    private fun registerLogicalFunctionTypes() {
        register("not", FunctionSignature(
            parameters = listOf(ParameterSignature("value", TypePattern.Scalar(ScalarKind.BOOLEAN))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
        
        register("and", FunctionSignature(
            parameters = listOf(
                ParameterSignature("value1", TypePattern.Scalar(ScalarKind.BOOLEAN)),
                ParameterSignature("value2", TypePattern.Scalar(ScalarKind.BOOLEAN))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
        
        register("or", FunctionSignature(
            parameters = listOf(
                ParameterSignature("value1", TypePattern.Scalar(ScalarKind.BOOLEAN)),
                ParameterSignature("value2", TypePattern.Scalar(ScalarKind.BOOLEAN))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
        
        register("xor", FunctionSignature(
            parameters = listOf(
                ParameterSignature("value1", TypePattern.Scalar(ScalarKind.BOOLEAN)),
                ParameterSignature("value2", TypePattern.Scalar(ScalarKind.BOOLEAN))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
    }
    
    private fun registerBinaryFunctionTypes() {
        register("toBinary", FunctionSignature(
            parameters = listOf(ParameterSignature("data", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BINARY))
        ))
        
        register("binaryToString", FunctionSignature(
            parameters = listOf(ParameterSignature("binary", TypePattern.Scalar(ScalarKind.BINARY))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("binaryLength", FunctionSignature(
            parameters = listOf(ParameterSignature("binary", TypePattern.Scalar(ScalarKind.BINARY))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.INTEGER))
        ))
        
        register("gzip", FunctionSignature(
            parameters = listOf(ParameterSignature("data", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BINARY))
        ))
        
        register("gunzip", FunctionSignature(
            parameters = listOf(ParameterSignature("compressed", TypePattern.Scalar(ScalarKind.BINARY))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BINARY))
        ))
    }
    
    private fun registerFinancialFunctionTypes() {
        register("formatCurrency", FunctionSignature(
            parameters = listOf(
                ParameterSignature("amount", TypePattern.Scalar(ScalarKind.NUMBER)),
                ParameterSignature("currency", TypePattern.Scalar(ScalarKind.STRING))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("parseCurrency", FunctionSignature(
            parameters = listOf(ParameterSignature("currencyString", TypePattern.Scalar(ScalarKind.STRING))),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.NUMBER))
        ))
        
        register("calculateTax", FunctionSignature(
            parameters = listOf(
                ParameterSignature("amount", TypePattern.Scalar(ScalarKind.NUMBER)),
                ParameterSignature("taxRate", TypePattern.Scalar(ScalarKind.NUMBER))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.NUMBER))
        ))
        
        register("presentValue", FunctionSignature(
            parameters = listOf(
                ParameterSignature("futureValue", TypePattern.Scalar(ScalarKind.NUMBER)),
                ParameterSignature("rate", TypePattern.Scalar(ScalarKind.NUMBER)),
                ParameterSignature("periods", TypePattern.Scalar(ScalarKind.INTEGER))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.NUMBER))
        ))
    }
    
    private fun registerGeospatialFunctionTypes() {
        register("distance", FunctionSignature(
            parameters = listOf(
                ParameterSignature("point1", TypePattern.Object()),
                ParameterSignature("point2", TypePattern.Object())
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.NUMBER))
        ))
        
        register("bearing", FunctionSignature(
            parameters = listOf(
                ParameterSignature("point1", TypePattern.Object()),
                ParameterSignature("point2", TypePattern.Object())
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.NUMBER))
        ))
        
        register("isPointInCircle", FunctionSignature(
            parameters = listOf(
                ParameterSignature("point", TypePattern.Object()),
                ParameterSignature("center", TypePattern.Object()),
                ParameterSignature("radius", TypePattern.Scalar(ScalarKind.NUMBER))
            ),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
        ))
    }
    
    private fun registerXmlFunctionTypes() {
        register("localName", FunctionSignature(
            parameters = listOf(ParameterSignature("qname", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("namespaceUri", FunctionSignature(
            parameters = listOf(ParameterSignature("qname", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("textContent", FunctionSignature(
            parameters = listOf(ParameterSignature("element", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Scalar(ScalarKind.STRING))
        ))
        
        register("attributes", FunctionSignature(
            parameters = listOf(ParameterSignature("element", TypePattern.Any)),
            returnType = ReturnTypeLogic.Fixed(TypeDefinition.Object(mapOf(), setOf(), true))
        ))
    }
    
    private fun registerCoreFunctionTypes() {
        register("if", FunctionSignature(
            parameters = listOf(
                ParameterSignature("condition", TypePattern.Scalar(ScalarKind.BOOLEAN)),
                ParameterSignature("thenValue", TypePattern.Any),
                ParameterSignature("elseValue", TypePattern.Any, optional = true)
            ),
            returnType = ReturnTypeLogic.Custom { args, context ->
                if (args.size >= 2) {
                    val thenType = context.analyzeExpression(args[1])
                    val elseType = if (args.size >= 3) context.analyzeExpression(args[2]) else TypeDefinition.Scalar(ScalarKind.NULL)
                    if (thenType == elseType) thenType else TypeDefinition.Union(listOf(thenType, elseType))
                } else TypeDefinition.Any
            }
        ))
        
        register("coalesce", FunctionSignature(
            parameters = listOf(ParameterSignature("values", TypePattern.Any, variadic = true)),
            returnType = ReturnTypeLogic.Custom { args, context ->
                if (args.isNotEmpty()) {
                    val types = args.map { context.analyzeExpression(it) }.distinct()
                    if (types.size == 1) types.first() else TypeDefinition.Union(types)
                } else TypeDefinition.Any
            }
        ))
        
        register("default", FunctionSignature(
            parameters = listOf(
                ParameterSignature("value", TypePattern.Any),
                ParameterSignature("defaultValue", TypePattern.Any)
            ),
            returnType = ReturnTypeLogic.PreserveFirstArgument
        ))
        
        register("concat", FunctionSignature(
            parameters = listOf(ParameterSignature("values", TypePattern.Any, variadic = true)),
            returnType = ReturnTypeLogic.Custom { args, context ->
                if (args.isNotEmpty()) {
                    val firstType = context.analyzeExpression(args[0])
                    when (firstType) {
                        is TypeDefinition.Scalar -> {
                            when (firstType.kind) {
                                ScalarKind.STRING -> TypeDefinition.Scalar(ScalarKind.STRING)
                                else -> TypeDefinition.Any
                            }
                        }
                        is TypeDefinition.Array -> TypeDefinition.Array(firstType.elementType)
                        else -> TypeDefinition.Any
                    }
                } else TypeDefinition.Any
            }
        ))
    }
    
    private fun register(name: String, signature: FunctionSignature) {
        functionSignatures[name] = signature
    }
    
    private fun integrateCompleteFunctions() {
        // Get all function signatures from CompleteFunctionRegistry
        val completeSignatures = CompleteFunctionRegistry.getAllFunctionNames()
        for (functionName in completeSignatures) {
            val signature = CompleteFunctionRegistry.getFunctionSignature(functionName)
            if (signature != null && !functionSignatures.containsKey(functionName)) {
                functionSignatures[functionName] = signature
            }
        }
    }
}

/**
 * Function signature for type inference
 *
 * Note: Constructor uses `returnType` parameter but internally stores as `returnTypeLogic`.
 * The public `returnType` property returns a resolved TypeDefinition for test compatibility.
 */
class FunctionSignature(
    val name: String? = null,
    val parameters: List<ParameterSignature>,
    returnType: ReturnTypeLogic,
    val description: String? = null
) {
    /**
     * Internal storage of return type logic
     */
    internal val returnTypeLogic: ReturnTypeLogic = returnType

    /**
     * Get the resolved return type as TypeDefinition
     * For compatibility with tests that expect a TypeDefinition directly
     */
    val returnType: TypeDefinition
        get() = when (returnTypeLogic) {
            is ReturnTypeLogic.Fixed -> returnTypeLogic.type
            else -> TypeDefinition.Any
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FunctionSignature) return false
        if (name != other.name) return false
        if (parameters != other.parameters) return false
        if (returnTypeLogic != other.returnTypeLogic) return false
        if (description != other.description) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + parameters.hashCode()
        result = 31 * result + returnTypeLogic.hashCode()
        result = 31 * result + (description?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "FunctionSignature(name=$name, parameters=$parameters, returnType=$returnTypeLogic, description=$description)"
    }

    /**
     * Check if the provided argument types match this function's parameters
     * Returns a validation result with errors and the inferred return type
     */
    fun checkArguments(argumentTypes: List<TypeDefinition>): ArgumentCheckResult {
        val errors = mutableListOf<String>()

        // Check argument count
        val requiredParams = parameters.filter { !it.optional && !it.variadic }
        val minArgs = requiredParams.size
        val maxArgs = if (parameters.any { it.variadic }) Int.MAX_VALUE else parameters.size

        if (argumentTypes.size < minArgs) {
            errors.add("Too few arguments: expected at least $minArgs, got ${argumentTypes.size}")
            return ArgumentCheckResult(errors, TypeDefinition.Never)
        }

        if (argumentTypes.size > maxArgs) {
            errors.add("Too many arguments: expected at most $maxArgs, got ${argumentTypes.size}")
            return ArgumentCheckResult(errors, TypeDefinition.Never)
        }

        // Check each argument type matches its parameter pattern
        argumentTypes.forEachIndexed { index, argType ->
            val param = if (index < parameters.size) parameters[index]
                       else parameters.lastOrNull()?.takeIf { it.variadic }

            if (param != null) {
                if (!matchesPattern(argType, param.type)) {
                    errors.add("Argument ${index + 1} (${param.name}): type mismatch - expected ${formatPattern(param.type)}, got ${formatType(argType)}")
                }
            }
        }

        // Determine return type
        val inferredReturnType = when (returnTypeLogic) {
            is ReturnTypeLogic.Fixed -> returnTypeLogic.type
            is ReturnTypeLogic.PreserveFirstArgument -> argumentTypes.firstOrNull() ?: TypeDefinition.Any
            is ReturnTypeLogic.ArrayElementType -> {
                val firstArg = argumentTypes.firstOrNull()
                if (firstArg is TypeDefinition.Array) firstArg.elementType else TypeDefinition.Any
            }
            is ReturnTypeLogic.ArrayFlatten -> {
                val firstArg = argumentTypes.firstOrNull()
                if (firstArg is TypeDefinition.Array && firstArg.elementType is TypeDefinition.Array) {
                    (firstArg.elementType as TypeDefinition.Array).elementType
                } else {
                    firstArg ?: TypeDefinition.Any
                }
            }
            is ReturnTypeLogic.ThirdArgumentOrAny -> {
                argumentTypes.getOrNull(2) ?: TypeDefinition.Any
            }
            is ReturnTypeLogic.ArrayTransform -> TypeDefinition.Any // Simplified for now
            is ReturnTypeLogic.Custom -> TypeDefinition.Any // Would need expression context
        }

        return ArgumentCheckResult(errors, inferredReturnType)
    }

    private fun matchesPattern(type: TypeDefinition, pattern: TypePattern): Boolean {
        return when (pattern) {
            is TypePattern.Any -> true
            is TypePattern.Scalar -> {
                type is TypeDefinition.Scalar && (pattern.kind == null || type.kind == pattern.kind)
            }
            is TypePattern.Array -> {
                type is TypeDefinition.Array && (pattern.elementType == null || matchesPattern(type.elementType, pattern.elementType))
            }
            is TypePattern.Object -> type is TypeDefinition.Object
            is TypePattern.Function -> true // Lambda/function types not fully implemented
            is TypePattern.Union -> pattern.types.any { matchesPattern(type, it) }
        }
    }

    private fun formatPattern(pattern: TypePattern): String = when (pattern) {
        is TypePattern.Any -> "Any"
        is TypePattern.Scalar -> pattern.kind?.name ?: "Scalar"
        is TypePattern.Array -> "Array<${pattern.elementType?.let { formatPattern(it) } ?: "Any"}>"
        is TypePattern.Object -> "Object"
        is TypePattern.Function -> "Function"
        is TypePattern.Union -> pattern.types.joinToString(" | ") { formatPattern(it) }
    }

    private fun formatType(type: TypeDefinition): String = when (type) {
        is TypeDefinition.Scalar -> type.kind.name
        is TypeDefinition.Array -> "Array<${formatType(type.elementType)}>"
        is TypeDefinition.Object -> "Object"
        is TypeDefinition.Union -> type.types.joinToString(" | ") { formatType(it) }
        is TypeDefinition.Any -> "Any"
        is TypeDefinition.Unknown -> "Unknown"
        is TypeDefinition.Never -> "Never"
    }
}

/**
 * Result of argument type checking
 */
data class ArgumentCheckResult(
    val errors: List<String>,
    val returnType: TypeDefinition
) {
    fun isValid(): Boolean = errors.isEmpty()
}

/**
 * Parameter signature
 */
data class ParameterSignature(
    val name: String,
    val type: TypePattern,
    val optional: Boolean = false,
    val variadic: Boolean = false
)

/**
 * Type patterns for matching parameter types
 */
sealed class TypePattern {
    object Any : TypePattern()
    data class Scalar(val kind: ScalarKind? = null) : TypePattern()
    data class Array(val elementType: TypePattern? = null) : TypePattern()
    data class Object(val properties: Map<String, TypePattern>? = null) : TypePattern()
    data class Function(val returnType: TypePattern? = null) : TypePattern()
    data class Union(val types: List<TypePattern>) : TypePattern()
}

/**
 * Return type logic for functions
 */
sealed class ReturnTypeLogic {
    object PreserveFirstArgument : ReturnTypeLogic()
    object ArrayElementType : ReturnTypeLogic()
    object ArrayFlatten : ReturnTypeLogic()
    object ThirdArgumentOrAny : ReturnTypeLogic()
    data class Fixed(val type: TypeDefinition) : ReturnTypeLogic()
    data class ArrayTransform(val transformer: (TypeDefinition, List<TypeDefinition>) -> TypeDefinition) : ReturnTypeLogic()
    data class Custom(val analyzer: (List<org.apache.utlx.core.ast.Expression>, TypeInferenceContext) -> TypeDefinition) : ReturnTypeLogic()
}

/**
 * Context for type inference
 */
interface TypeInferenceContext {
    fun analyzeExpression(expression: org.apache.utlx.core.ast.Expression): TypeDefinition
}