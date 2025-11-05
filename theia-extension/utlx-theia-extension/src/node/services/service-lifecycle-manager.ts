/**
 * Service Lifecycle Manager
 *
 * Manages the lifecycle of UTLXD daemon and MCP server processes.
 * Starts services on Theia backend initialization and stops them on shutdown.
 *
 * This is essential for the Electron app where all services should be bundled
 * and managed automatically.
 */

import { spawn, ChildProcess } from 'child_process';
import { injectable, inject } from 'inversify';
import { BackendApplicationContribution } from '@theia/core/lib/node';
import { UTLXDaemonClient } from '../daemon/utlx-daemon-client';
import * as path from 'path';
import * as fs from 'fs';

export interface ServiceConfig {
    // UTLXD Configuration
    utlxdJarPath?: string;
    utlxdRestPort?: number;
    utlxdLogFile?: string;

    // MCP Server Configuration
    mcpServerPath?: string;
    mcpServerPort?: number;
    mcpServerLogFile?: string;

    // General
    autoStart?: boolean;
    shutdownTimeout?: number;
}

@injectable()
export class ServiceLifecycleManager implements BackendApplicationContribution {

    private mcpProcess: ChildProcess | null = null;
    private config: Required<ServiceConfig>;
    private isShuttingDown = false;

    private daemonClient: UTLXDaemonClient | null = null;

    constructor() {
        console.log('[ServiceLifecycle] ===== CONSTRUCTOR CALLED =====');
        console.log('[ServiceLifecycle] About to load config...');
        try {
            this.config = this.loadConfig();
            console.log('[ServiceLifecycle] ===== CONFIG LOADED SUCCESSFULLY =====');
            console.log('[ServiceLifecycle] Config:', JSON.stringify(this.config, null, 2));
        } catch (error) {
            console.error('[ServiceLifecycle] ===== ERROR IN CONSTRUCTOR =====');
            console.error('[ServiceLifecycle] Error:', error);
            console.error('[ServiceLifecycle] Stack:', (error as Error).stack);
            // Don't throw - let's see if this helps
            this.config = {
                utlxdJarPath: '/tmp/fake.jar',
                utlxdRestPort: 7779,
                utlxdLogFile: '/tmp/ut lxd.log',
                mcpServerPath: '/tmp/fake.js',
                mcpServerPort: 3001,
                mcpServerLogFile: '/tmp/mcp.log',
                autoStart: false,
                shutdownTimeout: 5000
            };
            console.log('[ServiceLifecycle] Using fallback config');
        }
        console.log('[ServiceLifecycle] ===== CONSTRUCTOR COMPLETE =====');
    }

    /**
     * Load configuration with defaults
     */
    private loadConfig(): Required<ServiceConfig> {
        // Try to find paths relative to project root
        const projectRoot = this.findProjectRoot();
        console.log('[ServiceLifecycle] Project root:', projectRoot);

        return {
            // UTLXD defaults - projectRoot should now be the utl-x repository root
            utlxdJarPath: process.env.UTLXD_JAR_PATH ||
                path.join(projectRoot, 'modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar'),
            utlxdRestPort: parseInt(process.env.UTLXD_REST_PORT || '7779', 10),
            utlxdLogFile: process.env.UTLXD_LOG_FILE || '/tmp/utlxd-theia.log',

            // MCP Server defaults - projectRoot should now be the utl-x repository root
            mcpServerPath: process.env.MCP_SERVER_PATH ||
                path.join(projectRoot, 'mcp-server/dist/index.js'),
            mcpServerPort: parseInt(process.env.MCP_SERVER_PORT || '3001', 10),
            mcpServerLogFile: process.env.MCP_SERVER_LOG_FILE || '/tmp/mcp-server-theia.log',

            // General
            autoStart: process.env.AUTO_START_SERVICES !== 'false',
            shutdownTimeout: parseInt(process.env.SERVICE_SHUTDOWN_TIMEOUT || '5000', 10)
        };
    }

