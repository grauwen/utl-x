= Every Format Becomes One Tree

The UDM of Chapter 5 is only useful if every format can reach it. For text-structured formats that is
easy — UTL-X already reads XML and JSON. The hard, and interesting, case is *binary*: an AIS burst,
an ADS-B squitter, an ASTERIX record — bits packed with no delimiters and no tags. This chapter is
how those become trees too.

== The problem with binary: it does not describe itself

A JSON document carries its own structure — braces, keys, values. A bit-packed frame does not. An
ADS-B 1090ES message is 112 bits with a 24-bit CRC; nothing *in* those bits says "the next 5 bits are
a type code." The layout lives *outside* the data, in the specification. To read the frame, the engine
needs that layout supplied to it.

== Form-class: the layout as data

The oldest name for a non-self-describing record whose shape is defined externally comes from the
trading floor: a *form-class*. UTL-X adopts the term. A form-class is a *definition* — a declarative
description of a binary format's physical layout: the fields, their bit offsets and widths, their
encodings, scalings, enumerations, and discriminators. It is *data*, not code.

```
%utlx 1.1
input  binf { definition: "packs/ads-b.def" }
output json
---
{ icao: $input.icao, altitude: $input.alt, callsign: $input.callsign }
```

The *BINF codec* reads the raw bytes, consults the form-class, and produces a UDM tree — the same
kind of tree a JSON reader produces. From that point on the mapping is format-agnostic: it navigates
a tree, indifferent to the fact that the tree came from bits.

#align(center)[
  *raw bytes + form-class (definition) → BINF codec → UDM tree*
]

== Why BINF, not "an ADS-B format"

A fair question: why declare `input binf { definition: "ads-b.def" }` rather than simply
`input ads-b`? Because the generic mechanism is the asset. ADS-B is *one* fixed-112-bit format; the
integration world has dozens of bit-packed formats, and new ones arrive with each standards
revision. Baking each into the engine as a named reader would mean a code change per format and a
content-bearing engine. Making the engine a *generic bit-level codec* driven by a form-class means a
new format is a new *definition file* — buildable by whoever holds the spec, with no engine change
and nothing domain-specific baked into the engine.

Named aliases are then a convenience on top: an operator can bind `input ads-b` to
`binf { definition: "packs/ads-b.def" }` so day-to-day mappings read cleanly, while the engine stays
generic underneath.

== The encoding spectrum, one codec

BINF spans the binary bands of Chapter 3 with one mechanism:

#table(
  columns: (auto, 1fr),
  [*Bit-level*], [fields at arbitrary bit offsets, no delimiters — AIS, ADS-B],
  [*Byte-structured*], [records/PDUs on byte boundaries — DIS PDUs],
  [*Self-tagged (TLV/KLV)*], [key-length-value, partly self-describing — MISB KLV ST 0601],
  [*Presence-driven*], [existence bitmaps declaring which optional fields are present — ASTERIX FSPEC],
)

The presence-driven case is the demanding one, and it recurs across several binary formats — which is
why ASTERIX is the proving ground: get FSPEC right and the harder presence-driven form-classes follow
the same pattern.

== Mapping decisions: UDM does not come for free

Does a bit-field map cleanly onto a UDM scalar, object, or array? Often, but not always — and
pretending otherwise would be dishonest. A form-class must make real decisions, and they are design
choices, not mechanical ones:

#table(
  columns: (auto, 1fr),
  [*Scaling*], [a raw integer that means "latitude × 10⁷" becomes a UDM number — the scaling lives in the definition],
  [*Enumerations*], [a 3-bit code becomes a meaningful string (or stays numeric) — a definition choice],
  [*Sentinels*], [a reserved "value not available" encoding must become `null`, not a misleading number],
  [*Multi-frame state*], [ADS-B CPR position needs two frames to resolve a position — a bounded, declared exception to pure per-message decoding],
  [*Opaque sub-fields*], [a field BINF should not interpret is carried as a UDM *Binary* node, untouched],
)

These decisions are exactly where format expertise lives, and exactly why a *declarative* definition
is the right home for them: they are reviewable, versionable, and changed as data when the standard
revises — not buried in a decoder's source.

== Round-trip and re-encoding

Because a form-class describes a layout, BINF reads *and writes* it: a mapping can emit a bit-packed
frame as readily as it consumes one, re-encoding from the UDM tree. That is what makes true
binary↔binary and binary↔modern bridges possible — decode an AIS position to the UDM, and re-encode
it as CoT XML, or as JSON, from the same model. Because every format meets in one model, re-encoding
is tractable rather than lossy.

With every format now a tree, Chapter 7 adds the step that makes the Mapper *high-assurance*:
asserting that the tree is correct before it is allowed out.
