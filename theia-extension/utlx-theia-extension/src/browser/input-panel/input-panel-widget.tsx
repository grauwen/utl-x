/**
 * Input Panel Widget
 *
 * Left panel for loading:
 * - Runtime Mode: Input data (XML, JSON, CSV, YAML)
 * - Design-Time Mode: Input schema (XSD, JSON Schema)
 */

import * as React from 'react';
import { injectable, inject, postConstruct, optional } from 'inversify';
import { ReactWidget } from '@theia/core/lib/browser/widgets/react-widget';
import { MessageService } from '@theia/core';
import { FileDialogService } from '@theia/filesystem/lib/browser';
import {
    UTLXService,
    UTLX_SERVICE_SYMBOL,
    UTLXMode,
    InputDocument,
    SchemaDocument,
    DataFormat,
    SchemaFormat,
    INPUT_PANEL_ID
} from '../../common/protocol';

export interface InputPanelState {
    mode: UTLXMode;
    activeTab: 'instance' | 'schema';
    instanceContent: string;
    schemaContent: string;
    instanceFormat: DataFormat;
    schemaFormat: SchemaFormat;
    instanceFileName: string;
    schemaFileName: string;
    loading: boolean;
}

@injectable()
export class InputPanelWidget extends ReactWidget {
    static readonly ID = INPUT_PANEL_ID;
    static readonly LABEL = 'Input';

    @inject(UTLX_SERVICE_SYMBOL)
    protected readonly utlxService!: UTLXService;

    @inject(MessageService)
    protected readonly messageService!: MessageService;

    @inject(FileDialogService) @optional()
    protected readonly fileDialog?: FileDialogService;

    private state: InputPanelState = {
        mode: UTLXMode.RUNTIME,
        activeTab: 'instance',
        instanceContent: '',
        schemaContent: '',
        instanceFormat: 'json',
        schemaFormat: 'xsd',
        instanceFileName: '',
        schemaFileName: '',
        loading: false
    };

    constructor() {
        super();
        this.id = InputPanelWidget.ID;
        this.title.label = InputPanelWidget.LABEL;
        this.title.caption = 'Input Data/Schema';
        this.title.closable = false;
        this.addClass('utlx-input-panel');
    }

    @postConstruct()
    protected init(): void {
        this.update();
        // Subscribe to mode changes
        this.utlxService.getMode().then(config => {
            this.setState({
                mode: config.mode,
                activeTab: config.mode === UTLXMode.DESIGN_TIME ? 'schema' : 'instance'
            });
        });

        // Listen for mode changes from toolbar
        window.addEventListener('utlx-mode-changed', ((event: CustomEvent) => {
            const newMode = event.detail.mode;
            this.setState({
                mode: newMode,
                activeTab: newMode === UTLXMode.DESIGN_TIME ? 'schema' : 'instance'
            });
        }) as EventListener);
    }


    protected render(): React.ReactNode {
        const {
            mode,
            activeTab,
            instanceContent,
            schemaContent,
            instanceFormat,
            schemaFormat,
            instanceFileName,
            schemaFileName,
            loading
        } = this.state;

        // Get current content, format, and fileName based on active tab
        const currentContent = activeTab === 'instance' ? instanceContent : schemaContent;
        const currentFormat = activeTab === 'instance' ? instanceFormat : schemaFormat;
        const currentFileName = activeTab === 'instance' ? instanceFileName : schemaFileName;

        return (
            <div className='utlx-input-panel-container'>
                <div className='utlx-panel-header'>
                    <h3>Input</h3>
                    <div className='utlx-panel-actions'>
                        <button
                            onClick={() => this.handleLoadFile()}
                            disabled={loading}
                            title='Load from file'
                        >
                            📁 Load
                        </button>
                        <button
                            onClick={() => this.handleClear()}
                            disabled={loading || !currentContent}
                            title='Clear input'
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
                        disabled={loading}
                    >
                        Instance
                    </button>
                    <button
                        className={`utlx-tab ${activeTab === 'schema' ? 'active' : ''}`}
                        onClick={() => this.handleTabSwitch('schema')}
                        disabled={loading}
                    >
                        Schema
                    </button>
                </div>

                <div className='utlx-panel-toolbar'>
                    <label>
                        Format:
                        <select
                            value={currentFormat}
                            onChange={(e) => this.handleFormatChange((e.target as HTMLSelectElement).value as any)}
                            disabled={loading}
                        >
                            {activeTab === 'schema' ? (
                                <>
                                    <option value='xsd'>XSD</option>
                                    <option value='json-schema'>JSON Schema</option>
                                    <option value='avro-schema'>Avro Schema</option>
                                    <option value='protobuf'>Protobuf</option>
                                </>
                            ) : (
                                <>
                                    <option value='xml'>XML</option>
                                    <option value='json'>JSON</option>
                                    <option value='yaml'>YAML</option>
                                    <option value='csv'>CSV</option>
                                    <option value='auto'>Auto-detect</option>
                                </>
                            )}
                        </select>
                    </label>

                    {currentFileName && (
                        <span className='utlx-file-name' title={currentFileName}>
                            📄 {currentFileName}
                        </span>
                    )}
                </div>

                <div className='utlx-panel-content'>
                    <textarea
                        className='utlx-input-editor'
                        value={currentContent}
                        onChange={(e) => this.handleContentChange((e.target as HTMLTextAreaElement).value)}
                        placeholder={this.getPlaceholder()}
                        disabled={loading}
                        spellCheck={false}
                    />
                </div>

                <div className='utlx-panel-footer'>
                    <span className='utlx-status'>
                        {loading ? '⏳ Loading...' : currentContent ? `${currentContent.length} characters` : 'No input loaded'}
                    </span>
                </div>
            </div>
        );
    }

