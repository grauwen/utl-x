# PrettyPrint Functions - Integration Guide

## Location
**File:** `stdlib/src/main/kotlin/org/apache/utlx/stdlib/serialization/PrettyPrintFunctions.kt`

## Functions.kt Registration

Add the following to `stdlib/src/main/kotlin/org/apache/utlx/stdlib/Functions.kt`:

```kotlin
// ==================== IMPORTS ====================
import org.apache.utlx.stdlib.serialization.PrettyPrintFunctions

// ==================== INSIDE registerAllFunctions() ====================

// Pretty-Print Functions (18 functions)
register("prettyPrintJson") { args ->
    when (args[0]) {
        is UDM.String -> {
            val indent = (args.getOrNull(1) as? UDM.Number)?.value?.toInt() ?: 2
            UDM.String(PrettyPrintFunctions.prettyPrintJson(
                (args[0] as UDM.String).value, indent
            ))
        }
        else -> {
            val indent = (args.getOrNull(1) as? UDM.Number)?.value?.toInt() ?: 2
            UDM.String(PrettyPrintFunctions.prettyPrintJson(args[0], indent))
        }
    }
}

register("compactJson") { args ->
    requireString(args[0], "compactJson")
    UDM.String(PrettyPrintFunctions.compactJson((args[0] as UDM.String).value))
}

register("prettyPrintXml") { args ->
    when (args[0]) {
        is UDM.String -> {
            val indent = (args.getOrNull(1) as? UDM.Number)?.value?.toInt() ?: 2
            val preserveWs = (args.getOrNull(2) as? UDM.Boolean)?.value ?: false
            UDM.String(PrettyPrintFunctions.prettyPrintXml(
                (args[0] as UDM.String).value, indent, preserveWs
            ))
        }
        else -> {
            val indent = (args.getOrNull(1) as? UDM.Number)?.value?.toInt() ?: 2
            val preserveWs = (args.getOrNull(2) as? UDM.Boolean)?.value ?: false
            UDM.String(PrettyPrintFunctions.prettyPrintXml(args[0], indent, preserveWs))
        }
    }
}

register("compactXml") { args ->
    requireString(args[0], "compactXml")
    UDM.String(PrettyPrintFunctions.compactXml((args[0] as UDM.String).value))
}

register("prettyPrintYaml") { args ->
    when (args[0]) {
        is UDM.String -> {
            val indent = (args.getOrNull(1) as? UDM.Number)?.value?.toInt() ?: 2
            val flowStyle = (args.getOrNull(2) as? UDM.Boolean)?.value ?: false
            UDM.String(PrettyPrintFunctions.prettyPrintYaml(
                (args[0] as UDM.String).value, indent, flowStyle
            ))
        }
        else -> {
            val indent = (args.getOrNull(1) as? UDM.Number)?.value?.toInt() ?: 2
            val flowStyle = (args.getOrNull(2) as? UDM.Boolean)?.value ?: false
            UDM.String(PrettyPrintFunctions.prettyPrintYaml(args[0], indent, flowStyle))
        }
    }
}

register("prettyPrintCsv") { args ->
    requireString(args[0], "prettyPrintCsv")
    val delimiter = (args.getOrNull(1) as? UDM.String)?.value ?: ","
    val align = (args.getOrNull(2) as? UDM.Boolean)?.value ?: true
    UDM.String(PrettyPrintFunctions.prettyPrintCsv(
        (args[0] as UDM.String).value, delimiter, align
    ))
}

register("compactCsv") { args ->
    requireString(args[0], "compactCsv")
    val delimiter = (args.getOrNull(1) as? UDM.String)?.value ?: ","
    UDM.String(PrettyPrintFunctions.compactCsv(
        (args[0] as UDM.String).value, delimiter
    ))
}

register("prettyPrint") { args ->
    when {
        args.size == 1 && args[0] is UDM.String -> {
            // Auto-detect format from string
            UDM.String(PrettyPrintFunctions.prettyPrint((args[0] as UDM.String).value))
        }
        args.size >= 2 && args[1] is UDM.String -> {
            // Format specified: prettyPrint(udm, "json")
            val format = (args[1] as UDM.String).value
            val indent = (args.getOrNull(2) as? UDM.Number)?.value?.toInt() ?: 2
            UDM.String(PrettyPrintFunctions.prettyPrint(args[0], format, indent))
        }
        else -> {
            val indent = (args.getOrNull(1) as? UDM.Number)?.value?.toInt() ?: 2
            UDM.String(PrettyPrintFunctions.prettyPrint((args[0] as UDM.String).value, indent))
        }
    }
}

register("debugPrint") { args ->
    val indent = (args.getOrNull(1) as? UDM.Number)?.value?.toInt() ?: 2
    val maxDepth = (args.getOrNull(2) as? UDM.Number)?.value?.toInt() ?: 10
    UDM.String(PrettyPrintFunctions.debugPrint(args[0], indent, maxDepth))
}

register("debugPrintCompact") { args ->
    val maxLength = (args.getOrNull(1) as? UDM.Number)?.value?.toInt() ?: 200
    UDM.String(PrettyPrintFunctions.debugPrintCompact(args[0], maxLength))
}
```

