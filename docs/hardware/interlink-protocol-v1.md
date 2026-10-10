# Interlink Protocol v1 — Interface Control Document (draft) for the UTL-X Split Guard

*Working document, Glomidco B.V. · 2026-10 · v1.1-draft · Status: exploratory design. Not a standard; not a security accreditation.*

| Field | Value |
|---|---|
| Document | interlink-protocol-v1 |
| Status | Draft ICD — for review by both owners of a split guard |
| Scope | The protocol on the dedicated link between half A and half B of a UTL-X split guard (deployment shape D) |
| Related | `UDM-content-guard.md` §3b (split guard), `hardware-acceleration.md` §4a (transform → verify) and §9.8, `ZCU106-dual-10GbE-guard-prototype-abstract.md`, `BINF-bit-level-binary-format.md`, `guard-parser-profile.md` |
| Owners | Owner A (sending half) and Owner B (receiving half) — **both must sign** this ICD and every change to it |

> **Design rule in one line:** the interlink protocol is the most security-critical interface
> in the split guard, so it is **small, fixed, stateless and fully checkable by half B in
> FPGA logic** before any software sees a byte. No IP, no TCP, no sessions, no options.

---

## 1. Purpose and principles

The interlink carries **approved, canonical messages** from half A (after A's export policy)
to half B (which applies its own import policy). Principles:

1. **Protocol break.** Nothing a network attacker understands crosses the link: no IP, TCP,
   UDP, ARP, DHCP or routing. Commercial hardware verifiers use the same idea — a simple
   protocol over raw Ethernet so that no TCP/IP crosses the verifier.
2. **Fixed format.** One frame layout, fixed header, no TLV extensions, no optional fields.
   Unknown values are rejected, never ignored.
3. **Stateless verification.** Each frame can be verified on its own (except bounded
   reassembly and sequence tracking).
4. **Drop, never repair.** Any deviation → drop + count + alert. B never "fixes" a frame.
5. **One-way by default.** A transmits, B receives. Two-way traffic = two independent
   simplex links, each with its own ICD instance.
6. **Independent verification.** B's verifier is implemented independently of A's encoder
   (hand-written or by a second team) and checked against this ICD, so a single mistake is
   not replicated in both halves.

---

## 2. Layer model

```
 ┌──────────────────────────────────────────────┐
 │ 4. Payload: canonical message (simple form)  │  ← UTL-X canonical write, BINF form-class
 ├──────────────────────────────────────────────┤
 │ 3. Guard frame: fixed header + payload + MAC │  ← this ICD (§4)
 ├──────────────────────────────────────────────┤
 │ 2. Link layer: raw L2 (prototype)            │  ← §3.2
 │              or Aurora 64B/66B simplex (product)
 ├──────────────────────────────────────────────┤
 │ 1. Physical: 10GBASE-R over fibre (SFP+)     │  ← §3.1; one strand for one-way
 └──────────────────────────────────────────────┘
```

---

## 3. Physical and link layer

### 3.1 Physical (L1)

| Item | Value |
|---|---|
| Medium | Optical fibre (multimode OM3/OM4 with 10GBASE-SR, or single-mode with 10GBASE-LR) |
| Line rate | 10.3125 GBd (10GBASE-R, 64b/66b) — default; 1000BASE-X allowed with restrictions (§3.3) |
| Ports | ZCU106 prototype: SFP+ cage 2 on each board |
| One-way | **Only A's TX fibre is connected to B's RX.** B's TX is not connected (or the SFP on B is receive-only). 10GBASE-R has no auto-negotiation; with custom FPGA logic B can lock without a return path. |
| Note | A home-built one-way link is acceptable for **prototyping only**. In production, a certified diode (e.g. EAL7+) may replace the bare fibre; the frame format in §4 stays the same. |

### 3.2 Link layer (L2) — two allowed profiles

| | **Profile E — raw Ethernet** (prototype) | **Profile R — AMD Aurora 64B/66B** (product) |
|---|---|---|
| Framing | Ethernet II frames, no VLAN tags | Aurora user frames, no Ethernet |
| Addressing | Fixed destination and source MAC, configured per ICD; others → drop | Point-to-point, no addressing |
| EtherType | `0x88B5` (IEEE 802 local experimental) — **only** this value accepted | n/a |
| Max L2 payload | 9,000 B (jumbo, point-to-point only) | Per Aurora configuration ≥ L3 max frame |
| Must be disabled | Pause frames (802.3x/PFC), VLAN processing, any IP/ARP offload in the MAC | Flow-control and native-flow-control options |
| Simplex | Achieved physically (one strand) | Aurora 64B/66B supports simplex (TX-only / RX-only) operation — **verify in AMD PG074 for the chosen core version** |
| Debugging | Wireshark-readable on a tap | Needs FPGA ILA or a protocol analyser |

The L3 guard frame (§4) is identical in both profiles. Exactly one profile is selected per
link in the ICD configuration (§9).

---

### 3.3 Line rate choice — why 10G, even with 1G network sides

The interlink line rate is independent of the network-side rate. **10GBASE-R is the default** for this
ICD, also on platforms whose network sides run at 1 GbE (e.g. the KR260 split guard):

| Reason | Detail |
|---|---|
| One-way operation | 10GBASE-R has no auto-negotiation — a receive-only side locks without transmitting. 1000BASE-X auto-negotiation expects a two-way exchange and **must be disabled** for single-strand operation. |
| Headroom | Repetition (R = 2, §5.1), heartbeats and optional constant-rate mode (§7.3) multiply the payload rate; a fully loaded 1G network side with R = 2 already needs ≈ 2G of interlink. |
| Reuse | One interlink design for every platform (KR260, ZCU106, appliance, multi-guard chassis trunks). |
| Cost | The difference between 10G SR SFP+ and 1G SX SFP optics is ≈ €10 – 20 per module. |

**Allowed alternative — 1000BASE-X (profile E only):** permitted for development or for very small
designs, provided auto-negotiation is disabled, the same L3 frame format is used, and the link remains
fibre. **Not allowed:** copper 1000BASE-T as interlink in any demonstration or deployment — it is
inherently two-way and provides no galvanic isolation. A DAC cable may be used for bring-up only.

---

## 4. Guard frame format (L3) — "interlink-frame v1"

### 4.1 Encoding rules

- All multi-byte integers **big-endian** (network order).
- All fields fixed-size; offsets fixed.
- Reserved bytes **must be zero**; non-zero → reject.
- Maximum L3 frame size: **8,192 bytes** (header 72 + payload ≤ 8,084 + MAC 32 + CRC 4).
- Minimum L3 frame size: **108 bytes** (empty payload, e.g. heartbeat).

### 4.2 Header layout (72 bytes)

| Offset | Size | Field | Contents / rule |
|---|---|---|---|
| 0 | 4 | `magic` | `0x55 0x54 0x4C 0x47` ("UTLG"); anything else → reject |
| 4 | 1 | `version` | `0x01` for this ICD; unknown → reject |
| 5 | 1 | `type` | `0x01` DATA, `0x02` HEARTBEAT; all other values → reject |
| 6 | 2 | `flow_id` | Approved flow (§9 flow table); `0x0000` only for HEARTBEAT |
| 8 | 8 | `seq` | Per-flow sequence number within the current epoch; starts at 1; strictly increasing per frame |
| 16 | 2 | `frag` | Fragment index, 0 … `frag_count`−1 |
| 18 | 2 | `frag_count` | Number of fragments of this message, 1 … flow's `max_frag` |
| 20 | 4 | `payload_len` | Payload length in bytes, 0 … min(flow `max_payload`, 8,084) |
| 24 | 2 | `payload_format` | Registered simple-form ID (§6); must match the flow's configured format |
| 26 | 1 | `key_id` | Active MAC key identifier (§7.2) |
| 27 | 1 | `reserved` | Must be `0x00` |
| 28 | 4 | `epoch` | A's boot/restart counter (monotonic, persisted in A) |
| 32 | 8 | `ruleset_id` | First 8 bytes of SHA-256 of A's active export rule set |
| 40 | 32 | `label_digest` | SHA-256 of the canonical STANAG 4774 label of the message; all-zero if the flow is configured as unlabelled |

### 4.3 Trailer

| Offset | Size | Field | Contents / rule |
|---|---|---|---|
| 72 + L | 32 | `mac` | HMAC-SHA-256 (key = `key_id`) over bytes `[0, 72+L)` |
| 104 + L | 4 | `crc` | CRC-32C over bytes `[0, 104+L)` — transmission errors only; **not** a security control |

(L = `payload_len`.)

### 4.4 Frame types

| Type | Purpose | Constraints |
|---|---|---|
| `DATA` (0x01) | Carries one fragment of one approved message | `flow_id` ≠ 0; `payload_len` > 0 |
| `HEARTBEAT` (0x02) | Liveness of A and of the link | `flow_id` = 0, `frag` = 0, `frag_count` = 1, `payload_len` = 0, `payload_format` = 0, `label_digest` all-zero; has its own `seq` stream (flow 0) |

No other frame types exist in v1. In particular there are **no control, status,
acknowledgement, configuration or management frames**.

---

## 5. Sending and receiving behaviour

### 5.1 Sender (half A)

1. UTL-X applies A's export policy (parse → contract → label → `validate.*`) and produces the
   **canonical simple form** for the flow (§6).
2. Split into fragments if larger than the per-frame payload maximum; all fragments of one
   message get **consecutive** `seq` values.
3. Build header, compute MAC, compute CRC.
4. **Repetition:** transmit each frame **R times** (R per flow, default 2) — compensates for
   loss without acknowledgements. On a short internal fibre, R = 2 is normally sufficient;
   use FEC at L2 if the link is long or noisy.
5. **Rate limit:** token bucket per flow (§9); A never blocks on B.
6. **Heartbeat:** send `HEARTBEAT` every `T_hb` (default 100 ms), also when idle.
7. On restart: increment and persist `epoch`, restart all `seq` at 1.
8. Log per message: flow, epoch, seq range, payload hash, `ruleset_id`, verdict.

### 5.2 Receiver (half B) — verification pipeline

Stages **HW1–HW9 run in FPGA logic**; only frames passing all of them reach software.

| Stage | Check | On failure (reason code, §5.4) |
|---|---|---|
| HW1 | L2: correct EtherType / MACs (profile E) or valid Aurora frame (profile R); size 108 … 8,192 | `R_L2`, `R_SIZE` |
| HW2 | `crc` correct | `R_CRC` |
| HW3 | `magic`, `version`, `type`, `reserved` exact | `R_HDR` |
| HW4 | `flow_id` in flow table (or 0 for heartbeat); `payload_format` matches flow | `R_FLOW`, `R_FMT` |
| HW5 | `payload_len` ≤ flow max; `frag` < `frag_count` ≤ flow `max_frag`; frame length = 108 + `payload_len` | `R_LEN`, `R_FRAG` |
| HW6 | `key_id` currently accepted; `mac` correct | `R_KEY`, `R_MAC` |
| HW7 | `epoch` ≥ last epoch; sequence check (§5.3) | `R_EPOCH`, `R_SEQ`, `R_DUP` |
| HW8 | `label_digest` zero iff flow unlabelled | `R_LABEL` |
| HW9 | Payload conforms to the simple form for `payload_format` (form-class verifier, §6) | `R_FORM` |
| SW1 | Reassembly complete within `T_reasm`; message size ≤ flow max | `R_REASM` |
| SW2 | UTL-X on B: parse simple form → B's import contract → label releasability → `validate.*` | `R_POLICY` |
| SW3 | Canonical write to network B | — |

### 5.3 Sequence, duplicates and gaps

- B keeps, per flow, the highest accepted `seq` in the current `epoch` and a **duplicate
  window** of W frames (default 1,024).
- `seq` = expected → accept.
- `seq` already seen **and** frame byte-identical to the accepted one (same MAC) → expected
  repetition: drop silently, count `C_REPEAT`.
- `seq` already seen but **different content** → `R_DUP` (possible attack) → drop + alert.
- `seq` > expected → accept, record **gap** (lost frames), alert `A_GAP`; incomplete
  messages are discarded at `T_reasm`.
- `seq` < window → `R_SEQ`.
- New `epoch` > current → reset sequence state for all flows, log `E_EPOCH`. Lower epoch →
  `R_EPOCH`.

### 5.4 Reason codes and alerts

| Code | Meaning | Default action |
|---|---|---|
| `R_L2`, `R_SIZE`, `R_CRC` | Link / size / transmission error | Drop, count; alert above threshold |
| `R_HDR`, `R_FLOW`, `R_FMT`, `R_LEN`, `R_FRAG`, `R_LABEL` | Malformed or unapproved frame | Drop, count, **alert immediately** |
| `R_KEY`, `R_MAC` | Authentication failure | Drop, count, **alert immediately** |
| `R_EPOCH`, `R_SEQ`, `R_DUP` | Replay / reordering / conflicting duplicate | Drop, count, **alert immediately** |
| `R_FORM` | Payload violates simple form | Drop, count, **alert immediately** |
| `R_REASM` | Reassembly timeout or oversize | Discard message, alert |
| `R_POLICY` | B's import policy rejects | Dead-letter on B, alert |
| `A_GAP` | Lost frames detected | Alert |
| `A_HB` | No heartbeat for 3 × `T_hb` | Alert (link or A down) |
| `C_REPEAT` | Expected repetition dropped | Count only |

Counters are kept per flow and per reason, and are part of B's audit log. Thresholds and
alert routing are B's local configuration (B's owner decides).

