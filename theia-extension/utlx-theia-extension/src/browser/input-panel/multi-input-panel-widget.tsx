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

export type InstanceFormat = 'csv' | 'json' | 'xml' | 'yaml';
export type SchemaFormatType = 'xsd' | 'jsch' | 'avro' | 'proto';

export interface InputTab {
    id: string;
    name: string;
    instanceContent: string;
    instanceFormat: InstanceFormat;
    schemaContent: string;
    schemaFormat: SchemaFormatType;
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
    static readonly BUILD_VERSION = '2025-11-08T06:00:00Z'; // Build version for deployment verification

    @inject(UTLX_SERVICE_SYMBOL) @optional()
    protected readonly utlxService?: UTLXService;

    @inject(MessageService)
    protected readonly messageService!: MessageService;

    private state: MultiInputPanelState = {
        mode: UTLXMode.RUNTIME,
        inputs: [{
            id: 'input-1',
            name: 'Input 1',
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

        // Listen for mode changes
        window.addEventListener('utlx-mode-changed', ((event: CustomEvent) => {
            const newMode = event.detail.mode;
            this.setState({
                mode: newMode,
                activeSubTab: newMode === UTLXMode.DESIGN_TIME ? 'instance' : 'instance'
            });
        }) as EventListener);
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

                    {/* Format Selector */}
                    <div className='utlx-panel-toolbar'>
                        <label>
                            Format:
                            <select
                                value={currentFormat}
                                onChange={(e) => this.handleFormatChange(e.target.value as any)}
                                disabled={loading}
                            >
                                {isDesignTime && activeSubTab === 'schema' ? (
                                    <>
                                        {this.getSchemaFormatOptions(activeInput.instanceFormat)}
                                    </>
                                ) : (
                                    <>
                                        <option value='csv'>CSV</option>
                                        <option value='json'>JSON</option>
                                        <option value='xml'>XML</option>
                                        <option value='yaml'>YAML</option>
                                        {isDesignTime && (
                                            <>
                                                <option value='xsd'>XSD</option>
                                                <option value='jsch'>JSON Schema</option>
                                                <option value='avro'>Avro</option>
                                                <option value='proto'>Protobuf</option>
                                            </>
                                        )}
                                    </>
                                )}
                            </select>
                        </label>
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
     * According to design doc:
     * - json → jsch | avro (dropdown, jsch default)
     * - xml → xsd
     * - yaml → jsch | avro (dropdown, jsch default)
     * - csv → tsch (show "//tsch not implemented yet")
     * - xsd, jsch, avro, proto → BLUR the schema tab
     */
    private getSchemaFormatOptions(instanceFormat: InstanceFormat | SchemaFormatType): React.ReactNode[] {
        switch (instanceFormat) {
            case 'json':
            case 'yaml':
                return [
                    <option key='jsch' value='jsch'>JSON Schema</option>,
                    <option key='avro' value='avro'>Avro</option>
                ];
            case 'xml':
                return [<option key='xsd' value='xsd'>XSD</option>];
            case 'csv':
                return [<option key='tsch' value='tsch'>TSCH (not implemented yet)</option>];
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
            switch (format) {
                case 'xml':
                    return '<root>\n  <element>data</element>\n</root>';
                case 'json':
                    return '{\n  "key": "value"\n}';
                case 'yaml':
                    return 'key: value\nlist:\n  - item1\n  - item2';
                case 'csv':
                    return 'name,age,email\nJohn,30,john@example.com';
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
            name: `Input ${this.nextInputId}`,
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
    }

    private handleRenameInput(inputId: string, newName: string): void {
        this.setState({
            inputs: this.state.inputs.map(input =>
                input.id === inputId ? { ...input, name: newName } : input
            )
        });
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
        // TODO: Implement file loading
        this.messageService.info('File loading not yet implemented');
    }

    private setState(partial: Partial<MultiInputPanelState>): void {
        this.state = { ...this.state, ...partial };
        this.update();
    }

    /**
     * Get all input documents (for runtime mode)
     */
    getInputDocuments(): InputDocument[] {
        return this.state.inputs
            .filter(input => input.instanceContent)
            .map(input => ({
                id: input.id,
                name: input.name,
                content: input.instanceContent,
                format: this.mapInstanceFormatToDataFormat(input.instanceFormat)
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
            'yaml': 'yaml'
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
            'jsch': 'json-schema',
            'avro': 'avro-schema',
            'proto': 'protobuf'
        };
        return formatMap[format] || 'json-schema';
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
