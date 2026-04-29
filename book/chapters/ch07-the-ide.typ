= The UTL-X IDE

== IDE Layout and Panels
// - Input panel: paste or load input data (XML, JSON, CSV, YAML, OData)
// - Transformation panel: the .utlx editor with syntax highlighting
// - Output panel: live preview of transformation result
// - The three-panel workflow: Input → Transform → Output (side by side)
// - UDM Tree Browser: inspect the parsed UDM with format-specific annotations
// - Function Library panel: browse 652 functions by category, search, signature preview
// - Diagnostics panel: real-time errors, warnings, suggestions

== Design-Time vs Runtime
// - Design-time: writing the transformation, schema mapping, testing with sample data
//   - Schema-to-schema mapping: define the contract FIRST
//   - Scaffolding: generate transformation skeleton from input/output schemas
//   - Live preview: see output update as you type
//   - Type checking: catch errors before running
//   - Autocompletion: function names, property paths from schema
//
// - Runtime: executing the transformation in production
//   - utlxe engine: HTTP/gRPC transport, 8-128 workers
//   - No IDE needed — transformation is a deployed artifact
//   - Hot reload: update without restart
//   - Monitoring: Prometheus metrics, health probes
//
// The IDE is a design-time tool. utlxe is a runtime tool.
// What you create in the IDE deploys unchanged to utlxe.

== Schema-to-Schema Mapping (Design-Time First)
// - The professional approach: define input AND output schemas BEFORE writing the transformation
// - Input schema: "What does my source data look like?" (XSD, JSON Schema, Avro, etc.)
// - Output schema: "What must my target data look like?" (XSD, JSON Schema, Avro, etc.)
// - The IDE shows both schemas side-by-side
// - Draw mapping lines between source and target fields
// - Generate UTL-X transformation skeleton from the mapping
// - Fill in business logic (calculations, conditions, lookups)
// - Validate output against target schema
//
// This is how Tibco BW, SAP CPI, and Azure Logic Apps work —
// but they only support XML (XSLT). UTL-X does this for ALL formats.

== Scaffolding
// - Given: input schema + output schema
// - Generate: .utlx transformation with:
//   - All target fields listed with TODO comments
//   - Type conversions where source/target types differ
//   - Array mappings where cardinality differs
//   - Placeholder expressions for business logic
// - The developer fills in the actual mapping logic
// - Scaffolding accelerates development — not a replacement for writing logic

== The UDM Tree Browser
// - Shows the parsed Universal Data Model as an interactive tree
// - Each node shows:
//   - Property name
//   - UDM type (Scalar, Object, Array, DateTime, etc.)
//   - Value (for scalars)
//   - Format-specific information:
//     - XML: element name, attributes (@attr), namespace declarations, _text
//     - JSON: type (string, number, boolean, null, object, array)
//     - CSV: column index, header name, detected type
//     - YAML: anchor/alias references, tag type
//     - OData: @odata.type, navigation properties, entity key
// - Click a node → inserts the accessor path into the transformation editor
//   ($input.Order.Customer.Name)
// - Expand/collapse nodes for deep structures
// - Search within the tree

== The Function Library
// - Browse all 652 standard library functions
// - Organized by category (18 categories)
// - For each function:
//   - Name, signature, return type
//   - Description with usage notes
//   - Example: input → function call → output
//   - Related functions
// - Search by name, description, or category
// - Click to insert function call into editor
// - Inline documentation on hover

== The %utlx Header Editor
// - Visual editor for the transformation header
// - Input format selection (dropdown + options)
// - Output format selection (dropdown + options)
// - Multi-input configuration (add/remove named inputs)
// - Output options (writeAttributes, encoding, delimiter, etc.)
// - Schema references (input/output validation)
// - The header generates the %utlx 1.0 / input / output / --- block