---

## 6. Payload — canonical simple forms

- The payload is A's **canonical write** of the approved message in a **registered simple
  form**: fixed field order, bounded lengths, no comments, no duplicate keys, no
  free-form extension fields.
- Each simple form is defined by a **BINF form-class** (preferred) or another strictly
  bounded schema, and gets a `payload_format` ID in the registry below.
- The same form-class definition is used by A's encoder and is the reference for B's
  verifier (HW9) — but B's verifier is implemented independently (principle 6).

### 6.1 Two transport modes

The payload format sets the **assurance tier**. Two modes are supported; **Mode 1 is the default.**
Both are the same two-mapping, one-way, re-validate-on-B design (zero trust holds in both); they
differ only in the *size of B's gate*, and therefore the assurance.

- **Mode 1 — simple-form transport (default, high assurance).** The payload is a **bounded, per-type
  BINF form-class** (the rows below). B's first gate is a **small, fixed-field verifier that runs in
  hardware** (HW9), so what enters the protected domain has a minimal attack surface and is
  independently *hardware-verifiable* (transform → verify). Cost: one BINF form-class must be defined
  per message type, and A maps its output UDM into that form. **This is the only mode permitted for
  high-assurance crossings.**
- **Mode 2 — UDM transport (opt-in, lower assurance).** The payload is a **generic, bounded
  serialisation of the UDM** (a canonical UDM encoding; `payload_format` 0x0200), so no per-type form
  need be defined and A may carry an arbitrary-shaped output UDM. Trade-off: B must run a
  **general-purpose UDM parser** — software only; it **cannot be hardware-verified**, because arbitrary
  structure cannot be checked in fixed logic — a materially larger attack surface on the protected
  side. B still bounds it (depth / size / node caps, per the guard parser profile) and still
  re-applies its import policy, **but the assurance tier is lower.** Permit **only** where the threat
  model allows a general parser on the receiver (intra-zone, same-owner, lower-differential, or
  prototyping); **never for high-assurance crossings.**

