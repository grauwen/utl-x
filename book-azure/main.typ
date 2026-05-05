// UTLXe on Azure — Deployment and Operations Guide
// Compile: typst compile main.typ "UTLXe on Azure.pdf"
// Watch:   typst watch main.typ "UTLXe on Azure.pdf"

#set document(
  title: "UTLXe on Azure — Deployment and Operations Guide",
  author: "Ir. Marcel A. Grauwen",
  date: datetime.today(),
)

#set text(font: "New Computer Modern", size: 10pt)
#set page(
  paper: "a4",
  margin: (top: 3cm, bottom: 3cm, left: 2cm, right: 2cm),
  header: context {
    if counter(page).get().first() > 2 {
      let current-page = here().page()
      let all-headings = query(heading.where(level: 1))
      let chapter-name = {
        let found = [UTLXe on Azure]
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
#show raw.where(block: true): it => block(
  fill: luma(245),
  inset: 10pt,
  radius: 4pt,
  width: 100%,
  text(size: 8.5pt, it),
)

// ── Title Page ──

#page(margin: 0pt, fill: luma(242))[
#set par(spacing: 0pt)
#set block(spacing: 0pt)
// Top section — same image as the main book
#image("pictures/coverpage/cover-top-hires.png", width: 100%)

// UTL-X title + logo area — light gray background
#block(fill: luma(242), width: 100%, inset: (x: 1.5cm, top: 0.1cm, bottom: 0.5cm))[
  #align(center)[
    #text(size: 80pt, weight: "bold", font: "Arial", fill: rgb("#333333"))[UTL]#text(size: 80pt, weight: "bold", font: "Arial", fill: rgb("#CC0000"))[X]#text(size: 40pt, weight: "bold", font: "Arial", fill: rgb("#0078D4"))[e]
    #v(0.4cm)
    #text(size: 16pt, style: "italic", fill: rgb("#333333"))[Production Transformation Engine]
    #v(0.5cm)
    #image("pictures/utlx-logos/utlx-logo-5formats-red.png", width: 13cm)
  ]
]

// Subtitle bar — book title
#block(width: 100%, inset: (x: 1.5cm, y: 0.6cm))[
  #align(center)[
    #text(size: 15pt, weight: "bold")[Deployment and Operations Guide]
    #v(0.6cm)
    #text(size: 13pt)[Running UTLXe on Azure Container Apps — From First Deploy to Production]
  ]
]

// Author area
#v(1fr)
#align(center)[
  #text(size: 13pt)[Ir. Marcel A. Grauwen]
]
#v(1fr)

// Footer
#align(center)[
  #text(size: 8pt, fill: rgb("#AAAAAA"))[First Edition — 2026]
]
#v(0.5cm)
] // end of #page(margin: 0pt)

#pagebreak()

// ── Copyright / Colophon Page ──

#set text(size: 9pt)
#v(1fr)

*UTLXe on Azure — Deployment and Operations Guide*

Copyright \u{00A9} 2026 Ir. Marcel A. Grauwen. All rights reserved.

Published by GLOMIDCO B.V., The Netherlands

First edition, 2026.

No part of this publication may be reproduced, stored in a retrieval system, or transmitted in any form or by any means without the prior written permission of the author, except for brief quotations in reviews and critical articles.

UTL-X is open source software. The language specification, CLI tool, and standard library are freely available at `https://github.com/grauwen/utl-x`.

This is a companion guide to _UTL-X: One Language, All Formats_ (ISBN 978-90-819728-1-9), which covers the complete language specification, standard library, and format system.

Typeset with Typst in New Computer Modern.

#set text(size: 10pt)
#pagebreak()

// ── Table of Contents ──

#outline(
  title: [Table of Contents],
  indent: 2em,
  depth: 2,
)

#pagebreak()

#include "chapters/ch00-why-utlxe.typ"
#pagebreak()
#include "chapters/ch01-quick-start.typ"
#pagebreak()
#include "chapters/ch02-writing-transformations.typ"
#pagebreak()
#include "chapters/ch03-admin-api.typ"
#pagebreak()
#include "chapters/ch04-azure-services.typ"
#pagebreak()
#include "chapters/ch05-monitoring.typ"
#pagebreak()
#include "chapters/ch06-operations.typ"
#pagebreak()
#include "chapters/ch07-persistence-and-scaling.typ"
#pagebreak()
#include "chapters/ch08-security.typ"
#pagebreak()
#include "chapters/ch09-cicd.typ"
#pagebreak()
#include "chapters/ch10-troubleshooting.typ"
#pagebreak()
#include "chapters/appendix-a-api-reference.typ"
#pagebreak()
#include "chapters/appendix-b-config-reference.typ"
#pagebreak()
#include "chapters/appendix-c-utlx-quick-reference.typ"
