= Schema Validation

#block(
  fill: rgb("#E3F2FD"),
  inset: 12pt,
  radius: 4pt,
  width: 100%,
)[
  *UTLXe engine feature.* Runtime schema validation — validating input and output data against schemas during transformation — is a UTLXe production engine capability. The CLI (`utlx`) does not validate data payloads at runtime. This is a deliberate design decision — see _"Where Validation Lives — and Why"_ below for the reasoning.
]

A transformation that produces wrong output is worse than one that fails — because wrong output is sent downstream, corrupts data in the target system, and the error is discovered hours or days later. Schema validation catches these errors at transformation time, before they leave UTL-X.

This chapter covers UTL-X's validation capabilities: what schemas are supported, how the validation orchestrator works, and the difference between syntactical and semantic validation.

== Where Validation Lives — and Why

The CLI does not validate data payloads at runtime. This is not a missing feature. It is a deliberate design decision.

=== The CLI Must Accept Anything

The real world is full of malformed messages — missing fields, wrong types, unexpected structures, legacy formats that don't match their own documentation. The CLI's job is to be a workhorse: read whatever comes in, transform it, produce output.

If the CLI rejected non-conforming input, it would be useless for the very situations where UTL-X is needed most — cleaning up, normalizing, and restructuring messy data. A transformation developer needs to _see_ what the bad data looks like, not be told it's invalid.

More fundamentally: *transformation IS the fix.* Often the whole point of writing the transformation is to handle the malformed data — add missing fields, fix types, normalize structures. If the CLI rejected the input, you couldn't write the fix for it. The tool would prevent you from solving the problem it exists to solve.

And schemas lie. In practice, the "official" schema for an API or format is frequently outdated, incomplete, or aspirational. SAP IDoc documentation says a field is mandatory, but production IDocs arrive without it. An API's JSON Schema says `required: ["address"]`, but 30% of real records have no address. The CLI must deal with reality, not the spec.

There is also a discovery workflow to consider. Before you can validate, you need to understand. A developer receiving an unknown IDoc or a partner's XML for the first time needs the CLI to parse it, explore it with `$input.*`, and figure out the actual structure. Validation would block this discovery phase entirely.

=== UTLXe Operates in Middleware Where Validation Is Vital

The engine sits in production pipelines between systems — receiving orders, invoices, payments, patient records. Here, a malformed message that passes through silently can corrupt a downstream database, trigger a wrong payment, or violate regulatory compliance. The validation orchestrator (pre-validate → transform → post-validate) guarantees that only conforming data enters and leaves the pipeline.

Fail-fast saves money. A malformed invoice that passes through UTLXe unchecked might only be caught by the tax authority 30 days later — costing investigation time, penalties, and re-processing. Pre-validation catches it in milliseconds.

Auditability matters. In regulated industries (banking, healthcare, e-invoicing), you need proof that every message was validated. The validation orchestrator creates that audit trail. A CLI session leaves no trace.

And the blast radius is different. The CLI processes one message for one developer. UTLXe processes thousands per second for potentially millions of downstream consumers. The cost of letting a bad message through scales with the audience.

=== The Right Default in the Right Executable

This split mirrors how compilers work. A C compiler will compile code with warnings and let you run it. But a CI/CD pipeline enforces `-Werror` — same tool, different strictness depending on context. UTL-X makes this the default rather than a flag: you don't need developers to remember to turn validation off, or operations teams to remember to turn it on. The right behavior is built into the right executable.

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Aspect*], [*CLI (utlx)*], [*Engine (UTLXe)*],
  [Purpose], [Development, exploration, testing], [Production middleware],
  [Input tolerance], [Accept anything], [Validate against schema],
  [Output checking], [Developer inspects visually], [Post-validation enforces contract],
  [Bad data], [Let the developer see it and fix it], [Reject it before it propagates],
  [Schema role], [Static script analysis (`utlx validate`)], [Runtime data validation],
  [Audience], [One developer], [Thousands of downstream consumers],
)

== Why Validate?

Consider a Peppol e-invoicing flow. Your transformation maps Dynamics 365 JSON to UBL 2.1 XML. Without validation:

- A missing `cbc:InvoiceTypeCode` element → Peppol Access Point rejects the invoice hours later
- A wrong VAT calculation → the tax authority flags the invoice weeks later
- An invalid IBAN → the payment fails days later

With validation:

