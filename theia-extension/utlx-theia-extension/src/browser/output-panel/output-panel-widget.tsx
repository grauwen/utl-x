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
import { FileDialogService, SaveFileDialogProps } from '@theia/filesystem/lib/browser';
import { FileService } from '@theia/filesystem/lib/browser/file-service';
import { URI } from '@theia/core/lib/common/uri';
import {
    UTLXService, UTLX_SERVICE_SYMBOL,
    UTLXMode,
    ExecutionResult,
    SchemaInferenceResult,
    Diagnostic,
    OUTPUT_PANEL_ID
} from '../../common/protocol';
import { UTLXEventService } from '../events/utlx-event-service';
import { SchemaFieldInfo, parseJsonSchemaToFieldTree, parseXsdToFieldTree } from '../utils/schema-field-tree-parser';
import { isScaffoldSupportedFormat } from '../utils/scaffold-generator';

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

    @inject(FileDialogService)
    protected readonly fileDialogService!: FileDialogService;

    @inject(FileService)
    protected readonly fileService!: FileService;

    private state: OutputPanelState = {
        mode: UTLXMode.RUNTIME,
        activeTab: 'instance',
        instanceContent: '',
        schemaContent: '',
        viewMode: 'raw' // Default to raw view to show output as-is from UTLXD
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
                // When entering design-time mode, link schema format to instance format
                let linkedSchemaFormat = this.state.schemaFormat;
                if (config.mode === UTLXMode.DESIGN_TIME && this.state.instanceFormat) {
                    const linked = this.getLinkedSchemaFormat(this.state.instanceFormat);
                    if (linked) {
                        linkedSchemaFormat = linked;
                    }
                }

                this.setState({
                    mode: config.mode,
                    activeTab: config.mode === UTLXMode.DESIGN_TIME ? 'schema' : 'instance',
                    schemaFormat: linkedSchemaFormat
                });
            }).catch(error => {
                console.error('[OutputPanel] Failed to load initial mode:', error);
            });
        }

        // Subscribe to mode changes
        this.eventService.onModeChanged(event => {
            console.log('[OutputPanelWidget] Mode changed to:', event.mode);

            // When entering design-time mode, link schema format to instance format
            let linkedSchemaFormat = this.state.schemaFormat;
            if (event.mode === UTLXMode.DESIGN_TIME && this.state.instanceFormat) {
                const linked = this.getLinkedSchemaFormat(this.state.instanceFormat);
                if (linked) {
                    linkedSchemaFormat = linked;
                }
            }

            this.setState({
                mode: event.mode,
                activeTab: event.mode === UTLXMode.DESIGN_TIME ? 'schema' : 'instance',
                schemaFormat: linkedSchemaFormat
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
                        {/* Infer Schema / Load button - only in design-time mode on schema tab */}
                        {mode === UTLXMode.DESIGN_TIME && activeTab === 'schema' && !this.isSchemaTabDisabled() && instanceFormat !== 'csv' && (
                            instanceContent ? (
                                <button
                                    onClick={() => this.handleInferSchema()}
                                    title='Infer output schema from transformation'
                                >
                                    <span className='codicon codicon-symbol-structure' style={{fontSize: '11px'}}></span>
                                    {' '}Infer Schema
                                </button>
                            ) : (
                                <button
                                    onClick={() => this.handleLoadSchema()}
                                    title='Load schema from file'
                                >
                                    <span className='codicon codicon-folder-opened' style={{fontSize: '11px'}}></span>
                                    {' '}Load
                                </button>
                            )
                        )}
                        <button
                            onClick={() => this.handleCopy()}
                            disabled={!currentContent}
                            title='Copy to clipboard'
                        >
                            <span className='codicon codicon-copy' style={{fontSize: '11px'}}></span>
                            {' '}Copy
                        </button>
                        <button
                            onClick={() => this.handleSave()}
                            disabled={!currentContent}
                            title='Save to file'
                        >
                            <span className='codicon codicon-save' style={{fontSize: '11px'}}></span>
                            {' '}Save
                        </button>
                        <button
                            onClick={() => this.handleClear()}
                            disabled={!currentContent && !currentError}
                            title='Clear output'
                        >
                            <span className='codicon codicon-clear-all' style={{fontSize: '11px'}}></span>
                            {' '}Clear
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
                    return this.formatXml(content);
                default:
                    return content;
            }
        } catch {
            // If formatting fails, return original content
            return content;
        }
    }

    /**
     * Format XML with proper indentation
     * This is a robust formatter that handles text content, attributes, comments, and processing instructions
     */
    private formatXml(xml: string): string {
        try {
            const lines: string[] = [];
            let indent = 0;
            const tab = '  '; // 2 spaces

            // Normalize: remove extra whitespace between tags, but preserve text content
            xml = xml.trim();

            // Split by tags while preserving them in the result
            const regex = /(<\?[^?]*\?>|<!--[\s\S]*?-->|<[^>]+>)/g;
            const parts = xml.split(regex).filter(part => part.length > 0);

            for (let i = 0; i < parts.length; i++) {
                const part = parts[i].trim();

                if (!part) continue; // Skip empty parts

                if (part.startsWith('<?')) {
                    // Processing instruction (e.g., <?xml version="1.0"?>)
                    lines.push(tab.repeat(indent) + part);
                } else if (part.startsWith('<!--')) {
                    // Comment
                    lines.push(tab.repeat(indent) + part);
                } else if (part.startsWith('</')) {
                    // Closing tag
                    indent = Math.max(0, indent - 1);
                    lines.push(tab.repeat(indent) + part);
                } else if (part.startsWith('<')) {
                    // Opening or self-closing tag
                    const isSelfClosing = part.endsWith('/>');

                    // Check if there's text content and a closing tag following this opening tag
                    // Pattern: <tag>text</tag>
                    let hasSimpleTextContent = false;
                    let textContent = '';
                    let closingTag = '';

                    if (!isSelfClosing && i + 2 < parts.length) {
                        const nextPart = parts[i + 1].trim();
                        const followingPart = parts[i + 2].trim();

                        // Check if next is text and following is closing tag
                        if (nextPart && !nextPart.startsWith('<') &&
                            followingPart && followingPart.startsWith('</')) {
                            hasSimpleTextContent = true;
                            textContent = nextPart;
                            closingTag = followingPart;
                        }
                    }

                    if (hasSimpleTextContent) {
                        // Tag with simple text content: <tag>text</tag>
                        // Put everything on one line
                        lines.push(tab.repeat(indent) + part + textContent + closingTag);
                        // Skip the text content and closing tag in next iterations
                        i += 2;
                    } else {
                        // Tag without immediate text content (has nested elements)
                        lines.push(tab.repeat(indent) + part);

                        // Increase indent for opening tags (not self-closing)
                        if (!isSelfClosing) {
                            indent++;
                        }
                    }
                } else {
                    // Pure text content (should have been handled above, but just in case)
                    if (part.length > 0) {
                        lines.push(tab.repeat(indent) + part);
                    }
                }
            }

            return lines.join('\n');
        } catch (error) {
            console.error('[OutputPanel] XML formatting failed:', error);
            return xml; // Return original if formatting fails
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
        try {
            // Get current content based on active tab
            const content = this.state.activeTab === 'instance'
                ? this.state.instanceContent
                : this.state.schemaContent;

            if (!content) {
                this.messageService.warn('No content to save');
                return;
            }

            // Determine file extension based on format
            const format = this.state.activeTab === 'instance'
                ? this.state.instanceFormat
                : this.state.schemaFormat;

            const extension = this.getFileExtension(format);
            const defaultFileName = this.state.activeTab === 'instance'
                ? `output${extension}`
                : `schema${extension}`;

            // Show save file dialog
            const dialogProps: SaveFileDialogProps = {
                title: `Save ${this.state.activeTab === 'instance' ? 'Output' : 'Schema'}`,
                saveLabel: 'Save',
                filters: {
                    'All Files': ['*']
                },
                inputValue: defaultFileName
            };

            const uri = await this.fileDialogService.showSaveDialog(dialogProps);

            if (!uri) {
                console.log('[OutputPanel] Save cancelled by user');
                return;
            }

            // Write content to file
            await this.fileService.write(uri, content);

            this.messageService.info(`Saved to ${uri.path.base}`);
        } catch (error) {
            console.error('[OutputPanel] Save error:', error);
            this.messageService.error(`Failed to save: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    /**
     * Get file extension based on format
     */
    private getFileExtension(format?: string): string {
        switch (format) {
            case 'json':
                return '.json';
            case 'xml':
                return '.xml';
            case 'csv':
                return '.csv';
            case 'yaml':
                return '.yaml';
            case 'xsd':
                return '.xsd';
            case 'jsch':
                return '.schema.json';
            case 'avro':
                return '.avsc';
            case 'proto':
                return '.proto';
            default:
                return '.txt';
        }
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

    /**
     * Handle "Infer Schema" button click
     * Fires event for frontend-contribution to execute schema inference
     */
    private handleInferSchema(): void {
        const schemaFormat = this.state.schemaFormat || this.getLinkedSchemaFormat(this.state.instanceFormat || 'json') || 'jsch';
        console.log('[OutputPanelWidget] Infer Schema requested, format:', schemaFormat);
        this.eventService.fireRequestOutputSchemaInference({
            schemaFormat
        });
    }

    /**
     * Handle "Load" button click
     * Fires event for frontend-contribution to open file dialog and load schema
     */
    private handleLoadSchema(): void {
        const schemaFormat = this.state.schemaFormat || this.getLinkedSchemaFormat(this.state.instanceFormat || 'json') || 'jsch';
        console.log('[OutputPanelWidget] Load Schema requested, format:', schemaFormat);
        this.eventService.fireRequestLoadOutputSchema({
            schemaFormat
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
                // Preserve existing format if result doesn't provide one
                instanceFormat: result.format || this.state.instanceFormat || 'json',
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
                // Note: We intentionally don't clear instanceFormat on error
                // This preserves the format dropdown selection
            });
        }
    }

    /**
     * Display schema inference result (design-time mode - schema output)
     */
    displaySchemaResult(result: SchemaInferenceResult): void {
        if (result.success && result.schema) {
            this.setState({
                activeTab: 'schema',  // Switch to schema tab to show result
                schemaContent: result.schema,
                schemaFormat: result.schemaFormat,
                schemaError: undefined,
                schemaDiagnostics: result.typeErrors
            });
        } else {
            this.setState({
                activeTab: 'schema',  // Switch to schema tab to show error
                schemaContent: '',
                schemaError: result.error || 'Failed to infer schema',
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
     * Get instance content and format for schema inference
     * PUBLIC: Called by frontend contribution for instance-based schema inference
     */
    public getInstanceData(): { content: string; format: string } {
        return {
            content: this.state.instanceContent,
            format: this.state.instanceFormat || 'json'
        };
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

    /**
     * Check if output structure is available for scaffolding.
     * Scaffolding is available when:
     * - Output schema exists (JSON Schema or XSD), OR
     * - Output instance exists (JSON or XML)
     * CSV is not supported for scaffolding.
     *
     * PUBLIC: Called by toolbar to determine button state
     */
    public hasOutputStructure(): boolean {
        // Check schema first (preferred source)
        if (this.state.schemaContent && this.state.schemaFormat) {
            const format = this.state.schemaFormat.toLowerCase();
            if (format === 'jsch' || format === 'xsd') {
                return true;
            }
        }

        // Check instance content (fallback)
        if (this.state.instanceContent && this.state.instanceFormat) {
            return isScaffoldSupportedFormat(this.state.instanceFormat);
        }

        return false;
    }

    /**
     * Get output structure for scaffold generation.
     * Priority: Schema > Instance
     *
     * Returns null if no valid structure is available.
     * PUBLIC: Called by frontend contribution for scaffold generation
     */
    public getStructureForScaffold(): { fields: SchemaFieldInfo[]; format: 'json' | 'xml' } | null {
        console.log('[OutputPanelWidget] getStructureForScaffold called');
        console.log('[OutputPanelWidget] Schema content length:', this.state.schemaContent?.length || 0);
        console.log('[OutputPanelWidget] Schema format:', this.state.schemaFormat);
        console.log('[OutputPanelWidget] Instance content length:', this.state.instanceContent?.length || 0);
        console.log('[OutputPanelWidget] Instance format:', this.state.instanceFormat);

        // Priority 1: Use schema if available
        if (this.state.schemaContent && this.state.schemaFormat) {
            const schemaFormat = this.state.schemaFormat.toLowerCase();

            if (schemaFormat === 'jsch') {
                const fields = parseJsonSchemaToFieldTree(this.state.schemaContent);
                if (fields.length > 0) {
                    console.log('[OutputPanelWidget] Using JSON Schema, fields:', fields.length);
                    return { fields, format: 'json' };
                }
            } else if (schemaFormat === 'xsd') {
                const fields = parseXsdToFieldTree(this.state.schemaContent);
                if (fields.length > 0) {
                    console.log('[OutputPanelWidget] Using XSD, fields:', fields.length);
                    return { fields, format: 'xml' };
                }
            }
        }

        // Priority 2: Parse instance content to infer structure
        if (this.state.instanceContent && this.state.instanceFormat) {
            const instanceFormat = this.state.instanceFormat.toLowerCase();

            if (instanceFormat === 'json') {
                const fields = this.parseJsonInstanceToFields(this.state.instanceContent);
                if (fields && fields.length > 0) {
                    console.log('[OutputPanelWidget] Using JSON instance, fields:', fields.length);
                    return { fields, format: 'json' };
                }
            } else if (instanceFormat === 'xml') {
                const fields = this.parseXmlInstanceToFields(this.state.instanceContent);
                if (fields && fields.length > 0) {
                    console.log('[OutputPanelWidget] Using XML instance, fields:', fields.length);
                    return { fields, format: 'xml' };
                }
            }
        }

        console.log('[OutputPanelWidget] No valid structure available for scaffolding');
        return null;
    }

    /**
     * Parse JSON instance to field tree (for scaffolding when no schema available)
     */
    private parseJsonInstanceToFields(content: string): SchemaFieldInfo[] | null {
        try {
            const data = JSON.parse(content);
            return this.objectToFields(data);
        } catch (error) {
            console.error('[OutputPanelWidget] Failed to parse JSON instance:', error);
            return null;
        }
    }

    /**
     * Convert a JS object to field tree
     */
    private objectToFields(obj: any, isArrayItem: boolean = false): SchemaFieldInfo[] {
        if (obj === null || obj === undefined) {
            return [];
        }

        if (Array.isArray(obj)) {
            // Array - analyze first element to infer item structure
            if (obj.length > 0 && typeof obj[0] === 'object' && obj[0] !== null) {
                return [{
                    name: '[]',
                    type: 'array',
                    fields: this.objectToFields(obj[0], true)
                }];
            }
            return [{
                name: '[]',
                type: 'array'
            }];
        }

        if (typeof obj === 'object') {
            const fields: SchemaFieldInfo[] = [];
            for (const [key, value] of Object.entries(obj)) {
                const field: SchemaFieldInfo = {
                    name: key,
                    type: this.inferType(value)
                };

                if (Array.isArray(value)) {
                    field.type = 'array';
                    if (value.length > 0 && typeof value[0] === 'object' && value[0] !== null) {
                        field.fields = this.objectToFields(value[0], true);
                    }
                } else if (typeof value === 'object' && value !== null) {
                    field.type = 'object';
                    field.fields = this.objectToFields(value);
                }

                fields.push(field);
            }
            return fields;
        }

        return [];
    }

    /**
     * Infer type from value
     */
    private inferType(value: any): string {
        if (value === null) return 'null';
        if (Array.isArray(value)) return 'array';
        if (typeof value === 'boolean') return 'boolean';
        if (typeof value === 'number') {
            return Number.isInteger(value) ? 'integer' : 'number';
        }
        if (typeof value === 'string') return 'string';
        if (typeof value === 'object') return 'object';
        return 'any';
    }

    /**
     * Parse XML instance to field tree (for scaffolding when no schema available)
     */
    private parseXmlInstanceToFields(content: string): SchemaFieldInfo[] | null {
        try {
            const parser = new DOMParser();
            const doc = parser.parseFromString(content, 'text/xml');

            // Check for parse errors
            const parseError = doc.querySelector('parsererror');
            if (parseError) {
                console.error('[OutputPanelWidget] XML parse error:', parseError.textContent);
                return null;
            }

            // Start from document element
            const root = doc.documentElement;
            if (!root) {
                return null;
            }

            return [this.xmlElementToField(root)];
        } catch (error) {
            console.error('[OutputPanelWidget] Failed to parse XML instance:', error);
            return null;
        }
    }

    /**
     * Convert XML element to field info
     */
    private xmlElementToField(element: Element): SchemaFieldInfo {
        const field: SchemaFieldInfo = {
            name: element.tagName,
            type: 'string' // default for leaf elements
        };

        const childFields: SchemaFieldInfo[] = [];

        // Add attributes
        for (const attr of Array.from(element.attributes)) {
            childFields.push({
                name: `@${attr.name}`,
                type: this.inferTypeFromValue(attr.value)
            });
        }

        // Add child elements
        const childElements = Array.from(element.children);
        const elementCounts = new Map<string, number>();

        // Count occurrences of each element name
        for (const child of childElements) {
            const count = elementCounts.get(child.tagName) || 0;
            elementCounts.set(child.tagName, count + 1);
        }

        // Process unique child elements
        const processedNames = new Set<string>();
        for (const child of childElements) {
            if (processedNames.has(child.tagName)) continue;
            processedNames.add(child.tagName);

            const childField = this.xmlElementToField(child);

            // If element appears multiple times, mark as array
            if ((elementCounts.get(child.tagName) || 0) > 1) {
                childField.type = 'array';
            }

            childFields.push(childField);
        }

        if (childFields.length > 0) {
            field.fields = childFields;
            field.type = 'object';
        } else {
            // Leaf element - infer type from text content
            const text = element.textContent || '';
            field.type = this.inferTypeFromValue(text);
        }

        return field;
    }

    /**
     * Infer normalized type from a string value.
     * Best-effort for instance documents where no schema type is available.
     */
    private inferTypeFromValue(value: string): string {
        const trimmed = value.trim();
        if (trimmed === '') return 'string';
        if (trimmed === 'true' || trimmed === 'false') return 'boolean';
        if (/^-?\d+$/.test(trimmed)) return 'integer';
        if (/^-?\d+\.\d+$/.test(trimmed)) return 'number';
        // ISO date patterns
        if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(trimmed)) return 'datetime';
        if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) return 'date';
        if (/^\d{2}:\d{2}(:\d{2})?$/.test(trimmed)) return 'time';
        return 'string';
    }
}
