# OData — Comprehensive Reference

OData (Open Data Protocol) is both a **metadata description format** (CSDL/EDMX) and a **data exchange protocol** with a rich query language. This document covers all aspects relevant to UTLX integration.

---

## 1. Protocol Versions

### History

| Version | Year | Standardization | Status |
|---------|------|-----------------|--------|
| v1.0/v2.0 | 2007-2010 | Microsoft Open Specification Promise | Legacy, still used in SAP/SharePoint |
| v3.0 | 2012 | Microsoft OSP | Transitional, limited adoption |
| v4.0 | 2014 | OASIS + ISO/IEC 20802 | Current standard |
| v4.01 | 2020 | OASIS Committee Specification | Latest, backward-compatible with v4.0 |

### Key Differences: v2 vs v4

| Feature | OData v2 | OData v4 |
|---------|----------|----------|
| Payload format | Both XML (Atom) and JSON required | JSON primary, XML optional |
| Metadata format | XML only | XML (v4.0), XML or JSON (v4.01) |
| HTTP methods | GET, POST, POST+X-HTTP-METHOD for MERGE | Standard: GET, POST, PUT, PATCH, DELETE |
| Query options | $filter, $select, $orderby at root only | Applicable at any level in hierarchy |
| $expand | Returns all properties, no nested filtering | Supports nested $select, $filter, $top, $orderby |
| $search | Not supported | Full free-text search |
| Date/Time types | Edm.DateTime (no timezone), Edm.Time | Edm.DateTimeOffset, Edm.Duration, Edm.TimeOfDay, Edm.Date |
| Operations | Function imports only | Bound/unbound actions and functions |
| Metadata control | Limited | Three levels: minimal, full, none |
| JSON verbosity | Verbose with OData wrappers (`d:`, `__metadata`) | Streamlined, resembles plain JSON |

### v4.01 Additions over v4.0

- JSON-based CSDL metadata (no XML parser required)
- JSON batch format (alternative to multipart/mime)
- Deep Update/Upsert (v4.0 only had deep insert)
- `$compute` query option (on-the-fly computed properties)
- `in` operator (multi-value equality, replaces OR chains)
- `matchesPattern` (regex matching in $filter)
- `case` operator (conditional evaluation)
- Set-based operations (`/$each`, `/$filter(...)`)
- Key-as-Segment (`/Products/1` instead of `/Products(1)`)
- `Edm.Untyped` (properties holding any valid type)
- Key-less entity types (for singletons and containment)
- Annotation inheritance through type hierarchies
- Ordered collections with zero-based indexing

---

## 2. OData as Metadata (CSDL/EDMX)

### Entity Data Model (EDM)

Every OData service exposes its model via a metadata document at `$metadata`. The model defines:

### Core Model Elements

**EntityType** — A structured record with named, typed properties and a **key** (one or more properties uniquely identifying an instance). Supports inheritance. Can be abstract.

```xml
<EntityType Name="Product">
  <Key>
    <PropertyRef Name="ID"/>
  </Key>
  <Property Name="ID" Type="Edm.Int32" Nullable="false"/>
  <Property Name="Name" Type="Edm.String" MaxLength="100"/>
  <Property Name="Price" Type="Edm.Decimal" Precision="10" Scale="2"/>
  <NavigationProperty Name="Category" Type="Namespace.Category" Nullable="false"/>
</EntityType>
```

**ComplexType** — A structured type with named, typed properties but **no key**. Can only exist as a property of a containing entity or as a transient value. Supports inheritance. In v4.01, complex types can contain navigation properties.

**EnumType** — Named type with discrete named values mapped to integer constants. Supports Flags (bitwise combination).

```xml
<EnumType Name="Color" IsFlags="true">
  <Member Name="Red" Value="1"/>
  <Member Name="Green" Value="2"/>
  <Member Name="Blue" Value="4"/>
</EnumType>
```

**TypeDefinition** (v4+) — Named type based on a primitive type with optional facet values. Assigns semantic meaning to primitives (e.g., `typedef PhoneNumber = Edm.String`).

**NavigationProperty** — Defines a relationship to another entity type. Can be single-valued (to-one) or collection-valued (to-many). Supports partner navigation (bidirectional), referential constraints, and containment.

