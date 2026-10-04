# FPGA Programming Primer — Vivado, Vitis and the Bitstream

*Working document, Glomidco B.V. · 2026-10 · v1.1 · Status: explanatory reference — civilian edition (UTL-X Guard).*

| Field | Value |
|---|---|
| Document | fpga-programming-primer (civilian) |
| Repository | `utl-x` — `docs/guard/` |
| Audience | UTL-X developers and product owners new to FPGA work |
| Scope | What an FPGA is, how it is programmed (Vivado, Vitis, Vitis HLS), how the configuration is stored, loaded and updated, and what that means for the UTL-X Guard hardware proof of concept and a later appliance |
| Related | `civilian-guard.md` (§8.4.1 hardware PoC), `interlink-protocol-v1.md`, `ZCU106-dual-10GbE-guard-prototype-abstract.md`, `hardware-acceleration.md`; MIL counterpart: `utlx-mil/docs/fpga-programming-primer.md` |

> **One line:** an FPGA is a chip of uncommitted logic that *becomes* your circuit when it is configured
> with a **bitstream**. On the AMD Zynq/Versal parts used for the guard, that configuration is volatile
> (lost at power-off) and reloaded from rewritable flash at every boot — **hard-wired while running,
> rewritable whenever you choose**, and protectable with encryption and signatures.

---

## 1. What an FPGA is

A Field-Programmable Gate Array is a chip full of **unconnected building blocks** — small look-up tables
(LUTs), flip-flops, block RAM, DSP multipliers, high-speed transceivers — plus a huge network of
**switchable connections**. Programming it does not load a program that runs step by step; it **sets the
switches**, so the chip physically behaves as your circuit, with everything running truly in parallel at
hardware speed.

A **SoC-FPGA** (AMD Zynq UltraScale+ MPSoC, Versal) puts ARM processor cores **and** FPGA fabric on one
chip. For the guard this is the natural split:

| Part of the chip | Runs | Guard role |
|---|---|---|
| **ARM cores** (processing system, PS) | Linux + the GraalVM-native UTL-X binary | parse → UDM → `validate.*` → canonical serialize; policy |
| **FPGA fabric** (programmable logic, PL) | the circuit described by the bitstream | protocol break, interlink framing, BINF decode + CRC, simple-form **verifier** |

---

## 1a. Why the FPGA matters — taking the operating system off the boundary

The FPGA requirement is less about speed (10G) and more about **where untrusted network traffic first
lands**. The guard terminates the network in the FPGA so that untrusted bytes **never reach an
operating system directly**, and so that the boundary is enforced by something an attacker cannot
reprogram.

*Figure: `utlx-fpga-vs-os-boundary.svg` / `.png` — A vs B side by side.*

```
A. Network through the processor (the "normal" way)

network ──▶ Ethernet controller ──▶ Linux kernel ──▶ TCP/IP stack ──▶ UTL-X ──▶ Linux ──▶ interlink
            (processor side)        drivers, ARP,     sockets                    (sent by software)
                                    ICMP, buffers
            ▲ untrusted bytes hit millions of lines of OS code BEFORE the guard sees them

B. Network through the FPGA (our design)

network ──▶ FPGA: MAC + protocol break ──DMA──▶ UTL-X on ARM ──DMA──▶ FPGA: framing ══▶ interlink
            only expected frames, size              (payload only)      transmit-only logic
            and rate limits, no TCP/IP
            on the boundary
```

### Why B is much stronger

1. **The OS is the largest attack surface.** In A every packet first passes the Linux kernel — network
   drivers, ARP, ICMP, the TCP/IP stack, buffer management: millions of lines of code with a long
   vulnerability history, all *before* UTL-X inspects anything. In B packets land in a small, fixed
   circuit that only understands what was built into it.
2. **The protocol break becomes real.** The FPGA terminates the network protocols and passes **only the
   payload** to the ARM cores via DMA. Nobody on the network can talk to Linux: no open ports, no ping
   replies, no ARP, no listening services. The processor is not addressable from the network.
3. **Hardware enforces the boundary, not software.** If an attacker *does* break into Linux:
   - in **A** they control the network stack and can send anything over the interlink;
   - in **B** the interlink is driven by FPGA logic — **transmit-only** on side A, sending only the fixed
     frame format — and side B's **FPGA verifier** rejects every frame that does not match exactly.
     Compromised software cannot change these circuits; they are fixed by a signed bitstream.

   *The thing that enforces the boundary cannot be reprogrammed by the thing being attacked.*
