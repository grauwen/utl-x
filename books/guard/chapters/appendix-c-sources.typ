= Appendix C: Sources & Further Reading

_This book synthesises the UTL-X design documents and public standards. The design docs carry their own
sourced references._

== UTL-X design documents

#table(
  columns: (auto, 1fr),
  [`utl-x/docs/architecture/civilian-guard.md`], [the civilian-first strategy: product, markets, assurance route, roadmap, risks — the basis of this book],
  [`utlx-mil/docs/UDM-content-guard.md`], [the guard architecture: CDS/CDR framing, threat model, pipeline, deployment shapes, verdict policy, non-negotiables],
  [`utlx-mil/docs/guard-rule-library.md`], [the seven `validate.*` rule categories + OWASP coverage map],
  [`utlx-mil/docs/guard-parser-profile.md`], [the hardened read profile + canonical low-fidelity serializer],
  [`utlx-mil/docs/BINF-bit-level-binary-format.md`], [the form-class / bit-level codec],
  [`utl-x/docs/proposals/utlx-1_1-semantic-validation.md`], [the 1.1 `validate.*` namespace and `ValidationResult`],
  [`utl-x/docs/architecture/utlx-test-corpora.md`], [the test doctrine for hardening the parsers and serializers],
)

== Companion books

#table(
  columns: (auto, 1fr),
  [_UTL-X MIL — The Content Guard_], [the defence edition: restricted packs, accreditation, rugged hardware — same engine and architecture],
  [_UTL-X: One Language, All Formats_], [the complete language specification],
  [_UTLXe on Azure_], [running the engine as a managed cloud component with Dapr],
)

== External standards & guidance (verify against primary sources)

- *Assurance:* NL BSPA (NBV/NLNCSA); IEC 62443-4-1/-4-2 (OT secure development/components); ISO/IEC 27001;
  Common Criteria.
- *Regulation:* NIS2 and national implementations; EU dual-use Regulation (EU) 2021/821.
- *Labels:* STANAG 4774 / 4778 (public).
- *Open formats:* EU CISE; ITU-R M.1371 (AIS); EUROCONTROL ASTERIX; RTCA DO-260B (ADS-B); MISB ST 0601
  (KLV); MITRE Cursor-on-Target; UN/EDIFACT; IHO S-100; IALA IVEF; Cospas-Sarsat.
- *Security benchmark:* OWASP Top 10 / API Security Top 10 / Core Rule Set — used as coverage map and test
  corpus, never as a rule port.

== Cross-domain literature & standards basis

The guard concept, its taxonomy and its controls are well established — the classification and the AC-4
mapping in this book rest on these, not on our own definitions:

- R. Smith, *Constructing a High Assurance Mail Guard*, 17th National Computer Security Conference (1994)
  — the store-and-forward mail guard: address allow-lists, label checks, attachment filtering; assurance
  "even under attack and failure".
- Sundaravarathan et al., *Cross-Domain Solutions (CDS): A Comprehensive Survey*, IEEE Access (2024) — the
  access / transfer / MLS taxonomy; uni- vs bidirectional transfer.
- *NIST SP 800-53 Rev. 5*, control AC-4 (Information Flow Enforcement) — the CDS / high-assurance-guard
  control enhancements (Chapter 8).
- US DoDI 8540.01 (CDS types) & NSA NCDSMO; CNSSI 1253 Cross Domain Solution Overlay; UK NCSC cross-domain
  principles & 2026 guidance; NZ NZISM; AU ACSC introduction to cross-domain solutions.

#v(1cm)
#line(length: 100%, stroke: 0.5pt + luma(180))
#v(0.3cm)
#align(center)[
  #text(size: 9pt, style: "italic", fill: luma(100))[
    UTL-X Guard · Exploratory edition, 2026 · MIL-ready by design, proven in
    civilian service.
  ]
]
