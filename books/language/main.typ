// UTL-X: One Language, All Formats
// Compile: typst compile main.typ "UTL-X One Language All Formats.pdf"
// Watch:   typst watch main.typ "UTL-X One Language All Formats.pdf"

#set document(
  title: "UTL-X: One Language, All Formats",
  author: "Ir. Marcel A. Grauwen",
  date: datetime.today(),
)

#set text(font: "New Computer Modern", size: 10pt)
#set page(
  paper: "a4",
  margin: (top: 3cm, bottom: 3cm, left: 2.5cm, right: 2.5cm),
  header: context {
    if counter(page).get().first() > 2 {
      let current-page = here().page()
      let all-headings = query(heading.where(level: 1))
      let chapter-name = {
        let found = [UTL-X]
        for h in all-headings {
          if h.location().page() <= current-page {
            found = h.body
          }
        }
        found
      }
      set text(size: 9pt, fill: gray)
      [#emph(chapter-name) #h(1fr) #counter(page).display()]
    }
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

#page(margin: 0pt, fill: luma(242))[
#set par(spacing: 0pt)
#set block(spacing: 0pt)
// Top section — single image, no seams
#image("pictures/coverpage/cover-top-hires.png", width: 100%)

// 4. UTL-X title + logo area — light gray background
#block(fill: luma(242), width: 100%, inset: (x: 1.5cm, top: 0.1cm, bottom: 0.5cm))[
  #align(center)[
    #text(size: 80pt, weight: "bold", font: "Arial", fill: rgb("#333333"))[UTL]#text(size: 80pt, weight: "bold", font: "Arial", fill: rgb("#CC0000"))[X]
    #v(0.4cm)
    #text(size: 16pt, style: "italic", fill: rgb("#333333"))[#text(weight: "bold")[U]niversal #text(weight: "bold")[T]ransformation #text(weight: "bold")[L]anguage e#text(weight: "bold", fill: rgb("#CC0000"))[X]tended]
    #v(0.5cm)
    #image("pictures/utlx-logos/utlx-logo-5formats-red.png", width: 13cm)
  ]
]

// 5. Subtitle bar — book title
#block(width: 100%, inset: (x: 1.5cm, y: 0.6cm))[
  #align(center)[
    #text(size: 15pt, weight: "bold")[One Language, All Formats]
    #v(0.6cm)
    #text(size: 13pt)[Data Mapping and Transformation Across XML, JSON, CSV, YAML, OData, and Beyond]
  ]
]

// 6. Author area
#v(1fr)
#align(center)[
  #text(size: 13pt)[Ir. Marcel A. Grauwen]
]
#v(1fr)

// 7. Footer
#align(center)[
  #text(size: 8pt, fill: rgb("#AAAAAA"))[First Edition — 2026]
]
#v(0.5cm)
] // end of #page(margin: 0pt)

#pagebreak()

// ── Copyright / Colophon Page ──

#set text(size: 9pt)
#v(1fr)

*UTL-X: One Language, All Formats — Data Mapping and Transformation Across XML, JSON, CSV, YAML, OData, and Beyond*

Copyright \u{00A9} 2026 Ir. Marcel A. Grauwen. All rights reserved.

Published by GLOMIDCO B.V., The Netherlands

ISBN 978-90-819728-1-9

First edition, 2026.

No part of this publication may be reproduced, stored in a retrieval system, or transmitted in any form or by any means without the prior written permission of the author, except for brief quotations in reviews and critical articles.

UTL-X is open source software. The language specification, CLI tool, and standard library are freely available at `https://github.com/grauwen/utl-x`.

Typeset with Typst in New Computer Modern.

#set text(size: 10pt)
#pagebreak()

// ── About the Author ──

#heading(numbering: none, outlined: false)[About the Author]

#show heading: set heading(numbering: none)

*Ir. Marcel A. Grauwen* is a Dutch software engineer and architect with over twenty-five years of experience in enterprise integration, data transformation, and middleware platforms.

He has designed and built integration solutions on Tibco BusinessWorks, MuleSoft, SAP CPI, Azure Logic Apps, and IBM Integration Bus — and grew increasingly frustrated with the fundamental limitation they all shared: format-specific transformation logic tied to vendor platforms.

