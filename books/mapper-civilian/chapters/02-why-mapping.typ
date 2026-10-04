= Why Mapping Is the Bottleneck

Integration is where data programmes slow down. Not because mapping is hard in principle — a field
is a field — but because mapping is done the wrong way: as bespoke code, one connector at a time,
each a small island of knowledge that decays the moment its author moves on. This chapter is the
case for doing it differently.

== The combinatorial problem

A real estate is not two systems; it is dozens. Sensors, applications, agency and partner feeds,
logistics, and the modern IT and cloud estate behind them. Every pair that must exchange data needs
a translation, and the number of possible pairs grows with the square of the systems. Built
pairwise, that is an unbounded backlog of connectors — each written, tested, and maintained on its
own.

A *canonical model* breaks the square. Map each format to one model once, and any format can reach
any other through it: $N$ readers and $N$ writers instead of $N^2$ connectors. This is the oldest
good idea in integration, and the UTL-X Mapper is built around it — the Universal Data Model of
Chapter 5.

#align(center)[
  *$N^2$ bespoke connectors → $N$ readers + $N$ writers over one model*
]

== Two bridges that recur

The combinatorial argument is abstract; the operational need is concrete. Two bridges recur:

#table(
  columns: (auto, 1fr),
  [*Binary ↔ modern IT*], [an AIS position, an ADS-B squitter, an ASTERIX plot — born in a bit-packed binary format, needed as JSON or XML in a cloud analytics platform, a dashboard, or a data lake. The Mapper decodes the binary frame and emits the modern document, and back again.],
  [*New system ↔ existing picture*], [a newly fielded sensor, service, or partner system that must join an established data picture. Rather than modify the picture, write one mapping that makes the newcomer speak its format.],
)

Both are the same operation — parse, model, validate, emit — and both are, today, usually a
hand-written adapter nobody wants to own.

== What bespoke code costs

Bespoke connector code carries four recurring costs, and each compounds over time:

#table(
  columns: (auto, 1fr),
  [*Opacity*], [the mapping logic lives in a general-purpose language, legible only to its author; an integration or compliance reviewer cannot read the intent from the code],
  [*Fragility*], [a standard revises — a new ASTERIX category, a changed message field — and the change is a code change, a rebuild, and a redeployment, not a data edit],
  [*Inconsistency*], [the same conversion written twice by two teams differs in a dozen small ways; there is no single source of truth for "how we map a position"],
  [*Unassurability*], [arbitrary code can do anything — call the network, read a file, behave differently on the thousandth message; it cannot be certified as a pure, bounded transformation],
)

== What a declarative mapper changes

The UTL-X Mapper attacks all four by making the mapping a *declarative artefact* in a purpose-built
language, not code in a general one:

#table(
  columns: (auto, 1fr),
  [*Readable*], [a mapping reads as a description of the output in terms of the input; an integrator or a reviewer reads the intent directly (Chapter 5)],
  [*Standards-as-data*], [a binary format is a `definition` file (a form-class, Chapter 6); a revised standard is an edited definition, not a rebuilt binary],
  [*One source of truth*], [one mapping per format pair, versioned and shared; "how we map a position" is a file, not a tribe],
  [*Assurable*], [the language is pure, deterministic, and single-pass — a mapping cannot reach the network or behave nondeterministically, so it can be tested exhaustively and certified (Chapter 7)],
)

== Assured is validation, not just translation

Best-effort conversion is often tolerated; a safety-, compliance-, or operations-critical feed is
not. A malformed track, an out-of-range coordinate, a mislabelled or missing field — each is an
operational error, and sometimes a compliance or security one. This is why the Mapper is built on
UTL-X *1.1*: every mapping can assert, in the same language, that its output conforms to the
receiving contract — structure, value ranges, cross-field consistency, and labelling — before the
message is allowed out (Chapter 7). The Mapper does not just translate; it refuses to emit a message
it cannot certify.

With the *why* established, Chapter 3 shows the *what*: the landscape of open formats the Mapper
reads and writes.
