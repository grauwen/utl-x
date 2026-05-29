/**
 * Claude Code LLM Provider
 *
 * Routes completions through an agentic Claude Code session via the Claude
 * Agent SDK, instead of a single-shot model API call. Two defining traits:
 *
 *   1. Auth flows through the user's Claude Code login (~/.claude) — no API key
 *      to provision or store. (ANTHROPIC_API_KEY is still honoured if present.)
 *   2. When a validate callback is injected (selfCorrect), the session can call
 *      a `validate_utlx` tool to check its own output against the UTLX engine
 *      and iterate until it compiles — the main lever on output quality.
 *
 * The provider is host-agnostic: it depends only on an optional `validateUtlx`
 * callback (LLMProviderDeps), never on DaemonClient, so it behaves identically
 * whether the MCP code runs standalone or inside the Electron/Theia backend.
 */

import { z } from 'zod';
import {
  query,
  tool,
  createSdkMcpServer,
  type Options,
} from '@anthropic-ai/claude-agent-sdk';
import {
  LLMProvider,
  LLMCompletionRequest,
  LLMCompletionResponse,
  LLMProviderDeps,
} from '../types.js';

export interface ClaudeCodeConfig {
  model?: string;
  maxTokens?: number;
  maxTurns?: number;
  selfCorrect?: boolean;
  pathToExecutable?: string;
}

// Built-in Claude Code tools we never want active during UTLX generation —
// this is a focused code-generation task, not a filesystem/agent session.
const DISALLOWED_BUILTIN_TOOLS = [
  'Bash',
  'Read',
  'Write',
  'Edit',
  'MultiEdit',
  'NotebookEdit',
  'Glob',
  'Grep',
  'WebFetch',
  'WebSearch',
  'Task',
  'TodoWrite',
];

const VALIDATE_TOOL = 'mcp__utlx__validate_utlx';

export class ClaudeCodeProvider implements LLMProvider {
  private model?: string;
  private maxTurns: number;
  private selfCorrect: boolean;
  private pathToExecutable?: string;
  private validateUtlx?: LLMProviderDeps['validateUtlx'];

  constructor(config: ClaudeCodeConfig, deps?: LLMProviderDeps) {
    this.model = config.model;
    this.selfCorrect = config.selfCorrect !== false; // default on
    this.pathToExecutable = config.pathToExecutable;
    this.validateUtlx = deps?.validateUtlx;
    // With self-correction we need room for validate→fix cycles; otherwise a
    // single turn is enough.
    const canSelfCorrect = this.selfCorrect && !!this.validateUtlx;
    this.maxTurns = config.maxTurns ?? (canSelfCorrect ? 8 : 1);
  }

  async generateCompletion(request: LLMCompletionRequest): Promise<LLMCompletionResponse> {
    try {
      const system = request.messages
        .filter(m => m.role === 'system')
        .map(m => m.content)
        .join('\n\n');

      const prompt = this.foldConversation(request);
      const options = this.buildOptions(system);

      let resultText = '';
      let lastAssistantText = '';
      let inputTokens = 0;
      let outputTokens = 0;
      let stopReason: LLMCompletionResponse['stopReason'] = 'end_turn';

      for await (const message of query({ prompt, options })) {
        if (message.type === 'result') {
          // Final message of the session.
          if (message.subtype === 'success' && typeof message.result === 'string') {
            resultText = message.result;
          } else if (message.subtype === 'error_max_turns') {
            stopReason = 'max_tokens';
          }
          const usage = (message as { usage?: { input_tokens?: number; output_tokens?: number } }).usage;
          if (usage) {
            inputTokens = usage.input_tokens ?? 0;
            outputTokens = usage.output_tokens ?? 0;
          }
        } else if (message.type === 'assistant') {
          // Track the latest assistant turn as a fallback if no result text
          // arrives (e.g. session ended on max turns).
          let text = '';
          for (const block of message.message?.content ?? []) {
            if (block.type === 'text') {
              text += block.text;
            }
          }
          if (text) {
            lastAssistantText = text;
          }
        }
      }

      const content = resultText || lastAssistantText;
      if (!content) {
        throw new Error('Claude Code session returned no content');
      }

      return {
        content,
        stopReason,
        usage: { inputTokens, outputTokens },
      };
    } catch (error) {
      throw new Error(
        `Claude Code error: ${error instanceof Error ? error.message : String(error)}`
      );
    }
  }

