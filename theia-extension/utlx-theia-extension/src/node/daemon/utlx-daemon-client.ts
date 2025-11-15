/**
 * UTL-X Daemon Client
 *
 * Manages communication with the UTL-X daemon process via REST API.
 * Uses HTTP requests to communicate with the daemon on port 7779.
 */

import { spawn, ChildProcess } from 'child_process';
import { EventEmitter } from 'events';
import { injectable } from 'inversify';
import * as http from 'http';
import FormData = require('form-data');
import {
    ParseResult,
    ValidationResult,
    ExecutionResult,
    SchemaInferenceResult,
    InputDocument,
    SchemaDocument,
    Position,
    HoverInfo,
    CompletionItem,
    FunctionInfo,
    OperatorInfo,
    ModeConfiguration,
    ValidateUdmRequest,
    ValidateUdmResult
} from '../../common/protocol';

/**
 * Daemon client options
 */
export interface DaemonClientOptions {
    daemonJarPath?: string;
    lspPort?: number;
    apiPort?: number;
    requestTimeout?: number;
    startupTimeout?: number;
}

/**
 * Daemon client events
 */
export interface DaemonClientEvents {
    started: () => void;
    stopped: (code: number | null, signal: NodeJS.Signals | null) => void;
    error: (error: Error) => void;
    stderr: (message: string) => void;
}

@injectable()
export class UTLXDaemonClient extends EventEmitter {
    private process: ChildProcess | null = null;
    private options: Required<DaemonClientOptions>;
    private apiBaseUrl: string;
    private lspBaseUrl: string;

    constructor() {
        super();
        console.log('[BACKEND] UTLXDaemonClient constructor called');
        // Use default options - no parameters needed for DI
        const options: DaemonClientOptions = {};
        this.options = {
            daemonJarPath: options.daemonJarPath || '/Users/magr/data/mapping/github-git/utl-x/modules/daemon/build/libs/utlxd-1.0.0-SNAPSHOT.jar',
            lspPort: options.lspPort || 7777,
            apiPort: options.apiPort || 7779,
            requestTimeout: options.requestTimeout || 30000,
            startupTimeout: options.startupTimeout || 10000
        };
        this.apiBaseUrl = `http://localhost:${this.options.apiPort}`;
        this.lspBaseUrl = `http://localhost:${this.options.lspPort}`;
        console.log('[BACKEND] UTLXDaemonClient initialized');
    }

    /**
     * Start the daemon process
     */
    async start(): Promise<void> {
        if (this.isRunning()) {
            throw new Error('Daemon is already running');
        }

        return new Promise((resolve, reject) => {
            const timeout = setTimeout(() => {
                this.stop();
                reject(new Error(`Daemon failed to start within ${this.options.startupTimeout}ms`));
            }, this.options.startupTimeout);

            try {
                console.log('[DaemonClient] Starting daemon with LSP on port', this.options.lspPort, 'and API on port', this.options.apiPort);
                console.log('[DaemonClient] Using JAR:', this.options.daemonJarPath);

                // Spawn daemon with both LSP and API
                this.process = spawn('java', [
                    '-jar',
                    this.options.daemonJarPath,
                    'start',
                    '--lsp',
                    '--lsp-transport', 'http',
                    '--lsp-port', String(this.options.lspPort),
                    '--api',
                    '--api-port', String(this.options.apiPort)
                ], {
                    stdio: ['ignore', 'pipe', 'pipe']
                });

                // Set up stderr reader for logging
                this.process.stderr!.on('data', (data: Buffer) => {
                    const message = data.toString('utf-8');
                    console.warn('[UTLXDaemon stderr]:', message);
                    this.emit('stderr', message);
                });

                // Set up stdout reader for logging
                this.process.stdout!.on('data', (data: Buffer) => {
                    const message = data.toString('utf-8');
                    console.log('[UTLXDaemon stdout]:', message);
                });

                // Handle process exit
                this.process.on('exit', (code, signal) => {
                    console.log(`UTLXDaemon exited: code=${code}, signal=${signal}`);
                    this.cleanup();
                    this.emit('stopped', code, signal);
                });

                // Handle process errors
                this.process.on('error', (error) => {
                    console.error('UTLXDaemon process error:', error);
                    clearTimeout(timeout);
                    reject(error);
                    this.emit('error', error);
                });

                // Wait for daemon to be ready by pinging API
                this.ping()
                    .then(() => {
                        clearTimeout(timeout);
                        console.log('[DaemonClient] Daemon API ready at', this.apiBaseUrl);
                        console.log('[DaemonClient] Daemon LSP ready at', this.lspBaseUrl);
                        this.emit('started');
                        resolve();
                    })
                    .catch((error) => {
                        clearTimeout(timeout);
                        this.stop();
                        reject(error);
                    });

            } catch (error) {
                clearTimeout(timeout);
                reject(error);
            }
        });
    }

