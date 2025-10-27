# RAML Fragments Integration Study

## Executive Summary

**Format:** RAML Fragments (Modular RAML Components)
**Primary Use Case:** Reusable, modular API schema and specification components
**Output Format:** YAML (JSON-compatible subset)
**Dependencies:** **0 MB** (reuse YAML serializer, same as base RAML)
**USDL Extensions Required:** Minimal - mainly metadata directives
**Effort Estimate:** 3-5 days (DataType/Library fragments), 8-12 days (all fragment types)
**Strategic Value:** **Medium** - Useful for modular schema libraries, but RAML ecosystem declining
**Recommendation:** **Implement DataType & Library fragments only** - Focus on schema modularity, defer API-level fragments

**Key Insight:** RAML Fragments are about **modularity and reuse**. **DataType fragments** map perfectly to USDL's modular type definitions, making them the most valuable fragment type for UTL-X. Other fragment types (Traits, ResourceTypes, Overlays) are API-level concepts beyond USDL scope.

**RAML Fragment Types:**
- ✅ **DataType** - Schema definitions (perfect USDL fit)
- ✅ **Library** - Collections of types (USDL modules)
- ⚠️ **Trait** - Reusable API traits (API-level, not USDL scope)
- ⚠️ **ResourceType** - Resource templates (API-level, not USDL scope)
- ⚠️ **SecurityScheme** - Authentication/authorization (API-level)
- ⚠️ **AnnotationTypeDeclaration** - Custom metadata (possible with USDL)
- ⚠️ **DocumentationItem** - Documentation (metadata)
- ✅ **Example** - Sample data (USDL %example directive exists)
- ❌ **Overlay** - Spec modifications (beyond USDL)
- ❌ **Extension** - Spec extensions (beyond USDL)

---

## 1. RAML Fragments Overview

### What are RAML Fragments?

**RAML Fragments** are **modular, reusable pieces** of RAML specifications that can be stored in separate files and imported into main API definitions. They enable:

- **Code reuse** - Define once, use many times
- **Modularity** - Break large specs into manageable pieces
- **Consistency** - Shared definitions across multiple APIs
- **Team collaboration** - Different teams maintain different fragments
- **Versioning** - Version control for individual components

**Example:**
```
project/
├── api.raml              # Main API specification
├── types/
│   ├── Customer.raml     # DataType fragment
│   ├── Order.raml        # DataType fragment
│   └── common-types.raml # Library fragment
├── traits/
│   ├── pageable.raml     # Trait fragment
│   └── cacheable.raml    # Trait fragment
└── examples/
    ├── customer.json     # Example fragment
    └── order.json        # Example fragment
```

**Main API uses fragments:**
```yaml
#%RAML 1.0
title: E-commerce API
types:
  Customer: !include types/Customer.raml
  Order: !include types/Order.raml
traits:
  pageable: !include traits/pageable.raml
```

### Fragment Types in RAML 1.0

**Fragment Type Syntax:**
```yaml
#%RAML 1.0 <FragmentType>
# Fragment content...
```

**10 Fragment Types:**

| Fragment Type | Identifier | Purpose | USDL Fit |
|---------------|------------|---------|----------|
| **DataType** | `#%RAML 1.0 DataType` | Type/schema definitions | ✅ **Perfect** |
| **Library** | `#%RAML 1.0 Library` | Collections of types | ✅ **Good** |
| **Trait** | `#%RAML 1.0 Trait` | Reusable API behaviors | ⚠️ API-level |
| **ResourceType** | `#%RAML 1.0 ResourceType` | Resource templates | ⚠️ API-level |
| **SecurityScheme** | `#%RAML 1.0 SecurityScheme` | Authentication schemes | ⚠️ API-level |
| **AnnotationTypeDeclaration** | `#%RAML 1.0 AnnotationType` | Custom annotations | ⚠️ Metadata |
| **DocumentationItem** | `#%RAML 1.0 DocumentationItem` | Documentation | ⚠️ Metadata |
| **Example** | `#%RAML 1.0 NamedExample` | Sample data | ✅ **Good** |
| **Overlay** | `#%RAML 1.0 Overlay` | Spec modifications | ❌ Beyond USDL |
| **Extension** | `#%RAML 1.0 Extension` | Spec extensions | ❌ Beyond USDL |

**USDL Compatibility:**
- **High compatibility:** DataType, Library, Example (schema-focused)
- **Medium compatibility:** AnnotationType (metadata)
- **Low compatibility:** Trait, ResourceType, SecurityScheme (API-level)
- **No compatibility:** Overlay, Extension (spec manipulation)

---

## 2. DataType Fragments (Highest Priority)

### What are DataType Fragments?

**DataType fragments** define **individual types/schemas** that can be reused across multiple RAML specifications.

**Example: `types/Customer.raml`**
```yaml
#%RAML 1.0 DataType

type: object
description: Customer entity
properties:
  customerId:
    type: integer
    required: true
    description: Unique customer identifier
  firstName:
    type: string
    required: true
    minLength: 1
    maxLength: 50
  lastName:
    type: string
    required: true
  email:
    type: string
    required: true
    pattern: ^.+@.+\..+$
  dateOfBirth:
    type: date-only
    required: false
  status:
    type: string
    enum: [active, inactive, suspended]
    default: active
```

**Usage in Main API:**
```yaml
#%RAML 1.0
title: CRM API
types:
  Customer: !include types/Customer.raml
  Order: !include types/Order.raml
/customers:
  get:
    responses:
      200:
        body:
          application/json:
            type: Customer[]
```

### USDL Mapping for DataType Fragments

**USDL already supports modular types!**

