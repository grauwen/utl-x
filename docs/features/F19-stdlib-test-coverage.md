# F19: Stdlib Test Coverage — Systematic Gradle Tests for All Functions

**Status:** Open  
**Priority:** Medium (quality/confidence, not blocking)  
**Created:** May 2026  
**Triggered by:** B23 audit — discovered 16+ functions with placeholder implementations that passed tests because tests asserted wrong behavior

---

## Summary

The UTL-X stdlib has 659 registered functions. There are 68 test files with 1960 test methods (~3 tests per function average). However, a name-based analysis shows ~106 functions are not explicitly mentioned in any test file. Some of these (like `concat`, `filter`, `find`) are tested indirectly through the 522 conformance suite tests or as part of other test expressions, but they lack dedicated unit tests. The earlier estimate of "503 untested" was incorrect — it likely counted functions without dedicated test files, not functions without any test coverage.

This feature adds systematic Kotlin test coverage for all stdlib functions, prioritized by risk.

## Why this matters

B23 demonstrated the failure pattern:
1. Function scaffolded with TODO placeholder
2. Test written that asserts placeholder behavior (wrong result)
3. Test passes → green CI → nobody notices
4. Function ships as "working" but produces wrong output

Without proper tests, we can't distinguish "works" from "works by accident."

## Largest untested areas (~503 functions)

| Category | Examples | ~Count | Risk |
|---|---|---|---|
| Date/Time | addDays, diffMonths, dayOfWeek, isLeapYear, convertTimezone | ~80 | Medium — complex logic, edge cases |
| String | camelCase, capitalize, padLeft, startsWith, indexOf, regex | ~70 | Low — mostly simple operations |
| XML/Namespace | escapeXML, localName, namespaceUri, childCount, createCDATA | ~50 | Medium — format-specific |
| Type checks | isArray, isString, isNumber, isNull, isEmpty, isBoolean | ~40 | Low — trivial functions |
| Array/Collection | flatMap, partition, distinct, intersect, union, take, drop | ~35 | **High** — may take lambdas |
| CSV | csvFilter, csvSort, csvTranspose, etc. | ~15 | Medium — may have placeholders |
| YAML | yamlMerge, yamlPath, yamlSort, etc. | ~20 | Medium — may have placeholders |
| Binary/Archive | compress, gzip, zip, readByte, writeByte | ~30 | Medium — native operations |
| Encoding/Crypto | hmacSHA256, sha512, encryptAES, generateKey | ~20 | Low — already tested in native validation |
| Financial | calculateTax, compoundInterest, formatCurrency | ~15 | Medium — precision matters |
| Geo | geoBearing, geoDistance, geoMidpoint, inPolygon | ~10 | Low — niche functions |
| URL | buildURL, parseURL, getHost, parseQueryString | ~15 | Low — string parsing |
| Schema | parseXSDSchema, parseJSONSchema, renderAvroSchema | ~10 | Low — design-time only |

## Priority order (by risk of hidden bugs)

1. **Array/Collection functions that take lambdas** — B23 pattern, highest risk
2. **CSV/YAML utility functions** — may have same placeholder pattern
3. **Date/Time** — complex logic, timezone edge cases, leap years
4. **Financial** — precision matters for production use
5. **XML/Namespace** — format-specific, may have encoding issues
6. **String** — large count but low risk
7. **Type checks** — trivial, lowest risk
8. **Binary/Archive** — native operations, platform-dependent
9. **Encoding/Crypto** — already validated during native binary testing
10. **Geo/URL/Schema** — niche, low usage

## Test requirements per function

Each function needs at minimum:
- **Happy path** — normal input, expected output
- **Edge cases** — empty input, null, single element, boundary values
- **Error handling** — wrong arg count, wrong arg types
- **Lambda invocation** (if applicable) — real lambda, not `UDM.Scalar("placeholder")`
- **Format correctness** (if applicable) — assert actual format output, not just `contains("somestring")`

## How to execute

- Use `./gradlew testStdlib` for fast iteration (~2s)
- One category per commit
- Run `./gradlew test` + conformance suite before merging
- Track progress in this document

## Approach

```
For each untested function:
  1. Read the function source code
  2. Check for TODO/placeholder/simplified comments
  3. If placeholder → fix first (B23 pattern), then test
  4. If real implementation → write tests that assert correct output
  5. Run testStdlib to verify
```

## Progress

| Category | Functions | Tests | Status |
|---|---|---|---|
| Enhanced Array (B23) | 7 | 57 | **Done** |
| Critical Array (B23) | 3 | 18 | **Done** |
| Tree operations (B23) | 2 | 17 | **Done** |
| Join functions (B23) | 2 | 6 | **Done** |
| Regex replaceWithFunction (B23) | 1 | 11 | **Done** |
| Serialization render* (B23) | 3+2 new | 19 | **Done** |
| Measure (B23) | 1 | 1 updated | **Done** |
| Date/Time | ~80 | 0 | Pending |
| String | ~70 | 0 | Pending |
| XML/Namespace | ~50 | 0 | Pending |
| Type checks | ~40 | 0 | Pending |
| Array/Collection (remaining) | ~35 | 0 | Pending |
| CSV utilities | ~15 | 0 | Pending |
| YAML utilities | ~20 | 0 | Pending |
| Binary/Archive | ~30 | 0 | Pending |
| Encoding/Crypto | ~20 | 0 | Pending |
| Financial | ~15 | 0 | Pending |
| Geo | ~10 | 0 | Pending |
| URL | ~15 | 0 | Pending |
| Schema | ~10 | 0 | Pending |

---

*Feature F19. May 2026. Systematic test coverage for all 652 stdlib functions. Triggered by B23 audit.*
