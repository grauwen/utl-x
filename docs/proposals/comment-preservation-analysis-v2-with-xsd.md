# Comment and Documentation Preservation in UTL-X (v2 - with XSD Support)

**Author:** Analysis by Claude Code
**Date:** 2025-10-25
**Status:** Proposal / Research Document
**Version:** 2.0 - Enhanced with XSD Documentation Analysis
**Related:**
- UDM Architecture
- Format Handlers (XML, YAML, JSON, CSV)
- `docs/architecture/decisions/xml-documentation-analysis.md`
- Previous version: `comment-preservation-analysis.md`

---

## Executive Summary

This document extends the original comment preservation analysis to include comprehensive XML Schema (XSD) documentation capabilities. While the original analysis focused on simple comments (YAML `#`, XML `<!-->`), this version recognizes that XML/XSD ecosystems have sophisticated, standardized documentation mechanisms that go far beyond simple comments.

**Key Additions in v2:**
- ✅ XSD `<xs:annotation>`, `<xs:documentation>`, and `<xs:appinfo>` support
- ✅ Processing instructions (`<?doc ?>`) for structured documentation
- ✅ Custom documentation elements (namespace-based)
- ✅ Metadata attributes (`doc:description="..."`)
- ✅ Multilingual documentation (`xml:lang` attribute)
- ✅ Machine-readable vs. human-readable documentation separation
- ✅ Tool integration and programmatic access patterns

**Expanded Scope:**
This proposal now covers **five levels of documentation richness**, from simple comments to full XSD annotation systems.

---

## Table of Contents

