= Standard Library Reference <ch50-stdlib>

This appendix lists the UTL-X standard library functions alphabetically. Each entry shows:
- *Signature:* `functionName(required, required, optional?)` — parameters with `?` are optional
- *Category tag:* Str, Arr, Obj, Num, Date, Fmt, XML, JSON, CSV, YAML, Sec, Bin, Geo, URL, Sys, Type
- *Example:* practical, runnable usage
- *Anti-pattern:* what NOT to do (where applicable)

#heading(level: 2, outlined: false, numbering: none)[Function Index]

#context {
  let start-page = here().page()
  let entries = query(heading.where(level: 3)).filter(h => h.location().page() >= start-page)
  let cols = ()
  for entry in entries {
    let page-num = counter(page).at(entry.location()).first()
    cols.push([#link(entry.location())[#entry.body] #box(width: 1fr, repeat[.]) #str(page-num)])
  }
  set text(size: 9pt)
  columns(2, gutter: 1em, cols.join(linebreak()))
}

#pagebreak()

#include "ch50-stdlib-A-D.typ"
#include "ch50-stdlib-E-I.typ"
#include "ch50-stdlib-J-O.typ"
#include "ch50-stdlib-P-Z.typ"
