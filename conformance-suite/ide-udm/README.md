# IDE UDM Conformance Test Suite

**Location**: `conformance-suite/ide-udm/`

This conformance test suite validates the Universal Data Model (UDM) implementation for the IDE (TypeScript) against the reference implementation (Kotlin CLI/backend).

## 🎯 Purpose

Ensures that the IDE's TypeScript UDM implementation is **100% compatible** with the Kotlin reference implementation, guaranteeing:
- ✅ Identical parsing behavior
- ✅ Identical serialization output
- ✅ Identical path resolution
- ✅ Round-trip data integrity

## 🏃 Quick Start

```bash
# Run all conformance tests
./run-conformance-tests.sh

# Or run individual test suites:
cd ../../theia-extension/utlx-theia-extension
node lib/browser/udm/__tests__/udm-roundtrip.test.js  # TypeScript unit tests

cd ../..
./gradlew :modules:core:test --tests "TypeScriptInteropTest"  # Kotlin ↔ TS interop
```

## 📋 Test Suites

1. **TypeScript Unit Tests** (54 tests)
   - Parser correctness
   - Serializer correctness
   - Navigator path resolution
   - All UDM types

2. **Kotlin ↔ TypeScript Interop** (8 tests)
   - Round-trip validation
   - Cross-language compatibility
   - Path resolution parity

3. **Example Generation** (17 files)
   - Node.js-generated examples (10)
   - USDL feature examples (7)

4. **Comprehensive Integration** (50+ tests)
   - Format compatibility
   - Deep nesting
   - Real-world scenarios

## 📁 Directory Structure

```
conformance-suite/ide-udm/
├── README.md                              # This file
├── run-conformance-tests.sh               # ⭐ Main test runner
│
├── tests/                                 # Test suites
│   ├── udm-roundtrip.test.ts             # TypeScript unit tests
│   ├── kotlin-roundtrip-bridge.ts        # Kotlin ↔ TS bridge
│   ├── TypeScriptInteropTest.kt          # Kotlin interop tests
│   └── comprehensive-test-suite.ts       # Integration tests
│
├── scripts/                               # Example generators
│   ├── generate-cli-examples.sh          # CLI → UDM (8 formats)
│   ├── generate-nodejs-examples.ts       # TypeScript → UDM
│   └── test-usdl-features.ts             # USDL examples
│
├── examples/                              # Generated test data
│   ├── cli-generated/                    # CLI → UDM examples
│   │   ├── json_*.udm                   # Tier 1: JSON
│   │   ├── xml_*.udm                    # Tier 1: XML
│   │   ├── csv_*.udm                    # Tier 1: CSV
│   │   ├── yaml_*.udm                   # Tier 1: YAML
│   │   ├── xsd_*.udm                    # Tier 2: XSD Schema
│   │   ├── jsch_*.udm                   # Tier 2: JSON Schema
│   │   ├── avro_*.udm                   # Tier 2: Avro
│   │   └── proto_*.udm                  # Tier 2: Protobuf
│   │
│   ├── nodejs-generated/                 # TypeScript → UDM examples
│   │   ├── 01_all-scalar-types_*.udm
│   │   ├── 02_all-datetime-types_*.udm
│   │   ├── 03_arrays_*.udm
│   │   ├── 04_objects-with-attributes_*.udm
│   │   ├── 05_objects-with-metadata_*.udm
│   │   ├── 06_binary-type_*.udm
│   │   ├── 07_lambda-type_*.udm
│   │   ├── 08_healthcare-claim_*.udm     # Real-world example
│   │   ├── 09_deep-nesting_*.udm         # 6-level depth test
│   │   └── 10_all-types-combined_*.udm   # Comprehensive
│   │
│   └── usdl-examples/                    # USDL feature examples
│       ├── kind-annotations.usdl         # %kind type system
│       ├── functions-example.usdl        # %map, %filter, %reduce
│       ├── complex-kind.usdl             # Complex type annotations
│       ├── utlx-integration.usdl         # UTLX transformation integration
    ├── validation-rules.usdl           # %validate rules
    ├── schema-inheritance.usdl         # %extends, %instance
    └── comprehensive-features.usdl     # All USDL features
```

