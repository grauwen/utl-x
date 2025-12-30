/**
 * Playwright Browser Client
 * Manages connection to running browser and collects console logs, errors, and network activity
 */

import { chromium, Browser, Page, ConsoleMessage as PWConsoleMessage, Request, Response } from 'playwright';
import { ConsoleMessage, ErrorLog, NetworkLog } from '../types/mcp.js';

export interface PlaywrightClientConfig {
  cdpUrl?: string;
  logBufferSize?: number;
  screenshotPath?: string;
}

export class PlaywrightClient {
  private browser?: Browser;
  private page?: Page;
  private consoleLogs: ConsoleMessage[] = [];
  private errorLogs: ErrorLog[] = [];
  private networkLogs: NetworkLog[] = [];
  private config: Required<PlaywrightClientConfig>;
  private connected: boolean = false;

  constructor(config: PlaywrightClientConfig = {}) {
    this.config = {
      cdpUrl: config.cdpUrl || 'http://localhost:9222',
      logBufferSize: config.logBufferSize || 500,
      screenshotPath: config.screenshotPath || './screenshots',
    };
  }

  /**
   * Connect to existing browser via Chrome DevTools Protocol
   */
  async connect(): Promise<void> {
    try {
      console.error('[PlaywrightClient] Connecting to browser at', this.config.cdpUrl);

      // Connect to existing browser
      this.browser = await chromium.connectOverCDP(this.config.cdpUrl);

      // Get the first context and page
      const contexts = this.browser.contexts();
      if (contexts.length === 0) {
        throw new Error('No browser contexts found');
      }

      const pages = contexts[0].pages();
      if (pages.length === 0) {
        throw new Error('No pages found in browser context');
      }

      this.page = pages[0];
      this.setupEventListeners();
      this.connected = true;

      console.error('[PlaywrightClient] Connected successfully to', await this.page.url());
    } catch (error) {
      console.error('[PlaywrightClient] Failed to connect:', error);
      throw new Error(`Failed to connect to browser: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  /**
   * Set up event listeners for console, errors, and network
   */
  private setupEventListeners(): void {
    if (!this.page) return;

    // Console messages
    this.page.on('console', (msg: PWConsoleMessage) => {
      this.handleConsoleMessage(msg);
    });

    // Page errors (uncaught exceptions)
    this.page.on('pageerror', (error: Error) => {
      this.handlePageError(error);
    });

    // Network requests
    this.page.on('request', (request: Request) => {
      this.handleRequest(request);
    });

    // Network responses
    this.page.on('response', (response: Response) => {
      this.handleResponse(response);
    });

    console.error('[PlaywrightClient] Event listeners set up');
  }

  /**
   * Handle console message
   */
  private handleConsoleMessage(msg: PWConsoleMessage): void {
    const consoleLog: ConsoleMessage = {
      timestamp: new Date().toISOString(),
      level: msg.type() as ConsoleMessage['level'],
      message: msg.text(),
      location: msg.location() ? {
        url: msg.location().url,
        lineNumber: msg.location().lineNumber,
        columnNumber: msg.location().columnNumber,
      } : undefined,
    };

    this.consoleLogs.push(consoleLog);

    // Trim buffer if needed
    if (this.consoleLogs.length > this.config.logBufferSize) {
      this.consoleLogs = this.consoleLogs.slice(-this.config.logBufferSize);
    }
  }

  /**
   * Handle page error
   */
  private handlePageError(error: Error): void {
    const errorLog: ErrorLog = {
      timestamp: new Date().toISOString(),
      message: error.message,
      stack: error.stack,
      name: error.name,
    };

    this.errorLogs.push(errorLog);

    // Trim buffer
    if (this.errorLogs.length > this.config.logBufferSize) {
      this.errorLogs = this.errorLogs.slice(-this.config.logBufferSize);
    }
  }

  /**
   * Handle network request
   */
  private handleRequest(request: Request): void {
    const networkLog: NetworkLog = {
      timestamp: new Date().toISOString(),
      method: request.method(),
      url: request.url(),
      failed: false,
      type: this.classifyRequestType(request),
    };

    // We'll update this with response data
    this.networkLogs.push(networkLog);

    // Trim buffer
    if (this.networkLogs.length > this.config.logBufferSize) {
      this.networkLogs = this.networkLogs.slice(-this.config.logBufferSize);
    }
  }

  /**
   * Handle network response
   */
  private handleResponse(response: Response): void {
    // Find the corresponding request log
    const logIndex = this.networkLogs.findIndex(
      log => log.url === response.url() && log.method === response.request().method()
    );

    if (logIndex !== -1) {
      const requestTime = new Date(this.networkLogs[logIndex].timestamp).getTime();
      const now = Date.now();

      this.networkLogs[logIndex].status = response.status();
      this.networkLogs[logIndex].statusText = response.statusText();
      this.networkLogs[logIndex].duration = now - requestTime;
      this.networkLogs[logIndex].failed = !response.ok();
    }
  }

  /**
   * Classify request type
   */
  private classifyRequestType(request: Request): NetworkLog['type'] {
    const resourceType = request.resourceType();

    if (resourceType === 'xhr') return 'xhr';
    if (resourceType === 'fetch') return 'fetch';
    if (resourceType === 'document') return 'document';
    if (resourceType === 'script') return 'script';
    if (resourceType === 'stylesheet') return 'stylesheet';
    if (resourceType === 'image') return 'image';

    return 'other';
  }

  /**
   * Get console logs with optional filtering
   */
  getConsoleLogs(options: {
    level?: ConsoleMessage['level'] | 'all';
    limit?: number;
    since?: string;
  } = {}): ConsoleMessage[] {
    let logs = [...this.consoleLogs];

    // Filter by level
    if (options.level && options.level !== 'all') {
      logs = logs.filter(log => log.level === options.level);
    }

    // Filter by timestamp
    if (options.since) {
      const sinceTime = new Date(options.since).getTime();
      logs = logs.filter(log => new Date(log.timestamp).getTime() >= sinceTime);
    }

    // Limit
    if (options.limit) {
      logs = logs.slice(-options.limit);
    }

    return logs;
  }

  /**
   * Get error logs
   */
  getErrors(options: {
    limit?: number;
    includeStackTrace?: boolean;
  } = {}): ErrorLog[] {
    let errors = [...this.errorLogs];

    if (options.limit) {
      errors = errors.slice(-options.limit);
    }

    if (!options.includeStackTrace) {
      errors = errors.map(({ stack, ...rest }) => rest);
    }

    return errors;
  }

  /**
   * Get network logs with optional filtering
   */
  getNetworkLogs(options: {
    filter?: 'all' | 'failed' | 'xhr' | 'fetch';
    limit?: number;
  } = {}): NetworkLog[] {
    let logs = [...this.networkLogs];

    // Filter by type
    if (options.filter === 'failed') {
      logs = logs.filter(log => log.failed);
    } else if (options.filter === 'xhr') {
      logs = logs.filter(log => log.type === 'xhr');
    } else if (options.filter === 'fetch') {
      logs = logs.filter(log => log.type === 'fetch');
    }

    // Limit
    if (options.limit) {
      logs = logs.slice(-options.limit);
    }

    return logs;
  }

  /**
   * Take screenshot
   */
  async takeScreenshot(options: {
    fullPage?: boolean;
    path?: string;
  } = {}): Promise<{ path?: string; base64?: string }> {
    if (!this.page) {
      throw new Error('Not connected to browser');
    }

    const screenshot = await this.page.screenshot({
      fullPage: options.fullPage || false,
      path: options.path,
      type: 'png',
    });

    return {
      path: options.path,
      base64: options.path ? undefined : screenshot.toString('base64'),
    };
  }

  /**
   * Get current page info
   */
  async getPageInfo(): Promise<{url: string; title: string}> {
    if (!this.page) {
      throw new Error('Not connected to browser');
    }

    return {
      url: this.page.url(),
      title: await this.page.title(),
    };
  }

  /**
   * Start tracing
   */
  async startTrace(outputPath: string): Promise<void> {
    if (!this.page) {
      throw new Error('Not connected to browser');
    }

    await this.page.context().tracing.start({
      screenshots: true,
      snapshots: true,
      sources: true,
    });

    console.error('[PlaywrightClient] Trace started');
  }

  /**
   * Stop tracing
   */
  async stopTrace(outputPath: string): Promise<void> {
    if (!this.page) {
      throw new Error('Not connected to browser');
    }

    await this.page.context().tracing.stop({ path: outputPath });

    console.error('[PlaywrightClient] Trace stopped and saved to', outputPath);
  }

  /**
   * Check if connected
   */
  isConnected(): boolean {
    return this.connected && this.page !== undefined;
  }

  /**
   * Disconnect
   */
  async disconnect(): Promise<void> {
    if (this.browser) {
      await this.browser.close();
      this.browser = undefined;
      this.page = undefined;
      this.connected = false;
      console.error('[PlaywrightClient] Disconnected');
    }
  }
}
