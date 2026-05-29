/**
 * Common types for LLM providers
 */

export interface LLMMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

/**
 * Per-request verification context, supplied by tools that want an agentic
 * provider to self-verify its output. The model only ever produces the
 * transformation BODY; `header` is the fixed editor header that must never be
 * modified — it is reconstructed (header + '---' + body) for validate/execute.
 */
export interface LLMVerificationContext {
  header: string;            // fixed UTLX header — never regenerated
  input?: string;            // sample input data, enables execution
  inputFormat?: string;      // e.g. xml, json, csv
  outputFormat?: string;     // target output format
}

export interface LLMCompletionRequest {
  messages: LLMMessage[];
  maxTokens?: number;
  temperature?: number;
  stopSequences?: string[];
  verification?: LLMVerificationContext;
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
  type: 'ollama' | 'claude-code';
  ollama?: {
    endpoint: string;
    model: string;
    maxTokens?: number;
    numCtx?: number;  // Context window size (e.g., 2048, 4096, 8192, 16384)
  };
  claudeCode?: {
    // Optional model override (e.g. 'claude-sonnet-4-6', 'claude-opus-4-8').
    // When omitted, the Claude Code session uses its configured default.
    model?: string;
    maxTokens?: number;
    // Upper bound on agentic turns (tool calls + responses) per completion.
    maxTurns?: number;
    // When true (default), expose a validate_utlx tool so the session can
    // check its own output against the UTLX engine and self-correct.
    selfCorrect?: boolean;
    // Optional path to the Claude Code CLI executable, if not on PATH.
    pathToExecutable?: string;
  };
}

/**
 * Result of validating a UTLX program against the engine.
 * Mirrors the daemon's ValidationResponse without coupling providers to the
 * DaemonClient, so they remain host-agnostic (standalone or in-process).
 */
export interface UTLXValidationResult {
  valid: boolean;
  diagnostics: Array<{
    severity: 'error' | 'warning' | 'info';
    message: string;
    line?: number;
    column?: number;
  }>;
  error?: string;
}

/**
 * Result of executing a UTLX program against the engine with sample input.
 * Mirrors the daemon's ExecutionResponse without coupling providers to it.
 */
export interface UTLXExecutionResult {
  success: boolean;
  output?: string;
  error?: string;
}

export interface UTLXExecutionInput {
  utlx: string;
  input: string;
  inputFormat?: string;
  outputFormat?: string;
}

/**
 * Optional dependencies injected into providers by the gateway/host.
 * Used by agentic providers (e.g. Claude Code) for self-correction.
 * Both are generic daemon accessors; per-request context (header, sample
 * input) arrives via LLMCompletionRequest.verification.
 */
export interface LLMProviderDeps {
  validateUtlx?: (utlx: string) => Promise<UTLXValidationResult>;
  executeUtlx?: (req: UTLXExecutionInput) => Promise<UTLXExecutionResult>;
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