  getName(): string {
    return 'Claude Code';
  }

  async isAvailable(): Promise<boolean> {
    try {
      // Minimal hermetic round-trip — verifies the CLI is installed and the
      // session is authenticated (Claude login or ANTHROPIC_API_KEY).
      for await (const message of query({
        prompt: 'Reply with the single word: ok',
        options: {
          ...(this.model ? { model: this.model } : {}),
          ...(this.pathToExecutable
            ? { pathToClaudeCodeExecutable: this.pathToExecutable }
            : {}),
          maxTurns: 1,
          allowedTools: [],
          disallowedTools: DISALLOWED_BUILTIN_TOOLS,
          permissionMode: 'default',
          settingSources: [],
        },
      })) {
        if (message.type === 'result') {
          return message.subtype === 'success';
        }
      }
      return false;
    } catch (error) {
      console.error(
        '[ClaudeCodeProvider] Availability check failed:',
        error instanceof Error ? error.message : String(error)
      );
      return false;
    }
  }

  /**
   * Build Agent SDK options, wiring the in-process validate tool when
   * self-correction is enabled and a validate callback was injected.
   */
  private buildOptions(system: string): Options {
    const canSelfCorrect = this.selfCorrect && !!this.validateUtlx;

    const options: Options = {
      ...(this.model ? { model: this.model } : {}),
      ...(this.pathToExecutable
        ? { pathToClaudeCodeExecutable: this.pathToExecutable }
        : {}),
      ...(system ? { systemPrompt: system } : {}),
      maxTurns: this.maxTurns,
      disallowedTools: DISALLOWED_BUILTIN_TOOLS,
      allowedTools: canSelfCorrect ? [VALIDATE_TOOL] : [],
      // Hermetic: don't auto-load user/project settings or CLAUDE.md.
      settingSources: [],
      // Only our injected validate tool is reachable; everything else is on the
      // disallow list, so 'default' mode auto-denies stray tool requests
      // without prompting (there is no human in this loop).
      permissionMode: 'default',
    };

    if (canSelfCorrect) {
      options.mcpServers = {
        utlx: createSdkMcpServer({
          name: 'utlx',
          version: '1.0.0',
          tools: [
            tool(
              'validate_utlx',
              'Validate a complete UTLX program against the UTL-X engine. ' +
                'Returns whether it compiles and any diagnostics. Call this ' +
                'before finishing to confirm your transformation is valid, and ' +
                'fix any reported errors.',
              { utlx: z.string().describe('The complete UTLX program to validate') },
              async (args: { utlx: string }) => {
                const result = await this.validateUtlx!(args.utlx);
                return {
                  content: [
                    { type: 'text', text: JSON.stringify(result, null, 2) },
                  ],
                };
              }
            ),
          ],
        }),
      };
    }

    return options;
  }

  /**
   * Fold the non-system conversation into a single prompt string. For a first
   * generation this is just the user prompt; for refinement turns it preserves
   * the prior assistant output and the new feedback.
   */
  private foldConversation(request: LLMCompletionRequest): string {
    const turns = request.messages.filter(m => m.role !== 'system');
    if (turns.length === 1) {
      return turns[0].content;
    }
    return turns
      .map(m => `${m.role === 'assistant' ? 'Assistant' : 'User'}: ${m.content}`)
      .join('\n\n');
  }
}
