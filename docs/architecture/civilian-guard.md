# UTL-X Guard (Civilian Edition) — the open-standards guard first

*Working document, Glomidco B.V. · 2026-10 · v1.2 · Status: exploratory strategy. Not legal, export-control or accreditation advice.*

| Field | Value |
|---|---|
| Document | civilian-guard |
| Status | Exploratory — proposes building and fielding a civilian guard before the MIL edition |
| Scope | Product definition, format scope, markets, assurance route, roadmap and risks of a UTL-X content guard limited to **open standards** |
| Related | `UDM-content-guard.md` (architecture, shapes A–D), `UTL-X-MIL-Standards-Overview.md` (open-standards programme, effort), `UTL-X-MIL-Possibilities.md` (restricted track), `interlink-protocol-v1.md`, `hardware-cots-shopping-list.md`, book *UTL-X MIL — The Content Guard* (Ch. 4, investment case) |

> **One line:** build the guard first for civilian and dual-use customers, on open standards only —
> same engine, same architecture, same non-negotiables — so it can be sold, fielded and proven now,
> while the MIL edition becomes "the civilian guard + restricted packs + higher assurance".

---

## 1. The decision in short

The MIL track depends on things Glomidco does not control: a sponsor, export-control clearance,
access to restricted standards, and a multi-year accreditation campaign. The civilian guard depends
on none of them.

| | Civilian guard (first) | MIL guard (later) |
|---|---|---|
| Formats | open standards only | open + restricted packs (Link 16, JREAP-C, VMF, …) |
| Sponsor needed | no | yes |
| Export control | light (dual-use check, see §10) | heavy (restricted standards, possibly ITAR/EAR exposure via partners) |
| Assurance target | evidence-based, then BSPA / modest Common Criteria / IEC 62443 | NATO, national high assurance, NCDSMO RTB (US, via partner) |
| Deployment shapes | A (gateway), B (diode-adjacent), D (split guard) | A–D, incl. C with an accredited hardware verifier |
| Time to first customer | months | years |

**Principle:** nothing built for the civilian guard is throw-away. The engine, parser profile, rule
library, canonical serializer, audit trail and hardware path are identical; the MIL edition adds
packs and assurance, not a different architecture.

---

## 2. Product definition

**UTL-X Guard (civilian edition)** is a content guard / content gateway that sits on the boundary
between two trust zones and, for every message:

```
untrusted bytes → hardened parse → UDM → validate.* rules → canonical serialize → egress
                                         └──── any failure → dead-letter + audit record
```

### 2.1 Non-negotiables (unchanged from the MIL design)

1. **No pass-through.** Every message is parsed to the UDM and re-serialized; the original bytes never
   reach egress. The target format is a policy choice (same format, another format, or a simple form).
2. **Allow-list, fail-closed.** Only content matching an approved Message Contract and rule set passes.
3. **Pure and deterministic.** Only UTL-X 1.0 / 1.1 in the trusted path; `ai.*` (2.0) is excluded.
4. **Canonical, low-fidelity output.** Comments, BOMs, padding, unknown and non-allow-listed fields are dropped.
5. **Every verdict auditable.** Input hash, output hash, contract ID, rule-set version, rule IDs, timestamp.
6. **UDM only sees what it models.** Opaque binary payloads are blocked or handed to a specialised inspector
   (e.g. a file-CDR engine).

> "Civilian" must never mean "softer". These six rules are what make the later step to MIL a matter of
> assurance and packs rather than redesign.

### 2.2 Deployment shapes in scope

| Shape | Civilian use | Notes |
|---|---|---|
| **A — content gateway** | inline gateway / API proxy between zones or organisations | first and simplest product |
| **B — diode-adjacent filter** | content policy next to a hardware data diode (OT, low→high import) | partner with diode vendors |
| **D — split guard** | two owners, two halves, one-way interlink (`interlink-protocol-v1.md`) | chain partners that will not host each other's box |
| **C — hardsec transform stage** | only with a partner's verifier | mostly a MIL/high-assurance play; keep the hook |

---

## 3. Format scope — open standards only

