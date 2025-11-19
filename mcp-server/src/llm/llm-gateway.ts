/**
 * LLM Gateway - Manages multiple LLM providers
 */

import { LLMProvider, LLMProviderConfig, LLMCompletionRequest, LLMCompletionResponse } from './types.js';
import { ClaudeProvider } from './providers/claude-provider.js';
import { OllamaProvider } from './providers/ollama-provider.js';

export class LLMGateway {
  private provider: LLMProvider | null = null;
  private config: LLMProviderConfig;

  constructor(config: LLMProviderConfig) {
    this.config = config;
    this.initializeProvider();
  }

  private initializeProvider(): void {
    switch (this.config.type) {
      case 'claude':
        if (!this.config.claude) {
          throw new Error('Claude configuration is required when type is "claude"');
        }
        this.provider = new ClaudeProvider(this.config.claude);
        break;

      case 'ollama':
        if (!this.config.ollama) {
          throw new Error('Ollama configuration is required when type is "ollama"');
        }
        this.provider = new OllamaProvider(this.config.ollama);
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
   * Reload configuration and reinitialize provider
   */
  async reload(config: LLMProviderConfig): Promise<void> {
    this.config = config;
    this.initializeProvider();
  }
}
