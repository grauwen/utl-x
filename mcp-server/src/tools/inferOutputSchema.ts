/**
 * Tool 4: infer_output_schema
 *
 * Infers the output schema from UTLX transformation code
 */

import { Tool, ToolInvocationResponse } from '../types/mcp';
import { DaemonClient } from '../client/DaemonClient';
import { Logger } from 'winston';
import { z } from 'zod';

export const inferOutputSchemaTool: Tool = {
  name: 'infer_output_schema',
  description: 'Infers the output schema (JSON Schema or XSD) from UTLX transformation code. Optionally accepts input schema for better inference.',
  inputSchema: {
    type: 'object',
    properties: {
      utlx: {
        type: 'string',
        description: 'The UTLX transformation code to analyze',
      },
      inputSchema: {
        type: 'string',
        description: 'Optional input schema (JSON Schema or XSD) for better inference',
      },
      format: {
        type: 'string',
        description: 'Output schema format: json-schema or xsd (default: json-schema)',
        enum: ['json-schema', 'xsd'],
        default: 'json-schema',
      },
    },
    required: ['utlx'],
  },
};

const InferOutputSchemaArgsSchema = z.object({
  utlx: z.string(),
  inputSchema: z.string().optional(),
  format: z.enum(['json-schema', 'xsd']).optional().default('json-schema'),
});

export async function handleInferOutputSchema(
  args: Record<string, unknown>,
  daemonClient: DaemonClient,
  logger: Logger
): Promise<ToolInvocationResponse> {
  try {
    // Validate arguments
    const { utlx, inputSchema, format } = InferOutputSchemaArgsSchema.parse(args);

    logger.info('Inferring output schema', {
      utlxLength: utlx.length,
      hasInputSchema: !!inputSchema,
      format,
    });

    // Call daemon schema inference endpoint
    const response = await daemonClient.inferSchema({
      utlx,
      inputSchema,
      format,
    });

    if (response.success && response.schema) {
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify({
              success: true,
              schema: response.schema,
              schemaFormat: response.schemaFormat,
              confidence: response.confidence || 1.0,
              message: 'Output schema inferred successfully',
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
              error: response.error || 'Schema inference failed',
            }, null, 2),
          },
        ],
        isError: true,
      };
    }
  } catch (error) {
    logger.error('Error inferring output schema', { error });
    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify({
            error: 'Schema inference failed',
            message: error instanceof Error ? error.message : 'Unknown error',
          }, null, 2),
        },
      ],
      isError: true,
    };
  }
}
