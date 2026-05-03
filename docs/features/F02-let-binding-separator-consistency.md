# F02: Let Binding Separator Consistency

**Status:** Design decision needed  
**Priority:** High (breaking change — fix before significant user adoption)  
**Created:** April 2026  
**Urgency:** Before Azure Marketplace gains live users

---

## The Problem

The `let` binding in UTL-X requires different separators depending on context:

| Context | Separator | Example |
|---------|-----------|---------|
| Top-level (.utlx body) | Newline (nothing) | `let x = 1` ↵ `let y = 2` ↵ `{...}` |
| Inside object literal `{}` | Comma | `{let x = 1, let y = 2, result: x + y}` |
| Lambda returning array `[]` | Semicolon | `let x = 1; let y = 2; [x, y]` |

This is **inconsistent, confusing, and a source of bugs**. Both the language creator and AI assistants have been confused by this during development. If experts get confused, users will too.

## Why This Happened

The inconsistency is not a design choice — it's an artifact of the parser implementation:

1. **Top-level newlines work** because the top-level parser treats newlines as implicit statement separators. This is convenient and feels natural.

2. **Commas inside objects** because the parser reuses the object-member parsing rule for `let` bindings. Inside `{}`, everything is comma-separated: properties, let bindings, spread operators. The `let` was grafted onto the object grammar.

3. **Semicolons before arrays** because the parser cannot distinguish between:
   - `let y = 20[x, y]` — indexing `20` with `[x, y]` (valid expression)
   - `let y = 20; [x, y]` — binding 20 to y, then returning array `[x, y]`

   Without the semicolon, the parser greedily interprets `[` as an index operator on the `let` value. The semicolon forces the parser to end the `let` expression.

## Impact Assessment

### Who is affected
- Every UTL-X user writing non-trivial transformations
- IDE autocompletion and error diagnostics (must know which separator to suggest)
- AI code generation (LLMs will generate wrong separators)
- Documentation (three rules instead of one)
- Migration scripts (if the syntax changes)

### Severity
- **Beginner impact:** HIGH — "my code doesn't parse and I don't know why"
- **Expert impact:** MEDIUM — "I know the rules but they feel wrong"
- **AI generation impact:** HIGH — LLMs will inconsistently use commas/semicolons/nothing
- **Conformance suite impact:** tests must cover all three contexts

### Current test coverage
- Top-level let: covered in conformance suite
- Object-literal let with commas: covered in conformance suite
- Lambda-with-semicolons: partially covered (auto-captured tests)
- **No test explicitly validates that wrong separators produce clear error messages**

## What Other Languages Do

