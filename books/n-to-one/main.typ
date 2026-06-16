// Many to One — The Theory of N:1 Data Mapping
// Compile: typst compile main.typ "Many to One - The Theory of N to 1 Data Mapping.pdf"
// Watch:   typst watch main.typ "Many to One - The Theory of N to 1 Data Mapping.pdf"

#set document(
  title: "Many to One: The Theory of N:1 Data Mapping",
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
        let found = [Many to One]
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
#image("pictures/coverpage/cover-top-hires.png", width: 100%)

#block(fill: luma(242), width: 100%, inset: (x: 1.5cm, top: 0.1cm, bottom: 0.5cm))[
  #align(center)[
    #text(size: 80pt, weight: "bold", font: "Arial", fill: rgb("#333333"))[UTL]#text(size: 80pt, weight: "bold", font: "Arial", fill: rgb("#CC0000"))[X]
    #v(0.4cm)
    #text(size: 16pt, style: "italic", fill: rgb("#333333"))[#text(weight: "bold")[U]niversal #text(weight: "bold")[T]ransformation #text(weight: "bold")[L]anguage e#text(weight: "bold", fill: rgb("#CC0000"))[X]tended]
    #v(0.5cm)
    #image("pictures/utlx-logos/utlx-logo-5formats-red.png", width: 13cm)
  ]
]

#block(width: 100%, inset: (x: 1.5cm, y: 0.6cm))[
  #align(center)[
    #text(size: 22pt, weight: "bold")[Many to One]
    #v(0.4cm)
    #text(size: 13pt)[The Theory of N:1 Data Mapping — the Universal Data Model, Message Contracts, and Schema-Driven Mapping]
  ]
]

#v(1fr)
#align(center)[
  #text(size: 13pt)[Ir. Marcel A. Grauwen]
]
#v(1fr)

#align(center)[
  #text(size: 8pt, fill: rgb("#AAAAAA"))[First Edition — 2026]
]
#v(0.5cm)
]

#pagebreak()

// ── Copyright / Colophon Page ──

#set text(size: 9pt)
#v(1fr)

*Many to One: The Theory of N:1 Data Mapping — the Universal Data Model, Message Contracts, and Schema-Driven Mapping*

Copyright \u{00A9} 2026 Ir. Marcel A. Grauwen. All rights reserved.

Published by GLOMIDCO B.V., The Netherlands

ISBN to be assigned.

First edition, 2026.

No part of this publication may be reproduced, stored in a retrieval system, or transmitted in any form or by any means without the prior written permission of the author, except for brief quotations in reviews and critical articles.

UTL-X is open source software. The language specification, CLI tool, and standard library are freely available at `https://github.com/grauwen/utl-x`.

This is a companion volume to *UTL-X: One Language, All Formats*. Where that book teaches the language, this one develops the underlying theory of mapping many inputs to one output.

Typeset with Typst in New Computer Modern.

#set text(size: 10pt)
#pagebreak()

// ── About the Author ──

#heading(numbering: none, outlined: false)[About the Author]

#show heading: set heading(numbering: none)

*Ir. Marcel A. Grauwen* is a Dutch software engineer and architect with over twenty-five years of experience in enterprise integration, data transformation, and middleware platforms.

He has designed and built integration solutions on Tibco BusinessWorks, MuleSoft, SAP CPI, Azure Logic Apps, and IBM Integration Bus, and grew increasingly frustrated with the fundamental limitation they all shared: format-specific transformation logic tied to vendor platforms.

UTL-X was born from that frustration — one transformation language that works on all data formats, runs anywhere, and belongs to no vendor. Marcel designed the language, built the runtime (CLI, IDE daemon, and production engine), wrote the standard library, and created the USDL schema-classification system. This book grew out of the design work behind the Message Contract mode and the Open-M multi-input pipeline: the recurring question of *how a transformation that merges many inputs into one contract can be reasoned about, not just written.*

Marcel holds an Ir. degree (Master of Science in Engineering) and is based in the Netherlands.

#pagebreak()

// ── Table of Contents ──

#outline(
  title: [Table of Contents],
  indent: 2em,
  depth: 2,
)

#pagebreak()

// ── Preface ──

#include "chapters/ch00-preface.typ"
#pagebreak()
#include "chapters/ch00b-introduction.typ"
#show heading: set heading(numbering: "1.1")
#pagebreak()

// ── Part I: Foundations ──

#align(center)[
  #v(6cm)
  #text(size: 28pt, weight: "bold", fill: rgb("#003366"))[Part I]
  #v(0.5cm)
  #text(size: 18pt, fill: rgb("#666666"))[Foundations]
  #v(1cm)
  #text(size: 12pt, fill: rgb("#999999"))[The problem, the data model, and the two modes]
]
#pagebreak()

