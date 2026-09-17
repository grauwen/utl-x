# COBOL Copybook & Fixed-Width (Flat File) Format Support

**Status:** Draft (2026-06-21)
**Target Version:** v1.2 (text/positional) · v1.3 (binary/EBCDIC)
**Type:** New format module (non-breaking)
**Author:** Open-M team (peer project — proposed via the established proposal process)
**Related:**
- `docs/architecture/reversexsl-integration-study.md` (the text-EDI pre-processor thread — complementary)
- Ch41 "Formats Not Yet Covered" (EDI/HL7/IATA gap list)
- `docs/comparison/vs-dataweave.md` (DataWeave "Flat File (fixed-width, positional)" — a UTL-X gap)
- `docs/proposals/xsd-jsch-format-support.md` (the format-support proposal this mirrors structurally)
- Open-M `open-m-connector-inventory-v2.md` #161 (fixed-width/copybook Build parser), `open-m-connector-network-matrix.md` (a `format` connector — no network, rides a transport)

---

## Executive Summary

UTL-X reads XML/JSON/CSV/YAML/Avro/Protobuf and schema formats (XSD/JSCH/TSCH/OSCH). It does **not**
read **positional / fixed-width records** or **COBOL copybook–described data** — the dominant
on-the-wire format of mainframe (IBM z/OS) and **IBM i (AS/400)** systems, and a staple of banking,
insurance, and government integration. DataWeave (the primary competitor) ships this as first-class
**Flat File** + **Copybook** formats; UTL-X's own `vs-dataweave.md` already lists it as a gap.

This proposes a new **`copybook` / `flatfile` format module** (`formats/copybook`) that decodes
positional records into the UTL-X Data Model (UDM), driven by a **COBOL copybook** (or a plain
fixed-width layout) as the schema — exactly as XSD/JSCH drive the structured formats today.

The crucial design point, and the reason this is **not** subsumed by the reverseXSL thread: reverseXSL
is a **regex-on-text** "anything-to-XML" parser. It handles positional **text** well, but it cannot
decode **binary field encodings** — EBCDIC codepages, packed decimal (COMP-3), zoned/overpunch
decimal, and binary (COMP-4/COMP-5). Those byte-level encodings *are* the mainframe/AS-400 payload.
So the two are complementary:

| Thread | Covers | Mechanism |
|---|---|---|
| **reverseXSL** (existing study) | EDIFACT / X12 / IATA / SWIFT MT / HL7 v2 / positional **text** | regex DEF files → XML, as a pre-processor |
| **This proposal** | COBOL copybook / fixed-width **binary** records — EBCDIC + packed/zoned/binary decimal | native binary-aware format reader → UDM |

## Motivation

### Use cases
- **Mainframe migration** — CICS/IMS and batch exports are "fixed-length COBOL copybook format"
  (UTL-X's own Ch31 §608–609 says so). MQ/file bridges deliver these records; something must decode
  them before mapping.
- **IBM i (AS/400)** — externally-described DB2/400 files and program-described records are copybook-
  shaped, EBCDIC-encoded, with packed/zoned decimals. This is the "AS/400 types" capability MuleSoft
  advertises — see the AS/400 note below.
- **Banking / insurance / government** — flat-file batch interchange (positional and copybook) remains
  pervasive; ISO 20022/EDIFACT modernisation has *not* eliminated it.
- **Competitive parity** — closes the DataWeave Flat File + Copybook gap named in `vs-dataweave.md`,
  and fills Open-M inventory #161 (the one connector capability neither TIBCO nor MuleSoft parity was
  covered against).

### Is MuleSoft's "AS/400 support" the same thing as copybook? — Advice

**Yes — same family, with two encoding concerns copybook parsing must handle:**

1. **The schema** — AS/400 records are described by a **COBOL copybook** (program-described) or by
   **DDS / externally-described DB2/400 files**. The copybook path is identical to mainframe. DDS is
   an *additional* schema source (phase 2), but the field model is the same.
