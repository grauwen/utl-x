/**
 * LLM Configuration Loader
 *
 * Loads LLM provider configuration from environment variables or config file
 */

import * as fs from 'fs';
import * as path from 'path';
import { LLMProviderConfig } from '../llm/types';
import { Logger } from 'winston';

/**
 * Load LLM configuration from environment variables and config file
 */
export function loadLLMConfig(logger: Logger): LLMProviderConfig | null {
  // Try environment variables first
  const providerType = process.env.UTLX_LLM_PROVIDER;

  if (providerType === 'claude') {
    const apiKey = process.env.ANTHROPIC_API_KEY || process.env.CLAUDE_API_KEY;
    if (!apiKey) {
      logger.warn('Claude provider selected but no API key found in environment (ANTHROPIC_API_KEY or CLAUDE_API_KEY)');
      return null;
    }

    const model = process.env.UTLX_LLM_MODEL || 'claude-3-5-sonnet-20241022';
    const maxTokens = process.env.UTLX_LLM_MAX_TOKENS
      ? parseInt(process.env.UTLX_LLM_MAX_TOKENS, 10)
      : 4096;

    logger.info('Loaded Claude configuration from environment', { model, maxTokens });

    return {
      type: 'claude',
      claude: {
        apiKey,
        model,
        maxTokens,
      },
    };
  }

  if (providerType === 'ollama') {
    const endpoint = process.env.OLLAMA_ENDPOINT || 'http://localhost:11434';
    const model = process.env.OLLAMA_MODEL || 'codellama';
    const maxTokens = process.env.UTLX_LLM_MAX_TOKENS
      ? parseInt(process.env.UTLX_LLM_MAX_TOKENS, 10)
      : 4096;

    logger.info('Loaded Ollama configuration from environment', { endpoint, model, maxTokens });

    return {
      type: 'ollama',
      ollama: {
        endpoint,
        model,
        maxTokens,
      },
    };
  }

  // Try loading from config file
  const configPath = process.env.UTLX_LLM_CONFIG || path.join(process.cwd(), 'llm-config.json');

  if (fs.existsSync(configPath)) {
    try {
      const configData = fs.readFileSync(configPath, 'utf-8');
      const config = JSON.parse(configData) as LLMProviderConfig;

      logger.info('Loaded LLM configuration from file', { configPath, type: config.type });

      return config;
    } catch (error) {
      logger.error('Error loading LLM config file', { configPath, error });
      return null;
    }
  }

  // No configuration found - default to Ollama with codellama:70b
  logger.info('No LLM provider configured. Using default: Ollama with codellama:70b');
  logger.info('To override, set UTLX_LLM_PROVIDER=claude or ollama with appropriate credentials.');

  return {
    type: 'ollama',
    ollama: {
      endpoint: 'http://localhost:11434',
      model: 'codellama:70b',
      maxTokens: 4096,
    },
  };
}
