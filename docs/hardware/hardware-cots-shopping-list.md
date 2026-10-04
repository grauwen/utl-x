# COTS Hardware — Buy-and-Climb Shopping List for the UTL-X Guard

*Working document, Glomidco B.V. · 2026-10 · v2 · Status: exploratory reference.*

| Field | Value |
|---|---|
| Document | hardware-cots-shopping-list |
| Version | v2.2 (2026-10) — adds NL euro prices + ordering notes (§1b) and optics/10G test set (§1c); v2.1: adds network-port column, ZCU106 two-port/split-guard rig, enclosures (19″ / shock / MIL / TEMPEST) and shipboard minimal-SWaP section |
| Status | Exploratory — companion to `hardware-acceleration.md` |
| Scope | What you can actually **buy** to build / prototype the UTL-X guard (gateway), from cheap eval silicon to rugged SOSA cards and assurance hardware |
| Related | `hardware-acceleration.md` (feasibility, transform→verify, military fit), `UDM-content-guard.md` (deployment shapes A/B/C), `BINF-bit-level-binary-format.md` |

> **Caveats — read first.**
> 1. Prices below are **list prices seen in AMD/distributor listings in 2026** (USD unless
>    noted, ex VAT); distributor prices vary and some kits have 8–23 week lead times. This
>    is **not** a quoted bill of materials. **Verify current part numbers, SOSA status,
>    availability, and export terms with the vendor.**
> 2. In defence, **"COTS" = catalogue product, not bespoke — not a web checkout.** Rugged
>    SOSA cards, diodes and assurance hardware are catalogue items but **procurement-gated**
>    (vendor vetting, ITAR/EAR end-user checks, NDAs, long lead times).
> 3. **Type 1 / NATO-approved crypto is NOT COTS** (government-controlled / cleared-vendor
>    only). Commercial FIPS 140-3 HSMs are buyable.
> 4. Even eval boards carry export classifications (e.g. Kria starter kits list ECCN
>    5A992C; "-ED" *encryption-disabled* variants are EAR99). Buy the **standard** SKU if
>    you want to prototype bitstream encryption/authentication.

---

## 1. Start today — prototype silicon (buy online)

Same chip families as the rugged cards, so FPGA + ARM work ports across (one AMD
Vivado/Vitis toolchain). **The ARM cores run Linux, so the GraalVM-native UTL-X binary
(linux-aarch64) runs directly on the ARM "slow-path" / transformer — no new port**; you
develop the BINF **fast-path / verifier** in the FPGA fabric and run the existing engine on
the ARM cores, on one chip.

| Board | Silicon | List price | Fast network ports (>1 GbE) | Guard relevance |
|---|---|---|---|---|
| **AMD Kria KV260** (starter kit) | Zynq UltraScale+ (K26 SOM) | ~$199–250 | none | cheapest start; fabric experiments only |
| **AMD Kria KR260** (starter kit) | Zynq UltraScale+ (K26 SOM) | **$349** | **1× SFP+ (10G)** + 4× RJ45 1 GbE | cheapest rig; **only one 10G port**; same K26 SOM as the shipboard route (§9) |
| **ZCU104** eval | Zynq UltraScale+ MPSoC | **~$1,554** | none (1 GbE only) | more fabric; not a network rig |
| **ZCU106** eval | Zynq UltraScale+ MPSoC (EV, ZU7EV) | **$3,234** (≈ €3,135 ex VAT at DigiKey) | **2× SFP+ (10G)** + 1× RJ45 1 GbE | **cheapest two-port board → one 10G port per zone + separate management**; basis of the split-guard rig (§1a) |
| **VEK280** eval | **Versal AI Edge** | **~$6,995** | 1× SFP28 | cheapest Versal entry; one fast port |
| **VMK180** eval | **Versal Prime** | **~$9,345** | SFP28 + QSFP28 + RJ45 | Versal *without* AI Engines; two-port Versal upgrade path |
| **VCK190** eval | **Versal AI Core** | **$13,195** (v1 doc said $3–8k — wrong) | SFP28 + QSFP28 + RJ45 | AI Engines irrelevant to the guard |
| **VEK385** eval | **Versal AI Edge / Prime Gen 2** (Cortex-A78) | **$15,995**, ~16-week lead | QSFP28 + SFP28 (25–100 GbE) | forward-looking; strong ARM complex for UTL-X on-chip |