    /**
     * Stop the daemon process
     */
    async stop(timeout: number = 5000): Promise<void> {
        if (!this.isRunning()) {
            return;
        }

        return new Promise((resolve) => {
            const killTimeout = setTimeout(() => {
                if (this.process) {
                    console.warn('Daemon did not stop gracefully, forcing kill');
                    this.process.kill('SIGKILL');
                }
                resolve();
            }, timeout);

            this.process!.once('exit', () => {
                clearTimeout(killTimeout);
                resolve();
            });

            // Try graceful shutdown
            this.process!.kill('SIGTERM');
        });
    }

    /**
     * Check if daemon is running
     * Returns true if we spawned a process that's still running,
     * OR if we can successfully connect to the daemon (manual start)
     */
    isRunning(): boolean {
        // If we spawned the process, check if it's still running
        if (this.process !== null && this.process.exitCode === null) {
            return true;
        }
        // If process is null or exited, we can't definitively say it's not running
        // (it might have been started manually), so we return true to allow requests
        // The actual HTTP request will fail if daemon is truly not accessible
        return true;
    }

    /**
     * Make HTTP request to daemon REST API
     */
    private async httpRequest(endpoint: string, method: string = 'POST', body?: any): Promise<any> {
        if (!this.isRunning()) {
            throw new Error('Daemon is not running');
        }

        const url = `${this.apiBaseUrl}${endpoint}`;
        const bodyStr = body ? JSON.stringify(body) : undefined;

        console.log('[DaemonClient] ========== HTTP REQUEST ==========');
        console.log('[DaemonClient] URL:', url);
        console.log('[DaemonClient] Method:', method);
        if (bodyStr) {
            console.log('[DaemonClient] Request body:');
            console.log(bodyStr);
        }
        console.log('[DaemonClient] ==========================================');

        return new Promise((resolve, reject) => {
            const urlObj = new URL(url);
            const options: http.RequestOptions = {
                hostname: urlObj.hostname,
                port: urlObj.port,
                path: urlObj.pathname,
                method: method,
                headers: {
                    'Content-Type': 'application/json',
                    ...(bodyStr && { 'Content-Length': Buffer.byteLength(bodyStr) })
                },
                timeout: this.options.requestTimeout
            };

            const req = http.request(options, (res) => {
                let data = '';

                res.on('data', (chunk) => {
                    data += chunk;
                });

                res.on('end', () => {
                    console.log('[DaemonClient] ========== HTTP RESPONSE ==========');
                    console.log('[DaemonClient] Status:', res.statusCode);
                    console.log('[DaemonClient] Response body:');
                    console.log(data);
                    console.log('[DaemonClient] ==========================================');

                    if (res.statusCode && res.statusCode >= 200 && res.statusCode < 300) {
                        try {
                            const result = data ? JSON.parse(data) : {};
                            resolve(result);
                        } catch (error) {
                            reject(new Error(`Failed to parse response: ${error}`));
                        }
                    } else {
                        reject(new Error(`HTTP ${res.statusCode}: ${data}`));
                    }
                });
            });

            req.on('error', (error) => {
                console.error('[DaemonClient] HTTP request error:', error);
                reject(error);
            });

            req.on('timeout', () => {
                req.destroy();
                reject(new Error(`Request timed out after ${this.options.requestTimeout}ms`));
            });

            if (bodyStr) {
                req.write(bodyStr);
            }
            req.end();
        });
    }