UTL-X was born from that frustration. The idea was simple: one transformation language that works on all data formats, runs anywhere, and belongs to no vendor. Marcel designed the language, built the runtime (CLI, IDE daemon, and production engine), wrote the 652-function standard library, and created the USDL schema classification system.

Before UTL-X, Marcel held senior engineering and architecture roles in financial services, energy and utilities, logistics, and the semiconductor equipment industry — including many years of integration work for ASML, the world's leading lithography machine manufacturer. These are domains where data formats are many, schemas are strict, and getting the mapping wrong has real consequences.

Marcel holds an Ir. degree (Master of Science in Engineering) and is based in the Netherlands.

#pagebreak()

// ── Table of Contents ──

#outline(
  title: [Table of Contents],
  indent: 2em,
  depth: 2,
)
#v(-0.65em)
#link(<ch50-stdlib>)[LIBRARY REFERENCE #h(0.3em) #box(width: 1fr, repeat[.#h(0.15em)]) #h(0.3em) #context{str(counter(page).at(locate(<ch50-stdlib>)).first())}]

#pagebreak()

// ── Preface ──

#include "chapters/ch00-preface.typ"
#show heading: set heading(numbering: "1.1")
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

#include "chapters/ch01-introduction.typ"
#pagebreak()
#include "chapters/ch02-licensing.typ"
#pagebreak()
#include "chapters/ch03-transformation-in-integration.typ"
#pagebreak()
#include "chapters/ch04-getting-started.typ"
#pagebreak()
#include "chapters/ch05-command-line.typ"
#pagebreak()
#include "chapters/ch06-the-three-executables.typ"
#pagebreak()
#include "chapters/ch07-the-ide.typ"
#pagebreak()
#include "chapters/ch08-language-fundamentals.typ"
#pagebreak()
#include "chapters/ch09-operators.typ"
#pagebreak()
#include "chapters/ch10-universal-data-model.typ"
#pagebreak()
#include "chapters/ch11-schema-to-schema-mapping.typ"
#pagebreak()
#include "chapters/ch12-usdl.typ"
#pagebreak()
#include "chapters/ch13-format-support.typ"
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

#include "chapters/ch14-expressions-and-operators.typ"
#pagebreak()
#include "chapters/ch15-functions-and-lambdas.typ"
#pagebreak()
#include "chapters/ch16-standard-library.typ"
#pagebreak()
#include "chapters/ch17-security-library.typ"
#pagebreak()
#include "chapters/ch18-pattern-matching.typ"
#pagebreak()
#include "chapters/ch19-schema-validation.typ"
#pagebreak()
#include "chapters/ch20-pipeline-chaining.typ"
#pagebreak()
#include "chapters/ch21-data-restructuring.typ"
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

#include "chapters/ch22-xml-transformations.typ"
#pagebreak()
#include "chapters/ch23-xml-attribute-design.typ"
#pagebreak()
#include "chapters/ch24-json-transformations.typ"
#pagebreak()
#include "chapters/ch25-csv-transformations.typ"
#pagebreak()
#include "chapters/ch26-yaml-transformations.typ"
#pagebreak()
#include "chapters/ch27-odata-transformations.typ"
#pagebreak()
#include "chapters/ch28-schema-formats.typ"
#pagebreak()
#include "chapters/ch29-xsd-patterns-deep-dive.typ"
#pagebreak()
#include "chapters/ch30-cross-format-patterns.typ"
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

#include "chapters/ch31-enterprise-integration.typ"
#pagebreak()
#include "chapters/ch32-engine-lifecycle.typ"
#pagebreak()
#include "chapters/ch33-cloud-deployment.typ"
#pagebreak()
#include "chapters/ch34-sdks-and-wrappers.typ"
#pagebreak()
#include "chapters/ch35-migration-guides.typ"
#pagebreak()
#include "chapters/ch36-performance-and-optimization.typ"
#pagebreak()
#include "chapters/ch37-message-parsing-and-memory.typ"
#pagebreak()
#include "chapters/ch38-logging-and-compliance.typ"
#pagebreak()
#include "chapters/ch39-quality-assurance.typ"
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