**USDL Equivalent:**
```json
{
  "%types": {
    "Customer": {
      "%kind": "structure",
      "%documentation": "Customer entity",
      "%fields": [
        {
          "%name": "customerId",
          "%type": "integer",
          "%required": true,
          "%description": "Unique customer identifier"
        },
        {
          "%name": "firstName",
          "%type": "string",
          "%required": true,
          "%constraints": {
            "%minLength": 1,
            "%maxLength": 50
          }
        },
        {
          "%name": "lastName",
          "%type": "string",
          "%required": true
        },
        {
          "%name": "email",
          "%type": "string",
          "%required": true,
          "%pattern": "^.+@.+\\..+$"
        },
        {
          "%name": "dateOfBirth",
          "%type": "date",
          "%required": false
        },
        {
          "%name": "status",
          "%type": "string",
          "%required": true,
          "%default": "active",
          "%enum": ["active", "inactive", "suspended"]
        }
      ]
    }
  }
}
```

**Key Insight:** DataType fragments map 1:1 to USDL type definitions!

### Implementation for DataType Fragments

**Very simple - reuse existing RAML serializer:**

```kotlin
class RAMLDataTypeFragmentSerializer(
    private val prettyPrint: Boolean = true
) {
    fun serialize(udm: UDM): String {
        // Step 1: Extract single type from USDL
        val types = (udm as UDM.Object).properties["%types"] as? UDM.Object
            ?: throw IllegalArgumentException("USDL requires '%types' directive")

        // Step 2: Get the single type (DataType fragments are single types)
        val typeName = types.properties.keys.firstOrNull()
            ?: throw IllegalArgumentException("DataType fragment requires exactly one type")

        val typeDef = types.properties[typeName] as UDM.Object

        // Step 3: Convert USDL type → RAML type
        val ramlType = convertUSDLToRAMLType(typeDef)

        // Step 4: Add fragment header
        val fragment = UDM.Object(
            properties = mapOf(
                "_header" to UDM.Scalar("#%RAML 1.0 DataType")
            ) + ramlType.properties
        )

        // Step 5: Serialize with YAML serializer
        val yamlSerializer = YAMLSerializer(prettyPrint)
        return yamlSerializer.serialize(fragment)
    }
}
```

**Effort:** 1-2 days (simple extension of existing RAML serializer)

---

## 3. Library Fragments (High Priority)

### What are Library Fragments?

**Library fragments** are **collections of types** that can be imported as a group.

**Example: `types/common-types.raml`**
```yaml
#%RAML 1.0 Library

types:
  Email:
    type: string
    pattern: ^.+@.+\..+$
    description: Email address format

  PhoneNumber:
    type: string
    pattern: ^\+?[1-9]\d{1,14}$
    description: E.164 phone number format

  Money:
    type: object
    properties:
      amount:
        type: number
        minimum: 0
      currency:
        type: string
        minLength: 3
        maxLength: 3
        description: ISO 4217 currency code

  Address:
    type: object
    properties:
      street: string
      city: string
      state: string
      zipCode: string
      country:
        type: string
        minLength: 2
        maxLength: 2
        description: ISO 3166-1 alpha-2 country code
```

**Usage in Main API:**
```yaml
#%RAML 1.0
title: E-commerce API
uses:
  common: types/common-types.raml

types:
  Customer:
    type: object
    properties:
      email: common.Email          # Reference from library
      phone: common.PhoneNumber
      address: common.Address
```

### USDL Mapping for Library Fragments

**USDL supports multiple types in single file:**

**USDL Equivalent: `common-types.json`**
```json
{
  "%types": {
    "Email": {
      "%kind": "primitive",
      "%baseType": "string",
      "%pattern": "^.+@.+\\..+$",
      "%documentation": "Email address format"
    },
    "PhoneNumber": {
      "%kind": "primitive",
      "%baseType": "string",
      "%pattern": "^\\+?[1-9]\\d{1,14}$",
      "%documentation": "E.164 phone number format"
    },
    "Money": {
      "%kind": "structure",
      "%fields": [
        {"%name": "amount", "%type": "number", "%required": true, "%minimum": 0},
        {"%name": "currency", "%type": "string", "%required": true, "%minLength": 3, "%maxLength": 3, "%description": "ISO 4217 currency code"}
      ]
    },
    "Address": {
      "%kind": "structure",
      "%fields": [
        {"%name": "street", "%type": "string", "%required": true},
        {"%name": "city", "%type": "string", "%required": true},
        {"%name": "state", "%type": "string", "%required": true},
        {"%name": "zipCode", "%type": "string", "%required": true},
        {"%name": "country", "%type": "string", "%required": true, "%minLength": 2, "%maxLength": 2, "%description": "ISO 3166-1 alpha-2 country code"}
      ]
    }
  }
}
```

**Key Insight:** Library fragments are just multiple USDL types in one file!

### Implementation for Library Fragments

**Even simpler - Library = multiple types:**

```kotlin
class RAMLLibraryFragmentSerializer(
    private val prettyPrint: Boolean = true
) {
    fun serialize(udm: UDM): String {
        // Step 1: Extract types from USDL
        val types = (udm as UDM.Object).properties["%types"] as? UDM.Object
            ?: throw IllegalArgumentException("USDL requires '%types' directive")

        // Step 2: Convert all USDL types → RAML types
        val ramlTypes = mutableMapOf<String, UDM>()
        types.properties.forEach { (typeName, typeDef) ->
            ramlTypes[typeName] = convertUSDLToRAMLType(typeDef as UDM.Object)
        }

        // Step 3: Build library structure
        val library = UDM.Object(
            properties = mapOf(
                "_header" to UDM.Scalar("#%RAML 1.0 Library"),
                "types" to UDM.Object(properties = ramlTypes)
            )
        )

        // Step 4: Serialize with YAML serializer
        val yamlSerializer = YAMLSerializer(prettyPrint)
        return yamlSerializer.serialize(library)
    }
}
```

**Effort:** 1-2 days (very similar to DataType fragment)

---

## 4. Example Fragments (Medium Priority)

### What are Example Fragments?

**Example fragments** are **sample data** for types, stored as separate files.

