# B28: XML element-name-with-space gives a cryptic `Expected '='` error

**Status:** Fix **PRESENT on `development`** (already implemented; see cross-branch note). **Untested** — no regression test yet. **NOT on `main`.**
**Priority:** Low — diagnostic/DX quality, not a correctness bug. The parser already *rejected* the malformed input; this only improves the *message*.
**Created:** September 2026
**Reported:** Split out of the B27 review — spotted in the `main`..`development` diff of `xml_parser.kt` as a second, unrelated change riding inside the namespace-rename commit.
**Related:** B27 (same file, XML prolog/epilog comments), B26 (also an error-message quality fix — precedent for a B-number on diagnostics).

> **Classification: a DIAGNOSTIC improvement, not a wrong-output or crash bug.** The old behavior
> was *technically correct* — XML genuinely expects `=` after an attribute name — but the message
> was cryptic for the common real cause (a space inside an element name). B28 replaces it with a
> message that names the likely mistake and the fix.

---

## TL;DR

Parsing an element whose name contains a space — e.g. `<Purchase Order>` — made the parser read
`Purchase` as the element name and `Order` as an attribute name, then hit `>` where it wanted `=`,
emitting the unhelpful:

```
Expected '=' after attribute name
```

The fix detects exactly this shape (an attribute name immediately followed by `>`, with no `=`) and
emits a message that names the probable cause and the correction:

```
XML parse error at L:C - Found 'Order' after element name 'Purchase'. This looks like a space
inside an element name (e.g., '<Purchase Order>' should be '<PurchaseOrder>'). XML element names
cannot contain spaces.
```

| # | Symptom | Root cause | File:line | Status |
|---|---|---|---|---|
| 1 | `<Foo Bar>` → cryptic `Expected '='` | attribute loop assumed a well-formed `name="value"`; a bare name-then-`>` fell through to the generic `consume('=')` failure | `xml_parser.kt` attribute loop (~140) | ✅ fixed on `development` |

---

## Reproduction

```bash
echo '<Purchase Order>5</Purchase Order>' | utlx --from xml --to json -e '$input'
```

- **`main` (and any pre-fix build):** `XML parse error … - Expected '=' after attribute name`
  — the user is left guessing; nothing points at the space in `Purchase Order`.
- **`development`:** the message above — names `Purchase`/`Order`, states element names can't
  contain spaces, and shows the intended `<PurchaseOrder>`.

---

## Root cause

`formats/xml/src/main/kotlin/com/glomidco/utlx/formats/xml/xml_parser.kt`, the attribute loop in
`parseElement`:

```kotlin
while (peek() != '>' && peek() != '/') {
    val attrName = parseName()      // for "<Purchase Order>" this reads "Order"
    skipWhitespace()
    consume('=', "Expected '=' after attribute name")   // ← hits '>', throws the cryptic message
    ...
}
```

`parseName()` stops at whitespace, so `<Purchase Order>` parses as element `Purchase` + attribute
`Order`. The loop then unconditionally required `=`, so a name directly followed by `>` produced a
message about `=` that never mentioned the real problem (a space where the author meant a single name).

---

## The fix (as implemented on `development`)

A targeted check *before* the generic `consume('=')`, plus a more specific fallback message:

```kotlin
val attrName = parseName()
skipWhitespace()
if (peek() == '>') {
    error("XML parse error at $line:$column - Found '$attrName' after element name '$name'. " +
        "This looks like a space inside an element name (e.g., '<$name $attrName>' should be " +
        "'<$name$attrName>'). XML element names cannot contain spaces.")
}
consume('=', "Expected '=' after attribute name '$attrName' in element '$name'")
```

- The new branch fires only for the exact ambiguous shape (`name` then `>`), so genuine
  missing-`=` typos (`<a b c>` → `b` then `c`) still get the generic — now element-qualified — message.
- No behavior change for well-formed XML; only the *error text* on already-rejected input changes.

---

## Cross-branch note (why this is its own item, not part of B27)

This fix is **already on `development`**, but it was **committed inside the namespace-rename commit
`c557858d`** (605 files, `org.apache.utlx` → `com.glomidco.utlx`) rather than as a standalone change.
That commit **originated on `feature/namespace`** (cut from `development`) and reached `development`
via a fast-forward merge — it was not authored directly on `development`. `main` carries a
**different** rename commit (run separately from `uat/namespace` per the migration plan's Decision 3),
so it never received this hunk. Consequences:

- It is **development-only** — `main` still emits the cryptic `Expected '='`.
- It is **not independently cherry-pickable** to `main` (cherry-picking `c557858d` would drag the
  entire rename, which `main` already has via its own rename commit). B27's cherry-pick does **not**
  carry it either — different, non-overlapping part of the file.
- To land it on `main`, make a **fresh standalone commit** applying just the attribute-loop hunk
  (subject e.g. `B28: friendlier error for a space in an XML element name`), then this doc travels
  with it.

---

## Test plan (not yet written — the fix currently has no regression test)

Add to `XmlPrologEpilogCommentTest` (or a new `XmlElementNameTest`):

1. `<Purchase Order>…` → `XMLParseException` whose message contains *"element names cannot contain
   spaces"* and both `Purchase` and `Order`.
2. Regression: a genuine missing-`=` (`<a b>` with `b` not followed by `>` … e.g. `<a b"x">`) still
   yields the generic (element-qualified) `Expected '='` message — the new branch must not swallow it.
3. Regression: well-formed `<a b="x">` still parses unchanged.

---

## Checklist

- [x] Fix implemented on `development` (rode in via `c557858d`)
- [ ] Regression test added (`XmlElementNameTest` — cases above)
- [ ] Ported to `main` as a **standalone** commit (can't cherry-pick — tangled in the rename)
- [ ] This doc cherry-picked/copied alongside the `main` port
