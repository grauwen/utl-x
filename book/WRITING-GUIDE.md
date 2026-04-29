# UTL-X Book — Writing Guide

## Two Approaches to Chapter Writing

### Approach A: Diagrams First (Marcel's preferred approach)

1. **Draw the concept** in PowerPoint — architecture, flow, comparison
2. **Describe each diagram** — the text explains what the reader sees
3. **Add code examples** that match the diagrams
4. **Fill in details** — edge cases, options, caveats

This works best for:
- Architecture chapters (Ch03, Ch05, Ch29, Ch30)
- Flow diagrams (Ch18 pipelines, Ch28 enterprise integration)
- Comparison chapters (Ch09 schema mapping, Ch40 competitive landscape)
- Case studies (Ch44 — the flow IS the story)

### Approach B: Text First, Diagrams Later

1. **Write the explanation** — what the reader needs to understand
2. **Identify diagram points** — mark with `// DIAGRAM: description`
3. **Create diagrams** that illustrate the text
4. **Refine text** to reference the diagrams

This works best for:
- Language reference chapters (Ch07, Ch12, Ch13, Ch14)
- Function reference (Ch47 stdlib)
- Configuration chapters (Ch20 attribute design, Ch35 logging)
- Future outlook (Ch37-Ch43 — concepts, not architecture)

### Recommended: Hybrid

For each chapter, ask: "Is this chapter about a CONCEPT or a STRUCTURE?"
- **Structure** (architecture, flow, deployment) → draw first
- **Concept** (syntax, functions, rules) → write first

## PowerPoint Source Files

```
book/assets/source/
├── part1-foundation.pptx       ← UDM architecture, three executables, IDE layout
├── part2-language.pptx         ← Pipeline flow, multi-hop, validation sandwich
├── part3-formats.pptx          ← XML/JSON/CSV conversion, attribute handling, XSD patterns
├── part4-applications.pptx     ← Engine lifecycle, cloud deployment, SDK architecture
├── part5-future.pptx           ← Semantic validation layers, API contract hierarchy
├── part6-case-studies.pptx     ← E-invoicing flow, FHIR flow, SWIFT flow
└── shared-icons.pptx           ← Reusable icons, color palette, style guide
```

## Export Settings

- **Format**: PNG (for compatibility) or SVG (for scalability)
- **Resolution**: 2x (3840x2160 from 1920x1080 slide) for retina screens
- **Background**: white or transparent (for book pages)
- **Font**: Segoe UI (matches existing presentations)

## Color Palette (consistent across all diagrams)

| Color | Hex | Usage |
|-------|-----|-------|
| Dark Blue | #003366 | Titles, borders |
| Azure Blue | #0078D4 | Azure/cloud components |
| UTL-X Orange | #E86C00 | UTL-X components, highlights |
| Green | #107C10 | Success, output, correct |
| Purple | #5C2D91 | External systems, targets |
| Light Gray | #F5F5F5 | Code backgrounds |
| Dark Gray | #333333 | Body text |

## Typst Image Reference

```typst
// Full-width diagram
#figure(
  image("assets/diagrams/ch05-udm-architecture.png", width: 100%),
  caption: [Universal Data Model (UDM) — how formats map to the internal representation]
)

// Half-width, side by side
#grid(
  columns: (1fr, 1fr),
  image("assets/diagrams/ch19-xml-input.png"),
  image("assets/diagrams/ch19-json-output.png"),
)
```

## Chapter Writing Template

```typst
= Chapter Title

// DIAGRAM: [description of diagram needed]
// Source: part1-foundation.pptx, slide 3

== Section Title

[Text describing what the diagram shows]

// Code example that matches the diagram
```utlx
%utlx 1.0
input xml
output json
---
{result: $input.Order.Customer}
```

[Additional explanation, edge cases, options]
```

## Progress Tracking

