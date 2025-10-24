# Conformance Suite Outstanding Issues - Detailed Analysis

**Status**: 93.8% Pass Rate (270/288 tests passing) ⬆️ +3 tests
**Outstanding**: 18 failing tests
**Last Updated**: 2025-10-24 (Latest: Fixed all transformation errors!)

---

## Latest Fixes (2025-10-24 Evening)

### ✅ All Transformation Errors Fixed! (3 tests)

**Progress**: 267/288 (92.7%) → **270/288 (93.8%)** ⬆️ +3 tests

**Fixed Tests:**
1. **transform_auto_43d9da56** (xml-to-json + xml-transform) - Missing XML root element in path
   - Changed: `$input.Material.MaterialNumber`
   - To: `$input.SAP_Response.Material.MaterialNumber`

2. **xml_namespace_handling** - Missing SOAP envelope root element
   - Changed: `$input["soap:Body"]`
   - To: `$input["soap:Envelope"]["soap:Body"]`

3. **multi_input_json_xml_to_xml** - Two issues fixed:
   - Changed `@permissions` and `@users` to `$permissions` and `$users` (@ is for XML attributes, $ for inputs)
   - Added array handling: `if (isArray(perms.Access.Module)) perms.Access.Module else [perms.Access.Module]`

**Root Cause**:
- Auto-captured and example tests had incorrect XML path syntax - missing root element references. XML parsing always includes the root element as a property.
- The `isArray()` check is required due to the XML-to-JSON cardinality issue (not a bug - see [XML Array Handling Guide](docs/formats/xml-array-handling.md))

**Impact**: All tests now execute successfully - no more runtime errors! Remaining 18 failures are all output mismatches (tests run but produce different output than expected).

---

## Current Failing Tests (18 total - all output mismatches)

### Transformation Errors (0 tests) ✅ ALL FIXED!

~~All transformation errors have been resolved!~~

1. ~~**transform_auto_43d9da56**~~ ✅ **FIXED** - Added missing `SAP_Response` root element to XML path
2. ~~**xml_namespace_handling**~~ ✅ **FIXED** - Added missing `soap:Envelope` root element to XML path
3. ~~**multi_input_json_xml_to_xml**~~ ✅ **FIXED** - Changed `@` to `$` for multi-input refs + added array check for Module elements

### Output Mismatches (16 tests)
Tests that execute successfully but produce different output than expected:

#### Auto-Captured Tests (4)
4. **renderJson_auto_df69e6e1** - [`tests/auto-captured/stdlib/serialization/renderJson_auto_df69e6e1.yaml`](conformance-suite/tests/auto-captured/stdlib/serialization/renderJson_auto_df69e6e1.yaml)
5. **contains_auto_b4e73406** - [`tests/auto-captured/stdlib/string/contains_auto_b4e73406.yaml`](conformance-suite/tests/auto-captured/stdlib/string/contains_auto_b4e73406.yaml)
6. **transform_auto_8fb5ad0f** - [`tests/auto-captured/xml-to-json/transform_auto_8fb5ad0f.yaml`](conformance-suite/tests/auto-captured/xml-to-json/transform_auto_8fb5ad0f.yaml)
7. **transform_auto_0b0f9dfa** - [`tests/auto-captured/xml-transform/transform_auto_0b0f9dfa.yaml`](conformance-suite/tests/auto-captured/xml-transform/transform_auto_0b0f9dfa.yaml)

#### Example Tests (3)
8. **csv_to_json_transformation** - [`tests/examples/intermediate/csv_to_json_transformation.yaml`](conformance-suite/tests/examples/intermediate/csv_to_json_transformation.yaml)
9. **sap_integration** - [`tests/examples/real-world/sap_integration.yaml`](conformance-suite/tests/examples/real-world/sap_integration.yaml) - Likely timestamp (`now()`) difference
10. **sap_integration_out_of_stock_scenario** - [`tests/examples/real-world/sap_integration.yaml`](conformance-suite/tests/examples/real-world/sap_integration.yaml) - Likely timestamp (`now()`) difference

