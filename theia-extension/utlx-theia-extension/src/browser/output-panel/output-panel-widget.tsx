/**
 * Output Panel Widget
 *
 * Right panel for displaying:
 * - Runtime Mode: Transformation output (XML, JSON, CSV, YAML)
 * - Design-Time Mode: Inferred output schema (JSON Schema)
 */

import * as React from 'react';
import { injectable, inject, postConstruct, optional } from 'inversify';
import { ReactWidget } from '@theia/core/lib/browser/widgets/react-widget';
import { MessageService } from '@theia/core';
import {
    UTLXService, UTLX_SERVICE_SYMBOL,
    UTLXMode,
    ExecutionResult,
    SchemaInferenceResult,
    Diagnostic,
    OUTPUT_PANEL_ID
} from '../../common/protocol';
import { UTLXEventService } from '../events/utlx-event-service';

export interface OutputPanelState {
    mode: UTLXMode;
    activeTab: 'instance' | 'schema';
    instanceContent: string;
    schemaContent: string;
    instanceFormat?: string;
    schemaFormat?: string;
    instanceExecutionTime?: number;
    schemaExecutionTime?: number;
    instanceError?: string;
    schemaError?: string;
    instanceDiagnostics?: Diagnostic[];
    schemaDiagnostics?: Diagnostic[];
    viewMode: 'pretty' | 'raw';
}

@injectable()
export class OutputPanelWidget extends ReactWidget {
    static readonly ID = OUTPUT_PANEL_ID;
    static readonly LABEL = 'Output';

    @inject(UTLX_SERVICE_SYMBOL) @optional()
    protected readonly utlxService?: UTLXService;

    @inject(MessageService)
    protected readonly messageService!: MessageService;

    @inject(UTLXEventService)
    protected readonly eventService!: UTLXEventService;

    private state: OutputPanelState = {
        mode: UTLXMode.RUNTIME,
        activeTab: 'instance',
        instanceContent: '',
        schemaContent: '',
        viewMode: 'pretty'
    };

    constructor() {
        super();
        this.id = OutputPanelWidget.ID;
        this.title.label = OutputPanelWidget.LABEL;
        this.title.caption = 'Output Data/Schema';
        this.title.closable = false;
        this.addClass('utlx-output-panel');
    }

    @postConstruct()
    protected init(): void {
        this.update();

        // Try to load initial mode from service if available
        if (this.utlxService) {
            this.utlxService.getMode().then(config => {
                this.setState({
                    mode: config.mode,
                    activeTab: config.mode === UTLXMode.DESIGN_TIME ? 'schema' : 'instance'
                });
            }).catch(error => {
                console.error('[OutputPanel] Failed to load initial mode:', error);
            });
        }

        // Subscribe to mode changes
        this.eventService.onModeChanged(event => {
            console.log('[OutputPanelWidget] Mode changed to:', event.mode);
            this.setState({
                mode: event.mode,
                activeTab: event.mode === UTLXMode.DESIGN_TIME ? 'schema' : 'instance'
            });
        });
    }


