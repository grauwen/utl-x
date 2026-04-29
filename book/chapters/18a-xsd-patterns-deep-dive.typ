= XSD Design Patterns Deep Dive

== The Eight XML Schema Design Patterns

XML Schema (XSD) can be structured in fundamentally different ways. Understanding these patterns is essential for working with enterprise XML — and for understanding why schema-to-schema mapping is challenging.

=== 1. Russian Doll
// - Everything nested inside one global element
// - Types defined inline (anonymous), deeply nested
// - Like a Russian matryoshka doll: open one, find another inside
//
// <xs:element name="Order">
//   <xs:complexType>
//     <xs:sequence>
//       <xs:element name="Customer">
//         <xs:complexType>
//           <xs:sequence>
//             <xs:element name="Name" type="xs:string"/>
//             <xs:element name="Address">
//               <xs:complexType>
//                 ...
//               </xs:complexType>
//             </xs:element>
//           </xs:sequence>
//         </xs:complexType>
//       </xs:element>
//     </xs:sequence>
//   </xs:complexType>
// </xs:element>
//
// Pros: self-contained, one file, clear hierarchy
// Cons: no reuse, types cannot be referenced elsewhere, verbose
// Used by: simple schemas, auto-generated XSDs
// UTL-X: auto-detected, full support for parsing and generation

=== 2. Salami Slice
// - All elements are global (top-level)
// - Types are inline but elements are independently defined
// - Like salami slices: separate pieces, assembled into a sandwich
//
// <xs:element name="Name" type="xs:string"/>
// <xs:element name="Address">
//   <xs:complexType>...</xs:complexType>
// </xs:element>
// <xs:element name="Customer">
//   <xs:complexType>
//     <xs:sequence>
//       <xs:element ref="Name"/>
//       <xs:element ref="Address"/>
//     </xs:sequence>
//   </xs:complexType>
// </xs:element>
//
// Pros: elements reusable via ref, substitution groups possible
// Cons: flat namespace (all elements global), collision risk
// Used by: some industry standards
// UTL-X: auto-detected, ref resolution supported

=== 3. Venetian Blind
// - All types are global (named), elements reference them
// - Types are reusable, elements are local
// - Like Venetian blinds: the slats (types) are shared, arrangement varies
//
// <xs:complexType name="AddressType">
//   <xs:sequence>
//     <xs:element name="Street" type="xs:string"/>
//     <xs:element name="City" type="xs:string"/>
//   </xs:sequence>
// </xs:complexType>
// <xs:complexType name="CustomerType">
//   <xs:sequence>
//     <xs:element name="Name" type="xs:string"/>
//     <xs:element name="Address" type="AddressType"/>
//   </xs:sequence>
// </xs:complexType>
// <xs:element name="Order">
//   <xs:complexType>
//     <xs:sequence>
//       <xs:element name="Customer" type="CustomerType"/>
//     </xs:sequence>
//   </xs:complexType>
// </xs:element>
//
// Pros: maximum type reuse, clean separation, extensible
// Cons: more complex, requires type management
// Used by: most enterprise standards (UBL, HL7, ISO 20022)
// UTL-X: auto-detected, pattern-aware parsing and generation
// This is the DOMINANT pattern in enterprise XML.

=== 4. Garden of Eden
// - BOTH types AND elements are global
// - Maximum reuse, maximum flexibility
// - Combination of Salami Slice + Venetian Blind
//
// Pros: ultimate reuse and substitutability
// Cons: most complex, hardest to read
// Used by: some meta-standards (W3C specifications)
// UTL-X: supported

=== 5. Swiss Army Knife (Hybrid)
// - Mix of global and local types and elements
// - Pragmatic: use whatever works for each part
// - Most real-world schemas end up here
//
// Pros: practical, flexible
// Cons: inconsistent, harder to tool
// Used by: most hand-written schemas
// UTL-X: supported (all patterns handled uniformly)

=== 6-8. Chameleon, Abstract, Adapter
// - Chameleon: namespace-less components that adopt the importing schema's namespace
// - Abstract: uses abstract types and substitution groups for polymorphism
// - Adapter: wraps external schema types for local use
//
// These are advanced patterns used in large-scale schema architectures.
// UTL-X handles them via standard XSD import/include processing.

== Pattern Detection in UTL-X
// UTL-X can detect which pattern an XSD uses:
//   utlx design detect-pattern schema.xsd
//
// Output: "Venetian Blind" (or "Russian Doll", etc.)
// This helps when:
// - Choosing a conversion strategy to JSON Schema
// - Understanding unfamiliar schemas
// - Deciding how to refactor a schema

== Pattern Conversion in UTL-X
// UTL-X can convert between patterns:
//   Russian Doll → Venetian Blind (extract inline types to named types)
//   Salami Slice → Venetian Blind (convert global elements to type references)
//
// Why: some tools only work well with specific patterns.
// Converting to Venetian Blind is the most useful (maximizes reuse).

== XSD Patterns in Cross-Format Mapping
// When converting XSD → JSON Schema:
// - Russian Doll → nested JSON Schema (inline definitions)
// - Venetian Blind → JSON Schema with $ref (definitions/components)
// - Salami Slice → JSON Schema with $ref (shared definitions)
//
// UTL-X preserves the pattern structure during conversion:
// - Named types → $ref definitions (not inlined)
// - Inline types → inline schemas (not extracted)
// - This ensures round-trip fidelity: XSD → JSON Schema → XSD → same pattern