**Example: `examples/customer-example.raml`**
```yaml
#%RAML 1.0 NamedExample

value:
  customerId: 12345
  firstName: "John"
  lastName: "Smith"
  email: "john.smith@example.com"
  dateOfBirth: "1985-03-15"
  status: "active"
  address:
    street: "123 Main St"
    city: "Springfield"
    state: "IL"
    zipCode: "62701"
    country: "US"
```

**Usage in API:**
```yaml
types:
  Customer:
    type: object
    properties:
      # ... property definitions
    example: !include examples/customer-example.raml
```

### USDL Mapping for Example Fragments

**USDL has `%example` directive:**

```json
{
  "%types": {
    "Customer": {
      "%kind": "structure",
      "%example": {
        "customerId": 12345,
        "firstName": "John",
        "lastName": "Smith",
        "email": "john.smith@example.com",
        "dateOfBirth": "1985-03-15",
        "status": "active"
      },
      "%fields": [...]
    }
  }
}
```

**For separate example files:**
```json
{
  "%example": {
    "customerId": 12345,
    "firstName": "John",
    "lastName": "Smith",
    "email": "john.smith@example.com"
  }
}
```

**Implementation:**

```kotlin
class RAMLExampleFragmentSerializer {
    fun serialize(udm: UDM): String {
        // Extract %example directive
        val example = (udm as UDM.Object).properties["%example"]
            ?: throw IllegalArgumentException("Example fragment requires '%example' directive")

        // Build RAML example structure
        val fragment = UDM.Object(
            properties = mapOf(
                "_header" to UDM.Scalar("#%RAML 1.0 NamedExample"),
                "value" to example
            )
        )

        val yamlSerializer = YAMLSerializer(prettyPrint = true)
        return yamlSerializer.serialize(fragment)
    }
}
```

**Effort:** 0.5-1 day (very simple)

---

## 5. API-Level Fragments (Lower Priority)

### Trait Fragments (API-Level)

**Traits** define reusable API behaviors (pagination, caching, rate limiting).

**Example: `traits/pageable.raml`**
```yaml
#%RAML 1.0 Trait

queryParameters:
  page:
    type: integer
    minimum: 1
    default: 1
    description: Page number
  pageSize:
    type: integer
    minimum: 1
    maximum: 100
    default: 20
    description: Items per page
responses:
  200:
    headers:
      X-Total-Count:
        type: integer
        description: Total number of items
      X-Page-Count:
        type: integer
        description: Total number of pages
```

**Usage:**
```yaml
/customers:
  get:
    is: [pageable]  # Apply trait
    responses:
      200:
        body:
          application/json:
            type: Customer[]
```

**USDL Fit:** ❌ **Not applicable** - Traits are API-level behaviors, not data structures

**Recommendation:** Skip traits - beyond USDL scope (use OpenAPI for API specs)

### ResourceType Fragments (API-Level)

**ResourceTypes** are templates for REST resources.

**Example: `resource-types/collection.raml`**
```yaml
#%RAML 1.0 ResourceType

get:
  description: Retrieve all <<resourcePathName>>
  responses:
    200:
      body:
        application/json:
          type: <<resourceType>>[]
post:
  description: Create a new <<resourcePathName | !singularize>>
  body:
    application/json:
      type: <<resourceType>>
  responses:
    201:
      body:
        application/json:
          type: <<resourceType>>
```

**USDL Fit:** ❌ **Not applicable** - Resource templates are API-level, not data structures

**Recommendation:** Skip resource types - beyond USDL scope

### SecurityScheme Fragments (API-Level)

**SecuritySchemes** define authentication/authorization.

**Example: `security/oauth2.raml`**
```yaml
#%RAML 1.0 SecurityScheme

type: OAuth 2.0
describedBy:
  headers:
    Authorization:
      description: Bearer token
      type: string
      pattern: ^Bearer .+$
settings:
  authorizationUri: https://auth.example.com/oauth2/authorize
  accessTokenUri: https://auth.example.com/oauth2/token
  authorizationGrants: [authorization_code, implicit]
  scopes:
    - read:customers
    - write:customers
    - read:orders
    - write:orders
```

**USDL Fit:** ❌ **Not applicable** - Security is API-level, not data structures

**Recommendation:** Skip security schemes - beyond USDL scope

---

## 6. Metadata Fragments (Low Priority)

### AnnotationTypeDeclaration Fragments

**Annotations** define custom metadata that can be attached to RAML elements.

**Example: `annotations/experimental.raml`**
```yaml
#%RAML 1.0 AnnotationTypeDeclaration

allowedTargets: [API, Resource, Method, Response, RequestBody, ResponseBody, TypeDeclaration]
type: object
properties:
  version:
    type: string
    description: Version when feature will be stable
  note:
    type: string
    description: Additional notes about experimental status
```

**Usage:**
```yaml
/new-feature:
  (experimental):
    version: "2.0"
    note: "This endpoint is under development"
  get:
    responses:
      200:
        body:
          application/json:
            type: NewFeature
```

**USDL Fit:** ⚠️ **Possible** - Could use USDL metadata directives

**Potential USDL Extension:**
```json
{
  "%annotations": {
    "experimental": {
      "%allowedTargets": ["TYPE_DEFINITION", "FIELD_DEFINITION"],
      "%properties": {
        "version": "string",
        "note": "string"
      }
    }
  }
}
```

**Effort:** 2-3 days (new USDL feature)
**Recommendation:** Low priority - niche use case

### DocumentationItem Fragments

**DocumentationItem** fragments contain documentation content.

**Example: `docs/getting-started.raml`**
```yaml
#%RAML 1.0 DocumentationItem

title: Getting Started
content: |
  # Getting Started with Our API

  This API provides access to customer and order data.

  ## Authentication

  All requests require an API key...

  ## Rate Limiting

  Requests are limited to 1000 per hour...
```