**Property** — Structural property with a type (primitive, complex, enum, or collection thereof), nullability, default value, and type facets.

### Service Elements

**EntityContainer** — Top-level container defining exposed resources. Contains:

| Element | Description |
|---------|-------------|
| **EntitySet** | Named collection of entities of a given type. Supports navigation property bindings. |
| **Singleton** | Single entity instance at a fixed URL (v4+). |
| **ActionImport** | Exposes an unbound action at service level. |
| **FunctionImport** | Exposes an unbound function at service level. |

### Operations

**Action** — Server-side operation that may have side effects. Can be bound or unbound. Parameters passed in request body (POST).

**Function** — Server-side operation that MUST NOT have side effects. Can be bound or unbound. Parameters passed in URL. Must have a return type. Can be composable (result can be further queried).

---

## 3. OData as Exchange Format

### JSON Format (v4, primary)

```
Content-Type: application/json;odata.metadata=minimal;odata.streaming=true;IEEE754Compatible=false
```

Typical entity response:
```json
{
  "@odata.context": "$metadata#Products/$entity",
  "@odata.etag": "W/\"12345\"",
  "ID": 1,
  "Name": "Widget",
  "Price": 29.99,
  "Category": { "ID": 5, "Name": "Gadgets" }
}
```

**Control information annotations:**

| Annotation | Purpose |
|------------|---------|
| `@odata.context` | Metadata context for the payload |
| `@odata.count` | Total count (when `$count=true`) |
| `@odata.nextLink` | URL for next page of results |
| `@odata.deltaLink` | URL to track changes since this response |
| `@odata.etag` | Entity version for concurrency control |
| `@odata.id` | Canonical entity identifier URL |
| `@odata.editLink` / `@odata.readLink` | URLs for modifying/reading |
| `@odata.type` | Type annotation when not inferable from context |
| `@odata.mediaReadLink` / `@odata.mediaEditLink` | For media entities |

In v4.01, the `odata.` prefix is optional (e.g., `@context`, `@count`).

### Atom/XML Format (v2/v3, legacy)

- `application/atom+xml` for feeds/entries
- `application/xml` for individual properties and links
- Entity properties wrapped in `<atom:entry>` → `<atom:content>` → `<m:properties>`
- Navigation properties use `<atom:link>` with `rel` attributes

### Batch Requests

**Multipart/MIME (v2/v3/v4.0):** Individual operations separated by boundary strings. Change sets (atomic groups) use nested multipart boundaries.

**JSON batch (v4.01):**
```json
{
  "requests": [
    { "id": "1", "method": "GET", "url": "Products(1)" },
    { "id": "2", "method": "PATCH", "url": "Products(2)",
      "headers": { "Content-Type": "application/json" },
      "body": { "Price": 19.99 },
      "atomicityGroup": "g1" }
  ]
}
```

### Delta Payloads

Track changes since a previous request (via `$deltatoken`):
- Added/changed entities (full or partial representation)
- Deleted entities (`@removed` in v4.01, `@odata.removed` in v4.0)
- Added/deleted links between entities
- v4.01 supports nested delta for expanded navigation properties

### Streaming

- `odata.streaming=true` parameter enables streaming JSON parsers
- `Edm.Stream` typed properties reference binary streams via media links
- Server-driven paging (`@odata.nextLink`) provides result streaming

---

## 4. OData Query Language

### System Query Options

**$filter** — Restricts entities matching a boolean expression:
```
GET /Products?$filter=Price gt 20.00 and Category/Name eq 'Electronics'
```

| Category | Operators/Functions |
|----------|-------------------|
| Comparison | `eq`, `ne`, `gt`, `ge`, `lt`, `le` |
| Logical | `and`, `or`, `not` |
| Arithmetic | `add`, `sub`, `mul`, `div`, `mod`, `divby` (v4.01) |
| String | `contains()`, `startswith()`, `endswith()`, `length()`, `indexof()`, `substring()`, `tolower()`, `toupper()`, `trim()`, `concat()` |
| Date | `year()`, `month()`, `day()`, `hour()`, `minute()`, `second()`, `date()`, `time()`, `now()`, `mindatetime()`, `maxdatetime()` |
| Math | `round()`, `floor()`, `ceiling()` |
| Type | `cast()`, `isof()` |
| Geo | `geo.distance()`, `geo.intersects()`, `geo.length()` |
| v4.01 | `in`, `matchesPattern()`, `case`, `hassubset()`, `hassubsequence()` |

