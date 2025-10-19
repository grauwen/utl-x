# UTL-X Conformance Suite

A comprehensive test suite for validating UTL-X transformation correctness, language stability, and implementation compliance.

## 🎯 Purpose

The UTL-X Conformance Suite ensures:
- **Language Specification Validation**: Consistent behavior across implementations
- **Regression Prevention**: Catch breaking changes in stdlib functions and syntax
- **Implementation Verification**: Validate different UTL-X runtimes produce identical results
- **Documentation by Example**: Canonical examples of every language feature
- **Quality Assurance**: Systematic testing of edge cases and error conditions

## 📁 Structure

```
conformance-suite
├── conformance-suite
│   └── tests
│       └── auto-captured
│           ├── auto-captured
│           │   ├── json-to-xml
│           │   │   └── transform_auto_b900b260.yaml
│           │   ├── xml-to-json
│           │   │   └── transform_auto_8fb5ad0f.yaml
│           │   └── xml-transform
│           │       └── transform_auto_43d9da56.yaml
│           └── stdlib
│               ├── crypto
│               │   └── md5_auto_09f6088a.yaml
│               ├── serialization
│               │   ├── parseJson_auto_3521f712.yaml
│               │   ├── parseJson_auto_635263b5.yaml
│               │   ├── parseJson_auto_b55a35c5.yaml
│               │   ├── renderJson_auto_28f36a9f.yaml
│               │   └── renderJson_auto_39c4ad24.yaml
│               └── string
│                   └── upper_auto_2939d8cb.yaml
├── data
│   ├── expected
│   ├── samples
│   └── schemas
├── README.md
├── reports
│   ├── compatibility
│   ├── coverage
│   └── performance
├── requirements.txt
├── runners
│   ├── cli-runner
│   │   ├── run-all.sh
│   │   ├── run-category.sh
│   │   ├── run-performance.sh
│   │   ├── run-tests.sh
│   │   └── simple-runner.py
│   ├── junit-runner
│   └── node-runner
├── specs
├── test-case-schema.json
├── tests
│   ├── auto-captured
│   │   ├── auto-captured
│   │   │   ├── json-transform
│   │   │   │   ├── transform_auto_29cfa913.yaml
│   │   │   │   ├── transform_auto_31bb45fe.yaml
│   │   │   │   ├── transform_auto_81081c90.yaml
│   │   │   │   ├── transform_auto_ba5c3b14.yaml
│   │   │   │   ├── transform_auto_f05fc122.yaml
│   │   │   │   └── transform_auto_f5df96cd.yaml
│   │   │   ├── xml-transform
│   │   │   │   └── transform_auto_0b0f9dfa.yaml
│   │   │   └── yaml-transform
│   │   │       └── transform_auto_bd4d3f5b.yaml
│   │   ├── README.md
│   │   └── stdlib
│   │       ├── array
│   │       │   └── sum_auto_a4eced76.yaml
│   │       ├── serialization
│   │       │   ├── parseJson_auto_96e8da01.yaml
│   │       │   └── renderJson_auto_df69e6e1.yaml
│   │       └── string
│   │           └── contains_auto_b4e73406.yaml
│   ├── core
│   │   ├── object
│   │   │   └── object_construction.yaml
│   │   ├── operators
│   │   │   └── arithmetic_basic.yaml
│   │   ├── semantics
│   │   ├── syntax
│   │   │   ├── basic_literals.yaml
│   │   │   └── input_binding.yaml
│   │   └── types
│   ├── edge-cases
│   │   ├── array_index_bounds.yaml
│   │   └── division_by_zero.yaml
│   ├── examples
│   │   ├── advanced
│   │   ├── basic
│   │   │   ├── array_transformation.yaml
│   │   │   ├── json_to_xml_conversion.yaml
│   │   │   └── simple_property_mapping.yaml
│   │   ├── intermediate
│   │   │   ├── csv_to_json_transformation.yaml
│   │   │   ├── data_normalization.yaml
│   │   │   ├── xml_namespace_handling.yaml
│   │   │   └── yaml_config_processing.yaml
│   │   ├── README.md
│   │   └── real-world
│   │       ├── financial_data_processing.yaml
│   │       └── sap_integration.yaml
│   ├── formats
│   │   ├── csv
│   │   ├── json
│   │   ├── xml
│   │   └── yaml
│   ├── integration
│   ├── performance
│   │   └── large_array_processing.yaml
│   ├── registry.json
│   └── stdlib
│       ├── array
│       │   ├── count_basic.yaml
│       │   ├── filter_basic.yaml
│       │   ├── find_basic.yaml
│       │   ├── first_basic.yaml
│       │   ├── flatten_basic.yaml
│       │   ├── last_basic.yaml
│       │   ├── length_basic.yaml
│       │   ├── map_basic.yaml
│       │   ├── reduce_basic.yaml
│       │   ├── sort_basic.yaml
│       │   ├── sum_basic.yaml
│       │   └── unique_basic.yaml
│       ├── compression
│       ├── crypto
│       │   ├── md5_basic.yaml
│       │   └── sha256_basic.yaml
│       ├── datetime
│       │   ├── now_basic.yaml
│       │   └── parseDate_basic.yaml
│       ├── encoding
│       │   ├── base64_basic.yaml
│       │   └── url_basic.yaml
│       ├── geospatial
│       │   └── distance_basic.yaml
│       ├── integration
│       ├── math
│       │   ├── abs_basic.yaml
│       │   ├── ceil_basic.yaml
│       │   ├── floor_basic.yaml
│       │   ├── max_basic.yaml
│       │   ├── min_basic.yaml
│       │   ├── pow_basic.yaml
│       │   ├── round_basic.yaml
│       │   └── sqrt_basic.yaml
│       ├── object
│       │   ├── keys_basic.yaml
│       │   └── values_basic.yaml
│       ├── runtime
│       ├── serialization
│       │   ├── parseJson_basic.yaml
│       │   └── renderJson_basic.yaml
│       ├── string
│       │   ├── contains_basic.yaml
│       │   ├── length_basic.yaml
│       │   ├── lower_basic.yaml
│       │   ├── replace_basic.yaml
│       │   ├── split_basic.yaml
│       │   ├── toNumber_basic.yaml
│       │   ├── toString_basic.yaml
│       │   ├── trim_basic.yaml
│       │   └── upper_basic.yaml
│       ├── type
│       │   ├── isNumber_basic.yaml
│       │   ├── isString_basic.yaml
│       │   ├── toString_basic.yaml
│       │   └── typeof_basic.yaml
│       ├── validation
│       └── xml
│           ├── convertXMLEncoding_basic.yaml
│           ├── detectXMLEncoding_basic.yaml
│           ├── stripBOM_basic.yaml
│           ├── updateXMLEncoding_basic.yaml
│           └── validateEncoding_basic.yaml
└── tools
    └── validate-tests.py

71 directories, 99 files

```

