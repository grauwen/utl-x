import { test, expect } from '@playwright/test';
import { createHelpers } from './helpers/theia-helpers';
import { TestData, ExpectedDetection } from './test-data';

/**
 * Format Detection Tests
 *
 * Tests the format detection indicators that appear when loading files
 * into the input panel. Verifies both positive matches (✓ Format) and
 * mismatches (⚠ Format).
 */

test.describe('Format Detection Indicators', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('.theia-preload', { state: 'hidden', timeout: 30000 });
  });

  test('XML file should show green format indicator', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    // Add new input with XML format
    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-xml');
    await helpers.inputPanel.setInputFormat('xml');

    // Load XML content
    await helpers.inputPanel.setInputContent(TestData.xml.simple);

    // Wait for validation
    await helpers.inputPanel.waitForValidation();

    // Check format indicator - should match
    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);
    expect(await helpers.inputPanel.getDetectedFormat()).toBe('xml');

    // Check UDM validation - should succeed
    expect(await helpers.inputPanel.isUDMValid()).toBe(true);
  });

  test('XML content with YAML format should show red format mismatch', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    // Add new input with YAML format
    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-mismatch');
    await helpers.inputPanel.setInputFormat('yaml');

    // Load XML content (mismatch!)
    await helpers.inputPanel.setInputContent(TestData.xml.simple);

    // Wait for validation
    await helpers.inputPanel.waitForValidation();

    // Check format indicator - should show mismatch
    expect(await helpers.inputPanel.isFormatMismatch()).toBe(true);
    expect(await helpers.inputPanel.getDetectedFormat()).toBe('xml');

    // UDM might still parse (YAML accepts any text), but format is wrong
  });

  test('JSON file should show green format indicator', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-json');
    await helpers.inputPanel.setInputFormat('json');
    await helpers.inputPanel.setInputContent(TestData.json.simple);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);
    expect(await helpers.inputPanel.getDetectedFormat()).toBe('json');
    expect(await helpers.inputPanel.isUDMValid()).toBe(true);
  });

  test('CSV file should show green format indicator', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-csv');
    await helpers.inputPanel.setInputFormat('csv');
    await helpers.inputPanel.setInputContent(TestData.csv.comma);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);
    expect(await helpers.inputPanel.getDetectedFormat()).toBe('csv');
    expect(await helpers.inputPanel.isUDMValid()).toBe(true);
  });

  test('YAML file should show green format indicator', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-yaml');
    await helpers.inputPanel.setInputFormat('yaml');
    await helpers.inputPanel.setInputContent(TestData.yaml.simple);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);
    expect(await helpers.inputPanel.getDetectedFormat()).toBe('yaml');
    expect(await helpers.inputPanel.isUDMValid()).toBe(true);
  });

  test('XSD file should show green format indicator and not be confused with XML', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-xsd');
    await helpers.inputPanel.setInputFormat('xsd');
    await helpers.inputPanel.setInputContent(TestData.xsd.simple);
    await helpers.inputPanel.waitForValidation();

    // Critical: Should detect as XSD, not XML
    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);
    expect(await helpers.inputPanel.getDetectedFormat()).toBe('xsd');
    expect(await helpers.inputPanel.isUDMValid()).toBe(true);
  });

  test('JSON Schema (JSCH) should be detected correctly', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-jsch');
    await helpers.inputPanel.setInputFormat('jsch');
    await helpers.inputPanel.setInputContent(TestData.jsch.simple);
    await helpers.inputPanel.waitForValidation();

    // Should detect as JSCH, not generic JSON
    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);
    expect(await helpers.inputPanel.getDetectedFormat()).toBe('jsch');
    expect(await helpers.inputPanel.isUDMValid()).toBe(true);
  });

  test('Protocol Buffers (Proto) should be detected correctly', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-proto');
    await helpers.inputPanel.setInputFormat('proto');
    await helpers.inputPanel.setInputContent(TestData.proto.proto3);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);
    expect(await helpers.inputPanel.getDetectedFormat()).toBe('proto');
    expect(await helpers.inputPanel.isUDMValid()).toBe(true);
  });

  test('Avro schema should be detected correctly', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-avro');
    await helpers.inputPanel.setInputFormat('avro');
    await helpers.inputPanel.setInputContent(TestData.avro.simple);
    await helpers.inputPanel.waitForValidation();

    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);
    expect(await helpers.inputPanel.getDetectedFormat()).toBe('avro');
    expect(await helpers.inputPanel.isUDMValid()).toBe(true);
  });

  test('Format indicator should update when format is changed', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    // Start with XML format and XML content
    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-change');
    await helpers.inputPanel.setInputFormat('xml');
    await helpers.inputPanel.setInputContent(TestData.xml.simple);
    await helpers.inputPanel.waitForValidation();

    // Should match
    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);

    // Change format to YAML (mismatch!)
    await helpers.inputPanel.setInputFormat('yaml');
    await helpers.inputPanel.waitForValidation();

    // Should now show mismatch
    expect(await helpers.inputPanel.isFormatMismatch()).toBe(true);
    expect(await helpers.inputPanel.getDetectedFormat()).toBe('xml');
  });

  test('Unknown format should show yellow ? Format indicator', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-unknown');
    await helpers.inputPanel.setInputFormat('xml');

    // Load content that doesn't match any known format
    await helpers.inputPanel.setInputContent('This is just plain text with no structure');
    await helpers.inputPanel.waitForValidation();

    // Should show unknown
    expect(await helpers.inputPanel.isFormatUnknown()).toBe(true);
  });
});

