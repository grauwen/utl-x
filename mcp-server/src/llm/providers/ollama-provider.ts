/**
 * Ollama LLM Provider for local models
 */

import { LLMProvider, LLMCompletionRequest, LLMCompletionResponse } from '../types.js';

export interface OllamaConfig {
  endpoint: string;
  model: string;
  maxTokens?: number;
  numCtx?: number;  // Context window size
}

interface OllamaMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

interface OllamaRequest {
  model: string;
  messages: OllamaMessage[];
  stream: boolean;
  options?: {
    temperature?: number;
    num_predict?: number;
    num_ctx?: number;  // Context window size
    stop?: string[];
  };
}

interface OllamaResponse {
  model: string;
  created_at: string;
  message: {
    role: string;
    content: string;
  };
  done: boolean;
  total_duration?: number;
  load_duration?: number;
  prompt_eval_count?: number;
  eval_count?: number;
}

export class OllamaProvider implements LLMProvider {
  private endpoint: string;
  private model: string;
  private defaultMaxTokens: number;
  private numCtx?: number;

  constructor(config: OllamaConfig) {
    this.endpoint = config.endpoint || 'http://localhost:11434';
    this.model = config.model || 'codellama';
    this.defaultMaxTokens = config.maxTokens || 4096;
    this.numCtx = config.numCtx;  // Optional context window size
  }

  async generateCompletion(request: LLMCompletionRequest): Promise<LLMCompletionResponse> {
    try {
      const ollamaRequest: OllamaRequest = {
        model: this.model,
        messages: request.messages.map(m => ({
          role: m.role,
          content: m.content,
        })),
        stream: false,
        options: {
          temperature: request.temperature !== undefined ? request.temperature : 0.7,
          num_predict: request.maxTokens || this.defaultMaxTokens,
          num_ctx: this.numCtx,  // Set context window if specified
          stop: request.stopSequences,
        },
      };

      const response = await fetch(`${this.endpoint}/api/chat`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(ollamaRequest),
      });

      if (!response.ok) {
        throw new Error(`Ollama API error: ${response.status} ${response.statusText}`);
      }

      const data: OllamaResponse = await response.json();

      return {
        content: data.message.content,
        stopReason: data.done ? 'end_turn' : 'max_tokens',
        usage: {
          inputTokens: data.prompt_eval_count || 0,
          outputTokens: data.eval_count || 0,
        },
      };
    } catch (error) {
      throw new Error(`Ollama API error: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  getName(): string {
    return 'Ollama';
  }

  async isAvailable(): Promise<boolean> {
    try {
      const response = await fetch(`${this.endpoint}/api/tags`);
      return response.ok;
    } catch {
      return false;
    }
  }
}
