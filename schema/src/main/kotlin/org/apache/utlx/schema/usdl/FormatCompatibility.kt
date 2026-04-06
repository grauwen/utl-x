package org.apache.utlx.schema.usdl

/**
 * Format compatibility metadata for USDL directives
 *
 * Defines supported formats and their compatibility percentages with USDL directive tiers.
 * Used by DirectiveRegistry to provide format-specific directive filtering.
 */
object FormatCompatibility {

    /**
     * Format metadata including name, abbreviation, and tier support percentages
     */
    data class FormatMetadata(
        val name: String,
        val abbreviation: String,
        val tier1Support: Int,  // Percentage: 0-100
        val tier2Support: Int,  // Percentage: 0-100
        val tier3Support: Int,  // Percentage: 0-100
        val overallSupport: Int, // Weighted average
        val notes: String,
        val domain: String  // "data-schema", "rest-api", "messaging", "database", "entity-model"
    )

    /**
     * All supported formats with their compatibility metadata
     */
    val FORMATS = mapOf(
        // ===== Data Schema Formats =====
        "xsd" to FormatMetadata(
            name = "XML Schema Definition",
            abbreviation = "xsd",
            tier1Support = 100,
            tier2Support = 95,
            tier3Support = 40,
            overallSupport = 95,
            notes = "Best coverage, some XSD-specific directives",
            domain = "data-schema"
        ),

        "jsch" to FormatMetadata(
            name = "JSON Schema",
            abbreviation = "jsch",
            tier1Support = 100,
            tier2Support = 90,
            tier3Support = 45,
            overallSupport = 90,
            notes = "Excellent coverage, OpenAPI adds more",
            domain = "data-schema"
        ),

        "proto" to FormatMetadata(
            name = "Protocol Buffers",
            abbreviation = "proto",
            tier1Support = 100,
            tier2Support = 80,
            tier3Support = 60,
            overallSupport = 85,
            notes = "Needs %fieldNumber, limited constraints",
            domain = "data-schema"
        ),

        "avro" to FormatMetadata(
            name = "Apache Avro",
            abbreviation = "avro",
            tier1Support = 100,
            tier2Support = 75,
            tier3Support = 50,
            overallSupport = 80,
            notes = "Needs %logicalType, %precision, %scale",
            domain = "data-schema"
        ),

        "avsc" to FormatMetadata(
            name = "Avro Schema",
            abbreviation = "avsc",
            tier1Support = 100,
            tier2Support = 75,
            tier3Support = 50,
            overallSupport = 80,
            notes = "Alias for avro format",
            domain = "data-schema"
        ),

        // ===== REST API Formats =====
        "openapi" to FormatMetadata(
            name = "OpenAPI Specification",
            abbreviation = "openapi",
            tier1Support = 100,
            tier2Support = 90,
            tier3Support = 55,
            overallSupport = 85,
            notes = "JSON Schema + REST extensions",
            domain = "rest-api"
        ),

        "raml" to FormatMetadata(
            name = "RESTful API Modeling Language",
            abbreviation = "raml",
            tier1Support = 100,
            tier2Support = 80,
            tier3Support = 35,
            overallSupport = 75,
            notes = "REST API modeling",
            domain = "rest-api"
        ),

        "apiblueprint" to FormatMetadata(
            name = "API Blueprint",
            abbreviation = "apiblueprint",
            tier1Support = 100,
            tier2Support = 75,
            tier3Support = 30,
            overallSupport = 70,
            notes = "Markdown-based API documentation",
            domain = "rest-api"
        ),

        // ===== Messaging / Event-Driven Formats =====
        "asyncapi" to FormatMetadata(
            name = "AsyncAPI Specification",
            abbreviation = "asyncapi",
            tier1Support = 100,
            tier2Support = 85,
            tier3Support = 40,
            overallSupport = 80,
            notes = "Event-driven, messaging protocols",
            domain = "messaging"
        ),

        "grpc" to FormatMetadata(
            name = "gRPC",
            abbreviation = "grpc",
            tier1Support = 100,
            tier2Support = 80,
            tier3Support = 55,
            overallSupport = 80,
            notes = "Uses Protobuf, adds RPC operations",
            domain = "messaging"
        ),

        // ===== Database Formats =====
        "sql" to FormatMetadata(
            name = "SQL DDL",
            abbreviation = "sql",
            tier1Support = 100,
            tier2Support = 70,
            tier3Support = 70,
            overallSupport = 75,
            notes = "Needs many database-specific directives",
            domain = "database"
        ),

        // ===== Entity Model Formats =====
        "odata" to FormatMetadata(
            name = "OData",
            abbreviation = "odata",
            tier1Support = 100,
            tier2Support = 50,
            tier3Support = 50,
            overallSupport = 60,
            notes = "Entity/navigation focus",
            domain = "entity-model"
        ),

        "graphql" to FormatMetadata(
            name = "GraphQL Schema",
            abbreviation = "graphql",
            tier1Support = 100,
            tier2Support = 60,
            tier3Support = 30,
            overallSupport = 70,
            notes = "Good type coverage, limited constraints",
            domain = "entity-model"
        ),

        // ===== Other Binary Serialization Formats =====
        "thrift" to FormatMetadata(
            name = "Apache Thrift",
            abbreviation = "thrift",
            tier1Support = 100,
            tier2Support = 70,
            tier3Support = 40,
            overallSupport = 70,
            notes = "Similar to Protobuf",
            domain = "data-schema"
        ),

        "parquet" to FormatMetadata(
            name = "Apache Parquet",
            abbreviation = "parquet",
            tier1Support = 100,
            tier2Support = 60,
            tier3Support = 45,
            overallSupport = 65,
            notes = "Columnar storage",
            domain = "data-schema"
        ),

        "capnp" to FormatMetadata(
            name = "Cap'n Proto",
            abbreviation = "capnp",
            tier1Support = 100,
            tier2Support = 70,
            tier3Support = 50,
            overallSupport = 70,
            notes = "Zero-copy serialization",
            domain = "data-schema"
        ),

        "flatbuf" to FormatMetadata(
            name = "FlatBuffers",
            abbreviation = "flatbuf",
            tier1Support = 100,
            tier2Support = 65,
            tier3Support = 45,
            overallSupport = 65,
            notes = "Game/performance focus",
            domain = "data-schema"
        )
    )

    /**
     * Get format metadata by abbreviation
     */
    fun getFormatMetadata(abbreviation: String): FormatMetadata? {
        return FORMATS[abbreviation]
    }

    /**
     * Get all formats in a specific domain
     */
    fun getFormatsByDomain(domain: String): List<Pair<String, FormatMetadata>> {
        return FORMATS.filter { it.value.domain == domain }.toList()
    }

    /**
     * Get all format abbreviations
     */
    fun getAllFormatAbbreviations(): List<String> {
        return FORMATS.keys.toList()
    }

    /**
     * Calculate average compatibility across all formats
     */
    fun getAverageCompatibility(): Int {
        val formats = FORMATS.values
        return if (formats.isEmpty()) 0
        else formats.map { it.overallSupport }.average().toInt()
    }

    /**
     * Get formats sorted by compatibility (descending)
     */
    fun getFormatsByCompatibility(): List<Pair<String, FormatMetadata>> {
        return FORMATS.toList().sortedByDescending { it.second.overallSupport }
    }
}
