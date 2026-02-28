# OData v4+ Implementation Plan for UTLX

## Context

OData has a dual nature: it is both a **metadata/schema description** (CSDL/EDMX — like XSD or JSON Schema) and a **data exchange format** (OData JSON — like JSON or XML). This requires two distinct integration tracks in UTLX:

- **Tier 1 (Data):** OData JSON payloads as transformation input/output — the actual entity data with `@odata.*` control annotations
- **Tier 2 (Schema):** CSDL/EDMX metadata as schema format (`osch`) — entity models with types, keys, navigation properties, annotations

Both tracks converge through UDM (Universal Data Model) as the internal representation, following the established UTLX pattern where all formats parse to UDM and serialize from UDM.

### What Already Exists

| Component | Status |
|-----------|--------|
| 5 USDL directives (`%entityType`, `%navigation`, `%target`, `%cardinality`, `%referentialConstraint`) | Defined in USDL 1.0 |
| Format compatibility metadata (60% rating, entity-model domain) | Defined in DirectiveRegistry |
| 5 example UTLX files demonstrating OData patterns | In `examples/usdl/odata/` |
| EDMX↔USDL mapping documentation | In `docs/design/usdl-syntax-rationale.md` |
| Parser/Serializer | **Nothing implemented** |
| UI support (format dropdowns, tree strategy) | **Nothing implemented** |

---

## Architecture: Two Tracks

```
                        ┌──────────────────────────────────────────┐
                        │              UTLX Engine                 │
                        │                                          │
  ┌─────────────┐       │  ┌──────────┐         ┌──────────────┐  │
  │ OData JSON  │──────▶│  │ OData    │────────▶│              │  │
  │ Payload     │  T1   │  │ JSON     │  UDM    │  Interpreter │  │
  │ (data)      │◀──────│  │ Parser/  │◀────────│  &           │  │
  └─────────────┘       │  │ Serializer│         │  Transform   │  │
                        │  └──────────┘         │              │  │
  ┌─────────────┐       │  ┌──────────┐         │              │  │
  │ EDMX/CSDL   │──────▶│  │ EDMX     │────────▶│              │  │
  │ Metadata    │  T2   │  │ Parser/  │  UDM    │              │  │
  │ (schema)    │◀──────│  │ Serializer│◀────────│              │  │
  └─────────────┘       │  └──────────┘         └──────────────┘  │
                        │                                          │
                        │  ┌──────────┐                            │
                        │  │ USDL     │  Directives applied to    │
                        │  │ Directive │  EDMX serialization       │
                        │  │ Engine   │                            │
                        │  └──────────┘                            │
                        └──────────────────────────────────────────┘
```

---

## Track 1: OData JSON as Data Format (Tier 1)

OData JSON payloads are JSON with a thin annotation layer (`@odata.context`, `@odata.type`, `@odata.id`, etc.). UTLX already supports JSON. The key question is: **how to handle OData control annotations.**

### 1.1 Design Decision: OData JSON vs Plain JSON

OData JSON payloads are valid JSON. The difference is semantic:

```json
{
  "@odata.context": "$metadata#Products/$entity",
  "@odata.etag": "W/\"12345\"",
  "ID": 1,
  "Name": "Widget",
  "Price": 29.99,
  "Category@odata.navigationLink": "Products(1)/Category"
}
```

**Approach: Extend the existing JSON parser, don't create a separate format.**

OData JSON is declared via the UTLX header `input odata` (or `input json` with auto-detection). The JSON parser gains an OData-aware mode that:

1. Recognizes `@odata.*` annotations and maps them to UDM attributes (not properties)
2. Strips or preserves annotations based on a format option
3. Unwraps the `value` array wrapper from collection responses
4. Handles `@odata.nextLink` for paginated results (optional, future)

### 1.2 Backend: OData JSON Parser Extension

**File:** `formats/json/src/main/kotlin/org/apache/utlx/formats/json/ODataJSONParser.kt`

```
Extends JSONParser with:
- Auto-detect OData payload (presence of @odata.context)
- Map @odata.* to UDM.Object.attributes (not properties)
- Unwrap { "value": [...] } collection wrapper → UDM.Array
- Parse @odata.type for type-aware field handling
- Handle @odata.removed for delta payloads
- Preserve entity identity (@odata.id, @odata.etag) as attributes
```