*Note on latency:* Mode 1 is usually also the **faster** mode — a fixed record verifies at line rate
in the FPGA and parses with no branching, whereas a generic UDM stream is variable-size and
software-only. **Do not** choose Mode 2 for throughput; choose it for *flexibility / less setup*, and
accept the lower assurance.

| `payload_format` | Simple form | Defined in | Notes |
|---|---|---|---|
| 0x0000 | none | — | Heartbeat only |
| 0x0001 | *example:* position report (fixed binary) | `forms/udm-pos-v1` (BINF) | **Mode 1.** Bounded fields, no strings |
| 0x0002 | *example:* track report (fixed binary) | `forms/udm-track-v1` (BINF) | **Mode 1.** Bounded array of positions |
| 0x0100 | *example:* canonical JSON profile, bounded | `forms/json-bounded-v1` | **Mode 1.** Allowed only if B's verifier supports it; prefer binary simple forms |
| 0x0200 | UDM transport (generic canonical UDM) | `forms/udm-stream-v1` | **Mode 2 — lower assurance.** General parser on B, software-only verify, bounded by the guard profile; **not hardware-verifiable**; not for high-assurance crossings |

(Entries are placeholders until the flows are agreed.)

**Labels:** if the flow is labelled, the handling label travels inside the payload as a
field of the simple form, and `label_digest` = SHA-256 of its canonical encoding. B
recomputes and compares (HW8/SW2).

