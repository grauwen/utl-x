= The Universal Data Model (UDM)

== Why UDM Exists
// - The bridge between all formats
// - XML has attributes, namespaces, mixed content
// - JSON has objects, arrays, null
// - CSV has rows, headers, delimiters
// - UDM normalizes all into one representation

== UDM Types
// - Scalar: strings, numbers, booleans, null
// - Object: properties (Map<String, UDM>) + attributes (Map<String, String>) + name + metadata
// - Array: ordered list of UDM elements
// - DateTime, Date, LocalDateTime, Time: temporal types
// - Binary: byte arrays
// - Lambda: function values

== How Formats Map to UDM
// - JSON → UDM: direct mapping (objects, arrays, scalars)
// - XML → UDM: elements become Objects, attributes stored separately, _text for leaf content
// - CSV → UDM: rows become array of objects, headers become property names
// - YAML → UDM: same as JSON (YAML is a superset)
// - OData → UDM: entity sets, navigation properties

== XML-Specific UDM Conventions
// - _text: internal key for text content of XML elements
// - attributes map: XML attributes stored separately from properties
// - @attribute: accessor syntax for reading attributes
// - Namespace handling: xmlns declarations, namespace context metadata
// - The B13/B14 story: why _text exists and how serializers handle it

== UDM Navigation
// - Dot notation: $input.Order.Customer
// - Index access: $input.Items[0]
// - Attribute access: $input.Order.@id
// - Recursive descent: $input..ProductCode
// - Safe navigation: $input.Order?.Customer

== UDM and Type Coercion
// - asString(), asNumber(), asBoolean()
// - Auto-unwrapping of XML text nodes
// - When coercion happens (explicit vs implicit)
// - toNumber(), toBoolean(), toString() stdlib functions
