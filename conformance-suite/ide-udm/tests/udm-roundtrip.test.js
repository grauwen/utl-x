"use strict";
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
Object.defineProperty(exports, "__esModule", { value: true });
const udm_core_1 = require("../udm-core");
const udm_language_parser_1 = require("../udm-language-parser");
const udm_language_serializer_1 = require("../udm-language-serializer");
const udm_navigator_1 = require("../udm-navigator");
// Simple test framework
let testsPassed = 0;
let testsFailed = 0;
function assert(condition, message) {
    if (!condition) {
        console.error(`  ❌ FAIL: ${message}`);
        testsFailed++;
        throw new Error(message);
    }
    else {
        console.log(`  ✅ PASS: ${message}`);
        testsPassed++;
    }
}
function assertEquals(actual, expected, message) {
    const actualStr = JSON.stringify(actual);
    const expectedStr = JSON.stringify(expected);
    if (actualStr !== expectedStr) {
        console.error(`  ❌ FAIL: ${message}`);
        console.error(`     Expected: ${expectedStr}`);
        console.error(`     Actual:   ${actualStr}`);
        testsFailed++;
        throw new Error(message);
    }
    else {
        console.log(`  ✅ PASS: ${message}`);
        testsPassed++;
    }
}
function test(name, fn) {
    console.log(`\n🧪 Test: ${name}`);
    try {
        const result = fn();
        if (result instanceof Promise) {
            result.catch(err => {
                console.error(`  💥 Error: ${err.message}`);
            });
        }
    }
    catch (err) {
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
    const parsed = udm_language_parser_1.UDMLanguageParser.parse(stringUdm);
    assert(parsed.type === 'scalar', 'Parsed type should be scalar');
    assertEquals(parsed.value, 'hello', 'String value should match');
    const numberUdm = '@udm-version: 1.0\n\n42';
    const parsedNum = udm_language_parser_1.UDMLanguageParser.parse(numberUdm);
    assert(parsedNum.type === 'scalar', 'Parsed type should be scalar');
    assertEquals(parsedNum.value, 42, 'Number value should match');
    const boolUdm = '@udm-version: 1.0\n\ntrue';
    const parsedBool = udm_language_parser_1.UDMLanguageParser.parse(boolUdm);
    assert(parsedBool.type === 'scalar', 'Parsed type should be scalar');
    assertEquals(parsedBool.value, true, 'Boolean value should match');
    const nullUdm = '@udm-version: 1.0\n\nnull';
    const parsedNull = udm_language_parser_1.UDMLanguageParser.parse(nullUdm);
    assert(parsedNull.type === 'scalar', 'Parsed type should be scalar');
    assertEquals(parsedNull.value, null, 'Null value should match');
});
// Test 2: Parse simple object (shorthand format)
test('Parse simple object (shorthand)', () => {
    const udmStr = '@udm-version: 1.0\n\n{ name: "Alice", age: 30 }';
    const parsed = udm_language_parser_1.UDMLanguageParser.parse(udmStr);
    assert(parsed.type === 'object', 'Parsed type should be object');
    const obj = parsed;
    const name = udm_core_1.UDMObjectHelper.get(obj, 'name');
    assert(name !== undefined, 'Should have name property');
    assert(name.type === 'scalar', 'Name should be scalar');
    assertEquals(name.value, 'Alice', 'Name value should match');
    const age = udm_core_1.UDMObjectHelper.get(obj, 'age');
    assert(age !== undefined, 'Should have age property');
    assert(age.type === 'scalar', 'Age should be scalar');
    assertEquals(age.value, 30, 'Age value should match');
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
    const parsed = udm_language_parser_1.UDMLanguageParser.parse(udmStr);
    assert(parsed.type === 'object', 'Parsed type should be object');
    const obj = parsed;
    const id = udm_core_1.UDMObjectHelper.getAttribute(obj, 'id');
    assertEquals(id, '123', 'Attribute id should match');
    const type = udm_core_1.UDMObjectHelper.getAttribute(obj, 'type');
    assertEquals(type, 'person', 'Attribute type should match');
    const name = udm_core_1.UDMObjectHelper.get(obj, 'name');
    assert(name !== undefined && name.type === 'scalar', 'Should have name property');
    assertEquals(name.value, 'Bob', 'Name value should match');
});
// Test 4: Parse array
test('Parse array', () => {
    const udmStr = '@udm-version: 1.0\n\n[1, 2, 3, "four"]';
    const parsed = udm_language_parser_1.UDMLanguageParser.parse(udmStr);
    assert(parsed.type === 'array', 'Parsed type should be array');
    const arr = parsed;
    assertEquals(arr.elements.length, 4, 'Array should have 4 elements');
    assertEquals(arr.elements[0].value, 1, 'First element should be 1');
    assertEquals(arr.elements[3].value, 'four', 'Fourth element should be "four"');
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
    const parsed = udm_language_parser_1.UDMLanguageParser.parse(udmStr);
    assert(parsed.type === 'object', 'Parsed type should be object');
    // Test navigation
    const street = (0, udm_navigator_1.navigate)(parsed, 'customer.address.street');
    assert(street !== undefined && typeof street !== 'string', 'Should find street');
    assertEquals(street.value, '123 Main St', 'Street value should match');
    const city = (0, udm_navigator_1.getScalarValue)(parsed, 'customer.address.city');
    assertEquals(city, 'Boston', 'City value should match');
});
// Test 6: Serialize simple object
test('Serialize simple object', () => {
    const udm = udm_core_1.UDMFactory.object(new Map([
        ['name', udm_core_1.UDMFactory.scalar('Diana')],
        ['age', udm_core_1.UDMFactory.scalar(28)]
    ]));
    const serialized = (0, udm_language_serializer_1.toUDMLanguage)(udm);
    console.log('  📄 Serialized:\n' + serialized.split('\n').map(l => `     ${l}`).join('\n'));
    // Parse it back
    const parsed = udm_language_parser_1.UDMLanguageParser.parse(serialized);
    assert(parsed.type === 'object', 'Re-parsed type should be object');
    const name = (0, udm_navigator_1.getScalarValue)(parsed, 'name');
    assertEquals(name, 'Diana', 'Name should match after round-trip');
    const age = (0, udm_navigator_1.getScalarValue)(parsed, 'age');
    assertEquals(age, '28', 'Age should match after round-trip');
});
// Test 7: Serialize object with attributes
test('Serialize object with attributes', () => {
    const udm = udm_core_1.UDMFactory.object(new Map([
        ['name', udm_core_1.UDMFactory.scalar('Eve')],
        ['score', udm_core_1.UDMFactory.scalar(95)]
    ]), new Map([
        ['id', '456'],
        ['class', 'student']
    ]));
    const serialized = (0, udm_language_serializer_1.toUDMLanguage)(udm);
    console.log('  📄 Serialized:\n' + serialized.split('\n').map(l => `     ${l}`).join('\n'));
    // Parse it back
    const parsed = udm_language_parser_1.UDMLanguageParser.parse(serialized);
    assert(parsed.type === 'object', 'Re-parsed type should be object');
    const obj = parsed;
    const id = udm_core_1.UDMObjectHelper.getAttribute(obj, 'id');
    assertEquals(id, '456', 'Attribute id should match after round-trip');
    const name = (0, udm_navigator_1.getScalarValue)(parsed, 'name');
    assertEquals(name, 'Eve', 'Name should match after round-trip');
});
// Test 8: Round-trip nested structure
test('Round-trip nested structure', () => {
    const original = udm_core_1.UDMFactory.object(new Map([
        ['customer', udm_core_1.UDMFactory.object(new Map([
                ['name', udm_core_1.UDMFactory.scalar('Frank')],
                ['address', udm_core_1.UDMFactory.object(new Map([
                        ['street', udm_core_1.UDMFactory.scalar('456 Elm St')],
                        ['city', udm_core_1.UDMFactory.scalar('NYC')],
                        ['zip', udm_core_1.UDMFactory.scalar('10001')]
                    ]))]
            ]))]
    ]));
    // Serialize
    const serialized = (0, udm_language_serializer_1.toUDMLanguage)(original);
    console.log('  📄 Serialized:\n' + serialized.split('\n').map(l => `     ${l}`).join('\n'));
    // Parse back
    const parsed = udm_language_parser_1.UDMLanguageParser.parse(serialized);
    // Verify deep navigation works
    const street = (0, udm_navigator_1.getScalarValue)(parsed, 'customer.address.street');
    assertEquals(street, '456 Elm St', 'Deep property should match');
    const zip = (0, udm_navigator_1.getScalarValue)(parsed, 'customer.address.zip');
    assertEquals(zip, '10001', 'Deep property should match');
});
// Test 9: Test path navigation with $input prefix
test('Navigation with $input prefix', () => {
    const udm = udm_core_1.UDMFactory.object(new Map([
        ['providers', udm_core_1.UDMFactory.object(new Map([
                ['address', udm_core_1.UDMFactory.object(new Map([
                        ['street', udm_core_1.UDMFactory.scalar('789 Oak Ave')]
                    ]))]
            ]))]
    ]));
    // Test with $input prefix (should be stripped)
    const value1 = (0, udm_navigator_1.getScalarValue)(udm, '$input.providers.address.street');
    assertEquals(value1, '789 Oak Ave', 'Should navigate with $input prefix');
    // Test without prefix
    const value2 = (0, udm_navigator_1.getScalarValue)(udm, 'providers.address.street');
    assertEquals(value2, '789 Oak Ave', 'Should navigate without $input prefix');
});
// Test 10: Test getAllPaths
test('Get all paths from UDM', () => {
    const udm = udm_core_1.UDMFactory.object(new Map([
        ['name', udm_core_1.UDMFactory.scalar('Test')],
        ['nested', udm_core_1.UDMFactory.object(new Map([
                ['field1', udm_core_1.UDMFactory.scalar('value1')],
                ['field2', udm_core_1.UDMFactory.scalar('value2')]
            ]))]
    ]), new Map([['id', '123']]));
    const paths = (0, udm_navigator_1.getAllPaths)(udm, true);
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
    const parsed = udm_language_parser_1.UDMLanguageParser.parse(udmStr);
    // Get all paths
    const paths = (0, udm_navigator_1.getAllPaths)(parsed, false); // Don't include attributes for this test
    console.log('  📋 Paths found:', paths);
    // CRITICAL: paths should NOT include "properties" as a field
    assert(!paths.includes('properties'), 'Should NOT have "properties" in paths');
    assert(!paths.includes('properties.providers'), 'Should NOT have "properties.providers" in paths');
    // CORRECT paths should be:
    assert(paths.includes('providers'), 'Should have "providers" in paths');
    assert(paths.includes('providers.address'), 'Should have "providers.address" in paths');
    assert(paths.includes('providers.address.street'), 'Should have "providers.address.street" in paths');
    // Test navigation without "properties" in path
    const street1 = (0, udm_navigator_1.getScalarValue)(parsed, 'providers.address.street');
    assertEquals(street1, 'Main St', 'Should navigate as: providers.address.street (CLI-style)');
    // This should NOT work (IDE was doing this incorrectly):
    const street2 = (0, udm_navigator_1.getScalarValue)(parsed, 'properties.providers.address.street');
    assertEquals(street2, undefined, 'Should NOT navigate as: properties.providers.address.street');
});
// Test 12: DateTime types
test('DateTime types', () => {
    const udm = udm_core_1.UDMFactory.object(new Map([
        ['timestamp', udm_core_1.UDMFactory.datetime('2024-01-15T10:30:00Z')],
        ['date', udm_core_1.UDMFactory.date('2024-01-15')],
        ['time', udm_core_1.UDMFactory.time('10:30:00')]
    ]));
    const serialized = (0, udm_language_serializer_1.toUDMLanguage)(udm);
    console.log('  📄 Serialized:\n' + serialized.split('\n').map(l => `     ${l}`).join('\n'));
    const parsed = udm_language_parser_1.UDMLanguageParser.parse(serialized);
    const timestamp = (0, udm_navigator_1.navigate)(parsed, 'timestamp');
    assert(timestamp !== undefined && typeof timestamp !== 'string', 'Should have timestamp');
    if (typeof timestamp !== 'string') {
        assert(timestamp.type === 'datetime', 'Timestamp should be datetime type');
    }
});
// Test 13: Array with objects
test('Array with objects', () => {
    const udm = udm_core_1.UDMFactory.object(new Map([
        ['items', udm_core_1.UDMFactory.array([
                udm_core_1.UDMFactory.object(new Map([['name', udm_core_1.UDMFactory.scalar('Item1')], ['price', udm_core_1.UDMFactory.scalar(10)]])),
                udm_core_1.UDMFactory.object(new Map([['name', udm_core_1.UDMFactory.scalar('Item2')], ['price', udm_core_1.UDMFactory.scalar(20)]]))
            ])]
    ]));
    const serialized = (0, udm_language_serializer_1.toUDMLanguage)(udm);
    const parsed = udm_language_parser_1.UDMLanguageParser.parse(serialized);
    const item0Name = (0, udm_navigator_1.getScalarValue)(parsed, 'items[0].name');
    assertEquals(item0Name, 'Item1', 'First item name should match');
    const item1Price = (0, udm_navigator_1.getScalarValue)(parsed, 'items[1].price');
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
}
else {
    console.log('✅ All tests passed!');
    process.exit(0);
}
//# sourceMappingURL=udm-roundtrip.test.js.map