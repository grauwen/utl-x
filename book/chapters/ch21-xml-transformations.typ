= XML Transformations

XML is the most feature-rich format UTL-X handles — and the most common in enterprise integration. SOAP services, UBL invoices, HL7 healthcare messages, SAP IDocs, SWIFT payments, government data — XML dominates the enterprise world.

This chapter covers everything specific to XML: how to read elements and attributes, how to write XML output with namespaces, how the parser handles edge cases, and real-world patterns from Peppol, FHIR, and SWIFT.

== Reading XML

=== Element Access

XML elements map to UDM Object properties. Access them with dot notation:

```xml
<Order>
  <Customer>Alice Johnson</Customer>
  <Total>299.99</Total>
</Order>
```

```utlx
$input.Order.Customer       // "Alice Johnson"
$input.Order.Total          // 299.99 (auto-detected as number)
```

UTL-X auto-unwraps text content — you get the value directly, not a wrapper object. This is the B13/B14 behavior described in Chapter 9.

=== Attribute Access

XML attributes use the `\@` prefix:

```xml
<Product id="P-001" price="29.99" currency="EUR">Widget</Product>
```

```utlx
$input.Product.@id          // "P-001"
$input.Product.@price       // "29.99" (string — attributes are always strings)
$input.Product.@currency    // "EUR"
$input.Product              // "Widget" (text content, auto-unwrapped)
```

Note: attribute values are always strings. Use `toNumber()` for numeric attributes:

```utlx
toNumber($input.Product.@price)    // 29.99 (number)
```

=== Repeated Elements

When an XML element appears multiple times, UTL-X automatically creates an array:

```xml
<Items>
  <Item>Widget</Item>
  <Item>Gadget</Item>
  <Item>Gizmo</Item>
</Items>
```

```utlx
$input.Items.Item           // ["Widget", "Gadget", "Gizmo"] (array)
$input.Items.Item[0]        // "Widget"
count($input.Items.Item)    // 3
```

=== The Single-Element Problem

If there's only ONE `Item`, it's parsed as a single object — not an array of one:

```xml
<Items>
  <Item>Widget</Item>
</Items>
```

```utlx
$input.Items.Item           // "Widget" (NOT ["Widget"])
```

This can break code that expects an array. Solution: use array hints or always wrap with `flatten([...])`:

```utlx
// Safe: always treat as array
let items = if (isArray($input.Items.Item)) $input.Items.Item else [$input.Items.Item]
map(items, (i) -> ...)
```

The XML parser supports array hints — element names that should always be treated as arrays, even with a single occurrence. This is configured at the engine level.

=== Nested Access

Deep access chains naturally:

```utlx
$input.Invoice.AccountingSupplierParty.Party.PartyName.Name
$input.Bundle.entry[0].resource.Patient.name[0].family
```

Use safe navigation for optional elements:

```utlx
$input.Invoice?.AccountingCustomerParty?.Party?.Contact?.ElectronicMail
```

=== Namespace-Prefixed Elements

XML with namespace prefixes:

```xml
<cac:AccountingSupplierParty>
  <cac:Party>
    <cbc:Name>Windmill Trading B.V.</cbc:Name>
  </cac:Party>
</cac:AccountingSupplierParty>
```

Access with the full prefixed name:

```utlx
$input["cac:AccountingSupplierParty"]["cac:Party"]["cbc:Name"]
```

Bracket notation is required because `:` is not valid in dot notation. This is common in UBL, FHIR, and XBRL documents.

== Writing XML

=== Basic Element Output

When `output xml` is declared, objects become elements:

```utlx
%utlx 1.0
input json
output xml
---
{
  Order: {
    Customer: $input.name,
    Total: $input.amount
  }
}
```

Produces:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Order>
  <Customer>Alice Johnson</Customer>
  <Total>299.99</Total>
</Order>
```

=== Creating Attributes

Use `\@` prefix in property names:

```utlx
{
  Product: {
    @id: "P-001",
    @currency: "EUR",
    _text: $input.description
  }
}
```

Produces:

```xml
<Product id="P-001" currency="EUR">Widget</Product>
```

The `_text` property sets the text content when the element also has attributes. Without attributes, text content is set directly: `{Product: "Widget"}`.

=== Self-Closing Elements

Empty objects with attributes produce self-closing elements:

```utlx
{Item: {@sku: "W-01", @price: "50.00", @qty: "2"}}
```

Produces:

```xml
<Item sku="W-01" price="50.00" qty="2"/>
```

=== Namespace Declarations

Add XML namespace declarations as attributes:

```utlx
{
  Invoice: {
    "@xmlns": "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
    "@xmlns:cbc": "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
    "cbc:ID": $input.invoiceNumber,
    "cbc:IssueDate": $input.date
  }
}
```

Produces:

```xml
<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
  <cbc:ID>INV-2026-001</cbc:ID>
  <cbc:IssueDate>2026-04-30</cbc:IssueDate>
