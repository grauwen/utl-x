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
      originalHeader: {
        type: 'string',
        description: 'Original UTLX header from editor (required for validation)',
      },
    },
    required: ['prompt', 'inputs', 'outputFormat', 'originalHeader'],
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
  originalHeader: z.string(),
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

      // Skip all header patterns and stray words
      if (line.startsWith('%utlx') ||
          line.startsWith('input:') ||
          line.startsWith('input ') ||
          line.startsWith('- name:') ||
          line.startsWith('type:') ||
          line.startsWith('output:') ||
          line.startsWith('output ') ||
          line === '---' ||
          line === '' ||
          // Skip single standalone words (likely fragments)
          (line.length <= 3 && !/^[\$\{\[]/.test(line))) {
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

    // Check if this looks like an explanation or stray word
    if (line.toLowerCase().startsWith('this is') ||
        line.toLowerCase().startsWith('this will') ||
        line.toLowerCase().startsWith('the ') ||
        line.toLowerCase().startsWith('note:') ||
        line.toLowerCase().includes('correct utlx') ||
        /^[\d.)\-*]/.test(line) ||
        // Skip single standalone words (likely fragments from explanations)
        (line.length <= 3 && !/^[\$\{\[]/.test(line))) {
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
  llmGateway?: LLMGateway,
  onProgress?: (progress: number, message?: string) => void
): Promise<ToolInvocationResponse> {
  try {
    // Validate arguments
    const { prompt, inputs, outputFormat, originalHeader } = GenerateUtlxArgsSchema.parse(args);

    logger.info('Generating UTLX code', {
      prompt: prompt.substring(0, 100),
      inputCount: inputs.length,
      outputFormat,
      hasOriginalHeader: !!originalHeader,
    });

    // Report progress: Starting
    if (onProgress) {
      onProgress(0, 'Initializing AI assistant...');
    }

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

    // Report progress: Fetching context
    if (onProgress) {
      onProgress(0, 'Collecting UTLX function and operator context...');
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
      systemPromptLength: systemPrompt.length,
      userPromptLength: userPrompt.length,
      hasFunctions: !!functionsContext,
      hasOperators: !!operatorsContext,
    });

    // Log the full prompts for debugging (can be disabled in production)
    logger.debug('=== SYSTEM PROMPT ===');
    logger.debug(systemPrompt);
    logger.debug('=== USER PROMPT ===');
    logger.debug(userPrompt);
    logger.debug('=== END PROMPTS ===');

    // Iterative refinement: Try up to 3 times with validation feedback
    const MAX_ATTEMPTS = 3;
    let generatedCode = '';
    let totalInputTokens = 0;
    let totalOutputTokens = 0;
    let validationResult: any = null;

    const conversationHistory: Array<{ role: 'system' | 'user' | 'assistant'; content: string }> = [
      { role: 'system', content: systemPrompt },
      { role: 'user', content: userPrompt },
    ];

    for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      // Report progress
      if (onProgress) {
        if (attempt === 1) {
          onProgress(0, `Generating transformation code (attempt ${attempt}/${MAX_ATTEMPTS})...`);
        } else {
          onProgress(0, `Refining code based on validation errors (attempt ${attempt}/${MAX_ATTEMPTS})...`);
        }
      }

      logger.info(`Generation attempt ${attempt}/${MAX_ATTEMPTS}`);

      // Call LLM
      const response = await llmGateway.generateCompletion({
        messages: conversationHistory,
        maxTokens: 4096,
        temperature: 0.3, // Lower temperature for more deterministic code generation
      });

      totalInputTokens += response.usage?.inputTokens || 0;
      totalOutputTokens += response.usage?.outputTokens || 0;

      // Add assistant response to conversation
      conversationHistory.push({ role: 'assistant', content: response.content });

      // Extract clean code
      const rawResponse = response.content.trim();
      generatedCode = extractCleanCode(rawResponse);

      logger.info(`Attempt ${attempt}: Generated code`, { length: generatedCode.length });
      logger.debug(`Attempt ${attempt}: Raw LLM response:\n${rawResponse}`);
      logger.debug(`Attempt ${attempt}: Extracted clean code:\n${generatedCode}`);

      // Report progress: Validating
      if (onProgress) {
        onProgress(0, 'Validating generated code...');
      }

      // Reconstruct full UTLX program for validation using original header
      // Note: header already includes trailing newline from frontend
      const header = originalHeader.endsWith('\n') ? originalHeader.slice(0, -1) : originalHeader;
      logger.info(`Attempt ${attempt}: Using original header from editor`);

      const fullProgram = `${header}\n---\n${generatedCode}`;

      logger.info(`Attempt ${attempt}: Validating full program`, {
        programLength: fullProgram.length
      });

      // Log the actual program being validated for debugging
      logger.debug(`Attempt ${attempt}: Full program to validate:\n${fullProgram}`);

      // Validate the code using daemon
      try {
        validationResult = await daemonClient.validate({ utlx: fullProgram });

        // Check if validation passed (valid=true and no error-level diagnostics)
        const errorDiagnostics = (validationResult.diagnostics || []).filter((d: any) => d.severity === 'error');

        if (validationResult.valid && errorDiagnostics.length === 0) {
          // Validation passed!
          logger.info(`Attempt ${attempt}: Validation PASSED`);

          if (onProgress) {
            onProgress(0, `Code validated successfully on attempt ${attempt}!`);
          }

          // Success - return the body
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  success: true,
                  utlx: generatedCode,
                  validation: {
                    valid: true,
                    message: `Validation passed on attempt ${attempt}/${MAX_ATTEMPTS}`,
                    attempts: attempt,
                  },
                  usage: {
                    inputTokens: totalInputTokens,
                    outputTokens: totalOutputTokens,
                  },
                }, null, 2),
              },
            ],
          };
        } else {
          // Validation failed - prepare feedback for next attempt
          const errors = errorDiagnostics;
          logger.warn(`Attempt ${attempt}: Validation FAILED with ${errors.length} error(s)`, { errors });

          if (onProgress) {
            onProgress(0, `Validation failed: ${errors.length} error(s). Refining...`);
          }

          if (attempt < MAX_ATTEMPTS) {
            // Prepare feedback message for LLM
            const errorMessages = errors.map((err: any, idx: number) =>
              `${idx + 1}. Line ${err.line || '?'}: ${err.message}`
            ).join('\n');

            const feedbackPrompt = `The generated code has validation errors:\n\n${errorMessages}\n\nPlease fix these errors. Generate ONLY the corrected transformation body (no header, no ---, no explanations).`;

            // Add feedback to conversation
            conversationHistory.push({ role: 'user', content: feedbackPrompt });

            logger.info(`Attempt ${attempt}: Added validation feedback to conversation`);
          }
        }
      } catch (validationError) {
        logger.error(`Attempt ${attempt}: Validation call failed`, { error: validationError });

        // If validation API fails, proceed with the generated code
        if (attempt === MAX_ATTEMPTS) {
          logger.warn('Validation API failed, returning code without validation');
          return {
            content: [
              {
                type: 'text',
                text: JSON.stringify({
                  success: true,
                  utlx: generatedCode,
                  validation: {
                    valid: false,
                    message: 'Validation API unavailable - code not validated',
                    attempts: attempt,
                  },
                  usage: {
                    inputTokens: totalInputTokens,
                    outputTokens: totalOutputTokens,
                  },
                }, null, 2),
              },
            ],
          };
        }
      }
    }

    // All attempts exhausted - return last generated code with validation errors
    logger.warn(`All ${MAX_ATTEMPTS} attempts completed, validation still failing`);

    if (onProgress) {
      onProgress(0, `Generation complete after ${MAX_ATTEMPTS} attempts (validation issues remain)`);
    }

    const finalErrors = validationResult?.diagnostics?.filter((d: any) => d.severity === 'error') || [];

    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify({
            success: true,
            utlx: generatedCode,
            validation: {
              valid: false,
              message: `Code generated but validation failed after ${MAX_ATTEMPTS} attempts`,
              attempts: MAX_ATTEMPTS,
              errors: finalErrors,
            },
            usage: {
              inputTokens: totalInputTokens,
              outputTokens: totalOutputTokens,
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