### 6.2 Form-class type library — elements, composites and message forms

A simple form such as `udm-pos-v1` is **not** written from scratch each time. Form-classes are
built from a small, shared **type library**, exactly the way a trading floor keeps one dictionary
of field, type and product definitions and composes each instrument's message from it. This keeps
the forms consistent, cheap to add, and — importantly here — cheap to **verify in hardware**.

**The three layers.** A form-class is composed, not invented:

```
 Elements     bounded primitives, defined once:
              fixedpoint-lat, fixedpoint-lon, u32-bounded, utc-millis,
              enum{…}, utf8(maxlen)                               ← the shared catalogue

 Composites   reusable groups built from elements:
              Position = { lat, lon, alt, t }     Identity = { id, kind }

 Message      concrete, CLOSED compositions — one per payload_format:
 forms        udm-pos-v1   = Identity + Position
              udm-track-v1 = Identity + bounded-array<Position, N>
```

Each **message form** (the thing with a `payload_format` ID in §6) is a *fixed, closed*
composition of composites; each composite is a fixed group of elements; each element is a bounded
primitive. Nothing in a message form is open-ended.

**The one rule that governs everything — share *definitions*, never *variability*.** There are two
things people call "derive from a super-schema", and only one of them is safe on this link:

| | What it means | Allowed on the interlink? |
|---|---|---|
| **Share definitions** (authoring-time reuse) | a message form *references* shared elements/composites; once composed it is a **fixed, closed** shape | ✅ yes — this is the whole point of the library |
| **Share variability** (runtime polymorphism) | a "super form" carries a union / any-of / optional subset that the receiver resolves at runtime | ❌ no — a variable shape cannot be verified in fixed FPGA logic (HW9) |

