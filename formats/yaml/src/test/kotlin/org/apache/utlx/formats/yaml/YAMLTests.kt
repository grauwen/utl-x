// formats/yaml/src/test/kotlin/org/apache/utlx/formats/yaml/YAMLTests.kt
package org.apache.utlx.formats.yaml

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.utlx.core.udm.*
import org.yaml.snakeyaml.DumperOptions
import java.time.Instant

class YAMLParserTest : DescribeSpec({
    
    describe("YAMLParser - Basic Types") {
        
        it("should parse null values") {
            val yaml = "value: null"
            val result = YAMLParser.parseYAML(yaml)
            
            result.shouldBeInstanceOf<UDM.Object>()
            (result as UDM.Object).properties["value"].shouldBe(UDM.Scalar.nullValue())
        }
        
        it("should parse string values") {
            val yaml = "name: John Doe"
            val result = YAMLParser.parseYAML(yaml)
            
            result.shouldBeInstanceOf<UDM.Object>()
            val obj = result as UDM.Object
            obj.properties["name"].shouldBeInstanceOf<UDM.Scalar>()
            (obj.properties["name"] as UDM.Scalar).value.shouldBe("John Doe")
        }
        
        it("should parse integer numbers") {
            val yaml = "age: 42"
            val result = YAMLParser.parseYAML(yaml)
            
            result.shouldBeInstanceOf<UDM.Object>()
            val obj = result as UDM.Object
            obj.properties["age"].shouldBeInstanceOf<UDM.Scalar>()
            (obj.properties["age"] as UDM.Scalar).value.shouldBe(42L)
        }
        
        it("should parse floating point numbers") {
            val yaml = "price: 19.99"
            val result = YAMLParser.parseYAML(yaml)
            
            result.shouldBeInstanceOf<UDM.Object>()
            val obj = result as UDM.Object
            obj.properties["price"].shouldBeInstanceOf<UDM.Scalar>()
            (obj.properties["price"] as UDM.Scalar).value.shouldBe(19.99)
        }
        
        it("should parse boolean values") {
            val yaml = """
                active: true
                disabled: false
            """.trimIndent()
            val result = YAMLParser.parseYAML(yaml)
            
            result.shouldBeInstanceOf<UDM.Object>()
            val obj = result as UDM.Object
            (obj.properties["active"] as UDM.Scalar).value.shouldBe(true)
            (obj.properties["disabled"] as UDM.Scalar).value.shouldBe(false)
        }
    }
    
    describe("YAMLParser - Collections") {
        
        it("should parse arrays") {
            val yaml = """
                items:
                  - apple
                  - banana
                  - cherry
            """.trimIndent()
            val result = YAMLParser.parseYAML(yaml)
            
            result.shouldBeInstanceOf<UDM.Object>()
            val obj = result as UDM.Object
            val items = obj.properties["items"]
            items.shouldBeInstanceOf<UDM.Array>()
            
            val array = items as UDM.Array
            array.elements.size.shouldBe(3)
            (array.elements[0] as UDM.Scalar).value.shouldBe("apple")
            (array.elements[1] as UDM.Scalar).value.shouldBe("banana")
            (array.elements[2] as UDM.Scalar).value.shouldBe("cherry")
        }
        
        it("should parse nested objects") {
            val yaml = """
                person:
                  name: John
                  age: 30
                  address:
                    street: 123 Main St
                    city: Springfield
            """.trimIndent()
            val result = YAMLParser.parseYAML(yaml)
            
            result.shouldBeInstanceOf<UDM.Object>()
            val obj = result as UDM.Object
            val person = obj.properties["person"] as UDM.Object
            
            (person.properties["name"] as UDM.Scalar).value.shouldBe("John")
            (person.properties["age"] as UDM.Scalar).value.shouldBe(30L)
            
            val address = person.properties["address"] as UDM.Object
            (address.properties["street"] as UDM.Scalar).value.shouldBe("123 Main St")
            (address.properties["city"] as UDM.Scalar).value.shouldBe("Springfield")
        }
        
        it("should parse array of objects") {
            val yaml = """
                users:
                  - name: Alice
                    age: 25
                  - name: Bob
                    age: 30
            """.trimIndent()
            val result = YAMLParser.parseYAML(yaml)
            
            result.shouldBeInstanceOf<UDM.Object>()
            val obj = result as UDM.Object
            val users = obj.properties["users"] as UDM.Array
            
            users.elements.size.shouldBe(2)
            val user1 = users.elements[0] as UDM.Object
            (user1.properties["name"] as UDM.Scalar).value.shouldBe("Alice")
            (user1.properties["age"] as UDM.Scalar).value.shouldBe(25L)
        }
    }
    
    describe("YAMLParser - Multi-document") {
        
        it("should parse multi-document YAML") {
            val yaml = """
                ---
                name: Document 1
                value: 100
                ---
                name: Document 2
                value: 200
            """.trimIndent()
            
            val options = YAMLParser.ParseOptions(multiDocument = true)
            val result = YAMLParser().parse(yaml, options)
            
            result.shouldBeInstanceOf<UDM.Array>()
            val array = result as UDM.Array
            array.elements.size.shouldBe(2)
            
            val doc1 = array.elements[0] as UDM.Object
            (doc1.properties["name"] as UDM.Scalar).value.shouldBe("Document 1")
        }
    }
    
    describe("YAMLParser - Error Handling") {
        
        it("should throw exception for invalid YAML") {
            val invalidYaml = """
                key: value
                invalid: [unclosed list
            """.trimIndent()
            
            shouldThrow<YAMLParseException> {
                YAMLParser.parseYAML(invalidYaml)
            }
        }
        
        it("should throw exception for duplicate keys when not allowed") {
            val yaml = """
                name: First
                name: Second
            """.trimIndent()
            
            val options = YAMLParser.ParseOptions(allowDuplicateKeys = false)
            
            shouldThrow<YAMLParseException> {
                YAMLParser().parse(yaml, options)
            }
        }
    }
})