    /**
     * Cleanup resources
     */
    private cleanup(): void {
        this.process = null;
    }

    /**
     * Ping daemon to check if alive
     */
    async ping(): Promise<boolean> {
        try {
            await this.httpRequest('/health', 'GET');
            return true;
        } catch (error) {
            return false;
        }
    }

    /**
     * Parse UTL-X source
     */
    async parse(source: string, documentId?: string): Promise<ParseResult> {
        return this.httpRequest('/parse', 'POST', { source, documentId });
    }

    /**
     * Validate UTL-X source
     */
    async validate(source: string): Promise<ValidationResult> {
        return this.httpRequest('/validate', 'POST', { source });
    }

    /**
     * Execute UTL-X transformation (runtime mode) using multipart/form-data
     */
    async execute(source: string, inputs: InputDocument[]): Promise<ExecutionResult> {
        console.log('[DaemonClient] Executing transformation via multipart REST API');
        console.log('[DaemonClient] UTLX source code (' + source.length + ' characters):');
        console.log(source);
        console.log('[DaemonClient] Input documents (' + inputs.length + ' total):');
        inputs.forEach((input, index) => {
            const encoding = input.encoding || 'UTF-8';
            const bom = input.bom || false;
            console.log('  [' + (index + 1) + '] Input "' + input.name + '" (' + input.format + ', ' + encoding + ', BOM=' + bom + '): ' + input.content.length + ' characters');
        });

        if (!this.isRunning()) {
            throw new Error('Daemon is not running');
        }

        const startTime = Date.now();

        return new Promise((resolve, reject) => {
            const form = new FormData();

            // Add UTLX source code as a form field
            form.append('utlx', source);

            // Add each input as a file part with metadata headers
            inputs.forEach((input, index) => {
                const encoding = input.encoding || 'UTF-8';
                const bom = input.bom || false;

                console.log('[DaemonClient] Adding input_' + index + ': ' + input.name + ', format=' + input.format + ', encoding=' + encoding + ', BOM=' + bom);

                // Convert string content to buffer with specified encoding
                const buffer = Buffer.from(input.content, 'utf-8'); // Content is already a string in UTF-8

                form.append('input_' + index, buffer, {
                    filename: input.name,
                    contentType: 'application/octet-stream',
                    knownLength: buffer.length,
                    header: {
                        'X-Format': input.format,
                        'X-Encoding': encoding,
                        'X-BOM': String(bom)
                    }
                });
            });

            const url = `${this.apiBaseUrl}/api/execute-multipart`;
            const urlObj = new URL(url);

            console.log('[DaemonClient] ========== MULTIPART HTTP REQUEST ==========');
            console.log('[DaemonClient] URL:', url);
            console.log('[DaemonClient] Method: POST');
            console.log('[DaemonClient] Inputs:', inputs.length);
            console.log('[DaemonClient] ==========================================');

            const options: http.RequestOptions = {
                hostname: urlObj.hostname,
                port: urlObj.port,
                path: urlObj.pathname,
                method: 'POST',
                headers: form.getHeaders(),
                timeout: this.options.requestTimeout
            };

            const req = http.request(options, (res) => {
                let data = '';

                res.on('data', (chunk) => {
                    data += chunk;
                });

                res.on('end', () => {
                    const executionTime = Date.now() - startTime;

                    console.log('[DaemonClient] ========== MULTIPART HTTP RESPONSE ==========');
                    console.log('[DaemonClient] Status:', res.statusCode);
                    console.log('[DaemonClient] Execution time:', executionTime + 'ms');
                    console.log('[DaemonClient] Response body:');
                    console.log(data);
                    console.log('[DaemonClient] ==========================================');

                    if (res.statusCode && res.statusCode >= 200 && res.statusCode < 300) {
                        try {
                            const result = data ? JSON.parse(data) : {};
                            resolve(result);
                        } catch (error) {
                            reject(new Error(`Failed to parse response: ${error}`));
                        }
                    } else {
                        reject(new Error(`HTTP ${res.statusCode}: ${data}`));
                    }
                });
            });

            req.on('error', (error) => {
                console.error('[DaemonClient] Multipart HTTP request error:', error);
                reject(error);
            });

            req.on('timeout', () => {
                req.destroy();
                reject(new Error(`Request timed out after ${this.options.requestTimeout}ms`));
            });

            // Pipe the form data to the request
            form.pipe(req);
        });
    }