*Port notes:* exact SFP28/QSFP28 cage counts on VMK180/VCK190/VEK385 — check the board
user guides. A QSFP28 port can be split into 4× 25G or 4× 10G with a breakout cable. An
FMC card such as the Opsero Quad SFP28 FMC adds four SFP/SFP+/SFP28 ports to boards with
an FMC slot (reference designs exist for VCK190, VMK180, VEK280, VPK120; also used with
ZCU106).

*Prices in this table are 2026 USD list prices; current euro prices in the Netherlands are
higher — see §1b.*

**Recommendation for the guard:** **KR260** (cheapest; prove UTL-X on ARM + fabric) →
**ZCU106** (two 10G ports: one per zone; split-guard rig with two boards) → **VMK180 or
VEK385** when you need Versal. Skip the VCK190 unless AI-Engine work is in scope. For a
shipboard / minimal-SWaP product, see §9 (Kria SOM or VNX+).

### 1a. ZCU106 specifics — the two-port and split-guard rig

- **Ports:** 2× SFP+ cages (10GBASE-R capable) on FPGA (PL) GTH transceivers + 1× RJ45
  1 GbE on the processor (PS) side. Use the RJ45 for management only.
- **10G MAC lives in the FPGA.** The PS Ethernet controllers are 1 GbE only. Options: AMD
  10G/25G Ethernet Subsystem (licensed; evaluation licence available) or open-source
  cores (verilog-ethernet / "taxi", which has ZCU106 10GBASE-R reference designs that build
  in free Vivado Standard).
- **Linux path:** FPGA → ARM via AXI DMA/MCDMA + driver. For a guard this is a feature:
  every packet passes FPGA logic (protocol break, fast-path checks) before UTL-X.
- **Mechanics:** 24.13 × 18.60 cm, **micro-ATX chassis footprint** with PCIe Gen3 x4
  endpoint edge (taller than a standard PCIe card). Fits any micro-ATX-capable case.
- **Environment:** operating 0 °C to +45 °C, storage −25 °C to +60 °C → lab/office only.
- **Not included:** SFP+ optics or DAC cables — see §1c for the test set.
- **Split guard:** two ZCU106 back to back = one 10G port per network, one 10G fiber
  interlink, one private management port per owner (see `UDM-content-guard.md` §3b and
  `ZCU106-dual-10GbE-guard-prototype-abstract.md`).

**Alternative silicon (optional):** **Microchip PolarFire SoC Icicle Kit** (RISC-V + FPGA)
— a few hundred dollars (*verify*), for comparing a non-AMD supply chain and its
security features.

**Software you need regardless (free/open):** GraalVM (native-image, aarch64), Vitis HLS,
an eBPF/XDP toolchain, **Vectorscan** (open fork of Hyperscan; Arm support; usable from the
JVM via hyperscan-java), Jazzer (JVM fuzzing), a P4 compiler (p4c) for form-class →
parser experiments.

---

### 1b. Buying in the Netherlands — current EUR prices (checked 2026-10)

Prices seen on DigiKey (digikey.nl; ZCU104/ZCU106 from digikey.be, same 21 % VAT) for one unit.
Euro prices are now **higher** than the USD list prices in the table above. Indicative — re-check
on the day of ordering.

| Item | Part number | Ex VAT | Incl. 21 % VAT | Notes |
|---|---|---|---|---|
| Kria KV260 starter kit | SK-KV260-G | **€243.01** | ≈ €294 | board only — no power supply; add AMD "KV260 Basic Accessory Pack" (12 V adapter, cables) + microSD |
| Kria KR260 starter kit | SK-KR260-G | **€369 – 372** | ≈ €447 – 450 | includes power supply, cables, microSD; buy the standard SKU (the "-ED" encryption-disabled variant is obsolete) |
| ZCU104 eval kit | EK-U1-ZCU104-G | ≈ €1,614 | ≈ €1,953 | same XCZU7EV chip as ZCU106, but no SFP+ |
| **ZCU106 eval kit** | **EK-U1-ZCU106-G** | **≈ €3,110** | **≈ €3,763** | 2 × SFP+; kit includes a Vivado Design Edition seat (per Farnell listing) |
| Kria K26 SOM, industrial | SM-K26-XCL2GI | **€496.97** | €601.33 | production module (−40…100 °C Tj, 77 × 60 mm, 4 GB RAM, 16 GB eMMC); needs a carrier board; 26-week lead time when stock runs out |
| Kria K26 SOM, commercial | SM-K26-XCL2GC | €358.92 | ≈ €434 | production module, commercial temperature range |
| Kria K24 SOM, commercial | SM-K24-XCL2GC | €267.53 | ≈ €324 | smaller fabric |
| K26 heatsink | e.g. ATS-KRA-3567-C1-R0 | ≈ €27 – 34 | — | needed for a bare SOM |