**USDL Fit:** ⚠️ **Possible** - Could store as %documentation

**Recommendation:** Low priority - documentation is separate concern

---

## 7. Overlay and Extension Fragments (Out of Scope)

### Overlay Fragments

**Overlays** modify existing RAML specifications without changing the original file.

**Example: `overlays/production.raml`**
```yaml
#%RAML 1.0 Overlay

# Extends api.raml
extends: ../api.raml

# Override base URI for production
baseUri: https://api.production.example.com

# Add production-specific annotations
(monitoring):
  enabled: true
  alerting: critical
```

**USDL Fit:** ❌ **Not applicable** - Overlays are about spec composition, not data structures

**Recommendation:** Out of scope - this is configuration management, not schema definition

### Extension Fragments

**Extensions** extend existing RAML specifications with new resources.

**Example: `extensions/beta-features.raml`**
```yaml
#%RAML 1.0 Extension

# Extends api.raml
extends: ../api.raml

# Add new beta endpoints
/beta/analytics:
  get:
    description: Analytics data (beta)
    responses:
      200:
        body:
          application/json:
            type: AnalyticsData
```

**USDL Fit:** ❌ **Not applicable** - Extensions are API-level composition

**Recommendation:** Out of scope - beyond USDL scope

---

## 8. USDL Mapping Summary

### Fragment Type Compatibility Matrix

| Fragment Type | USDL Compatibility | Implementation Effort | Strategic Value | Recommendation |
|---------------|-------------------|----------------------|-----------------|----------------|
| **DataType** | ✅ **Excellent** (1:1 mapping) | 1-2 days | **High** | ✅ **Implement** |
| **Library** | ✅ **Excellent** (multiple types) | 1-2 days | **High** | ✅ **Implement** |
| **Example** | ✅ **Good** (%example exists) | 0.5-1 day | **Medium** | ✅ **Implement** |
| **AnnotationType** | ⚠️ **Possible** (metadata) | 2-3 days | Low | ⚠️ **Defer** |
| **DocumentationItem** | ⚠️ **Possible** (%documentation) | 1-2 days | Low | ⚠️ **Defer** |
| **Trait** | ❌ **Not applicable** (API-level) | N/A | Low | ❌ **Skip** |
| **ResourceType** | ❌ **Not applicable** (API-level) | N/A | Low | ❌ **Skip** |
| **SecurityScheme** | ❌ **Not applicable** (API-level) | N/A | Low | ❌ **Skip** |
| **Overlay** | ❌ **Not applicable** (composition) | N/A | Low | ❌ **Skip** |
| **Extension** | ❌ **Not applicable** (composition) | N/A | Low | ❌ **Skip** |

### Recommended Implementation Scope

**Phase 1 (High Priority - 3-5 days):**
- ✅ DataType fragments (1-2 days)
- ✅ Library fragments (1-2 days)
- ✅ Example fragments (0.5-1 day)
- ✅ Testing & documentation (0.5-1 day)

**Phase 2 (Optional - 3-5 days):**
- ⚠️ AnnotationType fragments (2-3 days)
- ⚠️ DocumentationItem fragments (1-2 days)

**Out of Scope:**
- ❌ Trait fragments (API-level)
- ❌ ResourceType fragments (API-level)
- ❌ SecurityScheme fragments (API-level)
- ❌ Overlay fragments (composition)
- ❌ Extension fragments (composition)

---

## 9. Implementation Architecture

### Module Structure

```
formats/raml/
├── RAMLTypeSerializer.kt           # Base RAML serializer (existing)
├── RAMLTypeParser.kt               # Base RAML parser (existing)
├── fragments/
│   ├── RAMLFragmentSerializer.kt   # ← NEW: Fragment serializer factory
│   ├── RAMLFragmentParser.kt       # ← NEW: Fragment parser factory
│   ├── RAMLDataTypeFragmentSerializer.kt    # ← NEW
│   ├── RAMLLibraryFragmentSerializer.kt     # ← NEW
│   ├── RAMLExampleFragmentSerializer.kt     # ← NEW
│   └── RAMLFragmentValidator.kt             # ← NEW
└── test/
    └── fragments/
        ├── DataTypeFragmentTest.kt
        ├── LibraryFragmentTest.kt
        └── ExampleFragmentTest.kt
```

### Fragment Serializer Factory

```kotlin
package org.apache.utlx.formats.raml.fragments

enum class RAMLFragmentType {
    DATATYPE,
    LIBRARY,
    EXAMPLE,
    TRAIT,
    RESOURCE_TYPE,
    SECURITY_SCHEME,
    ANNOTATION_TYPE,
    DOCUMENTATION_ITEM,
    OVERLAY,
    EXTENSION
}

class RAMLFragmentSerializer(
    private val fragmentType: RAMLFragmentType,
    private val prettyPrint: Boolean = true
) {
    fun serialize(udm: UDM): String {
        return when (fragmentType) {
            RAMLFragmentType.DATATYPE -> {
                RAMLDataTypeFragmentSerializer(prettyPrint).serialize(udm)
            }
            RAMLFragmentType.LIBRARY -> {
                RAMLLibraryFragmentSerializer(prettyPrint).serialize(udm)
            }
            RAMLFragmentType.EXAMPLE -> {
                RAMLExampleFragmentSerializer(prettyPrint).serialize(udm)
            }
            else -> {
                throw UnsupportedOperationException(
                    "Fragment type $fragmentType not supported. " +
                    "Only DataType, Library, and Example fragments are supported."
                )
            }
        }
    }
}
```

### DataType Fragment Serializer

