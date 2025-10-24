# Conformance Suite Outstanding Issues - Detailed Analysis

**Status**: 91.6% Pass Rate (262/286 tests passing)
**Outstanding**: 24 failing tests
**Last Updated**: 2025-10-24

---

## Executive Summary

### Test Results by Category

| Category | Total | Passing | Failing | Pass Rate |
|----------|-------|---------|---------|-----------|
| **Overall** | **286** | **262** | **24** | **91.6%** |
| Parse Errors | 2 | 0 | 2 | 0% |
| Output Mismatches | 19 | 0 | 19 | 0% |
| Unknown/Other | 3 | 0 | 3 | 0% |

### Failures by Test Category

- **multi-input tests**: 13 failing (most of multi-input suite)
- **xml-encoding tests**: 4 failing (all encoding tests)
- **auto-captured tests**: 5 failing
- **examples tests**: 2 failing

---

## Category 1: Parse Errors (2 tests)

### Overview
Two tests failing with identical parse errors: "Expected property name or spread operator"

### 1.1 multi_input_json_yaml_to_yaml

**File:** `tests/multi-input/18_json_yaml_to_yaml.yaml` ([view on GitHub](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/multi-input/18_json_yaml_to_yaml.yaml))
**Category:** multi-input
**Error:** ParseException - Expected property name or spread operator
**Location:** Line 26, Column 9 (approx)

**Status:** 🔴 FAILING

**Error Details:**
```
org.apache.utlx.core.parser.ParseException: Expected property name or spread operator
	at org.apache.utlx.core.parser.Parser.parseObjectLiteral(parser_impl.kt:634)
```

**Investigation Notes:**
- Parse error occurs in object literal parsing
- Likely related to spread operator (`...`) or property name syntax
- May be an issue with the auto-captured transformation syntax
- Location: parser_impl.kt:634 in parseObjectLiteral method

**Potential Causes:**
1. Invalid spread operator usage in object literal
2. Missing property name before colon
3. Reserved keyword used as property name without quotes
4. Syntax not properly migrated during `@input` → `$input` migration

**Action Items:**
- [ ] Read the test file at line 26
- [ ] Check transformation syntax around object literal construction
- [ ] Verify spread operator usage is correct
- [ ] Test with minimal reproduction case

---

### 1.2 multi_input_xml_yaml_to_yaml

**File:** `tests/multi-input/19_xml_yaml_to_yaml.yaml` ([view on GitHub](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/multi-input/19_xml_yaml_to_yaml.yaml))
**Category:** multi-input
**Error:** ParseException - Expected property name or spread operator
**Location:** Line 32, Column 13 (approx)

**Status:** 🔴 FAILING

**Error Details:**
```
org.apache.utlx.core.parser.ParseException: Expected property name or spread operator
	at org.apache.utlx.core.parser.Parser.parseObjectLiteral(parser_impl.kt:634)
```

**Investigation Notes:**
- Identical error to test 1.1, different location
- Both failures are in YAML output tests
- Same parser location (parseObjectLiteral:634)

**Potential Causes:**
- Same as 1.1 - likely same root cause
- May be a pattern in how YAML multi-input tests are structured

**Action Items:**
- [ ] Compare with test 1.1 to identify common pattern
- [ ] Check if YAML-specific syntax causes the issue
- [ ] Review parser's handling of nested object literals

---

## Category 2: Output Mismatches (19 tests)

### Overview
19 tests execute successfully but produce output that doesn't match expected results. These need individual investigation to determine if the issue is in:
- The transformation logic
- The expected output definition
- Format-specific serialization

### 2.1 renderJson_auto_df69e6e1

**File:** `tests/auto-captured/stdlib/serialization/renderJson_auto_df69e6e1.yaml` ([view on GitHub](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/auto-captured/stdlib/serialization/renderJson_auto_df69e6e1.yaml))
**Category:** stdlib/serialization
**Error:** Output Mismatch

**Status:** 🔴 FAILING

**Investigation Notes:**
- Auto-captured test for `renderJson` stdlib function
- Transformation executes but output doesn't match expected
- May be related to JSON formatting differences (whitespace, order, etc.)

**Action Items:**
- [ ] Run test with `--verbose` to see actual vs expected output
- [ ] Check if issue is formatting (pretty-print, spacing) or structural
- [ ] Verify renderJson function behavior

---

### 2.2 contains_auto_b4e73406