4. **Limits apply before anything is stored.** Frame size, rate and type are checked at line rate in
   hardware; floods and oversized packets are dropped before they reach memory or the OS.
5. **Predictable behaviour.** A circuit has no scheduler, no background processes, no hidden state —
   it does the same thing every time, which an evaluator can analyse. That is very hard to argue for a
   general-purpose operating system.

### What the "≥ 10.3 Gb/s transceivers" requirement really means

The security requirement is: **the network terminates in the FPGA, not in the processor.** The
transceiver speed is only the physical requirement for doing that at **10G**:

- **10G** needs fast transceivers in the FPGA fabric (GTH) — which is why boards such as the ZUBoard
  1CG or Ultra96 (only processor-side PS-GTR transceivers, ≈ 6 Gb/s max) cannot do 10G in the fabric.
- **1G** can be done with ordinary FPGA I/O and an external PHY (e.g. FPGA-wired RJ45 ports on the
  KR260 — check the board documentation for which ports go to the FPGA).

*"Network in the FPGA" is the security requirement; "10G" is a speed choice.*

### Honest nuance

| Where | Role of the FPGA |
|---|---|
| **Interlink** | **Essential** — transmit-only on side A, independent verifier on side B; without it there is no hardware one-way guarantee and no independent check |
| **Network ingress** | **Strongly preferred** — keeps the network attack away from Linux; for a quick software prototype the processor's Ethernet can be used temporarily, knowing the assurance is lower |
| **Software guard (Azure / server)** | none — relies on a hardened OS and container isolation; acceptable for lower assurance, and exactly why the hardware version is the stronger offering |

### What the FPGA is (and is not) for

**Decode ≠ decrypt.** The FPGA **decodes** bit-packed formats (BINF: "bits 8–37 are the vessel ID") —
that is parsing, no secret involved. It does **not** primarily **decrypt**.

| Priority | Role |
|---|---|
| 1 | **Independent verifier** (transform → verify) |
| 2 | **Protocol break** — network terminated in hardware |
| 3 | **Physical one-way enforcement** (with single-strand fibre) |
| 4 | Line-rate binary decoding — only if message volume demands it |
| 5 | Crypto offload — optional; ARM cores (AES/SHA instructions) handle HMAC and label signatures at message rates. Encryption of classified links is done by an **approved encryptor**, deliberately not by our FPGA. |

**In one sentence:** the FPGA keeps untrusted traffic away from the operating system and makes the
boundary a circuit that the software it protects cannot change.

---

## 2. The AMD toolchain: Vivado, Vitis, Vitis HLS

| Tool | What it does | In the guard |
|---|---|---|
| **Vivado** | Hardware design: block design / IP integration, RTL (Verilog/VHDL), synthesis, place & route, timing closure, **bitstream generation**, on-chip debug (ILA) | interlink MAC/PCS, protocol break, verifier logic, AXI DMA to the ARM side |
| **Vitis** | Software for the ARM cores (bare-metal or Linux applications), platform/boot image creation, acceleration libraries | the Linux platform that runs the UTL-X binary; boot image |
| **Vitis HLS** | High-Level Synthesis: write logic in **C/C++**, the tool generates RTL that Vivado turns into hardware | BINF decoders and form-class verifiers written in (or generated as) C++ instead of hand-written Verilog |
| **PetaLinux / Yocto** | Building the embedded Linux image for the ARM cores | root filesystem with the UTL-X binary |

**Why Vitis HLS matters for UTL-X:** a BINF form-class is a static, bounded description of bit fields.
It can be turned into C++ (field extraction, scaling checks, CRC) and from there into hardware — the
declarative source stays the master, and a standards revision becomes a re-generation and re-synthesis,
not a Verilog rewrite.

**Licensing:** Vivado/Vitis come in a free edition (supports many smaller and mid-range devices) and paid
editions; evaluation kits typically include a device-locked licence. **Check that the exact device**
(e.g. XCZU7EV on the ZCU106) is covered by the edition you plan to use.

---

## 3. The design flow

```
design source
  ├─ RTL (Verilog / VHDL)  ─┐
  └─ C++ via Vitis HLS ─────┤
                            ▼
                    synthesis            → design mapped to LUTs, flip-flops, BRAM, DSP
                    place & route        → which block where, which wires connect
                    timing analysis      → does it run at the target clock?
                            ▼
                    bitstream (.bit)     → settings of millions of configuration cells
                            ▼
                    boot image (BOOT.BIN) together with boot loaders and Linux
```

