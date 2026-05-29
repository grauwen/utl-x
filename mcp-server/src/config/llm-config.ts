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

  if (providerType === 'claude-code') {
    const model = process.env.UTLX_LLM_MODEL; // optional; session default if unset
    const maxTokens = process.env.UTLX_LLM_MAX_TOKENS
      ? parseInt(process.env.UTLX_LLM_MAX_TOKENS, 10)
      : 4096;
    // Self-correction (validate-and-fix loop) is on unless explicitly disabled.
    const selfCorrect = process.env.UTLX_CLAUDE_CODE_SELF_CORRECT !== 'false';
    const maxTurns = process.env.UTLX_CLAUDE_CODE_MAX_TURNS
      ? parseInt(process.env.UTLX_CLAUDE_CODE_MAX_TURNS, 10)
      : undefined;
    const pathToExecutable = process.env.UTLX_CLAUDE_CODE_PATH || undefined;

    logger.info('Loaded Claude Code configuration from environment', {
      model: model || 'session default',
      selfCorrect,
    });

    return {
      type: 'claude-code',
      claudeCode: {
        ...(model ? { model } : {}),
        maxTokens,
        ...(maxTurns ? { maxTurns } : {}),
        selfCorrect,
        ...(pathToExecutable ? { pathToExecutable } : {}),
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

  // No configuration found - default to Ollama with codellama-34b-16k
  logger.info('No LLM provider configured. Using default: Ollama with codellama-34b-16k (16K context)');
  logger.info('To override, set UTLX_LLM_PROVIDER=claude-code (uses your Claude login) or ollama.');

  return {
    type: 'ollama',
    ollama: {
      endpoint: 'http://localhost:11434',
      model: 'codellama-34b-16k:latest',
      maxTokens: 4096,
      numCtx: 16384,  // 16K context window
    },
  };
}
