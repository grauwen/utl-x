= Appendices

== Appendix A: Format Conversion Matrix

Every cell shows whether the conversion is direct (pass-through works), needs mapping (transformation logic required), or is not applicable.

#table(
  columns: (auto, auto, auto, auto, auto, auto, auto),
  align: (left, center, center, center, center, center, center),
  [*From / To*], [*JSON*], [*XML*], [*CSV*], [*YAML*], [*OData*], [*Any Schema*],
  [*JSON*], [--], [Direct], [Flatten], [Direct], [Add \@odata], [Parse schema],
  [*XML*], [Direct], [--], [Flatten], [Direct], [Strip+add], [Parse schema],
  [*CSV*], [Direct], [Add structure], [--], [Direct], [Add \@odata], [N/A],
  [*YAML*], [Direct], [Direct], [Flatten], [--], [Add \@odata], [Parse schema],
  [*OData*], [Strip \@odata], [Strip+map], [Strip+flatten], [Strip \@odata], [--], [EDMX parse],
)

*Direct:* `$input` pass-through produces valid output (structures are compatible).

*Flatten:* nested data must be denormalized for CSV (one row per child, parent fields repeated). Use `unnest()` (Chapter 21) or manual `flatten(map(...))`.

*Add structure:* CSV is flat — converting to XML requires adding element names and nesting.

*Strip:* OData annotations (`\@odata.*`) are automatically separated during parsing, producing clean data.

*Parse schema:* schema formats (XSD, JSON Schema, etc.) are parsed as data — the schema structure becomes the transformation input.

== Appendix B: Common Error Messages

=== Parser Errors

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Error*], [*Fix*],
  [Expected expression], [Check for missing value after `let x =` or empty transformation body],
  [Expected property name], [Object key is missing or uses reserved word — quote it: `"input": value`],
  [Expected ')' after arguments], [Mismatched parentheses in function call — count opening and closing],
  [Unclosed string literal], [Missing closing `"` or `'` — check for unescaped quotes inside strings],
  [User-defined functions must start with uppercase], [Rename `myFunc` to `MyFunc` — PascalCase required],
  [Expected 'output' declaration], [Multiple `input` lines not supported — use `input:` with commas],
)

=== Runtime Errors

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Error*], [*Fix*],
  [Undefined variable: name], [Variable not declared with `let`, or misspelled. Check: is the input named?],
  [Cannot index non-array with number], [Trying `obj[0]` on an object — use `.propertyName` instead],
  [Cannot index non-object with string], [Trying `arr["key"]` on an array — use `arr[0]` (number index)],
  [Array index out of bounds], [Index exceeds array size — check with `count()` first or use `?.`],
  [Division by zero], [Denominator is zero — guard with `if (divisor != 0) ... else 0`],
  [Type error: expected string got number], [Use `toString(value)` to convert, or check input data types],
)

=== Validation Errors (UTLXe)

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Error*], [*Fix*],
  [Validation error at \$.field: required field missing], [The input or output is missing a required field — check mapping],
  [Does not match pattern], [String value doesn't match the regex in the schema — check format],
  [Value is less than minimum], [Number below the schema's `minimum` — check calculation],
  [Type mismatch: expected string got number], [Schema expects a string but transformation produces a number — add `toString()`],
)

== Appendix C: UTL-X vs Competitors (Feature Comparison)

=== UTL-X vs XSLT

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Feature*], [*XSLT*], [*UTL-X*],
  [Input formats], [XML only], [XML, JSON, CSV, YAML, OData],
  [Output formats], [XML (text output for others)], [All 11 formats natively],
  [Paradigm], [Template matching (push)], [Functional expressions (pull)],
  [Syntax], [XML (verbose)], [Concise, JSON-like],
  [Functions], [XPath 2.0/3.0 functions], [650+ stdlib functions (superset of XPath 2.0, partial XPath 3.0)],
  [User functions], [Named templates], [`function` with PascalCase],
  [Variables], [`xsl:variable` (immutable)], [`let` (immutable)],
  [Schema validation], [XSD only], [7 schema formats],
  [Production engine], [No (processor required)], [UTLXe (86K msg/s)],
  [IDE], [XML editors], [VS Code + live preview],
  [Learning curve], [Steep], [Moderate (familiar syntax)],
)

