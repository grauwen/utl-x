= XSD Design Patterns Deep Dive

XML Schema (XSD) is the most complex schema format in use today. The same data structure can be expressed in fundamentally different ways — and the pattern choice affects reusability, tooling compatibility, and how well the schema converts to other formats. This chapter covers the design patterns you'll encounter in enterprise XML and how UTL-X handles each one.

== Why Patterns Matter

Consider a simple order with a customer and address. In XSD, there are at least five valid ways to express this. Each produces a valid schema that validates the same XML — but they differ in how types are defined, where elements are declared, and how reusable the components are.

When you convert an XSD to JSON Schema, Avro, or Protobuf, the pattern determines the output structure. A schema with named reusable types converts cleanly to JSON Schema `$defs`. A schema with deeply nested anonymous types produces a monolithic JSON Schema with no reusable definitions. Understanding the pattern tells you what to expect.

== The Five Core Patterns

=== 1. Russian Doll

Everything nested inside one global element. Types are defined inline (anonymous), deeply nested — like a matryoshka doll: open one, find another inside.

```xml
<xs:element name="Order">
  <xs:complexType>
    <xs:sequence>
      <xs:element name="Customer">
        <xs:complexType>
          <xs:sequence>
            <xs:element name="Name" type="xs:string"/>
            <xs:element name="Address">
              <xs:complexType>
                <xs:sequence>
                  <xs:element name="Street" type="xs:string"/>
                  <xs:element name="City" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
            </xs:element>
          </xs:sequence>
        </xs:complexType>
      </xs:element>
    </xs:sequence>
  </xs:complexType>
</xs:element>
```

*Pros:* self-contained, one file, clear hierarchy, easy to read top-to-bottom.

*Cons:* no reuse — types cannot be referenced from other elements or schemas. The Address type is buried inside Customer inside Order. If another element needs the same Address structure, it must be duplicated.

*Used by:* simple schemas, auto-generated XSDs, quick prototypes.

*Converting to JSON Schema:* produces nested inline definitions — no `$defs` section. The JSON Schema is as deeply nested as the XSD.

=== 2. Salami Slice

All elements are global (top-level). Types may be inline, but elements are independently defined and assembled via `ref`:

```xml
<xs:element name="Street" type="xs:string"/>
<xs:element name="City" type="xs:string"/>

<xs:element name="Address">
  <xs:complexType>
    <xs:sequence>
      <xs:element ref="Street"/>
      <xs:element ref="City"/>
    </xs:sequence>
  </xs:complexType>
</xs:element>

<xs:element name="Customer">
  <xs:complexType>
    <xs:sequence>
      <xs:element name="Name" type="xs:string"/>
      <xs:element ref="Address"/>
    </xs:sequence>
  </xs:complexType>
</xs:element>
```

*Pros:* elements reusable via `ref`, substitution groups possible, flat structure.

*Cons:* all elements share one global namespace — collision risk. Every element is a potential document root.

*Used by:* some industry standards, schemas designed for element substitution.

*Converting to JSON Schema:* global elements become `$defs` entries. References become `$ref` pointers.

=== 3. Venetian Blind

All types are global (named), elements reference them by type. Types are reusable, elements are local:

```xml
<xs:complexType name="AddressType">
  <xs:sequence>
    <xs:element name="Street" type="xs:string"/>
    <xs:element name="City" type="xs:string"/>
  </xs:sequence>
</xs:complexType>

<xs:complexType name="CustomerType">
  <xs:sequence>
    <xs:element name="Name" type="xs:string"/>
    <xs:element name="Address" type="AddressType"/>
  </xs:sequence>
</xs:complexType>

<xs:element name="Order">
  <xs:complexType>
    <xs:sequence>
      <xs:element name="Customer" type="CustomerType"/>
    </xs:sequence>
  </xs:complexType>
</xs:element>
```

*Pros:* maximum type reuse, clean separation of types from elements, extensible via type derivation.

*Cons:* more files to manage, requires type naming discipline.

