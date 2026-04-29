= XML Transformations

== XML in UTL-X
// - How XML maps to UDM (elements, attributes, text, namespaces)
// - The _text convention (internal, never in output)
// - Attribute access: $input.Order.@id
// - Namespace handling: xmlns declarations

== Reading XML
// - Element access: $input.Order.Customer
// - Attribute access: $input.Order.@id
// - Text content: automatic unwrapping (B13/B14)
// - Repeated elements: automatic array detection
// - Array hints: forcing single elements to arrays
// - Namespace-prefixed elements: $input.Order["cbc:ID"]

== Writing XML
// - Object-to-element mapping
// - Creating attributes: @attributeName syntax
// - Text content with attributes: _text + @attr
// - Namespace declarations in output
// - Self-closing elements: empty objects with attributes
// - XML declaration and encoding

== XML Attributes in JSON/YAML Output
// - Default behavior (writeAttributes=false): leaf attributes dropped
// - writeAttributes=true: @attr + #text preserved
// - The B14 story: why this design decision was made
// - Industry conventions: Badgerfish, Parker, DataWeave

== Common XML Patterns
// - SOAP envelope parsing
// - UBL invoice transformation (Peppol BIS 3.0)
// - HL7 FHIR XML (value attribute convention)
// - RSS/Atom feed processing
// - SVG/HTML transformation
// - EDI/EDIFACT to XML mapping

== XML Encoding
// - detectXMLEncoding: auto-detect from declaration
// - convertXMLEncoding: UTF-8 ↔ ISO-8859-1 ↔ Windows-1252
// - BOM handling: stripBOM, detectBOM

== XML Namespaces Deep Dive
// - Default namespace: xmlns="..."
// - Prefixed namespace: xmlns:cbc="..."
// - Namespace inheritance: parent → child
// - The inheritNamespaces option
// - Accessing namespaced elements in transformations
