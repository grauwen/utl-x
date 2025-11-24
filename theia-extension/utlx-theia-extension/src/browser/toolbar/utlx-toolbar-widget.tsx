/**
 * UTLX Toolbar Widget
 *
 * Top toolbar for:
 * - Mode switching (Design-Time ↔ Runtime)
 * - Current mode indicator
 * - MCP prompt for AI assistance
 */

import * as React from 'react';
import { injectable, inject, postConstruct } from 'inversify';
import { ReactWidget } from '@theia/core/lib/browser/widgets/react-widget';
import { MessageService } from '@theia/core';
import { ApplicationShell } from '@theia/core/lib/browser';
import {
    UTLXMode,
    UTLXService,
    UTLX_SERVICE_SYMBOL,
    GenerateUtlxRequest
} from '../../common/protocol';
import { UTLXEventService } from '../events/utlx-event-service';
import { MultiInputPanelWidget } from '../input-panel/multi-input-panel-widget';
import { UTLXEditorWidget } from '../editor/utlx-editor-widget';

export interface ToolbarState {
    currentMode: UTLXMode;
    showMCPDialog: boolean;
    mcpPrompt: string;
    mcpLoading: boolean;
    mcpProgressMessage: string;
    mcpProgressPercent: number;  // 0-100
    mcpDialogMode: 'prompt' | 'preview';  // 'prompt' = input, 'preview' = show result
    generatedCode: string;  // The generated code to preview
    systemStatus: {
        mcpServer: boolean | null;  // null = checking, true = ok, false = error
        utlxd: boolean | null;
        llm: boolean | null;
        llmProvider?: string;
        llmModel?: string;
    };
}

@injectable()
export class UTLXToolbarWidget extends ReactWidget {
    static readonly ID = 'utlx-toolbar';
    static readonly LABEL = 'UTLX Toolbar';

    @inject(MessageService)
    protected readonly messageService!: MessageService;

    @inject(UTLXEventService)
    protected readonly eventService!: UTLXEventService;

    @inject(UTLX_SERVICE_SYMBOL)
    protected readonly utlxService!: UTLXService;

    @inject(ApplicationShell)
    protected readonly shell!: ApplicationShell;

    private state: ToolbarState = {
        currentMode: UTLXMode.RUNTIME,
        showMCPDialog: false,
        mcpPrompt: '',
        mcpLoading: false,
        mcpProgressMessage: '',
        mcpProgressPercent: 0,
        mcpDialogMode: 'prompt',
        generatedCode: '',
        systemStatus: {
            mcpServer: null,
            utlxd: null,
            llm: null,
            llmProvider: undefined,
            llmModel: undefined
        }
    };

    constructor() {
        super();
        this.id = UTLXToolbarWidget.ID;
        this.title.label = UTLXToolbarWidget.LABEL;
        this.title.closable = false;
        this.addClass('utlx-toolbar');
    }

    @postConstruct()
    protected init(): void {
        this.update();
        // Mode will be loaded from other widgets via events if needed
    }

