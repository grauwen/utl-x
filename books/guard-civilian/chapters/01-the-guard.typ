= The UTL-X Guard

The UTL-X Guard is a *content control point for the boundary between two zones of different trust* — two
agencies, an OT network and its enterprise, a ministry and its chain partners, a coast-guard system and a
coalition picture. It inspects every message that tries to cross, passes only what policy explicitly
allows, and rebuilds it clean. It does this for *any* open message format — JSON, XML, CSV, EDIFACT, and
binary standards such as AIS, ADS-B, ASTERIX and KLV — using *one* set of rules.

This is the *civilian edition*: open standards only, software-first, sold and fielded now. It is the same
engine, the same architecture and the same non-negotiables as the defence edition (_UTL-X MIL — The
Content Guard_); the MIL edition adds restricted-format packs and higher assurance on top — not a
different product.

#block(fill: luma(245), inset: 12pt, radius: 5pt, width: 100%)[
  *At a glance*
  #table(
    columns: (auto, 1fr),
    stroke: none, inset: 4pt,
    [*What it is*], [a declarative, auditable *content guard* (the civil term is Content Disarm & Reconstruction)],
    [*How it works*], [every message is parsed to one model and *rebuilt* — never passed through — then re-serialized to the same format or a different one],
    [*What is new*], [one canonical model inspects *every* format — text and binary — with one rule set, instead of a separate guard per format],
    [*Built on*], [UTL-X, an open-source (AGPL-3.0), format-agnostic transformation language — pure, deterministic, safe on a boundary],
    [*Who it is for*], [coast guard / CISE, critical-infrastructure OT, government data sharing, air-traffic surveillance, police and crisis response],
    [*Connects via*], [any message bus or endpoint — Kafka, AMQP, MQTT, DDS, files — through the Dapr component model; one-way, fire-and-forget],
    [*Fielding*], [software gateway, diode-adjacent filter, or a split two-owner guard — on ordinary servers; hardware only where assurance or OT require it],
    [*Status*], [exploratory capability + go-to-market study; not an accredited product],
  )
]

== The problem it ends

When two zones of different trust must exchange messages, something has to look at each message and
decide whether it may cross — and rebuild it so nothing unexpected rides along. The trouble is that every
message format has needed its *own* inspector: a JSON guard, an XML guard, a guard for each binary feed.
Each is a separate piece of software, a separate attack surface, and a separate rule set to write, test
and assure. The real cost is the *multiplication* — N formats means N inspectors and N ways to be wrong.

UTL-X parses every format it reads — text or binary — into a single internal model, the Universal Data
Model (UDM). Write the inspection rules against the UDM, and the picture collapses:

#align(center)[
  *N formats → one canonical model → one rule set → one re-serializer*
]

== The six non-negotiables

"Civilian" must never mean "softer". These six rules are exactly what make the later step to MIL a matter
of *assurance and packs*, not redesign:

#table(
  columns: (auto, 1fr),
  [*No pass-through*], [every message is parsed to the UDM and re-serialized; the original bytes never reach egress — even an approved message is rebuilt, not forwarded],
  [*Allow-list, fail-closed*], [only content matching an approved Message Contract and rule set passes; everything else is blocked],
  [*Pure and deterministic*], [only UTL-X 1.0 / 1.1 in the trusted path; the probabilistic `ai.*` (2.0) is excluded],
  [*Canonical, low-fidelity output*], [comments, BOMs, padding, unknown and non-allow-listed fields are dropped on rebuild],
  [*Every verdict auditable*], [input hash, output hash, contract ID, rule-set version, rule IDs, timestamp — a reproducible record],
  [*UDM only sees what it models*], [opaque binary payloads are blocked or handed to a specialised inspector, never waved through],
)

Chapter 8 turns these into the operational *verdict policy* — rebuild what is clean, reject what is
wrong, never repair.

== How to read this book

*Part I* is the product: what the guard is (this chapter), what it does and why it is different
(Chapter 2), where it fits — markets and deployment (Chapter 3), and the open formats it speaks
(Chapter 4). *Part II* is the mechanism for the technical evaluator: the language and model, binary
decoding, validation and hardening, and the rules and verdict policy (Chapters 5–8). *Part III* is the
business: assurance route, deployment and the path to market (Chapter 9).
