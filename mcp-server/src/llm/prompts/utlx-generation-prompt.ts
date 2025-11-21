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
}

/**
 * Build the system prompt for UTLX generation
 */
export function buildUTLXGenerationSystemPrompt(): string {
  return `You are an expert at writing UTLX (Universal Transformation Language) code.

UTLX SYNTAX OVERVIEW:

1. HEADER FORMAT:
%utlx 1.0
input: name1 format1, name2 format2, ...
output format
---

2. DATA ACCESS:
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

EXAMPLE 1 - Simple pass-through:
%utlx 1.0
input: data-json json
output json
---
$data-json

EXAMPLE 2 - XML to JSON with attribute access:
%utlx 1.0
input: customers-xml xml
output json
---
{
  customers: $customers-xml.customers.customer |> map(c => {
    id: c.@id,
    name: c.name,
    email: c.email
  })
}

EXAMPLE 3 - Multi-input transformation:
%utlx 1.0
input: customers-xml xml, orders-csv csv
output json
---
{
  customers: $customers-xml.customers.customer |> map(c => {
    id: c.@id,
    name: c.name,
    orders: $orders-csv |> filter(o => o.CustomerID == c.@id)
  })
}

EXAMPLE 4 - CSV to XML:
%utlx 1.0
input: employees-csv csv
output xml
---
{
  Employees: {
    Employee: $employees-csv |> map(e => {
      _: e.Name,
      @id: e.EmployeeID,
      Department: e.Department
    })
  }
}

When generating UTLX code:
1. Carefully examine the UDM structure to understand data layout
2. Use appropriate operators (map, filter) for arrays
3. Access XML attributes correctly with @ prefix
4. Ensure proper nesting for output structure
5. Reference inputs by their exact names
6. Include complete, syntactically correct code`;
}

/**
 * Build the user prompt with context
 */
export function buildUTLXGenerationUserPrompt(context: UTLXGenerationContext): string {
  let prompt = 'AVAILABLE INPUTS:\n\n';

  for (const input of context.inputs) {
    prompt += `Input: $${input.name} (${input.format} format)\n`;

    if (input.originalData) {
      prompt += `\nOriginal ${input.format.toUpperCase()} data:\n`;
      prompt += '```\n';
      // Truncate if too long
      const truncated = input.originalData.length > 1000
        ? input.originalData.substring(0, 1000) + '\n... (truncated)'
        : input.originalData;
      prompt += truncated;
      prompt += '\n```\n';
    }

    if (input.udm) {
      prompt += `\nAfter UTLX parsing (UDM structure):\n`;
      prompt += '```\n';
      // Truncate if too long
      const truncated = input.udm.length > 1500
        ? input.udm.substring(0, 1500) + '\n... (truncated)'
        : input.udm;
      prompt += truncated;
      prompt += '\n```\n';
    }

    prompt += '\n---\n\n';
  }

  prompt += `TARGET OUTPUT FORMAT: ${context.outputFormat}\n\n`;
  prompt += `USER REQUEST:\n${context.userPrompt}\n\n`;
  prompt += `CRITICAL INSTRUCTIONS:\n`;
  prompt += `1. Generate complete UTLX transformation code with header (%utlx 1.0, input declarations, output format, ---)\n`;
  prompt += `2. Return ONLY the raw UTLX code - NO markdown code blocks, NO explanations, NO commentary\n`;
  prompt += `3. Do NOT wrap in \`\`\`utlx or \`\`\` blocks\n`;
  prompt += `4. Do NOT include any text before or after the UTLX code\n`;
  prompt += `5. Start with %utlx 1.0 and end with the transformation expression\n`;
  prompt += `6. Ensure syntactically correct code that references all inputs properly\n\n`;
  prompt += `YOUR RESPONSE MUST START WITH: %utlx 1.0`;

  return prompt;
}
