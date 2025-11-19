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
import { getUsdlDirectivesTool } from './getUsdlDirectives';

// Export all 8 MCP tools in logical order
export const tools: Tool[] = [
  getInputSchemaTool,        // Tool 1: Parse input schemas
  getStdlibFunctionsTool,    // Tool 2: Get stdlib function registry
  getOperatorsTool,          // Tool 3: Get operator registry
  getUsdlDirectivesTool,     // Tool 4: Get USDL directive registry
  validateUtlxTool,          // Tool 5: Validate UTLX code
  inferOutputSchemaTool,     // Tool 6: Infer output schema
  executeTransformationTool, // Tool 7: Execute transformation
  getExamplesTool,           // Tool 8: Search conformance examples
];

export {
  getInputSchemaTool,
  getStdlibFunctionsTool,
  getOperatorsTool,
  getUsdlDirectivesTool,
  validateUtlxTool,
  inferOutputSchemaTool,
  executeTransformationTool,
  getExamplesTool,
};
