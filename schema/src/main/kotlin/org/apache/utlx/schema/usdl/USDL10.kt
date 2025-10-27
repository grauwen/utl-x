package org.apache.utlx.schema.usdl

/**
 * USDL 1.0 Directive Catalog
 *
 * Complete catalog of all directives defined in USDL 1.0 specification.
 * Organized by tier for incremental implementation.
 *
 * Tier 1 (Core): Universal directives required for all schema languages
 * Tier 2 (Common): Recommended directives supported by 80%+ schema languages
 *                  Now includes messaging & event-driven API directives for AsyncAPI, OpenAPI webhooks
 * Tier 3 (Format-Specific): Specialized directives for specific formats
 * Tier 4 (Reserved): Future USDL versions, advanced features
 *
 * USDL 1.0 Extended Support:
 * - Data schema formats: XSD, JSON Schema, Avro, Protobuf, Parquet, SQL DDL
 * - API specifications: OpenAPI, AsyncAPI (messaging/event-driven)
 * - Messaging protocols: Kafka, AMQP, MQTT, WebSocket, gRPC
 */
object USDL10 {

    const val VERSION = "1.0"

    /**
     * Directive tier classification
     */
    enum class Tier {
        CORE,              // Tier 1 - Required for all schemas
        COMMON,            // Tier 2 - Recommended, 80%+ support
        FORMAT_SPECIFIC,   // Tier 3 - Format-specific needs
        RESERVED           // Tier 4 - Future versions
    }

    /**
     * Directive applicability scope
     */
    enum class Scope {
        TOP_LEVEL,              // Root schema level
        TYPE_DEFINITION,        // Within type definitions
        FIELD_DEFINITION,       // Within field definitions
        CONSTRAINT,             // Within constraints object
        ENUMERATION,            // Within enumeration values
        CHANNEL_DEFINITION,     // Within channel/topic definitions (messaging)
        OPERATION_DEFINITION,   // Within operation definitions (messaging/API)
        SERVER_DEFINITION,      // Within server/endpoint definitions (messaging/API)
        MESSAGE_DEFINITION      // Within message definitions (messaging)
    }

    /**
     * Complete directive definition
     */
    data class Directive(
        val name: String,
        val tier: Tier,
        val scopes: Set<Scope>,
        val valueType: String,
        val required: Boolean = false,
        val description: String,
        val supportedFormats: Set<String> = setOf("xsd", "jsch", "proto", "sql", "avro", "graphql", "odata")
    )

    /**
     * Tier 1 (Core) - Universal directives
     */
    private val TIER1_DIRECTIVES = listOf(
        Directive(
            name = "%namespace",
            tier = Tier.CORE,
            scopes = setOf(Scope.TOP_LEVEL),
            valueType = "String",
            description = "Schema namespace or package name"
        ),
        Directive(
            name = "%version",
            tier = Tier.CORE,
            scopes = setOf(Scope.TOP_LEVEL),
            valueType = "String",
            description = "Schema version"
        ),
        Directive(
            name = "%types",
            tier = Tier.CORE,
            scopes = setOf(Scope.TOP_LEVEL),
            valueType = "Object",
            required = true,
            description = "Type definitions (at least one required)"
        ),
        Directive(
            name = "%kind",
            tier = Tier.CORE,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "String",
            required = true,
            description = "Type kind: structure, enumeration, primitive, array, union, interface"
        ),
        Directive(
            name = "%name",
            tier = Tier.CORE,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "String",
            required = true,
            description = "Field or element name"
        ),
        Directive(
            name = "%type",
            tier = Tier.CORE,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "String",
            required = true,
            description = "Field type (primitive or type reference)"
        ),
        Directive(
            name = "%description",
            tier = Tier.CORE,
            scopes = setOf(Scope.FIELD_DEFINITION, Scope.ENUMERATION),
            valueType = "String",
            description = "Field-level or value-level description"
        ),
        Directive(
            name = "%value",
            tier = Tier.CORE,
            scopes = setOf(Scope.ENUMERATION),
            valueType = "String or Number",
            description = "Enumeration value (when using object form with description)"
        ),
        Directive(
            name = "%documentation",
            tier = Tier.CORE,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "String",
            description = "Type-level documentation"
        )
    )

