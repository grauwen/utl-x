/**
 * JSON-RPC 2.0 Protocol Handler for MCP
 *
 * Handles incoming JSON-RPC requests and routes them to appropriate tool handlers
 */

import { Logger } from 'winston';
import {
  JsonRpcRequest,
  JsonRpcResponse,
  JsonRpcError,
  JsonRpcErrorCode,
  Tool,
} from '../types/mcp';
import { DaemonClient } from '../client/DaemonClient';
import { toolHandlers } from '../tools/handlers';
import { tools } from '../tools';

export class JsonRpcHandler {
  private logger: Logger;
  private daemonClient: DaemonClient;
  private toolsMap: Map<string, Tool>;
  private shutdownCallback?: () => void;

  constructor(daemonClient: DaemonClient, logger: Logger, shutdownCallback?: () => void) {
    this.logger = logger;
    this.daemonClient = daemonClient;
    this.shutdownCallback = shutdownCallback;

    // Build tools map for quick lookup
    this.toolsMap = new Map();
    for (const tool of tools) {
      this.toolsMap.set(tool.name, tool);
    }
  }

  /**
   * Handle a JSON-RPC request
   */
  async handleRequest(request: JsonRpcRequest): Promise<JsonRpcResponse> {
    this.logger.info('Handling JSON-RPC request', {
      method: request.method,
      id: request.id,
    });

    try {
      // Validate request
      if (request.jsonrpc !== '2.0') {
        return this.errorResponse(
          request.id,
          JsonRpcErrorCode.INVALID_REQUEST,
          'Invalid JSON-RPC version. Expected "2.0"'
        );
      }

      // Route to appropriate handler based on method
      switch (request.method) {
        case 'initialize':
          return this.handleInitialize(request);

        case 'tools/list':
          return this.handleToolsList(request);

        case 'tools/call':
          return this.handleToolsCall(request);

        case 'shutdown':
          return this.handleShutdown(request);

        case 'ping':
          return this.handlePing(request);

        default:
          return this.errorResponse(
            request.id,
            JsonRpcErrorCode.METHOD_NOT_FOUND,
            `Method not found: ${request.method}`
          );
      }
    } catch (error) {
      this.logger.error('Error handling JSON-RPC request', {
        error,
        method: request.method,
        id: request.id,
      });

      return this.errorResponse(
        request.id,
        JsonRpcErrorCode.INTERNAL_ERROR,
        error instanceof Error ? error.message : 'Internal error'
      );
    }
  }

  /**
   * Handle initialize request
   */
  private handleInitialize(request: JsonRpcRequest): JsonRpcResponse {
    this.logger.info('Initializing MCP server');

    return {
      jsonrpc: '2.0',
      id: request.id,
      result: {
        protocolVersion: '2024-11-05',
        serverInfo: {
          name: 'utlx-mcp-server',
          version: '1.0.0',
        },
        capabilities: {
          tools: {
            listChanged: false,
          },
        },
      },
    };
  }

  /**
   * Handle tools/list request
   */
  private handleToolsList(request: JsonRpcRequest): JsonRpcResponse {
    this.logger.info('Listing available tools');

    return {
      jsonrpc: '2.0',
      id: request.id,
      result: {
        tools: tools.map((tool) => ({
          name: tool.name,
          description: tool.description,
          inputSchema: tool.inputSchema,
        })),
      },
    };
  }

  /**
   * Handle tools/call request
   */
  private async handleToolsCall(request: JsonRpcRequest): Promise<JsonRpcResponse> {
    const params = request.params as { name?: string; arguments?: Record<string, unknown> };

    if (!params || !params.name) {
      return this.errorResponse(
        request.id,
        JsonRpcErrorCode.INVALID_PARAMS,
        'Missing required parameter: name'
      );
    }

    const toolName = params.name;
    const args = params.arguments || {};

    this.logger.info('Calling tool', { toolName, args });

    // Check if tool exists
    if (!this.toolsMap.has(toolName)) {
      return this.errorResponse(
        request.id,
        JsonRpcErrorCode.METHOD_NOT_FOUND,
        `Tool not found: ${toolName}`
      );
    }

    // Get tool handler
    const handler = toolHandlers[toolName];
    if (!handler) {
      return this.errorResponse(
        request.id,
        JsonRpcErrorCode.INTERNAL_ERROR,
        `Handler not implemented for tool: ${toolName}`
      );
    }

    try {
      // Invoke tool handler
      const result = await handler(args, this.daemonClient, this.logger);

      return {
        jsonrpc: '2.0',
        id: request.id,
        result,
      };
    } catch (error) {
      this.logger.error('Error calling tool', {
        toolName,
        error,
      });

      return this.errorResponse(
        request.id,
        JsonRpcErrorCode.INTERNAL_ERROR,
        error instanceof Error ? error.message : 'Tool execution failed'
      );
    }
  }

  /**
   * Handle shutdown request
   */
  private handleShutdown(request: JsonRpcRequest): JsonRpcResponse {
    this.logger.info('Shutdown requested');

    // Schedule shutdown after sending response
    if (this.shutdownCallback) {
      setTimeout(() => {
        this.shutdownCallback!();
      }, 100);
    }

    return {
      jsonrpc: '2.0',
      id: request.id,
      result: {
        status: 'shutting_down'
      },
    };
  }

  /**
   * Handle ping request
   */
  private handlePing(request: JsonRpcRequest): JsonRpcResponse {
    this.logger.debug('Ping request received');

    return {
      jsonrpc: '2.0',
      id: request.id,
      result: {
        status: 'ok',
        service: 'utlx-mcp-server',
        timestamp: new Date().toISOString()
      },
    };
  }

  /**
   * Create a JSON-RPC error response
   */
  private errorResponse(
    id: string | number | null,
    code: number,
    message: string,
    data?: unknown
  ): JsonRpcResponse {
    const error: JsonRpcError = {
      code,
      message,
      data,
    };

    return {
      jsonrpc: '2.0',
      id: id || null,
      error,
    };
  }

  /**
   * Parse incoming JSON-RPC message
   */
  static parseRequest(message: string): JsonRpcRequest | JsonRpcError {
    try {
      const parsed = JSON.parse(message);

      // Validate required fields
      if (!parsed.jsonrpc || !parsed.method) {
        return {
          code: JsonRpcErrorCode.INVALID_REQUEST,
          message: 'Invalid JSON-RPC request: missing required fields',
        };
      }

      return parsed as JsonRpcRequest;
    } catch (error) {
      return {
        code: JsonRpcErrorCode.PARSE_ERROR,
        message: 'Invalid JSON',
        data: error instanceof Error ? error.message : 'Unknown parse error',
      };
    }
  }

  /**
   * Handle batch requests
   */
  async handleBatchRequest(requests: JsonRpcRequest[]): Promise<JsonRpcResponse[]> {
    this.logger.info('Handling batch JSON-RPC request', {
      count: requests.length,
    });

    const responses: JsonRpcResponse[] = [];

    for (const request of requests) {
      const response = await this.handleRequest(request);
      responses.push(response);
    }

    return responses;
  }
}
