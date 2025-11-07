/**
 * UTL-X Workbench Widget
 *
 * Main container widget that manages the three-panel layout:
 * - Mode Selector (top toolbar)
 * - Input Panel (left)
 * - Editor (middle) - handled by Monaco/LSP
 * - Output Panel (right)
 */

import * as React from 'react';
import { injectable, inject, postConstruct } from 'inversify';
import { ReactWidget } from '@theia/core/lib/browser/widgets/react-widget';
import { MessageService, Command, CommandRegistry } from '@theia/core';
import { WidgetManager } from '@theia/core/lib/browser';
import { MultiInputPanelWidget } from '../input-panel/multi-input-panel-widget';
import { OutputPanelWidget } from '../output-panel/output-panel-widget';
import { ModeSelectorWidget } from '../mode-selector/mode-selector-widget';
import { UTLXEditorWidget } from '../editor/utlx-editor-widget';
import {
    UTLXService, UTLX_SERVICE_SYMBOL,
    UTLXMode,
    UTLXCommands
} from '../../common/protocol';

export interface WorkbenchState {
    executing: boolean;
    validating: boolean;
    inferring: boolean;
}

@injectable()
export class UTLXWorkbenchWidget extends ReactWidget {
    static readonly ID = 'utlx-workbench';
    static readonly LABEL = 'UTL-X Workbench';

    @inject(UTLX_SERVICE_SYMBOL)
    protected readonly utlxService!: UTLXService;

    @inject(MessageService)
    protected readonly messageService!: MessageService;

    @inject(CommandRegistry)
    protected readonly commandRegistry!: CommandRegistry;

    @inject(WidgetManager)
    protected readonly widgetManager!: WidgetManager;

    protected inputPanel?: MultiInputPanelWidget;
    protected editorWidget?: UTLXEditorWidget;
    protected outputPanel?: OutputPanelWidget;
    protected modeSelector?: ModeSelectorWidget;

    private state: WorkbenchState = {
        executing: false,
        validating: false,
        inferring: false
    };

    private currentEditorContent: string = '';

    constructor() {
        super();
        this.id = UTLXWorkbenchWidget.ID;
        this.title.label = UTLXWorkbenchWidget.LABEL;
        this.title.caption = 'UTL-X Transformation Workbench';
        this.title.closable = true;
        this.addClass('utlx-workbench');
    }

    @postConstruct()
    protected init(): void {
        this.update();
        this.registerCommands();

        // Load widgets asynchronously after construction
        this.loadWidgets();
    }

    private async loadWidgets(): Promise<void> {
        // Get widget instances via WidgetManager
        this.inputPanel = await this.widgetManager.getOrCreateWidget(MultiInputPanelWidget.ID);
        this.editorWidget = await this.widgetManager.getOrCreateWidget(UTLXEditorWidget.ID);
        this.outputPanel = await this.widgetManager.getOrCreateWidget(OutputPanelWidget.ID);
        this.modeSelector = await this.widgetManager.getOrCreateWidget(ModeSelectorWidget.ID);

        // Subscribe to mode changes
        this.modeSelector.onModeChange((mode) => {
            this.handleModeChange(mode);
        });

        // Listen for editor content changes
        this.editorWidget.node.addEventListener('utlx-content-changed', ((event: CustomEvent) => {
            this.currentEditorContent = event.detail.content;
        }) as EventListener);

        // Trigger re-render now that widgets are loaded
        this.update();
    }


    protected render(): React.ReactNode {
        if (!this.inputPanel || !this.editorWidget || !this.outputPanel || !this.modeSelector) {
            return <div>Loading...</div>;
        }

        const { executing, validating, inferring } = this.state;
        const mode = this.modeSelector.getCurrentMode();

        return (
            <div className='utlx-workbench-container'>
                <div className='utlx-workbench-header'>
                    <div className='utlx-workbench-title'>
                        <h2>🔄 UTL-X Transformation Workbench</h2>
                    </div>
                    <div className='utlx-workbench-actions'>
                        <button
                            onClick={() => this.handleValidate()}
                            disabled={validating}
                            className='utlx-action-button'
                            title='Validate UTL-X code'
                        >
                            {validating ? '⏳' : '✓'} Validate
                        </button>

                        {mode === UTLXMode.RUNTIME ? (
                            <button
                                onClick={() => this.handleExecute()}
                                disabled={executing}
                                className='utlx-action-button utlx-primary'
                                title='Execute transformation'
                            >
                                {executing ? '⏳' : '▶️'} Execute
                            </button>
                        ) : (
                            <button
                                onClick={() => this.handleInferSchema()}
                                disabled={inferring}
                                className='utlx-action-button utlx-primary'
                                title='Infer output schema'
                            >
                                {inferring ? '⏳' : '🔍'} Infer Schema
                            </button>
                        )}

                        <button
                            onClick={() => this.handleClear()}
                            className='utlx-action-button'
                            title='Clear all panels'
                        >
                            🗑️ Clear All
                        </button>
                    </div>
                </div>

                <div className='utlx-workbench-body'>
                    <div className='utlx-workbench-panes'>
                        <div className='utlx-workbench-pane left'>
                            <div
                                ref={container => {
                                    if (container && !container.hasChildNodes()) {
                                        container.appendChild(this.inputPanel!.node);
                                    }
                                }}
                                style={{ flex: 1, overflow: 'hidden' }}
                            />
                        </div>
                        <div className='utlx-workbench-pane middle'>
                            <div
                                ref={container => {
                                    if (container && !container.hasChildNodes()) {
                                        container.appendChild(this.editorWidget!.node);
                                    }
                                }}
                                style={{ flex: 1, overflow: 'hidden' }}
                            />
                        </div>
                        <div className='utlx-workbench-pane right'>
                            <div
                                ref={container => {
                                    if (container && !container.hasChildNodes()) {
                                        container.appendChild(this.outputPanel!.node);
                                    }
                                }}
                                style={{ flex: 1, overflow: 'hidden' }}
                            />
                        </div>
                    </div>
                </div>
            </div>
        );
    }

