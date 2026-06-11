// Post-process the IN-IDE docs build (.vitepress/dist-ide): prefix the root-absolute content links
// (/reference/…, /guide/…) — emitted as RAW HTML by build-stdlib.mjs, present in BOTH the static .html
// pages AND the client .js chunks — with the sub-path base, so they don't escape the /utlx-docs/ mount
// and 404. VitePress only base-rewrites Markdown/theme links, not raw HTML, which is why the sidebar
// works but the function cards don't.
//
// Idempotent (the regex matches `="/reference/…`, never the already-prefixed `="/utlx-docs/reference/…`).
// Only runs for the IDE build; the web build (base '/') doesn't call this.

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const DIR = fileURLToPath(new URL('../.vitepress/dist-ide', import.meta.url))
const BASE = '/utlx-docs/'
const RE = /(href|src)="\/(reference|guide)(["/#?])/g

if (!fs.existsSync(DIR)) {
  console.error(`[fix-ide-base] ${DIR} not found — run the build first`)
  process.exit(0)
}

let scanned = 0
let changed = 0

function walk(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name)
    if (e.isDirectory()) { walk(p); continue }
    if (!/\.(html|js)$/.test(e.name)) continue
    scanned++
    const src = fs.readFileSync(p, 'utf8')
    const out = src.replace(RE, (_m, attr, seg, tail) => `${attr}="${BASE}${seg}${tail}`)
    if (out !== src) { fs.writeFileSync(p, out); changed++ }
  }
}

walk(DIR)
console.log(`[fix-ide-base] base-prefixed /reference & /guide links in ${changed}/${scanned} files`)
