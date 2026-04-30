= Migration Guides

Moving from an existing transformation tool to UTL-X? This chapter provides side-by-side translations for the most common migration paths: XSLT, DataWeave, jq, TIBCO BusinessWorks, and custom code.

== Migrating from XSLT

XSLT and UTL-X solve the same problem — transforming XML — but with fundamentally different approaches. XSLT uses template-based pattern matching (push model). UTL-X uses functional expressions (pull model). The mapping is surprisingly direct:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*XSLT*], [*UTL-X*],
  [`\<xsl:value-of select="Order/Customer"/>`], [`\$input.Order.Customer`],
  [`\<xsl:for-each select="Items/Item">`], [`map(\$input.Items.Item, (item) -> ...)`],
  [`\<xsl:if test="Total > 100">`], [`if (\$input.Total > 100) ... else ...`],
  [`\<xsl:choose>/\<xsl:when>/\<xsl:otherwise>`], [`match` or chained `if/else`],
  [`\<xsl:attribute name="id">`], [`"@id": \$input.orderId`],
  [`\<xsl:variable name="x" select="..."/>`], [`let x = ...`],
  [`\<xsl:template name="CalcTax">`], [`function CalcTax(amount, rate) { ... }`],
  [`\<xsl:apply-templates/>`], [Not needed — expressions compose directly],
  [`\<xsl:sort select="@price"/>`], [`sortBy(items, (i) -> i.@price)`],
  [`\<xsl:copy-of select="."/>`], [`...\$input` (spread)],
  [`position()`], [Index parameter in `map`],
  [`count(Items/Item)`], [`count(\$input.Items.Item)`],
  [`sum(Items/Item/@price)`], [sum + map + toNumber],
  [`concat(FirstName, ' ', LastName)`], [concat with three arguments],
)

=== Side-by-Side Example

XSLT:

```xml
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:template match="/Orders">
    <Invoices>
      <xsl:for-each select="Order">
        <Invoice>
          <xsl:attribute name="id"><xsl:value-of select="@orderId"/></xsl:attribute>
          <Customer><xsl:value-of select="CustomerName"/></Customer>
          <Total><xsl:value-of select="sum(Item/Price * Item/Qty)"/></Total>
          <xsl:if test="sum(Item/Price * Item/Qty) > 1000">
            <Priority>HIGH</Priority>
          </xsl:if>
        </Invoice>
      </xsl:for-each>
    </Invoices>
  </xsl:template>
</xsl:stylesheet>
```

UTL-X:

```utlx
%utlx 1.0
input xml
output xml
---
{
  Invoices: map($input.Orders.Order, (order) -> {
    let total = sum(map(order.Item, (i) -> toNumber(i.Price) * toNumber(i.Qty)))
    Invoice: {
      "@id": order.@orderId,
      Customer: order.CustomerName,
      Total: total,
      ...if (total > 1000) { Priority: "HIGH" } else {}
    }
  })
}
```

16 lines of XSLT become 14 lines of UTL-X. More importantly: the UTL-X version reads top-to-bottom as "map orders to invoices, calculate total, add priority if high." The XSLT version requires understanding template matching, the `xsl:` namespace, and attribute value templates.

=== What XSLT Does That UTL-X Doesn't

- *Template matching with priorities:* XSLT's push model (`apply-templates` + pattern matching) has no direct UTL-X equivalent. UTL-X uses explicit navigation and `match` expressions instead.
- *XPath axes:* `preceding-sibling`, `ancestor`, `following` — XSLT can navigate the tree in any direction. UTL-X navigates downward from `$input` and uses `filter()` for sibling access.
- *XSLT 2.0+ grouping:* `xsl:for-each-group` — use `groupBy()` in UTL-X.
- *Multiple output documents:* `xsl:result-document` — use pipeline fan-out in UTLXe.

== Migrating from MuleSoft DataWeave

DataWeave and UTL-X are the most similar transformation languages — both functional, both format-agnostic, both use `@` for XML attributes. Migration is often nearly mechanical:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*DataWeave*], [*UTL-X*],
  [`payload.orderId`], [`\$input.orderId`],
  [`payload.order.@id`], [`\$input.order.@id`],
  [`payload map (item) -> ...`], [`map(\$input, (item) -> ...)`],
  [`payload filter (item) -> ...`], [`filter(\$input, (item) -> ...)`],
  [`payload reduce (acc, item) -> ...`], [`reduce(\$input, init, (acc, item) -> ...)`],
  [`sizeOf(payload)`], [`count(\$input)`],
  [`payload groupBy \\$.category`], [`groupBy(\$input, (x) -> x.category)`],
  [`payload orderBy \\$.name`], [`sortBy(\$input, (x) -> x.name)`],
  [`payload distinctBy \\$.id`], [`unique(map(\$input, (x) -> x.id))`],
  [`payload[0]`], [`\$input[0]`],
  [`upper(name)`], [`toUpperCase(name)`],
  [`now()`], [`now()`],
  [`{(vars.key): value}`], [`{[key]: value}`],
  [`writeAttributes: true`], [`{writeAttributes: true}`],
)

=== Key Differences

- *Variable prefix:* DataWeave uses `payload`, `vars`, `attributes`. UTL-X uses `$input` (or `$name` for multi-input).
- *Function call syntax:* DataWeave: `payload map (x) -> x.name`. UTL-X: `map($input, (x) -> x.name)` — the collection is a parameter, not a method receiver.
- *User-defined functions:* DataWeave: `fun calcTax(amount, rate) = ...`. UTL-X: `function CalcTax(amount, rate) { ... }` — PascalCase required.
- *Output directive:* DataWeave: `output application/json`. UTL-X: `output json`.
- *Conditional output:* DataWeave: `if (condition) field: value`. UTL-X: `...if (condition) { field: value } else {}` with spread.

