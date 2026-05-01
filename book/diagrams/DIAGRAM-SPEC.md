# UTL-X Book Diagram Specifications

## Color Palette (consistent across all diagrams)

| Element | Color | Hex |
|---------|-------|-----|
| UTL-X / UDM (primary) | Dark blue | #003366 |
| Input / Source | Green | #4CAF50 |
| Output / Target | Orange | #FF9800 |
| Error / Warning | Red | #F44336 |
| Neutral / Background | Light gray | #F5F5F5 |
| Text on dark | White | #FFFFFF |
| Borders | Medium gray | #BDBDBD |
| Highlight / accent | Light blue | #E3F2FD |

## Font: Segoe UI (or Calibri fallback)

## File Organization

```
book/diagrams/
├── DIAGRAM-SPEC.md          ← this file
├── part1-foundation.pptx    ← chapters 1-13
├── part2-language.pptx      ← chapters 14-21
├── part3-formats.pptx       ← chapters 22-30
├── part4-applications.pptx  ← chapters 31-39
├── part5-future.pptx        ← chapters 40-50
└── exported/                ← PNG/SVG exports for Typst inclusion
```

---

## Part 1: Foundation (chapters 1-13)

### Slide 1: ch01 — Four Partners, One Engine
**File:** part1-foundation.pptx
**Chapter reference:** `ch01-introduction.typ:24`

Four boxes on the left (labeled "Partner A: XML", "Partner B: JSON", "Partner C: CSV", "Partner D: YAML") with arrows converging into a central UTL-X box (dark blue, logo), then one arrow out to "Canonical Output" on the right.

**Message:** Many formats in, one engine, one output.

### Slide 2: ch01 — The Processing Pipeline
**Chapter reference:** `ch01-introduction.typ:79`

Horizontal flow: `Input (green)` → `Parse` → `UDM (dark blue)` → `Transform` → `UDM (dark blue)` → `Serialize` → `Output (orange)`

Each step is a rounded rectangle. Arrows between them. UDM boxes are taller/prominent. Parse and Serialize are smaller.

### Slide 3: ch03 — Three Pillars of Integration
**Chapter reference:** `ch03-transformation-in-integration.typ:17`

Three pillars (columns) supporting a bridge labeled "Integration":
- Left pillar: "Messaging" (green)
- Center pillar: "Transformation" (dark blue, highlighted)
- Right pillar: "Routing" (orange)

UTL-X logo on the center pillar.

### Slide 4: ch03 — The Anti-Pattern (Multi-Format Round-Trip)
**Chapter reference:** `ch03-transformation-in-integration.typ:65`

Flow: `JSON` → `XML Parser` → `XML` → `XSLT` → `XML` → `JSON Serializer` → `JSON`

Red "lossy!" markers between JSON→XML and XML→JSON. Red X marks on the unnecessary conversion steps.

### Slide 5: ch03 — The UTL-X Way
**Chapter reference:** `ch03-transformation-in-integration.typ:91`

Flow: `Any Format` → `Parse (once)` → `UDM` → `Transform` → `UDM` → `Serialize (once)` → `Any Format`

Green checkmarks. Clean, simple. Contrast with Slide 4.

### Slide 6: ch03 — Event-Driven Architecture
**Chapter reference:** `ch03-transformation-in-integration.typ:104`

Flow: `Service Bus` → `Dapr Sidecar` → `UTLXe Container` → `Dapr Sidecar` → `Target Queue`

Docker/container icons. Dapr logo. Cloud background.

### Slide 7: ch03 — Canonical Model (Star Topology)
**Chapter reference:** `ch03-transformation-in-integration.typ:124`

Star topology: 6 system boxes around the edges (SAP, D365, Shopify, WooCommerce, Warehouse, Accounting) with arrows to/from a central "Canonical Model (UTL-X)" hub.

Label: "N + M transformations instead of N × M"

### Slide 8: ch06 — Three Executables, Shared Core
**Chapter reference:** `ch06-the-three-executables.typ:20`

Layered diagram:
- Bottom layer (wide): "Shared Core" — Parser, UDM, Stdlib (652 functions), Format Parsers/Serializers (11 formats)
- Three boxes on top: "utlx (CLI)", "utlxd (Daemon/IDE)", "utlxe (Engine)"
- Each top box has its unique features listed

### Slide 9: ch06 — SDK Wrapper Architecture
**Chapter reference:** `ch06-the-three-executables.typ:171`

Left side: three boxes stacked — "C# App", "Go App", "Python App"
Arrows from each → central "UTLXe (JVM)" box
Label on arrows: "stdin/stdout protobuf"
Right side: alternative arrow labeled "HTTP API"

