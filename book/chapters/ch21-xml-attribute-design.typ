= XML Attribute Handling: Design Decisions

== The Fundamental Problem
// JSON and YAML have no concept of "attributes"
// XML has two ways to attach data to an element:
//   1. Child elements: <Customer><Name>Alice</Name></Customer>
//   2. Attributes: <Customer name="Alice"/>
// When converting XML → JSON/YAML, what happens to attributes?
// There is NO standard answer — every tool makes different choices.

== How UTL-X Handles Attributes Internally (UDM)
// XML attributes are stored SEPARATELY from properties in UDM:
//   UDM.Object(
//     properties = {"_text" → Scalar("299.99")},    ← child elements / text
//     attributes = {"currency" → "EUR"},              ← XML attributes
//     name = "Total"
//   )
//
// This preserves full XML fidelity:
// - Properties and attributes never collide
// - $input.Total → the text value (auto-unwrapped)
// - $input.Total.@currency → the attribute value
// - Both accessible, clearly distinguished by @

== The \_text Convention
// When XML has: <Total currency="EUR">299.99</Total>
// UDM stores text content as a property named "_text"
// This is an INTERNAL convention — never visible in output (after B14 fix)
//
// Why _text exists:
// - UDM.Object stores properties as Map<String, UDM>
// - There's no "value" field on UDM.Object for text content
// - _text is the key for the text value
// - This is necessary for elements that have BOTH text AND attributes
//
// History:
// - B13 bug: _text was not auto-unwrapped during property access (fixed)
// - B14 bug: _text leaked into JSON/YAML output (fixed)
// - After B14: _text is invisible in output — serializers unwrap it

== The writeAttributes Option
// Following MuleSoft DataWeave convention:
//
// output json                          ← default: writeAttributes=false
// output json {writeAttributes: true}  ← opt-in: preserve leaf attributes
//
// Default (writeAttributes=false):
//   <Total currency="EUR">299.99</Total>  →  "Total": 299.99
//   (currency attribute dropped — clean output)
//
// With writeAttributes=true:
//   <Total currency="EUR">299.99</Total>  →  "Total": {"@currency": "EUR", "#text": 299.99}
//   (attribute preserved, text content uses #text key)

== What Gets Kept vs Dropped (Default)
//
// | XML Element | Output | Attributes | Why |
// |-------------|--------|------------|-----|
// | <Order id="ORD-001">...</Order> | @id: "ORD-001" | KEPT | Non-leaf: has children |
// | <Customer>Alice</Customer> | "Alice" | N/A | Leaf, no attributes |
// | <Total currency="EUR">299.99</Total> | 299.99 | DROPPED | Leaf+text: unwrapped |
// | <Sales type="direct"/> | @type: "direct" | KEPT | Self-closing: no text |
//
// The rule: only LEAF elements with BOTH text AND attributes lose their attributes
// in default mode. Everything else is preserved.

== Why Drop by Default? (DataWeave-Compatible)
// - MuleSoft DataWeave uses the same default (writeAttributes=false)
// - Most JSON/YAML consumers don't expect XML attributes
// - Clean output is more important than completeness for the common case
// - Users who need attributes: map explicitly ($input.Order.Total.@currency)
// - Power users: opt into writeAttributes=true for full fidelity
// - Zero breaking change risk (default matches existing behavior)

== JSON vs YAML Consistency
// Both serializers use the SAME convention:
//
// JSON: "@currency": "EUR"    (@ prefix, inline with other properties)
// YAML: '@currency': EUR      (@ prefix, inline — same as JSON)
//
// Previously, YAML used a non-standard "_attributes" block.
// This was a UTL-X invention — no other tool uses it.
// Fixed to use @ prefix inline (same as JSON, same as industry convention).

== Industry Conventions Reference
//
// | Convention | Attribute prefix | Text key | Used by |
// |-----------|-----------------|----------|---------|
// | Badgerfish | @ | $ | Java XML libs, .NET |
// | Parker | (dropped) | plain value | Simple converters |
// | GData | (none) | $t | Google APIs |
// | DataWeave | @ | (value) | MuleSoft |
// | yq | +@ (configurable) | +content | CLI tool |
// | **UTL-X** | **@** | **#text** | **Follows DataWeave** |

== FHIR XML: The value Attribute Convention
// HL7 FHIR uses value= attributes instead of text content:
//   <birthDate value="1955-03-12"/>  (not <birthDate>1955-03-12</birthDate>)
//
// This is by FHIR design (extensibility — every element can have child extensions)
//
// UTL-X handles FHIR via @value accessor:
//   $input.Patient.birthDate.@value → "1955-03-12"
//
// No special FHIR mode needed — the @ accessor works for all XML attributes.
// See FHIR analysis chapter for complete details.
