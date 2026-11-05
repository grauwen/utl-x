# B38: non-finite numbers serialise as invalid JSON

> **B = core bug** (shared core and the format serialisers built on it), the family of B01–B37.
> This is **B38**.

**Status:** **OPEN — needs a decision, not an implementation.**
**Priority:** Medium. The output is unparseable, so it fails loudly downstream rather than
corrupting data silently — but it fails at the consumer rather than at the producer.
**Component:** the JSON serialiser primarily; the same question applies to CSV and XML output.

> **One-line:** a payload carrying infinity or NaN serialises to JSON as the bare tokens `Infinity`
> and `NaN`, which JSON does not define.

## Evidence

```
input yaml:   x: .inf
              y: .nan
output json:  { "x": Infinity, "y": NaN }
```

Neither token is valid JSON. A conforming parser rejects the document, so a transformation that
appears to succeed produces output the next hop cannot read.

## How a value gets there

- **YAML input.** `.inf`, `-.inf` and `.nan` are ordinary YAML floats and parse to the
  corresponding doubles. This is the easy path and needs no unusual data.
- **Arithmetic overflow.** `1e300 * 1e300` is infinity. Division by zero is caught and raised, so
  that route is already closed.
- **Text inputs.** CSV and XML accept `NaN` and `Infinity` as numeric text, because the decision
  about what counts as a number is `Double.parseDouble`'s, and it accepts them.

## Why this is not simply fixed

Three defensible answers, and they differ in kind rather than in quality:

1. **Reject** — raise on serialising a non-finite number. Fail-closed, consistent with the JSON
   canonicalisation function, which already throws for exactly these values per RFC 8785 §3.2.2.
   But it turns output that "worked" into a hard failure.
2. **Emit `null`** — valid JSON, and JSON's own convention for "no value". Silently discards the
   distinction between infinity, NaN and a genuinely absent value.
3. **Emit a string** (`"Infinity"`) — valid JSON and lossless, but changes the field's *type*, which
   breaks schema-validated consumers and is its own kind of surprise.

The right shape is almost certainly **profile-driven** — reject under a strict profile, substitute
under a lenient one — mirroring how parser strictness is already handled
([F23](../features/F23-parser-strictness-profiles.md)). That requires a serialisation profile, and
there is no shared one today: each format carries its own `SerializeOptions` with no common
strictness axis. So the work is the plumbing, and the plumbing deserves its own design rather than
being improvised inside a numeric fix.

## Recommendation

Add a serialisation strictness axis alongside the parse profile, then:

- **STRICT** — reject. A guard should never emit a frame its consumer cannot parse, and a rejected
  message is recoverable where an unparseable one is not.
- **STANDARD / LENIENT** — emit `null`, and record a notice so the substitution is visible rather
  than silent.

Until then the behaviour is unchanged and documented here, so it is not rediscovered as a regression
of [B37](B37-serializers-saturate-large-doubles-to-long-max.md), which was found in the same audit
and fixed because it had one correct answer.

## Related

- [B37](B37-serializers-saturate-large-doubles-to-long-max.md) — the other serialiser defect from the
  same audit; fixed, because there the right answer was not a judgement call.
- [F23](../features/F23-parser-strictness-profiles.md) — the profile mechanism this should reuse.
