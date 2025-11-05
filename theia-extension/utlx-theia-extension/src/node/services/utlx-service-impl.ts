/**
 * UTL-X Service Implementation
 *
 * Backend service that implements the UTLXService interface.
 * Manages the daemon client and provides a clean API for the frontend.
 */

import { injectable, inject, postConstruct } from 'inversify';
import {
    UTLXService,
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
    ModeConfiguration,
    UTLXMode
} from '../../common/protocol';
import { UTLXDaemonClient } from '../daemon/utlx-daemon-client';

@injectable()
export class UTLXServiceImpl implements UTLXService {
    @inject(UTLXDaemonClient)
    private readonly daemonClient!: UTLXDaemonClient;

    private currentMode: ModeConfiguration = {
        mode: UTLXMode.RUNTIME,
        autoInferSchema: false,
        enableTypeChecking: true
    };

    @postConstruct()
    protected async init(): Promise<void> {
        try {
            console.log('Starting UTL-X daemon...');
            await this.daemonClient.start();
            console.log('UTL-X daemon started successfully');

            // Set up event handlers
            this.daemonClient.on('stopped', (code, signal) => {
                console.warn(`Daemon stopped unexpectedly: code=${code}, signal=${signal}`);
                // TODO: Notify frontend about daemon restart
            });

            this.daemonClient.on('error', (error) => {
                console.error('Daemon error:', error);
                // TODO: Notify frontend about daemon errors
            });

        } catch (error) {
            console.error('Failed to start UTL-X daemon:', error);
            throw error;
        }
    }

    /**
     * Parse UTL-X source code
     */
    async parse(source: string, documentId?: string): Promise<ParseResult> {
        try {
            return await this.daemonClient.parse(source, documentId);
        } catch (error) {
            console.error('Parse error:', error);
            throw new Error(`Parse failed: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    /**
     * Validate UTL-X source code
     */
    async validate(source: string): Promise<ValidationResult> {
        try {
            return await this.daemonClient.validate(source);
        } catch (error) {
            console.error('Validation error:', error);
            throw new Error(`Validation failed: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    /**
     * Execute transformation (runtime mode)
     */
    async execute(source: string, inputs: InputDocument[]): Promise<ExecutionResult> {
        if (this.currentMode.mode !== UTLXMode.RUNTIME) {
            return {
                success: false,
                error: 'Execute is only available in Runtime mode. Switch to Runtime mode to execute transformations.'
            };
        }

        try {
            return await this.daemonClient.execute(source, inputs);
        } catch (error) {
            console.error('Execution error:', error);
            return {
                success: false,
                error: error instanceof Error ? error.message : String(error)
            };
        }
    }

    /**
     * Infer output schema (design-time mode)
     */
    async inferSchema(source: string, inputSchema?: SchemaDocument): Promise<SchemaInferenceResult> {
        if (this.currentMode.mode !== UTLXMode.DESIGN_TIME) {
            return {
                success: false,
                typeErrors: [{
                    severity: 1, // Error
                    message: 'Schema inference is only available in Design-Time mode',
                    range: { start: { line: 0, column: 0 }, end: { line: 0, column: 0 } }
                }]
            };
        }

        try {
            const schema = this.currentMode.inputSchema || inputSchema;
            return await this.daemonClient.inferSchema(source, schema);
        } catch (error) {
            console.error('Schema inference error:', error);
            return {
                success: false,
                typeErrors: [{
                    severity: 1,
                    message: error instanceof Error ? error.message : String(error),
                    range: { start: { line: 0, column: 0 }, end: { line: 0, column: 0 } }
                }]
            };
        }
    }

    /**
     * Get hover information at position
     */
    async getHover(source: string, position: Position): Promise<HoverInfo | null> {
        try {
            return await this.daemonClient.getHover(source, position);
        } catch (error) {
            console.error('Hover error:', error);
            return null;
        }
    }

    /**
     * Get completion suggestions at position
     */
    async getCompletions(source: string, position: Position): Promise<CompletionItem[]> {
        try {
            return await this.daemonClient.getCompletions(source, position);
        } catch (error) {
            console.error('Completions error:', error);
            return [];
        }
    }

    /**
     * Get available standard library functions
     */
    async getFunctions(): Promise<FunctionInfo[]> {
        try {
            return await this.daemonClient.getFunctions();
        } catch (error) {
            console.error('Get functions error:', error);
            return [];
        }
    }

    /**
     * Set mode configuration
     */
    async setMode(config: ModeConfiguration): Promise<void> {
        this.currentMode = { ...config };
        try {
            await this.daemonClient.setMode(config);
            console.log('Mode changed to:', config.mode);
        } catch (error) {
            console.error('Set mode error:', error);
            throw new Error(`Failed to set mode: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    /**
     * Get current mode configuration
     */
    async getMode(): Promise<ModeConfiguration> {
        return { ...this.currentMode };
    }

    /**
     * Ping daemon to check if alive
     */
    async ping(): Promise<boolean> {
        try {
            return await this.daemonClient.ping();
        } catch (error) {
            return false;
        }
    }

    /**
     * Restart daemon
     */
    async restart(): Promise<void> {
        console.log('Restarting daemon...');
        try {
            await this.daemonClient.stop();
            await new Promise(resolve => setTimeout(resolve, 1000)); // Wait a second
            await this.daemonClient.start();
            console.log('Daemon restarted successfully');
        } catch (error) {
            console.error('Failed to restart daemon:', error);
            throw new Error(`Daemon restart failed: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    /**
     * Dispose resources
     */
    dispose(): void {
        console.log('Disposing UTL-X service...');
        this.daemonClient.stop().catch(error => {
            console.error('Error stopping daemon during dispose:', error);
        });
    }
}
