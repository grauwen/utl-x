/**
 * Auto-Start Services
 *
 * This module automatically starts UTLXD and MCP Server when imported.
 * It runs immediately when the backend module loads, bypassing the
 * BackendApplicationContribution lifecycle issues.
 */

import { spawn, ChildProcess } from 'child_process';
import * as path from 'path';
import * as fs from 'fs';

interface ServiceConfig {
    utlxdJarPath: string;
    utlxdRestPort: number;
    utlxdLogFile: string;
    mcpServerPath: string;
    mcpServerPort: number;
    mcpServerLogFile: string;
    autoStart: boolean;
    shutdownTimeout: number;
}

let utlxdProcess: ChildProcess | null = null;
let mcpProcess: ChildProcess | null = null;

console.log('[AutoStart] ===== AUTO-START SERVICES MODULE LOADED =====');

/**
 * Find the utl-x repository root
 *
 * This works in two scenarios:
 * 1. Development: Extension loaded from node_modules/utlx-theia-extension
 * 2. Webpack-bundled: Code running from browser-app/lib/backend
 */
function findProjectRoot(): string {
    let dir = __dirname;

    console.log('[AutoStart] Starting directory:', dir);

    // Strategy 1: Look for modules/server/build/libs directly from current location
    // This works when webpack-bundled in browser-app/lib/backend
    let searchDir = dir;
    while (searchDir !== '/') {
        const modulesPath = path.join(searchDir, 'modules/server/build/libs');
        if (fs.existsSync(modulesPath)) {
            console.log('[AutoStart] Found utl-x repository root (direct):', searchDir);
            return searchDir;
        }
        searchDir = path.dirname(searchDir);
    }

    // Strategy 2: Find utlx-theia-extension package, then look for repo root
    // This works in development when loaded from node_modules
    searchDir = dir;
    let extensionRoot: string | null = null;

    while (searchDir !== '/') {
        const packagePath = path.join(searchDir, 'package.json');
        if (fs.existsSync(packagePath)) {
            const pkg = JSON.parse(fs.readFileSync(packagePath, 'utf-8'));
            if (pkg.name === 'utlx-theia-extension') {
                extensionRoot = searchDir;
                console.log('[AutoStart] Found extension package at:', extensionRoot);
                break;
            }
        }
        searchDir = path.dirname(searchDir);
    }

    if (extensionRoot) {
        searchDir = extensionRoot;
        while (searchDir !== '/') {
            const modulesPath = path.join(searchDir, 'modules/server/build/libs');
            if (fs.existsSync(modulesPath)) {
                console.log('[AutoStart] Found utl-x repository root (via extension):', searchDir);
                return searchDir;
            }
            searchDir = path.dirname(searchDir);
        }
    }

    console.warn('[AutoStart] Could not find utl-x repo root, using fallback');
    return extensionRoot || dir;
}

/**
 * Load configuration
 */
function loadConfig(): ServiceConfig {
    const projectRoot = findProjectRoot();
    console.log('[AutoStart] Project root:', projectRoot);

    return {
        utlxdJarPath: process.env.UTLXD_JAR_PATH ||
            path.join(projectRoot, 'modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar'),
        utlxdRestPort: parseInt(process.env.UTLXD_REST_PORT || '7779', 10),
        utlxdLogFile: process.env.UTLXD_LOG_FILE || '/tmp/utlxd-theia.log',
        mcpServerPath: process.env.MCP_SERVER_PATH ||
            path.join(projectRoot, 'mcp-server/dist/index.js'),
        mcpServerPort: parseInt(process.env.MCP_SERVER_PORT || '3001', 10),
        mcpServerLogFile: process.env.MCP_SERVER_LOG_FILE || '/tmp/mcp-server-theia.log',
        autoStart: process.env.AUTO_START_SERVICES !== 'false',
        shutdownTimeout: parseInt(process.env.SERVICE_SHUTDOWN_TIMEOUT || '5000', 10)
    };
}

/**
 * Wait for service to be ready
 */
async function waitForService(url: string, name: string, maxAttempts = 30, delayMs = 500): Promise<void> {
    for (let i = 0; i < maxAttempts; i++) {
        try {
            const response = await fetch(url);
            if (response.ok) {
                console.log(`[AutoStart] ${name} health check passed`);
                return;
            }
        } catch (error) {
            // Still starting, wait and retry
        }
        await new Promise(resolve => setTimeout(resolve, delayMs));
    }
    throw new Error(`${name} failed to become ready`);
}