    /**
     * Find project root by looking for package.json
     * For development, finds the actual utl-x repository root
     */
    private findProjectRoot(): string {
        // First, try to find utlx-theia-extension package
        let dir = __dirname;
        let extensionRoot: string | null = null;

        while (dir !== '/') {
            const packagePath = path.join(dir, 'package.json');
            if (fs.existsSync(packagePath)) {
                const pkg = JSON.parse(fs.readFileSync(packagePath, 'utf-8'));
                if (pkg.name === 'utlx-theia-extension') {
                    extensionRoot = dir;
                    break;
                }
            }
            dir = path.dirname(dir);
        }

        // If we found the extension, look for the actual utl-x repository root
        // by going up and checking for modules/server directory
        if (extensionRoot) {
            dir = extensionRoot;
            while (dir !== '/') {
                const modulesPath = path.join(dir, 'modules/server/build/libs');
                if (fs.existsSync(modulesPath)) {
                    console.log('[ServiceLifecycle] Found utl-x repository root:', dir);
                    return dir;
                }
                dir = path.dirname(dir);
            }
        }

        console.log('[ServiceLifecycle] Could not find utl-x repo root, using extension root');
        return extensionRoot || __dirname;
    }

    /**
     * Initialize services on backend startup
     */
    async initialize(): Promise<void> {
        console.log('[ServiceLifecycle] initialize() called');

        if (!this.config.autoStart) {
            console.log('[ServiceLifecycle] Auto-start disabled (AUTO_START_SERVICES=false)');
            return;
        }

        console.log('[ServiceLifecycle] Starting services...');

        try {
            // Start UTLXD first
            console.log('[ServiceLifecycle] Step 1: Starting UTLXD...');
            await this.startUTLXD();
            console.log('[ServiceLifecycle] Step 1: UTLXD started successfully');

            // Then start MCP server (depends on UTLXD)
            console.log('[ServiceLifecycle] Step 2: Starting MCP server...');
            await this.startMCPServer();
            console.log('[ServiceLifecycle] Step 2: MCP server started successfully');

            console.log('[ServiceLifecycle] ✓ All services started successfully');
        } catch (error) {
            console.error('[ServiceLifecycle] ✗ Failed to start services:', error);
            // Don't throw - let Theia start even if services fail
            console.error('[ServiceLifecycle] Continuing without managed services');
        }
    }

    /**
     * Start UTLXD daemon
     */
    private async startUTLXD(): Promise<void> {
        console.log('[ServiceLifecycle] Starting UTLXD daemon...');

        // Check if jar exists
        if (!fs.existsSync(this.config.utlxdJarPath)) {
            throw new Error(`UTLXD jar not found at: ${this.config.utlxdJarPath}`);
        }

        // Spawn UTLXD directly without daemon client for now
        {
            // Instead of using the daemon client's spawn, we'll start it externally
            // and let the daemon client connect to it

            console.log(`[ServiceLifecycle] Spawning: java -jar ${this.config.utlxdJarPath}`);

            const utlxdProcess = spawn('java', [
                '-jar',
                this.config.utlxdJarPath,
                'start',
                '--daemon-lsp',
                '--daemon-rest',
                '--daemon-rest-port', this.config.utlxdRestPort.toString()
            ], {
                stdio: ['ignore', 'pipe', 'pipe'],
                detached: false
            });

            utlxdProcess.stdout?.on('data', (data) => {
                console.log('[UTLXD]:', data.toString().trim());
            });

            utlxdProcess.stderr?.on('data', (data) => {
                console.error('[UTLXD Error]:', data.toString().trim());
            });

            utlxdProcess.on('exit', (code, signal) => {
                console.log(`[UTLXD] Process exited: code=${code}, signal=${signal}`);
            });

            // Wait for UTLXD to be ready
            await this.waitForUTLXD();
        }

        console.log('[ServiceLifecycle] UTLXD started successfully');
    }

    /**
     * Wait for UTLXD to be ready
     */
    private async waitForUTLXD(maxAttempts = 30, delayMs = 500): Promise<void> {
        for (let i = 0; i < maxAttempts; i++) {
            try {
                const response = await fetch(`http://localhost:${this.config.utlxdRestPort}/api/health`);
                if (response.ok) {
                    console.log('[ServiceLifecycle] UTLXD health check passed');
                    return;
                }
            } catch (error) {
                // Still starting, wait and retry
            }
            await new Promise(resolve => setTimeout(resolve, delayMs));
        }
        throw new Error('UTLXD failed to become ready');
    }