#include "chapters/ch40-semantic-validation.typ"
#pagebreak()
#include "chapters/ch41-api-contracts.typ"
#pagebreak()
#include "chapters/ch42-formats-not-yet-covered.typ"
#pagebreak()
#include "chapters/ch43-competitive-landscape.typ"
#pagebreak()
#include "chapters/ch44-open-m-teaser.typ"
#pagebreak()
#include "chapters/ch45-ai-and-utlx.typ"
#pagebreak()
#include "chapters/ch46-why-kotlin-and-graalvm.typ"
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

#include "chapters/ch47-case-studies.typ"
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

#include "chapters/ch48-grammar-reference.typ"
#pagebreak()
#include "chapters/ch49-appendices.typ"
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

// Exclude ch50 headings from front Table of Contents
#show heading: set heading(outlined: false)
#include "chapters/ch50-stdlib-reference.typ"

#pagebreak()

// ── Index (Full Table of Contents) ──
#show heading: set heading(outlined: true)

#heading(level: 1, numbering: none)[INDEX]

#context {
  let entries = query(heading).filter(h => h.body != [INDEX])
  for entry in entries {
    let nums = counter(heading).at(entry.location())
    if nums.first() == 0 { continue }
    let indent-amount = (entry.level - 1) * 2em
    let page-num = counter(page).at(entry.location()).first()
    let num-str = nums.slice(0, calc.min(entry.level, nums.len())).map(str).join(".")
    let body = if entry.level == 1 {
      strong[#num-str #h(0.5em) #entry.body]
    } else {
      [#num-str #h(0.5em) #entry.body]
    }
    if entry.level == 1 { v(0.3em) }
    h(indent-amount)
    link(entry.location())[#body]
    h(0.3em)
    box(width: 1fr, repeat[.#h(0.15em)])
    h(0.3em)
    str(page-num)
    linebreak()
  }
}

#pagebreak()

// ── Back Page ──

#set page(fill: rgb("#CC0000"))
#set text(fill: white, size: 10pt)

#v(0.5cm)

#text(size: 18pt, weight: "bold")[UTL-X: One Language, All Formats]

#v(0.6cm)

Integration mapping hasn't meaningfully advanced in a decade. XSLT works — for XML. The moment your source or target is JSON, CSV, or YAML, you are on your own. UTL-X eliminates that tax. One transformation language, all formats, open source.

#v(0.4cm)

This book covers:

- The Universal Data Model (UDM) — how UTL-X makes all formats interchangeable
- Writing transformations across XML, JSON, CSV, YAML, and OData
- Schema-to-schema conversion via USDL (XSD, JSON Schema, Avro, Protobuf)
- The three executables: utlx (CLI), utlxd (IDE), utlxe (production engine)
- 652 standard library functions with examples
- Production deployment on Azure with Kafka, Prometheus, and hot-reload
- Data restructuring: groupBy, nestBy, lookupBy, chunkBy, unnest
- Case studies: European e-invoicing (Peppol/UBL) and healthcare (HL7 FHIR)

#v(0.4cm)

Written for integration developers, data engineers, and architects who are tired of maintaining separate transformation tools for every format. Whether you are migrating from XSLT, evaluating alternatives to MuleSoft or Tibco, or building multi-format ETL pipelines — this book shows you how.

#v(1fr)

#line(length: 100%, stroke: 0.5pt + white)

#v(0.4cm)

#grid(
  columns: (1fr, auto),
  gutter: 1cm,
  [
    #text(weight: "bold", size: 11pt)[GLOMIDCO B.V.] \
    The Netherlands \
    Web: www.glomidco.com

    #v(0.3cm)

    #text(size: 8pt)[GLOMIDCO B.V. is a technology company specialising in enterprise integration, data transformation, and middleware solutions.]
  ],
  [
    #block(fill: white, inset: 6pt, radius: 3pt)[
      #image("pictures/isbn-barcode.png", width: 4cm)
    ]
  ]
)
