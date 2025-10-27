# PrettyPrint Functions for UTL-X - Decision & Rationale

## **RECOMMENDATION: YES - Implement PrettyPrint Functions** ✅

## Executive Summary

After analyzing the UTL-X stdlib architecture and real-world use cases, **I strongly recommend adding dedicated pretty-print functions** for JSON, XML, YAML, and CSV. Here's why:

---

## Three-Layer Architecture

UTL-X needs formatting capabilities at **three distinct layers**:

### 1️⃣ **Serialization Layer** (Already Exists)
```kotlin
// Format parsers with options
JSONSerializer(prettyPrint = true, indent = 2)
XMLSerializer(prettyPrint = true, indent = 4)
```
**Use Case:** Initial transformation output formatting

### 2️⃣ **Runtime Functions** (MISSING - Proposed Solution)
```utlx
// Callable from UTL-X code
prettyPrintJson(compactJsonString)
prettyPrintXml(embeddedXml)
debugPrint(complexObject)
```
**Use Case:** Reformatting existing strings, debugging, logging

### 3️⃣ **Output Directives** (Part of Language Spec)
```utlx
%utlx 1.0
output json {
  prettyPrint: true
}
```
**Use Case:** Declarative output formatting

---

## Why We Need Stdlib PrettyPrint Functions

### **Problem 1: Nested/Embedded Formats**

Real-world data often contains **formats within formats**:

```json
{
  "messageId": "12345",
  "payload": "{\"customer\":\"Alice\",\"order\":100}",
  "metadata": "<xml><status>pending</status></xml>"
}
```

**Without PrettyPrint Functions:**
```utlx
// Can't format the embedded strings!
{
  payload: input.payload,  // Still compact JSON string
  metadata: input.metadata  // Still compact XML string
}
```

**With PrettyPrint Functions:**
```utlx
{
  payload: prettyPrintJson(input.payload),
  metadata: prettyPrintXml(input.metadata)
}
```

### **Problem 2: REST API Wrapping SOAP**

```json
{
  "apiVersion": "v2",
  "soapRequest": "<soap:Envelope>...</soap:Envelope>",
  "response": "{\"status\":\"ok\"}"
}
```

Need to:
1. Parse the outer JSON ✅ (already works)
2. **Format the embedded SOAP XML** ❌ (needs prettyPrintXml)
3. **Format the embedded JSON response** ❌ (needs prettyPrintJson)

### **Problem 3: Debugging Complex Transformations**

```utlx
// User needs to see intermediate state
let intermediate = complexTransformation(input)

// How to inspect it?
log(intermediate)  // UDM.Object@1a2b3c (not helpful!)

// Solution:
log(debugPrint(intermediate))  // Human-readable structure
```

### **Problem 4: Log-Friendly Output**

```utlx
// Production logging
{
  timestamp: now(),
  request: compactJson(input.request),      // Save space
  response: prettyPrintJson(output.data),   // Human-readable
  debug: debugPrintCompact(internalState)   // Quick overview
}
```

---

## Comparison with Other Languages

| Language | Pretty-Print Support | How It Works |
|----------|---------------------|--------------|
| **DataWeave** | ❌ No dedicated functions | Uses `write()` with format options |
| **XSLT** | ✅ Yes | `<xsl:output indent="yes"/>` |
| **JSONata** | ❌ No | Only output formatting |
| **JQ** | ✅ Yes | `--pretty` flag and `@json` format |
| **Python** | ✅ Yes | `json.dumps(indent=2)`, `pprint()` |
| **JavaScript** | ✅ Yes | `JSON.stringify(null, 2)` |

**Lesson:** Most mature languages provide explicit formatting functions, not just output directives.

---

## Real-World Use Cases

### 1. **Enterprise Integration - Message Queue**
```utlx
// Receive compact JSON, transform, send pretty XML
{
  xmlPayload: prettyPrintXml(
    parseJson(input.compactJsonMessage),
    4  // 4-space indent for SOAP
  )
}
```