- Pre-validation catches input data issues before the transformation runs
- Post-validation catches mapping bugs before the output leaves UTL-X
- The error message tells you exactly which field, which constraint, what's wrong

Validation is the difference between "it compiled and ran" and "it produced correct output."

== Supported Schema Formats

UTL-X includes seven schema validators — one for each schema format:

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Validator*], [*Schema format*], [*Validates*], [*Standard*],
  [JsonSchemaValidator], [JSON Schema], [JSON data], [Draft-04 through 2020-12],
  [XsdValidator], [XSD], [XML data], [W3C XSD 1.0 / 1.1],
  [AvroSchemaValidator], [Avro Schema], [Avro-compatible data], [Apache Avro 1.11],
  [ProtobufValidator], [Protobuf], [Proto3-compatible data], [Protocol Buffers 3],
  [TableSchemaValidator], [Table Schema (TSCH)], [Tabular / CSV data], [Frictionless Data],
  [ODataSchemaValidator], [OData Schema (OSCH)], [OData entities], [OASIS OData v4],
  [YamlSchemaValidator], [YAML via JSON Schema], [YAML data], [YAML validated as JSON],
)

Each validator understands the schema format's native constraint language. JSON Schema's `required`, `pattern`, `minimum`. XSD's `minOccurs`, `xs:pattern`, `xs:restriction`. Avro's field defaults and unions. They're not wrappers — they validate against the real schema semantics.

== The Validation Orchestrator

UTLXe's `ValidationOrchestrator` runs validation in a sandwich pattern around the transformation:

```
┌──────────────────────────────────────────────────┐
│ 1. PRE-VALIDATION                                │
│    Validate input data against input schema       │
│    → Fail fast if input is invalid                │
├──────────────────────────────────────────────────┤
│ 2. TRANSFORMATION                                 │
│    Execute the .utlx transformation               │
├──────────────────────────────────────────────────┤
│ 3. POST-VALIDATION                                │
│    Validate output data against output schema     │
│    → Catch mapping bugs before output leaves      │
└──────────────────────────────────────────────────┘
```

// DIAGRAM: Sandwich: Pre-validate → Transform → Post-validate
// Source: part2-language.pptx

If pre-validation fails, the transformation never runs — saving compute time and preventing cascading errors from bad input. If post-validation fails, the output is rejected — the mapping has a bug that needs fixing.

=== Configuring Validation

Validation is configured per transformation in the UTLXe engine. There are two ways to attach schemas:

*Via the dynamic load API (protobuf/gRPC):* The transport config map includes `validate_input`, `input_schema`, `input_schema_format` (and the same for output). This is the primary mechanism for runtime validation.

*Via transform.yaml in a bundle:*

```yaml
# transform.yaml
strategy: TEMPLATE
validationPolicy: STRICT
inputs:
  - name: input
    schema: "order-input.json"
output:
  schema: "invoice-output.xsd"
```

A future enhancement (F01) will allow inline schema declaration in the .utlx header:

```utlx
%utlx 1.0
input json {schema: "order-input.json"}
output xml {schema: "invoice-output.xsd"}
---
// transformation body
```

This syntax is parsed today but not yet wired to the validation orchestrator. When F01 is implemented, the same validators will be used — the only difference is where the schema reference lives.

=== CLI Static Validation

The CLI offers a separate validation capability — static analysis of a `.utlx` script against a schema:

```bash
utlx validate --schema order-output.json transformation.utlx
```

This checks whether the transformation's _output structure_ (inferred via type analysis) is compatible with the schema. It does NOT validate runtime data — it validates the script itself at compile time. Think of it as type checking: "will this transformation always produce valid output?" rather than "is this specific input valid?"

== Validation Policies

Not every validation failure should stop the transformation. The policy controls what happens:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Policy*], [*On failure*], [*Use when*],
  [STRICT], [Transformation fails with error], [Production — contract enforcement],
  [WARN], [Warning logged, transformation continues], [Migration — monitor before enforcing],
  [SKIP], [Schema loaded but not validated], [Documentation only],
)

Start with `WARN` during development (see what would fail). Switch to `STRICT` in production (enforce the contract).

== JSON Schema Validation

JSON Schema is the most commonly used validator — it validates JSON, YAML, and OData data.

=== What JSON Schema Checks

