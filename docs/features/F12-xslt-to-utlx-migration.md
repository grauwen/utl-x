# F12: XSLT-to-UTL-X Migration Tool

**Status:** Discussion  
**Priority:** High (competitive differentiator for Tibco/MuleSoft migration)  
**Created:** May 2026

---

## Summary

A deterministic transpiler that converts XSLT stylesheets to `.utlx` transformations. Targets 85-90% coverage of XSLT 1.0 (the version used by Tibco BW, SAP CPI, and most enterprise middleware). Complex constructs that cannot be transpiled deterministically are marked with `// TODO` for manual or AI-assisted completion.

## Architecture Decision: CLI Subcommand

The transpiler is a CLI subcommand (`utlx migrate`), NOT a separate binary:

| Option | Verdict | Reasoning |
|--------|---------|-----------|
| CLI subcommand | **Recommended** | Zero distribution overhead, instant discovery via `--help`, version-locked to core |
| Separate binary (`utlxt`) | Rejected | Distribution headache (Homebrew/Chocolatey/manual), version compatibility risk, poor discovery |
| Part of IDE (utlxd) | Rejected | The deterministic part doesn't need an IDE. Batch processing 500 files needs CLI. |
| Cookbook only | Complement | Documentation + examples alongside the tool, not a replacement |

The transpiler adds ~200KB to the native binary (pure Kotlin, no new dependencies — XSLT is XML, already parsed by UTL-X). Zero impact on normal `utlx` startup — migration code only initializes when `utlx migrate` is invoked.

### Two-Tier Migration Workflow

| Tier | Tool | Coverage | Interaction |
|------|------|----------|-------------|
| 1. Deterministic transpiler | CLI (`utlx migrate`) | 70-85% | Batch, scriptable, no network |
| 2. AI-assisted refinement | IDE (utlxd + LLM via MCP) | Remaining 15-30% | Interactive, schema-aware, live preview |

The CLI does the heavy lifting. The IDE handles the nuance. A customer migrating from Tibco BW runs the CLI first, then opens the results in the IDE to finish the TODOs.

## Target: XSLT 1.0 (Tibco BW)

Tibco BW (the most widely deployed integration platform) uses XSLT 1.0 via Apache Xalan. BW's visual mapper generates predictable, regular XSLT — no hand-crafted template matching tricks. This makes BW-generated XSLT particularly amenable to automated conversion.

XSLT 2.0/3.0 support is future scope — lower priority because most enterprise XSLT is 1.0.

## Deterministic Conversion Rules

### Trivial (direct mapping):

| XSLT | UTL-X | Notes |
|------|-------|-------|
| `<xsl:value-of select="customer/name"/>` | `$input.customer.name` | XPath → dot notation |
| `<xsl:for-each select="items/item">` | `map($input.items.item, (item) -> ...)` | Iteration |
| `<xsl:if test="price > 100">` | `if ($input.price > 100) ...` | Conditional |
| `<xsl:choose>/<xsl:when>/<xsl:otherwise>` | `match` or `if/else if/else` | Multi-branch |
| `<xsl:variable name="x" select="expr"/>` | `let x = expr` | Variable binding |
| `<xsl:sort select="price">` | `sortBy(items, (i) -> i.price)` | Sorting |
| `concat()`, `substring()`, `string-length()` | `concat()`, `substring()`, `length()` | Function mapping table |
| Named templates with `<xsl:call-template>` | `function Name(params) { ... }` | Function definition |
| `<xsl:copy-of select="node"/>` | `...$input.node` | Spread operator |
| `<xsl:element name="foo">` | `{"foo": ...}` | Element construction |
| `<xsl:attribute name="bar">` | `{"@bar": ...}` | Attribute construction |

### Pattern recognition (deterministic but requires detection):

| XSLT pattern | Detection | UTL-X output |
|------|-----------|------|
| Muenchian grouping (`key()` + `generate-id()`) | Detect `xsl:key` + double `for-each` with `generate-id` | `groupBy()` |
| Identity transform with exceptions | Detect `<xsl:copy>` + `<xsl:apply-templates select="@*|node()"/>` + override templates | `{...$input, overridden: newValue}` |
| `<xsl:apply-templates>` (simple) | Single matching template per element | Inline the template body |
| `document()` function | External document reference | Multi-input header |
| `format-number()` with patterns | Number formatting | `formatNumber()` |

### TODO markers (needs human or AI):

| XSLT construct | Why it can't be transpiled | TODO output |
|------|------|------|
| Complex multi-template matching with priorities | Implicit control flow, no UTL-X equivalent | `// TODO: template matching with priority — manual conversion needed` |
| Recursive `apply-templates` across modes | State machine behavior | `// TODO: recursive template modes` |
| `<xsl:import>` with override precedence | Inheritance semantics | `// TODO: import precedence` |
| Result-tree-fragment → node-set (XSLT 1.0 hack) | Workaround that disappears in UTL-X | `// TODO: result-tree-fragment conversion` |
| Complex XPath predicates with position() | Context-dependent evaluation | `// TODO: positional predicate` |

