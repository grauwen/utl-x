# The AMD ZCU106 as a Dual-10 GbE Prototype Platform for the UTL-X Guard

*Working note, Glomidco B.V. · 2026-10 · Status: exploratory. Companion to `hardware-cots-shopping-list.md` and `hardware-acceleration.md`.*

## Abstract

A cross-domain guard prototype needs at least two high-speed network interfaces, one per security zone, plus a separate management path. This note looks at whether the AMD Zynq UltraScale+ MPSoC **ZCU106** evaluation board meets that need.

The ZCU106 has **two SFP+ cages**, each able to run **10 GbE (10GBASE-R)**. That makes it the **cheapest board in the hardware shortlist with two ports above 1 GbE**. The KR260 offers only one 10G port, and the ZCU104 and VEK280 lack a second fast port.

The ports are not plug-and-play network interfaces, however. The ARM processor's built-in Ethernet only reaches 1 GbE, so the **10G Ethernet logic has to run in the FPGA**. Two options:

- AMD's licensed **10G/25G Ethernet Subsystem** (an evaluation licence is available).
- Open-source cores such as **verilog-ethernet / "taxi"**, which include 10GBASE-R reference designs for the ZCU106 and build in the free Vivado Standard edition.

Getting the ports into Linux, and so into the GraalVM-native UTL-X engine, also needs a **DMA path from the FPGA to the processor** (for example AXI DMA/MCDMA plus a driver).

For a guard this architecture is an advantage, not a drawback: **all traffic passes through FPGA logic first**, where the protocol break and fast-path checks belong, before reaching the UTL-X transformer on the ARM cores. A separate **1 GbE RJ45 port on the processor side** serves as the management interface. SFP+ optics or DAC cables must be bought separately.

We conclude that the ZCU106 is a suitable **low-cost platform for a two-zone guard prototype** within the Zynq UltraScale+ family, with the **VMK180** (SFP28 + QSFP28) as the Versal-based upgrade path.

One caveat: both zone interfaces sit on a single device. That is acceptable for prototyping but will likely need **physical separation for accreditation**.

## Extension: two ZCU106 boards as a split guard

Placing **two ZCU106 boards back to back** turns the single-board prototype into a
**split guard** (deployment shape D in `UDM-content-guard.md` §3b): each network owns and
operates its own half, and the halves meet only over a dedicated fiber interlink.

```
 Network A (owner A)                                         Network B (owner B)
      │                                                            │
  [SFP+ 1]                                                     [SFP+ 1]
 ┌──────────────┐        fiber, simple framed protocol        ┌──────────────┐
 │  ZCU106 "A"  │[SFP+ 2]════════════════════════════[SFP+ 2] │  ZCU106 "B"  │
 │ FPGA + UTL-X │        (no TCP/IP crosses)                  │ FPGA + UTL-X │
 └──────────────┘                                             └──────────────┘
  [RJ45] mgmt A                                                 [RJ45] mgmt B
```

The board's port set maps exactly onto this design: one 10G port per network, one 10G
interlink, one private 1 GbE management port per owner.

- **Ownership:** each owner controls configuration, keys, logs, updates and
  accreditation of their half.
- **Double inspection:** the sender applies its *export* policy, the receiver its *import*
  policy — two Message Contracts and two `validate.*` rule sets.
- **Protocol break:** TCP/IP terminates in each node's FPGA; only canonical messages in a
  simple framed format cross the interlink.
- **One-way option:** for A → B only, remove one fiber strand (10GBASE-R has no
  auto-negotiation; custom FPGA logic can receive without a return path). This is a
  home-built diode for prototyping only; production uses a certified EAL7+ diode.

**Interlink protocol:** defined in `interlink-protocol-v1.md` — for this prototype use
profile E (raw Ethernet, EtherType 0x88B5, no IP) on SFP+ 2, with the fixed
"interlink-frame v1" (72-byte header, payload in a registered simple form, HMAC-SHA-256,
CRC-32C), verified by B's FPGA before UTL-X applies B's import policy.

**Main pitfalls:** identical halves share identical bugs (keep B's receiving stage minimal,
ideally a hardware verifier); no shared management network; the interlink format must be
an interface specification approved by both owners.

**Prototype BOM** (NL prices, ex VAT, checked 2026-10 — see `hardware-cots-shopping-list.md` §1b–§1c):
2 × ZCU106 (≈ €3,110 each); 2 × 10GBASE-SR SFP+ optics (interlink, €20–35 each); 1 × OM3/OM4 LC-LC
patch (€5–15); 2 × SFP+ DAC to a test PC (€15–30 each); 1 × dual-port 10G SFP+ NIC (used €40–80, new
€250–350); two separate management networks. **Total ≈ €6,350 – 6,670 ex VAT.** A DAC can replace the
optics for first bring-up, but only fibre demonstrates a physical one-way link.

## Housing and beyond the prototype

- **Mechanics:** 24.13 × 18.60 cm, micro-ATX chassis footprint → fits standard 2U
  micro-ATX 19″ cases; operating range 0–45 °C (lab/office only).
- **Rack:** one 2U case per owner, stacked in a shock-isolated transit rack (SKB /
  Pelican-Hardigg) or a TEMPEST-shielded cabinet (e.g. Holland Shielding, Siltec,
  Spectrum Control); fiber through waveguides; filtered power. TEMPEST/MIL compliance is
  certified per complete configuration — see `hardware-cots-shopping-list.md` §8.
- **Shipboard / minimal SWaP:** the ZCU106 is not the product platform. Move to Kria
  K26/K24 SOMs on a fanless carrier or VNX+ (VITA 90) modules — see
  `hardware-cots-shopping-list.md` §9 and `hardware-acceleration.md` §9.8.

## Fast-port comparison (shortlist)

| Board | Fast ports on board | Two ports >1G? |
|---|---|---|
| KV260 | none | No |
| KR260 ($349) | 4× 1 GbE RJ45 + 1× SFP+ (10G) | No |
| ZCU104 (~$1,554) | 1 GbE only | No |
| **ZCU106** (≈ €3,110 ex VAT / ≈ €3,763 incl. VAT in NL) | **2× SFP+ (10G)** + 1× 1 GbE RJ45 (management) | **Yes** |
| VEK280 (~$6,995) | 1× SFP28 | No |
| VMK180 (~$9,345) | SFP28 + QSFP28 + RJ45 | Yes |
| VCK190 ($13,195) | SFP28 + QSFP28 + RJ45 | Yes |
| VEK385 ($15,995) | QSFP28 + SFP28 (25–100 GbE) | Yes |

## To verify before ordering

- Current ZCU106 price and lead time (seen: $3,234 list; DigiKey €3,134.88, ~8-week lead).
- Transceiver wiring and SFP+ details in the ZCU106 user guide (UG1244).
- Licensing terms if using AMD's 10G/25G Ethernet Subsystem rather than an open-source MAC.
- Exact SFP28/QSFP28 cage count on VMK180, VCK190 and VEK385.
- For the split guard: whether a unidirectional 10GBASE-R link (one fiber strand) behaves as expected with the chosen SFP+ modules and PCS/MAC core.

## Sources

- RidgeRun Developer Wiki, Zynq UltraScale+ developer kits (ZCU106: 2× SFP+ cages).
- verilog-ethernet / "taxi" example design for ZCU106 (looped-back 10GBASE-R MACs on the SFP+ ports).
- AMD KR260 product page (4× RJ45 1 GbE, 1× SFP+ 10G).
- AMD VMK180 and VEK280 user guides; AMD VEK385 product page.