## 🚀 Quick Start

### 1. Generate All Examples

```bash
# Generate CLI examples (requires CLI to be built)
cd /Users/magr/data/mapping/github-git/utl-x
./gradlew assemble
./examples/udm/generate-cli-examples.sh

# Generate Node.js examples
node examples/udm/generate-nodejs-examples.js

# Generate USDL examples
node examples/udm/test-usdl-features.js
```

### 2. Run Comprehensive Tests

```bash
# Run TypeScript unit tests
cd theia-extension/utlx-theia-extension
node lib/browser/udm/__tests__/udm-roundtrip.test.js

# Run Kotlin ↔ TypeScript interop tests
cd ../..
./gradlew :modules:core:test --tests "com.glomidco.utlx.core.udm.TypeScriptInteropTest"

# Run comprehensive test suite
node examples/udm/comprehensive-test-suite.js
```

## 📊 Test Coverage

### UDM Types (100% Coverage)

| Type | Example File | Test Status |
|------|-------------|-------------|
| Scalar (string) | `01_all-scalar-types_*.udm` | ✅ Passing |
| Scalar (number) | `01_all-scalar-types_*.udm` | ✅ Passing |
| Scalar (boolean) | `01_all-scalar-types_*.udm` | ✅ Passing |
| Scalar (null) | `01_all-scalar-types_*.udm` | ✅ Passing |
| DateTime | `02_all-datetime-types_*.udm` | ✅ Passing |
| Date | `02_all-datetime-types_*.udm` | ✅ Passing |
| LocalDateTime | `02_all-datetime-types_*.udm` | ✅ Passing |
| Time | `02_all-datetime-types_*.udm` | ✅ Passing |
| Array | `03_arrays_*.udm` | ✅ Passing |
| Object | All files | ✅ Passing |
| Binary | `06_binary-type_*.udm` | ✅ Passing |
| Lambda | `07_lambda-type_*.udm` | ✅ Passing |

### Format Coverage (8/8 Formats)

#### Tier 1 Formats
- ✅ **JSON** - `json_*.udm`
- ✅ **XML** - `xml_*.udm`
- ✅ **CSV** - `csv_*.udm`
- ✅ **YAML** - `yaml_*.udm`

#### Tier 2 Formats
- ✅ **XSD** (XML Schema) - `xsd_*.udm`
- ✅ **JSON Schema** - `jsch_*.udm`
- ✅ **Avro** - `avro_*.udm`
- ✅ **Protobuf** - `proto_*.udm`

### Feature Coverage

| Feature | Test File | Status |
|---------|-----------|--------|
| Attributes (XML-style) | `04_objects-with-attributes_*.udm` | ✅ Passing |
| Metadata maps | `05_objects-with-metadata_*.udm` | ✅ Passing |
| Element names | `05_objects-with-metadata_*.udm` | ✅ Passing |
| Deep nesting (6+ levels) | `09_deep-nesting_*.udm` | ✅ Passing |
| Array indexing | `08_healthcare-claim_*.udm` | ✅ Passing |
| Attribute access (@) | `04_objects-with-attributes_*.udm` | ✅ Passing |
| $input prefix | All navigation tests | ✅ Passing |

### USDL Language Features

