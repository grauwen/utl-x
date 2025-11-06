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
import { Emitter } from '@theia/core';
import {
    UTLXMode
} from '../../common/protocol';

export interface ToolbarState {
    currentMode: UTLXMode;
    showMCPDialog: boolean;
    mcpPrompt: string;
    mcpLoading: boolean;
}

@injectable()
export class UTLXToolbarWidget extends ReactWidget {
    static readonly ID = 'utlx-toolbar';
    static readonly LABEL = 'UTLX Toolbar';

    @inject(MessageService)
    protected readonly messageService!: MessageService;

    private state: ToolbarState = {
        currentMode: UTLXMode.RUNTIME,
        showMCPDialog: false,
        mcpPrompt: '',
        mcpLoading: false
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
                        title='Execute transformation'
                    >
                        ▶️ Execute
                    </button>
                </div>

                {/* MCP Dialog */}
                {showMCPDialog && (
                    <div className='utlx-dialog-overlay' onClick={() => this.closeMCPDialog()}>
                        <div className='utlx-dialog' onClick={(e) => e.stopPropagation()}>
                            <div className='utlx-dialog-header'>
                                <h3>🤖 AI Assistant for UTLX</h3>
                                <button
                                    className='utlx-dialog-close'
                                    onClick={() => this.closeMCPDialog()}
                                >
                                    ✕
                                </button>
                            </div>

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
                            </div>

                            <div className='utlx-dialog-footer'>
                                <button
                                    className='utlx-dialog-button utlx-dialog-button-secondary'
                                    onClick={() => this.closeMCPDialog()}
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

        // Dispatch event for other widgets to update
        window.dispatchEvent(new CustomEvent('utlx-mode-changed', { detail: { mode: newMode } }));
    }

    private openMCPDialog(): void {
        this.setState({ showMCPDialog: true, mcpPrompt: '' });
    }

    private closeMCPDialog(): void {
        this.setState({ showMCPDialog: false, mcpPrompt: '', mcpLoading: false });
    }

    private async submitMCPPrompt(): Promise<void> {
        const { mcpPrompt } = this.state;

        if (!mcpPrompt.trim()) {
            return;
        }

        this.setState({ mcpLoading: true });

        try {
            // Call MCP service to generate UTLX
            this.messageService.info('🤖 Asking AI to generate UTLX transformation...');

            // TODO: Call actual MCP service
            // const result = await this.utlxService.generateUTLXFromPrompt(mcpPrompt);

            // For now, show a placeholder message
            await new Promise(resolve => setTimeout(resolve, 1000));

            this.messageService.info('✨ UTLX transformation generated! Check the editor.');

            // Dispatch event to update editor with generated UTLX
            window.dispatchEvent(new CustomEvent('utlx-generated', {
                detail: {
                    prompt: mcpPrompt,
                    // utlx: result.utlx
                }
            }));

            this.closeMCPDialog();
        } catch (error) {
            this.messageService.error(`Failed to generate UTLX: ${error}`);
            this.setState({ mcpLoading: false });
        }
    }

    private async handleExecute(): Promise<void> {
        try {
            this.messageService.info('▶️ Executing transformation...');
            // TODO: Trigger execution
            // await this.utlxService.execute();
        } catch (error) {
            this.messageService.error(`Execution failed: ${error}`);
        }
    }

    private setState(partial: Partial<ToolbarState>): void {
        this.state = { ...this.state, ...partial };
        this.update();
    }

    /**
     * Get current mode (for other widgets to query)
     */
    getCurrentMode(): UTLXMode {
        return this.state.currentMode;
    }
}
