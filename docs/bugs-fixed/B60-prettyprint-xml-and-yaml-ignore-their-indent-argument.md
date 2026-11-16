# B60 — `prettyPrintXML` and `prettyPrintYAML` ignore their indent argument

**Status:** Open — **implementation declined; downgraded to a metadata fix.** See "Decision" below.
**Priority:** Low — the behaviour is wrong, but the functions have no users; the honest fix is to
stop advertising parameters they discard
**Component:** `:stdlib` (`serialization/PrettyprintFunctions.kt`)
**Found:** 2026-11-14, while clearing compiler warnings after the 1.4.0 release

## Summary

`prettyPrintXML(xml, indent)` and `prettyPrintYAML(yaml, indent)` take an indent width,
document it, and discard it. `prettyPrintXML` additionally does not indent at all — it
inserts newlines and leaves every element at column 0. `preserveWhitespace` and `flowStyle`
are accepted and ignored the same way.

## Repro

```utlx
%utlx 1.0
input json
output json
---
{
  xmlIndent2: prettyPrintXML("<a><b>1</b></a>", 2),
  xmlIndent8: prettyPrintXML("<a><b>1</b></a>", 8),
  yamlIndent2: prettyPrintYAML("a:\n  b: 1", 2),
  yamlIndent8: prettyPrintYAML("a:\n  b: 1", 8)
}
```

```json
{
  "xmlIndent2": "<a>\n<b>1</b>\n</a>",
  "xmlIndent8": "<a>\n<b>1</b>\n</a>",
  "yamlIndent2": "a:\n  b: 1",
  "yamlIndent8": "a:\n  b: 1"
}
```

Two different indent widths, identical output. The XML has no indentation at either
setting, and the YAML is returned unchanged.

## Root cause

The public functions thread the argument all the way to a private helper that drops it.
The compiler names each one:

```
w: ...PrettyprintFunctions.kt:742 Parameter 'preserveWhitespace' is never used
w: ...PrettyprintFunctions.kt:746 Parameter 'indent' is never used
w: ...PrettyprintFunctions.kt:746 Parameter 'flowStyle' is never used
w: ...PrettyprintFunctions.kt:751 Parameter 'flowStyle' is never used
w: ...PrettyprintFunctions.kt:1070 Parameter 'indentStr' is never used
w: ...PrettyprintFunctions.kt:1070 Parameter 'preserveWhitespace' is never used
```

The helpers are reached from `prettyPrintXML` (line 242), `prettyPrintYAML` (323),
`prettyPrintFormat` (552-553) and `udmToYAMLInternal` (371), so every caller is affected,
including `prettyPrintFormat(udm, "xml", indent)`.

## Why it was not noticed

The functions return plausible-looking output: `prettyPrintXML` does add newlines, so a
glance at the result suggests it worked. The indent width is the part that silently does
nothing, and no test asserts a specific indent — the stdlib tests check that the call
returns a string.

## Decision (2026-11-16): do not implement, correct the metadata instead

Implementing this was weighed against the evidence and declined.

### 1. The functions have no users

Searching `conformance-suite/`, `examples/`, `docs/` and `test-data/` for the whole family —
`prettyPrint`, `prettyPrintXML`, `prettyPrintYAML`, `prettyPrintJSON`, `prettyPrintCSV`,
`prettyPrintFormat` — returns exactly one file: **this report**. Not one conformance fixture, not
one example, not one documentation page calls any of them. The only exercise they get is
`PrettyprintFunctionsTest` in `:stdlib`.

### 2. The defect is narrower than the title suggests

`prettyPrintJSON` honours its indent correctly:

```
prettyPrintJSON("{\"a\":{\"b\":1}}", 2)  =>  {\n  "a": {\n    "b": 1\n  }\n}
prettyPrintJSON("{\"a\":{\"b\":1}}", 8)  =>  {\n        "a": {\n                "b": 1\n        }\n}
```

Only **XML and YAML** ignore it, and `prettyPrintXML` does not indent at all — it inserts newlines
and leaves every element at column 0:

```
prettyPrintXML("<a><b>1</b></a>", 2)  =>  <a>\n<b>1</b>\n</a>
prettyPrintXML("<a><b>1</b></a>", 8)  =>  <a>\n<b>1</b>\n</a>      (identical)
```

### 3. "Fixing" it means writing an XML pretty-printer

Not threading a parameter through — the indentation does not exist. Doing it properly means
handling nested elements, attributes, mixed content, text nodes, CDATA, self-closing tags and
namespace declarations, plus fixtures that assert exact whitespace. Per `AGENTS.md` those fixtures
must compare through **XML or CSV output**, because `format: json` is compared by parsed value and
cannot see leading whitespace at all. That is hours of work and a new surface to maintain, for
functions nobody calls.

### What to do instead

Correct the published metadata so the tools stop advertising what they discard. Today the
catalogue says:

```
prettyPrintXML       category=Other   prettyPrintXML(xmlString: Xmlstring value, indentUDM: ...)
```

— an auto-generated signature (the B55 family of noise), filed under **Other**, promising an
`indent` that has no effect. The fix is a correct `@UTLXFunction` description that states the
real behaviour ("normalises line breaks; does not indent"), a real category, and removal of the
parameters that are ignored — or marking them explicitly as reserved.

That is minutes of work, removes the false claim, and leaves the implementation for whoever
actually needs it. It is the same class of defect as
[CB06](CB06-parse-profile-is-undiscoverable-at-the-point-of-rejection.md): the tool describing
itself inaccurately.

### If someone does need XML pretty-printing later

Reopen this with a real use case. The work is self-contained and the report above already
documents the root cause and the fixture constraints, so it will not need re-investigating.

## Not fixed in the 1.4.0 cleanup pass

The cleanup commit was kept behaviour-neutral. Removing the unused parameters would have cemented
the defect, and implementing them is a behaviour change needing fixtures.
