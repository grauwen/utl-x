import { test, expect } from '@playwright/test';

test.describe('Basic UI Conformance', () => {
  test('Theia workbench should load successfully', async ({ page }) => {
    // Navigate to Theia
    await page.goto('/');

    // Wait for the workbench to load (preload should disappear)
    await page.waitForSelector('.theia-preload', { state: 'hidden', timeout: 30000 });

    // Verify main shell is present
    const shell = await page.locator('#theia-app-shell');
    await expect(shell).toBeVisible();
  });

  test('Main menu should be accessible', async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('.theia-preload', { state: 'hidden', timeout: 30000 });

    // Verify menu bar exists
    const menuBar = await page.locator('#theia-top-panel .p-MenuBar');
    await expect(menuBar).toBeVisible();
  });

  test('Status bar should be visible', async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('.theia-preload', { state: 'hidden', timeout: 30000 });

    // Verify status bar
    const statusBar = await page.locator('#theia-statusBar');
    await expect(statusBar).toBeVisible();
  });
});
