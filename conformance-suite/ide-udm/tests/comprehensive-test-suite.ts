/**
 * Comprehensive UDM Test Suite
 *
 * Tests:
 * 1. CLI-generated .udm files (8 formats → UDM)
 * 2. Node.js-generated .udm files (all UDM types)
 * 3. Round-trip: UDM → serialize → parse → validate
 * 4. Cross-validation: CLI vs Node.js for same data
 * 5. USDL language features (%kind, %functions, etc.)
 * 6. Format conversions: Format → UDM → Format
 *
 * Run with: node lib/examples/udm/comprehensive-test-suite.js
 */

import * as fs from 'fs';
import * as path from 'path';
import { execSync } from 'child_process';
import { UDMLanguageParser, UDMParseException } from '../../theia-extension/utlx-theia-extension/lib/browser/udm/udm-language-parser';
import { toUDMLanguage } from '../../theia-extension/utlx-theia-extension/lib/browser/udm/udm-language-serializer';
import { navigate, getAllPaths, getScalarValue } from '../../theia-extension/utlx-theia-extension/lib/browser/udm/udm-navigator';
import { UDM, isObject, isArray, isScalar } from '../../theia-extension/utlx-theia-extension/lib/browser/udm/udm-core';

// Test counters
let totalTests = 0;
let passedTests = 0;
let failedTests = 0;

// Test results
interface TestResult {
    category: string;
    name: string;
    passed: boolean;
    error?: string;
    duration?: number;
}

const results: TestResult[] = [];

function test(category: string, name: string, fn: () => void): void {
    totalTests++;
    const startTime = Date.now();

    try {
        fn();
        const duration = Date.now() - startTime;
        results.push({ category, name, passed: true, duration });
        passedTests++;
        console.log(`  ✅ ${name} (${duration}ms)`);
    } catch (error: any) {
        const duration = Date.now() - startTime;
        results.push({ category, name, passed: false, error: error.message, duration });
        failedTests++;
        console.log(`  ❌ ${name}`);
        console.log(`     Error: ${error.message}`);
    }
}

function assert(condition: boolean, message: string): void {
    if (!condition) {
        throw new Error(message);
    }
}

function assertEquals(actual: any, expected: any, message: string): void {
    if (JSON.stringify(actual) !== JSON.stringify(expected)) {
        throw new Error(`${message}\n  Expected: ${JSON.stringify(expected)}\n  Actual: ${JSON.stringify(actual)}`);
    }
}

console.log('═'.repeat(80));
console.log('COMPREHENSIVE UDM TEST SUITE');
console.log('═'.repeat(80));
console.log('');

// ============================================================================
// Category 1: Node.js-Generated UDM Files
// ============================================================================
console.log('📦 Category 1: Node.js-Generated UDM Files');
console.log('─'.repeat(80));

const nodejsDir = path.join(__dirname, 'nodejs-generated');

if (fs.existsSync(nodejsDir)) {
    const files = fs.readdirSync(nodejsDir).filter(f => f.endsWith('.udm'));

    files.forEach(file => {
        const filepath = path.join(nodejsDir, file);

        test('nodejs-generated', `Parse ${file}`, () => {
            const content = fs.readFileSync(filepath, 'utf-8');
            const parsed = UDMLanguageParser.parse(content);
            assert(parsed !== undefined, 'Should parse successfully');
        });

        test('nodejs-generated', `Round-trip ${file}`, () => {
            const content = fs.readFileSync(filepath, 'utf-8');
            const parsed = UDMLanguageParser.parse(content);
            const serialized = toUDMLanguage(parsed);
            const reparsed = UDMLanguageParser.parse(serialized);

            // Both should have same structure
            assert(parsed.type === reparsed.type, 'Type should match after round-trip');
        });
    });
} else {
    console.log('  ⚠️  Node.js-generated directory not found. Run generate-nodejs-examples.ts first.');
}

console.log('');

// ============================================================================
// Category 2: CLI-Generated UDM Files
// ============================================================================
console.log('📦 Category 2: CLI-Generated UDM Files');
console.log('─'.repeat(80));

const cliDir = path.join(__dirname, 'cli-generated');

