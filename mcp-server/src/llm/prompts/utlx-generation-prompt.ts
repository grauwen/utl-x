/**
 * System prompt template for UTLX code generation
 */

import * as fs from 'fs';
import * as path from 'path';

export interface UTLXGenerationInput {
  name: string;
  format: string;
  originalData?: string;
  udm?: string;
}

export interface UTLXGenerationContext {
  inputs: UTLXGenerationInput[];
  outputFormat: string;
  userPrompt: string;
  functionsContext?: string;
  operatorsContext?: string;
  // USDL directive guidance, present when a Tier 2 schema format is involved.
  usdlContext?: string;
}

/**
 * Load the UTLX language reference from markdown file
 */
function loadLanguageReference(): string {
  // Try multiple paths: dist (production) and src (development)
  const possiblePaths = [
    path.join(__dirname, 'utlx-language-reference.md'),                    // dist/llm/prompts/
    path.join(__dirname, '../../src/llm/prompts/utlx-language-reference.md'), // from dist/ to src/
    path.join(process.cwd(), 'src/llm/prompts/utlx-language-reference.md'),   // from project root
  ];

  for (const refPath of possiblePaths) {
    try {
      if (fs.existsSync(refPath)) {
        return fs.readFileSync(refPath, 'utf-8');
      }
    } catch (error) {
      // Try next path
    }
  }

  console.warn('Could not load UTLX language reference from any path:', possiblePaths);
  return 'UTLX Language Reference not available.';
}

/**
 * Build the system prompt for UTLX generation
 */
export function buildUTLXGenerationSystemPrompt(): string {
  const languageRef = loadLanguageReference();

  return `You are an expert UTLX code generator. You generate ONLY transformation expressions.

# CRITICAL RULES

1. **NEVER generate or modify headers** - headers are provided separately
2. **Generate ONLY the transformation body** - start with $ or { or [
3. **Use exact input names** from the context provided
4. **No explanations, no markdown, no code blocks** - just valid UTLX code
5. **Follow UTLX syntax precisely** as specified in the reference below
6. **NO TRAILING COMMAS** - Do not add commas after the last property in objects or arrays

# UTLX LANGUAGE REFERENCE

${languageRef}

# YOUR TASK

Generate a transformation expression that:
- Transforms input data according to the user's requirements
- Uses only the syntax and constructs defined above
- Returns a value compatible with the specified output format
- Is concise, correct, and efficient`;
}

/**
 * Format-specific structural guidance. The model is told the target format by
 * name, but each format imposes structural constraints it must respect up front
 * (rather than discovering them via execution failures). Keyed off the output
 * format; returns an empty string for unknown formats.
 */
export function buildOutputFormatGuidance(outputFormat: string): string {
  const fmt = outputFormat.trim().toLowerCase();

  switch (fmt) {
    case 'csv':
      // CSV covers tab-separated data too — TSV is just CSV with the delimiter
      // set to "\t" in the header (which is fixed and supplied separately).
      return (
        `- CSV is FLAT: produce an **array of objects**, one object per row.\n` +
        `- Every field value must be a **scalar** (string, number, or boolean).\n` +
        `- **No nested objects or arrays** — flatten them first ` +
        `(e.g. \`address.city\` becomes a \`city\` field, a list becomes joined text).\n` +
        `- All row objects should share the **same keys** (they become the columns/header).\n`
      );
    case 'xml':
      return (
        `- XML needs a **single root element**. Wrap your result accordingly.\n` +
        `- Object keys become element/attribute names — they must be valid XML names ` +
        `(no spaces, not starting with a digit).\n` +
        `- Arrays become repeated child elements under a parent element.\n`
      );
    case 'json':
    case 'yaml':
      return (
        `- ${fmt.toUpperCase()} supports arbitrary nesting (objects, arrays, scalars).\n` +
        `- Object keys must be strings; preserve the structure the user asked for.\n`
      );
    case 'odata':
      return (
        `- OData carries ENTITIES and entity collections (not a schema — that is EDMX).\n` +
        `- A collection is an **array of entity objects**; a single entity is an object.\n` +
        `- Property values are scalars or nested entities/collections per the entity model.\n`
      );
    default:
      return '';
  }
}

/**
 * Build the user prompt with context
 */
export function buildUTLXGenerationUserPrompt(context: UTLXGenerationContext): string {
  let prompt = '# GENERATION TASK\n\n';

  // Show available inputs
  prompt += '## Available Inputs\n\n';
  for (const input of context.inputs) {
    prompt += `### Input: \`$${input.name}\` (${input.format.toUpperCase()} format)\n\n`;

    if (input.udm) {
      // Check if this is compact path format or full UDM
      const isPathFormat = input.udm.includes('(array)') || input.udm.includes('(object)') || input.udm.includes('(string)');

      if (isPathFormat) {
        prompt += '**Accessible Paths** (use these exact expressions):\n```\n';
        prompt += input.udm;
        prompt += '\n```\n\n';
      } else {
        prompt += 'Data Structure:\n```\n';
        const truncated = input.udm.length > 1500
          ? input.udm.substring(0, 1500) + '\n... (truncated)'
          : input.udm;
        prompt += truncated;
        prompt += '\n```\n\n';
      }
    } else if (input.originalData) {
      prompt += 'Sample Data:\n```\n';
      const truncated = input.originalData.length > 800
        ? input.originalData.substring(0, 800) + '\n... (truncated)'
        : input.originalData;
      prompt += truncated;
      prompt += '\n```\n\n';
    }

    prompt += `Access this input: \`$${input.name}\`\n`;
    if (input.udm && input.udm.includes('[]')) {
      prompt += `For arrays: Use \`$${input.name} |> map(item => ...)\` or iterate with path like \`$${input.name}[].field\`\n`;
    }
    prompt += '\n';
  }

  // Show available functions and operators
  if (context.functionsContext) {
    prompt += '## ' + context.functionsContext.trim() + '\n\n';
  }

  if (context.operatorsContext) {
    prompt += '## ' + context.operatorsContext.trim() + '\n\n';
  }

  // Target output format
  prompt += `## Target Output Format\n\n${context.outputFormat.toUpperCase()}\n\n`;

  // Format-specific structural rules (steer the model before execution does).
  const formatGuidance = buildOutputFormatGuidance(context.outputFormat);
  if (formatGuidance) {
    prompt += `**${context.outputFormat.toUpperCase()} structural requirements:**\n${formatGuidance}\n`;
  }

  // USDL schema directives — present only when a Tier 2 schema format is
  // involved (as input or output). Tier 1 data formats omit this section.
  if (context.usdlContext) {
    prompt += `## USDL Schema Directives\n\n${context.usdlContext}\n`;
  }

  // User's transformation request
  prompt += `## User Request\n\n${context.userPrompt}\n\n`;

  // Generation instructions
  prompt += `## Instructions\n\n`;
  prompt += `Generate ONLY the transformation body that:\n`;
  prompt += `1. Uses these exact input names:\n`;
  for (const input of context.inputs) {
    prompt += `   - \`$${input.name}\`\n`;
  }
  prompt += `2. Transforms data according to the user request above\n`;
  prompt += `3. Outputs ${context.outputFormat.toUpperCase()} format\n`;
  prompt += `4. Follows UTLX syntax exactly as specified in the language reference\n`;
  prompt += `5. Is a single expression (no header, no ---, no explanations)\n\n`;

  prompt += `Start your response with the transformation code immediately.\n`;
  prompt += `Do not include headers, separators, markdown, or explanations.\n`;

  return prompt;
}
