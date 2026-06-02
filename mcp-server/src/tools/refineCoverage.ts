/**
 * Tool: refine_coverage  (IF11 — LLM gap refinement)
 *
 * The IDE computes deterministic contract coverage (output field ← input field by
 * name/type). Fields it can't match by name fall to the GAP/delta. This tool asks
 * the LLM to resolve those gaps SEMANTICALLY against the available input fields:
 * e.g. id ← customerId, fullName ← firstName + lastName, or genuinely unmappable.
 *
 * Opt-in (the IDE calls it only when the user clicks "Refine gaps (AI)"). The LLM
 * may ONLY reference fields from the supplied input list — it must not invent
 * sources. Output is strict JSON, one suggestion per gap.
 */

import { Tool, ToolInvocationResponse } from '../types/mcp';
import { DaemonClient } from '../client/DaemonClient';
import { Logger } from 'winston';
import { z } from 'zod';
import { LLMGateway } from '../llm/llm-gateway';

export const refineCoverageTool: Tool = {
  name: 'refine_coverage',
  description:
    'Resolve unmapped output-contract fields (gaps) to source fields by meaning. ' +
    'Returns, per gap, a semantic source/derivation or "unmappable". Only uses the supplied input fields.',
  inputSchema: {
    type: 'object',
    properties: {
      gaps: {
        type: 'array',
        description: 'Output fields with no deterministic source.',
        items: {
          type: 'object',
          properties: {
            path: { type: 'string' },
            type: { type: 'string' },
            required: { type: 'boolean' },
          },
          required: ['path'],
        },
      },
      inputs: {
        type: 'array',
        description: 'Available source fields, grouped by input name.',
        items: {
          type: 'object',
          properties: {
            name: { type: 'string' },
            fields: {
              type: 'array',
              items: {
                type: 'object',
                properties: {
                  path: { type: 'string' },
                  name: { type: 'string' },
                  type: { type: 'string' },
                },
                required: ['path'],
              },
            },
          },
          required: ['name', 'fields'],
        },
      },
    },
    required: ['gaps', 'inputs'],
  },
};

const ArgsSchema = z.object({
  gaps: z.array(z.object({
    path: z.string(),
    type: z.string().optional(),
    required: z.boolean().optional(),
  })),
  inputs: z.array(z.object({
    name: z.string(),
    fields: z.array(z.object({
      path: z.string(),
      name: z.string().optional(),
      type: z.string().optional(),
    })),
  })),
});

interface Suggestion {
  path: string;
  status: 'direct' | 'derivable' | 'unmappable';
  source?: string;
  expression?: string;
  rationale?: string;
}

const ok = (suggestions: Suggestion[]): ToolInvocationResponse => ({
  content: [{ type: 'text', text: JSON.stringify({ success: true, suggestions }, null, 2) }],
});
const fail = (error: string): ToolInvocationResponse => ({
  content: [{ type: 'text', text: JSON.stringify({ success: false, error }, null, 2) }],
  isError: true,
});

/** Pull the first JSON object/array out of a (possibly fenced) LLM reply. */
function extractJson(text: string): any {
  let t = (text || '').trim();
  // Strip ```json … ``` fences if present.
  const fence = t.match(/```(?:json)?\s*([\s\S]*?)```/i);
  if (fence) t = fence[1].trim();
  // Otherwise slice from the first { or [ to its matching last } or ].
  if (!t.startsWith('{') && !t.startsWith('[')) {
    const start = Math.min(
      ...['{', '['].map(c => { const i = t.indexOf(c); return i < 0 ? Number.MAX_SAFE_INTEGER : i; }),
    );
    if (start !== Number.MAX_SAFE_INTEGER) t = t.slice(start);
  }
  return JSON.parse(t);
}

export async function handleRefineCoverage(
  args: Record<string, unknown>,
  _daemonClient: DaemonClient,
  logger: Logger,
  llmGateway?: LLMGateway,
): Promise<ToolInvocationResponse> {
  const { gaps, inputs } = ArgsSchema.parse(args);

  if (gaps.length === 0) {
    return ok([]);
  }
  if (!llmGateway) {
    return fail('LLM gateway is not configured.');
  }
  if (!(await llmGateway.isAvailable())) {
    return fail(`LLM provider (${llmGateway.getProviderName()}) is not available.`);
  }

  const gapLines = gaps
    .map(g => `- ${g.path} (${g.type || 'any'})${g.required ? ' [required]' : ''}`)
    .join('\n');
  const inputLines = inputs
    .map(i => `[${i.name}]\n` + i.fields.map(f => `  ${f.path} (${f.type || 'any'})`).join('\n'))
    .join('\n');

  const system =
    'You map unmapped TARGET fields to SOURCE fields by meaning. For each target, pick ' +
    'the best source field(s) from the provided inputs (e.g. "id" ← "customerId"; ' +
    '"fullName" ← "firstName" + "lastName"; a code that needs a lookup table → unmappable). ' +
    'STRICT RULES: only reference source paths that appear in the input list — never invent ' +
    'fields. If no plausible source exists, mark the target "unmappable". Reply with STRICT ' +
    'JSON only, no prose, no code fences.';
  const user =
    `TARGET fields needing a source:\n${gapLines}\n\n` +
    `AVAILABLE source fields:\n${inputLines}\n\n` +
    'Return JSON of this exact shape:\n' +
    '{"suggestions":[{"path":"<target path>","status":"direct|derivable|unmappable",' +
    '"source":"<input.path or input.a + input.b>","expression":"<optional UTL-X-ish hint>",' +
    '"rationale":"<short why>"}]}\n' +
    'Use "direct" when a single source field matches 1:1, "derivable" when it needs a ' +
    'combine/convert/lookup-from-the-inputs, "unmappable" when nothing in the inputs fits ' +
    '(omit source/expression then).';

  try {
    const response = await llmGateway.generateCompletion({
      messages: [
        { role: 'system', content: system },
        { role: 'user', content: user },
      ],
      maxTokens: 800,
      temperature: 0.1,
    });
    const parsed = extractJson(response.content || '');
    const rawList: any[] = Array.isArray(parsed) ? parsed : (parsed.suggestions || []);
    const suggestions: Suggestion[] = rawList
      .filter(s => s && typeof s.path === 'string')
      .map(s => {
        const status: Suggestion['status'] =
          s.status === 'direct' || s.status === 'derivable' ? s.status
          : s.status === 'unmappable' ? 'unmappable'
          : (s.source ? 'derivable' : 'unmappable');
        return {
          path: String(s.path),
          status,
          source: typeof s.source === 'string' ? s.source : undefined,
          expression: typeof s.expression === 'string' ? s.expression : undefined,
          rationale: typeof s.rationale === 'string' ? s.rationale : undefined,
        };
      });
    return ok(suggestions);
  } catch (error) {
    logger.warn('refine_coverage failed', { error });
    return fail(error instanceof Error ? error.message : String(error));
  }
}
