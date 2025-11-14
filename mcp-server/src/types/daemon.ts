/**
 * Type definitions for UTL-X Daemon REST API
 */

/**
 * Validation request
 */
export interface ValidationRequest {
  utlx: string;
  strict?: boolean;
}

export interface Diagnostic {
  severity: 'error' | 'warning' | 'info';
  message: string;
  line?: number;
  column?: number;
  source?: string;
}

export interface ValidationResponse {
  valid: boolean;
  diagnostics: Diagnostic[];
  error?: string;
}

/**
 * Execution request
 */
export interface ExecutionRequest {
  utlx: string;
  input: string;
  inputFormat?: string; // json, xml, csv, yaml
  outputFormat?: string;
}

export interface ExecutionResponse {
  success: boolean;
  output?: string;
  error?: string;
  executionTimeMs: number;
}

/**
 * Schema inference request
 */
export interface InferSchemaRequest {
  utlx: string;
  inputSchema?: string;
  format?: string; // json-schema, xsd
}

export interface InferSchemaResponse {
  success: boolean;
  schema?: string;
  schemaFormat: string;
  confidence?: number;
  error?: string;
}

/**
 * Schema parsing request
 */
export interface ParseSchemaRequest {
  schema: string;
  format: string; // xsd, json-schema, csv-header, yaml
}

export interface ParseSchemaResponse {
  success: boolean;
  typeDef?: Record<string, unknown>; // Parsed type definition
  schemaFormat?: string; // Format of the parsed schema
  rootElement?: string; // Root element name (for XSD)
  normalized?: string;
  error?: string;
}

/**
 * Health check response
 */
export interface HealthResponse {
  status: string;
  version: string;
  uptime: number;
}

/**
 * Function registry types
 */
export interface ParameterInfo {
  name: string;
  type: string;
  description?: string;
}

export interface ReturnInfo {
  type: string;
  description?: string;
}

export interface FunctionInfo {
  name: string;
  category: string;
  description: string;
  signature: string;
  minArgs?: number;
  maxArgs?: number;
  parameters?: ParameterInfo[];
  returns?: ReturnInfo;
  examples?: string[];
  notes?: string;
  tags?: string[];
  seeAlso?: string[];
  since?: string;
  deprecated?: boolean;
  deprecationMessage?: string;
  isAlias?: boolean;
  aliasOf?: string;
}

export interface FunctionRegistry {
  version: string;
  generatedAt: string;
  totalFunctions: number;
  functions: FunctionInfo[];
  categories: Record<string, FunctionInfo[]>;
}
