// formats/yaml/src/main/kotlin/org/apache/utlx/examples/YAMLTransformationExample.kt
package org.apache.utlx.examples

import org.apache.utlx.core.udm.*
import org.apache.utlx.formats.yaml.*

/**
 * Example demonstrating YAML parsing and serialization with UTL-X
 */
fun main() {
    println("=".repeat(60))
    println("UTL-X YAML Format Example")
    println("=".repeat(60))
    println()
    
    // Example 1: Parse Simple YAML
    simpleYAMLExample()
    
    // Example 2: Parse Complex YAML
    complexYAMLExample()
    
    // Example 3: Serialize UDM to YAML
    serializationExample()
    
    // Example 4: Round-trip transformation
    roundTripExample()
    
    // Example 5: Multi-document YAML
    multiDocumentExample()
    
    // Example 6: YAML to JSON conversion (via UDM)
    yamlToJsonExample()
}

fun simpleYAMLExample() {
    println("Example 1: Simple YAML Parsing")
    println("-".repeat(60))
    
    val yaml = """
        name: John Doe
        age: 30
        email: john.doe@example.com
        active: true
    """.trimIndent()
    
    println("Input YAML:")
    println(yaml)
    println()
    
    val udm = YAMLParser.parseYAML(yaml)
    println("Parsed UDM:")
    println(udm)
    println()
    
    // Access values
    val obj = udm as UDM.Object
    println("Name: ${(obj.properties["name"] as UDM.Scalar).value}")
    println("Age: ${(obj.properties["age"] as UDM.Scalar).value}")
    println("Email: ${(obj.properties["email"] as UDM.Scalar).value}")
    println("Active: ${(obj.properties["active"] as UDM.Scalar).value}")
    println()
}

fun complexYAMLExample() {
    println("Example 2: Complex YAML with Nested Structures")
    println("-".repeat(60))
    
    val yaml = """
        company:
          name: Acme Corp
          founded: 2020
          locations:
            - city: New York
              country: USA
              employees: 150
            - city: London
              country: UK
              employees: 75
          products:
            - name: Widget
              price: 19.99
              inStock: true
            - name: Gadget
              price: 49.99
              inStock: false
    """.trimIndent()
    
    println("Input YAML:")
    println(yaml)
    println()
    
    val udm = YAMLParser.parseYAML(yaml)
    val company = (udm as UDM.Object).properties["company"] as UDM.Object
    
    println("Company Name: ${(company.properties["name"] as UDM.Scalar).value}")
    println("Founded: ${(company.properties["founded"] as UDM.Scalar).value}")
    
    val locations = company.properties["locations"] as UDM.Array
    println("\nLocations:")
    locations.elements.forEach { loc ->
        val location = loc as UDM.Object
        println("  - ${(location.properties["city"] as UDM.Scalar).value}, " +
                "${(location.properties["country"] as UDM.Scalar).value}: " +
                "${(location.properties["employees"] as UDM.Scalar).value} employees")
    }
    
    val products = company.properties["products"] as UDM.Array
    println("\nProducts:")
    products.elements.forEach { prod ->
        val product = prod as UDM.Object
        val name = (product.properties["name"] as UDM.Scalar).value
        val price = (product.properties["price"] as UDM.Scalar).value
        val inStock = (product.properties["inStock"] as UDM.Scalar).value as Boolean
        println("  - $name: $$price (${if (inStock) "In Stock" else "Out of Stock"})")
    }
    println()
}

