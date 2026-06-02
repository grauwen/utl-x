/**
 * Tool: describe_input  (IF10 v2 — semantic gloss for an input)
 *
 * Given a DETERMINISTIC structural summary of an input message, returns ONE concise
 * sentence naming the likely domain/kind of message. This is the "what is this"
 * layer the deterministic abstract can't provide. It is opt-in (the IDE calls it only
 * when the user clicks "Explain (AI)") and the gloss is flavor on top of the facts —
 * never the source of field truths.
 */

import { Tool, ToolInvocationResponse } from '../types/mcp';
import { DaemonClient } from '../client/DaemonClient';
import { Logger } from 'winston';
import { z } from 'zod';
import { LLMGateway } from '../llm/llm-gateway';

export const describeInputTool: Tool = {
  name: 'describe_input',
  description: 'Given a structural summary of an input message, name the likely domain/kind of message in one sentence (semantic gloss).',
  inputSchema: {
    type: 'object',
    properties: {
      abstract: { type: 'string', description: 'Deterministic structural summary of the input (entity, fields, arrays, depth).' },
      format: { type: 'string', description: 'Input format (json, xml, jsch, xsd, …).' },
    },
    required: ['abstract'],
  },
};

const DescribeInputArgsSchema = z.object({
  abstract: z.string(),
  format: z.string().optional(),
});

const ok = (description: string): ToolInvocationResponse => ({
  content: [{ type: 'text', text: JSON.stringify({ success: true, description }, null, 2) }],
});
const fail = (error: string): ToolInvocationResponse => ({
  content: [{ type: 'text', text: JSON.stringify({ success: false, error }, null, 2) }],
  isError: true,
});

export async function handleDescribeInput(
  args: Record<string, unknown>,
  _daemonClient: DaemonClient,
  logger: Logger,
  llmGateway?: LLMGateway,
): Promise<ToolInvocationResponse> {
  const { abstract, format } = DescribeInputArgsSchema.parse(args);

  if (!llmGateway) {
    return fail('LLM gateway is not configured.');
  }
  if (!(await llmGateway.isAvailable())) {
    return fail(`LLM provider (${llmGateway.getProviderName()}) is not available.`);
  }

  const system =
    'You label data structures. Given a STRUCTURAL summary of a message, reply with ' +
    'ONE concise sentence naming the likely domain/kind of message and its purpose ' +
    '(e.g. "an HL7-style patient record", "a B2B purchase order", "a customer profile"). ' +
    'Do NOT list fields, do NOT invent fields, and do NOT output code. If genuinely ' +
    'unsure, say so briefly.';
  const user =
    `Format: ${format || 'unknown'}\nStructure:\n${abstract}\n\n` +
    'What kind of message is this, in one sentence?';

  try {
    const response = await llmGateway.generateCompletion({
      messages: [
        { role: 'system', content: system },
        { role: 'user', content: user },
      ],
      maxTokens: 120,
      temperature: 0.2,
    });
    const description = (response.content || '').trim();
    if (!description) {
      return fail('No description produced.');
    }
    return ok(description);
  } catch (error) {
    logger.warn('describe_input failed', { error });
    return fail(error instanceof Error ? error.message : String(error));
  }
}
