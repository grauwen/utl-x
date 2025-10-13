# API Reference

Complete reference for embedding UTL-X in applications.

## JVM API (Kotlin/Java)

### UTLXEngine

Main entry point for transformations.

#### Builder Pattern

```kotlin
val engine = UTLXEngine.builder()
    .compile(File("transform.utlx"))
    .build()
```

#### Methods

##### compile(source: File): Builder
Compile UTL-X script from file.

```kotlin
val engine = UTLXEngine.builder()
    .compile(File("transform.utlx"))
    .build()
```

##### compile(source: String): Builder
Compile UTL-X script from string.

```kotlin
val script = """
    %utlx 1.0
    input json
    output json
    ---
    {result: input.value * 2}
"""
val engine = UTLXEngine.builder()
    .compile(script)
    .build()
```

##### withFormat(name: String, parser: FormatParser): Builder
Register custom format parser.

```kotlin
val engine = UTLXEngine.builder()
    .compile(source)
    .withFormat("properties", PropertiesParser())
    .build()
```

##### withOptimization(level: OptimizationLevel): Builder
Set optimization level.

```kotlin
val engine = UTLXEngine.builder()
    .compile(source)
    .withOptimization(OptimizationLevel.AGGRESSIVE)
    .build()
```

##### build(): UTLXEngine
Build the engine.

#### Transform Methods

##### transform(input: String): String
Transform string input to string output.

```kotlin
val output = engine.transform(inputJson)
```

##### transform(input: InputStream, output: OutputStream)
Stream-based transformation.

```kotlin
FileInputStream("input.json").use { input ->
    FileOutputStream("output.json").use { output ->
        engine.transform(input, output)
    }
}
```

##### transform(input: String, outputFormat: Format): String
Override output format.

```kotlin
val xml = engine.transform(jsonInput, Format.XML)
```

### Format

Output format enumeration.

```kotlin
enum class Format {
    JSON,
    XML,
    CSV,
    YAML,
    AUTO
}
```

### OptimizationLevel

```kotlin
enum class OptimizationLevel {
    NONE,
    BASIC,
    AGGRESSIVE
}
```

### Example: Spring Boot Integration

```kotlin
@Configuration
class UTLXConfig {
    @Bean
    fun utlxEngine(): UTLXEngine {
        return UTLXEngine.builder()
            .compile(ClassPathResource("transform.utlx").file)
            .withOptimization(OptimizationLevel.AGGRESSIVE)
            .build()
    }
}

@RestController
class TransformController(
    private val engine: UTLXEngine
) {
    @PostMapping("/transform")
    fun transform(@RequestBody input: String): String {
        return engine.transform(input)
    }
}
```

## JavaScript API (Node.js)

### compile(script: string): Engine

Compile UTL-X script.

```javascript
const utlx = require('@apache/utlx');

const engine = utlx.compile(`
  %utlx 1.0
  input json
  output json
  ---
  {result: input.value * 2}
`);
```

### Engine.transform(input: string, options?: Options): string

Transform data.

```javascript
const output = engine.transform(inputJson);

// With options
const output = engine.transform(inputJson, {
    outputFormat: 'xml',
    pretty: true
});
```

### Options

```typescript
interface Options {
    outputFormat?: 'json' | 'xml' | 'csv' | 'yaml';
    pretty?: boolean;
    indent?: number;
}
```

### Example: Express.js Integration

```javascript
const express = require('express');
const utlx = require('@apache/utlx');
const fs = require('fs');

const app = express();
const engine = utlx.compile(
    fs.readFileSync('transform.utlx', 'utf8')
);

app.post('/transform', express.json(), (req, res) => {
    try {
        const output = engine.transform(JSON.stringify(req.body));
        res.json(JSON.parse(output));
    } catch (error) {
        res.status(400).json({error: error.message});
    }
});

app.listen(3000);
```

### Example: AWS Lambda

```javascript
const utlx = require('@apache/utlx');
const fs = require('fs');

const engine = utlx.compile(
    fs.readFileSync('transform.utlx', 'utf8')
);

exports.handler = async (event) => {
    try {
        const output = engine.transform(event.body);
        return {
            statusCode: 200,
            body: output
        };
    } catch (error) {
        return {
            statusCode: 400,
            body: JSON.stringify({error: error.message})
        };
    }
};
```

## Browser API

### Via CDN

```html
<script src="https://cdn.jsdelivr.net/npm/@apache/utlx@1.0.0/dist/utlx.min.js"></script>
<script>
  const engine = UTLX.compile(`
    %utlx 1.0
    input json
    output json
    ---
    {result: input.value * 2}
  `);
  
  const output = engine.transform('{"value": 21}');
  console.log(output); // {"result": 42}
</script>
```