## Usage Examples in UTL-X

### JSON Pretty-Printing
```utlx
%utlx 1.0
input json
output json
---
{
  // Reformat embedded JSON string
  formattedConfig: prettyPrintJson(input.configString),
  
  // Compact JSON for transmission
  compactData: compactJson(input.verboseJson),
  
  // Pretty-print UDM object
  debugOutput: prettyPrintJson(input.customer, 4)
}
```

### XML Pretty-Printing
```utlx
%utlx 1.0
input xml
output json
---
{
  // Format embedded XML
  formattedXml: prettyPrintXml(input.EmbeddedXML, 2),
  
  // Compact XML for storage
  compactXml: compactXml(input.VerboseXML)
}
```

### Auto-Detection
```utlx
%utlx 1.0
input json
output json
---
{
  // Auto-detect and format
  formatted: prettyPrint(input.unknownFormat),
  
  // Explicit format
  asJson: prettyPrint(input.data, "json"),
  asXml: prettyPrint(input.data, "xml")
}
```

### Debug Helpers
```utlx
%utlx 1.0
input json
output json
---
{
  // Inspect complex structure
  customerDebug: debugPrint(input.customer),
  
  // Compact debug for logs
  quickCheck: debugPrintCompact(input.order)
}
```

## Function Signatures

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `prettyPrintJson(str, indent?)` | String, Number? | String | Pretty-print JSON string |
| `prettyPrintJson(udm, indent?)` | UDM, Number? | String | Format UDM as JSON |
| `compactJson(str)` | String | String | Remove whitespace from JSON |
| `prettyPrintXml(str, indent?, preserve?)` | String, Number?, Boolean? | String | Pretty-print XML string |
| `prettyPrintXml(udm, indent?, preserve?)` | UDM, Number?, Boolean? | String | Format UDM as XML |
| `compactXml(str)` | String | String | Remove whitespace from XML |
| `prettyPrintYaml(str, indent?, flow?)` | String, Number?, Boolean? | String | Pretty-print YAML string |
| `prettyPrintYaml(udm, indent?, flow?)` | UDM, Number?, Boolean? | String | Format UDM as YAML |
| `prettyPrintCsv(str, delim?, align?)` | String, String?, Boolean? | String | Format CSV with column alignment |
| `compactCsv(str, delim?)` | String, String? | String | Remove extra whitespace from CSV |
| `prettyPrint(str, indent?)` | String, Number? | String | Auto-detect format and pretty-print |
| `prettyPrint(udm, format, indent?)` | UDM, String, Number? | String | Format UDM as specified format |
| `debugPrint(udm, indent?, depth?)` | UDM, Number?, Number? | String | Human-readable debug output |
| `debugPrintCompact(udm, maxLen?)` | UDM, Number? | String | Compact debug output |

## Real-World Use Cases

### 1. **API Gateway - Logging**
```utlx
// Log request/response with readable format
{
  logEntry: {
    timestamp: now(),
    request: debugPrint(input.request),
    response: prettyPrintJson(input.response)
  }
}
```

### 2. **Config File Transformation**
```utlx
// Convert compact JSON config to pretty XML
{
  xmlConfig: prettyPrintXml(
    parseJson(input.compactJsonConfig),
    4
  )
}
```

### 3. **Data Migration - CSV Alignment**
```utlx
// Make CSV reports more readable
{
  report: prettyPrintCsv(input.csvData, ",", true)
}
```

### 4. **Message Queue - Compact Payload**
```utlx
// Remove whitespace before sending to queue
{
  message: compactJson(renderJson(input.payload))
}
```

### 5. **Debug Console**
```utlx
// Interactive debugging
{
  quickView: debugPrintCompact(input.complexObject, 100),
  detailedView: debugPrint(input.complexObject, 2, 5)
}
```

## Implementation Notes

1. **Dependencies**: Requires format parsers/serializers (JSON, XML, YAML, CSV)
2. **Error Handling**: All functions validate input and throw descriptive errors
3. **Performance**: Parse/serialize cycle adds overhead - use for debugging, not production transforms
4. **Memory**: Debug functions limit depth/length to prevent excessive memory use
5. **Auto-Detection**: Uses heuristics - may fail on ambiguous data

## Testing Strategy

See `PrettyPrintFunctionsTest.kt` for comprehensive tests covering:
- All format variations (JSON, XML, YAML, CSV)
- Edge cases (empty, nested, large data)
- Error conditions (invalid input)
- Performance (large documents)
- Debug output formats

## Documentation Links

- **Serialization Guide**: `stdlib/docs/serialization_usage_guide.md`
- **Format Specs**: `docs/formats/`
- **Function Reference**: `docs/reference/stdlib-reference.md`
