= Appendix C: Sources & Further Reading

_The design documents, specifications, and companion books behind this edition. Standards references
are indicative; verify each against its primary source._

== Companion books

#table(
  columns: (auto, 1fr),
  [_UTL-X Guard_], [the security counterpart: the same engine as a content guard — allow-list, fail-closed, the verdict policy, deployment shapes],
  [_UTL-X: One Language, All Formats_], [the complete UTL-X language specification],
  [_UTLXe on Azure — Deployment and Operations Guide_], [running the engine as a managed cloud component with Dapr],
)

== UTL-X design documents

#table(
  columns: (auto, 1fr),
  [`utl-x/docs/architecture/utlx-1_1-semantic-validation.md`], [the UTL-X 1.1 `validate.*` namespace and `ValidationResult` — the validation this book depends on],
  [`utl-x/docs/architecture/utlx-language-versioning-validation.md`], [versioning and packaging: why `validate.*` is 1.1 and `ai.*` is 2.0; UTLXe vs UTLXS],
  [`utl-x/docs/architecture/utlx-validate-vocabulary-coverage.md`], [the `validate.*` vocabulary, audited against Schematron and FEEL],
  [`BINF-bit-level-binary-format.md`], [the BINF codec and form-class: binary decoding, named aliases, `^` metadata, UDM mapping decisions],
  [`formats-open/README.md`], [the encoding-grouped classification of the open formats and the proving order],
)

== Standards referenced (verify against primary sources)

#table(
  columns: (auto, 1fr),
  [*Surveillance & tracks*], [EUROCONTROL ASTERIX; ITU-R M.1371 (AIS); RTCA DO-260B (ADS-B 1090ES); MISB ST 0601 (KLV)],
  [*Overlays & symbology*], [Cursor-on-Target; APP-6 / MIL-STD-2525 symbology (public); C2SIM],
  [*Maritime & logistics*], [EU CISE; IALA IVEF; IHO S-100; UN/EDIFACT; EMSWe; RIS (ERI, NtS, Inland AIS)],
  [*Labelling*], [STANAG 4774 (confidentiality label syntax) / 4778 (binding) — public; referenced for label validation],
)

== A note on scope

Every format in this book is publicly specified and buildable without restriction — no sponsor, no
clearance, no restricted access. The engine stays content-free: a binary format is a *definition pack*,
and the ones here are open. Specialised, access-restricted standards (the defence tactical data links)
are a separately governed edition, not part of this book.

#v(1cm)
#line(length: 100%, stroke: 0.5pt + luma(180))
#v(0.3cm)
#align(center)[
  #text(size: 9pt, style: "italic", fill: luma(100))[
    UTL-X — The Mapper · Exploratory edition, 2026 · An assured message-mapping
    component built on UTL-X 1.1.
  ]
]
