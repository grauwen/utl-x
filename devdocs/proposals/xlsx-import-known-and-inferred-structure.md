# Spreadsheet import: two cases, one engine

**Status:** Discussion draft — for Marcel, Owen, and anyone weighing in
**Date:** 2026-11-12
**Context:** Owen B-R has a working local XLSX prototype (named worksheet, first row as
headers, original Excel row numbers carried into the import preview). The question is not
"can we read XLSX" — he has done that — but **where spreadsheet handling belongs** so that
UTL-X's core promise survives contact with a format that has no contract.

---

## 1. The distinction that drives everything

A spreadsheet arrives in one of two states, and they are not variations of one problem —
they are different problems with different answers.

| | **Case A — structure known** | **Case B — structure unknown** |
|---|---|---|
| Origin | Someone filled in *your* template | A user uploads *their* spreadsheet |
| Example | "Download our import template, fill it, upload it" | "Here is the sheet I've kept since 2009" |
| Columns | Known at design time | Discovered at import time |
| Mapping | **Written once, by a human** | **Must be produced per layout** |
| AI needed | **None** | Yes — to propose the mapping |
| Determinism | Total | Total *at execution*; the proposal is probabilistic |

Case A is already solved the moment a reader exists: it is an ordinary UTL-X
transformation, authored in the IDE, reviewed, committed, and run forever.

Case B is the interesting one, and the claim of this document is that it needs **no new
language features and no change to the engine**.

---

## 2. The architectural claim: AI writes the mapping, it never performs it

This is the whole proposal in one line, and everything else follows from it.

UTL-X 1.0 makes four guarantees — **pure, stateless, deterministic, single-pass** — and the
project's standing rule is that *which guarantee an extension breaks* decides whether it is
a minor version, a major version, or a separate edition. That rule is why `ai.*` lives in
`utl-x-infer` under `%utlx 2.0`: calling a model *from inside a transformation* destroys
determinism, so it cannot live in the core language. It is also why `ai.*` is forbidden
outright in the Guard path, where purity *is* the security boundary.

**Generating a transformation is categorically different from being one.**

```
   Case B pipeline

   spreadsheet ──▶ [1 EXTRACT] ──▶ structure ──▶ [2 PROPOSE] ──▶ candidate .utlx
                   deterministic                  AI                     │
                                                                         ▼
                        presented to user ◀── [3 VERIFY] ◀── deterministic engine
                                                   │
                                                   └── fail ──▶ refine (bounded loop)
```

Stage 2 is the only probabilistic step, and its **output is a plain `%utlx 1.0` (or 1.1)
script**. Once written, that script is an ordinary artifact: pure, inspectable, diffable,
versionable, re-runnable, and — importantly — still eligible for the Guard path, because
nothing probabilistic remains in it.

Consequences worth stating plainly:

- **No `ai.*`, no `%utlx 2.0`, no dependency on `utl-x-infer`.** This is a *generator*
  standing beside the engine, not a language feature inside it.
- **The engine does not change.** Not one of the four guarantees is touched.
- **Generate once per *layout*, execute many times.** Inference cost is amortised across
  every row and every subsequent import of the same shape.
- **The user can read what was produced.** A `.utlx` file is reviewable in a way "the LLM
  transformed your data" never is.

Contrast with the obvious alternative — handing rows to a model and asking for JSON back.
That is non-reproducible, unauditable, costs per row, leaks every cell to a third party,
and fails silently. The generator approach avoids all four.

---

## 3. Stage 1 — Extract (deterministic, no AI)

Getting structure out of a spreadsheet is mechanical. It is also harder than it looks, and
worth enumerating so nobody under-budgets it.

XLSX is a ZIP of cross-referencing XML parts (ECMA-376). A single cell value is not
knowable from one stream:

1. **`xl/sharedStrings.xml`** — a cell with `t="s"` holds an *index* into a string table,
   not a string.
2. **`xl/styles.xml` is load-bearing for types.** A date is stored as a serial number.
   `45678` is a date or a quantity depending entirely on its `numFmt`. You must read
   presentation to recover *semantics* — this is unavoidable and is not the same as
   carrying presentation into the data model.