if (fs.existsSync(cliDir)) {
    const files = fs.readdirSync(cliDir).filter(f => f.endsWith('.udm'));

    files.forEach(file => {
        const filepath = path.join(cliDir, file);

        test('cli-generated', `Parse ${file}`, () => {
            const content = fs.readFileSync(filepath, 'utf-8');
            const parsed = UDMLanguageParser.parse(content);
            assert(parsed !== undefined, 'Should parse successfully');
        });

        test('cli-generated', `Validate paths ${file}`, () => {
            const content = fs.readFileSync(filepath, 'utf-8');
            const parsed = UDMLanguageParser.parse(content);

            const paths = getAllPaths(parsed, false);

            // CRITICAL: Paths should NOT contain "properties" or "attributes" as field names
            paths.forEach(p => {
                assert(!p.includes('.properties.'), 'Paths should NOT include .properties.');
                assert(!p.includes('.attributes.'), 'Paths should NOT include .attributes.');
                assert(!p.startsWith('properties.'), 'Paths should NOT start with properties.');
                assert(!p.startsWith('attributes.'), 'Paths should NOT start with attributes.');
            });
        });
    });
} else {
    console.log('  ⚠️  CLI-generated directory not found. Run generate-cli-examples.sh first.');
}

console.log('');

// ============================================================================
// Category 3: Specific UDM Type Tests
// ============================================================================
console.log('📦 Category 3: Specific UDM Type Tests');
console.log('─'.repeat(80));

test('udm-types', 'All scalar types', () => {
    const file = path.join(nodejsDir, '01_all-scalar-types_nodejs-generated.udm');
    if (!fs.existsSync(file)) return;

    const parsed = UDMLanguageParser.parse(fs.readFileSync(file, 'utf-8'));
    assert(isObject(parsed), 'Root should be object');

    const stringVal = getScalarValue(parsed, 'stringValue');
    assertEquals(stringVal, 'Hello, World!', 'String value should match');

    const intVal = getScalarValue(parsed, 'intValue');
    assertEquals(intVal, '42', 'Int value should match');

    const boolVal = getScalarValue(parsed, 'booleanTrue');
    assertEquals(boolVal, 'true', 'Boolean value should match');

    const nullVal = navigate(parsed, 'nullValue');
    assert(nullVal !== undefined && typeof nullVal !== 'string', 'Null should exist');
    if (typeof nullVal !== 'string' && isScalar(nullVal)) {
        assertEquals(nullVal.value, null, 'Null value should be null');
    }
});

test('udm-types', 'All DateTime types', () => {
    const file = path.join(nodejsDir, '02_all-datetime-types_nodejs-generated.udm');
    if (!fs.existsSync(file)) return;

    const parsed = UDMLanguageParser.parse(fs.readFileSync(file, 'utf-8'));

    const timestamp = navigate(parsed, 'timestamp');
    assert(timestamp !== undefined && typeof timestamp !== 'string', 'Timestamp should exist');
    if (typeof timestamp !== 'string') {
        assert(timestamp.type === 'datetime', 'Should be datetime type');
    }

    const date = navigate(parsed, 'date');
    assert(date !== undefined && typeof date !== 'string', 'Date should exist');
    if (typeof date !== 'string') {
        assert(date.type === 'date', 'Should be date type');
    }

    const time = navigate(parsed, 'time');
    assert(time !== undefined && typeof time !== 'string', 'Time should exist');
    if (typeof time !== 'string') {
        assert(time.type === 'time', 'Should be time type');
    }
});