**Parser dispatch addition in `UDMService.kt`:**
```kotlin
"odata" -> ODataJSONParser(content, options).parse()
```

### 1.3 Backend: OData JSON Serializer Extension

**File:** `formats/json/src/main/kotlin/org/apache/utlx/formats/json/ODataJSONSerializer.kt`

```
Extends JSONSerializer with:
- Add @odata.context from metadata option
- Write @odata.type when entity type differs from context
- Wrap collections in { "value": [...] }
- Optionally add @odata.id, @odata.etag from UDM attributes
- Support metadata levels: minimal (default), full, none
```

**Serializer dispatch addition in `UDMService.kt`:**
```kotlin
"odata" -> ODataJSONSerializer(options).serialize(udm)
```

### 1.4 Format Options for OData JSON

**Addition to `FormatOptions`:**
```kotlin
// OData JSON options
val odataMetadataLevel: String? = null,    // "minimal", "full", "none"
val odataContext: String? = null,          // $metadata URL for serialization
val odataStripAnnotations: Boolean? = null, // Strip @odata.* from parsed data
val odataCollectionWrap: Boolean? = null    // Wrap output in { "value": [...] }
```

### 1.5 Frontend: OData JSON Support

**Protocol type additions (`protocol.ts`):**
```typescript
export type DataFormat = '...' | 'odata';
export type Tier1Format = '...' | 'odata';
```

**Input panel (`multi-input-panel-widget.tsx`):**
- Add `'odata'` to `InputDataFormatType`
- Auto-detect: if JSON content contains `@odata.context`, suggest `odata`

**Tree strategy (`strategies/odata-tree-strategy.ts`):**
```
Extends JSONTreeStrategy with:
- Filter @odata.* from property display (show as attributes instead)
- Show entity type badge from @odata.type
- Show navigation link indicators
```

### 1.6 UTLX Header Syntax

```utlx
%utlx 1.0
input odata          # OData JSON payload (auto-strips @odata.*)
output json               # Output as plain JSON
---
{
  id: $input.ID,
  name: $input.Name,
  price: $input.Price
}
```

Or used as output:
```utlx
%utlx 1.0
input json
output odata         # Produces OData-compliant JSON
---
{
  "@odata.context": "$metadata#Products/$entity",
  ID: $input.id,
  Name: $input.name
}
```

### 1.7 OData Annotations in UTLX Expressions

OData `@odata.*` annotations use **dotted names** (e.g., `@odata.context`, `@odata.type`). In UTLX, the `@` prefix is the general mechanism for setting **UDM attributes** — the same mechanism used for XML attributes in XML output. The key question is: how do dotted annotation names work?

**The answer: quoted string keys with the `@` prefix.**

UTLX's parser (`parser_impl.kt:1118-1134`) already supports quoted property names. When a quoted key starts with `@`, it is treated as an attribute — exactly like the unquoted `@attr` syntax used for simple XML attributes. This means:

```utlx
// XML output — unquoted, simple attribute names:
{ @currency: "USD" }              // → UDM attribute "currency" = "USD"

// OData output — quoted, dotted annotation names:
{ "@odata.type": "#Products.Product" }  // → UDM attribute "odata.type" = "#Products.Product"
```

Both forms use the same underlying UDM attribute mechanism. The quoted form is necessary because dotted names like `odata.context` cannot be expressed as bare identifiers.

**Three methods for OData annotations reaching output:**

1. **Header options** — The `odata` format serializer can auto-inject annotations based on format options:
   ```utlx
   %utlx 1.0
   output odata
   %option odata.context "$metadata#Products/$entity"
   ```

2. **Attribute passthrough** — When both input and output are `odata`, annotations parsed as UDM attributes pass through automatically.

3. **Manual expression** — Explicitly set annotations using the quoted `@` syntax:
   ```utlx
   {
     "@odata.context": "$metadata#Products/$entity",
     "@odata.type": "#Products.Product",
     "@odata.id": "Products(" + $input.ID + ")",
     ID: $input.ID,
     Name: $input.Name
   }
   ```

---

## Track 2: OData Schema Format — `osch` (Tier 2)

OData metadata is described using two related but distinct standards:

- **CSDL** (Common Schema Definition Language) — the actual schema language that defines entity types, properties, relationships, etc. This is the *content*.
- **EDMX** (Entity Data Model XML) — the XML envelope/wrapper that contains CSDL plus additional elements like `<edmx:Reference>` for external schema imports. This is the *container*.

