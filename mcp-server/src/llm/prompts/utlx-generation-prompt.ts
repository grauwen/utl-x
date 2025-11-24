/**
 * System prompt template for UTLX code generation
 */

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
}

/**
 * Build the system prompt for UTLX generation
 */
export function buildUTLXGenerationSystemPrompt(): string {
  return `You are an expert at writing UTLX (Universal Transformation Language) transformation expressions.

CRITICAL: You generate ONLY transformation expressions (the body). The header is handled separately.

UTLX TRANSFORMATION SYNTAX:

1. DATA ACCESS:
- Reference inputs with $inputName
- Access object properties: $input.property
- Access nested properties: $input.parent.child
- Access array elements: $input.items[0]
- Access XML attributes with @: $input.element.@id

3. OPERATORS:
- map: Transform array elements
  Example: $input |> map(x => { id: x.@id, name: x.name })
- filter: Filter array elements
  Example: $input |> filter(x => x.age > 18)
- flatten: Flatten nested arrays
- merge: Merge objects
- keys: Get object keys
- values: Get object values

4. OBJECT LITERALS:
{
  key: value,
  nested: {
    property: $input.field
  }
}

5. ARRAY LITERALS:
[
  $input.item1,
  $input.item2
]

6. CONDITIONALS:
- Use ternary: condition ? value1 : value2
- Example: $input.age > 18 ? "adult" : "minor"

7. STRING OPERATIONS:
- Concatenation with +
- Example: $input.firstName + " " + $input.lastName

IMPORTANT RULES:
- Always include the complete header (%utlx 1.0, input, output, ---)
- Use exact input names as provided
- For XML: attributes are accessed with @ prefix
- For CSV: data is typically an array of objects
- For JSON: direct property access
- Arrays can be iterated with map operator
- Objects can be constructed with { } syntax
- Reference multiple inputs by name: $input1, $input2, etc.

TRANSFORMATION EXPRESSION EXAMPLES:

Simple pass-through:
$data

Array transformation:
$items |> map(item => {
  id: item.id,
  name: item.name
})

XML with attributes:
$xml.root.elements |> map(el => {
  id: el.@id,
  value: el.text
})

Filtering and mapping:
$data |> filter(x => x.active) |> map(x => x.name)

Object construction:
{
  total: $data |> map(x => x.amount) |> sum(),
  items: $data
}

SYNTAX RULES:
- Use $inputName to reference inputs
- Use |> to pipe operations
- Use => for lambdas
- Use { } for objects
- Use [ ] for arrays
- Use @ for XML attributes
- Start with $ or { or [`;
}

/**
 * Build the user prompt with context
 */
export function buildUTLXGenerationUserPrompt(context: UTLXGenerationContext): string {
  // Build dynamic examples based on actual inputs
  let prompt = '==================================================\n';
  prompt += 'YOUR TASK: Generate UTLX transformation body ONLY\n';
  prompt += '==================================================\n\n';

  prompt += 'INPUTS YOU MUST USE:\n';
  for (const input of context.inputs) {
    prompt += `- $${input.name} (${input.format} format)\n`;
  }
  prompt += '\n';

  // Show data structure for each input
  for (const input of context.inputs) {
    prompt += `INPUT: $${input.name}\n`;
    prompt += `Format: ${input.format.toUpperCase()}\n`;

    if (input.udm) {
      prompt += `\nData Structure (UDM):\n`;
      prompt += '```\n';
      // Truncate if too long
      const truncated = input.udm.length > 1500
        ? input.udm.substring(0, 1500) + '\n... (truncated)'
        : input.udm;
      prompt += truncated;
      prompt += '\n```\n';
    } else if (input.originalData) {
      prompt += `\nSample Data:\n`;
      prompt += '```\n';
      const truncated = input.originalData.length > 800
        ? input.originalData.substring(0, 800) + '\n... (truncated)'
        : input.originalData;
      prompt += truncated;
      prompt += '\n```\n';
    }

    prompt += `\nTo access this input: $${input.name}\n`;
    prompt += `To access properties: $${input.name}.propertyName\n`;
    if (input.format === 'xml') {
      prompt += `To access XML attributes: $${input.name}.element.@attributeName\n`;
    }
    if (input.format === 'json' || input.format === 'csv') {
      prompt += `If array, use: $${input.name} |> map(item => { ... })\n`;
    }
    prompt += '\n---\n\n';
  }

  // Add functions and operators context if available
  if (context.functionsContext) {
    prompt += context.functionsContext;
    prompt += '\n';
  }

  if (context.operatorsContext) {
    prompt += context.operatorsContext;
    prompt += '\n';
  }

  prompt += `TARGET OUTPUT FORMAT: ${context.outputFormat}\n\n`;
  prompt += `==================================================\n`;
  prompt += `USER REQUEST:\n`;
  prompt += `${context.userPrompt}\n`;
  prompt += `==================================================\n\n`;

  // Generate dynamic example based on actual inputs
  prompt += `STRICT INSTRUCTIONS:\n\n`;
  prompt += `1. Generate ONLY the transformation body (NO header, NO ---)\n`;
  prompt += `2. Start your response with: $${context.inputs[0].name} or { or [\n`;
  prompt += `3. Use EXACTLY these input names:\n`;
  for (const input of context.inputs) {
    prompt += `   - $${input.name}\n`;
  }
  prompt += `4. Use |> for piping (e.g., $${context.inputs[0].name} |> map(x => ...))\n`;
  prompt += `5. Use => for lambda functions (e.g., x => x.property)\n`;
  prompt += `6. Use { } for objects, [ ] for arrays\n`;
  prompt += `7. NO explanations, NO markdown, NO code blocks\n`;
  prompt += `8. Output ${context.outputFormat.toUpperCase()} format\n\n`;

  prompt += `EXAMPLE for $${context.inputs[0].name}:\n`;
  if (context.inputs.length === 1) {
    if (context.inputs[0].format === 'json' || context.inputs[0].format === 'csv') {
      prompt += `$${context.inputs[0].name} |> map(item => {\n`;
      prompt += `  property1: item.field1,\n`;
      prompt += `  property2: item.field2\n`;
      prompt += `})\n\n`;
    } else if (context.inputs[0].format === 'xml') {
      prompt += `$${context.inputs[0].name}.rootElement.childArray |> map(item => {\n`;
      prompt += `  id: item.@id,\n`;
      prompt += `  name: item.name\n`;
      prompt += `})\n\n`;
    }
  }

  prompt += `NOW GENERATE THE TRANSFORMATION:\n`;

  return prompt;
}