**$select** — Choose properties: `?$select=Name,Price`

**$expand** — Include related entities (v4 supports nested options):
```
GET /Products?$expand=Category($select=Name),Reviews($filter=Rating gt 3;$top=5;$orderby=Date desc)
```

**$orderby** — Sort: `?$orderby=Price desc,Name asc`

**$top / $skip** — Paging: `?$top=10&$skip=20`

**$count** — Total count: `?$count=true` or `/Products/$count`

**$search** — Free-text: `?$search="wireless headphones"`

**$compute** (v4.01) — Computed properties: `?$compute=Price mul Quantity as Total&$select=Name,Total`

### Lambda Operators

```
GET /Products?$filter=Tags/any(t: t eq 'Featured')
GET /Products?$filter=Reviews/all(r: r/Rating ge 3)
```

### Aggregation ($apply)

Pipeline of transformations:
```
GET /Sales?$apply=filter(Date ge 2024-01-01)/groupby((Region),aggregate(Amount with sum as Total))
```

| Transform | Purpose |
|-----------|---------|
| `aggregate` | Compute: `$count`, `sum`, `average`, `min`, `max`, `countdistinct` |
| `groupby` | Group by properties |
| `filter` | Pre-aggregation filtering |
| `compute` | Define computed values |
| `concat` | Combine multiple transformation results |
| `topcount` / `bottomcount` | Top/bottom N by measure |
| `toppercent` / `bottompercent` | Top/bottom by cumulative percentage |

---

## 5. OData Type System

### Primitive Types (v4)

| Type | Description | JSON Representation | Facets |
|------|-------------|---------------------|--------|
| `Edm.Binary` | Binary data | Base64url string | MaxLength |
| `Edm.Boolean` | True/false | `true`/`false` | — |
| `Edm.Byte` | Unsigned 8-bit (0–255) | Number | — |
| `Edm.SByte` | Signed 8-bit (-128–127) | Number | — |
| `Edm.Int16` | Signed 16-bit | Number | — |
| `Edm.Int32` | Signed 32-bit | Number | — |
| `Edm.Int64` | Signed 64-bit | String (IEEE754) or Number | — |
| `Edm.Single` | IEEE 754 float32 | Number or string | — |
| `Edm.Double` | IEEE 754 float64 | Number or string | — |
| `Edm.Decimal` | Arbitrary precision | String (IEEE754) or Number | Precision, Scale |
| `Edm.String` | UTF-8 characters | String | MaxLength, Unicode |
| `Edm.Guid` | 128-bit UUID | String | — |
| `Edm.Date` | Date only | `YYYY-MM-DD` | — |
| `Edm.DateTimeOffset` | Date+time+timezone | ISO 8601 | Precision |
| `Edm.TimeOfDay` | Time only | `hh:mm:ss.fff` | Precision |
| `Edm.Duration` | Signed duration | ISO 8601 `PnDTnHnMnS` | Precision |
| `Edm.Stream` | Binary stream | Not inline; via URL | — |

### Geospatial Types

**Geography** (round-earth, default SRID 4326/WGS84):
`GeographyPoint`, `GeographyLineString`, `GeographyPolygon`, `GeographyMultiPoint`, `GeographyMultiLineString`, `GeographyMultiPolygon`, `GeographyCollection`

**Geometry** (flat-earth, default SRID 0):
`GeometryPoint`, `GeometryLineString`, `GeometryPolygon`, `GeometryMultiPoint`, `GeometryMultiLineString`, `GeometryMultiPolygon`, `GeometryCollection`

GeoJSON representation in JSON format.

### Abstract Types

- `Edm.PrimitiveType` — base for all primitives
- `Edm.ComplexType` — base for all complex types
- `Edm.EntityType` — base for all entity types
- `Edm.Untyped` (v4.01) — can hold any valid JSON value

