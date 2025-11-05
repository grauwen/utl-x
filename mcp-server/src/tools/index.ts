/**
 * MCP Tools Registry
 *
 * Exports all available tools for UTL-X transformation assistance
 */

import { Tool } from '../types/mcp';
import { getInputSchemaTool } from './getInputSchema';
import { getStdlibFunctionsTool } from './getStdlibFunctions';
import { validateUtlxTool } from './validateUtlx';
import { inferOutputSchemaTool } from './inferOutputSchema';
import { executeTransformationTool } from './executeTransformation';
import { getExamplesTool } from './getExamples';

// Export all 6 MCP tools in logical order
export const tools: Tool[] = [
  getInputSchemaTool,        // Tool 1: Parse input schemas
  getStdlibFunctionsTool,    // Tool 2: Get stdlib function registry
  validateUtlxTool,          // Tool 3: Validate UTLX code
  inferOutputSchemaTool,     // Tool 4: Infer output schema
  executeTransformationTool, // Tool 5: Execute transformation
  getExamplesTool,           // Tool 6: Search conformance examples
];

export {
  getInputSchemaTool,
  getStdlibFunctionsTool,
  validateUtlxTool,
  inferOutputSchemaTool,
  executeTransformationTool,
  getExamplesTool,
};