class YAMLSerializerTest : DescribeSpec({
    
    describe("YAMLSerializer - Basic Types") {
        
        it("should serialize string values") {
            val udm = UDM.Object(mapOf("name" to UDM.Scalar("John Doe")))
            val yaml = YAMLSerializer.toYAML(udm)
            
            yaml.shouldNotBe("")
            yaml.contains("name:").shouldBe(true)
            yaml.contains("John Doe").shouldBe(true)
        }
        
        it("should serialize numbers") {
            val udm = UDM.Object(mapOf(
                "age" to UDM.Scalar(42.0),
                "price" to UDM.Scalar(19.99)
            ))
            val yaml = YAMLSerializer.toYAML(udm)
            
            yaml.contains("age:").shouldBe(true)
            yaml.contains("42").shouldBe(true)
            yaml.contains("price:").shouldBe(true)
            yaml.contains("19.99").shouldBe(true)
        }
        
        it("should serialize boolean values") {
            val udm = UDM.Object(mapOf(
                "active" to UDM.Scalar(true),
                "disabled" to UDM.Scalar(false)
            ))
            val yaml = YAMLSerializer.toYAML(udm)
            
            yaml.contains("active: true").shouldBe(true)
            yaml.contains("disabled: false").shouldBe(true)
        }
        
        it("should serialize null values") {
            val udm = UDM.Object(mapOf("value" to UDM.Scalar.nullValue()))
            val yaml = YAMLSerializer.toYAML(udm)
            
            yaml.contains("value:").shouldBe(true)
        }
    }
    
    describe("YAMLSerializer - Collections") {
        
        it("should serialize arrays") {
            val udm = UDM.Object(mapOf(
                "items" to UDM.Array(listOf(
                    UDM.Scalar("apple"),
                    UDM.Scalar("banana"),
                    UDM.Scalar("cherry")
                ))
            ))
            val yaml = YAMLSerializer.toYAML(udm)
            
            yaml.contains("items:").shouldBe(true)
            yaml.contains("apple").shouldBe(true)
            yaml.contains("banana").shouldBe(true)
            yaml.contains("cherry").shouldBe(true)
        }
        
        it("should serialize nested objects") {
            val udm = UDM.Object(mapOf(
                "person" to UDM.Object(mapOf(
                    "name" to UDM.Scalar("John"),
                    "address" to UDM.Object(mapOf(
                        "street" to UDM.Scalar("123 Main St"),
                        "city" to UDM.Scalar("Springfield")
                    ))
                ))
            ))
            val yaml = YAMLSerializer.toYAML(udm)
            
            yaml.contains("person:").shouldBe(true)
            yaml.contains("name: John").shouldBe(true)
            yaml.contains("address:").shouldBe(true)
            yaml.contains("street:").shouldBe(true)
        }
    }
    
    describe("YAMLSerializer - Formatting Options") {
        
        it("should serialize in compact flow style") {
            val udm = UDM.Object(mapOf(
                "items" to UDM.Array(listOf(
                    UDM.Scalar("a"),
                    UDM.Scalar("b"),
                    UDM.Scalar("c")
                ))
            ))
            
            val yaml = YAMLSerializer.toCompactYAML(udm)

            // Flow style should be more compact
            (yaml.length < YAMLSerializer.toYAML(udm).length).shouldBe(true)
        }
        
        it("should respect custom indentation") {
            val udm = UDM.Object(mapOf(
                "level1" to UDM.Object(mapOf(
                    "level2" to UDM.Scalar("value")
                ))
            ))
            
            val options = YAMLSerializer.SerializeOptions(indent = 4)
            val yaml = YAMLSerializer().serialize(udm, options)
            
            yaml.shouldNotBe("")
        }
    }
    
    describe("YAMLSerializer - Multi-document") {
        
        it("should serialize multiple documents") {
            val docs = listOf(
                UDM.Object(mapOf("name" to UDM.Scalar("Doc1"))),
                UDM.Object(mapOf("name" to UDM.Scalar("Doc2")))
            )
            
            val yaml = YAMLSerializer().serializeMultiDocument(docs)
            
            yaml.contains("---").shouldBe(true)
            yaml.contains("Doc1").shouldBe(true)
            yaml.contains("Doc2").shouldBe(true)
        }
    }
    
    describe("YAMLSerializer - Round-trip") {
        
        it("should round-trip basic structures") {
            val original = UDM.Object(mapOf(
                "name" to UDM.Scalar("Test"),
                "value" to UDM.Scalar(42.0),
                "active" to UDM.Scalar(true),
                "items" to UDM.Array(listOf(
                    UDM.Scalar("a"),
                    UDM.Scalar("b")
                ))
            ))
            
            val yaml = YAMLSerializer.toYAML(original)
            val parsed = YAMLParser.parseYAML(yaml)
            
            // Verify structure
            parsed.shouldBeInstanceOf<UDM.Object>()
            val obj = parsed as UDM.Object
            (obj.properties["name"] as UDM.Scalar).value.shouldBe("Test")
            (obj.properties["value"] as UDM.Scalar).value.shouldBe(42L)
            (obj.properties["active"] as UDM.Scalar).value.shouldBe(true)
        }
    }
})

// Helper function for comparison
private infix fun Int.lessThan(other: Int): Boolean = this < other