    /**
     * Infer output schema (design-time mode)
     */
    async inferSchema(source: string, inputSchema?: SchemaDocument): Promise<SchemaInferenceResult> {
        return this.httpRequest('/inferSchema', 'POST', {
            source,
            inputSchema: inputSchema ? {
                format: inputSchema.format,
                content: inputSchema.content
            } : undefined
        });
    }

    /**
     * Validate if input data can be parsed to UDM
     * Uses /api/udm/export endpoint (which validates by converting to UDM)
     */
    async validateUdm(request: ValidateUdmRequest): Promise<ValidateUdmResult> {
        console.log('[DaemonClient] Validating UDM:', {
            format: request.format,
            contentLength: request.content.length,
            csvHeaders: request.csvHeaders,
            csvDelimiter: request.csvDelimiter
        });

        try {
            // Use /api/udm/export to validate - it converts source data to UDM
            // If conversion succeeds, the data is valid and we get the UDM representation
            const response = await this.httpRequest('/api/udm/export', 'POST', {
                content: request.content,
                format: request.format,
                hasHeaders: request.csvHeaders,    // Note: Kotlin uses 'hasHeaders'
                delimiter: request.csvDelimiter,   // Note: Kotlin uses 'delimiter'
                prettyPrint: false
            });

            console.log('[DaemonClient] UDM export successful, response:', {
                success: response.success,
                hasUdmLanguage: !!response.udmLanguage,
                udmLanguageLength: response.udmLanguage?.length
            });

            // If we get here, the export succeeded (data is valid)
            return {
                success: true,
                udmLanguage: response.udmLanguage
            };
        } catch (error) {
            // Export failed - data cannot be parsed
            const errorMessage = error instanceof Error ? error.message : String(error);
            console.log('[DaemonClient] UDM validation failed:', errorMessage);

            return {
                success: false,
                error: errorMessage
            };
        }
    }

    /**
     * Get hover information
     */
    async getHover(source: string, position: Position): Promise<HoverInfo | null> {
        return this.httpRequest('/hover', 'POST', { source, position });
    }

    /**
     * Get completion suggestions
     */
    async getCompletions(source: string, position: Position): Promise<CompletionItem[]> {
        return this.httpRequest('/completions', 'POST', { source, position });
    }

    /**
     * Get standard library functions
     */
    async getFunctions(): Promise<FunctionInfo[]> {
        const response = await this.httpRequest('/api/functions', 'GET');
        // The response is FunctionRegistry with {functions: FunctionInfo[], ...}
        return response.functions || [];
    }

    /**
     * Get operators registry from daemon
     */
    async getOperators(): Promise<OperatorInfo[]> {
        const response = await this.httpRequest('/api/operators', 'GET');
        // The response is OperatorRegistryData with {operators: OperatorInfo[], ...}
        return response.operators || [];
    }

    /**
     * Set mode configuration
     */
    async setMode(config: ModeConfiguration): Promise<void> {
        return this.httpRequest('/mode', 'PUT', config);
    }

    /**
     * Get current mode configuration
     */
    async getMode(): Promise<ModeConfiguration> {
        return this.httpRequest('/mode', 'GET');
    }
}