### Collection Types

Any type can be wrapped: `Collection(Edm.String)`, `Collection(Namespace.ComplexType)`

v4.01 introduces ordered collections with indexing.

### Type Facets

| Facet | Applies To | Description |
|-------|-----------|-------------|
| `Precision` | Decimal, DateTimeOffset, Duration, TimeOfDay | Max significant digits / fractional seconds (0–12) |
| `Scale` | Decimal | Digits right of decimal; `variable` for floating (v4.01) |
| `MaxLength` | Binary, String, Stream | Max length; `max` for server-determined |
| `Unicode` | String | `true` (default) = full Unicode; `false` = ASCII only |
| `SRID` | Geography, Geometry | Coordinate reference system ID |

### Type Migration v2→v4

| v2/v3 Type | v4 Replacement |
|-----------|----------------|
| `Edm.DateTime` | `Edm.DateTimeOffset` |
| `Edm.Time` | `Edm.Duration` + `Edm.TimeOfDay` |
| — | `Edm.Date` (new) |
| — | `Edm.Stream` (new) |
| — | `Edm.Untyped` (v4.01, new) |

---

## 6. CSDL Schema Structure

### XML-based CSDL (v4.0 and v4.01)

Two namespaces:
- **EDMX**: `http://docs.oasis-open.org/odata/ns/edmx`
- **EDM**: `http://docs.oasis-open.org/odata/ns/edm`

```xml
<?xml version="1.0" encoding="utf-8"?>
<edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">

  <!-- External references -->
  <edmx:Reference Uri="https://oasis-tcs.github.io/odata-vocabularies/vocabularies/Org.OData.Core.V1.xml">
    <edmx:Include Namespace="Org.OData.Core.V1" Alias="Core"/>
  </edmx:Reference>

  <edmx:DataServices>
    <Schema Namespace="com.example.model" Alias="Model"
            xmlns="http://docs.oasis-open.org/odata/ns/edm">

      <!-- Types -->
      <EnumType Name="Status">...</EnumType>
      <ComplexType Name="Address">...</ComplexType>
      <EntityType Name="Customer">...</EntityType>

      <!-- Operations -->
      <Action Name="Approve" IsBound="true">
        <Parameter Name="bindingParameter" Type="Model.Order"/>
      </Action>
      <Function Name="TopProducts" IsBound="false">
        <Parameter Name="count" Type="Edm.Int32"/>
        <ReturnType Type="Collection(Model.Product)"/>
      </Function>

      <!-- Entity container -->
      <EntityContainer Name="Container">
        <EntitySet Name="Customers" EntityType="Model.Customer">
          <NavigationPropertyBinding Path="Orders" Target="Orders"/>
        </EntitySet>
        <EntitySet Name="Orders" EntityType="Model.Order"/>
        <Singleton Name="CurrentUser" Type="Model.User"/>
        <ActionImport Name="ResetDatabase" Action="Model.ResetDatabase"/>
        <FunctionImport Name="TopProducts" Function="Model.TopProducts"
                        EntitySet="Products" IncludeInServiceDocument="true"/>
      </EntityContainer>

      <!-- External annotations -->
      <Annotations Target="Model.Customer/Name">
        <Annotation Term="Core.Description" String="Customer full name"/>
      </Annotations>

    </Schema>
  </edmx:DataServices>
</edmx:Edmx>
```

### Namespace Handling

- **Namespace** (required): Unique identifier (e.g., `com.example.model`)
- **Alias** (optional): Short form (e.g., `Model`) for convenience within the document
- **edmx:Reference/edmx:Include**: Imports external schemas with optional aliasing
- Type references use qualified names: `Namespace.TypeName` or `Alias.TypeName`

### JSON-based CSDL (v4.01)

