# Editor Tests

Playwright tests for UTL-X editor functionality, format strategies, and format detection.

## Test Files

### `format-detection.spec.ts`
Tests the format detection indicators that appear in the input panel when loading files.

**What it tests:**
- ✓ Format (green) - Format matches declared format
- ⚠ Format (red) - Format mismatch detected
- ? Format (yellow) - Format could not be auto-detected

**Coverage:**
- All 8 formats: XML, XSD, JSON, JSCH, YAML, CSV, Avro, Proto
- Edge cases: XML with comments, CSV with various delimiters, XSD vs XML confusion
- Format indicator updates when format changes

### `format-strategies.spec.ts`
Tests that each format has its own dedicated parsing strategy and correctly transforms content.

**What it tests:**
- Format-specific UDM tree generation
- Round-trip transformations (input → UDM → output)
- Cross-format transformations (CSV → XML, JSON → XML, etc.)
- Error handling for malformed input
- Hyphenated input names (e.g., `$input-csv`, `$my-data`)

**Coverage:**
- XML: attributes, namespaces, mixed content, CDATA
- JSON: nested objects, arrays, type preservation
- CSV: delimiters, headers, array structure
- YAML: nesting, arrays, multi-line strings
- XSD: schema definitions, complex types
- JSCH: JSON Schema with $schema, properties, definitions
- Avro: record types, fields, protocols
- Proto: proto2/proto3, messages, packages

### `test-data.ts`
Provides sample content for all format types to use in tests.

**Includes:**
- Valid content for each format
- Edge cases (comments, DOCTYPE, various delimiters)
- Expected detection results

## Running Tests

### Run all editor tests
```bash
cd /Users/magr/data/mapping/github-git/utl-x/conformance-suite/theia-extension
npm test tests/editor
```

### Run specific test file
```bash
npm test tests/editor/format-detection.spec.ts
npm test tests/editor/format-strategies.spec.ts
```

### Run with UI (headed mode)
```bash
npm test tests/editor -- --headed
```

### Debug a specific test
```bash
npm test tests/editor/format-detection.spec.ts -- --debug
```

## Test Status

**Current Status:** Test structure created with TODO placeholders

**Next Steps:**
1. Implement test helpers to:
   - Open input panel programmatically
   - Load content/files into inputs
   - Access format indicators and UDM status
   - Execute transformations
   - Verify output

2. Implement actual test logic for each TODO

3. Add test fixtures for complex real-world examples

4. Run tests against all 24 YAML examples in `/examples/yaml/`

## Key Test Scenarios

### Critical Bugs to Prevent

1. **Namespace Pollution (Fixed)**
   - XML with namespaces should only declare them on root
   - Child elements should NOT repeat `xmlns` declarations
   - Test: `format-strategies.spec.ts` → "XML round-trip should preserve structure without namespace pollution"

2. **Hyphen Support (Fixed)**
   - Input names with hyphens should parse correctly
   - Test: `format-strategies.spec.ts` → "Input names with hyphens should parse correctly"

3. **XSD vs XML Confusion (Fixed)**
   - Files with `xmlns:xsi` are regular XML (for validation)
   - Only files with `xs:schema` or `xsd:schema` are XSD
   - Test: `format-detection.spec.ts` → "Regular XML with xmlns:xsi should NOT be detected as XSD"

4. **CSV to XML Array Handling**
   - CSV (array) → XML should not create multiple `<root>` elements
   - Must wrap array in object: `{ Employees: $input-csv }`
   - Test: `format-strategies.spec.ts` → "CSV to XML should wrap array in root element"

### Format Detection Edge Cases

- XML with leading comments
- XML with DOCTYPE declarations
- CSV with semicolon, tab, or pipe delimiters
- JSCH vs regular JSON (needs `$schema`, `properties`, or `definitions`)
- Avro vs regular JSON (needs `type: "record"`, `fields`, or `protocol`)
- Proto detection (needs `syntax = "proto2/3"`, `message`, `package`)

## Test Architecture

```
tests/editor/
├── README.md                    # This file
├── format-detection.spec.ts     # Format detection indicator tests
├── format-strategies.spec.ts    # Format parsing and transformation tests
└── test-data.ts                 # Sample content for all formats
```

## Future Enhancements

- Add performance benchmarks for large files
- Test memory usage with very large CSV/JSON files
- Test concurrent transformations
- Add fuzzing tests for malformed input
- Test accessibility of format indicators (screen readers, keyboard navigation)
