# UTL-X Standard Library: XML Documentation Functions Proposal

## Executive Summary

This document proposes a comprehensive set of standard library functions for UTL-X to handle XML comments, processing instructions, and all XML Schema (XSD) documentation capabilities as outlined in the XML documentation analysis. These functions will enable UTL-X users to read, write, manipulate, and extract documentation from XML documents and schemas programmatically.

**Project:** UTL-X (Universal Transformation Language Extended)  
**Module:** `stdlib/xml/` (new category)  
**Version:** 1.0.0  
**Author:** Based on analysis of XML documentation capabilities  
**Date:** October 25, 2025

---

## Table of Contents

1. [Design Principles](#design-principles)
2. [Function Categories](#function-categories)
3. [XML Comment Functions](#xml-comment-functions)
4. [Processing Instruction Functions](#processing-instruction-functions)
5. [XSD Annotation Functions](#xsd-annotation-functions)
6. [Documentation Element Functions](#documentation-element-functions)
7. [Documentation Extraction & Querying](#documentation-extraction--querying)
8. [Documentation Generation Functions](#documentation-generation-functions)
9. [Integration Examples](#integration-examples)
10. [Implementation Guidelines](#implementation-guidelines)

---

## Design Principles

### 1. Format-Agnostic Interface
All functions should work with UTL-X's Universal Data Model (UDM), not raw XML, maintaining consistency with the project's core philosophy.

### 2. Functional & Immutable
All functions are pure, return new values, and never mutate input data.

### 3. Composable
Functions should be chainable using UTL-X's pipe operator (`|>`).

### 4. Type-Safe
Leverage UTL-X's type system to catch errors at compile time.

### 5. Intuitive Naming
Function names should be clear, descriptive, and follow UTL-X conventions.

---

## Function Categories

We propose organizing XML documentation functions into the following stdlib categories:

```
stdlib/
├── xml/
│   ├── comments.kt          # XML comment manipulation
│   ├── processing.kt        # Processing instructions
│   ├── xsd_annotations.kt   # XSD annotation functions
│   ├── doc_elements.kt      # Custom documentation elements
│   ├── doc_extraction.kt    # Querying and extracting docs
│   └── doc_generation.kt    # Generating documentation
```

---

## XML Comment Functions

### Core Comment Operations

#### `xml.comment.create(text: String): Comment`
Creates an XML comment node.

**Signature:**
```kotlin
fun create(text: String): UDMNode
```

**UTL-X Usage:**
```utlx
%utlx 1.0
input xml
output xml
---
{
  root: {
    _comment: xml.comment.create("This is a customer record"),
    customer: $input.customer
  }
}
```

**Output:**
```xml
<root>
  <!-- This is a customer record -->
  <customer>...</customer>
</root>
```

---

#### `xml.comment.extract(node: Node): Array<String>`
Extracts all comments from a node and its children.

**Signature:**
```kotlin
fun extract(node: UDMNode): List<String>
```

**UTL-X Usage:**
```utlx
let comments = xml.comment.extract($input.root)
// Returns: ["This is a comment", "Another comment"]
```

---

#### `xml.comment.extractAt(node: Node, path: String): Array<String>`
Extracts comments at a specific XPath location.

**Signature:**
```kotlin
fun extractAt(node: UDMNode, path: String): List<String>
```

**UTL-X Usage:**
```utlx
let customerComments = xml.comment.extractAt($input, "//customer")
```

---

#### `xml.comment.remove(node: Node): Node`
Removes all comments from a node tree (returns new node).

**Signature:**
```kotlin
fun remove(node: UDMNode): UDMNode
```

**UTL-X Usage:**
```utlx
let cleanData = xml.comment.remove($input)
```

---

#### `xml.comment.filter(node: Node, predicate: (String) => Boolean): Node`
Filters comments based on a predicate, removing those that don't match.

**Signature:**
```kotlin
fun filter(node: UDMNode, predicate: (String) -> Boolean): UDMNode
```

**UTL-X Usage:**
```utlx
// Keep only TODO comments
let todoData = xml.comment.filter($input, comment => 
  contains(comment, "TODO")
)
```

---

#### `xml.comment.insertBefore(node: Node, comment: String): Node`
Inserts a comment before the specified node.

**Signature:**
```kotlin
fun insertBefore(node: UDMNode, comment: String): UDMNode
```

**UTL-X Usage:**
```utlx
{
  customer: xml.comment.insertBefore(
    $input.customer,
    "Customer data validated on " + now()
  )
}
```

---

#### `xml.comment.insertAfter(node: Node, comment: String): Node`
Inserts a comment after the specified node.

**Signature:**
```kotlin
fun insertAfter(node: UDMNode, comment: String): UDMNode
```

---

#### `xml.comment.wrap(node: Node, before: String, after: String): Node`
Wraps a node with comments before and after.

**Signature:**
```kotlin
fun wrap(node: UDMNode, before: String, after: String): UDMNode
```

**UTL-X Usage:**
```utlx
{
  customer: xml.comment.wrap(
    $input.customer,
    "BEGIN CUSTOMER DATA",
    "END CUSTOMER DATA"
  )
}
```

**Output:**
```xml
<!-- BEGIN CUSTOMER DATA -->
<customer>...</customer>
<!-- END CUSTOMER DATA -->
```

---

#### `xml.comment.count(node: Node): Number`
Counts total comments in a node tree.

**Signature:**
```kotlin
fun count(node: UDMNode): Int
```

---

## Processing Instruction Functions

### Core PI Operations

#### `xml.pi.create(target: String, data: String): ProcessingInstruction`
Creates a processing instruction.

**Signature:**
```kotlin
fun create(target: String, data: String): UDMNode
```

**UTL-X Usage:**
```utlx
{
  _pi: xml.pi.create("xml-stylesheet", "type='text/xsl' href='style.xsl'"),
  root: $input.root
}
```

**Output:**
```xml
<?xml-stylesheet type='text/xsl' href='style.xsl'?>
<root>...</root>
```

---

#### `xml.pi.extract(node: Node): Array<{target: String, data: String}>`
Extracts all processing instructions.

**Signature:**
```kotlin
fun extract(node: UDMNode): List<PIInfo>
```

**UTL-X Usage:**
```utlx
let pis = xml.pi.extract($input)
// Returns: [{target: "xml-stylesheet", data: "..."}]
```

---

#### `xml.pi.find(node: Node, target: String): Array<String>`
Finds processing instructions by target name.

**Signature:**
```kotlin
fun find(node: UDMNode, target: String): List<String>
```

**UTL-X Usage:**
```utlx
let stylesheets = xml.pi.find($input, "xml-stylesheet")
```

---

#### `xml.pi.remove(node: Node, target: String?): Node`
Removes processing instructions (optionally filtered by target).

**Signature:**
```kotlin
fun remove(node: UDMNode, target: String? = null): UDMNode
```

**UTL-X Usage:**
```utlx
// Remove all PIs
let clean = xml.pi.remove($input)

// Remove only stylesheet PIs
let noStyles = xml.pi.remove($input, "xml-stylesheet")
```

---

#### `xml.pi.parse(data: String): Object`
Parses PI data string into key-value pairs.

**Signature:**
```kotlin
fun parse(data: String): Map<String, String>
```

**UTL-X Usage:**
```utlx
let piData = xml.pi.parse("type='text/xsl' href='style.xsl'")
// Returns: {type: "text/xsl", href: "style.xsl"}
```

---

## XSD Annotation Functions

### Schema Annotation Operations

#### `xsd.annotation.extract(schema: Node, elementName: String): Object`
Extracts all annotations for a schema element.

**Signature:**
```kotlin
fun extract(schema: UDMNode, elementName: String): AnnotationInfo
```

**UTL-X Usage:**
```utlx
let customerAnnotations = xsd.annotation.extract($schema, "customer")
// Returns: {
//   documentation: ["Customer entity...", "Spanish: Cliente..."],
//   appinfo: {...}
// }
```

---

#### `xsd.documentation.get(schema: Node, path: String): Array<String>`
Gets documentation strings for a schema element/type.

**Signature:**
```kotlin
fun get(schema: UDMNode, path: String): List<String>
```

**UTL-X Usage:**
```utlx
let docs = xsd.documentation.get($schema, "//xs:element[@name='customer']")
```

---

#### `xsd.documentation.getByLang(schema: Node, path: String, lang: String): String?`
Gets documentation in a specific language.

**Signature:**
```kotlin
fun getByLang(schema: UDMNode, path: String, lang: String): String?
```

**UTL-X Usage:**
```utlx
let spanishDoc = xsd.documentation.getByLang(
  $schema, 
  "//xs:element[@name='customer']",
  "es"
)
```

---

#### `xsd.documentation.getAllLanguages(schema: Node, path: String): Array<String>`
Gets list of available documentation languages.

**Signature:**
```kotlin
fun getAllLanguages(schema: UDMNode, path: String): List<String>
```

**UTL-X Usage:**
```utlx
let languages = xsd.documentation.getAllLanguages($schema, "//xs:element[@name='customer']")
// Returns: ["en", "es", "de", "fr"]
```

---

#### `xsd.appinfo.extract(schema: Node, elementName: String): Object`
Extracts application-specific metadata from xs:appinfo.

**Signature:**
```kotlin
fun extract(schema: UDMNode, elementName: String): Map<String, Any>
```

**UTL-X Usage:**
```utlx
let dbMapping = xsd.appinfo.extract($schema, "customer")
// Returns: {
//   table: "customers",
//   primaryKey: "customer_id",
//   indexes: [...]
// }
```

---

#### `xsd.appinfo.get(schema: Node, elementName: String, namespace: String): Node?`
Gets appinfo for specific namespace.

**Signature:**
```kotlin
fun get(schema: UDMNode, elementName: String, namespace: String): UDMNode?
```

**UTL-X Usage:**
```utlx
let jaxbInfo = xsd.appinfo.get($schema, "customer", "http://java.sun.com/xml/ns/jaxb")
```

---

#### `xsd.annotation.create(documentation: Array<Doc>, appinfo: Array<Node>?): Node`
Creates an xs:annotation element.

**Signature:**
```kotlin
fun create(
    documentation: List<DocumentationInfo>,
    appinfo: List<UDMNode>? = null
): UDMNode
```

**UTL-X Usage:**
```utlx
let annotation = xsd.annotation.create(
  [
    {lang: "en", text: "Customer entity", source: null},
    {lang: "es", text: "Entidad de cliente", source: null}
  ],
  [{namespace: "http://example.com/db", content: {...}}]
)
```

---

#### `xsd.element.getDocumentation(schema: Node, elementName: String): String?`
Convenience function to get primary documentation for an element.

**Signature:**
```kotlin
fun getDocumentation(schema: UDMNode, elementName: String): String?
```

**UTL-X Usage:**
```utlx
let description = xsd.element.getDocumentation($schema, "customer")
```

---

#### `xsd.type.getDocumentation(schema: Node, typeName: String): String?`
Gets documentation for a type definition.

**Signature:**
```kotlin
fun getDocumentation(schema: UDMNode, typeName: String): String?
```

---

#### `xsd.attribute.getDocumentation(schema: Node, elementName: String, attributeName: String): String?`
Gets documentation for an attribute.

**Signature:**
```kotlin
fun getDocumentation(
    schema: UDMNode,
    elementName: String,
    attributeName: String
): String?
```

---

#### `xsd.enumeration.getDocs(schema: Node, typeName: String): Object`
Gets documentation for all enumeration values in a type.

**Signature:**
```kotlin
fun getDocs(schema: UDMNode, typeName: String): Map<String, String>
```

**UTL-X Usage:**
```utlx
let statusDocs = xsd.enumeration.getDocs($schema, "OrderStatusType")
// Returns: {
//   "PENDING": "Order received but not confirmed",
//   "CONFIRMED": "Payment confirmed, in queue",
//   ...
// }
```

---

## Documentation Element Functions

### Custom Documentation Element Operations

#### `xml.doc.createElement(namespace: String, name: String, content: String): Node`
Creates a custom documentation element.

**Signature:**
```kotlin
fun createElement(namespace: String, name: String, content: String): UDMNode
```

**UTL-X Usage:**
```utlx
{
  customer: {
    _docElement: xml.doc.createElement(
      "http://example.com/doc",
      "description",
      "This is a VIP customer with special pricing"
    ),
    name: $input.name,
    email: $input.email
  }
}
```

**Output:**
```xml
<customer xmlns:doc="http://example.com/doc">
  <doc:description>This is a VIP customer with special pricing</doc:description>
  <n>John Doe</n>
  <email>john@example.com</email>
</customer>
```

---

#### `xml.doc.extract(node: Node, namespace: String): Array<Node>`
Extracts all documentation elements from a specific namespace.

**Signature:**
```kotlin
fun extract(node: UDMNode, namespace: String): List<UDMNode>
```

**UTL-X Usage:**
```utlx
let docs = xml.doc.extract($input, "http://example.com/doc")
```

---

#### `xml.doc.find(node: Node, namespace: String, elementName: String): Array<Node>`
Finds specific documentation elements.

**Signature:**
```kotlin
fun find(node: UDMNode, namespace: String, elementName: String): List<UDMNode>
```

**UTL-X Usage:**
```utlx
let descriptions = xml.doc.find(
  $input,
  "http://example.com/doc",
  "description"
)
```

---

#### `xml.doc.remove(node: Node, namespace: String?): Node`
Removes documentation elements (optionally filtered by namespace).

**Signature:**
```kotlin
fun remove(node: UDMNode, namespace: String? = null): UDMNode
```

---

#### `xml.doc.createWrapper(node: Node, documentation: Object): Node`
Wraps a data node with documentation metadata.

**Signature:**
```kotlin
fun createWrapper(node: UDMNode, documentation: DocumentationWrapper): UDMNode
```

**UTL-X Usage:**
```utlx
{
  customer: xml.doc.createWrapper(
    $input.customer,
    {
      summary: "Customer account information",
      version: "2.1",
      author: "Jane Developer",
      lastModified: now()
    }
  )
}
```

**Output:**
```xml
<documented-element>
  <documentation>
    <summary>Customer account information</summary>
    <version>2.1</version>
    <author>Jane Developer</author>
    <lastModified>2025-10-25T14:30:00Z</lastModified>
  </documentation>
  <data>
    <customer>...</customer>
  </data>
</documented-element>
```

---

#### `xml.doc.unwrap(wrappedNode: Node): Node`
Extracts data from a documentation wrapper.

**Signature:**
```kotlin
fun unwrap(wrappedNode: UDMNode): UDMNode
```

---

#### `xml.doc.createMetadata(attributes: Object): Attributes`
Creates documentation metadata attributes.

**Signature:**
```kotlin
fun createMetadata(attributes: Map<String, String>): Map<String, String>
```

**UTL-X Usage:**
```utlx
{
  customer: {
    ...xml.doc.createMetadata({
      "doc:version": "2.0",
      "doc:author": "System",
      "doc:deprecated": "false"
    }),
    name: $input.name
  }
}
```

---

## Documentation Extraction & Querying

### Query and Analysis Functions

#### `xml.doc.query.findAll(node: Node, options: QueryOptions): Array<DocNode>`
Finds all documentation across comments, PIs, and elements.

**Signature:**
```kotlin
fun findAll(node: UDMNode, options: QueryOptions): List<DocumentationNode>
```

**UTL-X Usage:**
```utlx
let allDocs = xml.doc.query.findAll($input, {
  includeComments: true,
  includeProcessingInstructions: true,
  includeDocElements: true,
  namespaces: ["http://example.com/doc"]
})
```

---

#### `xml.doc.query.search(node: Node, searchText: String, options: SearchOptions): Array<Match>`
Searches documentation content for specific text.

**Signature:**
```kotlin
fun search(
    node: UDMNode,
    searchText: String,
    options: SearchOptions
): List<SearchMatch>
```

**UTL-X Usage:**
```utlx
let matches = xml.doc.query.search(
  $input,
  "deprecated",
  {
    caseSensitive: false,
    includeComments: true,
    includeElements: true
  }
)
// Returns: [
//   {path: "/root/customer", type: "comment", text: "DEPRECATED: Use..."},
//   ...
// ]
```

---

#### `xml.doc.query.getByPath(node: Node, path: String): Array<Documentation>`
Gets all documentation at a specific XPath.

**Signature:**
```kotlin
fun getByPath(node: UDMNode, path: String): List<DocumentationNode>
```

---

#### `xsd.doc.coverage(schema: Node): CoverageReport`
Analyzes documentation coverage in a schema.

**Signature:**
```kotlin
fun coverage(schema: UDMNode): CoverageReport
```

**UTL-X Usage:**
```utlx
let report = xsd.doc.coverage($schema)
// Returns: {
//   totalElements: 45,
//   documentedElements: 38,
//   coveragePercent: 84.4,
//   undocumented: ["paymentMethod", "shippingAddress", ...],
//   wellDocumented: ["customer", "order", ...],
//   poorlyDocumented: ["item"]  // < 20 chars
// }
```

---

#### `xsd.doc.validate(schema: Node, rules: ValidationRules): ValidationResult`
Validates documentation against quality rules.

**Signature:**
```kotlin
fun validate(schema: UDMNode, rules: ValidationRules): ValidationResult
```

**UTL-X Usage:**
```utlx
let validation = xsd.doc.validate($schema, {
  minLength: 50,
  requireExamples: true,
  requireBusinessRules: false,
  checkSpelling: true,
  languages: ["en", "es"]
})
```

---

#### `xml.doc.stats(node: Node): Statistics`
Gathers statistics about documentation in a document.

**Signature:**
```kotlin
fun stats(node: UDMNode): DocumentationStats
```

**UTL-X Usage:**
```utlx
let stats = xml.doc.stats($input)
// Returns: {
//   commentCount: 15,
//   piCount: 2,
//   docElementCount: 23,
//   avgCommentLength: 47,
//   totalChars: 1893,
//   languages: ["en"]
// }
```

---

#### `xsd.doc.extractMetadata(schema: Node): SchemaMetadata`
Extracts high-level schema metadata from annotations.

**Signature:**
```kotlin
fun extractMetadata(schema: UDMNode): SchemaMetadata
```

**UTL-X Usage:**
```utlx
let metadata = xsd.doc.extractMetadata($schema)
// Returns: {
//   version: "2.1.0",
//   namespace: "http://example.com/customer",
//   description: "Customer management schema",
//   authors: ["Architecture Team"],
//   lastModified: "2025-10-15",
//   dependencies: ["common-types.xsd", "address-schema.xsd"]
// }
```

---

## Documentation Generation Functions

### Output and Report Generation

#### `xml.doc.generate.html(node: Node, options: HtmlOptions): String`
Generates HTML documentation from XML/XSD.

**Signature:**
```kotlin
fun html(node: UDMNode, options: HtmlOptions): String
```

**UTL-X Usage:**
```utlx
let htmlDoc = xml.doc.generate.html($schema, {
  title: "Customer Schema Documentation",
  includeExamples: true,
  includeIndex: true,
  css: "documentation.css",
  template: "detailed"
})
```

---

#### `xml.doc.generate.markdown(node: Node, options: MarkdownOptions): String`
Generates Markdown documentation.

**Signature:**
```kotlin
fun markdown(node: UDMNode, options: MarkdownOptions): String
```

**UTL-X Usage:**
```utlx
let mdDoc = xml.doc.generate.markdown($schema, {
  includeTableOfContents: true,
  level: 2,
  format: "github"
})
```

---

#### `xsd.doc.generate.report(schema: Node, options: ReportOptions): Report`
Generates comprehensive documentation report for a schema.

**Signature:**
```kotlin
fun report(schema: UDMNode, options: ReportOptions): DocumentationReport
```

**UTL-X Usage:**
```utlx
let report = xsd.doc.generate.report($schema, {
  includeElementTree: true,
  includeStatistics: true,
  includeCoverage: true,
  includeDeprecated: true,
  format: "detailed"
})
```

---

#### `xml.doc.generate.index(schemas: Array<Node>, options: IndexOptions): Index`
Generates a cross-schema documentation index.

**Signature:**
```kotlin
fun index(schemas: List<UDMNode>, options: IndexOptions): DocumentationIndex
```

**UTL-X Usage:**
```utlx
let index = xml.doc.generate.index(
  [$customerSchema, $orderSchema, $productSchema],
  {
    includeTypes: true,
    includeElements: true,
    includeAttributes: true,
    crossReference: true
  }
)
```

---

#### `xml.doc.generate.javadoc(schema: Node, options: JavadocOptions): String`
Generates Javadoc-style documentation from XSD appinfo.

**Signature:**
```kotlin
fun javadoc(schema: UDMNode, options: JavadocOptions): String
```

---

#### `xml.doc.generate.openapi(schema: Node, options: OpenApiOptions): Object`
Converts XSD with documentation to OpenAPI specification.

**Signature:**
```kotlin
fun openapi(schema: UDMNode, options: OpenApiOptions): OpenApiSpec
```

**UTL-X Usage:**
```utlx
let apiSpec = xml.doc.generate.openapi($schema, {
  version: "3.0.0",
  servers: ["https://api.example.com"],
  includeExamples: true
})
```

---

## Integration Examples

### Example 1: Extract and Transform Documentation

```utlx
%utlx 1.0
input xml
output json
---
{
  schema: "customer-v2.xsd",
  elements: $input..element |> map(elem => {
    name: elem.@name,
    type: elem.@type,
    required: elem.@minOccurs != "0",
    documentation: xsd.element.getDocumentation($input, elem.@name),
    deprecated: xml.doc.query.search(
      elem,
      "deprecated",
      {caseSensitive: false}
    ) |> count() > 0
  }),
  coverage: xsd.doc.coverage($input)
}
```

---

### Example 2: Add Documentation to Transformed Data

```utlx
%utlx 1.0
input json
output xml
---
{
  _comment: xml.comment.create("Generated on " + formatDate(now(), "yyyy-MM-dd")),
  orders: {
    _xmlns:doc: "http://example.com/doc",
    order: $input.orders |> map(o => {
      _doc:description: "Order processed through API v2",
      _doc:source: "REST API",
      _doc:timestamp: now(),
      orderId: o.id,
      customer: o.customer.name,
      total: o.total
    })
  }
}
```

---

### Example 3: Generate Multi-Language Documentation

```utlx
%utlx 1.0
input xml (schema)
output json
---
{
  documentation: {
    english: xsd.documentation.getByLang($input, "//xs:element", "en"),
    spanish: xsd.documentation.getByLang($input, "//xs:element", "es"),
    german: xsd.documentation.getByLang($input, "//xs:element", "de")
  },
  supportedLanguages: xsd.documentation.getAllLanguages($input, "//xs:element")
}
```

---

### Example 4: Clean and Strip Documentation

```utlx
%utlx 1.0
input xml
output xml
---
// Strip all documentation for production deployment
$input
  |> xml.comment.remove()
  |> xml.pi.remove()
  |> xml.doc.remove("http://example.com/doc")
```

---

### Example 5: Validate Documentation Quality

```utlx
%utlx 1.0
input xml (schema)
output json
---
{
  validation: xsd.doc.validate($input, {
    minLength: 50,
    requireExamples: true,
    checkSpelling: true
  }),
  coverage: xsd.doc.coverage($input),
  recommendations: match {
    case coverage.coveragePercent < 80 =>
      "Consider adding more documentation",
    case validation.errors |> count() > 5 =>
      "Multiple documentation quality issues found",
    case _ =>
      "Documentation quality is good"
  }
}
```

---

### Example 6: Extract Database Mapping from XSD

```utlx
%utlx 1.0
input xml (schema)
output json
---
{
  tables: $input..element |> map(elem => {
    schemaElement: elem.@name,
    dbMapping: xsd.appinfo.extract($input, elem.@name),
    description: xsd.element.getDocumentation($input, elem.@name)
  }) |> filter(t => t.dbMapping != null)
}
```

---

### Example 7: Create Annotated Schema Elements

```utlx
%utlx 1.0
input json
output xml
---
{
  "xs:schema": {
    "@xmlns:xs": "http://www.w3.org/2001/XMLSchema",
    "@targetNamespace": "http://example.com/api",
    
    "xs:element": $input.models |> map(model => {
      "@name": model.name,
      "@type": model.type,
      
      "xs:annotation": xsd.annotation.create(
        [
          {
            lang: "en",
            text: model.description,
            source: "http://api.example.com/docs/" + model.name
          }
        ],
        [{
          namespace: "http://example.com/db",
          content: {
            table: model.tableName,
            primaryKey: model.pk
          }
        }]
      )
    })
  }
}
```

---

### Example 8: Generate Documentation Website

```utlx
%utlx 1.0
input xml (schema)
output json
---
{
  htmlPages: $input..element |> map(elem => {
    filename: elem.@name + ".html",
    content: xml.doc.generate.html(elem, {
      title: elem.@name + " Documentation",
      includeExamples: true,
      css: "docs.css"
    })
  }),
  index: xml.doc.generate.html($input, {
    title: "Schema Index",
    template: "index",
    includeIndex: true
  }),
  markdown: xml.doc.generate.markdown($input, {
    includeTableOfContents: true
  })
}
```

---

## Implementation Guidelines

### Module Structure

```
stdlib/xml/
├── comments.kt
│   └── XmlCommentFunctions
├── processing.kt
│   └── ProcessingInstructionFunctions
├── xsd_annotations.kt
│   └── XsdAnnotationFunctions
├── doc_elements.kt
│   └── DocumentationElementFunctions
├── doc_extraction.kt
│   └── DocumentationExtractionFunctions
└── doc_generation.kt
    └── DocumentationGenerationFunctions
```

---

### Kotlin Implementation Pattern

```kotlin
// stdlib/xml/comments.kt
package org.apache.utlx.stdlib.xml

import org.apache.utlx.core.udm.*
import org.apache.utlx.core.types.*

object XmlCommentFunctions {
    
    @StdlibFunction(
        name = "xml.comment.create",
        description = "Creates an XML comment node",
        category = "XML Documentation"
    )
    fun create(text: UDMString): UDMNode {
        return UDMComment(text.value)
    }
    
    @StdlibFunction(
        name = "xml.comment.extract",
        description = "Extracts all comments from a node tree"
    )
    fun extract(node: UDMNode): UDMArray {
        val comments = mutableListOf<UDMString>()
        
        fun traverse(n: UDMNode) {
            if (n is UDMComment) {
                comments.add(UDMString(n.text))
            }
            n.children?.forEach { traverse(it) }
        }
        
        traverse(node)
        return UDMArray(comments)
    }
    
    @StdlibFunction(
        name = "xml.comment.remove",
        description = "Removes all comments from a node tree"
    )
    fun remove(node: UDMNode): UDMNode {
        return node.copy(
            children = node.children?.filter { it !is UDMComment }?.map { remove(it) }
        )
    }
    
    // ... more functions
}
```

---

### Type Definitions

```kotlin
// Data classes for function return types

data class AnnotationInfo(
    val documentation: List<String>,
    val appinfo: Map<String, Any>,
    val languages: List<String>
)

data class PIInfo(
    val target: String,
    val data: String,
    val parsedData: Map<String, String>?
)

data class CoverageReport(
    val totalElements: Int,
    val documentedElements: Int,
    val coveragePercent: Double,
    val undocumented: List<String>,
    val wellDocumented: List<String>,
    val poorlyDocumented: List<String>
)

data class DocumentationNode(
    val type: DocType,  // COMMENT, PI, ELEMENT, XSD_ANNOTATION
    val path: String,
    val content: String,
    val attributes: Map<String, String>
)

enum class DocType {
    COMMENT,
    PROCESSING_INSTRUCTION,
    DOCUMENTATION_ELEMENT,
    XSD_ANNOTATION,
    XSD_DOCUMENTATION,
    XSD_APPINFO
}

data class SearchMatch(
    val path: String,
    val type: DocType,
    val text: String,
    val position: Int
)

data class ValidationResult(
    val valid: Boolean,
    val errors: List<ValidationError>,
    val warnings: List<ValidationWarning>,
    val score: Double
)

data class DocumentationStats(
    val commentCount: Int,
    val piCount: Int,
    val docElementCount: Int,
    val xsdAnnotationCount: Int,
    val avgCommentLength: Double,
    val totalChars: Int,
    val languages: List<String>
)
```

---

### UDM Extensions

```kotlin
// Extend UDM to support documentation nodes

sealed class UDMNode {
    // ... existing types
}

data class UDMComment(
    val text: String
) : UDMNode()

data class UDMProcessingInstruction(
    val target: String,
    val data: String
) : UDMNode()

data class UDMAnnotation(
    val documentation: List<DocumentationEntry>,
    val appinfo: List<UDMNode>
) : UDMNode()

data class DocumentationEntry(
    val text: String,
    val lang: String?,
    val source: String?
)
```

---

### Testing Strategy

```kotlin
// stdlib/xml/test/CommentFunctionsTest.kt

class XmlCommentFunctionsTest {
    
    @Test
    fun `create comment node`() {
        val comment = XmlCommentFunctions.create(UDMString("Test comment"))
        
        assertThat(comment).isInstanceOf(UDMComment::class.java)
        assertThat((comment as UDMComment).text).isEqualTo("Test comment")
    }
    
    @Test
    fun `extract comments from tree`() {
        val tree = UDMElement(
            name = "root",
            children = listOf(
                UDMComment("First comment"),
                UDMElement("child", children = listOf(
                    UDMComment("Nested comment")
                )),
                UDMComment("Last comment")
            )
        )
        
        val comments = XmlCommentFunctions.extract(tree)
        
        assertThat(comments.size).isEqualTo(3)
        assertThat(comments.map { it.value })
            .containsExactly("First comment", "Nested comment", "Last comment")
    }
    
    @Test
    fun `remove all comments`() {
        val tree = UDMElement(
            name = "root",
            children = listOf(
                UDMComment("Remove me"),
                UDMElement("keep", children = listOf(
                    UDMComment("Remove me too")
                ))
            )
        )
        
        val cleaned = XmlCommentFunctions.remove(tree)
        
        val allComments = XmlCommentFunctions.extract(cleaned)
        assertThat(allComments).isEmpty()
    }
}
```

---

### Integration with Parser

```kotlin
// Ensure XML parser preserves documentation

class XmlParser {
    fun parse(xml: String, options: ParseOptions): UDMNode {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = builder.parse(InputSource(StringReader(xml)))
        
        return convertToUDM(doc.documentElement, options)
    }
    
    private fun convertToUDM(node: Node, options: ParseOptions): UDMNode {
        return when (node.nodeType) {
            Node.COMMENT_NODE -> {
                if (options.preserveComments) {
                    UDMComment(node.textContent)
                } else null
            }
            Node.PROCESSING_INSTRUCTION_NODE -> {
                if (options.preserveProcessingInstructions) {
                    UDMProcessingInstruction(
                        target = node.nodeName,
                        data = node.nodeValue
                    )
                } else null
            }
            Node.ELEMENT_NODE -> {
                UDMElement(
                    name = node.localName,
                    attributes = getAttributes(node),
                    children = node.childNodes.let { children ->
                        (0 until children.length)
                            .map { children.item(it) }
                            .mapNotNull { convertToUDM(it, options) }
                    }
                )
            }
            else -> null
        } ?: UDMNull
    }
}

data class ParseOptions(
    val preserveComments: Boolean = false,
    val preserveProcessingInstructions: Boolean = false,
    val preserveWhitespace: Boolean = false
)
```

---

### Serialization Support

```kotlin
// Ensure XML serializer outputs documentation

class XmlSerializer {
    fun serialize(node: UDMNode, options: SerializeOptions): String {
        val writer = StringWriter()
        val xmlWriter = XMLOutputFactory.newInstance()
            .createXMLStreamWriter(writer)
        
        writeNode(node, xmlWriter, options)
        xmlWriter.flush()
        
        return writer.toString()
    }
    
    private fun writeNode(
        node: UDMNode,
        writer: XMLStreamWriter,
        options: SerializeOptions
    ) {
        when (node) {
            is UDMComment -> {
                if (options.includeComments) {
                    writer.writeComment(node.text)
                }
            }
            is UDMProcessingInstruction -> {
                if (options.includeProcessingInstructions) {
                    writer.writeProcessingInstruction(node.target, node.data)
                }
            }
            is UDMElement -> {
                writer.writeStartElement(node.name)
                
                // Write attributes
                node.attributes.forEach { (key, value) ->
                    writer.writeAttribute(key, value.toString())
                }
                
                // Write children
                node.children?.forEach { child ->
                    writeNode(child, writer, options)
                }
                
                writer.writeEndElement()
            }
            // ... other types
        }
    }
}

data class SerializeOptions(
    val includeComments: Boolean = true,
    val includeProcessingInstructions: Boolean = true,
    val prettyPrint: Boolean = true,
    val indent: String = "  "
)
```

---

## Function Summary Table

| Category | Function | Purpose |
|----------|----------|---------|
| **Comments** | `xml.comment.create` | Create comment node |
| | `xml.comment.extract` | Get all comments |
| | `xml.comment.extractAt` | Get comments at path |
| | `xml.comment.remove` | Remove comments |
| | `xml.comment.filter` | Filter comments |
| | `xml.comment.insertBefore` | Add comment before node |
| | `xml.comment.insertAfter` | Add comment after node |
| | `xml.comment.wrap` | Wrap with comments |
| | `xml.comment.count` | Count comments |
| **Processing Instructions** | `xml.pi.create` | Create PI |
| | `xml.pi.extract` | Get all PIs |
| | `xml.pi.find` | Find PIs by target |
| | `xml.pi.remove` | Remove PIs |
| | `xml.pi.parse` | Parse PI data |
| **XSD Annotations** | `xsd.annotation.extract` | Get element annotations |
| | `xsd.documentation.get` | Get documentation |
| | `xsd.documentation.getByLang` | Get language-specific docs |
| | `xsd.documentation.getAllLanguages` | List languages |
| | `xsd.appinfo.extract` | Get appinfo metadata |
| | `xsd.appinfo.get` | Get namespace-specific appinfo |
| | `xsd.annotation.create` | Create annotation |
| | `xsd.element.getDocumentation` | Get element docs |
| | `xsd.type.getDocumentation` | Get type docs |
| | `xsd.attribute.getDocumentation` | Get attribute docs |
| | `xsd.enumeration.getDocs` | Get enum docs |
| **Doc Elements** | `xml.doc.createElement` | Create custom doc element |
| | `xml.doc.extract` | Extract doc elements |
| | `xml.doc.find` | Find specific doc elements |
| | `xml.doc.remove` | Remove doc elements |
| | `xml.doc.createWrapper` | Wrap with documentation |
| | `xml.doc.unwrap` | Extract from wrapper |
| | `xml.doc.createMetadata` | Create metadata attributes |
| **Querying** | `xml.doc.query.findAll` | Find all documentation |
| | `xml.doc.query.search` | Search doc content |
| | `xml.doc.query.getByPath` | Get docs at path |
| | `xsd.doc.coverage` | Analyze coverage |
| | `xsd.doc.validate` | Validate doc quality |
| | `xml.doc.stats` | Get statistics |
| | `xsd.doc.extractMetadata` | Get schema metadata |
| **Generation** | `xml.doc.generate.html` | Generate HTML docs |
| | `xml.doc.generate.markdown` | Generate Markdown |
| | `xsd.doc.generate.report` | Generate report |
| | `xml.doc.generate.index` | Generate cross-schema index |
| | `xml.doc.generate.javadoc` | Generate Javadoc |
| | `xml.doc.generate.openapi` | Generate OpenAPI spec |

**Total Functions:** 56

---

## Priority Implementation Phases

### Phase 1: Core Foundation (v1.0)
- XML comment functions (create, extract, remove)
- Processing instruction basics (create, extract, remove)
- Basic XSD documentation extraction

**Functions:** 15 functions  
**Timeline:** 2-3 weeks

---

### Phase 2: XSD Support (v1.1)
- Full XSD annotation support
- Multilingual documentation
- Appinfo extraction
- Enumeration documentation

**Functions:** 15 functions  
**Timeline:** 3-4 weeks

---

### Phase 3: Advanced Querying (v1.2)
- Documentation search and query
- Coverage analysis
- Validation
- Statistics

**Functions:** 10 functions  
**Timeline:** 2-3 weeks

---

### Phase 4: Generation & Reports (v1.3)
- HTML generation
- Markdown generation
- Documentation reports
- OpenAPI conversion

**Functions:** 16 functions  
**Timeline:** 4-5 weeks

---

## Benefits to UTL-X

### 1. **Enhanced XML Capabilities**
UTL-X becomes a comprehensive XML documentation tool, not just a transformation language.

### 2. **Professional Schema Management**
Users can manage XSD schemas with full documentation support, competing with enterprise tools.

### 3. **Documentation as Code**
Enables treating documentation as a first-class citizen in data transformations.

### 4. **API Generation**
With OpenAPI generation, UTL-X can bridge XML schemas to modern API specifications.

### 5. **Quality Assurance**
Documentation validation and coverage analysis ensure high-quality schemas.

### 6. **Multilingual Support**
Native support for internationalized documentation in XSD.

---

## Conclusion

This comprehensive set of 56 functions provides UTL-X with industry-leading XML documentation capabilities. The functions enable users to:

1. **Read, write, and manipulate** all forms of XML documentation
2. **Extract and query** documentation programmatically
3. **Validate and analyze** documentation quality
4. **Generate reports** in multiple formats
5. **Integrate documentation** into transformation workflows

By implementing these functions, UTL-X will offer capabilities that rival or exceed commercial tools while maintaining its open-source, format-agnostic philosophy.

---

## Next Steps

1. **Community Review:** Share this proposal with the UTL-X community
2. **Prioritization:** Determine which phases to implement first
3. **API Design:** Refine function signatures based on feedback
4. **Implementation:** Begin Phase 1 development
5. **Testing:** Comprehensive test coverage for each function
6. **Documentation:** Create usage guides and examples
7. **Integration:** Ensure seamless integration with existing UTL-X features

---

**Prepared for:** UTL-X Project  
**Prepared by:** XML Documentation Analysis Team  
**Version:** 1.0.0  
**Date:** October 25, 2025  
**License:** AGPL-3.0 / Commercial (matching UTL-X licensing)