**File:** `tests/auto-captured/stdlib/string/contains_auto_b4e73406.yaml` ([view on GitHub](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/auto-captured/stdlib/string/contains_auto_b4e73406.yaml))
**Category:** stdlib/string
**Error:** Output Mismatch

**Status:** 🔴 FAILING

**Investigation Notes:**
- Auto-captured test for `contains` string function
- Likely a simple boolean or string comparison issue

**Action Items:**
- [ ] Check actual vs expected output
- [ ] Verify contains() function logic
- [ ] Check if case sensitivity or null handling is the issue

---

### 2.3 transform_auto_43d9da56

**File:** `tests/auto-captured/xml-to-json/transform_auto_43d9da56.yaml` ([view on GitHub](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/auto-captured/xml-to-json/transform_auto_43d9da56.yaml))
**Category:** xml-to-json
**Error:** Unknown/Other

**Status:** 🔴 FAILING

**Investigation Notes:**
- XML to JSON transformation
- Error type unclear - needs detailed investigation

**Action Items:**
- [ ] Get detailed error message
- [ ] Check transformation execution
- [ ] Verify XML parsing and JSON serialization

---

### 2.4 transform_auto_8fb5ad0f

**File:** `tests/auto-captured/xml-to-json/transform_auto_8fb5ad0f.yaml` ([view on GitHub](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/auto-captured/xml-to-json/transform_auto_8fb5ad0f.yaml))
**Category:** auto-captured/xml-to-json
**Error:** Output Mismatch

**Status:** 🔴 FAILING

---

### 2.5 transform_auto_0b0f9dfa

**File:** `tests/auto-captured/xml-transform/transform_auto_0b0f9dfa.yaml` ([view on GitHub](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/auto-captured/xml-transform/transform_auto_0b0f9dfa.yaml))
**Category:** auto-captured/xml-transform
**Error:** Output Mismatch

**Status:** 🔴 FAILING

---

### 2.6 csv_to_json_transformation

**File:** `tests/examples/intermediate/csv_to_json_transformation.yaml` ([view on GitHub](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/examples/intermediate/csv_to_json_transformation.yaml))
**Category:** examples/intermediate
**Error:** Output Mismatch

**Status:** 🔴 FAILING

**Investigation Notes:**
- Example test showing CSV to JSON transformation
- May be CSV parsing issue (headers, delimiters, escaping)
- Or JSON output formatting issue

**Action Items:**
- [ ] Check CSV parsing logic
- [ ] Verify header handling
- [ ] Compare actual vs expected JSON structure

---

### 2.7 xml_namespace_handling

**File:** `tests/examples/intermediate/xml_namespace_handling.yaml` ([view on GitHub](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/examples/intermediate/xml_namespace_handling.yaml))
**Category:** examples/intermediate
**Error:** Unknown/Other

**Status:** 🔴 FAILING

**Investigation Notes:**
- Namespace handling is complex in XML
- May be namespace prefix or URI resolution issue

**Action Items:**
- [ ] Get detailed error
- [ ] Check namespace declaration parsing
- [ ] Verify namespace-aware path selection

---

### 2.8-2.9 sap_integration tests

**File:** `tests/examples/real-world/sap_integration.yaml` ([view on GitHub](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/examples/real-world/sap_integration.yaml))
**Category:** examples/real-world
**Tests:** sap_integration, sap_integration_out_of_stock_scenario
**Error:** Output Mismatch (both)

**Status:** 🔴 FAILING

**Investigation Notes:**
- Real-world SAP integration examples
- Likely complex multi-input, multi-format scenario
- Two test scenarios in same file

**Action Items:**
- [ ] Review SAP integration transformation logic
- [ ] Check data mapping correctness
- [ ] Verify both scenarios independently

---

### 2.10-2.13 XML Encoding Tests

**Files:**
- `tests/examples/xml-encoding/encoding_precedence_rules.yaml` ([view](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/examples/xml-encoding/encoding_precedence_rules.yaml))
- `tests/examples/xml-encoding/multi_input_default_encoding.yaml` ([view](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/examples/xml-encoding/multi_input_default_encoding.yaml))
- `tests/examples/xml-encoding/multi_input_explicit_encoding.yaml` ([view](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/examples/xml-encoding/multi_input_explicit_encoding.yaml))
- `tests/examples/xml-encoding/multi_input_no_encoding.yaml` ([view](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/examples/xml-encoding/multi_input_no_encoding.yaml))

**Category:** xml-encoding
**Error:** Output Mismatch (all 4)

**Status:** 🔴 FAILING

