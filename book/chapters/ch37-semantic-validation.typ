= Semantic Validation: Beyond Syntax

== Syntactical vs Semantic Validation

=== What UTL-X Does Today: Syntactical Validation
// - Structure validation: "Is this valid XML? Valid JSON?"
// - Schema validation: "Does this match the XSD? The JSON Schema?"
// - Type checking: "Is this field a number? A date? A string?"
// - Cardinality: "Are required fields present? Are arrays the right size?"
// - Pattern matching: "Does the email match a regex? Does the date match ISO 8601?"
//
// Syntactical validation answers: "Is the data STRUCTURALLY correct?"
// It checks the shape, types, and constraints defined in a schema.

=== What UTL-X Does NOT Do: Semantic Validation
// - Business rules: "Is the invoice total == sum of line items?"
// - Cross-field validation: "If country is NL, then VAT ID must start with NL"
// - Temporal logic: "Is the end date after the start date?"
// - Domain constraints: "Is this IBAN valid? Is this BSN valid (11-check)?"
// - State machine: "Can an order move from SHIPPED to PENDING?"
// - Referential integrity: "Does the customer ID exist in the customer master?"
//
// Semantic validation answers: "Is the data MEANINGFUL and CORRECT?"
// It checks business logic that cannot be expressed in a structural schema.

=== The Gap
// - JSON Schema can express: type, required, pattern, enum, min/max, format
// - JSON Schema CANNOT express: if field A then field B must be > field C
// - XSD can express: type, cardinality, pattern, enumeration
// - XSD CANNOT express: sum(lineItems) == total
//
// This gap is where semantic validation languages live.

== Semantic Validation Languages

=== Schematron (XML world)
// - ISO/IEC 19757-3 standard
// - Rule-based validation using XPath assertions
// - Widely used in: UBL/Peppol, HL7 CDA, DITA, government XML
// - Example: Peppol BIS 3.0 has 65+ Schematron rules
//
// Example Schematron rule:
// <sch:rule context="cac:InvoiceLine">
//   <sch:assert test="cbc:LineExtensionAmount =
//     cbc:InvoicedQuantity * cac:Price/cbc:PriceAmount">
//     Line total must equal quantity * unit price
//   </sch:assert>
// </sch:rule>
//
// Strengths: mature, standardized, XPath-based (familiar to XML developers)
// Limitations: XML-only — cannot validate JSON, CSV, YAML

=== JSONTron
// - Schematron-inspired but for JSON
// - Uses JSONPath instead of XPath for assertions
// - Not widely adopted (no ISO standard)
// - Concept: same rule-based approach, different query language
//
// Example:
// { "rule": { "context": "$.invoice.lines[*]",
//   "assert": { "test": "@.total == @.quantity * @.unitPrice",
//               "message": "Line total must equal quantity * price" }}}
//
// Strengths: JSON-native, familiar pattern for JSON developers
// Limitations: no standard, limited tooling, no XML/CSV/YAML support

=== FEEL (Friendly Enough Expression Language)
// - Part of DMN (Decision Model and Notation) — OMG standard
// - Expression language for business rules and decisions
// - Used in: Camunda, jBPM, Kogito, Drools
// - Rich type system: dates, durations, ranges, contexts
//
// Example FEEL expressions:
// - invoice.total = sum(invoice.lines.amount)
// - if order.country = "NL" then vat.rate = 21 else vat.rate = 0
// - date(order.delivery) > date(order.placed) + duration("P2D")
//
// Strengths: readable, standardized (OMG), rich temporal/numeric expressions
// Limitations: not schema-aware, designed for decision tables not validation
// Opportunity: UTL-X could embed FEEL as a validation expression language

=== Other Approaches
// - OPA/Rego (Open Policy Agent): policy-as-code, used in Kubernetes admission
// - CUE: configuration language with built-in validation constraints
// - Ajv custom keywords: extend JSON Schema with JavaScript validators
// - CEL (Common Expression Language): Google's lightweight expression language

== How Semantic Validation Could Work in UTL-X

=== Option 1: Built-in assertion syntax
// - Add an `assert` keyword to the UTL-X language
// - assert sum($output.lines.total) == $output.grandTotal, "Totals must match"
// - Runs after transformation, before output serialization
// - Uses existing UTL-X expressions — no new language to learn
// - Natural extension of the existing validation orchestrator

=== Option 2: Schematron support for XML output
// - Parse Schematron rules (.sch files)
// - Apply to XML output using XPath evaluation
// - Standard-compliant: works with existing Peppol/UBL Schematron rules
// - Only works for XML output (not JSON, CSV, YAML)

=== Option 3: FEEL integration for cross-format validation
// - Embed a FEEL evaluator
// - Validate against FEEL expressions regardless of output format
// - {validate: "sum(lines.amount) = total", message: "Totals mismatch"}
// - Works for JSON, XML, CSV, YAML output
// - Requires a FEEL parser/evaluator (significant effort)

=== Option 4: Validation DSL (UTL-X native)
// - Define validation rules in the .utlx header:
//   validate {
//     assert sum($output.lines.total) == $output.grandTotal
//     assert $output.issueDate < $output.dueDate
//     assert if ($output.country == "NL") $output.vatId matches "NL[0-9]{9}B[0-9]{2}"
//   }
// - Uses the existing UTL-X expression syntax
// - No external language dependency
// - Integrated with the validation orchestrator (PRE → TRANSFORM → POST → SEMANTIC)

=== Recommended Approach
// - Short term: Option 4 (native assert syntax) — minimal effort, maximum value
// - Medium term: Option 2 (Schematron) — for Peppol/UBL compliance
// - Long term: Option 3 (FEEL) — if DMN/decision table market is pursued
//
// The key insight: UTL-X already has a rich expression language with 652 functions.
// Adding semantic validation is an extension of existing capabilities, not a new language.
