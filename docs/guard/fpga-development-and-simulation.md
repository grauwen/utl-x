# FPGA Development and Simulation — Building the Guard Logic Without (and Before) a Board

*Working document, Glomidco B.V. · 2026-10 · v1 · Status: explanatory reference and development plan. Shared by the civilian (`utl-x`) and MIL (`utlx-mil`) editions.*

| Field | Value |
|---|---|
| Document | fpga-development-and-simulation |
| Repositories | `utl-x` — `docs/guard/` and `utlx-mil` — `docs/` (identical copies) |
| Audience | UTL-X developers, FPGA developers, project leads planning the hardware PoC |
| Scope | How the guard's FPGA logic and ARM software are developed with Vivado/Vitis, and how almost all of it can be simulated and emulated **before a board exists** — tools, levels, a development path, test vectors from UTL-X, CI, and what still needs real hardware |
| Related | `fpga-programming-primer.md` (what an FPGA is, bitstream, boot, security, why the network terminates in the FPGA), `interlink-protocol-v1.md` (frame format, conformance tests T01–T27), `KR260-split-guard-prototype-abstract.md`, `ZCU106-dual-10GbE-guard-prototype-abstract.md`, `civilian-guard.md` §8.4.1 (hardware PoC), `military-multiguard-4U.md` |

> **One line:** with free tools, roughly everything except real transceivers, optics, timing-in-silicon, power and
> heat can be developed and tested in simulation — so FPGA work can start in parallel with UTL-X development,
> months before the boards arrive, and the hardware PoC becomes shorter and less risky.

---

## 1. What has to be built

| Part | Runs on | Built with | Example in the guard |
|---|---|---|---|
| UTL-X engine + guard rules | ARM cores (and any laptop) | Kotlin / GraalVM native | parse → UDM → `validate.*` → canonical write |
| Embedded Linux platform | ARM cores | PetaLinux or Yocto-based flow (check current AMD release) | root filesystem with the UTL-X binary, DMA drivers |
| FPGA logic (fabric) | programmable logic | Vivado (RTL, block design), Vitis HLS (C++ → RTL) | 10G/1G MAC, protocol break, interlink framing, BINF decode, independent verifier |
| Glue | both | Vivado block design, device tree, drivers | AXI DMA between FPGA and ARM |

---

## 2. Toolchain

| Tool | Role | Licence (check per device) |
|---|---|---|
| **Vivado** | RTL design, block design, synthesis, implementation, bitstream, **Vivado Simulator (XSim)**, on-chip debug (ILA) | free Standard edition covers many devices; eval kits often include a device-locked licence (the ZCU106 kit includes a Design Edition seat) |
| **Vitis** | embedded software, platform and boot image, software/hardware emulation targets | free |
| **Vitis HLS** | C/C++ → RTL; **C simulation** and **C/RTL co-simulation** | free |
| **PetaLinux / Yocto** | embedded Linux build; QEMU boot | free |
| **QEMU (AMD fork)** | emulation of the Zynq UltraScale+ processing system | free, open source |
| **Verilator** | very fast open-source (System)Verilog simulator | open source |
| **Icarus Verilog / GHDL** | open-source Verilog / VHDL simulators | open source |
| **cocotb** | Python testbenches for any of the simulators above | open source |
| **cocotbext-eth** | Ethernet frame, MII/GMII/XGMII models for cocotb | open source |

**Version pinning:** fix tool versions per project (Vivado/Vitis/PetaLinux release, simulator versions) and record
them with every build — essential for reproducible bitstreams and, in the MIL edition, a stated accreditation
expectation.

---

## 3. The simulation and emulation levels

