# F27 — Inline expressions and a raw text output format

**Status:** Proposed
**Component:** `:modules:cli`
**Raised:** 2026-11-11, from replacing `jq` with UTL-X in `scripts/check-action-versions.sh`
**Motivation:** UTL-X cannot currently be used the way `jq` is used — as a one-liner in a shell pipeline

## The gap

UTL-X can already do the work. What it cannot do is be *reached for* in a
script. Two things stand in the way.

### 1. Every query needs a file on disk

```bash
# jq
gh api repos/foo/bar/releases/latest --jq '.tag_name'

# UTL-X today
cat > /tmp/q.utlx <<'X'
%utlx 1.0
input json
output csv
---
input.tag_name
X
gh api repos/foo/bar/releases/latest | utlx transform /tmp/q.utlx
```

Five lines and a temp file against one token. For a transformation worth
versioning that is the right trade; for `.tag_name` it is not, and the result is
that nobody uses UTL-X here.

**Proposed:**

```bash
gh api repos/foo/bar/releases/latest | utlx -e 'input.tag_name' --from json --to text
```

`-e/--expr` takes the body of a script. The header is supplied by `--from`/`--to`
(both already exist as options), defaulting to `auto` input as identity mode
already does.

### 2. There is no raw text output

Output formats are `auto, xml, json, csv, yaml, odata, osch, tsch, xsd, jsch,
avro, proto`. For shell capture every one of them is wrong:

| `output` | result for `input.tag_name` | usable in `$(...)` |
|---|---|---|
| `json` | `"v3.0.4"` | no — quoted |
| `yaml` | `v3.0.4` | yes, by luck |
| `csv` | `v3.0.4` | yes, by luck |

`csv` on a bare scalar happens to emit the value with no quotes and no header,
and that is what `scripts/utlx/gh-field.utlx` relies on today. It works, but
nobody would guess it, and it stops working the moment the result is a list —
`csv` then emits a `value` header line that has to be stripped:

```
value        <- unwanted
v3.0.4
v3.0.3
```

**Proposed:** an `output text` format. Scalar renders as itself; an array
renders as one element per line, no header; an object is an error (or renders as
`key\tvalue`, to be decided). This is `jq -r`.

## Why it is worth doing

The project positions UTL-X against jq — `docs/comparison/vs-jq.md` exists, and
`docs/examples/jq-to-utlx-examples.md` is a migration guide. Someone following
that guide hits both of these in their first five minutes, because the whole
point of jq is that it is a shell tool.

This repo is the first example: `scripts/check-action-versions.sh` still uses
`gh --jq` for one step, because the UTL-X spelling was not worth the ceremony.

## Not in scope

Projection (`input.items.*.price`) is broken independently — see
[B48](../bugs-fixed/B48-path-steps-do-not-project-across-a-sequence.md) and
[B49](../bugs-fixed/B49-documented-wildcard-and-descent-selectors-are-parse-errors.md).
Those must be fixed for `-e` to be much use, since most one-liners project.
