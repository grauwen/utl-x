= Assurance, Deployment, and the Path to Market

The civilian guard's advantage is that it can be built and fielded now. This chapter is how: the assurance
route, the hardware (mostly none), the roadmap, and the legal frame.

== Assurance — climb in evidence first, then credentials

A serious buyer asks for evidence before certificates. So the guard climbs evidence first, and each step
produces evidence the MIL track later reuses — the civilian route is the first half of the MIL route, not
a detour.

#table(
  columns: (auto, 1fr),
  [1 — Engineering evidence], [guard-profile fuzzing (coverage-guided, grammar-based, differential), OWASP/CRS payloads as a test corpus, reproducible native build, SBOM — the baseline any serious buyer asks for],
  [2 — Independent pen test + code review], [first external evidence],
  [3 — Secure development process], [ISO/IEC 27001 for the organisation; *IEC 62443-4-1/-4-2* for OT buyers — opens critical-infrastructure],
  [4 — NL: BSPA], [Baseline Security Product Assessment (NBV), aimed at use up to Departementaal Vertrouwelijk / EU RESTRICTED — the first government credential],
  [5 — Common Criteria], [a modest level (e.g. EAL2–EAL4+ with a guard-specific security target) for international recognition],
  [6 — → MIL edition], [national high-assurance evaluation, NATO, and partners for the US NCDSMO route],
)

_(Verify current BSPA scope and procedure with the NBV/NLNCSA before planning; levels above are
indicative.)_

== Hardware — mostly none

Civilian buyers run the guard as *software*; hardware climbs only where assurance or OT environments
require it. No rugged defence hardware is needed for the civilian edition.

#table(
  columns: (auto, 1fr),
  [*Software product*], [x86 or ARM server, VM or container (Open-M `mode: component` pod); GraalVM-native binary — shapes A and D],
  [*Diode-adjacent*], [a server beside a certified diode (Fox, Arbit, Owl, Waterfall) — shape B; the diode carries flow assurance],
  [*Demonstrator*], [two SoC-FPGA boards as a split guard — public formats only, already civilian],
  [*Appliance (later)*], [a small fanless box or a multi-guard chassis for OT and multi-flow sites],
)

#figure(
  image("../pictures/utlx-guard-2U-12slot-300dpi.png", width: 92%),
  caption: [Illustrative appliance concept for multi-flow / OT sites: a 2U chassis in which every slot is a complete, independent guard on a power-only backplane — no shared data lanes. A later option, not needed for the software product.],
)

#figure(
  image("../pictures/utlx-guard-module-swap-transparent-300dpi.png", width: 100%),
  caption: [Hot-swap serviceability of the appliance concept: a single guard card withdrawn from the front. All data enters and leaves on the card itself; the power-only backplane means removing or inserting a card never bridges two networks. Slots mix send and receive cards.],
)

== A buildable demonstrator — proof of concept

The whole civilian story can be *demonstrated now*, on public hardware and public data, because
nothing in it is restricted. The demonstrator is a *split guard* (shape D) built from two SoC-FPGA
evaluation boards (for example two ZCU106): each board is one owner's half, running the UTL-X guard
over a one-way fibre interlink — and where request/response is needed, it is built as two separate
one-way flows, never a session through the guard.

#figure(
  image("../pictures/utlx-split-guard-two-racks-300dpi.png", width: 100%),
  caption: [The split guard (shape D) fielded: each half in its own rack, zone and ownership (A / B), enforcing its own policy and meeting only over a one-way fibre interlink. The proof of concept builds exactly this on two SoC-FPGA boards with public formats — already civilian.],
)

*What it proves:* the full pipeline — hardened parse → UDM → `validate.*` → canonical re-serialize —
running end to end across an owned boundary; two-owner independence (each half enforces its own
policy); a real protocol break on the interlink; and binary coverage, by decoding a public binary
sample (AIS or ADS-B) through BINF on the FPGA fabric.

*What it does not prove, honestly:* TEMPEST, certified crypto, a certified diode and scaling to
line-rate production are *investment*, not a weekend — and for the civilian software product they are
not needed at all. The civilian edition ships as *software*; the demonstrator exists only to make the
pipeline and the two-owner split visible and testable, not to field rugged hardware.

== Roadmap and effort

Order of magnitude, one experienced developer:

#table(
  columns: (auto, auto, 1fr),
  [*0 — Guard core*], [8–14 pw], [guard-profile parsers, canonical serializer, the seven rule categories, audit, dead-letter, no-pass-through — a software guard for JSON/XML/CSV/YAML/OData],
  [*1 — Foundation*], [15–23 pw], [BINF, streaming runtime, transports, tests — a binary-capable engine],
  [*2 — Tier A packs*], [18–30 pw], [AIS/NMEA, KLV, EDIFACT, CoT, CISE, geometry — coast-guard and dual-use scope],
  [*3 — Tier B packs*], [16–27 pw], [ASTERIX, RIS, IVEF, S-100 products, DIS, C2SIM, Cospas-Sarsat — full open scope],
  [*4 — Productisation*], [6–10 pw], [contract/policy UI/CLI, monitoring, packaging, docs — a sellable product],
  [*5 — Assurance*], [external], [fuzzing campaign, pen test, 62443/BSPA preparation],
)

Order inside the phases follows the chosen wedge: a CISE-first wedge pulls CISE and AIS forward; an
OT-first wedge prioritises guard core plus shape-B integration with one diode vendor. Full open scope is
roughly 63–104 person-weeks — about 15–24 months for one developer, roughly half with two.

== Legal, licensing, export

#table(
  columns: (auto, 1fr),
  [*Licensing*], [open-core: engine and public packs under AGPL-3.0; guard productisation, certified builds, support and policy tooling commercial. Offer a commercial licence where AGPL is unacceptable to a buyer.],
  [*Export control*], [open standards remove the restricted-standards problem; cryptographic functions (TLS, interlink HMAC, label signatures) may still fall under EU dual-use rules — far lighter than ITAR, but check before the first sale outside the EU],
  [*No restricted content*], [the engine stays content-free; restricted form-classes never enter the civilian repository],
)

== Bottom line

A civilian, open-standards guard can be built and fielded *now*, without a sponsor or restricted access,
on the same engine and architecture as the MIL guard. It creates customers, references and assurance
evidence — exactly what the MIL track lacks today. Build it first, keep the six non-negotiables and the
verdict policy uncompromised, and the MIL edition becomes an extension rather than a separate bet.
