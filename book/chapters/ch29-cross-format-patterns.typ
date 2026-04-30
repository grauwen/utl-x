= Cross-Format Patterns

The core promise of UTL-X is format agnosticism: write transformation logic once, apply it regardless of whether the data arrives as XML, JSON, CSV, or YAML. This chapter covers the practical patterns for converting between formats — the common conversions, the edge cases, and the strategies for multi-format integration flows.

== The Power of Format Agnosticism

In a traditional integration stack, you write different code for each format pair: an XSLT for XML-to-XML, a jq script for JSON-to-JSON, a Python script for CSV-to-JSON, and custom code for everything else. Six formats yield 30 possible pairs — each with its own tool and syntax.

In UTL-X, one language handles all of them. The UDM (Chapter 9) normalizes every format into the same tree structure. Your transformation works on the UDM, and the serializer produces whatever output format you declare:

```utlx
%utlx 1.0
input xml          // could be json, csv, yaml — same body works
output json        // could be xml, csv, yaml — same body works
---
{
  orderId: $input.Order.OrderId,
  customer: $input.Order.Customer,
  total: toNumber($input.Order.Total)
}
```

Change `input xml` to `input json` and the same transformation works — because `$input.Order.OrderId` navigates the UDM tree, not the XML DOM. The format is a declaration, not a dependency.

== XML to JSON

The most common enterprise conversion. Every modernization project eventually needs it.

=== Pass-Through (Identity)

The simplest conversion — no transformation logic at all:

```utlx
%utlx 1.0
input xml
output json
---
$input
```

Or from the command line:

```bash
cat order.xml | utlx --from xml --to json
```

UTL-X parses the XML into UDM and serializes it as JSON. Element names become property names, text content becomes values, repeated elements become arrays.

=== What Happens to XML Features in JSON

XML has features that JSON lacks. Here's how each one translates:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*XML feature*], [*JSON representation*], [*Notes*],
  [Child elements], [Object properties], [Direct mapping],
  [Text content], [String/number value], [Auto-unwrapped from `_text`],
  [Attributes (non-leaf)], [`@attr` properties], [Always preserved],
  [Attributes (leaf+text)], [Dropped (default)], [`writeAttributes: true` to preserve],
  [Repeated elements], [Arrays], [Auto-detected],
  [Namespaces], [Prefixed names], [`cbc:ID` accessed via bracket notation],
  [CDATA], [String value], [CDATA markers stripped],
  [Comments], [Dropped], [Not representable in JSON],
  [Processing instructions], [Dropped], [Not representable in JSON],
)

To preserve leaf element attributes (like `currency="EUR"` on `<Total>299.99</Total>`), use `output json {writeAttributes: true}`. Chapter 22 covers the attribute handling design in detail.

=== Reshaping During Conversion

Usually you don't want a 1:1 XML-to-JSON conversion — you want to reshape:

```utlx
%utlx 1.0
input xml
output json
---
map($input.Orders.Order, (order) -> {
  id: order.@id,
  customer: order.Customer,
  total: toNumber(order.Total),
  lines: map(order.Lines.Line, (line) -> {
    product: line.Product,
    quantity: toNumber(line.Qty),
    price: toNumber(line.Price)
  })
})
```

The XML hierarchy is flattened, renamed, and retyped in one step.

== JSON to XML

The reverse — common when feeding data into enterprise systems that expect XML.

=== Pass-Through

```utlx
%utlx 1.0
input json
output xml {root: "Order"}
---
$input
```

The `root` option specifies the XML root element name. Without it, UTL-X uses the first property name as the root.

=== Creating Attributes

JSON has no concept of attributes. To create XML attributes, use the `@` prefix in property names:

```utlx
%utlx 1.0
input json
output xml
---
{
  Order: {
    "@id": $input.orderId,
    "@status": $input.status,
    Customer: $input.customerName,
    Total: $input.total
  }
}
```

Output:

```xml
<Order id="ORD-001" status="CONFIRMED">
  <Customer>Alice</Customer>
  <Total>299.99</Total>
</Order>
```

Properties starting with `@` become XML attributes on the parent element. Properties without `@` become child elements.

=== Adding Namespaces

For namespace-qualified XML (UBL, SOAP, FHIR):

```utlx
%utlx 1.0
input json
output xml
---
{
  "Invoice": {
    "@xmlns": "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
    "@xmlns:cbc": "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
    "cbc:ID": $input.invoiceId,
    "cbc:IssueDate": $input.date,
    "cbc:DocumentCurrencyCode": $input.currency
  }
}
```

Namespace declarations are attributes — use `@xmlns` and `@xmlns:prefix`.

== CSV to JSON

Turn flat tabular data into structured JSON:

```utlx
%utlx 1.0
input csv {delimiter: ";"}
output json
---
map($input, (row) -> {
  id: row.OrderId,
  customer: row.Customer,
  amount: toNumber(row.Amount),
  date: parseDate(row.OrderDate, "dd-MM-yyyy")
})
```

CSV rows become JSON objects. The header names become property names. Type conversion is explicit — CSV values are strings by default (except auto-detected numbers and booleans).

== JSON to CSV

Flatten structured JSON into tabular rows:

```utlx
%utlx 1.0
input json
output csv
---
map($input.orders, (order) -> {
  orderId: order.id,
  customer: order.customerName,
  total: order.total,
  status: order.status
})
```

The property names of the first object become CSV headers. The order of properties determines column order.

=== Flattening Nested JSON

CSV is flat — nested objects must be denormalized:

```utlx
%utlx 1.0
input json
output csv
---
flatten(map($input.orders, (order) ->
  map(order.lines, (line) -> {
    orderId: order.id,
    customer: order.customer,
    product: line.product,
    qty: line.qty,
    lineTotal: line.qty * line.price
  })
))
```