| Class | Formats | Mechanism | Status |
|---|---|---|---|
| **Tier 1 — data** | JSON, XML, CSV, YAML, OData | native readers | in UTL-X 1.0 |
| **Tier 2 — schema / metadata** | XSD, JSON Schema (JSCH), Avro, Protobuf, OSCH (OData/EDMX), TSCH (Table Schema) | native | in UTL-X 1.0 |
| **Maritime / coast guard** | CISE, AIS (NMEA 0183), IALA IVEF, IHO S-100 (GML products), EMSWe, Cospas-Sarsat | XML profiles; BINF + form-class | open-standards programme |
| **Aviation / surveillance** | ADS-B (1090ES), ASTERIX | BINF + form-class (+ FSPEC extension) | open-standards programme |
| **Ports, logistics, inland shipping** | UN/EDIFACT, RIS (ERI, NtS, Inland AIS) | EDIFACT-class reader; BINF | open-standards programme |
| **Open defence / security (dual-use)** | MISB KLV ST 0601, Cursor on Target / TAK, C2SIM, DIS, APP-6 / MIL-STD-2525 symbology | BINF / KLV; XML; Protobuf; lookup | open-standards programme |
| **Labels** | STANAG 4774 / 4778 (public), national government classification labels | XML + `validate.*` | guard rule library |

**Out of scope for this edition:** Link 16 J-series, JREAP-C, VMF, Link 22, and any standard whose
release status is unclear (e.g. STANAG 4607 until checked). These remain the separately governed
MIL edition.

Note that the open "military" formats (KLV, CoT, C2SIM, DIS) are publicly available, so the civilian
guard already covers substantial dual-use ground: police and border-security drones, civil–military
cooperation in crisis response, training and simulation.

---

## 4. Markets and first customers

| # | Segment | Use case | Shape | Formats | Why now |
|---|---|---|---|---|---|
| 1 | **Coast guard and maritime authorities** | CISE adaptor that also enforces policy; sharing tracks and incidents between agencies and countries | A, D | CISE, AIS, IVEF, EDIFACT | CISE requires an adaptor per connected system — a guard is an adaptor with policy |
| 2 | **Critical infrastructure / OT** (energy, water, ports) | one-way export of sensor/SCADA-derived data out of the control network; controlled import of updates/manifests | B | CSV, JSON, XML, OPC-UA-derived exports*, EDIFACT | data diodes are common; regulation (NIS2 and national implementations) pushes demonstrable control of data flows |
| 3 | **Government data sharing** | ministries and chain partners exchanging structured data across trust zones | A, D | JSON, XML, OData, XSD/JSCH, labels | natural market for a national product assessment (BSPA, §6) |
| 4 | **Air traffic / airport surveillance** | filtering and normalising surveillance feeds between operators | A, B | ASTERIX, ADS-B | strong BINF showcase; conservative buyers |
| 5 | **Police / border security / crisis response** | drone metadata, situational awareness between agencies | A | MISB KLV, CoT, C2SIM | dual-use stepping stone towards defence |

\* OPC UA itself is not in the current format list; exports from OT historians are typically CSV/JSON/XML.
A dedicated OPC UA reader is a possible later addition.

**Recommended wedge:** pick **one** of segments 1–3 for the first reference customer. The choice
determines which formats and which credential come first (see §12, open questions).

---

## 5. Positioning and competition

### 5.1 The gap

> File-CDR products disarm **documents**. API gateways and WAFs **route and filter** traffic.
> Diodes enforce **direction**. Nobody rebuilds **structured messages** across every format —
> text and binary — under one declarative, auditable rule set.

### 5.2 Civilian competitive landscape

| Category | Examples | Relationship |
|---|---|---|
| File CDR | OPSWAT, Glasswall, Votiro | complementary (files vs messages); possible integration for opaque payloads |
| API gateway / WAF | Kong, Apigee, Azure API Management + WAF, F5 | competitor for "good enough"; UTL-X Guard is allow-list content rebuild, not deny-list traffic filtering |
| Data diodes with filters | Fox Crypto, Arbit, Owl, Waterfall, Advenica | **partners** (shape B); some offer third-party filter integration |
| Integration platforms | ESB/iPaaS vendors | adjacent; they transform, but are not built as fail-closed guards |
| Cross-domain vendors | Infodas/Airbus, Everfox, Isode, BAE | mostly MIL/government high assurance; meet them later |

### 5.3 Naming

- **UTL-X Guard** — civilian edition, open standards, open core.
- **UTL-X MIL Guard** — same engine, restricted packs, accredited.

Tagline: **"MIL-ready by design, proven in civilian service."**

---

## 6. Assurance route (civilian)

Climb in evidence first, then in formal credentials.