| Level | Tool | What is tested | Speed | Board? |
|---|---|---|---|---|
| **1. UTL-X itself** | JVM / GraalVM on a laptop | parsing, rules, canonical output, interlink frame generation | fast | no |
| **2. ARM side** | **QEMU** (Zynq UltraScale+ PS; e.g. `petalinux-boot --qemu`) | boot chain, Linux image, aarch64 UTL-X binary, configuration | moderate | no |
| **3. FPGA logic in C++** | **Vitis HLS C simulation** | BINF decoder, verifier, framing as C++ against a C++ testbench | very fast | no |
| **4. Generated hardware** | **Vitis HLS C/RTL co-simulation** | same testbench against the generated RTL | slow | no |
| **5. RTL simulation** | **XSim**, Verilator, Icarus, GHDL | protocol break, interlink framing, MAC logic at signal level | slow (Verilator faster) | no |
| **6. Python testbenches** | **cocotb + cocotbext-eth** | Ethernet and interlink frames driven into the design; automated checks | slow, very productive | no |
| **7. ARM + FPGA together** | **QEMU co-simulation with RTL** (SystemC/TLM bridge to Verilator or XSim); Vitis **software / hardware emulation** targets for accelerated apps | DMA path between Linux/UTL-X and the FPGA logic, drivers, end-to-end flow | slow | no |
| **8. Synthesis and timing** | Vivado synthesis + implementation | fits in the device? meets the clock? resources and power estimate | minutes–hours | no (licence must cover the device) |
| **9. On the board** | KR260 / ZCU106 / VPX card + **ILA** | real transceivers, SFP+/optics, one-way fibre, throughput, latency, power, heat, secure boot | real time | **yes** |

---

## 4. Development path for the guard

```
 1. UTL-X on laptop          → rules, canonical form, interlink frames as test vectors
 2. HLS C-sim                → BINF decoder + verifier in C++, fed with UTL-X test vectors
 3. cocotb + XSim/Verilator  → protocol break, interlink framing, MAC, with Ethernet models
 4. QEMU                     → Linux + UTL-X binary on the emulated ARM cores
 5. QEMU + RTL co-sim        → DMA path ARM ↔ FPGA, end to end
 6. Synthesis / timing       → fits in XCK26 (KR260) / XCZU7EV (ZCU106) / XQZU19EG (VPX)?
 7. Board                    → transceivers, optics, one-way demonstration, measurements
```

Steps 1–6 need no hardware and can run **in parallel with UTL-X development**. Step 7 is the hardware PoC
(`civilian-guard.md` §8.4.1, roadmap phase 5).

---

## 5. Test vectors generated by UTL-X

UTL-X produces exactly what the FPGA must accept or reject, so it can **generate the expected data** for the
FPGA testbenches:

```
test cases (JSON, XML, AIS, …)
   │  UTL-X guard pipeline (parse → UDM → validate.* → canonical write)
   ▼
canonical simple form  ──▶  interlink frames (interlink-frame v1: header, payload, HMAC, CRC)
   │                              │
   │                              ├─▶ good frames    → verifier must ACCEPT, output must match
   │                              └─▶ mutated frames → verifier must REJECT with the right reason code
   ▼
files (.bin / .hex / .pcap) consumed by HLS C-sim, cocotb and the board tests
```

- **Positive vectors:** valid messages per flow and format → expected frames and payloads.
- **Negative vectors:** the conformance set from `interlink-protocol-v1.md` §11 (T01–T27) — wrong magic or
  version, unknown flow, oversized length, bad MAC/CRC, sequence replay, label mismatch, simple-form violations.
- **Fuzzed vectors:** random mutations of good frames (target ≥ 10⁸ cases in long runs); every frame must be
  accepted-valid or rejected with a reason code — never crash or hang.
- **Packet captures:** the same frames as `.pcap` files, so Wireshark (profile E, EtherType 0x88B5) and the board
  tests use identical data.

**Independence rule:** the UTL-X side *generates* frames; the FPGA verifier is implemented *independently*
(`fpga-programming-primer.md` §9). Test vectors check that both agree on the ICD — they do not replace an
independent implementation.

---

## 6. Example testbench sketch (cocotb, illustrative)

```python
# tb_verifier.py — illustrative cocotb test for the side-B frame verifier
import cocotb
from cocotb.clock import Clock
from cocotb.triggers import RisingEdge

def load_frames(path):
    # frames generated by UTL-X (one binary frame per file entry)
    ...

@cocotb.test()
async def accepts_valid_frames(dut):
    cocotb.start_soon(Clock(dut.clk, 6.4, units="ns").start())   # 156.25 MHz
    for frame in load_frames("vectors/valid.bin"):
        await send_frame(dut, frame)                               # drive AXI-Stream input
        verdict = await read_verdict(dut)
        assert verdict.accepted, f"valid frame rejected: {verdict.reason}"

@cocotb.test()
async def rejects_bad_mac(dut):
    ...
    assert not verdict.accepted and verdict.reason == "R_MAC"
```

The same structure covers every conformance test; cocotbext-eth supplies MAC-level models for the network side.

