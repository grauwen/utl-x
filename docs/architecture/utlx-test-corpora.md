# Test Corpora for UTLX Parser and Serialization Testing

This document lists public datasets and test suites for XML, JSON, YAML, CSV, and OData. Together they can supply tens of thousands of input files for UTLX parser and serializer testing. It also proposes how to organize them and how to use them in a layered testing strategy.

No single corpus covers all five formats. The recommended approach combines three kinds of sources:

| Source type | Purpose | Typical size |
|---|---|---|
| Conformance suites | Correctness: accept valid input, reject invalid input | Hundreds to tens of thousands of cases |
| Fuzzing corpora | Robustness: crashes, hangs, pathological input | Thousands of minimized inputs per project |
| Real-world dumps | Realism and volume: roundtrip and performance testing | Millions of files available |

> **Note:** File counts below are approximate and change over time. Verify the license of every source before committing files to the UTLX repository (see [Licensing](#licensing)).

---

## 1. Conformance and Test Suites

### 1.1 JSON

| Suite | Location | What it covers |
|---|---|---|
| JSONTestSuite | https://github.com/nst/JSONTestSuite | About 300+ files prefixed `y_` (must accept), `n_` (must reject), `i_` (implementation-defined). The de facto reference for RFC 8259 conformance. |
| JSON-Schema-Test-Suite | https://github.com/json-schema-org/JSON-Schema-Test-Suite | Schema validation cases across drafts. Useful if UTLX validates JSON against schemas. |
| nativejson-benchmark | https://github.com/miloyip/nativejson-benchmark | Conformance plus roundtrip tests, and large real-world files for performance. |
| simdjson examples | https://github.com/simdjson/simdjson (`jsonexamples/`) | Large realistic documents such as `twitter.json`, `citm_catalog.json`, `canada.json`. |
| JSON5 tests | https://github.com/json5/json5-tests | Relevant only if UTLX supports JSON5 or relaxed JSON. |

**Suggested UTLX use:** Treat `y_` files as must-parse, `n_` files as must-fail with a clean error (no crash or hang), and log the behavior of `i_` files as a documented implementation decision.

### 1.2 YAML

| Suite | Location | What it covers |
|---|---|---|
| yaml-test-suite | https://github.com/yaml/yaml-test-suite | The official suite, with several hundred cases. Each case includes the input YAML, the expected parser event stream, an expected JSON equivalent where applicable, and error markers for invalid input. |

**Suggested UTLX use:** The JSON equivalents make this suite especially valuable for cross-format testing. Parsing the YAML and the JSON should produce the same UTLX internal model. The `emit.yaml` and `out.yaml` files support serializer roundtrip checks.

### 1.3 XML

| Suite | Location | What it covers |
|---|---|---|
| W3C XML Conformance Test Suite | https://www.w3.org/XML/Test/ | A few thousand tests covering well-formedness, validity, namespaces, encodings, and DTDs. |
| W3C XML Schema Test Suite | https://github.com/w3c/xsdtests | Tens of thousands of schema and instance documents. Likely the largest single structured source in this list. |
| libxml2 tests | https://gitlab.gnome.org/GNOME/libxml2 (`test/`, `result/`) | Many hard edge cases, including encodings, entities, and malformed input with expected results. |
| Apache Xerces tests | https://xerces.apache.org | Additional parser edge cases. |

**Suggested UTLX use:** Pay special attention to security-relevant cases such as entity expansion ("billion laughs"), external entities (XXE), and deep nesting. Make sure UTLX rejects or limits these safely by default.

### 1.4 CSV

| Suite | Location | What it covers |
|---|---|---|
| CSV on the Web (CSVW) tests | https://github.com/w3c/csvw (`tests/`) | W3C tests for CSV with metadata, dialect descriptions, and conversion to JSON and RDF. |
| csv-spectrum | https://github.com/max-mapper/csv-spectrum | A small set of classic RFC 4180 edge cases (quoted newlines, escaped quotes, empty fields), each with expected JSON output. |
| Pollock benchmark | https://github.com/HPI-Information-Systems/Pollock | About 2,000+ deliberately polluted CSV files for measuring reader robustness, with clean reference versions. |
| CSV Wrangling dataset | https://github.com/alan-turing-institute/CSV_Wrangling | Thousands of messy real-world CSVs from GitHub and government portals, with annotated dialects (delimiter, quote character, escape character). |

**Suggested UTLX use:** CSV has no single strict standard, so record UTLX's chosen dialect behavior explicitly. Use csv-spectrum as hard pass/fail tests, Pollock for robustness scoring, and CSV Wrangling for dialect detection, if UTLX supports auto-detection.

### 1.5 OData

| Source | Location | What it covers |
|---|---|---|
| OASIS OData ABNF | https://github.com/oasis-tcs/odata-abnf | ABNF grammars plus test case files for URL syntax and JSON payload syntax. |
| OData reference services | https://services.odata.org | Live sample services (TripPin, Northwind, and others) that return JSON payloads and `$metadata` CSDL XML documents. |
| Microsoft OData .NET | https://github.com/OData/odata.net | Large collections of test payloads inside the library's test projects. |
| ASP.NET Core OData | https://github.com/OData/AspNetCoreOData | Additional payload and query test data. |

**Suggested UTLX use:** Write a small harvesting script that queries the reference services with varied `$select`, `$expand`, `$filter`, and `$top` options and stores the responses as fixtures. This can generate thousands of realistic OData JSON payloads plus their CSDL metadata. Do it once and commit the results so tests don't depend on live services.

### 1.6 Related formats (optional)

| Format | Location |
|---|---|
| TOML | https://github.com/toml-lang/toml-test |

---

## 2. Fuzzing Corpora

| Source | Location | Notes |
|---|---|---|
| OSS-Fuzz | https://github.com/google/oss-fuzz | Projects such as libxml2, expat, yaml-cpp, rapidjson, simdjson, and jsoncpp provide seed corpora and dictionaries. |
| AFL / libFuzzer dictionaries | Bundled with AFL++ and LLVM | Token dictionaries for XML, JSON, and YAML that guide mutation-based fuzzing. |

**Suggested UTLX use:** Use these as seed inputs for a UTLX-specific fuzz target. The pass criterion is not "parses correctly" but "never crashes, never hangs, never exhausts memory, and always returns either a result or a structured error."

---

## 3. Large Real-World Corpora

| Source | Location | Notes |
|---|---|---|
| The Stack v2 (BigCode) | https://huggingface.co/datasets/bigcode/the-stack-v2 | Millions of JSON, YAML, XML, and CSV files from GitHub. Filter by file extension. The fastest way to reach tens of thousands of files per format. |
| SchemaStore | https://github.com/SchemaStore/schemastore | Hundreds of JSON schemas, with positive and negative test files for many of them. Covers real configuration formats (package.json, GitHub Actions YAML, and many more). |
| data.gov | https://data.gov | Many datasets published simultaneously as CSV, JSON, and XML. |
| data.europa.eu | https://data.europa.eu | Same, for European open data. |
| data.gov.uk | https://data.gov.uk | Same, for UK open data. |
| Common Crawl | https://commoncrawl.org | Very large. Useful for extracting embedded JSON-LD or XML at scale. |

**Suggested UTLX use:** Open data portals that publish the same dataset in multiple formats are ideal for cross-format equivalence tests, for example CSV → UTLX → JSON compared against the official JSON version.

---

## 4. Proposed Repository Layout

```
test-corpora/
├── README.md                  # Points to this document
├── SOURCES.lock               # Source URL, commit hash or date, license per corpus
├── conformance/
│   ├── json/
│   │   ├── jsontestsuite/
│   │   └── json-schema-test-suite/
│   ├── yaml/
│   │   └── yaml-test-suite/
│   ├── xml/
│   │   ├── w3c-xmlconf/
│   │   └── w3c-xsdtests/
│   ├── csv/
│   │   ├── csv-spectrum/
│   │   ├── csvw/
│   │   └── pollock/
│   └── odata/
│       ├── odata-abnf/
│       └── reference-services/   # Harvested fixtures
├── fuzz/
│   ├── seeds/{json,yaml,xml,csv}/
│   └── dictionaries/
├── realworld/
│   └── {json,yaml,xml,csv}/       # Sampled subset, not the full dump
├── cross-format/
│   └── <dataset-name>/            # Same data as .csv, .json, .xml, .yaml
└── expectations/
    └── known-deviations.yaml      # Documented intentional differences
```

Keep large corpora out of the main repository. Fetch them with a script (pinned to a commit hash or download date) or store them with Git LFS. `SOURCES.lock` makes builds reproducible.

---

## 5. Testing Strategy for UTLX

### 5.0 Suite architecture: which suite runs which layer

The six layers in §5.1 are **not one test suite**. They differ on cadence, determinism, environment, and what a failure means — and those differences decide where each layer runs. Map them onto **three suites over one shared corpora store**, rather than folding everything into the conformance suite (which would make it slow and flaky) or into the performance suite (whose measurements need a stable, isolated environment).

| Layer (§5.1) | Suite | Deterministic | Cadence | On failure |
|---|---|---|---|---|
| 1. Conformance | **Conformance** | yes | every PR, fast | block (correctness bug) |
| 2. Roundtrip | **Conformance** | yes | every PR | block (serializer bug) |
| 3. Cross-format equivalence | **Conformance** | yes | every PR | block (mapping bug) |
| 5. Security (fixed attack inputs) | **Conformance** | yes | every PR | block (security bug) |
| 4. Robustness / fuzzing | **Robustness & security** | no (random, long) | nightly / continuous | crash = security bug |
| 6. Performance | **Performance** | no (HW-sensitive) | scheduled, stable HW | investigate regression (threshold-gated) |

**1. Conformance suite (extend the existing one).** Absorbs the deterministic correctness layers — conformance, roundtrip, cross-format equivalence, and the *fixed* security-regression cases (billion-laughs and XXE must be rejected or bounded; alias bombs limited). Roundtrip and cross-format are conformance of the serializer and of the internal model. Fast, deterministic, blocking, runs on every PR.

**2. Robustness & security suite (separate).** Coverage-guided and differential fuzzing (seeded from OSS-Fuzz; differential against a reference or receiving parser) plus polluted-input sets (Pollock). Non-deterministic and long-running, so it runs nightly or continuously, never in the PR gate. A crash, hang, or memory blow-up is a security bug, not a conformance miss.

**3. Performance suite (separate).** Large real-world corpora (The Stack samples, simdjson large files) on stable, isolated hardware, tracking throughput and memory as **trends against a baseline** with regression thresholds. It is a measurement, not an exact-match pass/fail, so it is scheduled and non-blocking.

**One shared corpora store, many consumers.** The suites do not each keep their own copy. `test-corpora/` (§4), pinned by `SOURCES.lock`, is a shared asset: the same large files feed both roundtrip (conformance) and performance; the same security cases feed both the fixed per-PR checks and the fuzz seeds.

**Note — high-assurance / accreditation.** For high-assurance consumers (for example a content guard built on UTL-X), the **robustness & security suite is the primary evidence of parser and serializer hardening**. An accreditor wants its fuzzing campaign and security-corpus results reported *explicitly and reproducibly* — not collapsed into a single green/red conformance tick — so keep this suite first-class and separately reported, with corpora pinned by commit or date so the evidence can be regenerated.

### 5.1 Test layers

| Layer | Input | Pass criterion |
|---|---|---|
| **1. Conformance** | Conformance suites | Valid input parses; invalid input fails with a structured error. Results match expected outputs where provided. |
| **2. Roundtrip** | All corpora | `parse(serialize(parse(x)))` equals `parse(x)` in the UTLX internal model. |
| **3. Cross-format equivalence** | yaml-test-suite JSON equivalents, csv-spectrum JSON, multi-format open data | Different source formats of the same data produce equal internal models. |
| **4. Robustness** | Fuzz corpora, Pollock, malformed files | No crash, hang, or unbounded memory use. Every input yields a result or an error. |
| **5. Security** | XML entity attacks, deep nesting, huge numbers, huge strings | Safe defaults: limits enforced, external entities disabled. |
| **6. Performance** | simdjson and nativejson large files, Stack samples | Throughput and memory tracked over time to catch regressions. |

### 5.2 Roundtrip testing in detail

Roundtrip tests are the most effective way to test serializers at scale, because they need no hand-written expected output.

1. Parse the input into the UTLX model (M1).
2. Serialize M1 to the same format (S1).
3. Parse S1 into a second model (M2).
4. Assert that M1 equals M2.
5. Optionally, serialize M2 to S2 and assert that S1 equals S2 byte for byte (serializer stability).

Then repeat the procedure across formats (JSON → XML → JSON, YAML → JSON → YAML, and so on). Where information is legitimately lost, such as XML attributes versus elements, comments, or YAML anchors, record the expected loss in `known-deviations.yaml` rather than skipping the test.

### 5.3 Format-specific edge cases to cover explicitly

**JSON:** duplicate keys, very large and very small numbers, `-0`, numeric precision beyond IEEE 754 double, lone surrogates in `\u` escapes, BOM handling, deep nesting, trailing commas (must reject in strict mode).

**YAML:** anchors and aliases (including alias bombs), multi-document streams, tags, block versus flow styles, the "Norway problem" (`no` read as boolean in YAML 1.1), indentation edge cases, YAML 1.1 versus 1.2 differences.

**XML:** namespaces and prefix rebinding, mixed content, CDATA, processing instructions, comments, encodings other than UTF-8, entity references, DTD handling, attribute ordering, whitespace preservation.

**CSV:** quoted fields containing delimiters and newlines, escaped quotes, empty versus missing fields, ragged rows, BOM, header versus no header, alternative delimiters (`;`, tab, `|`), mixed line endings.

**OData:** `@odata.*` annotations, `$expand` nesting, `$metadata` CSDL parsing, delta payloads, error payload format, `Edm` type mapping (`Edm.Decimal`, `Edm.DateTimeOffset`, `Edm.Int64` as string).

### 5.4 Synthetic data to fill gaps

Property-based testing tools can generate structured random documents that are guaranteed valid and therefore suit roundtrip testing:

- **Hypothesis** (Python): https://hypothesis.readthedocs.io
- **fast-check** (JavaScript/TypeScript): https://fast-check.dev
- **jqwik** (Java/JVM): https://jqwik.net
- **ScalaCheck** (Scala): https://scalacheck.org

### 5.5 Compositional proof: N readers + N writers, not N² transcoders

Every transcode factors through the UDM — `Fa → reader_a → UDM → writer_b → Fb`. There is no per-pair "json→xml" component: the path `json→xml` is `reader_json` composed with `writer_xml`. Readers and writers are **per-format, not per-pair**, so the proof obligation is the **2N** components, not the **N²** matrix — *provided* the decomposition is actually established. This section is what that takes.

**Roundtrip is necessary but not sufficient.** `parse(serialize(parse(x))) == parse(x)` proves only that `reader_a ∘ writer_a = identity` on the UDM states format *a* can reach — i.e. the `(reader_a, writer_a)` pair is **self-consistent**. It is blind to the three failure modes that cross-format paths expose:

1. **Compensating errors** — if `reader_a` and `writer_a` are both wrong in cancelling ways, roundtrip passes; feeding `writer_a` a UDM from a *different* reader removes the cancellation.
2. **Readers disagreeing on the UDM** — per-format roundtrips never check that `reader_a` and `reader_b` produce the *same* UDM for the same data. If they diverge, both roundtrips are green and `a→b` is still wrong.
3. **Writer coverage gap** — roundtrip exercises `writer_a` only on UDM states `reader_a` can produce. But `a→b` hands `writer_b` a UDM from `reader_a`, which may contain constructs `reader_b` never emits (e.g. an XML attribute node fed to the JSON writer). **Each writer must be proven on the union of what *any* reader can hand it**, not just its own roundtrip range.

**What closes the gap: cross-format equivalence + a golden UDM.** Add layer-3 equivalence (§5.1) anchored to a reference, and the decomposition becomes a real proof:

> If `reader_a(x_a) = reader_b(x_b) = U` (same data → same UDM) and `writer_b(reader_b(x_b)) = x_b` (roundtrip b),
> then `writer_b(reader_a(x_a)) = writer_b(U) = x_b` — the transcode `a→b` is correct.

Equivalence is **transitive through a pivot** (`equiv(a,P) ∧ equiv(b,P) ⇒ equiv(a,b)`), so you need **N−1** equivalences, not N². The strongest form is a **golden cross-format corpus**: one hand-verified canonical UDM rendered correctly in each format. It pins each **reader** and each **writer** to ground truth (defeating compensating errors), tests every writer on the **full shared UDM** (closing the coverage gap), and aligns all formats to one model (making composition valid).

| Approach | Obligations | Sufficient? |
|---|---|---|
| Test every transcode pair directly | **N²** | yes, but does not scale |
| Roundtrips only | N | **no** — blind to the three modes above |
| **Roundtrips + golden / pivot cross-format corpus** | **~2N** (N roundtrips + N−1 equivalences + 1 golden anchor) | **yes**, modulo documented loss |

The N² matrix then drops to a **spot-check** — a few representative pairs end-to-end as integration sanity — not the proof.

**The irreducible part is expressiveness, not combinatorics.** Formats are not equi-expressive (XML attributes / mixed content / comments / namespaces; JSON none of these; YAML anchors / tags / the "Norway problem"). The UDM is the union of what all formats express; `a→b` is faithful only on the **intersection** of "what *a* produces" and "what *b* represents," and outside it there is **legitimate loss** (an XML comment has nowhere to go in JSON). That loss must be **declared** in `known-deviations.yaml` (§4), not discovered — cross-format correctness is always "correct **modulo documented loss**." This is the kernel of "each format needs its own proof": each format needs its own roundtrip, its row in the golden corpus, and its entries in the loss table; it does **not** need a proof per pair.

**Recommended order.**

1. Roundtrips for XML, JSON, YAML (cheap, no oracle — necessary, not sufficient).
2. Build the golden cross-format corpus (the backbone: aligns readers, tests writers on the full UDM, defeats compensating errors).
3. Pick a pivot (or use the golden UDM itself) so equivalence stays N−1.
4. Maintain `known-deviations.yaml` for expressiveness losses — the irreducible per-format work.
5. Spot-check a few N² pairs end-to-end as integration sanity.

A real **mapping** (business logic transforming the UDM in the middle) is proven separately, on the UDM, independent of format; the format-fidelity proof here and the mapping proof **compose** — neither multiplies the other.

---

## 6. Licensing

Corpora come with different licenses, and some (notably The Stack) carry a separate license per file. Before committing any files to the UTLX repository:

- Record the license of each corpus in `SOURCES.lock`.
- Prefer fetching large or mixed-license corpora at test time rather than redistributing them.
- Check W3C test suite licenses, which have their own terms.
- Check that harvested OData reference service responses are allowed for redistribution, or regenerate them during CI.

---

## 7. Quick Start Checklist

_Items map to the three suites of §5.0: correctness → **conformance suite**; fuzzing → **robustness & security suite**; volume and throughput → **performance suite**; all read from the shared `test-corpora/` store._

- [ ] Add JSONTestSuite, yaml-test-suite, W3C XML conformance, and csv-spectrum as layer 1 conformance tests.
- [ ] Implement the generic roundtrip harness (layer 2) and run it over all conformance files.
- [ ] Add yaml-test-suite JSON equivalents and csv-spectrum JSON as the first cross-format tests (layer 3).
- [ ] Build the **golden cross-format corpus** — a hand-verified canonical UDM rendered in XML, JSON and YAML — and seed **`known-deviations.yaml`** with the legitimate per-format losses (attributes vs elements, comments, anchors). This is the backbone that pins every reader and writer to ground truth, tests each writer on the full shared UDM, and makes transcoding a ~2N proof instead of N² (§5.5).
- [ ] Harvest OData reference service fixtures and commit them.
- [ ] Add the W3C XML Schema Test Suite and Pollock for volume and robustness.
- [ ] Set up a fuzz target seeded with OSS-Fuzz corpora.
- [ ] Sample 10,000+ files per format from The Stack v2 for nightly roundtrip runs.
- [ ] Track performance on simdjson large files in CI.