    protected render(): React.ReactNode {
        const { currentMode, showMCPDialog, mcpPrompt, mcpLoading } = this.state;

        return (
            <div className='utlx-toolbar-container'>
                {/* Left section: Mode indicator and switcher */}
                <div className='utlx-toolbar-left'>
                    <div className='utlx-mode-indicator'>
                        <span className={`utlx-mode-badge ${currentMode === UTLXMode.RUNTIME ? 'runtime' : 'design-time'}`}>
                            {currentMode === UTLXMode.RUNTIME ? '▶️ Runtime Mode' : '🎨 Design-Time Mode'}
                        </span>
                    </div>

                    <div className='utlx-mode-switcher'>
                        <label className='utlx-toggle-switch'>
                            <input
                                type='checkbox'
                                checked={currentMode === UTLXMode.DESIGN_TIME}
                                onChange={() => this.toggleMode()}
                                title={`Switch to ${currentMode === UTLXMode.RUNTIME ? 'Design-Time' : 'Runtime'} mode`}
                            />
                            <span className='utlx-toggle-slider'>
                                <span className='utlx-toggle-label-left'>Runtime</span>
                                <span className='utlx-toggle-label-right'>Design</span>
                            </span>
                        </label>
                    </div>
                </div>

                {/* Center section: Info */}
                <div className='utlx-toolbar-center'>
                    <span className='utlx-mode-description'>
                        {this.getModeDescription(currentMode)}
                    </span>
                </div>

                {/* Right section: Actions */}
                <div className='utlx-toolbar-right'>
                    <button
                        className='utlx-toolbar-button utlx-mcp-button'
                        onClick={() => this.openMCPDialog()}
                        title='Ask AI to help write UTLX transformation'
                        disabled={mcpLoading}
                    >
                        🤖 AI Assist
                    </button>

                    <button
                        className='utlx-toolbar-button'
                        onClick={() => this.handleExecute()}
                        title={currentMode === UTLXMode.RUNTIME ? 'Execute transformation' : 'Evaluate transformation'}
                    >
                        {currentMode === UTLXMode.RUNTIME ? '▶️ Execute' : '🔍 Evaluate'}
                    </button>
                </div>

                {/* MCP Dialog */}
                {showMCPDialog && (
                    <div className='utlx-dialog-overlay' onClick={() => this.closeMCPDialog()}>
                        <div className='utlx-dialog' onClick={(e) => e.stopPropagation()}>
                            <div className='utlx-dialog-header'>
                                <h3>🤖 AI Assistant for UTLX</h3>
                                <div className='utlx-status-indicators'>
                                    {this.renderStatusIndicator('MCP Server', this.state.systemStatus.mcpServer)}
                                    {this.renderStatusIndicator('UTLXD', this.state.systemStatus.utlxd)}
                                    {this.renderStatusIndicator(
                                        this.state.systemStatus.llmModel
                                            ? `${this.state.systemStatus.llmProvider} (${this.state.systemStatus.llmModel})`
                                            : 'LLM',
                                        this.state.systemStatus.llm
                                    )}
                                </div>
                                <button
                                    className='utlx-dialog-close'
                                    onClick={() => this.closeMCPDialog()}
                                >
                                    ✕
                                </button>
                            </div>

                            {/* Prompt Input Mode */}
                            {this.state.mcpDialogMode === 'prompt' && (
                                <>
                                    <div className='utlx-dialog-body'>
                                        <p>Describe what transformation you want to create:</p>
                                        <textarea
                                            className='utlx-mcp-prompt-input'
                                            value={mcpPrompt}
                                            onChange={(e) => this.setState({ mcpPrompt: (e.target as HTMLTextAreaElement).value })}
                                            placeholder='Example: "Transform XML customer data to JSON, extracting name, email, and address fields"'
                                            rows={6}
                                            disabled={mcpLoading}
                                            autoFocus
                                        />
                                        {mcpLoading && this.state.mcpProgressMessage && (
                                            <div className='utlx-mcp-progress'>
                                                <div className='utlx-mcp-progress-spinner'>⏳</div>
                                                <div className='utlx-mcp-progress-message'>{this.state.mcpProgressMessage}</div>
                                            </div>
                                        )}
                                    </div>

                                    <div className='utlx-dialog-footer'>
                                        <button
                                            className='utlx-dialog-button utlx-dialog-button-secondary'
                                            onClick={() => {
                                                console.log('[UTLXToolbar] 🔘 Cancel button clicked');
                                                this.closeMCPDialog();
                                            }}
                                            disabled={mcpLoading}
                                        >
                                            Cancel
                                        </button>
                                        <button
                                            className='utlx-dialog-button utlx-dialog-button-primary'
                                            onClick={() => this.submitMCPPrompt()}
                                            disabled={mcpLoading || !mcpPrompt.trim()}
                                        >
                                            {mcpLoading ? '⏳ Generating...' : '✨ Generate UTLX'}
                                        </button>
                                    </div>
                                </>
                            )}

                            {/* Preview Mode */}
                            {this.state.mcpDialogMode === 'preview' && (
                                <>
                                    <div className='utlx-dialog-body'>
                                        <p>Review the generated UTLX transformation:</p>
                                        <textarea
                                            className='utlx-mcp-prompt-input utlx-preview-code'
                                            value={this.state.generatedCode}
                                            readOnly
                                            rows={15}
                                        />
                                    </div>

                                    <div className='utlx-dialog-footer'>
                                        <button
                                            className='utlx-dialog-button utlx-dialog-button-secondary'
                                            onClick={() => this.cancelGeneration()}
                                        >
                                            Cancel
                                        </button>
                                        <button
                                            className='utlx-dialog-button utlx-dialog-button-secondary'
                                            onClick={() => this.retryGeneration()}
                                        >
                                            🔄 Retry
                                        </button>
                                        <button
                                            className='utlx-dialog-button utlx-dialog-button-primary'
                                            onClick={() => this.applyGeneratedCode()}
                                        >
                                            ✅ Apply
                                        </button>
                                    </div>
                                </>
                            )}
                        </div>
                    </div>
                )}
            </div>
        );
    }

