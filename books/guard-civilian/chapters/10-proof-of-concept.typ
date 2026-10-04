= Proof of Concept — the Split Guard

Everything in this book is a claim until it is built. This chapter is a concrete, buildable proof of
concept — deliberately small, on *public hardware and public data* — that exercises the whole civilian
guard story at once, followed by an honest statement of where a demonstrator stops and a funded step
begins. Because nothing in the civilian edition is restricted, the demonstrator needs no sponsor and no
clearance: it can be built now.

== The demonstrator: a split guard on two boards

The guard's strongest architecture is *transform → verify*: software turns complex input into a simple,
strongly-typed form; independent logic verifies that form; the delivered message is rebuilt only from
verified data. The proof of concept makes this physical by *splitting the guard across two boards* joined
by a one-way link — the smallest version of the two-owner split guard (shape D).

#table(
  columns: (auto, 1fr),
  [*Board A — ingest / transform*], [receives several formats; runs UTL-X on the Arm cores to parse → UDM → `validate.*` → re-serialize to a *simple, strongly-typed form*; uses the FPGA fabric to decode a binary sample (BINF) and enforce structural caps],
  [*Link — protocol break*], [a fibre SFP+ connection carrying only the simple form; made physically one-way it emulates the diode / protocol break — no TCP/IP state crosses],
  [*Board B — verify / egress*], [FPGA fabric *verifies* the simple form against the form-class / schema; the Arm side rebuilds and forwards only verified data; anything failing → dead-letter (fail-closed)],
)

Two identical *Xilinx ZCU106* boards (Zynq UltraScale+ MPSoC: Arm cores + FPGA fabric on one chip) are
the split pair.

#block(fill: luma(245), inset: 8pt, radius: 4pt, width: 100%)[
  *Board note (honest).* The cheaper *ZCU104* or *Kria KR260* are an equally good fit; two ZCU106 are
  used here simply as available, identical SoC-FPGA boards. The *split* — transform on one chip, verify
  on the other — is the point, not the exact board.
]

== Architecture

#figure(
  image("../pictures/diagrams/split-guard.svg", width: 100%),
  caption: [The split guard: Board A transforms any input format into a simple, strongly-typed form; a protocol-break link carries only that form; Board B independently verifies it and forwards only verified data. Anything failing is dead-lettered (fail-closed).],
)

The Arm side runs the existing GraalVM-native UTL-X binary (`linux-aarch64`) unchanged — no new port. In
deployment this same split becomes the two-owner guard pair, meeting over fibre (multimode in a building,
single-mode or dark fibre between sites), with one-way enforcement carried in the optics:

#figure(
  image("../pictures/utlx-split-guard-two-racks-300dpi.png", width: 100%),
  caption: [The split guard (shape D) fielded: each half in its own rack, zone and ownership (A / B), enforcing its own policy and meeting only over a one-way fibre interlink. The proof of concept builds exactly this on two SoC-FPGA boards with public formats — already civilian.],
)

== What it demonstrates

- *Universal parsing:* JSON, XML, CSV and a *binary* AIS/ADS-B frame all reduced to one UDM.
- *One rule set, many formats:* the same `validate.*` block (structural caps, schema conformance, value
  ranges, labelling) applied across all of them.
- *BINF in fabric:* a fixed binary frame decoded + CRC-checked in FPGA logic at the edge.
- *Allow-list / fail-closed:* only conforming, approved messages reach Board B; the rest are
  dead-lettered.
- *Transform → verify:* Board A transforms; Board B *independently* verifies a simple form; delivery is
  rebuilt from verified data.
- *Protocol break:* no transport state crosses the link; two-way traffic would be two separate one-way
  flows.

== Success criteria (indicative)

1. The *same* rule block passes a valid JSON position report and a valid AIS frame, and produces
   identical downstream output (e.g. CoT / JSON).
2. An over-limit message (depth/size bomb), a message with a forbidden field, and a message failing a
   label check are each *blocked, fail-closed*, with an audit record (`REJECT`).
