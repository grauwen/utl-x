# Advanced XML Serialization Guide

## Overview

Standard XML serializers work great for well-formed XML, but real-world integrations often require:

✅ **Namespace prefix control** - Force specific prefixes (soap:, api:, etc.)  
✅ **Empty element formatting** - Control `<tag/>` vs `<tag></tag>`  
✅ **Legacy system compatibility** - Non-standard XML that systems expect  
✅ **Schema compliance** - Specific formatting for validation  

---

## Problem 1: Namespace Prefix Enforcement

### The Problem

Standard serializers auto-generate prefixes:

```xml
<!-- What you get (standards-compliant but wrong for target system): -->
<ns1:Envelope xmlns:ns1="http://schemas.xmlsoap.org/soap/envelope/">
  <ns1:Header>
    <ns2:Security xmlns:ns2="http://docs.oasis-open.org/wss/2004/01">
      <ns2:Username>admin</ns2:Username>
    </ns2:Security>
  </ns1:Header>
  <ns1:Body>
    <ns3:GetUser xmlns:ns3="http://example.com/api">
      <ns3:userId>123</ns3:userId>
    </ns3:GetUser>
  </ns1:Body>
</ns1:Envelope>

<!-- What system requires: -->
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Header>
    <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01">
      <wsse:Username>admin</wsse:Username>
    </wsse:Security>
  </soap:Header>
  <soap:Body>
    <api:GetUser xmlns:api="http://example.com/api">
      <api:userId>123</api:userId>
    </api:GetUser>
  </soap:Body>
</soap:Envelope>
```

### The Solution

```kotlin
val options = XMLSerializationOptions(
    namespaceAliases = mapOf(
        "http://schemas.xmlsoap.org/soap/envelope/" to "soap",
        "http://docs.oasis-open.org/wss/2004/01" to "wsse",
        "http://example.com/api" to "api"
    )
)

val xml = serializeXMLWithOptions(envelope, options)
```

**Or post-process existing XML:**

```kotlin
val fixedXML = enforceNamespacePrefixes(existingXML, mapOf(
    "http://schemas.xmlsoap.org/soap/envelope/" to "soap"
))
```

### UTL-X Usage

```utlx
%utlx 1.0
input json
output xml
---

{
  "soap:Envelope": {
    "@xmlns:soap": "http://schemas.xmlsoap.org/soap/envelope/",
    "soap:Body": {
      "api:Request": {
        "@xmlns:api": "http://example.com/api",
        "api:userId": input.userId
      }
    }
  }
}

// Use serializeXMLWithOptions to enforce prefixes
```

---

## Problem 2: Empty Element Formatting

### The Problem

Different systems expect different empty element formats:

```xml
<!-- Self-closing (XML standard): -->
<item id="123"/>

<!-- Explicit closing (XHTML, some parsers): -->
<item id="123"></item>

<!-- Nil attribute (XML Schema): -->
<item xsi:nil="true"/>

<!-- Omitted (optional elements): -->
<!-- (element not present at all) -->
```

### The Solution

```kotlin
// Global setting
val options = XMLSerializationOptions(
    emptyElementStyle = EmptyElementStyle.EXPLICIT
)

// Per-element control
val options = XMLSerializationOptions(
    explicitClosingElements = setOf("div", "span", "script"),
    customEmptyHandlers = mapOf(
        "optionalField" to { name, attrs -> "" }  // Omit
    )
)
```

**Post-process approach:**

```kotlin
val formatted = formatEmptyElements(xml, mapOf(
    "body" to EmptyElementStyle.EXPLICIT,     // <body></body>
    "br" to EmptyElementStyle.SELF_CLOSING,   // <br/>
    "null" to EmptyElementStyle.NIL_ATTRIBUTE // <null xsi:nil="true"/>
))
```

### Real-World Example: SOAP

