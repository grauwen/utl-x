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
    content: string;
    format: DataFormat | SchemaFormat;
    fileName: string;
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
        content: '',
        format: 'json',
        fileName: '',
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
            this.setState({ mode: config.mode });
        });
    }


    protected render(): React.ReactNode {
        const { mode, content, format, fileName, loading } = this.state;

        return (
            <div className='utlx-input-panel-container'>
                <div className='utlx-panel-header'>
                    <h3>{mode === UTLXMode.DESIGN_TIME ? 'Input Schema' : 'Input Data'}</h3>
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
                            disabled={loading || !content}
                            title='Clear input'
                        >
                            🗑️ Clear
                        </button>
                    </div>
                </div>

                <div className='utlx-panel-toolbar'>
                    <label>
                        Format:
                        <select
                            value={format}
                            onChange={(e) => this.handleFormatChange((e.target as HTMLSelectElement).value as any)}
                            disabled={loading}
                        >
                            {mode === UTLXMode.DESIGN_TIME ? (
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

                    {fileName && (
                        <span className='utlx-file-name' title={fileName}>
                            📄 {fileName}
                        </span>
                    )}
                </div>

                <div className='utlx-panel-content'>
                    <textarea
                        className='utlx-input-editor'
                        value={content}
                        onChange={(e) => this.handleContentChange((e.target as HTMLTextAreaElement).value)}
                        placeholder={this.getPlaceholder()}
                        disabled={loading}
                        spellCheck={false}
                    />
                </div>

                <div className='utlx-panel-footer'>
                    <span className='utlx-status'>
                        {loading ? '⏳ Loading...' : content ? `${content.length} characters` : 'No input loaded'}
                    </span>
                </div>
            </div>
        );
    }

    private getPlaceholder(): string {
        const { mode, format } = this.state;

        if (mode === UTLXMode.DESIGN_TIME) {
            switch (format) {
                case 'xsd':
                    return '<?xml version="1.0"?>\n<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">\n  <!-- Define your XML schema here -->\n</xs:schema>';
                case 'json-schema':
                    return '{\n  "$schema": "http://json-schema.org/draft-07/schema#",\n  "type": "object",\n  "properties": {\n    ...\n  }\n}';
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

                this.setState({
                    content,
                    fileName,
                    loading: false
                });

                this.messageService.info(`Loaded: ${fileName}`);
            } else {
                this.setState({ loading: false });
            }
        } catch (error) {
            this.messageService.error(`Failed to load file: ${error}`);
            this.setState({ loading: false });
        }
    }

    private handleContentChange(content: string): void {
        this.setState({ content });
    }

    private handleFormatChange(format: DataFormat | SchemaFormat): void {
        this.setState({ format });
    }

    private handleClear(): void {
        this.setState({
            content: '',
            fileName: ''
        });
    }

    private setState(partial: Partial<InputPanelState>): void {
        this.state = { ...this.state, ...partial };
        this.update();
    }

    /**
     * Get current input document (for runtime mode)
     */
    getInputDocument(): InputDocument | null {
        if (this.state.mode !== UTLXMode.RUNTIME || !this.state.content) {
            return null;
        }

        return {
            id: 'input1',
            name: this.state.fileName || 'input',
            content: this.state.content,
            format: this.state.format as DataFormat
        };
    }

    /**
     * Get current schema document (for design-time mode)
     */
    getSchemaDocument(): SchemaDocument | null {
        if (this.state.mode !== UTLXMode.DESIGN_TIME || !this.state.content) {
            return null;
        }

        return {
            format: this.state.format as SchemaFormat,
            content: this.state.content
        };
    }

    /**
     * Set mode from external source
     */
    setMode(mode: UTLXMode): void {
        if (mode !== this.state.mode) {
            this.setState({
                mode,
                content: '',
                fileName: '',
                format: mode === UTLXMode.DESIGN_TIME ? 'xsd' : 'json'
            });
        }
    }

    /**
     * Load content programmatically
     */
    loadContent(content: string, format: DataFormat | SchemaFormat, fileName?: string): void {
        this.setState({
            content,
            format,
            fileName: fileName || ''
        });
    }
}
