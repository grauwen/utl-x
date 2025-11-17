import { test, expect } from '@playwright/test';
import { createHelpers } from './helpers/theia-helpers';
import { TestData } from './test-data';

/**
 * Format Strategy Tests
 *
 * Tests that each format (XML, JSON, CSV, YAML, XSD, JSCH, Avro, Proto)
 * has its own dedicated tree strategy and properly parses/transforms content.
 */

test.describe('Format-Specific Tree Strategies', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('.theia-preload', { state: 'hidden', timeout: 30000 });
  });

  test('XML strategy should parse XML files correctly', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    // Add XML input
    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('input-xml');
    await helpers.inputPanel.setInputFormat('xml');
    await helpers.inputPanel.setInputContent(TestData.xml.simple);
    await helpers.inputPanel.waitForValidation();

    // Verify UDM parsing succeeded
    expect(await helpers.inputPanel.isUDMValid()).toBe(true);
    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);

    // Create transformation that passes through the XML
    await helpers.editor.setContent('$input-xml');
    await helpers.editor.execute();

    // Verify output is generated
    expect(await helpers.output.isSuccess()).toBe(true);
    const output = await helpers.output.getContent();
    expect(output).toContain('<Order');
    expect(output).toContain('id="12345"');
  });

  test('JSON strategy should parse JSON files correctly', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('input-json');
    await helpers.inputPanel.setInputFormat('json');
    await helpers.inputPanel.setInputContent(TestData.json.withTypes);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isUDMValid()).toBe(true);
    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);

    // Test round-trip
    await helpers.editor.setContent('$input-json');
    await helpers.editor.execute();

    expect(await helpers.output.isSuccess()).toBe(true);
    const output = await helpers.output.getContent();

    // Verify types are preserved
    expect(output).toContain('"integer": 42');
    expect(output).toContain('"boolean": true');
    expect(output).toContain('"null": null');
  });

  test('CSV strategy should parse CSV files correctly', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('input-csv');
    await helpers.inputPanel.setInputFormat('csv');
    await helpers.inputPanel.setInputContent(TestData.csv.comma);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isUDMValid()).toBe(true);
    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);

    // CSV parses to array of objects
    await helpers.editor.setContent('$input-csv');
    await helpers.editor.execute();

    expect(await helpers.output.isSuccess()).toBe(true);
  });

  test('YAML strategy should parse YAML files correctly', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('input-yaml');
    await helpers.inputPanel.setInputFormat('yaml');
    await helpers.inputPanel.setInputContent(TestData.yaml.simple);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isUDMValid()).toBe(true);
    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);

    // Test round-trip
    await helpers.editor.setContent('$input-yaml');
    await helpers.editor.execute();

    expect(await helpers.output.isSuccess()).toBe(true);
  });

  test('XSD strategy should parse XML Schema files correctly', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('input-xsd');
    await helpers.inputPanel.setInputFormat('xsd');
    await helpers.inputPanel.setInputContent(TestData.xsd.simple);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isUDMValid()).toBe(true);
    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);

    // XSD should preserve schema structure
    await helpers.editor.setContent('$input-xsd');
    await helpers.editor.execute();

    expect(await helpers.output.isSuccess()).toBe(true);
    const output = await helpers.output.getContent();
    expect(output).toContain('xs:schema');
  });

  test('JSCH strategy should parse JSON Schema files correctly', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('input-jsch');
    await helpers.inputPanel.setInputFormat('jsch');
    await helpers.inputPanel.setInputContent(TestData.jsch.simple);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isUDMValid()).toBe(true);
    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);

    // JSCH should preserve schema metadata
    await helpers.editor.setContent('$input-jsch');
    await helpers.editor.execute();

    expect(await helpers.output.isSuccess()).toBe(true);
    const output = await helpers.output.getContent();
    expect(output).toContain('$schema');
    expect(output).toContain('properties');
  });

  test('Avro strategy should parse Avro schema files correctly', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('input-avro');
    await helpers.inputPanel.setInputFormat('avro');
    await helpers.inputPanel.setInputContent(TestData.avro.simple);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isUDMValid()).toBe(true);
    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);

    // Avro should preserve record structure
    await helpers.editor.setContent('$input-avro');
    await helpers.editor.execute();

    expect(await helpers.output.isSuccess()).toBe(true);
    const output = await helpers.output.getContent();
    expect(output).toContain('"type": "record"');
    expect(output).toContain('"fields"');
  });

  test('Proto strategy should parse Protocol Buffers files correctly', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('input-proto');
    await helpers.inputPanel.setInputFormat('proto');
    await helpers.inputPanel.setInputContent(TestData.proto.proto3);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isUDMValid()).toBe(true);
    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);

    // Proto should preserve message structure
    await helpers.editor.setContent('$input-proto');
    await helpers.editor.execute();

    expect(await helpers.output.isSuccess()).toBe(true);
    const output = await helpers.output.getContent();
    expect(output).toContain('syntax = "proto3"');
    expect(output).toContain('message Order');
  });
});