    protected render(): React.ReactNode {
        const {
            mode,
            activeTab,
            instanceContent,
            schemaContent,
            instanceFormat,
            schemaFormat,
            instanceExecutionTime,
            schemaExecutionTime,
            instanceError,
            schemaError,
            instanceDiagnostics,
            schemaDiagnostics,
            viewMode
        } = this.state;

        // Get current content, format, etc. based on active tab
        const currentContent = activeTab === 'instance' ? instanceContent : schemaContent;
        const currentFormat = activeTab === 'instance' ? instanceFormat : schemaFormat;
        const currentExecutionTime = activeTab === 'instance' ? instanceExecutionTime : schemaExecutionTime;
        const currentError = activeTab === 'instance' ? instanceError : schemaError;
        const currentDiagnostics = activeTab === 'instance' ? instanceDiagnostics : schemaDiagnostics;

        return (
            <div className='utlx-output-panel-container'>
                <div className='utlx-panel-header'>
                    <h3>Output</h3>
                    <div className='utlx-panel-actions'>
                        <button
                            onClick={() => this.handleCopy()}
                            disabled={!currentContent}
                            title='Copy to clipboard'
                        >
                            📋 Copy
                        </button>
                        <button
                            onClick={() => this.handleSave()}
                            disabled={!currentContent}
                            title='Save to file'
                        >
                            💾 Save
                        </button>
                        <button
                            onClick={() => this.handleClear()}
                            disabled={!currentContent && !currentError}
                            title='Clear output'
                        >
                            🗑️ Clear
                        </button>
                    </div>
                </div>

                {/* Tab Navigation */}
                <div className='utlx-tab-container'>
                    <button
                        className={`utlx-tab ${activeTab === 'instance' ? 'active' : ''}`}
                        onClick={() => this.handleTabSwitch('instance')}
                    >
                        Instance
                    </button>
                    {/* Only show Schema tab in Design-Time mode */}
                    {mode === UTLXMode.DESIGN_TIME && (
                        <button
                            className={`utlx-tab ${activeTab === 'schema' ? 'active' : ''} ${this.isSchemaTabDisabled() ? 'disabled' : ''}`}
                            onClick={() => !this.isSchemaTabDisabled() && this.handleTabSwitch('schema')}
                            disabled={this.isSchemaTabDisabled()}
                            title={this.isSchemaTabDisabled() ? 'Schema not available for schema formats' : 'View output schema'}
                        >
                            Schema
                        </button>
                    )}
                </div>

                <div className='utlx-panel-toolbar'>
                    <label>
                        Format:
                        <select
                            value={currentFormat || 'json'}
                            onChange={(e) => this.handleFormatChange((e.target as HTMLSelectElement).value)}
                        >
                            <option value='csv'>csv</option>
                            <option value='json'>json</option>
                            <option value='xml'>xml</option>
                            <option value='yaml'>yaml</option>
                            <option value='xsd'>xsd %USDL 1.0</option>
                            <option value='jsch'>jsch %USDL 1.0</option>
                            <option value='avro'>avro %USDL 1.0</option>
                            <option value='proto'>proto %USDL 1.0</option>
                        </select>
                    </label>

                    <label>
                        View:
                        <select
                            value={viewMode}
                            onChange={(e) => this.handleViewModeChange((e.target as HTMLSelectElement).value as 'pretty' | 'raw')}
                        >
                            <option value='pretty'>Pretty</option>
                            <option value='raw'>Raw</option>
                        </select>
                    </label>

                    {currentExecutionTime !== undefined && (
                        <span className='utlx-execution-time'>
                            ⏱️ {currentExecutionTime}ms
                        </span>
                    )}
                </div>

                <div className='utlx-panel-content'>
                    {currentError ? (
                        <div className='utlx-error-display'>
                            <div className='utlx-error-header'>❌ Error</div>
                            <pre className='utlx-error-message'>{currentError}</pre>
                        </div>
                    ) : currentContent ? (
                        <pre className='utlx-output-display'>
                            {viewMode === 'pretty' ? this.formatContent(currentContent, currentFormat) : currentContent}
                        </pre>
                    ) : (
                        <div className='utlx-placeholder'>
                            {activeTab === 'schema'
                                ? this.getSchemaPlaceholder()
                                : '▶️ Click "Execute" to see transformation output'}
                        </div>
                    )}

                    {currentDiagnostics && currentDiagnostics.length > 0 && (
                        <div className='utlx-diagnostics'>
                            <div className='utlx-diagnostics-header'>
                                ⚠️ Diagnostics ({currentDiagnostics.length})
                            </div>
                            <ul className='utlx-diagnostics-list'>
                                {currentDiagnostics.map((diagnostic, index) => (
                                    <li key={index} className={`diagnostic-${diagnostic.severity}`}>
                                        <span className='diagnostic-location'>
                                            Line {diagnostic.range.start.line + 1}, Col {diagnostic.range.start.column + 1}:
                                        </span>
                                        <span className='diagnostic-message'>{diagnostic.message}</span>
                                    </li>
                                ))}
                            </ul>
                        </div>
                    )}
                </div>

                <div className='utlx-panel-footer'>
                    <span className='utlx-status'>
                        {currentContent
                            ? `${currentContent.length} characters`
                            : currentError
                            ? 'Error occurred'
                            : 'No output'}
                    </span>
                </div>
            </div>
        );
    }

