= Introduction

== The Data Transformation Problem
// - Every enterprise has multiple data formats
// - XML (legacy, SOAP, EDI), JSON (REST APIs, modern), CSV (reports, imports), YAML (config, K8s)
// - Current tools force you to learn a different language per format
// - XSLT for XML, Liquid for JSON, custom code for CSV, nothing for YAML

== What Is UTL-X?
// - Format-agnostic: one transformation language for all formats
// - Functional: declarative, composable, testable
// - Universal Data Model (UDM): the abstraction that makes it possible
// - 652 built-in functions across 18 categories
// - Open source (AGPL-3.0)

== The Swiss Army Knife Analogy
// - Like a Swiss Army knife for data: one tool, many blades
// - Each format is a blade — XML, JSON, CSV, YAML, OData
// - The handle is the UDM — the common core
// - The transformation language works with any blade

== UTL-X vs The Alternatives
// - vs XSLT: multi-format, modern syntax, functional
// - vs DataWeave: open source, no vendor lock-in
// - vs jq: not just JSON — XML, CSV, YAML, OData too
// - vs custom code: declarative, built-in parsers, type-safe
// - Comparison table with capabilities

== Architecture Overview
// - Parser → AST → Interpreter/Compiler → Output
// - Input formats: parsers convert to UDM
// - Transformation: operates on UDM
// - Output formats: serializers convert from UDM
// - Diagram: Input → Parse → UDM → Transform → UDM → Serialize → Output

== The UTL-X Ecosystem
// - utlx CLI — command-line tool for developers
// - utlxd — daemon for IDE integration (VS Code LSP)
// - utlxe — production engine (HTTP, gRPC, Dapr)
// - Conformance suite — 453+ tests
// - VS Code extension — syntax highlighting, live preview

== A First Example
// - Simple XML-to-JSON transformation
// - Show input, transformation, output side by side
// - Explain each line
// - Run it with the CLI
