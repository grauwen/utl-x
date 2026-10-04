# The AMD Kria KR260 as a Low-Cost Split-Guard Prototype Platform for the UTL-X Guard

*Working note, Glomidco B.V. · 2026-10 · Status: exploratory. Companion to `ZCU106-dual-10GbE-guard-prototype-abstract.md`, `hardware-cots-shopping-list.md`, `civilian-guard.md` §8.4.1 and `fpga-programming-primer.md`.*

## Abstract

A UTL-X split-guard proof of concept needs, per half, one connection to its own network, one
dedicated interlink to the other half, and a separate management path — with the network and the
interlink terminating **in the FPGA**, not in the operating system (`fpga-programming-primer.md` §1a).
This note examines whether the AMD **Kria KR260 Robotics Starter Kit** — the cheapest board that can
do this — is a suitable platform, as a low-cost alternative to the dual-10 GbE ZCU106.

The KR260 carries a Kria **K26 SOM** (Zynq UltraScale+, quad Cortex-A53 + FPGA fabric) on a compact
carrier (119 × 140 × 36 mm). Its Ethernet interfaces are exactly what the split guard needs: **one PS
Gb RGMII Ethernet, one PS Gb SGMII Ethernet, two PL Gb Ethernet (RGMII) ports with TSN support, and one
SFP+ connector supporting 10 GigE**. Mapped onto the guard:

- **SFP+ (10G, FPGA side)** → the one-way fibre **interlink** to the other half;
- **PL RJ45 (1 GbE, FPGA side)** → the owner's **network** — so network traffic terminates in the FPGA,
  not in Linux;
- **PS RJ45 (1 GbE, processor side)** → the owner's **management** port, never reachable from the
  guarded network.

The network side therefore runs at **1 GbE instead of 10 GbE**. For a proof of concept that is not a
limitation: guarded message traffic rarely needs 10G, and the part that matters for the security
argument — a 10G fibre interlink with a hardware protocol break and physical one-way enforcement —
is fully present.

Two KR260 boards cost roughly **€740 ex VAT**, against roughly **€6,220** for two ZCU106 boards, and
the kit already includes a power supply, cables and a microSD card. Because the KR260 uses the **same
K26 module** that is sold separately as a production SOM (e.g. the industrial SM-K26-XCL2GI), designs
proven on the KR260 carry over directly to a future own carrier board or appliance.

As with the ZCU106, the 10G Ethernet logic for the SFP+ port and the 1G logic for the PL RJ45 port
must be implemented in the FPGA (AMD Ethernet IP or open-source cores), and a DMA path is needed to
hand payloads to the GraalVM-native UTL-X engine on the ARM cores. The KR260 uses a commercial-grade
device with active (fan) cooling — a lab platform, not a fielded one.

We conclude that **two KR260 boards are the most cost-effective platform for the split-guard proof of
concept**, with the ZCU106 pair reserved for cases that need 10G on the network side as well.

## The split guard on two KR260 boards

```
 Network A (owner A)                                              Network B (owner B)
      │ 1 GbE                                                          │ 1 GbE
  [PL RJ45]                                                        [PL RJ45]
 ┌────────────────────┐     fibre, single strand, simple frames   ┌────────────────────┐
 │  KR260 "A"         │[SFP+]═══════════════════════════════▶[SFP+]│  KR260 "B"         │
 │  FPGA: MAC +       │      10GBASE-R · no TCP/IP crosses        │  FPGA: verifier +  │
 │  protocol break,   │                                           │  MAC               │
 │  framing (TX-only) │                                           │  ARM: UTL-X import │
 │  ARM: UTL-X export │                                           │                    │
 └────────────────────┘                                           └────────────────────┘
  [PS RJ45] mgmt A (owner A only)                                   [PS RJ45] mgmt B (owner B only)
```

| Board port | Wired to | Guard role |
|---|---|---|
| PL Gb Ethernet #1 (RJ45) | FPGA | network of this owner — terminated in the FPGA (protocol break) |
| PL Gb Ethernet #2 (RJ45) | FPGA | spare (e.g. second network or test tap) |
| SFP+ (10G) | FPGA (GTH transceivers) | interlink to the other half — `interlink-protocol-v1.md` profile E |
| PS Gb Ethernet (RJ45) | processor | owner management only |
| PS Gb SGMII Ethernet | processor | unused / spare |

