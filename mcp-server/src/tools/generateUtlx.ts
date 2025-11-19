/**
 * Tool: generate_utlx_from_prompt
 *
 * Generates UTLX transformation code from natural language description
 * using configured LLM provider (Claude, Ollama, etc.)
 */

import { Tool, ToolInvocationResponse } from '../types/mcp';
import { DaemonClient } from '../client/DaemonClient';
import { Logger } from 'winston';
import { z } from 'zod';
import { LLMGateway } from '../llm/llm-gateway';
import {
  buildUTLXGenerationSystemPrompt,
  buildUTLXGenerationUserPrompt,
  UTLXGenerationContext,
} from '../llm/prompts/utlx-generation-prompt';

export const generateUtlxTool: Tool = {
  name: 'generate_utlx_from_prompt',
  description: 'Generates UTLX transformation code from natural language description. Supports multiple inputs with original data and UDM structure.',
  inputSchema: {
    type: 'object',
    properties: {
      prompt: {
        type: 'string',
        description: 'Natural language description of the transformation to generate',
      },
      inputs: {
        type: 'array',
        description: 'Array of input definitions with name, format, and optional sample data',
        items: {
          type: 'object',
          properties: {
            name: {
              type: 'string',
              description: 'Input name (e.g., "customers-xml", "orders-csv")',
            },
            format: {
              type: 'string',
              description: 'Input format (xml, json, csv, yaml, xsd, jsch, avro, proto)',
            },
            originalData: {
              type: 'string',
              description: 'Optional original input data sample',
            },
            udm: {
              type: 'string',
              description: 'Optional UDM structure after parsing',
            },
          },
          required: ['name', 'format'],
        },
      },
      outputFormat: {
        type: 'string',
        description: 'Target output format (xml, json, csv, yaml, etc.)',
      },
    },
    required: ['prompt', 'inputs', 'outputFormat'],
  },
};

const GenerateUtlxArgsSchema = z.object({
  prompt: z.string(),
  inputs: z.array(z.object({
    name: z.string(),
    format: z.string(),
    originalData: z.string().optional(),
    udm: z.string().optional(),
  })),
  outputFormat: z.string(),
});

export async function handleGenerateUtlx(
  args: Record<string, unknown>,
  daemonClient: DaemonClient,
  logger: Logger,
  llmGateway?: LLMGateway
): Promise<ToolInvocationResponse> {
  try {
    // Validate arguments
    const { prompt, inputs, outputFormat } = GenerateUtlxArgsSchema.parse(args);

    logger.info('Generating UTLX code', {
      prompt: prompt.substring(0, 100),
      inputCount: inputs.length,
      outputFormat,
    });

    // Check if LLM gateway is configured
    if (!llmGateway) {
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify({
              success: false,
              error: 'LLM gateway is not configured. Please configure an LLM provider in the server configuration.',
            }, null, 2),
          },
        ],
        isError: true,
      };
    }

    // Check if LLM is available
    const isAvailable = await llmGateway.isAvailable();
    if (!isAvailable) {
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify({
              success: false,
              error: `LLM provider (${llmGateway.getProviderName()}) is not available. Check configuration and API keys.`,
            }, null, 2),
          },
        ],
        isError: true,
      };
    }

    // Build context for prompt
    const context: UTLXGenerationContext = {
      inputs: inputs.map(input => ({
        name: input.name,
        format: input.format,
        originalData: input.originalData,
        udm: input.udm,
      })),
      outputFormat,
      userPrompt: prompt,
    };

    // Build prompts
    const systemPrompt = buildUTLXGenerationSystemPrompt();
    const userPrompt = buildUTLXGenerationUserPrompt(context);

    logger.info('Calling LLM for UTLX generation');

    // Call LLM
    const response = await llmGateway.generateCompletion({
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: userPrompt },
      ],
      maxTokens: 4096,
      temperature: 0.3, // Lower temperature for more deterministic code generation
    });

    const generatedCode = response.content.trim();

    logger.info('UTLX code generated', {
      length: generatedCode.length,
      inputTokens: response.usage?.inputTokens,
      outputTokens: response.usage?.outputTokens,
    });

    // Validate generated code
    logger.info('Validating generated UTLX code');
    const validationResult = await daemonClient.validate({
      utlx: generatedCode,
      strict: false,
    });

    if (!validationResult.valid) {
      // Log validation errors but still return the code
      const errors = validationResult.diagnostics
        .filter(d => d.severity === 'error')
        .map(d => d.message)
        .join(', ');

      logger.warn('Generated code has validation errors', { errors });

      // Try to regenerate with error feedback
      logger.info('Attempting to regenerate with error feedback');

      const retryPrompt = `${userPrompt}\n\nPREVIOUS ATTEMPT HAD ERRORS:\n${errors}\n\nPlease fix these errors and generate correct UTLX code.`;

      const retryResponse = await llmGateway.generateCompletion({
        messages: [
          { role: 'system', content: systemPrompt },
          { role: 'user', content: retryPrompt },
        ],
        maxTokens: 4096,
        temperature: 0.3,
      });

      const retriedCode = retryResponse.content.trim();

      // Validate again
      const retryValidation = await daemonClient.validate({
        utlx: retriedCode,
        strict: false,
      });

      if (retryValidation.valid) {
        logger.info('Regenerated code is valid');
        return {
          content: [
            {
              type: 'text',
              text: JSON.stringify({
                success: true,
                utlx: retriedCode,
                validation: {
                  valid: true,
                  message: 'Code generated and validated successfully (after retry)',
                },
                usage: {
                  inputTokens: (response.usage?.inputTokens || 0) + (retryResponse.usage?.inputTokens || 0),
                  outputTokens: (response.usage?.outputTokens || 0) + (retryResponse.usage?.outputTokens || 0),
                },
              }, null, 2),
            },
          ],
        };
      } else {
        // Return code with validation warnings
        return {
          content: [
            {
              type: 'text',
              text: JSON.stringify({
                success: true,
                utlx: retriedCode,
                validation: {
                  valid: false,
                  diagnostics: retryValidation.diagnostics,
                  warning: 'Generated code may have validation issues',
                },
              }, null, 2),
            },
          ],
        };
      }
    }

    // Code is valid
    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify({
            success: true,
            utlx: generatedCode,
            validation: {
              valid: true,
              message: 'Code generated and validated successfully',
            },
            usage: {
              inputTokens: response.usage?.inputTokens,
              outputTokens: response.usage?.outputTokens,
            },
          }, null, 2),
        },
      ],
    };

  } catch (error) {
    logger.error('Error generating UTLX code', { error });

    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify({
            success: false,
            error: error instanceof Error ? error.message : String(error),
          }, null, 2),
        },
      ],
      isError: true,
    };
  }
}