fun serializationExample() {
    println("Example 3: Serialize UDM to YAML")
    println("-".repeat(60))
    
    // Create UDM structure
    val udm = UDM.Object(mapOf(
        "order" to UDM.Object(mapOf(
            "id" to UDM.Scalar("ORD-12345"),
            "date" to UDM.Scalar("2025-10-14"),
            "customer" to UDM.Object(mapOf(
                "name" to UDM.Scalar("Jane Smith"),
                "email" to UDM.Scalar("jane@example.com")
            )),
            "items" to UDM.Array(listOf(
                UDM.Object(mapOf(
                    "product" to UDM.Scalar("Widget"),
                    "quantity" to UDM.Scalar(2.0),
                    "price" to UDM.Scalar(19.99)
                )),
                UDM.Object(mapOf(
                    "product" to UDM.Scalar("Gadget"),
                    "quantity" to UDM.Scalar(1.0),
                    "price" to UDM.Scalar(49.99)
                ))
            )),
            "total" to UDM.Scalar(89.97)
        ))
    ))
    
    println("Generated YAML (Pretty):")
    val prettyYaml = YAMLSerializer.toYAML(udm, pretty = true)
    println(prettyYaml)
    println()
    
    println("Generated YAML (Compact Flow Style):")
    val compactYaml = YAMLSerializer.toCompactYAML(udm)
    println(compactYaml)
    println()
}

fun roundTripExample() {
    println("Example 4: Round-trip (YAML → UDM → YAML)")
    println("-".repeat(60))
    
    val originalYaml = """
        person:
          name: Alice Johnson
          age: 28
          skills:
            - Kotlin
            - Java
            - Python
          experience:
            years: 5
            level: senior
    """.trimIndent()
    
    println("Original YAML:")
    println(originalYaml)
    println()
    
    // Parse
    val udm = YAMLParser.parseYAML(originalYaml)
    
    // Serialize back
    val regeneratedYaml = YAMLSerializer.toYAML(udm)
    println("Regenerated YAML:")
    println(regeneratedYaml)
    println()
    
    // Verify they parse to the same structure
    val udm2 = YAMLParser.parseYAML(regeneratedYaml)
    println("Round-trip successful: ${udm == udm2}")
    println()
}

fun multiDocumentExample() {
    println("Example 5: Multi-document YAML")
    println("-".repeat(60))
    
    val multiDoc = """
        ---
        title: Document 1
        content: First document
        ---
        title: Document 2
        content: Second document
        ---
        title: Document 3
        content: Third document
    """.trimIndent()
    
    println("Input (Multi-document YAML):")
    println(multiDoc)
    println()
    
    val options = YAMLParser.ParseOptions(multiDocument = true)
    val udm = YAMLParser().parse(multiDoc, options)
    
    val documents = udm as UDM.Array
    println("Parsed ${documents.elements.size} documents:")
    documents.elements.forEachIndexed { index, doc ->
        val obj = doc as UDM.Object
        val title = (obj.properties["title"] as UDM.Scalar).value
        val content = (obj.properties["content"] as UDM.Scalar).value
        println("  ${index + 1}. $title: $content")
    }
    println()
    
    // Serialize back to multi-document
    println("Regenerated Multi-document YAML:")
    val regenerated = YAMLSerializer().serializeMultiDocument(documents.elements)
    println(regenerated)
    println()
}

fun yamlToJsonExample() {
    println("Example 6: YAML to JSON Conversion (via UDM)")
    println("-".repeat(60))
    
    val yaml = """
        api:
          version: 1.0
          endpoints:
            - path: /users
              methods: [GET, POST]
            - path: /users/{id}
              methods: [GET, PUT, DELETE]
          authentication:
            type: bearer
            required: true
    """.trimIndent()
    
    println("Input YAML:")
    println(yaml)
    println()
    
    // Parse YAML
    val udm = YAMLParser.parseYAML(yaml)
    
    // Convert to JSON (using hypothetical JSON serializer)
    // In real implementation, you would use:
    // val json = JSONSerializer.toJSON(udm)
    println("Converted to JSON (conceptual):")
    println("This would use JSONSerializer.toJSON(udm)")
    println()
    
    println("UDM (Universal Data Model) acts as the bridge:")
    println("  YAML → UDM → JSON")
    println("  XML ← UDM ← CSV")
    println("  etc.")
    println()
}
