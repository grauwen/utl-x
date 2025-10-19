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
conformance-suite/
├── specs/                    # Human-readable specifications
├── tests/                    # Test implementations
│   ├── core/                # Core language tests
│   ├── stdlib/              # Standard library tests (656+ functions)
│   ├── formats/             # I/O format tests
│   ├── integration/         # End-to-end scenarios
│   ├── performance/         # Performance benchmarks
│   └── edge-cases/          # Error conditions and limits
├── data/                    # Test data files
├── runners/                 # Test execution frameworks
└── reports/                 # Test result analysis
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

#direct 1
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
