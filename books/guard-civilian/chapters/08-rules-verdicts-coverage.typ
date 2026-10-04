= Rules, Verdicts, and Coverage

The parser (Chapter 7) delivers a safe UDM tree; this chapter is about what the guard then *decides* — the
rules, the labels, and above all the *verdict policy* that governs what passes, what is stripped, and what
is rejected.

== Seven categories of rule

A guard policy is composed from seven categories of pure `validate.*` rule over the UDM, applied cheapest
and most decisive first, fail-closed throughout:

#table(
  columns: (auto, 1fr),
  [1 — Structural caps], [depth, size, node and array counts — anti-bomb, defence-in-depth with the parser],
  [2 — Schema allow-list], [the message must conform to an *approved* contract, with no additional properties],
  [3 — Field allow-list], [even within the schema, only permitted fields may cross],
  [4 — Value constraints], [ranges, enumerations, anchored (ReDoS-safe) regex, finite numbers],
  [5 — Cross-field semantics], [mutual consistency: `stale > time`, `lineTotal == qty × price`],
  [6 — Releasability / labelling], [classification ≤ X, releasable-to the target zone (labels below)],
  [7 — Keyword / dirty-word], [forbidden tokens, control characters, embedded markup in free text],
)

The decisive category is the schema allow-list: the message must be an *expected, approved* type, or it is
rejected. The guard never enumerates bad shapes; it enumerates the single good one.

== Labels — STANAG 4774 / 4778

A 4774 confidentiality label is XML-structured data, so it parses into the UDM like anything else;
releasability is then a *pure rule* over the label subtree and the target zone's clearance. Binding
verification (4778) is deterministic — a hash and signature check. Unlabelled or forged-label traffic
fails closed. The public 4774/4778 standards are in scope for the civilian edition; national government
classification labels are handled the same way.

== The verdict policy — rebuild what is clean, reject what is wrong

This is the rule that governs everything the guard does with a message:

#align(center)[
  #block(fill: luma(245), inset: 12pt, radius: 5pt, width: 92%)[
    #align(center)[*Rebuild what is clean, reject what is wrong — never repair.*]
  ]
]

A guard must *never* "fish out" the malicious parts and pass the rest. As soon as a message violates
policy, the *whole message* goes to isolation (dead-letter or quarantine), with an audit record and an
alert. But not everything the guard drops is "malicious" — three categories must be kept distinct:

#table(
  columns: (5.2em, 1fr, 1fr),
  [*1 — Formatting noise*], [comments, whitespace, BOM, odd-but-valid encodings, number formatting, `^` metadata], [*dropped silently by the canonical serializer*; the message passes. Not fishing out malice — the normal rebuild.],
  [*2 — Fields removed by policy*], [internal / high-side-only fields (releasability), debug fields], [*only if explicitly listed in the contract as "strip"*; the message passes, and the audit records which fields were removed],
  [*3 — Violations*], [parse error, bomb, field off the allow-list (no strip rule), value out of range, wrong / missing / forged label, dirty word, CRC or signature error], [*reject the whole message* → isolation + audit record + alert],
)

The default for unknown fields is *reject*. "Strip" is an explicit, per-field choice in the Message
Contract, never a catch-all.

== Why not "repair and pass"?

#table(
  columns: (auto, 1fr),
  [*Repair means guessing intent*], [you do not know what the attacker was after, so you cannot know which remainder is safe],
  [*Repair logic is an attack surface*], [an attacker crafts the message so that what is left *after* cleaning is exactly what they wanted delivered],
  [*Meaning can change*], [removing one field or element can flip a status, a "not", or a coordinate pair],
  [*Integrity and origin break*], [a label binding (4778) or a signature no longer matches a modified message — the receiver gets something the sender never sent],
  [*A violation is a signal*], [a malicious message says something about the sender or the channel; that deserves an alert and investigation, not silent cleaning],
)

And *"mark as untrusted and pass it on"*? Not for a guard — that is how an IDS or monitoring system works.
A guard's job is precisely that nothing untrusted crosses. The only exception is *monitor / shadow mode*:
the guard runs alongside an existing solution, logs its verdicts but does not block — for building
evidence, not for operational use.

== What happens to a rejected message

#table(
  columns: (auto, 1fr),
  [*Default*], [hash plus verdict in the audit record; the original is discarded],
  [*Optional quarantine*], [for forensics, a copy of the original in an isolated, access-controlled, encrypted store *on the ingress side*, with a retention period. That copy *never crosses* the boundary and is never released automatically — release only by a person, back through the same guard path, rebuilt and inspected.],
)

== Three verdict states

Every message leaves exactly one record:

#table(
  columns: (auto, 1fr),
  [`PASS`], [clean; only formatting noise dropped (category 1)],
  [`PASS-STRIPPED`], [passed after explicitly approved field removal (category 2); the removed fields are listed],
  [`REJECT`], [violation (category 3); whole message isolated — rule IDs, reason, hash, optional quarantine reference],
)

This works identically for civilian and MIL use. Civilian customers will sometimes ask for "just clean it
and pass it" for convenience. The answer is category 2: allowed, but *only* as an explicit strip rule in
the contract — never as a default.

== OWASP — a coverage map, not a rule port

It is reasonable to ask how the guard compares to OWASP and to a WAF's managed rule set. The framing
matters: the guard is *not* a WAF, so "we cover the WAF rules" is never the security claim — the allow-list
and schema conformance *are* the security property. Used correctly, the comparison is useful two ways: as
a *coverage map* (recognised OWASP risk classes mapped to how the guard addresses each — injection → value
+ keyword rules; data exposure → field allow-list + releasability; XXE → the parser profile; integrity
failures → rebuild + CRC) for communication and gap-finding; and as a *test corpus* (OWASP CRS payloads
fired at the guard as adversarial inputs — the hardened parser must fail-closed on every bomb; the
allow-list must neutralise every injection). Never rules copied into the guard.
