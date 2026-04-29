= The UTL-X IDE

While the CLI is powerful for quick transformations and scripting, professional integration development happens in an IDE. UTL-X provides a VS Code extension backed by the utlxd daemon, giving you real-time feedback, autocompletion, and live preview as you write transformations.

This chapter describes the IDE experience and the design-time workflow. If you're using UTL-X purely from the command line, you can skip ahead to Chapter 8 — but the IDE will make you significantly more productive.

== Installing the VS Code Extension

The UTL-X extension is available from the VS Code marketplace:

+ Open VS Code
+ Go to Extensions (Ctrl+Shift+X / Cmd+Shift+X)
+ Search for "UTL-X"
+ Click Install

The extension automatically starts the utlxd daemon when you open a .utlx file. No manual configuration needed — the daemon manages its own lifecycle.

== The Three-Panel Layout

The UTL-X IDE experience is built around three panels, visible simultaneously:

// DIAGRAM: Three-panel IDE layout — Input (left), Transform (center), Output (right)
// Source: part1-foundation.pptx, slide 11

*Left panel — Input:* paste or load sample input data. Select the format (XML, JSON, CSV, YAML, OData). This is your test data — what the transformation will process.

*Center panel — Transformation:* the .utlx editor with syntax highlighting, autocompletion, and real-time error diagnostics. This is where you write your transformation logic.

*Right panel — Output:* the live preview of the transformation result. Updates automatically as you type in the center panel. Shows the output in the declared format (JSON, XML, CSV, YAML).

This three-panel workflow — Input, Transform, Output — is the core of UTL-X development. You see the input, you write the logic, you see the output. No save-compile-run cycle. No switching between terminal and editor. Everything is visible at once.

== Design-Time vs Runtime

Understanding the difference between design-time and runtime is crucial for UTL-X development:

*Design-time* is when you write the transformation:
- You're in VS Code, editing a .utlx file
- utlxd parses your code on every keystroke
- The IDE shows errors, completions, and live preview
- You work with _sample data_ — representative examples, not production messages
- You can experiment freely — nothing is deployed, nothing is production

*Runtime* is when the transformation executes in production:
- utlxe runs the compiled transformation against real messages
- 8--128 workers process messages concurrently
- Schema validation enforces contracts
- Prometheus metrics track throughput and errors
- There is no IDE — the transformation is a deployed artifact

What you create at design-time deploys _unchanged_ to runtime. The .utlx file you write in VS Code is the exact same file that utlxe loads in production. No compilation step, no packaging, no build process — the source IS the artifact.

== Schema-Driven Development

The most productive way to use the IDE is _schema-driven development_: define the input and output schemas first, then write the transformation to connect them.

=== The Professional Approach

+ *Define the input schema:* what does the source data look like? (XSD, JSON Schema, Avro, etc.)
+ *Define the output schema:* what must the target data look like?
+ *Load both schemas in the IDE:* the UDM tree browser shows both structures side-by-side
+ *Write the transformation:* map fields from input to output, guided by the schemas
+ *Validate:* the IDE checks the output against the target schema in real-time

This is how Tibco BW, SAP CPI, and MuleSoft work — but they limit you to XML (XSLT). UTL-X does schema-driven development for _all_ formats: JSON Schema, XSD, Avro, Protobuf, Table Schema, and OData Schema.

=== Scaffolding

When both input and output schemas are defined, the IDE can generate a transformation skeleton:

- All target fields listed with placeholder expressions
- Type conversions where source and target types differ
- Array mappings where cardinality differs
- TODO comments for fields requiring business logic

You fill in the actual mapping logic. Scaffolding accelerates development — it's not a replacement for writing transformations, but it eliminates the boilerplate of creating the output structure manually.

== The UDM Tree Browser

The UDM tree browser is a panel that shows the parsed Universal Data Model as an interactive tree. Load sample data in the input panel, and the tree browser displays the complete structure:

// DIAGRAM: UDM tree browser showing an XML document with element names, types, values, attributes
// Source: part1-foundation.pptx, slide 12

Each node in the tree shows:

- *Property name:* the field name in the data
- *UDM type:* Scalar, Object, Array, DateTime, etc.
- *Value:* for scalar nodes, the actual value
- *Format-specific information:*
  - XML: element name, attributes (\@attr), namespace declarations
  - JSON: type (string, number, boolean, null, object, array)
  - CSV: column index, header name, detected type
  - YAML: anchor/alias references
  - OData: \@odata.type, navigation properties

Click a node to insert the accessor path into the transformation editor. For example, clicking the "Name" node under "Customer" inserts `\$input.Customer.Name` at the cursor position. This eliminates typos and speeds up field mapping.

Expand and collapse nodes to navigate deep structures. Search within the tree to find specific fields in large documents.

== The Function Library

The function library panel lets you browse all 652 standard library functions without leaving the editor:

- *Browse by category:* 18 categories (String, Array, Math, Date, Type, Encoding, XML, JSON, CSV, YAML, Security, Binary, Financial, Geospatial, Utility, Object, Core, Other)
- *Search:* type a keyword to find functions by name or description
- *Function details:* name, signature (parameter types and return type), description, example
- *Click to insert:* click a function name to insert a call template at the cursor

For example, searching "date" shows: now(), today(), parseDate(), formatDate(), addDays(), dateDiff(), and more — with signatures and examples for each.

== Real-Time Diagnostics

The IDE shows errors as you type, without saving or running:

- *Syntax errors:* missing brackets, invalid keywords, unterminated strings — highlighted with red underlines and error messages
- *Unknown functions:* calling a function that doesn't exist in the stdlib — immediately flagged
- *Type warnings:* passing a string where a number is expected (when detectable from context)
- *Schema violations:* when output doesn't match the declared output schema

Errors appear in the Problems panel (Ctrl+Shift+M) with line numbers and descriptions. Click an error to jump to the offending line.

== Autocompletion

The IDE provides context-aware autocompletion:

- *After \$input. :* property names from the parsed input data (if sample data is loaded)
- *After a function name and parenthesis:* parameter hints showing expected types
- *At the start of an expression:* function names, keywords (if, let, def, match, try)
- *Inside an object literal:* available property names from the output schema (if loaded)

Autocompletion uses the utlxd daemon's real-time understanding of your transformation — it's not a static word list but a live analysis of the AST and available data.

== The Header Editor

The .utlx header (the part above the separator) can be edited visually:

- *Input format:* dropdown to select XML, JSON, CSV, YAML, OData
- *Output format:* dropdown with format-specific options
- *Output options:* checkboxes and fields for options like writeAttributes, encoding, delimiter
- *Multi-input:* add or remove named inputs with their formats
- *Schema references:* attach input and output validation schemas

The header editor generates the header block — you can always switch to text editing if you prefer manual control.

== Keyboard Shortcuts

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Shortcut*], [*Action*],
  [Ctrl+Space], [Trigger autocompletion],
  [F12], [Go to definition],
  [Shift+F12], [Find all references],
  [Ctrl+Shift+F], [Format document],
  [Ctrl+Shift+M], [Show Problems panel],
  [Ctrl+K Ctrl+I], [Show hover information],
  [Ctrl+Shift+P then "UTL-X"], [All UTL-X commands],
)

== Tips for Productive IDE Usage

*Start with sample data.* Before writing a single line of transformation code, paste representative sample data in the input panel. The UDM tree browser and autocompletion become dramatically more useful when they have real data to analyze.

*Use the live preview.* Don't run transformations from the terminal during development. The live preview updates on every keystroke — you see the output change as you type. This is faster than any save-compile-run cycle.

*Work schema-first.* If you have schemas for both input and output, load them. The IDE validates your output in real-time, catching contract violations before you even save the file.

*Browse functions, don't memorize.* With 652 functions, nobody remembers them all. Use the function library search. Type what you want to do — "convert date", "filter array", "encrypt" — and the library shows relevant functions.

*Trust the red underlines.* If the IDE shows an error, fix it before moving on. Real-time diagnostics catch 90% of issues that would otherwise only surface at runtime.