### JavaScript / TypeScript
```javascript
let x = 10;
let y = 20;
// Semicolons everywhere. Newlines work via ASI (Automatic Semicolon Insertion).
// ASI is widely criticized as a source of bugs.
```
**Verdict:** Semicolons are the formal separator, but ASI makes them optional. The ASI rules are notoriously confusing (similar to UTL-X's current situation).

### Kotlin
```kotlin
val x = 10
val y = 20
// Newlines are the separator. Semicolons optional (only needed for multiple statements on one line).
// No confusion — newlines always work.
```
**Verdict:** Newlines are sufficient everywhere. Semicolons are an escape hatch, not a requirement. **This is the cleanest model.**

### Rust
```rust
let x = 10;
let y = 20;
// Semicolons required everywhere. No ambiguity, no context-dependent rules.
```
**Verdict:** Consistent but verbose. Works because Rust developers expect C-like syntax.

### Python
```python
x = 10
y = 20
# Newlines are the separator. Period. No semicolons, no commas.
# Inside expressions: no let bindings allowed (walrus operator := is different).
```
**Verdict:** Newlines everywhere. Python avoids the problem entirely by not allowing bindings inside expressions.

### Haskell / ML family
```haskell
let x = 10
    y = 20
in x + y
-- let...in block: indentation determines scope. No separators needed.
```
**Verdict:** Indentation-based scoping. Clean but requires whitespace-sensitive parsing.

### DataWeave (MuleSoft — closest competitor)
```dataweave
var x = 10
var y = 20
---
{result: x + y}
// Variables declared BEFORE the separator (---), used AFTER.
// No separator issue because variables and body are in different sections.
```
**Verdict:** DataWeave avoids the problem by separating variable declarations from the body. Variables live in the header, not inline. **Different design philosophy.**

### Summary Table

| Language | Separator | Inside expressions? | Ambiguity? |
|----------|-----------|-------------------|------------|
| JavaScript | ; (with ASI) | Yes | Yes (ASI bugs) |
| Kotlin | Newline | Yes (val in blocks) | No |
| Rust | ; (required) | Yes | No |
| Python | Newline | No (no inline let) | No |
| Haskell | Indentation | Yes (let...in) | No |
| DataWeave | Header section | No (vars before ---) | No |
| **UTL-X now** | **Newline / , / ;** | **Yes** | **Yes** |

## Proposed Solution

### Recommended: The Kotlin Model

**Newlines are always sufficient. Semicolons are optional (for single-line usage). Commas are never used with `let`.**

```utlx
// Top-level — works today, no change
let x = 10
let y = 20
{result: x + y}

// Inside object literal — CHANGE: no comma before/after let
{
  let subtotal = 100
  let tax = subtotal * 0.08
  let total = subtotal + tax

  subtotal: subtotal,
  tax: tax,
  total: total
}

// Inside lambda — CHANGE: no semicolon needed
items |> map(item => {
  let price = toNumber(item.Price)
  let tax = price * 0.08

  [item.Name, price + tax]
})

// Single-line — semicolons as optional shorthand
let x = 10; let y = 20; {result: x + y}
```

### The Key Parser Change

The `[` ambiguity (array return vs indexing) is the only real challenge. The solution:

**After a `let` binding, if the next token is `[` at the start of a new line, treat it as a new expression (array), not an index operator.**

This is how Kotlin, JavaScript (with ASI), and Go handle it. The rule:

```
If the token before [ is a newline → [ starts a new expression (array literal)
If the token before [ is on the same line → [ is an index operator
```

This is called **newline-sensitive parsing** and is well-understood in language design.

### Concrete Parser Changes

In `parser_impl.kt`, the `let` expression parsing needs:

1. **Remove the requirement for commas** between `let` bindings inside object literals
2. **Remove the requirement for semicolons** before array returns
3. **Add newline-sensitivity** to the `[` token: if preceded by a newline, it's an array literal, not an index

Estimated effort: 2-4 days of parser work + updating all affected tests.

### What Stays the Same

- `let` keyword and binding syntax: unchanged
- Commas between object properties: unchanged (`name: "Alice", age: 30`)
- Semicolons as optional single-line separators: still work
- Top-level behavior: unchanged (already newline-based)

### What Changes

| Before (current) | After (proposed) |
|-------------------|------------------|
| `{let x = 1, let y = 2, result: x + y}` | `{let x = 1` ↵ `let y = 2` ↵ `result: x + y}` |
| `let x = 1; let y = 2; [x, y]` | `let x = 1` ↵ `let y = 2` ↵ `[x, y]` |
| Commas required inside `{}` | Commas only between properties |
| Semicolons required before `[]` | Newline sufficient |

### Backward Compatibility

The old syntax (commas and semicolons) should **still work** — they become optional, not forbidden. This means:

- Existing .utlx files continue to parse correctly
- Users can gradually adopt the cleaner style
- No breaking change for existing conformance tests
- The old style is deprecated but not removed

## Migration Strategy

### Option A: Fix Now (Before Marketplace Users)

- UTL-X is on Azure Marketplace but pending approval (no live users yet)
- The conformance suite has 453+ tests that validate current behavior
- Fix the parser, update tests, release as v1.0.3
- **Cost:** 2-4 days of work
- **Risk:** Low (no live users to break)
- **Benefit:** Clean syntax from day one for Marketplace users

### Option B: Fix in v1.1 (After Some Adoption)

- Release v1.0.2 with current syntax (documented, warts and all)
- Fix in v1.1 with backward compatibility (old syntax still works)
- **Cost:** Same parser work + migration documentation
- **Risk:** Users learn the inconsistent syntax, then must re-learn
- **Benefit:** More time to validate the design

### Option C: Fix in v2.0 (Breaking Change)

- Live with current syntax through the v1.x lifecycle
- Clean break in v2.0 (drop comma/semicolon requirement entirely)
- **Cost:** Years of inconsistency, user confusion, AI generation issues
- **Risk:** The inconsistency becomes "how UTL-X works" — harder to change later
- **Benefit:** Avoids any risk now

### Recommendation: Option A (Fix Now)

**Fix it before the first Marketplace user encounters it.** The reasoning:

1. **No live users yet.** Azure Marketplace is pending approval. Zero production deployments. Zero users to break.

2. **The fix is backward compatible.** Old syntax (commas, semicolons) still works. No .utlx files break. Tests still pass.

3. **The cost only grows with time.** Every user who learns the comma/semicolon rules is a user who must re-learn later. Every AI model trained on examples with commas produces wrong code in the future.

4. **The conformance suite validates the change.** 453+ tests ensure nothing breaks. Add 5-10 new tests for the newline-sensitive `[` parsing.

5. **The book hasn't been published.** Chapter 8 documents the current behavior with a "tagged for improvement" note. Fix the parser, update the chapter, publish the book with the clean syntax.

6. **Competitors got this right.** Kotlin, Python, and DataWeave all have consistent binding syntax. UTL-X should too. Users coming from these languages will expect newlines to work everywhere.

## Impact on the Book

Chapter 8 (Language Fundamentals) currently has a section explaining the three-separator rule. When F02 is implemented:

- Remove the separator table and the "three contexts" explanation
- Replace with: "let bindings are separated by newlines. Semicolons work as an optional alternative for single-line usage."
- Remove the "Inside Object Literals" and "Inside Lambdas" subsections
- Add a note: "In UTL-X versions before 1.0.3, commas were required inside object literals and semicolons before array returns. This is no longer necessary but still accepted for backward compatibility."

## Test Plan

1. All existing 495+ conformance tests pass unchanged (backward compatibility)
2. New tests: `let` with newlines inside object literals (no commas)
3. New tests: `let` with newlines before array return (no semicolons)
4. New tests: `let` with semicolons on single line (still works)
5. New tests: `let` with commas (still works, deprecated)
6. New tests: the `[` ambiguity case — newline before `[` = array, same line = index
7. Error message test: clear error if the ambiguity is detected
8. New tests: `let` bindings OUTSIDE `{ }` — `let` before the output object must continue to work (this pattern works in CLI/utlxe today; see IB01 for the known IDE issue). Critical to verify the newline-sensitivity changes don't break this case:
   - `let x = $input.field` ↵ `{result: x}` — must work
   - `info("msg")` ↵ `let x = 1` ↵ `{result: x}` — side-effect + let + object
   - `let x = 1` ↵ `let y = x + 1` ↵ `[x, y]` — let chain returning array
   - `let x = 1` ↵ `x + 1` — let returning bare expression (no object)

## Baseline Tests (added May 2026)

Conformance tests in `conformance-suite/utlx/tests/language/let-bindings/` document the current behavior as a baseline before F02 implementation.

### Tests that PASS today (must continue to pass after F02):

| Test | Pattern | Description |
|------|---------|-------------|
| `let_before_object_newlines` | `let a = ...` ↵ `let b = ...` ↵ `{...}` | Top-level let with newlines before object |
| `let_inside_object_commas` | `{let a = ..., let b = ..., result: ...}` | Let inside object with commas (backward compat) |
| `let_bare_expression_return` | `let a = ...` ↵ `a + 1` | Let returning bare expression (no object) |
| `let_chained_dependency` | `let a` ↵ `let b = f(a)` ↵ `let c = f(b)` ↵ `{...}` | Chained lets where each depends on previous |
| `let_single_binding` | `let x = ...` ↵ `{...}` | Single let before object |
| `let_in_lambda_commas` | `map(arr, (x) -> {let y = ..., result: y})` | Let inside lambda with commas (backward compat) |

### Patterns that FAIL today (must pass after F02):

These are NOT in the conformance suite (runner doesn't support `skip`). Add them as passing tests when F02 is implemented.

| Pattern | Current error | F02 fix |
|---------|--------------|---------|
| `{let a = 1` ↵ `let b = 2` ↵ `result: a + b}` | `Expected ';' or ',' after let binding` | Newlines accepted inside `{ }` |
| `let a = 1` ↵ `let b = 2` ↵ `[a, b]` | `Expected ']'` (parser treats `[` as index on `2`) | Newline before `[` = array literal |
| `map(arr, (x) -> {let y = x * 2` ↵ `result: y})` | `Expected ';' or ','` | Newlines accepted in lambda object body |

### Pattern that works in CLI but fails in IDE (IB01 — separate issue):

| Pattern | CLI | IDE |
|---------|-----|-----|
| `info("msg")` ↵ `let x = $input.val` ↵ `{result: x}` | ✅ Works | ✗ `$input` not available |

## Files to Change

| File | Change |
|------|--------|
| `parser_impl.kt` | Newline-sensitive `[` parsing, remove comma requirement for let in objects |
| `lexer_impl.kt` | May need to track newlines as significant tokens (like Go/Kotlin) |
| Conformance suite | Add the 3 "currently failing" tests as passing + keep 6 baseline tests |
| `docs/language-guide/syntax.md` | Update let binding documentation |
| Book chapter 8 | Simplify (remove three-separator explanation) |

---

*Feature document F02. April 2026. Updated May 2026 with baseline tests.*
*Priority: fix before first Marketplace user.*
