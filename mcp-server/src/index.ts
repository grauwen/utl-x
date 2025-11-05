#!/usr/bin/env node

/**
 * UTL-X MCP Server
 *
 * Model Context Protocol server for UTL-X transformation assistance
 * Supports stdio and HTTP transports
 */

import * as readline from 'readline';
import * as http from 'http';
import { createLogger, format, transports, Logger } from 'winston';
import { DaemonClient } from './client/DaemonClient';
import { JsonRpcHandler } from './protocol/JsonRpcHandler';
import { JsonRpcRequest, JsonRpcResponse } from './types/mcp';

// Environment configuration
const DAEMON_URL = process.env.UTLX_DAEMON_URL || 'http://localhost:7779';
const TRANSPORT = process.env.UTLX_MCP_TRANSPORT || 'stdio'; // 'stdio' or 'http'
const HTTP_PORT = parseInt(process.env.UTLX_MCP_PORT || '3000', 10);
const LOG_LEVEL = process.env.UTLX_LOG_LEVEL || 'info';

/**
 * Create logger instance
 */
function createServerLogger(): Logger {
  const isStdio = TRANSPORT === 'stdio';

  return createLogger({
    level: LOG_LEVEL,
    format: format.combine(
      format.timestamp(),
      format.errors({ stack: true }),
      format.json()
    ),
    transports: [
      // Log to stderr for stdio transport (stdout is for JSON-RPC)
      // Log to file for HTTP transport to avoid interference
      isStdio
        ? new transports.Stream({
            stream: process.stderr,
          })
        : new transports.File({
            filename: 'mcp-server.log',
          }),
    ],
  });
}

/**
 * Start server with stdio transport
 */
async function startStdioServer(
  handler: JsonRpcHandler,
  logger: Logger
): Promise<void> {
  logger.info('Starting MCP server with stdio transport');

  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: false,
  });

  rl.on('line', async (line) => {
    try {
      // Parse incoming JSON-RPC request
      const parsed = JsonRpcHandler.parseRequest(line);

      // Check if parse error
      if ('code' in parsed) {
        const errorResponse: JsonRpcResponse = {
          jsonrpc: '2.0',
          id: null,
          error: parsed,
        };
        process.stdout.write(JSON.stringify(errorResponse) + '\n');
        return;
      }

      const request = parsed as JsonRpcRequest;

      // Handle request
      const response = await handler.handleRequest(request);

      // Send response to stdout
      process.stdout.write(JSON.stringify(response) + '\n');
    } catch (error) {
      logger.error('Error processing stdin line', { error, line });

      const errorResponse: JsonRpcResponse = {
        jsonrpc: '2.0',
        id: null,
        error: {
          code: -32603,
          message: 'Internal error',
          data: error instanceof Error ? error.message : 'Unknown error',
        },
      };
      process.stdout.write(JSON.stringify(errorResponse) + '\n');
    }
  });

  rl.on('close', () => {
    logger.info('Stdio transport closed');
    process.exit(0);
  });

  // Handle process signals
  process.on('SIGINT', () => {
    logger.info('Received SIGINT, shutting down');
    process.exit(0);
  });

  process.on('SIGTERM', () => {
    logger.info('Received SIGTERM, shutting down');
    process.exit(0);
  });
}

/**
 * Start server with HTTP transport
 */
async function startHttpServer(
  handler: JsonRpcHandler,
  logger: Logger
): Promise<void> {
  logger.info('Starting MCP server with HTTP transport', { port: HTTP_PORT });

  const server = http.createServer(async (req, res) => {
    // Set CORS headers
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    // Handle preflight
    if (req.method === 'OPTIONS') {
      res.writeHead(204);
      res.end();
      return;
    }

    // Only accept POST requests
    if (req.method !== 'POST') {
      res.writeHead(405, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Method not allowed' }));
      return;
    }

    // Read request body
    let body = '';
    req.on('data', (chunk) => {
      body += chunk.toString();
    });

    req.on('end', async () => {
      try {
        // Parse incoming JSON-RPC request
        const parsed = JsonRpcHandler.parseRequest(body);

        // Check if parse error
        if ('code' in parsed) {
          const errorResponse: JsonRpcResponse = {
            jsonrpc: '2.0',
            id: null,
            error: parsed,
          };
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify(errorResponse));
          return;
        }

        const request = parsed as JsonRpcRequest;

        // Handle request
        const response = await handler.handleRequest(request);

        // Send response
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(response));
      } catch (error) {
        logger.error('Error processing HTTP request', { error, body });

        const errorResponse: JsonRpcResponse = {
          jsonrpc: '2.0',
          id: null,
          error: {
            code: -32603,
            message: 'Internal error',
            data: error instanceof Error ? error.message : 'Unknown error',
          },
        };
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(errorResponse));
      }
    });
  });

  server.listen(HTTP_PORT, () => {
    logger.info(`HTTP server listening on port ${HTTP_PORT}`);
  });

  // Handle process signals
  process.on('SIGINT', () => {
    logger.info('Received SIGINT, shutting down');
    server.close(() => {
      process.exit(0);
    });
  });

  process.on('SIGTERM', () => {
    logger.info('Received SIGTERM, shutting down');
    server.close(() => {
      process.exit(0);
    });
  });
}

/**
 * Main entry point
 */
async function main(): Promise<void> {
  // Create logger
  const logger = createServerLogger();

  logger.info('Initializing UTL-X MCP Server', {
    daemonUrl: DAEMON_URL,
    transport: TRANSPORT,
    httpPort: HTTP_PORT,
    logLevel: LOG_LEVEL,
  });

  try {
    // Create daemon client
    const daemonClient = new DaemonClient(
      {
        baseUrl: DAEMON_URL,
        timeout: 30000,
        retries: 3,
        retryDelay: 1000,
      },
      logger
    );

    // Check daemon health
    try {
      const health = await daemonClient.health();
      logger.info('Daemon health check successful', { health });
    } catch (error) {
      logger.warn('Daemon health check failed - server will start but tools may not work', {
        error: error instanceof Error ? error.message : 'Unknown error',
        daemonUrl: DAEMON_URL,
      });
    }

    // Create JSON-RPC handler
    const handler = new JsonRpcHandler(daemonClient, logger);

    // Start server with appropriate transport
    if (TRANSPORT === 'stdio') {
      await startStdioServer(handler, logger);
    } else if (TRANSPORT === 'http') {
      await startHttpServer(handler, logger);
    } else {
      logger.error('Invalid transport specified', { transport: TRANSPORT });
      process.exit(1);
    }
  } catch (error) {
    logger.error('Fatal error starting server', { error });
    process.exit(1);
  }
}

// Run main
main();