```kotlin
// SOAP services often require explicit closing for Body
val soapOptions = XMLSerializationOptions(
    namespaceAliases = mapOf(
        "http://schemas.xmlsoap.org/soap/envelope/" to "soap"
    ),
    emptyElementStyle = EmptyElementStyle.EXPLICIT
)

val xml = serializeXMLWithOptions(envelope, soapOptions)
// <soap:Body></soap:Body>  NOT  <soap:Body/>
```

---

## Problem 3: Non-Well-Formed XML (Legacy Systems)

### The Problem

Some legacy systems expect XML that violates standards:

```xml
<!-- Missing namespace declarations -->
<root>
  <item custom:attr="value"/>  <!-- custom: not declared! -->
</root>

<!-- Duplicate attributes (technically invalid) -->
<item id="1" id="2"/>

<!-- Namespace prefix without declaration -->
<soap:Envelope>
  <soap:Body/>  <!-- soap: never declared -->
</soap:Envelope>
```

### ⚠️ The Solution (Use with Extreme Caution)

```kotlin
val options = XMLSerializationOptions(
    allowNonWellFormed = true,
    namespaceDeclarationStyle = NamespaceDeclarationStyle.NONE
)

val xml = serializeForLegacySystem(data, requiredPrefixes = mapOf(
    "http://legacy.example.com" to "legacy"
))
```

**Warning:** This produces invalid XML. Only use if:
- Target system cannot be fixed
- System doesn't validate against schema
- You've exhausted all other options
- Integration is temporary

### Better Alternative: Fix the Target System

Before using non-well-formed XML, try:
1. **Update the legacy system** (if possible)
2. **Use an adapter/proxy** that fixes XML
3. **Contact vendor** about schema compliance
4. **Find alternative integration method**

---

## Problem 4: Namespace Declaration Placement

### The Problem

Where to declare namespaces:

```xml
<!-- Option 1: All on root (standard, readable) -->
<root xmlns:a="..." xmlns:b="..." xmlns:c="...">
  <a:child1/>
  <b:child2/>
  <c:child3/>
</root>

<!-- Option 2: Minimal (declare on first use) -->
<root>
  <a:child1 xmlns:a="..."/>
  <b:child2 xmlns:b="..."/>
  <c:child3 xmlns:c="..."/>
</root>

<!-- Option 3: Per-element (redundant but some systems require) -->
<root>
  <a:child1 xmlns:a="..."/>
  <a:child2 xmlns:a="..."/>  <!-- Redundant but required -->
</root>
```

### The Solution

```kotlin
val options = XMLSerializationOptions(
    namespaceDeclarationStyle = NamespaceDeclarationStyle.ROOT  // All on root
)

// Or minimal
val options = XMLSerializationOptions(
    namespaceDeclarationStyle = NamespaceDeclarationStyle.MINIMAL
)

// Or force declarations (legacy systems)
val options = XMLSerializationOptions(
    namespaceDeclarationStyle = NamespaceDeclarationStyle.PER_ELEMENT
)
```

---

## Common Scenarios

### Scenario 1: SOAP 1.1 Web Service

```kotlin
val soapEnvelope = createSOAPEnvelope(
    body = requestBody,
    header = securityHeader,
    soapVersion = "1.1"
)

// Produces:
// <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
//   <soap:Header>...</soap:Header>
//   <soap:Body>...</soap:Body>
// </soap:Envelope>
```

**Or with custom options:**

```kotlin
val options = XMLSerializationOptions(
    namespaceAliases = mapOf(
        "http://schemas.xmlsoap.org/soap/envelope/" to "soap",
        "http://www.w3.org/2001/XMLSchema-instance" to "xsi"
    ),
    emptyElementStyle = EmptyElementStyle.EXPLICIT,
    namespaceDeclarationStyle = NamespaceDeclarationStyle.ROOT
)

val xml = serializeForSOAP11(envelope)
```

### Scenario 2: XHTML Output