=== UTL-X vs DataWeave

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Feature*], [*DataWeave*], [*UTL-X*],
  [Format agnostic], [Yes], [Yes],
  [Attribute prefix], [`@`], [`@`],
  [writeAttributes], [Same name, same default], [Same name, same default],
  [Function style], [Functional], [Functional],
  [Open source], [No (proprietary)], [Yes (AGPL)],
  [Platform], [MuleSoft Anypoint only], [Any (Docker, CLI, cloud)],
  [Price], [\$1,250+/month], [\$35/month],
  [Schema formats], [JSON Schema, XSD], [7 formats (+ USDL bridge)],
  [Production engine], [Mule Runtime], [UTLXe (86K msg/s)],
  [Version control], [Embedded in Mule app], [`.utlx` files in Git],
  [Conformance suite], [No], [500+ tests],
)

=== UTL-X vs jq

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Feature*], [*jq*], [*UTL-X*],
  [Formats], [JSON only], [11 formats],
  [Dot notation], [`.name`], [`.name` (identical)],
  [Raw output], [`-r`], [`-r` (identical)],
  [Pipe], [`\|`], [`\|>` (explicit)],
  [Filter], [`select()`], [`filter()`],
  [Map], [`[.[] \| .name]`], [`map(., (x) -> x.name)`],
  [User functions], [`def f: ...;`], [`function F(...) { ... }`],
  [Schema validation], [No], [7 formats],
  [Production engine], [No], [UTLXe],
  [Stdlib functions], [~50], [650+],
  [XML support], [No], [Full (namespaces, attributes, C14N)],
)

== Appendix D: Industry Standards Quick Reference

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Standard*], [*Domain*], [*UTL-X relevance*],
  [UBL 2.1 / Peppol BIS 3.0], [E-invoicing (EU)], [XML output with UBL namespaces (Ch30)],
  [HL7 FHIR R4], [Healthcare], [`@value` accessor for FHIR XML (Ch21, Ch30)],
  [ISO 20022 (SWIFT MX)], [Financial messaging], [pain.001, camt.053 XML (Ch30)],
  [EDIFACT], [Logistics/retail EDI], [Planned — reverseXSL integration (Ch41)],
  [ANSI X12], [North American EDI], [Planned — reverseXSL integration (Ch41)],
  [OpenAPI 3.x], [REST API contracts], [YAML/JSON input, schema extraction (Ch40)],
  [AsyncAPI], [Event-driven APIs], [Kafka/AMQP message schemas (Ch40)],
  [XBRL], [Financial reporting], [QName functions for taxonomy processing (Ch21)],
  [SOAP/WSDL], [Web services], [Envelope parsing, WS-Addressing (Ch21)],
  [OData v4], [Enterprise REST], [Native format with annotation handling (Ch26)],
  [NEN 7510], [Dutch healthcare security], [Tenant isolation architecture (Ch37)],
  [PCI DSS], [Payment card security], [Log metadata, not data (Ch37)],
  [GDPR], [EU data protection], [Data minimization in logging (Ch37)],
)

