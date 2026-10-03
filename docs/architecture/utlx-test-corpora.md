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

---

## 6. Licensing

Corpora come with different licenses, and some (notably The Stack) carry a separate license per file. Before committing any files to the UTLX repository:

- Record the license of each corpus in `SOURCES.lock`.
- Prefer fetching large or mixed-license corpora at test time rather than redistributing them.
- Check W3C test suite licenses, which have their own terms.
- Check that harvested OData reference service responses are allowed for redistribution, or regenerate them during CI.

---

## 7. Quick Start Checklist

- [ ] Add JSONTestSuite, yaml-test-suite, W3C XML conformance, and csv-spectrum as layer 1 conformance tests.
- [ ] Implement the generic roundtrip harness (layer 2) and run it over all conformance files.
- [ ] Add yaml-test-suite JSON equivalents and csv-spectrum JSON as the first cross-format tests (layer 3).
- [ ] Harvest OData reference service fixtures and commit them.
- [ ] Add the W3C XML Schema Test Suite and Pollock for volume and robustness.
- [ ] Set up a fuzz target seeded with OSS-Fuzz corpora.
- [ ] Sample 10,000+ files per format from The Stack v2 for nightly roundtrip runs.
- [ ] Track performance on simdjson large files in CI.
