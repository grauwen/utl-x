/**
 * Multi-Input Panel Widget
 *
 * Enhanced input panel supporting multiple inputs with vertical tabs
 * According to detailed-theia-design.txt specifications:
 * - Vertical tabs for multiple inputs
 * - Add/delete input functionality
 * - Format dropdowns (csv, json, xml, yaml, xsd, jsch, avro, proto)
 * - Runtime MODE: Single format per input
 * - Design Time MODE: Instance + Schema tabs with format linking
 */

import * as React from 'react';
import { injectable, inject, postConstruct, optional } from 'inversify';
import { ReactWidget } from '@theia/core/lib/browser/widgets/react-widget';
import { MessageService } from '@theia/core';
import {
    UTLXService, UTLX_SERVICE_SYMBOL,
    UTLXMode,
    InputDocument,
    SchemaDocument,
    DataFormat,
    SchemaFormat,
    INPUT_PANEL_ID
} from '../../common/protocol';
import { UTLXEventService } from '../events/utlx-event-service';

// Input panel format types (separate from protocol types)
// Data formats - used for actual data instances
export type InputDataFormatType = 'csv' | 'json' | 'xml' | 'yaml';

// Schema formats - used for schema definitions
export type InputSchemaFormatType = 'xsd' | 'jsch' | 'avro' | 'proto';

// All 8 formats (tier1 data + tier2 schema formats)
export type AllInputFormats = InputDataFormatType | InputSchemaFormatType;

// Runtime mode: All 8 formats allowed
export type RuntimeInstanceFormat = AllInputFormats;

// Design-Time mode: Instance tab - ALL 8 formats allowed
// (tier2 formats like xsd/jsch/avro/proto can be inputs for schema-to-schema transformations)
export type DesignTimeInstanceFormat = AllInputFormats;

// Design-Time mode: Schema tab - linked formats based on instance selection
export type DesignTimeSchemaFormat = InputSchemaFormatType;

// Combined types for state storage (backward compatibility)
export type InstanceFormat = AllInputFormats;
export type SchemaFormatType = InputSchemaFormatType;

export interface InputTab {
    id: string;
    name: string;
    instanceContent: string;
    instanceFormat: InstanceFormat;
    schemaContent: string;
    schemaFormat: SchemaFormatType;
    // CSV-specific parameters
    csvHeaders?: boolean;      // Default true
    csvDelimiter?: string;     // Default ","
    // Encoding parameters
    encoding?: string;         // Character encoding (UTF-8, UTF-16LE, UTF-16BE, ISO-8859-1, Windows-1252)
    bom?: boolean;             // Byte Order Mark (default false)
}

export interface MultiInputPanelState {
    mode: UTLXMode;
    inputs: InputTab[];
    activeInputId: string;
    activeSubTab: 'instance' | 'schema'; // For Design Time mode
    loading: boolean;
}

@injectable()
export class MultiInputPanelWidget extends ReactWidget {
    static readonly ID = INPUT_PANEL_ID;
    static readonly LABEL = 'Input';
    static readonly BUILD_VERSION = '2025-11-08T07:00:00Z'; // Build version for deployment verification

    @inject(UTLX_SERVICE_SYMBOL) @optional()
    protected readonly utlxService?: UTLXService;

    @inject(MessageService)
    protected readonly messageService!: MessageService;

    @inject(UTLXEventService)
    protected readonly eventService!: UTLXEventService;

    private state: MultiInputPanelState = {
        mode: UTLXMode.RUNTIME,
        inputs: [{
            id: 'input-1',
            name: 'input',
            instanceContent: '',
            instanceFormat: 'json',
            schemaContent: '',
            schemaFormat: 'jsch'
        }],
        activeInputId: 'input-1',
        activeSubTab: 'instance',
        loading: false
    };

    private nextInputId = 2;

    constructor() {
        super();
        this.id = MultiInputPanelWidget.ID;
        this.title.label = MultiInputPanelWidget.LABEL;
        this.title.caption = 'Input Data/Schema';
        this.title.closable = false;
        this.addClass('utlx-multi-input-panel');
    }