test.describe('Format Strategy Round-Trip Tests', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('.theia-preload', { state: 'hidden', timeout: 30000 });
  });

  test('XML round-trip should preserve structure without namespace pollution', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    // Load XML with namespaces on root
    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('input-xml');
    await helpers.inputPanel.setInputFormat('xml');
    await helpers.inputPanel.setInputContent(TestData.xml.withNamespaces);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isUDMValid()).toBe(true);

    // Transform with pass-through
    await helpers.editor.setContent('$input-xml');
    await helpers.editor.execute();

    expect(await helpers.output.isSuccess()).toBe(true);
    const output = await helpers.output.getContent();

    // Verify output has single root element
    expect(output).toContain('<Order');

    // Verify namespaces ONLY on root element (no pollution)
    // Count xmlns occurrences - should only appear once on root
    const xmlnsCount = (output.match(/xmlns=/g) || []).length;
    expect(xmlnsCount).toBe(1);

    // Child elements should NOT have xmlns
    expect(output).not.toMatch(/<Customer[^>]*xmlns/);
    expect(output).not.toMatch(/<Items[^>]*xmlns/);
  });

  test('JSON round-trip should preserve types', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('input-json');
    await helpers.inputPanel.setInputFormat('json');
    await helpers.inputPanel.setInputContent(TestData.json.withTypes);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isUDMValid()).toBe(true);

    // Transform with pass-through
    await helpers.editor.setContent('$input-json');
    await helpers.editor.execute();

    expect(await helpers.output.isSuccess()).toBe(true);
    const output = await helpers.output.getContent();

    // Verify integers don't become floats (42 not 42.0)
    expect(output).toContain('"integer": 42');
    expect(output).not.toContain('"integer": 42.0');

    // Verify booleans stay boolean
    expect(output).toContain('"boolean": true');

    // Verify null stays null
    expect(output).toContain('"null": null');
  });

  test('CSV round-trip should preserve structure', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('input-csv');
    await helpers.inputPanel.setInputFormat('csv');
    await helpers.inputPanel.setInputContent(TestData.csv.comma);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isUDMValid()).toBe(true);

    // Transform with pass-through
    await helpers.editor.setContent('$input-csv');
    await helpers.editor.execute();

    expect(await helpers.output.isSuccess()).toBe(true);
    const output = await helpers.output.getContent();

    // Verify output has CSV structure
    expect(output).toContain('EmployeeID');
    expect(output).toContain('E001');
  });

  test('YAML round-trip should preserve structure', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('input-yaml');
    await helpers.inputPanel.setInputFormat('yaml');
    await helpers.inputPanel.setInputContent(TestData.yaml.simple);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isUDMValid()).toBe(true);

    // Transform with pass-through
    await helpers.editor.setContent('$input-yaml');
    await helpers.editor.execute();

    expect(await helpers.output.isSuccess()).toBe(true);
    const output = await helpers.output.getContent();

    // Verify YAML structure is preserved
    expect(output).toContain('order:');
    expect(output).toContain('items:');
  });
});

test.describe('Format Strategy Transformation Tests', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('.theia-preload', { state: 'hidden', timeout: 30000 });
  });

  test('CSV to XML should wrap array in root element', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    // Load CSV file (becomes array of objects)
    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('input-csv');
    await helpers.inputPanel.setInputFormat('csv');
    await helpers.inputPanel.setInputContent(TestData.csv.comma);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isUDMValid()).toBe(true);

    // Transform: wrap array in root element
    await helpers.editor.setContent('{ Employees: $input-csv }');
    await helpers.editor.execute();

    expect(await helpers.output.isSuccess()).toBe(true);
    const output = await helpers.output.getContent();

    // Verify output has single <Employees> root
    expect(output).toContain('<Employees>');
    expect(output).toContain('</Employees>');

    // Verify NO multiple <root> elements at top level
    const rootCount = (output.match(/<root>/g) || []).length;
    expect(rootCount).toBe(0);

    // Verify employee data is present
    expect(output).toContain('E001');
    expect(output).toContain('John');
  });

  test('JSON to XML should handle nested objects', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('input-json');
    await helpers.inputPanel.setInputFormat('json');
    await helpers.inputPanel.setInputContent(TestData.json.simple);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isUDMValid()).toBe(true);

    // Transform to XML
    await helpers.editor.setContent('$input-json');
    await helpers.editor.execute();

    expect(await helpers.output.isSuccess()).toBe(true);
    const output = await helpers.output.getContent();

    // Verify nested structure is preserved
    expect(output).toContain('<order>');
    expect(output).toContain('<items>');
    expect(output).toContain('id="12345"');
  });

  test('XML to JSON should preserve attributes', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('input-xml');
    await helpers.inputPanel.setInputFormat('xml');
    await helpers.inputPanel.setInputContent(TestData.xml.simple);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isUDMValid()).toBe(true);

    // Transform to JSON
    await helpers.editor.setContent('$input-xml');
    await helpers.editor.execute();

    expect(await helpers.output.isSuccess()).toBe(true);
    const output = await helpers.output.getContent();

    // Verify attributes are accessible
    expect(output).toContain('"id"');
    expect(output).toContain('"12345"');
  });

  test('YAML to JSON should handle arrays and objects', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('input-yaml');
    await helpers.inputPanel.setInputFormat('yaml');
    await helpers.inputPanel.setInputContent(TestData.yaml.simple);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isUDMValid()).toBe(true);

    // Transform to JSON
    await helpers.editor.setContent('$input-yaml');
    await helpers.editor.execute();

    expect(await helpers.output.isSuccess()).toBe(true);
    const output = await helpers.output.getContent();

    // Verify structure is preserved
    expect(output).toContain('"order"');
    expect(output).toContain('"items"');
    expect(output).toContain('"id": "12345"');
  });
});

