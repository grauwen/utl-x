/**
 * UDM Parser/Serializer Round-Trip Tests
 *
 * These tests validate that:
 * 1. Parser correctly parses UDM Language format
 * 2. Serializer correctly serializes UDM objects
 * 3. Round-trip (parse → serialize → parse) preserves data
 * 4. TypeScript implementation matches Kotlin behavior
 *
 * Run with: node lib/browser/udm/__tests__/udm-roundtrip.test.js
 */

import { UDM, UDMFactory, UDMObjectHelper } from '../udm-core';
import { UDMLanguageParser } from '../udm-language-parser';
import { UDMLanguageSerializer, toUDMLanguage } from '../udm-language-serializer';
import { navigate, getAllPaths, getScalarValue } from '../udm-navigator';

// Simple test framework
let testsPassed = 0;
let testsFailed = 0;

function assert(condition: boolean, message: string): void {
    if (!condition) {
        console.error(`  ❌ FAIL: ${message}`);
        testsFailed++;
        throw new Error(message);
    } else {
        console.log(`  ✅ PASS: ${message}`);
        testsPassed++;
    }
}

function assertEquals(actual: any, expected: any, message: string): void {
    const actualStr = JSON.stringify(actual);
    const expectedStr = JSON.stringify(expected);
    if (actualStr !== expectedStr) {
        console.error(`  ❌ FAIL: ${message}`);
        console.error(`     Expected: ${expectedStr}`);
        console.error(`     Actual:   ${actualStr}`);
        testsFailed++;
        throw new Error(message);
    } else {
        console.log(`  ✅ PASS: ${message}`);
        testsPassed++;
    }
}

function test(name: string, fn: () => void | Promise<void>): void {
    console.log(`\n🧪 Test: ${name}`);
    try {
        const result = fn();
        if (result instanceof Promise) {
            result.catch(err => {
                console.error(`  💥 Error: ${err.message}`);
            });
        }
    } catch (err: any) {
        console.error(`  💥 Error: ${err.message}`);
    }
}

// ============================================================================
// TESTS
// ============================================================================

console.log('═══════════════════════════════════════════════════════════');
console.log('UDM Parser/Serializer Round-Trip Tests');
console.log('═══════════════════════════════════════════════════════════\n');

// Test 1: Parse simple scalar values
test('Parse scalar values', () => {
    const stringUdm = '@udm-version: 1.0\n\n"hello"';
    const parsed = UDMLanguageParser.parse(stringUdm);
    assert(parsed.type === 'scalar', 'Parsed type should be scalar');
    assertEquals((parsed as any).value, 'hello', 'String value should match');

    const numberUdm = '@udm-version: 1.0\n\n42';
    const parsedNum = UDMLanguageParser.parse(numberUdm);
    assert(parsedNum.type === 'scalar', 'Parsed type should be scalar');
    assertEquals((parsedNum as any).value, 42, 'Number value should match');

    const boolUdm = '@udm-version: 1.0\n\ntrue';
    const parsedBool = UDMLanguageParser.parse(boolUdm);
    assert(parsedBool.type === 'scalar', 'Parsed type should be scalar');
    assertEquals((parsedBool as any).value, true, 'Boolean value should match');

    const nullUdm = '@udm-version: 1.0\n\nnull';
    const parsedNull = UDMLanguageParser.parse(nullUdm);
    assert(parsedNull.type === 'scalar', 'Parsed type should be scalar');
    assertEquals((parsedNull as any).value, null, 'Null value should match');
});

// Test 2: Parse simple object (shorthand format)
test('Parse simple object (shorthand)', () => {
    const udmStr = '@udm-version: 1.0\n\n{ name: "Alice", age: 30 }';
    const parsed = UDMLanguageParser.parse(udmStr);

    assert(parsed.type === 'object', 'Parsed type should be object');
    const obj = parsed as UDM & { type: 'object' };

    const name = UDMObjectHelper.get(obj, 'name');
    assert(name !== undefined, 'Should have name property');
    assert(name!.type === 'scalar', 'Name should be scalar');
    assertEquals((name as any).value, 'Alice', 'Name value should match');

    const age = UDMObjectHelper.get(obj, 'age');
    assert(age !== undefined, 'Should have age property');
    assert(age!.type === 'scalar', 'Age should be scalar');
    assertEquals((age as any).value, 30, 'Age value should match');
});

// Test 3: Parse object with attributes and properties (full format)
test('Parse object with attributes', () => {
    const udmStr = `@udm-version: 1.0

{
  attributes: {
    id: "123",
    type: "person"
  },
  properties: {
    name: "Bob",
    age: 25
  }
}`;

    const parsed = UDMLanguageParser.parse(udmStr);
    assert(parsed.type === 'object', 'Parsed type should be object');
    const obj = parsed as UDM & { type: 'object' };

    const id = UDMObjectHelper.getAttribute(obj, 'id');
    assertEquals(id, '123', 'Attribute id should match');

    const type = UDMObjectHelper.getAttribute(obj, 'type');
    assertEquals(type, 'person', 'Attribute type should match');

    const name = UDMObjectHelper.get(obj, 'name');
    assert(name !== undefined && name.type === 'scalar', 'Should have name property');
    assertEquals((name as any).value, 'Bob', 'Name value should match');
});