3. **Epoch** — `workbookPr/@date1904`. Mac-origin workbooks are four years out if ignored.
4. **Sparse cells** — `A1`, `C1`, no `B1`. "First row as headers" requires reconstructing a
   grid, not iterating elements.
5. **Formulas** — `<f>` versus the cached `<v>`. Take the cached value; decide explicitly
   what happens when it is absent (a workbook saved by a non-Excel writer may have none).
6. **Multiple sheets, merged cells, two-row headers, leading blank rows, trailing totals.**

Output of this stage is a **structural description**: sheet names, a header row, a column
list with inferred types and a confidence signal, row count, and the anomalies above
flagged rather than silently smoothed over.

Note what this stage is *not*: it is not schema inference in the USDL sense, because a
spreadsheet has no schema to recover. The header row is a **convention**, not a contract.
That is the defining property of the format and the reason stage 2 exists at all.

**This is also where Owen's prototype already sits**, and his instinct to carry original
Excel row numbers through is exactly right — when an import half-fails, "row 47" is the
only thing a user can act on.

---

## 4. Stage 2 — Propose (the AI step)

Inputs: the structural description from stage 1, plus the **target contract** — which the
receiving application always knows, because it is its own import format.

Output: a candidate `.utlx`.

This is not new ground for the project. It is **Message Contract Mode** as already
specified in `devdocs/architecture/ide-modes-specification.md`: build a transformation
against a known input instance and a *declared output schema*, verify by comparing the
result against that schema, and scaffold the gaps. Execution-mode AI assist is implemented
(`IF08`); MC-mode generation is specified and not yet built (`IF11`, `IF20`).

Two lessons already paid for and worth not re-learning:

- **`IB05`** — the MC generate path sent the contract's *schema* format as the
  transformation's *output data* format, so the assistant produced a USDL schema instead of
  a mapping. The input/output/contract triple is easy to confuse in a prompt, and the
  failure is confident and wrong rather than loud.
- **`IF08`'s mode boundary** exists because the same prompt cannot serve both modes. A
  spreadsheet import is MC-shaped (known target contract), not execution-shaped.

**Privacy, which matters for Owen specifically.** Only the *structure* needs to reach a
model — headers, inferred types, and at most a handful of sample values for
disambiguation. Not the collection. A whisky collection is personal data; an enterprise
price list is commercially sensitive. "Structure out, never rows out" should be a stated
property, not an implementation detail, and it is a genuine selling point against
row-level LLM transformation.

---

## 5. Stage 3 — Verify, and the honest limit

The repository already has the verification machinery, and it is stronger than most
generated-code pipelines get:

| check | what it catches |
|---|---|
| `utlx validate` L1 | the script does not parse |
| `utlx validate` L2 | undefined variables, type errors |
| `utlx validate` L3 | input-schema violations |
| `utlx lint` | style, dead code, complexity |
| execute on sample rows | runtime failure |
| output schema comparison | result does not satisfy the target contract |
| **`%utlx 1.1` `validate.*`** | **semantic rules — the only defence where no schema exists** |

That last row is the point worth making to Owen. There is no XSD for a spreadsheet, so
structural validation can only go so far; `validate.*` is what lets you assert that an ABV
is between 0 and 100, a bottling year is not in the future, and a volume is positive. The
format's lack of a contract is precisely the case semantic validation was built for.

### The failure this cannot catch

**A generated mapping can be structurally perfect and semantically wrong.**

Every check above passes if the model maps `Age` to `vintage` instead of `ageStatement`.
Both are numbers. Both satisfy the contract. The import succeeds. The data is wrong, and
nothing in the pipeline knows.

This is the real risk, and it bounds how the feature should be presented:

- The loop must terminate on a **bounded attempt count**, not on "until it passes" — a
  model that keeps failing will eventually produce something that passes for the wrong
  reason.
