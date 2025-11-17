"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const test_1 = require("@playwright/test");
test_1.test.describe('Basic UI Conformance', () => {
    (0, test_1.test)('Theia workbench should load successfully', async ({ page }) => {
        // Navigate to Theia
        await page.goto('/');
        // Wait for the workbench to load (preload should disappear)
        await page.waitForSelector('.theia-preload', { state: 'hidden', timeout: 30000 });
        // Verify main shell is present
        const shell = await page.locator('#theia-app-shell');
        await (0, test_1.expect)(shell).toBeVisible();
    });
    (0, test_1.test)('Main menu should be accessible', async ({ page }) => {
        await page.goto('/');
        await page.waitForSelector('.theia-preload', { state: 'hidden', timeout: 30000 });
        // Verify menu bar exists
        const menuBar = await page.locator('#theia-top-panel .p-MenuBar');
        await (0, test_1.expect)(menuBar).toBeVisible();
    });
    (0, test_1.test)('Status bar should be visible', async ({ page }) => {
        await page.goto('/');
        await page.waitForSelector('.theia-preload', { state: 'hidden', timeout: 30000 });
        // Verify status bar
        const statusBar = await page.locator('#theia-statusBar');
        await (0, test_1.expect)(statusBar).toBeVisible();
    });
});
