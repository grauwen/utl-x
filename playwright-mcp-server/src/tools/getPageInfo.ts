/**
 * MCP Tool: get_page_info
 * Gets current page information (URL, title, timestamp)
 */

import { Tool, ToolInvocationResponse } from '../types/mcp.js';
import { PlaywrightClient } from '../browser/playwright-client.js';

export const getPageInfoTool: Tool = {
  name: 'get_page_info',
  description: `Retrieves current page information including URL and title.

Use this tool to confirm which page is currently being monitored and its state.

Example use cases:
- "What page is currently loaded?"
- "Get the current URL"
- "Show me page details"`,
  inputSchema: {
    type: 'object',
    properties: {},
  },
};

export async function handleGetPageInfo(
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

  try {
    const pageInfo = await client.getPageInfo();

    const output = `# Page Information\n\n` +
      `**URL:** ${pageInfo.url}\n` +
      `**Title:** ${pageInfo.title}\n` +
      `**Checked:** ${new Date().toISOString()}\n`;

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
          text: `Error retrieving page info: ${error instanceof Error ? error.message : String(error)}`,
        },
      ],
    };
  }
}