2. **The encoding** — this is where AS/400 (and z/OS) differ from ASCII flat files and where the real
   work is:
   - **EBCDIC** codepages (cp037 US, cp500 international, cp273 DE, cp1141, …) — not ASCII.
   - **Packed decimal (COMP-3)** — two digits per byte, sign nibble in the last byte.
   - **Zoned decimal** — one digit per byte, sign overpunched into the last byte's zone nibble.
   - **Binary (COMP-4 / COMP-5 / BINARY)** — big-endian two's-complement integers.
   - **Signed / unsigned, implied decimal point** (PIC S9(7)V99 COMP-3), `REDEFINES`, `OCCURS`,
     `OCCURS … DEPENDING ON`.

So MuleSoft's AS/400 capability ≈ **DataWeave Copybook + Flat File formats + EBCDIC/packed-decimal
decoding**. Treat "AS/400 support" and "copybook support" as **one feature**: a copybook-driven,
EBCDIC/packed-decimal-aware positional reader. DDS-described files are an optional second schema
front-end onto the same decoder.

## Scope

**In scope (phase 1 — text/positional, v1.2):**
- Fixed-width / positional **text** records (ASCII/UTF-8), layout from a copybook or a simple
  field-spec (name, offset, length, type).
- Copybook parsing: `01/05/…` levels, `PIC X/9/A`, `PIC 9(n)V9(m)` implied decimal, groups.
- `REDEFINES`, `OCCURS n TIMES`, `OCCURS n TO m DEPENDING ON`.

**In scope (phase 2 — binary/EBCDIC, v1.3 — the differentiator):**
- **EBCDIC codepage** decode/encode (configurable: cp037/cp500/cp273/cp1141/…).
- **Packed decimal (COMP-3)**, **zoned decimal**, **binary (COMP-4/5)**, sign handling.
- Round-trip (write) support for the same, for egress to mainframe/AS-400.

**Out of scope (later / community):**
- **DDS** and RPG data-structure schema front-ends (phase 3 — reuse the phase-2 decoder).
- Record-type discrimination beyond `REDEFINES` (multi-format files) — can lean on reverseXSL or a
  UTL-X pre-match.
- The text-EDI formats (EDIFACT/X12/HL7/SWIFT) — those belong to the **reverseXSL** thread; this
  module deliberately does **not** duplicate them.

## Format Type Extension

Mirroring `xsd-jsch-format-support.md`:

```
FormatType += Copybook            # alias: FlatFile
```

**File-extension detection:** `.cpy`, `.cbl`, `.cob` (copybook schema); data files are typeless
(`.dat`, `.bin`, `.txt`) so the **format must be declared explicitly** with its schema — a positional
file is meaningless without its layout.

## Format Options Syntax

Consistent with the XSD proposal's `{ key: value }` options:

```utlx
%utlx 1.0
// Read an EBCDIC, packed-decimal mainframe record described by a copybook:
input copybook {
  schema:   "schemas/CUSTOMER.cpy",   // the copybook (or DDS in phase 3)
  encoding: "cp037",                  // EBCDIC US; omit / "ascii" for plain fixed-width text
  numeric:  "host"                    // host = packed/zoned/binary per PIC; "display" = text digits
}
output json
---
{
  customerId: $.CUSTOMER-RECORD.CUST-ID,
  name:       trim($.CUSTOMER-RECORD.CUST-NAME),
  balance:    $.CUSTOMER-RECORD.BALANCE      // PIC S9(7)V99 COMP-3 → decimal 1500.00
}
```

A plain positional **text** file (no copybook) can use an inline layout:

```utlx
input flatfile {
  layout: [
    { name: "id",      length: 6,  type: "integer" },
    { name: "name",    length: 20, type: "string"  },
    { name: "balance", length: 8,  type: "decimal", scale: 2 }
  ]
}
```

## Architecture

### Module structure (parallel to `formats/csv`, `formats/xml`, `formats/xsd`)

