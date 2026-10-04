= Open Standards — What It Speaks

The civilian edition is defined by a line drawn around *open standards only*. Everything it reads is
publicly specified and buildable without a sponsor or restricted access. That line is what lets the
product ship now; the restricted tactical data links stay in the separately governed MIL edition.

== The format scope

#table(
  columns: (auto, 1fr),
  [*Data (Tier 1)*], [JSON, XML, CSV, YAML, OData — native readers, already in UTL-X 1.0],
  [*Schema / metadata (Tier 2)*], [XSD, JSON Schema, Avro, Protobuf, OData EDMX, Table Schema — native],
  [*Maritime / coast guard*], [CISE, AIS (NMEA 0183), IALA IVEF, IHO S-100 (GML products), EMSWe, Cospas-Sarsat],
  [*Aviation / surveillance*], [ADS-B (1090ES), ASTERIX — binary, via the form-class mechanism],
  [*Ports, logistics, inland shipping*], [UN/EDIFACT, RIS (ERI, NtS, Inland AIS)],
  [*Open defence / security (dual-use)*], [MISB KLV ST 0601, Cursor-on-Target / TAK, C2SIM, DIS, APP-6 / MIL-STD-2525 symbology],
  [*Labels*], [STANAG 4774 / 4778 (public), national government classification labels — XML + `validate.*`],
)

The encoding, not the domain, decides the work. Text-structured formats (the XML and JSON families) are
already read and written by UTL-X, so for them the work is *mapping*, not decoding. Binary formats (AIS,
ADS-B, ASTERIX, KLV) go through the BINF codec and a *form-class* (Chapter 6). A dedicated EDIFACT-class
reader covers the delimited dialects.

== Dual-use ground, already covered

The open "military" formats — KLV, Cursor-on-Target, C2SIM, DIS — are *publicly available*. So the
civilian guard already covers substantial dual-use ground without touching anything restricted: police
and border-security drones, civil–military cooperation in crisis response, training and simulation. This
is the bridge toward defence that needs no defence paperwork.

== What is deliberately out of scope

#table(
  columns: (auto, 1fr),
  [*Restricted tactical data links*], [Link 16 J-series, JREAP-C, VMF, Link 22 — the MIL edition's separately governed packs],
  [*Unclear release status*], [anything whose distribution terms are unverified (e.g. STANAG 4607 until checked)],
)

These are not a different architecture — they are *packs* the MIL edition adds under its own access
rights. The civilian engine stays content-free: restricted form-classes never enter the open repository.

== Open core

The split falls out cleanly as an open-core model: the *engine* and the *public packs* are open source
(AGPL-3.0); guard-specific productisation — the contract/policy tooling, certified builds, monitoring and
support — is the commercial layer; restricted packs are the MIL edition, supplied separately. Nothing the
civilian guard builds is throw-away: the engine, parser profile, rule library, canonical serializer and
audit trail are identical across editions.