```kotlin
val xhtml = serializeForXHTML(htmlDocument)

// Ensures:
// - <div></div> not <div/>
// - <script></script> not <script/>
// - <textarea></textarea> not <textarea/>
```

### Scenario 3: EDI/B2B Integration

```kotlin
// Partner requires exact namespace prefixes
val options = XMLSerializationOptions(
    namespaceAliases = mapOf(
        "urn:edi:x12:005010" to "x12",
        "urn:partner:custom" to "custom"
    ),
    namespaceDeclarationStyle = NamespaceDeclarationStyle.ROOT,
    emptyElementStyle = EmptyElementStyle.OMIT  // Don't send empty fields
)

val ediXML = serializeXMLWithOptions(ediData, options)
```

### Scenario 4: Schema Validation Requirements

```kotlin
// Schema requires specific namespaces even if not used
val options = XMLSerializationOptions(
    forceNamespaceDeclarations = mapOf(
        "xsi" to "http://www.w3.org/2001/XMLSchema-instance",
        "xs" to "http://www.w3.org/2001/XMLSchema"
    )
)

val xml = serializeXMLWithOptions(data, options)
// <root xmlns:xsi="..." xmlns:xs="...">
//   <!-- Even if xsi: and xs: never used -->
// </root>
```

---

## UTL-X Integration Examples

### Example 1: Transform JSON to SOAP

```utlx
%utlx 1.0
input json
output xml with options {
  namespaceAliases: {
    "http://schemas.xmlsoap.org/soap/envelope/": "soap",
    "http://example.com/service": "svc"
  },
  emptyElementStyle: "EXPLICIT"
}
---

{
  "soap:Envelope": {
    "@xmlns:soap": "http://schemas.xmlsoap.org/soap/envelope/",
    "@xmlns:svc": "http://example.com/service",
    "soap:Body": {
      "svc:Request": {
        "svc:userId": input.user_id,
        "svc:action": input.action_type
      }
    }
  }
}
```

### Example 2: Handle Empty Elements

```utlx
%utlx 1.0
input json
output xml
---

let result = {
  "response": {
    "status": input.status,
    "message": input.message,
    "data": input.data,  // May be null
    "errors": input.errors  // May be empty array
  }
}

// Apply empty element rules
formatEmptyElements(result, {
  "data": if (input.data == null) "OMIT" else "SELF_CLOSING",
  "errors": if (isEmpty(input.errors)) "OMIT" else "EXPLICIT"
})
```

### Example 3: Fix Namespace Prefixes

```utlx
%utlx 1.0
input xml
output xml
---

// Input has wrong prefixes (ns1:, ns2:)
// Fix to required prefixes (soap:, wsse:)

enforceNamespacePrefixes(input, {
  "http://schemas.xmlsoap.org/soap/envelope/": "soap",
  "http://docs.oasis-open.org/wss/2004/01": "wsse"
})
```

---

## Configuration Patterns

### Pattern 1: Define Reusable Profiles

```kotlin
// In your UTL-X config
object XMLProfiles {
    val SOAP11 = XMLSerializationOptions(
        namespaceAliases = mapOf(
            "http://schemas.xmlsoap.org/soap/envelope/" to "soap"
        ),
        emptyElementStyle = EmptyElementStyle.EXPLICIT
    )
    
    val EDI_X12 = XMLSerializationOptions(
        namespaceAliases = mapOf(
            "urn:edi:x12:005010" to "x12"
        ),
        emptyElementStyle = EmptyElementStyle.OMIT
    )
    
    val XHTML = XMLSerializationOptions(
        explicitClosingElements = setOf(
            "div", "span", "p", "script", "style"
        ),
        emptyElementStyle = EmptyElementStyle.EXPLICIT
    )
}

// Use in transformations
val xml = serializeXMLWithOptions(data, XMLProfiles.SOAP11)
```

### Pattern 2: Environment-Specific Options