    @postConstruct()
    protected init(): void {
        // Log build version for deployment verification
        console.log(`[MultiInputPanelWidget] BUILD VERSION: ${MultiInputPanelWidget.BUILD_VERSION}`);
        console.log('[MultiInputPanelWidget] Widget initialized with vertical tabs layout');

        this.update();

        // Subscribe to mode changes
        this.eventService.onModeChanged(event => {
            console.log('[MultiInputPanelWidget] Mode changed to:', event.mode);
            this.setState({
                mode: event.mode,
                activeSubTab: event.mode === UTLXMode.DESIGN_TIME ? 'instance' : 'instance'
            });
        });
    }

    protected render(): React.ReactNode {
        const { mode, inputs, activeInputId, activeSubTab, loading } = this.state;
        const activeInput = inputs.find(input => input.id === activeInputId);

        if (!activeInput) {
            return <div>Error: No active input</div>;
        }

        // Determine current content and format based on mode and active sub-tab
        const isDesignTime = mode === UTLXMode.DESIGN_TIME;
        const currentContent = isDesignTime && activeSubTab === 'schema'
            ? activeInput.schemaContent
            : activeInput.instanceContent;
        const currentFormat = isDesignTime && activeSubTab === 'schema'
            ? activeInput.schemaFormat
            : activeInput.instanceFormat;

        return (
            <div className='utlx-multi-input-panel-container'>
                {/* Vertical Tabs for Multiple Inputs - Fixed on Left */}
                <div className='utlx-vertical-tabs'>
                    {inputs.map(input => (
                        <div
                            key={input.id}
                            className={`utlx-vertical-tab ${input.id === activeInputId ? 'active' : ''}`}
                            onClick={() => this.handleSelectInput(input.id)}
                        >
                            <span className='utlx-vertical-tab-name' title={input.name}>
                                {input.name}
                            </span>
                            {inputs.length > 1 && (
                                <button
                                    className='utlx-vertical-tab-delete'
                                    onClick={(e) => this.handleDeleteInput(input.id, e)}
                                    title='Delete input'
                                >
                                    ×
                                </button>
                            )}
                        </div>
                    ))}
                    <button
                        className='utlx-vertical-tab-add'
                        onClick={() => this.handleAddInput()}
                        title='Add input'
                    >
                        +
                    </button>
                </div>

                {/* Main Content Area - Right Side */}
                <div className='utlx-input-content-area'>
                    {/* Header - Input name and action buttons */}
                    <div className='utlx-panel-header'>
                        <div className='utlx-header-left'>
                            <input
                                type='text'
                                className='utlx-input-name-editor'
                                value={activeInput.name}
                                onChange={(e) => this.handleRenameInput(activeInputId, e.target.value)}
                                placeholder='Input name'
                            />
                        </div>
                        <div className='utlx-panel-actions'>
                            <button
                                onClick={() => this.handleLoadFile()}
                                disabled={loading}
                                title='Load from file'
                            >
                                Load
                            </button>
                            <button
                                onClick={() => this.handleClear()}
                                disabled={loading || !currentContent}
                                title='Clear input'
                            >
                                Clear
                            </button>
                        </div>
                    </div>

                    {/* Design Time Mode: Instance/Schema Horizontal Tabs - Below header */}
                    {isDesignTime && (
                        <div className='utlx-horizontal-tabs'>
                            <button
                                className={`utlx-horizontal-tab ${activeSubTab === 'instance' ? 'active' : ''}`}
                                onClick={() => this.handleSubTabSwitch('instance')}
                            >
                                Instance
                            </button>
                            <button
                                className={`utlx-horizontal-tab ${activeSubTab === 'schema' ? 'active' : ''}`}
                                onClick={() => this.handleSubTabSwitch('schema')}
                                disabled={this.isSchemaTabDisabled(activeInput.instanceFormat)}
                            >
                                Schema
                            </button>
                        </div>
                    )}

                    {/* Format Selector and CSV parameters */}
                    <div className='utlx-panel-toolbar'>
                        <label>
                            Format:
                            <select
                                value={currentFormat}
                                onChange={(e) => this.handleFormatChange(e.target.value as any)}
                                disabled={loading}
                            >
                                {isDesignTime && activeSubTab === 'schema' ? (
                                    // Design-Time Schema tab: linked formats based on instance
                                    <>
                                        {this.getSchemaFormatOptions(activeInput.instanceFormat)}
                                    </>
                                ) : (
                                    // Runtime mode + Design-Time Instance tab: all 8 formats
                                    <>
                                        <option value='csv'>csv</option>
                                        <option value='json'>json</option>
                                        <option value='xml'>xml</option>
                                        <option value='yaml'>yaml</option>
                                        <option value='xsd'>xsd</option>
                                        <option value='jsch'>jsch</option>
                                        <option value='avro'>avro</option>
                                        <option value='proto'>proto</option>
                                    </>
                                )}
                            </select>
                        </label>

                        {/* CSV-specific parameters - shown inline to the right of format */}
                        {currentFormat === 'csv' && activeSubTab === 'instance' && (
                            <>
                                <label>
                                    Headers:
                                    <select
                                        value={activeInput.csvHeaders === false ? 'false' : 'true'}
                                        onChange={(e) => this.handleCsvHeadersChange(e.target.value === 'true')}
                                        disabled={loading}
                                    >
                                        <option value='true'>Yes</option>
                                        <option value='false'>No</option>
                                    </select>
                                </label>
                                <label>
                                    Delimiter:
                                    <select
                                        value={activeInput.csvDelimiter || ','}
                                        onChange={(e) => this.handleCsvDelimiterChange(e.target.value)}
                                        disabled={loading}
                                    >
                                        <option value=','>Comma (,)</option>
                                        <option value=';'>Semicolon (;)</option>
                                        <option value='\t'>Tab (\t)</option>
                                        <option value='|'>Pipe (|)</option>
                                    </select>
                                </label>
                            </>
                        )}

                        {/* Encoding parameters - shown for all formats in instance tab */}
                        {activeSubTab === 'instance' && (
                            <>
                                <label>
                                    Encoding:
                                    <select
                                        value={activeInput.encoding || 'UTF-8'}
                                        onChange={(e) => this.handleEncodingChange(e.target.value)}
                                        disabled={loading}
                                    >
                                        <option value='UTF-8'>UTF-8</option>
                                        <option value='UTF-16LE'>UTF-16LE</option>
                                        <option value='UTF-16BE'>UTF-16BE</option>
                                        <option value='ISO-8859-1'>ISO-8859-1</option>
                                        <option value='Windows-1252'>Windows-1252</option>
                                    </select>
                                </label>
                                <label>
                                    BOM:
                                    <select
                                        value={activeInput.bom ? 'true' : 'false'}
                                        onChange={(e) => this.handleBomChange(e.target.value === 'true')}
                                        disabled={loading || !this.isBomSupported(activeInput.encoding)}
                                        title={!this.isBomSupported(activeInput.encoding) ? 'BOM not applicable for this encoding' : 'Byte Order Mark'}
                                    >
                                        <option value='false'>No</option>
                                        <option value='true'>Yes</option>
                                    </select>
                                </label>
                            </>
                        )}
                    </div>

                    {/* Content Editor */}
                    <div className='utlx-panel-content'>
                        <textarea
                            className='utlx-input-editor'
                            value={currentContent}
                            onChange={(e) => this.handleContentChange(e.target.value)}
                            placeholder={this.getPlaceholder(activeInput, activeSubTab)}
                            disabled={loading}
                            spellCheck={false}
                        />
                    </div>

                    {/* Footer */}
                    <div className='utlx-panel-footer'>
                        <span className='utlx-status'>
                            {loading
                                ? 'Loading...'
                                : currentContent
                                ? `${currentContent.length} characters`
                                : 'No input loaded'}
                        </span>
                    </div>
                </div>
            </div>
        );
    }