```kotlin
class RAMLDataTypeFragmentSerializer(
    private val prettyPrint: Boolean = true
) {
    fun serialize(udm: UDM): String {
        // Step 1: Validate input (must be single type)
        val types = (udm as UDM.Object).properties["%types"] as? UDM.Object
            ?: throw IllegalArgumentException("USDL requires '%types' directive")

        if (types.properties.size != 1) {
            throw IllegalArgumentException(
                "DataType fragment requires exactly one type. " +
                "Got ${types.properties.size} types. " +
                "Use Library fragment for multiple types."
            )
        }

        // Step 2: Extract the single type
        val (typeName, typeDef) = types.properties.entries.first()
        val typeDefObj = typeDef as UDM.Object

        // Step 3: Convert USDL type → RAML type structure
        val ramlType = convertUSDLToRAMLType(typeDefObj)

        // Step 4: Serialize with YAML serializer
        val yamlSerializer = YAMLSerializer(prettyPrint)
        val yamlContent = yamlSerializer.serialize(ramlType)

        // Step 5: Add RAML fragment header
        return "#%RAML 1.0 DataType\n\n$yamlContent"
    }

    private fun convertUSDLToRAMLType(typeDef: UDM.Object): UDM {
        // Reuse conversion logic from base RAMLTypeSerializer
        // (same as existing RAML integration)
        val kind = (typeDef.properties["%kind"] as? UDM.Scalar)?.value as? String

        return when (kind) {
            "structure" -> convertStructureToRAML(typeDef)
            "enumeration" -> convertEnumToRAML(typeDef)
            "primitive" -> convertPrimitiveToRAML(typeDef)
            else -> throw IllegalArgumentException("Unknown kind: $kind")
        }
    }

    private fun convertStructureToRAML(structDef: UDM.Object): UDM {
        // Implementation from existing RAMLTypeSerializer
        // ...
    }

    // ... other conversion methods
}
```

### Library Fragment Serializer

```kotlin
class RAMLLibraryFragmentSerializer(
    private val prettyPrint: Boolean = true
) {
    fun serialize(udm: UDM): String {
        // Step 1: Extract all types
        val types = (udm as UDM.Object).properties["%types"] as? UDM.Object
            ?: throw IllegalArgumentException("USDL requires '%types' directive")

        // Step 2: Convert all USDL types → RAML types
        val ramlTypes = mutableMapOf<String, UDM>()
        types.properties.forEach { (typeName, typeDef) ->
            ramlTypes[typeName] = convertUSDLToRAMLType(typeDef as UDM.Object)
        }

        // Step 3: Build library structure
        val library = UDM.Object(
            properties = mapOf(
                "types" to UDM.Object(properties = ramlTypes)
            )
        )

        // Step 4: Serialize with YAML serializer
        val yamlSerializer = YAMLSerializer(prettyPrint)
        val yamlContent = yamlSerializer.serialize(library)

        // Step 5: Add RAML fragment header
        return "#%RAML 1.0 Library\n\n$yamlContent"
    }

    private fun convertUSDLToRAMLType(typeDef: UDM.Object): UDM {
        // Reuse conversion logic from DataType serializer
        // ...
    }
}
```

### Example Fragment Serializer

```kotlin
class RAMLExampleFragmentSerializer(
    private val prettyPrint: Boolean = true
) {
    fun serialize(udm: UDM): String {
        // Step 1: Extract example data
        val example = (udm as UDM.Object).properties["%example"]
            ?: throw IllegalArgumentException("Example fragment requires '%example' directive")

        // Step 2: Build RAML example structure
        val exampleFragment = UDM.Object(
            properties = mapOf(
                "value" to example
            )
        )

        // Step 3: Serialize with YAML serializer
        val yamlSerializer = YAMLSerializer(prettyPrint)
        val yamlContent = yamlSerializer.serialize(exampleFragment)

        // Step 4: Add RAML fragment header
        return "#%RAML 1.0 NamedExample\n\n$yamlContent"
    }
}
```

---

## 10. Effort Estimation

### Detailed Breakdown

| Component | Complexity | Effort | Dependencies |
|-----------|------------|--------|--------------|
| **Module Setup** | Low | 0.5 day | - |
| **RAMLFragmentSerializer (factory)** | Low | 0.5 day | Base RAML serializer |
| **RAMLDataTypeFragmentSerializer** | Low | 1 day | Base RAML serializer |
| **RAMLLibraryFragmentSerializer** | Low | 1 day | DataType serializer |
| **RAMLExampleFragmentSerializer** | Low | 0.5 day | YAML serializer |
| **RAMLFragmentParser (factory)** | Low | 0.5 day | Base RAML parser |
| **Fragment validation** | Low | 0.5 day | - |
| **CLI Integration** | Low | 0.5 day | CLI module |
| **Testing & Documentation** | Low | 1 day | - |
| **Total (Phase 1)** | | **5-6 days** | |

### Comparison with Full RAML Support

| Scope | Fragments Included | Effort | Strategic Value |
|-------|-------------------|--------|-----------------|
| **DataType Only** | DataType only | 2-3 days | Medium |
| **Schema Fragments** | DataType + Library + Example | **5-6 days** | **High** |
| **All Fragments** | All 10 fragment types | 12-15 days | Low |
| **Base RAML** | Full API types (no fragments) | 5-7 days | Medium |
| **Full RAML + Fragments** | API specs + all fragments | 20-25 days | Low |

**Recommendation:** **Schema Fragments only** (5-6 days) - DataType, Library, Example

---

## 11. Benefits and Use Cases

### Use Case 1: Modular Schema Library

**Scenario:** Enterprise with shared schema library across multiple APIs

**Solution:**
```bash
# Schema library structure
schema-library/
├── types/
│   ├── Customer.json         # USDL type definition
│   ├── Order.json
│   ├── Product.json
│   └── common-types.json     # USDL library (multiple types)

# Generate RAML fragments
utlx transform types/Customer.json \
  --format raml-fragment --fragment-type datatype \
  -o raml-lib/Customer.raml

utlx transform types/common-types.json \
  --format raml-fragment --fragment-type library \
  -o raml-lib/common-types.raml

# Result: Reusable RAML fragments
raml-lib/
├── Customer.raml        # #%RAML 1.0 DataType
├── Order.raml           # #%RAML 1.0 DataType
├── Product.raml         # #%RAML 1.0 DataType
└── common-types.raml    # #%RAML 1.0 Library
```