    /**
     * Tier 2 (Common) - Recommended directives
     */
    private val TIER2_DIRECTIVES = listOf(
        Directive(
            name = "%fields",
            tier = Tier.COMMON,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "Array",
            description = "Array of field definitions (for structures)"
        ),
        Directive(
            name = "%values",
            tier = Tier.COMMON,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "Array",
            description = "Enumeration values"
        ),
        Directive(
            name = "%itemType",
            tier = Tier.COMMON,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "String",
            description = "Element type for arrays"
        ),
        Directive(
            name = "%baseType",
            tier = Tier.COMMON,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "String",
            description = "Base type for primitives or restrictions"
        ),
        Directive(
            name = "%options",
            tier = Tier.COMMON,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "Array",
            description = "Type references for union types"
        ),
        Directive(
            name = "%required",
            tier = Tier.COMMON,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "Boolean",
            description = "Is this field required?"
        ),
        Directive(
            name = "%array",
            tier = Tier.COMMON,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "Boolean",
            description = "Is this field an array/repeated?"
        ),
        Directive(
            name = "%nullable",
            tier = Tier.COMMON,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "Boolean",
            description = "Can this field be null?"
        ),
        Directive(
            name = "%default",
            tier = Tier.COMMON,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "Any",
            description = "Default value for field"
        ),
        Directive(
            name = "%constraints",
            tier = Tier.COMMON,
            scopes = setOf(Scope.FIELD_DEFINITION, Scope.TYPE_DEFINITION),
            valueType = "Object",
            description = "Constraint definitions"
        ),
        Directive(
            name = "%minLength",
            tier = Tier.COMMON,
            scopes = setOf(Scope.CONSTRAINT),
            valueType = "Integer",
            description = "Minimum string length"
        ),
        Directive(
            name = "%maxLength",
            tier = Tier.COMMON,
            scopes = setOf(Scope.CONSTRAINT),
            valueType = "Integer",
            description = "Maximum string length"
        ),
        Directive(
            name = "%pattern",
            tier = Tier.COMMON,
            scopes = setOf(Scope.CONSTRAINT),
            valueType = "String",
            description = "Regex pattern for string validation"
        ),
        Directive(
            name = "%minimum",
            tier = Tier.COMMON,
            scopes = setOf(Scope.CONSTRAINT),
            valueType = "Number",
            description = "Minimum value (inclusive)"
        ),
        Directive(
            name = "%maximum",
            tier = Tier.COMMON,
            scopes = setOf(Scope.CONSTRAINT),
            valueType = "Number",
            description = "Maximum value (inclusive)"
        ),
        Directive(
            name = "%exclusiveMinimum",
            tier = Tier.COMMON,
            scopes = setOf(Scope.CONSTRAINT),
            valueType = "Number",
            description = "Minimum value (exclusive)"
        ),
        Directive(
            name = "%exclusiveMaximum",
            tier = Tier.COMMON,
            scopes = setOf(Scope.CONSTRAINT),
            valueType = "Number",
            description = "Maximum value (exclusive)"
        ),
        Directive(
            name = "%enum",
            tier = Tier.COMMON,
            scopes = setOf(Scope.CONSTRAINT),
            valueType = "Array",
            description = "Allowed values enumeration"
        ),
        Directive(
            name = "%format",
            tier = Tier.COMMON,
            scopes = setOf(Scope.CONSTRAINT),
            valueType = "String",
            description = "Format hint: email, uri, date, date-time, etc."
        ),
        Directive(
            name = "%multipleOf",
            tier = Tier.COMMON,
            scopes = setOf(Scope.CONSTRAINT),
            valueType = "Number",
            description = "Value must be a multiple of this number"
        ),

        // ===== MESSAGING & EVENT-DRIVEN API DIRECTIVES =====
        // These directives support AsyncAPI, OpenAPI webhooks, and future messaging formats
        // Cross-format Tier 2 directives for event-driven architectures

        // Root-level messaging directives
        Directive(
            name = "%servers",
            tier = Tier.COMMON,
            scopes = setOf(Scope.TOP_LEVEL),
            valueType = "Object",
            description = "Server/endpoint definitions for messaging or API specs",
            supportedFormats = setOf("asyncapi", "openapi", "grpc")
        ),
        Directive(
            name = "%channels",
            tier = Tier.COMMON,
            scopes = setOf(Scope.TOP_LEVEL),
            valueType = "Object",
            description = "Channel/topic definitions for messaging APIs (Kafka, AMQP, MQTT, WebSocket)",
            supportedFormats = setOf("asyncapi", "grpc")
        ),
        Directive(
            name = "%operations",
            tier = Tier.COMMON,
            scopes = setOf(Scope.TOP_LEVEL),
            valueType = "Object",
            description = "API operations (publish/subscribe, send/receive, RPC methods)",
            supportedFormats = setOf("asyncapi", "grpc", "graphql")
        ),
        Directive(
            name = "%messages",
            tier = Tier.COMMON,
            scopes = setOf(Scope.TOP_LEVEL),
            valueType = "Object",
            description = "Message definitions for event-driven APIs",
            supportedFormats = setOf("asyncapi", "grpc")
        ),

        // Server-level directives
        Directive(
            name = "%host",
            tier = Tier.COMMON,
            scopes = setOf(Scope.SERVER_DEFINITION),
            valueType = "String",
            description = "Server host (hostname:port)",
            supportedFormats = setOf("asyncapi", "openapi", "grpc")
        ),
        Directive(
            name = "%protocol",
            tier = Tier.COMMON,
            scopes = setOf(Scope.SERVER_DEFINITION, Scope.CHANNEL_DEFINITION),
            valueType = "String",
            description = "Communication protocol (kafka, amqp, mqtt, websocket, http, grpc)",
            supportedFormats = setOf("asyncapi", "grpc")
        ),

        // Channel-level directives
        Directive(
            name = "%address",
            tier = Tier.COMMON,
            scopes = setOf(Scope.CHANNEL_DEFINITION),
            valueType = "String",
            description = "Channel address/topic name (AsyncAPI 3.0)",
            supportedFormats = setOf("asyncapi")
        ),
        Directive(
            name = "%subscribe",
            tier = Tier.COMMON,
            scopes = setOf(Scope.CHANNEL_DEFINITION),
            valueType = "Object",
            description = "Subscribe operation definition (AsyncAPI 2.x)",
            supportedFormats = setOf("asyncapi")
        ),
        Directive(
            name = "%publish",
            tier = Tier.COMMON,
            scopes = setOf(Scope.CHANNEL_DEFINITION),
            valueType = "Object",
            description = "Publish operation definition (AsyncAPI 2.x)",
            supportedFormats = setOf("asyncapi")
        ),
        Directive(
            name = "%bindings",
            tier = Tier.COMMON,
            scopes = setOf(Scope.CHANNEL_DEFINITION, Scope.OPERATION_DEFINITION, Scope.MESSAGE_DEFINITION),
            valueType = "Object",
            description = "Protocol-specific bindings (Kafka, AMQP, MQTT configurations)",
            supportedFormats = setOf("asyncapi")
        ),

        // Message-level directives
        Directive(
            name = "%contentType",
            tier = Tier.COMMON,
            scopes = setOf(Scope.MESSAGE_DEFINITION),
            valueType = "String",
            description = "Message content type (application/json, avro/binary, text/plain)",
            supportedFormats = setOf("asyncapi", "openapi")
        ),
        Directive(
            name = "%headers",
            tier = Tier.COMMON,
            scopes = setOf(Scope.MESSAGE_DEFINITION),
            valueType = "Object or String",
            description = "Message headers schema or type reference",
            supportedFormats = setOf("asyncapi", "openapi")
        ),
        Directive(
            name = "%payload",
            tier = Tier.COMMON,
            scopes = setOf(Scope.MESSAGE_DEFINITION),
            valueType = "Object or String",
            description = "Message payload schema or type reference",
            supportedFormats = setOf("asyncapi", "openapi")
        ),

        // Operation-level directives (AsyncAPI 3.0 style)
        Directive(
            name = "%action",
            tier = Tier.COMMON,
            scopes = setOf(Scope.OPERATION_DEFINITION),
            valueType = "String",
            description = "Operation action type (send, receive, publish, subscribe)",
            supportedFormats = setOf("asyncapi")
        ),
        Directive(
            name = "%channel",
            tier = Tier.COMMON,
            scopes = setOf(Scope.OPERATION_DEFINITION),
            valueType = "String",
            description = "Channel reference for this operation",
            supportedFormats = setOf("asyncapi")
        ),
        Directive(
            name = "%message",
            tier = Tier.COMMON,
            scopes = setOf(Scope.OPERATION_DEFINITION),
            valueType = "String or Array",
            description = "Message type reference(s) for this operation",
            supportedFormats = setOf("asyncapi")
        ),

        // Example directive (moved from Reserved to Common for better support)
        Directive(
            name = "%example",
            tier = Tier.COMMON,
            scopes = setOf(Scope.TYPE_DEFINITION, Scope.FIELD_DEFINITION, Scope.TOP_LEVEL),
            valueType = "Any",
            description = "Example value or data for types, fields, or standalone examples",
            supportedFormats = setOf("jsch", "openapi", "asyncapi", "raml", "avro")
        )
    )

