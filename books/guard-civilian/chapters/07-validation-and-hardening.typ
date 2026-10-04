= Validation, and Safe by Construction

Two things make the guard trustworthy: it can *assert* that content is correct before passing it
(validation), and it survives parsing hostile input to do so (hardening). This chapter covers both.

== Validation as a first-class citizen — UTL-X 1.1

UTL-X 1.0 maps and restructures data; it does not *validate* it. UTL-X 1.1 closes that gap with the
`validate.*` namespace, which asserts facts over the UDM and returns a structured `ValidationResult` —
which rule failed, on which field, and why — suitable for routing and for an audit trail:

```
validate.inRange($msg.latitude, -90, 90)
validate.oneOf($msg.status, ["OK", "WARN", "FAIL"])
validate.conformsTo($msg, "contracts/approved/position-report:1.0.0")
validate.assert($msg.stale > $msg.time, "stale must be after time")
```

Crucially, `validate.*` keeps *every* 1.0 guarantee — pure, stateless, deterministic — so it only adds; a
1.0 script runs unchanged on a 1.1 engine. That is exactly why it is a *minor* version, and why it is the
natural rule engine for a guard: a guard's verdict must be reproducible and reviewable, and `validate.*`
is precisely a deterministic rule layer. The probabilistic `ai.*` of 2.0 is the opposite, and is excluded
from the trusted path.

== The parser is the crux

A content guard must parse untrusted, possibly hostile input *before* it can inspect anything. The parser
is the single most dangerous component in the design, because it touches the attacker's bytes first. The
good news: the risk is manageable, and the act of parsing-then-rebuilding is itself a defence.

#table(
  columns: (auto, 1fr),
  [*Resource bombs*], [entity expansion (billion laughs), deep nesting, huge arrays/strings],
  [*Length-field overflow*], [a 32-bit length claiming 4 GB; attacker-controlled repeat counts in binary],
  [*XXE / external entities*], [DTD and external-entity resolution in XML],
  [*Parser differential*], [the guard's parser and the receiver's disagree on the same bytes (duplicate keys, BOMs, number precision)],
)

Three properties turn "the parser is dangerous" into "the parser is a bounded risk":

#table(
  columns: (auto, 1fr),
  [*Memory-safe runtime*], [UTL-X runs on the JVM / GraalVM; the buffer-overflow-to-code-execution path is largely off the table — the realistic blast radius is denial of service, not attacker code],
  [*Parse → rebuild disarms*], [rebuilding from the UDM drops comments, duplicate keys, smuggled trailing bytes, polyglot tricks — the reconstruction half of CDR, almost for free],
  [*Safe parse, then decide*], [if the parser cannot be exploited, a rogue message is just a well-formed UDM tree the rules inspect — the security decision happens at *inspection*, not at parsing],
)

== The hardened "guard profile" parser

An ordinary mapping parser is lenient; a guard parser is strict, bounded and fail-closed. The guard
profile enforces resource limits *during* parse (size, depth, element count, time, memory) so a bomb dies
while being read; disables XXE / DTD / external entities; for binary, bounds and cross-checks every
length field and verifies CRC on read; fails closed; runs in a resource-capped sandbox; and is fuzzed as
an assurance-grade component (coverage-guided, grammar-based, and differential against the receiving
parser).

== The anti-round-trip serializer

Here the guard deliberately does the opposite of ordinary UTL-X. Elsewhere the engine preserves
format-fidelity metadata for byte-exact round-trip; a guard wants the reverse — a *low-fidelity,
canonical* re-serialization that drops everything non-essential (comments, whitespace, BOMs, non-canonical
numbers, and any field not on the allow-list), with keys in canonical order. This disarms the message and
defeats parser-differential smuggling, because the receiver re-parses one strict canonical form.

The honest limit: a canonical rebuild and a format break stop attacks aimed at the *encoding*, but not
hostile content that survives *semantically* — a malicious URL in a text field, an out-of-range
coordinate, a forbidden label. Catching those is the job of the rules, which Chapter 8 — and the verdict
policy — makes precise.