**Main APIs use fragments:**
```yaml
#%RAML 1.0
title: Customer API
uses:
  common: ../raml-lib/common-types.raml
types:
  Customer: !include ../raml-lib/Customer.raml
/customers:
  get:
    responses:
      200:
        body:
          application/json:
            type: Customer[]
```

**Benefits:**
- ✅ Single source of truth (USDL)
- ✅ Consistent types across APIs
- ✅ RAML fragments for MuleSoft integration
- ✅ Version control for individual types

### Use Case 2: RAML Migration to OpenAPI

**Scenario:** Migrate RAML API to OpenAPI while preserving types

**Solution:**
```bash
# Step 1: Parse RAML fragments → USDL
utlx schema extract raml-lib/Customer.raml --format usdl -o usdl/Customer.json
utlx schema extract raml-lib/Order.raml --format usdl -o usdl/Order.json

# Step 2: Transform USDL → OpenAPI schemas
utlx transform usdl/Customer.json --format openapi-schema -o openapi/Customer.yaml
utlx transform usdl/Order.json --format openapi-schema -o openapi/Order.yaml

# Step 3: Use in OpenAPI spec
```

**OpenAPI spec:**
```yaml
openapi: 3.1.0
info:
  title: Customer API
components:
  schemas:
    Customer:
      $ref: './schemas/Customer.yaml'
    Order:
      $ref: './schemas/Order.yaml'
```

**Benefits:**
- ✅ Automated type conversion
- ✅ USDL as intermediate format
- ✅ Preserve type definitions during migration

### Use Case 3: Multi-Format Schema Generation

**Scenario:** Generate schemas in multiple formats from single source

**Solution:**
```bash
# Single USDL type definition
cat > Customer.json <<EOF
{
  "%types": {
    "Customer": {
      "%kind": "structure",
      "%fields": [...]
    }
  }
}
EOF

# Generate DataType fragment
utlx transform Customer.json \
  --format raml-fragment --fragment-type datatype \
  -o Customer.raml

# Generate JSON Schema
utlx transform Customer.json --format jsch -o Customer-schema.json

# Generate XSD
utlx transform Customer.json --format xsd -o Customer.xsd

# Generate Avro schema
utlx transform Customer.json --format avro-schema -o Customer.avsc

# Generate Protobuf
utlx transform Customer.json --format protobuf -o Customer.proto
```

**Benefits:**
- ✅ Single source of truth (USDL)
- ✅ Consistent schemas across formats
- ✅ RAML fragments for API specs
- ✅ JSON Schema for validation
- ✅ Avro/Protobuf for data serialization

### Use Case 4: Example Data Management

**Scenario:** Maintain example data separately from schemas

**Solution:**
```bash
# USDL type with example
cat > Customer.json <<EOF
{
  "%types": {
    "Customer": {
      "%kind": "structure",
      "%fields": [...],
      "%example": {
        "customerId": 12345,
        "firstName": "John",
        "lastName": "Smith"
      }
    }
  }
}
EOF

# Generate DataType fragment (schema)
utlx transform Customer.json \
  --format raml-fragment --fragment-type datatype \
  -o types/Customer.raml

# Generate Example fragment (data)
utlx transform Customer.json \
  --format raml-fragment --fragment-type example \
  -o examples/customer-example.raml
```

**Result:**
```yaml
# types/Customer.raml
#%RAML 1.0 DataType
type: object
properties:
  customerId:
    type: integer
  # ...

# examples/customer-example.raml
#%RAML 1.0 NamedExample
value:
  customerId: 12345
  firstName: "John"
  lastName: "Smith"
```

**Benefits:**
- ✅ Separate schema from examples
- ✅ Reusable example data
- ✅ Better organization

### Use Case 5: Team Collaboration

**Scenario:** Different teams maintain different schema domains

**Solution:**
```
company-schemas/
├── customer-team/
│   ├── usdl/
│   │   ├── Customer.json
│   │   ├── Contact.json
│   │   └── Address.json
│   └── raml/
│       ├── Customer.raml       # Generated fragments
│       ├── Contact.raml
│       └── Address.raml
├── order-team/
│   ├── usdl/
│   │   ├── Order.json
│   │   ├── OrderItem.json
│   │   └── Shipment.json
│   └── raml/
│       ├── Order.raml
│       ├── OrderItem.raml
│       └── Shipment.raml
└── common/
    ├── usdl/
    │   └── common-types.json
    └── raml/
        └── common-types.raml   # Library fragment
```

**CI/CD Pipeline:**
```bash
# Regenerate RAML fragments when USDL changes
find . -name "*.json" -path "*/usdl/*" | while read usdl_file; do
  raml_file=$(echo $usdl_file | sed 's/usdl/raml/' | sed 's/.json/.raml/')
  utlx transform $usdl_file \
    --format raml-fragment --fragment-type auto \
    -o $raml_file
done
```

**Benefits:**
- ✅ Team autonomy (own USDL files)
- ✅ Automated RAML generation
- ✅ Shared common types
- ✅ Clear ownership

---

## 12. Testing Strategy

### Unit Tests (30+ tests)

