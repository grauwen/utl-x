/**
 * UTLX Editor Widget
 *
 * Monaco-based editor for editing UTLX transformation code with LSP support.
 * This is the MIDDLE pane in the 3-pane layout (Input | Editor | Output).
 */

import * as React from '@theia/core/shared/react';
import { injectable, inject, postConstruct, optional } from '@theia/core/shared/inversify';
import { ReactWidget, Message } from '@theia/core/lib/browser';
import { MonacoEditorProvider } from '@theia/monaco/lib/browser/monaco-editor-provider';
import { MonacoEditor } from '@theia/monaco/lib/browser/monaco-editor';
import URI from '@theia/core/lib/common/uri';
import { DisposableCollection } from '@theia/core/lib/common/disposable';

export const UTLX_EDITOR_WIDGET_ID = 'utlx-editor';

@injectable()
export class UTLXEditorWidget extends ReactWidget {
    static readonly ID = UTLX_EDITOR_WIDGET_ID;
    static readonly LABEL = 'UTLX Editor';

    @inject(MonacoEditorProvider) @optional()
    protected readonly editorProvider?: MonacoEditorProvider;

    protected editor: MonacoEditor | undefined;
    protected editorContainer: HTMLDivElement | undefined;
    protected toDispose = new DisposableCollection();

    constructor() {
        super();
        this.id = UTLX_EDITOR_WIDGET_ID;
        this.title.label = 'UTLX Transformation';
        this.title.caption = 'UTLX Transformation Editor';
        this.title.closable = false;
        this.addClass('utlx-editor-widget');
    }

    @postConstruct()
    protected init(): void {
        this.update();
    }

    protected onActivateRequest(msg: Message): void {
        super.onActivateRequest(msg);
        if (this.editor) {
            this.editor.focus();
        }
    }

    protected onAfterAttach(msg: Message): void {
        super.onAfterAttach(msg);
        this.createEditor();
    }

    protected onResize(msg: any): void {
        super.onResize(msg);
        if (this.editor) {
            this.editor.refresh();
        }
    }

    protected onBeforeDetach(msg: Message): void {
        super.onBeforeDetach(msg);
        this.disposeEditor();
    }

    /**
     * Create Monaco editor instance
     */
    protected async createEditor(): Promise<void> {
        if (this.editorContainer && this.editorProvider) {
            // Create a virtual file URI for the UTLX content
            const uri = new URI('inmemory://utlx-editor/transformation.utlx');

            // Create Monaco editor with UTLX language support
            this.editor = await this.editorProvider.get(uri);

            // Configure editor options
            this.editor.getControl().updateOptions({
                lineNumbers: 'on',
                minimap: { enabled: true },
                scrollBeyondLastLine: false,
                wordWrap: 'on',
                fontSize: 14,
                folding: true,
                renderWhitespace: 'selection',
                automaticLayout: true,
                tabSize: 2,
                insertSpaces: true,
                theme: 'vs-dark'
            });

            // Set initial content
            this.setContent(this.getDefaultContent());

            // Attach editor to container
            const editorNode = this.editor.getControl().getDomNode();
            if (editorNode) {
                this.editorContainer.appendChild(editorNode);
                this.editor.refresh();
            }

            // Listen for content changes
            this.toDispose.push(
                this.editor.getControl().onDidChangeModelContent(() => {
                    this.onContentChanged();
                })
            );

            // Focus editor
            this.editor.focus();
        }
    }

    /**
     * Dispose editor instance
     */
    protected disposeEditor(): void {
        if (this.editor) {
            this.toDispose.dispose();
            this.editor.dispose();
            this.editor = undefined;
        }
    }

    /**
     * Get default UTLX content
     */
    protected getDefaultContent(): string {
        return `%utlx 1.0
input json
output json
---
# Welcome to UTLX!
#
# This is a sample transformation.
# Edit the code below and press Execute to run.

output: {
  message: "Hello from UTLX!",
  input: $input
}
`;
    }

    /**
     * Get current editor content
     */
    public getContent(): string {
        if (this.editor) {
            return this.editor.getControl().getValue();
        }
        return '';
    }

    /**
     * Set editor content
     */
    public setContent(content: string): void {
        if (this.editor) {
            this.editor.getControl().setValue(content);
        }
    }

    /**
     * Clear editor content
     */
    public clearContent(): void {
        this.setContent(this.getDefaultContent());
    }

    /**
     * Handler for content changes
     */
    protected onContentChanged(): void {
        // Notify parent workbench that content has changed
        // This will be used for validation/execution
        this.node.dispatchEvent(new CustomEvent('utlx-content-changed', {
            bubbles: true,
            detail: { content: this.getContent() }
        }));
    }

    /**
     * Load UTLX file
     */
    public async loadFile(content: string): Promise<void> {
        this.setContent(content);
    }

    /**
     * Save UTLX file
     */
    public async saveFile(): Promise<string> {
        return this.getContent();
    }

    protected render(): React.ReactNode {
        return (
            <div className='utlx-editor-container'>
                <div className='utlx-editor-header'>
                    <div className='utlx-editor-title'>
                        <span className='codicon codicon-code'></span>
                        <span>UTLX Transformation</span>
                    </div>
                    <div className='utlx-editor-toolbar'>
                        <button
                            className='theia-button secondary'
                            title='Load UTLX File'
                            onClick={() => this.handleLoadFile()}
                        >
                            <span className='codicon codicon-folder-opened'></span>
                            Load
                        </button>
                        <button
                            className='theia-button secondary'
                            title='Save UTLX File'
                            onClick={() => this.handleSaveFile()}
                        >
                            <span className='codicon codicon-save'></span>
                            Save
                        </button>
                        <button
                            className='theia-button secondary'
                            title='Clear Editor'
                            onClick={() => this.clearContent()}
                        >
                            <span className='codicon codicon-clear-all'></span>
                            Clear
                        </button>
                    </div>
                </div>
                <div
                    className='utlx-editor-content'
                    ref={container => {
                        if (container && !this.editorContainer) {
                            this.editorContainer = container;
                            this.createEditor();
                        }
                    }}
                />
                <div className='utlx-editor-footer'>
                    <span className='utlx-editor-info'>
                        UTLX Editor | Connected to LSP on localhost:7777
                    </span>
                </div>
            </div>
        );
    }

    /**
     * Handle load file button click
     */
    protected async handleLoadFile(): Promise<void> {
        // Open file dialog
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.utlx';
        input.onchange = async (e: Event) => {
            const target = e.target as HTMLInputElement;
            const file = target.files?.[0];
            if (file) {
                const content = await file.text();
                this.loadFile(content);
            }
        };
        input.click();
    }

    /**
     * Handle save file button click
     */
    protected async handleSaveFile(): Promise<void> {
        const content = this.saveFile();

        // Create download link
        const blob = new Blob([await content], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'transformation.utlx';
        a.click();
        URL.revokeObjectURL(url);
    }

    dispose(): void {
        this.disposeEditor();
        super.dispose();
    }
}
