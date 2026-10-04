= Appendix B: Open-Format Catalogue

_Every format in the civilian scope, by encoding, with the mechanism it needs. All are publicly specified;
nothing here is restricted. "Mechanism" is what the engine does to read it._

== Text-structured → native readers (mapping only)

#table(
  columns: (auto, 1fr),
  [JSON / YAML], [native — the modern-IT side of most bridges],
  [XML (incl. GML)], [native — CISE, IVEF, S-100 products, CoT, C2SIM, labels],
  [CSV], [native — OT historian exports, tabular feeds],
  [OData], [native — government data services],
  [Protobuf / Avro], [native (schema) — TAK Protobuf and data pipelines],
)

== Schema / metadata → native

#table(
  columns: (auto, 1fr),
  [XSD, JSON Schema], [structural conformance (`validate.conformsTo`)],
  [OData EDMX, Table Schema], [service and tabular contracts],
)

== Binary → BINF codec + form-class

#table(
  columns: (auto, 1fr),
  [AIS (NMEA 0183)], [6-bit-packed binary; the ideal first binary pack],
  [ADS-B (1090ES)], [fixed 112-bit frames + 24-bit CRC],
  [ASTERIX], [binary, FSPEC presence bitmaps — the variable-structure stress test],
  [MISB KLV ST 0601], [KLV byte-structured, self-tagged — FMV metadata],
  [DIS], [binary PDUs — simulation],
)

== EDI / delimited → EDIFACT-class reader

#table(
  columns: (auto, 1fr),
  [UN/EDIFACT], [ports, customs, logistics],
  [RIS (ERI, NtS, Inland AIS)], [inland shipping — routes by sub-format],
)

== Code systems / labels

#table(
  columns: (auto, 1fr),
  [APP-6 / MIL-STD-2525 (SIDC)], [symbol identity codes — a lookup, not a codec],
  [STANAG 4774 / 4778 (public)], [confidentiality label syntax + binding — XML + `validate.*`],
)

== Out of scope (MIL edition)

Link 16 J-series, JREAP-C, VMF, Link 22, and any standard of unclear release status — separately governed
packs, never in the civilian repository.
