/**
 * MCP Type Definitions for Playwright MCP Server
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

export interface ToolInvocationResponse {
  content: Array<{
    type: 'text' | 'resource' | 'image';
    text?: string;
    data?: string;
    mimeType?: string;
  }>;
  isError?: boolean;
}

export interface ConsoleMessage {
  timestamp: string;
  level: 'log' | 'warn' | 'error' | 'info' | 'debug';
  message: string;
  location?: {
    url: string;
    lineNumber?: number;
    columnNumber?: number;
  };
}

export interface ErrorLog {
  timestamp: string;
  message: string;
  stack?: string;
  name?: string;
}

export interface NetworkLog {
  timestamp: string;
  method: string;
  url: string;
  status?: number;
  statusText?: string;
  duration?: number;
  failed: boolean;
  type: 'xhr' | 'fetch' | 'document' | 'script' | 'stylesheet' | 'image' | 'other';
}

export interface PageInfo {
  url: string;
  title: string;
  timestamp: string;
}

export interface Screenshot {
  path?: string;
  base64?: string;
  timestamp: string;
}
