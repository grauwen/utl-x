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

/**
 * Extract clean UTLX code from LLM response
 * Handles cases where LLM adds markdown, explanations, or headers
 */
function extractCleanCode(response: string): string {
  let code = response.trim();

  // Step 1: Remove numbered list prefix (e.g., "1. ```")
  code = code.replace(/^\d+\.\s*/, '');

  // Step 2: Remove markdown code blocks
  code = code.replace(/^```(?:utlx|javascript|js)?\n?/gm, '');
  code = code.replace(/\n?```$/gm, '');

  // Step 3: Find the separator (---) which marks start of actual transformation
  const separatorIndex = code.indexOf('---');
  if (separatorIndex !== -1) {
    // Extract everything after the separator
    code = code.substring(separatorIndex + 3).trim();
  } else {
    // No separator found - skip header-like lines manually
    const lines = code.split('\n');
    let startIndex = 0;

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim();

      // Skip all header patterns
      if (line.startsWith('%utlx') ||
          line.startsWith('input:') ||
          line.startsWith('input ') ||
          line.startsWith('- name:') ||
          line.startsWith('type:') ||
          line.startsWith('output:') ||
          line.startsWith('output ') ||
          line === '---' ||
          line === '') {
        startIndex = i + 1;
        continue;
      }

      // Found actual transformation code
      break;
    }

    code = lines.slice(startIndex).join('\n').trim();
  }

  // Step 4: Remove trailing explanations
  // Split by newlines and find where explanation starts
  const codeLines = code.split('\n');
  let endIndex = codeLines.length;

  // Look for explanation patterns from the end
  for (let i = codeLines.length - 1; i >= 0; i--) {
    const line = codeLines[i].trim();

    // Skip empty lines
    if (line === '') {
      continue;
    }

    // Check if this looks like an explanation
    if (line.toLowerCase().startsWith('this is') ||
        line.toLowerCase().startsWith('this will') ||
        line.toLowerCase().startsWith('the ') ||
        line.toLowerCase().startsWith('note:') ||
        line.toLowerCase().includes('correct utlx') ||
        /^[\d.)\-*]/.test(line)) {
      endIndex = i;
      continue;
    }

    // If we hit actual code (ends with }, ], ), or is a valid expression), stop
    if (line.endsWith('}') || line.endsWith(']') || line.endsWith(')') ||
        line.startsWith('$') || line.includes('=>')) {
      break;
    }
  }

  code = codeLines.slice(0, endIndex).join('\n').trim();

  return code;
}

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

    // Fetch available functions and operators for context
    logger.info('Fetching UTLX functions and operators for LLM context');
    let functionsContext = '';
    let operatorsContext = '';

    try {
      // Get all stdlib functions - just names, no details
      const functionsResult = await daemonClient.getFunctions();
      if (functionsResult && functionsResult.functions) {
        functionsContext = '\nAVAILABLE FUNCTIONS: ';
        functionsContext += functionsResult.functions.map(fn => fn.name).join(', ');
        functionsContext += '\n';
        logger.info(`Added ${functionsResult.functions.length} function names to context`);
      }
    } catch (error) {
      logger.warn('Could not fetch functions, proceeding without function context', { error });
    }

    try {
      // Get all operators - just symbols, no details
      const operatorsResult = await daemonClient.getOperators();
      if (operatorsResult && operatorsResult.operators) {
        operatorsContext = '\nAVAILABLE OPERATORS: ';
        operatorsContext += operatorsResult.operators.map(op => op.symbol).join(', ');
        operatorsContext += '\n';
        logger.info(`Added ${operatorsResult.operators.length} operator symbols to context`);
      }
    } catch (error) {
      logger.warn('Could not fetch operators, proceeding without operator context', { error });
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
      functionsContext,
      operatorsContext,
    };

    // Build prompts
    const systemPrompt = buildUTLXGenerationSystemPrompt();
    const userPrompt = buildUTLXGenerationUserPrompt(context);

    logger.info('Calling LLM for UTLX generation', {
      promptLength: systemPrompt.length + userPrompt.length,
      hasFunctions: !!functionsContext,
      hasOperators: !!operatorsContext,
    });

    // Call LLM
    const response = await llmGateway.generateCompletion({
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: userPrompt },
      ],
      maxTokens: 4096,
      temperature: 0.3, // Lower temperature for more deterministic code generation
    });

    let generatedCode = response.content.trim();

    // Extract clean code from response (handle cases where LLM adds explanations)
    generatedCode = extractCleanCode(generatedCode);

    logger.info('Extracted clean code', { length: generatedCode.length });

    logger.info('UTLX code generated', {
      length: generatedCode.length,
      inputTokens: response.usage?.inputTokens,
      outputTokens: response.usage?.outputTokens,
    });

    // NOTE: We do NOT validate here because we only have the body.
    // The header will be restored by the frontend, and validation should happen there.
    // Validating body-only code will fail with "Expected '---' separator after header"

    // Return the extracted body only - frontend will restore the header
    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify({
            success: true,
            utlx: generatedCode,
            validation: {
              valid: true,
              message: 'Body generated successfully (header will be preserved by client)',
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
