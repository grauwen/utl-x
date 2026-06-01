/**
 * MESSAGE-CONTRACT-mode prompt builder for UTLX code generation (IF08).
 *
 * Message Contract mode works from schemas / USDL (Tier-2) and contracts rather
 * than instance data, with design-time (schema-conformance) validation instead of
 * execution. That is a genuinely different job from Execution mode, so it lives in
 * its own module — the dispatcher (`prompt-dispatcher.ts`) selects between them and
 * neither builder contains the other's conditionals.
 *
 * v1 (IF08) ships the mode boundary in code but NOT the Message Contract prompt
 * itself: the collector and prompt are deliberately stubbed so we never claim a
 * capability we have not built. Implemented in IF08 v2.
 */

import { BuiltPrompt } from './prompt-dispatcher';
import { UTLXGenerationContext } from './execution-prompt';

/** Thrown when Message Contract AI assist is requested before v2 lands. */
export class MessageContractNotImplementedError extends Error {
  constructor(
    message = 'Message Contract mode AI assist is not yet implemented (IF08 v2). ' +
      'Switch to Execution mode, or generate the transformation there.'
  ) {
    super(message);
    this.name = 'MessageContractNotImplementedError';
  }
}

/**
 * Stub: building a Message Contract prompt is not implemented yet. Throws a
 * typed error the caller turns into a clean "not implemented" response.
 */
export function buildMessageContractPrompt(_context: UTLXGenerationContext): BuiltPrompt {
  throw new MessageContractNotImplementedError();
}