**RAMLDataTypeFragmentSerializer Tests:**
```kotlin
@Test
fun `serialize single USDL type to DataType fragment`() {
    val usdl = """
    {
      "%types": {
        "Customer": {
          "%kind": "structure",
          "%fields": [
            {"%name": "customerId", "%type": "integer", "%required": true},
            {"%name": "name", "%type": "string", "%required": true}
          ]
        }
      }
    }
    """.trimIndent()

    val serializer = RAMLDataTypeFragmentSerializer()
    val raml = serializer.serialize(parseJSON(usdl))

    raml shouldStartWith "#%RAML 1.0 DataType"
    raml shouldContain "type: object"
    raml shouldContain "customerId:"
    raml shouldContain "type: integer"
}

@Test
fun `reject multiple types for DataType fragment`() {
    val usdl = """
    {
      "%types": {
        "Customer": {...},
        "Order": {...}
      }
    }
    """.trimIndent()

    val serializer = RAMLDataTypeFragmentSerializer()

    shouldThrow<IllegalArgumentException> {
        serializer.serialize(parseJSON(usdl))
    }.message shouldContain "exactly one type"
}
```

**RAMLLibraryFragmentSerializer Tests:**
```kotlin
@Test
fun `serialize multiple USDL types to Library fragment`() {
    val usdl = """
    {
      "%types": {
        "Email": {
          "%kind": "primitive",
          "%baseType": "string",
          "%pattern": "^.+@.+\\..+$"
        },
        "PhoneNumber": {
          "%kind": "primitive",
          "%baseType": "string",
          "%pattern": "^\\+?[1-9]\\d{1,14}$"
        }
      }
    }
    """.trimIndent()

    val serializer = RAMLLibraryFragmentSerializer()
    val raml = serializer.serialize(parseJSON(usdl))

    raml shouldStartWith "#%RAML 1.0 Library"
    raml shouldContain "types:"
    raml shouldContain "Email:"
    raml shouldContain "PhoneNumber:"
}
```

**RAMLExampleFragmentSerializer Tests:**
```kotlin
@Test
fun `serialize example to NamedExample fragment`() {
    val usdl = """
    {
      "%example": {
        "customerId": 12345,
        "name": "John Smith",
        "email": "john@example.com"
      }
    }
    """.trimIndent()

    val serializer = RAMLExampleFragmentSerializer()
    val raml = serializer.serialize(parseJSON(usdl))

    raml shouldStartWith "#%RAML 1.0 NamedExample"
    raml shouldContain "value:"
    raml shouldContain "customerId: 12345"
    raml shouldContain "name: John Smith"
}
```

### Conformance Tests (15+ tests)

```bash
test-data/raml-fragments/
├── datatype/
│   ├── simple-customer.json       # USDL input
│   ├── simple-customer.raml       # Expected RAML fragment
│   ├── nested-order.json
│   └── nested-order.raml
├── library/
│   ├── common-types.json
│   ├── common-types.raml
│   ├── validation-types.json
│   └── validation-types.raml
└── example/
    ├── customer-example.json
    ├── customer-example.raml
    ├── order-example.json
    └── order-example.raml
```

### Integration Tests (10+ tests)

1. **CLI generation test** - Generate fragments via CLI
2. **Fragment validation** - Validate with RAML parser
3. **Multi-fragment workflow** - Generate DataType + Example
4. **Library import test** - Use generated library in main API
5. **Migration test** - RAML fragment → USDL → OpenAPI schema
6. **Roundtrip test** - USDL → RAML fragment → USDL

---

## 13. Strategic Analysis

### RAML Fragments vs Base RAML

**RAML Fragments Advantages:**
- ✅ **Modularity** - Separate files for each type
- ✅ **Reusability** - Share types across APIs
- ✅ **Team collaboration** - Different teams own different fragments
- ✅ **Version control** - Individual type versioning
- ✅ **Organization** - Better file structure

**Implementation Effort:**
- Base RAML types: 5-7 days
- RAML fragments (schema only): **5-6 days** (similar effort!)
- Full fragments (all types): 12-15 days

**Recommendation:** **Implement schema fragments** (DataType, Library, Example) - Same effort as base RAML, better modularity

### Market Reality

**RAML Ecosystem Status:**
- ~10-15% market share (declining)
- MuleSoft Anypoint Platform primary use case
- Many projects migrated to OpenAPI
- Limited new adoption

**Fragment Usage:**
- Fragments are advanced RAML feature
- Primarily used by larger organizations with multiple APIs
- Smaller projects use monolithic RAML files

**UTL-X Value Proposition:**
- Generate modular RAML fragments from USDL
- Support RAML → OpenAPI migration
- Provide schema library management

### Comparison with OpenAPI

| Feature | RAML Fragments | OpenAPI Components |
|---------|---------------|-------------------|
| **Modularity** | ✅ Separate files | ✅ $ref references |
| **Fragment Types** | 10 types | Components only |
| **Market Share** | ~10% | ~80% |
| **Tool Support** | Moderate | Excellent |
| **Syntax** | Fragment headers | Standard JSON/YAML |
| **Reusability** | Excellent | Excellent |

**Verdict:** OpenAPI has broader adoption, but RAML fragments have better modularity syntax

---

## 14. Success Metrics

### Technical Metrics

**Phase 1 (Schema Fragments):**
- ✅ Generate valid DataType fragments from USDL
- ✅ Generate valid Library fragments from USDL
- ✅ Generate valid Example fragments from USDL
- ✅ 100% test coverage for fragment serializers
- ✅ Validate fragments with RAML parser

**Performance:**
- < 10ms to generate simple DataType fragment
- < 50ms to generate Library fragment (10 types)
- < 5ms to generate Example fragment

### User Adoption Metrics

**6 Months Post-Launch:**
- 5-10 RAML fragment generation workflows
- 3-5 RAML → OpenAPI migrations using fragments
- 2-3 schema library implementations

**12 Months Post-Launch:**
- 15-20 active fragment users
- 10+ RAML API projects using generated fragments
- Integration with MuleSoft tooling

**Reality Check:** Lower adoption than OpenAPI due to declining RAML ecosystem

### Business Metrics

**Value Proposition:**
- Modular schema management
- RAML → OpenAPI migration support
- MuleSoft Anypoint Platform integration

**ROI:**
- Low-medium (RAML declining)
- Primarily useful for existing RAML users
- Migration tool value

---