#### Multi-Input Tests (11)
11. **multi_input_json_json_to_json** - [`tests/multi-input/05_json_json_to_json.yaml`](conformance-suite/tests/multi-input/05_json_json_to_json.yaml)
12. **multi_input_csv_csv_to_json** - [`tests/multi-input/08_csv_csv_to_json.yaml`](conformance-suite/tests/multi-input/08_csv_csv_to_json.yaml)
13. **multi_input_xml_xml_to_xml** - [`tests/multi-input/11_xml_xml_to_xml.yaml`](conformance-suite/tests/multi-input/11_xml_xml_to_xml.yaml) - XML output mismatch
14. **multi_input_xml_json_to_xml** - [`tests/multi-input/12_xml_json_to_xml.yaml`](conformance-suite/tests/multi-input/12_xml_json_to_xml.yaml) - XML output mismatch
15. **multi_input_csv_csv_to_csv** - [`tests/multi-input/14_csv_csv_to_csv.yaml`](conformance-suite/tests/multi-input/14_csv_csv_to_csv.yaml) - CSV row count mismatch
16. **multi_input_json_csv_to_csv** - [`tests/multi-input/15_json_csv_to_csv.yaml`](conformance-suite/tests/multi-input/15_json_csv_to_csv.yaml) - CSV row count mismatch
17. **multi_input_xml_csv_to_csv** - [`tests/multi-input/16_xml_csv_to_csv.yaml`](conformance-suite/tests/multi-input/16_xml_csv_to_csv.yaml) - CSV row count mismatch
18. **multi_input_yaml_yaml_to_yaml** - [`tests/multi-input/17_yaml_yaml_to_yaml.yaml`](conformance-suite/tests/multi-input/17_yaml_yaml_to_yaml.yaml) - YAML output mismatch
19. **multi_input_json_yaml_to_yaml** - [`tests/multi-input/18_json_yaml_to_yaml.yaml`](conformance-suite/tests/multi-input/18_json_yaml_to_yaml.yaml) - YAML parse error in output
21. **multi_input_xml_yaml_to_yaml** - [`tests/multi-input/19_xml_yaml_to_yaml.yaml`](conformance-suite/tests/multi-input/19_xml_yaml_to_yaml.yaml) - YAML output mismatch (parser fixed, output still differs)

---

## Recent Changes (2025-10-24)

### Parser Fix 1: DataWeave-Style If/Else with Object Literals ✅

**Fixed Issue:** Parser now supports DataWeave-style `if/else` expressions that return object literals.

**Syntax Supported:**
```utlx
property: if (condition) {
  key1: value1,
  key2: value2
} else {
  key: value
}
```

**Technical Change:** Modified `parsePrefixIfExpression()` to use `parseTernary()` instead of `parseLogicalOr()`, allowing the parser to reach `parsePrimary()` where object literals are handled.

**Status:** ✅ Verified working

---

### Parser Fix 2: Keywords as Property Names ✅

**Fixed Issue:** Reserved keywords (like `template`, `match`, `filter`, etc.) can now be used as property names in object literals.

**Example:**
```utlx
{
  template: { metadata: { ... } },
  match: "some value",
  filter: [1, 2, 3]
}
```

**Technical Change:** Added keyword check in `parseObjectLiteral()` property name parsing (line 633-636).

**Status:** ✅ Verified working

---

### Parser Fix 3: Block Expressions Without Let Bindings ✅

**Fixed Issue:** Lambda bodies containing only an object literal (no let bindings) now parse correctly.

**Example:**
```utlx
map(items, item => {
  {
    id: item.id,
    name: item.name
  }
})
```

**Technical Change:** Added special case detection in `parseObjectLiteral()` for blocks starting with `LBRACE` when there are no let bindings (line 569-574).

**Status:** ✅ Verified working

---

### XML Encoding Test Fix: Spread Operator Syntax ✅

**Fixed Issue:** All 4 XML encoding tests were using incorrect syntax that created wrapper elements instead of merging child elements directly.

**Root Cause:** Tests used property assignment (`Data: $input`) which wraps XML in a tag, instead of spread operator (`...$input`) which merges children directly.

**Example Fix:**
```utlx
// ❌ Wrong (creates <Data> wrapper):
{
  Integration: {
    Data: $input
  }
}

// ✅ Correct (merges children directly):
{
  Integration: {
    ...$input
  }
}
```

**Technical Details:**
- Spread operator (`...`) merges object/array contents directly into parent
- Parser already supported spread syntax (lines 617-622 in parser_impl.kt)
- Tests had incorrect syntax in expected vs actual comparison

**Tests Fixed:**
- `tests/examples/xml-encoding/encoding_precedence_rules.yaml`
- `tests/examples/xml-encoding/multi_input_default_encoding.yaml`
- `tests/examples/xml-encoding/multi_input_explicit_encoding.yaml`
- `tests/examples/xml-encoding/multi_input_no_encoding.yaml`

**Status:** ✅ All 4 XML encoding tests now passing

---