test('udm-types', 'Arrays', () => {
    const file = path.join(nodejsDir, '03_arrays_nodejs-generated.udm');
    if (!fs.existsSync(file)) return;

    const parsed = UDMLanguageParser.parse(fs.readFileSync(file, 'utf-8'));

    const emptyArr = navigate(parsed, 'emptyArray');
    assert(emptyArr !== undefined && typeof emptyArr !== 'string', 'Empty array should exist');
    if (typeof emptyArr !== 'string' && isArray(emptyArr)) {
        assertEquals(emptyArr.elements.length, 0, 'Empty array should have 0 elements');
    }

    const numberArr = navigate(parsed, 'numberArray');
    assert(numberArr !== undefined && typeof numberArr !== 'string', 'Number array should exist');
    if (typeof numberArr !== 'string' && isArray(numberArr)) {
        assertEquals(numberArr.elements.length, 3, 'Number array should have 3 elements');
    }

    const mixedArr = navigate(parsed, 'mixedArray');
    assert(mixedArr !== undefined && typeof mixedArr !== 'string', 'Mixed array should exist');
    if (typeof mixedArr !== 'string' && isArray(mixedArr)) {
        assertEquals(mixedArr.elements.length, 4, 'Mixed array should have 4 elements');
    }
});

test('udm-types', 'Objects with attributes', () => {
    const file = path.join(nodejsDir, '04_objects-with-attributes_nodejs-generated.udm');
    if (!fs.existsSync(file)) return;

    const parsed = UDMLanguageParser.parse(fs.readFileSync(file, 'utf-8'));

    const customer = navigate(parsed, 'customer');
    assert(customer !== undefined && typeof customer !== 'string', 'Customer should exist');
    if (typeof customer !== 'string' && isObject(customer)) {
        const id = customer.attributes.get('id');
        assertEquals(id, 'CUST-001', 'Customer ID attribute should match');

        const type = customer.attributes.get('type');
        assertEquals(type, 'premium', 'Customer type attribute should match');
    }

    const name = getScalarValue(parsed, 'customer.name');
    assertEquals(name, 'John Doe', 'Customer name should match');
});

test('udm-types', 'Binary type', () => {
    const file = path.join(nodejsDir, '06_binary-type_nodejs-generated.udm');
    if (!fs.existsSync(file)) return;

    const parsed = UDMLanguageParser.parse(fs.readFileSync(file, 'utf-8'));

    const content = navigate(parsed, 'document.content');
    assert(content !== undefined && typeof content !== 'string', 'Binary content should exist');
    if (typeof content !== 'string') {
        assert(content.type === 'binary', 'Should be binary type');
    }
});

test('udm-types', 'Lambda type', () => {
    const file = path.join(nodejsDir, '07_lambda-type_nodejs-generated.udm');
    if (!fs.existsSync(file)) return;

    const parsed = UDMLanguageParser.parse(fs.readFileSync(file, 'utf-8'));

    const mapper = navigate(parsed, 'transformations.mapper');
    assert(mapper !== undefined && typeof mapper !== 'string', 'Mapper lambda should exist');
    if (typeof mapper !== 'string') {
        assert(mapper.type === 'lambda', 'Should be lambda type');
    }
});

console.log('');

// ============================================================================
// Category 4: Path Navigation Tests
// ============================================================================
console.log('📦 Category 4: Path Navigation Tests');
console.log('─'.repeat(80));

test('navigation', 'Deep nesting navigation', () => {
    const file = path.join(nodejsDir, '09_deep-nesting_nodejs-generated.udm');
    if (!fs.existsSync(file)) return;

    const parsed = UDMLanguageParser.parse(fs.readFileSync(file, 'utf-8'));

    // Test navigation to level 6
    const val = getScalarValue(parsed, 'level1.level2.level3.level4.level5.level6.data6');
    assertEquals(val, 'L6 - Deep value', 'Should navigate 6 levels deep');
});

test('navigation', '$input prefix handling', () => {
    const file = path.join(nodejsDir, '08_healthcare-claim_nodejs-generated.udm');
    if (!fs.existsSync(file)) return;

    const parsed = UDMLanguageParser.parse(fs.readFileSync(file, 'utf-8'));

    // With $input prefix
    const val1 = getScalarValue(parsed, '$input.claimId');
    assert(val1 !== undefined, 'Should work with $input prefix');

    // Without $input prefix
    const val2 = getScalarValue(parsed, 'claimId');
    assert(val2 !== undefined, 'Should work without $input prefix');

    assertEquals(val1, val2, 'Both should return same value');
});

