= Format Support Overview

UTL-X supports 11 formats organized in two tiers. Tier 1 formats are _data formats_ — you transform instances of these daily. Tier 2 formats are _schema formats_ — you work with these when defining contracts, validating data, or converting between schema standards.

This chapter provides an overview of all supported formats, their key characteristics, and the conversion matrix. Detailed treatment of each format is in Part III (Chapters 20--28).

== Tier 1: Data Formats

Data formats carry the actual business data — orders, invoices, patient records, sensor readings, configurations.

=== JSON

The lingua franca of modern APIs. Every REST service, every frontend framework, every NoSQL database speaks JSON.

- *Parser:* recursive descent, handles all JSON types including nested structures
- *Number handling:* preserves integer vs float distinction (42 vs 42.0)
- *Encoding:* UTF-8 (standard), auto-detects BOM
- *Options:* `{writeAttributes: true}` for XML attribute preservation
- *Round-trip:* JSON → UDM → JSON is lossless

=== XML

The enterprise workhorse. SOAP services, EDI, UBL invoices, HL7 healthcare, government data — XML is everywhere in enterprise integration.

- *Parser:* custom recursive descent with namespace support
- *Attributes:* stored separately in UDM, accessed via `\@` prefix
- *Namespaces:* full support including prefixed elements and inheritance
- *Text content:* internal \_text convention (auto-unwrapped, invisible in output)
- *Repeated elements:* auto-detected as arrays, configurable via array hints
- *Encoding:* auto-detects from XML declaration (UTF-8, ISO-8859-1, Windows-1252)
- *Options:* `{encoding: "UTF-8"}` for output encoding

=== CSV

The universal import/export format. Every spreadsheet, every database, every legacy system can produce and consume CSV.

- *Parser:* handles quoted fields, escaped delimiters, multi-line values
- *Headers:* auto-detected from first row (configurable)
- *Type detection:* numbers, booleans, dates auto-detected from string content
- *Delimiters:* comma (default), semicolon, pipe, tab
- *Regional formats:* USA (1,234.56), European (1.234,56), French (1 234,56), Swiss (1'234.56)
- *BOM:* detects and handles UTF-8 BOM
- *Options:* `{delimiter: ";", headers: true, regionalFormat: "european", decimals: 2}`

=== YAML

The configuration standard. Kubernetes manifests, CI/CD pipelines, Docker Compose, OpenAPI specs — YAML dominates DevOps.

- *Parser:* SnakeYAML-based, handles all YAML 1.1 features
- *Anchors and aliases:* resolved during parsing (transparent to transformation)
- *Multi-document:* supported (separated by `---`)
- *Output styles:* block (default, human-readable) or flow (compact, JSON-like)
- *Options:* `{writeAttributes: true}`, same as JSON
- *Round-trip:* YAML is a superset of JSON — same UDM mapping

=== OData

The Microsoft ecosystem format. Dynamics 365, SharePoint, SAP Gateway, Power Platform — OData JSON is standard JSON with metadata conventions.

- *Parser:* standard JSON parser with OData metadata awareness
- *Metadata:* \@odata.context, \@odata.type, \@odata.id accessible as properties
- *Entity sets:* standard `value` array pattern
- *Navigation properties:* nested entity access
- *Options:* `{metadata: "...", context: "...", wrapCollection: true}`

== Tier 2: Schema Formats

Schema formats describe the _structure_ of data — types, constraints, cardinality. They're used for validation, documentation, code generation, and contract enforcement.

=== XSD (XML Schema Definition)

The W3C standard for XML validation. Every enterprise XML standard (UBL, HL7, ISO 20022, EDIFACT) ships with XSD.

- *Versions:* 1.0 and 1.1
- *Patterns:* Russian Doll, Venetian Blind, Salami Slice, Garden of Eden, Swiss Army Knife (Chapter 28)
- *Features:* complex types, simple types, restrictions, extensions, imports

=== JSCH (JSON Schema)

The schema language for JSON. Used in OpenAPI, AsyncAPI, data contracts.

- *Drafts:* draft-04, draft-06, draft-07, 2019-09, 2020-12
- *Features:* type, properties, required, pattern, enum, \$ref, allOf/oneOf/anyOf

=== Avro

Apache Avro schema for data pipelines. The standard schema format for Kafka and Hadoop ecosystems.

- *Features:* records, enums, arrays, maps, unions, logical types (date, timestamp, decimal, UUID)

=== Protobuf (Protocol Buffers)

Google's schema format for gRPC and efficient serialization.

- *Version:* proto3
- *Features:* messages, enums, repeated fields, oneof, maps, packages

=== OSCH (OData Schema / EDMX)

The schema format for OData services. Describes entity types, navigation properties, and service endpoints.

- *Format:* XML-based CSDL (Common Schema Definition Language)

=== TSCH (Table Schema)

The Frictionless Data standard for CSV metadata. Describes column names, types, and constraints for tabular data.

- *Format:* JSON-based field definitions

== Format Detection

UTL-X auto-detects the input format in identity mode and expression mode:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Detection rule*], [*Format*],
  [Starts with `<` or `<?xml`], [XML],
  [Starts with `\{` or `[`], [JSON],
  [Contains commas/semicolons with consistent column count], [CSV],
  [Contains `: ` patterns without braces], [YAML],
  [Explicit `--from` flag], [Overrides auto-detection],
)