*Used by:* most enterprise standards — UBL, HL7 CDA, ISO 20022, SWIFT, XBRL. This is the dominant pattern in professional XML.

*Converting to JSON Schema:* named types map directly to `$defs` with `$ref`. This is the cleanest conversion because the type structure already matches JSON Schema's reference model.

=== 4. Garden of Eden

Both types AND elements are global. The combination of Salami Slice and Venetian Blind — maximum reuse and substitutability:

```xml
<xs:complexType name="AddressType">
  <xs:sequence>
    <xs:element ref="Street"/>
    <xs:element ref="City"/>
  </xs:sequence>
</xs:complexType>

<xs:element name="Street" type="xs:string"/>
<xs:element name="City" type="xs:string"/>
<xs:element name="Address" type="AddressType"/>
```

*Pros:* ultimate reuse — both types and elements are independently referenceable. Supports the most advanced XSD features (substitution groups, type derivation, element polymorphism).

*Cons:* the most complex pattern to read and maintain. Requires careful namespace management.

*Used by:* W3C specifications, meta-standards, large-scale schema architectures.

=== 5. Swiss Army Knife (Hybrid)

A pragmatic mix of global and local types and elements. Use whatever works for each part — named types for reusable structures, inline types for one-off elements:

```xml
<!-- Named type for reuse -->
<xs:complexType name="AddressType">
  <xs:sequence>
    <xs:element name="Street" type="xs:string"/>
    <xs:element name="City" type="xs:string"/>
  </xs:sequence>
</xs:complexType>

<!-- Inline type (one-off) -->
<xs:element name="Order">
  <xs:complexType>
    <xs:sequence>
      <xs:element name="OrderId" type="xs:string"/>
      <xs:element name="ShippingAddress" type="AddressType"/>
      <xs:element name="Notes">
        <xs:simpleType>
          <xs:restriction base="xs:string">
            <xs:maxLength value="500"/>
          </xs:restriction>
        </xs:simpleType>
      </xs:element>
    </xs:sequence>
  </xs:complexType>
</xs:element>
```

*Pros:* practical, flexible, reflects how most people actually write schemas.

*Cons:* inconsistent — harder to tool and convert because the pattern varies within the same file.

*Used by:* most hand-written schemas, internal enterprise schemas, schemas that evolved organically.

== Advanced Patterns

=== Chameleon Include

A schema file with no `targetNamespace` that adopts the namespace of whatever schema includes it. Like a chameleon changing color:

```xml
<!-- common-types.xsd (no targetNamespace) -->
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:complexType name="AddressType">...</xs:complexType>
</xs:schema>

<!-- order.xsd (has targetNamespace) -->
<xs:schema targetNamespace="urn:example:orders" ...>
  <xs:include schemaLocation="common-types.xsd"/>
  <!-- AddressType now belongs to urn:example:orders -->
</xs:schema>
```

This allows the same reusable types file to be included by multiple schemas with different namespaces. UTL-X resolves chameleon includes correctly — the types are assigned the including schema's namespace.

=== Abstract Types

Uses abstract complex types and substitution groups to achieve polymorphism — the XSD equivalent of interfaces and inheritance:

```xml
<xs:complexType name="PaymentType" abstract="true"/>

<xs:complexType name="CreditCardPayment">
  <xs:complexContent>
    <xs:extension base="PaymentType">
      <xs:sequence>
        <xs:element name="CardNumber" type="xs:string"/>
      </xs:sequence>
    </xs:extension>
  </xs:complexContent>
</xs:complexType>

<xs:complexType name="BankTransferPayment">
  <xs:complexContent>
    <xs:extension base="PaymentType">
      <xs:sequence>
        <xs:element name="IBAN" type="xs:string"/>
      </xs:sequence>
    </xs:extension>
  </xs:complexContent>
</xs:complexType>
```

When converting to JSON Schema, abstract types become `oneOf` compositions. When converting to Protobuf, they become `oneof` groups. UTL-X preserves the inheritance structure through USDL.