=== MuleSoft Migration Strategy

+ Export DataWeave scripts from Anypoint Studio
+ Translate syntax (usually mechanical — see table above)
+ Replace `payload` with `$input`, adjust function call order
+ Run against sample data in UTL-X IDE — compare output
+ Add to conformance suite as regression tests

== Migrating from jq

jq users will feel at home — UTL-X's expression mode was designed to mirror jq syntax:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*jq*], [*UTL-X*],
  [`.name`], [`.name` or `\$input.name`],
  [`.[] | .name`], [`map(., (x) -> x.name)`],
  [`select(.active)`], [`filter(., (x) -> x.active)`],
  [`sort_by(.name)`], [`sortBy(., (x) -> x.name)`],
  [`group_by(.category)`], [`groupBy(., (x) -> x.category)`],
  [`length`], [`count(.)`],
  [`keys`], [`keys(.)`],
  [`to_entries`], [`entries(.)`],
  [`from_entries`], [`fromEntries(.)`],
  [`map(select(...))`], [`filter(., ...)`],
  [`. | @base64`], [`base64Encode(.)`],
  [`. | @json`], [`renderJson(.)`],
  [`\{a, b\}`], [`{a: .a, b: .b}`],
  [`if ... then ... else ... end`], [`if (...) ... else ...`],
)

=== What jq Does That UTL-X Doesn't

- *Recursive descent:* `..` (jq) walks the entire tree. UTL-X has no recursive wildcard — use explicit paths or `.*` for one level.
- *String interpolation:* `"Hello \(.name)"` — UTL-X uses `concat("Hello ", .name)`.
- *`?//` alternative operator:* jq's try-alternative. UTL-X uses `try { ... } catch { fallback }`.

=== What UTL-X Does That jq Doesn't

- *XML, CSV, YAML, OData input/output* — jq is JSON-only
- *User-defined functions with PascalCase* — reusable, named logic
- *Schema validation* — UTLXe validates input/output against schemas
- *Pipeline chaining* — multi-step transformations with UDM hand-off
- *652 stdlib functions* — jq has ~50 built-in functions

== Migrating from TIBCO BusinessWorks

TIBCO BW uses a visual mapper for XML-to-XML transformations. The mapper generates XSD-driven mappings that correspond to UTL-X expressions:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*TIBCO BW concept*], [*UTL-X equivalent*],
  [Mapper activity], [`.utlx` transformation file],
  [Input schema (XSD)], [`input xml` (or `input xml {schema: "..."}`  — planned)],
  [Output schema (XSD)], [`output xml`],
  [Line mapping (drag-and-drop)], [Property assignment: `target: \$input.source`],
  [For-Each group], [`map(array, (item) -> ...)`],
  [Choice / If-Then], [`if (...) ... else ...` or `match`],
  [XSLT sub-activity], [Inline in the transformation body],
  [Global variables], [`let` bindings],
  [BW process (multi-step)], [UTLXe pipeline (Chapter 19)],
  [JMS binding], [Dapr input binding or HTTP API],
  [Process variables], [Not needed — functional, no mutable state],
)

=== XSD Pattern Consideration

TIBCO BW generates Russian Doll XSD schemas (Chapter 28) — all types inline, one global root element. When migrating:

+ Export the input and output XSDs from BW
+ Convert to Venetian Blind if needed: `utlx --from xsd --to xsd {pattern: "venetian-blind"}`
+ Use the XSD to understand the structure, then write the UTL-X transformation
+ The UTL-X transformation doesn't need the XSD at runtime (CLI is lenient) — but UTLXe can validate against it

=== Performance Comparison

BW runs on a JVM application server with resource allocation per process. UTLXe runs as a container with worker threads:

- *BW:* typically 10-50 messages/second per process engine (depends on complexity)
- *UTLXe TEMPLATE:* 1,000-5,000 messages/second per container
- *UTLXe COMPILED:* 10,000-86,000 messages/second per container

The difference: BW's mapper interpreter is slower than UTL-X's tree-walking interpreter, and much slower than the COMPILED bytecode strategy. Plus UTLXe's worker model processes messages concurrently — BW processes require separate engine instances.

== Migrating from Custom Code

Many organizations have transformations written in Java, C\#, or Python — hand-coded XML/JSON processing.

=== When to Migrate

Migrate to UTL-X when:
- The transformation logic is primarily *field mapping* (A.field → B.field)
- Multiple developers need to maintain the transformation
- You need format flexibility (today XML, tomorrow JSON)
- You want testable, version-controlled transformation artifacts
- The custom code has grown unmaintainable (500+ lines of DOM manipulation)

Keep custom code when:
- The transformation involves *complex business logic* (not just mapping)
- You need database access, HTTP calls, or file I/O during transformation
- The transformation is performance-critical AND tightly coupled to the host application
- The code is well-tested and stable — "if it ain't broke, don't fix it"

=== Hybrid Approach

The best migration strategy is often hybrid: UTL-X for the mapping, custom code for the business logic:

```
Custom Code                           UTL-X
─────────────                         ──────
1. Receive message
2. Validate business rules
3. Call external API for enrichment
                                      4. Transform structure (field mapping)
                                      5. Format conversion (XML → JSON)
6. Send to target system
```

Steps 4-5 are the mapping — move these to `.utlx` files. Steps 1-3 and 6 stay in custom code. Connect them via the SDK wrapper (Chapter 33) or HTTP API.

This gives you the best of both worlds: custom code for orchestration and business logic, UTL-X for the mapping that changes most frequently and benefits most from format agnosticism.