// Test 4: Parse array
test('Parse array', () => {
    const udmStr = '@udm-version: 1.0\n\n[1, 2, 3, "four"]';
    const parsed = UDMLanguageParser.parse(udmStr);

    assert(parsed.type === 'array', 'Parsed type should be array');
    const arr = parsed as UDM & { type: 'array' };

    assertEquals(arr.elements.length, 4, 'Array should have 4 elements');
    assertEquals((arr.elements[0] as any).value, 1, 'First element should be 1');
    assertEquals((arr.elements[3] as any).value, 'four', 'Fourth element should be "four"');
});

// Test 5: Parse nested object
test('Parse nested object', () => {
    const udmStr = `@udm-version: 1.0

{
  customer: {
    name: "Charlie",
    address: {
      street: "123 Main St",
      city: "Boston"
    }
  }
}`;

    const parsed = UDMLanguageParser.parse(udmStr);
    assert(parsed.type === 'object', 'Parsed type should be object');

    // Test navigation
    const street = navigate(parsed, 'customer.address.street');
    assert(street !== undefined && typeof street !== 'string', 'Should find street');
    assertEquals((street as any).value, '123 Main St', 'Street value should match');

    const city = getScalarValue(parsed, 'customer.address.city');
    assertEquals(city, 'Boston', 'City value should match');
});

// Test 6: Serialize simple object
test('Serialize simple object', () => {
    const udm = UDMFactory.object(new Map([
        ['name', UDMFactory.scalar('Diana')],
        ['age', UDMFactory.scalar(28)]
    ]));

    const serialized = toUDMLanguage(udm);
    console.log('  📄 Serialized:\n' + serialized.split('\n').map(l => `     ${l}`).join('\n'));

    // Parse it back
    const parsed = UDMLanguageParser.parse(serialized);
    assert(parsed.type === 'object', 'Re-parsed type should be object');

    const name = getScalarValue(parsed, 'name');
    assertEquals(name, 'Diana', 'Name should match after round-trip');

    const age = getScalarValue(parsed, 'age');
    assertEquals(age, '28', 'Age should match after round-trip');
});

// Test 7: Serialize object with attributes
test('Serialize object with attributes', () => {
    const udm = UDMFactory.object(
        new Map([
            ['name', UDMFactory.scalar('Eve')],
            ['score', UDMFactory.scalar(95)]
        ]),
        new Map([
            ['id', '456'],
            ['class', 'student']
        ])
    );

    const serialized = toUDMLanguage(udm);
    console.log('  📄 Serialized:\n' + serialized.split('\n').map(l => `     ${l}`).join('\n'));

    // Parse it back
    const parsed = UDMLanguageParser.parse(serialized);
    assert(parsed.type === 'object', 'Re-parsed type should be object');
    const obj = parsed as UDM & { type: 'object' };

    const id = UDMObjectHelper.getAttribute(obj, 'id');
    assertEquals(id, '456', 'Attribute id should match after round-trip');

    const name = getScalarValue(parsed, 'name');
    assertEquals(name, 'Eve', 'Name should match after round-trip');
});

// Test 8: Round-trip nested structure
test('Round-trip nested structure', () => {
    const original = UDMFactory.object(new Map<string, UDM>([
        ['customer', UDMFactory.object(new Map<string, UDM>([
            ['name', UDMFactory.scalar('Frank')],
            ['address', UDMFactory.object(new Map<string, UDM>([
                ['street', UDMFactory.scalar('456 Elm St')],
                ['city', UDMFactory.scalar('NYC')],
                ['zip', UDMFactory.scalar('10001')]
            ]))]
        ]))]
    ]));

    // Serialize
    const serialized = toUDMLanguage(original);
    console.log('  📄 Serialized:\n' + serialized.split('\n').map(l => `     ${l}`).join('\n'));

    // Parse back
    const parsed = UDMLanguageParser.parse(serialized);

    // Verify deep navigation works
    const street = getScalarValue(parsed, 'customer.address.street');
    assertEquals(street, '456 Elm St', 'Deep property should match');

    const zip = getScalarValue(parsed, 'customer.address.zip');
    assertEquals(zip, '10001', 'Deep property should match');
});

// Test 9: Test path navigation with $input prefix
test('Navigation with $input prefix', () => {
    const udm = UDMFactory.object(new Map([
        ['providers', UDMFactory.object(new Map([
            ['address', UDMFactory.object(new Map([
                ['street', UDMFactory.scalar('789 Oak Ave')]
            ]))]
        ]))]
    ]));

    // Test with $input prefix (should be stripped)
    const value1 = getScalarValue(udm, '$input.providers.address.street');
    assertEquals(value1, '789 Oak Ave', 'Should navigate with $input prefix');

    // Test without prefix
    const value2 = getScalarValue(udm, 'providers.address.street');
    assertEquals(value2, '789 Oak Ave', 'Should navigate without $input prefix');
});

