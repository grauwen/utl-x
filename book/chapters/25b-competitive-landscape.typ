= Competitive Landscape: Mapping and Integration Tools

== Command-Line Transformation Tools

=== jq — The JSON Swiss Knife
// - JSON-only, pipe-friendly, ubiquitous on Linux
// - Strengths: fast, lightweight, installed everywhere, great for shell scripts
// - Limitations: JSON only — no XML, CSV, YAML, OData
// - UTL-X advantage: all formats, same syntax; jq users feel at home with -e mode
// - Website: stedolan.github.io/jq

=== yq — YAML/JSON/XML/CSV/TOML Processor
// - Mike Farah's yq: multi-format (YAML, JSON, XML, CSV, TOML, properties)
// - Strengths: multi-format, Go binary, jq-like syntax, active development
// - Limitations: simple field access/modification, no complex transformations,
//   no schema validation, no function library, no pipeline chaining
// - UTL-X advantage: 652 functions, schema validation, compiled strategy,
//   production engine (utlxe), IDE integration
// - The closest CLI competitor to UTL-X for quick format conversion
// - Website: mikefarah.gitbook.io/yq

=== JSONata — JSON Query and Transformation
// - Declarative JSON query/transformation language
// - Strengths: elegant expression syntax, browser + Node.js, lightweight
// - Limitations: JSON only, no XML/CSV/YAML, no schema validation,
//   no production engine, limited stdlib
// - UTL-X advantage: all formats, production engine, schema validation
// - Used in: Node-RED, some API gateways
// - Website: jsonata.org

=== XSLT — The XML Veteran
// - W3C standard, 25+ years, the original transformation language
// - Strengths: standardized, every XML tool supports it, powerful template matching
// - Limitations: XML only, verbose syntax, no JSON/CSV/YAML, steep learning curve
// - UTL-X advantage: all formats, modern syntax, functional paradigm
// - Still dominant in: government, banking, healthcare (legacy)
// - UTL-X migration path: Chapter 22 (Migration from XSLT)

== Enterprise Integration Platforms (iPaaS)

=== MuleSoft (Salesforce)
// - DataWeave: format-agnostic transformation language (closest competitor to UTL-X)
// - Strengths: mature, enterprise-grade, Salesforce ecosystem, Anypoint Platform
// - DataWeave syntax: similar to UTL-X (functional, @ for attributes)
// - Limitations: proprietary (closed source), expensive ($1,250+/month),
//   vendor lock-in, Salesforce acquisition concerns
// - UTL-X advantage: open source (AGPL), $35/month, no vendor lock-in
// - writeAttributes option: same name, same default — deliberate compatibility

=== Tibco BusinessWorks (BWCE)
// - Visual mapper with XSLT generation
// - Strengths: mature, visual IDE, enterprise customer base
// - Mapper: drag-and-drop field mapping → generates XSLT
// - Limitations: XML-centric (JSON requires conversion), expensive licensing,
//   heavy runtime, slow startup
// - UTL-X advantage: native JSON/CSV/YAML, lightweight (Container App vs full server),
//   10x cheaper
// - Worker model: Tibco BW worker pool similar to UTLXe workers

=== Boomi (formerly Dell Boomi)
// - Cloud-native iPaaS with visual data mapping
// - Strengths: easy-to-use UI, cloud-native, good connector library
// - Limitations: mapping is visual-only (no scripting language), limited
//   transformation logic, pricing per connection
// - UTL-X advantage: scriptable transformations, version-controllable (.utlx files),
//   testable (conformance suite)

=== Workato
// - Recipe-based automation platform
// - Strengths: citizen developer friendly, 1000+ connectors
// - Limitations: limited transformation capabilities, no format-agnostic language,
//   visual-only mapping
// - UTL-X advantage: professional-grade transformations, schema validation, performance

=== Informatica / IICS
// - Enterprise data integration, ETL/ELT
// - Strengths: data quality, MDM, governance, batch processing
// - Limitations: heavy, expensive, batch-oriented (not real-time),
//   XML-centric mapping
// - UTL-X advantage: real-time (86K+ msg/s), lightweight, event-driven

== Hyperscaler Native Tools

=== Azure
// - Data Mapper (Logic Apps): XSLT-based, JSON→XML internally, limited
// - Liquid templates (Logic Apps): JSON only, no XML/CSV/YAML
// - Azure Functions: custom code (C#/Python), no declarative mapping
// - Integration Account: XSLT maps, expensive ($300+/month)
// - UTL-X on Azure: Container App, $35/month, all formats, Marketplace listing

=== AWS
// - No native transformation language
// - Step Functions: orchestration, not transformation
// - Lambda: custom code per transformation
// - EventBridge Input Transformer: basic JSON path extraction, very limited
// - Glue: ETL jobs (Python/Spark), batch-oriented
// - UTL-X on AWS: ECS/Fargate, $44/month, all formats