**Ordering notes (DigiKey NL):**
- Delivery typically ~3 working days; free shipping above €75 (€25 below) — check current threshold.
- Choose **UPS (or FedEx) = DDP**: duty and customs paid by DigiKey, no surprises at the door.
  DHL is CPT: duty, customs and VAT are due on delivery.
- Business account with invoice payment available; **VAT is reclaimable** for Glomidco B.V., so the
  ex-VAT price is the real cost.
- Development kits are **non-cancelable / non-returnable** — double-check part numbers.
- Export classification (e.g. ECCN 5A992C on Kria kits) may trigger an end-use question on the first
  order; normally a formality for a Dutch B.V. and civilian development.
- Alternatives: Farnell (nl.farnell.com), Mouser (nl.mouser.com), RS Components (nl.rs-online.com);
  AMD distributor Avnet/EBV for project pricing. **Avoid marketplaces** (eBay etc.): import VAT, no
  warranty, counterfeit risk — unacceptable for a security product.

### 1c. Optics, cables and the 10G test set

Evaluation boards ship **without SFP+ optics or cables**. Add them to every 10G setup.

**Minimal split-guard test set (2 × ZCU106):**

| Item | Purpose | Qty | Indicative price (ex VAT) |
|---|---|---|---|
| 10GBASE-SR SFP+ optic (850 nm, multimode, generic MSA-compliant) | interlink A → B over fibre | 2 | €20 – 35 each |
| OM3/OM4 LC-LC duplex patch cable, 1 – 2 m | interlink fibre; for the one-way test connect one strand only | 1 | €5 – 15 |
| SFP+ DAC cable (direct-attach copper), 1 – 3 m | network side: each board to the test PC | 2 | €15 – 30 each |
| Dual-port 10G SFP+ NIC for a test PC (e.g. Intel X520-DA2 / Mellanox ConnectX-4 Lx used, Intel X710-DA2 new) | one PC plays "network A" and "network B" | 1 | used €40 – 80; new €250 – 350 |

**Extra cost: ≈ €130 – 200** (used NIC) or **≈ €350 – 450** (new NIC), ex VAT.

**Cheaper variants:**
- **DAC cable for the interlink too** — no optics needed; fine to bring up the 10G MAC, but copper is
  always two-way, so it cannot demonstrate a *physical* one-way link. Start with DAC, switch to
  optics + fibre for the demonstration.
- **Network side on the 1 GbE RJ45 port** for a first test — then only 2 optics + 1 fibre are needed
  (≈ €50 – 80) and no 10G NIC.
- **Small 10G switch** (e.g. MikroTik with 4 × SFP+, ≈ €140 – 170) instead of a NIC when several
  machines must connect.
- **Single KR260:** one SFP+ port → one DAC or one optic plus a 10G counterpart.

**Watch out for:**
1. **Generic MSA-compliant optics** (FS.com, 10Gtek, …) are fine — the 10G MAC is your own FPGA logic,
   so there is no vendor-coding check; vendor-coded optics cost many times more.
2. **Match optic and fibre:** SR ↔ multimode (OM3/OM4, aqua/violet); LR ↔ single-mode (yellow).
3. **Avoid 10GBASE-T (RJ45) SFP+ modules** — ≈ 2.5 – 3 W, may exceed what an eval-board SFP+ cage is
   designed for.

**Where to buy (NL):** FS.com (EU warehouse in Germany) for optics, DACs and fibre; Alternate.nl,
Azerty.nl or Amazon.nl for NICs and MikroTik switches; DigiKey/Mouser also stock optics, usually at a
higher price.

**Complete minimal split-guard PoC hardware:** 2 × ZCU106 (≈ €6,220) + test set (≈ €130 – 450)
= **≈ €6,350 – 6,670 ex VAT (≈ €7,700 – 8,100 incl. VAT)**. Optional: two 2U cases (≈ €300 – 800, §8).

---

## 2. Data-centre tier — buy now

