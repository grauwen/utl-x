/**
 * Anthropic Claude LLM Provider
 */

import Anthropic from '@anthropic-ai/sdk';
import { LLMProvider, LLMCompletionRequest, LLMCompletionResponse } from '../types.js';

export interface ClaudeConfig {
  apiKey: string;
  model: string;
  maxTokens?: number;
}

export class ClaudeProvider implements LLMProvider {
  private client: Anthropic;
  private model: string;
  private defaultMaxTokens: number;

  constructor(config: ClaudeConfig) {
    this.client = new Anthropic({
      apiKey: config.apiKey,
    });
    this.model = config.model || 'claude-3-5-sonnet-20241022';
    this.defaultMaxTokens = config.maxTokens || 4096;
  }

  async generateCompletion(request: LLMCompletionRequest): Promise<LLMCompletionResponse> {
    try {
      // Separate system messages from conversation messages
      const systemMessages = request.messages.filter(m => m.role === 'system');
      const conversationMessages = request.messages.filter(m => m.role !== 'system');

      // Combine system messages into single system prompt
      const system = systemMessages.map(m => m.content).join('\n\n');

      // Convert messages to Anthropic format
      const messages: Anthropic.MessageParam[] = conversationMessages.map(m => ({
        role: m.role as 'user' | 'assistant',
        content: m.content,
      }));

      const response = await this.client.messages.create({
        model: this.model,
        max_tokens: request.maxTokens || this.defaultMaxTokens,
        temperature: request.temperature !== undefined ? request.temperature : 0.7,
        system: system || undefined,
        messages,
        stop_sequences: request.stopSequences,
      });

      // Extract text content
      const content = response.content
        .filter(block => block.type === 'text')
        .map(block => (block as Anthropic.TextBlock).text)
        .join('');

      return {
        content,
        stopReason: response.stop_reason as 'end_turn' | 'max_tokens' | 'stop_sequence',
        usage: {
          inputTokens: response.usage.input_tokens,
          outputTokens: response.usage.output_tokens,
        },
      };
    } catch (error) {
      throw new Error(`Claude API error: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  getName(): string {
    return 'Claude';
  }

  async isAvailable(): Promise<boolean> {
    try {
      // Test with minimal request
      await this.client.messages.create({
        model: this.model,
        max_tokens: 10,
        messages: [{ role: 'user', content: 'test' }],
      });
      return true;
    } catch {
      return false;
    }
  }
}
