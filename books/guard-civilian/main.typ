// UTL-X Guard — The Civilian Edition
// Compile: typst compile main.typ "UTL-X Guard - Civilian Edition.pdf"

#set document(
  title: "UTL-X Guard — The Civilian Edition",
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
      let name = [UTL-X Guard]
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

// ── Civilian (red) palette ──
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
    #text(size: 80pt, weight: "bold", font: "Arial", fill: rgb("#333333"))[UTL]#text(size: 80pt, weight: "bold", font: "Arial", fill: red-main)[X]#text(size: 30pt, weight: "bold", font: "Arial", fill: rgb("#333333"))[ Guard]
    #v(0.3cm)
    #text(size: 15pt, style: "italic", fill: rgb("#333333"))[The Civilian Edition]
    #v(0.4cm)
    #image("pictures/utlx-knife-red-transparent.svg", width: 16cm)
  ]
]

#block(width: 100%, inset: (x: 1.5cm, y: 0.5cm))[
  #align(center)[
    #text(size: 14pt, weight: "bold")[A content guard for open standards]
    #v(0.4cm)
    #text(size: 11pt)[rebuild what is clean, reject what is wrong — every format, one rule set]
  ]
]

#v(1fr)
#align(center)[#text(size: 13pt)[Ir. Marcel A. Grauwen]]
#v(1fr)
#align(center)[#text(size: 10pt, style: "italic", fill: luma(110))[MIL-ready by design, proven in civilian service]]
#v(0.3cm)
#align(center)[#text(size: 8pt, fill: rgb("#AAAAAA"))[Exploratory edition — 2026]]
#v(0.5cm)
]

#pagebreak()

// ── Colophon ──
#set text(size: 9pt)
#v(1fr)

*UTL-X Guard — The Civilian Edition*

Copyright \u{00A9} 2026 Ir. Marcel A. Grauwen. All rights reserved.

Published by GLOMIDCO B.V., The Netherlands. Exploratory edition, 2026.

UTL-X is open-source software (AGPL-3.0), freely available at `https://github.com/grauwen/utl-x`.
This book depends on *UTL-X 1.1* (the `validate.*` semantic-validation extension).

This is the civilian companion to _UTL-X MIL — The Content Guard_ (the defence edition). The two share
one engine and one architecture; the MIL edition adds restricted-format packs and higher assurance — not
a different design. See also _UTL-X: One Language, All Formats_ and _UTLXe on Azure_.

*Status — exploratory.* This book is a capability and go-to-market study of a civilian, open-standards
content guard built on UTL-X. It is not a product specification, nor legal, export-control or
accreditation advice. External standards are named indicatively; verify against primary sources.

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

// ══ PART I — THE GUARD ══
#part-divider("I", "The Guard", "What it is, what it does, where it fits, and the formats it speaks.")
#include "chapters/01-the-guard.typ"
#pagebreak()
#include "chapters/02-what-it-does.typ"
#pagebreak()
#include "chapters/03-where-it-fits.typ"
#pagebreak()
#include "chapters/04-open-standards.typ"

// ══ PART II — HOW IT WORKS ══
#part-divider("II", "How It Works", "One language, one model, binary decoding, validation, hardening, and the verdict.")
#include "chapters/05-one-language.typ"
#pagebreak()
#include "chapters/06-every-format.typ"
#pagebreak()
#include "chapters/07-validation-and-hardening.typ"
#pagebreak()
#include "chapters/08-rules-verdicts-coverage.typ"

// ══ PART III — FIELDING & BUSINESS ══
#part-divider("III", "Fielding It", "Assurance, deployment, and the path to market.")
#include "chapters/09-assurance-and-market.typ"

#pagebreak()
#include "chapters/appendix-a-quick-reference.typ"
#pagebreak()
#include "chapters/appendix-b-format-catalogue.typ"
#pagebreak()
#include "chapters/appendix-c-sources.typ"