| Item | Products | Note |
|---|---|---|
| **DPU / SmartNIC** (fast-path, DOCA / DPL-P4) | **NVIDIA BlueField-3**; BlueField-4 entering early availability in 2026 (Vera Rubin platform, likely hyperscaler-first) | PCIe cards via OEM/distributors. **No hardware regex** — NVIDIA discontinued DOCA RegEx/DPI; plan keyword screening on CPU (Vectorscan) or FPGA |
| **ARM server** (to run the same aarch64 UTL-X binary as on the SoC) | Ampere-based servers (various OEMs) | optional; keeps one binary from lab to edge |
| **Rugged / transit-case servers** | Crystal Group, Systel, Klas, Curtiss-Wright DuraCOR, Mercury rugged servers | deployable tier |

---

## 3. Rugged SOSA-aligned VPX FPGA cards (catalogue, procurement-gated)

The operational target — real catalogue products; engage the vendor (defence/export
vetting). **"SOSA-aligned" is a vendor claim**; The Open Group's directory explicitly says
aligned ≠ conformant/certified. Ask for conformance status if the customer requires it.

| Vendor | Example products (verify current SKUs) |
|---|---|
| **Curtiss-Wright** | **VPX3-536** (3U, **Versal Premium VP2502**, announced Feb 2025, Fabric100 ecosystem); CHAMP-XD3 / VPX3-1262 Intel processor cards and VPX3-6816 switch for the slow-path side |
| **Annapolis Micro Systems** | **WILDSTAR 3XV-series** (3U, Versal Premium VP1502/VP1702, 100/200 GbE, VITA 48.2) — e.g. 3XV1, 3XVD (low latency), 3XVF/3XVC (optics) |
| **Abaco / AMETEK** | **VP241** (3U, **Versal Prime** VM1502/VM1802 SoM carrier, Dec 2025) + RTM332 100 GbE rear transition module; VP430 (direct-RF Versal) for RF work |
| **Mercury Systems** | Versal / Zynq VPX processing and RF modules (major SOSA player) — *ask for current Versal SKUs* |
| **Kontron** | VX3 / VX6 SOSA CPU boards (x86 / ARM slow-path side) |
| **Elma Electronic** | **SOSA-aligned development chassis + backplanes** — host the cards while prototyping |

**Lab rig tip:** an **Elma (or Annapolis) SOSA dev chassis + one Versal 3U card + one
processor card** is the standard "rugged-COTS" bench before committing to a
platform-specific build. For the guard, the **Abaco VP241 (Versal Prime)** or a
**WILDSTAR 3XV** maps best to the VMK180 lab work.

---

## 4. Assurance hardware — buyable (the part that matters most for a guard)

### 4.1 Data diodes (one-way enforcement) — buyable with end-user vetting

| Product | Vendor / owner | Assurance (verify current) | Why it matters for UTL-X |
|---|---|---|---|
| **Fox DataDiode** (Fort Fox Hardware Data Diode) | **Fox Crypto B.V.**, Delft — sold by NCC Group to **CR Group Nordic AB** (Mar 2025); *v1 doc said Fox-IT/Thales — incorrect* | CC **EAL7+** (NSCIB, valid to Sep 2028); NBV/AIVD **Zeer Geheim** | Dutch-based, highest assurance; shape B partner for NL |
| **Arbit Data Diode** | Arbit Cyber Defence Systems (DK) | CC **EAL7+** (BSI, recertified via TÜViT); NATO SECRET accreditation via Danish authority (higher levels per datasheet) | **explicitly supports third-party content filters** on the receive side → natural UTL-X plug-in route |
| **SDoT Diode / SDoT gateways** | Infodas — **Airbus Defence and Space** (DE) | approved up to GEHEIM / NATO SECRET / EU SECRET | also a competitor (SDoT Gateway Express does XML/JREAP/JSON filtering) |
| Owl diodes / CDS | Owl Cyber Defense (US) | various | US market, shape B |
| Advenica, Nexor, Waterfall, ST Engineering | SE / UK / IL / SG | various | regional options |
| XTS Diode / XTS Guard 7 | BAE Systems | Guard 7 is Raise-the-Bar compliant per vendor | US/UK market; content-filter API |

### 4.2 Hardware verification / hardsec (partner rather than buy)