    private getPlaceholder(): string {
        const { activeTab, instanceFormat, schemaFormat } = this.state;

        if (activeTab === 'schema') {
            switch (schemaFormat) {
                case 'xsd':
                    return '<?xml version="1.0"?>\n<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">\n  <!-- Define your XML schema here -->\n</xs:schema>';
                case 'json-schema':
                    return '{\n  "$schema": "http://json-schema.org/draft-07/schema#",\n  "type": "object",\n  "properties": {\n    ...\n  }\n}';
                default:
                    return 'Paste or load your schema here...';
            }
        } else {
            switch (instanceFormat) {
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

    private async handleLoadFile(): Promise<void> {
        if (!this.fileDialog) {
            this.messageService.warn('File dialog service not available');
            return;
        }

        try {
            this.setState({ loading: true });

            const uri = await this.fileDialog.showOpenDialog({
                title: 'Load Input File',
                canSelectMany: false
            });

            if (uri) {
                // TODO: Read file content using filesystem service
                const fileName = uri.path.base;
                const content = ''; // TODO: Load actual content

                if (this.state.activeTab === 'instance') {
                    this.setState({
                        instanceContent: content,
                        instanceFileName: fileName,
                        loading: false
                    });
                } else {
                    this.setState({
                        schemaContent: content,
                        schemaFileName: fileName,
                        loading: false
                    });
                }

                this.messageService.info(`Loaded: ${fileName}`);
            } else {
                this.setState({ loading: false });
            }
        } catch (error) {
            this.messageService.error(`Failed to load file: ${error}`);
            this.setState({ loading: false });
        }
    }

    private handleTabSwitch(tab: 'instance' | 'schema'): void {
        this.setState({ activeTab: tab });
    }

    private handleContentChange(content: string): void {
        if (this.state.activeTab === 'instance') {
            this.setState({ instanceContent: content });
        } else {
            this.setState({ schemaContent: content });
        }
    }

    private handleFormatChange(format: DataFormat | SchemaFormat): void {
        if (this.state.activeTab === 'instance') {
            this.setState({ instanceFormat: format as DataFormat });
        } else {
            this.setState({ schemaFormat: format as SchemaFormat });
        }
    }

    private handleClear(): void {
        if (this.state.activeTab === 'instance') {
            this.setState({
                instanceContent: '',
                instanceFileName: ''
            });
        } else {
            this.setState({
                schemaContent: '',
                schemaFileName: ''
            });
        }
    }

    private setState(partial: Partial<InputPanelState>): void {
        this.state = { ...this.state, ...partial };
        this.update();
    }

    /**
     * Get current input document (for runtime mode)
     */
    getInputDocument(): InputDocument | null {
        if (!this.state.instanceContent) {
            return null;
        }

        return {
            id: 'input1',
            name: this.state.instanceFileName || 'input',
            content: this.state.instanceContent,
            format: this.state.instanceFormat
        };
    }

    /**
     * Get current schema document (for design-time mode)
     */
    getSchemaDocument(): SchemaDocument | null {
        if (!this.state.schemaContent) {
            return null;
        }

        return {
            format: this.state.schemaFormat,
            content: this.state.schemaContent
        };
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
     * Load content programmatically
     */
    loadContent(content: string, format: DataFormat | SchemaFormat, fileName?: string): void {
        // Determine if this is instance or schema data based on format
        const isSchemaFormat = ['xsd', 'json-schema', 'avro-schema', 'protobuf'].includes(format);

        if (isSchemaFormat) {
            this.setState({
                schemaContent: content,
                schemaFormat: format as SchemaFormat,
                schemaFileName: fileName || ''
            });
        } else {
            this.setState({
                instanceContent: content,
                instanceFormat: format as DataFormat,
                instanceFileName: fileName || ''
            });
        }
    }
}
