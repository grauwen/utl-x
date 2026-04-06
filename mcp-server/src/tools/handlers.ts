/**
 * Tool Handler Registry
 *
 * Maps tool names to their handler functions
 */

import { ToolInvocationResponse } from '../types/mcp';
import { DaemonClient } from '../client/DaemonClient';
import { Logger } from 'winston';

import { handleGetInputSchema } from './getInputSchema';
import { handleGetStdlibFunctions } from './getStdlibFunctions';
import { handleGetOperators } from './getOperators';
import { handleValidateUtlx } from './validateUtlx';
import { handleInferOutputSchema } from './inferOutputSchema';
import { handleExecuteTransformation } from './executeTransformation';
import { handleGetExamples } from './getExamples';
import { handleGetUsdlDirectives } from './getUsdlDirectives';
import { handleGenerateUtlx } from './generateUtlx';
import { handleCheckLlmStatus } from './checkLlmStatus';
import { LLMGateway } from '../llm/llm-gateway';

export type ToolHandler = (
  args: Record<string, unknown>,
  daemonClient: DaemonClient,
  logger: Logger,
  llmGateway?: LLMGateway,
  onProgress?: (progress: number, message?: string) => void
) => Promise<ToolInvocationResponse>;

export const toolHandlers: Record<string, ToolHandler> = {
  get_input_schema: handleGetInputSchema,
  get_stdlib_functions: handleGetStdlibFunctions,
  get_operators: handleGetOperators,
  validate_utlx: handleValidateUtlx,
  infer_output_schema: handleInferOutputSchema,
  execute_transformation: handleExecuteTransformation,
  get_examples: handleGetExamples,
  get_usdl_directives: handleGetUsdlDirectives,
  generate_utlx_from_prompt: handleGenerateUtlx,
  check_llm_status: handleCheckLlmStatus,
};