## XPath Conversion

The core of the transpiler is converting XPath 1.0 expressions to UTL-X:

| XPath | UTL-X | Rule |
|-------|-------|------|
| `customer/name` | `$input.customer.name` | `/` → `.` |
| `@id` | `$input.@id` | Keep `@` prefix |
| `item[1]` | `$input.item[0]` | 1-based → 0-based index |
| `item[last()]` | `$input.item[count($input.item) - 1]` | `last()` expansion |
| `item[price > 100]` | `filter($input.item, (i) -> i.price > 100)` | Predicate → filter |
| `//name` | `$input..name` | Recursive descent |
| `sum(item/price)` | `sumBy($input.item, (i) -> i.price)` | Aggregate functions |
| `count(item)` | `count($input.item)` | Direct |
| `string-length(name)` | `length($input.name)` | Rename |
| `translate(str, 'abc', 'ABC')` | `upperCase($input.str)` (if common pattern) | Pattern detection |
| `normalize-space(text)` | `normalizeSpace($input.text)` | Direct |

## Module Structure

```
modules/migrate/
  build.gradle.kts                         depends on: core, formats/xml
  src/main/kotlin/org/apache/utlx/migrate/
    MigrateCommand.kt                      CLI command handler
    XSLTTranspiler.kt                      Main transpiler orchestrator
    XPathConverter.kt                      XPath 1.0 → UTL-X expression converter
    XSLTPatternDetector.kt                 Detect BW patterns, grouping, identity transform
    XSLTFunctionMapper.kt                  XSLT/XPath function → UTL-X stdlib mapping
    UTLXCodeGenerator.kt                   Generate formatted .utlx output
    MigrationReport.kt                     Summary: converted, TODO, skipped
  src/test/kotlin/
    XSLTTranspilerTest.kt                  End-to-end transpilation tests
    XPathConverterTest.kt                  XPath expression conversion tests
```

## CLI Interface

```bash
# Basic: single file
utlx migrate invoice.xsl
# Output: invoice.utlx

# With schemas (improves type hints in output)
utlx migrate invoice.xsl --input-schema invoice.xsd --output-schema order.xsd

# Batch: entire directory
utlx migrate src/xslt/ --output-dir src/utlx/

# Report only (don't generate, just analyze)
utlx migrate --analyze invoice.xsl
# Output: 14/16 constructs convertible (87%), 2 TODOs

# Verbose: show each conversion decision
utlx migrate --verbose invoice.xsl
```

## Migration Report

After conversion, a report summarizes what happened:

```
Migration Report: invoice.xsl → invoice.utlx
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Templates converted:    8/10 (80%)
  XPath expressions:     23/25 (92%)
  Functions mapped:      12/12 (100%)
  TODO markers:           2
    Line 34: // TODO: recursive apply-templates with mode="summary"
    Line 67: // TODO: Muenchian grouping with composite key

  Overall: 87% automated — 2 sections need manual review
```

## GraalVM Native Image

The transpiler is pure Kotlin operating on UDM (parse XSLT as XML, walk tree, emit strings). No new dependencies, no reflection, no resource bundles. Works in native image without configuration.

## IDE Integration (Tier 2 — Future)

After CLI produces `.utlx` files with TODO markers, the IDE can:
- Show original XSLT and generated UTL-X side-by-side
- Highlight TODO sections with the original XSLT context
- Suggest completions via LLM (connected through MCP)
- Validate the converted output against schemas (IDE Design Time mode)

This is a utlxd enhancement, not part of F12. F12 is the deterministic CLI transpiler only.

## Effort Estimate

| Task | Effort |
|------|--------|
| XPath 1.0 → UTL-X expression converter | 3-4 days |
| XSLT construct mapping (trivial + medium) | 3-4 days |
| BW pattern detector (Muenchian, identity, etc.) | 2-3 days |
| Function mapping table (40+ XPath/XSLT functions) | 1-2 days |
| CLI command + report generation | 1 day |
| Tests (unit + BW-generated XSLT samples) | 2-3 days |
| **Total** | **12-16 days** |

## Competitive Value

No other transformation tool offers automated XSLT migration:
- MuleSoft: manual rewrite from XSLT to DataWeave
- Azure Logic Apps: manual rewrite from XSLT to Liquid templates
- Informatica: no XSLT migration path

`utlx migrate` would be the first automated XSLT-to-modern-transformation converter — a significant selling point for enterprises with hundreds of XSLT stylesheets locked in Tibco BW or SAP CPI.

---

*Feature F12. May 2026. Discussion document.*
*Key insight: XSLT is XML — UTL-X already parses it. The transpiler is a tree walker that emits UTL-X code, not a new parser.*
