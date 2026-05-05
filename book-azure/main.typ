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
  margin: (top: 3cm, bottom: 3cm, left: 2.5cm, right: 2.5cm),
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
#show raw.where(block: true): block.with(
  fill: luma(245),
  inset: 10pt,
  radius: 4pt,
  width: 100%,
)

// ── Cover page ──
#page(header: none)[
  #v(4cm)
  #align(center)[
    #text(size: 28pt, weight: "bold")[UTLXe on Azure]
    #v(0.5cm)
    #text(size: 16pt, fill: gray)[Deployment and Operations Guide]
    #v(2cm)
    #text(size: 14pt)[Ir. Marcel A. Grauwen]
    #v(0.5cm)
    #text(size: 11pt, fill: gray)[GLOMIDCO B.V.]
    #v(4cm)
    #text(size: 10pt, fill: gray)[
      Companion guide to _UTL-X: One Language, All Formats_\
      For UTLXe v1.0 on Azure Container Apps
    ]
  ]
]

// ── Table of contents ──
#page(header: none)[
  #outline(indent: 1.5em, depth: 2)
]

// ── Chapters ──
#include "chapters/ch01-quick-start.typ"
#include "chapters/ch02-writing-transformations.typ"
#include "chapters/ch03-admin-api.typ"
#include "chapters/ch04-azure-services.typ"
#include "chapters/ch05-monitoring.typ"
#include "chapters/ch06-operations.typ"
#include "chapters/ch07-persistence-and-scaling.typ"
#include "chapters/ch08-security.typ"
#include "chapters/ch09-cicd.typ"
#include "chapters/ch10-troubleshooting.typ"
#include "chapters/appendix-a-api-reference.typ"
#include "chapters/appendix-b-config-reference.typ"
#include "chapters/appendix-c-utlx-quick-reference.typ"