**Investigation Notes:**
- ALL XML encoding tests are failing
- These test encoding detection, conversion, and preservation
- Critical for multi-input XML handling with different encodings
- May be related to:
  - `detectXMLEncoding()` function
  - `convertXMLEncoding()` function
  - BOM (Byte Order Mark) handling
  - Encoding declaration in XML prolog

**Potential Causes:**
1. Encoding detection returning wrong encoding name
2. Encoding conversion not applied correctly
3. Output encoding not matching expected
4. BOM not being stripped/added correctly

**Action Items:**
- [ ] Test encoding detection functions in isolation
- [ ] Check if encoding conversion functions work
- [ ] Verify XML prolog encoding declaration parsing
- [ ] Test with actual ISO-8859-1 and UTF-16 files

---

### 2.14-2.22 Multi-Input Tests (9 tests)

**Files:**
- `tests/multi-input/05_json_json_to_json.yaml` ([view](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/multi-input/05_json_json_to_json.yaml))
- `tests/multi-input/08_csv_csv_to_json.yaml` ([view](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/multi-input/08_csv_csv_to_json.yaml))
- `tests/multi-input/11_xml_xml_to_xml.yaml` ([view](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/multi-input/11_xml_xml_to_xml.yaml))
- `tests/multi-input/12_xml_json_to_xml.yaml` ([view](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/multi-input/12_xml_json_to_xml.yaml))
- `tests/multi-input/13_json_xml_to_xml.yaml` ([view](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/multi-input/13_json_xml_to_xml.yaml)) (Unknown/Other)
- `tests/multi-input/14_csv_csv_to_csv.yaml` ([view](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/multi-input/14_csv_csv_to_csv.yaml))
- `tests/multi-input/15_json_csv_to_csv.yaml` ([view](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/multi-input/15_json_csv_to_csv.yaml))
- `tests/multi-input/16_xml_csv_to_csv.yaml` ([view](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/multi-input/16_xml_csv_to_csv.yaml))
- `tests/multi-input/17_yaml_yaml_to_yaml.yaml` ([view](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/multi-input/17_yaml_yaml_to_yaml.yaml))

**Category:** multi-input
**Error:** Output Mismatch (8), Unknown/Other (1)

**Status:** 🔴 FAILING

**Investigation Notes:**
- 9 out of ~19 multi-input tests failing
- Tests cover various format combinations
- These test the multi-input feature implemented in v0.2.0
- Most are output mismatches, suggesting transformation logic works but output formatting is wrong

**Common Pattern:**
- All involve multiple named inputs (`$input1`, `$input2`, etc.)
- Various format combinations (JSON+JSON, XML+JSON, CSV+CSV, YAML+YAML)
- May be related to how named inputs are accessed or how output is formatted

**Potential Causes:**
1. Named input binding not working correctly (`$input1` vs `$input`)
2. Multi-input CLI argument parsing issue
3. Format-specific serialization differences
4. Expected output format not matching actual multi-input behavior

**Action Items:**
- [ ] Test one failing multi-input test in detail
- [ ] Verify named input binding in interpreter
- [ ] Check CLI `--input name=file` argument handling
- [ ] Compare with passing multi-input tests to find pattern

---

## Category 3: Unknown/Other Errors (3 tests)

### 3.1 transform_auto_43d9da56

*See Section 2.3 above*

### 3.2 xml_namespace_handling

*See Section 2.7 above*

### 3.3 multi_input_json_xml_to_xml

**File:** `tests/multi-input/13_json_xml_to_xml.yaml` ([view on GitHub](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/multi-input/13_json_xml_to_xml.yaml))
**Category:** multi-input
**Error:** Unknown/Other

**Status:** 🔴 FAILING

**Investigation Notes:**
- Multi-input test combining JSON and XML to XML output
- Error type not clearly categorized
- Needs detailed error output

**Action Items:**
- [ ] Run test individually to get full error message
- [ ] Determine if parse error, runtime error, or output mismatch
- [ ] Check JSON+XML combination handling

---

## Summary by Priority

### 🔥 High Priority (Critical Path)

1. **Parse Errors (2 tests)** - Blocking basic functionality
   - Tests: 1.1, 1.2
   - Impact: Tests cannot execute at all
   - Fix: Parser logic in parseObjectLiteral()

2. **XML Encoding Tests (4 tests)** - Core feature not working
   - Tests: 2.10-2.13
   - Impact: Multi-input XML encoding feature broken
   - Fix: Encoding detection/conversion functions

