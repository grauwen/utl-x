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
    val obj = udm as UDMObject
    println("Name: ${(obj.properties["name"] as UDMString).value}")
    println("Age: ${(obj.properties["age"] as UDMNumber).longValue}")
    println("Email: ${(obj.properties["email"] as UDMString).value}")
    println("Active: ${(obj.properties["active"] as UDMBoolean).value}")
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
    val company = (udm as UDMObject).properties["company"] as UDMObject
    
    println("Company Name: ${(company.properties["name"] as UDMString).value}")
    println("Founded: ${(company.properties["founded"] as UDMNumber).longValue}")
    
    val locations = company.properties["locations"] as UDMArray
    println("\nLocations:")
    locations.elements.forEach { loc ->
        val location = loc as UDMObject
        println("  - ${(location.properties["city"] as UDMString).value}, " +
                "${(location.properties["country"] as UDMString).value}: " +
                "${(location.properties["employees"] as UDMNumber).longValue} employees")
    }
    
    val products = company.properties["products"] as UDMArray
    println("\nProducts:")
    products.elements.forEach { prod ->
        val product = prod as UDMObject
        val name = (product.properties["name"] as UDMString).value
        val price = (product.properties["price"] as UDMNumber).value
        val inStock = (product.properties["inStock"] as UDMBoolean).value
        println("  - $name: $$price (${if (inStock) "In Stock" else "Out of Stock"})")
    }
    println()
}

fun serializationExample() {
    println("Example 3: Serialize UDM to YAML")
    println("-".repeat(60))
    
    // Create UDM structure
    val udm = UDMObject(mapOf(
        "order" to UDMObject(mapOf(
            "id" to UDMString("ORD-12345"),
            "date" to UDMString("2025-10-14"),
            "customer" to UDMObject(mapOf(
                "name" to UDMString("Jane Smith"),
                "email" to UDMString("jane@example.com")
            )),
            "items" to UDMArray(listOf(
                UDMObject(mapOf(
                    "product" to UDMString("Widget"),
                    "quantity" to UDMNumber(2.0, 2),
                    "price" to UDMNumber(19.99, 19)
                )),
                UDMObject(mapOf(
                    "product" to UDMString("Gadget"),
                    "quantity" to UDMNumber(1.0, 1),
                    "price" to UDMNumber(49.99, 49)
                ))
            )),
            "total" to UDMNumber(89.97, 89)
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
    
    val documents = udm as UDMArray
    println("Parsed ${documents.elements.size} documents:")
    documents.elements.forEachIndexed { index, doc ->
        val obj = doc as UDMObject
        val title = (obj.properties["title"] as UDMString).value
        val content = (obj.properties["content"] as UDMString).value
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
