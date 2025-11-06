import { test, expect } from '@playwright/test';
import { exec } from 'child_process';
import { promisify } from 'util';

const execAsync = promisify(exec);

test.describe('Service Auto-Start Conformance', () => {
  test('Theia should be accessible on port 3000', async ({ page }) => {
    // Navigate to Theia
    await page.goto('/');

    // Wait for page to load
    await page.waitForSelector('.theia-preload', { state: 'hidden', timeout: 30000 });

    // Verify page title
    await expect(page).toHaveTitle(/UTL-X IDE/);
  });

  test('UTLXD should start with correct command-line arguments', async () => {
    // Check if UTLXD process is running
    const { stdout } = await execAsync('ps aux | grep "java.*utlxd" | grep -v grep');

    // Verify command-line arguments
    expect(stdout).toContain('--lsp');
    expect(stdout).toContain('--lsp-transport');
    expect(stdout).toContain('socket');
    expect(stdout).toContain('--api');
    expect(stdout).toContain('--api-port');
    expect(stdout).toContain('7779');
  });

  test('UTLXD REST API should be accessible on port 7779', async () => {
    // Check health endpoint
    const response = await fetch('http://localhost:7779/api/health');
    expect(response.ok).toBeTruthy();
  });

  test('MCP Server should be running on port 3001', async () => {
    // Check if MCP server port is open
    const { stdout } = await execAsync('lsof -nP -i:3001 | grep LISTEN || echo "NOT_RUNNING"');

    expect(stdout).not.toContain('NOT_RUNNING');
    expect(stdout).toContain('3001');
  });

  test('All required services should be running', async () => {
    // Check all ports
    const { stdout: theiaCheck } = await execAsync('lsof -nP -i:3000 | grep LISTEN || echo "NOT_RUNNING"');
    const { stdout: utlxdCheck } = await execAsync('lsof -nP -i:7779 | grep LISTEN || echo "NOT_RUNNING"');
    const { stdout: mcpCheck } = await execAsync('lsof -nP -i:3001 | grep LISTEN || echo "NOT_RUNNING"');

    expect(theiaCheck).not.toContain('NOT_RUNNING');
    expect(utlxdCheck).not.toContain('NOT_RUNNING');
    expect(mcpCheck).not.toContain('NOT_RUNNING');
  });
});