== The Pattern Matrix

Every XSD pattern is determined by three structural decisions: whether elements are global or local, whether types are global (named) or local (anonymous), and how composition is handled (include, import, abstract). The full matrix:

#table(
  columns: (auto, auto, auto, auto, auto),
  align: (left, left, left, left, left),
  [*Pattern*], [*Elements*], [*Types*], [*Composition*], [*Reusability*],
  [1. Russian Doll], [Local (nested)], [Local (anonymous)], [None], [None — self-contained],
  [2. Salami Slice], [Global], [Local (anonymous)], [`ref` to global elements], [Element reuse],
  [3. Venetian Blind], [Local], [Global (named)], [`type` references], [Type reuse],
  [4. Garden of Eden], [Global], [Global (named)], [`ref` + `type` references], [Both reusable],
  [5. Swiss Army Knife], [Mixed], [Mixed], [Mixed], [Varies per section],
  [6. Chameleon], [Local], [Global (named)], [Namespace-less `include`], [Cross-namespace reuse],
  [7. Abstract], [Global], [Global (abstract)], [Substitution groups], [Polymorphic reuse],
  [8. Adapter], [Local], [Global (wrapping)], [`import` + wrapper types], [Cross-schema bridging],
)

The first four are the classical patterns described by the XML Schema community. Patterns 5-8 are pragmatic variations found in real-world enterprise schemas. The three parameters that determine the pattern:

+ *Element scope* — are elements declared at the schema top level (global) or nested inside other types (local)?
+ *Type scope* — are complex types named at the schema top level (global/named) or defined inline where they're used (local/anonymous)?
+ *Composition mechanism* — how are components connected? Element `ref`, type name reference, `xs:include`, `xs:import`, substitution groups, or a mix?

== Pattern Detection

UTL-X can analyze an XSD and identify which pattern it uses. This is useful when:

- Receiving an unfamiliar schema from a partner or vendor
- Choosing the best conversion strategy to another format
- Deciding how to refactor a schema for better reusability

The analysis looks at the ratio of global vs local elements and types:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Pattern*], [*Global elements*], [*Global types*],
  [Russian Doll], [1 (root only)], [0 (all inline)],
  [Salami Slice], [Many], [Few (inline)],
  [Venetian Blind], [1 (root only)], [Many],
  [Garden of Eden], [Many], [Many],
  [Swiss Army Knife], [Mixed], [Mixed],
)

=== Accessing Pattern Metadata with ^

When UTL-X parses an XSD, the parser attaches metadata to the UDM tree — including the detected pattern. Access it with the `^` prefix (see Chapter 8):

```utlx
%utlx 1.0
input xsd
output json
---
{
  pattern: $input.^xsdPattern,           // "venetian-blind", "russian-doll", etc.
  version: $input.^xsdVersion,           // "1.0" or "1.1"
  globalElements: $input.^xsdGlobalElements,  // "3"
  globalTypes: $input.^xsdGlobalTypes,        // "12"
  inlineTypes: $input.^xsdInlineTypes         // "0"
}
```

Available XSD metadata keys:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Metadata key*], [*Value*],
  [`^schemaType`], [UDM node type: `"xsd-schema"`, `"xsd-element"`, `"xsd-complexType"`, etc.],
  [`^xsdPattern`], [Detected design pattern: `"russian-doll"`, `"venetian-blind"`, `"salami-slice"`, `"garden-of-eden"`, `"swiss-army-knife"`],
  [`^xsdVersion`], [XSD version: `"1.0"` or `"1.1"`],
  [`^xsdGlobalElements`], [Count of global element declarations (as string)],
  [`^xsdGlobalTypes`], [Count of global type declarations (as string)],
  [`^xsdInlineTypes`], [Count of anonymous/inline type declarations (as string)],
  [`^xsdElementDeclaration`], [Per-element: `"global"` or `"local"`],
  [`^xsdTypeDeclaration`], [Per-type: `"global"` or `"local"`],
)