```json
{
  "type": "object",
  "required": ["orderId", "customer", "total"],
  "properties": {
    "orderId": {"type": "string", "pattern": "^ORD-[0-9]{3,}$"},
    "customer": {"type": "string", "minLength": 1},
    "total": {"type": "number", "minimum": 0},
    "currency": {"type": "string", "enum": ["EUR", "USD", "GBP"]},
    "lines": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "object",
        "required": ["product", "qty"],
        "properties": {
          "product": {"type": "string"},
          "qty": {"type": "integer", "minimum": 1}
        }
      }
    }
  }
}
```

This schema enforces: orderId is required and matches a pattern, customer is a non-empty string, total is a non-negative number, currency is one of three values, lines is a non-empty array of objects with required product and qty fields.

=== Validation Errors

When validation fails, the error message pinpoints the problem:

```
Validation error at $.customer: required field missing
Validation error at $.total: -50 is less than minimum 0
Validation error at $.orderId: "INV-001" does not match pattern "^ORD-[0-9]{3,}$"
Validation error at $.lines[2].qty: "five" is not of type integer
```

Each error includes the JSON Path to the failing field, the constraint that was violated, and the actual value. This makes debugging fast — you know exactly what's wrong and where.

== XSD Validation

XSD validation is essential for enterprise XML — UBL invoices, HL7 messages, ISO 20022 payments all ship with XSD schemas.

```utlx
%utlx 1.0
input json
output xml {schema: "UBL-Invoice-2.1.xsd"}
---
// If the output doesn't match the UBL XSD, transformation fails
```

XSD checks: element names, types, cardinality (minOccurs/maxOccurs), patterns, enumerations, namespace compliance. The JDK's built-in `javax.xml.validation` handles XSD 1.0; XSD 1.1 support uses the same API.

== Syntactical vs Semantic Validation

Everything described so far is *syntactical validation* — checking the structure, types, and constraints defined in a schema.

=== What Syntactical Validation Catches

- Is this field a string? A number? A date?
- Is this field present (required)?
- Does this string match a pattern (regex)?
- Is this number within range (min/max)?
- Is this array the right length?
- Does this element have the right children (XSD structure)?

=== What Syntactical Validation Cannot Catch

- Does the invoice total equal the sum of line items?
- If the country is NL, does the VAT ID start with "NL"?
- Is the end date after the start date?
- Is this IBAN valid (checksum)?
- Can an order move from SHIPPED back to PENDING?

These are *semantic* rules — they involve business logic, cross-field relationships, and domain knowledge. No structural schema can express them.

=== The Gap

UTL-X currently supports syntactical validation (7 schema validators). Semantic validation — business rules expressed in languages like Schematron, FEEL, or custom assertions — is a planned future capability. Chapter 39 covers the semantic validation vision in detail, including the proposed `assert` syntax:

```utlx
// Future syntax (not yet implemented):
validate {
  assert sum($output.lines.total) == $output.grandTotal
  assert $output.issueDate < $output.dueDate
  assert if ($output.country == "NL") matches($output.vatId, "NL[0-9]{9}B[0-9]{2}")
}
```

For now, semantic rules must be expressed as part of the transformation logic — using `if/else`, `match`, or `try/catch` to check conditions and produce errors or warnings.

== Validation in Practice

=== Development Workflow

+ Write your transformation
+ Add an output schema (JSON Schema or XSD)
+ Set policy to WARN
+ Run with sample data — check which fields fail validation
+ Fix the transformation until all warnings clear
+ Switch to STRICT for production

=== Production Workflow

+ Message arrives
+ Pre-validate against input schema → reject bad input immediately
+ Transform
+ Post-validate against output schema → catch mapping bugs
+ Send output — guaranteed to match the contract

=== When to Skip Validation

- *Performance-critical pipelines:* validation adds 1-5ms per message. At 86K msg/s, this matters. Skip validation when the input source is trusted and the transformation is well-tested.
- *Pass-through transformations:* when `\$input` is the entire body (no field mapping), validation of the output is the same as validation of the input — redundant.
- *Development/exploration:* when experimenting with data, validation errors slow you down. Use SKIP until the mapping stabilizes.

== Validation and the Conformance Suite

The UTL-X conformance suite (453+ tests) validates transformation correctness — not schema compliance. These are different concerns:

- *Conformance tests:* "Does this transformation produce the expected output?" (functional correctness)
- *Schema validation:* "Does this output match the contract?" (structural compliance)

Both are important. Conformance tests catch regressions. Schema validation catches contract violations. Use both in production.
