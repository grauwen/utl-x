# Hardware Acceleration (FPGA / SmartNIC) for the UTL-X Guard

*Working document, Glomidco B.V. · 2026-10 · v2 · Status: exploratory. Not an engineering commitment.*

| Field | Value |
|---|---|
| Document | hardware-acceleration |
| Version | v2.1 (2026-10) — adds split guard, enclosures/TEMPEST and shipboard minimal-SWaP (§9.8); v2: corrected DataPower history, DPU regex status; added the **transform → verify (hardsec)** pattern |
| Status | Exploratory reasoning / feasibility |
| Scope | Can the UDM content guard (or parts) run in hardware — FPGA/SmartNIC — à la IBM DataPower? And where does hardware fit a UTL-X **guard/gateway**? |
| Related | `UDM-content-guard.md`, `guard-parser-profile.md`, `BINF-bit-level-binary-format.md`, `hardware-cots-shopping-list.md` (what to actually buy) |

> **Verdict up front.** The *whole* guard in FPGA: **no** — UTL-X's dynamic-UDM-tree +
> general-`validate.*` + text-parsing core is antithetical to the fixed-streaming FPGA
> model, and the XML-appliance history warns against it. A **hybrid fast-path /
> slow-path** (hardware for stable, hot, streaming primitives; software for the general
> engine) is feasible and is the industry-standard pattern.
>
> **New in v2 — the more important insight:** in high-assurance cross-domain products
> the FPGA is mostly used *not to accelerate* but to **verify**. The established
> "hardsec" pattern is: **software transforms complex data into a simple, strongly typed
> form → hardware logic verifies that form → the delivered message is rebuilt from
> verified data.** UTL-X is a natural *transformer* in that pattern, and a **BINF
> form-class (or simple-form schema) compiled to a hardware verifier** is the standout
> hardware target.

---

## 1. The DataPower lesson (corrected)

IBM acquired DataPower in **October 2005**. The product line at the time was the **XA35
XML Accelerator**, the **XS40 XML Security Gateway** and the **XI50 Integration
appliance**. The v1 of this document said the hardware story was "mostly packaging +
optimised software + crypto offload". The record is more nuanced:

- DataPower's core "XG3" technology was **compiler-centric**: XSLT and related processing
  were compiled to fast native code; support for new web-services specs was loaded into
  flash rather than hard-wired, precisely so it could be updated.