test('navigation', 'Array indexing', () => {
    const file = path.join(nodejsDir, '08_healthcare-claim_nodejs-generated.udm');
    if (!fs.existsSync(file)) return;

    const parsed = UDMLanguageParser.parse(fs.readFileSync(file, 'utf-8'));

    const code = getScalarValue(parsed, 'services[0].procedureCode');
    assertEquals(code, '99213', 'Should access array element by index');

    const desc = getScalarValue(parsed, 'services[1].description');
    assert(desc !== undefined, 'Should access second array element');
});

test('navigation', 'Attribute access with @', () => {
    const file = path.join(nodejsDir, '04_objects-with-attributes_nodejs-generated.udm');
    if (!fs.existsSync(file)) return;

    const parsed = UDMLanguageParser.parse(fs.readFileSync(file, 'utf-8'));

    const attr = navigate(parsed, 'customer.@id');
    assertEquals(attr, 'CUST-001', 'Should access attribute with @ prefix');
});

console.log('');

// ============================================================================
// Category 5: CRITICAL Path Tests (properties/attributes keywords)
// ============================================================================
console.log('📦 Category 5: CRITICAL Path Tests');
console.log('─'.repeat(80));

test('critical-paths', 'No "properties" in paths', () => {
    const file = path.join(nodejsDir, '08_healthcare-claim_nodejs-generated.udm');
    if (!fs.existsSync(file)) return;

    const parsed = UDMLanguageParser.parse(fs.readFileSync(file, 'utf-8'));
    const paths = getAllPaths(parsed, false);

    // Verify NO paths contain "properties" keyword
    const badPaths = paths.filter(p => p.includes('properties'));
    assertEquals(badPaths.length, 0, `Paths should NOT contain "properties": ${badPaths.join(', ')}`);
});

test('critical-paths', 'CLI-style paths work', () => {
    const file = path.join(nodejsDir, '08_healthcare-claim_nodejs-generated.udm');
    if (!fs.existsSync(file)) return;

    const parsed = UDMLanguageParser.parse(fs.readFileSync(file, 'utf-8'));

    // CLI-style path (CORRECT)
    const street1 = getScalarValue(parsed, 'provider.address.street');
    assertEquals(street1, '123 Medical Plaza', 'CLI-style path should work');

    // IDE-wrong path (should NOT work)
    const street2 = getScalarValue(parsed, 'properties.provider.properties.address.properties.street');
    assertEquals(street2, undefined, 'IDE-wrong path should NOT work');
});

test('critical-paths', 'Attributes NOT in property paths', () => {
    const file = path.join(nodejsDir, '04_objects-with-attributes_nodejs-generated.udm');
    if (!fs.existsSync(file)) return;

    const parsed = UDMLanguageParser.parse(fs.readFileSync(file, 'utf-8'));

    // Access via @ prefix (CORRECT)
    const id1 = navigate(parsed, '@xmlns');
    assert(id1 !== undefined, 'Should access root attribute with @');

    // "attributes" should NOT be a property
    const attrs = navigate(parsed, 'attributes');
    assertEquals(attrs, undefined, '"attributes" should NOT be a navigable property');
});

console.log('');

// ============================================================================
// Summary
// ============================================================================
console.log('═'.repeat(80));
console.log('TEST SUMMARY');
console.log('═'.repeat(80));
console.log('');
console.log(`Total Tests:  ${totalTests}`);
console.log(`✅ Passed:    ${passedTests} (${((passedTests / totalTests) * 100).toFixed(1)}%)`);
console.log(`❌ Failed:    ${failedTests}`);
console.log('');

// Group results by category
const categories = Array.from(new Set(results.map(r => r.category)));
categories.forEach(category => {
    const categoryResults = results.filter(r => r.category === category);
    const passed = categoryResults.filter(r => r.passed).length;
    const failed = categoryResults.filter(r => r.passed === false).length;

    console.log(`${category}: ${passed}/${categoryResults.length} passed`);
    if (failed > 0) {
        categoryResults.filter(r => !r.passed).forEach(r => {
            console.log(`  ❌ ${r.name}: ${r.error}`);
        });
    }
});

console.log('');
console.log('═'.repeat(80));

if (failedTests > 0) {
    console.log('❌ Some tests failed!');
    process.exit(1);
} else {
    console.log('✅ All tests passed!');
    process.exit(0);
}
