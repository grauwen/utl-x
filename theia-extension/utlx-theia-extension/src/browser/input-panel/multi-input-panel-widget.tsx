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
import { FileDialogService } from '@theia/filesystem/lib/browser';
import { FileService } from '@theia/filesystem/lib/browser/file-service';
import URI from '@theia/core/lib/common/uri';
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
    // Encoding parameters (COMMENTED OUT - encoding/BOM are auto-detected for inputs, not manually set)
    // encoding?: string;         // Character encoding (UTF-8, UTF-16LE, UTF-16BE, ISO-8859-1, Windows-1252)
    // bom?: boolean;             // Byte Order Mark (default false)
    // UDM parse status
    udmParsed?: boolean;       // true = success (green check), false = error (red X), undefined = not checked yet
    udmValidating?: boolean;   // true = currently validating (pulse icon)
    udmError?: string;         // Error message for tooltip
    udmLanguage?: string;      // The parsed UDM representation of the input data (from /api/udm/export)
}

export interface MultiInputPanelState {
    mode: UTLXMode;
    inputs: InputTab[];
    activeInputId: string;
    activeSubTab: 'instance' | 'schema'; // For Design Time mode
    loading: boolean;
    udmDialogOpen?: boolean;
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

    @inject(FileDialogService)
    protected readonly fileDialogService!: FileDialogService;

