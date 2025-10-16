# XML Serialization Options - Integration Summary

## What This Adds to UTL-X

These functions solve **real-world XML integration problems** that standard serializers can't handle:

### 🎯 Core Capabilities

| Capability | Current Status | After Implementation |
|------------|---------------|---------------------|
| **Namespace prefix control** | ❌ Auto-generated (ns1, ns2, etc.) | ✅ Enforced (soap:, api:, custom:) |
| **Empty element formatting** | ❌ Standard only (`<tag/>`) | ✅ Configurable (self-closing, explicit, nil, omit) |
| **Namespace placement** | ❌ Standard (root only) | ✅ Configurable (root, minimal, per-element) |
| **Legacy XML support** | ❌ Well-formed only | ⚠️ Non-well-formed (with warnings) |
| **SOAP compatibility** | ⚠️ Basic | ✅ Full control |
| **XHTML compatibility** | ❌ Not supported | ✅ Explicit closing tags |

---

## File Organization

### Add to Existing Structure:

```
stdlib/src/main/kotlin/org/apache/utlx/stdlib/xml/
├── XMLCanonicalizationFunctions.kt        ← Already exists (C14N)
├── XmlUtilityFunctions.kt                 ← Already exists (utilities)
├── XMLSerializationOptions.kt             ← NEW (this implementation)
└── QNameFunctions.kt                      ← Already exists (QName handling)
```

### Create Documentation:

```
stdlib/docs/
├── c14n_integration_guide.md              ← Already exists
└── xml_serialization_options_guide.md     ← NEW
```

### Add Tests:

```
stdlib/src/test/kotlin/org/apache/utlx/stdlib/xml/
├── XMLCanonicalizationTest.kt             ← Already exists
└── XMLSerializationOptionsTest.kt         ← NEW
```

---

## Integration Points

### 1. With Existing XML Serializer

Update `formats/xml/src/main/kotlin/.../xml_serializer.kt`:

```kotlin
// Before (standard serialization)
fun serializeXML(udm: UDM): String {
    return standardSerialize(udm)
}

// After (with options support)
fun serializeXML(
    udm: UDM,
    options: XMLSerializationOptions? = null
): String {
    return if (options != null) {
        serializeXMLWithOptions(udm, options)
    } else {
        standardSerialize(udm)
    }
}
```

### 2. With XML Parser

Integrate with existing parser for post-processing:

```kotlin
// Parse, transform, serialize with options
val parsed = parseXML(inputXML)
val transformed = applyTransform(parsed)
val output = serializeXMLWithOptions(transformed, options)
```

### 3. With UTL-X Runtime

Make options available in transformations:

```utlx
%utlx 1.0
input json
output xml with options {
  namespaceAliases: {
    "http://schemas.xmlsoap.org/soap/envelope/": "soap"
  },
  emptyElementStyle: "EXPLICIT"
}
---
{
  "soap:Envelope": {
    "soap:Body": input.body
  }
}
```

---

## Use Case Coverage

### ✅ Now Supported:

1. **SOAP Web Services**
   - Enforce soap: prefix
   - Explicit closing for Body/Header
   - WS-Security namespace handling

2. **EDI/B2B Integration**
   - Partner-specific namespace prefixes
   - Empty element handling per spec
   - Schema validation requirements

3. **Legacy System Integration**
   - Non-well-formed XML (last resort)
   - Custom namespace declaration placement
   - Specific empty element formatting

4. **XHTML Output**
   - Explicit closing for HTML elements
   - Proper void element handling
   - DOCTYPE preservation

5. **Schema Validation**
   - Force namespace declarations
   - Specific formatting requirements
   - Validation-friendly output

---

## Implementation Checklist

### Phase 1: Core Functions (Week 1-2)

- [ ] Implement `XMLSerializationOptions` data class
- [ ] Implement `serializeXMLWithOptions()` function
- [ ] Implement `enforceNamespacePrefixes()` post-processor
- [ ] Implement `formatEmptyElements()` post-processor
- [ ] Implement `addNamespaceDeclarations()` helper
- [ ] Add comprehensive documentation
- [ ] Create unit tests

### Phase 2: Convenience Functions (Week 3)

- [ ] Implement `createSOAPEnvelope()` helper
- [ ] Implement `serializeForSOAP11()` preset
- [ ] Implement `serializeForXHTML()` preset
- [ ] Implement `serializeForLegacySystem()` preset
- [ ] Add integration tests
- [ ] Create example transformations

### Phase 3: Runtime Integration (Week 4)

- [ ] Integrate with XML serializer
- [ ] Add UTL-X syntax support for options
- [ ] Update CLI to support options
- [ ] Create migration guide
- [ ] Performance testing
- [ ] Documentation review

---

## Testing Strategy

### Unit Tests

```kotlin
class XMLSerializationOptionsTest {
    
    @Test
    fun `enforces namespace prefixes`() {
        val xml = """<ns1:root xmlns:ns1="http://example.com"/>"""
        val result = enforceNamespacePrefixes(xml, mapOf(
            "http://example.com" to "custom"
        ))
        assertEquals("""<custom:root xmlns:custom="http://example.com"/>""", result)
    }
    
    @Test
    fun `formats empty elements as explicit closing`() {
        val xml = """<body/>"""
        val result = formatEmptyElements(xml, mapOf(
            "body" to EmptyElementStyle.EXPLICIT
        ))
        assertEquals("""<body></body>""", result)
    }
    
    @Test
    fun `creates SOAP envelope with correct prefixes`() {
        val body = parseXML("<GetUser><userId>123</userId></GetUser>")
        val soap = createSOAPEnvelope(body, soapVersion = "1.1")
        assertTrue(soap.contains("soap:Envelope"))
        assertTrue(soap.contains("xmlns:soap="))
    }
}
```

