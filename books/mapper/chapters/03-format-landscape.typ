= What It Maps — the Format Landscape

The Mapper's reach is defined by the formats it reads and writes. This chapter surveys that
landscape. The organising idea is deliberately counter-intuitive: formats are grouped not by
*domain* — maritime, air, logistics — but by *encoding*, because the encoding, not the mission,
decides what the Mapper has to do with a message.

== Encoding decides the mechanism

A domain grouping tells you *who* uses a format. An encoding grouping tells you *what the engine
must do* to read it. The single most important line runs between *binary* and *text-structured*:

#table(
  columns: (auto, auto, 1fr),
  [*Band*], [*Self-describing?*], [*Mapper mechanism*],
  [Bit-level binary], [no — needs a form-class], [BINF codec + form-class (Chapter 6)],
  [Byte-structured binary (TLV/KLV)], [partial], [BINF, byte-granular mode],
  [XML family (incl. GML)], [yes — tags in the data], [native XML reader (exists)],
  [JSON-alike (JSON/YAML/Protobuf)], [yes], [native JSON/YAML/Protobuf (exists)],
  [EDI / delimited (EDIFACT)], [yes, via grammar], [EDIFACT-class reader],
  [Code systems / vocabularies], [n/a], [stdlib lookup, not a codec],
)

The practical consequence: the whole text-structured half — every XML and JSON format — is
*mapping only*, because UTL-X already reads and writes it. The new engineering is concentrated in
one place, *BINF* (the binary half, Chapter 6), with a separate EDIFACT reader for the delimited
dialects. That is what makes the breadth below affordable: most of it is configuration, not code.

== The open formats

Everything the Mapper reads is publicly specified and buildable with no restrictions.

#table(
  columns: (auto, auto, 1fr),
  [*Format*], [*Encoding*], [*Role*],
  [AIS], [6-bit-packed binary in NMEA armour], [maritime positions; the ideal first binary pack],
  [ADS-B (1090ES)], [fixed 112-bit frames + 24-bit CRC], [air positions; type-code discriminator, CPR position],
  [ASTERIX], [binary, FSPEC presence bitmaps], [radar / surveillance; the variable-structure stress test],
  [MISB KLV ST 0601], [KLV byte-structured binary], [full-motion-video metadata; self-tagged TLV],
  [DIS], [binary PDUs, fixed layouts], [simulation record / replay],
  [Cursor-on-Target (CoT)], [XML (TAK also Protobuf)], [the canonical map-to target for decoded tracks],
  [CISE], [XML], [EU maritime surveillance data model — a flagship civilian bridge],
  [C2SIM / IVEF], [XML], [command-and-control ↔ simulation; VTS-centre exchange],
  [IHO S-100 (GML part)], [GML (= XML)], [hydrographic; GML handled as XML],
  [UN/EDIFACT, RIS], [segment/element delimited text], [ports, customs, logistics, inland shipping — huge installed base],
  [APP-6 / MIL-STD-2525], [symbol identity codes (SIDC)], [a public symbology lookup/enum, e.g. identity → CoT type],
)

The natural first bridge — and the Mapper's "hello world" — is *AIS → CoT*: decode a bit-packed
maritime position and emit it as a Cursor-on-Target event, ready for a TAK client. It exercises the
binary codec on the way in and a native reader on the way out, end to end.

== Open core

The crucial design line: the *engine is content-free*. UTL-X itself ships no domain definitions. A
binary format is a *definition pack* — a form-class file (Chapter 6) — and the publicly specified
ones (AIS, ADS-B, ASTERIX, KLV, CoT, CISE…) are open. The engine, the UDM, the BINF codec and the
native readers are open source; the commercial layer is productisation — contract and policy
tooling, support, packaging — not the engine.

#table(
  columns: (auto, 1fr),
  [*Open-core engine*], [the UTL-X 1.1 engine, UDM, BINF codec, native readers — no embedded content],
  [*Open packs*], [AIS, ADS-B, ASTERIX, KLV, CoT, CISE, EDIFACT… — publicly buildable definitions],
)

Specialised, access-restricted standards (the defence tactical data links) are not part of this
edition; they are a separately governed edition, supplied by their holder under their own access
rights, and never embedded in the open engine.

With the landscape mapped, Chapter 4 turns to how the component is deployed into a pipeline — and
Chapter 9 returns to these standards in depth.
