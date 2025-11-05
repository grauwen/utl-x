/**
 * UTL-X Language Client Contribution
 *
 * Integrates UTL-X Language Server (provided by UTLXD) with Theia's LSP infrastructure.
 */

import { injectable, inject } from 'inversify';
import { MessageService, CommandRegistry } from '@theia/core';
import {
    BaseLanguageClientContribution,
    Workspace,
    Languages,
    LanguageClientFactory,
    ILanguageClient
} from '@theia/languages/lib/browser';
import { SemanticHighlightingService } from '@theia/editor/lib/browser/semantic-highlight/semantic-highlighting-service';
import { UTLXService } from '../../common/protocol';

@injectable()
export class UTLXLanguageClientContribution extends BaseLanguageClientContribution {

    readonly id = 'utlx';
    readonly name = 'UTL-X';

    @inject(UTLXService)
    protected readonly utlxService!: UTLXService;

    @inject(MessageService)
    protected readonly messageService!: MessageService;

    constructor(
        @inject(Workspace) protected readonly workspace: Workspace,
        @inject(Languages) protected readonly languages: Languages,
        @inject(LanguageClientFactory) protected readonly languageClientFactory: LanguageClientFactory,
        @inject(SemanticHighlightingService) protected readonly semanticHighlightingService: SemanticHighlightingService
    ) {
        super(workspace, languages, languageClientFactory);
    }

    protected get globPatterns(): string[] {
        return ['**/*.utlx'];
    }

    protected get documentSelector(): string[] | undefined {
        return ['utlx'];
    }

    /**
     * Get workspace configuration section
     */
    protected get workspaceContains(): string[] {
        return ['*.utlx'];
    }

    /**
     * Create language client
     */
    protected async createLanguageClient(connection: any): Promise<ILanguageClient> {
        const client = await super.createLanguageClient(connection);

        // Configure client capabilities
        client.clientOptions.initializationOptions = {
            enableTypeChecking: true,
            enableSemanticHighlighting: true,
            enableCodeLens: true
        };

        return client;
    }

    /**
     * Start language server connection
     *
     * Note: UTLXD daemon should already be running via UTLXService.
     * The LSP server is available via stdio when daemon is started with --daemon-lsp flag.
     */
    protected async startLanguageServer(): Promise<void> {
        try {
            // Check if daemon is running
            const alive = await this.utlxService.ping();
            if (!alive) {
                this.messageService.warn('UTL-X daemon is not running. LSP features will not be available.');
                return;
            }

            // The daemon is already running and provides LSP via stdio
            // No need to spawn a separate process
            console.log('UTL-X LSP server is available via daemon');
        } catch (error) {
            console.error('Failed to check daemon status:', error);
            this.messageService.error('Failed to connect to UTL-X daemon');
        }
    }

    /**
     * Register custom LSP commands
     */
    registerCommand(registry: CommandRegistry): void {
        // Custom LSP commands can be registered here
        registry.registerCommand({
            id: 'utlx.lsp.restart',
            label: 'UTL-X: Restart Language Server'
        }, {
            execute: async () => {
                try {
                    await this.restart();
                    this.messageService.info('UTL-X Language Server restarted');
                } catch (error) {
                    this.messageService.error(`Failed to restart language server: ${error}`);
                }
            }
        });
    }

    /**
     * Activate language client
     */
    async activate(): Promise<void> {
        try {
            await super.activate();
            console.log('UTL-X language client activated');
        } catch (error) {
            console.error('Failed to activate UTL-X language client:', error);
            this.messageService.error(`Failed to activate language features: ${error}`);
        }
    }

    /**
     * Deactivate language client
     */
    async deactivate(): Promise<void> {
        try {
            await super.deactivate();
            console.log('UTL-X language client deactivated');
        } catch (error) {
            console.error('Failed to deactivate UTL-X language client:', error);
        }
    }
}