In OData v4.0, CSDL is only available inside an EDMX XML wrapper. In v4.01, CSDL also has a standalone JSON representation (no EDMX envelope needed). In practice, people use "EDMX" and "CSDL" interchangeably to mean "the OData metadata file."

**UTLX uses a single format name `osch` (OData SCHema) for both.** Whether the underlying file is XML EDMX or JSON CSDL, the UTLX header uses `input osch` / `output osch`. This is analogous to XSD for XML or JSON Schema for JSON — it describes the structure, types, keys, and relationships of entities.

### 2.1 Backend: EDMX Parser

**New module:** `formats/odata/`

**File:** `formats/odata/src/main/kotlin/org/apache/utlx/formats/odata/EDMXParser.kt`

```
Parses EDMX/CSDL XML (v4.0) to UDM:

Input:   EDMX XML document ($metadata)
Output:  UDM with USDL directives applied

Parsing flow:
1. Parse XML (reuse XML parser infrastructure)
2. Extract <edmx:Edmx> → version detection (4.0, 4.01)
3. Extract <edmx:Reference> → external schema references
4. Extract <Schema> → namespace, alias
5. For each <EntityType>:
   - Create UDM type with %entityType: true
   - Parse <Key>/<PropertyRef> → %key: true on field
   - Parse <Property> → fields with Edm.* type mapping
   - Parse <NavigationProperty> → %navigation entries
   - Parse <ReferentialConstraint> → %referentialConstraint
6. For each <ComplexType>:
   - Create UDM type with %entityType: false
   - Parse <Property> → fields
7. For each <EnumType>:
   - Create UDM type with %kind: "enum"
   - Parse <Member> → enum values
8. For each <Action>/<Function>:
   - Store as metadata (operations)
9. Parse <EntityContainer>:
   - Extract EntitySet, Singleton, navigation bindings
10. Parse <Annotations>:
    - Map to USDL annotations where possible
```

**Edm → UTLX Type Mapping:**

| Edm Type | UTLX Type | Notes |
|----------|-----------|-------|
| `Edm.String` | `string` | |
| `Edm.Guid` | `string` | schemaType preserved |
| `Edm.Boolean` | `boolean` | |
| `Edm.Byte`, `Edm.SByte`, `Edm.Int16`, `Edm.Int32` | `integer` | |
| `Edm.Int64` | `integer` | precision note in schemaType |
| `Edm.Single`, `Edm.Double` | `number` | |
| `Edm.Decimal` | `number` | Precision/Scale preserved |
| `Edm.Date` | `date` | |
| `Edm.TimeOfDay` | `time` | |
| `Edm.DateTimeOffset` | `datetime` | |
| `Edm.Duration` | `string` | schemaType: "Edm.Duration" |
| `Edm.Binary`, `Edm.Stream` | `binary` | |
| `Edm.Geography*`, `Edm.Geometry*` | `any` | Complex spatial |
| `Collection(T)` | array of T | |

**Parser dispatch in `UDMService.kt`:**
```kotlin
"osch" -> EDMXParser(content).parse()
```

### 2.2 Backend: EDMX Serializer

**File:** `formats/odata/src/main/kotlin/org/apache/utlx/formats/odata/EDMXSerializer.kt`

```
Serializes UDM (with USDL directives) to EDMX/CSDL XML (v4.0):

Input:   UDM with USDL directives (%entityType, %navigation, etc.)
Output:  Valid EDMX XML document

Two modes (following XSD serializer pattern):
1. USDL mode: UDM has %types → transform directives to EDMX
2. Low-level mode: UDM already has EDMX XML structure → pass through

USDL mode transformation:
1. Read %namespace → <Schema Namespace="...">
2. Read %version → EDMX version
3. For each type in %types:
   a. If %entityType: true → <EntityType>
      - Fields with %key: true → <Key><PropertyRef>
      - Map UTLX types → Edm.* types
      - Apply Precision, Scale, MaxLength from %constraints
   b. If %entityType: false → <ComplexType>
      - Map fields to <Property> elements
   c. If %kind: "enum" → <EnumType>
      - Map values to <Member>
4. For %navigation entries:
   - Generate <NavigationProperty>
   - Map %target → Type attribute
   - Map %cardinality → Collection() wrapper or single reference
   - Map %referentialConstraint → <ReferentialConstraint>
5. Generate <EntityContainer>:
   - One EntitySet per entity type
   - NavigationPropertyBinding from relationships
6. Wrap in <edmx:Edmx><edmx:DataServices>
```

