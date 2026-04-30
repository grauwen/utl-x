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

== XML Canonicalization (C14N)

XML canonicalization produces a deterministic, normalized representation of an XML document. Two XML documents that are semantically identical but differ in whitespace, attribute order, or namespace declarations will produce the same canonical form. This is essential for XML digital signatures (XMLDSig), change detection, and round-trip verification.

UTL-X implements the W3C XML Canonicalization standards:

```utlx
// Standard C14N (W3C 1.0) — sort attributes, normalize whitespace, remove comments
c14n($input)

// C14N with comments preserved
c14nWithComments($input)

// Exclusive C14N — only includes namespaces actually used (ideal for SOAP signatures)
excC14n($input)
excC14nWithComments($input)

// C14N 1.1 — handles XML 1.1 features
c14n11($input)
c14n11WithComments($input)
```

=== Hashing and Comparison

For verifying that a transformation preserves XML content — or detecting changes:

```utlx
// Hash the canonical form (default SHA-256)
c14nHash($input)                       // "a1b2c3d4..."
c14nHash($input, "SHA-512")            // different algorithm

// Compare two XML documents semantically (ignoring attribute order, whitespace)
c14nEquals(xml1, xml2)                 // true if same canonical form

// Generate a fingerprint (shorter than full hash, useful for logging)
c14nFingerprint($input)
```

=== Round-Trip Verification

When transforming XML → JSON → XML, you can verify that no data was lost:

```utlx
// Original XML
let original = $input

// Transform to JSON and back
let asJson = renderJson($input)
let backToXml = parse(asJson, "json")

// Compare canonical forms
c14nEquals(original, backToXml)        // true if round-trip is lossless
```

This is particularly valuable in compliance scenarios (e-invoicing, healthcare) where you must prove that your transformation pipeline does not alter the business content.

== QName Functions (Qualified Names)

XML uses Qualified Names (QNames) to identify elements and attributes within namespaces. A QName like `cbc:InvoiceTypeCode` combines a prefix (`cbc`), a local name (`InvoiceTypeCode`), and an implied namespace URI (`urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2`).

UTL-X provides 9 functions for decomposing, constructing, and matching QNames:

```utlx
// Decompose a QName
localName($input.Invoice)              // "Invoice" (without prefix)
namespacePrefix($input.Invoice)        // "cbc" (prefix only)
qualifiedName($input.Invoice)          // "cbc:Invoice" (full prefixed name)
namespaceUri($input.Invoice)           // "urn:oasis:names:..." (namespace URI)

// Check and list namespaces
hasNamespace($input.Invoice)           // true
getNamespaces($input.Invoice)          // {"cbc": "urn:oasis:...", "cac": "urn:oasis:..."}

// Construct a QName
createQname("Invoice", "urn:oasis:names:...", "cbc")
// → {localName: "Invoice", namespaceUri: "urn:oasis:...", prefix: "cbc", qualifiedName: "cbc:Invoice"}

// Resolve a prefixed name using namespace context
resolveQname("cbc:Invoice", $input)
// → {localName: "Invoice", prefix: "cbc", namespaceUri: "urn:oasis:...", qualifiedName: "cbc:Invoice"}

// Match by QName (string or structured)
matchesQname($input.Invoice, "cbc:Invoice")   // true
```

These functions are essential for enterprise XML formats where namespace awareness determines correctness — you cannot just compare element names as strings when the same local name appears in different namespaces.

=== XBRL (Financial Reporting)

XBRL (eXtensible Business Reporting Language) is the most namespace-intensive XML format in production use. Every financial fact is identified by a QName — the namespace tells you which taxonomy (US-GAAP, IFRS, local GAAP) defines the concept, and the local name identifies the concept itself:

```xml
<xbrli:xbrl xmlns:us-gaap="http://fasb.org/us-gaap/2024"
            xmlns:ifrs="http://xbrl.ifrs.org/taxonomy/2024-03-27">
  <us-gaap:Revenue contextRef="FY2024" unitRef="USD" decimals="-6">42000000000</us-gaap:Revenue>
  <us-gaap:NetIncome contextRef="FY2024" unitRef="USD" decimals="-6">15000000000</us-gaap:NetIncome>
  <ifrs:Revenue contextRef="FY2024" unitRef="EUR" decimals="-6">38000000000</ifrs:Revenue>
</xbrli:xbrl>
```

Notice: `us-gaap:Revenue` and `ifrs:Revenue` have the same local name but different namespaces — they are different concepts with different definitions. String comparison (`"Revenue" == "Revenue"`) would wrongly treat them as the same. QName functions handle this correctly:

```utlx
%utlx 1.0
input xml
output json
---
let facts = $input["xbrli:xbrl"].*

// Extract all US-GAAP facts (filter by namespace)
let usgaap = filter(facts, (fact) ->
  namespaceUri(fact) == "http://fasb.org/us-gaap/2024"
)

// Build a financial summary from XBRL facts
map(usgaap, (fact) -> {
  concept: localName(fact),
  namespace: namespacePrefix(fact),
  value: toNumber(fact),
  context: fact.@contextRef,
  unit: fact.@unitRef,
  decimals: toNumber(fact.@decimals)
})
```

QName functions are also needed for:
- *Inline XBRL (iXBRL):* HTML documents with embedded XBRL facts tagged by QName
- *Taxonomy switching:* mapping from one reporting standard to another (US-GAAP → IFRS)
- *XBRL validation:* verifying that facts reference valid taxonomy concepts
- *Regulatory submissions:* SEC (US), ESMA (EU), HMRC (UK) all require XBRL filings

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

== CDATA Sections

CDATA (`<![CDATA[...]]>`) is an XML mechanism for including text that would otherwise need escaping. Inside a CDATA section, characters like `<`, `>`, and `&` are treated as literal text, not XML markup:

```xml
<Script><![CDATA[
  if (price < 100 && quantity > 0) {
    discount = price * 0.1;
  }
]]></Script>
```

Without CDATA, the `<` and `&` would need escaping as `&lt;` and `&amp;`. CDATA is common in:
- Embedded scripts or code snippets in XML configurations
- HTML content inside XML elements (CMS systems, email templates)
- SQL queries stored in XML configuration files
- Log messages with special characters

=== How UTL-X Handles CDATA

On input, CDATA sections are transparent — the parser extracts the content and treats it as plain text. The CDATA markers are stripped:

```utlx
// Input: <Script><![CDATA[if (x < 10) alert("low");]]></Script>
$input.Script    // 'if (x < 10) alert("low");'
```

You work with the text content directly — no CDATA markers, no escaping. The content is a regular string.

=== XML Inside CDATA

A common real-world pattern — not part of the XML standard, but widely used in integration — is embedding XML inside CDATA. The outer XML parser sees the inner XML as plain text, avoiding namespace conflicts and double-parsing:

```xml
<Message>
  <Header>
    <MessageId>MSG-001</MessageId>
  </Header>
  <Payload><![CDATA[
    <Order xmlns="urn:example:orders">
      <Customer>Acme Corp</Customer>
      <Total>299.99</Total>
    </Order>
  ]]></Payload>
</Message>
```

This appears in SOAP envelopes (inner payload wrapped to avoid namespace collisions), message queues (JMS/MQ wrapping XML bodies), SAP PI/PO adapters, API gateway logs, and configuration files that embed XML snippets.

UTL-X handles this naturally — the CDATA content arrives as a string, and you parse it with `parse()`:

```utlx
// $input.Message.Payload is a string containing XML
let innerXml = parse($input.Message.Payload, "xml")

// Now navigate the inner XML:
{
  messageId: $input.Message.Header.MessageId,
  customer: innerXml.Order.Customer,
  total: toNumber(innerXml.Order.Total)
}
```

The `parse()` function takes the CDATA string and parses it as XML into a navigable UDM tree. This two-level parsing — outer XML automatically, inner XML explicitly — is the correct approach because UTL-X cannot know whether a CDATA string contains XML, JSON, SQL, or plain text.

The reverse — embedding XML output inside CDATA in another XML document — requires `render()`:

```utlx
{
  Message: {
    Header: { MessageId: "MSG-002" },
    Payload: render({
      Order: {
        Customer: $input.customer,
        Total: $input.total
      }
    }, "xml")    // renders the inner XML as a string
  }
}
```

The inner XML is serialized to a string by `render()`, and the outer XML serializer will entity-escape it (producing `&lt;Order&gt;...` rather than CDATA). The result is semantically identical — any XML parser reads both forms the same way.

If a downstream system specifically requires CDATA markers (some legacy systems check for them), this is a known limitation — UTL-X uses entity escaping on output, not CDATA.

== XML Comments and Processing Instructions

=== Comments

XML comments (`<!-- ... -->`) are *not preserved* during parsing. They are skipped and do not appear in the UDM:

```xml
<!-- This is an order from Acme Corp -->
<Order>
  <Customer>Alice</Customer>  <!-- primary contact -->
</Order>
```

After parsing, only the `Order` and `Customer` elements exist. The comments are gone. This is standard behavior for data transformation tools — comments are metadata about the XML document, not part of the data.

If you need to *generate* XML comments in output, this is not currently supported. Comments are a formatting concern, not a data concern.

=== Processing Instructions

XML processing instructions (`<?target data?>`) — like `<?xml-stylesheet type="text/xsl" href="style.xsl"?>` — are also not preserved. They are parsed and discarded. The XML declaration (`<?xml version="1.0" encoding="UTF-8"?>`) is handled separately: it's read for encoding detection and regenerated on output with the declared encoding.

== XML Documentation (xs:annotation)

XSD schemas can include documentation for types and elements using `xs:annotation` and `xs:documentation`:

```xml
<xs:element name="OrderId">
  <xs:annotation>
    <xs:documentation>
      Unique identifier for the order. Format: ORD-NNNNN.
      Assigned by the order management system at creation time.
    </xs:documentation>
  </xs:annotation>
  <xs:simpleType>
    <xs:restriction base="xs:string">
      <xs:pattern value="ORD-[0-9]{5}"/>
    </xs:restriction>
  </xs:simpleType>
</xs:element>
```

This is not data inside an XML instance — it's metadata inside an XSD schema. UTL-X handles it in two ways:

*Reading:* When parsing an XSD (`input xsd`), documentation is extracted and mapped to the USDL `%description` directive. It becomes part of the schema model and is available for transformation.

*Writing:* When generating XSD (`output xsd {addDocumentation: true}`), USDL `%description` values are emitted as `xs:documentation` elements. Without the option, documentation is omitted for a cleaner schema.

*Converting:* When converting XSD to JSON Schema, `xs:documentation` becomes the `description` keyword. When converting to Avro, it becomes the `doc` field. The USDL tier system maps documentation across all schema formats.

== XML Round-Trip

XML → UDM → XML preserves structure, attributes, namespaces, and text content. The round-trip is not byte-for-byte identical (whitespace and formatting may differ) but is semantically equivalent.

What IS preserved: element names, element order, attributes, attribute values, text content, namespaces, CDATA content (as text).

What is NOT preserved: insignificant whitespace, comment nodes, processing instructions, DTD declarations, entity references (resolved during parsing), CDATA markers (content preserved, markers not).

For most integration use cases, this is correct behavior — you care about the data, not the formatting. Use `c14nEquals()` (see XML Canonicalization section above) to verify semantic equivalence.
