/**
 * UTL-X VS Code Extension
 *
 * Provides language support for UTL-X transformation language by connecting to the UTLXD LSP server.
 * The extension acts as a thin LSP client - all language intelligence is provided by the Java/Kotlin
 * UTLXD daemon running on localhost:7777.
 */

import * as vscode from 'vscode';
import {
    LanguageClient,
    LanguageClientOptions,
    StreamInfo
} from 'vscode-languageclient/node';
import * as net from 'net';

let client: LanguageClient | undefined;

/**
 * Activate the extension
 * Called when a .utlx file is opened or the extension is explicitly activated
 */
export function activate(context: vscode.ExtensionContext) {
    console.log('[UTLX] Extension activating...');

    // Get configuration
    const config = vscode.workspace.getConfiguration('utlx');
    const lspHost = config.get<string>('lsp.host', 'localhost');
    const lspPort = config.get<number>('lsp.port', 7777);

    console.log(`[UTLX] Connecting to LSP server at ${lspHost}:${lspPort}`);

    // Configure LSP client options
    const clientOptions: LanguageClientOptions = {
        // Register the server for utlx documents
        documentSelector: [
            { scheme: 'file', language: 'utlx' },
            { scheme: 'untitled', language: 'utlx' }
        ],
        synchronize: {
            // Notify the server about file configuration changes
            fileEvents: vscode.workspace.createFileSystemWatcher('**/.utlxrc')
        },
        // Output channel for LSP communication traces
        outputChannelName: 'UTL-X Language Server',
        // Trace level from configuration
        traceOutputChannel: vscode.window.createOutputChannel('UTL-X LSP Trace')
    };

    // Create LSP client with socket connection to UTLXD daemon
    client = new LanguageClient(
        'utlxLanguageServer',
        'UTL-X Language Server',
        () => {
            return new Promise<StreamInfo>((resolve, reject) => {
                const socket = net.connect({ port: lspPort, host: lspHost }, () => {
                    console.log(`[UTLX] Connected to LSP server at ${lspHost}:${lspPort}`);
                    resolve({
                        reader: socket,
                        writer: socket
                    });
                });

                socket.on('error', (error) => {
                    console.error('[UTLX] Failed to connect to LSP server:', error);
                    vscode.window.showErrorMessage(
                        `Failed to connect to UTLXD LSP server at ${lspHost}:${lspPort}. ` +
                        `Make sure the UTLXD daemon is running with LSP enabled.`
                    );
                    reject(error);
                });

                socket.on('close', () => {
                    console.log('[UTLX] Connection to LSP server closed');
                });
            });
        },
        clientOptions
    );

    // Start the client (and connect to the server)
    client.start().then(() => {
        console.log('[UTLX] Language client started successfully');
        vscode.window.showInformationMessage('UTL-X language support activated');
    }).catch((error) => {
        console.error('[UTLX] Failed to start language client:', error);
        vscode.window.showErrorMessage(`Failed to start UTL-X language client: ${error.message}`);
    });

    // Register custom commands (optional - for future extensions)
    const reloadCommand = vscode.commands.registerCommand('utlx.reload', () => {
        if (client) {
            client.stop().then(() => {
                vscode.window.showInformationMessage('UTL-X language server reloading...');
                client!.start();
            });
        }
    });

    const showStatusCommand = vscode.commands.registerCommand('utlx.showStatus', () => {
        if (client && client.isRunning()) {
            vscode.window.showInformationMessage(
                `UTL-X LSP connected to ${lspHost}:${lspPort}`
            );
        } else {
            vscode.window.showWarningMessage(
                `UTL-X LSP not connected. Make sure UTLXD daemon is running.`
            );
        }
    });

    context.subscriptions.push(reloadCommand);
    context.subscriptions.push(showStatusCommand);
}

/**
 * Deactivate the extension
 * Called when the extension is deactivated or VS Code is shutting down
 */
export function deactivate(): Thenable<void> | undefined {
    if (!client) {
        return undefined;
    }

    console.log('[UTLX] Deactivating extension, stopping language client...');
    return client.stop();
}
