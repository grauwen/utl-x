/**
 * Code Generator (Graph → UTLX)
 *
 * Traverses the React Flow graph to generate UTLX transformation code.
 * Each output field with incoming connections produces a UTLX expression.
 *
 * Examples:
 *   Direct mapping:    OutputField: $input.InputField
 *   Function:          OutputField: toUpperCase($input.InputField)
 *   Chained:           OutputField: trim(toUpperCase($input.Name))
 *   Operator:          OutputField: $input.Price * $input.Quantity
 *   Literal + func:    OutputField: concat($input.First, " ", $input.Last)
 */

import type {
    MappingNode,
    MappingEdge,
    InputSchemaNodeData,
    OutputSchemaNodeData,
    FunctionNodeData,
    OperatorNodeData,
    LiteralNodeData,
    CodeBlockNodeData,
    SchemaField,
} from './mapping-types';

// ─── Public API ───

/**
 * Generate UTLX code from the current mapping graph.
 * Returns the body section (without headers).
 */
export function generateUtlxFromGraph(
    nodes: MappingNode[],
    edges: MappingEdge[]
): string {
    const outputNodes = nodes.filter(n => n.type === 'outputSchema');
    if (outputNodes.length === 0) return '';

    const lines: string[] = [];

    for (const outputNode of outputNodes) {
        const data = outputNode.data as OutputSchemaNodeData;
        const fieldLines = generateFieldMappings(
            data.fields,
            outputNode.id,
            nodes,
            edges,
            0
        );
        lines.push(...fieldLines);
    }

    return lines.join('\n');
}

// ─── Internal ───

/**
 * Generate UTLX lines for a list of output fields.
 * Recursively handles nested objects.
 */
function generateFieldMappings(
    fields: SchemaField[],
    outputNodeId: string,
    nodes: MappingNode[],
    edges: MappingEdge[],
    indent: number
): string[] {
    const lines: string[] = [];
    const pad = '    '.repeat(indent);

    for (const field of fields) {
        // Find incoming edge to this field's target handle
        const incomingEdge = edges.find(
            e => e.target === outputNodeId && e.targetHandle === field.id
        );

        if (incomingEdge) {
            // Trace back through the graph to build the expression
            const expr = traceExpression(
                incomingEdge.source,
                incomingEdge.sourceHandle ?? null,
                nodes,
                edges
            );

            if (field.children && field.children.length > 0) {
                // Object field with a mapped source — this could be an array mapping
                lines.push(`${pad}${field.name}: ${expr}`);
            } else {
                lines.push(`${pad}${field.name}: ${expr}`);
            }
        } else if (field.children && field.children.length > 0) {
            // Nested object — check if any children have mappings
            const childLines = generateFieldMappings(
                field.children,
                outputNodeId,
                nodes,
                edges,
                indent + 1
            );

            if (childLines.length > 0) {
                lines.push(`${pad}${field.name}: {`);
                lines.push(...childLines);
                lines.push(`${pad}}`);
            }
        }
        // If no incoming edge and no children with mappings, skip the field
    }

    return lines;
}

/**
 * Trace back from a source node/handle to build a UTLX expression.
 * Recursively follows incoming edges through function/operator nodes.
 */
function traceExpression(
    nodeId: string,
    handleId: string | null,
    nodes: MappingNode[],
    edges: MappingEdge[]
): string {
    const node = nodes.find(n => n.id === nodeId);
    if (!node) return '/* unknown source */';

    switch (node.type) {
        case 'inputSchema':
            return traceInputField(node, handleId);

        case 'function':
            return traceFunctionNode(node, nodes, edges);

        case 'operator':
            return traceOperatorNode(node, nodes, edges);

        case 'literal':
            return traceLiteralNode(node);

        case 'conditional':
            return traceConditionalNode(node, nodes, edges);

        case 'codeBlock':
            return traceCodeBlockNode(node);

        default:
            return '/* unsupported node type */';
    }
}

/**
 * Build a $input.path reference from an input schema node handle.
 */
function traceInputField(node: MappingNode, handleId: string | null): string {
    const data = node.data as InputSchemaNodeData;

    if (!handleId) {
        return `$${data.inputName}`;
    }

    // Handle ID format: "input-<name>.<path>"
    // Extract the field path from the handle ID
    const prefix = `input-${data.inputName}.`;
    if (handleId.startsWith(prefix)) {
        const fieldPath = handleId.slice(prefix.length);
        return `$${data.inputName}.${fieldPath}`;
    }

    return `$${data.inputName}`;
}

