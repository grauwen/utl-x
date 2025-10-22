# Three-Phase JVM Runtime Architecture: Validation-First with Copy-Based Instantiation

**Document Version:** 1.0 (Alternative Design)
**Last Updated:** 2025-10-22
**Status:** Design Proposal
**Target Release:** UTL-X v2.0

**Design Philosophy:** Inspired by Tibco BusinessWorks and traditional middleware approaches

---

## Executive Summary

### The Middleware Philosophy

This document presents an **alternative architectural approach** for the UTL-X three-phase runtime, inspired by proven middleware solutions like **Tibco BusinessWorks**, **IBM Integration Bus (IIB)**, and **Oracle Service Bus (OSB)**.

**Key Philosophical Differences from Previous Design:**

| Aspect | Template-Based Approach | **Copy-Based Approach (This Document)** |
|--------|-------------------------|------------------------------------------|
| **Init-time creation** | Generate templates from schema | **Build complete UDM model structure** |
| **Runtime instantiation** | Fill template with data | **Copy pre-built model and populate** |
| **Validation strategy** | Optional, after parsing | **Mandatory first step (when schema available)** |
| **Invalid messages** | Parse anyway, transform | **Can still process after validation failure** |
| **Memory model** | Template + data → UDM | **Pre-built UDM skeleton → copy → fill** |

### The Validation-First Principle

**In middleware solutions, validation is NOT optional - it's the first line of defense:**

```
┌────────────────────────────────────────────────────────┐
│  1. VALIDATE INPUT (if schema available)               │
│     - XML + XSD → javax.xml.validation                 │
│     - JSON + JSON Schema → validation                  │
│     - CSV + metadata → structure check                 │
│                                                         │
│     Result: VALID or INVALID (with errors)            │
│     Action: ALWAYS continue to transformation          │
│             (middleware must handle invalid messages)  │
└────────────────────────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────────────────────────┐
│  2. COPY PRE-BUILT UDM MODEL                          │
│     - At init-time: Built complete UDM structure      │
│     - At runtime: Fast memory copy (not creation)     │
│     - No parsing, no schema inference                 │
└────────────────────────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────────────────────────┐
│  3. FILL COPIED MODEL WITH DATA                       │
│     - Direct assignment to pre-allocated nodes        │
│     - No dynamic structure creation                   │
│     - Predictable memory layout                       │
└────────────────────────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────────────────────────┐
│  4. EXECUTE TRANSFORMATION                            │
│     - Transform on pre-structured UDM                 │
│     - All paths already resolved at init-time         │
└────────────────────────────────────────────────────────┘
```

### The Tibco BusinessWorks Model

**How Tibco BW Works:**

```
┌─────────────────────────────────────────────────────────┐
│  DESIGN TIME (BusinessWorks Designer)                   │
├─────────────────────────────────────────────────────────┤
│  - Create process definition (.process file)           │
│  - Define input/output schemas (XSD)                   │
│  - Configure activities (mappers, validators)          │
│  - Bundle into EAR (zipped configurations)             │
│                                                         │
│  Output: EAR file (deployment artifact)                │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  INIT TIME (BW Engine Startup)                         │
├─────────────────────────────────────────────────────────┤
│  - Load EAR file                                        │
│  - Parse XSD schemas                                    │
│  - Build DOM model structure for each schema           │
│  - Pre-compile XPath expressions                       │
│  - Initialize validators (javax.xml.validation)        │
│  - Prepare thread pools and memory                     │
│                                                         │
│  Output: Ready-to-run engine with pre-built models     │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  RUNTIME (Message Processing)                          │
├─────────────────────────────────────────────────────────┤
│  FOR EACH MESSAGE:                                      │
│                                                         │
│  1. VALIDATE (if XSD available)                        │
│     validator.validate(new StreamSource(xmlString))    │
│     → VALID or INVALID (continue anyway)               │
│                                                         │
│  2. COPY PRE-BUILT DOM                                 │
│     newDoc = preBuildDoc.cloneNode(deep=true)          │
│     → Fast memory copy, not parsing                    │
│                                                         │
│  3. FILL DOM WITH DATA                                 │
│     Direct assignment to known nodes                   │
│                                                         │
│  4. EXECUTE MAPPING                                    │
│     Pre-compiled XPath expressions                     │
│                                                         │
│  Total: 5-15ms per message (very fast!)               │
└─────────────────────────────────────────────────────────┘
```

**UTL-X Equivalent (This Design):**

```kotlin
// DESIGN TIME
val compiled = compiler.compile("""
    %utlx 1.0
    input xml schema order-v1.xsd
    output json schema invoice-v1.json
    ---
    { /* transformation */ }
""")

// INIT TIME
val executor = compiled.initialize(
    inputXSD = File("order-v1.xsd"),
    outputJSONSchema = File("invoice-v1.json")
)
// Result: Pre-built UDM model + validator

// RUNTIME
for (message in messages) {
    // 1. Validate (mandatory step, but continue anyway)
    val validationResult = executor.validate(message)
    if (!validationResult.isValid) {
        logger.warn("Invalid message: ${validationResult.errors}")
        // Still continue - middleware must handle invalid messages
    }

    // 2. Copy pre-built UDM (fast!)
    val udmCopy = executor.copyPreBuiltModel()

    // 3. Fill with data
    executor.fillModel(udmCopy, message)

    // 4. Transform
    val result = executor.transform(udmCopy)
}
```

---

## Table of Contents