| Step | What | Purpose |
|---|---|---|
| 1 | **Engineering evidence**: guard-profile fuzzing (coverage-guided, grammar-based, differential), OWASP/CRS payloads as test corpus, reproducible native build, SBOM | baseline any serious buyer asks for |
| 2 | **Independent pen test and code review** | first external evidence |
| 3 | **Secure development process**: ISO/IEC 27001 controls for the organisation; **IEC 62443-4-1** (secure product development) and **62443-4-2** (component requirements) for OT buyers | opens segment 2 |
| 4 | **NL: BSPA** (Baseline Security Product Assessment, NBV) — product assessment aimed at use up to *Departementaal Vertrouwelijk* / EU RESTRICTED | opens segment 3; first government credential |
| 5 | **Common Criteria** at a modest level (e.g. EAL2–EAL4+ with a guard-specific security target) | international recognition |
| 6 | → MIL edition: national high-assurance evaluation, NATO, partners for NCDSMO RTB | the MIL track |

Each step produces evidence the MIL track will reuse — the civilian route is the first half of the
MIL route, not a detour.

*(Verify current BSPA scope and procedure with the NBV/NLNCSA before planning; credentials and
levels above are indicative.)*

---

## 7. Hardware

Civilian buyers mostly run the guard as software; hardware climbs only where assurance or OT
environments require it. How the product is *delivered* — cloud, on-premises, appliance —
is covered in §8.

| Phase | Platform | Notes |
|---|---|---|
| Software product | x86 or ARM server, VM or container (Open-M `mode: component` pod) | GraalVM-native binary; shapes A and D |
| Diode-adjacent | server next to a certified diode (Fox, Arbit, Owl, Waterfall) | shape B; diode carries flow assurance |
| Demonstrator | 2 × ZCU106 split guard (see `ZCU106-dual-10GbE-guard-prototype-abstract.md`) | public formats only — already civilian |
| Appliance (later) | small fanless SoC-FPGA box or the 2U 12-slot multi-guard chassis concept | for OT and multi-flow sites; power-only backplane rules apply |

No rugged SOSA hardware is needed for the civilian edition.

---

## 8. Delivery models

**Decision:** the civilian guard is **software-first, hardware later**. The hardware step is not
necessarily through partners: it can be a partner bundle, an appliance on a reference platform, or
Glomidco's own appliance — the route is chosen when demand and funding are clear. The uniqueness of
the offering is the content layer — every message rebuilt through one model, across every format
including binary, under one declarative rule set — and a dedicated UTL-X Guard appliance can add
physical assurance on top of it.

### 8.1 Software vs own hardware

| | Software (Azure Marketplace, container, VM) | Own hardware (appliance) |
|---|---|---|
| Time to first customer | weeks — a trial is one click | months — build, certify, ship |
| Capital and risk | low | high — stock, components, supply chain |
| Certification | product security | plus CE, EMC, electrical safety, possibly MIL/naval |
| Support | central, remote updates | RMA, spare parts, on-site service, multi-year lifecycle |
| Procurement | Marketplace (eligible offers can count towards customers' Azure commitments — verify per offer type) | tenders, purchase orders, delivery |
| Scaling | unlimited | linear with production |
| Assurance claim | content assurance | content assurance + physical one-way flow and protocol break |
| Fits | gateway (A), split guard over a network (D) | OT, diode-adjacent (B), high-security sites |

### 8.2 What the cloud cannot do

A guard in Azure is excellent for **API and data sharing between organisations and tenants** (CISE,
chain partners, government data sharing). One-way flow, physical separation and a protocol break do
not exist inside a cloud subscription. Segment and form factor therefore go together:

| Segment | Natural delivery |
|---|---|
| Coast guard / CISE, government data sharing | cloud or on-premises software |
| Critical infrastructure / OT, higher security levels | on-premises, next to a diode or as an appliance |

### 8.3 One product, three delivery models

| # | Model | Shape | What it is | Who brings the hardware |
|---|---|---|---|---|
| 1 | **UTL-X Guard for Azure** (first) | A, D | Azure Marketplace offer — container or managed application; builds on the *UTLXe on Azure* deployment guide | Microsoft (cloud) |
| 2 | **UTL-X Guard on-premises** | A, D | the same software as container or VM on the customer's servers (government, OT, no-cloud policies) | customer |
| 3 | **UTL-X Guard appliance** (later) | B (and A/D on-site) | three possible routes, chosen when demand is clear: (a) **bundle with a diode vendor** (Fox Crypto, Arbit, Owl, Waterfall) — they supply certified one-way hardware, UTL-X the content filter; Arbit already supports third-party filters. (b) **reference platform** — a standard fanless industrial PC (x86 or ARM), certified by Glomidco for the guard software, installed and sealed by an integrator. (c) **own UTL-X Guard appliance** — e.g. the SoC-FPGA split guard or the 2U multi-guard chassis (§8.4) | diode vendor, integrator, or Glomidco |

### 8.4 Own hardware — the later step

Glomidco's own appliance is a real option, not only a showcase. The SoC-FPGA split guard (2 × ZCU106
demonstrator) and the 2U 12-slot multi-guard chassis show where it leads: one product that combines
content assurance with physical one-way flow, protocol break and hot-swappable guard cards — a
genuinely unique offering in the civilian market.

