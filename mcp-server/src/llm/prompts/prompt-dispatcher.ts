/**
 * Mode dispatcher for AI-assist prompt building (IF08).
 *
 * The IDE has two modes whose prompt construction is fully separated:
 *   - 'execution'        -> execution-prompt.ts        (instance data -> output)
 *   - 'message-contract' -> message-contract-prompt.ts (schemas/USDL; v2 stub)
 *
 * This dispatcher is the ONLY place that knows both exist. Each builder stays
 * independent — no shared `if (mode === …)` bodies inside the builders.
 */

import {
  UTLXGenerationContext,
  buildUTLXGenerationSystemPrompt,
  buildUTLXGenerationUserPrompt,
} from './execution-prompt';
import { buildMessageContractPrompt } from './message-contract-prompt';

export type AssistMode = 'execution' | 'message-contract';

export interface BuiltPrompt {
  systemPrompt: string;
  userPrompt: string;
}

/**
 * Build the system + user prompt for the given mode. Throws
 * MessageContractNotImplementedError for 'message-contract' until IF08 v2.
 */
export function buildPrompt(mode: AssistMode, context: UTLXGenerationContext): BuiltPrompt {
  switch (mode) {
    case 'message-contract':
      return buildMessageContractPrompt(context);
    case 'execution':
    default:
      return {
        systemPrompt: buildUTLXGenerationSystemPrompt(),
        userPrompt: buildUTLXGenerationUserPrompt(context),
      };
  }
}