1. [Validation-First Architecture](#1-validation-first-architecture)
2. [Design-Time Phase](#2-design-time-phase)
3. [Init-Time Phase](#3-init-time-phase)
4. [Runtime Phase](#4-runtime-phase)
5. [Copy-Based UDM Instantiation](#5-copy-based-udm-instantiation)
6. [Schema Validation Integration](#6-schema-validation-integration)
7. [Handling Invalid Messages](#7-handling-invalid-messages)
8. [Pre-Built UDM Model Structure](#8-pre-built-udm-model-structure)
9. [Memory Management](#9-memory-management)
10. [Implementation Architecture](#10-implementation-architecture)
11. [Performance Analysis](#11-performance-analysis)
12. [Comparison: Template vs Copy-Based](#12-comparison-template-vs-copy-based)
13. [Middleware Integration Patterns](#13-middleware-integration-patterns)
14. [Implementation Roadmap](#14-implementation-roadmap)
15. [Migration from Current Architecture](#15-migration-from-current-architecture)

---

## 1. Validation-First Architecture

### 1.1 Core Principle: Validate Before Transform

**Middleware Golden Rule:** Always validate input before transformation (when schema is available).

**Why Validation-First?**

1. **Early Error Detection** - Catch malformed messages before expensive processing
2. **Security** - Prevent malicious payloads (XXE, injection attacks)
3. **Data Quality** - Ensure downstream systems receive valid data
4. **Debugging** - Clear error messages at validation stage
5. **Contract Enforcement** - Guarantee message adheres to agreed schema

**BUT: Invalid messages must still be processable!**

Middleware must handle:
- Messages without schemas
- Malformed messages (for error transformation/logging)
- Schema evolution (messages don't match current schema)
- Legacy systems (non-validating sources)

### 1.2 Validation Pipeline

```
┌─────────────────────────────────────────────────────────┐
│  Input Message                                          │
└────────────────────┬────────────────────────────────────┘
                     ▼
        ┌────────────────────────┐
        │  Schema Available?     │
        └────────┬───────────────┘
                 │
         ┌───────┴───────┐
         │               │
        YES              NO
         │               │
         ▼               ▼
┌────────────────┐  ┌──────────────┐
│  VALIDATE      │  │  SKIP        │
│  (javax.xml.   │  │  VALIDATION  │
│   validation,  │  │              │
│   JSON Schema) │  │              │
└────────┬───────┘  └──────┬───────┘
         │                 │
         ▼                 ▼
   ┌──────────┐      ┌──────────┐
   │  VALID?  │      │          │
   └────┬─────┘      │          │
        │            │          │
    ┌───┴────┐      │          │
    │        │      │          │
   YES      NO      │          │
    │        │      │          │
    │   ┌────┴──────┴──────────┘
    │   │
    ▼   ▼
┌──────────────────────────────┐
│  Log Validation Result       │
│  (valid, invalid, skipped)   │
└────────────┬─────────────────┘
             ▼
┌──────────────────────────────┐
│  COPY PRE-BUILT UDM MODEL    │
│  (regardless of validation)  │
└────────────┬─────────────────┘
             ▼
┌──────────────────────────────┐
│  FILL MODEL WITH DATA        │
└────────────┬─────────────────┘
             ▼
┌──────────────────────────────┐
│  TRANSFORM                   │
└──────────────────────────────┘
```

### 1.3 Validation Result Handling

```kotlin
/**
 * Validation result (always produced, even if skipped)
 */
sealed class ValidationResult {
    /**
     * Message is valid according to schema
     */
    object Valid : ValidationResult()

    /**
     * Message is invalid
     */
    data class Invalid(
        val errors: List<ValidationError>
    ) : ValidationResult()

    /**
     * Validation skipped (no schema available)
     */
    data class Skipped(
        val reason: String
    ) : ValidationResult()
}

data class ValidationError(
    val line: Int,
    val column: Int,
    val message: String,
    val severity: Severity
)

enum class Severity {
    ERROR,      // Schema violation
    WARNING,    // Deprecated field, missing optional
    INFO        // Additional information
}

/**
 * Validation policy determines what to do with invalid messages
 */
enum class ValidationPolicy {
    /**
     * Stop processing on invalid message
     * Throw exception, send to error queue
     */
    STRICT,

    /**
     * Log warning, continue processing
     * This is the MIDDLEWARE DEFAULT
     */
    WARN_AND_CONTINUE,

    /**
     * Silently continue (not recommended)
     */
    SILENT,

    /**
     * Custom handler
     */
    CUSTOM
}
```

### 1.4 Why Validation Must Not Block Transformation

**Real-World Middleware Scenarios:**

1. **Schema Evolution:**
   ```
   Producer sends message with new field (v2 schema)
   Consumer expects old schema (v1)
   → Validation fails, but transformation should still work
   ```

2. **Error Transformation:**
   ```
   Invalid message arrives
   → Must transform to error format for error handling system
   → Cannot skip transformation!
   ```

3. **Legacy Integration:**
   ```
   Old system sends malformed XML
   → Validation fails (missing namespace, wrong structure)
   → Still need to extract what we can and send to modern system
   ```

4. **Debugging/Logging:**
   ```
   Unknown message format
   → Validation impossible (no schema)
   → Transform to logging format for analysis
   ```

---

## 2. Design-Time Phase

### 2.1 Schema Declaration in UTL-X

**New Syntax: Declare schemas in UTL-X header**

```utlx
%utlx 1.0
input xml schema "order-v1.xsd"
output json schema "invoice-v1.json"
validation policy WARN_AND_CONTINUE
---
{
    invoiceId: "INV-" + input.Order.@id,
    /* ... transformation ... */
}
```

**Alternative: External schema files**

```utlx
%utlx 1.0
input xml schema file("schemas/order-v1.xsd")
output json schema file("schemas/invoice-v1.json")
---
/* transformation */
```

**For messages without schemas:**

```utlx
%utlx 1.0
input xml     # No schema - validation skipped
output json   # No schema - no validation
---
/* transformation */
```

### 2.2 Schema Validation During Compilation

**Design-time validation ensures transformation matches schemas:**

```kotlin
/**
 * Design-time compiler with schema awareness
 */
class SchemaAwareCompiler {
    fun compile(source: String): CompiledTransform {
        // Parse UTL-X
        val ast = parser.parse(source)

        // Extract schema declarations
        val inputSchema = extractInputSchema(ast.header)
        val outputSchema = extractOutputSchema(ast.header)

        // Validate transformation against schemas
        if (inputSchema != null) {
            validateInputPaths(ast, inputSchema)
        }

        if (outputSchema != null) {
            validateOutputStructure(ast, outputSchema)
        }

        // Compile
        return compile(ast, inputSchema, outputSchema)
    }

    /**
     * Validate that all input paths exist in schema
     */
    private fun validateInputPaths(
        ast: Program,
        schema: Schema
    ) {
        val accessedPaths = PathAnalyzer().findAllPaths(ast)

        for (path in accessedPaths) {
            if (!schema.hasPath(path)) {
                throw CompilationError(
                    "Path '$path' does not exist in input schema"
                )
            }
        }
    }

    /**
     * Validate that output matches declared schema
     */
    private fun validateOutputStructure(
        ast: Program,
        schema: Schema
    ) {
        val outputStructure = OutputAnalyzer().analyze(ast)

        if (!schema.matches(outputStructure)) {
            throw CompilationError(
                "Output structure does not match declared schema"
            )
        }
    }
}
```

**Example Compilation Error:**

```
Error: Input path validation failed
  --> transform.utlx:12:15
   |
12 |     customer: input.Order.Client.Name
   |               ^^^^^^^^^^^^^^^^^^^^^^^ Path 'Order.Client.Name' not found
   |
Note: Schema defines 'Order.Customer.Name', not 'Order.Client.Name'
Help: Did you mean 'input.Order.Customer.Name'?
```

### 2.3 Compiled Transform with Schema Metadata

```kotlin
/**
 * Compiled transform with embedded schemas
 */
data class SchemaAwareCompiledTransform(
    val bytecode: ByteArray,
    val metadata: TransformMetadata,

    /** Input schema (XSD, JSON Schema, or inferred) */
    val inputSchema: Schema?,

    /** Output schema (JSON Schema, XSD, or inferred) */
    val outputSchema: Schema?,

    /** Validation policy */
    val validationPolicy: ValidationPolicy
) : CompiledTransform {

    override fun createExecutor(config: ExecutorConfig): TransformExecutor {
        return CopyBasedExecutor(
            transform = this,
            config = config
        )
    }
}

/**
 * Schema representation (unified for XSD, JSON Schema, etc.)
 */
sealed class Schema {
    abstract fun hasPath(path: String): Boolean
    abstract fun getNodeType(path: String): SchemaType?
    abstract fun buildUDMStructure(): UDM

    data class XMLSchema(
        val xsd: javax.xml.validation.Schema,
        val rootElement: QName,
        val structure: ElementDefinition
    ) : Schema()

    data class JSONSchema(
        val schema: org.everit.json.schema.Schema,
        val structure: JSONSchemaDefinition
    ) : Schema()

    data class CSVSchema(
        val columns: List<ColumnDefinition>
    ) : Schema()
}
```

---

## 3. Init-Time Phase

### 3.1 Init-Time Objectives

**At init-time (engine startup), we:**

1. **Load and parse schemas** (XSD, JSON Schema)
2. **Build validator instances** (javax.xml.validation.Validator, JSON Schema validator)
3. **Construct complete UDM model structure** from schema
4. **Pre-compile all XPath-like expressions** to direct access paths
5. **Initialize memory structures** for fast copying

**Result: Ready-to-run executor with pre-built UDM model**

### 3.2 Validator Initialization

#### 3.2.1 XML + XSD Validator

```kotlin
/**
 * Initialize XML validator from XSD
 */
class XMLValidatorFactory {
    fun create(xsdPath: String): XMLValidator {
        // Load XSD schema
        val schemaFactory = SchemaFactory.newInstance(
            XMLConstants.W3C_XML_SCHEMA_NS_URI
        )

        val schema = schemaFactory.newSchema(File(xsdPath))

        // Create validator instance (thread-safe)
        val validator = schema.newValidator()

        // Configure error handler
        validator.errorHandler = ValidationErrorHandler()

        return XMLValidator(validator)
    }
}

/**
 * XML validator wrapper
 */
class XMLValidator(
    private val validator: javax.xml.validation.Validator
) {
    /**
     * Validate XML message
     * Returns validation result (does NOT throw)
     */
    fun validate(xml: String): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        try {
            // Create error collector
            val errorHandler = CollectingErrorHandler(errors)

            // Validate (synchronized - javax.xml.validation.Validator is not thread-safe)
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

/**
 * Error handler that collects errors instead of throwing
 */
class CollectingErrorHandler(
    private val errors: MutableList<ValidationError>
) : ErrorHandler {

    override fun warning(exception: SAXParseException) {
        errors.add(ValidationError(
            line = exception.lineNumber,
            column = exception.columnNumber,
            message = exception.message ?: "",
            severity = Severity.WARNING
        ))
    }

    override fun error(exception: SAXParseException) {
        errors.add(ValidationError(
            line = exception.lineNumber,
            column = exception.columnNumber,
            message = exception.message ?: "",
            severity = Severity.ERROR
        ))
    }

    override fun fatalError(exception: SAXParseException) {
        errors.add(ValidationError(
            line = exception.lineNumber,
            column = exception.columnNumber,
            message = exception.message ?: "",
            severity = Severity.ERROR
        ))
        // Don't throw - collect and continue
    }
}
```

#### 3.2.2 JSON Schema Validator

```kotlin
/**
 * Initialize JSON Schema validator
 */
class JSONSchemaValidatorFactory {
    fun create(schemaPath: String): JSONSchemaValidator {
        // Load JSON Schema
        val schemaJson = File(schemaPath).readText()

        // Parse using everit-org/json-schema library
        val rawSchema = JSONObject(JSONTokener(schemaJson))
        val schema = SchemaLoader.load(rawSchema)

        return JSONSchemaValidator(schema)
    }
}

/**
 * JSON Schema validator wrapper
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
            // Collect all validation errors
            e.causingExceptions.forEach { cause ->
                errors.add(ValidationError(
                    line = 0,  // JSON Schema doesn't provide line numbers
                    column = 0,
                    message = "${cause.pointerToViolation}: ${cause.message}",
                    severity = Severity.ERROR
                ))
            }

            if (errors.isEmpty()) {
                // Single error
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

### 3.3 Pre-Built UDM Model Construction

**Core Innovation: Build entire UDM structure at init-time from schema**

```kotlin
/**
 * Build complete UDM model structure from schema
 * This is the "DOM model" that gets copied at runtime
 */
class UDMModelBuilder {

    /**
     * Build UDM model from XML Schema (XSD)
     */
    fun buildFromXSD(schema: Schema.XMLSchema): UDM {
        return buildElement(schema.structure)
    }

    /**
     * Build UDM model from JSON Schema
     */
    fun buildFromJSONSchema(schema: Schema.JSONSchema): UDM {
        return buildFromJSONDefinition(schema.structure)
    }

    /**
     * Recursively build UDM structure from XSD element definition
     */
    private fun buildElement(element: ElementDefinition): UDM {
        return when (element.type) {
            is XMLType.Simple -> {
                // Scalar element
                UDM.Scalar(getDefaultValue(element.type.scalarType))
            }

            is XMLType.Complex -> {
                // Complex element with children
                val properties = element.children.associate { child ->
                    child.name to buildElement(child)
                }

                val attributes = element.attributes.mapValues { (_, attr) ->
                    getDefaultValue(attr.type)?.toString() ?: ""
                }

                UDM.Object(
                    properties = properties,
                    attributes = attributes,
                    name = element.name
                )
            }
        }
    }

    /**
     * Get default value for scalar type
     */
    private fun getDefaultValue(type: ScalarType): Any? {
        return when (type) {
            ScalarType.STRING -> ""
            ScalarType.NUMBER -> 0.0
            ScalarType.BOOLEAN -> false
            ScalarType.NULL -> null
            ScalarType.ANY -> null
        }
    }
}

/**
 * Example: Build UDM from XSD
 */
// Given XSD:
// <xs:element name="Order">
//   <xs:complexType>
//     <xs:sequence>
//       <xs:element name="OrderId" type="xs:string"/>
//       <xs:element name="Customer">
//         <xs:complexType>
//           <xs:sequence>
//             <xs:element name="Name" type="xs:string"/>
//             <xs:element name="Email" type="xs:string"/>
//           </xs:sequence>
//         </xs:complexType>
//       </xs:element>
//     </xs:sequence>
//     <xs:attribute name="id" type="xs:string"/>
//   </xs:complexType>
// </xs:element>

// Built UDM structure:
val preBuildModel = UDM.Object(
    properties = mapOf(
        "OrderId" to UDM.Scalar(""),
        "Customer" to UDM.Object(
            properties = mapOf(
                "Name" to UDM.Scalar(""),
                "Email" to UDM.Scalar("")
            )
        )
    ),
    attributes = mapOf(
        "id" to ""
    ),
    name = "Order"
)
```

### 3.4 Pre-Built Model Characteristics

**The pre-built UDM model:**

1. **Complete structure** - All elements/properties present
2. **Default values** - Empty strings, zeros, nulls
3. **Immutable** - Never modified, only copied
4. **Memory-efficient** - Single instance, shared across all runtime copies
5. **Fast to copy** - Simple deep clone operation

**Memory Layout:**

```
┌──────────────────────────────────────────────────┐
│  Pre-Built UDM Model (Init-Time)                 │
│  ┌────────────────────────────────────────┐     │
│  │  UDM.Object("Order")                   │     │
│  │    attributes: {id: ""}                │     │
│  │    properties:                         │     │
│  │      OrderId: UDM.Scalar("")           │     │
│  │      Customer: UDM.Object()            │     │
│  │        properties:                     │     │
│  │          Name: UDM.Scalar("")          │     │
│  │          Email: UDM.Scalar("")         │     │
│  └────────────────────────────────────────┘     │
│                                                  │
│  Stored in memory: ~500 bytes                   │
│  Never modified                                 │
│  Copied for each message (fast!)               │
└──────────────────────────────────────────────────┘
```

---

## 4. Runtime Phase

### 4.1 Runtime Message Processing Pipeline

```
┌─────────────────────────────────────────────────────────┐
│  STEP 1: VALIDATE INPUT (if schema available)           │
├─────────────────────────────────────────────────────────┤
│  Input: XML/JSON/CSV string                             │
│  Process: validator.validate(input)                     │
│  Time: 1-5ms                                            │
│  Output: ValidationResult (Valid/Invalid/Skipped)       │
│  Action: Log result, ALWAYS continue                    │
└────────────────────────┬────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────┐
│  STEP 2: COPY PRE-BUILT UDM MODEL                      │
├─────────────────────────────────────────────────────────┤
│  Input: Pre-built UDM model (from init-time)           │
│  Process: udmCopy = preBuiltModel.deepCopy()           │
│  Time: 0.5-2ms (much faster than parsing!)             │
│  Output: Empty UDM structure (ready to fill)           │
└────────────────────────┬────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────┐
│  STEP 3: FILL UDM WITH MESSAGE DATA                    │
├─────────────────────────────────────────────────────────┤
│  Input: udmCopy + message data                         │
│  Process: Direct assignment to known paths             │
│  Time: 2-5ms                                           │
│  Output: Populated UDM model                           │
└────────────────────────┬────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────┐
│  STEP 4: EXECUTE TRANSFORMATION                        │
├─────────────────────────────────────────────────────────┤
│  Input: Populated UDM                                  │
│  Process: Execute compiled transformation              │
│  Time: 3-10ms                                          │
│  Output: Result UDM                                    │
└────────────────────────┬────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────┐
│  STEP 5: SERIALIZE OUTPUT                              │
├─────────────────────────────────────────────────────────┤
│  Input: Result UDM                                     │
│  Process: Serialize to JSON/XML/CSV                    │
│  Time: 2-5ms                                           │
│  Output: Output string                                 │
└─────────────────────────────────────────────────────────┘

Total: 8.5-27ms per message
```

### 4.2 Copy-Based Executor Implementation

```kotlin
/**
 * Copy-based executor (Tibco BW-style)
 */
class CopyBasedExecutor(
    private val transform: SchemaAwareCompiledTransform,
    private val config: ExecutorConfig
) : TransformExecutor {

    // Init-time components
    private val inputValidator: Validator?
    private val outputValidator: Validator?
    private val preBuiltInputModel: UDM
    private val preBuiltOutputModel: UDM?

    init {
        // Initialize validators
        inputValidator = transform.inputSchema?.let {
            createValidator(it)
        }

        outputValidator = transform.outputSchema?.let {
            createValidator(it)
        }

        // Build pre-built UDM models
        preBuiltInputModel = transform.inputSchema?.let {
            UDMModelBuilder().build(it)
        } ?: UDM.Object.empty()

        preBuiltOutputModel = transform.outputSchema?.let {
            UDMModelBuilder().build(it)
        }
    }

    /**
     * Transform single message
     */
    override fun transform(input: String): String {
        // STEP 1: Validate
        val validationResult = validate(input)
        handleValidationResult(validationResult)

        // STEP 2: Copy pre-built model
        val inputUDM = preBuiltInputModel.deepCopy()

        // STEP 3: Fill with data
        fillModel(inputUDM, input)

        // STEP 4: Transform
        val outputUDM = transform.execute(inputUDM)

        // STEP 5: Serialize
        val output = serialize(outputUDM)

        // Optional: Validate output
        if (outputValidator != null && config.validateOutput) {
            val outputValidation = outputValidator.validate(output)
            handleValidationResult(outputValidation)
        }

        return output
    }

    /**
     * Validate input message
     */
    private fun validate(input: String): ValidationResult {
        return if (inputValidator != null) {
            inputValidator.validate(input)
        } else {
            ValidationResult.Skipped("No schema available")
        }
    }

    /**
     * Handle validation result according to policy
     */
    private fun handleValidationResult(result: ValidationResult) {
        when (result) {
            is ValidationResult.Valid -> {
                logger.debug("Validation passed")
            }

            is ValidationResult.Invalid -> {
                when (transform.validationPolicy) {
                    ValidationPolicy.STRICT -> {
                        throw ValidationException(result.errors)
                    }

                    ValidationPolicy.WARN_AND_CONTINUE -> {
                        logger.warn("Validation failed: ${result.errors}")
                        // Continue processing
                    }

                    ValidationPolicy.SILENT -> {
                        // Do nothing
                    }

                    ValidationPolicy.CUSTOM -> {
                        config.customValidationHandler?.handle(result)
                    }
                }
            }

            is ValidationResult.Skipped -> {
                logger.debug("Validation skipped: ${result.reason}")
            }
        }
    }

    /**
     * Fill UDM model with message data
     */
    private fun fillModel(udm: UDM, data: String) {
        when (transform.metadata.inputFormat) {
            Format.XML -> fillFromXML(udm, data)
            Format.JSON -> fillFromJSON(udm, data)
            Format.CSV -> fillFromCSV(udm, data)
            else -> throw UnsupportedOperationException()
        }
    }

    /**
     * Fill UDM from XML data
     */
    private fun fillFromXML(udm: UDM, xml: String) {
        // Parse XML to DOM
        val doc = parseXMLToDOM(xml)

        // Fill UDM from DOM
        fillFromDOM(udm, doc.documentElement)
    }

    /**
     * Fill UDM from DOM node
     */
    private fun fillFromDOM(udm: UDM, node: org.w3c.dom.Element) {
        when (udm) {
            is UDM.Object -> {
                // Fill attributes
                val attrs = node.attributes
                for (i in 0 until attrs.length) {
                    val attr = attrs.item(i) as org.w3c.dom.Attr
                    (udm.attributes as MutableMap)[attr.name] = attr.value
                }

                // Fill properties (child elements)
                val children = node.childNodes
                for (i in 0 until children.length) {
                    val child = children.item(i)
                    if (child is org.w3c.dom.Element) {
                        val childUDM = udm.properties[child.localName]
                        if (childUDM != null) {
                            fillFromDOM(childUDM, child)
                        }
                    }
                }
            }

            is UDM.Scalar -> {
                // Fill scalar value
                val value = node.textContent
                (udm as MutableUDM).setValue(parseScalar(value, udm.type))
            }

            else -> { /* Handle other types */ }
        }
    }
}
```

---

## 5. Copy-Based UDM Instantiation

### 5.1 Deep Copy Implementation

**The core of this architecture: Fast deep copying of pre-built UDM**

```kotlin
/**
 * UDM deep copy extension
 * Optimized for copy-based instantiation
 */
fun UDM.deepCopy(): UDM {
    return when (this) {
        is UDM.Scalar -> {
            // Scalars are immutable - can reuse
            UDM.Scalar(this.value)
        }

        is UDM.Array -> {
            // Copy array and all elements
            val copiedElements = this.elements.map { it.deepCopy() }
            UDM.Array(copiedElements.toMutableList())
        }

        is UDM.Object -> {
            // Copy object structure
            val copiedProperties = this.properties.mapValues { (_, value) ->
                value.deepCopy()
            }.toMutableMap()

            val copiedAttributes = this.attributes.toMutableMap()

            UDM.Object(
                properties = copiedProperties,
                attributes = copiedAttributes,
                name = this.name
            )
        }

        is UDM.DateTime -> UDM.DateTime(this.instant)
        is UDM.Date -> UDM.Date(this.date)
        is UDM.LocalDateTime -> UDM.LocalDateTime(this.dateTime)
        is UDM.Time -> UDM.Time(this.time)
        is UDM.Binary -> UDM.Binary(this.data.copyOf())
        is UDM.Lambda -> this  // Lambdas are immutable
    }
}

/**
 * Mutable UDM marker interface
 * Allows in-place modification during filling
 */
interface MutableUDM {
    fun setValue(value: Any?)
}

/**
 * Make UDM.Scalar mutable for filling
 */
data class MutableScalar(var value: Any?) : UDM.Scalar(value), MutableUDM {
    override fun setValue(newValue: Any?) {
        value = newValue
    }
}

/**
 * Make UDM.Object mutable for filling
 */
data class MutableObject(
    override val properties: MutableMap<String, UDM>,
    override val attributes: MutableMap<String, String>,
    override val name: String? = null
) : UDM.Object(properties, attributes, name), MutableUDM {
    override fun setValue(value: Any?) {
        // Objects don't have direct values
    }
}
```

### 5.2 Copy Performance Optimization

**Optimization 1: Structural Sharing (Where Possible)**

```kotlin
/**
 * Optimized deep copy with structural sharing
 * Immutable parts are shared, mutable parts are copied
 */
fun UDM.optimizedCopy(): UDM {
    return when (this) {
        is UDM.Scalar -> {
            // Scalars are immutable - SHARE
            this
        }

        is UDM.Object -> {
            // Copy structure, but share immutable children initially
            val copiedProperties = this.properties.toMutableMap()
            val copiedAttributes = this.attributes.toMutableMap()

            MutableObject(
                properties = copiedProperties,
                attributes = copiedAttributes,
                name = this.name
            )
        }

        // ... other types
    }
}
```

**Optimization 2: Object Pooling for Copies**

```kotlin
/**
 * Pool of UDM copies
 * Reuse copied models across messages
 */
class UDMCopyPool(
    private val preBuildModel: UDM,
    private val poolSize: Int = 100
) {
    private val pool = ConcurrentLinkedQueue<UDM>()

    init {
        // Pre-create copies
        repeat(poolSize) {
            pool.offer(preBuildModel.deepCopy())
        }
    }

    /**
     * Acquire copy from pool
     */
    fun acquire(): UDM {
        return pool.poll()?.also {
            // Reset to empty state
            reset(it)
        } ?: preBuildModel.deepCopy()
    }

    /**
     * Return copy to pool
     */
    fun release(copy: UDM) {
        if (pool.size < poolSize) {
            pool.offer(copy)
        }
    }

    /**
     * Reset UDM to empty state (for reuse)
     */
    private fun reset(udm: UDM) {
        when (udm) {
            is MutableObject -> {
                udm.attributes.clear()
                udm.properties.values.forEach { reset(it) }
            }
            is MutableScalar -> {
                udm.setValue(null)
            }
            // ... other types
        }
    }
}
```

### 5.3 Performance Comparison: Copy vs Parse

```kotlin
/**
 * Benchmark: Copy vs Parse
 */
fun benchmarkCopyVsParse() {
    val xml = """
        <Order id="ORD-001">
            <Customer>
                <Name>Alice</Name>
                <Email>alice@example.com</Email>
            </Customer>
        </Order>
    """

    val schema = loadXSD("order.xsd")
    val preBuiltModel = UDMModelBuilder().buildFromXSD(schema)

    // Benchmark: Parse from scratch
    val parseTime = measureTimeMillis {
        repeat(1000) {
            val udm = parseXMLToUDM(xml)
        }
    }

    // Benchmark: Copy + Fill
    val copyTime = measureTimeMillis {
        repeat(1000) {
            val copy = preBuiltModel.deepCopy()
            fillFromXML(copy, xml)
        }
    }

    println("Parse: ${parseTime}ms (${parseTime/1000.0}ms per message)")
    println("Copy:  ${copyTime}ms (${copyTime/1000.0}ms per message)")
    println("Speedup: ${parseTime.toDouble() / copyTime}x")
}

// Expected results:
// Parse: 12000ms (12ms per message)
// Copy:  3000ms (3ms per message)
// Speedup: 4x
```

---

## 6. Schema Validation Integration

### 6.1 XML + XSD Validation (javax.xml.validation)

```kotlin
/**
 * Complete XML validation example
 */
class XMLValidationExample {
    fun example() {
        // INIT TIME: Load schema
        val schemaFactory = SchemaFactory.newInstance(
            XMLConstants.W3C_XML_SCHEMA_NS_URI
        )
        val schema = schemaFactory.newSchema(File("order.xsd"))
        val validator = schema.newValidator()

        // RUNTIME: Validate message
        val xml = """
            <Order id="ORD-001">
                <Customer>
                    <Name>Alice</Name>
                    <Email>invalid-email</Email>
                </Customer>
            </Order>
        """

        val errors = mutableListOf<ValidationError>()
        val errorHandler = CollectingErrorHandler(errors)
        validator.errorHandler = errorHandler

        try {
            validator.validate(StreamSource(StringReader(xml)))
            println("Valid!")
        } catch (e: SAXException) {
            println("Invalid:")
            errors.forEach { error ->
                println("  Line ${error.line}: ${error.message}")
            }
        }
    }
}
```

### 6.2 JSON Schema Validation

```kotlin
/**
 * Complete JSON Schema validation example
 */
class JSONSchemaValidationExample {
    fun example() {
        // INIT TIME: Load schema
        val schemaJson = """
        {
          "${'$'}schema": "http://json-schema.org/draft-07/schema#",
          "type": "object",
          "properties": {
            "orderId": {"type": "string"},
            "customer": {
              "type": "object",
              "properties": {
                "name": {"type": "string"},
                "email": {"type": "string", "format": "email"}
              },
              "required": ["name", "email"]
            }
          },
          "required": ["orderId", "customer"]
        }
        """

        val rawSchema = JSONObject(JSONTokener(schemaJson))
        val schema = SchemaLoader.load(rawSchema)

        // RUNTIME: Validate message
        val json = """
        {
          "orderId": "ORD-001",
          "customer": {
            "name": "Alice",
            "email": "invalid-email"
          }
        }
        """

        try {
            val jsonObject = JSONObject(JSONTokener(json))
            schema.validate(jsonObject)
            println("Valid!")
        } catch (e: org.everit.json.schema.ValidationException) {
            println("Invalid:")
            println("  ${e.message}")
            e.causingExceptions.forEach { cause ->
                println("  ${cause.pointerToViolation}: ${cause.message}")
            }
        }
    }
}
```

### 6.3 Validation Performance

```
┌────────────────────────────────────────────────────────┐
│  Validation Performance (1000 messages)                │
├────────────────────────────────────────────────────────┤
│  XML + XSD (javax.xml.validation):  1-5ms per message  │
│  JSON Schema (everit-org):          0.5-2ms per msg    │
│  CSV Structure Check:               0.1-0.5ms per msg  │
└────────────────────────────────────────────────────────┘

Total overhead: 1-5ms per message
Benefit: Early error detection, security, data quality
```

---

## 7. Handling Invalid Messages

### 7.1 Validation Policy Strategies

```kotlin
/**
 * Validation policy implementation
 */
class ValidationPolicyHandler {

    /**
     * STRICT: Stop processing
     */
    fun handleStrict(result: ValidationResult.Invalid): Nothing {
        throw ValidationException(
            message = "Message validation failed",
            errors = result.errors
        )
    }

    /**
     * WARN_AND_CONTINUE: Log and continue (MIDDLEWARE DEFAULT)
     */
    fun handleWarnAndContinue(result: ValidationResult.Invalid) {
        logger.warn("Message validation failed, continuing anyway:")
        result.errors.forEach { error ->
            logger.warn("  ${error.severity}: ${error.message}")
        }

        // Emit metrics
        metrics.incrementInvalidMessageCount()
        metrics.recordValidationErrors(result.errors)

        // Continue processing - DO NOT THROW
    }

    /**
     * CUSTOM: User-defined handler
     */
    fun handleCustom(
        result: ValidationResult.Invalid,
        handler: (ValidationResult.Invalid) -> Unit
    ) {
        handler(result)
    }
}
```

### 7.2 Error Transformation Pattern

**Middleware Pattern: Transform invalid messages to error format**

```kotlin
/**
 * Error transformation for invalid messages
 */
fun transformInvalidMessage(
    input: String,
    validationResult: ValidationResult.Invalid
): String {
    // Copy pre-built error model
    val errorModel = preBuiltErrorModel.deepCopy()

    // Fill with error details
    errorModel.set("originalMessage", input)
    errorModel.set("validationErrors", validationResult.errors.map { error ->
        mapOf(
            "line" to error.line,
            "column" to error.column,
            "message" to error.message,
            "severity" to error.severity.name
        )
    })
    errorModel.set("timestamp", Instant.now().toString())

    // Serialize to error queue format
    return serializeToJSON(errorModel)
}

// Example usage
val validationResult = validator.validate(input)

val output = when (validationResult) {
    is ValidationResult.Valid -> {
        // Normal transformation
        executor.transform(input)
    }

    is ValidationResult.Invalid -> {
        when (policy) {
            ValidationPolicy.STRICT -> throw ValidationException(validationResult.errors)

            ValidationPolicy.WARN_AND_CONTINUE -> {
                logger.warn("Invalid message, transforming anyway")
                executor.transform(input)
            }

            ValidationPolicy.CUSTOM -> {
                // Transform to error format
                transformInvalidMessage(input, validationResult)
            }
        }
    }

    is ValidationResult.Skipped -> {
        // No schema - transform normally
        executor.transform(input)
    }
}
```

### 7.3 Partial Transformation of Invalid Messages

**Pattern: Extract what you can from invalid messages**

```kotlin
/**
 * Partial transformation (best-effort)
 */
fun partialTransform(input: String, validationResult: ValidationResult.Invalid): String {
    // Copy pre-built model
    val udmCopy = preBuiltModel.deepCopy()

    try {
        // Try to fill as much as possible
        fillModelBestEffort(udmCopy, input)
    } catch (e: Exception) {
        logger.error("Partial fill failed", e)
    }

    // Transform whatever we could extract
    val output = transform.execute(udmCopy)

    // Add metadata about validation errors
    output.metadata["validationErrors"] = validationResult.errors
    output.metadata["partial"] = "true"

    return serialize(output)
}
```

---

## 8. Pre-Built UDM Model Structure

### 8.1 Model Building from XSD

```kotlin
/**
 * Complete example: Build UDM from XSD
 */
class XSDToUDMBuilder {

    fun build(xsdFile: File): UDM {
        // Parse XSD
        val schemaFactory = SchemaFactory.newInstance(
            XMLConstants.W3C_XML_SCHEMA_NS_URI
        )
        val schema = schemaFactory.newSchema(xsdFile)

        // Extract structure using XSModel (Xerces API)
        val xsModel = getXSModel(schema)
        val rootElement = xsModel.getElementDeclaration("Order", null)

        // Build UDM recursively
        return buildFromElement(rootElement)
    }

    private fun buildFromElement(element: XSElementDeclaration): UDM {
        val typeDefinition = element.typeDefinition

        return when (typeDefinition) {
            is XSSimpleTypeDefinition -> {
                // Scalar element
                MutableScalar(getDefaultValue(typeDefinition))
            }

            is XSComplexTypeDefinition -> {
                // Complex element
                buildFromComplexType(typeDefinition, element.name)
            }

            else -> MutableScalar(null)
        }
    }

    private fun buildFromComplexType(
        complexType: XSComplexTypeDefinition,
        elementName: String
    ): UDM {
        val properties = mutableMapOf<String, UDM>()
        val attributes = mutableMapOf<String, String>()

        // Process attributes
        val attrUses = complexType.attributeUses
        for (i in 0 until attrUses.length) {
            val attrUse = attrUses.item(i) as XSAttributeUse
            val attr = attrUse.attrDeclaration
            attributes[attr.name] = ""  // Empty default
        }

        // Process child elements
        val particle = complexType.particle
        if (particle != null) {
            val term = particle.term
            if (term is XSModelGroup) {
                processModelGroup(term, properties)
            }
        }

        return MutableObject(
            properties = properties,
            attributes = attributes,
            name = elementName
        )
    }

    private fun processModelGroup(
        modelGroup: XSModelGroup,
        properties: MutableMap<String, UDM>
    ) {
        val particles = modelGroup.particles
        for (i in 0 until particles.length) {
            val particle = particles.item(i) as XSParticle
            val term = particle.term

            if (term is XSElementDeclaration) {
                val childUDM = buildFromElement(term)
                properties[term.name] = childUDM
            }
        }
    }
}
```

### 8.2 Model Building from JSON Schema

```kotlin
/**
 * Build UDM from JSON Schema
 */
class JSONSchemaToUDMBuilder {

    fun build(schemaFile: File): UDM {
        val schemaJson = schemaFile.readText()
        val rawSchema = JSONObject(JSONTokener(schemaJson))
        val schema = SchemaLoader.load(rawSchema)

        return buildFromSchema(schema)
    }

    private fun buildFromSchema(schema: org.everit.json.schema.Schema): UDM {
        return when (schema) {
            is org.everit.json.schema.ObjectSchema -> {
                val properties = schema.propertySchemas.mapValues { (_, propSchema) ->
                    buildFromSchema(propSchema)
                }.toMutableMap()

                MutableObject(
                    properties = properties,
                    attributes = mutableMapOf()
                )
            }

            is org.everit.json.schema.ArraySchema -> {
                val elementSchema = schema.allItemSchema
                val elementUDM = buildFromSchema(elementSchema)

                UDM.Array(mutableListOf())  // Empty array initially
            }

            is org.everit.json.schema.StringSchema -> {
                MutableScalar("")
            }

            is org.everit.json.schema.NumberSchema -> {
                MutableScalar(0.0)
            }

            is org.everit.json.schema.BooleanSchema -> {
                MutableScalar(false)
            }

            else -> {
                MutableScalar(null)
            }
        }
    }
}
```

---

## 9. Memory Management

### 9.1 Memory Layout Comparison

```
┌──────────────────────────────────────────────────────────┐
│  Template-Based Approach (Previous Document)             │
├──────────────────────────────────────────────────────────┤
│  Init-Time:                                              │
│    - Template objects: 100KB                             │
│    - Object pool: 100 × 500KB = 50MB                     │
│                                                           │
│  Runtime (per message):                                  │
│    - Acquire from pool: 0.001ms                          │
│    - Fill template: 2-5ms                                │
│    - Return to pool: 0.001ms                             │
│                                                           │
│  Total runtime memory: 50MB (pooled)                     │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│  Copy-Based Approach (This Document)                     │
├──────────────────────────────────────────────────────────┤
│  Init-Time:                                              │
│    - Pre-built model: 500KB (single instance)            │
│    - Copy pool: 100 × 500KB = 50MB                       │
│    - Validators: 1MB                                     │
│                                                           │
│  Runtime (per message):                                  │
│    - Validate: 1-5ms                                     │
│    - Acquire copy: 0.001ms                               │
│    - Fill copy: 2-5ms                                    │
│    - Return copy: 0.001ms                                │
│                                                           │
│  Total runtime memory: 51MB (pooled + validators)        │
└──────────────────────────────────────────────────────────┘

Difference: +1ms for validation, +1MB for validators
Benefit: Security, data quality, error detection
```

### 9.2 Copy Pool Management

```kotlin
/**
 * Optimized copy pool with metrics
 */
class UDMCopyPool(
    private val preBuiltModel: UDM,
    private val config: PoolConfig
) {
    private val pool = ConcurrentLinkedQueue<UDM>()
    private val metrics = PoolMetrics()

    init {
        // Pre-create copies at init-time
        repeat(config.initialSize) {
            pool.offer(preBuiltModel.deepCopy())
        }
    }

    fun acquire(): UDM {
        val copy = pool.poll()

        return if (copy != null) {
            metrics.poolHits.incrementAndGet()
            reset(copy)
            copy
        } else {
            metrics.poolMisses.incrementAndGet()
            preBuiltModel.deepCopy()
        }
    }

    fun release(copy: UDM) {
        if (pool.size < config.maxSize) {
            pool.offer(copy)
        }
        // If pool full, let GC collect
    }

    fun getMetrics(): PoolMetrics = metrics
}

data class PoolMetrics(
    val poolHits: AtomicLong = AtomicLong(0),
    val poolMisses: AtomicLong = AtomicLong(0)
) {
    val hitRate: Double
        get() {
            val total = poolHits.get() + poolMisses.get()
            return if (total > 0) {
                poolHits.get().toDouble() / total
            } else 0.0
        }
}
```

---

## 10. Implementation Architecture

### 10.1 Complete Class Structure

```kotlin
// ============================================
// DESIGN TIME
// ============================================

interface CompiledTransform {
    val metadata: TransformMetadata
    val inputSchema: Schema?
    val outputSchema: Schema?
    val validationPolicy: ValidationPolicy

    fun createExecutor(config: ExecutorConfig): TransformExecutor
}

class SchemaAwareCompiledTransform(
    override val metadata: TransformMetadata,
    override val inputSchema: Schema?,
    override val outputSchema: Schema?,
    override val validationPolicy: ValidationPolicy,
    private val bytecode: ByteArray
) : CompiledTransform

// ============================================
// INIT TIME
// ============================================

interface Validator {
    fun validate(input: String): ValidationResult
}

class XMLValidator(
    private val validator: javax.xml.validation.Validator
) : Validator

class JSONSchemaValidator(
    private val schema: org.everit.json.schema.Schema
) : Validator

class UDMModelBuilder {
    fun build(schema: Schema): UDM
    fun buildFromXSD(schema: Schema.XMLSchema): UDM
    fun buildFromJSONSchema(schema: Schema.JSONSchema): UDM
}

class UDMCopyPool(
    private val preBuiltModel: UDM,
    private val config: PoolConfig
) {
    fun acquire(): UDM
    fun release(copy: UDM)
}

// ============================================
// RUNTIME
// ============================================

interface TransformExecutor {
    fun transform(input: String): String
    fun transformBatch(inputs: List<String>): List<String>
}

class CopyBasedExecutor(
    private val transform: SchemaAwareCompiledTransform,
    private val config: ExecutorConfig
) : TransformExecutor {

    private val inputValidator: Validator?
    private val outputValidator: Validator?
    private val preBuiltInputModel: UDM
    private val copyPool: UDMCopyPool

    override fun transform(input: String): String {
        // 1. Validate
        val validationResult = validate(input)
        handleValidationResult(validationResult)

        // 2. Copy pre-built model
        val udmCopy = copyPool.acquire()

        try {
            // 3. Fill with data
            fillModel(udmCopy, input)

            // 4. Transform
            val outputUDM = executeTransform(udmCopy)

            // 5. Serialize
            return serialize(outputUDM)

        } finally {
            // 6. Return to pool
            copyPool.release(udmCopy)
        }
    }
}
```

---

## 11. Performance Analysis

### 11.1 Detailed Performance Breakdown

```
┌──────────────────────────────────────────────────────────┐
│  Per-Message Performance (Copy-Based Approach)           │
├──────────────────────────────────────────────────────────┤
│  Step 1: Validate (if schema available)   1-5ms          │
│  Step 2: Acquire copy from pool           0.001ms        │
│  Step 3: Fill copy with data              2-5ms          │
│  Step 4: Execute transformation           3-10ms         │
│  Step 5: Serialize output                 2-5ms          │
│  Step 6: Return copy to pool              0.001ms        │
│                                                           │
│  TOTAL:                                   8-25ms         │
├──────────────────────────────────────────────────────────┤
│  Comparison with Current CLI:             30-70ms        │
│  Speedup:                                 2.5-4x         │
└──────────────────────────────────────────────────────────┘
```

### 11.2 Validation Overhead Analysis

```
With Validation (Copy-Based):        8-25ms
Without Validation (Template-Based): 7-20ms
Validation Overhead:                 1-5ms   (12-20% of total time)

Benefits of Validation Overhead:
✓ Early error detection
✓ Security (prevent XXE, injection)
✓ Data quality assurance
✓ Clear error messages
✓ Contract enforcement

Recommendation: ALWAYS validate in production middleware
```

---

## 12. Comparison: Template vs Copy-Based

| Aspect | Template-Based | Copy-Based (This Doc) |
|--------|----------------|----------------------|
| **Philosophy** | Modern (object pooling) | Traditional middleware (Tibco BW) |
| **Init-time** | Generate templates | Build complete UDM + validators |
| **Runtime** | Fill template | Copy + fill |
| **Validation** | Optional | Mandatory first step |
| **Invalid messages** | Process anyway | Validate first, then process |
| **Memory** | Template + pool | Pre-built model + copy pool + validators |
| **Performance** | 7-20ms | 8-25ms (+validation) |
| **Security** | Lower | Higher (validation) |
| **Debugging** | Harder | Easier (validation errors) |
| **Use case** | Modern cloud-native | Traditional enterprise middleware |

**Recommendation:**
- **Use Template-Based** for: Cloud-native, microservices, high throughput
- **Use Copy-Based** for: Enterprise middleware, regulated industries, legacy integration

---

## 13. Middleware Integration Patterns

### 13.1 Tibco BusinessWorks Integration

```kotlin
/**
 * Tibco BW-style process
 */
class UTLXBWProcess(
    private val transformPath: String,
    private val xsdPath: String
) {
    private lateinit var executor: CopyBasedExecutor

    // Init (called at BW engine startup)
    fun init() {
        val compiled = UTLXCompiler().compile(
            File(transformPath).readText()
        )

        executor = compiled.createExecutor(ExecutorConfig(
            validationPolicy = ValidationPolicy.WARN_AND_CONTINUE
        )) as CopyBasedExecutor
    }

    // Process (called for each message)
    fun process(input: String): String {
        return executor.transform(input)
    }

    // Cleanup (called at shutdown)
    fun cleanup() {
        // Release resources
    }
}
```

### 13.2 IBM Integration Bus (IIB) Pattern

```kotlin
/**
 * IIB-style message flow
 */
class UTLXMessageFlow {
    private lateinit var executor: CopyBasedExecutor

    fun initialize(properties: Properties) {
        val compiled = UTLXCompiler().compile(
            properties.getProperty("transformation.path")
        )

        executor = compiled.createExecutor(ExecutorConfig(
            validationPolicy = ValidationPolicy.STRICT  // IIB style
        ))
    }

    fun evaluate(input: MbMessage): MbMessage {
        val inputXML = input.root.toString()

        try {
            val output = executor.transform(inputXML)
            return createMbMessage(output)
        } catch (e: ValidationException) {
            // Send to exception queue
            throw MbException("Validation failed", e)
        }
    }
}
```

---

## 14. Implementation Roadmap

### Phase 1: Validation Infrastructure (Weeks 1-3)

- [ ] Week 1: javax.xml.validation integration
- [ ] Week 2: JSON Schema validation integration
- [ ] Week 3: Validation policy framework

### Phase 2: Pre-Built Model Construction (Weeks 4-6)

- [ ] Week 4: UDM model builder from XSD
- [ ] Week 5: UDM model builder from JSON Schema
- [ ] Week 6: Deep copy optimization

### Phase 3: Copy-Based Executor (Weeks 7-9)

- [ ] Week 7: CopyBasedExecutor implementation
- [ ] Week 8: Copy pool management
- [ ] Week 9: Integration testing

### Phase 4: Production Readiness (Weeks 10-12)

- [ ] Week 10: Performance testing
- [ ] Week 11: Documentation
- [ ] Week 12: Release

---

## 15. Migration from Current Architecture

### 15.1 CLI Compatibility

```kotlin
// Old CLI (still works)
./utlx transform input.xml transform.utlx

// New CLI with validation
./utlx transform input.xml transform.utlx \
    --schema input-schema.xsd \
    --validate \
    --validation-policy WARN_AND_CONTINUE
```

### 15.2 Programmatic API

```kotlin
// Simple API (auto-detect approach)
val engine = UTLXEngine.builder()
    .transformation(transformSource)
    .inputSchema(File("schema.xsd"))
    .validationPolicy(ValidationPolicy.WARN_AND_CONTINUE)
    .useCopyBased(true)  // Enable copy-based approach
    .build()

val result = engine.transform(input)
```

---

## Conclusion

This **copy-based, validation-first** architecture provides:

**Key Benefits:**
1. **Mandatory validation** - Security and data quality
2. **Copy-based instantiation** - Proven middleware pattern
3. **Handles invalid messages** - Middleware requirement
4. **Compatible with enterprise standards** - Tibco BW, IBM IIB

**Performance:**
- **8-25ms per message** (including validation)
- **2.5-4x faster** than current CLI
- **Slightly slower** than template-based (due to validation)

**Trade-offs:**
- **+1-5ms overhead** for validation
- **+1MB memory** for validators
- **More complex** implementation

**Recommendation:**
Use copy-based approach for **enterprise middleware** deployments where validation, security, and regulatory compliance are critical.

---

**Document Status:** Ready for Review
**Author:** UTL-X Architecture Team
**Last Updated:** 2025-10-22