    private getModeDescription(mode: UTLXMode): string {
        switch (mode) {
            case UTLXMode.DESIGN_TIME:
                return 'Schema validation • Type checking • No data execution';
            case UTLXMode.RUNTIME:
                return 'Data transformation • Real execution • Performance metrics';
        }
    }

    private toggleMode(): void {
        const newMode = this.state.currentMode === UTLXMode.RUNTIME
            ? UTLXMode.DESIGN_TIME
            : UTLXMode.RUNTIME;

        this.setState({ currentMode: newMode });

        const modeName = newMode === UTLXMode.RUNTIME ? 'Runtime' : 'Design-Time';
        this.messageService.info(`✓ Switched to ${modeName} mode`);

        // Fire event for other widgets to update
        this.eventService.fireModeChanged({ mode: newMode });
    }

    private openMCPDialog(): void {
        console.log('[UTLXToolbar] 🤖 Opening MCP dialog');
        this.setState({
            showMCPDialog: true,
            mcpPrompt: '',
            systemStatus: {
                mcpServer: null,
                utlxd: null,
                llm: null,
                llmProvider: undefined,
                llmModel: undefined
            }
        });
        console.log('[UTLXToolbar] ✅ MCP dialog state updated, showMCPDialog:', true);

        // Check system status asynchronously
        this.checkSystemStatus();
    }

    private async checkSystemStatus(): Promise<void> {
        console.log('[UTLXToolbar] Checking system status...');

        // Check MCP Server & LLM in parallel
        try {
            const llmStatus = await this.utlxService.checkLlmStatus();
            console.log('[UTLXToolbar] LLM status result:', llmStatus);

            // MCP Server is OK if we got a response
            const mcpServerOk = true;

            // UTLXD check - we'll assume it's OK if MCP is OK (they're tightly coupled)
            // In the future, we could add a separate UTLXD ping endpoint
            const utlxdOk = true;

            this.setState({
                systemStatus: {
                    mcpServer: mcpServerOk,
                    utlxd: utlxdOk,
                    llm: llmStatus.available,
                    llmProvider: llmStatus.provider,
                    llmModel: llmStatus.model
                }
            });
        } catch (error) {
            console.error('[UTLXToolbar] Error checking system status:', error);
            this.setState({
                systemStatus: {
                    mcpServer: false,
                    utlxd: false,
                    llm: false,
                    llmProvider: undefined,
                    llmModel: undefined
                }
            });
        }
    }

    private renderStatusIndicator(label: string, status: boolean | null): React.ReactNode {
        let icon: string;
        let className: string;

        if (status === null) {
            // Checking
            icon = '⏳';
            className = 'utlx-status-checking';
        } else if (status) {
            // OK
            icon = '✓';
            className = 'utlx-status-ok';
        } else {
            // Error
            icon = '✕';
            className = 'utlx-status-error';
        }

        return (
            <div className={`utlx-status-indicator ${className}`} title={label}>
                <span className='utlx-status-icon'>{icon}</span>
                <span className='utlx-status-label'>{label}</span>
            </div>
        );
    }

