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
  LLMVerificationContext,
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

const VERIFY_TOOL = 'mcp__utlx__validate_and_run';
const LOOKUP_TOOL = 'mcp__utlx__lookup_function';

export class ClaudeCodeProvider implements LLMProvider {
  private model?: string;
  private maxTurns: number;
  private selfCorrect: boolean;
  private pathToExecutable?: string;
  private validateUtlx?: LLMProviderDeps['validateUtlx'];
  private executeUtlx?: LLMProviderDeps['executeUtlx'];
  private lookupFunctions?: LLMProviderDeps['lookupFunctions'];

  constructor(config: ClaudeCodeConfig, deps?: LLMProviderDeps) {
    this.model = config.model;
    this.selfCorrect = config.selfCorrect !== false; // default on
    this.pathToExecutable = config.pathToExecutable;
    this.validateUtlx = deps?.validateUtlx;
    this.executeUtlx = deps?.executeUtlx;
    this.lookupFunctions = deps?.lookupFunctions;
    // Agentic tools (validate→run→fix cycles, or function lookups) need
    // multiple turns; a plain single-shot completion needs only one.
    const agentic = (this.selfCorrect && !!this.validateUtlx) || !!this.lookupFunctions;
    this.maxTurns = config.maxTurns ?? (agentic ? 8 : 1);
  }

  async generateCompletion(request: LLMCompletionRequest): Promise<LLMCompletionResponse> {
    try {
      const system = request.messages
        .filter(m => m.role === 'system')
        .map(m => m.content)
        .join('\n\n');

      const prompt = this.foldConversation(request);
      const options = this.buildOptions(system, request.verification);

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
   * Build Agent SDK options, wiring the in-process validate_and_run tool when
   * self-correction is enabled, a validate callback was injected, AND the
   * request carries a verification context (the fixed header). Without the
   * header we cannot reconstruct a complete program, so we skip the tool rather
   * than validate a misleading headerless fragment.
   */
  private buildOptions(system: string, verification?: LLMVerificationContext): Options {
    const canSelfCorrect =
      this.selfCorrect && !!this.validateUtlx && !!verification?.header;
    const canLookup = !!this.lookupFunctions;

    // Assemble the in-process tools and the matching allowlist.
    const tools = [];
    const allowedTools: string[] = [];
    if (canSelfCorrect && verification) {
      tools.push(this.buildVerifyTool(verification));
      allowedTools.push(VERIFY_TOOL);
    }
    if (canLookup) {
      tools.push(this.buildLookupTool());
      allowedTools.push(LOOKUP_TOOL);
    }

    // Tell the session to actually use the tools (provider-specific addendum;
    // does not affect other providers' shared system prompt).
    let systemPrompt = system;
    if (allowedTools.length > 0) {
      const hints: string[] = [];
      if (canSelfCorrect) {
        hints.push(
          '- Before finishing, call validate_and_run with your transformation BODY to confirm it ' +
            'compiles AND runs on the sample input; fix any compile or runtime errors it reports.'
        );
      }
      if (canLookup) {
        hints.push(
          '- When unsure how a stdlib function is called, call lookup_function with the name(s) and ' +
            'follow the returned signature/examples — do not guess arguments.'
        );
      }
      systemPrompt = `${system}\n\n# TOOLS\nYou have tools available — use them:\n${hints.join('\n')}`;
    }

    const options: Options = {
      ...(this.model ? { model: this.model } : {}),
      ...(this.pathToExecutable
        ? { pathToClaudeCodeExecutable: this.pathToExecutable }
        : {}),
      ...(systemPrompt ? { systemPrompt } : {}),
      maxTurns: this.maxTurns,
      disallowedTools: DISALLOWED_BUILTIN_TOOLS,
      allowedTools,
      // Hermetic: don't auto-load user/project settings or CLAUDE.md.
      settingSources: [],
      // Only our injected tools are reachable; everything else is on the
      // disallow list, so 'default' mode auto-denies stray tool requests
      // without prompting (there is no human in this loop).
      permissionMode: 'default',
    };

    if (tools.length > 0) {
      options.mcpServers = {
        utlx: createSdkMcpServer({ name: 'utlx', version: '1.0.0', tools }),
      };
    }

    return options;
  }

  /**
   * On-demand stdlib lookup. The model passes the names of functions it intends
   * to use and gets back their signatures, descriptions, and examples — so it
   * can call them correctly instead of guessing from the name alone.
   */
  private buildLookupTool() {
    return tool(
      'lookup_function',
      'Look up the exact signature, description, and examples for UTLX stdlib ' +
        'functions BEFORE using them. Pass the function names you intend to use ' +
        '(e.g. ["mapGroups", "sumBy"]). Use this whenever you are unsure how a ' +
        'function is called — do not guess its arguments.',
      { names: z.array(z.string()).describe('Function names to look up') },
      async (args: { names: string[] }) => {
        const details = await this.lookupFunctions!(args.names);
        return {
          content: [{
            type: 'text',
            text: details.length
              ? JSON.stringify(details, null, 2)
              : `No stdlib functions matched: ${args.names.join(', ')}`,
          }],
        };
      }
    );
  }

  /**
   * The validate_and_run tool. The model supplies ONLY the transformation
   * body; this reconstructs the full program with the fixed (never-modified)
   * header, validates it, and — if sample input is available — executes it so
   * runtime errors (e.g. property access on an array) surface to the model.
   */
  private buildVerifyTool(verification: LLMVerificationContext) {
    // Normalise the header exactly as the generate tool does.
    const header = verification.header.endsWith('\n')
      ? verification.header.slice(0, -1)
      : verification.header;

    return tool(
      'validate_and_run',
      'Verify your UTLX transformation BODY against the UTL-X engine. The ' +
        'fixed header is supplied automatically — pass ONLY the transformation ' +
        'body (the part after the "---"). This validates the full program and, ' +
        'when sample input is available, RUNS it. Always call this before ' +
        'finishing and fix any compile errors or runtime errors it reports.',
      { body: z.string().describe('The transformation body only (no header, no ---)') },
      async (args: { body: string }) => {
        const fullProgram = `${header}\n---\n${args.body}`;

        const validation = await this.validateUtlx!(fullProgram);
        const errorDiags = (validation.diagnostics || []).filter(d => d.severity === 'error');

        if (!validation.valid || errorDiags.length > 0) {
          return {
            content: [{
              type: 'text',
              text: JSON.stringify({
                stage: 'validation',
                valid: false,
                diagnostics: validation.diagnostics,
                error: validation.error,
              }, null, 2),
            }],
          };
        }

        // Compiles. Run it if we have both sample input and an execute callback.
        if (this.executeUtlx && verification.input) {
          const exec = await this.executeUtlx({
            utlx: fullProgram,
            input: verification.input,
            inputFormat: verification.inputFormat,
            outputFormat: verification.outputFormat,
          });
          return {
            content: [{
              type: 'text',
              text: JSON.stringify({
                stage: 'execution',
                valid: true,
                executed: exec.success,
                output: exec.success ? exec.output : undefined,
                error: exec.success ? undefined : exec.error,
              }, null, 2),
            }],
          };
        }

        return {
          content: [{
            type: 'text',
            text: JSON.stringify({
              stage: 'validation',
              valid: true,
              executed: false,
              note: 'Validated. No sample input available to run against.',
            }, null, 2),
          }],
        };
      }
    );
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