What it takes, and why it comes after the software:

| Topic | Implication |
|---|---|
| Design | own carrier board / card (e.g. Kria SOM-based), enclosure, power-only backplane |
| Certification | CE, EMC, electrical safety; for OT sites possibly IEC 62443-4-2 on the device |
| Supply chain | component sourcing, lead times, long-term availability of the SoC-FPGA |
| Production | contract manufacturer (EMS partner) rather than own production |
| Support | RMA, spare parts, firmware/bitstream updates, multi-year lifecycle |
| Funding | upfront investment before revenue — best triggered by a launching customer or programme |

**Trigger for the hardware *product*:** a launching customer (e.g. an OT operator or government
agency) or a funded programme that needs a dedicated appliance, or software revenue sufficient to
finance it. Partner bundles (route a/b) can serve early on-site demand in the meantime.

### 8.4.1 First step: a hardware proof of concept on own hardware

**Sequence:** finish the software solution first (phases 0–4, §9), then build a **proof of concept on
Glomidco's own hardware** — before any decision on the hardware product route.

```
software guard (phases 0–4) ──▶ hardware PoC on own hardware (phase 5) ──▶ decide route a / b / c
```

**Why own hardware, and why after the software:**

- The PoC proves the claims a partner bundle cannot: protocol break and frame verification in FPGA
  logic, a physically one-way interlink, and the split-guard ownership model — with **UTL-X's own**
  design, not a vendor's box.
- Doing it after the software means the PoC runs the **finished, unchanged guard binary** on the ARM
  cores (one binary everywhere, §8.5). The PoC then tests hardware, not half-finished software.
- It produces the most convincing demonstration for customers, partners and investors, and the facts
  needed to choose between routes a, b and c.

**Scope (civilian, public data only):**

| Item | Choice |
|---|---|
| Boards | 2 × AMD ZCU106 (Zynq UltraScale+): one per owner — split guard (shape D) |
| Interlink | SFP+ 10G fibre, single strand A→B, `interlink-protocol-v1.md` profile E (raw L2, no IP) |
| Board A (sender) | UTL-X guard on ARM: parse → UDM → `validate.*` → canonical simple form; FPGA: BINF decode + CRC (AIS), protocol break, framing |
| Board B (receiver) | FPGA: independent frame + simple-form verifier; ARM: UTL-X import policy, rebuild, egress |
| Formats | JSON, XML, CSV and public AIS / ADS-B |
| Housing | bench first; optionally two 2U micro-ATX cases (one per owner) for demonstrations |

**Success criteria** (as in the book, Ch. 4): the same rule block passes a JSON position report and an
AIS frame with identical output; bombs, forbidden fields and label failures are blocked fail-closed with
audit records; an AIS frame is decoded in Board A's fabric; a tampered frame is rejected **by Board B's
verifier** (independence); latency and throughput are measured (indicative).

**Indicative hardware budget:** 2 × ZCU106 (list ≈ $3,234 each), SFP+ optics/fibre, optional cases —
roughly **€7 – 9k** excluding VAT and labour.

**Not in scope:** rugged or certified hardware, TEMPEST, accreditation, production design. The PoC
proves the concept; the product route is decided afterwards.

### 8.5 The rule that makes this work: one binary everywhere

The software is **identical** across all delivery models — the same GraalVM-native binary, the same
Message Contracts and policies, the same audit records — whether it runs in Azure, on-premises, next
to a diode, or on an FPGA board. A customer can start in the cloud and move to an appliance later
without touching a rule. That portability is a selling point in itself, and it keeps the civilian and
MIL editions on one codebase.

---

## 9. Roadmap and effort

Effort figures reuse `UTL-X-MIL-Standards-Overview.md` §6 (person-weeks, one experienced Kotlin
developer familiar with UTL-X; order of magnitude).