// Test 10: Test getAllPaths
test('Get all paths from UDM', () => {
    const udm = UDMFactory.object(
        new Map<string, UDM>([
            ['name', UDMFactory.scalar('Test')],
            ['nested', UDMFactory.object(new Map<string, UDM>([
                ['field1', UDMFactory.scalar('value1')],
                ['field2', UDMFactory.scalar('value2')]
            ]))]
        ]),
        new Map<string, string>([['id', '123']])
    );

    const paths = getAllPaths(udm, true);
    console.log('  📋 All paths:', paths);

    assert(paths.includes('name'), 'Should include name');
    assert(paths.includes('nested'), 'Should include nested');
    assert(paths.includes('nested.field1'), 'Should include nested.field1');
    assert(paths.includes('nested.field2'), 'Should include nested.field2');
    assert(paths.includes('@id'), 'Should include @id attribute');
});

// Test 11: Critical test - properties/attributes should NOT appear in paths
test('CRITICAL: properties/attributes not in paths', () => {
    const udmStr = `@udm-version: 1.0

{
  attributes: {
    id: "999"
  },
  properties: {
    providers: {
      address: {
        street: "Main St"
      }
    }
  }
}`;

    const parsed = UDMLanguageParser.parse(udmStr);

    // Get all paths
    const paths = getAllPaths(parsed, false); // Don't include attributes for this test
    console.log('  📋 Paths found:', paths);

    // CRITICAL: paths should NOT include "properties" as a field
    assert(!paths.includes('properties'), 'Should NOT have "properties" in paths');
    assert(!paths.includes('properties.providers'), 'Should NOT have "properties.providers" in paths');

    // CORRECT paths should be:
    assert(paths.includes('providers'), 'Should have "providers" in paths');
    assert(paths.includes('providers.address'), 'Should have "providers.address" in paths');
    assert(paths.includes('providers.address.street'), 'Should have "providers.address.street" in paths');

    // Test navigation without "properties" in path
    const street1 = getScalarValue(parsed, 'providers.address.street');
    assertEquals(street1, 'Main St', 'Should navigate as: providers.address.street (CLI-style)');

    // This should NOT work (IDE was doing this incorrectly):
    const street2 = getScalarValue(parsed, 'properties.providers.address.street');
    assertEquals(street2, undefined, 'Should NOT navigate as: properties.providers.address.street');
});

// Test 12: DateTime types
test('DateTime types', () => {
    const udm = UDMFactory.object(new Map<string, UDM>([
        ['timestamp', UDMFactory.datetime('2024-01-15T10:30:00Z')],
        ['date', UDMFactory.date('2024-01-15')],
        ['time', UDMFactory.time('10:30:00')]
    ]));

    const serialized = toUDMLanguage(udm);
    console.log('  📄 Serialized:\n' + serialized.split('\n').map(l => `     ${l}`).join('\n'));

    const parsed = UDMLanguageParser.parse(serialized);
    const timestamp = navigate(parsed, 'timestamp');
    assert(timestamp !== undefined && typeof timestamp !== 'string', 'Should have timestamp');
    if (typeof timestamp !== 'string') {
        assert(timestamp!.type === 'datetime', 'Timestamp should be datetime type');
    }
});

// Test 13: Array with objects
test('Array with objects', () => {
    const udm = UDMFactory.object(new Map<string, UDM>([
        ['items', UDMFactory.array([
            UDMFactory.object(new Map<string, UDM>([['name', UDMFactory.scalar('Item1')], ['price', UDMFactory.scalar(10)]])),
            UDMFactory.object(new Map<string, UDM>([['name', UDMFactory.scalar('Item2')], ['price', UDMFactory.scalar(20)]]))
        ])]
    ]));

    const serialized = toUDMLanguage(udm);
    const parsed = UDMLanguageParser.parse(serialized);

    const item0Name = getScalarValue(parsed, 'items[0].name');
    assertEquals(item0Name, 'Item1', 'First item name should match');

    const item1Price = getScalarValue(parsed, 'items[1].price');
    assertEquals(item1Price, '20', 'Second item price should match');
});

// ============================================================================
// SUMMARY
// ============================================================================

console.log('\n═══════════════════════════════════════════════════════════');
console.log('Test Results');
console.log('═══════════════════════════════════════════════════════════');
console.log(`✅ Passed: ${testsPassed}`);
console.log(`❌ Failed: ${testsFailed}`);
console.log(`📊 Total:  ${testsPassed + testsFailed}`);
console.log('═══════════════════════════════════════════════════════════\n');

if (testsFailed > 0) {
    console.log('❌ Some tests failed!');
    process.exit(1);
} else {
    console.log('✅ All tests passed!');
    process.exit(0);
}