Auto-detection works for 95% of cases. Use `--from` when the input is ambiguous (e.g., a YAML file that looks like JSON, or a CSV with only one column).

== Format Conversion Matrix

UTL-X can convert between any pair of Tier 1 formats:

#table(
  columns: (auto, auto, auto, auto, auto, auto),
  align: (left, center, center, center, center, center),
  [*From / To*], [*JSON*], [*XML*], [*CSV*], [*YAML*], [*OData*],
  [*JSON*], [--], [Yes], [Yes], [Yes], [Yes],
  [*XML*], [Yes], [--], [Yes], [Yes], [Yes],
  [*CSV*], [Yes], [Yes], [--], [Yes], [Yes],
  [*YAML*], [Yes], [Yes], [Yes], [--], [Yes],
  [*OData*], [Yes], [Yes], [Yes], [Yes], [--],
)

Every conversion is supported. Some are more natural than others:

- *JSON ↔ YAML:* lossless (YAML is a superset of JSON)
- *JSON ↔ XML:* mostly lossless (XML attributes need `writeAttributes` for full fidelity)
- *CSV → JSON/XML:* lossless (rows become objects/elements)
- *JSON/XML → CSV:* requires flattening (hierarchical → tabular, may lose nesting)
- *OData → JSON:* strip metadata for clean JSON

== Identity Mode Defaults

When using identity mode (no script, just format conversion), UTL-X picks sensible defaults:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Input*], [*Default output*], [*Override with*],
  [XML], [JSON], [`--to yaml`, `--to csv`],
  [JSON], [XML], [`--to yaml`, `--to csv`],
  [CSV], [JSON], [`--to xml`, `--to yaml`],
  [YAML], [JSON], [`--to xml`, `--to csv`],
)

The default "flip" is always to JSON (except JSON input, which flips to XML). This reflects the most common use case: "I have XML/CSV/YAML and I need JSON."

== Format Options Summary

All format options use the `{key: value}` syntax in the output declaration:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Format*], [*Option*], [*Example*],
  [JSON], [writeAttributes], [`{writeAttributes: true}`],
  [XML], [encoding], [`{encoding: "UTF-8"}`],
  [CSV], [delimiter], [`{delimiter: ";"}`],
  [CSV], [headers], [`{headers: true}`],
  [CSV], [regionalFormat], [`{regionalFormat: "european"}`],
  [CSV], [decimals], [`{decimals: 2}`],
  [CSV], [bom], [`{bom: true}`],
  [YAML], [writeAttributes], [`{writeAttributes: true}`],
  [Any], [schema (planned)], [`{schema: "contract.json"}`],
)

Options are optional — sensible defaults apply when not specified. Multiple options combine: `output csv {delimiter: ";", headers: true, regionalFormat: "european"}`.

== BOM (Byte Order Mark)

A BOM is an invisible character (Unicode U+FEFF) that some tools prepend to text files to signal the encoding. It was designed for UTF-16 (where byte order matters — hence the name), but it's also commonly added to UTF-8 files by Windows tools, especially Excel and Notepad.

The problem: a BOM is invisible in text editors but very much visible to parsers. A JSON file that starts with a BOM contains `\uFEFF{"name": "Alice"}` — the `\uFEFF` before the opening brace causes a parse error in strict parsers. A CSV file with a BOM has an invisible character prepended to the first column header, so `\uFEFFName` doesn't match `Name` in your transformation.

UTL-X handles BOM automatically across all formats:

- *On input:* the JSON, CSV, and YAML parsers detect and strip a leading BOM before parsing. No configuration needed — it just works. You never see the BOM in `$input`.
- *On output:* CSV can optionally prepend a BOM with `{bom: true}`. This is useful when the output will be opened in Excel on Windows, which uses the BOM to detect UTF-8 encoding for non-ASCII characters (accented names, CJK characters). JSON, XML, and YAML output never includes a BOM.

In practice, BOM issues appear most often when:
- Receiving CSV files from Windows systems (Excel adds BOM by default)
- Processing JSON files saved with Windows Notepad
- Integrating with legacy systems that expect or produce BOM-prefixed files

UTL-X's approach: strip on input (always), add on output (only when explicitly requested for CSV). This matches the W3C recommendation: UTF-8 files should not have a BOM, but parsers should tolerate one.

== What's Next

Part I is complete. You now understand:
- What UTL-X is and why it exists (Chapters 1--3)
- How to install and use it (Chapters 4--5)
- The three executables and the IDE (Chapters 6--7)
- The language fundamentals (Chapter 8)
- The Universal Data Model (Chapter 9)
- Schema mapping and USDL (Chapters 10--11)
- All supported formats (this chapter)

Part II dives into the language itself: expressions, functions, pattern matching, validation, and pipeline chaining. By the end of Part II, you'll be able to write any transformation UTL-X is capable of.
