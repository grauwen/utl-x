/**
 * MCP Tool: capture_trace
 * Start/stop Playwright trace capture
 */

import { Tool, ToolInvocationResponse } from '../types/mcp.js';
import { PlaywrightClient } from '../browser/playwright-client.js';

export const captureTraceTool: Tool = {
  name: 'capture_trace',
  description: `Start or stop Playwright trace capture for detailed debugging.

Playwright traces include screenshots, snapshots, network activity, and console logs.
Traces can be viewed using the Playwright Trace Viewer (npx playwright show-trace <file>).

Example use cases:
- "Start trace capture"
- "Stop trace and save to debug-trace.zip"
- "Capture a trace of this interaction"`,
  inputSchema: {
    type: 'object',
    properties: {
      action: {
        type: 'string',
        enum: ['start', 'stop'],
        description: 'Start or stop trace capture',
      },
      outputPath: {
        type: 'string',
        description: 'Path to save trace file (required when action=stop, e.g., "debug-trace.zip")',
      },
    },
    required: ['action'],
  },
};

export async function handleCaptureTrace(
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

  const action = args.action as string;
  const outputPath = args.outputPath as string | undefined;

  try {
    if (action === 'start') {
      await client.startTrace(outputPath || './trace.zip');

      return {
        content: [
          {
            type: 'text' as const,
            text: `# Trace Started\n\n` +
              `Playwright trace capture has started.\n\n` +
              `Trace will include:\n` +
              `- Screenshots\n` +
              `- DOM snapshots\n` +
              `- Network activity\n` +
              `- Console logs\n\n` +
              `Use \`capture_trace\` with action="stop" to save the trace.`,
          },
        ],
      };
    } else if (action === 'stop') {
      if (!outputPath) {
        return {
          content: [
            {
              type: 'text' as const,
              text: 'Error: outputPath is required when stopping trace capture.',
            },
          ],
        };
      }

      await client.stopTrace(outputPath);

      return {
        content: [
          {
            type: 'text' as const,
            text: `# Trace Saved\n\n` +
              `**File:** ${outputPath}\n` +
              `**Time:** ${new Date().toISOString()}\n\n` +
              `To view the trace, run:\n` +
              `\`\`\`\n` +
              `npx playwright show-trace ${outputPath}\n` +
              `\`\`\``,
          },
        ],
      };
    } else {
      return {
        content: [
          {
            type: 'text' as const,
            text: `Error: Invalid action "${action}". Use "start" or "stop".`,
          },
        ],
      };
    }
  } catch (error) {
    return {
      content: [
        {
          type: 'text' as const,
          text: `Error managing trace: ${error instanceof Error ? error.message : String(error)}`,
        },
      ],
    };
  }
}