3. A public AIS frame is decoded in Board A's fabric and mapped to CoT / JSON.
4. A *tampered* simple form is *rejected by Board B's verifier* (not by Board A) — proving the
   independence of the verify stage.
5. End-to-end latency and a rough throughput figure are measured — *indicative only*.

== Scope and effort

- *Formats:* public only — JSON, XML, CSV, and public *AIS/ADS-B*.
- *Rules:* a small policy from the seven categories of Chapter 8, bound to one approved contract.
- *Effort:* order of weeks, one engineer familiar with UTL-X plus a Vitis/HLS starting point; reuses the
  existing native binary and the eval-board toolchain.
- *Indicative hardware:* two ZCU106 (or, cheaper, two ZCU104 / KR260), plus a host PC.

== What this PoC deliberately does NOT do

A capability and architecture demonstrator, *not* a product. It excludes operational throughput, a
rugged/appliance form, redundancy; a certified crypto / data diode (the protocol break is *emulated*);
and any formal assurance (no IEC 62443, BSPA, or Common Criteria yet). Note what it does *not* need, which
the MIL edition does: *no TEMPEST, no emission-security facility, no NATO/NCDSMO campaign.* The civilian
scale-up is a far smaller climb.

== The investment case (civilian)

Moving from this demonstrator to a sellable civilian product is *investment, but not a mountain* — and,
critically, it is funded by the first wedge customer rather than by a multi-year accreditation programme.
The scale-up is three things, none of them defence hardware:

#table(
  columns: (auto, 1fr),
  [*Assurance*], [the evidence ladder of Chapter 9 — fuzzing campaign, independent pen test, then IEC 62443-4-2 (OT) or BSPA (government) as the first credential the wedge customer requires],
  [*Productisation*], [contract and policy tooling (UI/CLI), monitoring, packaging, docs — turning the engine into something an operator runs],
  [*Scale & integration*], [line-rate throughput where needed, and a *certified diode partner* for shape B (the diode carries flow assurance; UTL-X carries content) — not bespoke silicon],
)

Contrast the MIL edition, whose scale-up *is* a mountain: TEMPEST, certified high-assurance crypto, a
certified verifier, rugged SOSA hardware, and a multi-year national/NATO accreditation campaign. The
civilian product reaches revenue first, on software, and *funds* that later climb.

== Start with a proof of concept

If this is a fit for a boundary you own, the right first step is small and concrete — a *proof of concept
on your terms*.

*The offer.* A four-to-six-week joint PoC: we take one of your flows (or a public dataset standing in for
it), build the split guard on two public eval boards, run your candidate rules, and show it end to end.

*What you get:*

- a working split-guard demonstrator on *your* use case;
- a verdict log — `PASS` / `PASS-STRIPPED` / `REJECT` — over real or representative messages;
- measured end-to-end latency and a rough throughput figure;
- a short written *go / no-go*, mapping the approach to your formats, your rules and your wedge.

*Why it is low-risk.* Weeks, not years. Public hardware you keep; your data stays yours. No sponsor, no
restricted access, no accreditation commitment — it answers the feasibility question *before* any funded
programme.

*To start,* bring one flow, its formats, and a sketch of what "good" looks like (a candidate contract).
From there the path is the roadmap of Chapter 9: pick the wedge, climb the assurance ladder, and let the
civilian references fund the MIL edition.

#v(0.6cm)
#align(center)[
  #block(fill: luma(244), inset: 14pt, radius: 6pt, width: 82%, stroke: 0.5pt + rgb("#CC0000"))[
    #align(center)[
      #text(size: 12pt, weight: "bold")[Reach out — commission a proof of concept]
      #v(0.35cm)
      Ir. Marcel A. Grauwen · GLOMIDCO B.V.
      #v(0.15cm)
      #link("mailto:marcel.grauwen@glomidco.com")[marcel.grauwen\@glomidco.com]
      #linebreak()
      #link("https://www.linkedin.com/in/marcelgrauwen")[linkedin.com/in/marcelgrauwen]
    ]
  ]
]
