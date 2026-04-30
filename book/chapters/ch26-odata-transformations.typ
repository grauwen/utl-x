= OData Transformations

OData (Open Data Protocol) is the REST-based data access standard behind Microsoft's ecosystem: Dynamics 365, SharePoint, Power Platform, Dataverse, and Azure APIs. If you integrate with Microsoft products — or with SAP systems that expose OData services — you will encounter OData JSON.

OData has evolved through several versions, each with different payload formats:

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Version*], [*Payload format*], [*Status*], [*UTL-X support*],
  [OData v1-v3], [XML (Atom/AtomPub)], [Legacy — largely deprecated], [Not supported],
  [OData v3], [JSON (verbose format)], [Legacy], [Not supported],
  [*OData v4*], [*JSON with `@odata.*` annotations*], [*Current OASIS standard*], [*Fully supported*],
)

#block(
  fill: rgb("#E3F2FD"),
  inset: 12pt,
  radius: 4pt,
  width: 100%,
)[
  *UTL-X supports OData v4 JSON only.* This is the current OASIS standard and the format used by all modern Microsoft services (Dynamics 365, Dataverse, Graph API), SAP Gateway, and other OData v4 providers. The older Atom/XML format (OData v1-v3) and the verbose JSON format (OData v3) are not supported — these are effectively deprecated, and services that still use them typically offer a v4 endpoint as well.
]

OData v4 JSON looks like regular JSON but carries metadata annotations: `@odata.context`, `@odata.type`, `@odata.id`. These tell the client about the entity's type, its URL, and where to find the schema. UTL-X understands these annotations natively and can strip, preserve, or generate them as needed.

If you encounter OData v1-v3 XML (Atom) payloads from a legacy service, parse them as standard XML (`input xml`) and navigate the Atom structure manually. But in practice, this is rare — upgrade the service endpoint to v4 if at all possible.

== OData v4 JSON and UDM

When UTL-X parses OData JSON, it separates the metadata annotations from the business data:

```json
{
  "@odata.context": "https://crm.example.com/$metadata#Accounts",
  "@odata.type": "#Microsoft.Dynamics.CRM.account",
  "accountid": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Acme Corp",
  "revenue": 5000000,
  "primarycontactid@odata.type": "#Microsoft.Dynamics.CRM.contact"
}
```

After parsing:

- Business properties (`accountid`, `name`, `revenue`) become regular UDM Object properties — accessed with dot notation
- `@odata.*` annotations are moved to UDM attributes — accessed with `@` prefix
- Per-property annotations (`primarycontactid@odata.type`) are stored as attributes on the target property

```utlx
$input.name                        // "Acme Corp"
$input.accountid                   // "550e8400-..."
$input.revenue                     // 5000000
$input.@["odata.context"]          // "https://crm.example.com/$metadata#Accounts"
```

This separation is the key design: your transformation works with clean business data, while the OData metadata is preserved and available when needed.

=== Collection Responses

OData collection responses wrap the entity array in a `value` property:

```json
{
  "@odata.context": "https://crm.example.com/$metadata#Accounts",
  "@odata.count": 42,
  "value": [
    {"accountid": "...", "name": "Acme Corp"},
    {"accountid": "...", "name": "Globex Inc"}
  ]
}
```

UTL-X's OData parser intelligently unwraps this: when the only data property is `value` and no collection-level OData attributes are needed, it unwraps to a bare array. Otherwise it preserves the wrapper. In either case, you access the entities naturally:

```utlx
// If unwrapped to array:
$input[0].name                     // "Acme Corp"

// If wrapper preserved:
$input.value[0].name               // "Acme Corp"
count($input.value)                // 42
```

== Reading OData

=== Dynamics 365 API Responses

A typical D365 sales order response:

```utlx
%utlx 1.0
input odata
output json
---
map($input, (order) -> {
  orderId: order.salesorderid,
  orderNumber: order.ordernumber,
  customer: order.customerid_account?.name,
  total: order.totalamount,
  currency: order.transactioncurrencyid?.isocurrencycode,
  status: order.statuscode,
  lines: map(order.order_details ?? [], (line) -> {
    product: line.productid?.name ?? line.productdescription,
    quantity: line.quantity,
    unitPrice: line.priceperunit,
    lineTotal: line.extendedamount
  })
})
```

Note the safe navigation (`?.`) — OData navigation properties (like `customerid_account`) may be null if the related entity wasn't expanded in the API query.

=== SharePoint List Items

```utlx
%utlx 1.0
input odata
output json
---
map($input, (item) -> {
  id: item.Id,
  title: item.Title,
  created: item.Created,
  author: item.Author?.Title ?? "Unknown",
  status: item.Status,
  attachments: item.AttachmentFiles ?? []
})
```

=== SAP OData Services

SAP Gateway exposes business objects via OData. SAP uses some conventions of its own (SAP-specific metadata), but the core OData JSON format is standard:

```utlx
%utlx 1.0
input odata
output json
---
map($input, (bp) -> {
  partnerId: bp.BusinessPartner,
  name: bp.BusinessPartnerFullName,
  category: bp.BusinessPartnerCategory,
  country: bp.to_BusinessPartnerAddress[0]?.Country ?? "XX",
  email: bp.to_BusinessPartnerAddress[0]?.to_EmailAddress[0]?.EmailAddress
})
```

== Writing OData

=== Output Options

