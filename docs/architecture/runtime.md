# Runtime Architecture

Detailed documentation of UTL-X runtime execution.

## Runtime Overview

The runtime executes compiled UTL-X transformations.

```
Compiled Transform
        │
        ▼
┌───────────────┐
│  Load & Init  │
└───────┬───────┘
        │
        ▼
┌───────────────┐
│ Parse Input   │ → UDM
└───────┬───────┘
        │
        ▼
┌───────────────┐
│   Execute     │ → Transform UDM
└───────┬───────┘
        │
        ▼
┌───────────────┐
│Serialize Out  │ → Output
└───────────────┘
```

## JVM Runtime

### Execution Model

```kotlin
class JVMRuntime(private val compiledTransform: CompiledTransform) {
    fun execute(input: UDM): UDM {
        val context = ExecutionContext()
        return compiledTransform.execute(input, context)
    }
}

data class ExecutionContext(
    val variables: MutableMap<String, UDM> = mutableMapOf(),
    val functions: Map<String, Function> = standardLibrary
)
```

### Stack-Based Execution

```kotlin
class StackMachine {
    private val stack = mutableListOf<UDM>()
    
    fun execute(instructions: List<Instruction>) {
        for (instruction in instructions) {
            when (instruction) {
                is Push -> stack.add(instruction.value)
                is Pop -> stack.removeLast()
                is Add -> {
                    val right = stack.removeLast()
                    val left = stack.removeLast()
                    stack.add(add(left, right))
                }
                // ... more instructions
            }
        }
    }
}
```

## JavaScript Runtime

### Execution Model

```javascript
class JavaScriptRuntime {
    constructor(compiledTransform) {
        this.transform = compiledTransform;
    }
    
    execute(input) {
        const context = {
            input: input,
            functions: standardLibrary
        };
        return this.transform(context);
    }
}
```

### Function Generation

```javascript
// Generated JavaScript
(function(context) {
    'use strict';
    const input = context.input;
    const {map, filter, sum} = context.functions;
    
    return {
        result: map($input.items, item => item.price * 2)
    };
})
```

## Native Runtime

### Compilation to Native

```
UTL-X → LLVM IR → Machine Code
```

### Performance Benefits

- **No JVM/Node.js overhead**: Direct machine code
- **Ahead-of-time compilation**: No startup cost
- **Memory efficiency**: Minimal runtime footprint

## Standard Library Implementation

### JVM Implementation

```kotlin
object StandardLibrary {
    val functions = mapOf<String, Function>(
        "sum" to Function { args ->
            val array = args[0] as UDM.Array
            val total = array.elements
                .mapNotNull { (it as? UDM.Scalar)?.value as? Double }
                .sum()
            UDM.Scalar(NumberValue(total))
        },
        
        "map" to Function { args ->
            val array = args[0] as UDM.Array
            val fn = args[1] as LambdaFunction
            val mapped = array.elements.map { fn.apply(it) }
            UDM.Array(mapped)
        }
        
        // ... more functions
    )
}
```

### JavaScript Implementation

```javascript
const standardLibrary = {
    sum: (array) => {
        return array.elements
            .map(e => e.value)
            .reduce((a, b) => a + b, 0);
    },
    
    map: (array, fn) => {
        return {
            type: 'array',
            elements: array.elements.map(fn)
        };
    }
    
    // ... more functions
};
```

## Memory Management

### JVM

- **Garbage Collection**: Automatic
- **Object Pooling**: For frequently allocated objects
- **Weak References**: For caches

### JavaScript

- **Garbage Collection**: Automatic (V8/SpiderMonkey)
- **Memory Limits**: Configurable heap size

### Native

- **Manual Management**: Reference counting or arena allocation
- **No GC Pauses**: Predictable performance

## Error Handling

### Runtime Errors

```kotlin
sealed class RuntimeError : Exception() {
    data class DivisionByZero(val location: SourceLocation) : RuntimeError()
    data class NullPointer(val path: String, val location: SourceLocation) : RuntimeError()
    data class TypeMismatch(val expected: Type, val actual: Type) : RuntimeError()
    data class StackOverflow(val recursionDepth: Int) : RuntimeError()
}
```

### Error Recovery

```kotlin
fun executeWithRecovery(transform: CompiledTransform, input: UDM): Result<UDM> {
    return try {
        Result.Success(transform.execute(input))
    } catch (e: RuntimeError) {
        Result.Failure(e)
    }
}
```

## Concurrency

### Thread Safety

All UDM data structures are immutable, making them thread-safe.

### Parallel Execution

```kotlin
fun parallelTransform(inputs: List<UDM>): List<UDM> {
    return inputs.parallelStream()
        .map { input -> runtime.execute(input) }
        .toList()
}
```

## Streaming

### Streaming Parser

```kotlin
class StreamingXMLParser {
    fun parse(input: InputStream): Sequence<UDM> = sequence {
        val reader = XMLStreamReader(input)
        while (reader.hasNext()) {
            yield(parseElement(reader))
        }
    }
}
```

### Streaming Transformer

```kotlin
fun streamingTransform(
    input: Sequence<UDM>,
    transform: CompiledTransform
): Sequence<UDM> = sequence {
    $input.forEach { element ->
        yield(transform.execute(element))
    }
}
```

## Performance Monitoring

### Metrics

```kotlin
data class RuntimeMetrics(
    val executionTime: Duration,
    val memoryUsed: Long,
    val nodesProcessed: Int,
    val cacheHits: Int,
    val cacheMisses: Int
)

class MetricsCollector {
    fun measure(block: () -> UDM): Pair<UDM, RuntimeMetrics> {
        val startTime = System.nanoTime()
        val startMemory = Runtime.getRuntime().totalMemory()
        
        val result = block()
        
        val endTime = System.nanoTime()
        val endMemory = Runtime.getRuntime().totalMemory()
        
        return result to RuntimeMetrics(
            executionTime = Duration.ofNanos(endTime - startTime),
            memoryUsed = endMemory - startMemory,
            nodesProcessed = countNodes(result),
            cacheHits = cacheHitCount,
            cacheMisses = cacheMissCount
        )
    }
}
```

---
