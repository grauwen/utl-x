/**
 * MCP Tool: get_console_logs
 * Retrieves browser console logs with optional filtering
 */

import { Tool, ToolInvocationResponse } from '../types/mcp.js';
import { PlaywrightClient } from '../browser/playwright-client.js';

export const getConsoleLogsTool: Tool = {
  name: 'get_console_logs',
  description: `Retrieves recent browser console logs from UTLX Theia extension.

Use this tool to debug issues by examining console output, warnings, and errors.
Supports filtering by log level (log, warn, error, info, debug) and time range.

Example use cases:
- "Check the console for errors"
- "Show me recent console warnings"
- "Get console logs from the last 5 minutes"`,
  inputSchema: {
    type: 'object',
    properties: {
      level: {
        type: 'string',
        enum: ['log', 'warn', 'error', 'info', 'debug', 'all'],
        description: 'Filter by log level (default: all)',
      },
      limit: {
        type: 'number',
        description: 'Maximum number of logs to return (default: 50)',
      },
      since: {
        type: 'string',
        description: 'ISO 8601 timestamp to get logs since (e.g., "2024-01-01T12:00:00Z")',
      },
    },
  },
};

export async function handleGetConsoleLogs(
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

  const level = (args.level as string) || 'all';
  const limit = (args.limit as number) || 50;
  const since = args.since as string | undefined;

  try {
    const logs = client.getConsoleLogs({ level: level as any, limit, since });

    if (logs.length === 0) {
      return {
        content: [
          {
            type: 'text' as const,
            text: `No console logs found${level !== 'all' ? ` for level: ${level}` : ''}.`,
          },
        ],
      };
    }

    // Format logs as readable text
    let output = `# Browser Console Logs\n\n`;
    output += `**Total:** ${logs.length} log${logs.length === 1 ? '' : 's'}\n`;
    if (level !== 'all') {
      output += `**Level:** ${level}\n`;
    }
    output += `\n---\n\n`;

    for (const log of logs) {
      const icon = getLevelIcon(log.level);
      output += `**${icon} ${log.level.toUpperCase()}** - ${log.timestamp}\n`;
      output += `\`\`\`\n${log.message}\n\`\`\`\n`;

      if (log.location) {
        output += `📍 Location: ${log.location.url}`;
        if (log.location.lineNumber) {
          output += `:${log.location.lineNumber}`;
          if (log.location.columnNumber) {
            output += `:${log.location.columnNumber}`;
          }
        }
        output += '\n';
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
          text: `Error retrieving console logs: ${error instanceof Error ? error.message : String(error)}`,
        },
      ],
    };
  }
}

function getLevelIcon(level: string): string {
  switch (level) {
    case 'error':
      return '❌';
    case 'warn':
      return '⚠️';
    case 'info':
      return 'ℹ️';
    case 'debug':
      return '🐛';
    default:
      return '📝';
  }
}
