// UTL-X — The Mapper (Civilian Edition)
// Compile: typst compile main.typ "UTL-X Mapper - Civilian Edition.pdf"

#set document(
  title: "UTL-X — The Mapper (Civilian Edition)",
  author: "Ir. Marcel A. Grauwen",
  date: datetime.today(),
)

#set text(font: "New Computer Modern", size: 10pt)
#set page(
  paper: "a4",
  margin: (top: 3cm, bottom: 3cm, left: 2.5cm, right: 2.5cm),
  header: context {
    if counter(page).get().first() > 2 {
      let all-headings = query(heading.where(level: 1))
      let current = here().page()
      let name = [UTL-X Mapper]
      for h in all-headings {
        if h.location().page() <= current { name = h.body }
      }
      set text(size: 9pt, fill: gray)
      [#emph(name) #h(1fr) #counter(page).display()]
    }
  },
)

#set heading(numbering: "1.1")
#set par(justify: true)

#let red-main = rgb("#CC0000")

#show raw.where(block: true): it => block(
  fill: luma(245), inset: 10pt, radius: 4pt, width: 100%, text(size: 8.5pt, it),
)

// ── Title page ──
#page(margin: 0pt, fill: luma(242))[
#set par(spacing: 0pt)
#set block(spacing: 0pt)
#image("pictures/coverpage/cover-top-hires.png", width: 100%)

#block(fill: luma(242), width: 100%, inset: (x: 1.5cm, top: 0.1cm, bottom: 0.4cm))[
  #align(center)[
    #text(size: 80pt, weight: "bold", font: "Arial", fill: rgb("#333333"))[UTL]#text(size: 80pt, weight: "bold", font: "Arial", fill: red-main)[X]#text(size: 30pt, weight: "bold", font: "Arial", fill: rgb("#333333"))[ Mapper]
    #v(0.3cm)
    #text(size: 15pt, style: "italic", fill: rgb("#333333"))[The Civilian Edition]
    #v(0.4cm)
    #image("pictures/utlx-knife-red-transparent.svg", width: 16cm)
  ]
]

#block(width: 100%, inset: (x: 1.5cm, y: 0.5cm))[
  #align(center)[
    #text(size: 14pt, weight: "bold")[An assured message-mapping component]
    #v(0.4cm)
    #text(size: 11pt)[every open format, one model, validated on UTL-X 1.1]
  ]
]

#v(1fr)
#align(center)[#text(size: 13pt)[Ir. Marcel A. Grauwen]]
#v(1fr)
#align(center)[#text(size: 10pt, style: "italic", fill: luma(110))[The short on-ramp — not the full language book]]
#v(0.3cm)
#align(center)[#text(size: 8pt, fill: rgb("#AAAAAA"))[Exploratory edition — 2026]]
#v(0.5cm)
]

#pagebreak()

// ── Colophon ──
#set text(size: 9pt)
#v(1fr)

*UTL-X — The Mapper · Civilian Edition*

Copyright \u{00A9} 2026 Ir. Marcel A. Grauwen. All rights reserved.

Published by GLOMIDCO B.V., The Netherlands. Exploratory edition, 2026.

UTL-X is open-source software (AGPL-3.0), freely available at `https://github.com/grauwen/utl-x`.
This book depends on *UTL-X 1.1* (the `validate.*` semantic-validation extension).

This is the short, component-focused companion to _UTL-X: One Language, All Formats_ (the complete
language specification) — for readers who want to understand UTL-X *as a mapping component* without
reading the full language book. Its security counterpart is _UTL-X Guard — The Civilian Edition_;
its defence sibling is _UTL-X MIL — The Mapper_ (restricted-format packs, higher assurance).

*Status — exploratory.* This book is a capability study of an assured, open-standards message-mapping
component built on UTL-X. It is not a product specification. External standards are named indicatively;
verify against primary sources.

Typeset with Typst in New Computer Modern.

#set text(size: 10pt)
#pagebreak()

#outline(title: [Table of Contents], indent: 2em, depth: 2)
#pagebreak()

// ── Part-divider helper ──
#let part-divider(num, title, blurb) = {
  pagebreak()
  v(1fr)
  align(center)[
    #text(size: 15pt, fill: luma(130), weight: "bold")[PART #num]
    #v(0.35cm)
    #text(size: 30pt, weight: "bold")[#title]
    #v(0.3cm)
    #line(length: 38%, stroke: 1pt + red-main)
    #v(0.35cm)
    #text(size: 12pt, style: "italic", fill: luma(90))[#blurb]
  ]
  v(1.4fr)
  pagebreak()
}

// ══ PART I — THE MAPPER ══
#part-divider("I", "The Mapper", "What it is, the problem it solves, what it maps, and how it is deployed.")
#include "chapters/01-the-mapper.typ"
#pagebreak()
#include "chapters/02-why-mapping.typ"
#pagebreak()
#include "chapters/03-format-landscape.typ"
#pagebreak()
#include "chapters/04-deploying-the-component.typ"

// ══ PART II — HOW IT WORKS ══
#part-divider("II", "How It Works", "One language, one model, binary decoding, validation, and contracts.")
#include "chapters/05-one-language-one-model.typ"
#pagebreak()
#include "chapters/06-every-format.typ"
#pagebreak()
#include "chapters/07-validation.typ"
#pagebreak()
#include "chapters/08-message-contracts.typ"
#pagebreak()
#include "chapters/09-standards-in-depth.typ"

#pagebreak()
#include "chapters/appendix-a-quick-reference.typ"
#pagebreak()
#include "chapters/appendix-b-format-catalogue.typ"
#pagebreak()
#include "chapters/appendix-c-sources.typ"