== Appendix E: Glossary

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Term*], [*Definition*],
  [AST], [Abstract Syntax Tree — the parsed representation of a `.utlx` file],
  [BOM], [Byte Order Mark (U+FEFF) — encoding indicator stripped on input, optional on CSV output],
  [C14N], [XML Canonicalization — W3C standard for deterministic XML serialization],
  [CDATA], [Character Data section in XML — content not parsed as markup],
  [Dapr], [Distributed Application Runtime — CNCF sidecar for broker-agnostic messaging],
  [EDMX], [Entity Data Model XML — OData metadata schema format (OSCH in UTL-X)],
  [FHIR], [Fast Healthcare Interoperability Resources — HL7's modern health data standard],
  [GraalVM], [JVM with native image compilation — used for UTL-X CLI binary],
  [IDoc], [Intermediate Document — SAP's data exchange format],
  [iPaaS], [Integration Platform as a Service — cloud-based integration tools],
  [JMS], [Java Message Service — enterprise Java messaging standard],
  [KEDA], [Kubernetes Event-Driven Autoscaling — scales containers based on queue depth],
  [MCP], [Model Context Protocol — provides LLMs with UTL-X language context],
  [MPPM], [Message Processing Pipeline Model — Open-M's integration flow standard],
  [OData], [Open Data Protocol — REST-based data access (Microsoft, SAP)],
  [Peppol], [Pan-European Public Procurement Online — e-invoicing network],
  [QName], [Qualified Name — namespace prefix + local name in XML],
  [RAML], [RESTful API Modeling Language — MuleSoft's API description format],
  [UBL], [Universal Business Language — OASIS standard for business documents],
  [UDM], [Universal Data Model — UTL-X's format-agnostic data representation],
  [USDL], [Universal Schema Definition Language — UTL-X's schema dialect],
  [UTLXe], [UTL-X Engine — the production runtime for high-throughput transformation],
  [XBRL], [eXtensible Business Reporting Language — financial reporting standard],
)

== Appendix F: Version History

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Version*], [*Date*], [*Highlights*],
  [1.0.0], [April 2026], [Initial release. CLI, daemon, engine. 5 data formats, 6 schema formats, 652 stdlib functions.],
  [1.0.1], [April 2026], [CLI enhancements: `-e` expression mode, identity flip, `--from`/`--to` flags.],
  [1.0.2], [April 2026], [Bug fixes: XML text unwrapping in UDM, `_text` leaking into JSON/YAML output. New: `writeAttributes` option, `#text` key for text content, `@` prefix consistency for JSON and YAML.],
  [1.1.0], [May 2026], [Major feature release. Newline separators (commas optional in multi-line objects and match cases). Data restructuring functions: nestBy, lookupBy, chunkBy, unnest. USDL enrichment for all 6 Tier 2 parsers. groupBy returns Object + mapGroups. `#text` alias for `_text`. `xs:decimal` type mapping with JSON Schema `"format": "decimal"`. GraalVM native binary fixes — 650+ stdlib functions work in native image. Native binary startup ~7ms.],
)

== Appendix G: License

UTL-X is dual-licensed:

=== Open Source: AGPL-3.0

The UTL-X parser, interpreter, engine, and standard library are licensed under the GNU Affero General Public License v3.0. This means:

- *Free to use:* run UTL-X for any purpose
- *Free to modify:* change the source code
- *Free to distribute:* share your modified version
- *Copyleft:* if you distribute a modified version or offer it as a network service, you must share your modifications under AGPL-3.0

*Your transformations are yours.* The AGPL covers the UTL-X *software* — the parser, interpreter, and engine. It does NOT cover the `.utlx` transformation files you write or the data you process. Your transformations are your intellectual property.

=== Commercial Licensing

For organizations that cannot use AGPL (proprietary embedding, SaaS without source disclosure):

- Azure Marketplace: Starter (\$35/month) and Professional (\$105/month) plans include a commercial license
- Direct licensing: contact Glomidco B.V. for custom terms

=== Third-Party Components

UTL-X includes components under compatible open-source licenses:

- SnakeYAML: Apache 2.0
- Jackson: Apache 2.0
- ASM: BSD 3-Clause
- Apache XML Security: Apache 2.0
- SLF4J + Logback: MIT / EPL-1.0
- reverseXSL (vendored): Apache 2.0
