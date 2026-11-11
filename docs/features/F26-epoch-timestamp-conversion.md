# F26: epoch timestamp conversion (`toEpochMillis` / `toEpochMicros`)

**Status:** **Implemented** (2026-11-11)
**Priority:** Medium — small, self-contained, and unblocks a class of sink that every integration
eventually meets.
**Component:** `:stdlib` — `DateFunctions`.
**Requested by:** GitHub issue **#7** (`taatuut`), with a concrete use case, the proposed signatures,
and a note on what they do today instead.

## The gap

UTL-X could parse date-times (`parseDate`, `parseDateTimeWithTimezone`, `toUTC`) and produce the
current instant (`now()`), but nothing turned a date-time into the **number** that a timestamp
column wants. The reporter's pipeline writes ARTS POSLog receipts into a Databricks Delta table
through Zerobus, which validates against the table schema and needs `TIMESTAMP` as epoch
**microseconds**. The same need turns up for Kafka, Elasticsearch, InfluxDB, Parquet and most JSON
APIs.

Their workarounds were the two that the language exists to remove: arithmetic on `substring` parts
with days-from-civil by hand, or a Java step after the transformation — "which keeps part of the
mapping outside the script".

Worth noting the capability was already there and simply not reachable: `UDM.DateTime` has had
`toEpochMillis()` and `fromEpochMillis()` as Kotlin members all along. F26 is mostly about exposing
them properly.

## The functions

```
toEpochMillis(value, zone?)   -> whole milliseconds since 1970-01-01T00:00:00Z
toEpochMicros(value, zone?)   -> whole microseconds
fromEpochMillis(n)            -> DateTime
fromEpochMicros(n)            -> DateTime
```

```
toEpochMillis("2026-11-04T14:36:30.438+01:00")   1793799390438
toEpochMicros("2026-11-04T14:36:30.438+01:00")   1793799390438000
```

Those are the reporter's own examples and values.

### Three decisions worth recording

**1. The result is a `Long`, not a `Double`.** The request asked for "a number that serializes as a
plain integer in JSON (not `1.79E15`)", and that is the right requirement for two independent
reasons: epoch micros passed 2⁵³ in 2255 — no, in **1970 + 2⁵³µs ≈ 2255** — but far more immediately,
`Double.toString()` switches to exponent notation above 10⁷, so *every* epoch value would have
rendered as `1.79E15`. A `Long` renders plainly and is exact.

**2. It accepts a value, not only a string.** An ISO-8601 string, `UDM.DateTime`, `UDM.Date` or
`UDM.LocalDateTime` all work, so `toEpochMillis(now())` and `toEpochMillis($input.Timestamp)`
compose rather than forcing a round trip through text.

**3. A value with no offset is rejected unless a zone is given.**

```
toEpochMillis("2026-11-04T14:36:30")
  -> toEpochMillis: '2026-11-04T14:36:30' has no UTC offset, so it does not identify an instant.
     Pass a zone as the second argument, e.g. toEpochMillis(value, "Europe/Amsterdam"),
     or use a value that carries an offset (…+01:00 or …Z).

toEpochMillis("2026-11-04T14:36:30", "Europe/Amsterdam")   1793799390000
toEpochMillis("2026-11-04", "Europe/Amsterdam")            1793746800000
```

A bare date-time names a **wall-clock reading**, not an instant. Assuming UTC silently is wrong by
the size of the offset, and for a date it is wrong by up to a full day — `2026-11-04` in Amsterdam
begins at `23:00Z` on the 3rd. That is precisely the error that surfaces in production as "the
receipt landed on the wrong day", so it raises and names the fix instead.

### Micros is not millis × 1000

```kotlin
Math.addExact(Math.multiplyExact(instant.epochSecond, 1_000_000L), instant.nano / 1_000L)
```

Multiplying the millisecond value would throw away sub-millisecond digits the source carried:
`2026-11-04T13:36:30.438123Z` is `…438123`, not `…438000`. `Math.multiplyExact` makes an overflow
loud rather than silent; micros fit a `Long` to about the year 294,247.

## Not done

The reporter listed `fromEpochMillis(n, "Europe/Amsterdam")` as optional. The inverses are
implemented but return a `DateTime` (an instant) rather than a zoned local value — formatting into a
zone is what `formatDateTimeInTimezone` already does, so adding a zone argument here would be a
second way to do one thing. Worth revisiting only if someone asks.

## Tests

Two conformance fixtures:

- `stdlib/datetime/toEpochMillis_basic` — the reporter's exact example and values, both functions,
  and a check that the offset is genuinely applied (`+01:00` at 14:36 equals `Z` at 13:36).
- `stdlib/datetime/toEpochMillis_precision_and_zone` — sub-millisecond digits surviving `toEpochMicros`
  (the case that would be lost if micros were millis × 1000), a wall-clock value with an explicit
  zone, a date with a zone (the full-day case), and both round trips.

## Related

- Issue #10 / [B47](../bugs-fixed/B47-renderjson-and-object-rebuilds-drop-xml-attributes.md) — the
  same reporter, the same pipeline. Between them, the Java step they keep in production for key
  cleaning, attribute preservation and timestamp conversion can now be done in the script.
- The exponent-notation defect noted in
  [B46](../bugs-fixed/B46-product-scale-reports-places-no-operand-had.md) — a `Double` ≥ 10⁷
  serialises as `1.23456789E7`. Returning a `Long` here sidesteps it; the underlying defect is
  still open.