- DataPower **did** build dedicated XML silicon: the **XG4** chip family (announced 2004,
  "fourth generation", ~1 Gbit/s XML processing), offered to OEMs and planned for its own
  appliances. Competitors did the same (Tarari XML chips in Cisco's AON modules, 2005;
  Forum Systems' Vantage).
- The appliances also used crypto/SSL acceleration and optional HSMs.

**What happened next is the actual lesson:** the dedicated XML-silicon market faded
(Cisco AON and the XML-chip start-ups are gone), while DataPower survived as a
**software gateway**. Today IBM DataPower Gateway (10.6.x stream, 10.6.6 current at time
of writing) ships as physical appliance, **Docker container, Linux application, VMware
OVA and — since 10.6.5 (Oct 2025) — KVM** virtual machine.

**Lesson for the guard:**
1. The win was a **compiler** (declarative transform → fast code) plus **offloaded
   primitives** (crypto), not parsing in gates. UTL-X's GraalVM-native compilation is the
   same idea.
2. Dedicated general-purpose XML hardware was commercially overtaken by CPUs within a
   decade. Modern evidence points the same way: SIMD parsers such as simdjson parse JSON
   at gigabytes per second on a single core.
3. The specs that *change* stayed in updatable software. Same rule here (§6).

---

## 2. The guard is a pipeline — feasibility is per-stage

| Stage | FPGA fit | Why / v2 notes |
|---|---|---|
| Packet ingest / framing (UDP/TCP) | **Excellent** | line-rate streaming — SmartNIC/DPU territory |
| **Protocol break** (terminate TCP/IP, forward only payload over a simple link) | **Excellent** | canonical hardsec function; commercial verifiers do this in hardware logic over raw Ethernet |
| **BINF fixed bit-level decode** + CRC/parity | **Excellent** | static bit-fields → parallel extraction; CRC is a classic hardware primitive |
| **Verification of a simple, strongly-typed format** | **Excellent** | the hardsec sweet spot (§4a) — bounded grammar, byte-stream state machine |
| Regex / keyword / dirty-word matching | **Good (FPGA) / see note** | canonical FPGA win. **Note:** NVIDIA discontinued the DOCA RegEx/DPI libraries (from DOCA 2.5; removed by 2.10), so do **not** plan on BlueField hardware regex. CPU alternative: **Vectorscan** (BSD fork of Hyperscan with Arm NEON support; Hyperscan itself went proprietary after 5.4) |
| Structural caps (depth / size / count) | **Good** | counters & limits in a streaming pipeline |
| Crypto (TLS, signing, label-binding signatures) | **Excellent** | standard offload — exactly what DataPower did |
| Self-describing **text parse (JSON/XML)** | **Poor** | variable-length, recursive, unbounded nesting, Unicode — fights fixed pipelines (research prototypes exist; not a production path for a guard) |
| **Build the UDM tree** | **Poor** | dynamic, pointer-chasing, variable-size allocation — FPGAs hate dynamic structures |
| General **`validate.*`** evaluation | **Poor–mixed** | arbitrary expressions → CPU; only simple range/enum checks map to comparators |
| Canonical text re-serialize | **Mixed** | fixed-binary re-encode OK; string/key-sort serialization is software-ish |

---

## 3. The core mismatch

UTL-X's model is **"normalise any format → a dynamic UDM *tree* → run general
rules/transforms."** FPGAs are the opposite paradigm: **fixed, streaming, bounded, no
dynamic data structures, no general computation.** A faithful hardware UTL-X (arbitrary
format → full UDM → arbitrary `validate.*`) is therefore a **bad FPGA fit** — the very
generality that makes UTL-X valuable is what resists hardware.

So: **full guard in FPGA = not feasible, not wise.**

But note the inversion (§4a): hardware does not need to *understand* the complex format
if software first turns it into something hardware *can* fully check.

---

## 4. Where hardware wins (1) — hybrid fast-path / slow-path (throughput)

The standard architecture of high-end firewalls / IDS / guards, and a clean fit:

```
wire
 → [ FPGA / SmartNIC — FAST PATH, line rate ]
 |    framing · protocol break · BINF fixed-binary decode + CRC · structural caps ·
 |    keyword screening · crypto offload · fail-closed edge-drop of obvious-bad
 → [ CPU — SLOW PATH, the UTL-X engine ]
      full UDM · validate.* · Message-Contract allow-list · labels · canonical re-serialize
 → egress (PASS) / dead-letter (FAIL)
```

Hardware does the **cheap, high-volume, streaming, stable** work and drops obvious-bad at
the edge (fail-closed); the CPU UTL-X engine does the **general, variable, complex** work
on whatever survives.

## 4a. Where hardware wins (2) — transform → verify (assurance) — NEW

This is how high-assurance cross-domain vendors actually use FPGAs today:

```
untrusted input
 → [ SOFTWARE TRANSFORM (complex, lower assurance) ]
 |    parse any format → policy → emit a SIMPLE, strongly-typed, bounded form
 → [ HARDWARE VERIFIER (simple, high assurance; FPGA logic, no OS, no CPU) ]
 |    protocol break · byte-stream verification of the simple form's syntax + semantic constraints
 → [ DELIVERY ] rebuild output ONLY from verified simple-form data
```

Evidence that this is the mainstream pattern:

- **Everfox (ex-Forcepoint) High Speed Verifier 2:** all data crossing is transformed by
  software into a simple, strongly typed format with semantic constraints designed to be
  verified in hardware logic; a hardware-logic protocol break uses a simple protocol over
  raw Ethernet so no TCP/IP crosses; received data is never delivered — delivered data is
  built from verified simple data; verification runs at line speed.
- **NCSC "Pattern: Safely Importing Data"** required hardware verification for data
  crossing trust boundaries; vendors such as Glasswall transform complex files (documents,
  images) into simple XML so that a hardware verifier can check them against an XSD
  (integrated with Becrypt's APP-XD). *Status:* NCSC announced in April 2026 that the
  import/export patterns are being deprecated in favour of new cross-domain patterns built
  on the pipeline model — the transform/verify principle remains, the specific document
  will change.
- **Garrison** (FPGA hardsec, acquired by Everfox in 2024) is built on the same idea of
  hardware-enforced isolation.

**Why this is the right home for UTL-X + FPGA:**

| Role | Who | Why it fits |
|---|---|---|
| Transformer | **UTL-X** | "any format → canonical → clean output" is literally what UTL-X does; declarative, auditable; runs on the ARM cores of a SoC-FPGA or a server |
| Simple-form definition | **BINF form-class / USDL schema** | a static, bounded description: exactly what a verifier needs |
| Verifier | **FPGA logic generated from that definition** | bounded state machine, no dynamic structures, line-rate |

**The catch you must design for — independence.** If one definition generates *both* the
transformer's encoder *and* the verifier, a mistake in the definition is replicated in
both and the verifier no longer catches it. Mitigations: keep the simple form *very*
small and reviewed by hand; generate the verifier with a separate, independently
assured toolchain (or hand-write it); formally check verifier vs. definition; let an
accredited partner own the verifier (deployment shape C in `UDM-content-guard.md` §3a).

---

## 4b. Why the network terminates in the FPGA — taking the OS off the boundary

*Figure: `utlx-fpga-vs-os-boundary.svg` / `.png`. Full explanation: `fpga-programming-primer.md` §1a.*

| | A — network through the processor | B — network through the FPGA (UTL-X Guard) |
|---|---|---|
| First code untrusted bytes touch | OS kernel: drivers, ARP, ICMP, TCP/IP — millions of lines | a small, fixed FPGA circuit |
| Processor addressable from the network? | yes — ports, ping, ARP, services | no — protocol break, payload only via DMA |
| Size / rate / type limits | after the OS has buffered the data | at line rate, before memory |
| If Linux is compromised | attacker controls the interlink | FPGA stays transmit-only; side-B verifier rejects |
| Behaviour to analyse | general-purpose OS | deterministic circuit, fixed function |

**Principle:** the thing that enforces the boundary cannot be reprogrammed by the thing being attacked.
The security requirement is "network terminates in the FPGA"; the ≥ 10.3 Gb/s transceiver requirement
only applies when that is done at 10G (1G can use ordinary FPGA I/O with an external PHY). The FPGA's
roles in priority order: independent verifier, protocol break, physical one-way enforcement, line-rate
decoding; crypto offload is optional — FPGA **decodes** binary formats, it is not primarily a decryptor.

---

## 5. The sweet spot — compile a BINF form-class → FPGA logic

A **BINF form-class is a *static* description of bit fields** (offsets, widths, scaling,
discriminators, CRC) — and static bit-field layouts are **directly synthesizable** to
hardware: parallel field extraction + CRC + range/enum checks. So a form-class could be
compiled into FPGA logic in two roles:

1. **Decoder (throughput, §4):** decode+validate a fixed military/sensor format (AIS,
   ADS-B, ASTERIX subsets, Link 16 J-words) **at line rate**.
2. **Verifier (assurance, §4a):** verify that what UTL-X emitted conforms *exactly* to the
   approved simple form before it may leave.

This is the standout hardware target because:

- It fits the FPGA model (fixed layout, streaming, no dynamic tree).
- It keeps the **source declarative** — the form-class, not hand-written HDL — so the
  "standards-change-is-a-data-change" property survives (re-synthesize from the
  form-class, don't rewrite Verilog).
- It is **novel and differentiated** — hardware-speed tactical-binary decode/verify driven
  by a declarative definition.
- Practical path: generate HLS C++ (Vitis HLS) or a P4 parser from the form-class first
  (P4 parsers map naturally to header/field extraction), RTL only if needed.

You would **not** attempt this for JSON/XML (variable, recursive); you **would** for
fixed binary — and for any *simple form* UTL-X maps complex input into.

---

## 6. The catch — FPGA fights UTL-X's own value proposition

UTL-X's selling point is **"a standards revision is a definition change, not a code
rewrite."** FPGA logic is the opposite — specialised (HDL/HLS), slow to build, and
**re-synthesised on every change** (and, for an accredited verifier, re-evaluated).
Put *variable* logic in gates and you lose the agility that justifies the language.

**Rule:** burn only **stable, high-volume primitives** into hardware (framing, protocol
break, crypto, a frozen form-class, a frozen simple form, a fixed keyword set). Keep
everything that **changes with the standard or the policy** in software. The transform →
verify split helps here: policy churn happens in UTL-X; the verifier only changes when
the *simple form* changes, which should be rare by design.

---

## 7. Assurance — the place hardware is a positive, not just speed

For a cross-domain guard, hardware can be an **assurance asset**:

- A **data diode** is literally hardware-enforced one-way flow — uncircumventable by
  software. For NATO↔civilian crossings this is a recognised high-assurance pattern.
  Commercial diodes reach **Common Criteria EAL7+** (Fox DataDiode, NSCIB; Arbit, BSI).
- A **hardware verifier** gives a high-assurance content claim that does not depend on
  the (complex) transformer being bug-free (§4a).
- Hardware can enforce **fail-closed** and **deterministic timing** with no
  general-purpose OS attack surface.
- **But** FPGA adds its own assurance burden: **bitstream trust (encryption +
  authentication), IP-core provenance, toolchain trust, supply-chain** — non-trivial for
  an accredited guard. Note also that some AMD dev kits ship in "encryption disabled"
  (export-friendly) variants: for bitstream-protection work buy the standard SKU.

So hardware's strongest draw here is **assurance / enforcement** (diode- and
verifier-like guarantees) as much as throughput.

---

## 8. Reach for FPGA last (for throughput) — the cheaper ladder

1. **Measure first.** GraalVM native (already targeted for latency) may hit throughput
   goals with no custom silicon. Use SIMD-class libraries where the guard allows them
   (simdjson-class JSON parsing; Vectorscan for multi-pattern keyword matching —
   hyperscan-java has switched to Vectorscan and supports Linux/macOS arm64).
2. **eBPF / XDP** in-kernel fast path — software, near-hardware speed for the pre-filter
   and protocol break.
3. **SmartNIC / DPU (BlueField-3; BlueField-4 entering early availability in 2026 with
   the Vera Rubin platform)** — line-rate steering, crypto (IPsec/TLS), isolation. P4 is
   supported via NVIDIA's **DOCA Pipeline Language**, which implements a *subset* of
   P4-16 tailored to the ASIC pipeline. **No hardware regex any more** (§2).
4. **FPGA / ASIC** only for proven-stable, proven-hot primitives (and the BINF
   form-class case) — and, independent of throughput, for **assurance** (protocol break,
   hardware verifier).

---

## 9. What hardware is a "military fit"?

"Military fit" is not one box — it is **deployment tier + ruggedization envelope +
assurance hardware + a procurable open-standard form factor**. The combination, not any
single spec, is what makes hardware adoptable.

### 9.1 Tier decides almost everything

| Tier | Environment | What dominates | Hardware shape |
|---|---|---|---|
| **Strategic / data-centre** (HQ, accredited server rooms) | benign | assurance + crypto | rugged rack servers, **CDS/guard appliances**, DPUs, diodes |
| **Deployable / tactical ops centre** (shelters, vehicles) | semi-harsh | MIL-power, EMI, size | rugged **VPX/SOSA** chassis, small-form-factor rugged servers |
| **Platform-embedded** (aircraft, ship, vehicle) | harsh | SWaP + full MIL-STD | **conduction-cooled VPX/SOSA** cards; **VNX+ (VITA 90)** or SOM-based fanless boxes where space is tight (§9.8) |
| **Dismounted / edge** (soldier, UAS) | extreme SWaP-C | low power, tiny | ARM / **SoC-FPGA** modules |

### 9.2 Form factor — OpenVPX + SOSA (procurement-critical)

For embedded/tactical compute the dominant open standard is **OpenVPX (VITA 46/65)** and
especially **SOSA** (Sensor Open Systems Architecture — tri-service US DoD MOSA standard
under The Open Group; US Army sibling **CMOSS**). Modern US defence procurement
increasingly expects MOSA/SOSA alignment: **a SOSA-aligned card is procurable; a bespoke
box is not.** Status: Technical Standard Edition 1.0 is published; Edition 2.0 has
progressed through public snapshots — *verify the current edition*. Important wording:
The Open Group's directory lists products **"aligned"** to SOSA and states that these are
**not** to be read as conformant or certified; conformance certification is a separate
programme. Vendors: Curtiss-Wright, Mercury Systems, Kontron, Abaco/AMETEK, Annapolis,
Elma, Aitech. European note: SOSA is US-led; European platforms often specify OpenVPX
without SOSA — ask the customer.

**Small form factor — VNX+ (VITA 90).** Where 3U VPX does not fit, VNX+ offers nearly all
3U OpenVPX features at roughly 30 % of the slot size, conduction-cooled, up to 80 W per
module (its predecessor VNX / VITA 74 handled 20 W). VNX+ content was expanded in SOSA
Edition 2 (Snapshot 2, 2024). First application-ready kits are appearing (Elma VersaPLUS,
Sep 2026); Zynq UltraScale+ VNX+ modules exist (Enclustra Andromeda XZU70/XZU80-based).

### 9.3 Silicon that fits the guard's hybrid model

- **SoC-FPGA — AMD Zynq UltraScale+ MPSoC or Versal** — ARM cores **and** FPGA fabric on
  one chip: fabric runs the **BINF fast-path / verifier + framing + protocol break + CRC**,
  ARM cores run the **UTL-X slow-path / transformer** (UDM + `validate.*` + canonical
  re-serialize). One rugged module = the whole hybrid architecture. **The natural fit.**
  - *Versal Premium* is what the leading 3U VPX cards use (e.g. Curtiss-Wright VPX3-536,
    Annapolis WILDSTAR 3XV-series); *Versal Prime* appears on SoM-based carriers (Abaco
    VP241, Dec 2025).
  - *Versal AI Edge / Prime Gen 2* (eval kit VEK385 shipping 2026) brings Cortex-A78 cores
    — much more headroom for running the GraalVM-native UTL-X binary on-chip — and is
    marketed for high-security, long-lifecycle designs.
  - AI Engines are **irrelevant** to the guard (and `ai.*` is excluded from the guard
    path anyway); don't pay for them unless the same card also does sensor processing.
- **Microchip PolarFire SoC** (RISC-V + FPGA) — credible alternative with a strong
  security/supply-chain story and rad-tolerant siblings; smaller CPU complex, so UTL-X
  would run on an adjacent processor. *Verify fit.*
- **NVIDIA BlueField DPU** — data-centre tier, line-rate offload, DPL/P4-programmable.
- **NVIDIA Jetson (Orin; Thor)** — tactical edge (GPU only for 2.0/`ai.*` *outside* the
  guard path).
- **Rad-hard** (Versal XQR, Microchip RTG4 / RT PolarFire) — only if space is in scope.

### 9.4 Ruggedization envelope (the MIL-STD alphabet)

- **MIL-STD-810** environmental (shock/vibration/temp/humidity/altitude)
- **MIL-STD-461** EMI/EMC
- **MIL-STD-704** (aircraft power) / **MIL-STD-1275** (28 V vehicle power)
- **MIL-STD-1553 / ARINC-429** — the avionics/tactical **data buses** military I/O must
  speak (tactical data often arrives on 1553, not Ethernet)
- **VITA 48.x** cooling (48.2 conduction, 48.4 liquid flow-through), **VITA 66/67**
  optical/RF backplane I/O
- Conduction cooling (fanless) + IP-rated sealed enclosures
- European equivalents/expectations: **DEF STAN 00-35** (UK environmental), **AECTP**
  (NATO environmental test publications) — ask which the customer cites.

### 9.5 Assurance hardware — matters *more than ruggedness* for a guard

- **Data diode** — hardware-enforced one-way flow; the canonical cross-domain primitive.
  - **Fox DataDiode** — Fox Crypto B.V. (Delft; **sold by NCC Group to CR Group Nordic AB,
    Mar 2025**, so Swedish-owned but still Dutch-based). CC **EAL7+** (NSCIB certificate
    valid to Sep 2028); NBV/AIVD approved for **Zeer Geheim** (2019).
  - **Arbit** (DK) — CC **EAL7+** (BSI, recertified); accredited for NATO SECRET by the
    Danish authority, higher per vendor datasheet; supports receive-side filtering incl.
    third-party filters.
  - **Infodas / Airbus** (DE) — diodes and SDoT gateways, approved up to GEHEIM / NATO
    SECRET / EU SECRET.
  - Also **Owl** (US), **Advenica** (SE), **Nexor** (UK), **Waterfall** (IL), **ST
    Engineering** (SG), **BAE XTS** (UK/US), **Everfox/Garrison** (US/UK, FPGA hardsec).
- **Hardware verifiers / protocol breaks** — Everfox High Speed Verifier, Garrison
  hardsec; the target for §4a.
- **Accredited crypto** — **NSA Type 1** (US classified), **NATO-approved** crypto (e.g.
  SINA-family IP encryptors in the European market), or **HSM / FIPS 140-3**
  (commercial/coalition-releasable).
- **TEMPEST / NATO SDIP-27** — emission security (anti-emanation shielding).
- **Anti-tamper + zeroization**, **trusted/measured boot / TPM / secure enclave**,
  **trusted supply chain / foundry** (a specific FPGA accreditation gate), **bitstream
  encryption/authentication**.

### 9.6 Recommended fit per deployment

| Deployment | Recommended hardware |
|---|---|
| Lab / test-sim / low-assurance CDR | rugged **COTS x86 or ARM** server + optional **BlueField DPU**; GraalVM-native UTL-X — *measure before any FPGA* |
| Data-centre accredited guard | rugged server + **data diode** (one-way) + **hardware verifier / protocol break** (partner) + **HSM/Type-1 crypto**; DPU for line-rate steering |
| Deployable / platform-embedded | **SOSA-aligned VPX card, Versal/Zynq SoC-FPGA** — FPGA fast-path/verifier + ARM UTL-X transformer + crypto; MIL-STD-810/461/704; diode where one-way required |
| Shipboard (minimal space/power) | 2× **Kria K26/K24 SOM** on own carrier in a fanless conduction-cooled box, or **VNX+** modules; split guard with fiber interlink (§9.8) |
| Tactical edge / UAS | **SoC-FPGA or Jetson-class** module, SWaP-C optimised |

**Standout:** a **SOSA VPX card on a Versal/Zynq SoC-FPGA** physically *is* the
transformer + verifier pair on one procurable, ruggedized module — and the BINF
form-class → logic idea (§5) targets exactly its FPGA fabric. *But* for accreditation,
transformer and verifier on the same chip need a convincing separation argument
(separate power/clock domains, isolation design flow); many accreditors will prefer
physically separate devices.

### 9.8 Prototype housing and the shipboard (minimal-SWaP) guard

**Split guard (two owned halves).** Two SoC-FPGA nodes back to back — one per network,
each with its own UTL-X policy, joined by a fiber interlink carrying a simple framed format
(no TCP/IP) — implement deployment shape D (`UDM-content-guard.md` §3b). Prototype: 2×
ZCU106 (2× SFP+ 10G each: one to its network, one interlink; RJ45 for owner management).

**Housing (prototype).** COTS 19″ housings exist at every level — 2U micro-ATX cases (the
ZCU106 has a micro-ATX footprint), shock-isolated transit racks (SKB / Pelican-Hardigg),
MIL-rugged chassis (Elma 12R2 baseline-tested to 810F/167/901D/461D; Pixus), and
TEMPEST-shielded cabinets (Holland Shielding NL, Siltec PL in SDIP-27 A/B/C, Spectrum
Control / Emcon). One enclosure per owner; fiber through waveguides; filtered power.
TEMPEST/MIL compliance is certified per **complete configuration**; an eval board
(0–45 °C, socketed memory, fan) survives transport but is not MIL-qualifiable. Details and
BOM: `hardware-cots-shopping-list.md` §8.

**Shipboard guard — minimise space and power.**

| Route | Platform | Trade-off |
|---|---|---|
| A | 2× Kria K26/K24 industrial SOM on own carrier, fanless conduction-cooled box | smallest, cheapest per unit; you own carrier design + qualification |
| B | VNX+ (VITA 90) modules/chassis (Enclustra, Elma, Atrenne; Versal Gen 2 SFF via VITA 93) | procurable SOSA-aligned standard; higher cost |
| C | Benchmark/partner: Infodas/Airbus SDoT COMP-LAND (compact tactical gateway + software diode, up to NATO SECRET) | buy rather than build |

Design rules: no fans, no disks, read-only signed boot; fiber for networks, interlink and
management; UTL-X on the ARM cores (A53 ok for message rates; Versal Gen 2 A78 if not);
FPGA limited to protocol break + verifier. Naval qualification targets (confirm per
customer): MIL-STD-901E shock, MIL-STD-167-1 vibration, MIL-STD-810 incl. salt fog / IEC
60945, MIL-STD-461 EMC, MIL-STD-1399 or navy power standard, DNV/class approval for
non-military vessels. Prototype on a KR260 (same K26 SOM).

### 9.7 The gates that actually decide adoption

Procurement/accreditation, not raw specs: **SOSA/MOSA conformance** (procurability, US),
**Common Criteria / NIAP / national + NATO accreditation** (the guard), **NCDSMO Raise the
Bar + Baseline list** (US national-security market), **NCSC Principles Based Assurance**
(UK products), **NBV evaluation** (NL), **TEMPEST**, **anti-tamper**, **trusted supply
chain**. Sovereign/NL angle: European rugged-COTS + Dutch/Nordic assurance (Fox Crypto,
Arbit) + Dutch-developed declarative content engine.

---

## 10. Bottom line

- **Full UTL-X guard in FPGA: no.** Dynamic UDM + general `validate.*` + text parsing
  fight the hardware model; the XML-appliance era (DataPower XG4, Tarari, Cisco AON)
  shows dedicated XML silicon losing to compiled software.
- **Hybrid offload: yes, and standard** (throughput).
- **Transform → verify: yes, and the real opportunity** (assurance). UTL-X is the
  transformer; a small hardware verifier checks a simple, strongly typed form; delivery is
  rebuilt from verified data. This turns UTL-X from "something hardware can't run" into
  "the thing hardsec pipelines need in front of their verifier".
- **Standout target: BINF form-class / simple-form → FPGA logic**, as decoder and as
  verifier — with an explicit independence argument.
- **Measure before building silicon;** prefer eBPF/XDP and DPU before FPGA *for speed*;
  use FPGA *for assurance* via partners who already own accredited verifiers.
- **Military-fit target:** a **SOSA-aligned OpenVPX card on a Versal/Zynq SoC-FPGA** +
  **assurance hardware** (diode, verifier, accredited crypto, anti-tamper, trusted boot),
  ruggedized to the platform's MIL-STD envelope.

## 11. Open questions

- **Military-fit target & tier:** which deployment tier(s) are in scope?
- **SOSA alignment:** is MOSA/SOSA conformance a requirement for the intended (European)
  customers, or is OpenVPX enough?
- **Diode in scope?** Is hardware-enforced one-way flow required for the target crossings?
- **Verifier ownership:** build a verifier (and carry its accreditation) or partner
  (Everfox/Garrison, Becrypt-style integrators, a NL/EU integrator)?
- **Independence argument:** how is the verifier kept independent of the form-class that
  drives the transformer?
- **Crypto tier:** Type 1 / NATO-approved / FIPS 140-3 — which, for which releasability?
- Actual throughput/latency targets — do they even require hardware, or does GraalVM
  native suffice?
- Form-class → HLS/P4/RTL toolchain: feasibility, synthesis latency, which form-class
  features are synthesizable (fixed yes; variable FSPEC/continuation harder).
- Assurance of the bitstream/IP supply chain under the relevant accreditation regime.
- Partition point: exactly which checks run fast-path vs slow-path vs verifier, and how
  the fail-closed hand-off is proven.
- Impact of the forthcoming **NCSC cross-domain patterns** (replacing import/export
  patterns) on the transform/verify split.

---

## Changelog v1 → v2

- §1 DataPower corrected: XG3 compiler-centric core; XG4 XML chips did exist; current
  10.6.x software/container/KVM editions.
- §2 regex row: BlueField DOCA RegEx discontinued → FPGA or Vectorscan; protocol break and
  simple-form verification rows added.
- New §4a transform → verify (hardsec) pattern, with independence caveat.
- §5 form-class as decoder *and* verifier; HLS/P4 generation path.
- §8 DPU status (DPL = P4 subset; BlueField-4 early availability 2026).
- §9 updated silicon (Versal Premium in VPX, Versal Gen 2, PolarFire SoC), SOSA
  aligned-vs-conformant, diode vendor facts (Fox Crypto ownership, Arbit EAL7+).

## Changelog v2.1 → v2.2

- New §4b: network termination in the FPGA (A vs B), roles in priority order, decode ≠ decrypt.

## Changelog v2 → v2.1

- §9.1/§9.6: shipboard row; §9.2: VNX+ (VITA 90) small form factor.
- New §9.8: split guard, prototype housing/TEMPEST, shipboard minimal-SWaP routes.

## Sources (checked 2026-10)

- IBM acquires DataPower (Oct 2005): eWeek, Network World, The Register; DataPower XG4 announcement (Network Computing, May 2004); DataPower press releases on public.dhe.ibm.com
- IBM DataPower 10.6.5 KVM option (IBM Community, Nov 2025); DataPower firmware lifecycle (ibm.com/support)
- NVIDIA DOCA 2.5 release notes (RegEx/DPI discontinued); DOCA 2.10 "P4 Language Support in DPL"; BlueField-4 early availability 2026
- Everfox/Forcepoint High Speed Verifier 2 datasheet (forcepoint.com)
- Glasswall hardsec blog; Becrypt APP-XD / Glasswall partnership
- NCSC blog 21 Apr 2026 (patterns deprecated; pipeline model)
- Vectorscan (github.com/VectorCamp/vectorscan); hyperscan-java v5.4.11-3.0.0 release notes
- Curtiss-Wright VPX3-536 press release (Feb 2025); Abaco VP241 (Dec 2025); Annapolis SOSA product list
- The Open Group SOSA aligned-products directory; SOSA Edition 2.0 snapshot announcements
- AMD VEK385 product page; Fox Crypto NSCIB certificate (sec-certs.org); NCC Group FY25 results; Arbit EAL7+ recertification
- VNX+: Elma, Atrenne, Electronic Design, Enclustra; Infodas SDoT COMP-LAND; enclosure vendors as listed in `hardware-cots-shopping-list.md` sources
