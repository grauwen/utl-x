= Semantic Validation: Beyond Syntax

Chapter 19 covered syntactical validation — checking that data matches a schema's structure, types, and constraints. This chapter covers the next frontier: *semantic* validation — checking that data is not just structurally correct but *meaningfully* correct.

== The Gap Between Syntax and Meaning

Syntactical validation answers: "Is this valid XML? Does it match the XSD? Are required fields present?"

Semantic validation answers: "Is the invoice total correct? Does the VAT ID match the country? Is the delivery date after the order date?"

Consider a Peppol invoice that passes all syntactical checks:

```json
{
  "invoiceId": "INV-001",
  "country": "NL",
  "vatId": "DE123456789",
  "issueDate": "2026-04-30",
  "dueDate": "2026-03-15",
  "lines": [
    {"product": "Widget", "qty": 10, "unitPrice": 25.00, "lineTotal": 300.00},
    {"product": "Gadget", "qty": 5, "unitPrice": 49.99, "lineTotal": 249.95}
  ],
  "total": 999.99
}
```

This is valid JSON. It could pass a JSON Schema. But it has three semantic errors:

- `vatId` starts with "DE" but `country` is "NL" — cross-field inconsistency
- `dueDate` is *before* `issueDate` — temporal violation
- `total` is 999.99 but line totals sum to 549.95 — arithmetic mismatch

No structural schema can catch these. They require *business rule* validation.

== What Schemas Cannot Express

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Syntactical (schema)*], [*Semantic (business rules)*],
  [Field is required], [If country is NL, VAT ID must start with "NL"],
  [Field is a number], [Invoice total = sum of line totals],
  [Field matches pattern], [End date must be after start date],
  [Array has min 1 item], [Quantity x unit price = line total],
  [Value is in enum list], [Order status transition: DRAFT → CONFIRMED → SHIPPED (not backwards)],
  [String length 1-100], [IBAN checksum is valid (modulo 97)],
  [Type is date], [BSN (Dutch citizen ID) passes the 11-check],
)

The left column is what JSON Schema, XSD, and Avro can express. The right column requires a validation language that understands *relationships* between fields.

== Semantic Validation Languages in Industry

=== Schematron (XML)

The ISO standard (ISO/IEC 19757-3) for rule-based XML validation. Uses XPath assertions:

```xml
<sch:rule context="cac:InvoiceLine">
  <sch:assert test="cbc:LineExtensionAmount =
    cbc:InvoicedQuantity * cac:Price/cbc:PriceAmount">
    Line total must equal quantity times unit price
  </sch:assert>
</sch:rule>
```

Peppol BIS 3.0 ships with 65+ Schematron rules that every invoice must pass. UBL, HL7 CDA, DITA, and government XML standards all use Schematron extensively.

*Strength:* mature, ISO standardized, XPath-based.
*Limitation:* XML-only — cannot validate JSON, CSV, or YAML output.

=== FEEL (Friendly Enough Expression Language)

Part of DMN (Decision Model and Notation), an OMG standard. A readable expression language for business rules:

```
invoice.total = sum(invoice.lines.amount)
if order.country = "NL" then vat.rate = 21 else vat.rate = 0
date(order.delivery) > date(order.placed) + duration("P2D")
```

Used by Camunda, jBPM, Kogito, and Drools for decision tables and business rules.

*Strength:* readable, standardized, rich temporal and numeric expressions.
*Limitation:* not schema-aware, designed for decision tables rather than data validation.

=== Other Approaches

- *OPA/Rego (Open Policy Agent):* policy-as-code, used in Kubernetes admission control and API authorization
- *CUE:* configuration language with built-in validation constraints — validates and generates
- *CEL (Common Expression Language):* Google's lightweight expression language for security policies and IAM conditions
- *Ajv custom keywords:* extend JSON Schema with JavaScript validators — JSON-only

== How Semantic Validation Could Work in UTL-X

UTL-X already has a rich expression language with 652 functions, conditionals, cross-field access, and arithmetic. Adding semantic validation is an *extension* of existing capabilities, not a new language.

=== The Proposed Approach: Native Assertions

Add a `validate` block to the `.utlx` file — assertions that run after the transformation, before output serialization:

