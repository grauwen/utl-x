= XML Attribute Handling: Design Decisions

JSON and YAML have no concept of "attributes." XML has two ways to attach data to an element: child elements (`<Customer><Name>Alice</Name></Customer>`) and attributes (`<Customer name="Alice"/>`). When converting XML to JSON or YAML, what happens to the attributes? There is no standard answer — every tool makes different choices.

This chapter documents UTL-X's design decisions, the industry conventions that informed them, and the `writeAttributes` option that gives you control.

== How UTL-X Handles Attributes Internally

XML attributes are stored separately from child elements in the UDM. When UTL-X parses `<Total currency="EUR">299.99</Total>`, it creates:

```
UDM.Object(
  name = "Total",
  properties = {"_text" → Scalar("299.99")},   ← text content
  attributes = {"currency" → "EUR"}              ← XML attributes
)
```

Properties and attributes live in separate maps — they never collide. An element could theoretically have a child element and an attribute with the same name, and UTL-X handles both correctly.

Access is clean and unambiguous:

```utlx
$input.Total              // 299.99 (text content, auto-unwrapped)
$input.Total.@currency    // "EUR" (attribute value)
```

The `@` prefix distinguishes attribute access from property access. No guessing, no ambiguity.

== The \_text Convention

When an XML element has text content, UDM stores it as a property named `_text`. This is an *internal* convention — you never see it in output.

Why does `_text` exist? UDM Objects store properties as a `Map<String, UDM>`. There is no dedicated "value" field for text content. The `_text` key gives the text a place in the map — necessary for elements that have both text AND attributes or child elements.

When you write `$input.Total`, UTL-X automatically unwraps `_text` and returns the value `299.99` directly. You never need to write `$input.Total._text` — the unwrapping is invisible.

This was refined through two bug fixes:
- *B13:* `_text` was not auto-unwrapped during property access — fixed so `$input.Total` returns `299.99`, not `{_text: 299.99}`
- *B14:* `_text` leaked into JSON/YAML output — fixed so serializers unwrap it before writing

After these fixes, `_text` is a pure internal implementation detail.

== The writeAttributes Option

When XML is serialized to JSON or YAML, leaf elements with both text AND attributes present a dilemma: should the output be a simple value (losing the attribute) or an object (preserving it but changing the structure)?

UTL-X follows the MuleSoft DataWeave convention with a `writeAttributes` option:

```utlx
output json                          // default: writeAttributes=false
output json {writeAttributes: true}  // opt-in: preserve leaf attributes
```

=== Default Behavior (writeAttributes: false)

```xml
<Total currency="EUR">299.99</Total>
```

JSON output:

```json
"Total": 299.99
```

The `currency` attribute is dropped. The output is clean — most JSON consumers don't expect XML attributes and would not know what to do with them.

=== With writeAttributes: true

```xml
<Total currency="EUR">299.99</Total>
```

JSON output:

```json
"Total": {"@currency": "EUR", "#text": 299.99}
```

The attribute is preserved with an `@` prefix. The text content moves to a `#text` key. The element becomes an object instead of a plain value.

== What Gets Kept vs Dropped

Not all attributes are affected by the `writeAttributes` setting. The rule depends on the element type:

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*XML Element*], [*Output (default)*], [*Attributes*], [*Why*],
  [`<Order id="ORD-001">...</Order>`], [`"@id": "ORD-001"`], [Kept], [Non-leaf: has children, always an object],
  [`<Customer>Alice</Customer>`], [`"Alice"`], [N/A], [No attributes to lose],
  [`<Total currency="EUR">299.99</Total>`], [`299.99`], [Dropped], [Leaf + text: unwrapped to plain value],
  [`<Sales type="direct"/>`], [`"@type": "direct"`], [Kept], [Self-closing: no text to unwrap],
)

The rule: only *leaf elements with both text AND attributes* lose their attributes in default mode. Non-leaf elements (which have children) are already objects, so their attributes fit naturally as `@`-prefixed properties. Self-closing elements have no text to unwrap, so they stay as objects with their attributes.

== Why Drop by Default?

This is a deliberate design choice, not an oversight:

- *Most JSON/YAML consumers don't expect attributes.* A downstream REST API expects `"total": 299.99`, not `"total": {"@currency": "EUR", "#text": 299.99}`. Clean output is more important than completeness for the common case.
- *DataWeave compatibility.* MuleSoft's DataWeave uses the same default (`writeAttributes: false`). Developers moving between tools find familiar behavior.
- *Explicit access is always available.* Even with `writeAttributes: false`, you can still read attributes in your transformation: `$input.Total.@currency` works regardless of the output setting. The setting only affects *serialization*, not *access*.
- *Power users opt in.* When you need full fidelity — XML digital signatures, compliance round-trips, schema-driven output — set `writeAttributes: true`. One option, full control.

== JSON and YAML Consistency

Both serializers use the same convention for attributes:

```json
// JSON
{"@currency": "EUR", "#text": 299.99}

// YAML
'@currency': EUR
'#text': 299.99
```

The `@` prefix for attributes and `#text` for text content are identical in both formats. Previously, the YAML serializer used a non-standard `_attributes` block — a UTL-X invention that no other tool recognized. This was replaced with inline `@` prefix to match JSON and industry conventions.

== Industry Conventions

UTL-X's choice is informed by established XML-to-JSON conventions:

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Convention*], [*Attribute prefix*], [*Text key*], [*Used by*],
  [Badgerfish], [`@`], [`$`], [Java XML libs, .NET Json.NET],
  [Parker], [(dropped)], [plain value], [Simple converters],
  [GData], [(none — collision risk)], [`$t`], [Google APIs],
  [DataWeave], [`@`], [(value or `__text`)], [MuleSoft],
  [yq], [`+@` (configurable)], [`+content`], [CLI tool],
  [*UTL-X*], [*`@`*], [*`#text`*], [*Follows DataWeave convention*],
)

The `@` prefix for attributes is the de facto industry standard. The text key varies (`$`, `$t`, `__text`, `#text`) — UTL-X uses `#text` which is distinct from any reserved UTL-X symbol and reads naturally.

== FHIR XML: The value Attribute Convention

HL7 FHIR uses `value` attributes instead of text content — a deliberate design choice for extensibility (every FHIR element can have child extensions):

```xml
<!-- FHIR style (value attribute): -->
<birthDate value="1955-03-12"/>

<!-- Standard XML style (text content): -->
<birthDate>1955-03-12</birthDate>
```

UTL-X handles FHIR naturally via the `@` accessor:

```utlx
$input.Patient.birthDate.@value     // "1955-03-12"
$input.Patient.name[0].family.@value  // "Simpson"
$input.Patient.active.@value        // "true"
```

No special FHIR mode needed — the `@` accessor works for all XML attributes, including FHIR's `value` convention. Since FHIR elements are self-closing (no text content), `writeAttributes` does not affect them — their attributes are always preserved.