| Step | Typical time | Notes |
|---|---|---|
| HLS C++ → RTL | seconds – minutes | fast iteration; C-simulation first |
| Synthesis + implementation | minutes – hours | depends on design size and device |
| Bitstream generation | minutes | |
| Simulation / test benches | as needed | most bugs are found here, not on the board |

Because a hardware change costs minutes to hours of build time (and, for an accredited product,
re-evaluation), the guard's rule is: **stable logic in the bitstream, changing policy in software.**

---

## 4. Where the configuration lives — the "EEPROM" question

The comparison with an EEPROM helps, with one important difference:

- **The FPGA configuration itself is volatile.** On Zynq/Versal it is held in **SRAM configuration cells**
  inside the chip. Power off → the fabric is empty again.
- **The bitstream is stored next to it in rewritable memory** — QSPI flash, eMMC or an SD card. *That*
  storage behaves like an EEPROM: fixed until you deliberately rewrite it. At every power-up the chip
  loads the bitstream from there.

| FPGA technology | Example | Behaviour |
|---|---|---|
| **SRAM-based** | AMD Zynq UltraScale+, Versal | volatile; loaded from external flash at every boot; unlimited rewrites |
| **Flash-based** | Microchip PolarFire / PolarFire SoC | non-volatile, instant-on, rewritable — closest to the EEPROM idea |
| **Antifuse** | Microchip RTG4 | programmed **once**, permanent — used for space/radiation and maximum tamper resistance |

**In practice:** hard-wired while it runs, rewritable whenever you choose.

---

## 5. How a Zynq UltraScale+ boots

```
power on
 → BootROM (inside the chip, fixed)
 → First-Stage Boot Loader (FSBL) + platform management firmware
 → loads the BITSTREAM into the FPGA fabric          ← fabric becomes your circuit
 → ARM Trusted Firmware → U-Boot → Linux
 → Linux starts the UTL-X guard binary
```

All stages are packed into one **boot image** (`BOOT.BIN`) on SD card, QSPI flash or eMMC.

**Loading a bitstream later:** Linux can load a different bitstream while running (via the *FPGA Manager*
and device-tree overlays). **Partial reconfiguration** can replace one region of the fabric while the rest
keeps running.

**Development loop for the PoC:** build → copy `BOOT.BIN` to an SD card → insert → power on. Swapping
SD cards is the "rewrite the EEPROM" step and makes testing easy.

---

## 6. Updating in the field

| Mechanism | What it gives |
|---|---|
| New bitstream / boot image in flash, then reboot | the normal update path |
| **A/B images (MultiBoot)** | the boot ROM can fall back to a known-good ("golden") image if the new one fails |
| Runtime load via FPGA Manager | swap or add logic without a full reboot |
| Partial reconfiguration | update one function (e.g. a verifier for a new form-class version) while the rest runs |

Updates are a benefit **and** a risk: whoever can write the flash can, in principle, load a different
circuit. That is what bitstream security (§7) is for.

---

## 7. Bitstream security

Zynq UltraScale+ and Versal provide a hardware root of trust for the boot chain:

| Feature | Purpose |
|---|---|
| **Encryption** (AES-GCM, 256-bit) | the bitstream and boot images cannot be read or copied from flash |
| **Authentication** (asymmetric signatures) | the chip **refuses** any boot image or bitstream not signed with your key |
| **Key storage** in one-time-programmable eFuses or battery-backed RAM (with key-encryption options) | keys never leave the chip |
| **Secure boot chain** | each stage verifies the next — BootROM → FSBL → bitstream → U-Boot → Linux |
| **Tamper monitoring / lockdown** | reaction to voltage, temperature or debug-port tampering, up to key zeroisation |
| **JTAG / debug disable** | close the debug back door in production |

*(Details, algorithms and key options per device: AMD Zynq UltraScale+ Technical Reference Manual and
security application notes; Versal equivalents. Verify for the exact part before designing.)*

**For the guard:** in the PoC, security features can stay off for easy development. In any product,
**signed and encrypted boot images with JTAG locked** are mandatory — buy standard (not
"encryption-disabled") device variants if you want to prototype this.

---

## 8. What goes in hardware, what stays in software

