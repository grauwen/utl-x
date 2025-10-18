# UTL-X Example-Based Conformance Tests

This directory contains comprehensive conformance tests derived from real-world examples found in the UTL-X documentation and Markdown files throughout the project.

## Overview

These tests serve multiple purposes:
- **Real-world validation** - Test UTL-X with actual business scenarios
- **Documentation verification** - Ensure examples in docs actually work
- **Complexity coverage** - Test from basic to advanced transformation patterns
- **Format diversity** - Cover JSON, XML, CSV, YAML input/output combinations

## Directory Structure

```
examples/
├── basic/           # Simple transformations for learning UTL-X
├── intermediate/    # Multi-step transformations with business logic
├── real-world/      # Complex enterprise scenarios
└── advanced/        # Future: Most complex scenarios
```

## Test Categories

### Basic Tests
**Target Audience**: New UTL-X users  
**Complexity**: Single function calls, simple property mapping  
**Examples**:
- `simple_property_mapping.yaml` - Basic JSON field extraction and transformation
- `array_transformation.yaml` - Array filtering, mapping, and aggregation
- `json_to_xml_conversion.yaml` - Format conversion with basic structure

### Intermediate Tests  
**Target Audience**: Developers implementing business logic  
**Complexity**: Multiple functions, custom logic, format conversions  
**Examples**:
- `data_normalization.yaml` - String cleaning, validation, custom functions
- `xml_namespace_handling.yaml` - XML namespace processing and SOAP envelopes
- `csv_to_json_transformation.yaml` - CSV parsing with type inference and analysis
- `yaml_config_processing.yaml` - Configuration templating with environment variables

### Real-World Tests
**Target Audience**: Enterprise integration scenarios  
**Complexity**: Complete business workflows, multiple data sources  
**Examples**:
- `sap_integration.yaml` - SAP system integration with encoding handling
- `financial_data_processing.yaml` - Transaction analysis with complex aggregations

## Test File Structure

Each test follows the standard UTL-X conformance test format:

```yaml
name: "test_name"
category: "examples/level"
description: "Brief description of what this test demonstrates"
tags: ["format", "feature", "complexity"]

input:
  format: json|xml|csv|yaml
  data: |
    # Input data in specified format
    
transformation: |
  %utlx 1.0
  input format
  output format
  ---
  # UTL-X transformation code
  
expected:
  format: json|xml|csv|yaml  
  data: |
    # Expected output data
    
performance_limits:
  max_duration_ms: 500
  max_memory_mb: 25

# Optional test variants
variants:
  - name: "variant_name"
    input: {...}
    expected: {...}
    
metadata:
  author: "UTL-X Documentation"
  source: "docs/path/to/original/example.md"
  complexity: "basic|intermediate|advanced"
  business_case: "Brief business context"
  notes: ["Additional context", "Implementation notes"]
```

## Key Features Demonstrated

### Data Transformation Patterns
- **Property mapping and extraction**
- **Array filtering and transformation** 
- **Data aggregation and analysis**
- **Format conversion** (JSON ↔ XML ↔ CSV ↔ YAML)
- **Data validation and quality scoring**

### Business Logic Examples
- **Financial transaction processing**
- **Inventory management calculations**
- **Employee data analysis**
- **Configuration management**
- **Enterprise system integration**

### UTL-X Language Features
- **Function composition** - `map()`, `filter()`, `sum()`, `count()`, `avg()`
- **Conditional logic** - `if`/`else` expressions
- **Custom functions** - User-defined transformation logic
- **String manipulation** - Cleaning, formatting, validation
- **Date/time processing** - Parsing, formatting, calculations
- **XML processing** - Namespace handling, SOAP processing
- **Type conversions** - String to number, boolean conversion

## Running the Tests

### Individual Test
```bash
# Run a specific example test
./conformance-suite/runners/cli-runner/run-tests.sh examples/basic simple_property_mapping
```

### By Category
```bash
# Run all basic examples
./conformance-suite/runners/cli-runner/run-tests.sh examples/basic

# Run all intermediate examples  
./conformance-suite/runners/cli-runner/run-tests.sh examples/intermediate

# Run all real-world examples
./conformance-suite/runners/cli-runner/run-tests.sh examples/real-world
```

### All Example Tests
```bash
# Run all example-based tests
./conformance-suite/runners/cli-runner/run-tests.sh examples
```

## Test Development Guidelines

### Creating New Example Tests

1. **Start with documentation** - Find real examples in project Markdown files
2. **Verify syntax** - Ensure UTL-X transformation uses correct function-based syntax:
   - Use `map(array, item -> ...)` instead of `array[].property`
   - Use `filter(array, item -> condition)` instead of `array[condition]`
   - Use `sum(map(array, item -> item.field))` for aggregations
3. **Test locally** - Verify transformation works with UTL-X CLI
4. **Add complexity gradually** - Start basic, add variants for edge cases
5. **Document business context** - Explain real-world use case in metadata

### Syntax Best Practices

```utlx
# ✅ Correct UTL-X syntax
products: map(@input.items, item -> item.product)
total: sum(map(@input.items, item -> item.price))
engineers: map(filter(@input.employees, emp -> emp.dept == "Engineering"), emp -> emp.name)

# ❌ Incorrect syntax (not implemented)
products: @input.items[].product
total: sum(@input.items[].price)  
engineers: @input.employees[dept == "Engineering"].name
```

## Test Coverage Goals

- **✅ JSON transformations** - Property mapping, array operations
- **✅ XML processing** - Namespace handling, format conversion  
- **✅ CSV parsing** - Type inference, data analysis
- **✅ YAML configuration** - Template processing, validation
- **✅ Business scenarios** - Financial, inventory, employee data
- **✅ Enterprise integration** - SAP, encoding handling
- **🔄 Future**: More complex nested transformations
- **🔄 Future**: Error handling and recovery scenarios
- **🔄 Future**: Performance and memory stress tests

## Success Metrics

These example tests help ensure:
- **Documentation accuracy** - Examples in docs actually work
- **Real-world readiness** - UTL-X handles business scenarios
- **Language completeness** - All major features have working examples  
- **User experience** - Developers can learn from practical examples
- **Regression prevention** - Changes don't break documented functionality

---

**Note**: All tests use corrected UTL-X syntax with proper function calls rather than array access syntax. This ensures compatibility with the current UTL-X implementation while demonstrating best practices for transformation development.