```json
{
  "$Version": "4.01",
  "$Reference": {
    "https://oasis-tcs.github.io/.../Org.OData.Core.V1.json": {
      "$Include": [{ "$Namespace": "Org.OData.Core.V1", "$Alias": "Core" }]
    }
  },
  "$EntityContainer": "com.example.model.Container",
  "com.example.model": {
    "$Alias": "Model",
    "Customer": {
      "$Kind": "EntityType",
      "$Key": ["ID"],
      "ID": { "$Type": "Edm.Int32" },
      "Name": { "$Type": "Edm.String", "$MaxLength": 100 }
    },
    "Container": {
      "$Kind": "EntityContainer",
      "Customers": {
        "$Collection": true,
        "$Type": "Model.Customer"
      }
    }
  }
}
```

---

## 7. OData Annotations

### Mechanism

Annotations attach metadata to model elements using **vocabulary terms**. Can be:
- **Inline**: Within the annotated element
- **External**: Via `<Annotations Target="...">` elements
- **Instance**: Returned in response payloads (e.g., `@Core.Description`)

### OASIS Standard Vocabularies

**Org.OData.Core.V1** — Fundamental:
`Description`, `LongDescription`, `Computed`, `Immutable`, `IsURL`, `MediaType`, `Permissions`, `OptionalParameter`, `AlternateKeys`, `SchemaVersion`, `Ordered`

**Org.OData.Capabilities.V1** — Service capabilities:
`ConformanceLevel`, `SupportedFormats`, `FilterRestrictions`, `SortRestrictions`, `ExpandRestrictions`, `SearchRestrictions`, `InsertRestrictions`, `UpdateRestrictions`, `DeleteRestrictions`, `BatchSupport`, `NavigationRestrictions`, `CountRestrictions`, `SelectSupport`, `TopSupported`, `SkipSupported`

**Org.OData.Validation.V1** — Data validation:
`Minimum`, `Maximum`, `Pattern`, `AllowedValues`, `MaxItems`, `MinItems`, `Exclusive`

**Org.OData.Measures.V1** — Units:
`Unit`, `ISOCurrency`

**Org.OData.Aggregation.V1** — Aggregation:
`Groupable`, `Aggregatable`, `ContextDefiningProperties`

**Org.OData.Authorization.V1** — Security:
`SecuritySchemes`, `Authorizations` (OAuth2, OpenIDConnect, HTTP auth)

### SAP-Specific Vocabularies

| Vocabulary | Namespace | Purpose |
|------------|-----------|---------|
| **UI** | `com.sap.vocabularies.UI.v1` | `LineItem`, `HeaderInfo`, `FieldGroup`, `SelectionFields`, `Chart`, `DataPoint`, `Facets`, `Hidden` |
| **Common** | `com.sap.vocabularies.Common.v1` | `Label`, `Text`, `TextArrangement`, `ValueList`, `SemanticKey`, `FieldControl`, `SideEffects`, `DraftRoot`, `DraftNode` |
| **Communication** | `com.sap.vocabularies.Communication.v1` | vCard-based: `Contact`, `Address`, `PhoneNumber`, `EmailAddress` |
| **Analytics** | `com.sap.vocabularies.Analytics.v1` | `AggregatedProperty`, `Measure`, `Dimension` |
| **PersonalData** | `com.sap.vocabularies.PersonalData.v1` | GDPR: `IsPotentiallyPersonal`, `IsPotentiallySensitive` |
| **HTML5** | `com.sap.vocabularies.HTML5.v1` | UI5 rendering directives |
| **Session** | `com.sap.vocabularies.Session.v1` | Sticky session / draft handling |
| **Hierarchy** | `com.sap.vocabularies.Hierarchy.v1` | Hierarchical data |
| **CodeList** | `com.sap.vocabularies.CodeList.v1` | Value help / code lists |
| **ODM** | `com.sap.vocabularies.ODM.v1` | One Domain Model alignment |

---

## 8. OData and SAP

### SAP Gateway
Middleware exposing SAP business data as OData services. Supports v2 (primary for legacy) and v4. Sits between SAP backends (ECC, S/4HANA) and external consumers.

### CDS Views and OData Exposure
- **Pre-7.50**: Manual OData creation in SEGW transaction
- **AS ABAP 7.50+**: `@OData.publish: true` annotation directly exposes CDS views
- **ABAP Platform 7.54+**: Service Definition + Service Binding artifacts in ADT