So the library is a set of definitions you *compile against*, not a runtime union a message *selects
from*. Reuse happens when the forms are authored; every form on the wire is still one fixed shape.

**Subsets and "lite" variants** follow directly: a smaller `udm-pos-lite-v1` that uses only some of
`Position`'s fields is simply **another closed message form** composed from the same library, with
its own `payload_format` ID and version. It is *not* `udm-pos-v1` sent with fields omitted — because
optional/variable-length layout reintroduces the very variance HW9 cannot check, and it is
ambiguous (is a missing field *absent* or *unknown*?). Where a field may or may not be present,
make presence **explicit inside a fixed layout** (an always-present field with a defined
null/sentinel), or split into two forms (`…-2d-v1` / `…-3d-v1`). **Optionality and variance are the
enemy of hardware verification; push all variance into *which named form*, never into *the shape of
a form*.**

**Versioning.** Version the *composites*, and have each message form **pin** a composite version
(`udm-pos-v1` embeds `Position-v1`). Changing `Position` → `Position-v2` then affects only the forms
you deliberately re-cut; everything else is untouched. Adding or re-cutting a form is a
configuration change under §10 (both owners sign), never a `version` bump of the frame itself.

**Why this also shrinks the silicon.** Because `Position` is defined once and embedded by many
message forms, B's verifier (HW9) implements **"verify a Position block" once** and reuses that
logic across `udm-pos-v1`, `udm-track-v1`, and every other form that embeds it. The type library
is not just an authoring convenience — shared composites become **shared verification primitives in
gates**, so the hardware verifier stays small and composable as flows are added.

**Prior art (this is a solved problem).** The pattern is proven exactly where low latency and fixed
wire shapes meet:

- **SBE (Simple Binary Encoding)** — the FIX community's low-latency encoding: a schema of shared
  types/enums/composites compiled into **fixed-layout binary messages** for zero-copy / FPGA-friendly
  decode. This is the closest match to what the interlink wants.
- **ASN.1 modules + PER/UPER** — type modules composed into messages, packed into a compact binary
  with a fixed layout. The other strong match.
- **ISO 20022 / FpML** — excellent *modelling* discipline (one dictionary of components, messages
  composed from it) but **self-describing on the wire** (XML, polymorphic). Keep their modelling
  discipline; **do not** adopt their wire form here.
- **FIX tag=value** — a shared dictionary but a variable, self-describing wire: a good example of
  what the interlink must *not* look like.

**Governance.** The type library and every message form are **write-once, human-authored,
design-time artefacts**, reviewed and locked into both halves and into B's independent verifier
(principle 6). Nothing is generated on the fly: an unprovisioned message type is rejected (`R_FORM`),
or carried over the Mode-2 generic UDM stream (§6.1) at the lower assurance tier. Schema-from-samples
(UTL-X Infer, `%utlx 2.0`) may *draft* a form in developer tooling, but the output is reviewed,
corrected and locked by a human before it is provisioned — never a runtime function of the guard.