    /**
     * Get schema format options based on instance format
     * Linking rules:
     * - json → jsch (always)
     * - xml → xsd (always)
     * - yaml → jsch (always)
     * - csv → tsch (not implemented yet)
     * - xsd, jsch, avro, proto → BLUR the schema tab (no schema for schema)
     */
    private getSchemaFormatOptions(instanceFormat: InstanceFormat | SchemaFormatType): React.ReactNode[] {
        switch (instanceFormat) {
            case 'json':
            case 'yaml':
                return [
                    <option key='jsch' value='jsch'>jsch</option>
                ];
            case 'xml':
                return [<option key='xsd' value='xsd'>xsd</option>];
            case 'csv':
                return [<option key='tsch' value='tsch'>tsch (not implemented yet)</option>];
            default:
                return [];
        }
    }

    /**
     * Check if schema tab should be disabled (blurred)
     * Schema formats (xsd, jsch, avro, proto) when selected as instance should blur the schema tab
     */
    private isSchemaTabDisabled(instanceFormat: InstanceFormat | SchemaFormatType): boolean {
        return ['xsd', 'jsch', 'avro', 'proto'].includes(instanceFormat);
    }

    private getPlaceholder(input: InputTab, activeSubTab: 'instance' | 'schema'): string {
        const isSchema = activeSubTab === 'schema';
        const format = isSchema ? input.schemaFormat : input.instanceFormat;

        if (isSchema) {
            switch (format) {
                case 'xsd':
                    return '<?xml version="1.0"?>\n<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">\n  <!-- Define your XML schema here -->\n</xs:schema>';
                case 'jsch':
                    return '{\n  "$schema": "http://json-schema.org/draft-07/schema#",\n  "type": "object",\n  "properties": {\n    ...\n  }\n}';
                case 'avro':
                    return '{\n  "type": "record",\n  "name": "Example",\n  "fields": [\n    ...\n  ]\n}';
                case 'proto':
                    return 'syntax = "proto3";\n\nmessage Example {\n  ...\n}';
                default:
                    return 'Paste or load your schema here...';
            }
        } else {
            // Instance format placeholders (including schema formats for Runtime mode)
            switch (format) {
                case 'xml':
                    return '<root>\n  <element>data</element>\n</root>';
                case 'json':
                    return '{\n  "key": "value"\n}';
                case 'yaml':
                    return 'key: value\nlist:\n  - item1\n  - item2';
                case 'csv':
                    return 'name,age,email\nJohn,30,john@example.com';
                case 'xsd':
                    return '<?xml version="1.0"?>\n<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">\n  <!-- Define your XML schema here -->\n</xs:schema>';
                case 'jsch':
                    return '{\n  "$schema": "http://json-schema.org/draft-07/schema#",\n  "type": "object",\n  "properties": {\n    ...\n  }\n}';
                case 'avro':
                    return '{\n  "type": "record",\n  "name": "Example",\n  "fields": [\n    ...\n  ]\n}';
                case 'proto':
                    return 'syntax = "proto3";\n\nmessage Example {\n  ...\n}';
                default:
                    return 'Paste or load your input data here...';
            }
        }
    }