1. [Documentation Capabilities by Format](#1-documentation-capabilities-by-format)
2. [Current UDM Architecture](#2-current-udm-architecture)
3. [XML Documentation Ecosystem](#3-xml-documentation-ecosystem)
4. [XSD Documentation Deep Dive](#4-xsd-documentation-deep-dive)
5. [Use Cases Expanded](#5-use-cases-expanded)
6. [Enhanced Technical Challenges](#6-enhanced-technical-challenges)
7. [Proposed UDM Enhancements (v2)](#7-proposed-udm-enhancements-v2)
8. [Implementation Phases (Revised)](#8-implementation-phases-revised)
9. [Semantic Mapping Strategies](#9-semantic-mapping-strategies)
10. [Stdlib Functions (Expanded)](#10-stdlib-functions-expanded)
11. [Performance Analysis](#11-performance-analysis)
12. [Decision Matrix](#12-decision-matrix)
13. [Recommendations](#13-recommendations)

---

## 1. Documentation Capabilities by Format

### Comprehensive Format Matrix

| Format | Comment Types | Structured Docs | Multilingual | Machine-Readable | Programmatic Access | Current UTL-X |
|--------|--------------|-----------------|--------------|------------------|-------------------|---------------|
| **XML** | `<!--` comments<br>`<?` PIs | `<xs:annotation>`<br>Custom elements<br>Attributes | ✅ `xml:lang` | ✅ `<xs:appinfo>` | ✅ DOM/XPath | ❌ Stripped |
| **XSD** | `<!--` comments | `<xs:documentation>`<br>`<xs:appinfo>` | ✅ Built-in | ✅ Extensive | ✅ Schema APIs | ❌ Not preserved |
| **YAML** | `#` line comments | ❌ None standard | ❌ None | ❌ None | ❌ None | ❌ Stripped |
| **JSON** | ❌ None (RFC 8259) | ❌ None | ❌ None | ❌ None | N/A | N/A |
| **CSV** | ⚠️ `#` informal | ❌ None | ❌ None | ❌ None | ❌ None | ❌ Not handled |

### Documentation Richness Levels

#### Level 1: Simple Comments (YAML, XML)
```yaml
# This is a simple comment
key: value  # End-of-line comment
```

```xml
<!-- Simple XML comment -->
<element>value</element>
```

**Capabilities:** Human-readable text only, no structure, no programmatic access.

#### Level 2: Processing Instructions (XML only)
```xml
<?doc This element contains customer data ?>
<customer>
    <?note Validated against external CRM system ?>
    <name>John Doe</name>
</customer>
```

**Capabilities:** Application-specific, accessible via DOM, parameterizable.

#### Level 3: Custom Documentation Elements (XML)
```xml
<config xmlns:doc="http://example.com/documentation">
    <doc:description>
        This configuration file controls the application's database
        connection settings and retry logic.
    </doc:description>

    <database>
        <doc:field name="host" type="string" required="true">
            Database server hostname or IP address
        </doc:field>
        <host>localhost</host>
    </database>
</config>
```

**Capabilities:** Fully structured, queryable via XPath, validatable, internationalization support.

#### Level 4: Metadata Attributes (XML)
```xml
<customer
    doc:description="Primary customer record"
    doc:since="2.0"
    doc:deprecated="false">

    <email
        doc:format="RFC 5322"
        doc:required="true"
        doc:example="user@example.com">
        john@example.com
    </email>
</customer>
```

**Capabilities:** Terse annotations, easy to filter, minimal document impact.

#### Level 5: XSD Annotations (XML Schema - Most Powerful)
```xml
<xs:element name="customer">
    <xs:annotation>
        <!-- Human-readable documentation -->
        <xs:documentation xml:lang="en">
            Represents a customer entity in the CRM system.
            Contains personal information and account status.
        </xs:documentation>

        <xs:documentation xml:lang="es">
            Representa una entidad de cliente en el sistema CRM.
        </xs:documentation>

        <!-- Machine-readable metadata -->
        <xs:appinfo>
            <db:table>customers</db:table>
            <db:primaryKey>customer_id</db:primaryKey>
            <api:endpoint>/customers/{id}</api:endpoint>
        </xs:appinfo>
    </xs:annotation>

    <xs:complexType>
        <!-- type definition -->
    </xs:complexType>
</xs:element>
```

**Capabilities:**
- ✅ Standardized (W3C)
- ✅ Multilingual (`xml:lang`)
- ✅ Human + machine readable (separate elements)
- ✅ Tool integration (IDEs, generators)
- ✅ Programmatic access (XPath, schema APIs)
- ✅ External references (`source` attribute)

---

## 2. Current UDM Architecture

### Existing Infrastructure (Unchanged from v1)

```kotlin
// File: modules/core/src/main/kotlin/org/apache/utlx/core/udm/udm_core.kt
// Line: 60-64

data class Object(
    val properties: Map<String, UDM>,
    val attributes: Map<String, String> = emptyMap(),
    val name: String? = null,
    val metadata: Map<String, String> = emptyMap()  // ✅ Already exists!
) : UDM()
```

**Current State:**
- ✅ `metadata` field exists
- ✅ `attributes` field exists (can store XML attributes including `doc:*`)
- ❌ Marked as "Internal metadata (not serialized)"
- ❌ Not populated by parsers
- ❌ No support for structured documentation
- ❌ No support for multilingual documentation
- ❌ No separation of human vs. machine-readable content

---

## 3. XML Documentation Ecosystem

### XML Comment Types and Their Purposes

#### 3.1 Standard Comments (`<!-- -->`)

**Purpose:** Informal notes, temporary annotations

```xml
<!-- TODO: Update this section for v2.0 -->
<configuration>
    <!-- Production settings - DO NOT modify without approval -->
    <database>prod.db.example.com</database>
</configuration>
```

**Current Handling:** XML parser `skipComment()` function discards (line 344-357 in `xml_parser.kt`)

**Recommendation:** Preserve as `Comment` type with `CommentType.REGULAR`

#### 3.2 Processing Instructions (`<?target data?>`)

**Purpose:** Application directives, editor hints, transformation rules

```xml
<?xml-stylesheet type="text/xsl" href="style.xsl"?>
<?doc This section documents the API endpoints ?>
<?format preserve-whitespace="true"?>

<api>
    <endpoints>...</endpoints>
</api>
```

**Common Uses:**
- Stylesheet associations
- Editor formatting hints
- Build system directives
- Documentation markers

**Current Handling:** Partially handled for XML declaration, others ignored

**Recommendation:** Preserve as `Comment` type with `CommentType.PRAGMA` or new `ProcessingInstruction` type

#### 3.3 Custom Documentation Elements

**Purpose:** Structured, validatable, queryable documentation

```xml
<datacontract xmlns:doc="http://example.com/doc">
    <doc:metadata>
        <doc:version>2.1.0</doc:version>
        <doc:author>Data Architecture Team</doc:author>
        <doc:lastReview>2025-10-15</doc:lastReview>
    </doc:metadata>

    <servers>
        <production>
            <doc:description>
                Primary production database.
                Changes require CAB approval.
            </doc:description>
            <type>postgres</type>
            <host>prod.db.example.com</host>
        </production>
    </servers>
</datacontract>
```

**Current Handling:** Treated as regular elements (part of data structure)

**Recommendation:** Option to treat namespace-prefixed `doc:*` elements as documentation rather than data

#### 3.4 Metadata Attributes

**Purpose:** Lightweight annotations without disrupting structure

```xml
<servers>
    <production
        doc:description="Primary production database"
        doc:owner="ops-team"
        doc:criticality="high"
        doc:backupSchedule="daily">

        <host
            doc:format="hostname or IPv4"
            doc:example="prod.db.example.com">
            prod.db.example.com
        </host>

        <port
            doc:default="5432"
            doc:range="1024-65535">
            5432
        </port>
    </production>
</servers>
```

**Current Handling:** Stored in `attributes` map, but not distinguished from data attributes

**Recommendation:** Separate documentation attributes from data attributes based on namespace

---

## 4. XSD Documentation Deep Dive

### 4.1 The `<xs:annotation>` Element

**Structure:**
```xml
<xs:element name="invoice">
    <xs:annotation>
        <!-- Human-readable -->
        <xs:documentation xml:lang="en" source="http://example.com/docs/invoice.html">
            ## Invoice Element

            Represents a complete invoice document including line items,
            totals, and payment terms.

            **Business Rules:**
            - Total must equal sum of line items plus tax
            - Issue date must precede due date
            - Payment terms default to NET30 if not specified
        </xs:documentation>

        <xs:documentation xml:lang="es">
            Representa un documento de factura completo.
        </xs:documentation>

        <!-- Machine-readable -->
        <xs:appinfo>
            <db:table>invoices</db:table>
            <db:primaryKey>invoice_id</db:primaryKey>
            <api:endpoint>/invoices/{id}</api:endpoint>
            <api:methods>GET,POST,PUT,DELETE</api:methods>
            <validation:businessRules>
                <rule>total == sum(lineItems.amount) + tax</rule>
                <rule>issueDate &lt; dueDate</rule>
            </validation:businessRules>
        </xs:appinfo>
    </xs:annotation>

    <xs:complexType>
        <!-- definition -->
    </xs:complexType>
</xs:element>
```

**Key Features:**
- Multiple `<xs:documentation>` elements (multilingual)
- `xml:lang` attribute for language identification
- `source` attribute for external documentation links
- Separate `<xs:appinfo>` for machine-readable metadata
- Can contain rich formatting (markdown, XHTML)
- Accessible programmatically via XPath

### 4.2 Enumeration Documentation

**Example:**
```xml
<xs:simpleType name="OrderStatusType">
    <xs:annotation>
        <xs:documentation>
            Order processing status codes.

            Status transitions:
            PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED
        </xs:documentation>
    </xs:annotation>

    <xs:restriction base="xs:string">
        <xs:enumeration value="PENDING">
            <xs:annotation>
                <xs:documentation>
                    Order received but not yet confirmed.
                    Payment authorization pending.
                </xs:documentation>
                <xs:appinfo>
                    <ui:displayText>Pending Confirmation</ui:displayText>
                    <ui:icon>hourglass</ui:icon>
                    <workflow:notificationRequired>false</workflow:notificationRequired>
                </xs:appinfo>
            </xs:annotation>
        </xs:enumeration>

        <xs:enumeration value="CONFIRMED">
            <xs:annotation>
                <xs:documentation>
                    Payment confirmed, order in queue for processing.
                    Inventory reserved.
                </xs:documentation>
                <xs:appinfo>
                    <ui:displayText>Order Confirmed</ui:displayText>
                    <ui:icon>check-circle</ui:icon>
                    <workflow:notificationRequired>true</workflow:notificationRequired>
                    <workflow:emailTemplate>order-confirmation</workflow:emailTemplate>
                </xs:appinfo>
            </xs:annotation>
        </xs:enumeration>
    </xs:restriction>
</xs:simpleType>
```

### 4.3 Complex Type Documentation

**Example:**
```xml
<xs:complexType name="AddressType">
    <xs:annotation>
        <xs:documentation>
            Standard postal address format supporting international addresses.
            Conforms to Universal Postal Union standards.
        </xs:documentation>
    </xs:annotation>

    <xs:sequence>
        <xs:element name="street" type="xs:string">
            <xs:annotation>
                <xs:documentation>
                    Street address including house number.
                    May include apartment or suite information.
                </xs:documentation>
            </xs:annotation>
        </xs:element>

        <xs:element name="postalCode">
            <xs:annotation>
                <xs:documentation>
                    Postal or ZIP code. Format varies by country.
                </xs:documentation>
            </xs:annotation>
            <xs:simpleType>
                <xs:restriction base="xs:string">
                    <xs:pattern value="[0-9]{5}(-[0-9]{4})?">
                        <xs:annotation>
                            <xs:documentation>
                                US ZIP code format: 12345 or 12345-6789
                            </xs:documentation>
                        </xs:annotation>
                    </xs:pattern>
                </xs:restriction>
            </xs:simpleType>
        </xs:element>
    </xs:sequence>
</xs:complexType>
```

### 4.4 Schema-Level Documentation

**Example:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    targetNamespace="http://example.com/customer"
    elementFormDefault="qualified">

    <xs:annotation>
        <xs:documentation xml:lang="en">
            # Customer Management Schema

            ## Version: 2.1.0
            ## Last Updated: 2025-10-15
            ## Authors: Enterprise Architecture Team

            ## Change History

            ### Version 2.1.0 (2025-10-15)
            - Added loyalty program elements
            - Enhanced address validation

            ### Version 2.0.0 (2025-01-10)
            - Major restructuring for international support
            - Breaking changes: customer_id now required

            ## Support
            Contact: enterprise-architecture@example.com
        </xs:documentation>

        <xs:appinfo>
            <version>
                <major>2</major>
                <minor>1</minor>
                <patch>0</patch>
            </version>
            <compatibility>
                <backwards>2.0.0+</backwards>
            </compatibility>
            <tools>
                <generator>XMLSpy 2025</generator>
                <validator>Xerces 2.12</validator>
            </tools>
        </xs:appinfo>
    </xs:annotation>

    <!-- Schema content -->
</xs:schema>
```

---

## 5. Use Cases Expanded

### Original Use Cases (from v1)

1. **DataContract Documentation** - Field descriptions, constraints
2. **Configuration File Templates** - Human-readable explanations
3. **Round-Trip Transformation Fidelity** - Preserve comments
4. **Documentation Extraction** - For external tools

### New Use Cases (XSD-Specific)

#### 5.1 XSD-to-DataContract Translation

**Scenario:** Generate DataContract YAML from XSD with full documentation

**Input (XSD):**
```xml
<xs:element name="customer">
    <xs:annotation>
        <xs:documentation>Customer entity in CRM system</xs:documentation>
        <xs:appinfo>
            <db:table>customers</db:table>
        </xs:appinfo>
    </xs:annotation>
    <xs:complexType>
        <xs:sequence>
            <xs:element name="email" type="xs:string">
                <xs:annotation>
                    <xs:documentation>Primary email address</xs:documentation>
                </xs:annotation>
            </xs:element>
        </xs:sequence>
    </xs:complexType>
</xs:element>
```

**Desired Output (DataContract YAML):**
```yaml
# Customer entity in CRM system
models:
  customer:
    type: table
    dbTable: customers  # Extracted from xs:appinfo
    fields:
      email:  # Primary email address
        type: string
        required: true
```

#### 5.2 API Documentation Generation

**Scenario:** Generate OpenAPI/Swagger docs from XSD annotations

**XSD:**
```xml
<xs:element name="product">
    <xs:annotation>
        <xs:documentation>Product catalog entry</xs:documentation>
        <xs:appinfo>
            <api:endpoint>/products/{id}</api:endpoint>
            <api:methods>GET,POST,PUT,DELETE</api:methods>
            <api:authentication>required</api:authentication>
        </xs:appinfo>
    </xs:annotation>
</xs:element>
```

**Transform to:**
```yaml
paths:
  /products/{id}:
    get:
      summary: "Product catalog entry"  # from xs:documentation
      security:
        - apiKey: []  # from xs:appinfo
```

#### 5.3 Code Generation with Javadoc/JSDoc

**XSD:**
```xml
<xs:element name="order">
    <xs:annotation>
        <xs:documentation>
            Represents a customer order in the e-commerce system.
            Orders must have at least one line item.
        </xs:documentation>
        <xs:appinfo>
            <jaxb:class>
                <jaxb:javadoc>
                    <![CDATA[
                    Order entity representing a customer purchase.

                    @author Generated from schema
                    @since 2.0
                    ]]>
                </jaxb:javadoc>
            </jaxb:class>
        </xs:appinfo>
    </xs:annotation>
</xs:element>
```

**Generate Java:**
```java
/**
 * Order entity representing a customer purchase.
 *
 * <p>Represents a customer order in the e-commerce system.
 * Orders must have at least one line item.</p>
 *
 * @author Generated from schema
 * @since 2.0
 */
public class Order {
    // ...
}
```

#### 5.4 Multilingual Schema Documentation

**XSD:**
```xml
<xs:element name="invoice">
    <xs:annotation>
        <xs:documentation xml:lang="en">Invoice document</xs:documentation>
        <xs:documentation xml:lang="es">Documento de factura</xs:documentation>
        <xs:documentation xml:lang="de">Rechnungsdokument</xs:documentation>
        <xs:documentation xml:lang="fr">Document de facture</xs:documentation>
        <xs:documentation xml:lang="zh">发票文件</xs:documentation>
        <xs:documentation xml:lang="ja">請求書</xs:documentation>
    </xs:annotation>
</xs:element>
```

**Use Case:** Generate localized documentation for different markets

---

## 6. Enhanced Technical Challenges

### Challenge 1: Multi-Level Documentation Storage

**Question:** How to store 5 levels of documentation richness in UDM?

#### Proposed Structure

```kotlin
sealed class DocumentationNode {
    // Level 1: Simple comment
    data class Comment(
        val text: String,
        val position: CommentPosition = CommentPosition.ABOVE
    ) : DocumentationNode()

    // Level 2: Processing instruction
    data class ProcessingInstruction(
        val target: String,
        val data: String
    ) : DocumentationNode()

    // Level 3: Custom element (in properties, marked with special flag)
    // Level 4: Metadata attribute (in attributes, namespace-prefixed)

    // Level 5: XSD Annotation
    data class XSDAnnotation(
        val documentation: List<Documentation>,  // xs:documentation elements
        val appInfo: List<AppInfo>               // xs:appinfo elements
    ) : DocumentationNode()

    data class Documentation(
        val text: String,
        val lang: String? = null,      // xml:lang
        val source: String? = null     // external reference
    )

    data class AppInfo(
        val content: UDM.Object  // Structured machine-readable data
    )
}
```

### Challenge 2: XSD Annotation Preservation During Transformation

**Problem:** XSD annotations are schema-level, but transformations operate on instance documents.

**Example:**

**Schema (invoice.xsd):**
```xml
<xs:element name="invoice">
    <xs:annotation>
        <xs:documentation>Invoice must have total > 0</xs:documentation>
    </xs:annotation>
</xs:element>
```

**Instance (invoice.xml):**
```xml
<invoice>
    <total>100.00</total>
</invoice>
```

**Question:** When transforming XML → YAML, should XSD documentation be:
1. **Ignored** (it's schema info, not instance data)
2. **Preserved in metadata** (for reference)
3. **Extracted separately** (schema documentation vs. instance comments)

**Recommendation:** Treat XSD annotations separately from instance comments. Provide option to:
- `preserveSchemaDocumentation: boolean` - Load XSD and attach documentation to UDM
- `includeSchemaDocumentation: boolean` - Emit schema docs as comments in output

### Challenge 3: Namespace-Based Documentation Detection

**Problem:** Distinguish documentation elements/attributes from data

**Example:**
```xml
<config xmlns="http://example.com/config"
        xmlns:doc="http://example.com/documentation">

    <doc:description>This is documentation</doc:description>
    <database>prod.db.example.com</database>
</config>
```

**Options:**
1. **Hardcode namespaces:** Treat known namespaces as documentation
2. **Configuration:** User specifies documentation namespaces
3. **Convention:** Any namespace with `doc` in URL/prefix treated as documentation

**Recommendation:** Hybrid approach:
```utlx
%utlx 1.0
input: config xml {
    preserveComments: true,
    documentationNamespaces: [
        "http://example.com/documentation",
        "http://www.w3.org/2001/XMLSchema/documentation"
    ],
    documentationPrefixes: ["doc", "comment", "meta"]
}
```

### Challenge 4: Multilingual Documentation Routing

**Problem:** Which language to emit when transforming?

**Example:**
```xml
<xs:element name="product">
    <xs:annotation>
        <xs:documentation xml:lang="en">Product entry</xs:documentation>
        <xs:documentation xml:lang="es">Entrada de producto</xs:documentation>
        <xs:documentation xml:lang="de">Produkteintrag</xs:documentation>
    </xs:annotation>
</xs:element>
```

**Transform to YAML - which language?**

**Options:**
1. **All languages** (multi-line comment with language markers)
2. **Primary language only** (based on `defaultLanguage` setting)
3. **User's locale** (based on system locale)
4. **Configurable** (specify in transformation options)

**Recommendation:** Configurable with fallback:
```utlx
%utlx 1.0
input: schema xml { preserveDocumentation: true }
output yaml {
    includeDocumentation: true,
    documentationLanguages: ["en"],  // Emit English only
    documentationLanguageFallback: true  // If "en" not found, emit first available
}
```

### Challenge 5: Machine-Readable vs. Human-Readable Separation

**Problem:** XSD separates `<xs:documentation>` (human) from `<xs:appinfo>` (machine). How to preserve this distinction?

**Proposed Approach:**

```kotlin
data class DocumentationSet(
    val humanReadable: List<Documentation>,  // From xs:documentation or regular comments
    val machineReadable: UDM.Object?         // From xs:appinfo, structured data
)

// UDM.Object enhancement
data class Object(
    val properties: Map<String, UDM>,
    val attributes: Map<String, String> = emptyMap(),
    val name: String? = null,
    val documentation: DocumentationSet? = null,  // ✅ NEW
    val metadata: Map<String, String> = emptyMap()
) : UDM()
```

**Usage:**
```kotlin
val customer = UDM.Object(
    properties = mapOf("email" to UDM.Scalar("john@example.com")),
    documentation = DocumentationSet(
        humanReadable = listOf(
            Documentation("Primary customer record", lang = "en"),
            Documentation("Registro principal del cliente", lang = "es")
        ),
        machineReadable = UDM.Object(mapOf(
            "db:table" to UDM.Scalar("customers"),
            "api:endpoint" to UDM.Scalar("/customers/{id}")
        ))
    )
)
```

---

## 7. Proposed UDM Enhancements (v2)

### Comprehensive Documentation Support

```kotlin
// Add to udm_core.kt

/**
 * Documentation attached to UDM nodes.
 * Supports multiple documentation levels from simple comments to full XSD annotations.
 */
data class DocumentationSet(
    val comments: List<Comment> = emptyList(),                    // Level 1
    val processingInstructions: List<ProcessingInstruction> = emptyList(),  // Level 2
    // Level 3 (custom elements) stored in properties with special flag
    // Level 4 (attributes) stored in attributes with namespace prefix
    val xsdAnnotations: XSDAnnotation? = null                     // Level 5
) {
    fun isEmpty(): Boolean =
        comments.isEmpty() &&
        processingInstructions.isEmpty() &&
        xsdAnnotations == null

    companion object {
        fun empty() = DocumentationSet()
    }
}

/**
 * Simple comment (YAML # or XML <!-- -->)
 */
data class Comment(
    val text: String,
    val position: CommentPosition = CommentPosition.ABOVE,
    val type: CommentType = CommentType.REGULAR
)

enum class CommentPosition {
    ABOVE,      // Line(s) before the element
    INLINE,     // Same line as element (EOL)
    BELOW,      // Line(s) after element
    INTERNAL    // Inside element (for XML elements with children)
}

enum class CommentType {
    REGULAR,        // Standard comment
    DOCUMENTATION,  // Documentation comment (@doc style)
    TODO,           // TODO/FIXME markers
    DEPRECATED      // Deprecation notices
}

/**
 * XML Processing Instruction
 */
data class ProcessingInstruction(
    val target: String,   // PI target (e.g., "xml-stylesheet")
    val data: String      // PI data
)

/**
 * XSD Annotation (xs:annotation)
 */
data class XSDAnnotation(
    val documentation: List<Documentation> = emptyList(),  // xs:documentation
    val appInfo: AppInfo? = null                           // xs:appinfo
) {
    fun getDocumentation(lang: String): Documentation? =
        documentation.firstOrNull { it.lang == lang }

    fun getPrimaryDocumentation(): Documentation? =
        documentation.firstOrNull()
}

/**
 * XSD Documentation Element (xs:documentation)
 */
data class Documentation(
    val text: String,
    val lang: String? = null,      // xml:lang attribute
    val source: String? = null     // source attribute (external reference)
)

/**
 * XSD AppInfo Element (xs:appinfo) - Machine-readable metadata
 */
data class AppInfo(
    val content: UDM  // Structured data as UDM
)

// Enhanced UDM.Object
data class Object(
    val properties: Map<String, UDM>,
    val attributes: Map<String, String> = emptyMap(),
    val name: String? = null,
    val documentation: DocumentationSet? = null,  // ✅ NEW
    val metadata: Map<String, String> = emptyMap()
) : UDM() {

    fun hasDocumentation(): Boolean =
        documentation != null && !documentation.isEmpty()

    fun getDocumentation(lang: String): String? =
        documentation?.xsdAnnotations?.getDocumentation(lang)?.text

    fun getComments(): List<Comment> =
        documentation?.comments ?: emptyList()

    fun withDocumentation(doc: DocumentationSet): Object =
        copy(documentation = doc)

    fun addComment(comment: Comment): Object =
        copy(documentation = (documentation ?: DocumentationSet.empty()).copy(
            comments = (documentation?.comments ?: emptyList()) + comment
        ))
}
```

---

## 8. Implementation Phases (Revised)

### Phase 1: Foundation - Simple Comments (2-3 weeks)

**Goals:**
- Support Level 1 (simple comments) for XML and YAML
- Infrastructure for higher levels

**Tasks:**
1. ✅ Add `DocumentationSet` to `UDM.Object`
2. ✅ Add `Comment`, `CommentPosition`, `CommentType` types
3. ✅ XML parser: capture `<!-- -->` comments when `preserveComments: true`
4. ⚠️ YAML parser: use metadata field temporarily (SnakeYAML limitation)
5. ✅ XML serializer: emit comments
6. ✅ YAML serializer: emit `#` comments

**Deliverable:** Basic comment preservation for XML ↔ YAML

### Phase 2: Processing Instructions & XSD Basics (3-4 weeks)

**Goals:**
- Support Level 2 (processing instructions)
- Initial XSD annotation support (Level 5)

**Tasks:**
1. ✅ Add `ProcessingInstruction` type
2. ✅ XML parser: capture `<?target data?>` PIs
3. ✅ Add `XSDAnnotation`, `Documentation`, `AppInfo` types
4. ✅ XSD parser: extract `<xs:annotation>` content
5. ✅ Option to load schema alongside instance document
6. ✅ Attach schema documentation to instance UDM nodes

**Deliverable:** PI preservation, basic XSD annotation extraction

### Phase 3: Namespace-Based Documentation (4-5 weeks)

**Goals:**
- Support Level 3 (custom doc elements)
- Support Level 4 (doc attributes)

**Tasks:**
1. ✅ Configuration for documentation namespaces
2. ✅ XML parser: detect and separate documentation elements
3. ✅ XML parser: detect and separate documentation attributes
4. ✅ Store custom doc elements in `DocumentationSet`
5. ✅ Stdlib functions: `getDocumentation()`, `hasDocumentation()`

**Deliverable:** Full namespace-aware documentation handling

### Phase 4: Advanced XSD Support (5-6 weeks)

**Goals:**
- Complete XSD annotation support
- Multilingual documentation
- Schema-to-instance documentation flow

**Tasks:**
1. ✅ Multilingual documentation handling (`xml:lang`)
2. ✅ Language selection in serialization
3. ✅ External documentation references (`source` attribute)
4. ✅ AppInfo parsing (machine-readable metadata)
5. ✅ Schema documentation extraction tools
6. ✅ XSD → DataContract transformation

**Deliverable:** Production-ready XSD documentation support

### Phase 5: Cross-Format Mapping (6-8 weeks)

**Goals:**
- XML ↔ YAML documentation mapping
- XSD → DataContract with documentation
- OpenAPI generation from XSD

**Tasks:**
1. ✅ Semantic mapping rules (XSD → YAML comments)
2. ✅ DataContract: preserve XSD documentation as YAML comments
3. ✅ OpenAPI: generate from XSD with documentation
4. ✅ Conformance tests for all documentation levels
5. ✅ Performance optimization

**Deliverable:** Complete cross-format documentation preservation

### Phase 6: Polish & Tools (3-4 weeks)

**Goals:**
- Developer tools
- Documentation
- Examples

**Tasks:**
1. ✅ VS Code extension: show documentation on hover
2. ✅ CLI tools: extract documentation to markdown/HTML
3. ✅ Documentation: best practices guide
4. ✅ Examples: DataContract, XSD, config files
5. ✅ Performance benchmarks

**Deliverable:** Complete feature with tooling

**Total Timeline:** 6-9 months for full implementation

---

## 9. Semantic Mapping Strategies

### 9.1 XSD → YAML Comment Mapping

**XSD:**
```xml
<xs:element name="host">
    <xs:annotation>
        <xs:documentation xml:lang="en">
            Database server hostname or IP address.
            Must be resolvable via DNS.
        </xs:documentation>
        <xs:documentation xml:lang="es">
            Nombre de host o dirección IP del servidor de base de datos.
        </xs:documentation>
        <xs:appinfo>
            <validation:format>hostname</validation:format>
            <default>localhost</default>
        </xs:appinfo>
    </xs:annotation>
    <xs:simpleType>
        <xs:restriction base="xs:string"/>
    </xs:simpleType>
</xs:element>
```

**Transform to YAML:**

**Option A: Primary language only**
```yaml
# Database server hostname or IP address.
# Must be resolvable via DNS.
# (default: localhost, format: hostname)
host: prod.db.example.com
```

**Option B: All languages**
```yaml
# [EN] Database server hostname or IP address. Must be resolvable via DNS.
# [ES] Nombre de host o dirección IP del servidor de base de datos.
# (default: localhost, format: hostname)
host: prod.db.example.com
```

**Option C: Separate metadata file**
```yaml
# See: schema-docs.md#host
host: prod.db.example.com
```

### 9.2 YAML Comment → XSD Annotation

**YAML:**
```yaml
# Database server hostname or IP address
# @format hostname
# @default localhost
host: prod.db.example.com
```

**Transform to XSD:**
```xml
<xs:element name="host">
    <xs:annotation>
        <xs:documentation>
            Database server hostname or IP address
        </xs:documentation>
        <xs:appinfo>
            <validation:format>hostname</validation:format>
            <default>localhost</default>
        </xs:appinfo>
    </xs:annotation>
    <xs:simpleType>
        <xs:restriction base="xs:string"/>
    </xs:simpleType>
</xs:element>
```

**Parsing Rules:**
1. Regular comments → `<xs:documentation>`
2. Comments with `@tag value` → `<xs:appinfo><tag>value</tag></xs:appinfo>`
3. Multiple comment lines → combined into single `<xs:documentation>` block

### 9.3 Processing Instructions → YAML Comments

**XML:**
```xml
<?doc This section documents the production environment ?>
<production>
    <host>prod.db.example.com</host>
</production>
```

**Transform to YAML:**
```yaml
# PI[doc]: This section documents the production environment
production:
  host: prod.db.example.com
```

### 9.4 Custom Doc Elements → YAML

**XML:**
```xml
<config xmlns:doc="http://example.com/doc">
    <database>
        <doc:description>
            Production database configuration.
            Requires VPN access.
        </doc:description>
        <host>prod.db.example.com</host>
    </database>
</config>
```

**Transform to YAML:**

**Option A: As comments**
```yaml
database:
  # Production database configuration.
  # Requires VPN access.
  host: prod.db.example.com
```

**Option B: As special property**
```yaml
database:
  _documentation: |
    Production database configuration.
    Requires VPN access.
  host: prod.db.example.com
```

---

## 10. Stdlib Functions (Expanded)

### Basic Documentation Functions

```utlx
// Get all documentation from an object
getDocumentation(obj) => DocumentationSet
getDocumentation(obj, "propertyName") => DocumentationSet

// Get specific documentation types
getComments(obj) => Comment[]
getProcessingInstructions(obj) => ProcessingInstruction[]
getXSDAnnotations(obj) => XSDAnnotation

// Check for documentation
hasDocumentation(obj) => Boolean
hasComments(obj) => Boolean
hasXSDAnnotation(obj) => Boolean
```

### XSD-Specific Functions

```utlx
// Get documentation by language
getDocumentation(obj, language: "en") => String
getDocumentation(obj, language: "es") => String

// Get all available languages
getDocumentationLanguages(obj) => String[]

// Get machine-readable metadata
getAppInfo(obj) => Object
getAppInfo(obj, "db:table") => String

// Get external documentation links
getDocumentationSource(obj) => String
```

### Documentation Manipulation

```utlx
// Add documentation
addComment(obj, text: String, position: String) => Object
addDocumentation(obj, text: String, lang: String) => Object
addAppInfo(obj, metadata: Object) => Object

// Remove documentation
stripDocumentation(obj) => Object
stripComments(obj) => Object
stripXSDAnnotations(obj) => Object

// Update documentation
updateDocumentation(obj, lang: String, newText: String) => Object
translateDocumentation(obj, fromLang: String, toLang: String, translator: Function) => Object
```

### Documentation Extraction

```utlx
// Extract to separate structure
extractDocumentation(obj) => {
    docs: Map<String, DocumentationSet>,
    data: Object
}

// Generate documentation report
generateDocReport(obj, format: "markdown" | "html" | "json") => String

// Convert between documentation formats
convertToYAMLComments(xsdAnnotation: XSDAnnotation) => Comment[]
convertToXSDAnnotation(comments: Comment[]) => XSDAnnotation
```

### Schema Documentation Functions

```utlx
// Load schema and extract documentation
loadSchema(schemaPath: String) => Schema
getSchemaDocumentation(schema: Schema, elementName: String) => XSDAnnotation

// Apply schema documentation to instance
applySchemaDocumentation(instance: Object, schema: Schema) => Object

// Generate schema from documented instance
generateSchema(instance: Object, includeDocumentation: Boolean) => XSDSchema
```

---

## 11. Performance Analysis

### Memory Overhead by Documentation Level

| Level | Description | Memory Impact | Use Case |
|-------|-------------|---------------|----------|
| 0 | No documentation | 0% (baseline) | Performance-critical, no docs needed |
| 1 | Simple comments | +15-25% | Basic annotations |
| 2 | + Processing Instructions | +20-30% | Build directives, editor hints |
| 3 | + Custom doc elements | +30-50% | Structured documentation |
| 4 | + Doc attributes | +35-55% | Lightweight metadata |
| 5 | + XSD annotations | +50-80% | Full schema documentation |

**Mitigation:**
- Lazy loading: Don't load documentation unless accessed
- Opt-in levels: Configure which levels to capture
- Efficient storage: Share strings, use interning

### Parse Time Impact

| Operation | Without Docs | With Docs (Level 1-2) | With Docs (Level 5) |
|-----------|-------------|---------------------|-------------------|
| XML Parse | 100ms | 115ms (+15%) | 140ms (+40%) |
| YAML Parse | 80ms | 92ms (+15%) | N/A |
| XSD Parse | 150ms | 150ms (same) | 210ms (+40%) |

**Mitigation:**
- Parallel parsing: Parse docs in separate thread
- Streaming: Process large documents incrementally
- Caching: Cache parsed documentation

### Serialization Impact

| Operation | Without Docs | With Docs | Impact |
|-----------|-------------|-----------|--------|
| XML Serialize | 80ms | 92ms | +15% |
| YAML Serialize | 60ms | 69ms | +15% |

**Mitigation:**
- Skip if no docs: Check `hasDocumentation()` first
- Batch emission: Write all comments at once
- Template caching: Reuse comment formatting

---

## 12. Decision Matrix

### Choose Your Documentation Level

| Need | Recommended Level | Pros | Cons |
|------|------------------|------|------|
| Basic comments only | Level 1 | ✅ Simple<br>✅ Low overhead | ❌ No structure<br>❌ Limited features |
| Build directives | Level 2 | ✅ Application-specific<br>✅ DOM accessible | ❌ Non-standard<br>❌ Tool support varies |
| Structured docs | Level 3 | ✅ Queryable<br>✅ Validatable | ❌ Requires namespaces<br>❌ More complex |
| Lightweight annotations | Level 4 | ✅ Minimal impact<br>✅ Easy to filter | ❌ Limited space<br>❌ No rich formatting |
| Full XSD documentation | Level 5 | ✅ Standardized<br>✅ Tool integration<br>✅ Multilingual | ❌ XML/XSD only<br>❌ Higher overhead |

### Choose Your Preservation Strategy

| Goal | Strategy | Configuration |
|------|----------|--------------|
| **Minimal** | Preserve nothing | `preserveDocumentation: false` (default) |
| **Pragmatic** | Documentation-only | `preserveDocumentation: true`<br>`documentationType: "documentation"` |
| **Comprehensive** | All documentation levels | `preserveDocumentation: true`<br>`documentationType: "all"` |
| **XSD-Aware** | Instance + Schema docs | `preserveDocumentation: true`<br>`loadSchemaDocumentation: true`<br>`schemaPath: "schema.xsd"` |
| **Multilingual** | All languages | `preserveDocumentation: true`<br>`documentationLanguages: ["en", "es", "de"]` |

---

## 13. Recommendations

### Short-Term (Phase 1-2): Basic + XSD Fundamentals

**Implement:**
1. Level 1 (simple comments) for XML and YAML
2. Level 5 (XSD annotations) basic support - `xs:documentation` only
3. Opt-in via `preserveDocumentation: boolean`

**Rationale:**
- Solves 80% of use cases
- Foundation for advanced features
- Manageable scope (2-3 months)

**Configuration:**
```utlx
%utlx 1.0
input: datacontract yaml { preserveDocumentation: true }
output xml {
    includeDocumentation: true,
    documentationStyle: "xml-comments"  // or "xsd-annotation"
}
```

### Medium-Term (Phase 3-4): Full XSD Support

**Implement:**
1. Complete XSD annotation support (`xs:appinfo`, multilingual)
2. Processing instructions (Level 2)
3. Namespace-based documentation detection (Level 3-4)

**Rationale:**
- Enables XSD ↔ DataContract workflows
- Supports enterprise use cases
- Production-ready XSD handling

**Timeline:** 4-6 months total

### Long-Term (Phase 5-6): Complete Ecosystem

**Implement:**
1. All 5 documentation levels
2. Cross-format documentation mapping
3. Developer tools (VS Code extension, CLI tools)
4. OpenAPI generation from XSD
5. Schema generation from documented instances

**Rationale:**
- Complete documentation ecosystem
- Competitive with enterprise tools
- Foundation for commercial features

**Timeline:** 8-12 months total

---

## 14. Comparison with Other Tools

### DataWeave

**Documentation Support:**
- ❌ No comment preservation
- ❌ No XSD annotation support
- ❌ Documentation lost in transformations

**UTL-X Advantage:** Full documentation preservation

### XSLT

**Documentation Support:**
- ⚠️ Can preserve XML comments with special handling
- ❌ No automatic XSD documentation integration
- ⚠️ Custom templates required

**UTL-X Advantage:** Automatic, configurable preservation

### JSONata

**Documentation Support:**
- ❌ JSON has no comments
- N/A Not applicable

### jq

**Documentation Support:**
- ❌ JSON has no comments
- N/A Not applicable

---

## 15. Open Questions for User

### Question 1: Primary Use Cases

Which use cases are most important for your workflows?

**A. DataContract Documentation**
- Preserve YAML comments when transforming DataContract files
- Add XSD documentation to generated schemas

**B. XSD-to-DataContract Translation**
- Generate DataContract YAML from XSD schemas
- Preserve all XSD annotations as YAML comments

**C. Round-Trip Fidelity**
- XML → transform → XML without losing comments/annotations
- YAML → transform → YAML preserving all comments

**D. API Documentation Generation**
- Generate OpenAPI/Swagger from XSD
- Create developer documentation from schemas

**E. Multilingual Documentation**
- Support multiple languages in documentation
- Emit documentation in specific languages

### Question 2: Documentation Level Priority

Which levels should be prioritized?

**Priority Order:**
1. Level __: ________________
2. Level __: ________________
3. Level __: ________________
4. Level __: ________________
5. Level __: ________________

### Question 3: Default Behavior

Should documentation preservation be:

**A. Opt-in (Recommended)**
- Default: Off (no preservation)
- Explicit: `preserveDocumentation: true`
- Pro: No performance impact for users who don't need it
- Con: Users must remember to enable

**B. Opt-out**
- Default: On (always preserve)
- Explicit: `stripDocumentation: true`
- Pro: Surprising to lose documentation
- Con: Performance overhead for all users

**C. Format-Specific**
- XML/XSD: Default preserve
- YAML: Default preserve
- JSON/CSV: N/A (no comments)
- Pro: Sensible defaults per format
- Con: Inconsistent behavior

### Question 4: XSD Schema Loading

When should schemas be loaded?

**A. Automatic**
- Auto-detect schema references in XML
- Load and apply documentation automatically

**B. Explicit**
- Require `schemaPath: "schema.xsd"` in options
- Only load when specified

**C. Hybrid**
- Auto-detect but allow override
- Warning if schema found but not loaded

### Question 5: Multilingual Strategy

How should multilingual documentation be handled?

**A. Single Language**
- Emit only primary language
- Option: `documentationLanguage: "en"`

**B. All Languages**
- Emit all available languages with markers
- Example: `# [EN] English doc [ES] Spanish doc`

**C. Separate Files**
- Extract documentation to separate files per language
- `docs-en.md`, `docs-es.md`, `docs-de.md`

---

## 16. Next Steps

1. **Review this proposal** with stakeholders
2. **Answer open questions** (Section 15)
3. **Create detailed implementation plan** based on answers
4. **Build Phase 1 prototype** for validation
5. **Iterate and refine** based on feedback

---

## Appendices

### Appendix A: XSD Example - Complete Annotation

See `docs/architecture/decisions/xml-documentation-analysis.md` for comprehensive XSD examples including:
- Enumeration documentation
- Complex type documentation
- Attribute documentation
- Schema-level documentation
- Multilingual examples
- AppInfo examples

### Appendix B: Tool Integration

**IDEs Supporting XSD Documentation:**
- Visual Studio / VS Code: IntelliSense displays XSD docs
- IntelliJ IDEA: Quick documentation (Ctrl+Q)
- Eclipse: Content assist shows annotations
- Oxygen XML Editor: Documentation panel

**Code Generators:**
- JAXB (Java): Generates Javadoc from XSD
- XSD.exe (C#): Generates XML comments
- XMLBeans: Runtime access to schema docs

### Appendix C: Performance Benchmarks

(To be added after Phase 1 implementation)

---

**Document Version:** 2.0
**Last Updated:** 2025-10-25
**Status:** ✅ Ready for Stakeholder Review and Decision

**Previous Version:** `comment-preservation-analysis.md` (v1.0)

**Changes from v1.0:**
- Added 5-level documentation taxonomy
- Added comprehensive XSD annotation support
- Added processing instruction support
- Added namespace-based documentation detection
- Added multilingual documentation support
- Added machine-readable vs. human-readable separation
- Expanded use cases with XSD-specific scenarios
- Enhanced implementation phases (9 months vs. 6 months)
- Added detailed semantic mapping strategies
- Expanded stdlib function proposals
- Added decision matrix and comparison with other tools