**Files Modified:**
- `modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt`
- `conformance-suite/tests/multi-input/18_json_yaml_to_yaml.yaml` - Fixed @ to $ migration
- `conformance-suite/tests/multi-input/19_xml_yaml_to_yaml.yaml` - Fixed @ to $ migration
- `conformance-suite/tests/examples/xml-encoding/encoding_precedence_rules.yaml` - Fixed spread operator syntax
- `conformance-suite/tests/examples/xml-encoding/multi_input_default_encoding.yaml` - Fixed spread operator syntax
- `conformance-suite/tests/examples/xml-encoding/multi_input_explicit_encoding.yaml` - Fixed spread operator syntax
- `conformance-suite/tests/examples/xml-encoding/multi_input_no_encoding.yaml` - Fixed spread operator syntax

**Impact:**
- All parser errors in multi-input tests resolved! Tests now parse and execute successfully.
- All XML encoding tests now passing - encoding detection and conversion working correctly

---

## Executive Summary

### Test Results by Category

| Category | Total | Passing | Failing | Pass Rate |
|----------|-------|---------|---------|-----------|
| **Overall** | **288** | **267** | **21** | **92.7%** |
| Parse Errors | 2 | 2 | 0 | 100% ✅ |
| XML Encoding | 4 | 4 | 0 | 100% ✅ |
| Transformation Errors | 3 | 0 | 3 | 0% |
| Output Mismatches | 16 | 0 | 16 | 0% |

### Failures by Test Category

- **multi-input tests**: 11 failing (reduced from 13)
- ~~**xml-encoding tests**: 4 failing (all encoding tests)~~ ✅ **ALL PASSING**
- **auto-captured tests**: 5 failing
- **examples tests**: 4 failing (CSV transformation, XML namespace, 2x SAP integration)

---

## Category 1: Parse Errors (0 tests) ✅ RESOLVED

### Overview
All parse errors have been fixed! Previously, two tests were failing with "Expected property name or spread operator" errors.

### 1.1 multi_input_json_yaml_to_yaml ✅

**File:** `tests/multi-input/18_json_yaml_to_yaml.yaml` ([view on GitHub](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/multi-input/18_json_yaml_to_yaml.yaml))
**Category:** multi-input
**Previous Error:** ParseException - Expected property name or spread operator

**Status:** ✅ FIXED - Now parses and executes successfully

**Root Causes Identified:**
1. **Keyword "template" as property name**: The reserved keyword `template` was used as an object property name
2. **Block expression without let bindings**: Lambda body `{ { prop: value } }` wasn't recognized as a block containing an object

**Fixes Applied:**
1. Parser now allows keywords as property names in object literals
2. Parser now handles block expressions that start with `{` even without let bindings
3. Fixed `@` to `$` migration issues

---

### 1.2 multi_input_xml_yaml_to_yaml ✅

