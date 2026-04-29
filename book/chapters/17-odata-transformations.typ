= OData Transformations

== What Is OData?
// - Open Data Protocol — REST-based data access standard
// - Created by Microsoft, now an OASIS standard
// - Used by: Dynamics 365, SharePoint, SAP, Power Platform, Azure APIs
// - OData v4: JSON-based with metadata annotations
// - The @odata.context, @odata.type, @odata.id conventions

== OData in UTL-X
// - OData JSON is JSON with metadata conventions
// - UTL-X parses it as standard JSON with @odata properties accessible
// - Accessing metadata: $input["@odata.context"], $input["@odata.type"]
// - Entity sets: $input.value (the array of entities)
// - Navigation properties: $input.Orders[0].Customer

== Reading OData
// - Entity collections: $input.value (standard OData collection response)
// - Single entity: $input directly
// - Navigation properties: nested entity access
// - Complex types: structured properties
// - Enum values: string representation
// - Metadata annotations: @odata.* properties

== Writing OData
// - Generating OData-compliant JSON
// - Adding @odata.context, @odata.type annotations
// - Entity key construction
// - Delta payloads for updates (PATCH operations)

== OData-Specific Functions
// - OData JSON serializer with metadata options
// - OData schema (EDMX/CSDL) parsing
// - Entity type introspection

== Common OData Patterns

=== Dynamics 365 Integration
// - Reading D365 API responses (sales orders, invoices, contacts)
// - Handling the @odata.nextLink pagination
// - Transforming D365 JSON to canonical format
// - Creating D365 entities from external data

=== SharePoint Integration
// - SharePoint list items → JSON/CSV
// - Document metadata extraction
// - Site/list structure transformation

=== SAP OData Integration
// - SAP Gateway OData services
// - Business Partner, Material Master, Sales Order
// - SAP-specific conventions (SAP__metadata)

=== Power Platform
// - Power Automate data transformation
// - Dataverse entity manipulation
// - Power BI data preparation

== OData Schema (EDMX/CSDL)
// - Reading EDMX: entity types, complex types, navigation properties
// - The OSCH format in UTL-X
// - EDMX → JSON Schema conversion
// - EDMX → documentation generation
// - Using EDMX for validation

== OData ↔ Other Formats
// - OData JSON → standard JSON (strip metadata)
// - OData JSON → XML (for legacy systems)
// - OData JSON → CSV (for reporting)
// - Standard JSON → OData JSON (add metadata for D365 import)