    private closeMCPDialog(): void {
        console.log('[UTLXToolbar] 🚪 Closing MCP dialog');
        this.setState({
            showMCPDialog: false,
            mcpPrompt: '',
            mcpLoading: false,
            mcpDialogMode: 'prompt',
            generatedCode: ''
        });
        console.log('[UTLXToolbar] ✅ MCP dialog state updated to closed');
    }

    private applyGeneratedCode(): void {
        console.log('[UTLXToolbar] ✅ Applying generated code to editor');

        // Get editor widget
        const editor = this.shell.getWidgets('main').find((w: any) => w instanceof UTLXEditorWidget) as UTLXEditorWidget | undefined;
        if (!editor) {
            this.messageService.error('No active UTLX editor found');
            return;
        }

        // Apply the generated code
        editor.setContent(this.state.generatedCode);

        // Fire event
        this.eventService.fireUTLXGenerated({
            prompt: this.state.mcpPrompt,
            utlx: this.state.generatedCode
        });

        this.messageService.info('✨ UTLX transformation applied to editor!');
        this.closeMCPDialog();
    }

    private retryGeneration(): void {
        console.log('[UTLXToolbar] 🔄 Retrying generation - back to prompt');
        this.setState({
            mcpDialogMode: 'prompt',
            generatedCode: '',
            mcpLoading: false
        });
    }

    private cancelGeneration(): void {
        console.log('[UTLXToolbar] ❌ Cancelling - discarding generated code');
        this.closeMCPDialog();
    }