| Phase | Content | Effort | Outcome |
|---|---|---|---|
| **0. Guard core** | guard-profile parsers (Tier 1/2), canonical serializer, `validate.*` rule library (7 categories), audit record, dead-letter, no-pass-through enforcement | 8 – 14 pw (estimate) | software guard for JSON/XML/CSV/YAML/OData flows |
| **1. Foundation** | BINF, streaming runtime, transports, tests | 15 – 23 pw | binary-capable engine |
| **2. Tier A packs** | AIS/NMEA, KLV, EDIFACT, CoT, CISE, geometry | 18 – 30 pw | coast-guard and dual-use scope |
| **3. Tier B packs** | ASTERIX, RIS, IVEF, S-124/S-421, DIS, C2SIM, Cospas-Sarsat | 16 – 27 pw | full open-standards scope |
| **4. Productisation** | configuration UI/CLI for contracts and policies, monitoring, packaging, docs | 6 – 10 pw (estimate) | sellable product |
| **5. Hardware PoC (own hardware)** | split guard on 2 × ZCU106: FPGA protocol break, BINF decode, interlink profile E, independent verifier; finished guard binary on ARM (§8.4.1) | 6 – 10 pw (estimate) + ≈ €7 – 9k hardware | proof of concept on own hardware; basis for the hardware-route decision |
| **6. Assurance** | fuzzing campaign, pen test, 62443/BSPA preparation (can start in parallel with phase 4) | partly external cost | first credential |

The hardware PoC needs BINF and the AIS pack (phases 1–2); it is planned after the complete software
solution, but could be pulled forward after phase 2 if an earlier demonstration is needed.

Order inside phases follows the chosen wedge: a CISE-first wedge pulls CISE and AIS forward; an
OT-first wedge prioritises guard core + shape B integration with one diode vendor.

**Total to full open scope:** roughly the 49 – 80 pw open-standards programme plus guard core and
productisation (≈ 63 – 104 pw), i.e. 15 – 24 months for one developer, roughly half with two. The
hardware PoC adds ≈ 6 – 10 pw (FPGA/HLS work, ideally with an engineer experienced in Vitis) and
≈ €7 – 9k in hardware.

---

## 10. Legal, licensing and export

- **Licensing:** UTL-X is open source (AGPL-3.0). An open-core model fits: engine and public packs open;
  guard-specific productisation, support, certified builds and policy tooling commercial. Check that the
  AGPL obligations are acceptable to government and OT buyers, or offer a commercial licence.
- **Export control:** open standards remove the restricted-standards problem, but a product with
  cryptographic functions (TLS, HMAC on the interlink, label signatures) may still fall under EU dual-use
  rules (Regulation (EU) 2021/821, category 5 part 2). Far lighter than ITAR, but **check before the first
  sale outside the EU**.
- **No restricted content in the codebase.** The engine stays content-free; restricted form-classes never
  enter the civilian repository.

---

## 11. Risks

| Risk | Mitigation |
|---|---|
| Buyers settle for "API gateway + WAF" | sharp pitch (§5.1); show the no-pass-through rebuild and binary coverage in a demo |
| File-CDR vendors claim the space | position as complementary (messages vs files); integrate for opaque payloads |
| "Civilian" erodes the design | non-negotiables (§2.1) enforced in code and tests; same CI as the future MIL edition |
| Too broad a format scope too early | one wedge, one reference customer, formats pulled by that customer |
| Credential takes longer than planned | evidence-first (§6 steps 1–3) gives sales arguments before formal credentials |
| Single-developer bottleneck | parallelise packs once the foundation is done; partner for productisation |

---

## 12. Open questions

1. **Wedge:** coast guard (CISE), critical infrastructure (OT with diodes) or government data sharing?
2. **First credential:** BSPA, IEC 62443-4-2, or Common Criteria — which does the first customer require?
3. **Diode partner** for shape B: Fox Crypto, Arbit, Owl or Waterfall?
4. **Marketplace offer type:** container offer, Azure managed application, or both? Which pricing model (per flow, per message volume, per instance)?
5. **Licensing model:** AGPL + commercial licence, or a separate commercial guard distribution?
6. **OPC UA:** add a reader for the OT segment, or rely on historian exports?
7. **Hardware route:** after the hardware PoC — partner bundle, reference platform, or own appliance, and what is the trigger (launching customer, programme, or revenue threshold)?
8. **PoC staffing:** in-house FPGA/HLS skills or an external Vitis engineer for phase 5?
9. **Book:** add a section "Civilian first: the open-standards guard" to Chapter 4 (investment case)?

---

## 13. Bottom line

A civilian, open-standards guard can be built and fielded **now**, without a sponsor or restricted
access, on the same engine and architecture as the MIL guard. It creates customers, references and
assurance evidence — exactly what the MIL track lacks today. Build it first, keep the non-negotiables
uncompromised, and the MIL edition becomes an extension rather than a separate bet.
