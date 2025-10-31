  Design-time vs runtime transformation analysis. 

  The Core Concept: Design-Time Schema Transformation

  You're describing a static analysis capability where:

  Design Time (Schema level):
  Input Schema (XSD/JSON Schema) + UTLX Transform → Output Schema (XSD/JSON Schema)

  Runtime (Data level):
  Input Data (XML/JSON) + UTLX Transform → Output Data (XML/JSON)

  This is fundamentally about schema inference through static program analysis.

  Detailed Reasoning

  Scenario 1: XML Input with XSD Metadata

Design-time workflow:
  1. ec-api-design tool → generates order.xsd
  2. Developer writes UTLX transformation
  3. Design-time analyzer: order.xsd + transform.utlx → output.schema.json
  4. Validation: Output schema matches expected contract

  Runtime workflow:
  1. order.xml arrives (conforms to order.xsd)
  2. UTL-X runtime: order.xml + transform.utlx → result.json
  3. result.json conforms to output.schema.json

  Key insight: The XSD provides type information that enables static analysis of the transformation.

  Scenario 2: JSON Input with JSON Schema Metadata

ame principle:
  Design time: customer.schema.json + transform.utlx → output.xsd
  Runtime: customer.json + transform.utlx → output.xml

  How This Would Work: Type Inference Engine

  Step 1: Build Type Environment from Input Schema

  Given XSD:
  <xs:schema>
    <xs:element name="Order">
      <xs:complexType>
        <xs:attribute name="id" type="xs:string"/>
        <xs:sequence>
          <xs:element name="Customer">
            <xs:complexType>
              <xs:element name="Name" type="xs:string"/>
              <xs:element name="Email" type="xs:string"/>
            </xs:complexType>
          </xs:element>
          <xs:element name="Items">
            <xs:complexType>
              <xs:sequence>
                <xs:element name="Item" maxOccurs="unbounded">
                  <xs:complexType>
                    <xs:attribute name="sku" type="xs:string"/>
                    <xs:attribute name="quantity" type="xs:int"/>
                    <xs:attribute name="price" type="xs:decimal"/>
                  </xs:complexType>
                </xs:element>
              </xs:sequence>
            </xs:complexType>
          </xs:element>
        </xs:sequence>
      </xs:complexType>
    </xs:element>
  </xs:schema>

Type Environment (internal representation):
  input.Order : Object
  input.Order.@id : String
  input.Order.Customer : Object
  input.Order.Customer.Name : String
  input.Order.Customer.Email : String
  input.Order.Items : Object
  input.Order.Items.Item : Array<Object>
  input.Order.Items.Item[].@sku : String
  input.Order.Items.Item[].@quantity : Integer
  input.Order.Items.Item[].@price : Decimal

  Step 2: Analyze UTLX Transformation with Type Inference

  UTLX Script:
  %utlx 1.0
  input xml
  output json
  ---
  {
    invoice: {
      id: "INV-" + input.Order.@id,
      customer: {
        name: input.Order.Customer.Name,
        email: input.Order.Customer.Email
      },
      items: input.Order.Items.Item |> map(item => {
        sku: item.@sku,
        quantity: parseNumber(item.@quantity),
        unitPrice: parseNumber(item.@price),
        total: parseNumber(item.@quantity) * parseNumber(item.@price)
      }),
      summary: {
        itemCount: count(input.Order.Items.Item),
        subtotal: sum(input.Order.Items.Item |> map(item =>
          parseNumber(item.@quantity) * parseNumber(item.@price)
        ))
      }
    }
  }


  1. "INV-" + input.Order.@id
     → String + String = String

  2. input.Order.Customer.Name
     → String (from type environment)

  3. input.Order.Items.Item
     → Array<Object> (from type environment)

  4. input.Order.Items.Item |> map(item => {...})
     → Array<Object> (map preserves array, transforms element type)

  5. parseNumber(item.@quantity)
     → Number (function signature: parseNumber(String|Integer) -> Number)

  6. parseNumber(item.@quantity) * parseNumber(item.@price)
     → Number * Number = Number

  7. count(input.Order.Items.Item)
     → Integer (function signature: count(Array<T>) -> Integer)

  8. sum(...)
     → Number (function signature: sum(Array<Number>) -> Number)

 Inferred Output Type:
  {
    invoice: {
      id: String,
      customer: {
        name: String,
        email: String
      },
      items: Array<{
        sku: String,
        quantity: Number,
        unitPrice: Number,
        total: Number
      }>,
      summary: {
        itemCount: Integer,
        subtotal: Number
      }
    }
  }

  Step 3: Generate Output Schema

