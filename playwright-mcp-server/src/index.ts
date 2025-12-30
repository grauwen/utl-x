#!/usr/bin/env node

/**
 * Playwright MCP Server
 *
 * Provides browser debugging capabilities via Model Context Protocol (MCP).
 * Connects to a running browser instance and exposes console logs, errors,
 * network activity, and screenshot capabilities to Claude/LLMs.
 */

import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from '@modelcontextprotocol/sdk/types.js';

import { PlaywrightClient } from './browser/playwright-client.js';
import { getConsoleLogsTool, handleGetConsoleLogs } from './tools/getConsoleLogs.js';
import { getErrorsTool, handleGetErrors } from './tools/getErrors.js';
import { getNetworkLogsTool, handleGetNetworkLogs } from './tools/getNetworkLogs.js';
import { takeScreenshotTool, handleTakeScreenshot } from './tools/takeScreenshot.js';
import { getPageInfoTool, handleGetPageInfo } from './tools/getPageInfo.js';
import { captureTraceTool, handleCaptureTrace } from './tools/captureTrace.js';

/**
 * Main MCP Server class
 */
class PlaywrightMCPServer {
  private server: Server;
  private client: PlaywrightClient;

  constructor() {
    this.server = new Server(
      {
        name: 'playwright-mcp-server',
        version: '1.0.0',
      },
      {
        capabilities: {
          tools: {},
        },
      }
    );

    this.client = new PlaywrightClient({
      cdpUrl: process.env.CDP_URL || 'http://localhost:9222',
      logBufferSize: parseInt(process.env.LOG_BUFFER_SIZE || '500'),
      screenshotPath: process.env.SCREENSHOT_PATH || './screenshots',
    });

    this.setupHandlers();
  }

  /**
   * Set up MCP request handlers
   */
  private setupHandlers(): void {
    // List available tools
    this.server.setRequestHandler(ListToolsRequestSchema, async () => ({
      tools: [
        getConsoleLogsTool,
        getErrorsTool,
        getNetworkLogsTool,
        takeScreenshotTool,
        getPageInfoTool,
        captureTraceTool,
      ],
    }));

    // Handle tool invocations
    this.server.setRequestHandler(CallToolRequestSchema, async (request) => {
      const { name, arguments: args } = request.params;

      console.error(`[MCP Server] Tool called: ${name}`);

      try {
        switch (name) {
          case 'get_console_logs':
            return await handleGetConsoleLogs(args || {}, this.client) as any;

          case 'get_errors':
            return await handleGetErrors(args || {}, this.client) as any;

          case 'get_network_logs':
            return await handleGetNetworkLogs(args || {}, this.client) as any;

          case 'take_screenshot':
            return await handleTakeScreenshot(args || {}, this.client) as any;

          case 'get_page_info':
            return await handleGetPageInfo(args || {}, this.client) as any;

          case 'capture_trace':
            return await handleCaptureTrace(args || {}, this.client) as any;

          default:
            throw new Error(`Unknown tool: ${name}`);
        }
      } catch (error) {
        console.error(`[MCP Server] Error handling tool ${name}:`, error);
        throw error;
      }
    });
  }

  /**
   * Start the MCP server
   */
  async start(): Promise<void> {
    console.error('[MCP Server] Starting Playwright MCP Server...');

    // Connect to browser
    try {
      await this.client.connect();
      console.error('[MCP Server] Connected to browser successfully');
    } catch (error) {
      console.error('[MCP Server] WARNING: Failed to connect to browser:', error);
      console.error('[MCP Server] Server will start anyway. Browser connection can be retried.');
    }

    // Start stdio transport
    const transport = new StdioServerTransport();
    await this.server.connect(transport);

    console.error('[MCP Server] Server started and listening on stdio');
    console.error('[MCP Server] Available tools:');
    console.error('  - get_console_logs: Get browser console logs');
    console.error('  - get_errors: Get JavaScript errors');
    console.error('  - get_network_logs: Get network activity');
    console.error('  - take_screenshot: Capture screenshots');
    console.error('  - get_page_info: Get current page info');
    console.error('  - capture_trace: Start/stop trace capture');
  }

  /**
   * Graceful shutdown
   */
  async shutdown(): Promise<void> {
    console.error('[MCP Server] Shutting down...');
    await this.client.disconnect();
    await this.server.close();
    console.error('[MCP Server] Shutdown complete');
  }
}

/**
 * Main entry point
 */
async function main(): Promise<void> {
  const server = new PlaywrightMCPServer();

  // Handle shutdown signals
  process.on('SIGINT', async () => {
    await server.shutdown();
    process.exit(0);
  });

  process.on('SIGTERM', async () => {
    await server.shutdown();
    process.exit(0);
  });

  // Start server
  await server.start();
}

// Run
main().catch((error) => {
  console.error('[MCP Server] Fatal error:', error);
  process.exit(1);
});
