= What It Does, and Why It's Different

Chapter 1 made the promise; this chapter is the value proposition — what the guard actually does, and the
handful of properties that set it apart from everything else on a boundary.

== What a guard does

A guard sits between two zones of different trust and does three things, in order:

#align(center)[
  *normalise untrusted input → inspect against policy → pass only what is explicitly allowed, rebuilt clean*
]

There is no pass-through path: even an approved message is never forwarded as received — it is rebuilt
from the UDM, and the original bytes never reach egress. The civil world calls this shape Content Disarm
& Reconstruction (CDR). The UTL-X Guard is not a new product built from scratch to do it — it is this
shape expressed in a transformation engine: parse to the UDM, inspect with rules, re-serialize a clean
canonical message. Rebuilding from the model is itself a disarming step (Chapter 7).

== Always rebuilt — and in any format

From the one UDM tree the guard can re-serialize *to the same format* (a clean, canonical drop-in
replacement) or *to a different format* (decode a binary AIS or ADS-B frame and emit JSON or XML for a
modern consumer). Changing the format is itself a defence — a *format break*: an exploit crafted for the
input encoding cannot survive being rebuilt into a different one. And it comes for free as an
*integration* capability: the same box that guards the boundary also bridges formats.

#figure(
  image("../pictures/utlx-mandatory-rebuild-300dpi.png", width: 100%),
  caption: [Every message is rebuilt, never forwarded. There is no pass-through path (top): untrusted bytes are parsed to the UDM, inspected, and a fresh message serialized; the original bytes are discarded after parsing and kept only as a hash. The target format is a policy choice — same format, a different format (plus a format break), or a simple form for independent verification. Disarm strength grows with the choice (bottom); a format break stops encoding attacks, not hostile content that survives semantically — that is the rules' job (Chapters 7, 8).],
)

== The leverage: one rule set for every format

The difference that matters commercially is the collapse from many inspectors to one. Because every
format normalises into one UDM, a releasability check, a value-range check, a forbidden-field check is
each written *once* and applied to a JSON document and a binary AIS frame alike.

This is, deliberately, a *separation of layers*: the readers and serializers deal with *formats*; the
policy lives one layer up, *on the UDM*, and never names a format at all. A new format is a new *reader*,
never a new rule; a new rule applies to *every* format at once. The single policy a reviewer accredits is
the same policy that runs on every input — the heart of the assurance argument.

#figure(
  image("../pictures/utlx-udm-guard-model-300dpi.png", width: 100%),
  caption: [From any format to one tree: inputs of every encoding class are parsed (native readers, an EDIFACT reader, or the BINF codec with a form-class) into the UDM; one pure `validate.*` rule set inspects the tree; a canonical serializer rebuilds only what is allowed. Any failure goes to isolation with an audit record.],
)

== It is not a WAF

A web application firewall inspects HTTP traffic for known-bad patterns — a *deny-list*, leaky by design.
A content guard passes only *known-good* content — an *allow-list*, fail-closed — and decides about
*data*, not network traffic.

#table(
  columns: (auto, 1fr),
  [*WAF / API gateway*], [deny-list · HTTP · blocks known-bad · leaks the unknown],
  [*UTL-X Guard*], [allow-list · any format · passes only approved content, rebuilt · fail-closed],
)

== The gap it fills

#align(center)[
  #block(fill: luma(246), inset: 11pt, radius: 4pt, width: 94%)[
    _File-CDR products disarm *documents*. API gateways and WAFs *route and filter* traffic. Diodes
    enforce *direction*. Nobody rebuilds *structured messages* across every format — text and binary —
    under one declarative, auditable rule set._
  ]
]

== Five differentiators

#table(
  columns: (auto, 1fr),
  [*Every format, one model*], [text and binary — JSON, XML, CSV, EDIFACT, AIS, ADS-B, ASTERIX, KLV — all inspected by one rule set on the UDM],
  [*Allow-list, fail-closed*], [passes only approved content; blocks everything else, including novel attacks a deny-list would miss],
  [*Rebuilt, not forwarded*], [the received bytes never cross; what crosses is a clean canonical message built only from what the rules allowed],
  [*Declarative and auditable*], [rules are readable by security staff and traceable to policy; every verdict is reproducible — an assessment asset],
  [*Open-core and dual-use*], [open-source engine and public packs; the same engine serves civilian data-sharing and, with restricted packs, defence crossings — "MIL-ready by design"],
)

Chapter 3 shows where this is actually fielded — the civilian markets and the deployment shapes.