#include "chapters/ch01-the-n-to-one-problem.typ"
#pagebreak()
#include "chapters/ch02-universal-data-model.typ"
#pagebreak()
#include "chapters/ch03-two-modes.typ"
#pagebreak()

// ── Part II: The Theory of Mapping ──

#align(center)[
  #v(6cm)
  #text(size: 28pt, weight: "bold", fill: rgb("#003366"))[Part II]
  #v(0.5cm)
  #text(size: 18pt, fill: rgb("#666666"))[The Theory of Mapping]
  #v(1cm)
  #text(size: 12pt, fill: rgb("#999999"))[Schemas as graphs, matching, the formal mapping object, and the correspondence set]
]
#pagebreak()

#include "chapters/ch04-schemas-as-graphs.typ"
#pagebreak()
#include "chapters/ch05-schema-matching.typ"
#pagebreak()
#include "chapters/ch06-the-mapping-as-a-formal-object.typ"
#pagebreak()
#include "chapters/ch07-the-correspondence-set.typ"
#pagebreak()

// ── Part III: From Theory to Transformation ──

#align(center)[
  #v(6cm)
  #text(size: 28pt, weight: "bold", fill: rgb("#003366"))[Part III]
  #v(0.5cm)
  #text(size: 18pt, fill: rgb("#666666"))[From Theory to Transformation]
  #v(1cm)
  #text(size: 12pt, fill: rgb("#999999"))[Typing inputs, analysing outputs, inferring functions, strategy-first generation, and the place of AI]
]
#pagebreak()

#include "chapters/ch08-typing-the-inputs.typ"
#pagebreak()
#include "chapters/ch09-output-analysis.typ"
#pagebreak()
#include "chapters/ch10-function-inference.typ"
#pagebreak()
#include "chapters/ch11-strategy-first-generation.typ"
#pagebreak()
#include "chapters/ch12-proposing-a-mapping.typ"
#pagebreak()
#include "chapters/ch13-ai-author-not-executor.typ"
#pagebreak()

// ── Part IV: Scope & Rationale ──

#align(center)[
  #v(6cm)
  #text(size: 28pt, weight: "bold", fill: rgb("#003366"))[Part IV]
  #v(0.5cm)
  #text(size: 18pt, fill: rgb("#666666"))[Scope & Rationale]
  #v(1cm)
  #text(size: 12pt, fill: rgb("#999999"))[Why a single output, and the boundaries of the model]
]
#pagebreak()

#include "chapters/ch14-why-one-output.typ"
#pagebreak()

// ── Appendix: Axioms and Principles ──

#include "chapters/ch-appendix-axioms.typ"
#pagebreak()

// ── Part V: The Classical Foundation ──

#align(center)[
  #v(6cm)
  #text(size: 28pt, weight: "bold", fill: rgb("#003366"))[Part V]
  #v(0.5cm)
  #text(size: 18pt, fill: rgb("#666666"))[The Classical Foundation]
  #v(1cm)
  #text(size: 12pt, fill: rgb("#999999"))[The theory of 1:1 mapping, and an annotated bibliography]
]
#pagebreak()

#include "chapters/ch15-theory-of-one-to-one-mapping.typ"
#pagebreak()
#include "chapters/ch16-bibliography.typ"
#pagebreak()

// ── Back Page ──

#set page(fill: rgb("#CC0000"))
#set text(fill: white, size: 10pt)

#v(0.5cm)

#text(size: 18pt, weight: "bold")[Many to One]

#v(0.6cm)

Every integration eventually faces the same shape: several inputs — a payload, a prior pipeline step, a lookup table, a handful of configuration values — must become *one* output that satisfies a fixed contract. Writing that transformation is a craft. *Reasoning* about it is a theory.

#v(0.4cm)

This book develops that theory. It treats a mapping not as a pile of field assignments but as a formal object: a schema graph, a set of source-to-target dependencies, a scored correspondence set. It draws the line — sharply — between Execution mode, which transforms real data, and Message Contract mode, which reasons about schemas before a single byte flows. And it grounds everything in the Universal Data Model, the one representation that makes "all formats" mean something.

#v(0.4cm)

A companion to *UTL-X: One Language, All Formats* — for the architect who wants to understand *why* an N:1 mapping is correct, not just that it runs.

#v(1fr)

#align(right)[#text(size: 9pt)[GLOMIDCO B.V. · The Netherlands · 2026]]
