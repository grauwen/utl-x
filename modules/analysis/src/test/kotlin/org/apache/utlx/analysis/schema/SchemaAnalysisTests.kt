// modules/analysis/src/test/kotlin/org/apache/utlx/analysis/schema/SchemaAnalysisTests.kt
package org.apache.utlx.analysis.schema

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.utlx.analysis.types.*
import org.apache.utlx.core.ast.*
import org.apache.utlx.core.parser.Parser

class XSDSchemaParserTest : DescribeSpec({
    
    val parser = XSDSchemaParser()
    
    describe("XSD Schema Parsing") {
        
        it("should parse simple element with string type") {
            val xsd = """
                <?xml version="1.0"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="Name" type="xs:string"/>
                </xs:schema>
            """.trimIndent()
            
            val type = parser.parse(xsd, SchemaFormat.XSD)
            
            type.shouldBeInstanceOf<TypeDefinition.Scalar>()
            (type as TypeDefinition.Scalar).kind.shouldBe(ScalarKind.STRING)
        }
        
        it("should parse complex type with sequence") {
            val xsd = """
                <?xml version="1.0"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="Person">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="Name" type="xs:string"/>
                                <xs:element name="Age" type="xs:integer"/>
                                <xs:element name="Email" type="xs:string"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
            """.trimIndent()
            
            val type = parser.parse(xsd, SchemaFormat.XSD)
            
            type.shouldBeInstanceOf<TypeDefinition.Object>()
            val obj = type as TypeDefinition.Object
            
            obj.properties.size.shouldBe(3)
            obj.properties["Name"]?.type.shouldBeInstanceOf<TypeDefinition.Scalar>()
            obj.properties["Age"]?.type.shouldBeInstanceOf<TypeDefinition.Scalar>()
            obj.properties["Email"]?.type.shouldBeInstanceOf<TypeDefinition.Scalar>()
            
            obj.required.shouldBe(setOf("Name", "Age", "Email"))
        }
        
        it("should parse elements with attributes") {
            val xsd = """
                <?xml version="1.0"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="Product">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="Name" type="xs:string"/>
                            </xs:sequence>
                            <xs:attribute name="id" type="xs:string" use="required"/>
                            <xs:attribute name="category" type="xs:string"/>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
            """.trimIndent()
            
            val type = parser.parse(xsd, SchemaFormat.XSD)
            
            type.shouldBeInstanceOf<TypeDefinition.Object>()
            val obj = type as TypeDefinition.Object
            
            obj.properties["Name"].shouldNotBe(null)
            obj.properties["@id"].shouldNotBe(null)  // Attributes prefixed with @
            obj.properties["@category"].shouldNotBe(null)
            
            obj.required.contains("@id").shouldBe(true)
            obj.required.contains("@category").shouldBe(false)
        }
        
        it("should parse array elements (maxOccurs > 1)") {
            val xsd = """
                <?xml version="1.0"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="OrderList">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="Order" maxOccurs="unbounded">
                                    <xs:complexType>
                                        <xs:sequence>
                                            <xs:element name="OrderID" type="xs:string"/>
                                        </xs:sequence>
                                    </xs:complexType>
                                </xs:element>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
            """.trimIndent()
            
            val type = parser.parse(xsd, SchemaFormat.XSD)
            
            type.shouldBeInstanceOf<TypeDefinition.Object>()
            val obj = type as TypeDefinition.Object
            
            val orderType = obj.properties["Order"]?.type
            orderType.shouldBeInstanceOf<TypeDefinition.Array>()
            
            val array = orderType as TypeDefinition.Array
            array.elementType.shouldBeInstanceOf<TypeDefinition.Object>()
        }
        
        it("should parse simple type with restrictions") {
            val xsd = """
                <?xml version="1.0"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="Age">
                        <xs:simpleType>
                            <xs:restriction base="xs:integer">
                                <xs:minInclusive value="0"/>
                                <xs:maxInclusive value="150"/>
                            </xs:restriction>
                        </xs:simpleType>
                    </xs:element>
                </xs:schema>
            """.trimIndent()
            
            val type = parser.parse(xsd, SchemaFormat.XSD)
            
            type.shouldBeInstanceOf<TypeDefinition.Scalar>()
            val scalar = type as TypeDefinition.Scalar
            
            scalar.kind.shouldBe(ScalarKind.INTEGER)
            scalar.constraints.size.shouldBe(2)
        }
        
        it("should parse enumeration restrictions") {
            val xsd = """
                <?xml version="1.0"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="Status">
                        <xs:simpleType>
                            <xs:restriction base="xs:string">
                                <xs:enumeration value="pending"/>
                                <xs:enumeration value="approved"/>
                                <xs:enumeration value="rejected"/>
                            </xs:restriction>
                        </xs:simpleType>
                    </xs:element>
                </xs:schema>
            """.trimIndent()
            
            val type = parser.parse(xsd, SchemaFormat.XSD)
            
            type.shouldBeInstanceOf<TypeDefinition.Scalar>()
            val scalar = type as TypeDefinition.Scalar
            
            val enumConstraint = scalar.constraints.find { it.kind == ConstraintKind.ENUM }
            enumConstraint.shouldNotBe(null)
            (enumConstraint!!.value as List<*>).size.shouldBe(3)
        }
    }
})

