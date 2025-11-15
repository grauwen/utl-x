/**
 * MCP Tools Registry
 *
 * Exports all available tools for UTL-X transformation assistance
 */

import { Tool } from '../types/mcp';
import { getInputSchemaTool } from './getInputSchema';
import { getStdlibFunctionsTool } from './getStdlibFunctions';
import { getOperatorsTool } from './getOperators';
import { validateUtlxTool } from './validateUtlx';
import { inferOutputSchemaTool } from './inferOutputSchema';
import { executeTransformationTool } from './executeTransformation';
import { getExamplesTool } from './getExamples';

// Export all 7 MCP tools in logical order
export const tools: Tool[] = [
  getInputSchemaTool,        // Tool 1: Parse input schemas
  getStdlibFunctionsTool,    // Tool 2: Get stdlib function registry
  getOperatorsTool,          // Tool 3: Get operator registry
  validateUtlxTool,          // Tool 4: Validate UTLX code
  inferOutputSchemaTool,     // Tool 5: Infer output schema
  executeTransformationTool, // Tool 6: Execute transformation
  getExamplesTool,           // Tool 7: Search conformance examples
];

export {
  getInputSchemaTool,
  getStdlibFunctionsTool,
  getOperatorsTool,
  validateUtlxTool,
  inferOutputSchemaTool,
  executeTransformationTool,
  getExamplesTool,
};
