/**
 * Common types for LLM providers
 */

export interface LLMMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

export interface LLMCompletionRequest {
  messages: LLMMessage[];
  maxTokens?: number;
  temperature?: number;
  stopSequences?: string[];
}

export interface LLMCompletionResponse {
  content: string;
  stopReason?: 'end_turn' | 'max_tokens' | 'stop_sequence';
  usage?: {
    inputTokens: number;
    outputTokens: number;
  };
}

export interface LLMProviderConfig {
  type: 'claude' | 'ollama';
  claude?: {
    apiKey: string;
    model: string;
    maxTokens?: number;
  };
  ollama?: {
    endpoint: string;
    model: string;
    maxTokens?: number;
  };
}

/**
 * Abstract LLM Provider interface
 */
export interface LLMProvider {
  /**
   * Generate a completion from the LLM
   */
  generateCompletion(request: LLMCompletionRequest): Promise<LLMCompletionResponse>;

  /**
   * Get the provider name
   */
  getName(): string;

  /**
   * Check if the provider is available/configured
   */
  isAvailable(): Promise<boolean>;
}