```
formats/copybook/
  src/main/kotlin/com/glomidco/utlx/formats/copybook/
    CopybookSchemaParser.kt   # copybook text → internal layout model (levels, PIC, OCCURS, REDEFINES)
    LayoutModel.kt            # field tree: offset, length, PIC, usage (DISPLAY/COMP-3/COMP/…), sign, scale
    PositionalReader.kt       # bytes + layout → UDM   (phase 1: text; phase 2: binary)
    PositionalWriter.kt       # UDM + layout → bytes   (egress / round-trip)
    codec/
      Ebcdic.kt               # codepage tables (cp037/cp500/…)
      PackedDecimal.kt        # COMP-3 encode/decode
      ZonedDecimal.kt         # zoned / overpunch sign
      BinaryNumeric.kt        # COMP-4 / COMP-5
```

The **copybook layout is a schema source** — architecturally the same role XSD/JSCH play: it types
the data and produces the UDM tree the transform navigates. This reuses UTL-X's existing
schema-driven-format machinery rather than inventing a parallel one.

### UDM representation

A copybook group maps to a UDM object; `OCCURS` maps to a UDM array; elementary items map to typed
scalars (string / integer / decimal with scale). Example:

```
01 CUSTOMER-RECORD.              →   CUSTOMER-RECORD: {
   05 CUST-ID   PIC 9(6).                CUST-ID:  42,
   05 CUST-NAME PIC X(20).               CUST-NAME:"JOHN SMITH          ",
   05 BALANCE   PIC S9(7)V99 COMP-3.     BALANCE:  1500.00
                                     }
```

### Implementation options (recommendation)

- **Option A — native binary reader (RECOMMENDED for the differentiated value).** Build
  `formats/copybook` as above. Only this path decodes EBCDIC + packed/zoned/binary decimal, which is
  the whole point for mainframe/AS-400. Medium effort; the codecs are well-specified and testable.
- **Option B — reverseXSL for text-only positional.** For pure ASCII fixed-length, the reverseXSL
  pre-processor (per its study) already suffices → XML → UTL-X. Keep this for text EDI; do **not**
  stretch it to binary (regex cannot decode COMP-3/EBCDIC).

**Recommended split:** adopt reverseXSL (its study's Option A) for **text EDI/positional**, and build
`formats/copybook` for **binary/EBCDIC copybook & AS-400**. Together they close Ch41's format gaps and
match DataWeave across both text and binary flat files.

## Relationship to Open-M (the requesting peer project)

Open-M embeds UTLXe as its inline mapping engine. In Open-M's model a fixed-width/copybook parser is a
**`format` connector (#161)** — it has *no network of its own*; a transport connector (`file`/`sftp`/
`ibm-mq`) delivers the bytes, and the copybook decode + mapping happen **inline on the arrow** in
UTLXe. So implementing this in UTL-X (not as a separate Open-M pod) is the architecturally correct
home: zero extra pod, mapping-native, consistent with how EDIFACT/X12 are planned (parser feeds
UTL-X TSCH). A classic pattern becomes:

```
IBM MQ (direct) → copybook decode + UTL-X map (inline) → SAP IDoc (direct)
```

## Roadmap

| Phase | Version | Deliverable |
|---|---|---|
| 1 | v1.2 | Copybook schema parser + positional **text** reader/writer (`REDEFINES`/`OCCURS`/`ODO`) |
| 2 | v1.3 | **EBCDIC** + **packed/zoned/binary** decimal codecs (the mainframe/AS-400 differentiator) |
| 3 | later | **DDS** / externally-described-file schema front-end onto the phase-2 decoder |

## Open questions

1. **`copybook` vs `flatfile` as the format name** — one format with a `schema` option (copybook *or*
   inline layout), or two aliases? (Proposed: one module, `copybook` with `flatfile` alias.)
2. **Codepage table sourcing** — bundle the common EBCDIC codepages, or rely on the JVM `Charset`
   (`Cp037`, `Cp500`, … are JVM-provided)? (Proposed: JVM Charsets for EBCDIC text; custom codecs only
   for packed/zoned, which JVM does not provide.)
3. **Copybook dialect coverage** — IBM Enterprise COBOL vs GnuCOBOL vs Micro Focus quirks (COMP-5
   endianness, `SIGN LEADING/TRAILING SEPARATE`); scope to IBM Enterprise COBOL first.
4. **reverseXSL boundary** — formalise "reverseXSL = text EDI, copybook module = binary flat file" so
   the two don't overlap.
