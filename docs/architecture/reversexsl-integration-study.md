# reverseXSL Integration Study

**Date:** May 2026  
**Status:** Under consideration  
**Related:** Ch41 (Formats Not Yet Covered), F08 (USDL pipeline), EDI/IATA/SWIFT support

---

## What Is reverseXSL?

[reverseXSL](https://github.com/berhauz/reverseXSL) is an open-source "anything-to-XML" parser written in Java (Apache 2.0 license). It converts structured text formats — EDI/EDIFACT, ANSI X12, IATA messages, SWIFT MT, HL7 v2, fixed-length records, and other positional/delimited formats — into XML using regex-driven DEF (definition) files.

### Parsing Approach

reverseXSL uses a 4-step recursive process:
1. **Identify** — match the input against segment/record patterns
2. **Cut** — split into constituent parts (segments, fields, components)
3. **Extract** — pull values from identified positions using regex
4. **Validate** — check extracted values against constraints

DEF files describe the expected syntax declaratively — format definition rather than parsing code. This is philosophically aligned with UTL-X's approach: describe what you want, let the engine execute.

### Supported Formats

| Format | Use case |
|--------|----------|
| EDIFACT | International EDI (logistics, retail, government) |
| ANSI X12 | North American EDI |
| IATA | Airline messaging (PNR, ticketing, cargo) |
| SWIFT MT | Financial messaging (pre-ISO 20022) |
| HL7 v2 | Healthcare (pipe-delimited) |
| Fixed-length | Mainframe exports, COBOL copybooks |
| Positional/delimited | Any structured text with known layout |

## Why It's Relevant for UTL-X

UTL-X's ch41 identifies EDI, HL7 v2, and IATA as high-priority format gaps. reverseXSL covers all of them. The integration would turn UTL-X's biggest format gaps into strengths.

### Current State Without reverseXSL

```
EDI message → External tool (Smooks, commercial) → XML → UTL-X → target format
```

Two tools, two deployments, two failure points.

### With reverseXSL Integrated

```
EDI message → UTL-X (reverseXSL pre-parser → XML → transform) → target format
```

One tool, one deployment. The user writes:

```utlx
%utlx 1.0
input edi {def: "edifact-orders.def"}
output json
---
{
  orderId: $input.Order.BGM.DocumentNumber,
  lines: map($input.Order.LIN, (line) -> {
    product: line.ItemNumber,
    quantity: toNumber(line.QTY.Quantity)
  })
}
```

The `{def: "..."}` option tells UTL-X which DEF file to use for parsing. The rest is standard UTL-X.

## Technical Assessment

### Compatibility

- **License:** Apache 2.0 — permissive, compatible with UTL-X's AGPL
- **Language:** Java — runs on the same JVM as UTLXe, direct API call (no IPC)
- **Dependencies:** minimal (pure Java, no external dependencies beyond JDK)
- **Build:** Ant (would need migration to Gradle for integration)

### Integration Options

#### Option A: Library Dependency

Add reverseXSL as a Gradle dependency. Create a new format module (`formats/edi/`) that wraps reverseXSL's parser API:

```kotlin
"edi", "edifact", "x12", "iata", "swift-mt" -> {
    val defFile = options["def"] as? String ?: error("EDI format requires {def: \"...\"}")
    val xmlResult = ReverseXSLParser(data, defFile).parse()
    XMLParser(xmlResult).parse()  // Parse the XML output into UDM
}
```

Effort: 2-3 days (wrapper + format registration + basic tests).

#### Option B: Fork and Modernize

Fork reverseXSL, modernize the codebase:
- Ant → Gradle
- Java → Kotlin wrappers (keep Java core)
- Integrate DEF files into UTL-X's resource loading
- Add conformance tests for each format
- Maintain as a UTL-X sub-module

Effort: 1-2 weeks.

#### Option C: Pre-processor (No Integration)

Document reverseXSL as a recommended pre-processor:

```bash
# Convert EDI to XML, then transform with UTL-X
java -jar reversexsl.jar -def edifact-orders.def < orders.edi | utlx transform order-to-json.utlx
```

Effort: 0 (documentation only). But requires users to manage two tools.

### Recommendation

**Option A (library dependency) for v1.1.** Minimal effort, immediate value. The wrapper is thin — reverseXSL does the hard parsing work, UTL-X handles the transformation. No need to fork or maintain the reverseXSL codebase.

**Option B (fork and modernize) for v2.0** if EDI demand grows and reverseXSL's original author is not maintaining it.

## Risk Assessment

- **Maintenance:** reverseXSL's last commit was 2017. But EDI/IATA/SWIFT formats are stable standards — the parsing logic doesn't change. Risk is low for the parsing core; risk is medium for JDK compatibility (may need minor updates for newer Java versions).
- **Quality:** Apache 2.0 with a clear codebase. Used in production (IATA context per the author). Would need UTL-X conformance tests to verify correctness.
- **Size:** Small library (~50 Java files). Does not bloat the UTL-X distribution significantly.

## Impact

If integrated, UTL-X would cover:
- 5 data formats (current: JSON, XML, CSV, YAML, OData)
- 6 schema formats (current: XSD, JSON Schema, Avro, Protobuf, EDMX, Table Schema)
- **+ 7 structured text formats** (new: EDIFACT, X12, IATA, SWIFT MT, HL7 v2, fixed-length, positional)

That's **18 formats** — more than any integration tool on the market.

---

*Architecture study. May 2026.*
