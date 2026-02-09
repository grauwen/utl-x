/**
 * UTL-X Service Implementation
 *
 * Backend service that implements the UTLXService interface.
 * Manages the daemon client and provides a clean API for the frontend.
 */

import { injectable, inject, postConstruct, named } from 'inversify';
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
    OperatorInfo,
    ModeConfiguration,
    UTLXMode,
    ValidateUdmRequest,
    ValidateUdmResult,
    GenerateUtlxRequest,
    GenerateUtlxResponse,
    LlmStatusResponse
} from '../../common/protocol';
import { DirectiveRegistry, createEmptyDirectiveRegistry } from '../../common/usdl-types';
import { UTLXDaemonClient } from '../daemon/utlx-daemon-client';
import { MCPClient } from '../mcp/mcp-client';

@injectable()
export class UTLXServiceImpl implements UTLXService {
    @inject(UTLXDaemonClient)
    private readonly daemonClient!: UTLXDaemonClient;

    @inject(MCPClient)
    private readonly mcpClient!: MCPClient;

    private currentMode: ModeConfiguration = {
        mode: UTLXMode.RUNTIME,
        autoInferSchema: false,
        enableTypeChecking: true
    };

    // NOTE: @postConstruct removed because it's async and breaks RPC synchronous instantiation
    // The daemon is managed by ServiceLifecycleManager, no init needed here

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
        console.log('[BACKEND] ========================================');
        console.log('[BACKEND] UTLXServiceImpl.execute() CALLED');
        console.log('[BACKEND] Current mode:', this.currentMode.mode);
        console.log('[BACKEND] Source length:', source.length);
        console.log('[BACKEND] Input count:', inputs.length);
        console.log('[BACKEND] ========================================');

        if (this.currentMode.mode !== UTLXMode.RUNTIME) {
            console.warn('[BACKEND] Execution blocked - not in RUNTIME mode');
            return {
                success: false,
                error: 'Execute is only available in Runtime mode. Switch to Runtime mode to execute transformations.'
            };
        }

