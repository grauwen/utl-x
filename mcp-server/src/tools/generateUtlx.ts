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
import { buildUsdlContext } from '../llm/usdl-context';

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
          // Skip single standalone words (likely fragments), but NEVER a line
          // that is/starts with a bracket — those are code.
          (line.length <= 3 && !/^[\$\{\[\}\]\)]/.test(line))) {
        startIndex = i + 1;
        continue;
      }

      // Found actual transformation code
      break;
    }

    code = lines.slice(startIndex).join('\n').trim();
  }

  // Step 4: Remove trailing prose the model may append AFTER the code.
  // CRITICAL: never strip lines that are/contain code punctuation. Closing
  // brackets ( } ] ) ) commonly sit alone on short trailing lines — stripping
  // them silently breaks the program (missing closing brackets).
  const codeLines = code.split('\n');
  let endIndex = codeLines.length;

  for (let i = codeLines.length - 1; i >= 0; i--) {
    const line = codeLines[i].trim();

    // Skip blank trailing lines.
    if (line === '') {
      continue;
    }

    // Any bracket or code punctuation → this is code; stop trimming here.
    if (/[{}\[\]();]/.test(line) || line.startsWith('$') || line.includes('=>')) {
      break;
    }

    // Explicit prose markers → drop this trailing line and keep scanning up.
    const lower = line.toLowerCase();
    if (lower.startsWith('this is') ||
        lower.startsWith('this will') ||
        lower.startsWith('this transformation') ||
        lower.startsWith('note:') ||
        lower.startsWith('explanation') ||
        lower.includes('correct utlx')) {
      endIndex = i;
      continue;
    }

    // Anything else (a bare value or identifier) is assumed to be code: stop.
    break;
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

    // If any format (input or output) is a Tier 2 schema format, fetch USDL
    // directive guidance so the model knows the %-directive vocabulary. Tier 1
    // data formats skip this (buildUsdlContext returns undefined).
    let usdlContext: string | undefined;
    try {
      usdlContext = await buildUsdlContext(
        daemonClient,
        logger,
        [outputFormat, ...inputs.map(i => i.format)]
      );
      if (usdlContext) {
        logger.info('Tier 2 schema format detected — added USDL directive context to prompt');
      }
    } catch (error) {
      logger.warn('Could not build USDL context, proceeding without it', { error });
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
      usdlContext,
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

    // Pick a sample input to RUN the transformation against. Validation only
    // proves the program compiles; execution catches runtime errors (e.g.
    // accessing an object property on an array). The execute API takes a single
    // input, so use the first one that carries sample data.
    const sampleInput = inputs.find(i => i.originalData && i.originalData.length > 0);

    // Iterative refinement: Try up to 3 times with validation + execution feedback
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

      // Call LLM. Pass the fixed header + sample input so an agentic provider
      // (Claude Code) can reconstruct the full program and self-verify by
      // validating AND running it — never modifying the header.
      const response = await llmGateway.generateCompletion({
        messages: conversationHistory,
        maxTokens: 4096,
        temperature: 0.3, // Lower temperature for more deterministic code generation
        verification: {
          header: originalHeader,
          input: sampleInput?.originalData,
          inputFormat: sampleInput?.format,
          outputFormat,
        },
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
          // Validation passed (it compiles). Now RUN it against the sample
          // input — validation cannot catch runtime errors like accessing a
          // property on an array. The header is left untouched; we execute the
          // full reconstructed program.
          logger.info(`Attempt ${attempt}: Validation PASSED`);

          let runtimeError: string | undefined;
          let executedOutput: string | undefined;

          if (sampleInput?.originalData) {
            if (onProgress) {
              onProgress(0, `Running transformation against sample input (attempt ${attempt})...`);
            }
            try {
              const execResult = await daemonClient.execute({
                utlx: fullProgram,
                input: sampleInput.originalData,
                inputFormat: sampleInput.format,
                outputFormat,
              });
              if (execResult.success) {
                executedOutput = execResult.output;
                logger.info(`Attempt ${attempt}: Execution PASSED`);
              } else {
                runtimeError = execResult.error || 'Unknown runtime error';
                logger.warn(`Attempt ${attempt}: Execution FAILED`, { error: runtimeError });
              }
            } catch (execCallError) {
              // Execute endpoint unreachable — don't block, treat like
              // validation-API-unavailable (ship validated code).
              logger.error(`Attempt ${attempt}: Execute call failed`, { error: execCallError });
            }
          }

          if (!runtimeError) {
            // Validated, and executed cleanly (or no sample / execute skipped).
            if (onProgress) {
              onProgress(0, sampleInput?.originalData
                ? `Code validated and executed successfully on attempt ${attempt}!`
                : `Code validated successfully on attempt ${attempt}!`);
            }
            return {
              content: [
                {
                  type: 'text',
                  text: JSON.stringify({
                    success: true,
                    utlx: generatedCode,
                    validation: {
                      valid: true,
                      executed: !!executedOutput,
                      message: executedOutput !== undefined
                        ? `Validated and executed on attempt ${attempt}/${MAX_ATTEMPTS}`
                        : `Validation passed on attempt ${attempt}/${MAX_ATTEMPTS}`,
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

          // Runtime error: feed it back and refine the BODY only (header fixed).
          if (onProgress) {
            onProgress(0, `Execution failed: ${runtimeError}. Refining...`);
          }
          if (attempt < MAX_ATTEMPTS) {
            const feedbackPrompt = `The transformation compiles but FAILS AT RUNTIME on the sample input:\n\n${runtimeError}\n\nThis usually means an expression accesses data the wrong way (e.g. treating an array as an object, or an object as an array). Fix the logic so it runs successfully on the sample input. Generate ONLY the corrected transformation body (no header, no ---, no explanations).`;
            conversationHistory.push({ role: 'user', content: feedbackPrompt });
            logger.info(`Attempt ${attempt}: Added execution feedback to conversation`);
          } else {
            // Exhausted attempts with a runtime failure — return the code but
            // report the execution error honestly.
            logger.warn(`All ${MAX_ATTEMPTS} attempts completed, execution still failing`);
            return {
              content: [
                {
                  type: 'text',
                  text: JSON.stringify({
                    success: true,
                    utlx: generatedCode,
                    validation: {
                      valid: true,
                      executed: false,
                      message: `Code validated but FAILED AT RUNTIME after ${MAX_ATTEMPTS} attempts`,
                      attempts: MAX_ATTEMPTS,
                      runtimeError,
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