    private async submitMCPPrompt(): Promise<void> {
        const { mcpPrompt } = this.state;

        if (!mcpPrompt.trim()) {
            return;
        }

        this.setState({ mcpLoading: true, mcpProgressMessage: 'Initializing AI assistant...' });

        try {
            // Call MCP service to generate UTLX
            this.messageService.info('🤖 Asking AI to generate UTLX transformation...');

            // Step 0: Check LLM status first
            console.log('[Toolbar] Step 0: Checking LLM availability...');
            this.setState({ mcpProgressMessage: 'Checking LLM provider...' });

            const statusResult = await this.utlxService.checkLlmStatus();
            console.log('[Toolbar] LLM status:', statusResult);

            if (!statusResult.available) {
                const errorMsg = statusResult.error || `${statusResult.provider || 'LLM'} is not available`;
                throw new Error(errorMsg);
            }

            this.setState({ mcpProgressMessage: `✓ ${statusResult.provider} is ready` });
            await new Promise(resolve => setTimeout(resolve, 500)); // Brief pause to show success

            console.log('[Toolbar] Step 1: Finding widgets...');
            this.setState({ mcpProgressMessage: 'Collecting input context...' });

            // Get input panel widget to collect context
            const inputPanel = this.shell.getWidgets('left').find((w: any) => w instanceof MultiInputPanelWidget) as MultiInputPanelWidget | undefined;
            if (!inputPanel) {
                console.error('[Toolbar] Input panel not found');
                throw new Error('Input panel not found');
            }
            console.log('[Toolbar] ✓ Input panel found');

            // Get editor widget to determine output format
            const editor = this.shell.getWidgets('main').find((w: any) => w instanceof UTLXEditorWidget) as UTLXEditorWidget | undefined;
            if (!editor) {
                console.error('[Toolbar] Editor not found');
                throw new Error('Editor not found');
            }
            console.log('[Toolbar] ✓ Editor found');

            console.log('[Toolbar] Step 2: Collecting input context...');

            // Collect input context
            const inputs = inputPanel.getAllInputTabs();
            console.log('[Toolbar] Input tabs:', inputs);

            if (inputs.length === 0) {
                throw new Error('No inputs defined. Please add at least one input.');
            }

            // Get existing editor content and extract header
            const editorContent = editor.getContent();
            const { header, body } = this.splitHeaderAndBody(editorContent);
            const outputFormat = this.extractOutputFormat(editorContent);

            console.log('[Toolbar] Step 3: Building request...');
            console.log('[Toolbar] Existing header:', header.substring(0, 100));
            console.log('[Toolbar] Generating UTLX with:', {
                prompt: mcpPrompt.substring(0, 100),
                inputCount: inputs.length,
                outputFormat
            });

            this.setState({ mcpProgressMessage: `Preparing request with ${inputs.length} input(s)...` });

            // Build the request
            const request: GenerateUtlxRequest = {
                prompt: mcpPrompt,
                inputs: inputs.map(input => {
                    // Find the full input tab to get UDM
                    const fullInput = (inputPanel as any).state?.inputs?.find((i: any) => i.id === input.id);
                    console.log('[Toolbar] Processing input:', input.name, {
                        hasFullInput: !!fullInput,
                        hasContent: !!fullInput?.instanceContent,
                        hasUDM: !!fullInput?.udmLanguage
                    });
                    return {
                        name: input.name,
                        format: input.format,
                        originalData: fullInput?.instanceContent,
                        udm: fullInput?.udmLanguage
                    };
                }),
                outputFormat
            };

            console.log('[Toolbar] Step 4: Calling backend service...');
            this.setState({ mcpProgressMessage: 'Initializing AI assistant...', mcpProgressPercent: 10 });

            // Simulate progress updates to match MCP server steps
            const progressInterval = setInterval(() => {
                const currentPercent = this.state.mcpProgressPercent;
                if (currentPercent < 20) {
                    this.setState({ mcpProgressMessage: 'Collecting UTLX function and operator context...', mcpProgressPercent: 20 });
                } else if (currentPercent < 40) {
                    this.setState({ mcpProgressMessage: `Calling ${this.state.systemStatus.llmProvider || 'LLM'} to generate transformation...`, mcpProgressPercent: 40 });
                } else if (currentPercent < 80) {
                    this.setState({ mcpProgressMessage: 'Waiting for AI response...', mcpProgressPercent: 60 });
                }
            }, 2000); // Update every 2 seconds

            // Call the backend service
            const result = await this.utlxService.generateUtlxFromPrompt(request);

            // Clear the interval
            clearInterval(progressInterval);

            console.log('[Toolbar] Step 5: Backend returned:', result);
            this.setState({ mcpProgressMessage: 'Processing AI response and extracting clean code...', mcpProgressPercent: 80 });

            if (!result.success) {
                throw new Error(result.error || 'Generation failed');
            }

            if (!result.utlx) {
                throw new Error('No UTLX code returned');
            }

            this.setState({ mcpProgressMessage: 'Transformation generated successfully!', mcpProgressPercent: 100 });

            // ALWAYS put back the original header, no matter what AI returned
            const generatedBody = this.extractBodyFromGenerated(result.utlx);
            const finalCode = header + '---\n' + generatedBody;

            console.log('[Toolbar] Original header (preserved):', header);
            console.log('[Toolbar] Extracted body:', generatedBody);
            console.log('[Toolbar] Final code:', finalCode);

            // Show preview mode instead of directly updating editor
            this.setState({
                mcpLoading: false,
                mcpProgressMessage: '',
                mcpProgressPercent: 0,
                mcpDialogMode: 'preview',
                generatedCode: finalCode
            });
        } catch (error) {
            console.error('[Toolbar] ❌ Generate UTLX error:', error);
            console.error('[Toolbar] Error type:', error instanceof Error ? 'Error' : typeof error);
            console.error('[Toolbar] Error message:', error instanceof Error ? error.message : String(error));
            console.error('[Toolbar] Error stack:', error instanceof Error ? error.stack : 'No stack trace');

            const errorMsg = error instanceof Error ? error.message : String(error);
            this.messageService.error(`Failed to generate UTLX: ${errorMsg}`);
            this.setState({ mcpLoading: false, mcpProgressMessage: '', mcpProgressPercent: 0 });
        }
    }

