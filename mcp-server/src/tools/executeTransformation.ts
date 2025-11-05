/**
 * Tool 5: execute_transformation
 *
 * Executes a UTLX transformation with sample input data
 */

import { Tool, ToolInvocationResponse } from '../types/mcp';
import { DaemonClient } from '../client/DaemonClient';
import { Logger } from 'winston';
import { z } from 'zod';

export const executeTransformationTool: Tool = {
  name: 'execute_transformation',
  description: 'Executes a UTLX transformation with sample input data. Supports multiple input/output formats (json, xml, csv, yaml).',
  inputSchema: {
    type: 'object',
    properties: {
      utlx: {
        type: 'string',
        description: 'The UTLX transformation code to execute',
      },
      input: {
        type: 'string',
        description: 'Sample input data (as string)',
      },
      inputFormat: {
        type: 'string',
        description: 'Input format: json, xml, csv, yaml (default: json)',
        enum: ['json', 'xml', 'csv', 'yaml'],
        default: 'json',
      },
      outputFormat: {
        type: 'string',
        description: 'Output format: json, xml, csv, yaml (default: json)',
        enum: ['json', 'xml', 'csv', 'yaml'],
        default: 'json',
      },
    },
    required: ['utlx', 'input'],
  },
};

const ExecuteTransformationArgsSchema = z.object({
  utlx: z.string(),
  input: z.string(),
  inputFormat: z.enum(['json', 'xml', 'csv', 'yaml']).optional().default('json'),
  outputFormat: z.enum(['json', 'xml', 'csv', 'yaml']).optional().default('json'),
});

export async function handleExecuteTransformation(
  args: Record<string, unknown>,
  daemonClient: DaemonClient,
  logger: Logger
): Promise<ToolInvocationResponse> {
  try {
    // Validate arguments
    const { utlx, input, inputFormat, outputFormat } = ExecuteTransformationArgsSchema.parse(args);

    logger.info('Executing transformation', {
      utlxLength: utlx.length,
      inputLength: input.length,
      inputFormat,
      outputFormat,
    });

    // Call daemon execution endpoint
    const response = await daemonClient.execute({
      utlx,
      input,
      inputFormat,
      outputFormat,
    });

    if (response.success && response.output) {
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify({
              success: true,
              output: response.output,
              executionTimeMs: response.executionTimeMs,
              message: `Transformation executed successfully in ${response.executionTimeMs}ms`,
            }, null, 2),
          },
        ],
      };
    } else {
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify({
              success: false,
              error: response.error || 'Execution failed',
              executionTimeMs: response.executionTimeMs,
            }, null, 2),
          },
        ],
        isError: true,
      };
    }
  } catch (error) {
    logger.error('Error executing transformation', { error });
    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify({
            error: 'Execution failed',
            message: error instanceof Error ? error.message : 'Unknown error',
          }, null, 2),
        },
      ],
      isError: true,
    };
  }
}