**Serializer dispatch in `UDMService.kt`:**
```kotlin
"osch" -> EDMXSerializer(options).serialize(udm)
```

### 2.3 Backend: OData Model Types

**File:** `formats/odata/src/main/kotlin/org/apache/utlx/formats/odata/ODataModel.kt`

Intermediate model for clean EDMX↔UDM conversion:

```kotlin
data class ODataSchema(
    val version: String,          // "4.0", "4.01"
    val namespace: String,
    val alias: String?,
    val references: List<ODataReference>,
    val entityTypes: List<ODataEntityType>,
    val complexTypes: List<ODataComplexType>,
    val enumTypes: List<ODataEnumType>,
    val actions: List<ODataAction>,
    val functions: List<ODataFunction>,
    val container: ODataEntityContainer?
)

data class ODataEntityType(
    val name: String,
    val key: List<String>,         // Key property names
    val baseType: String?,         // Inheritance
    val abstract: Boolean,
    val properties: List<ODataProperty>,
    val navigationProperties: List<ODataNavigationProperty>,
    val annotations: List<ODataAnnotation>
)

data class ODataProperty(
    val name: String,
    val type: String,              // Edm.String, etc.
    val nullable: Boolean,
    val maxLength: Int?,
    val precision: Int?,
    val scale: Int?,
    val defaultValue: String?,
    val annotations: List<ODataAnnotation>
)

data class ODataNavigationProperty(
    val name: String,
    val type: String,              // Namespace.EntityType or Collection(...)
    val nullable: Boolean?,
    val partner: String?,
    val containsTarget: Boolean,
    val referentialConstraints: List<ODataReferentialConstraint>,
    val annotations: List<ODataAnnotation>
)

data class ODataReferentialConstraint(
    val property: String,          // Local property
    val referencedProperty: String // Target property
)

data class ODataAnnotation(
    val term: String,              // e.g., "Core.Description"
    val value: Any?                // String, Boolean, Record, etc.
)
```

### 2.4 Backend: JSON CSDL Parser (v4.01)

**File:** `formats/odata/src/main/kotlin/org/apache/utlx/formats/odata/JSONCSDLParser.kt`

OData v4.01 introduced JSON-based CSDL as an alternative to XML EDMX. Since it's JSON, parsing is simpler:

```
Input:   JSON CSDL document
Output:  Same ODataSchema intermediate model → UDM

Detection:  JSON object with "$Version" property
Parse flow:
1. Read $Version, $Reference, $EntityContainer
2. Each top-level key is a namespace
3. Objects with $Kind: "EntityType" → parse entity
4. Objects with $Kind: "ComplexType" → parse complex type
5. Objects with $Kind: "EnumType" → parse enum
6. Convert to same ODataSchema model as EDMX parser
```

### 2.5 Frontend: OData Schema (`osch`) Support

**Protocol type additions (`protocol.ts`):**
```typescript
export type SchemaFormat = '...' | 'osch';
export type Tier2Format = '...' | 'osch';
```

**Input panel (`multi-input-panel-widget.tsx`):**
- Add `'osch'` to `InputSchemaFormatType`
- Schema tab: OData schema as loadable schema format
- Auto-detect: XML content with `<edmx:Edmx` or JSON with `"$Version"` → suggest `osch`

**Output panel (`output-panel-widget.tsx`):**
- Add `osch` as output schema format option
- Scaffold generation: EDMX/CSDL → SchemaFieldInfo[] for scaffold

**Schema field tree parser (`schema-field-tree-parser.ts`):**

**New function: `parseOSchToFieldTree(content: string): SchemaFieldInfo[]`**

```
Follows same pattern as parseXsdToFieldTree():
1. Parse XML with DOMParser (or JSON for v4.01 CSDL)
2. Find EntityType elements
3. For each EntityType:
   - Create SchemaFieldInfo with name, type mapping
   - Mark key properties as required
   - Parse NavigationProperty → nested object/array fields
4. Find ComplexType elements
5. Return SchemaFieldInfo[] for Function Builder display
```

