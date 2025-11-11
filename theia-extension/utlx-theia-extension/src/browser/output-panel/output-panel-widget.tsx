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
    // CSV-specific output parameters
    csvHeaders?: boolean;      // Default true
    csvDelimiter?: string;     // Default ","
    csvBom?: boolean;          // Default false
    /*
    CSV has multiple parameter options. IDE has not implemented all of them (yet)

    private val dialect: CSVDialect = CSVDialect.DEFAULT,
    private val includeHeaders: Boolean = true,
    private val includeBOM: Boolean = false,
    private val regionalFormat: RegionalFormat = RegionalFormat.NONE,
    private val decimals: Int = 2,
    private val useThousands: Boolean = true
    CSVDialect  | delimieter | quote  | Description
    ------------|------------|--------|-------------
    DEFAULT     | ,          | "      | RFC 4180 CSV
    TSV         | \t         | "      | Tab-separated values
    SEMICOLON   | ;          | "      | Semicolon-delimited (common in European locales
    PIPE        | |          | "      |
    COLON       | :          | "      | rarely used but it is possible in rare occasions
    EXCEL       | ,          | "      | linTerminator = "\r\n"
    */
    // XML-specific output parameters
    xmlEncoding?: string;      // Default "UTF-8"
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

                    {/* CSV-specific parameters */}
                    {currentFormat === 'csv' && activeTab === 'instance' && (
                        <>
                            <label>
                                Headers:
                                <select
                                    value={this.state.csvHeaders === false ? 'false' : 'true'}
                                    onChange={(e) => this.handleCsvHeadersChange(e.target.value === 'true')}
                                >
                                    <option value='true'>Yes</option>
                                    <option value='false'>No</option>
                                </select>
                            </label>
                            <label>
                                Delimiter:
                                <select
                                    value={this.state.csvDelimiter || ','}
                                    onChange={(e) => this.handleCsvDelimiterChange(e.target.value)}
                                >
                                    <option value=','>Comma (,)</option>
                                    <option value=';'>Semicolon (;)</option>
                                    <option value='\t'>Tab (\t)</option>
                                    <option value='|'>Pipe (|)</option>
                                </select>
                            </label>
                            <label>
                                BOM:
                                <select
                                    value={this.state.csvBom ? 'true' : 'false'}
                                    onChange={(e) => this.handleCsvBomChange(e.target.value === 'true')}
                                    title='Byte Order Mark (UTF-8 BOM)'
                                >
                                    <option value='false'>No</option>
                                    <option value='true'>Yes</option>
                                </select>
                            </label>
                        </>
                    )}

                    {/* XML-specific parameters */}
                    {currentFormat === 'xml' && activeTab === 'instance' && (
                        <label>
                            Encoding:
                            <select
                                value={this.state.xmlEncoding || 'UTF-8'}
                                onChange={(e) => this.handleXmlEncodingChange(e.target.value)}
                            >
                                <option value='UTF-8'>UTF-8</option>
                                <option value='UTF-16'>UTF-16</option>
                                <option value='UTF-16LE'>UTF-16LE</option>
                                <option value='UTF-16BE'>UTF-16BE</option>
                                <option value='ISO-8859-1'>ISO-8859-1</option>
                                <option value='Windows-1252'>Windows-1252</option>
                            </select>
                        </label>
                    )}

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

    private handleCsvHeadersChange(hasHeaders: boolean): void {
        this.setState({ csvHeaders: hasHeaders });
        // Fire event to notify editor widget to update output format options
        this.eventService.fireOutputFormatChanged({
            format: 'csv',
            tab: this.state.activeTab,
            csvHeaders: hasHeaders,
            csvDelimiter: this.state.csvDelimiter,
            csvBom: this.state.csvBom
        });
    }

    private handleCsvDelimiterChange(delimiter: string): void {
        this.setState({ csvDelimiter: delimiter });
        // Fire event to notify editor widget to update output format options
        this.eventService.fireOutputFormatChanged({
            format: 'csv',
            tab: this.state.activeTab,
            csvHeaders: this.state.csvHeaders,
            csvDelimiter: delimiter,
            csvBom: this.state.csvBom
        });
    }

    private handleCsvBomChange(hasBom: boolean): void {
        this.setState({ csvBom: hasBom });
        // Fire event to notify editor widget to update output format options
        this.eventService.fireOutputFormatChanged({
            format: 'csv',
            tab: this.state.activeTab,
            csvHeaders: this.state.csvHeaders,
            csvDelimiter: this.state.csvDelimiter,
            csvBom: hasBom
        });
    }

    private handleXmlEncodingChange(encoding: string): void {
        this.setState({ xmlEncoding: encoding });
        // Fire event to notify editor widget to update output format options
        this.eventService.fireOutputFormatChanged({
            format: 'xml',
            tab: this.state.activeTab,
            xmlEncoding: encoding
        });
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

    /**
     * Get output format options for UTLX header generation
     * PUBLIC: Called by editor widget to build output format spec
     */
    public getOutputFormatOptions(): {
        format: string;
        csvHeaders?: boolean;
        csvDelimiter?: string;
        csvBom?: boolean;
        xmlEncoding?: string;
    } {
        const format = this.state.instanceFormat || 'json';

        return {
            format,
            csvHeaders: this.state.csvHeaders,
            csvDelimiter: this.state.csvDelimiter,
            csvBom: this.state.csvBom,
            xmlEncoding: this.state.xmlEncoding
        };
    }

    /**
     * Sync output format from parsed UTLX headers (for copy/paste support)
     * This updates the output panel to match the output format defined in the UTLX header
     */
    public syncFromHeaders(parsedOutput: {
        format: string;
        csvHeaders?: boolean;
        csvDelimiter?: string;
        csvBom?: boolean;
        xmlEncoding?: string;
    }): void {
        console.log('[OutputPanelWidget] Syncing from headers:', parsedOutput);

        // Update state with parsed output format and options
        this.setState({
            instanceFormat: parsedOutput.format,
            csvHeaders: parsedOutput.csvHeaders,
            csvDelimiter: parsedOutput.csvDelimiter,
            csvBom: parsedOutput.csvBom,
            xmlEncoding: parsedOutput.xmlEncoding
        });

        console.log('[OutputPanelWidget] Synced to format:', parsedOutput.format);
    }
}