## Custom Format Parsers

### JVM

```kotlin
interface FormatParser {
    fun canParse(input: InputStream): Boolean
    fun parse(input: InputStream): UDM
}

interface FormatSerializer {
    fun canSerialize(format: String): Boolean
    fun serialize(udm: UDM, output: OutputStream)
}

class PropertiesParser : FormatParser {
    override fun canParse(input: InputStream): Boolean {
        // Detection logic
        return true
    }
    
    override fun parse(input: InputStream): UDM {
        val props = Properties()
        props.load(input)
        return UDM.Object(
            props.entries.associate { 
                it.key.toString() to UDM.Scalar(it.value.toString())
            }
        )
    }
}

// Register
val engine = UTLXEngine.builder()
    .compile(source)
    .withFormat("properties", PropertiesParser())
    .build()
```

### JavaScript

```javascript
class PropertiesParser {
    canParse(input) {
        return input.includes('=');
    }
    
    parse(input) {
        const lines = input.split('\n');
        const obj = {};
        lines.forEach(line => {
            const [key, value] = line.split('=');
            if (key && value) {
                obj[key.trim()] = value.trim();
            }
        });
        return obj;
    }
}

utlx.registerFormat('properties', new PropertiesParser());
```

## Error Handling

### JVM

```kotlin
try {
    val output = engine.transform(input)
} catch (e: UTLXSyntaxError) {
    // Compilation error
    println("Syntax error: ${e.message}")
} catch (e: UTLXTypeError) {
    // Type error
    println("Type error: ${e.message}")
} catch (e: UTLXRuntimeError) {
    // Runtime error
    println("Runtime error: ${e.message}")
}
```

### JavaScript

```javascript
try {
    const output = engine.transform(input);
} catch (error) {
    if (error instanceof utlx.SyntaxError) {
        console.error('Syntax error:', error.message);
    } else if (error instanceof utlx.TypeError) {
        console.error('Type error:', error.message);
    } else if (error instanceof utlx.RuntimeError) {
        console.error('Runtime error:', error.message);
    }
}
```

## Performance Tuning

### Caching Compiled Engines

```kotlin
object EngineCache {
    private val cache = mutableMapOf<String, UTLXEngine>()
    
    fun get(scriptPath: String): UTLXEngine {
        return cache.getOrPut(scriptPath) {
            UTLXEngine.builder()
                .compile(File(scriptPath))
                .build()
        }
    }
}
```

### Streaming Large Files

```kotlin
FileInputStream("large-input.xml").use { input ->
    FileOutputStream("output.json").use { output ->
        engine.transform(input, output)
    }
}
```

### Parallel Processing

```kotlin
val inputs = listOf("input1.json", "input2.json", "input3.json")

inputs.parallelStream()
    .map { inputFile ->
        engine.transform(File(inputFile).readText())
    }
    .forEach { output ->
        // Process output
    }
```
### Multiple Input Transformations

#### JVM API

```kotlin
// Compile transformation with multiple inputs
val engine = UTLXEngine.builder()
    .compile(File("transform.utlx"))
    .build()

// Transform with multiple inputs
val inputs = mapOf(
    "orders" to File("orders.json").readText(),
    "customers" to File("customers.json").readText(),
    "products" to File("products.json").readText()
)

val output = engine.transformMultiple(inputs)

// Or with InputStreams
val inputStreams = mapOf(
    "orders" to FileInputStream("orders.json"),
    "customers" to FileInputStream("customers.json"),
    "products" to FileInputStream("products.json")
)

engine.transformMultiple(inputStreams, FileOutputStream("output.json"))
```

#### JavaScript API

```javascript
const utlx = require('@apache/utlx');
const fs = require('fs');

// Compile transformation
const engine = utlx.compile(
    fs.readFileSync('transform.utlx', 'utf8')
);

// Transform with multiple inputs
const inputs = {
    orders: fs.readFileSync('orders.json', 'utf8'),
    customers: fs.readFileSync('customers.json', 'utf8'),
    products: fs.readFileSync('products.json', 'utf8')
};

const output = engine.transformMultiple(inputs);

// Or with streaming
const inputStreams = {
    orders: fs.createReadStream('orders.json'),
    customers: fs.createReadStream('customers.json'),
    products: fs.createReadStream('products.json')
};

const outputStream = fs.createWriteStream('output.json');
engine.transformMultipleStream(inputStreams, outputStream);
```

---