    @inject(FileService)
    protected readonly fileService!: FileService;

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
        loading: false,
        udmDialogOpen: false
    };

    private nextInputId = 2;
    private udmDialogPosition = { x: 0, y: 0 };
    private isDraggingDialog = false;
    private dragOffset = { x: 0, y: 0 };
    private udmViewMode: 'raw' | 'pretty' = 'pretty';

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

        // Subscribe to UDM requests (e.g., when Function Builder opens)
        this.eventService.onRequestCurrentUdm(() => {
            console.log('[MultiInputPanelWidget] Received request for current UDM');
            // Re-fire UDM events for all inputs that have UDM data
            this.state.inputs.forEach(input => {
                if (input.udmLanguage && input.udmParsed === true) {
                    console.log('[MultiInputPanelWidget] Re-firing UDM for input:', input.name);
                    this.eventService.fireInputUdmUpdated({
                        inputId: input.id,
                        inputName: input.name,
                        udmLanguage: input.udmLanguage,
                        format: input.instanceFormat
                    });
                }
            });
        });
    }

    protected render(): React.ReactNode {
        const { mode, inputs, activeInputId, activeSubTab, loading, udmDialogOpen } = this.state;
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
                            {/* UDM Parse Status Indicator */}
                            {activeInput.udmValidating && (
                                <span
                                    className='utlx-udm-indicator'
                                    style={{ color: '#6272a4' }}
                                    title='Validating UDM...'
                                >
                                    ⟳ UDM
                                </span>
                            )}
                            {!activeInput.udmValidating && activeInput.udmParsed === true && (
                                <button
                                    className='utlx-udm-indicator utlx-udm-clickable'
                                    style={{ color: '#50fa7b' }}
                                    title={`UDM parsed successfully - Click to re-validate`}
                                    onClick={() => this.validateInput(activeInputId)}
                                >
                                    ✓ UDM
                                </button>
                            )}
                            {!activeInput.udmValidating && activeInput.udmParsed === false && (
                                <button
                                    className='utlx-udm-indicator utlx-udm-clickable'
                                    style={{ color: '#ff5555' }}
                                    title={`UDM parse error: ${activeInput.udmError || 'Unknown error'} - Click to retry`}
                                    onClick={() => this.validateInput(activeInputId)}
                                >
                                    ✗ UDM
                                </button>
                            )}
                        </div>
                        <div className='utlx-panel-actions'>
                            {activeInput.udmParsed === true && (
                                <button
                                    onClick={() => this.handleViewUdm()}
                                    title='View UDM representation'
                                >
                                    <span className='codicon codicon-eye' style={{fontSize: '11px'}}></span>
                                    {' '}UDM
                                </button>
                            )}
                            <button
                                onClick={() => this.handleLoadFile()}
                                disabled={loading}
                                title='Load from file'
                            >
                                <span className='codicon codicon-folder-opened' style={{fontSize: '11px'}}></span>
                                {' '}Load
                            </button>
                            <button
                                onClick={() => this.handleClear()}
                                disabled={loading || !currentContent}
                                title='Clear input'
                            >
                                <span className='codicon codicon-clear-all' style={{fontSize: '11px'}}></span>
                                {' '}Clear
                            </button>
                        </div>
                    </div>

                    {/* Instance/Schema Horizontal Tabs - Below header */}
                    <div className='utlx-horizontal-tabs'>
                        {isDesignTime ? (
                            <>
                                {/* Design-Time: Show both Instance and Schema tabs */}
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
                            </>
                        ) : (
                            <>
                                {/* Runtime: Show only Instance label (non-clickable) */}
                                <div className='utlx-horizontal-tab active'>
                                    Instance
                                </div>
                            </>
                        )}
                    </div>

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
                                {/* ENCODING/BOM CONTROLS REMOVED - These are auto-detected for inputs, not manually set
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
                                */}
                            </>
                        )}
                    </div>

                    {/* Content Editor */}
                    <div className='utlx-panel-content'>
                        <textarea
                            className='utlx-input-editor'
                            value={currentContent}
                            onChange={(e) => this.handleContentChange(e.target.value)}
                            onPaste={(e) => this.handlePaste(e)}
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

                {/* UDM View Dialog */}
                {udmDialogOpen && activeInput.udmLanguage && (
                    <div className='utlx-udm-dialog-overlay'>
                        <div
                            className='utlx-udm-dialog'
                            style={{
                                transform: `translate(calc(-50% + ${this.udmDialogPosition.x}px), calc(-50% + ${this.udmDialogPosition.y}px))`,
                                cursor: this.isDraggingDialog ? 'grabbing' : 'default'
                            }}
                        >
                            <div
                                className='utlx-udm-dialog-header'
                                onMouseDown={(e) => this.handleDragStart(e)}
                                style={{ cursor: 'grab' }}
                            >
                                <h3>UDM Representation</h3>
                                <button
                                    className='utlx-udm-dialog-close'
                                    onClick={() => this.handleCloseUdmDialog()}
                                    title='Close'
                                >
                                    <span className='codicon codicon-close'></span>
                                </button>
                            </div>
                            <div className='utlx-udm-dialog-toolbar'>
                                <label>
                                    View:
                                    <select
                                        value={this.udmViewMode}
                                        onChange={(e) => {
                                            this.udmViewMode = (e.target as HTMLSelectElement).value as 'raw' | 'pretty';
                                            this.update();
                                        }}
                                    >
                                        <option value='raw'>Raw</option>
                                        <option value='pretty'>Pretty</option>
                                    </select>
                                </label>
                            </div>
                            <div className='utlx-udm-dialog-content'>
                                <textarea
                                    className='utlx-udm-viewer'
                                    value={this.udmViewMode === 'pretty' ? this.formatUdm(activeInput.udmLanguage) : activeInput.udmLanguage}
                                    readOnly
                                    spellCheck={false}
                                />
                            </div>
                            <div className='utlx-udm-dialog-footer'>
                                <button
                                    onClick={() => this.handleCopyUdmToClipboard()}
                                    title='Copy UDM to clipboard'
                                >
                                    <span className='codicon codicon-copy' style={{fontSize: '11px'}}></span>
                                    {' '}Copy to Clipboard
                                </button>
                                <button
                                    onClick={() => this.handleSaveUdm()}
                                    title='Save UDM to file'
                                >
                                    <span className='codicon codicon-save' style={{fontSize: '11px'}}></span>
                                    {' '}Save
                                </button>
                                <button
                                    onClick={() => this.handleCloseUdmDialog()}
                                    title='Close dialog'
                                >
                                    <span className='codicon codicon-close' style={{fontSize: '11px'}}></span>
                                    {' '}Close
                                </button>
                            </div>
                        </div>
                    </div>
                )}
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

        // Prevent empty names
        if (!newName || newName.trim() === '') {
            this.messageService.warn('Input name cannot be empty');
            // Revert to old name by forcing a re-render
            this.update();
            return;
        }

        // Check if another input already has this name (case-insensitive check)
        const duplicateInput = this.state.inputs.find(
            input => input.id !== inputId && input.name.toLowerCase() === newName.toLowerCase()
        );

        if (duplicateInput) {
            this.messageService.warn(`Input name "${newName}" is already in use. Please choose a different name.`);
            // Revert to old name by forcing a re-render
            this.update();
            return;
        }

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

    private handlePaste(e: React.ClipboardEvent<HTMLTextAreaElement>): void {
        console.log('[MultiInputPanel] Paste detected');

        // Only validate instance content, not schema content
        const isSchema = this.state.mode === UTLXMode.DESIGN_TIME && this.state.activeSubTab === 'schema';
        if (isSchema) {
            console.log('[MultiInputPanel] Paste in schema tab - skipping UDM validation');
            return;
        }

        // Wait a bit for the paste to complete and state to update
        setTimeout(() => {
            console.log('[MultiInputPanel] Triggering UDM validation after paste');
            this.validateInput(this.state.activeInputId);
        }, 100);
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

        // Trigger UDM validation after format change (only for instance format, not schema)
        if (!isSchema && activeInput?.instanceContent.trim()) {
            console.log('[MultiInputPanel] Format changed - triggering UDM validation');
            // Use setTimeout to ensure state has been updated
            setTimeout(() => {
                this.validateInput(this.state.activeInputId);
            }, 100);
        }
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

    /**
     * Validate input content against UDM parser
     */
    private async validateInput(inputId: string): Promise<void> {
        console.log('[MultiInputPanel] ========================================');
        console.log('[MultiInputPanel] validateInput() CALLED');
        console.log('[MultiInputPanel] Input ID:', inputId);

        const input = this.state.inputs.find(i => i.id === inputId);

        if (!input) {
            console.warn('[MultiInputPanel] Input not found:', inputId);
            return;
        }

        console.log('[MultiInputPanel] Input found:', {
            name: input.name,
            format: input.instanceFormat,
            contentLength: input.instanceContent.length,
            hasContent: !!input.instanceContent.trim()
        });

        if (!input.instanceContent.trim()) {
            console.log('[MultiInputPanel] Empty content - clearing validation status');
            this.updateInputValidation(inputId, undefined, false);
            return;
        }

        if (!this.utlxService) {
            console.error('[MultiInputPanel] UTLXService not available for validation!');
            this.updateInputValidation(inputId, false, false, 'UTLXService not available');
            return;
        }

        console.log('[MultiInputPanel] Setting validating state...');
        this.updateInputValidation(inputId, undefined, true);

        try {
            // Build request - only include CSV parameters for CSV format
            const request: any = {
                content: input.instanceContent,
                format: input.instanceFormat
            };

            // Only add CSV parameters if the format is actually CSV
            if (input.instanceFormat.toLowerCase() === 'csv') {
                request.csvHeaders = input.csvHeaders !== undefined ? input.csvHeaders : true;
                request.csvDelimiter = input.csvDelimiter || ',';
            }

            console.log('[MultiInputPanel] Calling utlxService.validateUdm() with:', {
                format: request.format,
                contentLength: request.content.length,
                csvHeaders: request.csvHeaders,
                csvDelimiter: request.csvDelimiter,
                note: input.instanceFormat.toLowerCase() === 'csv' ? 'CSV parameters included' : 'CSV parameters EXCLUDED'
            });

            // Log first few lines of content for debugging
            const firstLines = request.content.split('\n').slice(0, 5);
            console.log('[MultiInputPanel] Content preview (first 5 lines):');
            firstLines.forEach((line: string, idx: number) => {
                console.log(`  Line ${idx + 1}: ${line.substring(0, 100)}`);
            });

            const result = await this.utlxService.validateUdm(request);

            console.log('[MultiInputPanel] ═══════════════════════════════════════');
            if (result.success) {
                console.log('[MultiInputPanel] ✅ Validation result: SUCCESS');
            } else {
                console.error('[MultiInputPanel] ❌ Validation result: FAILED');
            }
            console.log('[MultiInputPanel] Success:', result.success);
            console.log('[MultiInputPanel] Has Diagnostics:', !!result.diagnostics);
            console.log('[MultiInputPanel] Has UDM Language:', !!result.udmLanguage);
            console.log('[MultiInputPanel] UDM Language Length:', result.udmLanguage?.length || 0);

            if (result.error) {
                console.error('[MultiInputPanel] ❌ ERROR:', result.error);
                // Try to parse and display error details
                try {
                    const errorObj = JSON.parse(result.error.replace('HTTP 500: ', ''));
                    if (errorObj.error) {
                        console.error('[MultiInputPanel] Error details:', errorObj.error);
                    }
                } catch (e) {
                    // Not JSON, just show as-is
                }
            }

            if (result.udmLanguage) {
                console.log('[MultiInputPanel] UDM preview (first 500 chars):');
                console.log(result.udmLanguage.substring(0, 500));
                console.log('[MultiInputPanel] UDM preview (last 200 chars):');
                console.log(result.udmLanguage.substring(Math.max(0, result.udmLanguage.length - 200)));
            } else {
                console.warn('[MultiInputPanel] ⚠️ NO UDM LANGUAGE in result!');
                if (!result.success) {
                    console.warn('[MultiInputPanel] This is expected when validation fails');
                }
            }
            console.log('[MultiInputPanel] ═══════════════════════════════════════');

            // Verify the UDM is actually parseable before marking as success
            let actualSuccess = result.success;
            let actualError = result.error;

            if (result.success && result.udmLanguage) {
                console.log('[MultiInputPanel] 🔍 Verifying UDM is parseable...');
                try {
                    // Try to parse the UDM to ensure it's valid
                    const { UDMLanguageParser } = await import('../udm/udm-language-parser');
                    UDMLanguageParser.parse(result.udmLanguage);
                    console.log('[MultiInputPanel] ✅ UDM is valid and parseable');
                } catch (parseError) {
                    console.error('[MultiInputPanel] ❌ UDM is INVALID - parse error:', parseError);
                    actualSuccess = false;
                    actualError = parseError instanceof Error ? parseError.message : 'UDM parse error';
                }
            }

            this.updateInputValidation(
                inputId,
                actualSuccess,
                false,
                actualError,
                result.udmLanguage
            );

            // Fire event if UDM was successfully parsed AND is valid
            if (actualSuccess && result.udmLanguage && input) {
                console.log('[MultiInputPanel] Firing UDM updated event');
                this.eventService.fireInputUdmUpdated({
                    inputId: input.id,
                    inputName: input.name,
                    udmLanguage: result.udmLanguage,
                    format: input.instanceFormat
                });
            }

            console.log('[MultiInputPanel] Validation state updated');
            console.log('[MultiInputPanel] ========================================');
        } catch (error) {
            console.error('[MultiInputPanel] Validation exception:', error);
            console.error('[MultiInputPanel] Error stack:', error instanceof Error ? error.stack : 'N/A');
            this.updateInputValidation(
                inputId,
                false,
                false,
                String(error)
            );
            console.log('[MultiInputPanel] ========================================');
        }
    }

    /**
     * Update input validation state
     */
    private updateInputValidation(
        inputId: string,
        udmParsed?: boolean,
        udmValidating?: boolean,
        udmError?: string,
        udmLanguage?: string
    ): void {
        console.log('[MultiInputPanel] updateInputValidation() called:', {
            inputId,
            udmParsed,
            udmValidating,
            udmError,
            hasUdmLanguage: !!udmLanguage,
            udmLanguageLength: udmLanguage?.length
        });

        this.setState({
            inputs: this.state.inputs.map(input =>
                input.id === inputId
                    ? { ...input, udmParsed, udmValidating, udmError, udmLanguage }
                    : input
            )
        });
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

    // ENCODING/BOM HANDLERS COMMENTED OUT - These are for outputs, not inputs (inputs auto-detect encoding/BOM)
    // Keeping code here in case we want to use it for output panel later
    /*
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
    */

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

    private handleViewUdm(): void {
        this.udmDialogPosition = { x: 0, y: 0 }; // Reset position when opening
        this.udmViewMode = 'pretty'; // Default to pretty view
        this.setState({ udmDialogOpen: true });
    }

    private formatUdm(content: string): string {
        if (!content || !content.trim()) {
            return content;
        }

        try {
            return this.prettyPrintUdm(content);
        } catch (e) {
            // If pretty printing fails, return original
            console.error('[MultiInputPanel] UDM pretty print error:', e);
            return content;
        }
    }

    /**
     * Pretty print UDM format with proper indentation
     * Based on UDM Language Spec v1.0
     */
    private prettyPrintUdm(udm: string): string {
        const lines: string[] = [];
        let indent = 0;
        const tab = '  '; // 2 spaces per indent level

        // Split into tokens while preserving structure
        let i = 0;
        let currentLine = '';

        while (i < udm.length) {
            const char = udm[i];

            // Handle opening braces/brackets
            if (char === '{' || char === '[') {
                // Flush current line if it has content
                if (currentLine.trim()) {
                    lines.push(tab.repeat(indent) + currentLine.trim());
                    currentLine = '';
                }
                // Add opening brace
                lines.push(tab.repeat(indent) + char);
                indent++;
                i++;
                // Skip whitespace after opening
                while (i < udm.length && /\s/.test(udm[i])) i++;
                continue;
            }

            // Handle closing braces/brackets
            if (char === '}' || char === ']') {
                // Flush current line
                if (currentLine.trim()) {
                    lines.push(tab.repeat(indent) + currentLine.trim());
                    currentLine = '';
                }
                indent = Math.max(0, indent - 1);
                lines.push(tab.repeat(indent) + char);
                i++;
                // Handle trailing comma
                while (i < udm.length && /[\s,]/.test(udm[i])) i++;
                continue;
            }

            // Handle commas
            if (char === ',') {
                currentLine += char;
                lines.push(tab.repeat(indent) + currentLine.trim());
                currentLine = '';
                i++;
                // Skip whitespace after comma
                while (i < udm.length && /\s/.test(udm[i])) i++;
                continue;
            }

            // Handle newlines in original
            if (char === '\n') {
                if (currentLine.trim()) {
                    lines.push(tab.repeat(indent) + currentLine.trim());
                    currentLine = '';
                }
                i++;
                continue;
            }

            // Regular character
            currentLine += char;
            i++;
        }

        // Flush any remaining content
        if (currentLine.trim()) {
            lines.push(tab.repeat(indent) + currentLine.trim());
        }

        return lines.join('\n');
    }

    private handleCloseUdmDialog(): void {
        this.isDraggingDialog = false;
        this.setState({ udmDialogOpen: false });
    }

    private handleDragStart(e: React.MouseEvent): void {
        // Don't start drag if clicking on the close button
        if ((e.target as HTMLElement).closest('.utlx-udm-dialog-close')) {
            return;
        }

        this.isDraggingDialog = true;
        const startX = e.clientX;
        const startY = e.clientY;
        const startPosX = this.udmDialogPosition.x;
        const startPosY = this.udmDialogPosition.y;

        // Add document-level mouse move and mouse up handlers
        const handleMouseMove = (moveEvent: MouseEvent) => {
            if (this.isDraggingDialog) {
                this.udmDialogPosition = {
                    x: startPosX + (moveEvent.clientX - startX),
                    y: startPosY + (moveEvent.clientY - startY)
                };
                this.update();
            }
        };

        const handleMouseUp = () => {
            this.isDraggingDialog = false;
            this.update();
            document.removeEventListener('mousemove', handleMouseMove);
            document.removeEventListener('mouseup', handleMouseUp);
        };

        document.addEventListener('mousemove', handleMouseMove);
        document.addEventListener('mouseup', handleMouseUp);

        e.preventDefault();
    }

    private async handleCopyUdmToClipboard(): Promise<void> {
        const activeInput = this.state.inputs.find(i => i.id === this.state.activeInputId);
        if (!activeInput || !activeInput.udmLanguage) {
            this.messageService.error('No UDM data to copy');
            return;
        }

        try {
            // Get the content based on current view mode (raw or pretty)
            const content = this.udmViewMode === 'pretty'
                ? this.formatUdm(activeInput.udmLanguage)
                : activeInput.udmLanguage;

            // Copy to clipboard
            await navigator.clipboard.writeText(content);
            this.messageService.info('UDM copied to clipboard');
        } catch (error) {
            console.error('[MultiInputPanel] Failed to copy UDM to clipboard:', error);
            this.messageService.error('Failed to copy UDM to clipboard');
        }
    }

    private async handleSaveUdm(): Promise<void> {
        const activeInput = this.state.inputs.find(i => i.id === this.state.activeInputId);
        if (!activeInput || !activeInput.udmLanguage) {
            this.messageService.error('No UDM data to save');
            return;
        }

        try {
            // Show save dialog
            const defaultFileName = `${activeInput.name}.udm`;
            const saveUri = await this.fileDialogService.showSaveDialog({
                title: 'Save UDM File',
                saveLabel: 'Save',
                filters: { 'UDM Files': ['udm'], 'All Files': ['*'] },
                inputValue: defaultFileName
            });

            if (!saveUri) {
                // User cancelled
                return;
            }

            // Write file content
            const content = activeInput.udmLanguage;
            await this.fileService.write(
                saveUri,
                content
            );

            this.messageService.info(`UDM saved to ${saveUri.path.base}`);
        } catch (error) {
            this.messageService.error(`Failed to save UDM file: ${error}`);
            console.error('[MultiInputPanel] Error saving UDM:', error);
        }
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

                        // Trigger UDM parsing for instance content
                        await this.validateInput(this.state.activeInputId);
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
     * Note: encoding and BOM are auto-detected for inputs, so we use defaults here
     */
    public getInputDocuments(): InputDocument[] {
        return this.state.inputs
            .filter(input => input.instanceContent)
            .map(input => ({
                id: input.id,
                name: input.name,
                content: input.instanceContent,
                format: this.mapInstanceFormatToDataFormat(input.instanceFormat),
                encoding: 'UTF-8',  // Default, will be auto-detected by backend
                bom: false           // Default, will be auto-detected by backend
            }));
    }

    /**
     * Get all input tabs (for header generation)
     * PUBLIC: Called by frontend contribution for UTLX header generation
     */
    public getAllInputTabs(): Array<{id: string; name: string; format: string; csvHeaders?: boolean; csvDelimiter?: string}> {
        return this.state.inputs.map(input => ({
            id: input.id,
            name: input.name,
            format: input.instanceFormat,
            csvHeaders: input.csvHeaders,
            csvDelimiter: input.csvDelimiter
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

    /**
     * Sync input tabs from parsed UTLX headers (for copy/paste support)
     *
     * This completely syncs the panel to match the UTLX header structure:
     * - UTLX defines the order and structure (source of truth)
     * - Content is preserved where names match
     * - Tabs are reordered to match UTLX order
     * - Extra tabs not in UTLX are removed
     * - Missing tabs in UTLX are created empty
     */
    public syncFromHeaders(parsedInputs: Array<{
        name: string;
        format: string;
        csvHeaders?: boolean;
        csvDelimiter?: string;
        xmlArrays?: string[];
    }>): void {
        console.log('[MultiInputPanelWidget] Syncing from headers:', parsedInputs);
        console.log('[MultiInputPanelWidget] Current inputs before sync:', this.state.inputs.map(i => ({name: i.name, format: i.instanceFormat})));

        const currentInputs = this.state.inputs;

        // Validate for duplicate names in parsed inputs
        const nameSet = new Set<string>();
        const duplicates = new Set<string>();
        parsedInputs.forEach(input => {
            const lowerName = input.name.toLowerCase();
            if (nameSet.has(lowerName)) {
                duplicates.add(input.name);
            } else {
                nameSet.add(lowerName);
            }
        });

        if (duplicates.size > 0) {
            const duplicateList = Array.from(duplicates).join(', ');
            this.messageService.warn(`Duplicate input names detected in UTLX header: ${duplicateList}. Please ensure all input names are unique.`);
            console.warn('[MultiInputPanelWidget] Duplicate input names in parsed headers:', duplicates);
            // Continue anyway, but user has been warned
        }

        // Build new input tabs based on parsed headers
        // The order comes from parsedInputs (UTLX is source of truth)
        const newInputs: InputTab[] = parsedInputs.map((parsedInput, index) => {
            // Try to find existing tab with matching name to preserve content
            let existingInput = currentInputs.find(input => input.name === parsedInput.name);

            // Special case: single input on both sides with different names
            // Treat them as the same input (user renamed or pasted different UTLX)
            if (!existingInput && parsedInputs.length === 1 && currentInputs.length === 1) {
                existingInput = currentInputs[0];
                console.log(`[MultiInputPanelWidget] Single input case: renaming "${existingInput.name}" to "${parsedInput.name}"`);
            }

            if (existingInput) {
                // Found matching name - reuse it and update format/options
                console.log(`[MultiInputPanelWidget] Reusing existing input "${parsedInput.name}" (preserving content)`);
                return {
                    ...existingInput,
                    name: parsedInput.name, // Update to new name from UTLX
                    instanceFormat: parsedInput.format as InstanceFormat,
                    csvHeaders: parsedInput.csvHeaders,
                    csvDelimiter: parsedInput.csvDelimiter
                };
            } else {
                // No matching name - create new empty input
                console.log(`[MultiInputPanelWidget] Creating new input "${parsedInput.name}"`);
                const newId = `input-${Date.now()}-${index}`;
                const schemaFormat: SchemaFormatType =
                    (parsedInput.format === 'xsd' || parsedInput.format === 'jsch' ||
                     parsedInput.format === 'avro' || parsedInput.format === 'proto')
                    ? parsedInput.format as SchemaFormatType
                    : 'jsch';
                return {
                    id: newId,
                    name: parsedInput.name,
                    instanceContent: '',
                    instanceFormat: parsedInput.format as InstanceFormat,
                    schemaContent: '',
                    schemaFormat: schemaFormat,
                    csvHeaders: parsedInput.csvHeaders,
                    csvDelimiter: parsedInput.csvDelimiter
                };
            }
        });

        // Determine which input should be active
        // Priority: 1) Previously active input if it still exists (by name)
        //          2) First input
        let newActiveInputId = '';
        if (newInputs.length > 0) {
            const currentActive = currentInputs.find(input => input.id === this.state.activeInputId);
            if (currentActive) {
                // Find the new input with the same name as the previously active one
                const matchingNew = newInputs.find(input => input.name === currentActive.name);
                newActiveInputId = matchingNew ? matchingNew.id : newInputs[0].id;
            } else {
                newActiveInputId = newInputs[0].id;
            }
        }

        // Update state - this completely replaces the inputs array
        this.setState({
            inputs: newInputs,
            activeInputId: newActiveInputId
        });

        console.log('[MultiInputPanelWidget] Sync complete:', newInputs.map(i => ({name: i.name, format: i.instanceFormat})));
    }
}
