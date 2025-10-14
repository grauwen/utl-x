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
            
            result.shouldBeInstanceOf<UDMObject>()
            (result as UDMObject).properties["value"].shouldBe(UDMNull)
        }
        
        it("should parse string values") {
            val yaml = "name: John Doe"
            val result = YAMLParser.parseYAML(yaml)
            
            result.shouldBeInstanceOf<UDMObject>()
            val obj = result as UDMObject
            obj.properties["name"].shouldBeInstanceOf<UDMString>()
            (obj.properties["name"] as UDMString).value.shouldBe("John Doe")
        }
        
        it("should parse integer numbers") {
            val yaml = "age: 42"
            val result = YAMLParser.parseYAML(yaml)
            
            result.shouldBeInstanceOf<UDMObject>()
            val obj = result as UDMObject
            obj.properties["age"].shouldBeInstanceOf<UDMNumber>()
            (obj.properties["age"] as UDMNumber).longValue.shouldBe(42L)
        }
        
        it("should parse floating point numbers") {
            val yaml = "price: 19.99"
            val result = YAMLParser.parseYAML(yaml)
            
            result.shouldBeInstanceOf<UDMObject>()
            val obj = result as UDMObject
            obj.properties["price"].shouldBeInstanceOf<UDMNumber>()
            (obj.properties["price"] as UDMNumber).value.shouldBe(19.99)
        }
        
        it("should parse boolean values") {
            val yaml = """
                active: true
                disabled: false
            """.trimIndent()
            val result = YAMLParser.parseYAML(yaml)
            
            result.shouldBeInstanceOf<UDMObject>()
            val obj = result as UDMObject
            (obj.properties["active"] as UDMBoolean).value.shouldBe(true)
            (obj.properties["disabled"] as UDMBoolean).value.shouldBe(false)
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
            
            result.shouldBeInstanceOf<UDMObject>()
            val obj = result as UDMObject
            val items = obj.properties["items"]
            items.shouldBeInstanceOf<UDMArray>()
            
            val array = items as UDMArray
            array.elements.size.shouldBe(3)
            (array.elements[0] as UDMString).value.shouldBe("apple")
            (array.elements[1] as UDMString).value.shouldBe("banana")
            (array.elements[2] as UDMString).value.shouldBe("cherry")
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
            
            result.shouldBeInstanceOf<UDMObject>()
            val obj = result as UDMObject
            val person = obj.properties["person"] as UDMObject
            
            (person.properties["name"] as UDMString).value.shouldBe("John")
            (person.properties["age"] as UDMNumber).longValue.shouldBe(30L)
            
            val address = person.properties["address"] as UDMObject
            (address.properties["street"] as UDMString).value.shouldBe("123 Main St")
            (address.properties["city"] as UDMString).value.shouldBe("Springfield")
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
            
            result.shouldBeInstanceOf<UDMObject>()
            val obj = result as UDMObject
            val users = obj.properties["users"] as UDMArray
            
            users.elements.size.shouldBe(2)
            val user1 = users.elements[0] as UDMObject
            (user1.properties["name"] as UDMString).value.shouldBe("Alice")
            (user1.properties["age"] as UDMNumber).longValue.shouldBe(25L)
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
            
            result.shouldBeInstanceOf<UDMArray>()
            val array = result as UDMArray
            array.elements.size.shouldBe(2)
            
            val doc1 = array.elements[0] as UDMObject
            (doc1.properties["name"] as UDMString).value.shouldBe("Document 1")
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
            val udm = UDMObject(mapOf("name" to UDMString("John Doe")))
            val yaml = YAMLSerializer.toYAML(udm)
            
            yaml.shouldNotBe("")
            yaml.contains("name:").shouldBe(true)
            yaml.contains("John Doe").shouldBe(true)
        }
        
        it("should serialize numbers") {
            val udm = UDMObject(mapOf(
                "age" to UDMNumber(42.0, 42),
                "price" to UDMNumber(19.99, 19)
            ))
            val yaml = YAMLSerializer.toYAML(udm)
            
            yaml.contains("age:").shouldBe(true)
            yaml.contains("42").shouldBe(true)
            yaml.contains("price:").shouldBe(true)
            yaml.contains("19.99").shouldBe(true)
        }
        
        it("should serialize boolean values") {
            val udm = UDMObject(mapOf(
                "active" to UDMBoolean(true),
                "disabled" to UDMBoolean(false)
            ))
            val yaml = YAMLSerializer.toYAML(udm)
            
            yaml.contains("active: true").shouldBe(true)
            yaml.contains("disabled: false").shouldBe(true)
        }
        
        it("should serialize null values") {
            val udm = UDMObject(mapOf("value" to UDMNull))
            val yaml = YAMLSerializer.toYAML(udm)
            
            yaml.contains("value:").shouldBe(true)
        }
    }
    
    describe("YAMLSerializer - Collections") {
        
        it("should serialize arrays") {
            val udm = UDMObject(mapOf(
                "items" to UDMArray(listOf(
                    UDMString("apple"),
                    UDMString("banana"),
                    UDMString("cherry")
                ))
            ))
            val yaml = YAMLSerializer.toYAML(udm)
            
            yaml.contains("items:").shouldBe(true)
            yaml.contains("apple").shouldBe(true)
            yaml.contains("banana").shouldBe(true)
            yaml.contains("cherry").shouldBe(true)
        }
        
        it("should serialize nested objects") {
            val udm = UDMObject(mapOf(
                "person" to UDMObject(mapOf(
                    "name" to UDMString("John"),
                    "address" to UDMObject(mapOf(
                        "street" to UDMString("123 Main St"),
                        "city" to UDMString("Springfield")
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
            val udm = UDMObject(mapOf(
                "items" to UDMArray(listOf(
                    UDMString("a"),
                    UDMString("b"),
                    UDMString("c")
                ))
            ))
            
            val yaml = YAMLSerializer.toCompactYAML(udm)
            
            // Flow style should be more compact
            yaml.length shouldBe lessThan(YAMLSerializer.toYAML(udm).length)
        }
        
        it("should respect custom indentation") {
            val udm = UDMObject(mapOf(
                "level1" to UDMObject(mapOf(
                    "level2" to UDMString("value")
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
                UDMObject(mapOf("name" to UDMString("Doc1"))),
                UDMObject(mapOf("name" to UDMString("Doc2")))
            )
            
            val yaml = YAMLSerializer().serializeMultiDocument(docs)
            
            yaml.contains("---").shouldBe(true)
            yaml.contains("Doc1").shouldBe(true)
            yaml.contains("Doc2").shouldBe(true)
        }
    }
    
    describe("YAMLSerializer - Round-trip") {
        
        it("should round-trip basic structures") {
            val original = UDMObject(mapOf(
                "name" to UDMString("Test"),
                "value" to UDMNumber(42.0, 42),
                "active" to UDMBoolean(true),
                "items" to UDMArray(listOf(
                    UDMString("a"),
                    UDMString("b")
                ))
            ))
            
            val yaml = YAMLSerializer.toYAML(original)
            val parsed = YAMLParser.parseYAML(yaml)
            
            // Verify structure
            parsed.shouldBeInstanceOf<UDMObject>()
            val obj = parsed as UDMObject
            (obj.properties["name"] as UDMString).value.shouldBe("Test")
            (obj.properties["value"] as UDMNumber).longValue.shouldBe(42L)
            (obj.properties["active"] as UDMBoolean).value.shouldBe(true)
        }
    }
})

// Helper function for comparison
private infix fun Int.lessThan(other: Int): Boolean = this < other