/**
 * Start UTLXD daemon
 */
async function startUTLXD(config: ServiceConfig): Promise<void> {
    console.log('[AutoStart] Starting UTLXD daemon...');

    if (!fs.existsSync(config.utlxdJarPath)) {
        throw new Error(`UTLXD jar not found at: ${config.utlxdJarPath}`);
    }

    console.log(`[AutoStart] Spawning: java -jar ${config.utlxdJarPath}`);

    utlxdProcess = spawn('java', [
        '-jar',
        config.utlxdJarPath,
        'start',
        '--daemon-lsp',
        '--daemon-rest',
        '--daemon-rest-port', config.utlxdRestPort.toString()
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
    await waitForService(`http://localhost:${config.utlxdRestPort}/api/health`, 'UTLXD');
    console.log('[AutoStart] UTLXD started successfully');
}

/**
 * Start MCP server
 */
async function startMCPServer(config: ServiceConfig): Promise<void> {
    console.log('[AutoStart] Starting MCP server...');

    if (!fs.existsSync(config.mcpServerPath)) {
        throw new Error(`MCP server not found at: ${config.mcpServerPath}`);
    }

    return new Promise((resolve, reject) => {
        mcpProcess = spawn('node', [config.mcpServerPath], {
            stdio: ['ignore', 'pipe', 'pipe'],
            env: {
                ...process.env,
                UTLX_DAEMON_URL: `http://localhost:${config.utlxdRestPort}`,
                UTLX_MCP_TRANSPORT: 'http',
                UTLX_MCP_PORT: config.mcpServerPort.toString(),
                NODE_ENV: 'production'
            },
            detached: false
        });

        mcpProcess.stdout?.on('data', (data) => {
            console.log('[MCP Server]:', data.toString().trim());
        });

        mcpProcess.stderr?.on('data', (data) => {
            console.error('[MCP Server Error]:', data.toString().trim());
        });

        mcpProcess.on('exit', (code, signal) => {
            console.log(`[MCP Server] Process exited: code=${code}, signal=${signal}`);
        });

        mcpProcess.on('error', (error) => {
            console.error('[MCP Server] Spawn error:', error);
            reject(error);
        });

        // Wait for MCP server to be ready
        waitForService(`http://localhost:${config.mcpServerPort}`, 'MCP Server')
            .then(() => {
                console.log('[AutoStart] MCP server started successfully');
                resolve();
            })
            .catch(reject);
    });
}

/**
 * Auto-start services
 */
async function autoStartServices() {
    try {
        const config = loadConfig();

        if (!config.autoStart) {
            console.log('[AutoStart] Auto-start disabled (AUTO_START_SERVICES=false)');
            return;
        }

        console.log('[AutoStart] Starting services...');

        // Start UTLXD first
        console.log('[AutoStart] Step 1: Starting UTLXD...');
        await startUTLXD(config);
        console.log('[AutoStart] Step 1: UTLXD started successfully');

        // Then start MCP server
        console.log('[AutoStart] Step 2: Starting MCP server...');
        await startMCPServer(config);
        console.log('[AutoStart] Step 2: MCP server started successfully');

        console.log('[AutoStart] ===== ✓ ALL SERVICES STARTED SUCCESSFULLY =====');
    } catch (error) {
        console.error('[AutoStart] ===== ✗ FAILED TO START SERVICES =====');
        console.error('[AutoStart] Error:', error);
        console.error('[AutoStart] Continuing without managed services');
    }
}

// Graceful shutdown
process.on('SIGTERM', () => {
    console.log('[AutoStart] Received SIGTERM, stopping services...');
    if (mcpProcess) {
        mcpProcess.kill('SIGTERM');
    }
    if (utlxdProcess) {
        utlxdProcess.kill('SIGTERM');
    }
});

process.on('SIGINT', () => {
    console.log('[AutoStart] Received SIGINT, stopping services...');
    if (mcpProcess) {
        mcpProcess.kill('SIGTERM');
    }
    if (utlxdProcess) {
        utlxdProcess.kill('SIGTERM');
    }
});

// Start services immediately
autoStartServices().catch(err => {
    console.error('[AutoStart] Fatal error during service startup:', err);
});