Convert to JSON Schema:
  {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "type": "object",
    "properties": {
      "invoice": {
        "type": "object",
        "properties": {
          "id": {"type": "string"},
          "customer": {
            "type": "object",
            "properties": {
              "name": {"type": "string"},
              "email": {"type": "string", "format": "email"}
            },
            "required": ["name", "email"]
          },
          "items": {
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "sku": {"type": "string"},
                "quantity": {"type": "number"},
                "unitPrice": {"type": "number"},
                "total": {"type": "number"}
              },
              "required": ["sku", "quantity", "unitPrice", "total"]
            }
          },
          "summary": {
            "type": "object",
            "properties": {
              "itemCount": {"type": "integer"},
              "subtotal": {"type": "number"}
            },
            "required": ["itemCount", "subtotal"]
          }
        },
        "required": ["id", "customer", "items", "summary"]
      }
    },
    "required": ["invoice"]
  }

 Or convert to XSD (if output format is XML):
  <xs:schema>
    <xs:element name="invoice">
      <xs:complexType>
        <xs:sequence>
          <xs:element name="id" type="xs:string"/>
          <xs:element name="customer">
            <xs:complexType>
              <xs:element name="name" type="xs:string"/>
              <xs:element name="email" type="xs:string"/>
            </xs:complexType>
          </xs:element>
          <xs:element name="items">
            <xs:complexType>
              <xs:sequence>
                <xs:element name="item" maxOccurs="unbounded">
                  <xs:complexType>
                    <xs:element name="sku" type="xs:string"/>
                    <xs:element name="quantity" type="xs:decimal"/>
                    <xs:element name="unitPrice" type="xs:decimal"/>
                    <xs:element name="total" type="xs:decimal"/>
                  </xs:complexType>
                </xs:element>
              </xs:sequence>
            </xs:complexType>
          </xs:element>
          <!-- ... -->
        </xs:sequence>
      </xs:complexType>
    </xs:element>
  </xs:schema>

  Architecture: Design-Time UDM vs Runtime UDM

  Concept: UDM Type System

  Runtime UDM (current):
  sealed class UDM {
      data class Scalar(val value: Any?) : UDM()
      data class Array(val elements: List<UDM>) : UDM()
      data class Object(val properties: Map<String, UDM>) : UDM()
  }

 Design-Time UDM (new - type level):
  sealed class UDMType {
      object StringType : UDMType()
      object NumberType : UDMType()
      object IntegerType : UDMType()
      object BooleanType : UDMType()
      object NullType : UDMType()
      data class ArrayType(val elementType: UDMType) : UDMType()
      data class ObjectType(
          val properties: Map<String, PropertyInfo>,
          val additionalProperties: Boolean = true
      ) : UDMType()
      data class UnionType(val types: Set<UDMType>) : UDMType()  // For nullable, etc.
  }

  data class PropertyInfo(
      val type: UDMType,
      val required: Boolean,
      val minOccurs: Int = 1,
      val maxOccurs: Int = 1  // -1 for unbounded
  )

  Design-Time Analysis Flow


  ┌─────────────────────────────────────────────────────────────────┐
  │                       DESIGN TIME                                │
  │                                                                  │
  │  Input Schema (XSD/JSON Schema)                                 │
  │         ↓                                                        │
  │  Schema Parser → UDMType (Type Environment)                     │
  │         ↓                                                        │
  │  UTLX Parser → AST                                              │
  │         ↓                                                        │
  │  Type Inference Engine                                          │
  │    - Analyze expressions                                        │
  │    - Apply function signatures                                  │
  │    - Propagate types                                            │
  │    - Check for type errors                                      │
  │         ↓                                                        │
  │  Output UDMType                                                 │
  │         ↓                                                        │
  │  Schema Generator → Output Schema (XSD/JSON Schema)             │
  │                                                                  │
  └─────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────────┐
  │                       RUNTIME                                    │
  │                                                                  │
  │  Input Data (XML/JSON)                                          │
  │         ↓                                                        │
  │  Format Parser → UDM (Actual data)                              │
  │         ↓                                                        │
  │  UTLX Runtime Executor                                          │
  │    - Evaluate expressions                                       │
  │    - Call functions                                             │
  │    - Build output UDM                                           │
  │         ↓                                                        │
  │  Output UDM                                                     │
  │         ↓                                                        │
  │  Format Serializer → Output Data (XML/JSON)                     │
  │                                                                  │
  └─────────────────────────────────────────────────────────────────┘

 Implementation Requirements

  1. Schema to Type Environment

  class SchemaAnalyzer {
      fun buildTypeEnvironment(schema: Schema): TypeEnvironment {
          return when (schema) {
              is XSDSchema -> buildFromXSD(schema)
              is JSONSchema -> buildFromJSONSchema(schema)
              is AvroSchema -> buildFromAvro(schema)
          }
      }

      private fun buildFromXSD(xsd: XSDSchema): TypeEnvironment {
          val env = TypeEnvironment()

          // Map XSD elements to UDMType
          xsd.elements.forEach { element ->
              val path = "input.${element.name}"
              val type = convertXSDTypeToUDMType(element.type)
              env.bind(path, type)
          }

          return env
      }

      private fun convertXSDTypeToUDMType(xsdType: XSDType): UDMType {
          return when (xsdType) {
              is XSDSimpleType -> when (xsdType.name) {
                  "xs:string" -> UDMType.StringType
                  "xs:int", "xs:integer" -> UDMType.IntegerType
                  "xs:decimal", "xs:double" -> UDMType.NumberType
                  "xs:boolean" -> UDMType.BooleanType
                  else -> UDMType.StringType
              }
              is XSDComplexType -> {
                  val properties = xsdType.elements.associate { elem ->
                      elem.name to PropertyInfo(
                          type = convertXSDTypeToUDMType(elem.type),
                          required = elem.minOccurs > 0,
                          minOccurs = elem.minOccurs,
                          maxOccurs = elem.maxOccurs
                      )
                  }
                  UDMType.ObjectType(properties)
              }
          }
      }
  }

 2. Function Type Signatures

  class FunctionRegistry {
      private val signatures = mapOf(
          "parseNumber" to FunctionSignature(
              params = listOf(UDMType.StringType),
              returnType = UDMType.NumberType
          ),
          "count" to FunctionSignature(
              params = listOf(UDMType.ArrayType(UDMType.AnyType)),
              returnType = UDMType.IntegerType
          ),
          "sum" to FunctionSignature(
              params = listOf(UDMType.ArrayType(UDMType.NumberType)),
              returnType = UDMType.NumberType
          ),
          "map" to FunctionSignature(
              params = listOf(
                  UDMType.ArrayType(UDMType.GenericType("T")),
                  UDMType.FunctionType(
                      from = UDMType.GenericType("T"),
                      to = UDMType.GenericType("R")
                  )
              ),
              returnType = UDMType.ArrayType(UDMType.GenericType("R"))
          )
          // ... all stdlib functions
      )
  }