class JSONSchemaGeneratorTest : DescribeSpec({
    
    val generator = JSONSchemaGenerator()
    
    describe("JSON Schema Generation") {
        
        it("should generate schema for simple scalar types") {
            val type = TypeDefinition.Scalar(ScalarKind.STRING)
            
            val schema = generator.generate(
                type,
                SchemaFormat.JSON_SCHEMA,
                GeneratorOptions(pretty = false)
            )
            
            schema.contains("\"type\":\"string\"").shouldBe(true)
        }
        
        it("should generate schema for object types") {
            val type = TypeDefinition.Object(
                properties = mapOf(
                    "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                    "age" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER)),
                    "active" to PropertyType(TypeDefinition.Scalar(ScalarKind.BOOLEAN))
                ),
                required = setOf("name", "age")
            )
            
            val schema = generator.generate(
                type,
                SchemaFormat.JSON_SCHEMA,
                GeneratorOptions(pretty = true)
            )
            
            schema.contains("\"type\": \"object\"").shouldBe(true)
            schema.contains("\"name\"").shouldBe(true)
            schema.contains("\"age\"").shouldBe(true)
            schema.contains("\"active\"").shouldBe(true)
            schema.contains("\"required\"").shouldBe(true)
        }
        
        it("should generate schema for array types") {
            val type = TypeDefinition.Array(
                elementType = TypeDefinition.Scalar(ScalarKind.STRING),
                minItems = 1,
                maxItems = 10
            )
            
            val schema = generator.generate(
                type,
                SchemaFormat.JSON_SCHEMA,
                GeneratorOptions(pretty = false)
            )
            
            schema.contains("\"type\":\"array\"").shouldBe(true)
            schema.contains("\"items\"").shouldBe(true)
            schema.contains("\"minItems\":1").shouldBe(true)
            schema.contains("\"maxItems\":10").shouldBe(true)
        }
        
        it("should generate schema with constraints") {
            val type = TypeDefinition.Scalar(
                kind = ScalarKind.STRING,
                constraints = listOf(
                    Constraint(ConstraintKind.MIN_LENGTH, 3),
                    Constraint(ConstraintKind.MAX_LENGTH, 50),
                    Constraint(ConstraintKind.PATTERN, "^[A-Z].*")
                )
            )
            
            val schema = generator.generate(
                type,
                SchemaFormat.JSON_SCHEMA,
                GeneratorOptions(pretty = false)
            )
            
            schema.contains("\"minLength\":3").shouldBe(true)
            schema.contains("\"maxLength\":50").shouldBe(true)
            schema.contains("\"pattern\"").shouldBe(true)
        }
        
        it("should generate schema for nested objects") {
            val type = TypeDefinition.Object(
                properties = mapOf(
                    "person" to PropertyType(
                        TypeDefinition.Object(
                            properties = mapOf(
                                "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                                "address" to PropertyType(
                                    TypeDefinition.Object(
                                        properties = mapOf(
                                            "street" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                                            "city" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING))
                                        ),
                                        required = setOf("street", "city")
                                    )
                                )
                            ),
                            required = setOf("name", "address")
                        )
                    )
                ),
                required = setOf("person")
            )
            
            val schema = generator.generate(
                type,
                SchemaFormat.JSON_SCHEMA,
                GeneratorOptions(pretty = true)
            )
            
            schema.contains("\"person\"").shouldBe(true)
            schema.contains("\"address\"").shouldBe(true)
            schema.contains("\"street\"").shouldBe(true)
            schema.contains("\"city\"").shouldBe(true)
        }
        
        it("should generate schema with date formats") {
            val type = TypeDefinition.Scalar(ScalarKind.DATE)
            
            val schema = generator.generate(
                type,
                SchemaFormat.JSON_SCHEMA,
                GeneratorOptions(pretty = false)
            )
            
            schema.contains("\"type\":\"string\"").shouldBe(true)
            schema.contains("\"format\":\"date\"").shouldBe(true)
        }
    }
})