    private handleSelectInput(inputId: string): void {
        this.setState({ activeInputId: inputId });
    }

    private handleAddInput(): void {
        const newInput: InputTab = {
            id: `input-${this.nextInputId}`,
            name: `input${this.nextInputId}`,
            instanceContent: '',
            instanceFormat: 'json',
            schemaContent: '',
            schemaFormat: 'jsch'
        };
        this.nextInputId++;

        this.setState({
            inputs: [...this.state.inputs, newInput],
            activeInputId: newInput.id
        });

        this.messageService.info(`Added ${newInput.name}`);

        // Fire input added event
        this.eventService.fireInputAdded({
            inputId: newInput.id,
            name: newInput.name
        });

        // Fire format changed event for the new input
        this.eventService.fireInputFormatChanged({
            format: newInput.instanceFormat,
            inputId: newInput.id,
            isSchema: false
        });
    }

    private handleDeleteInput(inputId: string, event: React.MouseEvent): void {
        event.stopPropagation();

        if (this.state.inputs.length <= 1) {
            this.messageService.warn('Cannot delete the last input');
            return;
        }

        const inputToDelete = this.state.inputs.find(i => i.id === inputId);
        if (!inputToDelete) return;

        const confirmed = confirm(`Delete "${inputToDelete.name}"?`);
        if (!confirmed) return;

        const newInputs = this.state.inputs.filter(input => input.id !== inputId);
        let newActiveId = this.state.activeInputId;

        if (this.state.activeInputId === inputId) {
            newActiveId = newInputs[0]?.id || '';
        }

        this.setState({
            inputs: newInputs,
            activeInputId: newActiveId
        });

        this.messageService.info(`Deleted ${inputToDelete.name}`);

        // Fire input deleted event
        this.eventService.fireInputDeleted({
            inputId: inputToDelete.id,
            name: inputToDelete.name
        });
    }