---

## 7. Continuous integration

| Stage | Tooling | Runs where |
|---|---|---|
| UTL-X unit and conformance tests | Gradle / JVM | any CI runner |
| Test-vector generation | UTL-X CLI | any CI runner |
| RTL simulation + cocotb regression | Verilator, cocotb, cocotbext-eth | any CI runner (open source) |
| HLS C-sim / co-sim, XSim, synthesis, timing | Vitis HLS, Vivado | **self-hosted runner** with the AMD tools installed |
| QEMU boot + smoke test of the UTL-X binary | QEMU, PetaLinux/Yocto image | self-hosted or container runner |
| Board-in-the-loop (later) | KR260 / ZCU106 on a lab runner | lab runner with board attached |

Every merge then runs: UTL-X tests → vectors → RTL regression → (nightly) synthesis/timing → (when available)
board tests.

---

## 8. Suggested repository layout

```
fpga/
├── hls/              # Vitis HLS C++: BINF decoders, verifier, framing
│   └── tb/           # C++ testbenches
├── rtl/              # Verilog/VHDL: MAC integration, protocol break, glue
├── tb/               # cocotb testbenches (Python)
├── vectors/          # UTL-X-generated frames: valid/, invalid/, fuzz/, pcap/
├── bd/               # Vivado block designs (Tcl, not binary projects)
├── constraints/      # per board: kr260/, zcu106/, vpx-<card>/
├── platform/         # PetaLinux/Yocto configuration, device tree
└── scripts/          # build, simulation and CI scripts; pinned tool versions
```

Keep Vivado projects as **Tcl scripts** in the repository (reproducible), not as binary project folders.

---

## 9. What simulation does not tell you — and the board does

| Only on hardware | Why it matters |
|---|---|
| Real high-speed transceivers and SFP+ behaviour (link-up, signal integrity, one-way fibre without return path) | the one-way demonstration and 10GBASE-R behaviour |
| Timing in real silicon | synthesis predicts it well; measure anyway |
| Throughput and latency under load | simulation is too slow for seconds of real traffic |
| Power and heat | cooling and power-supply choice (`military-multiguard-4U.md` §6) |
| Bitstream security in practice (eFuse keys, secure boot, JTAG lock) | eFuses are one-time — test on a board you are willing to "burn" |

---

## 10. Practical guidance

- **Start now:** the FPGA developer can build and verify the verifier and framing logic while the UTL-X developer
  builds the guard core; the boards are needed only from step 7.
- **Simulation is for correctness, not performance:** keep long throughput tests for the board.
- **Check licence coverage** for each target device before relying on synthesis/timing in CI (XCK26, XCZU7EV,
  XQZU19EG).
- **Re-targeting** (K26 → defence-grade XQ device for the MIL edition) changes constraints and resources, not the
  testbenches — the same vectors and cocotb tests carry over.
- **Effort effect:** with steps 1–6 done in simulation, the hardware PoC (≈ 6–10 person-weeks) shifts largely to
  integration and measurement on the board.

---

## 11. Edition notes

| | Civilian (`utl-x`) | MIL (`utlx-mil`) |
|---|---|---|
| Target devices | XCK26 (KR260, K26 SOM), XCZU7EV (ZCU106) | XQZU19EG / ZU7–ZU5-class VPX cards (`military-multiguard-4U.md`) |
| Extra requirements | reproducible builds, SBOM | pinned and archived toolchain, configuration-controlled build environment, IP-core provenance records, independent verifier implementation evidence |
| Test evidence | regression results per release | regression and fuzzing results as part of the accreditation evidence package |

---

## Sources and further reading (verify against current documentation)

- AMD Vivado Design Suite and Vivado Simulator documentation; Vitis HLS user guide (C simulation, C/RTL co-simulation); Vitis embedded software and emulation documentation.
- AMD/Xilinx QEMU and PetaLinux tools documentation (QEMU boot of Zynq UltraScale+ images); AMD wiki "MPSoC PS and PL Ethernet Example Projects": https://xilinx-wiki.atlassian.net/wiki/spaces/A/pages/478937213/MPSoC+PS+and+PL+Ethernet+Example+Projects
- Verilator, Icarus Verilog, GHDL, cocotb and cocotbext-eth project documentation.
- `interlink-protocol-v1.md` §11 — conformance test cases T01–T27.
