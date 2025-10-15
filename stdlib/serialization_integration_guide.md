# Serialization Functions - Integration Guide

## File Location

```
stdlib/src/main/kotlin/org/apache/utlx/stdlib/serialization/SerializationFunctions.kt
```

## Integration into Functions.kt

### Step 1: Add Import Statement

In `stdlib/src/main/kotlin/org/apache/utlx/stdlib/Functions.kt`, add:

```kotlin
// Near the top of the file with other imports
import org.apache.utlx.stdlib.serialization.*
```

### Step 2: Register Functions in Function Map

Add these entries to the function registry map:

```kotlin
object Functions {
    private val functionRegistry = mapOf(
        // ... existing functions ...
        
        // ========================================
        // SERIALIZATION FUNCTIONS (Tibco BW parse/render equivalents)
        // ========================================
        
        // JSON Parse/Render
        "parseJson" to FunctionDescriptor(
            name = "parseJson",
            description = "Parse JSON string into object",
            parameters = listOf(
                ParameterDescriptor("jsonString", "String", "JSON string to parse")
            ),
            returnType = "Any",
            implementation = ::parseJson,
            category = "serialization",
            examples = listOf(
                "parseJson('{\"name\":\"John\"}')",
                "let data = parseJson(input.jsonField)"
            )
        ),
        
        "renderJson" to FunctionDescriptor(
            name = "renderJson",
            description = "Render object as JSON string",
            parameters = listOf(
                ParameterDescriptor("obj", "Any", "Object to serialize"),
                ParameterDescriptor("pretty", "Boolean", "Pretty-print output", optional = true, default = "false")
            ),
            returnType = "String",
            implementation = ::renderJson,
            category = "serialization",
            examples = listOf(
                "renderJson(customer)",
                "renderJson(order, pretty=true)"
            )
        ),
        
        // XML Parse/Render
        "parseXml" to FunctionDescriptor(
            name = "parseXml",
            description = "Parse XML string into object",
            parameters = listOf(
                ParameterDescriptor("xmlString", "String", "XML string to parse")
            ),
            returnType = "Any",
            implementation = ::parseXml,
            category = "serialization",
            examples = listOf(
                "parseXml('<customer><name>John</name></customer>')",
                "let soapEnv = parseXml(input.soapPayload)"
            )
        ),
        
        "renderXml" to FunctionDescriptor(
            name = "renderXml",
            description = "Render object as XML string",
            parameters = listOf(
                ParameterDescriptor("obj", "Any", "Object to serialize"),
                ParameterDescriptor("pretty", "Boolean", "Pretty-print output", optional = true, default = "false"),
                ParameterDescriptor("declaration", "Boolean", "Include XML declaration", optional = true, default = "true")
            ),
            returnType = "String",
            implementation = ::renderXml,
            category = "serialization",
            examples = listOf(
                "renderXml(customer)",
                "renderXml(order, pretty=true, declaration=false)"
            )
        ),
        
        // YAML Parse/Render
        "parseYaml" to FunctionDescriptor(
            name = "parseYaml",
            description = "Parse YAML string into object",
            parameters = listOf(
                ParameterDescriptor("yamlString", "String", "YAML string to parse")
            ),
            returnType = "Any",
            implementation = ::parseYaml,
            category = "serialization",
            examples = listOf(
                "parseYaml('name: John\\nage: 30')",
                "let config = parseYaml(input.yamlConfig)"
            )
        ),
        
        "renderYaml" to FunctionDescriptor(
            name = "renderYaml",
            description = "Render object as YAML string",
            parameters = listOf(
                ParameterDescriptor("obj", "Any", "Object to serialize")
            ),
            returnType = "String",
            implementation = ::renderYaml,
            category = "serialization",
            examples = listOf(
                "renderYaml(config)",
                "renderYaml({ name: 'John', age: 30 })"
            )
        ),
        
        // CSV Parse/Render
        "parseCsv" to FunctionDescriptor(
            name = "parseCsv",
            description = "Parse CSV string into array of objects",
            parameters = listOf(
                ParameterDescriptor("csvString", "String", "CSV string to parse"),
                ParameterDescriptor("delimiter", "String", "Field delimiter", optional = true, default = "\",\""),
                ParameterDescriptor("headers", "Boolean", "First row contains headers", optional = true, default = "true")
            ),
            returnType = "Array",
            implementation = ::parseCsv,
            category = "serialization",
            examples = listOf(
                "parseCsv('id,name\\n1,John\\n2,Jane')",
                "parseCsv(input.csvData, delimiter='|', headers=true)"
            )
        ),
        
        "renderCsv" to FunctionDescriptor(
            name = "renderCsv",
            description = "Render array as CSV string",
            parameters = listOf(
                ParameterDescriptor("arr", "Array", "Array to serialize"),
                ParameterDescriptor("delimiter", "String", "Field delimiter", optional = true, default = "\",\""),
                ParameterDescriptor("headers", "Boolean", "Include header row", optional = true, default = "true")
            ),
            returnType = "String",
            implementation = ::renderCsv,
            category = "serialization",
            examples = listOf(
                "renderCsv(customers)",
                "renderCsv(data, delimiter='\\t', headers=false)"
            )
        ),
        
        // Generic Parse/Render
        "parse" to FunctionDescriptor(
            name = "parse",
            description = "Parse string with auto-detection or explicit format",
            parameters = listOf(
                ParameterDescriptor("str", "String", "String to parse"),
                ParameterDescriptor("format", "String", "Format name (json/xml/yaml/csv)", optional = true)
            ),
            returnType = "Any",
            implementation = ::parse,
            category = "serialization",
            examples = listOf(
                "parse(input.unknownFormat)",
                "parse(input.data, 'json')",
                "parse(input.xmlString, 'xml')"
            )
        ),
        
        "render" to FunctionDescriptor(
            name = "render",
            description = "Render object in specified format",
            parameters = listOf(
                ParameterDescriptor("obj", "Any", "Object to serialize"),
                ParameterDescriptor("format", "String", "Format name (json/xml/yaml/csv)"),
                ParameterDescriptor("pretty", "Boolean", "Pretty-print output", optional = true, default = "false")
            ),
            returnType = "String",
            implementation = ::render,
            category = "serialization",
            examples = listOf(
                "render(customer, 'json')",
                "render(order, 'xml', pretty=true)"
            )
        ),
        
        // Tibco BW Compatibility Aliases
        "tibco_parse" to FunctionDescriptor(
            name = "tibco_parse",
            description = "Tibco BW compatible parse function (alias for parse)",
            parameters = listOf(
                ParameterDescriptor("str", "String", "String to parse"),
                ParameterDescriptor("format", "String", "Format name", optional = true)
            ),
            returnType = "Any",
            implementation = ::parse,
            category = "compatibility",
            examples = listOf(
                "tibco_parse(input.data)",
                "tibco_parse(input.xml, 'xml')"
            )
        ),
        
        "tibco_render" to FunctionDescriptor(
            name = "tibco_render",
            description = "Tibco BW compatible render function (alias for render)",
            parameters = listOf(
                ParameterDescriptor("obj", "Any", "Object to serialize"),
                ParameterDescriptor("format", "String", "Format name"),
                ParameterDescriptor("pretty", "Boolean", "Pretty-print", optional = true, default = "false")
            ),
            returnType = "String",
            implementation = ::render,
            category = "compatibility",
            examples = listOf(
                "tibco_render(customer, 'json')",
                "tibco_render(order, 'xml', true)"
            )
        ),
        
        // ... rest of existing functions ...
    )
}
```