| Product | Vendor | Note |
|---|---|---|
| High Speed Verifier 2 / iX | **Everfox** (ex-Forcepoint Federal) | transform → simple typed format → hardware-logic verify + protocol break — the reference for `hardware-acceleration.md` §4a |
| Hardsec isolation (FPGA) | **Garrison** (acquired by Everfox, 2024) | FPGA-based isolation |
| APP-XD | Becrypt (UK) | API/file CDS; works with Glasswall CDR for hardware-verifiable XML |

### 4.3 Crypto

| Item | Products | Note |
|---|---|---|
| **HSM / crypto** | Thales Luna, Entrust nShield, Utimaco, Marvell LiquidSecurity — **FIPS 140-3** | COTS; also for STANAG 4778 label-binding keys |
| **Network encryptors (NATO/national approved)** | e.g. SINA-family (secunet), others per nation — distributed in Benelux by approved resellers (Fox Crypto lists SINA and SkyTale) | **procurement-gated**, government channel |
| **Type 1 / NATO crypto** | — | **NOT COTS** — government-controlled / cleared-vendor only |

**Sovereign (NL/EU) note:** **Fox DataDiode (NL-based, Nordic-owned) + Arbit (DK) +
Infodas (DE/Airbus)** give a strong, buyable European diode story with EAL7+ at the top.
Note the ownership change at Fox Crypto when writing "Dutch sovereign" in proposals.

---

## 5. Edge tier

| Item | Products |
|---|---|
| **Edge compute** (ARM + GPU) | **NVIDIA Jetson Orin** dev kits + modules; **Jetson Thor** (newer, higher-end — *verify price/availability*) — GPU only for 2.0/`ai.*` *outside* the guard path |
| **SWaP-C SoC-FPGA** | AMD Kria K26 / K24 SOM (see §1); Versal AI Edge Gen 2 modules as they appear |

---

## 6. Recommended buy-and-climb path

Steps share the AMD toolchain and the UTL-X native binary, so **work carries forward** —
you de-risk, not rebuild.

| Step | Buy | Prove | Indicative budget |
|---|---|---|---|
| **1. Now** | **KR260** (+ 1 DAC or optic, §1c) | UTL-X native on ARM; BINF decoder in fabric; first form-class → HLS experiment | **≈ €0.4k ex VAT** |
| **1a. Two-zone rig** | **2× ZCU106** + optics/test set (§1c), optional 2× 2U cases (§8) | split guard: per-owner policy, fiber interlink, protocol break in FPGA | **≈ €6.4 – 6.7k ex VAT** (+ €0.3 – 0.8k cases) |
| **1b. Versal** | **VMK180** (or **VEK385** for Gen 2 / A78) | same design on Versal; simple-form **verifier** in fabric (transform → verify) | **~€9–16k** |
| **2. Data-centre PoC** | x86/ARM server + **BlueField-3** + an eval **diode** (Fox / Arbit / Owl via vendor) | gateway (shape A) and diode-adjacent filter (shape B); line-rate steering | server + DPU: quote; diode: vendor eval/loan |
| **3. Rugged lab** | **Elma/Annapolis SOSA chassis + Abaco VP241 or WILDSTAR 3XV or CW VPX3-536** | same design, rugged form; transformer/verifier separation on a card | quote (typically tens of k€) |
| **4. Operational** | platform-specific SOSA card + **accredited crypto + diode / partner verifier**, ruggedized to the MIL-STD envelope | accreditation | programme-funded |

---

## 7. What's cheap/now vs. gated

| Category | Buy now (online) | Procurement-gated | Not COTS |
|---|---|---|---|
| Prototype silicon (Kria / Zynq / Versal eval) | ✅ (lead times up to ~16–23 weeks for new Versal kits) | | |
| DPU (BlueField-3) | ✅ (via OEM/distributor) | BlueField-4: early availability | |
| HSM (FIPS 140-3) | ✅ | | |
| Rugged servers | ~ | ✅ | |
| SOSA VPX FPGA cards | | ✅ | |
| Data diode | | ✅ (end-user vetting; ask for eval units) | |
| Hardware verifier / hardsec | | ✅ (partner) | |
| NATO/national-approved encryptors | | ✅ (government channel) | |
| Type 1 crypto | | | ✅ |

---

## 8. Enclosures — 19″ rack, shock, MIL-rugged, TEMPEST

**Principle:** housings at every level are buyable COTS. The weak link is the evaluation
board inside, and **TEMPEST and MIL compliance are certified for the complete
configuration**, not for the box.