    /**
     * Tier 3 (Format-Specific) - Specialized directives
     */
    private val TIER3_DIRECTIVES = listOf(
        // Binary Serialization (Protobuf, Thrift)
        Directive(
            name = "%fieldNumber",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "Integer",
            description = "Field tag number (Protobuf, Thrift)",
            supportedFormats = setOf("proto", "thrift")
        ),
        Directive(
            name = "%fieldId",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "Integer",
            description = "Field identifier (alternative to fieldNumber)",
            supportedFormats = setOf("capnp", "flatbuf")
        ),
        Directive(
            name = "%ordinal",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.ENUMERATION),
            valueType = "Integer",
            description = "Enum value ordinal",
            supportedFormats = setOf("proto", "thrift", "avro")
        ),
        Directive(
            name = "%packed",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "Boolean",
            description = "Packed encoding for repeated numeric fields",
            supportedFormats = setOf("proto")
        ),
        Directive(
            name = "%reserved",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "Array",
            description = "Reserved field numbers or names",
            supportedFormats = setOf("proto", "thrift")
        ),
        Directive(
            name = "%oneof",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "String",
            description = "Oneof group name (mutually exclusive fields)",
            supportedFormats = setOf("proto")
        ),
        Directive(
            name = "%map",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "Boolean",
            description = "Is this a map/dictionary field?",
            supportedFormats = setOf("proto", "thrift", "avro")
        ),