*Check the KR260 board documentation for the exact RJ45-to-interface mapping before wiring.*

**Interlink protocol:** as for the ZCU106 prototype — profile E (raw Ethernet, EtherType 0x88B5, no IP)
on the SFP+, with the fixed "interlink-frame v1" (72-byte header, payload in a registered simple form,
HMAC-SHA-256, CRC-32C), verified by B's FPGA before UTL-X applies B's import policy. For the one-way
demonstration, connect only A's transmit fibre to B's receive side.

## Why a 10G interlink when the network sides run at 1G?

A 1G interlink is technically possible — the KR260's SFP+ cage also accepts ordinary 1G SFP modules —
but it saves almost nothing, and 10G over fibre has real advantages.

**What a 1G interlink would save**

| Interlink | Optics (2×) | Fibre | Difference |
|---|---|---|---|
| 10G — 10GBASE-SR SFP+ | €40 – 70 | same OM3/OM4 patch | — |
| 1G — 1000BASE-SX SFP | ≈ €20 – 30 | same | **saves ≈ €20 – 40** (3–5 % of the test set) |
| 1G copper — second PL RJ45 as interlink | none | Cat6 cable | saves ≈ €50 – 80, but see below |

The copper option undermines the security case: no galvanic isolation, worse for TEMPEST, and a
1000BASE-T link is inherently two-way, so a **physical one-way link cannot be demonstrated**. Acceptable
for a first bench test, not for the demonstration.

**Why 10G over fibre is the better choice**

1. **One-way is simpler at 10G.** 10GBASE-R has no auto-negotiation: the receiver locks without ever
   transmitting. 1000BASE-X uses auto-negotiation, which expects a two-way exchange — it must be
   disabled in the FPGA logic for a single-strand link. Doable, but one more thing to get right.
2. **Headroom for the interlink protocol.** The interlink carries more than the messages: each frame is
   repeated (default R = 2), plus heartbeats and an optional constant-rate mode against timing channels
   (`interlink-protocol-v1.md` §5.1, §7.3). A fully loaded 1G network side with R = 2 already needs
   ≈ 2G on the interlink; 10G never becomes the bottleneck.
3. **The interlink design carries over.** The interlink is the reusable part — ZCU106 pair, appliance,
   2U 12-slot chassis with fibre trunks. Built at 10G once, it never needs reworking.
4. **Stronger demonstration.** "10G fibre, physically one-way, no TCP/IP" — at negligible extra cost.

**When 1G makes sense**

- **As a development step:** a 1G Ethernet core is simpler, uses less logic and closes timing more
  easily (125 MHz vs ≥ 156.25 MHz). Framing and verifier can be brought up at 1G (or over a DAC cable)
  and moved to 10G for the demonstration.
- **In a future, very small design** where FPGA logic or power is tight — the difference stays modest.

**Recommendation:** target **10G over fibre** on the SFP+ for the PoC; optionally bring the link up first
with a DAC cable or a cheap 1G SFP.

## Bill of materials (NL, ex VAT, checked 2026-10)

| Item | Qty | Unit price | Total |
|---|---|---|---|
| Kria KR260 Robotics Starter Kit (SK-KR260-G — standard, encryption enabled) | 2 | ≈ €369 – 372 | ≈ €740 |
| 10GBASE-SR SFP+ optic (850 nm, generic MSA-compliant) | 2 | €20 – 35 | €40 – 70 |
| OM3/OM4 LC-LC duplex patch cable, 1 – 2 m | 1 | €5 – 15 | €5 – 15 |
| 1 GbE test connections (PC NIC ports, USB-Ethernet adapters or a small switch) | as needed | €0 – 40 | €0 – 40 |
| **Total** | | | **≈ €785 – 865** (≈ €950 – 1,050 incl. VAT) |