test.describe('Format Detection Edge Cases', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('.theia-preload', { state: 'hidden', timeout: 30000 });
  });

  test('XML with leading comment should be detected as XML', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-xml-comment');
    await helpers.inputPanel.setInputFormat('xml');
    await helpers.inputPanel.setInputContent(TestData.xml.withComment);
    await helpers.inputPanel.waitForValidation();

    // Should detect as XML even with leading comment
    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);
    expect(await helpers.inputPanel.getDetectedFormat()).toBe('xml');
    expect(await helpers.inputPanel.isUDMValid()).toBe(true);
  });

  test('XML with DOCTYPE should be detected as XML', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-xml-doctype');
    await helpers.inputPanel.setInputFormat('xml');
    await helpers.inputPanel.setInputContent(TestData.xml.withDoctype);
    await helpers.inputPanel.waitForValidation();

    // Should detect as XML
    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);
    expect(await helpers.inputPanel.getDetectedFormat()).toBe('xml');
  });

  test('CSV with semicolon delimiter should be detected as CSV', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-csv-semicolon');
    await helpers.inputPanel.setInputFormat('csv');
    await helpers.inputPanel.setInputContent(TestData.csv.semicolon);
    await helpers.inputPanel.waitForValidation();

    // Should detect as CSV with semicolon delimiter
    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);
    expect(await helpers.inputPanel.getDetectedFormat()).toBe('csv');
  });

  test('CSV with tab delimiter should be detected as CSV', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-csv-tab');
    await helpers.inputPanel.setInputFormat('csv');
    await helpers.inputPanel.setInputContent(TestData.csv.tab);
    await helpers.inputPanel.waitForValidation();

    // Should detect as CSV with tab delimiter
    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);
    expect(await helpers.inputPanel.getDetectedFormat()).toBe('csv');
  });

  test('CSV with pipe delimiter should be detected as CSV', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-csv-pipe');
    await helpers.inputPanel.setInputFormat('csv');
    await helpers.inputPanel.setInputContent(TestData.csv.pipe);
    await helpers.inputPanel.waitForValidation();

    // Should detect as CSV with pipe delimiter
    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);
    expect(await helpers.inputPanel.getDetectedFormat()).toBe('csv');
  });

  test('Regular XML with xmlns:xsi should NOT be detected as XSD', async ({ page }) => {
    const helpers = createHelpers(page);
    await helpers.theia.waitForTheiaReady();

    await helpers.inputPanel.addInput();
    await helpers.inputPanel.setInputName('test-xml-with-xsi');
    await helpers.inputPanel.setInputFormat('xml');
    await helpers.inputPanel.setInputContent(TestData.xml.withNamespaces);
    await helpers.inputPanel.waitForValidation();

    // Critical: xmlns:xsi is used for validation, not schema definition
    // Should detect as xml, not xsd
    expect(await helpers.inputPanel.isFormatMatch()).toBe(true);
    expect(await helpers.inputPanel.getDetectedFormat()).toBe('xml');
    expect(await helpers.inputPanel.isUDMValid()).toBe(true);
  });
});
