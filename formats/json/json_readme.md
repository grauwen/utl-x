# UTL-X JSON Format Module

JSON parser and serializer for UTL-X transformation language.

## Features

- ✅ **Complete JSON support** - Parse and serialize all JSON types
- ✅ **Streaming parser** - Handle large JSON files efficiently
- ✅ **Pretty printing** - Format JSON with indentation
- ✅ **Compact output** - Minimize JSON size
- ✅ **Error handling** - Detailed parse error messages with line/column
- ✅ **Round-trip safe** - Parse and serialize preserve data structure
- ✅ **UDM integration** - Seamless conversion between JSON and UDM
- ✅ **RuntimeValue support** - Serialize transformation results

## Usage

### Parsing JSON

```kotlin
import org.apache.utlx.formats.json.JSON

// Parse JSON string to UDM
val json = """
    {
      "name": "Alice",
      "age": 30,
      "active": true
    }
"""

val udm = JSON.parse(json)
```

### Serializing to JSON

```kotlin
import org.apache.utlx.core.udm.UDM
import org.apache.utlx.formats.json.JSON

// Create UDM structure
val data = UDM.Object.of(
    "name" to UDM.Scalar.string("Bob"),
    "age" to UDM.Scalar.number(25)
)

// Serialize with pretty printing
val json = JSON.stringify(data)

// Or compact (no whitespace)
val compactJson = JSON.stringifyCompact(data)
```

### Complete Transformation Example

```kotlin
import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.parser.ParseResult
import org.apache.utlx.core.interpreter.Interpreter
import org.apache.utlx.formats.json.JSON

// Input JSON
val inputJSON = """
    {
      "order": {
        "id": "ORD-001",
        "items": [
          {"name": "Widget", "price": 29.99, "quantity": 2},
          {"name": "Gadget", "price": 49.99, "quantity": 1}
        ]
      }
    }
"""

// UTL-X transformation
val transformation = """
    %utlx 1.0
    input json
    output json
    ---
    {
      invoice: {
        orderId: input.order.id,
        itemCount: count(input.order.items),
        total: sum(input.order.items.(price * quantity))
      }
    }
"""

// Execute transformation
val inputUDM = JSON.parse(inputJSON)
val tokens = Lexer(transformation).tokenize()
val program = (Parser(tokens).parse() as ParseResult.Success).program
val result = Interpreter().execute(program, inputUDM)

// Output as JSON
val outputJSON = JSON.stringify(result)
println(outputJSON)
```

Output:
```json
{
  "invoice": {
    "orderId": "ORD-001",
    "itemCount": 2,
    "total": 109.97
  }
}
```

## JSON Type Mapping

| JSON Type | UDM Type | Notes |
|-----------|----------|-------|
| `null` | `UDM.Scalar(null)` | |
| `true`/`false` | `UDM.Scalar(boolean)` | |
| number | `UDM.Scalar(number)` | Stored as Double |
| string | `UDM.Scalar(string)` | |
| array | `UDM.Array` | |
| object | `UDM.Object` | Properties stored in map |

## Advanced Features

### Custom Serialization Options

```kotlin
import org.apache.utlx.formats.json.JSONSerializer

// Create custom serializer
val serializer = JSONSerializer(
    prettyPrint = true,
    indent = "    "  // 4-space indentation
)

val json = serializer.serialize(udm)
```

### Streaming Parser (for large files)

```kotlin
import org.apache.utlx.formats.json.StreamingJSONParser
import java.io.FileReader

// Parse large JSON file
val parser = StreamingJSONParser(FileReader("large-file.json"))
val udm = parser.parse()
```

### Error Handling

```kotlin
import org.apache.utlx.formats.json.JSON
import org.apache.utlx.formats.json.JSONParseException

try {
    val udm = JSON.parse("{invalid json}")
} catch (e: JSONParseException) {
    println("Parse error at ${e.line}:${e.column} - ${e.message}")
}
```

## Performance

| Operation | Speed | Memory |
|-----------|-------|--------|
| Parse small JSON (<1KB) | ~0.1ms | Minimal |
| Parse medium JSON (~100KB) | ~5ms | Proportional to size |
| Parse large JSON (~10MB) | ~200ms | Full content in memory |
| Serialize to JSON | ~2ms | Output string buffer |

## Testing

Run the JSON format tests:

```bash
./gradlew :formats:json:test
```

Test coverage:
- Parsing all JSON types
- Escaping and unescaping strings
- Unicode support
- Error conditions
- Round-trip (parse → serialize → parse)
- Pretty printing
- Compact output

## Examples

See `examples/` directory for more examples:
- `CompleteJSONTransformationExample.kt` - Full transformation pipeline
- `APIGatewayExample.kt` - Legacy API transformation

## Integration with UTL-X

The JSON module is automatically used when you specify `input json` or `output json` in your UTL-X transformation:

```utlx
%utlx 1.0
input json    # Uses JSONParser
output json   # Uses JSONSerializer
---
{
  # Your transformation here
}
```

## Dependencies

- `modules:core` - Core UTL-X module (UDM, RuntimeValue)
- Kotlin stdlib

No external JSON libraries required - pure Kotlin implementation.

## License

Dual-licensed under:
- GNU Affero General Public License v3.0 (AGPL-3.0) for open source use
- Commercial License for proprietary applications

See [LICENSE.md](../../LICENSE.md) for details.