Included in each kit: power supply, adapters, microSD card and cables. No 10G network card is needed,
because the network sides run at 1 GbE. Optional: a DAC cable (€15 – 30) for first bring-up of the 10G
MAC before switching to optics + fibre for the one-way demonstration.

## KR260 pair vs ZCU106 pair

| | 2 × KR260 | 2 × ZCU106 |
|---|---|---|
| Boards (ex VAT) | ≈ €740 | ≈ €6,220 |
| Complete minimal test set (ex VAT) | ≈ €785 – 865 | ≈ €6,350 – 6,670 |
| Network side | 1 GbE, FPGA-terminated (PL RJ45) | 10 GbE, FPGA-terminated (SFP+) |
| Interlink | 10G SFP+ fibre | 10G SFP+ fibre |
| Management | PS RJ45 | PS RJ45 |
| Size | 119 × 140 × 36 mm per board | 24.1 × 18.6 cm per board (micro-ATX footprint) |
| Chip | K26 SOM (same module as production SOMs) | XCZU7EV (eval-board only) |
| Path to own hardware | **direct** — same K26 on an own carrier | redesign needed |
| Power & accessories | PSU, cables, microSD included | PSU included; optics/DAC not included |
| Best for | the PoC, demos, development, production path | tests that need 10G on the network side too |

## Why not the even cheaper boards?

| Board | Why it does not fit the split guard |
|---|---|
| Kria KV260 (≈ €243) | only one Gigabit Ethernet port and no SFP+ — no separate interlink, no FPGA-terminated network side; board only (no PSU) |
| Avnet ZUBoard 1CG ($159) | only processor-side PS-GTR transceivers (no 10G in the fabric), 1 GB RAM |
| Ultra96-V2 (ZU3EG) | same limitation — no fabric transceivers for 10G, no SFP+ |
| Plain x86/ARM mini PC | no FPGA — fine for the software guard, but no hardware protocol break, verifier or one-way logic |

## Housing and beyond the prototype

- **Mechanics:** 119 × 140 × 36 mm per kit with active cooling (fan + heatsink); fits a small desktop
  enclosure or a 1U shelf. One enclosure per owner, as in the split-guard design.
- **Environment:** commercial-grade device on a starter kit — lab and office only.
- **SFP+ power budget:** the carrier supplies 3.3 V at 600 mA to the SFP+ cage — use SR optics or DAC;
  avoid hot 10GBASE-T (RJ45) SFP+ modules.
- **Path to product:** the same K26 module exists as a production SOM (commercial SM-K26-XCL2GC or
  industrial SM-K26-XCL2GI, 77 × 60 mm, −40…100 °C Tj). A future UTL-X Guard appliance or shipboard
  unit (`hardware-cots-shopping-list.md` §9, route A) can put two such modules on an own carrier board,
  reusing the bitstream and software from this prototype.

## To verify before ordering

- Exact mapping of the four RJ45 ports to PL vs PS interfaces in the KR260 data sheet / user guide.
- Availability of a 10GBASE-R reference design for the KR260 SFP+ (AMD IP or open-source cores) and the
  PL Ethernet (RGMII) reference design.
- Current price and lead time (in stock at the time of writing; manufacturer lead time quoted 8–16 weeks).
- Order the **standard** SK-KR260-G (encryption enabled) — the "-ED" variant is obsolete and would block
  bitstream-security experiments.

## Sources

- AMD KR260 product page (4 × RJ45 10/100/1000, 1 × SFP+ 10G, 119 × 140 × 36 mm, XCK26).
- Kria KR260 Robotics Starter Kit data sheet (Ethernet interfaces: one PS Gb RGMII, one PS Gb SGMII,
  two PL Gb RGMII with TSN, one SFP+ 10GigE; SFP+ 3.3 V / 600 mA; commercial XCK26-C variants).
- DigiKey NL product pages (SK-KR260-G, SK-KV260-G, SM-K26-XCL2GI/GC) — prices checked 2026-10.
- Avnet ZUBoard 1CG announcement (PS-GTR transceivers, $159).
- `hardware-cots-shopping-list.md` §1b–§1c (NL prices, optics test set).