3. Type Inference Engine

  class TypeInferenceEngine(
      val typeEnv: TypeEnvironment,
      val functionRegistry: FunctionRegistry
  ) {
      fun inferType(expr: Expression): UDMType {
          return when (expr) {
              is LiteralExpr -> inferLiteral(expr)
              is PathExpr -> inferPath(expr)
              is BinaryOpExpr -> inferBinaryOp(expr)
              is FunctionCallExpr -> inferFunctionCall(expr)
              is MapExpr -> inferMap(expr)
              is ObjectConstructorExpr -> inferObjectConstructor(expr)
              is ArrayConstructorExpr -> inferArrayConstructor(expr)
              // ... other expression types
          }
      }

      private fun inferPath(expr: PathExpr): UDMType {
          // Look up in type environment
          return typeEnv.lookup(expr.path) ?: UDMType.AnyType
      }

      private fun inferFunctionCall(expr: FunctionCallExpr): UDMType {
          val signature = functionRegistry.lookup(expr.functionName)
          val argTypes = expr.arguments.map { inferType(it) }

          // Type check arguments
          if (!signature.matchesArgs(argTypes)) {
              throw TypeMismatchException(
                  "Function ${expr.functionName} expects ${signature.params} but got $argTypes"
              )
          }

          // Return type (with generic substitution if needed)
          return signature.returnType.substitute(/* generic mappings */)
      }

      private fun inferObjectConstructor(expr: ObjectConstructorExpr): UDMType {
          val properties = expr.properties.mapValues { (_, valueExpr) ->
              PropertyInfo(
                  type = inferType(valueExpr),
                  required = true  // All explicitly constructed fields are required
              )
          }
          return UDMType.ObjectType(properties)
      }
  }

 4. CLI Integration

  # Design-time analysis
  utlx schema analyze \
    --input-schema order.xsd \
    --transform transform.utlx \
    --output-schema invoice.schema.json

  # Output: Generates invoice.schema.json (JSON Schema for the output)

  # With validation
  utlx schema analyze \
    --input-schema order.xsd \
    --transform transform.utlx \
    --output-schema invoice.schema.json \
    --expected-output expected-invoice.schema.json  # Validate against expected

  # Output: 
  # ✓ Transformation type-checks successfully
  # ✓ Output schema matches expected schema
  # Generated: invoice.schema.json

  Benefits of Design-Time Analysis

  1. Early Error Detection

  {
    total: input.Order.Items.Item |> sum(item => item.@price)
  }

  // Design-time error:
  // Type Error: sum expects Array<Number> but got Array<String>
  // item.@price is type String (from XSD)
  // Suggestion: Use parseNumber(item.@price)

 2. Contract Validation

  # Verify transformation produces expected output schema
  utlx schema validate-contract \
    --input-schema api-request.schema.json \
    --transform request-handler.utlx \
    --expected-output api-response.schema.json

  # Output:
  # ✗ Contract violation:
  #   Expected: response.status: Integer
  #   Got: response.status: String
  #   Location: line 15, column 5

  3. Documentation Generation

  The output schema serves as executable documentation of what the transformation produces.

  4. Integration with API Design Tools

  ec-api-design (generates XSD)
         ↓
     order.xsd
         ↓
     UTL-X design-time analysis + transform.utlx
         ↓
     invoice.schema.json
         ↓
     API Gateway configuration

 5. IDE Support

  With type information, IDEs can provide:
  - Autocomplete: input.Order. → suggests @id, Customer, Items
  - Type hints: Hover over expression shows inferred type
  - Error highlighting: Red squiggly under type errors
  - Refactoring: Safe rename of fields

  Challenges and Limitations

  1. Dynamic Expressions

  {
    // Conditional types - output schema varies
    result: if (input.type == "premium")
      then { level: "gold", discount: 0.2 }
      else { level: "standard" }
  }

  // Output type: Union type
  // { level: String, discount?: Number }

  Solution: Use union types in output schema

  2. Data-Dependent Logic

  {
    // Output structure depends on data
    fields: input.fieldNames |> map(name => {
      [name]: input.values[name]
    })
  }

  Solution: Conservative approximation - Object with additional properties: Any

3. External Data Sources

  {
    // Fetching external data
    enriched: fetchCustomerData(input.customerId)
  }

  Solution: Require type annotations or external schema

  4. Incomplete Type Information

  If input schema is not provided, fall back to:
  - Runtime inference: Use sample data
  - Type annotations: Allow user to specify types
  - Gradual typing: Mix static and dynamic checking

  Recommendation

  This capability should be Phase 0 of the analysis module implementation - it's foundational for everything else:

  Phase 0: Type System & Design-Time Analysis (4-6 weeks)
  ├── UDMType representation
  ├── Schema → Type Environment
  ├── Function type signatures
  ├── Type inference engine
  ├── Type → Schema generation
  └── CLI integration

  Phase 1: JSON Schema Inference (builds on Phase 0)
  Phase 2: Avro Schema Inference (builds on Phase 0)
  Phase 3: Schema Validation (uses Phase 0 types)