- The user must see **the proposed column mapping in human terms** ("Distillery → producer,
  Age → ageStatement") before the first real import, not just a success message.
- The preview must show **actual transformed values for real rows**, because
  `Age → vintage` is obvious the moment you see `12` rendered as a year.
- The generated `.utlx` should be **saved and reused**, not regenerated per import. Review
  happens once; after that the mapping is frozen and deterministic. Regenerating each time
  reintroduces non-determinism through the back door.

Frame it as **"we drafted your mapping, please confirm it"** — not "we imported your
spreadsheet".

---

## 6. Where the code goes

```
formats/xlsx/              stage 1. A sibling of formats/avro and formats/protobuf,
                           both of which are comparably non-trivial. modules/core
                           never learns what a spreadsheet is.

(existing) IDE / MC mode   stage 2. IF11 / IF20, already specified.

(existing) validate/lint   stage 3. Already shipping.
```

`formats/` is already a separate layer from `modules/core`, so an XLSX reader is **not core
pollution** — that was the original worry and the layering already answers it.

### Scope line: read, do not write

- **Reading** XLSX needs presentation only to recover *types*. That is semantics.
- **Writing** XLSX requires *choosing* presentation — fonts, widths, number formats. That
  is where a spreadsheet model would genuinely contaminate the core.

So: reader first, no writer. Explicit non-goals — styles, fonts, colours, column widths,
merged-cell semantics, charts, pivot tables, macros, conditional formatting, and **XLSX
output**.

### Form-class fits here

Case A does not need AI, but it does need *something* to say "sheet `Bottles`, headers on
row 3, column D is an ISO date". That artifact — an external definition of a
non-self-describing record — is a **form-class**, the same concept already used for
bit-level binary formats. A form-class for a spreadsheet layout would:

- make Case A fully declarative and reviewable,
- give Case B's AI step something concrete to *emit* alongside the `.utlx`,
- and let a returning user re-import next year's file with zero inference.

That last point may be the strongest argument for the whole design: **stage 2's real output
is a reusable layout definition, not a one-off import.**

### GraalVM warning

ZIP handling plus any reflection-using library is exactly the class of problem that has bitten
this repo before (the CSV native-image fix). Budget reflection configuration and run the
conformance suite against the native binary, not only the JVM.

---

## 7. Open questions for discussion

1. **Which model, and can it be self-hosted?** Enterprise users will not send structure to
   a third-party API. A local model raises quality questions; a hosted one raises policy
   ones.
2. **Who pays for inference**, and is it metered per layout or per tenant?
3. **Is the generated `.utlx` committed to the consuming application** (reviewed, versioned,
   owned by the developer) or held as per-user state? This changes the trust model
   substantially.
4. **Layout fingerprinting** — how do we recognise "same shape as last time" and skip
   stage 2 entirely? Header-set hash is the obvious candidate.
5. **What happens when the shape changes?** A user adds a column in March. Detect and
   re-propose, or fail loudly?
6. **Two-row and merged headers** — common in real spreadsheets, badly served by
   "first row is headers". Stage 1 concern, but it determines how often stage 2 is handed
   garbage.
7. **Does Case A alone justify shipping first?** A template-driven importer needs no AI and
   could ship as soon as `formats/xlsx` exists. Case B could follow once MC-mode generation
   lands. Shipping A first de-risks B.

---

## 8. Summary

- Spreadsheets split into **known structure** (ordinary UTL-X, no AI) and **unknown
  structure** (AI proposes a mapping).
- **AI generates the transformation; it never executes it.** The artifact is a pure
  `%utlx 1.0`/`1.1` script, so the engine keeps all four guarantees and the result stays
  Guard-eligible. No `ai.*`, no `%utlx 2.0`, no `utl-x-infer` dependency.
- The three stages map onto work that largely **already exists**: a reader in `formats/`,
  Message Contract Mode for generation, and `validate`/`lint`/`validate.*` for verification.
- `formats/xlsx` is a sibling of `formats/avro`, not core pollution. **Read-only**, with
  presentation concerns written down as non-goals.
- The binding constraint is **not** technical: a generated mapping can be structurally
  valid and semantically wrong. The product answer is to present the mapping for
  confirmation and freeze it after review — never to silently import.