### SAP S/4HANA
- All SAP Fiori apps consume OData (predominantly v2, migrating to v4)
- Business APIs on SAP Business Accelerator Hub published as OData
- S/4HANA Cloud Public Edition exposes APIs consumable from SAP BTP

### SAP BTP (Business Technology Platform)
- **CAP** (Cloud Application Programming Model): Framework for OData v4 services using CDS with Node.js or Java. CAP's CDS compiles to OData EDMX/CSDL.
- **SAP Integration Suite**: OData adapters for v2 and v4
- **SAP Build Work Zone / Fiori Launchpad**: Consumes OData for UI rendering

### ABAP RAP (RESTful Application Programming Model)
CDS for data modeling, behavior definitions for logic, exposed as OData v2/v4. Supports managed/unmanaged scenarios, draft handling, authorization control, side effects.

### SAP-Specific Extensions
- **Legacy `sap:` namespace** (v2): Attributes like `sap:label`, `sap:filterable`, `sap:sortable`, `sap:creatable` on properties. Being replaced by vocabulary-based annotations.
- **Modern**: Standard OData annotation syntax with SAP-specific vocabularies (see Section 7).

---

## 9. OData and Microsoft

### Microsoft Graph API
One of the largest OData v4 implementations. Exposes Microsoft 365 data (Azure AD, Outlook, Teams, OneDrive, SharePoint, Planner):
- `https://graph.microsoft.com/v1.0/` (stable)
- `https://graph.microsoft.com/beta/` (preview)

Supports $filter, $select, $expand, $orderby, $top, $skip, $count, $search, $batch.

### Dynamics 365 / Dataverse
Dataverse Web API is an OData v4 RESTful service. Full CRUD, metadata queries, change tracking, batch operations, actions/functions.

### Power Platform
- **Power Automate**: OData queries via Dataverse connectors
- **Power Apps**: Model-driven apps use OData for all data access
- **Power BI**: OData feeds as data source with query folding

### Azure Services
- **Azure DevOps Analytics**: OData v4 endpoints
- **Azure Table Storage**: OData v3-compatible REST API
- **Azure Cognitive Search**: OData $filter syntax

### Authentication
OAuth 2.0 via Microsoft Entra ID (formerly Azure AD): Authorization Code, Client Credentials, On-behalf-of, Device Code flows.

---

## 10. OData Security

### Authentication Patterns

OData itself is transport-agnostic regarding authentication. Common patterns:

| Pattern | Use Case |
|---------|----------|
| **Basic Auth over HTTPS** | Simple, only over TLS |
| **OAuth 2.0 (Bearer tokens)** | Most common modern approach |
| **SAML** | Common in SAP environments |
| **Certificate-based (mTLS)** | High-security M2M |
| **Windows Integrated (Kerberos/NTLM)** | On-premises Microsoft |

### Batch Security
- All operations in a batch execute under the same authentication context
- Change sets provide atomicity but not isolation between concurrent batches
- Individual operations can fail authorization independently
- `Isolation: snapshot` header requests snapshot isolation for GETs across batch

### Best Practices
- Always use TLS/HTTPS
- Implement query complexity limits ($expand depth, $filter complexity)
- Validate $filter expressions against injection
- Use `Capabilities` annotations to declare required authorization
- Implement rate limiting

---

## 11. Comparison with Similar Formats

### OData vs GraphQL

| Aspect | OData | GraphQL |
|--------|-------|---------|
| Protocol type | REST-based, multiple endpoints | Single endpoint, schema-driven |
| Query location | URL query parameters | Request body (POST) |
| Schema | CSDL/EDMX (XML or JSON) | SDL (Schema Definition Language) |
| Standardization | OASIS/ISO standard | Open spec (Meta) |
| Filtering | Rich URL-based ($filter, $apply) | Field arguments, nested selections |
| Aggregation | Built-in ($apply) | Not built-in (custom resolvers) |
| Batch | Native support | Query batching (informal) |
| Caching | HTTP caching (GET) | Harder (POST-based) |
| Over/under-fetching | $select controls, coarser | Precise field selection |
| Adoption | Enterprise (SAP, Microsoft) | Web/mobile (Meta, GitHub, Shopify) |
| Real-time | Delta queries, polling | Subscriptions (WebSocket) |

