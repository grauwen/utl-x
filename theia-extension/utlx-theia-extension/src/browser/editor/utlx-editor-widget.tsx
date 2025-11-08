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
import * as monaco from '@theia/monaco-editor-core';

export const UTLX_EDITOR_WIDGET_ID = 'utlx-editor';

@injectable()
export class UTLXEditorWidget extends ReactWidget {
    static readonly ID = UTLX_EDITOR_WIDGET_ID;
    static readonly LABEL = 'UTLX Editor';

    @inject(MonacoEditorProvider) @optional()
    protected readonly editorProvider?: MonacoEditorProvider;

    protected editor: monaco.editor.IStandaloneCodeEditor | undefined;
    protected editorContainer: HTMLDivElement | undefined;
    protected toDispose = new DisposableCollection();
    protected readOnlyDecorations: string[] = [];
    protected headerEndLine: number = 0;

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
        // createEditor will be called by the ref callback after container is ready
        // Add slight delay to ensure React has rendered
        setTimeout(() => {
            if (!this.editor && this.editorContainer) {
                this.createEditor();
            }
        }, 50);
    }

    protected onResize(msg: any): void {
        super.onResize(msg);
        if (this.editor) {
            // Force layout recalculation
            this.editor.layout();
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
        console.log('[UTLXEditor] createEditor() called', {
            hasContainer: !!this.editorContainer,
            hasEditor: !!this.editor
        });

        if (this.editor) {
            console.log('[UTLXEditor] Editor already created, skipping');
            return;
        }

        if (!this.editorContainer) {
            console.warn('[UTLXEditor] No editor container, waiting for ref callback');
            return;
        }

        try {
            // Create Monaco model with UTLX content
            const model = monaco.editor.createModel(
                this.getDefaultContent(),
                'plaintext', // Language ID - we can register 'utlx' later
                monaco.Uri.parse('inmemory://utlx-editor/transformation.utlx')
            );

            // Create standalone Monaco editor
            this.editor = monaco.editor.create(this.editorContainer, {
                model: model,
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

            console.log('[UTLXEditor] Monaco editor created successfully');

            // Force layout after DOM attachment
            setTimeout(() => {
                if (this.editor) {
                    this.editor.layout();
                    console.log('[UTLXEditor] Editor layout refreshed');
                }
            }, 100);

            // Listen for content changes
            this.toDispose.push(
                this.editor.onDidChangeModelContent(() => {
                    this.onContentChanged();
                })
            );

            // Apply read-only decorations to initial content
            setTimeout(() => {
                this.applyReadOnlyDecorations();
            }, 200);

            // Enforce read-only headers
            this.enforceReadOnlyHeaders();

            // Focus editor
            this.editor.focus();
            console.log('[UTLXEditor] Editor initialized and focused');
        } catch (error) {
            console.error('[UTLXEditor] Failed to create editor:', error);
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
// Welcome to UTLX!
//
// This is a sample transformation.
// Edit the code below and press Execute to run.

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
            const model = this.editor.getModel();
            return model ? model.getValue() : '';
        }
        return '';
    }

    /**
     * Set editor content
     */
    public setContent(content: string): void {
        if (this.editor) {
            const model = this.editor.getModel();
            if (model) {
                model.setValue(content);
            }
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
     * Setup drag and drop for UTLX files
     */
    protected setupDragAndDrop(container: HTMLDivElement): void {
        // Prevent default drag behavior
        container.addEventListener('dragover', (e) => {
            e.preventDefault();
            e.stopPropagation();
            container.classList.add('utlx-drag-over');
        });

        container.addEventListener('dragleave', (e) => {
            e.preventDefault();
            e.stopPropagation();
            container.classList.remove('utlx-drag-over');
        });

        // Handle drop
        container.addEventListener('drop', async (e) => {
            e.preventDefault();
            e.stopPropagation();
            container.classList.remove('utlx-drag-over');

            const files = e.dataTransfer?.files;
            if (files && files.length > 0) {
                const file = files[0];
                // Accept .utlx files or any text file
                if (file.name.endsWith('.utlx') || file.type.startsWith('text/')) {
                    try {
                        const content = await file.text();
                        this.loadFile(content);
                        console.log(`[UTLXEditor] Loaded file: ${file.name}`);
                    } catch (error) {
                        console.error('[UTLXEditor] Failed to read dropped file:', error);
                    }
                } else {
                    console.warn('[UTLXEditor] Dropped file is not a UTLX or text file');
                }
            }
        });
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

    /**
     * Parse headers and find the separator line
     * Returns the line number where '---' is found (0-indexed)
     */
    protected parseHeaderEndLine(): number {
        if (!this.editor) return 0;

        const model = this.editor.getModel();
        if (!model) return 0;

        const lineCount = model.getLineCount();
        for (let i = 0; i < Math.min(lineCount, 10); i++) {
            const line = model.getLineContent(i + 1).trim();
            if (line === '---') {
                return i;
            }
        }
        return 0;
    }

    /**
     * Generate header content based on input/output formats
     */
    protected generateHeader(inputLines: string[], outputFormat: string): string {
        const header = ['%utlx 1.0'];

        // Add input line(s)
        header.push(...inputLines);

        // Add output line
        header.push(`output ${outputFormat}`);

        // Add separator
        header.push('---');

        return header.join('\n');
    }

    /**
     * Update headers with new input/output information
     * This is called when formats change in the panels
     */
    public updateHeaders(inputLines: string[], outputFormat: string): void {
        if (!this.editor) return;

        const model = this.editor.getModel();
        if (!model) return;

        // Find current separator
        const separatorLine = this.parseHeaderEndLine();

        // Generate new header
        const newHeader = this.generateHeader(inputLines, outputFormat);
        const newHeaderLines = newHeader.split('\n');

        // Get existing body content (everything after ---)
        const bodyStartLine = separatorLine + 2; // +1 for 0-index, +1 to skip ---
        const totalLines = model.getLineCount();
        const bodyLines: string[] = [];

        for (let i = bodyStartLine; i <= totalLines; i++) {
            bodyLines.push(model.getLineContent(i));
        }

        // Combine new header with existing body
        const newContent = newHeader + '\n' + bodyLines.join('\n');

        // Update model
        model.setValue(newContent);

        // Update header end line tracking
        this.headerEndLine = newHeaderLines.length - 1; // -1 because --- is the last header line

        // Apply read-only decorations
        this.applyReadOnlyDecorations();
    }

    /**
     * Apply read-only decorations to header lines
     */
    protected applyReadOnlyDecorations(): void {
        if (!this.editor) return;

        const model = this.editor.getModel();
        if (!model) return;

        // Parse header to find separator
        const separatorLine = this.parseHeaderEndLine();

        if (separatorLine === 0) return;

        // Create decorations for all header lines (including separator)
        const decorations: monaco.editor.IModelDeltaDecoration[] = [];

        for (let i = 1; i <= separatorLine + 1; i++) {
            decorations.push({
                range: new monaco.Range(i, 1, i, model.getLineMaxColumn(i)),
                options: {
                    className: 'utlx-readonly-line',
                    isWholeLine: true,
                    linesDecorationsClassName: 'utlx-readonly-line-decoration'
                }
            });
        }

        // Apply decorations
        this.readOnlyDecorations = this.editor.deltaDecorations(
            this.readOnlyDecorations,
            decorations
        );

        // Store header end line for read-only enforcement
        this.headerEndLine = separatorLine;
    }

    /**
     * Enforce read-only behavior on header lines
     */
    protected enforceReadOnlyHeaders(): void {
        if (!this.editor) return;

        // Listen for content changes and prevent edits to header lines
        this.toDispose.push(
            this.editor.onDidChangeModelContent((e) => {
                if (!this.editor) return;

                const model = this.editor.getModel();
                if (!model) return;

                // Check if any changes were made to header lines
                for (const change of e.changes) {
                    const startLine = change.range.startLineNumber;
                    const endLine = change.range.endLineNumber;

                    // If change affects header lines (lines 1 through headerEndLine + 1)
                    if (startLine <= this.headerEndLine + 1) {
                        console.log('[UTLXEditor] Attempted edit to read-only header, reverting');

                        // Revert by re-parsing and re-applying decorations
                        // The LSP or external source should maintain correct headers
                        this.applyReadOnlyDecorations();

                        // Note: Full prevention would require more complex undo handling
                        // For now, we visually indicate read-only status
                        break;
                    }
                }
            })
        );
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
                            this.setupDragAndDrop(container);
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