## 🚀 Quick Start

### Running Tests

```bash
# Run all tests
./runners/cli-runner/run-all.sh

# Run specific category
./runners/cli-runner/run-category.sh stdlib/array

# Run performance benchmarks
./runners/cli-runner/run-performance.sh

# Generate coverage report
./runners/cli-runner/generate-coverage.sh

#direct 0 (direct output)
cd conformance-suite
python3 runners/cli-runner/simple-runner.py

#direct 1 (suppressed output)
cd conformance-suite
python3 runners/cli-runner/simple-runner.py 2>&1 | tail -15

#direct 2
cd conformance-suite
python3 runners/cli-runner/simple-runner.py 2>&1 | grep -E "Running:|✗" | awk '/Running:/{test=$2} /✗/{print test}' | sort | uniq

#direct 3
cd conformance-suite
python3 runners/cli-runner/simple-runner.py 2>&1 | grep -E "Running:|  ✗" | grep -B 1 "✗" | grep "Running:" | awk '{print $2}'
```

### Adding New Tests

1. Create test case in appropriate category directory
2. Follow the standard test case format (see `test-case-schema.json`)
3. Add test data to `data/` directory if needed
4. Update test registry in `tests/registry.json`

## 📋 Test Categories

- **Core Language** (100+ tests): Syntax, types, semantics
- **Standard Library** (656+ tests): All stdlib functions with comprehensive coverage
- **Formats** (50+ tests): JSON, XML, CSV, YAML processing
- **Integration** (30+ tests): End-to-end transformation scenarios
- **Performance** (20+ tests): Benchmarks and scalability validation
- **Edge Cases** (100+ tests): Error handling and boundary conditions

## 📊 Coverage Status

| Category | Tests | Coverage | Status |
|----------|-------|----------|--------|
| Core Language | 25/100 | 25% | 🟡 In Progress |
| Array Functions | 15/60 | 25% | 🟡 In Progress |
| String Functions | 10/40 | 25% | 🟡 In Progress |
| Math Functions | 8/30 | 27% | 🟡 In Progress |
| Date/Time Functions | 12/50 | 24% | 🟡 In Progress |
| Other Categories | 0/476 | 0% | 🔴 Pending |

**Total Progress: 70/656 tests (10.7%)**

## 🔧 Development

### Test Case Format

```yaml
name: "test_identifier"
category: "stdlib/array" 
description: "Human readable description"
tags: ["core", "array", "map"]

input:
  format: json
  data: [1, 2, 3, 4, 5]

transformation: |
  %utlx 1.0
  input json
  output json
  ---
  @input | map(x -> x * 2)

expected:
  format: json
  data: [2, 4, 6, 8, 10]

performance_limits:
  max_duration_ms: 100
  max_memory_mb: 10
```

### Contributing

1. Follow the test case format specification
2. Include comprehensive variants and edge cases
3. Add performance expectations where appropriate
4. Ensure tests are deterministic and reproducible
5. Document any special requirements or dependencies

## 📈 Metrics & Reporting

The conformance suite tracks:
- **Test Coverage**: Percentage of language features tested
- **Pass/Fail Rates**: Test reliability across implementations
- **Performance Baselines**: Execution time and memory usage
- **Regression Detection**: Changes in behavior over time
- **Implementation Compatibility**: Cross-runtime consistency

## 🏆 Goals

- **Phase 1**: Core language + basic stdlib (100 tests) ✅ In Progress
- **Phase 2**: Complete stdlib coverage (656+ tests)
- **Phase 3**: Advanced scenarios and edge cases (200+ tests)
- **Phase 4**: Performance benchmarks (50+ tests)
- **Phase 5**: Cross-implementation validation

## 🔗 Related Documentation

- [Conformance Suite Design](../docs/architecture/conformance-suite-design.md)
- [UTL-X Language Specification](../docs/language-spec.md)
- [Standard Library Reference](../docs/stdlib-reference.md)
