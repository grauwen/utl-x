/**
 * MCP Tool: take_screenshot
 * Captures a screenshot of the current page
 */

import { Tool, ToolInvocationResponse } from '../types/mcp.js';
import { PlaywrightClient } from '../browser/playwright-client.js';
import * as path from 'path';
import * as fs from 'fs';

export const takeScreenshotTool: Tool = {
  name: 'take_screenshot',
  description: `Captures a screenshot of the UTLX Theia browser window for visual debugging.

Use this tool to see the current state of the UI, especially when debugging layout or rendering issues.
Screenshots can be saved to disk or returned as base64 data.

Example use cases:
- "Take a screenshot of the current page"
- "Show me what the UI looks like"
- "Capture the error dialog"`,
  inputSchema: {
    type: 'object',
    properties: {
      fullPage: {
        type: 'boolean',
        description: 'Capture the full scrollable page (default: false)',
      },
      path: {
        type: 'string',
        description: 'Optional file path to save screenshot (e.g., "debug-screenshot.png")',
      },
    },
  },
};

export async function handleTakeScreenshot(
  args: Record<string, unknown>,
  client: PlaywrightClient
): Promise<ToolInvocationResponse> {
  if (!client.isConnected()) {
    return {
      content: [
        {
          type: 'text' as const,
          text: 'Error: Not connected to browser. Please ensure UTLX Theia is running with remote debugging enabled (port 9222).',
        },
      ],
    };
  }

  const fullPage = (args.fullPage as boolean) || false;
  const screenshotPath = args.path as string | undefined;

  try {
    const result = await client.takeScreenshot({ fullPage, path: screenshotPath });

    let output = `# Screenshot Captured\n\n`;
    output += `**Time:** ${new Date().toISOString()}\n`;
    output += `**Full Page:** ${fullPage}\n\n`;

    if (result.path) {
      const absolutePath = path.resolve(result.path);
      const size = fs.statSync(absolutePath).size;
      output += `**Saved to:** ${absolutePath}\n`;
      output += `**Size:** ${(size / 1024).toFixed(2)} KB\n`;
    } else if (result.base64) {
      const sizeKB = (result.base64.length * 0.75 / 1024).toFixed(2);
      output += `**Base64 Data:** ${result.base64.substring(0, 100)}...\n`;
      output += `**Size:** ~${sizeKB} KB\n`;
      output += `\n*Tip: Use the 'path' parameter to save the screenshot to a file.*\n`;
    }

    return {
      content: [
        {
          type: 'text' as const,
          text: output,
        },
      ],
    };
  } catch (error) {
    return {
      content: [
        {
          type: 'text' as const,
          text: `Error capturing screenshot: ${error instanceof Error ? error.message : String(error)}`,
        },
      ],
    };
  }
}