## Step 3: Add to Function Categories

Update the category groupings:

```kotlin
object FunctionCategories {
    val categories = mapOf(
        // ... existing categories ...
        
        "serialization" to CategoryDescriptor(
            name = "Serialization",
            description = "Parse and render functions for nested format handling",
            functions = listOf(
                "parseJson", "renderJson",
                "parseXml", "renderXml",
                "parseYaml", "renderYaml",
                "parseCsv", "renderCsv",
                "parse", "render"
            )
        ),
        
        "compatibility" to CategoryDescriptor(
            name = "Compatibility",
            description = "Functions for compatibility with other systems",
            functions = listOf(
                "tibco_parse", "tibco_render"
            )
        )
    )
}
```

## Step 4: Add to Documentation Index

In `stdlib/stlib_complete_refernce.md`, add new section:

```markdown
## 12. Serialization Functions

Functions for parsing and rendering nested formats (similar to Tibco BW parse/render).

### 12.1 JSON Parse/Render
- `parseJson(jsonString: String): Any`
- `renderJson(obj: Any, pretty: Boolean = false): String`

### 12.2 XML Parse/Render
- `parseXml(xmlString: String): Any`
- `renderXml(obj: Any, pretty: Boolean = false, declaration: Boolean = true): String`

### 12.3 YAML Parse/Render
- `parseYaml(yamlString: String): Any`
- `renderYaml(obj: Any): String`

### 12.4 CSV Parse/Render
- `parseCsv(csvString: String, delimiter: String = ",", headers: Boolean = true): Array`
- `renderCsv(arr: Array, delimiter: String = ",", headers: Boolean = true): String`

### 12.5 Generic Parse/Render
- `parse(str: String): Any` - Auto-detect format
- `parse(str: String, format: String): Any` - Explicit format
- `render(obj: Any, format: String, pretty: Boolean = false): String`

### 12.6 Tibco BW Compatibility
- `tibco_parse(str: String, format: String? = null): Any`
- `tibco_render(obj: Any, format: String, pretty: Boolean = false): String`
```

