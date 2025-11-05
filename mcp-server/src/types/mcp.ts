/**
 * Model Context Protocol (MCP) Type Definitions
 *
 * Defines the JSON-RPC 2.0 based protocol for tool invocation
 */

export interface JsonRpcRequest {
  jsonrpc: '2.0';
  id: string | number;
  method: string;
  params?: Record<string, unknown>;
}

export interface JsonRpcResponse {
  jsonrpc: '2.0';
  id: string | number | null;
  result?: unknown;
  error?: JsonRpcError;
}

export interface JsonRpcError {
  code: number;
  message: string;
  data?: unknown;
}

export const JsonRpcErrorCode = {
  PARSE_ERROR: -32700,
  INVALID_REQUEST: -32600,
  METHOD_NOT_FOUND: -32601,
  INVALID_PARAMS: -32602,
  INTERNAL_ERROR: -32603,
} as const;

/**
 * MCP Tool Definition
 */
export interface Tool {
  name: string;
  description: string;
  inputSchema: {
    type: 'object';
    properties: Record<string, unknown>;
    required?: string[];
  };
}

/**
 * Tool invocation request
 */
export interface ToolInvocationRequest {
  name: string;
  arguments: Record<string, unknown>;
}

/**
 * Tool invocation response
 */
export interface ToolInvocationResponse {
  content: Array<{
    type: 'text' | 'resource';
    text?: string;
    uri?: string;
    mimeType?: string;
  }>;
  isError?: boolean;
}

/**
 * MCP Server capabilities
 */
export interface ServerCapabilities {
  tools?: {
    supported: boolean;
  };
  resources?: {
    supported: boolean;
  };
  prompts?: {
    supported: boolean;
  };
}

/**
 * Initialize request
 */
export interface InitializeRequest {
  protocolVersion: string;
  capabilities: {
    tools?: { supported: boolean };
  };
  clientInfo: {
    name: string;
    version: string;
  };
}

/**
 * Initialize response
 */
export interface InitializeResponse {
  protocolVersion: string;
  capabilities: ServerCapabilities;
  serverInfo: {
    name: string;
    version: string;
  };
}