### OData vs gRPC/Protobuf

| Aspect | OData | gRPC |
|--------|-------|------|
| Format | JSON/XML over HTTP/1.1+ | Binary (Protobuf) over HTTP/2 |
| Performance | Moderate (text) | High (binary, streaming, multiplexing) |
| Schema | CSDL | .proto files |
| Code generation | Optional | Required (protoc) |
| Browser support | Native | Requires gRPC-Web proxy |
| Querying | Rich URL-based | RPC methods (no built-in query) |
| Streaming | Limited (delta, paging) | Bidirectional native |
| Use case | Data-centric APIs, enterprise | Microservice comms, real-time |

### OData vs OpenAPI

Complementary, not competing:
- OData is a full protocol (query + format + metadata + conventions)
- OpenAPI is an API description format
- OData services can generate OpenAPI from CSDL (odata-openapi converter)

### OData vs JSON:API

| Aspect | OData | JSON:API |
|--------|-------|----------|
| Scope | Full protocol + query language | Response format spec |
| Query capabilities | Extensive ($filter, $apply, $expand) | Limited (filter, sort, pagination) |
| Metadata | Rich, self-describing CSDL | No standardized metadata |
| Complexity | Higher | Lower |
| Adoption | SAP, Microsoft enterprise | Web APIs (simpler use cases) |

---

## 12. Common OData Challenges

### Complex Type Mapping
- No keys = cannot be addressed individually via URL
- Navigation properties on complex types require entity ID + full path in URI
- Nested complex types create deep structures hard to flatten for tabular consumers

### Navigation Property Handling
- Bindings must be explicitly declared in EntityContainer
- Containment creates implicit entity sets scoped to parent
- Bidirectional navigation requires consistent declaration on both sides
- Deep paths through multiple navigations can cause performance issues
- $expand depth must be bounded

### Batch Processing
- Multipart/MIME is complex to construct and parse
- Error handling: partial success/failure reporting
- JSON batch (v4.01) simplifies but requires v4.01 support

### Pagination
- **Server-driven**: `@odata.nextLink` (sequential, server controls page size)
- **Client-driven**: `$top` + `$skip` (expensive at high offsets)
- **No cursor-based paging** in standard (unlike GraphQL)
- `$skiptoken` provides opaque server cursors but not standardized
- Count may change between pages with concurrent modifications

### Deep Insert/Update
- **Deep insert** (v4.0): Create entity with nested new related entities (single POST)
- **Deep update** (v4.01): Update with nested changes to related entities
- **Deep upsert** (v4.01): Insert-or-update on nested entities
- Circular references not supported
- Error reporting for nested failures requires careful construction

### Other Challenges
- $filter expression complexity (no standard limit)
- Null semantics: three-valued logic (true/false/null)
- Large metadata: hundreds of entity types = expensive parsing
- Version negotiation: supporting both v2 and v4 requires separate endpoints or version-aware middleware

---

## 13. OData in Practice

### Server Frameworks

| Framework | Language | Version | Notes |
|-----------|----------|---------|-------|
| Microsoft.OData (.NET) | C# | v4, v4.01 | Official MS, ASP.NET Core |
| Apache Olingo | Java | v2, v4 | Apache Foundation, JPA + Spring |
| SAP CAP | Node.js / Java | v4 | CDS-based |
| SAP Gateway / RAP | ABAP | v2, v4 | Native S/4HANA |
| CData API Server | Multi | v2, v4 | Database → OData |
| odata-v4-server | TypeScript | v4 | Node.js |

### Client Libraries

| Library | Language | Version | Notes |
|---------|----------|---------|-------|
| Microsoft.OData.Client | C# | v4 | Code gen from CSDL |
| Simple.OData.Client | C# | v1–v4 | Fluent API |
| Apache Olingo Client | Java | v2, v4 | Full-featured |
| odata2ts | TypeScript | v4 | Code gen, type-safe queries |
| SAP Cloud SDK | Java / JS | v2, v4 | SAP BTP integration |
| pyodata | Python | v2, v4 | SAP-maintained |