```utlx
output odata                                    // minimal metadata (default)
output odata {metadata: "full"}                 // all annotations
output odata {metadata: "none"}                 // no annotations (plain JSON)
output odata {context: "https://api.example.com/$metadata#Customers"}
output odata {wrapCollection: true}             // wrap array in {value: [...]}
```

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Option*], [*Type*], [*Default*], [*What it does*],
  [`metadata`], [String], [`"minimal"`], [`"minimal"`: context only. `"full"`: all annotations. `"none"`: plain JSON],
  [`context`], [String], [null], [The `@odata.context` URL to include],
  [`wrapCollection`], [Boolean], [`true`], [Wrap arrays in `{"value": [...]}`],
)

=== Metadata Levels

*Minimal* (default) — only the `@odata.context` URL:

```json
{
  "@odata.context": "https://api.example.com/$metadata#Customers",
  "value": [{"name": "Acme Corp"}, {"name": "Globex Inc"}]
}
```

*Full* — all available annotations:

```json
{
  "@odata.context": "https://api.example.com/$metadata#Customers",
  "@odata.count": 2,
  "value": [
    {
      "@odata.type": "#Example.Customer",
      "@odata.id": "Customers('C-42')",
      "name": "Acme Corp"
    }
  ]
}
```

*None* — plain JSON, no OData annotations. Use this when the target system doesn't understand OData conventions.

=== Generating OData for Dynamics 365 Import

Transform external data into D365-compatible OData JSON:

```utlx
%utlx 1.0
input json
output odata {metadata: "minimal", context: "https://org.crm.dynamics.com/api/data/v9.2/$metadata#accounts"}
---
map($input.customers, (c) -> {
  name: c.companyName,
  revenue: c.annualRevenue,
  telephone1: c.phone,
  emailaddress1: c.email,
  address1_city: c.city,
  address1_country: c.country
})
```

== OData Schema (EDMX/CSDL)

OData services publish their schema as EDMX (Entity Data Model XML) — also known as CSDL (Common Schema Definition Language). UTL-X can read and write EDMX using the `osch` format:

```utlx
%utlx 1.0
input osch
output yaml %usdl 1.0
---
$input
```

This reads an EDMX metadata document and outputs it as human-readable YAML with USDL directives — useful for understanding complex OData service models.

=== What EDMX Contains

An EDMX document defines:
- *Entity types* — the structure of business objects (Customer, Order, Product)
- *Complex types* — reusable structured types (Address, Money)
- *Enum types* — allowed values (OrderStatus, PaymentMethod)
- *Navigation properties* — relationships between entities (Customer has Orders)
- *Entity container* — the service root with entity sets and singletons
- *Referential constraints* — foreign key relationships

UTL-X parses all of these into USDL directives, making the OData model available for schema-to-schema conversion.

=== Cross-Format Schema Conversion

Convert an OData schema to other schema formats:

```utlx
// EDMX → JSON Schema
%utlx 1.0
input osch
output jsch
---
$input

// EDMX → XSD
%utlx 1.0
input osch
output xsd
---
$input

// JSON Schema → EDMX
%utlx 1.0
input jsch
output osch
---
$input
```

The USDL tier system (Chapter 11) handles the translation between formats. OData-specific directives like `%entityType`, `%navigation`, and `%cardinality` are Tier 3 — they map to EDMX natively and become documentation comments in other formats.

== Common OData Patterns

=== Stripping Metadata

Convert OData JSON to clean JSON by removing all annotations:

```utlx
%utlx 1.0
input odata
output json
---
$input
```

The OData parser moves annotations to UDM attributes. The JSON serializer doesn't write attributes by default. Result: clean JSON without `@odata.*` noise.

=== OData to CSV for Reporting

Export D365 data as CSV for Excel or Power BI:

```utlx
%utlx 1.0
input odata
output csv {delimiter: ";", regionalFormat: "european"}
---
map($input, (account) -> {
  id: account.accountid,
  name: account.name,
  revenue: account.revenue,
  country: account.address1_country,
  industry: account.industrycode,
  created: account.createdon
})
```

=== OData to XML for Legacy Systems

Transform a D365 response into XML for a legacy ERP:

```utlx
%utlx 1.0
input odata
output xml {root: "Customers"}
---
map($input, (c) -> {
  Customer: {
    Id: c.accountid,
    Name: c.name,
    Country: c.address1_country,
    Revenue: c.revenue
  }
})
```

=== Pagination Handling

OData uses `@odata.nextLink` for pagination. While UTL-X doesn't handle HTTP pagination itself (that's the orchestration layer's job), you can detect and flag it:

```utlx
%utlx 1.0
input odata
output json
---
{
  data: map($input.value ?? $input, (item) -> {
    ...item
  }),
  hasMore: $input.@["odata.nextLink"] != null,
  nextLink: $input.@["odata.nextLink"]
}
```

== OData vs Standard JSON: When to Use Which

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Use `input odata`*], [*Use `input json`*],
  [Dynamics 365, SharePoint, Dataverse APIs], [Standard REST APIs],
  [Response has `@odata.*` annotations], [No OData annotations],
  [You want annotations stripped automatically], [You want all properties as-is],
  [SAP Gateway OData services], [Generic JSON APIs],
  [You need `@odata.context` in output], [No metadata needed in output],
)

If your JSON has `@odata.*` properties and you use `input json`, they'll appear as regular properties named `@odata.context` (accessible via bracket notation). With `input odata`, they're separated into attributes — cleaner to work with.