    private registerCommands(): void {
        // Execute transformation command
        this.commandRegistry.registerCommand(
            { id: UTLXCommands.EXECUTE_TRANSFORMATION },
            {
                execute: () => this.handleExecute()
            }
        );

        // Validate command
        this.commandRegistry.registerCommand(
            { id: UTLXCommands.VALIDATE_CODE },
            {
                execute: () => this.handleValidate()
            }
        );

        // Infer schema command
        this.commandRegistry.registerCommand(
            { id: UTLXCommands.INFER_SCHEMA },
            {
                execute: () => this.handleInferSchema()
            }
        );

        // Toggle mode command
        this.commandRegistry.registerCommand(
            { id: UTLXCommands.TOGGLE_MODE },
            {
                execute: () => this.handleToggleMode()
            }
        );

        // Clear panels command
        this.commandRegistry.registerCommand(
            { id: UTLXCommands.CLEAR_PANELS },
            {
                execute: () => this.handleClear()
            }
        );
    }

    private async handleExecute(): Promise<void> {
        if (!this.modeSelector || !this.inputPanel || !this.outputPanel) return;

        if (this.state.executing) {
            return;
        }

        const mode = this.modeSelector.getCurrentMode();
        if (mode !== UTLXMode.RUNTIME) {
            this.messageService.warn('Execute is only available in Runtime mode');
            return;
        }

        const inputs = this.inputPanel.getInputDocuments();
        if (inputs.length === 0) {
            this.messageService.warn('Please load input data first');
            return;
        }

        if (!this.currentEditorContent) {
            this.messageService.warn('Please write a UTL-X transformation');
            return;
        }

        try {
            this.setState({ executing: true });

            const result = await this.utlxService.execute(
                this.currentEditorContent,
                inputs
            );

            this.outputPanel.displayExecutionResult(result);

            if (result.success) {
                this.messageService.info(`Transformation completed in ${result.executionTimeMs}ms`);
            } else {
                this.messageService.error('Transformation failed');
            }
        } catch (error) {
            this.messageService.error(`Execution error: ${error}`);
            this.outputPanel.displayError(String(error));
        } finally {
            this.setState({ executing: false });
        }
    }

    private async handleInferSchema(): Promise<void> {
        if (!this.modeSelector || !this.inputPanel || !this.outputPanel) return;

        if (this.state.inferring) {
            return;
        }

        const mode = this.modeSelector.getCurrentMode();
        if (mode !== UTLXMode.DESIGN_TIME) {
            this.messageService.warn('Schema inference is only available in Design-Time mode');
            return;
        }

        const schema = this.inputPanel.getSchemaDocument();
        if (!schema) {
            this.messageService.warn('Please load input schema first');
            return;
        }

        if (!this.currentEditorContent) {
            this.messageService.warn('Please write a UTL-X transformation');
            return;
        }

        try {
            this.setState({ inferring: true });

            const result = await this.utlxService.inferSchema(
                this.currentEditorContent,
                schema
            );

            this.outputPanel.displaySchemaResult(result);

            if (result.success) {
                this.messageService.info('Output schema inferred successfully');
            } else {
                this.messageService.error('Schema inference failed');
            }
        } catch (error) {
            this.messageService.error(`Schema inference error: ${error}`);
            this.outputPanel.displayError(String(error));
        } finally {
            this.setState({ inferring: false });
        }
    }

    private async handleValidate(): Promise<void> {
        if (this.state.validating) {
            return;
        }

        if (!this.currentEditorContent) {
            this.messageService.warn('No code to validate');
            return;
        }

        try {
            this.setState({ validating: true });

            const result = await this.utlxService.validate(this.currentEditorContent);

            if (result.valid) {
                this.messageService.info('✓ Code is valid');
            } else {
                const errorCount = result.diagnostics.filter(d => d.severity === 1).length;
                const warningCount = result.diagnostics.filter(d => d.severity === 2).length;
                this.messageService.warn(
                    `Found ${errorCount} error(s) and ${warningCount} warning(s)`
                );
            }
        } catch (error) {
            this.messageService.error(`Validation error: ${error}`);
        } finally {
            this.setState({ validating: false });
        }
    }

    private async handleToggleMode(): Promise<void> {
        if (!this.modeSelector) return;

        const currentMode = this.modeSelector.getCurrentMode();
        const newMode = currentMode === UTLXMode.DESIGN_TIME ? UTLXMode.RUNTIME : UTLXMode.DESIGN_TIME;
        await this.modeSelector.setMode(newMode);
    }

    private handleClear(): void {
        if (!this.outputPanel) return;

        this.outputPanel.clear();
        this.messageService.info('Panels cleared');
    }

    private handleModeChange(mode: UTLXMode): void {
        if (!this.inputPanel || !this.outputPanel) return;

        // Notify panels of mode change
        this.inputPanel.setMode(mode);
        this.outputPanel.setMode(mode);

        // Clear output when mode changes
        this.outputPanel.clear();
    }

    private setState(partial: Partial<WorkbenchState>): void {
        this.state = { ...this.state, ...partial };
        this.update();
    }

    /**
     * Set editor content (called from editor integration)
     */
    setEditorContent(content: string): void {
        this.currentEditorContent = content;
    }

    /**
     * Get editor content
     */
    getEditorContent(): string {
        return this.currentEditorContent;
    }
}