        try {
            console.log('[BACKEND] Calling daemonClient.execute()...');

            const result = await this.daemonClient.execute(source, inputs);

            console.log('[BACKEND] Daemon client returned:', result.success ? 'SUCCESS' : 'FAILURE');

            return result;
        } catch (error) {
            console.error('[BACKEND] Execution error:', error);
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
     * Validate if input data can be parsed to UDM
     */
    async validateUdm(request: ValidateUdmRequest): Promise<ValidateUdmResult> {
        console.log('[BACKEND] ========================================');
        console.log('[BACKEND] validateUdm() called');
        console.log('[BACKEND] Request:', {
            format: request.format,
            contentLength: request.content.length,
            csvHeaders: request.csvHeaders,
            csvDelimiter: request.csvDelimiter
        });
        try {
            const result = await this.daemonClient.validateUdm(request);
            console.log('[BACKEND] Daemon client returned:', {
                success: result.success,
                error: result.error,
                hasDiagnostics: !!result.diagnostics
            });
            console.log('[BACKEND] ========================================');
            return result;
        } catch (error) {
            console.error('[BACKEND] UDM validation error:', error);
            console.error('[BACKEND] Error stack:', error instanceof Error ? error.stack : 'N/A');
            console.log('[BACKEND] ========================================');
            return {
                success: false,
                error: error instanceof Error ? error.message : String(error)
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
     * Get operators from daemon
     */
    async getOperators(): Promise<OperatorInfo[]> {
        try {
            return await this.daemonClient.getOperators();
        } catch (error) {
            console.error('Get operators error:', error);
            return [];
        }
    }

    /**
     * Get USDL directive registry from daemon
     */
    async getUsdlDirectives(): Promise<DirectiveRegistry> {
        console.log('[UTLXService] Getting USDL directives...');
        try {
            const registry = await this.daemonClient.getUsdlDirectives();
            console.log('[UTLXService] USDL directives retrieved:', {
                total: registry.totalDirectives,
                core: registry.tiers.core.length,
                common: registry.tiers.common.length,
                formatSpecific: registry.tiers.format_specific.length,
                reserved: registry.tiers.reserved.length
            });
            return registry;
        } catch (error) {
            console.error('[UTLXService] Get USDL directives error:', error);
            return createEmptyDirectiveRegistry();
        }
    }

    /**
     * Set mode configuration
     * Note: Mode is tracked locally in the service, daemon doesn't have a mode endpoint
     */
    async setMode(config: ModeConfiguration): Promise<void> {
        this.currentMode = { ...config };
        console.log('[BACKEND] Mode changed to:', config.mode);
        // Mode is only tracked locally, daemon doesn't need to know
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
     * Generate UTLX code from natural language prompt using AI
     */
    async generateUtlxFromPrompt(request: GenerateUtlxRequest): Promise<GenerateUtlxResponse> {
        console.log('[BACKEND] ========================================');
        console.log('[BACKEND] generateUtlxFromPrompt() called');
        console.log('[BACKEND] Prompt:', request.prompt.substring(0, 100));
        console.log('[BACKEND] Input count:', request.inputs.length);
        console.log('[BACKEND] Output format:', request.outputFormat);
        console.log('[BACKEND] Has original header:', !!request.originalHeader);
        console.log('[BACKEND] Original header preview:', request.originalHeader?.substring(0, 100));
        console.log('[BACKEND] ========================================');

        try {
            // Check if MCP server is available
            const isAvailable = await this.mcpClient.ping();
            if (!isAvailable) {
                console.error('[BACKEND] MCP server is not available');
                return {
                    success: false,
                    error: 'MCP server is not available. Please ensure the MCP server is running.',
                };
            }

            // Call the generate_utlx_from_prompt tool via MCP with SSE progress
            console.log('[BACKEND] Calling MCP tool: generate_utlx_from_prompt (with SSE progress)');
            const result = await this.mcpClient.callToolWithProgress(
                'generate_utlx_from_prompt',
                {
                    prompt: request.prompt,
                    inputs: request.inputs,
                    outputFormat: request.outputFormat,
                    originalHeader: request.originalHeader,
                },
                (progress, message) => {
                    console.log(`[BACKEND] Progress: ${progress}% - ${message}`);
                }
            );

            console.log('[BACKEND] MCP tool returned:', result.success ? 'SUCCESS' : 'FAILURE');
            console.log('[BACKEND] ========================================');

            return result as GenerateUtlxResponse;
        } catch (error) {
            console.error('[BACKEND] Generate UTLX error:', error);
            console.error('[BACKEND] Error stack:', error instanceof Error ? error.stack : 'N/A');
            console.log('[BACKEND] ========================================');
            return {
                success: false,
                error: error instanceof Error ? error.message : String(error),
            };
        }
    }

    /**
     * Check if LLM provider is configured and available
     */
    async checkLlmStatus(): Promise<LlmStatusResponse> {
        console.log('[BACKEND] checkLlmStatus() called');

        try {
            // Check if MCP server is available
            const isAvailable = await this.mcpClient.ping();
            if (!isAvailable) {
                console.error('[BACKEND] MCP server is not available');
                return {
                    configured: false,
                    available: false,
                    error: 'MCP server is not available. Please ensure the MCP server is running.',
                };
            }

            // Call the check_llm_status tool via MCP
            console.log('[BACKEND] Calling MCP tool: check_llm_status');
            const result = await this.mcpClient.callTool('check_llm_status', {});

            console.log('[BACKEND] LLM status:', result);

            return result as LlmStatusResponse;
        } catch (error) {
            console.error('[BACKEND] Check LLM status error:', error);
            return {
                configured: false,
                available: false,
                error: error instanceof Error ? error.message : String(error),
            };
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