### 2. **API Gateway - Request/Response Logging**
```utlx
{
  logEntry: {
    timestamp: now(),
    method: input.method,
    path: input.path,
    requestBody: prettyPrintJson(input.body),    // Readable
    responseBody: compactJson(output.response),   // Compact
    debug: debugPrintCompact(input.headers, 100)  // Quick peek
  }
}
```

### 3. **Data Migration - Config File Transformation**
```utlx
// Convert compact JSON config to pretty YAML for human editing
{
  yamlConfig: prettyPrintYaml(
    parseJson(input.compactJsonConfig)
  )
}
```

### 4. **CSV Report Generation**
```utlx
// Make CSV reports readable with aligned columns
{
  report: prettyPrintCsv(
    renderCsv(input.data),
    ",",      // delimiter
    true      // align columns
  )
}
```

### 5. **Development/Debugging**
```utlx
// Interactive debugging during development
{
  // Quick overview
  summary: debugPrintCompact(input.order),
  
  // Detailed inspection
  details: debugPrint(input.order, 2, 5),
  
  // Format-specific views
  asJson: prettyPrintJson(input.order),
  asXml: prettyPrintXml(input.order)
}
```

---

## Proposed Function Set (10 Functions)

| Function | Purpose | Returns |
|----------|---------|---------|
| `prettyPrintJson(str/udm, indent?)` | Format JSON | String |
| `prettyPrintXml(str/udm, indent?, preserve?)` | Format XML | String |
| `prettyPrintYaml(str/udm, indent?, flow?)` | Format YAML | String |
| `prettyPrintCsv(str, delim?, align?)` | Format CSV | String |
| `compactJson(str)` | Remove JSON whitespace | String |
| `compactXml(str)` | Remove XML whitespace | String |
| `compactCsv(str, delim?)` | Remove CSV whitespace | String |
| `prettyPrint(str/udm, format?)` | Auto-detect or explicit | String |
| `debugPrint(udm, indent?, depth?)` | Debug output | String |
| `debugPrintCompact(udm, maxLen?)` | Quick debug | String |

---

## Implementation Strategy

### Phase 1: Core Functions (Week 1)
- ✅ JSON pretty-print/compact
- ✅ XML pretty-print/compact
- ✅ Auto-detect prettyPrint()

### Phase 2: Extended Formats (Week 2)
- ✅ YAML pretty-print
- ✅ CSV formatting
- ✅ Format-specific options

### Phase 3: Debug Helpers (Week 3)
- ✅ debugPrint() for deep inspection
- ✅ debugPrintCompact() for logging
- ✅ Depth/length limits

### Phase 4: Testing & Documentation (Week 4)
- ✅ Comprehensive unit tests
- ✅ Integration examples
- ✅ Performance benchmarks

---

## Performance Considerations

### ⚠️ **Warning: Parse/Serialize Overhead**

```utlx
// This is EXPENSIVE (parse → serialize)
prettyPrintJson(input.jsonString)

// This is FREE (already UDM)
renderJson(input.udmObject, { prettyPrint: true })
```

**Guideline:**
- Use `prettyPrint*()` functions for **debugging, logging, embedded strings**
- Use serializer options for **primary output formatting**

### Optimization Strategies

1. **Cache parsed UDM**: Don't parse same string multiple times
2. **Streaming for large docs**: Don't load entire doc into memory
3. **Lazy evaluation**: Only format when needed
4. **Depth limits**: Prevent runaway recursion

---

## Integration with Existing Stdlib

### Dependencies
```kotlin
// Requires format modules
import org.apache.utlx.formats.json.*
import org.apache.utlx.formats.xml.*
import org.apache.utlx.formats.yaml.*
import org.apache.utlx.formats.csv.*
```

### Registration in Functions.kt
```kotlin
// Add 10 new functions
register("prettyPrintJson") { ... }
register("prettyPrintXml") { ... }
register("prettyPrintYaml") { ... }
register("prettyPrintCsv") { ... }
register("compactJson") { ... }
register("compactXml") { ... }
register("compactCsv") { ... }
register("prettyPrint") { ... }
register("debugPrint") { ... }
register("debugPrintCompact") { ... }
```

### Total Function Count
```
Before: ~150 functions
After:  ~160 functions (+10)
```

