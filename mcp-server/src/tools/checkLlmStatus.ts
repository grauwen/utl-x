/**
 * Tool: check_llm_status
 *
 * Checks if the LLM provider is configured and available
 * Returns provider info, availability status, and any configuration issues
 */

import { Tool, ToolInvocationResponse } from '../types/mcp';
import { Logger } from 'winston';
import { LLMGateway } from '../llm/llm-gateway';

export const checkLlmStatusTool: Tool = {
  name: 'check_llm_status',
  description: 'Checks if the LLM provider is configured and available for UTLX code generation',
  inputSchema: {
    type: 'object',
    properties: {},
    required: [],
  },
};

export async function handleCheckLlmStatus(
  _args: Record<string, unknown>,
  _daemonClient: any,  // Not used, but needed for handler signature compatibility
  logger: Logger,
  llmGateway?: LLMGateway
): Promise<ToolInvocationResponse> {
  try {
    logger.info('Checking LLM status');

    // Check if LLM gateway is configured
    if (!llmGateway) {
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify({
              configured: false,
              available: false,
              error: 'LLM gateway is not configured. Please configure an LLM provider (Claude or Ollama).',
              provider: null,
            }, null, 2),
          },
        ],
      };
    }

    const providerName = llmGateway.getProviderName();
    const modelName = llmGateway.getModelName();
    logger.info(`Checking availability of ${providerName} with model ${modelName}`);

    // Check if LLM is available
    const isAvailable = await llmGateway.isAvailable();

    if (!isAvailable) {
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify({
              configured: true,
              available: false,
              provider: providerName,
              model: modelName,
              error: `${providerName} is not available. Please check:
- For Ollama: Is Ollama running on localhost:11434?
- For Ollama: Is the model (${modelName}) downloaded? Run: ollama pull ${modelName}
- For Claude: Is ANTHROPIC_API_KEY set correctly?
- For Claude: Do you have API credits?`,
            }, null, 2),
          },
        ],
      };
    }

    // LLM is available
    logger.info(`${providerName} with model ${modelName} is available and ready`);

    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify({
            configured: true,
            available: true,
            provider: providerName,
            model: modelName,
            message: `${providerName} is available and ready for code generation`,
          }, null, 2),
        },
      ],
    };

  } catch (error) {
    logger.error('Error checking LLM status', { error });

    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify({
            configured: false,
            available: false,
            error: error instanceof Error ? error.message : String(error),
          }, null, 2),
        },
      ],
      isError: true,
    };
  }
}