| Level | Example products | Fits ZCU106? | Notes |
|---|---|---|---|
| **Lab / office** | 2U micro-ATX 19″ rackmount cases (nVent Schroff, Fischer Elektronik, Hammond, generic) | Directly (micro-ATX holes) | ~€100–400 each; cut panel openings for SFP+ and RJ45 |
| **Shock-protected transport** | SKB 3RR/3RS shock racks (3U–14U, 20″/24″/30″ deep); Pelican-Hardigg rack cases | Yes, as a rack around the 2U cases | SKB: shell meets/exceeds MIL-STD-810G/H, 8 elastomeric isolators, **standard payload 40–150 lb** — two light 2U cases are below that: ask for a light-payload isolator set or add ballast. ~€1.5–2.5k |
| **MIL-rugged chassis** | Elma 12R2 (5U–14U, baseline-tested to MIL-STD-810F, 167, 901D, 461D); Pixus rugged rackmount (2U–6U, designed to MIL-STD-810/461); Curtiss-Wright Hybricon | Custom mounting plate | Built for Eurocard/VPX payloads — an eval board needs an adapter; this does **not** make the board MIL-qualified |
| **TEMPEST-shielded** | **Holland Shielding (NL)** custom shielded racks; **Siltec (PL)** 19″/21″ racks 16U–47U in SDIP-27 Level A/B/C versions; **Spectrum Control / Emcon** EMSEC cabinets 6U–42U (≥60 dB, 100 MHz–1 GHz; via Milexia in EU), 7RU cabinet with WGF-12 waveguides; ETM4U (NO) | Yes, as a cabinet around the cases | Shielding allows COTS gear in TEMPEST applications **subject to testing/certification of the complete configuration**; vendors offer SDIP-27 testing of populated racks |

**Split-guard rack layout (prototype):**

```
┌──────── 19″ shock rack or shielded cabinet ────────┐
│ [2U case A — owner A seal]  ZCU106 A, own PSU       │
│ [1U blank / cable management]                      │
│ [2U case B — owner B seal]  ZCU106 B, own PSU       │
│   interlink: duplex LC fiber A↔B (inside rack)     │
│   network A / network B fibers out via waveguides  │
│   filtered power entry                             │
└────────────────────────────────────────────────────┘
```

Rules: **one enclosure per owner** (own PSU, own tamper-evident seals); **fiber
everywhere** (passes shielding via waveguides; avoids ground loops); put the RJ45
management port on a fiber media converter or a filtered connector; for strict
separation use **two racks in two zones**.

**Limits to state honestly:** the ZCU106 (0–45 °C, socketed SODIMM, fan heatsink,
plug-in SFPs, no conformal coating) survives *transport* in a shock rack but will not pass
MIL-STD-810 qualification as a system. TEMPEST approval follows from testing the full set
(NL: NBV/NLNCSA route); check whether the room's SDIP-28 zoning already permits lower-level
equipment.

**Prototype enclosure BOM:** 2× 2U micro-ATX cases (~€300–800) + SKB 3RR 6U/7U 20″ shock
rack (~€1.5–2.5k) **or** a 6U–16U shielded cabinet (quote; first NL contact: Holland
Shielding) + fiber waveguide feedthroughs, filtered power entry, tamper-evident seals.

---

## 9. Shipboard / minimal-SWaP guard

On a boat, a ZCU106 in a 19″ case is the wrong direction: lab board, large, fan-cooled,
0–45 °C. Move to a **system-on-module (SOM)** in a **fanless, conduction-cooled** box. Same
Zynq UltraScale+ family → FPGA design and the UTL-X aarch64 binary carry over.

| Route | What | Size / power | Pros | Cons |
|---|---|---|---|---|
| **A. Own carrier + Kria SOMs** | 2× Kria K26 (or K24), industrial grade, on a small custom carrier with 2× SFP+ per half, in a fanless box | ~book-sized; far below eval-board power (*measure*) | lowest unit cost and size; production-grade SOM; ports exactly as needed | you design the carrier and own the full environmental qualification |
| **B. VNX+ (VITA 90)** | Military small form factor: OpenVPX-like features at ~30 % of a 3U slot; up to 80 W per module (VNX/VITA 74: 20 W); SOSA Edition 2 snapshot content | very compact, conduction-cooled | procurable, SOSA-aligned; Zynq US+ modules exist (Enclustra Andromeda XZU70/XZU80 in VNX+ systems); chassis: **Elma VersaPLUS Deployment Kit** (Sep 2026, <6 lb, FPGA/GPU/IO payload slot), **Atrenne 726 Series** (−40 to +85 °C card edge); Versal Gen 2 SFF: **New Wave V3211** (VITA 93 QMC) | higher cost; young ecosystem |
| **C. Buy a compact guard** | **Infodas/Airbus SDoT COMP-LAND** (Security Gateway Express + Software Data Diode) | compact tactical unit | for vehicles/weapon systems; structured-data filtering; approved up to DEU/EU/NATO SECRET | competitor — **benchmark** for size, power and approvals; possible partner |