    private handleRenameInput(inputId: string, newName: string): void {
        const oldInput = this.state.inputs.find(i => i.id === inputId);
        if (!oldInput) return;

        const oldName = oldInput.name;

        this.setState({
            inputs: this.state.inputs.map(input =>
                input.id === inputId ? { ...input, name: newName } : input
            )
        });

        // Fire input name changed event
        if (oldName !== newName) {
            this.eventService.fireInputNameChanged({
                inputId,
                oldName,
                newName
            });
        }
    }

    private handleSubTabSwitch(subTab: 'instance' | 'schema'): void {
        this.setState({ activeSubTab: subTab });
    }

    private handleContentChange(content: string): void {
        const activeInput = this.state.inputs.find(i => i.id === this.state.activeInputId);
        if (!activeInput) return;

        const isSchema = this.state.mode === UTLXMode.DESIGN_TIME && this.state.activeSubTab === 'schema';

        this.setState({
            inputs: this.state.inputs.map(input =>
                input.id === this.state.activeInputId
                    ? {
                        ...input,
                        [isSchema ? 'schemaContent' : 'instanceContent']: content
                    }
                    : input
            )
        });
    }

    private handleFormatChange(format: InstanceFormat | SchemaFormatType): void {
        const isSchema = this.state.mode === UTLXMode.DESIGN_TIME && this.state.activeSubTab === 'schema';
        const activeInput = this.state.inputs.find(input => input.id === this.state.activeInputId);

        console.log('[MultiInputPanel] Format changed:', { format, isSchema });

        this.setState({
            inputs: this.state.inputs.map(input =>
                input.id === this.state.activeInputId
                    ? {
                        ...input,
                        [isSchema ? 'schemaFormat' : 'instanceFormat']: format,
                        // When instance format changes, update schema format according to linking rules
                        ...((!isSchema && this.state.mode === UTLXMode.DESIGN_TIME) ? {
                            schemaFormat: this.getDefaultSchemaFormat(format as InstanceFormat)
                        } : {})
                    }
                    : input
            )
        });

        // Fire event to notify editor widget to update headers
        console.log('[MultiInputPanel] Firing input format changed event');
        this.eventService.fireInputFormatChanged({
            format,
            isSchema,
            inputId: this.state.activeInputId,
            csvHeaders: activeInput?.csvHeaders,
            csvDelimiter: activeInput?.csvDelimiter
        });
    }

    /**
     * Get default schema format based on instance format (format linking)
     */
    private getDefaultSchemaFormat(instanceFormat: InstanceFormat): SchemaFormatType {
        switch (instanceFormat) {
            case 'xml':
                return 'xsd';
            case 'json':
            case 'yaml':
                return 'jsch';
            case 'csv':
                return 'jsch'; // Default to jsch, though tsch is mentioned in design
            default:
                return 'jsch';
        }
    }

