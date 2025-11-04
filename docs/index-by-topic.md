# UTL-X Documentation Index

> **Last Updated:** 2025-10-28
> **Total Documents:** 191 markdown files
> **Organization:** Topical directory structure

This index lists all markdown documentation in the UTL-X project, organized by topic in a hierarchical directory structure. The documentation has been reorganized for better discoverability.

---

## Quick Navigation

- [🚀 Getting Started](#getting-started)
- [📚 Language Guide](#language-guide)
- [🏗️ Architecture](#architecture)
- [📖 Standard Library](#standard-library)
- [🎯 USDL (Schema Definition)](#usdl-universal-schema-definition-language)
- [🧪 Testing & Conformance](#testing--conformance)
- [📦 Format Support](#format-support)
- [🔧 Tools & CLI](#tools--cli)
- [📊 Analysis & Planning](#analysis--planning)
- [🤝 Community](#community)

---

## 🚀 Getting Started

Essential documentation for new users.

| Document | Description |
|----------|-------------|
| [README.md](../README.md) | **Project root documentation** |
| [QUICKSTART.md](../QUICKSTART.md) | Quick start guide |
| [CONTRIBUTORS.md](../CONTRIBUTORS.md) | Contributor information |
| [CONTRIBUTING.md](../CONTRIBUTING.md) | How to contribute |
| [LICENSE.md](../LICENSE.md) | Legal license information |
| [CLA.md](../CLA.md) | Contributor License Agreement |
| [NOTICE.md](../NOTICE.md) | Project notices |
| [installation.md](getting-started/installation.md) | Installation instructions |
| [your-first-transformation.md](getting-started/your-first-transformation.md) | Tutorial: first transformation |
| [basic-concepts.md](getting-started/basic-concepts.md) | Core concepts explained |
| [quick-reference.md](getting-started/quick-reference.md) | Quick syntax reference |
| [native-binary-quickstart.md](getting-started/native-binary-quickstart.md) | Native binary quick start |
| [quickstart_guide.md](../quickstart_guide.md) | Alternative quickstart |
| [README-NATIVE.md](../README-NATIVE.md) | Native compilation guide |
| [DOCUMENTATION_COMPLETE.md](../DOCUMENTATION_COMPLETE.md) | Documentation status |

---

# 📚 Language Guide

Complete language reference and syntax documentation.

| Document | Description |
|----------|-------------|
| [README.md](language-guide/README.md) | Language guide index |
| [overview.md](language-guide/overview.md) | Language overview |
| [syntax.md](language-guide/syntax.md) | Core syntax reference |
| [functions.md](language-guide/functions.md) | Function usage |
| [operators.md](language-guide/operators.md) | Operator reference |
| [control-flow.md](language-guide/control-flow.md) | Control flow constructs |
| [selectors.md](language-guide/selectors.md) | Path selectors |
| [templates.md](language-guide/templates.md) | Template usage |
| [multiple-inputs-outputs.md](language-guide/multiple-inputs-outputs.md) | Multi-input support ✅ |
| [quick-reference-multi-input.md](language-guide/quick-reference-multi-input.md) | Multi-input quick ref |
| [universal-schema-dsl.md](language-guide/universal-schema-dsl.md) | USDL language spec 🆕 |

---

# 🏗️ Architecture

System architecture, design decisions, and technical analysis.

### Core Architecture

| Document | Description |
|----------|-------------|
| [overview.md](architecture/overview.md) | Architecture overview |
| [compiler-pipeline.md](architecture/compiler-pipeline.md) | Compilation pipeline |
| [runtime.md](architecture/runtime.md) | Runtime system |
| [performance.md](architecture/performance.md) | Performance characteristics |
| [three-phase-runtime-design.md](architecture/three-phase-runtime-design.md) | 3-phase runtime design |
| [three-phase-runtime-validation-first.md](architecture/three-phase-runtime-validation-first.md) | Runtime validation |
| [parallelization-analysis.md](architecture/parallelization-analysis.md) | Parallelization analysis |
| [module-system-design.md](architecture/module-system-design.md) | Module system design |
| [spread-operator-explained.md](architecture/spread-operator-explained.md) | Spread operator details |

### Universal Data Model (UDM)

| Document | Description |
|----------|-------------|
| [universal-data-model.md](architecture/universal-data-model.md) | UDM specification |
| [udm_complete_guide.md](udm/udm_complete_guide.md) | Complete UDM guide |
| [udm_advanced_guide.md](udm/udm_advanced_guide.md) | Advanced UDM usage |
| [udm_visual_examples.md](udm/udm_visual_examples.md) | Visual UDM examples |
| [udm_documentation_index.md](udm/udm_documentation_index.md) | UDM doc index |

### Architecture Decisions

| Document | Description |
|----------|-------------|
| [project-directory-structure.md](architecture/decisions/project-directory-structure.md) | Directory structure |
| [cli-technology-choice.md](architecture/decisions/cli-technology-choice.md) | CLI tech decisions |
| [cli_clickt_vs_manual.md](architecture/decisions/cli_clickt_vs_manual.md) | CLI implementation |
| [clickt_migration_guide.md](architecture/decisions/clickt_migration_guide.md) | Clikt migration |
| [reach-audiance.md](architecture/decisions/reach-audiance.md) | Target audience |
| [uniform-file-paths.md](architecture/decisions/uniform-file-paths.md) | File path conventions |
| [xsd-complex-simple-type.md](architecture/decisions/xsd-complex-simple-type.md) | XSD type decisions |
| [xsd-discussions-style.md](architecture/decisions/xsd-discussions-style.md) | XSD style guide |
| [BOM-json.md](architecture/decisions/BOM-json.md) | JSON BOM handling |
| [date-time-localization.md](architecture/decisions/date-time-localization.md) | Date/time localization |
| [utlx-datetime-localization.md](architecture/decisions/utlx-datetime-localization.md) | DateTime design |
| [Next-steps.md](architecture/decisions/Next-steps.md) | Roadmap next steps |

### Comments & Documentation

| Document | Description |
|----------|-------------|
| [xml-documentation-analysis.md](architecture/comments-and-documentation-in-XML-YAML/xml-documentation-analysis.md) | XML doc analysis |
| [comment-preservation-analysis.md](architecture/comments-and-documentation-in-XML-YAML/comment-preservation-analysis.md) | Comment preservation |
| [comment-preservation-analysis-v2-with-xsd.md](architecture/comments-and-documentation-in-XML-YAML/comment-preservation-analysis-v2-with-xsd.md) | XSD comment handling |
| [utlx-xml-documentation-functions-proposal.md](architecture/comments-and-documentation-in-XML-YAML/utlx-xml-documentation-functions-proposal.md) | XML doc functions |
| [JSON-no-comments_JSON-Schema-does-allow-documentation.md](architecture/comments-and-documentation-in-XML-YAML/JSON-no-comments_JSON-Schema-does-allow-documentation.md) | JSON Schema docs |

---

## 📖 Standard Library

Standard library function documentation.

### General

| Document | Description |
|----------|-------------|
| [STDLIB_INTEGRATION.md](stdlib/STDLIB_INTEGRATION.md) | Integration overview |
| [stlib_readme.md](stdlib/stlib_readme.md) | Stdlib README |
| [stdlib_integration_guide_next_steps.md](stdlib/stdlib_integration_guide_next_steps.md) | Next steps guide |
| [stlib_completion_roadmap.md](stdlib/stlib_completion_roadmap.md) | Completion roadmap |

### Gap Analysis & Planning

| Document | Description |
|----------|-------------|
| [stlib_GAP_analysis_oct15th.md](stdlib/stlib_GAP_analysis_oct15th.md) | Gap analysis |
| [stlib_gaps_analysis.md](stdlib/stlib_gaps_analysis.md) | Gaps detailed |
| [stlib_gaps_analysis_type_cenversions.md](stdlib/stlib_gaps_analysis_type_cenversions.md) | Type conversion gaps |
| [stlib_complete_gaps_integration_plan.md](stdlib/stlib_complete_gaps_integration_plan.md) | Integration plan |
| [utlx_cap_analysis_oct15_afternoon.md](stdlib/utlx_cap_analysis_oct15_afternoon.md) | Capability analysis |

### Function Summaries

| Document | Description |
|----------|-------------|
| [stlib_complete_refernce.md](stdlib/stlib_complete_refernce.md) | Complete reference |
| [stlib_complete_reference_after_insert_c14n.md](stdlib/stlib_complete_reference_after_insert_c14n.md) | Reference w/ C14N |
| [stlib_final_enterprise.md](stdlib/stlib_final_enterprise.md) | Enterprise summary |
| [stlib_final_with_xml.md](stdlib/stlib_final_with_xml.md) | XML summary |
| [stdlib_final_summery.md](stdlib/stdlib_final_summery.md) | Final summary |
| [stlib_updated_summery.md](stdlib/stlib_updated_summery.md) | Updated summary |
| [stlibcompare_BW_xpath.md](stdlib/stlibcompare_BW_xpath.md) | XPath comparison |
| [dw_modules_added_summery.md](stdlib/dw_modules_added_summery.md) | DataWeave modules |
| [dataweave_vs_utilx_comparison.md](stdlib/dataweave_vs_utilx_comparison.md) | DW comparison |
| [utility_modules_complete_guide.md](stdlib/utility_modules_complete_guide.md) | Utility modules |

### Format-Specific Functions

| Document | Description |
|----------|-------------|
| [format_functions_summary.md](stdlib/format_functions_summary.md) | Format functions |
| [yaml_functions_guide.md](stdlib/yaml_functions_guide.md) | YAML functions |
| [yaml_functions_analysis.md](stdlib/yaml_functions_analysis.md) | YAML analysis |
| [yaml-maps-json-schema-summary.md](stdlib/yaml-maps-json-schema-summary.md) | YAML/JSON Schema |
| [xml_serialization_guide.md](stdlib/xml_serialization_guide.md) | XML serialization |
| [xml_options_integration_summary.md](stdlib/xml_options_integration_summary.md) | XML options |

---

## 🎯 USDL (Universal Schema Definition Language)

Schema definition language documentation (newest feature).

| Document | Description |
|----------|-------------|
| [universal-schema-dsl.md](language-guide/universal-schema-dsl.md) | USDL language spec |
| [usdl-syntax-rationale.md](design/usdl-syntax-rationale.md) | Design rationale |
| [usdl_naming_analysis.md](usdl/usdl_naming_analysis.md) | Naming analysis |
| [session-2025-10-27-usdl-implementation.md](usdl/session-2025-10-27-usdl-implementation.md) | Implementation session |
| [session-2025-10-27-complete-usdl-implementation.md](usdl/session-2025-10-27-complete-usdl-implementation.md) | Complete implementation |
| [session-2025-10-27-xsd-usdl-serializer.md](usdl/session-2025-10-27-xsd-usdl-serializer.md) | XSD serializer |
| [session-2025-10-27-json-schema-usdl-serializer.md](usdl/session-2025-10-27-json-schema-usdl-serializer.md) | JSON Schema serializer |

---

## 🧪 Testing & Conformance

Conformance suite and testing documentation.

| Document | Description |
|----------|-------------|
| [README.md](comformance-suite/README.md) | Conformance suite overview |
| [conformance-suite-design.md](comformance-suite/conformance-suite-design.md) | Test architecture |
| [test-capture-system.md](comformance-suite/test-capture-system.md) | Auto-capture system |
| [ARRAY_HINTS_IMPLEMENTATION.md](miscellaneous/ARRAY_HINTS_IMPLEMENTATION.md) | Array hints |
| [IMPACT_REPORT.md](miscellaneous/IMPACT_REPORT.md) | Test impact report |
| [TEST_SUMMARY.md](miscellaneous/TEST_SUMMARY.md) | Test summary |
| [FINAL_SUMMARY.md](miscellaneous/FINAL_SUMMARY.md) | Final test summary |
| [TRANSFORMATION_TESTS_FINDINGS.md](miscellaneous/TRANSFORMATION_TESTS_FINDINGS.md) | Test findings |
| [XSD_JSCH_INTEGRATION.md](jsch/XSD_JSCH_INTEGRATION.md) | XSD/JSON Schema integration |

---

## 📦 Format Support

Documentation for supported data formats.

### XML

| Document | Description |
|----------|-------------|
| [xml.md](xml/xml.md) | XML format guide |
| [xml_readme.md](xml/xml_readme.md) | XML README |
| [xml-array-handling.md](xml/xml-array-handling.md) | XML array handling |
| [xml_encoding_bom_functions.md](xml/xml_encoding_bom_functions.md) | XML encoding/BOM |
| [xsd-complex-simple-type.md](xsd/xsd-complex-simple-type.md) | XSD type handling |
| [xsd-discussions-style.md](xsd/xsd-discussions-style.md) | XSD style guide |

### JSON

| Document | Description |
|----------|-------------|
| [json.md](json/json.md) | JSON format guide |
| [json_readme.md](json/json_readme.md) | JSON README |
| [quick_reference.md](json/quick_reference.md) | JSON quick reference |
| [BOM-json.md](json/BOM-json.md) | JSON BOM handling |

### YAML

| Document | Description |
|----------|-------------|
| [yaml.md](yaml/yaml.md) | YAML format guide |
| [yaml_readme.md](yaml/yaml_readme.md) | YAML README |
| [yaml_quick_reference.md](yaml/yaml_quick_reference.md) | YAML quick reference |
| [yaml-dynamic-keys-support.md](yaml/yaml-dynamic-keys-support.md) | Dynamic keys |
| [yaml-dynamic-keys-findings.md](yaml/yaml-dynamic-keys-findings.md) | Dynamic keys findings |
| [yaml-dynamic-keys-summary.md](yaml/yaml-dynamic-keys-summary.md) | Dynamic keys summary |
| [yaml-dynamic-keys-output.md](yaml/yaml-dynamic-keys-output.md) | Dynamic keys output |
| [yaml-dynamic-keys-implementation-status.md](yaml/yaml-dynamic-keys-implementation-status.md) | Implementation status |

### CSV

| Document | Description |
|----------|-------------|
| [csv.md](csv/csv.md) | CSV format guide |
| [csv_readme.md](csv/csv_readme.md) | CSV README |
| [csv_functions_guide.md](csv/csv_functions_guide.md) | CSV functions |
| [csv_integration_notes.md](csv/csv_integration_notes.md) | CSV integration |
| [csv_parseCsv_clarification.md](csv/csv_parseCsv_clarification.md) | parseCsv clarification |

### Schema Formats

| Document | Description |
|----------|-------------|
| [xsd-jsch-format-support.md](proposals/xsd-jsch-format-support.md) | XSD/JSON Schema support |
| [xsd-jsch-design-decisions.md](proposals/xsd-jsch-design-decisions.md) | Design decisions |
| [XSD_JSCH_INTEGRATION.md](jsch/XSD_JSCH_INTEGRATION.md) | Integration guide |

### Integration Studies (Future Formats)

| Document | Description |
|----------|-------------|
| [avro-integration-study.md](avro/avro-integration-study.md) | Apache Avro |
| [protobuf-integration-study.md](protobuf/protobuf-integration-study.md) | Protocol Buffers |
| [parquet-integration-study.md](parquet/parquet-integration-study.md) | Apache Parquet |
| [asyncapi-integration-study.md](asyncapi/asyncapi-integration-study.md) | AsyncAPI |
| [openapi-integration-study.md](openapi/openapi-integration-study.md) | OpenAPI |
| [raml-integration-study.md](raml/raml-integration-study.md) | RAML |
| [raml-fragments-integration-study.md](raml/raml-fragments-integration-study.md) | RAML Fragments |
| [sap-idoc-integration-study.md](idoc/sap-idoc-integration-study.md) | SAP IDoc |
| [IDOC-meta-data.md](idoc/IDOC-meta-data.md) | IDoc metadata |
| [sql-ddl-integration-study.md](sql/sql-ddl-integration-study.md) | SQL DDL |

### Custom Formats

| Document | Description |
|----------|-------------|
| [custom-format.md](formats/custom-format.md) | Custom format guide |

---

## 🔧 Tools & CLI

Command-line tools and development utilities.

### CLI Documentation

| Document | Description |
|----------|-------------|
| [cli_readme.md](cli/cli_readme.md) | CLI README |
| [cli_quickstart.md](cli/cli_quickstart.md) | CLI quickstart |
| [implementation_guide.md](cli/implementation_guide.md) | Implementation guide |
| [cli_implementation_summery.md](cli/cli_implementation_summery.md) | Implementation summary |
| [cli-technology-choice.md](cli/cli-technology-choice.md) | Tech decisions |
| [CLI-FUNCTIONS-COMMAND.md](cli/CLI-FUNCTIONS-COMMAND.md) | Functions command |
| [cli-cheatsheet.md](functions/cli-cheatsheet.md) | CLI cheatsheet |

### Functions & Scripts

| Document | Description |
|----------|-------------|
| [function-reference.md](functions/function-reference.md) | Function reference |
| [ANNOTATION-TOOLING.md](scripts/ANNOTATION-TOOLING.md) | Annotation tools |

### Development

| Document | Description |
|----------|-------------|
| [Debugging-Guide.md](debug/Debugging-Guide.md) | Debugging guide |
| [core_readme.md](core/core_readme.md) | Core module README |

---

## 📊 Analysis & Planning

Analysis modules and project planning documentation.

| Document | Description |
|----------|-------------|
| [analysis_module_readme.md](analysis/analysis_module_readme.md) | Analysis module |
| [analysis_module_testkit_readme.md](analysis/analysis_module_testkit_readme.md) | TestKit README |
| [testkit_complete_summery.md](analysis/testkit_complete_summery.md) | TestKit summary |
| [complete_implementation_status.md](analysis/complete_implementation_status.md) | Implementation status |
| [feature_summary_roadmap.md](analysis/feature_summary_roadmap.md) | Feature roadmap |
| [schema_analysis_architecture.md](analysis/schema_analysis_architecture.md) | Schema analysis |
| [schema_workflow_example.md](analysis/schema_workflow_example.md) | Workflow example |
| [real_world_integration_example.md](analysis/real_world_integration_example.md) | Integration example |
| [updated_project_structure.md](analysis/updated_project_structure.md) | Project structure |
| [grammar-analysis.md](grammar/grammar-analysis.md) | Grammar analysis |
| [grammar-implementation-status.md](grammar/grammar-implementation-status.md) | Grammar status |

---

## 🎨 Examples

Example transformations and use cases.

| Document | Description |
|----------|-------------|
| [xml-to-json.md](examples/xml-to-json.md) | XML to JSON |
| [json-to-xml.md](examples/json-to-xml.md) | JSON to XML |
| [csv-transformation.md](examples/csv-transformation.md) | CSV transformation |
| [cookbook.md](examples/cookbook.md) | Recipe cookbook |
| [example_transforms.md](examples/example_transforms.md) | Example transforms |
| [multiple-inputs.md](examples/multiple-inputs.md) | Multi-input examples |
| [complex-transformations.md](examples/complex-transformations.md) | Complex examples |
| [real-world-use-cases.md](examples/real-world-use-cases.md) | Real-world use cases |

---

## 📚 Reference

Language and API reference documentation.

| Document | Description |
|----------|-------------|
| [language-spec.md](reference/language-spec.md) | Language specification |
| [grammar.md](reference/grammar.md) | Grammar reference |
| [stdlib-reference.md](reference/stdlib-reference.md) | Stdlib reference |
| [api-reference.md](reference/api-reference.md) | API reference |
| [cli-reference.md](reference/cli-reference.md) | CLI reference |

---

## 🤝 Community

Community resources and project information.

| Document | Description |
|----------|-------------|
| [faq.md](community/faq.md) | FAQ |
| [roadmap.md](community/roadmap.md) | Project roadmap |
| [changelog.md](community/changelog.md) | Changelog |
| [support.md](community/support.md) | Getting support |

---

## 📋 Proposals & Design

Design proposals and migration guides.

| Document | Description |
|----------|-------------|
| [README.md](proposals/README.md) | Proposals index |
| [xsd-jsch-format-support.md](proposals/xsd-jsch-format-support.md) | XSD/JSON Schema |
| [xsd-jsch-design-decisions.md](proposals/xsd-jsch-design-decisions.md) | Design decisions |
| [dollar-sign-input-prefix-migration.md](proposals/dollar-sign-input-prefix-migration.md) | $ input migration |
| [at-vs-dollar-quick-reference.md](proposals/at-vs-dollar-quick-reference.md) | @ vs $ reference |
| [MIGRATION-ISSUE-TEMPLATE.md](proposals/MIGRATION-ISSUE-TEMPLATE.md) | Migration template |

---

## 🔬 Specialized Topics

### Canonicalization

| Document | Description |
|----------|-------------|
| [c14n_integration_guide.md](canonicalization-xml/c14n_integration_guide.md) | XML C14N |
| [jcs_integration_guide.md](canonicalization-json/jcs_integration_guide.md) | JSON JCS |
| [jcs_integration_checklist.md](canonicalization-json/jcs_integration_checklist.md) | JCS checklist |

### Serialization

| Document | Description |
|----------|-------------|
| [serialization_integration_guide.md](inline-parse-render/serialization_integration_guide.md) | Integration guide |
| [serialization_usage_guide.md](inline-parse-render/serialization_usage_guide.md) | Usage guide |
| [xml_serialization_guide.md](inline-parse-render/xml_serialization_guide.md) | XML serialization |
| [prettyprint_integration.md](prettyprint/prettyprint_integration.md) | Pretty-print integration |
| [prettyprint_recommendation.md](prettyprint/prettyprint_recommendation.md) | Pretty-print recommendations |

### Security & Cryptography

| Document | Description |
|----------|-------------|
| [jws_analysis.md](jws/jws_analysis.md) | JWS analysis |
| [jws_jwt_jcs_integration.md](jws/jws_jwt_jcs_integration.md) | JWS/JWT/JCS |

### Date/Time

| Document | Description |
|----------|-------------|
| [date-time-enhancement-plan.md](date-time/date-time-enhancement-plan.md) | Enhancement plan |
| [date-time-localization.md](date-time/date-time-localization.md) | Localization |
| [utlx-datetime-localization.md](date-time/utlx-datetime-localization.md) | UTL-X DateTime |

### Arrays & Collections

| Document | Description |
|----------|-------------|
| [unzip_integration_guide.md](array/unzip_integration_guide.md) | Unzip function |

### URL Functions

| Document | Description |
|----------|-------------|
| [url_functions_integration_guide.md](url/url_functions_integration_guide.md) | URL functions |

### Operators

| Document | Description |
|----------|-------------|
| [spread-operator-explained.md](spread/spread-operator-explained.md) | Spread operator |

---

## 📝 Miscellaneous

Additional documentation and release notes.

| Document | Description |
|----------|-------------|
| [RELEASE-NOTES-MULTI-INPUT.md](miscellaneous/RELEASE-NOTES-MULTI-INPUT.md) | Multi-input release |
| [YAML_SUPPORT_ADDED.md](miscellaneous/YAML_SUPPORT_ADDED.md) | YAML support |
| [CLI_BINARY.md](miscellaneous/CLI_BINARY.md) | CLI binary |
| [Enhanced-Function-Annotations-v2.md](miscellaneous/Enhanced-Function-Annotations-v2.md) | Annotations v2 |
| [Enhanced-Function-Annotations-v1-OBSOLETE.md](miscellaneous/Enhanced-Function-Annotations-v1-OBSOLETE.md) | Annotations v1 (obsolete) |
| [lambda-functions-implementation.md](miscellaneous/lambda-functions-implementation.md) | Lambda implementation |
| [output-encoding-configuration.md](miscellaneous/output-encoding-configuration.md) | Output encoding |
| [error_message_improvements.md](miscellaneous/error_message_improvements.md) | Error improvements |
| [error_improvements_session_summary.md](miscellaneous/error_improvements_session_summary.md) | Error session |
| [calendar_support_analysis.md](miscellaneous/calendar_support_analysis.md) | Calendar support |
| [currency_support_analysis.md](miscellaneous/currency_support_analysis.md) | Currency support |

---

## 🤖 AI Assistance

Documentation for AI-assisted development.

| Document | Description |
|----------|-------------|
| [CLAUDE.md](ai/CLAUDE.md) | **Claude Code instructions** |

---

## 📊 Comparison with Other Tools

| Document | Description |
|----------|-------------|
| [vs-dataweave.md](comparison/vs-dataweave.md) | vs DataWeave |
| [vs-xslt.md](comparison/vs-xslt.md) | vs XSLT |
| [vs-jsonata.md](comparison/vs-jsonata.md) | vs JSONata |
| [vs-jq.md](comparison/vs-jq.md) | vs jq |
| [migration-guides.md](comparison/migration-guides.md) | Migration guides |

---

## Statistics

- **Total Documents:** 191 markdown files
- **Organization:** Hierarchical topic-based structure
- **Key Categories:**
  - Standard Library: 30+ docs
  - Architecture: 25+ docs
  - Format Support: 35+ docs
  - Language Guide: 11 docs
  - Examples: 8 docs
  - Tools & CLI: 10+ docs

### New Directory Organization

The documentation has been reorganized from a chronological flat structure to a hierarchical topic-based structure:

- **New directories:** array, asyncapi, avro, canonicalization-json, canonicalization-xml, date-time, debug, design, idoc, inline-parse-render, jsch, jws, parquet, prettyprint, protobuf, raml, scripts, spread, sql, url, usdl, and more
- **Benefits:** Better discoverability, logical grouping, scalability

---

## 🔍 Finding Documentation

### By Feature
- **Multi-input transformations:** See [language-guide/multiple-inputs-outputs.md](language-guide/multiple-inputs-outputs.md)
- **USDL schemas:** See [language-guide/universal-schema-dsl.md](language-guide/universal-schema-dsl.md)
- **XSD/JSON Schema:** See [proposals/xsd-jsch-format-support.md](proposals/xsd-jsch-format-support.md)
- **Standard library functions:** See [stdlib](stdlib/) directory
- **Format-specific docs:** See [xml](xml/), [json](json/), [yaml](yaml/), [csv](csv/) directories

### By Task
- **Getting started:** See [getting-started](getting-started/) directory
- **Writing transformations:** See [language-guide](language-guide/) directory
- **Using CLI:** See [cli](cli/) directory
- **Understanding architecture:** See [architecture](architecture/) directory
- **Contributing:** See [CONTRIBUTING.md](../CONTRIBUTING.md)

---

*This index reflects the reorganized documentation structure as of 2025-10-28. All files have been moved to topic-based directories for better organization and discoverability.*

**Note:** Files previously tracked with ❌ DELETED markers have been removed from this index as they are no longer present in the project.