=== GCP
// - Application Integration: Jsonnet templates, JSON only, no XML/CSV/YAML
// - Cloud Data Fusion: CDAP-based, batch ETL, not per-message
// - Dataflow (Apache Beam): stream processing, not data mapping
// - Apigee: basic JSON↔XML mediation policies, limited XSLT
// - UTL-X on GCP: Cloud Run, $44/month, all formats, Pub/Sub push native

=== Hyperscaler Comparison Table
// | Capability | Azure | AWS | GCP | UTL-X |
// |-----------|-------|-----|-----|-------|
// | JSON native | Liquid (limited) | Lambda (code) | Jsonnet (limited) | ✅ |
// | XML native | XSLT (Data Mapper) | Lambda (code) | Apigee (basic) | ✅ |
// | CSV native | ❌ | ❌ | ❌ | ✅ |
// | YAML native | ❌ | ❌ | ❌ | ✅ |
// | OData native | ❌ | ❌ | ❌ | ✅ |
// | Schema validation | XSD only | ❌ | ❌ | 7 formats |
// | Declarative language | XSLT/Liquid | ❌ | Jsonnet | ✅ (UTL-X) |
// | Cost (per month) | $300+ (Integration Acct) | Lambda pricing | Data Fusion $300+ | $35 |

== ERP Vendors

=== SAP
// - SAP CPI (Cloud Platform Integration): visual Message Mapping + Groovy scripts
// - SAP PI/PO (on-premise): XSLT + Java mapping
// - Limitations: XML-centric, SAP-specific, expensive licensing
// - UTL-X integration: SAP OData/IDoc → UTL-X → any format
// - Use case: SAP-to-non-SAP data transformation

=== Oracle
// - Oracle Integration Cloud (OIC): visual mapper, XSLT-based
// - Oracle SOA Suite: XSLT + XQuery transformation
// - Limitations: XML-centric, Oracle ecosystem lock-in
// - UTL-X integration: Oracle REST/SOAP → UTL-X → target format

=== Microsoft Dynamics 365
// - No built-in transformation language
// - Relies on: Azure Logic Apps, Power Automate, custom plugins
// - OData API: JSON responses with @odata conventions
// - UTL-X integration: D365 OData JSON → UTL-X → any format (e-invoicing, reporting)

=== Odoo
// - Python-based ERP, open source
// - API: JSON-RPC and REST (JSON)
// - No built-in transformation language
// - UTL-X integration: Odoo JSON → UTL-X → any format

=== Summary: ERP + UTL-X Integration Pattern
// All ERPs expose data via APIs (REST/SOAP/OData).
// None have a format-agnostic transformation language.
// UTL-X bridges the gap:
//   ERP API response → UTL-X → target format (e-invoice, FHIR, EDI, canonical JSON)

== Open Source Alternatives

=== Apache Camel
// - Integration framework (Java), not a transformation language
// - Has data format support: JSON, XML, CSV, YAML
// - Transformation: via Java code, XSLT, or embedded JSONata
// - Strengths: 300+ connectors, routing, EIP patterns
// - UTL-X advantage: declarative transformation language vs imperative Java code

=== Smooks
// - Java-based transformation framework
// - Supports: XML, JSON, CSV, EDI, Java objects
// - Strengths: EDI support (EDIFACT, X12), template-based
// - Limitations: Java-only, complex configuration, less active development
// - UTL-X advantage: simpler syntax, multi-format, cloud-native

=== Ballerina
// - Integration-oriented programming language (WSO2)
// - Built-in: HTTP, gRPC, GraphQL, data mapping
// - Strengths: language-level integration support, type system
// - Limitations: general-purpose language (not transformation-specific),
//   smaller community, WSO2 ecosystem
// - UTL-X advantage: focused on transformation, simpler, no general-purpose baggage

== Positioning Matrix

// | Tool | Type | Formats | Open Source | Cloud | Price |
// |------|------|---------|------------|-------|-------|
// | **UTL-X** | Language | All | Yes (AGPL) | All | $35/mo |
// | jq | CLI | JSON | Yes | N/A | Free |
// | yq | CLI | Multi | Yes | N/A | Free |
// | JSONata | Language | JSON | Yes | N/A | Free |
// | XSLT | Language | XML | Standard | N/A | Free |
// | DataWeave | Language | Multi | No | MuleSoft | $1,250+/mo |
// | Tibco BW | Platform | XML+JSON | No | On-prem/cloud | Enterprise |
// | Boomi | Platform | Visual | No | Cloud | Enterprise |
// | Azure DM | Feature | XML+JSON | No | Azure | $300+/mo |
// | AWS | Custom code | Any | N/A | AWS | Lambda pricing |
// | GCP AppInt | Feature | JSON | No | GCP | $300+/mo |