**Split guard in one minimal box:**

```
┌──────────── fanless box, conduction-cooled ────────────┐
│  ┌─ half A (own power rail) ─┐   ┌─ half B (own rail) ─┐ │
│  │ SOM: FPGA + UTL-X         │══▶│ SOM: FPGA verifier  │ │
│  │ SFP+ to network A         │fib│ SFP+ to network B   │ │
│  └───────────────────────────┘   └─────────────────────┘ │
│  mgmt A (fibre)                          mgmt B (fibre)  │
└──────── ship power in (filtered) ───────────────────────┘
```

If owners require physical separation: two small boxes, fiber interlink (single strand or
certified diode for one-way).

**Design choices (save power/space, add security):**
- **No fans, no disks:** conduction cooling via chassis wall; read-only signed boot from
  eMMC/QSPI — smaller attack surface, nothing to wear out, shock-tolerant.
- **Fiber everywhere:** networks, interlink and management — no ship ground loops, easier
  TEMPEST.
- **UTL-X on the ARM cores:** quad Cortex-A53 is enough for message traffic; if not, step to
  Versal Gen 2 (Cortex-A78) rather than adding an x86 board.
- **Small FPGA logic:** protocol break + verifier only → less power, heat and evaluation scope.

**Naval standards to plan for (confirm with the customer):** shock **MIL-STD-901E**;
vibration **MIL-STD-167-1**; environment **MIL-STD-810** (incl. salt fog, humidity) and/or
**IEC 60945** (maritime equipment); EMC **MIL-STD-461**; ship power **MIL-STD-1399** (US) or
the navy's own standard; non-military vessels: **DNV / class type approval**.

**Advice:** prototype route A on a **KR260** (same K26 SOM, $349) → choose **A** with a
volume customer willing to accept your qualification, **B** if procurement wants a
standard SOSA-aligned platform; study **SDoT COMP-LAND** as the comparison point.

---

## 10. Who to talk to first (guard positioning)

1. **Arbit** — diode with documented third-party filter support (shape B pilot).
2. **Fox Crypto** — NL presence, NBV relationship, Benelux crypto distribution.
3. **An integrator with a hardware verifier** (Everfox/Garrison, or a European integrator)
   — for shape C "UTL-X as transformation stage".
4. **AMD/Avnet/distributor FAE** — Versal Gen 2 roadmap, secure-boot/bitstream
   authentication on Kria/Versal.
5. **A VPX vendor with a European presence** (Curtiss-Wright, Abaco, Kontron, Elma) — only
   once a platform customer is identified.
6. **Holland Shielding** (NL) — shielded enclosure / TEMPEST configuration for the
   split-guard rack.
7. **Enclustra / Elma** — VNX+ modules and chassis for the shipboard minimal-SWaP route.

**Bottom line:** you can start **this week** on a **$349 KR260** (UTL-X native on its ARM
cores + protocol break and BINF decoder in fabric), move to a **VMK180/VEK385** for the
transform → verify prototype, add a **BlueField-3** and a **diode evaluation** for a
data-centre PoC, and only later climb to procurement-gated SOSA rugged cards + accredited
crypto — reusing the same toolchain and binary throughout. The fastest route to a real
deployment is **not** your own accredited hardware but a **filter/transformer role next to
an already-accredited diode or verifier**.

---

## Changelog v1 → v2

- Prices verified/corrected (VCK190 $13,195 not $3–8k; KR260 $349; ZCU104 ~$1,554; added
  VEK280, VMK180, VEK385).
- Recommendation changed: AI-Engine Versal not needed for the guard; KR260 → VMK180/VEK385.
- BlueField: hardware regex discontinued; BlueField-4 status.
- Fox DataDiode ownership corrected (Fox Crypto → CR Group Nordic, not Thales); added Arbit
  (EAL7+), Infodas/Airbus, BAE XTS; added hardware-verifier partners.
