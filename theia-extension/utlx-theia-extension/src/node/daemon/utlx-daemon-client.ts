/**
 * UTL-X Daemon Client
 *
 * Manages communication with the UTL-X daemon process via stdio.
 * Uses JSON-RPC 2.0 protocol for request/response communication.
 */

import { spawn, ChildProcess } from 'child_process';
import { EventEmitter } from 'events';
import { injectable } from 'inversify';
import {
    JsonRpcRequest,
    JsonRpcResponse,
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
    ModeConfiguration
} from '../../common/protocol';

/**
 * Pending request tracker
 */
interface PendingRequest {
    resolve: (result: any) => void;
    reject: (error: Error) => void;
    timeout: NodeJS.Timeout;
}

/**
 * Daemon client options
 */
export interface DaemonClientOptions {
    daemonPath?: string;
    logFile?: string;
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
    private requestId = 0;
    private pendingRequests = new Map<number, PendingRequest>();
    private buffer = '';
    private options: Required<DaemonClientOptions>;

    constructor(options: DaemonClientOptions = {}) {
        super();
        this.options = {
            daemonPath: options.daemonPath || 'utlxd',
            logFile: options.logFile || '/tmp/utlxd-theia.log',
            requestTimeout: options.requestTimeout || 30000,
            startupTimeout: options.startupTimeout || 10000
        };
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
                // Spawn daemon with LSP mode and REST API
                this.process = spawn(this.options.daemonPath, [
                    'start',
                    '--daemon-lsp',
                    '--daemon-rest',
                    '--daemon-rest-port', '7779'
                ], {
                    stdio: ['pipe', 'pipe', 'pipe']
                });

                // Set up stdout reader for JSON-RPC responses
                this.process.stdout!.on('data', (data: Buffer) => {
                    this.handleStdout(data);
                });

                // Set up stderr reader for logging
                this.process.stderr!.on('data', (data: Buffer) => {
                    const message = data.toString('utf-8');
                    console.warn('[UTLXDaemon stderr]:', message);
                    this.emit('stderr', message);
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

                // Wait for daemon to be ready
                this.ping()
                    .then(() => {
                        clearTimeout(timeout);
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
     */
    isRunning(): boolean {
        return this.process !== null && this.process.exitCode === null;
    }

    /**
     * Send JSON-RPC request to daemon
     */
    private async request(method: string, params?: any): Promise<any> {
        if (!this.isRunning()) {
            throw new Error('Daemon is not running');
        }

        return new Promise((resolve, reject) => {
            const id = ++this.requestId;
            const request: JsonRpcRequest = {
                jsonrpc: '2.0',
                id,
                method,
                params
            };

            const timeout = setTimeout(() => {
                this.pendingRequests.delete(id);
                reject(new Error(`Request ${id} (${method}) timed out after ${this.options.requestTimeout}ms`));
            }, this.options.requestTimeout);

            this.pendingRequests.set(id, {
                resolve: (result) => {
                    clearTimeout(timeout);
                    resolve(result);
                },
                reject: (error) => {
                    clearTimeout(timeout);
                    reject(error);
                },
                timeout
            });

            // Serialize to JSON and write to daemon's stdin
            const message = JSON.stringify(request) + '\n';

            this.process!.stdin!.write(message, 'utf-8', (err) => {
                if (err) {
                    clearTimeout(timeout);
                    this.pendingRequests.delete(id);
                    reject(new Error(`Failed to write to daemon: ${err.message}`));
                }
            });
        });
    }

    /**
     * Handle stdout data from daemon
     */
    private handleStdout(data: Buffer): void {
        this.buffer += data.toString('utf-8');

        // Process complete lines (newline-delimited JSON)
        let newlineIndex;
        while ((newlineIndex = this.buffer.indexOf('\n')) !== -1) {
            const line = this.buffer.substring(0, newlineIndex).trim();
            this.buffer = this.buffer.substring(newlineIndex + 1);

            if (line.length === 0) {
                continue;
            }

            try {
                const response: JsonRpcResponse = JSON.parse(line);
                this.handleResponse(response);
            } catch (error) {
                console.error('Invalid JSON from daemon:', line, error);
            }
        }
    }

    /**
     * Handle JSON-RPC response
     */
    private handleResponse(response: JsonRpcResponse): void {
        const pending = this.pendingRequests.get(response.id as number);
        if (pending) {
            if (response.error) {
                pending.reject(new Error(`${response.error.message} (code: ${response.error.code})`));
            } else {
                pending.resolve(response.result);
            }
            this.pendingRequests.delete(response.id as number);
        } else {
            console.warn('Received response for unknown request ID:', response.id);
        }
    }

    /**
     * Cleanup resources
     */
    private cleanup(): void {
        this.process = null;
        this.buffer = '';

        // Reject all pending requests
        for (const [id, pending] of this.pendingRequests.entries()) {
            clearTimeout(pending.timeout);
            pending.reject(new Error('Daemon process terminated'));
        }
        this.pendingRequests.clear();
    }

    /**
     * Ping daemon to check if alive
     */
    async ping(): Promise<boolean> {
        try {
            await this.request('ping');
            return true;
        } catch (error) {
            return false;
        }
    }

    /**
     * Parse UTL-X source
     */
    async parse(source: string, documentId?: string): Promise<ParseResult> {
        return this.request('parse', { source, documentId });
    }

    /**
     * Validate UTL-X source
     */
    async validate(source: string): Promise<ValidationResult> {
        return this.request('validate', { source });
    }

    /**
     * Execute UTL-X transformation (runtime mode)
     */
    async execute(source: string, inputs: InputDocument[]): Promise<ExecutionResult> {
        return this.request('execute', {
            source,
            inputs: inputs.map(input => ({
                id: input.id,
                name: input.name,
                content: input.content,
                format: input.format
            }))
        });
    }

    /**
     * Infer output schema (design-time mode)
     */
    async inferSchema(source: string, inputSchema?: SchemaDocument): Promise<SchemaInferenceResult> {
        return this.request('inferSchema', {
            source,
            inputSchema: inputSchema ? {
                format: inputSchema.format,
                content: inputSchema.content
            } : undefined
        });
    }

    /**
     * Get hover information
     */
    async getHover(source: string, position: Position): Promise<HoverInfo | null> {
        return this.request('hover', { source, position });
    }

    /**
     * Get completion suggestions
     */
    async getCompletions(source: string, position: Position): Promise<CompletionItem[]> {
        return this.request('completions', { source, position });
    }

    /**
     * Get standard library functions
     */
    async getFunctions(): Promise<FunctionInfo[]> {
        return this.request('getFunctions');
    }

    /**
     * Set mode configuration
     */
    async setMode(config: ModeConfiguration): Promise<void> {
        return this.request('setMode', config);
    }

    /**
     * Get current mode configuration
     */
    async getMode(): Promise<ModeConfiguration> {
        return this.request('getMode');
    }
}