### Tooling
- **SEGW**: ABAP transaction for OData v2 services
- **ADT**: Eclipse IDE for RAP/CDS
- **SAP Business Application Studio**: Cloud IDE with CAP
- **odata-openapi**: CSDL → OpenAPI 3.0 converter
- **XOData**: Web-based metadata visualizer

---

## 14. MIME Types and Content Negotiation

### MIME Types by Version

| Version | Resource | MIME Type |
|---------|----------|-----------|
| v2/v3 | Atom feed/entry | `application/atom+xml` |
| v2/v3 | Service document | `application/atomsvc+xml` |
| v2/v3 | JSON verbose | `application/json;odata=verbose` |
| v4.0 | JSON response | `application/json` |
| v4.0 | Metadata (XML) | `application/xml` |
| v4.0 | Batch | `multipart/mixed` |
| v4.01 | Metadata (JSON) | `application/json` |
| v4.01 | JSON batch | `application/json` |

### odata.metadata Parameter (v4)

**`odata.metadata=minimal`** (default):
Only `@odata.context` at root. Omits type/id/links when inferable. Smallest production payload.

**`odata.metadata=full`**:
All control information explicit: context, type, id, editLink, readLink, etag, nav links, action/function advertisements. Useful for debugging.

**`odata.metadata=none`**:
No metadata at all. Smallest payload but client needs out-of-band knowledge.

### Additional Parameters

- **`odata.streaming=true|false`**: Suitability for streaming JSON parsers
- **`IEEE754Compatible=true|false`**: When true, Int64/Decimal as JSON strings for precision

### Format Negotiation

1. Server selects format from `Accept` header (or `$format` query option)
2. If neither specified, defaults to `application/json;odata.metadata=minimal`
3. If server can't produce requested format, returns `406 Not Acceptable`
4. `Content-Type` in response indicates actual format

---

## 15. Relevance to UTLX

### Dual Nature

OData's dual nature as both metadata description and exchange format creates two integration paths for UTLX:

1. **CSDL/EDMX as schema format** (like XSD, JSON Schema) — parse OData metadata to understand entity models, types, relationships, constraints. This maps to USDL directives.

2. **OData JSON as data format** (like JSON, XML) — transform OData JSON payloads as input/output. The control annotations (`@odata.*`) need special handling.

### USDL Directive Mapping (already designed)

| USDL Directive | OData EDMX Equivalent |
|----------------|----------------------|
| `%namespace` | Schema Namespace |
| `%entityType: true` | `<EntityType>` |
| `%entityType: false` | `<ComplexType>` |
| `%key: true` | `<Key><PropertyRef>` |
| `%navigation` | `<NavigationProperty>` |
| `%target` | Navigation target entity |
| `%cardinality` | Multiplicity attribute |
| `%referentialConstraint` | Foreign key constraints |

### Type Mapping: Edm → UTLX Normalized Types

| Edm Type | UTLX Normalized Type |
|----------|---------------------|
| `Edm.String`, `Edm.Guid` | `string` |
| `Edm.Int16`, `Edm.Int32`, `Edm.Byte`, `Edm.SByte` | `integer` |
| `Edm.Int64` | `integer` (precision note) |
| `Edm.Single`, `Edm.Double`, `Edm.Decimal` | `number` |
| `Edm.Boolean` | `boolean` |
| `Edm.Date` | `date` |
| `Edm.TimeOfDay` | `time` |
| `Edm.DateTimeOffset` | `datetime` |
| `Edm.Binary`, `Edm.Stream` | `binary` |
| `Edm.Duration` | `string` (ISO 8601) |
| Geography/Geometry types | `any` (complex structure) |

### Estimated Implementation Effort

| Component | Effort | Notes |
|-----------|--------|-------|
| EDMX/CSDL Parser → USDL | ~2-3 days | XML parsing, entity/complex type mapping |
| USDL → EDMX/CSDL Serializer | ~2-3 days | Reverse direction |
| OData JSON payload handling | ~1-2 days | Strip/preserve `@odata.*` annotations |
| OData type facet mapping | ~1 day | Precision, Scale, MaxLength → USDL constraints |
| Navigation property support | ~1-2 days | Relationship modeling in transforms |
| **Total** | **~7-12 days** | Full bidirectional support |
