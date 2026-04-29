// UTL-X: The Book — Main Document
// Compile: typst compile main.typ utlx-book.pdf
// Watch:   typst watch main.typ utlx-book.pdf

#set document(
  title: "UTL-X: Format-Agnostic Data Transformation",
  author: "Ir. Marcel A. Grauwen",
  date: datetime.today(),
)

#set text(font: "New Computer Modern", size: 11pt)
#set page(
  paper: "a4",
  margin: (top: 3cm, bottom: 3cm, left: 2.5cm, right: 2.5cm),
  header: context {
    if counter(page).get().first() > 2 [
      #set text(size: 9pt, fill: gray)
      _UTL-X: Format-Agnostic Data Transformation_
      #h(1fr)
      #counter(page).display()
    ]
  },
)

#set heading(numbering: "1.1")
#set par(justify: true)

// Code block styling
#show raw.where(block: true): block.with(
  fill: luma(245),
  inset: 10pt,
  radius: 4pt,
  width: 100%,
)

// ── Title Page ──

#align(center)[
  #v(4cm)
  #text(size: 36pt, weight: "bold", fill: rgb("#003366"))[UTL-X]
  #v(0.5cm)
  #text(size: 18pt, fill: rgb("#666666"))[Format-Agnostic Data Transformation]
  #v(0.3cm)
  #text(size: 14pt, fill: rgb("#999999"))[The Complete Guide]
  #v(3cm)
  #text(size: 14pt)[Ir. Marcel A. Grauwen]
  #v(0.5cm)
  #text(size: 11pt, fill: rgb("#999999"))[Creator of UTL-X]
  #v(4cm)
  #text(size: 10pt, fill: rgb("#999999"))[Version 1.0 — 2026]
]

#pagebreak()

// ── Table of Contents ──

#outline(
  title: [Table of Contents],
  indent: 2em,
  depth: 3,
)

#pagebreak()

// ── Preface ──

#include "chapters/00-preface.typ"
#pagebreak()

// ── Part I: Foundation ──

#align(center)[
  #v(6cm)
  #text(size: 28pt, weight: "bold", fill: rgb("#003366"))[Part I]
  #v(0.5cm)
  #text(size: 18pt, fill: rgb("#666666"))[Foundation]
  #v(1cm)
  #text(size: 12pt, fill: rgb("#999999"))[Understanding the language, the data model, and the philosophy]
]
#pagebreak()

#include "chapters/01-introduction.typ"
#pagebreak()
#include "chapters/01a-licensing.typ"
#pagebreak()
#include "chapters/01b-transformation-in-integration.typ"
#pagebreak()
#include "chapters/02-getting-started.typ"
#pagebreak()
#include "chapters/03-the-three-executables.typ"
#pagebreak()
#include "chapters/03a-the-ide.typ"
#pagebreak()
#include "chapters/04-language-fundamentals.typ"
#pagebreak()
#include "chapters/05-universal-data-model.typ"
#pagebreak()
#include "chapters/05a-schema-to-schema-mapping.typ"
#pagebreak()
#include "chapters/05b-usdl.typ"
#pagebreak()
#include "chapters/06-format-support.typ"
#pagebreak()

// ── Part II: The Language ──

#align(center)[
  #v(6cm)
  #text(size: 28pt, weight: "bold", fill: rgb("#003366"))[Part II]
  #v(0.5cm)
  #text(size: 18pt, fill: rgb("#666666"))[The Language]
  #v(1cm)
  #text(size: 12pt, fill: rgb("#999999"))[Mastering expressions, functions, patterns, and advanced features]
]
#pagebreak()

#include "chapters/07-expressions-and-operators.typ"
#pagebreak()
#include "chapters/08-functions-and-lambdas.typ"
#pagebreak()
#include "chapters/09-standard-library.typ"
#pagebreak()
#include "chapters/09a-security-library.typ"
#pagebreak()
#include "chapters/10-pattern-matching.typ"
#pagebreak()
#include "chapters/11-schema-validation.typ"
#pagebreak()
#include "chapters/12-pipeline-chaining.typ"
#pagebreak()

// ── Part III: Formats Deep Dive ──

#align(center)[
  #v(6cm)
  #text(size: 28pt, weight: "bold", fill: rgb("#003366"))[Part III]
  #v(0.5cm)
  #text(size: 18pt, fill: rgb("#666666"))[Formats Deep Dive]
  #v(1cm)
  #text(size: 12pt, fill: rgb("#999999"))[Working with XML, JSON, CSV, YAML, OData, and schema formats]
]
#pagebreak()