---

## Documentation Requirements

### 1. **Stdlib Reference**
Add section: "Serialization & Formatting Functions"

### 2. **Usage Guide**
Document when to use:
- Output directives vs. runtime functions
- PrettyPrint vs. serializer options
- Debug helpers for development

### 3. **Migration Guide**
For users coming from:
- DataWeave: `write()` → `prettyPrint*()`
- XSLT: No equivalent, explain benefits
- Python/JS: Similar to `json.dumps()` / `JSON.stringify()`

### 4. **Performance Guide**
- Parse/serialize overhead
- Best practices
- Caching strategies

---

## Alternative Approaches Considered

### ❌ **Alternative 1: Only Output Directives**
```utlx
output json {
  prettyPrint: true
}
```
**Problem:** Can't format embedded strings or debug intermediate state

### ❌ **Alternative 2: Only Serializer Options**
```utlx
renderJson(data, { prettyPrint: true })
```
**Problem:** Requires UDM object, can't reformat existing strings

### ❌ **Alternative 3: Format-Specific Objects**
```utlx
JSON.prettify(string)
XML.prettify(string)
```
**Problem:** Not functional style, inconsistent with rest of stdlib

### ✅ **Recommended: Dedicated Stdlib Functions**
```utlx
prettyPrintJson(string)
prettyPrintXml(string)
prettyPrint(string)  // Auto-detect
```
**Benefits:**
- Functional style (pure functions)
- Consistent with stdlib patterns
- Handles both strings and UDM
- Flexible and composable

---

## Success Criteria

### Must Have
- ✅ Format JSON, XML, YAML, CSV strings
- ✅ Format UDM objects to any format
- ✅ Auto-detect format
- ✅ Debug helpers for development
- ✅ Comprehensive error handling
- ✅ Full test coverage

### Should Have
- Custom indentation
- Format-specific options
- Performance optimizations
- Memory safety (depth/length limits)

### Nice to Have
- Syntax highlighting (future)
- Interactive REPL formatting (future)
- Format validation (future)

---

## Final Recommendation

**✅ PROCEED with implementation**

**Priority:** High  
**Effort:** Medium (2-3 weeks)  
**Value:** High (enables critical use cases)  
**Risk:** Low (no breaking changes)

**Next Steps:**
1. ✅ Review this proposal
2. ✅ Create implementation branch
3. ✅ Implement core functions (JSON, XML)
4. ✅ Add tests and documentation
5. ✅ Extend to YAML, CSV
6. ✅ Add debug helpers
7. ✅ Performance testing
8. ✅ Merge to main

---

## Appendix: Function Signatures

```kotlin
// JSON
fun prettyPrintJson(jsonString: String, indent: Int = 2): String
fun prettyPrintJson(udm: UDM, indent: Int = 2): String
fun compactJson(jsonString: String): String

// XML
fun prettyPrintXml(xmlString: String, indent: Int = 2, preserveWhitespace: Boolean = false): String
fun prettyPrintXml(udm: UDM, indent: Int = 2, preserveWhitespace: Boolean = false): String
fun compactXml(xmlString: String): String

// YAML
fun prettyPrintYaml(yamlString: String, indent: Int = 2, flowStyle: Boolean = false): String
fun prettyPrintYaml(udm: UDM, indent: Int = 2, flowStyle: Boolean = false): String

// CSV
fun prettyPrintCsv(csvString: String, delimiter: String = ",", alignColumns: Boolean = true): String
fun compactCsv(csvString: String, delimiter: String = ","): String

// Generic
fun prettyPrint(data: String, indent: Int = 2): String  // Auto-detect
fun prettyPrint(udm: UDM, format: String, indent: Int = 2): String  // Explicit format

// Debug
fun debugPrint(udm: UDM, indent: Int = 2, maxDepth: Int = 10): String
fun debugPrintCompact(udm: UDM, maxLength: Int = 200): String
```

---

**Document Status:** ✅ Approved for Implementation  
**Author:** Claude (UTL-X Stdlib Team)  
**Date:** 2025-10-17  
**Version:** 1.0