| Feature | Example File | Description |
|---------|-------------|-------------|
| %kind | `kind-annotations.usdl` | Type annotations and validation |
| %map | `functions-example.usdl` | Collection mapping |
| %filter | `functions-example.usdl` | Collection filtering |
| %reduce | `functions-example.usdl` | Collection aggregation |
| %validate | `validation-rules.usdl` | Validation rules |
| %extends | `schema-inheritance.usdl` | Schema inheritance |
| %instance | `schema-inheritance.usdl` | Type instantiation |
| %if, %switch | `comprehensive-features.usdl` | Conditional logic |
| %lookup, %ref | `comprehensive-features.usdl` | Reference resolution |
| Aggregations | `comprehensive-features.usdl` | %sum, %avg, %count, %max, %min |
| Date operations | `comprehensive-features.usdl` | %formatDate, %addDays |
| String operations | `comprehensive-features.usdl` | %upper, %lower, %concat |
| Math operations | `comprehensive-features.usdl` | %round, %pow, %sqrt |

## 🔍 Critical Path Tests

### Issue: properties/attributes Keywords in Paths

**Problem**: The old IDE implementation incorrectly treated `properties:` and `attributes:` as data fields instead of structural metadata.

**Wrong Behavior** (Old IDE):
```
Path: $input.properties.providers.properties.address.properties.street
```

**Correct Behavior** (CLI & New Implementation):
```
Path: $input.providers.address.street
```

**Test Validation**:
```typescript
// Test: CRITICAL: properties/attributes not in paths
const paths = getAllPaths(udm, false);

// These should NOT exist
assert(!paths.includes('properties'));
assert(!paths.includes('properties.providers'));

// These SHOULD exist
assert(paths.includes('providers'));
assert(paths.includes('providers.address'));
assert(paths.includes('providers.address.street'));
```

**Status**: ✅ All tests passing (54/54 TypeScript tests, 8/8 Kotlin interop tests)

## 📈 Test Results

### TypeScript Unit Tests
```
✅ Passed: 54/54
❌ Failed: 0/54
📊 Success Rate: 100%
```

### Kotlin ↔ TypeScript Interop Tests
```
✅ test simple scalar round-trip() PASSED
✅ test simple object round-trip() PASSED
✅ test object with attributes round-trip() PASSED
✅ test nested object round-trip() PASSED
✅ test array round-trip() PASSED
✅ test array of objects round-trip() PASSED
✅ test DateTime types round-trip() PASSED
✅ test real-world example from docs() PASSED

Total: 8/8 PASSED
```

## 🛠️ Implementation Details

### TypeScript UDM Implementation

**Location**: `theia-extension/utlx-theia-extension/src/browser/udm/`

**Files**:
- `udm-core.ts` - UDM type definitions and factory
- `udm-language-parser.ts` - Parser (ported from Kotlin)
- `udm-language-serializer.ts` - Serializer (ported from Kotlin)
- `udm-navigator.ts` - Path navigation utilities

**Key Features**:
- ✅ Full UDM type hierarchy
- ✅ Map-based properties/attributes (not objects)
- ✅ Type guards for safe navigation
- ✅ Path resolution compatible with CLI
- ✅ Round-trip serialization

### Kotlin Reference Implementation

**Location**: `modules/core/src/main/kotlin/com/glomidco/utlx/core/udm/`

**Files**:
- `udm_core.kt` - UDM sealed class hierarchy
- `UDMLanguageParser.kt` - Reference parser
- `UDMLanguageSerializer.kt` - Reference serializer

## 🔄 Round-Trip Testing

### Test Flow

```
┌─────────────┐
│   Kotlin    │
│  UDM Object │
└──────┬──────┘
       │
       │ serialize
       ▼
┌─────────────┐
│  .udm file  │
│  (string)   │
└──────┬──────┘
       │
       │ stdin
       ▼
┌─────────────┐
│ TypeScript  │
│   Bridge    │
│             │
│  1. Parse   │
│  2. Validate│
│  3. Serialize│
└──────┬──────┘
       │
       │ stdout
       ▼
┌─────────────┐
│  .udm file  │
│  (string)   │
└──────┬──────┘
       │
       │ parse
       ▼
┌─────────────┐
│   Kotlin    │
│  UDM Object │
│             │
│  VALIDATE   │
│  matches    │
│  original   │
└─────────────┘
```