```utlx
%utlx 1.0
input json
output json
---
{
  invoiceId: $input.id,
  country: $input.country,
  vatId: $input.vatId,
  issueDate: $input.issueDate,
  dueDate: $input.dueDate,
  lines: map($input.lines, (line) -> {
    product: line.product,
    qty: line.qty,
    unitPrice: line.unitPrice,
    lineTotal: line.qty * line.unitPrice
  }),
  total: sum(map($input.lines, (l) -> l.qty * l.unitPrice))
}

validate {
  assert $output.total == sum(map($output.lines, (l) -> l.lineTotal)),
    "Invoice total must equal sum of line totals"

  assert parseDate($output.dueDate, "yyyy-MM-dd") > parseDate($output.issueDate, "yyyy-MM-dd"),
    "Due date must be after issue date"

  assert if ($output.country == "NL") startsWith($output.vatId, "NL") else true,
    "VAT ID must match country code"
}
```

The `validate` block uses standard UTL-X expressions — no new syntax to learn. Each `assert` takes a boolean expression and an error message. If any assertion fails, the transformation result is rejected (STRICT policy) or a warning is logged (WARN policy).

=== Why This Works

The validate block has access to both `$input` (the original message) and `$output` (the transformation result). This enables:

- *Cross-field validation:* compare any field with any other field
- *Computed validation:* sum line totals, check averages, verify checksums
- *Conditional validation:* different rules for different countries, message types, or customer segments
- *Temporal validation:* date comparisons using UTL-X's date functions
- *Pattern validation:* regex matching, string operations, format checks

All using the same 652 stdlib functions available in the transformation body.

=== Integration with the Validation Orchestrator

Semantic validation becomes a fourth step in UTLXe's sandwich pattern:

```
1. PRE-VALIDATION    Validate input against input schema (syntactical)
2. TRANSFORMATION    Execute the .utlx body
3. POST-VALIDATION   Validate output against output schema (syntactical)
4. SEMANTIC          Run validate { ... } assertions (semantic)
```

If semantic validation fails, the behavior depends on the validation policy:
- *STRICT:* transformation fails, output rejected, error returned
- *WARN:* warning logged with assertion message, output produced anyway

== Semantic Validation in Practice Today

The `validate` block is not yet implemented. But you can achieve semantic validation today using `if/else` in the transformation body:

```utlx
let total = sum(map($input.lines, (l) -> l.qty * l.unitPrice))
let calculatedTotal = sum(map($input.lines, (l) -> l.lineTotal))

if (total != calculatedTotal) {
  error: "Validation failed: total mismatch",
  expected: total,
  actual: calculatedTotal
} else {
  // normal output
  invoiceId: $input.id,
  total: total,
  ...
}
```

This works but mixes validation logic with transformation logic. The `validate` block will separate these concerns — the body produces output, the validate block checks it.

== Common Semantic Validation Rules

=== Financial

- Invoice total = sum of line totals
- Line total = quantity x unit price
- Tax amount = taxable amount x tax rate
- Balance = previous balance + credits - debits
- IBAN checksum (modulo 97)
- Currency code is valid ISO 4217

=== Healthcare

- Patient age matches date of birth
- Medication dosage within approved range
- Diagnosis code (ICD-10) is valid for the patient's age/gender
- Referral date before appointment date
- BSN (Dutch) passes 11-check

=== E-Invoicing (Peppol)

- Buyer and seller are not the same entity
- Tax category matches the tax rate
- Document currency matches line amounts currency
- Allowance/charge amounts add up correctly
- Payment means code is valid for the country

=== Logistics

- Delivery date after order date
- Total weight = sum of line item weights
- Shipping address country matches delivery terms (Incoterms)
- Package dimensions within carrier limits

== The Schematron Bridge

For organizations that already have Schematron rules (Peppol compliance, government XML), a future UTL-X feature could evaluate Schematron assertions against XML output:

```utlx
%utlx 1.0
input json
output xml {schema: "invoice.xsd", schematron: "peppol-bis3.sch"}
---
// transformation body
```

The Schematron file contains the 65+ Peppol business rules. UTL-X would evaluate them against the XML output after post-validation. This provides Peppol compliance without reimplementing the rules in UTL-X — use the official Schematron files directly.

== Status

- *Syntactical validation (7 schema formats):* implemented (Chapter 19)
- *Native assertion syntax (`validate` block):* planned — uses existing UTL-X expressions
- *Schematron support:* planned — for Peppol/UBL compliance
- *FEEL integration:* under consideration — for DMN/decision table workflows
- *Workaround today:* use `if/else` in the transformation body for semantic checks
