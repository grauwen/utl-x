/**
 * MCP Tool: get_errors
 * Retrieves JavaScript errors and exceptions
 */

import { Tool, ToolInvocationResponse } from '../types/mcp.js';
import { PlaywrightClient } from '../browser/playwright-client.js';

export const getErrorsTool: Tool = {
  name: 'get_errors',
  description: `Retrieves JavaScript errors and uncaught exceptions from the browser.

Use this tool to quickly identify runtime errors without sifting through all console logs.
Returns error messages with optional stack traces for debugging.

Example use cases:
- "Show me all JavaScript errors"
- "Get the last error with stack trace"
- "Check for uncaught exceptions"`,
  inputSchema: {
    type: 'object',
    properties: {
      limit: {
        type: 'number',
        description: 'Maximum number of errors to return (default: 20)',
      },
      includeStackTrace: {
        type: 'boolean',
        description: 'Include full stack traces (default: true)',
      },
    },
  },
};

export async function handleGetErrors(
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

  const limit = (args.limit as number) || 20;
  const includeStackTrace = args.includeStackTrace !== false;

  try {
    const errors = client.getErrors({ limit, includeStackTrace });

    if (errors.length === 0) {
      return {
        content: [
          {
            type: 'text' as const,
            text: '✅ No JavaScript errors found. The application appears to be running without errors.',
          },
        ],
      };
    }

    // Format errors as readable text
    let output = `# JavaScript Errors\n\n`;
    output += `**Total:** ${errors.length} error${errors.length === 1 ? '' : 's'}\n\n`;
    output += `---\n\n`;

    for (let i = 0; i < errors.length; i++) {
      const error = errors[i];
      output += `## Error ${i + 1}\n\n`;
      output += `**Time:** ${error.timestamp}\n`;
      output += `**Name:** ${error.name || 'Error'}\n\n`;
      output += `**Message:**\n\`\`\`\n${error.message}\n\`\`\`\n\n`;

      if (includeStackTrace && error.stack) {
        output += `**Stack Trace:**\n\`\`\`\n${error.stack}\n\`\`\`\n\n`;
      }

      output += '---\n\n';
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
          text: `Error retrieving JavaScript errors: ${error instanceof Error ? error.message : String(error)}`,
        },
      ],
    };
  }
}