**Tree strategy (`strategies/osch-tree-strategy.ts`):**
```
New FormatTreeStrategy implementation:
- Entity types shown with key icon
- Navigation properties shown with link icon
- Complex types shown as nested objects
- Enum types shown with enum values
- Type badge shows "Entity" or "Complex"
```

**Strategy factory update (`strategies/index.ts`):**
```typescript
case 'osch':
    return new OSchTreeStrategy();
```

### 2.6 UTLX Header Syntax

```utlx
%utlx 1.0
input osch                        # OData schema as input (rare, for schema transforms)
output json
---
// Transform OData metadata to JSON documentation
```

More commonly, OData schema is used in Design-Time mode:
```
Design-Time mode:
1. User loads EDMX/CSDL file as osch in input schema tab
2. UTLX parses EDMX/CSDL → SchemaFieldInfo[]
3. Function Builder shows entity types with typed fields
4. User writes transformation with full type awareness
```

---

## Track 3: USDL Directive Enhancement

The 5 existing OData directives cover basic entity modeling. For full v4 support, additional directives are needed.

### 3.1 New Directives Needed

| Directive | Scope | Type | Purpose | Priority |
|-----------|-------|------|---------|----------|
| `%abstract` | TYPE_DEFINITION | Boolean | Abstract entity/complex type | High |
| `%baseType` | TYPE_DEFINITION | String | Inheritance (derived types) | High |
| `%containsTarget` | FIELD_DEFINITION (nav) | Boolean | Containment navigation | Medium |
| `%partner` | FIELD_DEFINITION (nav) | String | Bidirectional nav partner | Medium |
| `%enumValues` | TYPE_DEFINITION | Array | Enum member values | Medium |
| `%isFlags` | TYPE_DEFINITION | Boolean | Flags enum | Low |
| `%action` | TYPE_DEFINITION | Object | Bound action definition | Low |
| `%function` | TYPE_DEFINITION | Object | Bound function definition | Low |
| `%singleton` | TOP_LEVEL | String | Singleton entity | Low |

### 3.2 Directive Registration

**File:** `schema/src/main/kotlin/org/apache/utlx/schema/usdl/USDL10.kt`

Add new directives to the OData section of the registry, following existing patterns. Mark as `tier: "format_specific"`, `supportedFormats: ["odata"]`.

---

## Implementation Phases

### Phase 1: OData Schema Parser (Tier 2 — Schema, Read Direction)
**Effort: 3-4 days**

This is the highest value deliverable — it enables Design-Time mode with OData services.

| Step | File | Description |
|------|------|-------------|
| 1.1 | `formats/odata/build.gradle.kts` | Create module with deps on :modules:core, :formats:json, :schema |
| 1.2 | `formats/odata/.../ODataModel.kt` | Intermediate model types |
| 1.3 | `formats/odata/.../EDMXParser.kt` | XML CSDL → ODataSchema → UDM |
| 1.4 | `formats/odata/.../JSONCSDLParser.kt` | JSON CSDL → ODataSchema → UDM |
| 1.5 | `modules/cli/.../UDMService.kt` | Register `"osch"` in parseInputToUDM() |
| 1.6 | `modules/daemon/.../RestApiServer.kt` | Verify format dispatch works |
| 1.7 | `formats/odata/src/test/...` | Unit tests with real EDMX samples |

**Deliverable:** `POST /api/udm/export` with `format: "osch"` returns UDM with USDL directives.

### Phase 2: OData Schema Frontend Integration (Tier 2 — Schema, UI)
**Effort: 2-3 days**

Wire the OData schema parser into the Theia IDE.

| Step | File | Description |
|------|------|-------------|
| 2.1 | `protocol.ts` | Add `'osch'` to SchemaFormat, DataFormat |
| 2.2 | `multi-input-panel-widget.tsx` | Add `'osch'` to schema format dropdown |
| 2.3 | `schema-field-tree-parser.ts` | Add `parseOSchToFieldTree()` |
| 2.4 | `strategies/osch-tree-strategy.ts` | New FormatTreeStrategy for OData schema |
| 2.5 | `strategies/index.ts` | Register strategy |
| 2.6 | `output-panel-widget.tsx` | Add `osch` as schema format option |

**Deliverable:** Load `.edmx` file in Design-Time schema tab → entities appear in Function Builder with types.