    private handleCsvHeadersChange(hasHeaders: boolean): void {
        const activeInput = this.state.inputs.find(input => input.id === this.state.activeInputId);

        this.setState({
            inputs: this.state.inputs.map(input =>
                input.id === this.state.activeInputId
                    ? {
                        ...input,
                        csvHeaders: hasHeaders
                    }
                    : input
            )
        });

        // Fire event to notify about CSV parameter change
        this.eventService.fireInputFormatChanged({
            format: 'csv',
            isSchema: false,
            inputId: this.state.activeInputId,
            csvHeaders: hasHeaders,
            csvDelimiter: activeInput?.csvDelimiter
        });
    }

    private handleCsvDelimiterChange(delimiter: string): void {
        const activeInput = this.state.inputs.find(input => input.id === this.state.activeInputId);

        this.setState({
            inputs: this.state.inputs.map(input =>
                input.id === this.state.activeInputId
                    ? {
                        ...input,
                        csvDelimiter: delimiter
                    }
                    : input
            )
        });

        // Fire event to notify about CSV parameter change
        this.eventService.fireInputFormatChanged({
            format: 'csv',
            isSchema: false,
            inputId: this.state.activeInputId,
            csvHeaders: activeInput?.csvHeaders,
            csvDelimiter: delimiter
        });
    }

    private handleEncodingChange(encoding: string): void {
        this.setState({
            inputs: this.state.inputs.map(input =>
                input.id === this.state.activeInputId
                    ? {
                        ...input,
                        encoding: encoding,
                        // Reset BOM if changing to encoding that doesn't support it
                        bom: this.isBomSupported(encoding) ? input.bom : false
                    }
                    : input
            )
        });
    }

    private handleBomChange(hasBom: boolean): void {
        this.setState({
            inputs: this.state.inputs.map(input =>
                input.id === this.state.activeInputId
                    ? {
                        ...input,
                        bom: hasBom
                    }
                    : input
            )
        });
    }

    private isBomSupported(encoding?: string): boolean {
        if (!encoding) return false;
        // BOM is only meaningful for UTF-8 and UTF-16 variants
        return ['UTF-8', 'UTF-16LE', 'UTF-16BE'].includes(encoding);
    }

    private handleClear(): void {
        const isSchema = this.state.mode === UTLXMode.DESIGN_TIME && this.state.activeSubTab === 'schema';

        this.setState({
            inputs: this.state.inputs.map(input =>
                input.id === this.state.activeInputId
                    ? {
                        ...input,
                        [isSchema ? 'schemaContent' : 'instanceContent']: ''
                    }
                    : input
            )
        });
    }

    private async handleLoadFile(): Promise<void> {
        try {
            // Create file input element
            const input = document.createElement('input');
            input.type = 'file';
            input.accept = '.csv,.json,.xml,.yaml,.yml,.xsd,.avsc,.proto,text/*';

            // Handle file selection
            input.onchange = async (e: Event) => {
                const target = e.target as HTMLInputElement;
                const file = target.files?.[0];
                if (!file) return;

                this.setState({ loading: true });

                try {
                    // Read file content
                    const content = await file.text();

                    // Determine if we're loading into instance or schema
                    const isSchema = this.state.mode === UTLXMode.DESIGN_TIME && this.state.activeSubTab === 'schema';

                    // Auto-detect format from file extension
                    const detectedFormat = this.detectFormatFromFilename(file.name);

                    // Update content and optionally format
                    this.setState({
                        inputs: this.state.inputs.map(input =>
                            input.id === this.state.activeInputId
                                ? {
                                    ...input,
                                    [isSchema ? 'schemaContent' : 'instanceContent']: content,
                                    ...(detectedFormat && !isSchema ? { instanceFormat: detectedFormat } : {}),
                                    ...(detectedFormat && isSchema ? { schemaFormat: detectedFormat as SchemaFormatType } : {})
                                }
                                : input
                        ),
                        loading: false
                    });

                    this.messageService.info(`Loaded file: ${file.name}${detectedFormat ? ` (format: ${detectedFormat})` : ''}`);

                    // Fire format changed event if format was auto-detected
                    if (detectedFormat && !isSchema) {
                        this.eventService.fireInputFormatChanged({
                            format: detectedFormat,
                            inputId: this.state.activeInputId,
                            isSchema: false
                        });
                    }

                    // Fire content changed event
                    if (isSchema) {
                        this.eventService.fireInputSchemaContentChanged({
                            inputId: this.state.activeInputId,
                            content
                        });
                    } else {
                        this.eventService.fireInputInstanceContentChanged({
                            inputId: this.state.activeInputId,
                            content
                        });
                    }
                } catch (error) {
                    this.setState({ loading: false });
                    this.messageService.error(`Failed to load file: ${error}`);
                }
            };

            // Trigger file dialog
            input.click();
        } catch (error) {
            this.messageService.error(`Failed to open file dialog: ${error}`);
        }
    }