This metadata is transient — it exists during transformation but is never included in the output. It's the parser telling you about the schema's structure so you can make decisions in your transformation.

== Pattern Conversion

UTL-X can convert between patterns. The most common conversion: Russian Doll to Venetian Blind — extracting inline anonymous types into named reusable types.

```utlx
%utlx 1.0
input xsd
output xsd {pattern: "venetian-blind"}
---
$input
```

This reads a Russian Doll XSD and outputs a Venetian Blind XSD — same structure, same validation rules, but with named types that can be reused and referenced. The reverse (Venetian Blind to Russian Doll) inlines all named types back into their usage points.

== Patterns in Cross-Format Conversion

The XSD pattern directly affects the quality of cross-format conversion:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*XSD Pattern*], [*JSON Schema result*], [*Quality*],
  [Russian Doll], [Deeply nested inline, no `$defs`], [Works but not reusable],
  [Venetian Blind], [Clean `$defs` with `$ref`], [Excellent — idiomatic JSON Schema],
  [Salami Slice], [`$defs` from global elements], [Good],
  [Garden of Eden], [Rich `$defs` with both types and elements], [Good but verbose],
  [Swiss Army Knife], [Mixed inline and `$ref`], [Varies by section],
)

If you're converting enterprise XSD to JSON Schema, Avro, or Protobuf, Venetian Blind produces the best results. If the source is Russian Doll, consider converting to Venetian Blind first (`output xsd {pattern: "venetian-blind"}`), then converting to the target format.

== Real-World XSD Architectures

=== UBL (Universal Business Language)

UBL uses Venetian Blind with a deep type hierarchy:

```
UBL-Invoice-2.1.xsd
  └── imports: UBL-CommonAggregateComponents-2.1.xsd
        └── imports: UBL-CommonBasicComponents-2.1.xsd
              └── imports: UBL-UnqualifiedDataTypes-2.1.xsd
                    └── imports: CCTS_CCT_SchemaModule-2.1.xsd
```

Five levels of imports, hundreds of named types. UTL-X resolves the entire chain and makes all types available in a single type graph.

=== ISO 20022 (Financial Messaging)

ISO 20022 (SWIFT MX) uses Venetian Blind with strict naming conventions — every type name ends with a version number (`CustomerCreditTransferInitiationV11`). Hundreds of message types share common component types. UTL-X handles the full ISO 20022 library.

=== HL7 CDA (Clinical Document Architecture)

Healthcare's CDA uses a mix of Venetian Blind and abstract types for clinical document extensibility. Abstract types (`ActRelationship`, `Participation`) are specialized via restriction — the XSD equivalent of Java interfaces. UTL-X preserves the inheritance structure during conversion.

== XSD Patterns in Integration Tools

Different integration tools generate and consume XSD in different patterns. Knowing which pattern a tool uses helps you choose the right conversion strategy when migrating or integrating:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Tool*], [*XSD pattern*], [*Notes*],
  [TIBCO BusinessWorks], [Russian Doll], [BW's schema editor generates deeply nested inline types. One global root element with all types anonymous. Simple to read but no reuse — types are locked inside their parent element. When migrating from BW, convert to Venetian Blind first for cleaner downstream conversion.],
  [IBM WTX / Mercator], [Venetian Blind], [Named types with explicit type trees. Strong separation between structure and content.],
  [SAP PI/PO], [Venetian Blind + imports], [IDoc XSDs use named types across multiple imported schema files. Heavy use of `xs:import` for cross-namespace composition.],
  [MuleSoft DataWeave], [Venetian Blind], [Anypoint Platform generates Venetian Blind from RAML/OAS type definitions.],
  [Microsoft BizTalk], [Russian Doll (default)], [BizTalk's schema editor defaults to Russian Doll with a flat editor view. Can be configured for other patterns but rarely is.],
  [Oracle SOA Suite], [Garden of Eden], [Uses both global elements and global types for maximum substitutability in BPEL processes.],
  [WSO2], [Venetian Blind], [WSDL-first approach with named types.],
)