- Concrete VPX SKUs (CW VPX3-536, Annapolis WILDSTAR 3XV, Abaco VP241); SOSA aligned vs
  conformant.
- Added export-variant caveat, software list, budget column, "who to talk to".

## Changelog v2 → v2.1

- §1 table: network-port column; KR260 corrected to **1× SFP+ only**; ZCU106 price ($3,234)
  and **2× SFP+**; port notes (QSFP28 breakout, Quad SFP28 FMC).
- New §1a ZCU106 specifics; split-guard step in §6.
- New §8 enclosures (lab / shock / MIL-rugged / TEMPEST) with split-guard rack layout.
- New §9 shipboard minimal-SWaP guard (Kria SOM carrier, VNX+, SDoT COMP-LAND; naval
  standards).

## Changelog v2.1 → v2.2

- New §1b: current EUR prices in NL (ex/incl. VAT) for KV260, KR260, ZCU104, ZCU106, K26/K24 SOMs and
  heatsink; DigiKey ordering notes (DDP vs CPT, VAT, NCNR, export check, alternatives).
- New §1c: SFP+ optics, DAC, fibre and 10G NIC test set; cheaper variants; caveats; where to buy;
  complete PoC hardware total.
- §6 budgets updated to the euro figures.

## Sources (checked 2026-10)

- AMD product pages: VCK190 ($13,195, EK-VCK190-G), VEK385 ($15,995, EK-VEK385-G); fpgadeveloper.com board list (KR260 $349, KV260 $199, ZCU104 $1,554, VEK280 $6,995, VMK180 $9,345); distributor listings (Kria ECCN 5A992C; -ED variants EAR99)
- NVIDIA DOCA 2.5 release notes; BlueField-4 announcements (2025–2026)
- Curtiss-Wright VPX3-536 press release (11 Feb 2025); Annapolis SOSA product page; Abaco VP241 press release (Dec 2025); The Open Group SOSA aligned-products directory
- Fox Crypto: sec-certs.org (NSCIB-CC-2300039-01, EAL7+); NCC Group FY25 results and Alliance News (sale to CR Group Nordic, 28 Mar 2025); NBV Zeer Geheim (dutchitchannel.nl, 2019); Fox Crypto product page (SINA, SkyTale)
- Arbit: EAL7+ recertification, datasheet and whitepaper (arbitcds.com)
- Infodas / Airbus press release (25 Mar 2024); BAE Systems XTS pages; Everfox HSV2 datasheet; Everfox–Garrison acquisition (2024)
- Vectorscan / hyperscan-java (GitHub)
- ZCU106: AMD UG1244 (dimensions, micro-ATX footprint, PC-chassis installation); Mouser/Embedded Computing (18.60 × 24.13 cm; 0–45 °C); boards.fpgadeveloper.com ($3,234; 2× SFP+, 1× 1 GbE); DigiKey (€3,134.88); RidgeRun wiki; verilog-ethernet/"taxi" ZCU106 and KR260 10GBASE-R examples; AMD KR260 page (4× RJ45 1 GbE, 1× SFP+)
- Opsero Quad SFP28 FMC (fpgadeveloper.com)
- SKB 3RR/3RS shock racks (skbcases.com, distributors); Pelican-Hardigg rack cases
- Elma 12R2 brochure (MIL-STD-810F/167/901D/461D); Pixus rugged rackmount enclosures; Curtiss-Wright Hybricon RM810
- Siltec shielded racks (SDIP-27 A/B/C); Spectrum Control EMSEC cabinets; Milexia 7RU shielded cabinet; Holland Shielding shielded racks datasheet; ETM4U
- VNX+: Elma VNX+ page and VersaPLUS kit press release (Sep 2026); Atrenne VNX+ and 726 Series datasheet; Electronic Design on VNX+ vs VNX power; Enclustra VPX/VNX+ page; New Wave V3211 (Military & Aerospace newsletter)
- Infodas SDoT COMP-LAND product page and flyer
- DigiKey NL/BE product pages (SK-KR260-G, SK-KV260-G, EK-U1-ZCU104-G, EK-U1-ZCU106-G, SM-K26-XCL2GI and related items), DigiKey NL delivery terms; Farnell NL listings (ZCU106 kit contents, KV260 kit contents)
- Optics/NIC/switch prices in §1c are indicative market ranges, not quotes