### Integration Tests

```kotlin
class XMLSerializationIntegrationTest {
    
    @Test
    fun `end-to-end SOAP transformation`() {
        // Input JSON
        val input = parseJSON("""{"userId": "123"}""")
        
        // Transform to SOAP
        val soapOptions = XMLSerializationOptions(
            namespaceAliases = mapOf(
                "http://schemas.xmlsoap.org/soap/envelope/" to "soap"
            ),
            emptyElementStyle = EmptyElementStyle.EXPLICIT
        )
        
        // Create SOAP structure
        val soapEnvelope = createSOAPStructure(input)
        
        // Serialize with options
        val xml = serializeXMLWithOptions(soapEnvelope, soapOptions)
        
        // Verify
        assertTrue(xml.contains("<soap:Envelope"))
        assertTrue(xml.contains("<soap:Body></soap:Body>"))
        assertFalse(xml.contains("<soap:Body/>"))
    }
}
```

### Real-World Tests

```kotlin
@Test
fun `works with actual SOAP service`() {
    val request = createSOAPRequest(userId = "123")
    val response = sendToSOAPService(request)
    assertTrue(response.isSuccessful)
}
```

---

## Migration Guide

### For Existing UTL-X Users

**Before (standard serialization):**
```utlx
%utlx 1.0
output xml
---
{
  "Envelope": {
    "@xmlns": "http://schemas.xmlsoap.org/soap/envelope/",
    "Body": input.body
  }
}
```

**After (with options):**
```utlx
%utlx 1.0
output xml with options {
  namespaceAliases: {
    "http://schemas.xmlsoap.org/soap/envelope/": "soap"
  },
  emptyElementStyle: "EXPLICIT"
}
---
{
  "soap:Envelope": {
    "@xmlns:soap": "http://schemas.xmlsoap.org/soap/envelope/",
    "soap:Body": input.body
  }
}
```

**Or post-process:**
```utlx
let xml = serializeXML(data)
let fixed = enforceNamespacePrefixes(xml, {
  "http://schemas.xmlsoap.org/soap/envelope/": "soap"
})
```

---

## Performance Considerations

### Expected Performance

| Operation | Performance | Notes |
|-----------|-------------|-------|
| Standard serialization | Baseline | No overhead |
| With namespace aliases | +5-10% | Prefix mapping |
| Post-processing | +10-20% | Parse + rewrite |
| Complex options | +15-25% | Multiple transformations |

### Optimization Tips

```kotlin
// ✅ GOOD: Reuse options object
val options = XMLSerializationOptions(...)
repeat(1000) {
    serializeXMLWithOptions(data, options)  // Options reused
}

// ❌ BAD: Create options each time
repeat(1000) {
    serializeXMLWithOptions(data, XMLSerializationOptions(...))
}

// ✅ GOOD: Process at serialization time
val xml = serializeXMLWithOptions(data, options)

// ⚠️ SLOWER: Post-process after standard serialization
val xml = serializeXML(data)
val fixed = enforceNamespacePrefixes(xml, prefixes)  // Parse + rewrite
```

---

## Security Considerations

### ⚠️ Non-Well-Formed XML

```kotlin
// ❌ DANGEROUS: Allows invalid XML
val options = XMLSerializationOptions(
    allowNonWellFormed = true
)

// Only use when:
// 1. Target system CANNOT be fixed
// 2. Integration is temporary
// 3. Documented business justification
// 4. Security review completed
```

### ✅ Safe Practices

```kotlin
// ✅ SAFE: Validate after serialization
val xml = serializeXMLWithOptions(data, options)
validateXMLAgainstSchema(xml, schemaURL)

// ✅ SAFE: Log when using non-standard options
if (options.allowNonWellFormed) {
    logger.warn("Using non-well-formed XML for legacy system")
}

// ✅ SAFE: Restrict in production
val options = if (environment == "production") {
    XMLSerializationOptions(allowNonWellFormed = false)
} else {
    testOptions
}
```

---

## Documentation Updates Needed

### 1. Update stdlib README

Add section on XML serialization options:
```markdown
## XML Functions

### Advanced Serialization
- `serializeXMLWithOptions()` - Fine-grained control
- `enforceNamespacePrefixes()` - Fix namespace prefixes
- `formatEmptyElements()` - Control empty element formatting
- `createSOAPEnvelope()` - SOAP-specific helper
```

### 2. Add Examples

Create `examples/xml-serialization/`:
```
examples/xml-serialization/
├── soap-service.utlx
├── legacy-integration.utlx
├── xhtml-output.utlx
└── namespace-control.utlx
```

### 3. Update API Documentation

Generate Kdoc for all new functions.

---

## Summary

### What Gets Added:

✅ **Core Functions** - Namespace control, empty element formatting  
✅ **Convenience Helpers** - SOAP, XHTML, legacy presets  
✅ **Post-Processors** - Fix existing XML  
✅ **Comprehensive Docs** - Usage guide, examples, best practices  
✅ **Tests** - Unit, integration, real-world  

### What It Solves:

✅ SOAP web service integration  
✅ EDI/B2B partner requirements  
✅ Legacy system compatibility  
✅ XHTML output  
✅ Schema validation requirements  

### File Locations:

- **Implementation:** `stdlib/src/main/kotlin/org/apache/utlx/stdlib/xml/XMLSerializationOptions.kt`
- **Tests:** `stdlib/src/test/kotlin/org/apache/utlx/stdlib/xml/XMLSerializationOptionsTest.kt`
- **Docs:** `stdlib/docs/xml_serialization_options_guide.md`

**This fills a critical gap in UTL-X for real-world XML integration!** 🎯