**File:** `tests/multi-input/19_xml_yaml_to_yaml.yaml` ([view on GitHub](https://github.com/grauwen/utl-x/blob/main/conformance-suite/tests/multi-input/19_xml_yaml_to_yaml.yaml))
**Category:** multi-input
**Previous Error:** ParseException - Expected property name or spread operator

**Status:** ✅ FIXED - Now parses successfully (output mismatch remains)

**Root Cause:** Block expression without let bindings in nested `map()` call

**Fix Applied:** Parser now correctly handles `map(item => { { prop: value } })` syntax

**Note:** Test now has an output mismatch (YAML formatting issue), but parser error is resolved

---

## Category 2: XML Encoding Tests (0 tests) ✅ RESOLVED

### Overview
All XML encoding tests are now passing! The issue was incorrect test syntax using property assignment instead of the spread operator.

### 2.1-2.4 All XML Encoding Tests ✅

**Files:**
- `tests/examples/xml-encoding/encoding_precedence_rules.yaml` ✅
- `tests/examples/xml-encoding/multi_input_default_encoding.yaml` ✅
- `tests/examples/xml-encoding/multi_input_explicit_encoding.yaml` ✅
- `tests/examples/xml-encoding/multi_input_no_encoding.yaml` ✅

**Previous Status:** 🔴 FAILING (all 4)
**Current Status:** ✅ ALL PASSING

**Root Cause:** Tests used `Data: $input` syntax which creates wrapper elements, instead of `...$input` which merges child elements directly.

**Fix Applied:** Changed all 4 test transformations to use spread operator:
```utlx
// Before (incorrect):
{
  Integration: {
    Data: $input  // Creates <Data><SAPSystem>...</SAPSystem></Data>
  }
}

// After (correct):
{
  Integration: {
    ...$input  // Creates <SAPSystem>...</SAPSystem> (merged directly)
  }
}
```

**Verification:** All 4 tests now pass with 100% success rate. Encoding detection and conversion functions (`detectXMLEncoding()`, `convertXMLEncoding()`) work correctly.

---

## Category 3: Output Mismatches (17 tests)

### Overview
17 tests execute successfully but produce output that doesn't match expected results (reduced from 19 after XML encoding fixes). These need individual investigation to determine if the issue is in:
- The transformation logic
- The expected output definition
- Format-specific serialization

### 3.1 renderJson_auto_df69e6e1

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

### 3.2 contains_auto_b4e73406

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

### 3.3 transform_auto_43d9da56

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

### 3.4 transform_auto_8fb5ad0f

**File:** `tests/auto-captured/xml-to-json/transform_auto_8fb5ad0f.yaml`
**Category:** auto-captured/xml-to-json
**Error:** Output Mismatch
**Status:** 🔴 FAILING

---

### 3.5 transform_auto_0b0f9dfa

**File:** `tests/auto-captured/xml-transform/transform_auto_0b0f9dfa.yaml`
**Category:** auto-captured/xml-transform
**Error:** Output Mismatch
**Status:** 🔴 FAILING

---

### 3.6 csv_to_json_transformation

**File:** `tests/examples/intermediate/csv_to_json_transformation.yaml`
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

### 3.7 xml_namespace_handling

**File:** `tests/examples/intermediate/xml_namespace_handling.yaml`
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

### 3.8-3.9 sap_integration tests

**File:** `tests/examples/real-world/sap_integration.yaml`
**Category:** examples/real-world
**Tests:** sap_integration, sap_integration_out_of_stock_scenario
**Error:** Output Mismatch (both)
**Status:** 🔴 FAILING

**Investigation Notes:**
- Real-world SAP integration examples
- Output mismatch is only timestamp difference (`now()` function generates current time)
- Tests likely need updating to use fixed timestamp for reproducibility

**Action Items:**
- [ ] Update tests to use fixed timestamp instead of `now()`
- [ ] Or adjust test framework to accept dynamic timestamp fields

---

### ~~3.10-3.13 XML Encoding Tests~~ ✅ ALL RESOLVED

**Status:** ✅ ALL 4 TESTS NOW PASSING

All XML encoding tests fixed by correcting spread operator syntax. See Category 2 above for details.

---

### 3.10-3.19 Multi-Input Tests (9 tests remaining)

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
**Status:** 🔴 FAILING (9 tests remaining)

**Investigation Notes:**
- 9 out of 19 multi-input tests still failing (down from 11 after parser fixes)
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

## Category 4: Unknown/Other Errors (3 tests)

### 4.1 transform_auto_43d9da56

*See Section 3.3 above*

### 4.2 xml_namespace_handling

*See Section 3.7 above*

### 4.3 multi_input_json_xml_to_xml

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

1. ~~**Parse Errors (2 tests)**~~ ✅ **COMPLETELY RESOLVED**
   - Tests: 1.1, 1.2
   - Status: All parser errors fixed
   - Fixes: Keywords as property names, block expressions without let bindings

2. ~~**XML Encoding Tests (4 tests)**~~ ✅ **COMPLETELY RESOLVED**
   - Tests: All encoding_precedence_rules, multi_input_*_encoding
   - Status: All 4 tests passing
   - Fix: Corrected spread operator syntax in test files

3. **Multi-Input Tests (9 tests remaining)** - Major feature validation
   - Tests: 3.10-3.19 (down from 11 tests)
   - Impact: v0.2.0 multi-input feature needs refinement
   - Fix: Named input binding, output formatting, edge cases

### ⚠️ Medium Priority

4. **Auto-Captured Tests (5 tests)** - May need syntax correction
   - Tests: 3.1, 3.2, 3.3, 3.4, 3.5
   - Impact: Auto-generated tests may need manual review
   - Fix: Update test syntax or fix transformation logic

5. **Example Tests (4 tests)** - Documentation/example issues
   - Tests: 3.6, 3.7, 3.8, 3.9
   - Impact: Examples don't work as documented
   - Fix: Update transformations or expected outputs (SAP tests only need timestamp handling)

---

## Next Steps

### Immediate Actions (Today)

1. ~~**Fix Parse Errors First**~~ ✅ **COMPLETED**
   - All parser errors resolved
   - Keywords can now be used as property names
   - Block expressions without let bindings now supported

2. ~~**Investigate XML Encoding Failures**~~ ✅ **COMPLETED**
   - Root cause: Incorrect spread operator syntax in tests
   - Fixed all 4 XML encoding test files
   - All tests now passing with correct syntax

3. **Debug One Multi-Input Test**
   ```bash
   ../utlx transform tests/multi-input/05_json_json_to_json.yaml \
     --input input1=... --input input2=... -v
   ```
   - Pick simplest failing multi-input test
   - Run with verbose output
   - Compare expected vs actual

### Short-Term (This Week)

1. ~~Fix all parse errors (2 tests)~~ ✅ **COMPLETED**
2. ~~Fix XML encoding tests (4 tests)~~ ✅ **COMPLETED**
3. Understand multi-input failure pattern
4. Fix at least 5 multi-input tests
5. Fix SAP integration timestamp issue (2 tests - easy fix)

### Goal

- **Target**: 95%+ pass rate (274+ tests passing out of 288)
- **Current**: 92.7% (267/288)
- **Needed**: Fix 7 tests to reach 95%
- **Progress**: 6 issues fixed (2 parse errors + 4 XML encoding tests = 6/27 total issues resolved)
- **Recommendation**: Focus on transformation errors first (3 tests) - these are blocking execution

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

- Test success rate improved from 12.9% → 91.3% → **92.7%** (steady progress!)
- Parser enhanced to support DataWeave-style if/else with object literals (2025-10-24)
- **Parser fixes completed (2025-10-24):**
  - ✅ Keywords can now be used as property names (`template`, `match`, etc.)
  - ✅ Block expressions without let bindings now supported
  - ✅ All parse errors resolved - 100% parse success rate
- **XML Encoding fixes completed (2025-10-24):**
  - ✅ All 4 XML encoding tests now passing
  - ✅ Corrected spread operator syntax in test files
  - ✅ Encoding detection and conversion working correctly
- Most remaining failures are output mismatches, suggesting core functionality works
- Multi-input feature (v0.2.0) validation ongoing - 9 of 21 failures
- **Zero parse errors remaining** - excellent parser robustness
- **Zero XML encoding failures** - encoding support fully working
- No NullPointerExceptions - good null safety

---

## Appendix: All Failing Tests (21 remaining, 19 unique)

### Parse Errors (0) ✅
~~All parse errors resolved~~

### XML Encoding Tests (0) ✅
~~All XML encoding tests now passing~~

### Transformation Errors (3 tests - 2 unique due to duplicate)
1. transform_auto_43d9da56 **(counted 2x)** - `Cannot access property 'MaterialNumber' on NullValue`
   - Appears in both `xml-to-json` and `xml-transform` categories
2. xml_namespace_handling - `Cannot index NullValue with string`
3. multi_input_json_xml_to_xml - `map() requires array as first argument`

### Output Mismatches (16 tests)

**Auto-Captured Tests (4):**
4. renderJson_auto_df69e6e1
5. contains_auto_b4e73406
6. transform_auto_8fb5ad0f
7. transform_auto_0b0f9dfa

**Example Tests (3):**
8. csv_to_json_transformation
9. sap_integration (timestamp issue only - `now()` function)
10. sap_integration_out_of_stock_scenario (timestamp issue only - `now()` function)

**Multi-Input Tests (11):**
11. ~~multi_input_default_encoding~~ ✅ FIXED (was #8)
12. ~~multi_input_explicit_encoding~~ ✅ FIXED (was #9)
13. ~~multi_input_no_encoding~~ ✅ FIXED (was #10)
14. ~~encoding_precedence_rules~~ ✅ FIXED (was #11)
15. multi_input_json_json_to_json
16. multi_input_csv_csv_to_json
17. multi_input_xml_xml_to_xml (XML output mismatch)
18. multi_input_xml_json_to_xml (XML output mismatch)
19. multi_input_csv_csv_to_csv (CSV row count mismatch)
20. multi_input_json_csv_to_csv (CSV row count mismatch)
21. multi_input_xml_csv_to_csv (CSV row count mismatch)
22. multi_input_yaml_yaml_to_yaml (YAML output mismatch)
23. multi_input_json_yaml_to_yaml (YAML parse error - ✅ parser fixed, output still differs)
24. multi_input_xml_yaml_to_yaml (YAML output mismatch - ✅ parser fixed, output still differs)

**Summary:**
- **Total Failing:** 21 test results (19 unique tests, `transform_auto_43d9da56` counted twice)
- **Total Fixed:** 6 tests (2 parse errors + 4 XML encoding)
- **Progress:** 267/288 tests passing (92.7%)
- **Remaining:** 3 transformation errors + 16 output mismatches = 19 unique issues