        // Big Data (Avro, Parquet)
        Directive(
            name = "%logicalType",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "String",
            description = "Semantic type annotation: date, timestamp-millis, decimal",
            supportedFormats = setOf("avro", "parquet")
        ),
        Directive(
            name = "%aliases",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.TYPE_DEFINITION, Scope.FIELD_DEFINITION),
            valueType = "Array",
            description = "Alternate names for schema evolution",
            supportedFormats = setOf("avro")
        ),
        Directive(
            name = "%precision",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION, Scope.CONSTRAINT),
            valueType = "Integer",
            description = "Decimal precision",
            supportedFormats = setOf("avro", "sql", "parquet")
        ),
        Directive(
            name = "%scale",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION, Scope.CONSTRAINT),
            valueType = "Integer",
            description = "Decimal scale",
            supportedFormats = setOf("avro", "sql", "parquet")
        ),
        Directive(
            name = "%size",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "Integer",
            description = "Fixed size for bytes or strings",
            supportedFormats = setOf("avro", "capnp")
        ),
        Directive(
            name = "%repetition",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "String",
            description = "Parquet repetition: required, optional, repeated",
            supportedFormats = setOf("parquet")
        ),
        Directive(
            name = "%encoding",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "String",
            description = "Parquet encoding type",
            supportedFormats = setOf("parquet")
        ),
        Directive(
            name = "%compression",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "String",
            description = "Compression algorithm: snappy, gzip, lzo",
            supportedFormats = setOf("avro", "parquet")
        ),

        // Database (SQL DDL)
        Directive(
            name = "%table",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "String",
            description = "Database table name",
            supportedFormats = setOf("sql")
        ),
        Directive(
            name = "%key",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "Boolean",
            description = "Is this field a primary key?",
            supportedFormats = setOf("sql", "odata", "graphql")
        ),
        Directive(
            name = "%autoIncrement",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "Boolean",
            description = "Auto-increment/serial column",
            supportedFormats = setOf("sql")
        ),
        Directive(
            name = "%unique",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "Boolean",
            description = "Unique constraint",
            supportedFormats = setOf("sql")
        ),
        Directive(
            name = "%index",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "String or Boolean",
            description = "Index hint or name",
            supportedFormats = setOf("sql")
        ),
        Directive(
            name = "%foreignKey",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "String",
            description = "Foreign key reference to another table/type",
            supportedFormats = setOf("sql")
        ),
        Directive(
            name = "%references",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "String",
            description = "Referenced column name",
            supportedFormats = setOf("sql")
        ),
        Directive(
            name = "%onDelete",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "String",
            description = "ON DELETE action: CASCADE, SET NULL, RESTRICT",
            supportedFormats = setOf("sql")
        ),
        Directive(
            name = "%onUpdate",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "String",
            description = "ON UPDATE action: CASCADE, SET NULL, RESTRICT",
            supportedFormats = setOf("sql")
        ),
        Directive(
            name = "%check",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION, Scope.TYPE_DEFINITION),
            valueType = "String",
            description = "CHECK constraint expression",
            supportedFormats = setOf("sql")
        ),

        // REST/OData
        Directive(
            name = "%entityType",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "Boolean",
            description = "Mark as entity type vs complex type",
            supportedFormats = setOf("odata")
        ),
        Directive(
            name = "%navigation",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "Array",
            description = "Navigation properties",
            supportedFormats = setOf("odata")
        ),
        Directive(
            name = "%target",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "String",
            description = "Navigation target entity",
            supportedFormats = setOf("odata")
        ),
        Directive(
            name = "%cardinality",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "String",
            description = "Relationship cardinality: 1:1, 1:N, N:N",
            supportedFormats = setOf("odata")
        ),
        Directive(
            name = "%referentialConstraint",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "Object",
            description = "Foreign key constraints for navigation",
            supportedFormats = setOf("odata")
        ),

        // GraphQL
        Directive(
            name = "%implements",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "Array",
            description = "Interfaces implemented by this type",
            supportedFormats = setOf("graphql", "avro", "thrift")
        ),
        Directive(
            name = "%resolver",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "String",
            description = "Resolver function hint",
            supportedFormats = setOf("graphql")
        ),

        // OpenAPI/JSON Schema Extensions
        Directive(
            name = "%readOnly",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "Boolean",
            description = "Read-only property (API responses only)",
            supportedFormats = setOf("jsch", "openapi")
        ),
        Directive(
            name = "%writeOnly",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "Boolean",
            description = "Write-only property (API requests only)",
            supportedFormats = setOf("jsch", "openapi")
        ),
        Directive(
            name = "%discriminator",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "Object",
            description = "Polymorphism discriminator",
            supportedFormats = setOf("jsch", "openapi")
        ),
        Directive(
            name = "%propertyName",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "String",
            description = "Discriminator property name",
            supportedFormats = setOf("jsch", "openapi")
        ),
        Directive(
            name = "%mapping",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "Object",
            description = "Discriminator value to schema mapping",
            supportedFormats = setOf("jsch", "openapi")
        ),
        Directive(
            name = "%externalDocs",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.TYPE_DEFINITION, Scope.FIELD_DEFINITION),
            valueType = "Object",
            description = "External documentation reference",
            supportedFormats = setOf("openapi")
        ),
        Directive(
            name = "%url",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "String",
            description = "External documentation URL",
            supportedFormats = setOf("openapi")
        ),
        Directive(
            name = "%examples",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION, Scope.TYPE_DEFINITION),
            valueType = "Array",
            description = "Example values",
            supportedFormats = setOf("jsch", "openapi")
        ),
        Directive(
            name = "%xml",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "Object",
            description = "XML representation hints",
            supportedFormats = setOf("openapi", "xsd")
        ),

        // XSD-Specific
        Directive(
            name = "%elementFormDefault",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.TOP_LEVEL),
            valueType = "String",
            description = "qualified or unqualified (XSD)",
            supportedFormats = setOf("xsd")
        ),
        Directive(
            name = "%attributeFormDefault",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.TOP_LEVEL),
            valueType = "String",
            description = "qualified or unqualified (XSD)",
            supportedFormats = setOf("xsd")
        ),
        Directive(
            name = "%choice",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "Boolean",
            description = "XSD choice compositor",
            supportedFormats = setOf("xsd")
        ),
        Directive(
            name = "%all",
            tier = Tier.FORMAT_SPECIFIC,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "Boolean",
            description = "XSD all compositor (unordered)",
            supportedFormats = setOf("xsd")
        )
    )

    /**
     * Tier 4 (Reserved) - Future USDL versions
     */
    private val TIER4_DIRECTIVES = listOf(
        // Schema Composition
        Directive(
            name = "%allOf",
            tier = Tier.RESERVED,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "Array",
            description = "Schema composition: all schemas must match"
        ),
        Directive(
            name = "%anyOf",
            tier = Tier.RESERVED,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "Array",
            description = "Schema composition: any schema may match"
        ),
        Directive(
            name = "%oneOf",
            tier = Tier.RESERVED,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "Array",
            description = "Schema composition: exactly one schema must match"
        ),
        Directive(
            name = "%not",
            tier = Tier.RESERVED,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "Object",
            description = "Schema negation"
        ),
        Directive(
            name = "%if",
            tier = Tier.RESERVED,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "Object",
            description = "Conditional schema: if condition"
        ),
        Directive(
            name = "%then",
            tier = Tier.RESERVED,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "Object",
            description = "Conditional schema: then branch"
        ),
        Directive(
            name = "%else",
            tier = Tier.RESERVED,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "Object",
            description = "Conditional schema: else branch"
        ),

        // Advanced Metadata
        Directive(
            name = "%deprecated",
            tier = Tier.RESERVED,
            scopes = setOf(Scope.TYPE_DEFINITION, Scope.FIELD_DEFINITION),
            valueType = "Boolean",
            description = "Mark as deprecated"
        ),
        Directive(
            name = "%reason",
            tier = Tier.RESERVED,
            scopes = setOf(Scope.TYPE_DEFINITION, Scope.FIELD_DEFINITION),
            valueType = "String",
            description = "Deprecation reason"
        ),
        Directive(
            name = "%replacedBy",
            tier = Tier.RESERVED,
            scopes = setOf(Scope.TYPE_DEFINITION, Scope.FIELD_DEFINITION),
            valueType = "String",
            description = "Replacement type or field"
        ),
        Directive(
            name = "%title",
            tier = Tier.RESERVED,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "String",
            description = "Human-readable title"
        ),
        Directive(
            name = "%comment",
            tier = Tier.RESERVED,
            scopes = setOf(Scope.TYPE_DEFINITION, Scope.FIELD_DEFINITION),
            valueType = "String",
            description = "Internal comment (not included in output)"
        ),
        Directive(
            name = "%tags",
            tier = Tier.RESERVED,
            scopes = setOf(Scope.TYPE_DEFINITION, Scope.FIELD_DEFINITION),
            valueType = "Array",
            description = "Tags for categorization"
        ),

        // Advanced References
        Directive(
            name = "%ref",
            tier = Tier.RESERVED,
            scopes = setOf(Scope.TYPE_DEFINITION, Scope.FIELD_DEFINITION),
            valueType = "String",
            description = "Reference to external schema"
        ),
        Directive(
            name = "%extends",
            tier = Tier.RESERVED,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "String",
            description = "Type extension/inheritance"
        ),
        Directive(
            name = "%typedef",
            tier = Tier.RESERVED,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "String",
            description = "Type alias definition"
        ),
        Directive(
            name = "%alignment",
            tier = Tier.RESERVED,
            scopes = setOf(Scope.FIELD_DEFINITION),
            valueType = "Integer",
            description = "Memory alignment (Cap'n Proto, FlatBuffers)"
        ),
        Directive(
            name = "%generic",
            tier = Tier.RESERVED,
            scopes = setOf(Scope.TYPE_DEFINITION),
            valueType = "Array",
            description = "Generic type parameters"
        )
    )

    /**
     * All directives by tier
     */
    val ALL_DIRECTIVES: Map<Tier, List<Directive>> = mapOf(
        Tier.CORE to TIER1_DIRECTIVES,
        Tier.COMMON to TIER2_DIRECTIVES,
        Tier.FORMAT_SPECIFIC to TIER3_DIRECTIVES,
        Tier.RESERVED to TIER4_DIRECTIVES
    )

    /**
     * All directives as flat list
     */
    val ALL_DIRECTIVES_FLAT: List<Directive> =
        TIER1_DIRECTIVES + TIER2_DIRECTIVES + TIER3_DIRECTIVES + TIER4_DIRECTIVES

    /**
     * Directive name lookup map
     */
    val DIRECTIVE_MAP: Map<String, Directive> =
        ALL_DIRECTIVES_FLAT.associateBy { it.name }

    /**
     * Valid directive names set
     */
    val VALID_DIRECTIVE_NAMES: Set<String> = DIRECTIVE_MAP.keys

    /**
     * Get directive by name
     */
    fun getDirective(name: String): Directive? = DIRECTIVE_MAP[name]

    /**
     * Check if directive name is valid
     */
    fun isValidDirective(name: String): Boolean = name in VALID_DIRECTIVE_NAMES

    /**
     * Get directives by tier
     */
    fun getDirectivesByTier(tier: Tier): List<Directive> = ALL_DIRECTIVES[tier] ?: emptyList()

    /**
     * Get directives by scope
     */
    fun getDirectivesByScope(scope: Scope): List<Directive> =
        ALL_DIRECTIVES_FLAT.filter { scope in it.scopes }

    /**
     * Get supported formats for a directive
     */
    fun getSupportedFormats(directiveName: String): Set<String> =
        getDirective(directiveName)?.supportedFormats ?: emptySet()

    /**
     * Check if directive is supported for format
     */
    fun isDirectiveSupportedForFormat(directiveName: String, format: String): Boolean {
        val directive = getDirective(directiveName) ?: return false
        return format in directive.supportedFormats
    }
}