## Step 5: Update Build Configuration

Ensure the serialization module is included in `stdlib/build.gradle.kts`:

```kotlin
dependencies {
    // ... existing dependencies ...
    
    // Format parsers/serializers
    implementation(project(":formats:json"))
    implementation(project(":formats:xml"))
    implementation(project(":formats:yaml"))
    implementation(project(":formats:csv"))
}
```

## Step 6: Add Unit Tests

Create `stdlib/src/test/kotlin/org/apache/utlx/stdlib/serialization/SerializationFunctionsTest.kt`:

```kotlin
package org.apache.utlx.stdlib.serialization

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SerializationFunctionsTest {
    
    @Test
    fun `parseJson should parse valid JSON string`() {
        val json = """{"name": "John", "age": 30}"""
        val result = parseJson(json)
        // Assert result structure
    }
    
    @Test
    fun `renderJson should produce valid JSON`() {
        val obj = mapOf("name" to "John", "age" to 30)
        val json = renderJson(obj)
        assertTrue(json.contains("\"name\""))
        assertTrue(json.contains("John"))
    }
    
    @Test
    fun `parseXml should parse valid XML string`() {
        val xml = "<customer><name>John</name></customer>"
        val result = parseXml(xml)
        // Assert result structure
    }
    
    @Test
    fun `parse should auto-detect JSON`() {
        val json = """{"test": true}"""
        val result = parse(json)
        // Assert it was parsed as JSON
    }
    
    @Test
    fun `parse should auto-detect XML`() {
        val xml = "<root><test>true</test></root>"
        val result = parse(xml)
        // Assert it was parsed as XML
    }
    
    @Test
    fun `parseCsv should parse CSV with headers`() {
        val csv = "id,name\n1,John\n2,Jane"
        val result = parseCsv(csv)
        // Assert array of objects with id and name properties
    }
    
    @Test
    fun `invalid JSON should throw ParseException`() {
        assertThrows<ParseException> {
            parseJson("{invalid json")
        }
    }
}
```

## Summary of Changes

### Files Created:
1. `stdlib/src/main/kotlin/org/apache/utlx/stdlib/serialization/SerializationFunctions.kt`
2. `stdlib/src/test/kotlin/org/apache/utlx/stdlib/serialization/SerializationFunctionsTest.kt`

### Files Modified:
1. `stdlib/src/main/kotlin/org/apache/utlx/stdlib/Functions.kt` - Add imports and function registrations
2. `stdlib/stlib_complete_refernce.md` - Add documentation section
3. `stdlib/build.gradle.kts` - Add format parser dependencies

### Function Count:
- **12 new functions** added to stdlib
- **2 compatibility aliases** for Tibco BW users
- **Total: 14 callable functions**

## Migration from Tibco BW

For users migrating from Tibco BW:

| Tibco BW | UTL-X Equivalent |
|----------|------------------|
| `parse($var1, "JSON/Text")` | `parseJson($var1)` |
| `parse($var1, "XML/Text")` | `parseXml($var1)` |
| `parse($var1, "CSV/Text")` | `parseCsv($var1)` |
| `renderJSON($var1)` | `renderJson($var1)` |
| `renderXML($var1)` | `renderXml($var1)` |
| N/A | `parseYaml($var1)` (new in UTL-X) |
| N/A | `renderYaml($var1)` (new in UTL-X) |

Or use the compatibility functions:
- `tibco_parse($var1, "json")` → Same as Tibco BW
- `tibco_render($var1, "xml")` → Same as Tibco BW