#include "chapters/13-xml-transformations.typ"
#pagebreak()
#include "chapters/13a-xml-attribute-design.typ"
#pagebreak()
#include "chapters/14-json-transformations.typ"
#pagebreak()
#include "chapters/15-csv-transformations.typ"
#pagebreak()
#include "chapters/16-yaml-transformations.typ"
#pagebreak()
#include "chapters/17-odata-transformations.typ"
#pagebreak()
#include "chapters/18-schema-formats.typ"
#pagebreak()
#include "chapters/18a-xsd-patterns-deep-dive.typ"
#pagebreak()
#include "chapters/19-cross-format-patterns.typ"
#pagebreak()

// ── Part IV: Real-World Applications ──

#align(center)[
  #v(6cm)
  #text(size: 28pt, weight: "bold", fill: rgb("#003366"))[Part IV]
  #v(0.5cm)
  #text(size: 18pt, fill: rgb("#666666"))[Real-World Applications]
  #v(1cm)
  #text(size: 12pt, fill: rgb("#999999"))[Enterprise integration, cloud deployment, and production patterns]
]
#pagebreak()

#include "chapters/20-enterprise-integration.typ"
#pagebreak()
#include "chapters/20a-engine-lifecycle.typ"
#pagebreak()
#include "chapters/21-cloud-deployment.typ"
#pagebreak()
#include "chapters/21a-sdks-and-wrappers.typ"
#pagebreak()
#include "chapters/22-migration-guides.typ"
#pagebreak()
#include "chapters/23-performance-and-optimization.typ"
#pagebreak()
#include "chapters/23a-message-parsing-and-memory.typ"
#pagebreak()
#include "chapters/23b-logging-and-compliance.typ"
#pagebreak()
#include "chapters/23c-quality-assurance.typ"
#pagebreak()

// ── Part V: Future Outlook ──

#align(center)[
  #v(6cm)
  #text(size: 28pt, weight: "bold", fill: rgb("#003366"))[Part V]
  #v(0.5cm)
  #text(size: 18pt, fill: rgb("#666666"))[Future Outlook]
  #v(1cm)
  #text(size: 12pt, fill: rgb("#999999"))[Semantic validation, API contracts, and the road ahead]
]
#pagebreak()

#include "chapters/24-semantic-validation.typ"
#pagebreak()
#include "chapters/25-api-contracts.typ"
#pagebreak()
#include "chapters/25a-formats-not-yet-covered.typ"
#pagebreak()
#include "chapters/25b-competitive-landscape.typ"
#pagebreak()
#include "chapters/25c-open-m-teaser.typ"
#pagebreak()
#include "chapters/25d-ai-and-utlx.typ"
#pagebreak()
#include "chapters/25e-why-kotlin-and-graalvm.typ"
#pagebreak()

// ── Part VI: Case Studies ──

#align(center)[
  #v(6cm)
  #text(size: 28pt, weight: "bold", fill: rgb("#003366"))[Part VI]
  #v(0.5cm)
  #text(size: 18pt, fill: rgb("#666666"))[Case Studies]
  #v(1cm)
  #text(size: 12pt, fill: rgb("#999999"))[Real-world use cases, step-by-step walkthroughs, and recipes]
]
#pagebreak()

#include "chapters/30-case-studies.typ"
#pagebreak()

// ── Part VII: Reference ──

#align(center)[
  #v(6cm)
  #text(size: 28pt, weight: "bold", fill: rgb("#003366"))[Part VII]
  #v(0.5cm)
  #text(size: 18pt, fill: rgb("#666666"))[Reference]
  #v(1cm)
  #text(size: 12pt, fill: rgb("#999999"))[Grammar, appendices, and general reference]
]
#pagebreak()

#include "chapters/27-grammar-reference.typ"
#pagebreak()
#include "chapters/28-appendices.typ"
#pagebreak()

// ── Part VIII: Standard Library Encyclopedia ──

#align(center)[
  #v(6cm)
  #text(size: 28pt, weight: "bold", fill: rgb("#003366"))[Part VIII]
  #v(0.5cm)
  #text(size: 18pt, fill: rgb("#666666"))[Standard Library Encyclopedia]
  #v(1cm)
  #text(size: 12pt, fill: rgb("#999999"))[Complete reference for all 652 functions — organized by category with examples]
]
#pagebreak()

#include "chapters/26-stdlib-reference.typ"