### Bridge Script

**Location**: `theia-extension/utlx-theia-extension/lib/browser/udm/__tests__/kotlin-roundtrip-bridge.js`

**Usage**:
```bash
echo "@udm-version: 1.0\n{ name: \"Test\" }" | node kotlin-roundtrip-bridge.js
```

## 📝 Example UDM Files

### Simple Object (Shorthand Format)
```udm
@udm-version: 1.0

{
  name: "Alice",
  age: 30,
  active: true
}
```

### Object with Attributes (Full Format)
```udm
@udm-version: 1.0

{
  attributes: {
    id: "CUST-001",
    type: "premium"
  },
  properties: {
    name: "Bob",
    email: "bob@example.com"
  }
}
```

### Real-World Healthcare Claim
See: `nodejs-generated/08_healthcare-claim_nodejs-generated.udm`

Features:
- Nested objects (6+ levels)
- Arrays of objects
- Attributes at multiple levels
- DateTime and Date types
- Metadata annotations
- Element names

## 🎓 Usage Examples

### Parsing UDM in TypeScript
```typescript
import { UDMLanguageParser } from './udm-language-parser';
import { navigate, getScalarValue } from './udm-navigator';

// Parse .udm file
const udmString = fs.readFileSync('example.udm', 'utf-8');
const udm = UDMLanguageParser.parse(udmString);

// Navigate using CLI-style paths
const value = getScalarValue(udm, 'customer.address.street');
// Result: "123 Main St"

// NOT: getScalarValue(udm, 'properties.customer.properties.address.properties.street')
//      ^^^ This is WRONG - "properties" is metadata, not a field
```

### Serializing UDM in TypeScript
```typescript
import { UDMFactory, toUDMLanguage } from './udm-core';

// Create UDM object
const udm = UDMFactory.object(new Map([
    ['name', UDMFactory.scalar('Charlie')],
    ['age', UDMFactory.scalar(35)]
]));

// Serialize to .udm format
const udmString = toUDMLanguage(udm);

// Output:
// @udm-version: 1.0
//
// {
//   name: "Charlie",
//   age: 35
// }
```

## 🚨 Known Issues

None! All tests passing.

## 🔜 Next Steps

1. **Integrate with Monaco Editor** - Replace regex-based completion with UDM navigator
2. **Integrate with Function Builder** - Use UDM parser instead of custom string parsing
3. **Performance Optimization** - Cache parsed UDM objects
4. **Error Handling** - Better parse error messages with line/column info
5. **Schema Validation** - Implement USDL %kind type checking

## 📚 References

- **Architecture Document**: `/docs/architects/udm-parsing-at-ide.md`
- **ANTLR4 Grammar**: `/modules/core/src/main/antlr4/com/glomidco/utlx/core/udm/UDMLang.g4`
- **Kotlin Implementation**: `/modules/core/src/main/kotlin/com/glomidco/utlx/core/udm/`
- **TypeScript Implementation**: `/theia-extension/utlx-theia-extension/src/browser/udm/`

## 🤝 Contributing

When adding new UDM features:
1. Add Kotlin implementation first (reference)
2. Port to TypeScript
3. Add tests to `udm-roundtrip.test.ts`
4. Add interop test to `TypeScriptInteropTest.kt`
5. Create example in `nodejs-generated/`
6. Update this README

## ✅ Test Checklist

Before merging UDM changes:
- [ ] All TypeScript unit tests pass (54/54)
- [ ] All Kotlin interop tests pass (8/8)
- [ ] Comprehensive test suite passes
- [ ] CLI examples generated successfully
- [ ] Node.js examples generated successfully
- [ ] USDL examples validated
- [ ] No "properties" or "attributes" in path outputs
- [ ] Round-trip preserves all data
- [ ] Documentation updated

---

**Last Updated**: 2024-11-16
**Test Suite Version**: 1.0
**Status**: ✅ All Tests Passing
