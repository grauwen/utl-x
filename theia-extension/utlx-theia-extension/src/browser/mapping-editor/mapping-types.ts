/**
 * Mapping Editor Type Definitions
 *
 * Defines the data model for the graphical mapping canvas.
 * All node and edge types used by React Flow are defined here.
 */

import type { Node, Edge } from '@xyflow/react';

// ─── Schema Field (shared between input/output nodes) ───

/** A field within a schema node, with a unique handle ID */
export interface SchemaField {
    id: string;              // unique handle ID: "input.order.id"
    name: string;            // display name: "id"
    path: string;            // full dot path: "order.id"
    type: string;            // "string" | "number" | "boolean" | "object" | "array" | ...
    children?: SchemaField[];
    isRequired?: boolean;
    schemaType?: string;     // original schema type (xs:string, etc.)
    constraints?: string;
}

// ─── Node Data Types ───

/** Data carried by an input schema node */
export interface InputSchemaNodeData {
    kind: 'inputSchema';
    inputName: string;       // e.g. "input", "orders"
    format: string;          // "json", "xml", "csv", ...
    fields: SchemaField[];
    isArray: boolean;
    [key: string]: unknown;  // React Flow requires index signature
}

/** Data carried by an output schema node */
export interface OutputSchemaNodeData {
    kind: 'outputSchema';
    format: string;
    fields: SchemaField[];
    [key: string]: unknown;
}

/** Data carried by a function node */
export interface FunctionNodeData {
    kind: 'function';
    functionName: string;    // e.g. "toUpperCase"
    category: string;
    parameters: FunctionParam[];
    returnType: string;
    signature: string;
    [key: string]: unknown;
}

export interface FunctionParam {
    name: string;
    type: string;
    handleId: string;        // unique handle ID for this parameter port
    literalValue?: string;   // inline literal value (if user typed it)
    optional?: boolean;
}

/** Data carried by an operator node */
export interface OperatorNodeData {
    kind: 'operator';
    symbol: string;          // "+", "==", "? :"
    name: string;
    arity: number;           // 1 (unary) or 2 (binary) or 3 (ternary)
    [key: string]: unknown;
}

/** Data carried by a literal node */
export interface LiteralNodeData {
    kind: 'literal';
    value: string;
    valueType: 'string' | 'number' | 'boolean' | 'null';
    [key: string]: unknown;
}

/** Data carried by a conditional node */
export interface ConditionalNodeData {
    kind: 'conditional';
    // Three input handles: condition, trueBranch, falseBranch
    // One output handle
    [key: string]: unknown;
}

/** Data carried by a code block node (opaque UTLX) */
export interface CodeBlockNodeData {
    kind: 'codeBlock';
    expression: string;      // raw UTLX expression
    outputFieldName: string; // the output field this maps to
    [key: string]: unknown;
}

export type MappingNodeData =
    | InputSchemaNodeData
    | OutputSchemaNodeData
    | FunctionNodeData
    | OperatorNodeData
    | LiteralNodeData
    | ConditionalNodeData
    | CodeBlockNodeData;

// ─── Node Type Registry ───

/** String identifiers for custom node types (used by React Flow nodeTypes) */
export const NODE_TYPES = {
    inputSchema: 'inputSchema',
    outputSchema: 'outputSchema',
    function: 'function',
    operator: 'operator',
    literal: 'literal',
    conditional: 'conditional',
    codeBlock: 'codeBlock',
} as const;

export type MappingNodeType = keyof typeof NODE_TYPES;

// ─── Edge Types ───

export interface MappingEdgeData {
    sourceType?: string;     // type flowing through this edge
    label?: string;          // optional label shown on edge
    [key: string]: unknown;
}

export const EDGE_TYPES = {
    mapping: 'mapping',
} as const;

// ─── Concrete Node/Edge types ───

export type MappingNode = Node<MappingNodeData, string>;
export type MappingEdge = Edge<MappingEdgeData>;

// ─── View Mode ───

export type ViewMode = 'classic' | 'canvas';