### Phase 3: OData JSON Parser/Serializer (Tier 1 — Data)
**Effort: 2-3 days**

Handle OData JSON payloads as a first-class data format.

| Step | File | Description |
|------|------|-------------|
| 3.1 | `formats/json/.../ODataJSONParser.kt` | OData JSON → UDM (annotation-aware) |
| 3.2 | `formats/json/.../ODataJSONSerializer.kt` | UDM → OData JSON (with control info) |
| 3.3 | `modules/cli/.../UDMService.kt` | Register `"odata"` in dispatch |
| 3.4 | `formats/json/src/test/...` | Tests with OData JSON samples |

**Deliverable:** `input odata` in UTLX header works; `@odata.*` annotations handled correctly.

### Phase 4: OData JSON Frontend Integration (Tier 1 — Data, UI)
**Effort: 1-2 days**

| Step | File | Description |
|------|------|-------------|
| 4.1 | `protocol.ts` | Add `'odata'` to Tier1Format |
| 4.2 | `multi-input-panel-widget.tsx` | Add `'odata'` to data format dropdown |
| 4.3 | `strategies/odata-tree-strategy.ts` | Tree strategy filtering `@odata.*` |
| 4.4 | `strategies/index.ts` | Register strategy |
| 4.5 | Auto-detection logic | Detect `@odata.context` in JSON → suggest odata |

**Deliverable:** Full round-trip: load OData JSON → transform → output.

### Phase 5: OData Schema Serializer (Tier 2 — Schema, Write Direction)
**Effort: 2-3 days**

Generate EDMX/CSDL from USDL-annotated UDM.

| Step | File | Description |
|------|------|-------------|
| 5.1 | `formats/odata/.../EDMXSerializer.kt` | UDM (with USDL directives) → EDMX XML |
| 5.2 | `formats/odata/.../JSONCSDLSerializer.kt` | UDM → JSON CSDL (v4.01) |
| 5.3 | `modules/cli/.../UDMService.kt` | Register `"osch"` in serializeUDMToFormat() |
| 5.4 | Two-mode detection | USDL mode vs low-level mode (following XSD pattern) |
| 5.5 | Tests | Round-trip: EDMX → UDM → EDMX |

**Deliverable:** Output schema inference produces valid EDMX. USDL → EDMX generation works.

### Phase 6: USDL Directive Enhancement
**Effort: 1-2 days**

| Step | File | Description |
|------|------|-------------|
| 6.1 | `schema/.../USDL10.kt` | Add new OData directives |
| 6.2 | `schema/.../DirectiveRegistry.kt` | Register in format compatibility matrix |
| 6.3 | `docs/usdl/USDL-DIRECTIVES-REFERENCE.md` | Document new directives |
| 6.4 | `examples/usdl/odata/` | Add examples for new directives |

**Deliverable:** Full directive coverage for OData v4 entity modeling.

### Phase 7: Testing & Documentation
**Effort: 2-3 days**

| Step | Description |
|------|-------------|
| 7.1 | Conformance tests with real-world EDMX files (SAP, Microsoft Graph, Northwind) |
| 7.2 | Round-trip tests: EDMX → UDM → EDMX structural equivalence |
| 7.3 | Integration tests: OData JSON → transform → JSON/XML output |
| 7.4 | Design-Time tests: load EDMX → Function Builder shows correct types |
| 7.5 | Update `docs/language-guide/universal-schema-dsl.md` with OData examples |
| 7.6 | Update `docs/formats/` with OData format reference |

---

## Total Effort Estimate

| Phase | Track | Effort | Dependency |
|-------|-------|--------|------------|
| Phase 1: OData Schema Parser | Tier 2 backend | 3-4 days | None |
| Phase 2: OData Schema Frontend | Tier 2 frontend | 2-3 days | Phase 1 |
| Phase 3: OData JSON | Tier 1 backend | 2-3 days | None (parallel with Phase 2) |
| Phase 4: OData JSON UI | Tier 1 frontend | 1-2 days | Phase 3 |
| Phase 5: OData Schema Serializer | Tier 2 backend | 2-3 days | Phase 1 |
| Phase 6: Directives | Cross-cutting | 1-2 days | Phase 1 |
| Phase 7: Testing & Docs | Cross-cutting | 2-3 days | All phases |
| **Total** | | **~13-20 days** | |

