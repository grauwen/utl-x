/**
 * Tool 1: get_input_schema
 *
 * Parses input schemas (XSD, JSON Schema, CSV) into structured type definitions
 */

import { Tool, ToolInvocationResponse } from '../types/mcp';
import { DaemonClient } from '../client/DaemonClient';
import { Logger } from 'winston';
import { z } from 'zod';

export const getInputSchemaTool: Tool = {
  name: 'get_input_schema',
  description: 'Parses input schemas (XSD, JSON Schema, CSV headers) into structured type definitions for UTLX transformations.',
  inputSchema: {
    type: 'object',
    properties: {
      schema: {
        type: 'string',
        description: 'The schema content to parse (XSD, JSON Schema, or CSV header)',
      },
      format: {
        type: 'string',
        description: 'Schema format: json-schema, xsd, or csv (default: json-schema)',
        enum: ['json-schema', 'xsd', 'csv'],
        default: 'json-schema',
      },
    },
    required: ['schema'],
  },
};

const GetInputSchemaArgsSchema = z.object({
  schema: z.string(),
  format: z.enum(['json-schema', 'xsd', 'csv']).optional().default('json-schema'),
});

export async function handleGetInputSchema(
  args: Record<string, unknown>,
  daemonClient: DaemonClient,
  logger: Logger
): Promise<ToolInvocationResponse> {
  try {
    // Validate arguments
    const { schema, format } = GetInputSchemaArgsSchema.parse(args);

    logger.info('Parsing input schema', {
      schemaLength: schema.length,
      format,
    });

    // Call daemon schema parsing endpoint
    const response = await daemonClient.parseSchema({
      schema,
      format,
    });

    if (response.success && response.typeDef) {
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify({
              success: true,
              typeDef: response.typeDef,
              schemaFormat: response.schemaFormat,
              rootElement: response.rootElement,
              message: 'Input schema parsed successfully',
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
              error: response.error || 'Schema parsing failed',
            }, null, 2),
          },
        ],
        isError: true,
      };
    }
  } catch (error) {
    logger.error('Error parsing input schema', { error });
    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify({
            error: 'Schema parsing failed',
            message: error instanceof Error ? error.message : 'Unknown error',
          }, null, 2),
        },
      ],
      isError: true,
    };
  }
}
