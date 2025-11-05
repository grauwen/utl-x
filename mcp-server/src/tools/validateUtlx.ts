/**
 * Tool 3: validate_utlx
 *
 * Validates UTLX transformation code for syntax and type errors
 */

import { Tool, ToolInvocationResponse } from '../types/mcp';
import { DaemonClient } from '../client/DaemonClient';
import { Logger } from 'winston';
import { z } from 'zod';

export const validateUtlxTool: Tool = {
  name: 'validate_utlx',
  description: 'Validates UTLX transformation code for syntax and type errors. Returns diagnostics with error/warning messages.',
  inputSchema: {
    type: 'object',
    properties: {
      utlx: {
        type: 'string',
        description: 'The UTLX transformation code to validate',
      },
      strict: {
        type: 'boolean',
        description: 'Enable strict type checking (default: false)',
        default: false,
      },
    },
    required: ['utlx'],
  },
};

const ValidateUtlxArgsSchema = z.object({
  utlx: z.string(),
  strict: z.boolean().optional().default(false),
});

export async function handleValidateUtlx(
  args: Record<string, unknown>,
  daemonClient: DaemonClient,
  logger: Logger
): Promise<ToolInvocationResponse> {
  try {
    // Validate arguments
    const { utlx, strict } = ValidateUtlxArgsSchema.parse(args);

    logger.info('Validating UTLX code', { length: utlx.length, strict });

    // Call daemon validation endpoint
    const response = await daemonClient.validate({ utlx, strict });

    if (response.valid) {
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify({
              valid: true,
              message: 'UTLX code is valid',
              diagnostics: response.diagnostics,
            }, null, 2),
          },
        ],
      };
    } else {
      const errorMessages = response.diagnostics
        .map((d) => `[${d.severity}] ${d.message}${d.line ? ` (line ${d.line})` : ''}`)
        .join('\n');

      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify({
              valid: false,
              message: 'UTLX code has errors',
              diagnostics: response.diagnostics,
              errorSummary: errorMessages,
            }, null, 2),
          },
        ],
      };
    }
  } catch (error) {
    logger.error('Error validating UTLX', { error });
    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify({
            error: 'Validation failed',
            message: error instanceof Error ? error.message : 'Unknown error',
          }, null, 2),
        },
      ],
      isError: true,
    };
  }
}
