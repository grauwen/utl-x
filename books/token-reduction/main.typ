// UTLX for AI Token Reduction — A Lean & Six-Sigma Approach
// Compile: typst compile main.typ "UTLX for AI Token Reduction.pdf"

#set document(
  title: "UTLX for AI Token Reduction",
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
        let found = [UTLX for AI Token Reduction]
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
    #text(size: 22pt, weight: "bold")[UTLX for AI Token Reduction]
    #v(0.4cm)
    #text(size: 13pt)[A Lean & Six-Sigma Approach to Trimming Structured Input for LLMs]
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

*UTLX for AI Token Reduction — A Lean & Six-Sigma Approach to Trimming Structured Input for LLMs*

Copyright \u{00A9} 2026 Ir. Marcel A. Grauwen. All rights reserved.

Published by GLOMIDCO B.V., The Netherlands

First edition, 2026.

No part of this publication may be reproduced, stored in a retrieval system, or transmitted in any form or by any means without the prior written permission of the author, except for brief quotations in reviews and critical articles.

UTLX is open source software. The language specification, CLI tool, and standard library are freely available at `https://github.com/grauwen/utl-x`.

This is a companion article to *Many to One: The Theory of N:1 Data Mapping* and *UTLX: One Language, All Formats*. Where those develop the theory and teach the language, this one applies UTLX to a practical problem: reducing the token cost of structured data sent to language models.

Typeset with Typst in New Computer Modern.

#set text(size: 10pt)
#pagebreak()

// ── About the Author ──

#heading(numbering: none, outlined: false)[About the Author]

*Ir. Marcel A. Grauwen* is a Dutch software engineer and architect with over twenty-five years of experience in enterprise integration, data transformation, and middleware platforms.

He designed UTLX — one transformation language that works on all data formats, runs anywhere, and belongs to no vendor — and built its runtime, standard library, and the USDL schema-classification system. This article grew out of a practical question raised while deploying UTLX alongside LLM pipelines: how much of what we send a model is waste, and how much of that can UTLX remove safely?

Marcel holds an Ir. degree (Master of Science in Engineering) and is based in the Netherlands.

#pagebreak()

// ── Abstract ──

#block(fill: luma(245), inset: 12pt, radius: 4pt, width: 100%)[
  #text(weight: "bold")[Thesis.] The structured data we feed to language models is full of waste. Some of that waste is _pure_ — the same information, encoded in a costlier shape — and can be removed deterministically with zero loss. The rest is _content_ the model may or may not need, and can only be removed by *measuring*, the way a Six-Sigma process reduces variation: define, measure, analyse, improve, control. UTLX — format-agnostic, path-addressable, deterministic — is the right tool for both, and it already has most of the machinery.
]

#v(0.6cm)

// ── Table of Contents ──

#outline(
  title: [Contents],
  indent: 2em,
  depth: 2,
)

#pagebreak()

// ── Body ──

#include "chapters/body.typ"
