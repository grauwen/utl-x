/**
 * MESSAGE-CONTRACT-mode prompt builder for UTLX code generation (IF08 / IF11).
 *
 * Message Contract mode maps N input schemas onto a PREDEFINED output schema (the
 * contract). Unlike Execution mode it works from schemas, not instance data, and the
 * output structure is fixed — so generation is *constrained synthesis*: fill every
 * output field from a source, and where there is no source (a coverage GAP) emit a
 * clearly-flagged placeholder/default rather than inventing data.
 *
 * The prompt is driven by three things the IDE supplies:
 *   - the output contract schema (the fixed target),
 *   - each input's schema (the available sources),
 *   - the coverage plan (output field → source / derivation / gap),
 * plus an OPTIONAL user instruction (guidance is optional in MCM — the default goal
 * is simply "map it as far as possible").
 */

import { BuiltPrompt } from './prompt-dispatcher';
import { UTLXGenerationContext, CoverageMappingHint } from './execution-prompt';

/** Retained for back-compat; no longer thrown now that MCM generation is implemented. */
export class MessageContractNotImplementedError extends Error {
  constructor(message = 'Message Contract mode AI assist is not yet implemented.') {
    super(message);
    this.name = 'MessageContractNotImplementedError';
  }
}

function renderCoverage(coverage: CoverageMappingHint[]): string {
  const line = (h: CoverageMappingHint) => {
    const tag = h.status === 'gap' ? 'GAP' : h.status === 'derivable' ? 'derive' : 'direct';
    const src = h.expression ? ` = ${h.expression}` : h.source ? ` <- ${h.source}` : '';
    const req = h.required ? ' [required]' : '';
    return `  ${h.outputPath} (${h.type})${req}: ${tag}${src}`;
  };
  const gaps = coverage.filter(h => h.status === 'gap');
  const mapped = coverage.filter(h => h.status !== 'gap');
  const parts: string[] = [];
  if (mapped.length) parts.push('MAPPED (fill from the source):\n' + mapped.map(line).join('\n'));
  if (gaps.length) {
    parts.push(
      'GAPS (no source — emit a placeholder default and a TODO comment, do NOT invent data):\n' +
      gaps.map(line).join('\n'),
    );
  }
  return parts.join('\n\n');
}

export function buildMessageContractSystemPrompt(): string {
  return [
    'You generate UTL-X transformations for MESSAGE CONTRACT mode.',
    '',
    'The OUTPUT structure is FIXED by a contract schema. Your job is constrained synthesis:',
    '- Produce output that conforms EXACTLY to the output contract — every required field,',
    '  correct nesting, correct types. Do NOT add fields outside the contract and do NOT',
    '  drop required ones.',
    '- Source each output field from the inputs. Access inputs as $inputName.path; map arrays',
    '  with `map`, and join/lookup across inputs by building an index then looking it up.',
    '- For a field marked GAP (no source): emit a clearly-flagged placeholder — a sensible',
    '  literal default for its type (e.g. "" / 0 / false) AND a `// TODO: <field> — <why>`',
    '  comment. NEVER fabricate a realistic-looking value for a gap.',
    '- Prefer the provided coverage plan for sourcing; use the schemas as the structural truth.',
    '',
    'Output ONLY the transformation body (after the --- separator). Do not restate the header.',
  ].join('\n');
}

export function buildMessageContractUserPrompt(context: UTLXGenerationContext): string {
  const parts: string[] = [];

  const guidance = (context.userPrompt || '').trim();
  parts.push(
    guidance
      ? `TASK: Map the inputs onto the output contract as far as possible. Additional guidance from the user:\n${guidance}`
      : 'TASK: Map the inputs onto the output contract as far as possible (no extra guidance — produce the best-effort contract mapping).',
  );

  if (context.outputSchema) {
    parts.push(
      `OUTPUT CONTRACT (${context.outputSchema.format}) — the fixed target structure:\n` +
      context.outputSchema.content,
    );
  }

  if (context.inputs.length) {
    const inputBlock = context.inputs
      .map(i => {
        const struct = i.schema || i.udm || i.originalData || '(no structure provided)';
        return `### $${i.name} (${i.format})\n${struct}`;
      })
      .join('\n\n');
    parts.push(`INPUTS (sources):\n${inputBlock}`);
  }

  if (context.coverage && context.coverage.length) {
    parts.push(`COVERAGE PLAN (output field -> source):\n${renderCoverage(context.coverage)}`);
  }

  parts.push(`Output data format: ${context.outputFormat}.`);

  if (context.functionsContext) parts.push(context.functionsContext);
  if (context.operatorsContext) parts.push(context.operatorsContext);
  if (context.usdlContext) parts.push(context.usdlContext);

  parts.push(
    'Produce the UTL-X transformation body that builds the output contract: fill every mapped ' +
    'field from its source, and for each GAP emit a typed placeholder default plus a TODO ' +
    'comment naming the missing field. Keep it as complete and as correct as the sources allow.',
  );

  return parts.join('\n\n');
}

export function buildMessageContractPrompt(context: UTLXGenerationContext): BuiltPrompt {
  return {
    systemPrompt: buildMessageContractSystemPrompt(),
    userPrompt: buildMessageContractUserPrompt(context),
  };
}
