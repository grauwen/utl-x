"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const test_1 = require("@playwright/test");
const child_process_1 = require("child_process");
const util_1 = require("util");
const execAsync = (0, util_1.promisify)(child_process_1.exec);
test_1.test.describe('Service Auto-Start Conformance', () => {
    (0, test_1.test)('Theia should be accessible on port 3000', async ({ page }) => {
        // Navigate to Theia
        await page.goto('/');
        // Wait for page to load
        await page.waitForSelector('.theia-preload', { state: 'hidden', timeout: 30000 });
        // Verify page title
        await (0, test_1.expect)(page).toHaveTitle(/UTL-X IDE/);
    });
    (0, test_1.test)('UTLXD should start with correct command-line arguments', async () => {
        // Check if UTLXD process is running
        const { stdout } = await execAsync('ps aux | grep "java.*utlxd" | grep -v grep');
        // Verify command-line arguments
        (0, test_1.expect)(stdout).toContain('--lsp');
        (0, test_1.expect)(stdout).toContain('--lsp-transport');
        (0, test_1.expect)(stdout).toContain('socket');
        (0, test_1.expect)(stdout).toContain('--api');
        (0, test_1.expect)(stdout).toContain('--api-port');
        (0, test_1.expect)(stdout).toContain('7779');
    });
    (0, test_1.test)('UTLXD REST API should be accessible on port 7779', async () => {
        // Check health endpoint
        const response = await fetch('http://localhost:7779/api/health');
        (0, test_1.expect)(response.ok).toBeTruthy();
    });
    (0, test_1.test)('MCP Server should be running on port 3001', async () => {
        // Check if MCP server port is open
        const { stdout } = await execAsync('lsof -nP -i:3001 | grep LISTEN || echo "NOT_RUNNING"');
        (0, test_1.expect)(stdout).not.toContain('NOT_RUNNING');
        (0, test_1.expect)(stdout).toContain('3001');
    });
    (0, test_1.test)('All required services should be running', async () => {
        // Check all ports
        const { stdout: theiaCheck } = await execAsync('lsof -nP -i:3000 | grep LISTEN || echo "NOT_RUNNING"');
        const { stdout: utlxdCheck } = await execAsync('lsof -nP -i:7779 | grep LISTEN || echo "NOT_RUNNING"');
        const { stdout: mcpCheck } = await execAsync('lsof -nP -i:3001 | grep LISTEN || echo "NOT_RUNNING"');
        (0, test_1.expect)(theiaCheck).not.toContain('NOT_RUNNING');
        (0, test_1.expect)(utlxdCheck).not.toContain('NOT_RUNNING');
        (0, test_1.expect)(mcpCheck).not.toContain('NOT_RUNNING');
    });
});
