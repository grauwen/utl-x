= Appendix B: Format & Encoding Catalogue

_Every format in the civilian scope, by encoding band, with the Mapper mechanism it needs. All are
publicly specified. "Mechanism" is what the engine does to read it._

== Binary → BINF codec + form-class

#table(
  columns: (auto, 1fr),
  [AIS], [6-bit-packed binary (NMEA armour)],
  [ADS-B (1090ES)], [fixed 112-bit frames + 24-bit CRC],
  [ASTERIX], [binary, FSPEC presence bitmaps],
  [MISB KLV ST 0601], [KLV (byte-structured, self-tagged)],
  [DIS], [binary PDUs, fixed layouts],
)

== XML family → native XML reader (mapping only)

#table(
  columns: (auto, 1fr),
  [Cursor-on-Target (CoT)], [XML (TAK also Protobuf)],
  [CISE], [XML],
  [C2SIM], [XML (ontology-based)],
  [IALA IVEF], [XML],
  [IHO S-100 (GML part)], [GML (= XML)],
)

== JSON-alike → native reader (mapping only)

#table(
  columns: (auto, 1fr),
  [JSON / YAML / OData], [structured text — the modern-IT side of a bridge],
  [Protobuf (incl. TAK)], [byte-aligned, schema-driven],
)

== EDI / delimited → EDIFACT-class reader

#table(
  columns: (auto, 1fr),
  [UN/EDIFACT], [segment / element delimited],
  [EMSWe], [XML + EDIFACT (XML → native, EDI → EDIFACT reader)],
  [RIS], [umbrella: AIS + EDIFACT + XML (routes by sub-format)],
)

== Code systems → stdlib lookup (not a codec)

#table(
  columns: (auto, 1fr),
  [APP-6 / MIL-STD-2525], [symbol identity codes (SIDC) — a public lookup/enum used inside a mapping],
)

== Out of core scope

#table(
  columns: (auto, 1fr),
  [IHO S-100 (ISO 8211 / HDF5)], [complex random-access containers — library-backed, not a form-class],
  [HLA (RTI)], [a simulation runtime API, not a wire format — bridge DIS instead],
)

== What the engine already has vs. must build

#table(
  columns: (auto, auto, 1fr),
  [*Capability*], [*Status*], [*Serves*],
  [XML reader/writer], [exists], [CoT, CISE, C2SIM, IVEF, GML],
  [JSON / YAML / Protobuf / OData], [exists], [modern-IT bridge, TAK Protobuf],
  [BINF codec + form-class], [new], [all binary (AIS, ADS-B, ASTERIX, KLV, DIS)],
  [EDIFACT-class reader], [new], [EDIFACT, EMSWe-EDI, RIS-EDI],
  [Code-set lookup (stdlib)], [partial], [APP-6 / 2525],
)
