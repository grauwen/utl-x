/**
 * MCP Tool: get_network_logs
 * Retrieves network requests and responses
 */

import { Tool, ToolInvocationResponse } from '../types/mcp.js';
import { PlaywrightClient } from '../browser/playwright-client.js';

export const getNetworkLogsTool: Tool = {
  name: 'get_network_logs',
  description: `Retrieves network requests and responses from the browser.

Use this tool to debug API calls, failed requests, or network-related issues.
Supports filtering by request type (XHR, fetch) and status (failed requests only).

Example use cases:
- "Show me failed network requests"
- "Get recent API calls"
- "Check XHR requests to the backend"`,
  inputSchema: {
    type: 'object',
    properties: {
      filter: {
        type: 'string',
        enum: ['all', 'failed', 'xhr', 'fetch'],
        description: 'Filter by request type or status (default: all)',
      },
      limit: {
        type: 'number',
        description: 'Maximum number of logs to return (default: 30)',
      },
    },
  },
};

export async function handleGetNetworkLogs(
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

  const filter = (args.filter as string) || 'all';
  const limit = (args.limit as number) || 30;

  try {
    const logs = client.getNetworkLogs({ filter: filter as any, limit });

    if (logs.length === 0) {
      return {
        content: [
          {
            type: 'text' as const,
            text: `No network requests found${filter !== 'all' ? ` for filter: ${filter}` : ''}.`,
          },
        ],
      };
    }

    // Format network logs as readable text
    let output = `# Network Activity\n\n`;
    output += `**Total:** ${logs.length} request${logs.length === 1 ? '' : 's'}\n`;
    if (filter !== 'all') {
      output += `**Filter:** ${filter}\n`;
    }
    output += `\n---\n\n`;

    for (const log of logs) {
      const statusIcon = log.failed ? '❌' : '✅';
      const typeLabel = log.type.toUpperCase();

      output += `**${statusIcon} ${log.method}** \`${typeLabel}\` - ${log.timestamp}\n\n`;
      output += `**URL:** ${log.url}\n`;

      if (log.status !== undefined) {
        output += `**Status:** ${log.status} ${log.statusText || ''}\n`;
      }

      if (log.duration !== undefined) {
        output += `**Duration:** ${log.duration}ms\n`;
      }

      if (log.failed) {
        output += `⚠️ **Request failed**\n`;
      }

      output += '\n';
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
          text: `Error retrieving network logs: ${error instanceof Error ? error.message : String(error)}`,
        },
      ],
    };
  }
}
