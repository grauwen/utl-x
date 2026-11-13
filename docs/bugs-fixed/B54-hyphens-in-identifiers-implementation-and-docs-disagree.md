# B54 — hyphens in identifiers: the lexer allows them, the guide says they are an error

**Status:** Partly fixed — 2026-11-13. **Decision: hyphens stay**, because the data formats
have them. The documentation is corrected; the diagnostic (item 3) is still open.
**Component:** `:core` — `lexer/lexer_impl.kt:306,331` vs `docs/language-guide/syntax.md:162,970`
**Found:** 2026-11-12, while checking a recollection that "operands need a space around them"
**Severity:** Medium — nothing is broken at runtime, but the two sources disagree about what
the language *is*, and the consequence is a silent wrong answer (below)

## The contradiction

The lexer treats `-` as an identifier character, deliberately and with a comment:

```kotlin
// Allow hyphens in identifiers: input-name, my-variable, etc.
while (peek().isLetterOrDigit() || peek() == '_' || peek() == '-') advance()
```

`docs/language-guide/syntax.md` says the opposite, in two places:

```utlx
user-name      // ❌ Hyphens not allowed        (line 162)
let my-var = 10       // ❌ ERROR: Hyphens not allowed   (line 970)
```

The implementation wins:

```bash
let my-var = 10
{ r: my-var }        # => {"r":10}        the guide says this is an ERROR
```

The lexer behaviour pre-dates the `org.apache.utlx → com.glomidco.utlx` rename, so it is
long-standing; the guide has been wrong about it for at least that long.

## Why it matters: subtraction is silently not subtraction

Because `-` binds into the identifier, `a-b` is **one name**, not `a` minus `b`:

```bash
# input: {"a":10, "b":4, "a-b":99}
$input.a-b        # => 99     the value of the key "a-b"
$input.a - $input.b   # => 6      subtraction, with spaces
```

So `$input.a-$input.b` is a parse error and `$input.a-b` quietly returns a *different
document's* value. **Subtraction requires whitespace around the `-`** — which is the real
form of the half-remembered rule that operands need spaces. It is true of `-` and of no other
operator: `+`, `*`, `==` all parse without spaces.

That rule is documented **nowhere**. Neither `docs/reference/language-spec.md` nor the
language guide mentions it, and the guide actively denies its premise.

## The decision: hyphens stay, because the data has them

The question looked balanced until it was asked the right way round. It is not "do we want
hyphens in UTL-X identifiers?" but **"can a UTL-X path address the data users actually have?"**
— and all three self-describing formats permit hyphens in names:

| format | hyphens in names | rule |
|---|---|---|
| JSON | yes | a key is any string |
| XML | yes | `-` is a `NameChar`; a name may not *start* with one |
| YAML | yes | a key is any scalar |

Measured against a document whose keys are hyphenated, every format resolves a dotted path:

```bash
# {"my-top": {"an-item": 42, "content-type": "text/xml"}}   (and the XML/YAML equivalents)
$input.my-top.an-item         # 42        — json, xml and yaml alike
$input.my-top.content-type    # "text/xml"
```

XML **attributes** matter at least as much, and they are where hyphens are commonest —
`xml-lang`, `data-*`, `http-equiv`, `content-type`:

```bash
$input.doc.@xml-lang              # "en"
$input.doc.@data-id               # "7"
$input.doc.item.@content-type     # "text/xml"
```

Supporting hyphens in identifiers is therefore not a stylistic preference that happened to be
left on — it is what makes `$input.my-top.an-item` mean the obvious thing. Removing it would
make the natural spelling of ordinary data stop working.

### What option (b) would have cost, now that it is measurable

A bracket accessor does exist, so dropping hyphens would not make the data *unreachable*:

```bash
$input["my-top"]["an-item"]       # 42
$input["my-top"].an-item          # 42
$input.doc["@xml-lang"]           # "en"
```

But `$input."my-top"` is **not** supported (`Expected property name`), so the only escape
hatch is the bracket form — and requiring it for `content-type` or `xml-lang` is a poor trade
against a `-` that needs spaces when it means subtraction. It would also break every existing
script that uses the dotted spelling.

So: **(a), keep hyphens.** The earlier count that nothing in the repository used them was
measuring the wrong thing — it counted the *corpus*, not what users' documents look like.

## What has to follow from that decision

Keeping hyphens makes the trap in the previous section permanent, so it has to be paid for:

1. ~~**Correct the language guide.**~~ **Done.** `docs/language-guide/syntax.md` claimed
   hyphens were invalid in two places (`user-name  // ❌ Hyphens not allowed`,
   `let my-var = 10  // ❌ ERROR`). Both were simply false — `let my-var = 10` returns 10 — and
   the second is why this went unnoticed for a year. The identifier rules now say hyphens are
   allowed, and the invalid-identifier example that used them was replaced with `my.var`.
2. ~~**Document the whitespace rule for `-`.**~~ **Done.** A new
   *Hyphens, and why subtraction needs spaces* section in the guide, and §4.1.1 in
   `docs/reference/language-spec.md`, both stating that `a - b` subtracts while `a-b` is one
   name, that this applies to `-` alone, and that the bracket form `input["total-discount"]`
   reaches any key. Every example in both was executed before being written down.
