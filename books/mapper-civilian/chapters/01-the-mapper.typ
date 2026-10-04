= The UTL-X Mapper

A modern organisation runs on data that will not line up. One system speaks one format, the data
platform another, a partner agency a third, and the cloud analytics stack a fourth. Between every
pair sits a translator — and today that translator is almost always bespoke code, written once,
understood by one team, and brittle the day a standard revises. The UTL-X Mapper is that
translator, made declarative, auditable, and assured.

This is a short book, on purpose. If you want the full language, read _UTL-X: One Language, All
Formats_. If you just want to understand UTL-X *as a mapping component* — what it does, what it
maps, how it deploys, and why its output can be trusted — this is the on-ramp.

== What it is

The Mapper is a *message-mapping component*: it takes a message in one format, turns it into a
single internal model, and writes it out in another — correctly, repeatably, and provably. It
reads the binary and the textual, the modern and the legacy, across every open standard, and maps
between any of them with one language and one model.

#align(center)[
  *parse any format → one model (UDM) → validate → emit any format*
]

It is built on *UTL-X 1.1*. Version 1.0 gives the language its four guarantees — pure, stateless,
deterministic, single-pass — which together make a mapping reproducible and testable. Version 1.1
adds the one thing a serious mapper cannot do without: *semantic validation* (`validate.*`), so the
Mapper does not merely transform a message, it certifies that the result conforms to its contract
before it is allowed out. That is the difference between a converter and an *assured* converter.

== At a glance

#table(
  columns: (auto, 1fr),
  [*What it does*], [maps a message from any supported format to any other, through one canonical model, with validation on the way out],
  [*Built on*], [UTL-X 1.1 — pure/deterministic transformation (1.0) plus semantic validation (`validate.*`, 1.1)],
  [*Formats*], [text and binary; open standards — JSON/XML/CSV/YAML/OData, EDIFACT, AIS, ADS-B, ASTERIX, MISB KLV, Cursor-on-Target, CISE and more],
  [*The model*], [one Universal Data Model (UDM): scalars, objects, arrays, binary, date-time, with data `.`, attribute `@`, and metadata `^` accessors],
  [*How it ships*], [as a software component — VM, container, Kubernetes workload, or a Dapr sidecar — embeddable in a pipeline or a service mesh],
  [*Connects via*], [any message bus or endpoint — Kafka, Pulsar, AMQP, JMS, MQTT, DDS, files — through the Dapr component model; fire-and-forget on a bus, or request/response as an API filter],
  [*What makes it assured*], [contract validation before emit; deterministic, reproducible, auditable output; open standards; and no AI in the mapping path],
)

== The Mapper and the Guard

This book has a companion, _UTL-X Guard — The Civilian Edition_. The two share an engine but answer
different questions. The Guard asks *"may this data cross?"* — it is a security device on a
boundary, allow-list and fail-closed. The Mapper asks *"what does this data become?"* — it is an
integration component in a pipeline, turning one system's output into another's input.

#table(
  columns: (auto, 1fr),
  [*The Guard*], [security boundary · allow-list · fail-closed · inspects and blocks · "may it cross?"],
  [*The Mapper*], [integration pipeline · transform · validate · converts and delivers · "what does it become?"],
)

They are deliberately built from the same parts — the same UTL-X engine, the same UDM, the same
binary codec, the same `validate.*`. A customer who fields the Guard already has the Mapper inside
it; a customer who fields the Mapper can harden it into a Guard. This book is about the integration
half of that story.

== What the rest of the book covers

Part I is the product. Chapter 2 explains why mapping is the integration bottleneck and what a
declarative mapper changes. Chapter 3 surveys what the Mapper reads and writes — the open-standards
format landscape. Chapter 4 shows how the component is deployed.

Part II is the mechanism. Chapter 5 is the language and the model; Chapter 6 is how every format,
including bit-level binary, becomes one tree; Chapter 7 is assured validation on UTL-X 1.1;
Chapter 8 is message contracts and the authoring workflow; Chapter 9 walks the standards in depth.
The appendices give a language quick reference, a format catalogue, and sources.