| In the bitstream (stable, hot, verifiable) | In UTL-X on the ARM cores (changing, general) |
|---|---|
| interlink MAC/PCS, framing, CRC | parsing any format into the UDM |
| protocol break (no TCP/IP crosses) | Message Contracts, `validate.*` rules, labels |
| BINF decode of a frozen form-class (e.g. AIS) | canonical serialization, target-format policy |
| simple-form **verifier** (receiver side) | audit records, dead-letter handling |
| structural caps on the wire | configuration, monitoring |

Rule of thumb: if it changes when a **policy** changes, it belongs in software; if it changes only when a
**standard or the simple form** changes, it may go into the bitstream.

---

## 9. From a form-class to a verifier — the intended flow

```
BINF form-class (.def)  ──generate──▶  C++ (fields, ranges, CRC)  ──Vitis HLS──▶  RTL
                                                                         │
                                                                   Vivado ▼
                                                                   bitstream (verifier)
```

**Independence rule:** if the same form-class generates both the sender's encoder and the receiver's
verifier, a mistake in the form-class is copied into both. Keep the simple form small, review it by hand,
and build or check the verifier independently (separate generator, hand-written verifier, or formal
equivalence check).

---

## 10. Glossary

| Term | Meaning |
|---|---|
| **Bitstream** | binary file holding the FPGA configuration |
| **PL / PS** | programmable logic (fabric) / processing system (ARM cores) on a SoC-FPGA |
| **LUT, FF, BRAM, DSP** | look-up table, flip-flop, block RAM, multiplier block — the fabric's building blocks |
| **RTL** | register-transfer level — hardware description in Verilog/VHDL |
| **HLS** | high-level synthesis — C/C++ to RTL |
| **Synthesis / place & route** | mapping a design to the chip's blocks and wiring them |
| **ILA** | integrated logic analyser — on-chip debug probe |
| **FSBL** | first-stage boot loader |
| **BOOT.BIN** | Zynq boot image (boot loaders + bitstream + U-Boot) |
| **FPGA Manager** | Linux framework to load bitstreams at runtime |
| **Partial reconfiguration** | replacing one region of the fabric while the rest runs |
| **eFuse / BBRAM** | one-time-programmable fuses / battery-backed RAM for keys |

---

## 11. Civilian edition — what this means for UTL-X Guard

### 11.1 The hardware proof of concept

Planned **after** the software solution is complete (`civilian-guard.md` §8.4.1, roadmap phase 5):

| Board | Fabric (bitstream) | ARM (software) |
|---|---|---|
| ZCU106 "A" (sender) | 10GBASE-R MAC on SFP+ 2, protocol break, AIS BINF decode + CRC, interlink framing (`interlink-protocol-v1.md` profile E) | finished UTL-X guard binary: export policy, canonical simple form |
| ZCU106 "B" (receiver) | interlink receive, frame + simple-form **verifier** (independent) | UTL-X import policy, rebuild, egress |

Development: security features off, SD-card boot, ILA debugging. Skills: one engineer with Vivado/Vitis
HLS experience for ≈ 6–10 person-weeks, alongside the UTL-X developer.

### 11.2 Towards an appliance

If the PoC leads to Glomidco's own appliance (route c in `civilian-guard.md` §8.3):

- switch on **secure boot**: encrypted + signed images, keys in eFuse, JTAG disabled;
- **A/B boot images** for safe field updates; a signed update package containing bitstream + software;
- consider the **Kria K26/K24 SOM** (industrial grade) on an own carrier instead of an evaluation board;
- treat the bitstream as part of the product's security evidence (version, hash, signing procedure) for
  IEC 62443-4-2 or a BSPA evaluation.

### 11.3 What the civilian guard does *not* need

Rugged or radiation-tolerant parts, antifuse FPGAs, TEMPEST engineering or trusted-foundry sourcing.
Those belong to the MIL edition (`utlx-mil/docs/fpga-programming-primer.md`).

---

## Sources and further reading (verify against current AMD documentation)

- AMD Vivado Design Suite and Vitis Unified Software Platform documentation (user guides for design flow, HLS, embedded software).
- AMD Zynq UltraScale+ MPSoC Technical Reference Manual (boot, configuration, security chapters) and related security application notes; Versal equivalents.
- AMD ZCU106 and Kria K26/K24 product documentation.
- Microchip PolarFire / RTG4 product documentation (flash-based and antifuse FPGAs).
