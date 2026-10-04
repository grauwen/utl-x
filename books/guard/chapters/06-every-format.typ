= Every Format Becomes One Tree

The UDM of Chapter 5 is only useful if every format can reach it. For text-structured formats that is
easy — UTL-X already reads XML and JSON. The interesting case is *binary*: an AIS burst, an ADS-B
squitter, an ASTERIX record — bits packed with no delimiters and no tags.

== The problem with binary: it does not describe itself

A JSON document carries its own structure — braces, keys, values. A bit-packed frame does not. An ADS-B
1090ES message is 112 bits with a 24-bit CRC; nothing *in* those bits says "the next 5 bits are a type
code". The layout lives *outside* the data, in the specification — so the engine must be given it.

== Form-class: the layout as data

The oldest name for a non-self-describing record whose shape is defined externally comes from the trading
floor: a *form-class*. UTL-X adopts the term. A form-class is a *definition* — a declarative description of
a binary format's physical layout: fields, bit offsets and widths, encodings, scalings, enumerations and
discriminators. It is *data*, not code.

```
%utlx 1.1
input  binf { definition: "packs/ais.def" }
output json
---
{ mmsi: $input.mmsi, lat: $input.lat, lon: $input.lon }
```

The *BINF codec* reads the raw bytes, consults the form-class, and produces a UDM tree — the same kind of
tree a JSON reader produces. From that point on the guard is format-agnostic: it inspects a tree,
indifferent to the fact that the tree came from bits.

#align(center)[
  *raw bytes + form-class → BINF codec → UDM tree → one rule set*
]

== The encoding spectrum, one codec

#table(
  columns: (auto, 1fr),
  [*Bit-level*], [fields at arbitrary bit offsets, no delimiters — AIS, ADS-B],
  [*Byte-structured*], [records / PDUs on byte boundaries — DIS PDUs],
  [*Self-tagged (KLV)*], [key-length-value, partly self-describing — MISB KLV ST 0601],
  [*Presence-driven*], [existence bitmaps declaring which optional fields are present — ASTERIX FSPEC],
)

The presence-driven case (ASTERIX FSPEC) is the demanding one, and the open ASTERIX pack is the proving
ground for it.

== Why generic, not "an AIS reader"

Baking each binary format into the engine as a named reader would mean a code change per format and a
content-bearing engine. Making the engine a *generic bit-level codec* driven by a form-class means a new
format is a new *definition file* — buildable by whoever holds the spec, with no engine change. Named
aliases are then a convenience on top: `input ads-b` can resolve to `binf { definition: "packs/ads-b.def"
}` so day-to-day scripts read cleanly, while the engine stays generic underneath.

== For the guard, this matters twice

First, the same rule set polices a binary AIS frame exactly as it polices a JSON document — because by the
time the rules run, both are the same tree. Second, because a form-class describes a layout, BINF reads
*and writes* it: the guard can re-serialize a decoded binary frame as a canonical binary message, or as
clean JSON — the format break of Chapter 2, and a free integration bridge.