> **In one line:** one shared catalogue of bounded *elements* and reusable *composites*, and N thin,
> closed, versioned *message forms* composed from it (SBE / ASN.1-style). Reuse at authoring time;
> every form on the wire is a fixed, hardware-verifiable shape.

---

## 7. Security considerations

### 7.1 Threats addressed

| Threat | Mitigation |
|---|---|
| Network attack via the interlink | No IP/TCP stack on the interlink; L2 filtering; protocol break in FPGA |
| Malformed / oversized frames | Fixed layout, hard size limits, HW1–HW5 |
| Forged or modified frames | HMAC-SHA-256 (HW6) |
| Replay | `epoch` + `seq` + duplicate window (HW7) |
| Smuggled content | Simple-form verification in hardware (HW9), then B's own policy (SW2) |
| Compromised half A | B re-verifies everything independently; A cannot send anything B's verifier and policy don't accept |
| Return channel B → A | Physically absent (one strand / diode) |

### 7.2 Keys

- One HMAC key per direction, identified by `key_id`; **two keys may be valid** during
  rotation.
- Keys are generated and loaded by a procedure both owners agree on (key ceremony, HSM or
  token); never transported over the interlink.
- The MAC provides **integrity and authenticity, not confidentiality**. If the interlink
  leaves a shared secure space, an approved link encryptor is required in addition.

### 7.3 Covert channels

If A is on the **higher** classification side (export direction), the *timing* of frames
and heartbeats could leak information. Options, chosen per accreditation:
- **constant-rate mode**: A sends frames at a fixed rate, padding with heartbeats or
  dummy frames, so timing carries no information;
- coarse heartbeat jitter; bounded queue depth.
The low → high direction (import) is less sensitive to timing channels.

### 7.4 What this ICD does not cover

- Accreditation of either half.
- The policies themselves (A's export rules, B's import rules) — owned by each side.
- Physical security of the fibre and enclosures.

---

## 8. Two-way operation (optional)

Two-way traffic uses **two independent simplex links**, each with its own instance of this
ICD (own flow table, keys, epochs and verifier):

```
 A ──(link A→B, ICD instance 1)──▶ B
 A ◀──(link B→A, ICD instance 2)── B
```

Request/response correlation is a field **inside the payload** (e.g. a correlation ID in the
simple form), never a session in the protocol. Each direction is verified by the receiving
half exactly as in §5.2.

---

## 9. Configuration (per ICD instance)

### 9.1 Link parameters

| Parameter | Default | Notes |
|---|---|---|
| `l2_profile` | E (prototype) / R (product) | §3.2 |
| `max_frame` | 8,192 B | Fixed in v1 |
| `T_hb` | 100 ms | Heartbeat interval |
| `hb_loss_factor` | 3 | `A_HB` after 3 × `T_hb` |
| `R` (repetition) | 2 | Per flow override allowed |
| `W` (dup window) | 1,024 frames | Per flow |
| `T_reasm` | 500 ms | Reassembly timeout |
| `constant_rate` | off | §7.3 |

### 9.2 Flow table (example)

| `flow_id` | Purpose | Contract (A export / B import) | `payload_format` | `max_payload` | `max_frag` | Rate limit | Labelled |
|---|---|---|---|---|---|---|---|
| 0x0001 | Track reports | `mc-track-export-v1` / `mc-track-import-v1` | 0x0001 | 512 B | 1 | 2,000 msg/s | yes |
| 0x0002 | AIS positions | `mc-ais-export-v1` / `mc-ais-import-v1` | 0x0002 | 256 B | 1 | 5,000 msg/s | no |

### 9.3 Illustrative configuration file (shared, signed by both owners)

```yaml
interlink:
  icd: interlink-protocol-v1
  instance: A-to-B
  l2_profile: E            # raw Ethernet for the ZCU106 prototype
  ethertype: 0x88B5
  mac_src: "02:00:00:00:00:0A"
  mac_dst: "02:00:00:00:00:0B"
  heartbeat_ms: 100
  repetition: 2
  dup_window: 1024
  reassembly_timeout_ms: 500
  constant_rate: false
  keys:
    accepted_key_ids: [1]
  flows:
    - id: 0x0001
      payload_format: 0x0001
      max_payload: 512
      max_frag: 1
      rate_msgs_per_s: 2000
      labelled: true
      contract_export: mc-track-export-v1
      contract_import: mc-track-import-v1
```