    private formatContent(content: string, format?: string): string {
        if (!format) {
            return content;
        }

        try {
            switch (format.toLowerCase()) {
                case 'json':
                case 'jsch':
                    return JSON.stringify(JSON.parse(content), null, 2);
                case 'xml':
                case 'xsd':
                    // Basic XML formatting (just add newlines)
                    return content
                        .replace(/></g, '>\n<')
                        .split('\n')
                        .map((line, index) => {
                            const indent = '  '.repeat(Math.max(0, (line.match(/</g) || []).length - (line.match(/\//g) || []).length));
                            return indent + line.trim();
                        })
                        .join('\n');
                default:
                    return content;
            }
        } catch {
            // If formatting fails, return original content
            return content;
        }
    }

    private handleTabSwitch(tab: 'instance' | 'schema'): void {
        this.setState({ activeTab: tab });
    }

    private async handleCopy(): Promise<void> {
        try {
            const content = this.state.activeTab === 'instance'
                ? this.state.instanceContent
                : this.state.schemaContent;
            await navigator.clipboard.writeText(content);
            this.messageService.info('Output copied to clipboard');
        } catch (error) {
            this.messageService.error(`Failed to copy: ${error}`);
        }
    }

    private async handleSave(): Promise<void> {
        // TODO: Implement save to file dialog
        this.messageService.info('Save functionality not yet implemented');
    }

    private handleClear(): void {
        if (this.state.activeTab === 'instance') {
            this.setState({
                instanceContent: '',
                instanceFormat: undefined,
                instanceExecutionTime: undefined,
                instanceError: undefined,
                instanceDiagnostics: undefined
            });
        } else {
            this.setState({
                schemaContent: '',
                schemaFormat: undefined,
                schemaExecutionTime: undefined,
                schemaError: undefined,
                schemaDiagnostics: undefined
            });
        }
    }

    private handleViewModeChange(viewMode: 'pretty' | 'raw'): void {
        this.setState({ viewMode });
    }

    private handleFormatChange(format: string): void {
        // Update format based on active tab
        if (this.state.activeTab === 'instance') {
            this.setState({ instanceFormat: format });

            // In Design-Time mode, auto-link schema format based on instance format
            if (this.state.mode === UTLXMode.DESIGN_TIME) {
                const linkedSchemaFormat = this.getLinkedSchemaFormat(format);
                if (linkedSchemaFormat) {
                    this.setState({ schemaFormat: linkedSchemaFormat });

                    // Fire schema format changed event
                    this.eventService.fireOutputSchemaFormatChanged({
                        format: linkedSchemaFormat
                    });
                }
            }
        } else {
            this.setState({ schemaFormat: format });
        }

        // Fire format change event for editor widget to update headers
        this.eventService.fireOutputFormatChanged({
            format,
            tab: this.state.activeTab
        });
    }

    /**
     * Get linked schema format for a given instance format
     * Returns null for schema formats (which can't have schemas)
     */
    private getLinkedSchemaFormat(instanceFormat: string): string | null {
        switch (instanceFormat) {
            case 'json':
            case 'yaml':
                return 'jsch';
            case 'xml':
                return 'xsd';
            case 'csv':
                return null; // tsch not implemented yet
            case 'xsd':
            case 'jsch':
            case 'avro':
            case 'proto':
                return null; // Schema formats can't have schemas
            default:
                return null;
        }
    }

    /**
     * Check if schema tab should be disabled
     * Schema tab is disabled when instance format is a schema format
     */
    private isSchemaTabDisabled(): boolean {
        const { instanceFormat } = this.state;
        if (!instanceFormat) return false;

        // Disable schema tab for schema formats (can't have schema of schema)
        return ['xsd', 'jsch', 'avro', 'proto'].includes(instanceFormat);
    }

    /**
     * Get placeholder text for schema tab based on instance format
     */
    private getSchemaPlaceholder(): string {
        const { instanceFormat } = this.state;

        if (instanceFormat === 'csv') {
            return '// tsch not implemented yet';
        }

        if (this.isSchemaTabDisabled()) {
            return '❌ Schema not available for schema formats';
        }

        return '💡 Click "Infer Schema" to generate output schema';
    }

    private setState(partial: Partial<OutputPanelState>): void {
        this.state = { ...this.state, ...partial };
        this.update();
    }

    /**
     * Display execution result (runtime mode - instance output)
     * PUBLIC: Called by frontend contribution after execution
     */
    public displayExecutionResult(result: ExecutionResult): void {
        if (result.success && result.output) {
            this.setState({
                instanceContent: result.output,
                instanceFormat: result.format,
                instanceExecutionTime: result.executionTimeMs,
                instanceError: undefined,
                instanceDiagnostics: result.diagnostics
            });
        } else {
            this.setState({
                instanceContent: '',
                instanceError: result.error || 'Unknown error occurred',
                instanceDiagnostics: result.diagnostics,
                instanceExecutionTime: result.executionTimeMs
            });
        }
    }

    /**
     * Display schema inference result (design-time mode - schema output)
     */
    displaySchemaResult(result: SchemaInferenceResult): void {
        if (result.success && result.schema) {
            this.setState({
                schemaContent: result.schema,
                schemaFormat: result.schemaFormat,
                schemaError: undefined,
                schemaDiagnostics: result.typeErrors
            });
        } else {
            this.setState({
                schemaContent: '',
                schemaError: 'Failed to infer schema',
                schemaDiagnostics: result.typeErrors
            });
        }
    }

    /**
     * Display error message
     * PUBLIC: Called by frontend contribution on errors
     */
    public displayError(error: string): void {
        if (this.state.activeTab === 'instance') {
            this.setState({
                instanceContent: '',
                instanceError: error,
                instanceDiagnostics: undefined
            });
        } else {
            this.setState({
                schemaContent: '',
                schemaError: error,
                schemaDiagnostics: undefined
            });
        }
    }

    /**
     * Set mode from external source
     */
    setMode(mode: UTLXMode): void {
        this.setState({
            mode,
            activeTab: mode === UTLXMode.DESIGN_TIME ? 'schema' : 'instance'
        });
    }

    /**
     * Clear all output
     */
    clear(): void {
        this.handleClear();
    }
}