/**
 * Build a function call expression by tracing all parameter inputs.
 */
function traceFunctionNode(
    node: MappingNode,
    nodes: MappingNode[],
    edges: MappingEdge[]
): string {
    const data = node.data as FunctionNodeData;

    // Collect arguments for each parameter
    const args: string[] = [];

    for (const param of data.parameters) {
        // Check if there's an incoming edge to this parameter's handle
        const paramEdge = edges.find(
            e => e.target === node.id && e.targetHandle === param.handleId
        );

        if (paramEdge) {
            const expr = traceExpression(
                paramEdge.source,
                paramEdge.sourceHandle ?? null,
                nodes,
                edges
            );
            args.push(expr);
        } else if (param.literalValue !== undefined && param.literalValue !== '') {
            // Inline literal value
            args.push(formatLiteral(param.literalValue, param.type));
        } else if (!param.optional) {
            args.push('/* missing */');
        }
    }

    return `${data.functionName}(${args.join(', ')})`;
}

/**
 * Build an operator expression by tracing operand inputs.
 * Handle IDs match operator-node.tsx: op.<nodeId>.left, op.<nodeId>.right, op.<nodeId>.else
 */
function traceOperatorNode(
    node: MappingNode,
    nodes: MappingNode[],
    edges: MappingEdge[]
): string {
    const data = node.data as OperatorNodeData;

    // Handle IDs defined in operator-node.tsx
    const handleNames = data.arity === 3
        ? ['left', 'right', 'else']   // ternary: cond=left, then=right, else=else
        : data.arity === 1
            ? ['left']
            : ['left', 'right'];

    const operands: string[] = [];
    for (const name of handleNames) {
        const handleId = `op.${node.id}.${name}`;
        const edge = edges.find(
            e => e.target === node.id && e.targetHandle === handleId
        );

        if (edge) {
            operands.push(
                traceExpression(edge.source, edge.sourceHandle ?? null, nodes, edges)
            );
        } else {
            operands.push('/* missing */');
        }
    }

    if (data.arity === 1) {
        return `${data.symbol}${operands[0]}`;
    } else if (data.arity === 3 && data.symbol === '? :') {
        return `(${operands[0]} ? ${operands[1]} : ${operands[2]})`;
    } else {
        return `(${operands[0]} ${data.symbol} ${operands[1]})`;
    }
}

/**
 * Format a literal value for UTLX output.
 */
function traceLiteralNode(node: MappingNode): string {
    const data = node.data as LiteralNodeData;
    return formatLiteral(data.value, data.valueType);
}

/**
 * Build a conditional (ternary) expression.
 * Handle IDs match conditional-node.tsx: cond.<nodeId>.condition, cond.<nodeId>.then, cond.<nodeId>.else
 */
function traceConditionalNode(
    node: MappingNode,
    nodes: MappingNode[],
    edges: MappingEdge[]
): string {
    const handles = ['condition', 'then', 'else'];
    const parts: string[] = [];

    for (const name of handles) {
        const handleId = `cond.${node.id}.${name}`;
        const edge = edges.find(
            e => e.target === node.id && e.targetHandle === handleId
        );

        if (edge) {
            parts.push(
                traceExpression(edge.source, edge.sourceHandle ?? null, nodes, edges)
            );
        } else {
            parts.push('/* missing */');
        }
    }

    return `if (${parts[0]}) then ${parts[1]} else ${parts[2]}`;
}

/**
 * Code block nodes contain raw UTLX — return as-is.
 */
function traceCodeBlockNode(node: MappingNode): string {
    const data = node.data as CodeBlockNodeData;
    return data.expression;
}

/**
 * Format a literal value based on its type.
 */
function formatLiteral(value: string, type: string): string {
    switch (type) {
        case 'string':
            return `"${value.replace(/"/g, '\\"')}"`;
        case 'number':
        case 'integer':
            return value || '0';
        case 'boolean':
            return value === 'true' ? 'true' : 'false';
        case 'null':
            return 'null';
        default:
            return `"${value}"`;
    }
}