3. **Multi-Input Tests (13 tests)** - Major feature failures
   - Tests: 2.14-2.22, 1.1, 1.2
   - Impact: v0.2.0 multi-input feature has issues
   - Fix: Named input binding, output formatting

### ⚠️ Medium Priority

4. **Auto-Captured Tests (5 tests)** - May need syntax correction
   - Tests: 2.1, 2.2, 2.3, 2.4, 2.5
   - Impact: Auto-generated tests may need manual review
   - Fix: Update test syntax or fix transformation logic

5. **Example Tests (2 tests)** - Documentation/example issues
   - Tests: 2.6, 2.7, 2.8, 2.9
   - Impact: Examples don't work as documented
   - Fix: Update transformations or expected outputs

---

## Next Steps

### Immediate Actions (Today)

1. **Fix Parse Errors First**
   ```bash
   ../utlx transform tests/multi-input/18_json_yaml_to_yaml.yaml
   ```
   - Read failing test files (1.1, 1.2)
   - Identify exact syntax causing parse failure
   - Fix parser or update test syntax

2. **Investigate XML Encoding Failures**
   ```bash
   ../utlx transform tests/examples/xml-encoding/encoding_precedence_rules.yaml
   ```
   - Test encoding detection functions
   - Verify encoding conversion logic
   - Check expected vs actual output

3. **Debug One Multi-Input Test**
   ```bash
   ../utlx transform tests/multi-input/05_json_json_to_json.yaml \
     --input input1=... --input input2=... -v
   ```
   - Pick simplest failing multi-input test
   - Run with verbose output
   - Compare expected vs actual

### Short-Term (This Week)

1. Fix all parse errors (2 tests)
2. Fix XML encoding tests (4 tests)
3. Understand multi-input failure pattern
4. Fix at least 5 multi-input tests

### Goal

- **Target**: 95%+ pass rate (271+ tests passing)
- **Current**: 91.6% (262/286)
- **Needed**: Fix 9 tests to reach 95%

---

## Test Execution Commands

### Run All Tests
```bash
cd conformance-suite
python3 runners/cli-runner/simple-runner.py
```

### Run with Failures Shown
```bash
python3 runners/cli-runner/simple-runner.py --show-failures
```

### Run Specific Test Category
```bash
python3 runners/cli-runner/simple-runner.py --filter multi-input
```

### Run Individual Test
```bash
cd ..
./utlx transform conformance-suite/tests/multi-input/18_json_yaml_to_yaml.yaml
```

---

## Related Files

- **Parser**: `modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt`
  - Line 634: parseObjectLiteral() - where parse errors occur
- **Lexer**: `modules/core/src/main/kotlin/org/apache/utlx/core/lexer/lexer_impl.kt`
- **Interpreter**: `modules/core/src/main/kotlin/org/apache/utlx/core/interpreter/interpreter.kt`
  - Lines 133-140: Input binding logic
- **CLI Transform**: `modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/TransformCommand.kt`
  - Multi-input argument handling
- **XML Encoding**: `stdlib/src/main/kotlin/org/apache/utlx/stdlib/xml/`
  - Encoding detection and conversion functions

---

## Notes

- Test success rate improved from 12.9% to 91.6% after `@input` → `$input` migration
- Most failures are output mismatches, suggesting core functionality works
- Multi-input feature (v0.2.0) needs attention - 13 of 24 failures
- All 4 XML encoding tests failing - critical gap in encoding support
- Only 2 parse errors - relatively clean parser implementation
- No NullPointerExceptions - good null safety

---

## Appendix: All Failing Tests

### Parse Errors (2)
1. multi_input_json_yaml_to_yaml
2. multi_input_xml_yaml_to_yaml

### Output Mismatches (19)
3. renderJson_auto_df69e6e1
4. contains_auto_b4e73406
5. transform_auto_8fb5ad0f
6. transform_auto_0b0f9dfa
7. csv_to_json_transformation
8. sap_integration
9. sap_integration_out_of_stock_scenario
10. encoding_precedence_rules
11. multi_input_default_encoding
12. multi_input_explicit_encoding
13. multi_input_no_encoding
14. multi_input_json_json_to_json
15. multi_input_csv_csv_to_json
16. multi_input_xml_xml_to_xml
17. multi_input_xml_json_to_xml
18. multi_input_csv_csv_to_csv
19. multi_input_json_csv_to_csv
20. multi_input_xml_csv_to_csv
21. multi_input_yaml_yaml_to_yaml

### Unknown/Other (3)
22. transform_auto_43d9da56
23. xml_namespace_handling
24. multi_input_json_xml_to_xml
