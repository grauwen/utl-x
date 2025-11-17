"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const test_1 = require("@playwright/test");
/**
 * Playwright configuration for Theia E2E tests
 * See https://playwright.dev/docs/test-configuration
 */
exports.default = (0, test_1.defineConfig)({
    testDir: './tests',
    /* Run tests in files in parallel */
    fullyParallel: false,
    /* Fail the build on CI if you accidentally left test.only in the source code. */
    forbidOnly: !!process.env.CI,
    /* Retry on CI only */
    retries: process.env.CI ? 2 : 0,
    /* Opt out of parallel tests on CI. */
    workers: 1,
    /* Reporter to use. See https://playwright.dev/docs/test-reporters */
    reporter: 'html',
    /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
    use: {
        /* Base URL for Theia */
        baseURL: 'http://localhost:3000',
        /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
        trace: 'on-first-retry',
        /* Screenshot on failure */
        screenshot: 'only-on-failure',
        /* Video on failure */
        video: 'retain-on-failure',
    },
    /* Configure projects for major browsers */
    projects: [
        {
            name: 'chromium',
            use: { ...test_1.devices['Desktop Chrome'] },
        },
    ],
    /* Web server for Theia */
    // Note: Tests assume Theia is already running on port 3000
    // The test runner script will handle starting/stopping Theia
});
