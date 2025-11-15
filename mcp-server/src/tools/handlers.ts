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

export type ToolHandler = (
  args: Record<string, unknown>,
  daemonClient: DaemonClient,
  logger: Logger
) => Promise<ToolInvocationResponse>;

export const toolHandlers: Record<string, ToolHandler> = {
  get_input_schema: handleGetInputSchema,
  get_stdlib_functions: handleGetStdlibFunctions,
  get_operators: handleGetOperators,
  validate_utlx: handleValidateUtlx,
  infer_output_schema: handleInferOutputSchema,
  execute_transformation: handleExecuteTransformation,
  get_examples: handleGetExamples,
};