Each half also has **local** configuration that the other owner does not control (its own
policy, alert routing, logging). Only the shared file above is jointly owned.

---

## 10. Change control and versioning

- This ICD and the shared configuration are **signed by both owners**; any change needs both
  signatures.
- `version` increments for any change to the frame layout or semantics; B rejects unknown
  versions, so a version change requires coordinated deployment.
- Adding a flow or simple form = configuration change (both signatures), no version change.
- B's verifier is re-validated against the conformance tests (§11) after every change.

---

## 11. Conformance test cases (minimum set)

| # | Test | Expected at B |
|---|---|---|
| T01 | Valid single-fragment DATA frame | Accepted, delivered after policy |
| T02 | Valid multi-fragment message | Reassembled, delivered |
| T03 | Valid HEARTBEAT | Accepted, no output |
| T04 | Wrong `magic` / `version` / `type` | `R_HDR` |
| T05 | Non-zero `reserved` | `R_HDR` |
| T06 | Unknown `flow_id` | `R_FLOW` |
| T07 | `payload_format` ≠ flow's format | `R_FMT` |
| T08 | `payload_len` > flow max; frame length ≠ 108 + `payload_len` | `R_LEN` |
| T09 | `frag` ≥ `frag_count`; `frag_count` > `max_frag` | `R_FRAG` |
| T10 | Corrupted payload byte, CRC recomputed | `R_MAC` |
| T11 | Corrupted byte, CRC not recomputed | `R_CRC` |
| T12 | Unknown `key_id` | `R_KEY` |
| T13 | Byte-identical repetition | `C_REPEAT` (silent) |
| T14 | Same `seq`, different content | `R_DUP` + alert |
| T15 | `seq` jump (frames lost) | Accepted + `A_GAP` |
| T16 | Old `seq` outside window | `R_SEQ` |
| T17 | Lower `epoch` | `R_EPOCH` |
| T18 | Higher `epoch` | Sequence reset, `E_EPOCH` logged |
| T19 | Missing fragment | `R_REASM` after `T_reasm` |
| T20 | `label_digest` non-zero on unlabelled flow (or zero on labelled) | `R_LABEL` |
| T21 | Payload violates simple form (out-of-range field, wrong length) | `R_FORM` |
| T22 | Valid frame, B's import policy rejects | `R_POLICY`, dead-letter |
| T23 | Frame < 108 B or > 8,192 B; wrong EtherType/MAC | `R_SIZE` / `R_L2` |
| T24 | Ethernet pause frame / VLAN-tagged frame | `R_L2` (never acted on) |
| T25 | Heartbeats stop | `A_HB` after 3 × `T_hb` |
| T26 | Fuzzing: random and mutated frames (≥ 10⁸ cases) | No crash, no hang, every frame either accepted-valid or rejected with a reason code |
| T27 | Throughput at line rate with maximum-size frames | No unintended drops; counters consistent |

---

## 12. Open questions

- Interlink line rate on very small designs: is 1000BASE-X (auto-negotiation disabled) ever preferable to 10GBASE-R (§3.3)?
- Profile R (Aurora 64B/66B simplex): confirm core version, licensing and one-strand
  behaviour on the chosen transceivers.
- Maximum frame size: is 8,192 B right, or should flows with large messages use a larger
  fixed maximum?
- MAC algorithm: HMAC-SHA-256 vs. a national/NATO-approved alternative required by the
  accrediting authority.
- Constant-rate mode: required for the intended crossing (export direction)?
- Which simple forms first, and who writes B's independent verifier?
- Should `ruleset_id` on B be checked against an expected value (strict coupling) or only
  logged (loose coupling)?

---

## Sources and references

- Everfox (ex-Forcepoint) High Speed Verifier 2 datasheet — simple protocol over raw
  Ethernet; data rebuilt from verified simple data.
- Arbit Data Diode whitepaper — transaction control and forward error correction on a
  one-way link.
- IEEE 802 local experimental EtherTypes (0x88B5 / 0x88B6).
- AMD PG074, Aurora 64B/66B LogiCORE IP Product Guide (simplex operation) — verify for the
  chosen version.
- NATO STANAG 4774 / 4778 (labels and binding) — see `UDM-content-guard.md` §5a.