```kotlin
val options = when (environment) {
    "production" -> XMLSerializationOptions(
        namespaceAliases = productionPrefixes,
        allowNonWellFormed = false
    )
    "legacy-test" -> XMLSerializationOptions(
        namespaceAliases = legacyPrefixes,
        allowNonWellFormed = true  // Only in test
    )
    else -> XMLSerializationOptions()
}
```

---

## Best Practices

### ✅ DO

```kotlin
// ✅ Use standard prefixes when possible
namespaceAliases = mapOf(
    "http://schemas.xmlsoap.org/soap/envelope/" to "soap",  // Standard
    "http://www.w3.org/2001/XMLSchema-instance" to "xsi",  // Standard
    "http://www.w3.org/2001/XMLSchema" to "xs"             // Standard
)

// ✅ Document why non-standard formatting is needed
XMLSerializationOptions(
    emptyElementStyle = EmptyElementStyle.EXPLICIT,
    // Reason: Legacy SOAP parser doesn't handle self-closing Body
)

// ✅ Test with actual target system
val xml = serializeXMLWithOptions(testData, options)
sendToTargetSystem(xml)  // Verify it works!

// ✅ Validate against schema when possible
validateAgainstSchema(xml, schemaURL)
```

### ❌ DON'T

```kotlin
// ❌ Don't use non-well-formed without documentation
allowNonWellFormed = true  // WHY???

// ❌ Don't mix multiple formatting approaches
// Pick one strategy and stick with it

// ❌ Don't assume - test with target system
// "It should work" ≠ "It does work"

// ❌ Don't use legacy mode in production
// Only for temporary workarounds
```

---

## Troubleshooting

### Issue: "System rejects XML with wrong prefixes"

**Solution:**
```kotlin
// Map each namespace to required prefix
val options = XMLSerializationOptions(
    namespaceAliases = mapOf(
        "http://actual-namespace-uri.com" to "required-prefix"
    )
)
```

### Issue: "Parser fails on self-closing tags"

**Solution:**
```kotlin
val options = XMLSerializationOptions(
    emptyElementStyle = EmptyElementStyle.EXPLICIT
)

// Or specific elements only
val options = XMLSerializationOptions(
    explicitClosingElements = setOf("Body", "Header")
)
```

### Issue: "Schema validation fails - missing namespace"

**Solution:**
```kotlin
val options = XMLSerializationOptions(
    forceNamespaceDeclarations = mapOf(
        "xsi" to "http://www.w3.org/2001/XMLSchema-instance"
    )
)
```

### Issue: "System expects namespace on every element"

**Solution:**
```kotlin
val options = XMLSerializationOptions(
    namespaceDeclarationStyle = NamespaceDeclarationStyle.PER_ELEMENT
)
```

---

## Summary

| Problem | Function | Use When |
|---------|----------|----------|
| Wrong namespace prefixes | `enforceNamespacePrefixes()` | Target system requires specific prefixes |
| Empty element format | `formatEmptyElements()` | XHTML, SOAP, or picky parsers |
| Missing namespaces | `addNamespaceDeclarations()` | Schema validation requires declarations |
| Full control | `serializeXMLWithOptions()` | Complex requirements |
| SOAP specific | `createSOAPEnvelope()` | SOAP web services |
| Legacy systems | `serializeForLegacySystem()` | ⚠️ Non-standard XML (last resort) |

---

## Next Steps

1. **Identify your requirements** - What does target system expect?
2. **Test with samples** - Verify formatting matches
3. **Create reusable profiles** - Don't repeat configuration
4. **Document deviations** - Why non-standard formatting is needed
5. **Plan migration** - Eventually fix the target system

---

## References

- [XML Namespaces](https://www.w3.org/TR/xml-names/)
- [SOAP 1.1 Specification](https://www.w3.org/TR/2000/NOTE-SOAP-20000508/)
- [XHTML Specification](https://www.w3.org/TR/xhtml1/)
- [XML Schema Part 1](https://www.w3.org/TR/xmlschema-1/)