**Recommended parallelization:**
- Phases 1+3 can run in parallel (different developers or sequential by one)
- Phases 2+4 can run in parallel after their backend phases
- Phase 5 starts after Phase 1
- Phase 6 can start anytime
- Phase 7 runs last

**Critical path:** Phase 1 → Phase 2 → Phase 7 (OData schema Design-Time support = highest user value)

---

## Test Data Sources

| Source | URL/Location | Use |
|--------|-------------|-----|
| OData Northwind | `https://services.odata.org/V4/Northwind/Northwind.svc/$metadata` | Classic reference |
| Microsoft Graph | `https://graph.microsoft.com/v1.0/$metadata` | Large real-world EDMX |
| SAP Northwind Gateway | SAP demo systems | SAP-specific annotations |
| TripPin (OData reference) | `https://services.odata.org/V4/TripPinServiceRW/$metadata` | Navigation properties |
| Local test files | `examples/odata/*.edmx` | Unit test fixtures |

---

## File Summary

### New Files (Backend — Kotlin)

```
formats/odata/
├── build.gradle.kts
└── src/
    ├── main/kotlin/org/apache/utlx/formats/odata/
    │   ├── ODataModel.kt              # Intermediate model types
    │   ├── EDMXParser.kt              # XML CSDL → ODataSchema → UDM
    │   ├── EDMXSerializer.kt          # UDM → EDMX XML
    │   ├── JSONCSDLParser.kt          # JSON CSDL (v4.01) → ODataSchema → UDM
    │   ├── JSONCSDLSerializer.kt      # UDM → JSON CSDL (v4.01)
    │   └── EdmTypeMapper.kt           # Edm.* ↔ UTLX type mapping
    └── test/kotlin/org/apache/utlx/formats/odata/
        ├── EDMXParserTest.kt
        ├── EDMXSerializerTest.kt
        ├── ODataJSONTest.kt
        └── testdata/                  # EDMX/CSDL test fixtures

formats/json/src/main/kotlin/org/apache/utlx/formats/json/
├── ODataJSONParser.kt                 # OData JSON → UDM (extends JSONParser)
└── ODataJSONSerializer.kt             # UDM → OData JSON (extends JSONSerializer)
```

### New Files (Frontend — TypeScript)

```
theia-extension/utlx-theia-extension/src/browser/
├── function-builder/strategies/
│   ├── osch-tree-strategy.ts           # FormatTreeStrategy for OData schema
│   └── odata-tree-strategy.ts    # FormatTreeStrategy for OData JSON
```

### Modified Files

```
Backend (Kotlin):
  modules/cli/.../UDMService.kt            # Add odata, osch to dispatch
  modules/daemon/.../RestApiServer.kt      # Verify format support
  schema/.../USDL10.kt                     # New OData directives
  schema/.../DirectiveRegistry.kt          # Update compatibility matrix
  settings.gradle.kts                      # Include :formats:odata module

Frontend (TypeScript):
  src/common/protocol.ts                   # Add types
  src/browser/input-panel/multi-input-panel-widget.tsx   # Format dropdowns
  src/browser/output-panel/output-panel-widget.tsx       # Schema format
  src/browser/utils/schema-field-tree-parser.ts          # parseOSchToFieldTree()
  src/browser/function-builder/strategies/index.ts       # Register strategies
```

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| EDMX complexity (large Graph API metadata = 3MB+) | Parser performance | Lazy parsing, only parse referenced types |
| OData v2 EDMX differences | Compatibility | Start with v4 only, add v2 compat layer later |
| SAP-specific annotations | Missing functionality | Parse as generic annotations, add SAP vocabulary support incrementally |
| Navigation property cycles | Infinite recursion | Depth limit in field tree parser (already exists for XSD) |
| JSON CSDL (v4.01) adoption | Low priority | Implement after XML CSDL, lower priority |

---

## Success Criteria

1. Load a real-world EDMX file (Northwind, TripPin) → entities appear correctly in Function Builder
2. Transform an OData JSON payload → correct UDM with entity data accessible via `$input.Property`
3. `@odata.*` annotations don't pollute the data model — handled as metadata, not fields
4. Round-trip EDMX → UDM → EDMX produces structurally equivalent output
5. Design-Time mode: EDMX schema → typed field suggestions in autocomplete
6. USDL with `%entityType`, `%navigation` → valid EDMX generation