    private splitHeaderAndBody(editorContent: string): { header: string; body: string } {
        const separatorIndex = editorContent.indexOf('---');

        if (separatorIndex === -1) {
            // No separator found - treat entire content as header
            return {
                header: editorContent.trim() + '\n',
                body: ''
            };
        }

        const header = editorContent.substring(0, separatorIndex).trim() + '\n';
        const body = editorContent.substring(separatorIndex + 3).trim(); // Skip '---'

        return { header, body };
    }

    private extractBodyFromGenerated(generatedCode: string): string {
        let code = generatedCode.trim();

        // Step 1: Find separator (---) and DELETE EVERYTHING BEFORE IT
        const separatorIndex = code.indexOf('---');
        if (separatorIndex !== -1) {
            // Found separator - take everything after it
            code = code.substring(separatorIndex + 3).trim();
        }
        // If no separator, assume entire response is body (LLM followed instructions)

        // Step 2: Remove markdown code blocks at start/end
        code = code.replace(/^```(?:utlx|javascript|js)?\s*/gm, '');
        code = code.replace(/\s*```\s*$/gm, '');

        // Step 3: Remove trailing explanation text (after empty line)
        const lines = code.split('\n');
        let endIndex = lines.length;

        // Find where code ends and explanation starts
        let foundEmptyLine = false;
        for (let i = 0; i < lines.length; i++) {
            const trimmed = lines[i].trim();

            if (trimmed === '') {
                foundEmptyLine = true;
                continue;
            }

            // After empty line, check if it's explanation
            if (foundEmptyLine) {
                if (trimmed.toLowerCase().startsWith('this ') ||
                    trimmed.toLowerCase().startsWith('the ') ||
                    trimmed.toLowerCase().includes('correct') ||
                    trimmed.toLowerCase().includes('will ')) {
                    // This is explanation, cut here
                    endIndex = i;
                    // Find the last non-empty line before explanation
                    while (endIndex > 0 && lines[endIndex - 1].trim() === '') {
                        endIndex--;
                    }
                    break;
                }
                foundEmptyLine = false; // Reset if not explanation
            }
        }

        return lines.slice(0, endIndex).join('\n').trim();
    }

    private extractOutputFormat(editorContent: string): string {
        // Parse the UTLX header to extract output format
        // Header format: "output format" or "output: format"
        const lines = editorContent.split('\n');
        for (const line of lines) {
            const trimmed = line.trim();
            if (trimmed.startsWith('output')) {
                // Extract format: "output json" or "output: json"
                const match = trimmed.match(/^output:?\s+(\w+)/);
                if (match) {
                    return match[1];
                }
            }
            // Stop at separator
            if (trimmed === '---') {
                break;
            }
        }
        return 'json'; // Default
    }

    private async handleExecute(): Promise<void> {
        const mode = this.state.currentMode === UTLXMode.RUNTIME ? 'execute' : 'evaluate';
        const modeLabel = this.state.currentMode === UTLXMode.RUNTIME ? 'Executing' : 'Evaluating';

        this.messageService.info(`${mode === 'execute' ? '▶️' : '🔍'} ${modeLabel} transformation...`);

        // Fire event for frontend contribution to coordinate execution
        this.eventService.fireExecuteTransformation({ mode });
    }

    private setState(partial: Partial<ToolbarState>): void {
        console.log('[UTLXToolbar] setState called with:', partial);
        const oldState = { ...this.state };
        this.state = { ...this.state, ...partial };
        console.log('[UTLXToolbar] State updated from:', oldState, 'to:', this.state);
        this.update();
        console.log('[UTLXToolbar] update() called, triggering re-render');
    }

    /**
     * Get current mode (for other widgets to query)
     */
    getCurrentMode(): UTLXMode {
        return this.state.currentMode;
    }
}