</Invoice>
```

=== Encoding

Control output encoding:

```utlx
output xml {encoding: "UTF-8"}      // default
output xml {encoding: "ISO-8859-1"} // legacy systems
```

== XML Encoding Detection and Conversion

UTL-X can detect and convert between XML encodings:

```utlx
detectXMLEncoding($input)                        // "UTF-8" or "ISO-8859-1"
convertXMLEncoding($input, "UTF-8")              // re-encode to UTF-8
```

This is essential when integrating with legacy systems that use ISO-8859-1 or Windows-1252. UTL-X reads any encoding and outputs in the declared encoding.

== XML Attributes in JSON/YAML Output

When XML is transformed to JSON or YAML, attributes need special handling because JSON and YAML have no concept of attributes. Chapter 22 covers this in detail. The short version:

- Non-leaf element attributes: preserved as `\@key` properties (always)
- Leaf element attributes: dropped by default, preserved with `{writeAttributes: true}`
- Self-closing element attributes: preserved (no text to unwrap)

== Common XML Patterns

=== SOAP Envelope Parsing

```utlx
// Strip the SOAP envelope, extract the business payload:
let body = $input["soap:Envelope"]["soap:Body"]
let payload = body[keys(body)[0]]    // first child of Body = the operation result
{
  ...payload
}
```

=== UBL Invoice (Peppol BIS 3.0)

```utlx
let inv = $input.Invoice
{
  invoiceId: inv["cbc:ID"],
  issueDate: inv["cbc:IssueDate"],
  currency: inv["cbc:DocumentCurrencyCode"],
  supplier: inv["cac:AccountingSupplierParty"]["cac:Party"]["cac:PartyName"]["cbc:Name"],
  customer: inv["cac:AccountingCustomerParty"]["cac:Party"]["cac:PartyName"]["cbc:Name"],
  total: toNumber(inv["cac:LegalMonetaryTotal"]["cbc:PayableAmount"]),
  lines: map(inv["cac:InvoiceLine"], (line) -> {
    lineId: line["cbc:ID"],
    description: line["cac:Item"]["cbc:Name"],
    quantity: toNumber(line["cbc:InvoicedQuantity"]),
    unitPrice: toNumber(line["cac:Price"]["cbc:PriceAmount"])
  })
}
```

The bracket notation `inv["cbc:ID"]` is a string-based property lookup — it accesses the child element named `cbc:ID` on the UDM Object. This works exactly like dot notation (`inv.name`) but allows characters that dot notation cannot handle, such as the colon in namespace prefixes. Since UBL elements all have prefixes like `cbc:` and `cac:`, bracket notation with string keys is the only way to navigate them.

=== HL7 FHIR XML

FHIR uses `value` attributes instead of text content (see FHIR analysis in the architecture docs):

```utlx
let patient = $input.Patient
{
  resourceType: "Patient",
  id: patient.id.@value,
  name: patient.name.family.@value,
  birthDate: patient.birthDate.@value,
  active: toBoolean(patient.active.@value)
}
```

The `\@value` accessor handles FHIR's convention naturally — no special FHIR mode needed.

=== SAP IDoc XML

SAP exports IDocs as flat XML segments (see Chapter 20 on data restructuring for the grouping pattern):

```utlx
// Simple field extraction from IDoc:
{
  orderNumber: $input.IDOC.E1EDK01.BELNR,
  customer: $input.IDOC.E1EDK01.KUNNR,
  currency: $input.IDOC.E1EDK01.WAERK,
  lines: map($input.IDOC.E1EDP01, (line) -> {
    material: line.MATNR,
    quantity: toNumber(line.MENGE),
    price: toNumber(line.NETPR)
  })
}
```

== XML Round-Trip

XML → UDM → XML preserves structure, attributes, namespaces, and text content. The round-trip is not byte-for-byte identical (whitespace and formatting may differ) but is semantically equivalent.

What IS preserved: element names, element order, attributes, attribute values, text content, namespaces, CDATA content.

What is NOT preserved: insignificant whitespace, comment nodes, processing instructions, DTD declarations, entity references (resolved during parsing).

For most integration use cases, this is correct behavior — you care about the data, not the formatting.
