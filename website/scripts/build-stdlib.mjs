#!/usr/bin/env node
/*
 * Build the per-function stdlib reference for utlx-lang.org.
 *
 * Source: the book's stdlib appendix (books/language/chapters/ch50-stdlib-*.typ),
 * where every function is a `=== name(args) → ret #text(...)[(Cat)]` heading.
 *
 * Output (all committed, so Coolify needs only Node — no pandoc at build time):
 *   website/reference/functions/<name>.md   one SEO page per function
 *   website/reference/stdlib.md              category-grouped A–Z index
 *   website/reference/.sidebar.json          collapsible sidebar data (read by config.mts)
 *
 * Re-run whenever the stdlib chapters change:  npm run stdlib   (needs pandoc on PATH)
 */
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '../..')
const SRC = path.join(root, 'books/language/chapters')
const REF = path.join(root, 'website/reference')
const FUNCS = path.join(REF, 'functions')

const CHAPTERS = ['ch50-stdlib-A-D', 'ch50-stdlib-E-I', 'ch50-stdlib-J-O', 'ch50-stdlib-P-Z']

// Short category codes used in the book → full names + display order.
const CATEGORIES = {
  Str: 'String', Arr: 'Array', Obj: 'Object', Num: 'Math', Type: 'Type',
  Date: 'Date & Time', XML: 'XML', JSON: 'JSON', CSV: 'CSV', YAML: 'YAML',
  Bin: 'Binary', Sec: 'Security', URL: 'URL', Geo: 'Geospatial', Fin: 'Financial',
  Fmt: 'Format', Sys: 'System'
}
const ORDER = ['Core', 'String', 'Array', 'Object', 'Math', 'Type', 'Date & Time',
  'XML', 'JSON', 'CSV', 'YAML', 'Binary', 'Security', 'URL', 'Geospatial',
  'Financial', 'Format', 'System', 'Other']

// Map a (possibly combined like "Num/Arr") code to one full category name.
function fullCategory(code) {
  const primary = code.split('/')[0].trim()
  return CATEGORIES[primary] || 'Other'
}

console.log('Reading stdlib chapters via pandoc…')
let md = ''
for (const ch of CHAPTERS) {
  const p = path.join(SRC, ch + '.typ')
  if (!fs.existsSync(p)) { console.warn(`  ⚠ missing ${ch}.typ`); continue }
  md += '\n' + execFileSync('pandoc', ['-f', 'typst', '-t', 'gfm-raw_html', p],
    { encoding: 'utf8', maxBuffer: 1 << 27 })
}
// Same smart-char normalization the guide conversion uses (… and curly quotes break Vue).
md = md.replace(/…/g, '...').replace(/[“”]/g, '"').replace(/[‘’]/g, "'")

// Split into `### `-delimited entries.
const entries = []
let cur = null
for (const line of md.split('\n')) {
  const h = line.match(/^### (.+?)\s*$/)
  if (h) { if (cur) entries.push(cur); cur = { heading: h[1], body: [] } }
  else if (cur) cur.body.push(line)
}
if (cur) entries.push(cur)

// Keep only real functions:  name(args) → ret (Cat)
const seen = new Set()
const funcs = []
let skipped = 0, dupes = 0
for (const e of entries) {
  const m = e.heading.match(/^([A-Za-z][A-Za-z0-9]*)\((.*?)\)\s*→\s*(.+?)\s*\(([^()]+)\)\s*$/)
  if (!m) { skipped++; continue }
  const [, name, argsRaw, ret, code] = m
  // Case-insensitive: a page filename like typeOf.md / typeof.md collides on a
  // case-insensitive filesystem (macOS), silently dropping a page. Keep the first.
  if (seen.has(name.toLowerCase())) { dupes++; continue }
  seen.add(name.toLowerCase())
  funcs.push({
    name,
    signature: `${name}(${argsRaw}) → ${ret.trim()}`,
    code: code.trim(),
    category: fullCategory(code),
    body: e.body.join('\n').replace(/\n{3,}/g, '\n\n').trim()
  })
}
funcs.sort((a, b) => a.name.localeCompare(b.name))
console.log(`Parsed ${funcs.length} functions (${dupes} duplicate names skipped, ${skipped} non-function sections skipped).`)

// First prose line of the body → SEO description.
function describe(f) {
  const line = f.body.split('\n').find(l => l.trim() && !l.startsWith('```') && !l.startsWith('-') && !l.startsWith('#'))
  let d = (line || `${f.category} function.`)
    .replace(/\\(.)/g, '$1')   // undo pandoc backslash-escapes (e.g. -\> → ->) for YAML safety
    .replace(/`/g, '').replace(/"/g, "'").trim()
  if (d.length > 155) d = d.slice(0, 152).replace(/\s+\S*$/, '') + '...'
  return d
}

// --- Per-function pages ---
fs.rmSync(FUNCS, { recursive: true, force: true })
fs.mkdirSync(FUNCS, { recursive: true })
for (const f of funcs) {
  const page = `---
title: ${f.name}
description: "${f.name} — UTL-X ${f.category} function. ${describe(f)}"
pageClass: stdlib-page
---

# ${f.name}

<p class="stdlib-meta"><code>${f.signature}</code> · <a href="/reference/stdlib#${f.category.toLowerCase().replace(/[^a-z0-9]+/g, '-')}">${f.category}</a></p>

${f.body}
`
  fs.writeFileSync(path.join(FUNCS, f.name + '.md'), page)
}

// --- Category-grouped index (website/reference/stdlib.md) ---
const byCat = {}
for (const f of funcs) (byCat[f.category] ??= []).push(f)
const cats = ORDER.filter(c => byCat[c])

let index = `---
title: Standard Library Reference
description: "Complete UTL-X standard library — ${funcs.length} functions across ${cats.length} categories, each with its own reference page, signature, and examples."
pageClass: stdlib-page
---

# Standard Library Reference

UTL-X ships **${funcs.length} built-in functions** across ${cats.length} categories.
Every function has its own page with signature, parameters, and runnable examples.
Use the sidebar or the local search (top right) to jump to any function.

`
for (const c of cats) {
  const list = byCat[c].sort((a, b) => a.name.localeCompare(b.name))
  const anchor = c.toLowerCase().replace(/[^a-z0-9]+/g, '-')
  index += `## ${c} {#${anchor}}\n\n<span class="stdlib-count">${list.length} functions</span>\n\n`
  // Dense responsive grid of name links — fills the width, no ugly wrapping.
  // Raw <a> tags (no blank lines) so markdown-it keeps them as direct grid children.
  index += '<div class="fn-grid">\n'
  for (const f of list) {
    index += `<a href="/reference/functions/${f.name}"><code>${f.name}</code></a>\n`
  }
  index += '</div>\n\n'
}
fs.writeFileSync(path.join(REF, 'stdlib.md'), index)

// --- Sidebar data for config.mts ---
const sidebar = cats.map(c => ({
  text: `${c} (${byCat[c].length})`,
  collapsed: true,
  items: byCat[c].sort((a, b) => a.name.localeCompare(b.name))
    .map(f => ({ text: f.name, link: `/reference/functions/${f.name}` }))
}))
fs.writeFileSync(path.join(REF, '.sidebar.json'), JSON.stringify(sidebar, null, 2))

console.log(`Wrote ${funcs.length} pages → ${FUNCS}`)
console.log(`Wrote index → ${path.join(REF, 'stdlib.md')}`)
console.log(`Wrote sidebar → ${path.join(REF, '.sidebar.json')}  (${cats.length} categories)`)
