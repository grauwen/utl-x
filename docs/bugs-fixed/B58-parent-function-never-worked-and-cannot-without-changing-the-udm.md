# B58 — `parent()` never worked, and could not without changing what the UDM is

**Status:** Fixed — 2026-11-13, by removing the function
**Component:** `:stdlib` — `xml/XmlUtilityFunctions.kt`, `Functions.kt` registration
**Found:** 2026-11-13, while deciding [B49](B49-documented-wildcard-and-descent-selectors-are-parse-errors.md)
**Severity:** Medium — a registered, documented, green-tested function that returned `null` for
every document UTL-X can parse

## Summary

```bash
# <order id="A1"><lines><line><sku>X</sku></line></lines></order>
parent($input.order.lines)   => null
parent($input.order)         => null
```

Not "null for a root node" — null for everything. It had been that way since its first commit.

## Broken three ways, from the start

**1. Nothing ever wrote `__parent`.** The function read
`element.properties["__parent"]`, and across `formats/`, `:core`, `:stdlib` and
`:modules:engine` exactly one file contains that string — the function reading it. The parsers
do set metadata: `__nodeName`, `__nsContext`, `__schemaType`, `__targetNamespace`, `__version`
and the `__xsd*` keys. Never `__parent`.

**2. It read the wrong map.** Its own description says *"Get parent element (if metadata
available)"*, but it looked in `properties`. `UDM.Object` keeps four separate spaces —
`properties`, `attributes`, `name`, `metadata` — and metadata is reached by `^` in the language
and `getMetadata()` in Kotlin. The description and the code disagreed about where the answer
would live.

**3. Neither map could have held it.** `metadata` is `Map<String, String>`; a parent element is
a `UDM.Object`. `properties` is structurally able to hold a node but is the child-data space.
There was nowhere in the UDM for a parent to go.

### And the test hid all of it

```kotlin
val element = UDM.Object(mutableMapOf("__parent" to parent))   // hand-built
val result = XmlUtilityFunctions.parent(listOf(element))
assertEquals(parent, result)                                    // green
```

The test manufactured the property the real parsers never create. That is how a registered,
catalogued, documented function stayed dead for a year with a passing suite — the same shape as
[B55](B55-forty-one-documented-examples-call-function-names-that-do-not-exist.md)'s
documented-but-uncallable examples and B49's emitted-but-unparsed token. The artefact exists,
so nobody looks again.

## Where it came from

The original design aimed at **XSLT-style template matching**, where a template matches a node
and routinely needs ancestor context — so a parent pointer is natural there. `parent()` arrived
in the first `Create XmlUtilityFunctions.kt` commit (2025-10-14) alongside `childCount`,
`childNames`, `elementPath` and `nodeType`: a DOM-shaped batch written by analogy. Three of the
four are computable from a node alone. `parent` is not.

**Template matching was never implemented.** `template`, `match` and `apply` remain reserved
words, the parser has no branch for `template match=`, and **0** of 578 conformance fixtures
use it. The language went a different way — expression-oriented, navigate down from `$input`,
functions over relational data — and in that design nothing asks a node for its parent.

## Why it was removed rather than fixed

Implementing it means putting a back-reference in the UDM, and `UDM.Object` is a `data class`.
Both ways of doing that are structural, not incremental:

| approach | consequence |
|---|---|
| a constructor property | the tree becomes cyclic and the generated `equals`/`hashCode`/`toString` recurse — a child comparing itself compares its parent, which compares its children. UDM equality is load-bearing: the conformance runner compares values, `ConformanceStrategyParityTest` compares three strategies' output, hundreds of tests `assertEquals` on UDM. `copy()` also breaks subtly, since a copied node's parent still points into the old tree. |
| outside the constructor | it leaves the data-class contract and must be assigned after construction — making the UDM **mutable**. Today it is an immutable tree shared across TEMPLATE, COPY and COMPILED with no defensive copying, which is exactly what lets the parity harness run one fixture through three strategies cheaply. |

So the question is not "add a field" but "change what kind of structure the UDM is", for a
feature with no current customer.

## What serves the real requirement

The case this appears to promise — **flat records linked by a parent key**, as in SAP IDoc
segments, EDI, or a CSV export with foreign keys — is a **key join**, not structural
containment. A tree pointer would not have served it even if implemented. `nestBy()` was built
for precisely that and says so in its first line (F03, implemented May 2026):

```utlx
nestBy($input.orders, $input.lines, (o) -> o.orderId, (l) -> l.orderId, "lines")
=> [{orderId: "A", cur: "EUR", lines: [{sku: "W"}, {sku: "G"}]}, …]
```

For ancestor *context* while navigating, the language already keeps it in scope:

```utlx
let order = $input.order
{ lines: map(order.lines.line, (l) -> { sku: l.sku, orderId: order.@id }) }
```

And if a future template engine genuinely needs ancestors, the functional answer is a zipper —
carry the path during traversal rather than store it in every node — which costs nothing when
unused. That is a design question for that feature, not a gap in the UDM today.

## What was removed

- `XmlUtilityFunctions.parent` and its `@UTLXFunction` annotation
- `register("parent", XmlUtilityFunctions::parent)`
- `XmlUtilityFunctionsTest.testParent` and `testParent_noParent`
- `parent` from `FunctionsTest.testXMLFunctions`'s expected-name list

Each site keeps a comment saying what was there and why it went, pointing at `nestBy()`.
Removed rather than commented out: this repository has a measured example of the alternative —
`UUIDFunctions.kt` carries a comment block holding **17** `register(...)` lines that were never
carried out, and it cost real time during the B55 audit, where they were counted as live
registrations until block comments were stripped. Commented-out code is invisible to the
compiler and to tests, but fully visible to search, so it misleads every later investigation.
Git already remembers the code; this report remembers the reasoning.

## Verification

| | |
|---|---|
| `:stdlib:test` | 2337, 0 failures |
| `:modules:core` / `:modules:cli` / `:modules:engine` / `:formats:json` / `:modules:daemon` | 384 / 153 / 1017 / 98 / 195, 0 failures |
| conformance | 578/578 |

`FunctionsTest.testXMLFunctions` failed on the removal — *"Missing XML function: parent"* —
which is the registry-completeness test doing its job. Its list now omits `parent`, with a note.