class AdvancedTypeInferenceTest : DescribeSpec({
    
    describe("Type Inference") {
        
        it("should infer type from simple object expression") {
            val inputType = TypeDefinition.Object(
                properties = mapOf(
                    "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                    "age" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER))
                ),
                required = setOf("name", "age")
            )
            
            val program = Program(
                declarations = emptyList(),
                mainExpression = ObjectExpression(
                    properties = mapOf(
                        "fullName" to PathExpression(listOf(
                            PathSegment("input"),
                            PathSegment("name")
                        )),
                        "years" to PathExpression(listOf(
                            PathSegment("input"),
                            PathSegment("age")
                        ))
                    )
                )
            )
            
            val inference = AdvancedTypeInference(inputType)
            val outputType = inference.inferOutputType(program)
            
            outputType.shouldBeInstanceOf<TypeDefinition.Object>()
            val obj = outputType as TypeDefinition.Object
            
            obj.properties["fullName"]?.type.shouldBeInstanceOf<TypeDefinition.Scalar>()
            obj.properties["years"]?.type.shouldBeInstanceOf<TypeDefinition.Scalar>()
        }
        
        it("should infer type from map operation") {
            val inputType = TypeDefinition.Object(
                properties = mapOf(
                    "items" to PropertyType(
                        TypeDefinition.Array(
                            elementType = TypeDefinition.Object(
                                properties = mapOf(
                                    "name" to PropertyType(TypeDefinition.Scalar(ScalarKind.STRING)),
                                    "price" to PropertyType(TypeDefinition.Scalar(ScalarKind.NUMBER))
                                ),
                                required = setOf("name", "price")
                            )
                        )
                    )
                ),
                required = setOf("items")
            )
            
            // Simulating: input.items |> map(item => { product: item.name })
            val program = Program(
                declarations = emptyList(),
                mainExpression = PipeExpression(
                    input = PathExpression(listOf(
                        PathSegment("input"),
                        PathSegment("items")
                    )),
                    operations = listOf(
                        FunctionCall(
                            name = "map",
                            arguments = listOf(
                                LambdaExpression(
                                    parameter = "item",
                                    body = ObjectExpression(
                                        properties = mapOf(
                                            "product" to PathExpression(listOf(
                                                PathSegment("item"),
                                                PathSegment("name")
                                            ))
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
            
            val inference = AdvancedTypeInference(inputType)
            val outputType = inference.inferOutputType(program)
            
            outputType.shouldBeInstanceOf<TypeDefinition.Array>()
            val array = outputType as TypeDefinition.Array
            
            array.elementType.shouldBeInstanceOf<TypeDefinition.Object>()
            val elementObj = array.elementType as TypeDefinition.Object
            
            elementObj.properties["product"]?.type.shouldBeInstanceOf<TypeDefinition.Scalar>()
        }
        
        it("should infer union type from if-else expression") {
            val inputType = TypeDefinition.Object(
                properties = mapOf(
                    "value" to PropertyType(TypeDefinition.Scalar(ScalarKind.INTEGER))
                ),
                required = setOf("value")
            )
            
            // Simulating: if (input.value > 0) "positive" else 0
            val program = Program(
                declarations = emptyList(),
                mainExpression = IfExpression(
                    condition = BinaryOperation(
                        operator = ">",
                        left = PathExpression(listOf(PathSegment("input"), PathSegment("value"))),
                        right = NumberLiteral(0.0)
                    ),
                    thenBranch = StringLiteral("positive"),
                    elseBranch = NumberLiteral(0.0)
                )
            )
            
            val inference = AdvancedTypeInference(inputType)
            val outputType = inference.inferOutputType(program)
            
            outputType.shouldBeInstanceOf<TypeDefinition.Union>()
            val union = outputType as TypeDefinition.Union
            
            union.types.size.shouldBe(2)
        }
        
        it("should infer type through function calls") {
            val inputType = TypeDefinition.Object(
                properties = mapOf(
                    "items" to PropertyType(
                        TypeDefinition.Array(
                            elementType = TypeDefinition.Scalar(ScalarKind.NUMBER)
                        )
                    )
                ),
                required = setOf("items")
            )
            
            // Simulating: sum(input.items)
            val program = Program(
                declarations = emptyList(),
                mainExpression = FunctionCall(
                    name = "sum",
                    arguments = listOf(
                        PathExpression(listOf(
                            PathSegment("input"),
                            PathSegment("items")
                        ))
                    )
                )
            )
            
            val inference = AdvancedTypeInference(inputType)
            val outputType = inference.inferOutputType(program)
            
            outputType.shouldBeInstanceOf<TypeDefinition.Scalar>()
            (outputType as TypeDefinition.Scalar).kind.shouldBe(ScalarKind.NUMBER)
        }
    }
})

class EndToEndSchemaGenerationTest : DescribeSpec({
    
    describe("End-to-End Schema Generation") {
        
        it("should generate output schema from XSD input and transformation") {
            // Input XSD
            val inputXSD = """
                <?xml version="1.0"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="Order">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="OrderID" type="xs:string"/>
                                <xs:element name="Total" type="xs:decimal"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
            """.trimIndent()
            
            // Parse transformation (simplified for test)
            val program = Program(
                declarations = emptyList(),
                mainExpression = ObjectExpression(
                    properties = mapOf(
                        "invoice" to ObjectExpression(
                            properties = mapOf(
                                "id" to PathExpression(listOf(
                                    PathSegment("input"),
                                    PathSegment("Order"),
                                    PathSegment("OrderID")
                                )),
                                "amount" to PathExpression(listOf(
                                    PathSegment("input"),
                                    PathSegment("Order"),
                                    PathSegment("Total")
                                ))
                            )
                        )
                    )
                )
            )
            
            // Generate output schema
            val schemaGen = SchemaGenerator()
            val outputSchema = schemaGen.generate(
                transformation = program,
                inputSchemaContent = inputXSD,
                inputSchemaFormat = SchemaFormat.XSD,
                outputSchemaFormat = SchemaFormat.JSON_SCHEMA,
                options = GeneratorOptions(pretty = true)
            )
            
            outputSchema.shouldNotBe("")
            outputSchema.contains("invoice").shouldBe(true)
            outputSchema.contains("\"id\"").shouldBe(true)
            outputSchema.contains("\"amount\"").shouldBe(true)
        }
    }
})

// Simplified AST nodes for testing
data class Program(
    val declarations: List<Declaration>,
    val mainExpression: Expression
)

sealed interface Declaration
data class LetDeclaration(val name: String, val value: Expression) : Declaration
data class FunctionDeclaration(
    val name: String,
    val parameters: List<Parameter>,
    val body: Expression
) : Declaration

data class Parameter(val name: String, val type: Type?)

sealed interface Expression
data class ObjectExpression(val properties: Map<String, Expression>) : Expression
data class ArrayExpression(val elements: List<Expression>) : Expression
data class PathExpression(val segments: List<PathSegment>) : Expression
data class StringLiteral(val value: String) : Expression
data class NumberLiteral(val value: Double) : Expression
data class BooleanLiteral(val value: Boolean) : Expression
object NullLiteral : Expression
data class BinaryOperation(val operator: String, val left: Expression, val right: Expression) : Expression
data class UnaryOperation(val operator: String, val operand: Expression) : Expression
data class FunctionCall(val name: String, val arguments: List<Expression>) : Expression
data class IfExpression(val condition: Expression, val thenBranch: Expression, val elseBranch: Expression?) : Expression
data class MatchExpression(val input: Expression, val cases: List<MatchCase>) : Expression
data class LetExpression(val bindings: Map<String, Expression>, val body: Expression) : Expression
data class VariableReference(val name: String) : Expression
data class PipeExpression(val input: Expression, val operations: List<Expression>) : Expression
data class LambdaExpression(val parameter: String, val body: Expression) : Expression

data class PathSegment(val name: String, val isArrayAccess: Boolean = false)
data class MatchCase(val pattern: Expression, val result: Expression)