Parent fields repeat for each child row. For complex hierarchies, the `unnest()` function (Chapter 20) automates this.

== XML to CSV

The most complex cross-format conversion — hierarchical to flat. This combines XML parsing with denormalization:

```utlx
%utlx 1.0
input xml
output csv {delimiter: ";", regionalFormat: "european"}
---
flatten(map($input.Orders.Order, (order) ->
  map(order.Lines.Line, (line) -> {
    orderId: order.@id,
    customer: order.Customer,
    product: line.Product,
    quantity: toNumber(line.Qty),
    unitPrice: toNumber(line.Price),
    lineTotal: toNumber(line.Qty) * toNumber(line.Price)
  })
))
```

XML attributes (`@id`), text content, and type conversion all happen in one step. The output uses European CSV conventions (semicolons, comma decimals).

== YAML to JSON and JSON to YAML

The simplest conversions — YAML is a superset of JSON, so the structures are equivalent:

```utlx
%utlx 1.0
input yaml
output json
---
$input
```

```utlx
%utlx 1.0
input json
output yaml
---
$input
```

Pass-through is all you need. The only loss: YAML comments and anchors disappear in JSON (JSON has neither). Going JSON to YAML is lossless.

== XML to YAML and YAML to XML

XML to YAML follows the same rules as XML to JSON — the same attribute handling, namespace conventions, and CDATA treatment apply. The only difference is the output serializer:

```utlx
%utlx 1.0
input xml
output yaml
---
$input
```

To preserve leaf element attributes, use `output yaml {writeAttributes: true}` — the same option as JSON. Chapter 22 covers the attribute handling design in detail.

YAML to XML is straightforward — use `@` prefixed properties for attributes and namespace declarations:

```utlx
%utlx 1.0
input yaml
output xml
---
$input
```

== Multi-Format Integration Flows

Real-world integrations often involve more than two formats. The canonical pattern: normalize all sources to a common structure, then convert to the target.

=== Order Processing: XML + CSV + JSON to UBL XML

```utlx
%utlx 1.0
input: orderData xml, pricing csv {delimiter: ";"}, customers json
output xml
---
let order = $orderData.Order
let price = find($pricing, (p) -> p.SKU == order.ProductCode)
let customer = find($customers, (c) -> c.id == order.CustomerId)

{
  Invoice: {
    "@xmlns": "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
    "cbc:ID": order.@id,
    "cbc:IssueDate": formatDate(now(), "yyyy-MM-dd"),
    "cac:AccountingCustomerParty": {
      "cac:Party": {
        "cbc:Name": customer?.name ?? "Unknown"
      }
    },
    "cac:InvoiceLine": {
      "cbc:ID": "1",
      "cbc:InvoicedQuantity": order.Quantity,
      "cbc:LineExtensionAmount": toNumber(order.Quantity) * (price?.UnitPrice ?? 0),
      "cac:Item": {
        "cbc:Name": price?.Description ?? order.ProductCode
      }
    }
  }
}
```

Three formats in, one format out. Each source contributes different data — the order structure from XML, pricing from CSV, customer details from JSON. The transformation merges them into a UBL invoice.

=== Data Lake Export: OData to CSV + JSON + YAML

One source, multiple outputs — same transformation logic, different output declarations:

```utlx
// report.utlx — used three times with different output formats
%utlx 1.0
input odata
output csv                    // change to json or yaml for other targets
---
map($input, (account) -> {
  id: account.accountid,
  name: account.name,
  revenue: account.revenue,
  country: account.address1_country
})
```

```bash
cat accounts.json | utlx report.utlx --to csv > report.csv
cat accounts.json | utlx report.utlx --to json > report.json
cat accounts.json | utlx report.utlx --to yaml > report.yaml
```

Same transformation, three formats. The `--to` flag overrides the output declaration.

== The Canonical Data Model Pattern

For organizations with many integrations, define a canonical (internal) data model and transform to/from it:

```
Source A (XML) ──→ Canonical JSON ──→ Target X (CSV)
Source B (CSV) ──→ Canonical JSON ──→ Target Y (XML)
Source C (YAML) ─→ Canonical JSON ──→ Target Z (OData)
```

Each source has one transformation: source → canonical. Each target has one transformation: canonical → target. Adding a new source means writing one new transformation, not N (one per target). Adding a new target means writing one new transformation, not M (one per source).

With N sources and M targets:
- *Without canonical model:* N x M transformations
- *With canonical model:* N + M transformations

For 5 sources and 5 targets: 25 transformations vs 10. For 10 and 10: 100 vs 20. The canonical model scales.

UTL-X's format agnosticism makes this practical — the canonical format can be JSON, and every source/target conversion is a standard `.utlx` file regardless of the source or target format.

== Format Conversion Quick Reference

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*From → To*], [*Complexity*], [*Key considerations*],
  [XML → JSON], [Low], [Attributes, namespaces, repeated elements],
  [JSON → XML], [Low], [Root element name, `@` for attributes],
  [CSV → JSON], [Low], [Type conversion (strings to numbers/dates)],
  [JSON → CSV], [Medium], [Flatten nested structures first],
  [XML → CSV], [Medium], [Denormalize hierarchy + type conversion],
  [CSV → XML], [Low], [Add structure and element names],
  [YAML → JSON], [Trivial], [Pass-through — lossless],
  [JSON → YAML], [Trivial], [Pass-through — lossless],
  [XML → YAML], [Low], [Same as XML → JSON, different serializer],
  [OData → JSON], [Low], [Strips `@odata.*` annotations automatically],
  [JSON → OData], [Low], [Add `@odata.context` and metadata],
  [Any → CSV], [Medium], [Must flatten to tabular structure],
)
