/**
 * LLM Gateway - Manages multiple LLM providers
 */

import {
  LLMProvider,
  LLMProviderConfig,
  LLMProviderDeps,
  LLMCompletionRequest,
  LLMCompletionResponse,
} from './types.js';
import { OllamaProvider } from './providers/ollama-provider.js';
import { ClaudeCodeProvider } from './providers/claude-code-provider.js';

export class LLMGateway {
  private provider: LLMProvider | null = null;
  private config: LLMProviderConfig;
  private deps: LLMProviderDeps;

  constructor(config: LLMProviderConfig, deps: LLMProviderDeps = {}) {
    this.config = config;
    this.deps = deps;
    this.initializeProvider();
  }

  private initializeProvider(): void {
    switch (this.config.type) {
      case 'ollama':
        if (!this.config.ollama) {
          throw new Error('Ollama configuration is required when type is "ollama"');
        }
        this.provider = new OllamaProvider(this.config.ollama);
        break;

      case 'claude-code':
        // claudeCode config is optional — the provider has sensible defaults.
        this.provider = new ClaudeCodeProvider(this.config.claudeCode ?? {}, this.deps);
        break;

      default:
        throw new Error(`Unknown LLM provider type: ${this.config.type}`);
    }
  }

  /**
   * Generate a completion using the configured provider
   */
  async generateCompletion(request: LLMCompletionRequest): Promise<LLMCompletionResponse> {
    if (!this.provider) {
      throw new Error('No LLM provider configured');
    }

    try {
      return await this.provider.generateCompletion(request);
    } catch (error) {
      console.error(`[LLMGateway] Error from ${this.provider.getName()}:`, error);
      throw error;
    }
  }

  /**
   * Check if the provider is available
   */
  async isAvailable(): Promise<boolean> {
    if (!this.provider) {
      return false;
    }
    return await this.provider.isAvailable();
  }

  /**
   * Get the current provider name
   */
  getProviderName(): string {
    return this.provider ? this.provider.getName() : 'None';
  }

  /**
   * Get the model name being used
   */
  getModelName(): string {
    if (!this.provider) {
      return 'None';
    }

    if (this.config.type === 'ollama' && this.config.ollama) {
      return this.config.ollama.model;
    }

    if (this.config.type === 'claude-code') {
      return this.config.claudeCode?.model ?? 'default (Claude Code session)';
    }

    return 'Unknown';
  }

  /**
   * Reload configuration and reinitialize provider
   */
  async reload(config: LLMProviderConfig, deps?: LLMProviderDeps): Promise<void> {
    this.config = config;
    if (deps) {
      this.deps = deps;
    }
    this.initializeProvider();
  }
}