    /**
     * Start MCP server
     */
    private async startMCPServer(): Promise<void> {
        console.log('[ServiceLifecycle] Starting MCP server...');

        // Check if MCP server exists
        if (!fs.existsSync(this.config.mcpServerPath)) {
            throw new Error(`MCP server not found at: ${this.config.mcpServerPath}`);
        }

        return new Promise((resolve, reject) => {
            // Spawn MCP server
            this.mcpProcess = spawn('node', [this.config.mcpServerPath], {
                stdio: ['ignore', 'pipe', 'pipe'],
                env: {
                    ...process.env,
                    UTLX_DAEMON_URL: `http://localhost:${this.config.utlxdRestPort}`,
                    UTLX_MCP_TRANSPORT: 'http',
                    UTLX_MCP_PORT: this.config.mcpServerPort.toString(),
                    NODE_ENV: 'production'
                },
                detached: false
            });

            // Capture stdout
            this.mcpProcess.stdout?.on('data', (data) => {
                console.log('[MCP Server]:', data.toString().trim());
            });

            // Capture stderr
            this.mcpProcess.stderr?.on('data', (data) => {
                console.error('[MCP Server Error]:', data.toString().trim());
            });

            // Handle exit
            this.mcpProcess.on('exit', (code, signal) => {
                console.log(`[MCP Server] Process exited: code=${code}, signal=${signal}`);
                if (!this.isShuttingDown && code !== 0) {
                    console.error('[MCP Server] Unexpected exit, attempting restart...');
                    // TODO: Implement restart logic
                }
            });

            // Handle spawn errors
            this.mcpProcess.on('error', (error) => {
                console.error('[MCP Server] Spawn error:', error);
                reject(error);
            });

            // Wait for MCP server to be ready
            this.waitForMCPServer()
                .then(() => {
                    console.log('[ServiceLifecycle] MCP server started successfully');
                    resolve();
                })
                .catch(reject);
        });
    }

    /**
     * Wait for MCP server to be ready
     */
    private async waitForMCPServer(maxAttempts = 30, delayMs = 500): Promise<void> {
        for (let i = 0; i < maxAttempts; i++) {
            try {
                const response = await fetch(`http://localhost:${this.config.mcpServerPort}`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        jsonrpc: '2.0',
                        id: 1,
                        method: 'ping',
                        params: {}
                    })
                });
                if (response.ok) {
                    console.log('[ServiceLifecycle] MCP server health check passed');
                    return;
                }
            } catch (error) {
                // Still starting, wait and retry
            }
            await new Promise(resolve => setTimeout(resolve, delayMs));
        }
        throw new Error('MCP server failed to become ready');
    }

    /**
     * Called when backend is stopping
     */
    async onStop(): Promise<void> {
        this.isShuttingDown = true;
        console.log('[ServiceLifecycle] Stopping services...');

        try {
            // Stop MCP server first
            if (this.mcpProcess) {
                await this.stopMCPServer();
            }

            // TODO: Stop UTLXD process (need to track the process)

            console.log('[ServiceLifecycle] All services stopped');
        } catch (error) {
            console.error('[ServiceLifecycle] Error stopping services:', error);
        }
    }

    /**
     * Stop MCP server
     */
    private async stopMCPServer(): Promise<void> {
        if (!this.mcpProcess) {
            return;
        }

        return new Promise((resolve) => {
            const timeout = setTimeout(() => {
                console.warn('[MCP Server] Did not stop gracefully, forcing kill');
                this.mcpProcess?.kill('SIGKILL');
                resolve();
            }, this.config.shutdownTimeout);

            this.mcpProcess!.once('exit', () => {
                clearTimeout(timeout);
                this.mcpProcess = null;
                resolve();
            });

            // Try graceful shutdown
            this.mcpProcess!.kill('SIGTERM');
        });
    }

    /**
     * Check if services are running
     */
    areServicesRunning(): boolean {
        return this.mcpProcess !== null;  // TODO: Track UTLXD process
    }

    /**
     * Get service status
     */
    getStatus() {
        return {
            utlxd: {
                running: false,  // TODO: Track UTLXD process
                port: this.config.utlxdRestPort,
                jarPath: this.config.utlxdJarPath
            },
            mcpServer: {
                running: this.mcpProcess !== null,
                port: this.config.mcpServerPort,
                path: this.config.mcpServerPath
            }
        };
    }
}