    /**
     * Auto-detect format from file extension
     * Returns null if format cannot be detected
     */
    private detectFormatFromFilename(filename: string): InstanceFormat | SchemaFormatType | null {
        const ext = filename.toLowerCase().split('.').pop();

        switch (ext) {
            case 'csv':
                return 'csv';
            case 'json':
                return 'json';
            case 'xml':
                return 'xml';
            case 'yaml':
            case 'yml':
                return 'yaml';
            case 'xsd':
                return 'xsd';
            case 'avsc':
            case 'avro':
                return 'avro';
            case 'proto':
                return 'proto';
            case 'jsonschema':
            case 'schema':
                return 'jsch';
            default:
                return null; // Unknown extension, don't change format
        }
    }

    private setState(partial: Partial<MultiInputPanelState>): void {
        this.state = { ...this.state, ...partial };
        this.update();
    }

    /**
     * Get all input documents (for runtime mode)
     * PUBLIC: Called by frontend contribution for execution
     */
    public getInputDocuments(): InputDocument[] {
        return this.state.inputs
            .filter(input => input.instanceContent)
            .map(input => ({
                id: input.id,
                name: input.name,
                content: input.instanceContent,
                format: this.mapInstanceFormatToDataFormat(input.instanceFormat),
                encoding: input.encoding || 'UTF-8',
                bom: input.bom || false
            }));
    }

    /**
     * Map custom instance format to protocol DataFormat
     */
    private mapInstanceFormatToDataFormat(format: InstanceFormat | SchemaFormatType): DataFormat {
        const formatMap: { [key: string]: DataFormat } = {
            'csv': 'csv',
            'json': 'json',
            'xml': 'xml',
            'yaml': 'yaml',
            'xsd': 'xsd',
            'jsch': 'jsch',
            'avro': 'avro',
            'proto': 'proto'
        };
        return formatMap[format] || 'json';
    }

    /**
     * Get schema document for active input (for design-time mode)
     */
    getSchemaDocument(): SchemaDocument | null {
        const activeInput = this.state.inputs.find(i => i.id === this.state.activeInputId);
        if (!activeInput || !activeInput.schemaContent) {
            return null;
        }

        return {
            format: this.mapSchemaFormatToProtocol(activeInput.schemaFormat),
            content: activeInput.schemaContent
        };
    }

    /**
     * Map custom schema format to protocol SchemaFormat
     */
    private mapSchemaFormatToProtocol(format: SchemaFormatType): SchemaFormat {
        const formatMap: { [key: string]: SchemaFormat } = {
            'xsd': 'xsd',
            'jsch': 'jsch',
            'avro': 'avro',
            'proto': 'proto'
        };
        return formatMap[format] || 'jsch';
    }

    /**
     * Set mode from external source
     */
    setMode(mode: UTLXMode): void {
        this.setState({
            mode,
            activeSubTab: mode === UTLXMode.DESIGN_TIME ? 'instance' : 'instance'
        });
    }
}