### Slide 10: ch07 — IDE Three-Panel Layout
**Chapter reference:** `ch07-the-ide.typ:22`

Screenshot-style mockup of VS Code with three panels:
- Left: "Input Panel" (sample data, green border)
- Center: "Transform Panel" (.utlx code, dark blue border)
- Right: "Output Panel" (result, orange border)
- Bottom: "UDM Tree Browser"

### Slide 11: ch10 — UDM Star Topology
**Chapter reference:** `ch10-universal-data-model.typ:23`

Star diagram: UDM in center (dark blue circle), 11 format icons around it (XML, JSON, CSV, YAML, OData, XSD, JSON Schema, Avro, Protobuf, EDMX, Table Schema) with bidirectional arrows.

Label: "Parse any → UDM → Serialize any"

---

## Part 2: Language (chapters 14-21)

### Slide 12: ch19 — Validation Sandwich
**Chapter reference:** `ch19-schema-validation.typ:112`

Three horizontal layers (sandwich):
1. Top bread: "PRE-VALIDATION — Validate input against schema" (green)
2. Filling: "TRANSFORMATION — Execute .utlx" (dark blue)
3. Bottom bread: "POST-VALIDATION — Validate output against schema" (orange)

Arrows flowing down. Red X on pre-validation = "Fail fast". Red X on post-validation = "Catch mapping bugs".

### Slide 13: ch20 — Pipeline Multi-Hop
**Chapter reference:** `ch20-pipeline-chaining.typ` (general)

Horizontal flow with 5 steps:
`Input` → `Step 1: Normalize` → `Step 2: Validate` → `Step 3: Enrich` → `Step 4: Generate` → `Output`

Between each step: "UDM hand-off (no serialize)" label
Below Step 3: additional input arrow from "Customer Data (JSON)"

### Slide 14: ch21 — Six Restructuring Patterns
**Chapter reference:** `ch21-data-restructuring.typ:21`

Four quadrants with arrows between states:
- "Flat" → "Hierarchical" (nestBy)
- "Flat" → "Enriched" (lookupBy)
- "Flat" → "Grouped" (chunkBy)
- "Hierarchical" → "Flat" (unnest)

Plus existing: groupBy, flatten

---

## Part 3: Formats (chapters 22-30)

### Slide 15: ch22 — XML to JSON Attribute Handling
**Chapter reference:** (ch22/ch23 general)

Two columns:
- Left: XML source with attributes highlighted (`<Total currency="EUR">299.99</Total>`)
- Right: two JSON outputs — default (attribute dropped) and writeAttributes=true (@currency + #text)

### Slide 16: ch30 — Cross-Format Conversion Matrix
**Chapter reference:** ch30 general

6×6 grid showing all format pair conversions. Color-coded: green (direct/trivial), yellow (needs mapping), gray (N/A).

---

## Part 4: Applications (chapters 31-39)

### Slide 17: ch31 — E-Invoicing Flow
**Chapter reference:** ch31 general

Flow: `Dynamics 365 (OData JSON)` → `Service Bus` → `UTLXe` → `Peppol Access Point (UBL XML)` → `Recipient`

### Slide 18: ch32 — Engine Three Phases
**Chapter reference:** `ch32-engine-lifecycle.typ` general

Three stacked boxes:
1. "DESIGN-TIME" — Developer → VS Code → .utlx (minutes/hours)
2. "INIT-TIME" — Parse → Compile → Register (milliseconds/seconds)
3. "RUNTIME" — Message → Transform → Output (per message, millions of times)

Arrow down between each. Timeline labels on the right.

### Slide 19: ch33 — Cloud Deployment
**Chapter reference:** ch33 general

Three cloud icons (Azure, GCP, AWS) each with UTLXe container inside. Price labels ($35, $44, $44).

---

## Part 5: Future (chapters 40-50)

### Slide 20: ch45 — AI Natural Language Round-Trip
**Chapter reference:** ch45 general

Circular flow:
`Natural Language` → (LLM generates) → `UTL-X Code` → (reads like) → `Natural Language`

"The Virtuous Circle" label.

---

## How to Use This Spec

1. Open PowerPoint
2. Create each PPTX file (part1 through part5)
3. For each slide: follow the layout description, use the color palette
4. Export each slide as PNG (300 DPI) to `book/diagrams/exported/`
5. Name files: `ch01-four-partners.png`, `ch03-pipeline.png`, etc.
6. In Typst, include with:
   ```typst
   #figure(
     image("diagrams/exported/ch01-four-partners.png", width: 100%),
     caption: [Four formats converge through one UTL-X engine]
   )
   ```