3. **Add the diagnostic.** *(open)* The detector already exists and already fires when the target's type
   is known (see the next section); it never reaches `$input` because that is typed `Any`
   (B53) and because `--schema` is not wired into the core type checker. With those two fixed,
   plus a hyphen-specific "did you mean `total - discount`?", the silent `null` becomes a
   warning. Measured false-positive risk: hyphenated member access appears **0** times in 1442
   corpus blocks.

   This is the item that actually removes the trap. Documentation only helps someone who reads
   it before making the mistake; `$input.total-discount` returning `null` is discovered at
   runtime, in output, by someone who did not.

## Knock-on: it constrains the `-e` dot shorthand

This is what surfaced it. [CB04](CB04-dot-shorthand-needs-whitespace-so-compact-jq-expressions-fail.md)
proposes expanding `.name` to `$input.name` based on the preceding character. Under (a), a
dot after `-` **must not** expand when the `-` follows an identifier character, because the
`-` belongs to the name:

| expression | correct expansion | why |
|---|---|---|
| `-.a` | `-$input.a` | unary minus — `-` starts the expression |
| `.a-.b` | unchanged | `a-` is an identifier; this is not subtraction |
| `.a - .b` | `$input.a - $input.b` | spaced, so `-` is the operator |

Under (b) all three would expand uniformly.

**CB04 no longer waits on this.** Its recommended approach — expand conservatively, parse,
and only fall back to an aggressive expansion on a *parse error* — never reaches the
ambiguous case: `.a-.b` and `.a-b` both parse under the conservative expansion, so they are
served as written and the aggressive rule never sees them. The two reports are therefore
independent again.

What remains is that this document and the implementation still disagree, and that the
whitespace rule for `-` is written down nowhere. That is reason enough on its own: a user
who writes `$input.total-discount` gets a key lookup where they meant subtraction, silently,
and no document warns them.

## Can the ambiguity be detected rather than decided?

Largely **yes — and one detector already exists and simply never runs.**

### It already fires when the target's type is known

The type checker's `MemberAccess` branch reports a missing property on a typed object. Give
it a known type and it catches the trap today, with no new code:

```utlx
{ let o = { total: 100, discount: 30 }, r: o.total-discount }
```
```
✗ Property 'total-discount' does not exist on object
```

The identical expression against `$input` passes silently, for two compounding reasons:

1. `$input`-derived values are typed `Any`, and the `MemberAccess` branch returns `Any` for
   an `Any` target — so no property is ever checked
   ([B53](B53-any-is-assignable-to-nothing-so-strict-types-rejects-every-real-script.md)).
2. `--schema` does not reach this type checker at all. `ValidateCommand` parses the schema
   into an `inputType` and hands it to `AdvancedTypeInference.inferOutputType` in
   `:modules:analysis`, which only infers an *output* type. `$input` is never bound in the
   core checker's environment, so `utlx validate --schema … --strict` still passes a script
   referencing a field the schema does not define.

Fixing (2) is plumbing — bind the schema-derived type to `$input` before `TypeChecker.check`
— and it turns an existing, already-correct detector on.

### A targeted hyphen detector, and why it would be quiet

Condition a warning on all three:

> the property name contains `-`, **and** it does not exist on the target, **and** both halves
> resolve as names in scope

then say `did you mean 'total - discount'?` rather than the generic "does not exist".

Measured false-positive risk: **hyphenated member access appears 0 times in 1442 UTL-X blocks**
across the conformance corpus and the documentation. The only kebab names anywhere are 37
broken documentation examples ([B55](B55-forty-one-documented-examples-call-function-names-that-do-not-exist.md)),
and **0** of the 728 registered function names use a hyphen. So the warning would fire on
real mistakes and essentially nothing else.

### Without any type information

At runtime the interpreter has the actual UDM, so the same three conditions are checkable
exactly where the damage occurs: a member access returning null, whose name contains `-`,
where both halves are present on the object. Cost falls only on the null path.

### What this means for the decision

Detection reduces the cost of **(a) keep hyphens** considerably — the silent-wrong-answer
class becomes a loud one. It does not eliminate it: a detector cannot fire where no type is
known and the key genuinely is absent at runtime but the script never checks the result.

It also sharpens **(b) drop hyphens**, because the evidence says nothing would break:

| | |
|---|---|
| kebab-named functions in the dispatch registry (728) | **0** |
| kebab-named entries in the generated catalogue (704) | **0** |
| hyphenated member accesses in 1442 corpus blocks | **0** |
| kebab names in `@UTLXFunction` examples | 37 — **all broken** (B55), identical on `main` |

The lexer comment cites `input-name` and `my-variable` as the motivating cases. Nothing in
the repository uses them.

**One real user, found late:** a PascalCase *user-defined function* may contain a hyphen, and
it works — `function My-Func(x) { x * 2 }` then `My-Func(21)` returns `42`. That is the
single thing option (b) would break, and it is also the reason the capital rule matters here:
a lowercase `base64-encode` is rejected as a user-defined name, so the hyphen only ever
reaches identifiers that are already PascalCase or are property names.

## Verification needed with the fix

- whichever direction: a lexer test asserting `a-b` tokenises as **one** token or **three**,
  explicitly, with the kebab-key document as a fixture
- `docs/language-guide/syntax.md:162` and `:970` corrected, and the whitespace rule for `-`
  added to `docs/reference/language-spec.md` — the absence of it is half this defect
- a conformance fixture for `$input.a-b` against a document containing both `a-b` and `a`,
  so the chosen meaning is pinned across all three strategies