| Chapter | Diagrams | Draft | Review | Final |
|---------|----------|-------|--------|-------|
| ch00 Preface | — | ☐ | ☐ | ☐ |
| ch01 Introduction | ☐ | ☐ | ☐ | ☐ |
| ch02 Licensing | — | ☐ | ☐ | ☐ |
| ch03 Transformation in Integration | ☐ | ☐ | ☐ | ☐ |
| ch04 Getting Started | ☐ | ☐ | ☐ | ☐ |
| ch05 Three Executables | ☐ | ☐ | ☐ | ☐ |
| ch06 The IDE | ☐ | ☐ | ☐ | ☐ |
| ch07 Language Fundamentals | — | ☐ | ☐ | ☐ |
| ch08 Universal Data Model | ☐ | ☐ | ☐ | ☐ |
| ch09 Schema-to-Schema Mapping | ☐ | ☐ | ☐ | ☐ |
| ch10 USDL | ☐ | ☐ | ☐ | ☐ |
| ch11 Format Support | ☐ | ☐ | ☐ | ☐ |
| ch12 Expressions & Operators | — | ☐ | ☐ | ☐ |
| ch13 Functions & Lambdas | — | ☐ | ☐ | ☐ |
| ch14 Standard Library | — | ☐ | ☐ | ☐ |
| ch15 Security Library | ☐ | ☐ | ☐ | ☐ |
| ch16 Pattern Matching | — | ☐ | ☐ | ☐ |
| ch17 Schema Validation | ☐ | ☐ | ☐ | ☐ |
| ch18 Pipeline Chaining | ☐ | ☐ | ☐ | ☐ |
| ch19 XML Transformations | ☐ | ☐ | ☐ | ☐ |
| ch20 XML Attribute Design | ☐ | ☐ | ☐ | ☐ |
| ch21 JSON Transformations | — | ☐ | ☐ | ☐ |
| ch22 CSV Transformations | — | ☐ | ☐ | ☐ |
| ch23 YAML Transformations | — | ☐ | ☐ | ☐ |
| ch24 OData Transformations | ☐ | ☐ | ☐ | ☐ |
| ch25 Schema Formats | ☐ | ☐ | ☐ | ☐ |
| ch26 XSD Patterns | ☐ | ☐ | ☐ | ☐ |
| ch27 Cross-Format Patterns | ☐ | ☐ | ☐ | ☐ |
| ch28 Enterprise Integration | ☐ | ☐ | ☐ | ☐ |
| ch29 Engine Lifecycle | ☐ | ☐ | ☐ | ☐ |
| ch30 Cloud Deployment | ☐ | ☐ | ☐ | ☐ |
| ch31 SDKs & Wrappers | ☐ | ☐ | ☐ | ☐ |
| ch32 Migration Guides | ☐ | ☐ | ☐ | ☐ |
| ch33 Performance | ☐ | ☐ | ☐ | ☐ |
| ch34 Message Parsing & Memory | ☐ | ☐ | ☐ | ☐ |
| ch35 Logging & Compliance | — | ☐ | ☐ | ☐ |
| ch36 Quality Assurance | ☐ | ☐ | ☐ | ☐ |
| ch37 Semantic Validation | ☐ | ☐ | ☐ | ☐ |
| ch38 API Contracts | ☐ | ☐ | ☐ | ☐ |
| ch39 Formats Not Yet Covered | — | ☐ | ☐ | ☐ |
| ch40 Competitive Landscape | ☐ | ☐ | ☐ | ☐ |
| ch41 Open-M | ☐ | ☐ | ☐ | ☐ |
| ch42 AI and UTL-X | ☐ | ☐ | ☐ | ☐ |
| ch43 Why Kotlin & GraalVM | — | ☐ | ☐ | ☐ |
| ch44 Case Studies | ☐ | ☐ | ☐ | ☐ |
| ch45 Grammar Reference | — | ☐ | ☐ | ☐ |
| ch46 Appendices | — | ☐ | ☐ | ☐ |
| ch47 Stdlib Reference | — | ☐ | ☐ | ☐ |

☐ = not started, — = not needed for this column