## 15. Conclusion and Recommendations

### Summary

RAML Fragments provide **modularity and reusability** for RAML specifications. Of the 10 fragment types:

- ✅ **3 types align with USDL** (DataType, Library, Example) - Schema-focused
- ⚠️ **2 types possible** (AnnotationType, DocumentationItem) - Metadata
- ❌ **5 types out of scope** (Trait, ResourceType, SecurityScheme, Overlay, Extension) - API-level

**Key Findings:**

1. **DataType & Library Fragments: Perfect Fit**
   - 1:1 mapping with USDL type definitions
   - 5-6 days implementation
   - High value for modular schemas

2. **Example Fragments: Good Fit**
   - USDL %example directive exists
   - Simple implementation (0.5-1 day)
   - Useful for test data

3. **API-Level Fragments: Out of Scope**
   - Traits, ResourceTypes, SecuritySchemes are API-level
   - Not applicable to USDL (schema-focused)
   - Skip implementation

4. **Metadata Fragments: Low Priority**
   - Annotations and documentation are possible
   - Lower value than schema fragments
   - Defer to Phase 2

5. **Zero Dependencies**
   - Reuse YAML serializer (like base RAML)
   - 0 MB new dependencies
   - Simple implementation

### Recommendations

**Recommendation 1: Implement Schema Fragments (Priority)**

**Phase 1 (5-6 days):**
- ✅ DataType fragments (1-2 days)
- ✅ Library fragments (1-2 days)
- ✅ Example fragments (0.5-1 day)
- ✅ Testing & documentation (1-2 days)

**Benefits:**
- Modular schema management
- Better than monolithic RAML files
- Same effort as base RAML (5-7 days)
- Higher value (modularity + reusability)

**Recommendation 2: Skip API-Level Fragments**

- ❌ Trait fragments
- ❌ ResourceType fragments
- ❌ SecurityScheme fragments
- ❌ Overlay fragments
- ❌ Extension fragments

**Rationale:** These are API-level concepts beyond USDL scope. Use OpenAPI for API specifications.

**Recommendation 3: Defer Metadata Fragments**

- ⚠️ AnnotationType fragments (Phase 2)
- ⚠️ DocumentationItem fragments (Phase 2)

**Rationale:** Lower priority than schema fragments. Implement if user demand exists.

**Recommendation 4: Position as Modular Alternative**

**Marketing angle:**
- "Generate modular RAML schemas from USDL"
- "Better organization than monolithic RAML files"
- "Supports RAML → OpenAPI migration"
- "Schema library management"

**Recommendation 5: Consider Strategic Value**

**RAML ecosystem is declining:**
- 10-15% market share (vs 80%+ for OpenAPI)
- Limited new adoption
- MuleSoft primary use case

**Should we implement?**
- ✅ **Yes** if MuleSoft integration is strategic priority
- ✅ **Yes** if targeting RAML → OpenAPI migration
- ⚠️ **Maybe** if focusing on schema modularity (OpenAPI also supports)
- ❌ **No** if prioritizing market-leading formats

**Alternative:** Implement **OpenAPI Components** instead (similar modularity, 80% market share)

### Final Verdict

**PROCEED with RAML Schema Fragments** as **low-medium priority** (after OpenAPI, Avro, SQL DDL):

- **Effort:** 5-6 days (same as base RAML)
- **Value:** Modular schema management
- **Market:** Declining but useful for existing users
- **Scope:** DataType, Library, Example fragments only

**Or DEFER** and prioritize OpenAPI instead (higher strategic value).

---

## Appendix A: RAML Fragment Header Reference

```yaml
# DataType Fragment
#%RAML 1.0 DataType
type: object
properties:
  # ...

# Library Fragment
#%RAML 1.0 Library
types:
  Type1: ...
  Type2: ...

# Example Fragment
#%RAML 1.0 NamedExample
value:
  field1: value1
  field2: value2

# Trait Fragment (Out of Scope)
#%RAML 1.0 Trait
queryParameters:
  # ...

# ResourceType Fragment (Out of Scope)
#%RAML 1.0 ResourceType
get:
  # ...

# SecurityScheme Fragment (Out of Scope)
#%RAML 1.0 SecurityScheme
type: OAuth 2.0
settings:
  # ...

# AnnotationType Fragment (Low Priority)
#%RAML 1.0 AnnotationTypeDeclaration
type: string
allowedTargets: [...]

# DocumentationItem Fragment (Low Priority)
#%RAML 1.0 DocumentationItem
title: Getting Started
content: |
  # Documentation...

# Overlay Fragment (Out of Scope)
#%RAML 1.0 Overlay
extends: ../api.raml
# Modifications...

# Extension Fragment (Out of Scope)
#%RAML 1.0 Extension
extends: ../api.raml
# New resources...
```

---

## Appendix B: USDL Fragment Directives

**Potential new USDL directives for fragment metadata:**

```kotlin
// Add to USDL10.kt - Tier 2 Common (Fragment Metadata)

Directive(
    name = "%fragmentType",
    tier = Tier.COMMON,
    scopes = setOf(Scope.ROOT),
    valueType = "String",
    description = "RAML fragment type (datatype, library, example)",
    supportedFormats = setOf("raml")
),
Directive(
    name = "%example",
    tier = Tier.COMMON,
    scopes = setOf(Scope.TYPE_DEFINITION, Scope.ROOT),
    valueType = "Any",
    description = "Example data for type or standalone example",
    supportedFormats = setOf("raml", "openapi", "jsch", "avro")
)
```

**Usage:**
```json
{
  "%fragmentType": "datatype",
  "%types": {
    "Customer": {
      "%kind": "structure",
      "%fields": [...]
    }
  }
}
```

**Or for examples:**
```json
{
  "%fragmentType": "example",
  "%example": {
    "customerId": 12345,
    "name": "John Smith"
  }
}
```

---

**END OF DOCUMENT**
