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

    // utlx-config (transform.yaml / engine.yaml): dependency-free schema lint.
    registerConfigDiagnostics(context);
}

/**
 * Lightweight, dependency-free validation for UTLX config files (`transform.yaml` / `engine.yaml`,
 * language id `utlx-config`). Line-based on purpose: it ships inside `out/extension.js` via `tsc`
 * (the .vsix excludes `node_modules` and there's no bundler), and lines give precise diagnostic
 * positions. Encodes the high-confidence rules from docs/api/config/*.schema.json:
 *   - tabs are illegal YAML indentation;
 *   - `strategy`/`defaultStrategy` ∈ {TEMPLATE, INTERPRETED, COPY, COMPILED};
 *   - `validationPolicy` ∈ {OFF, SKIP, WARN, STRICT} (transform.yaml);
 *   - unknown TOP-LEVEL keys in transform.yaml (the schema is additionalProperties:false).
 * Deeper semantic lints (messaging exactly-one-of, required input names) can layer on later.
 */
function registerConfigDiagnostics(context: vscode.ExtensionContext): void {
    const collection = vscode.languages.createDiagnosticCollection('utlx-config');
    context.subscriptions.push(collection);

    const STRATEGY = ['TEMPLATE', 'INTERPRETED', 'COPY', 'COMPILED'];
    const VALIDATION = ['OFF', 'SKIP', 'WARN', 'STRICT'];
    const TX_TOP_KEYS = ['strategy', 'validationPolicy', 'inputs', 'output', 'maxConcurrent', 'maxInputSize', 'outputBinding', 'input', 'output_messaging'];

    type Entry = { n: number; indent: number; isItem: boolean; key?: string; keyStart: number };

    const validate = (doc: vscode.TextDocument): void => {
        if (doc.languageId !== 'utlx-config') { collection.delete(doc.uri); return; }
        const base = (doc.fileName.split(/[\\/]/).pop() || '').toLowerCase();
        const isTransform = base === 'transform.yaml';
        const diags: vscode.Diagnostic[] = [];
        const lines = doc.getText().split(/\r?\n/);
        const entries: Entry[] = [];   // key lines + bare item markers, for the structural pass

        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];

            // 1) Tabs in leading whitespace — illegal YAML indentation.
            const lead = (line.match(/^[ \t]*/) || [''])[0];
            const tab = lead.indexOf('\t');
            if (tab >= 0) {
                diags.push(new vscode.Diagnostic(
                    new vscode.Range(i, tab, i, tab + 1),
                    'YAML does not allow tabs for indentation — use spaces.',
                    vscode.DiagnosticSeverity.Error));
                continue;
            }

            // Skip blank / comment-only lines (incl. the `# yaml-language-server:` modeline).
            if (!line.replace(/#.*$/, '').trim()) { continue; }

            // Bare list-item marker ("  -") — records an item boundary with no inline key.
            const bare = line.match(/^(\s*)-\s*$/);
            if (bare) { entries.push({ n: i, indent: bare[1].length, isItem: true, keyStart: bare[1].length }); continue; }

            const kv = line.match(/^(\s*)(-\s+)?([A-Za-z_][\w]*)\s*:\s*(.*?)\s*(?:#.*)?$/);
            if (!kv) { continue; }
            const indent = kv[1].length;
            const isListItem = !!kv[2];
            const key = kv[3];
            const rawVal = kv[4] || '';
            const value = rawVal.replace(/^["']|["']$/g, '');
            const keyStart = indent + (kv[2] ? kv[2].length : 0);
            const valStart = rawVal ? Math.max(keyStart, line.indexOf(rawVal, keyStart + key.length)) : keyStart;
            entries.push({ n: i, indent, isItem: isListItem, key, keyStart });

            if ((key === 'strategy' || key === 'defaultStrategy') && value && !STRATEGY.includes(value)) {
                diags.push(new vscode.Diagnostic(
                    new vscode.Range(i, valStart, i, valStart + rawVal.length),
                    `Invalid ${key} "${value}". Expected one of: ${STRATEGY.join(', ')}.`,
                    vscode.DiagnosticSeverity.Error));
            }
            if (isTransform && key === 'validationPolicy' && value && !VALIDATION.includes(value)) {
                diags.push(new vscode.Diagnostic(
                    new vscode.Range(i, valStart, i, valStart + rawVal.length),
                    `Invalid validationPolicy "${value}". Expected one of: ${VALIDATION.join(', ')}.`,
                    vscode.DiagnosticSeverity.Error));
            }
            if (isTransform && indent === 0 && !isListItem && !TX_TOP_KEYS.includes(key)) {
                diags.push(new vscode.Diagnostic(
                    new vscode.Range(i, keyStart, i, keyStart + key.length),
                    `Unknown top-level key "${key}" in transform.yaml. Allowed: ${TX_TOP_KEYS.join(', ')}.`,
                    vscode.DiagnosticSeverity.Warning));
            }
        }

        // 5/6) Structural lints (transform.yaml): every input has a name; messaging endpoints
        // declare exactly one of queue/topic/eventhub (§4b). Block = a top-level key's deeper-
        // indented children. (Inline `- name: x` is the common form; bare `-` items are handled too.)
        if (isTransform) {
            for (let idx = 0; idx < entries.length; idx++) {
                const e = entries[idx];
                if (e.indent !== 0 || e.isItem) { continue; }
                const children: Entry[] = [];
                for (let j = idx + 1; j < entries.length && entries[j].indent > e.indent; j++) { children.push(entries[j]); }

                if (e.key === 'inputs') {
                    const items: Entry[][] = [];
                    for (const c of children) {
                        if (c.isItem) { items.push([c]); }
                        else if (items.length) { items[items.length - 1].push(c); }
                    }
                    for (const item of items) {
                        if (!item.some(c => c.key === 'name')) {
                            const h = item[0];
                            diags.push(new vscode.Diagnostic(
                                new vscode.Range(h.n, h.keyStart, h.n, h.keyStart + 1),
                                'Each input under "inputs:" must declare a "name:".',
                                vscode.DiagnosticSeverity.Warning));
                        }
                    }
                } else if (e.key === 'input' || e.key === 'output_messaging') {
                    const eps = children.filter(c => c.key === 'queue' || c.key === 'topic' || c.key === 'eventhub');
                    if (eps.length !== 1) {
                        diags.push(new vscode.Diagnostic(
                            new vscode.Range(e.n, e.keyStart, e.n, e.keyStart + (e.key ? e.key.length : 0)),
                            `Messaging "${e.key}" must declare exactly one of queue / topic / eventhub (found ${eps.length}).`,
                            vscode.DiagnosticSeverity.Warning));
                    }
                }
            }
        }

        collection.set(doc.uri, diags);
    };

    context.subscriptions.push(
        vscode.workspace.onDidOpenTextDocument(validate),
        vscode.workspace.onDidChangeTextDocument(e => validate(e.document)),
        vscode.workspace.onDidCloseTextDocument(d => collection.delete(d.uri))
    );
    vscode.workspace.textDocuments.forEach(validate);
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
