= Standard Library Reference <ch50-stdlib>

This appendix lists the UTL-X standard library functions alphabetically. Each entry shows:
- *Signature:* `functionName(required, required, optional?)` — parameters with `?` are optional
- *Category tag:* Str, Arr, Obj, Num, Date, Fmt, XML, JSON, CSV, YAML, Sec, Bin, Geo, URL, Sys, Type
- *Example:* practical, runnable usage
- *Anti-pattern:* what NOT to do (where applicable)

#heading(level: 2, outlined: false, numbering: none)[Function Index]

#{
  set text(size: 7.5pt)
  set par(leading: 0.4em, spacing: 0.4em)
  show text.where(fill: gray): none
  columns(3, gutter: 1em, context {
    let current-page = here().page()
    let entries = query(heading.where(level: 3)).filter(h => h.location().page() > current-page)
    for entry in entries {
      let page-num = counter(page).at(entry.location()).first()
      grid(
        columns: (1fr, 2em),
        rows: (1.1em,),
        box(clip: true, height: 1.1em, link(entry.location())[#entry.body]),
        align(right)[#str(page-num)],
      )
    }
  })
}

#pagebreak()

#include "ch50-stdlib-A-D.typ"
#include "ch50-stdlib-E-I.typ"
#include "ch50-stdlib-J-O.typ"
#include "ch50-stdlib-P-Z.typ"
