= The Standards in Depth

This chapter walks the open formats the Mapper bridges — what each one is, how it is encoded, and what
it asks of a form-class and a contract. It is organised the way the engine sees them: the *binary*
formats that need a form-class, and the *text-structured* formats that do not.

== Binary — via a form-class

=== AIS — Automatic Identification System

Maritime self-reporting: position, identity, voyage. Encoded as *6-bit-packed binary inside NMEA
armour*, with scaling, enumerations, and reserved sentinels. It is the Mapper's ideal first binary pack
— small, complete, and exercising every form-class feature that matters (scaling, enums, "not
available" sentinels). The canonical demonstration is *AIS → CoT*.

=== ADS-B — 1090ES Extended Squitter

Air self-reporting. Fixed *112-bit Mode S frames with a 24-bit CRC*, a type-code discriminator, and
*CPR* position encoding that needs two frames to resolve a location — a bounded, declared multi-frame
exception to pure per-message decoding (Chapter 6). A fixed-layout form-class with a `match` on the type
code.

=== ASTERIX — EUROCONTROL surveillance

The radar / surveillance exchange. Binary records whose present fields are declared by *FSPEC* presence
bitmaps, organised into *categories* (e.g. CAT021 for ADS-B, CAT048 for radar). It is the Mapper's
variable-structure stress test — the demanding presence-driven pattern, on a public standard.

=== MISB KLV ST 0601 — full-motion-video metadata

FMV metadata as *KLV* (key-length-value) — self-tagged, byte-structured binary. Because it is
self-describing via keys, it is a clean byte-granular BINF demonstration, and fully public.

=== DIS — Distributed Interactive Simulation

Binary PDUs with fixed layouts, for simulation record and replay. A straightforward byte-structured
form-class, useful for training and test harnesses.

== Text-structured — mapping only (no codec)

=== Cursor-on-Target (CoT) and CISE

The two canonical *map-to* targets. CoT is a compact *XML* event (position + detail), the lingua franca
of TAK clients — the destination for a decoded AIS or ADS-B track. CISE is the EU's *XML* maritime
surveillance data model — the flagship civilian bridge: CISE requires an adaptor per connected system,
and the Mapper is an adaptor with validation. Both are native-XML, mapping-only.

=== C2SIM, IALA IVEF, IHO S-100

Command-and-control ↔ simulation (C2SIM), VTS-centre exchange (IVEF), and hydrographic products
(IHO S-100, GML part) — all *XML* families the native reader already handles; the work is *mapping*,
not decoding.

=== EDIFACT and RIS

UN/EDIFACT is *segment / element delimited text* — the ports, customs and logistics backbone, with an
enormous installed base; it needs the dedicated EDIFACT-class reader (not BINF, not XML). RIS (River
Information Services — ERI, NtS, Inland AIS) is an umbrella that routes to AIS, EDIFACT and XML
sub-formats by part.

=== APP-6 / MIL-STD-2525 symbology

Not a message format but a *public symbol-identity code system* (SIDC) — a lookup used inside a mapping
to turn an identity into, say, a CoT type. Handled as a stdlib lookup, not a codec.

== What the walk shows

Three things recur across every entry, and they are the thesis of the book in miniature. First, the
*encoding*, not the domain, decides the work — binary needs a form-class, XML does not. Second, the hard
binary patterns are *shared* — presence bitmaps, fixed words, discriminators — so getting one right
(ASTERIX's FSPEC) carries over to the next. Third, every format is *data* to the engine — a definition
pack or a mapping — so breadth is configuration, not code, and the open-core engine stays content-free.