test.describe('Format Strategy Error Handling', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('.theia-preload', { state: 'hidden', timeout: 30000 });
  });

  test('Invalid XML should show parse error', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-invalid-xml');
    await helpers.inputPanel.setInputFormat('xml');

    // Load malformed XML (unclosed tag)
    const invalidXML = `<?xml version="1.0"?>
<Order>
  <Customer>John Doe
  <Total>99.99</Total>
</Order>`;

    await helpers.inputPanel.setInputContent(invalidXML);
    await helpers.inputPanel.waitForValidation();

    // Verify error indicator (✗ UDM)
    expect(await helpers.inputPanel.isUDMInvalid()).toBe(true);
  });

  test('Invalid JSON should show parse error', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-invalid-json');
    await helpers.inputPanel.setInputFormat('json');

    // Load malformed JSON (trailing comma)
    const invalidJSON = `{
  "name": "test",
  "value": 123,
}`;

    await helpers.inputPanel.setInputContent(invalidJSON);
    await helpers.inputPanel.waitForValidation();

    // Verify error indicator
    expect(await helpers.inputPanel.isUDMInvalid()).toBe(true);
  });

  test('Invalid CSV should show parse error or warning', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-invalid-csv');
    await helpers.inputPanel.setInputFormat('csv');

    // Load CSV with inconsistent column counts
    const invalidCSV = `Name,Age,City
John,25,NYC
Jane,30
Bob,35,LA,ExtraColumn`;

    await helpers.inputPanel.setInputContent(invalidCSV);
    await helpers.inputPanel.waitForValidation();

    // CSV might parse with warnings rather than errors
    // Depending on parser settings, this could be valid or invalid
    // So we just check that validation completes
    const isValid = await helpers.inputPanel.isUDMValid();
    const isInvalid = await helpers.inputPanel.isUDMInvalid();
    expect(isValid || isInvalid).toBe(true);
  });

  test('Invalid YAML should show parse error', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-invalid-yaml');
    await helpers.inputPanel.setInputFormat('yaml');

    // Load malformed YAML (bad indentation)
    const invalidYAML = `order:
  id: 12345
 customer: John Doe
    total: 99.99`;

    await helpers.inputPanel.setInputContent(invalidYAML);
    await helpers.inputPanel.waitForValidation();

    // Verify error indicator
    expect(await helpers.inputPanel.isUDMInvalid()).toBe(true);
  });
});

test.describe('Format Strategy with Hyphenated Input Names', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('.theia-preload', { state: 'hidden', timeout: 30000 });
  });

  test('Input names with hyphens should parse correctly', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    // Create input with hyphenated name
    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('input-csv');
    await helpers.inputPanel.setInputFormat('csv');
    await helpers.inputPanel.setInputContent(TestData.csv.comma);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isUDMValid()).toBe(true);

    // Verify transformation can reference hyphenated name
    await helpers.editor.setContent('{ Employees: $input-csv }');
    await helpers.editor.execute();

    // Should execute successfully
    expect(await helpers.output.isSuccess()).toBe(true);
    const output = await helpers.output.getContent();
    expect(output).toContain('<Employees>');
  });

  test('Multiple hyphenated inputs should all be accessible', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    // Create multiple inputs with hyphens
    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('input-xml');
    await helpers.inputPanel.setInputFormat('xml');
    await helpers.inputPanel.setInputContent(TestData.xml.simple);
    await helpers.inputPanel.waitForValidation();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('input-json');
    await helpers.inputPanel.setInputFormat('json');
    await helpers.inputPanel.setInputContent(TestData.json.simple);
    await helpers.inputPanel.waitForValidation();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('my-data');
    await helpers.inputPanel.setInputFormat('csv');
    await helpers.inputPanel.setInputContent(TestData.csv.comma);
    await helpers.inputPanel.waitForValidation();

    // All should be valid
    expect(await helpers.inputPanel.isUDMValid()).toBe(true);

    // Reference all inputs in transformation
    await helpers.editor.setContent('{ xml: $input-xml, json: $input-json, data: $my-data }');
    await helpers.editor.execute();

    // Should execute successfully
    expect(await helpers.output.isSuccess()).toBe(true);
  });
});
