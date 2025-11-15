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
import { FileDialogService, SaveFileDialogProps } from '@theia/filesystem/lib/browser';
import { FileService } from '@theia/filesystem/lib/browser/file-service';
import * as monaco from '@theia/monaco-editor-core';
import { UTLXEventService } from '../events/utlx-event-service';
import { parseUTLXHeaders } from '../parser/utlx-header-parser';
import { FunctionBuilderDialog } from '../function-builder/function-builder-dialog';
import { UTLXService } from '../../common/protocol';
import { UTLX_SERVICE_SYMBOL } from '../../common/protocol';
import { FunctionInfo, OperatorInfo } from '../../common/protocol';
import { analyzeInsertionContext, InsertionContext } from '../function-builder/context-analyzer';

export const UTLX_EDITOR_WIDGET_ID = 'utlx-editor';

@injectable()
export class UTLXEditorWidget extends ReactWidget {
    static readonly ID = UTLX_EDITOR_WIDGET_ID;
    static readonly LABEL = 'UTLX Editor';

    @inject(MonacoEditorProvider) @optional()
    protected readonly editorProvider?: MonacoEditorProvider;

    @inject(UTLXEventService)
    protected readonly eventService!: UTLXEventService;

    @inject(UTLX_SERVICE_SYMBOL)
    protected readonly utlxService!: UTLXService;

    @inject(FileDialogService)
    protected readonly fileDialogService!: FileDialogService;

    @inject(FileService)
    protected readonly fileService!: FileService;

    protected editor: monaco.editor.IStandaloneCodeEditor | undefined;
    protected editorContainer: HTMLDivElement | undefined;
    protected toDispose = new DisposableCollection();
    protected readOnlyDecorations: string[] = [];
    protected headerEndLine: number = 0;
    protected savedHeaderContent: string[] = [];
    protected isUpdatingHeaders: boolean = false;
    protected contentChangeDebounceTimer: number | undefined;
    protected readonly CONTENT_CHANGE_DEBOUNCE_MS = 500; // 500ms delay
    protected inputNamesFromHeaders: string[] = []; // Input names from UTLX headers
    protected inputUdmMap: Map<string, string> = new Map(); // inputName -> UDM language
    protected inputFormatsMap: Map<string, string> = new Map(); // inputName -> format (json, csv, xml, etc.)

    // Function Builder state
    protected showFunctionBuilderDialog: boolean = false;
    protected functionBuilderFunctions: FunctionInfo[] = [];
    protected functionBuilderOperators: OperatorInfo[] = [];

    constructor() {
        super();
        this.id = UTLX_EDITOR_WIDGET_ID;
        this.title.label = 'UTLX Transformation';
        this.title.caption = 'UTLX Transformation Editor';
        this.title.closable = false;
        this.title.iconClass = 'codicon codicon-arrow-swap';
        this.addClass('utlx-editor-widget');
    }

    @postConstruct()
    protected init(): void {
        this.update();

        // ===== Mode Events =====
        this.eventService.onModeChanged(event => {
            console.log('[UTLXEditorWidget] 📡 RECEIVED: Mode changed:', event);
        });

        // ===== Input Management Events =====
        this.eventService.onInputFormatChanged(event => {
            console.log('[UTLXEditorWidget] 📡 RECEIVED: Input format changed:', event);
            // Header updates handled by UTLXFrontendContribution
        });

        this.eventService.onInputNameChanged(event => {
            console.log('[UTLXEditorWidget] 📡 RECEIVED: Input name changed:', event);
            this.renameInputReferences(event.oldName, event.newName);
        });

        this.eventService.onInputAdded(event => {
            console.log('[UTLXEditorWidget] 📡 RECEIVED: Input added:', event);
        });

        this.eventService.onInputDeleted(event => {
            console.log('[UTLXEditorWidget] 📡 RECEIVED: Input deleted:', event);
        });

        this.eventService.onInputUdmUpdated(event => {
            console.log('[UTLXEditorWidget] 📡 RECEIVED: Input UDM updated:', {
                inputId: event.inputId,
                inputName: event.inputName,
                udmLanguageLength: event.udmLanguage.length,
                format: event.format
            });
            // Store UDM and format for autocomplete and Function Builder
            this.inputUdmMap.set(event.inputName, event.udmLanguage);
            this.inputFormatsMap.set(event.inputName, event.format);

            // Re-render if Function Builder is open to update field tree
            if (this.showFunctionBuilderDialog) {
                console.log('[UTLXEditorWidget] Function Builder is open, re-rendering with updated UDM');
                this.update();
            }
        });

        this.eventService.onInputInferSchema(event => {
            console.log('[UTLXEditorWidget] 📡 RECEIVED: Input infer schema:', event);
        });

        this.eventService.onInputInstanceContentChanged(event => {
            console.log('[UTLXEditorWidget] 📡 RECEIVED: Input instance content changed:', {
                inputId: event.inputId,
                contentLength: event.content.length
            });
        });

        this.eventService.onInputSchemaContentChanged(event => {
            console.log('[UTLXEditorWidget] 📡 RECEIVED: Input schema content changed:', {
                inputId: event.inputId,
                contentLength: event.content.length
            });
        });

        // ===== Output Events =====
        this.eventService.onOutputFormatChanged(event => {
            console.log('[UTLXEditorWidget] 📡 RECEIVED: Output format changed:', event);
            // Header updates handled by UTLXFrontendContribution
        });

        this.eventService.onOutputPresetOn(event => {
            console.log('[UTLXEditorWidget] 📡 RECEIVED: Output preset mode ON:', {
                format: event.schemaFormat,
                contentLength: event.schemaContent.length
            });
        });

        this.eventService.onOutputPresetOff(event => {
            console.log('[UTLXEditorWidget] 📡 RECEIVED: Output preset mode OFF');
        });

        this.eventService.onOutputSchemaFormatChanged(event => {
            console.log('[UTLXEditorWidget] 📡 RECEIVED: Output schema format changed:', event);
        });

        this.eventService.onOutputSchemaContentChanged(event => {
            console.log('[UTLXEditorWidget] 📡 RECEIVED: Output schema content changed:', {
                contentLength: event.content.length
            });
        });

        // ===== AI Generation Events =====
        this.eventService.onUTLXGenerated(event => {
            console.log('[UTLXEditorWidget] 📡 RECEIVED: UTLX generated from prompt:', event.prompt);
            if (event.utlx) {
                this.setContent(event.utlx);
            }
        });

        console.log('[UTLXEditorWidget] ✓ All event subscriptions initialized');
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
            // Create or get Monaco model with UTLX content
            // Use a consistent URI for this editor instance
            const uri = monaco.Uri.parse('inmemory://utlx-editor/transformation.utlx');

            // Check if model already exists (to avoid "model already exists" error)
            let model = monaco.editor.getModel(uri);
            if (!model) {
                model = monaco.editor.createModel(
                    this.getDefaultContent(),
                    'utlx', // UTL-X language ID - registered via UTLXLanguageGrammarContribution
                    uri
                );
                console.log('[UTLXEditor] Created new Monaco model with UTL-X language');
            } else {
                console.log('[UTLXEditor] Reusing existing Monaco model');
                model.setValue(this.getDefaultContent());
            }

            // Create standalone Monaco editor
            this.editor = monaco.editor.create(this.editorContainer, {
                model: model,
                lineNumbers: 'on',
                minimap: { enabled: false }, // Disabled to save space - UTLX files are typically short
                scrollBeyondLastLine: false,
                wordWrap: 'on',
                fontSize: 12,
                folding: true,
                renderWhitespace: 'selection',
                automaticLayout: true,
                tabSize: 2,
                insertSpaces: true,
                theme: 'vs-dark'
            });

            console.log('[UTLXEditor] Monaco editor created successfully');

            // Register completion provider for $ input references
            this.registerCompletionProvider();

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

                // Set cursor to first line of body (after --- separator)
                this.setInitialCursorPosition();
            }, 200);

            // Enforce read-only headers
            this.enforceReadOnlyHeaders();

            // Prevent cursor from entering header area
            this.enforceCursorInBody();

            // Focus editor
            this.editor.focus();
            console.log('[UTLXEditor] Editor initialized and focused');
        } catch (error) {
            console.error('[UTLXEditor] Failed to create editor:', error);
        }
    }

    /**
     * Register Monaco completion provider for $ input references
     */
    protected registerCompletionProvider(): void {
        const completionProvider: monaco.languages.CompletionItemProvider = {
            triggerCharacters: ['$', '.'],
            provideCompletionItems: (model, position) => {
                console.log('[UTLXEditor] ===========================================');
                console.log('[UTLXEditor] Completion provider triggered');
                console.log('[UTLXEditor] Position:', { line: position.lineNumber, column: position.column });

                const lineContent = model.getLineContent(position.lineNumber);
                console.log('[UTLXEditor] Line content:', lineContent);

                const textBeforeCursor = lineContent.substring(0, position.column - 1);
                console.log('[UTLXEditor] Text before cursor:', textBeforeCursor);

                // Check if we're completing after a dot (field navigation)
                const lastDotIndex = textBeforeCursor.lastIndexOf('.');
                const lastDollarIndex = textBeforeCursor.lastIndexOf('$');

                console.log('[UTLXEditor] Last $ index:', lastDollarIndex);
                console.log('[UTLXEditor] Last . index:', lastDotIndex);

                // Case 1: Dot notation - $input.field.subfield
                if (lastDotIndex > lastDollarIndex && lastDollarIndex !== -1) {
                    console.log('[UTLXEditor] DOT NOTATION detected');
                    return this.provideFieldCompletions(textBeforeCursor, lastDollarIndex, lastDotIndex, position);
                }

                // Case 2: Input name completion - $input
                if (lastDollarIndex !== -1) {
                    console.log('[UTLXEditor] INPUT NAME completion');
                    return this.provideInputNameCompletions(textBeforeCursor, lastDollarIndex, position);
                }

                console.log('[UTLXEditor] No $ found, returning empty suggestions');
                console.log('[UTLXEditor] ===========================================');
                return { suggestions: [] };
            }
        };

        // Register for utlx language
        const disposable = monaco.languages.registerCompletionItemProvider('utlx', completionProvider);
        this.toDispose.push(disposable);

        console.log('[UTLXEditor] Completion provider registered');
    }

    /**
     * Provide input name completions for $
     */
    protected provideInputNameCompletions(
        textBeforeCursor: string,
        dollarIndex: number,
        position: monaco.Position
    ): monaco.languages.CompletionList {
        const textAfterDollar = textBeforeCursor.substring(dollarIndex + 1);
        console.log('[UTLXEditor] Text after $:', textAfterDollar);

        console.log('[UTLXEditor] Available inputs from headers:', this.inputNamesFromHeaders);

        const replaceStartColumn = dollarIndex + 2; // +1 for 0-index, +1 to skip $
        const replaceEndColumn = position.column;

        const suggestions: monaco.languages.CompletionItem[] = [];

        // For each input, check if it's an array and provide appropriate suggestions
        this.inputNamesFromHeaders.forEach(inputName => {
            const udm = this.inputUdmMap.get(inputName);
            const isArray = udm ? this.isUdmArray(udm) : false;

            if (isArray) {
                // For array inputs, provide multiple options:

                // 1. Plain input name (for passing whole array to functions like map, filter, etc.)
                suggestions.push({
                    label: inputName,
                    kind: monaco.languages.CompletionItemKind.Variable,
                    insertText: inputName,
                    detail: 'Array (entire collection)',
                    documentation: `Use for passing the entire array to functions like map, filter, count, etc.\n\n**Examples:**\n\`\`\`\nmap($${inputName}, e => e.field)\nfilter($${inputName}, e => e.field > 100)\ncount($${inputName})\n\`\`\``,
                    sortText: '1_' + inputName, // Sort first
                    range: {
                        startLineNumber: position.lineNumber,
                        startColumn: replaceStartColumn,
                        endLineNumber: position.lineNumber,
                        endColumn: replaceEndColumn
                    }
                });

                // 2. Array indexing with [0] (for accessing specific element)
                suggestions.push({
                    label: inputName + '[0]',
                    kind: monaco.languages.CompletionItemKind.Variable,
                    insertText: inputName + '[0]',
                    detail: 'First element',
                    documentation: `Direct access to the first element of the array.\n\n**Example:**\n\`\`\`\n$${inputName}[0].Department\n$${inputName}[0].FirstName\n\`\`\`\n\nFor processing all elements, use map() instead.`,
                    sortText: '2_' + inputName,
                    range: {
                        startLineNumber: position.lineNumber,
                        startColumn: replaceStartColumn,
                        endLineNumber: position.lineNumber,
                        endColumn: replaceEndColumn
                    }
                });

            } else {
                // For non-array inputs (objects), just provide the input name
                suggestions.push({
                    label: inputName,
                    kind: monaco.languages.CompletionItemKind.Variable,
                    insertText: inputName,
                    detail: 'Input reference',
                    documentation: this.inputUdmMap.has(inputName)
                        ? `UDM:\n\`\`\`\n${this.inputUdmMap.get(inputName)}\n\`\`\``
                        : 'Input from UTLX header',
                    sortText: '1_' + inputName,
                    range: {
                        startLineNumber: position.lineNumber,
                        startColumn: replaceStartColumn,
                        endLineNumber: position.lineNumber,
                        endColumn: replaceEndColumn
                    }
                });
            }
        });

        console.log('[UTLXEditor] Returning', suggestions.length, 'input name suggestions');
        console.log('[UTLXEditor] ===========================================');

        return { suggestions };
    }

    /**
     * Check if UDM is an array at root level
     */
    protected isUdmArray(udm: string): boolean {
        const trimmed = udm.trim()
            .replace(/@udm-version:[^\n]*\n/g, '')
            .replace(/@parsed-at:[^\n]*\n/g, '')
            .replace(/@source:[^\n]*\n/g, '')
            .trim();
        return trimmed.startsWith('[');
    }

    /**
     * Provide field completions for dot notation ($input.field)
     */
    protected provideFieldCompletions(
        textBeforeCursor: string,
        dollarIndex: number,
        dotIndex: number,
        position: monaco.Position
    ): monaco.languages.CompletionList {
        // Extract the full path: $inputName.field.subfield or $inputName[index].field
        const fullPath = textBeforeCursor.substring(dollarIndex + 1, dotIndex);

        // Check if path contains array indexing (e.g., "input2[0]")
        const arrayIndexMatch = fullPath.match(/^([^\[]+)(\[\d+\])/);

        let inputName: string;
        let fieldPath: string[];

        if (arrayIndexMatch) {
            // Path like: input2[0] or input2[0].customer
            inputName = arrayIndexMatch[1];
            const afterIndex = fullPath.substring(arrayIndexMatch[0].length);
            fieldPath = afterIndex ? afterIndex.split('.').filter(p => p.length > 0) : [];
        } else {
            // Path like: input2 or input2.customer
            const pathParts = fullPath.split('.');
            inputName = pathParts[0];
            fieldPath = pathParts.slice(1);
        }

        console.log('[UTLXEditor] Input name:', inputName);
        console.log('[UTLXEditor] Field path:', fieldPath);
        console.log('[UTLXEditor] Has array index:', !!arrayIndexMatch);

        // Get UDM for this input
        const udm = this.inputUdmMap.get(inputName);
        if (!udm) {
            console.log('[UTLXEditor] No UDM found for input:', inputName);
            console.log('[UTLXEditor] ===========================================');
            return { suggestions: [] };
        }

        console.log('[UTLXEditor] UDM found, parsing structure...');

        // Check if UDM is an array at root and user hasn't used array indexing yet
        const trimmedUdm = udm.trim().replace(/@udm-version:[^\n]*\n/g, '').replace(/@parsed-at:[^\n]*\n/g, '').trim();
        const isRootArray = trimmedUdm.startsWith('[');

        if (isRootArray && !arrayIndexMatch && fieldPath.length === 0) {
            // User typed $input2. but input2 is an array - suggest array indexing
            console.log('[UTLXEditor] Root is array and no index provided - suggesting array indexing');

            const replaceStartColumn = dotIndex + 2;
            const replaceEndColumn = position.column;

            const suggestions: monaco.languages.CompletionItem[] = [{
                label: '[index]',
                kind: monaco.languages.CompletionItemKind.Snippet,
                insertText: '[0].',
                detail: 'Array indexing required',
                documentation: 'This input is an array. Use array indexing syntax like $' + inputName + '[0].field to access fields.\n\nExamples:\n- $' + inputName + '[0].field - First element\n- $' + inputName + '[i].field - Loop variable',
                range: {
                    startLineNumber: position.lineNumber,
                    startColumn: dotIndex + 1, // Replace the dot
                    endLineNumber: position.lineNumber,
                    endColumn: replaceEndColumn
                }
            }];

            console.log('[UTLXEditor] Returning array indexing suggestion');
            console.log('[UTLXEditor] ===========================================');
            return { suggestions };
        }

        // Parse UDM and navigate to current position
        const fields = this.getFieldsAtPath(udm, fieldPath);

        console.log('[UTLXEditor] Fields at current path:', fields);

        const replaceStartColumn = dotIndex + 2; // +1 for 0-index, +1 to skip .
        const replaceEndColumn = position.column;

        const suggestions: monaco.languages.CompletionItem[] = fields.map(field => ({
            label: field.name,
            kind: field.type === 'object' ? monaco.languages.CompletionItemKind.Class :
                  field.type === 'array' ? monaco.languages.CompletionItemKind.Value :
                  monaco.languages.CompletionItemKind.Field,
            insertText: field.name,
            detail: field.type,
            documentation: field.description || `Field of type ${field.type}`,
            range: {
                startLineNumber: position.lineNumber,
                startColumn: replaceStartColumn,
                endLineNumber: position.lineNumber,
                endColumn: replaceEndColumn
            }
        }));

        console.log('[UTLXEditor] Returning', suggestions.length, 'field suggestions');
        console.log('[UTLXEditor] ===========================================');

        return { suggestions };
    }

    /**
     * Parse UDM structure and get fields at a specific path
     */
    protected getFieldsAtPath(udm: string, path: string[]): Array<{name: string, type: string, description?: string}> {
        console.log('[UTLXEditor] Parsing UDM for path:', path);
        console.log('[UTLXEditor] UDM content (first 500 chars):', udm.substring(0, 500));

        try {
            // Parse UDM language format
            const fields = this.parseUdmFields(udm, path);
            console.log('[UTLXEditor] Parsed fields:', fields);
            return fields;
        } catch (error) {
            console.error('[UTLXEditor] Failed to parse UDM:', error);
            return [];
        }
    }

    /**
     * Parse UDM language and extract fields
     * Handles both formats:
     * 1. Full: @Object(...) { properties: { field1: value } }
     * 2. Shorthand: [{field1: value, field2: value}] or {field1: value}
     */
    protected parseUdmFields(udm: string, path: string[]): Array<{name: string, type: string, description?: string}> {
        let currentUdm = udm.trim();

        // Skip metadata lines (@udm-version, @parsed-at, etc.)
        currentUdm = currentUdm.replace(/@udm-version:[^\n]*\n/g, '');
        currentUdm = currentUdm.replace(/@parsed-at:[^\n]*\n/g, '');
        currentUdm = currentUdm.replace(/@source:[^\n]*\n/g, '');
        currentUdm = currentUdm.trim();

        console.log('[UTLXEditor] After removing metadata:', currentUdm.substring(0, 300));

        // Check if it's an array at root level
        if (currentUdm.startsWith('[')) {
            console.log('[UTLXEditor] Root is an ARRAY');

            // Extract the first element to get field structure
            const firstElementMatch = currentUdm.match(/\[\s*\{([^}]+)\}/);
            if (firstElementMatch) {
                currentUdm = `{${firstElementMatch[1]}}`;
                console.log('[UTLXEditor] Using first array element:', currentUdm.substring(0, 200));
            } else {
                console.log('[UTLXEditor] Could not extract first array element');
                return [];
            }
        }

        // Navigate through the path
        for (const fieldName of path) {
            console.log('[UTLXEditor] Navigating to field:', fieldName);

            // Try shorthand format first: {fieldName: value, ...}
            const shorthandPattern = new RegExp(`${fieldName}:\\s*([^,}]+|\\{[^}]+\\})`, 's');
            const shorthandMatch = currentUdm.match(shorthandPattern);

            if (shorthandMatch) {
                currentUdm = shorthandMatch[1].trim();
                console.log('[UTLXEditor] Found field (shorthand):', currentUdm.substring(0, 100));
                continue;
            }

            // Try full format: properties: { fieldName: value }
            const propertiesMatch = currentUdm.match(/properties:\s*\{([^}]*(?:\{[^}]*\}[^}]*)*)\}/);
            if (propertiesMatch) {
                const propertiesContent = propertiesMatch[1];
                const fieldPattern = new RegExp(`${fieldName}:\\s*(@Object[^{]*\\{[^}]*(?:\\{[^}]*\\}[^}]*)*\\}|[^,]+)`, 's');
                const fieldMatch = propertiesContent.match(fieldPattern);

                if (fieldMatch) {
                    currentUdm = fieldMatch[1];
                    console.log('[UTLXEditor] Found field (full format):', currentUdm.substring(0, 100));
                    continue;
                }
            }

            console.log('[UTLXEditor] Field not found:', fieldName);
            return [];
        }

        // Extract fields at current level
        console.log('[UTLXEditor] Extracting fields from:', currentUdm.substring(0, 300));

        // Try shorthand format: {field1: value, field2: value}
        if (currentUdm.startsWith('{') || currentUdm.includes(':')) {
            const fields: Array<{name: string, type: string, description?: string}> = [];

            // Extract all field: value pairs
            const fieldPattern = /(\w+):\s*("(?:[^"\\]|\\.)*"|\{[^}]*\}|[^,}\n]+)/g;
            let match;

            while ((match = fieldPattern.exec(currentUdm)) !== null) {
                const fieldName = match[1];
                const fieldValue = match[2].trim();

                let type = this.inferUdmType(fieldValue);

                fields.push({
                    name: fieldName,
                    type: type,
                    description: `${fieldName}: ${type}`
                });
            }

            console.log('[UTLXEditor] Extracted fields (shorthand):', fields.length);
            return fields;
        }

        // Try full format: properties: { ... }
        const propertiesMatch = currentUdm.match(/properties:\s*\{([^}]*(?:\{[^}]*\}[^}]*)*)\}/);
        if (propertiesMatch) {
            const fields: Array<{name: string, type: string, description?: string}> = [];
            const propertiesContent = propertiesMatch[1];
            const fieldPattern = /(\w+):\s*(@\w+(?:\([^)]*\))?(?:\s*\{[^}]*\})?|"[^"]*"|[^,]+)/g;

            let match;
            while ((match = fieldPattern.exec(propertiesContent)) !== null) {
                const fieldName = match[1];
                const fieldValue = match[2].trim();
                let type = this.inferUdmType(fieldValue);

                fields.push({
                    name: fieldName,
                    type: type,
                    description: `${fieldName}: ${type}`
                });
            }

            console.log('[UTLXEditor] Extracted fields (full format):', fields.length);
            return fields;
        }

        console.log('[UTLXEditor] No fields found');
        return [];
    }

    /**
     * Infer type from UDM value
     */
    protected inferUdmType(value: string): string {
        value = value.trim();

        if (value.startsWith('@Object')) return 'object';
        if (value.startsWith('@Array')) return 'array';
        if (value.startsWith('@DateTime')) return 'datetime';
        if (value.startsWith('@Date')) return 'date';
        if (value.startsWith('@Binary')) return 'binary';
        if (value.startsWith('"')) return 'string';
        if (value === 'true' || value === 'false') return 'boolean';
        if (value === 'null') return 'null';
        if (/^-?\d+(\.\d+)?$/.test(value)) return 'number';
        if (value.startsWith('{')) return 'object';
        if (value.startsWith('[')) return 'array';

        return 'unknown';
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
{
// Welcome to UTLX!
//
// This is a sample transformation.
// Edit the code below and press Execute to run.

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
        const content = this.getContent();
        console.log('[UTLXEditorWidget] 📤 FIRING: Content changed, length:', content.length);

        // Fire content changed event for validation/execution
        this.eventService.fireContentChanged({ content });

        // Debounced header parsing for panel synchronization
        if (this.contentChangeDebounceTimer) {
            clearTimeout(this.contentChangeDebounceTimer);
        }

        this.contentChangeDebounceTimer = window.setTimeout(() => {
            this.parseAndUpdatePanels(content);
        }, this.CONTENT_CHANGE_DEBOUNCE_MS);
    }

    /**
     * Parse UTLX headers and fire event to update panels
     * Called with debounce during typing, immediately on file load
     */
    protected parseAndUpdatePanels(content: string): void {
        console.log('[UTLXEditorWidget] Parsing headers for panel sync');

        const parsed = parseUTLXHeaders(content);

        if (!parsed.valid) {
            console.log('[UTLXEditorWidget] Invalid headers, not updating panels:', parsed.errors);
            return;
        }

        console.log('[UTLXEditorWidget] Valid headers parsed:', {
            inputs: parsed.inputs.length,
            outputFormat: parsed.output.format
        });

        // Extract input names for autocomplete
        this.inputNamesFromHeaders = parsed.inputs.map(input => input.name);
        console.log('[UTLXEditor] Input names extracted from parsed headers:', this.inputNamesFromHeaders);

        // Fire event to trigger panel updates
        this.eventService.fireHeadersParsed({
            inputs: parsed.inputs,
            output: parsed.output
        });
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

        // Immediately parse headers (bypass debounce for file loads)
        this.parseAndUpdatePanels(content);
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
     * Extract input names from input header lines for autocomplete
     */
    protected extractInputNamesFromLines(inputLines: string[]): string[] {
        const inputNames: string[] = [];

        for (const line of inputLines) {
            // Parse lines like "input json" or "input employees json"
            // Format: input [name] format [options...]
            const match = line.match(/^input\s+(?:(\w+)\s+)?(\w+)/);
            if (match) {
                const inputName = match[1] || 'input'; // Default to 'input' if no name specified
                inputNames.push(inputName);
            }
        }

        return inputNames;
    }

    /**
     * Update headers with new input/output information
     * This is called when formats change in the panels
     */
    public updateHeaders(inputLines: string[], outputFormat: string): void {
        console.log('[UTLXEditor] updateHeaders() called', { inputLines, outputFormat });

        if (!this.editor) {
            console.warn('[UTLXEditor] No editor instance');
            return;
        }

        const model = this.editor.getModel();
        if (!model) {
            console.warn('[UTLXEditor] No model');
            return;
        }

        // Set flag to bypass read-only enforcement during update
        this.isUpdatingHeaders = true;

        try {
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

            // CRITICAL: Save the new header content so enforcement doesn't revert it
            this.saveHeaderContent();

            // Extract input names for autocomplete
            this.inputNamesFromHeaders = this.extractInputNamesFromLines(inputLines);
            console.log('[UTLXEditor] Input names extracted for autocomplete:', this.inputNamesFromHeaders);

            console.log('[UTLXEditor] Headers updated successfully');
        } finally {
            // Always clear the flag, even if there's an error
            this.isUpdatingHeaders = false;
        }
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
     * Save current header content for read-only protection
     */
    protected saveHeaderContent(): void {
        if (!this.editor) return;
        const model = this.editor.getModel();
        if (!model) return;

        this.savedHeaderContent = [];
        for (let i = 1; i <= this.headerEndLine + 1; i++) {
            this.savedHeaderContent.push(model.getLineContent(i));
        }
        console.log('[UTLXEditor] Saved header content:', this.savedHeaderContent);
    }

    /**
     * Enforce read-only behavior on header lines
     */
    protected enforceReadOnlyHeaders(): void {
        if (!this.editor) return;

        let isReverting = false;

        // Save initial headers
        this.saveHeaderContent();

        // Listen for content changes and prevent edits to header lines
        this.toDispose.push(
            this.editor.onDidChangeModelContent((e) => {
                if (!this.editor || isReverting || this.isUpdatingHeaders) return;

                const model = this.editor.getModel();
                if (!model) return;

                // Check if this is a full content replacement (paste)
                // If there's a single change that spans multiple lines including the body, it's likely a paste
                const isMajorChange = e.changes.length === 1 &&
                    e.changes[0].range.endLineNumber - e.changes[0].range.startLineNumber > 3;

                if (isMajorChange) {
                    // Major change detected (likely paste) - update saved headers instead of reverting
                    console.log('[UTLXEditor] Major content change detected (paste) - updating saved headers');
                    this.saveHeaderContent();
                    this.onContentChanged(); // Trigger parsing
                    return;
                }

                // Check if any changes were made to header lines
                let headerChanged = false;
                for (const change of e.changes) {
                    const startLine = change.range.startLineNumber;

                    // If change affects header lines (lines 1 through headerEndLine + 1)
                    if (startLine <= this.headerEndLine + 1) {
                        headerChanged = true;
                        break;
                    }
                }

                if (headerChanged) {
                    console.log('[UTLXEditor] Header edit detected - restoring saved content');

                    // Prevent infinite loop
                    isReverting = true;

                    // Restore headers from saved content
                    const edits: monaco.editor.IIdentifiedSingleEditOperation[] = [];
                    for (let i = 0; i < this.savedHeaderContent.length; i++) {
                        const lineNum = i + 1;
                        const currentContent = model.getLineContent(lineNum);
                        if (currentContent !== this.savedHeaderContent[i]) {
                            edits.push({
                                range: new monaco.Range(lineNum, 1, lineNum, model.getLineMaxColumn(lineNum)),
                                text: this.savedHeaderContent[i]
                            });
                        }
                    }

                    if (edits.length > 0) {
                        model.pushEditOperations([], edits, () => null);
                    }

                    isReverting = false;
                }
            })
        );
    }

    /**
     * Set initial cursor position to first line of body (after --- separator)
     */
    protected setInitialCursorPosition(): void {
        if (!this.editor) return;

        const model = this.editor.getModel();
        if (!model) return;

        // Find the separator line
        const separatorLine = this.parseHeaderEndLine();

        // Position cursor at first line after separator (body start)
        // This is separatorLine + 2 (because separatorLine is 0-indexed and we want the line after ---)
        const bodyStartLine = separatorLine + 2;

        // Find the first non-empty line in the body or use body start
        let targetLine = bodyStartLine;
        const lineCount = model.getLineCount();

        for (let i = bodyStartLine; i <= lineCount; i++) {
            const lineContent = model.getLineContent(i).trim();
            // Skip empty lines and comments
            if (lineContent && !lineContent.startsWith('//')) {
                targetLine = i;
                break;
            }
        }

        // Set cursor to column 1 of target line
        const position = new monaco.Position(targetLine, 1);
        this.editor.setPosition(position);
        this.editor.revealPositionInCenter(position);

        console.log('[UTLXEditor] Initial cursor position set to line', targetLine);
    }

    /**
     * Prevent cursor from entering header area - push it back to body
     * BUT allow selections that include headers (for copy/paste)
     */
    protected enforceCursorInBody(): void {
        if (!this.editor) return;

        // Listen for cursor position changes
        this.toDispose.push(
            this.editor.onDidChangeCursorPosition((e) => {
                if (!this.editor) return;

                const position = e.position;
                const selection = this.editor.getSelection();

                // Don't enforce if user is making a selection (allow selecting headers for copy)
                if (selection && !selection.isEmpty()) {
                    return;
                }

                // Only enforce if cursor is in header area AND there's no selection
                // If cursor is in header area (lines 1 through headerEndLine + 1)
                if (position.lineNumber <= this.headerEndLine + 1) {
                    console.log('[UTLXEditor] Cursor in header area (no selection), moving to body');

                    // Move cursor to first line of body
                    const bodyStartLine = this.headerEndLine + 2;
                    const newPosition = new monaco.Position(bodyStartLine, 1);

                    this.editor.setPosition(newPosition);
                }
            })
        );
    }

    /**
     * Open the Function Builder dialog
     */
    protected async openFunctionBuilder(): Promise<void> {
        console.log('[UTLXEditor] Opening Function Builder');

        try {
            // Parse headers DIRECTLY to ensure inputNamesFromHeaders is up to date
            const content = this.getContent();
            const parsed = parseUTLXHeaders(content);

            if (parsed.valid) {
                // Update inputNamesFromHeaders and formats immediately
                this.inputNamesFromHeaders = parsed.inputs.map(input => input.name);

                // Update input formats from headers (don't clear - merge with existing formats from UDM events)
                // This way, if headers exist they take precedence, but if not we keep formats from UDM
                parsed.inputs.forEach(input => {
                    this.inputFormatsMap.set(input.name, input.format);
                });

                console.log('[UTLXEditor] Parsed input names:', this.inputNamesFromHeaders);
                console.log('[UTLXEditor] Parsed input formats:', Array.from(this.inputFormatsMap.entries()));

                // Also fire the event for panel synchronization
                this.eventService.fireHeadersParsed({
                    inputs: parsed.inputs,
                    output: parsed.output
                });
            } else {
                console.warn('[UTLXEditor] Headers invalid, cannot determine inputs');
                console.log('[UTLXEditor] Using formats from UDM events:', Array.from(this.inputFormatsMap.entries()));
            }

            // Request current UDM from all inputs (will trigger UDM events if data exists)
            this.eventService.fireRequestCurrentUdm();

            // Small delay to let UDM events propagate
            await new Promise(resolve => setTimeout(resolve, 100));

            // Fetch stdlib functions from daemon
            this.functionBuilderFunctions = await this.utlxService.getFunctions();
            console.log('[UTLXEditor] Loaded', this.functionBuilderFunctions.length, 'stdlib functions');

            // Fetch operators from daemon
            this.functionBuilderOperators = await this.utlxService.getOperators();
            console.log('[UTLXEditor] Loaded', this.functionBuilderOperators.length, 'operators');

            // Log what we're passing to Function Builder
            console.log('[UTLXEditor] Opening Function Builder with:', {
                inputNamesFromHeaders: this.inputNamesFromHeaders,
                inputUdmMapSize: this.inputUdmMap.size,
                inputUdmMapKeys: Array.from(this.inputUdmMap.keys())
            });

            // Open dialog
            this.showFunctionBuilderDialog = true;
            this.update();
        } catch (error) {
            console.error('[UTLXEditor] Failed to load functions:', error);
            // TODO: Show error notification to user
        }
    }

    /**
     * Handle code insertion from Function Builder
     */
    protected handleInsertFromBuilder(code: string): void {
        console.log('[UTLXEditor] Inserting code from Function Builder:', code);

        if (!this.editor) {
            console.error('[UTLXEditor] No editor available for insertion');
            return;
        }

        const position = this.editor.getPosition();
        if (!position) {
            console.error('[UTLXEditor] No cursor position available');
            return;
        }

        // Insert the code at cursor position
        const range = new monaco.Range(
            position.lineNumber,
            position.column,
            position.lineNumber,
            position.column
        );

        this.editor.executeEdits('function-builder', [{
            range: range,
            text: code,
            forceMoveMarkers: true
        }]);

        // Find cursor placeholder (|) and position cursor there
        const placeholderMatch = code.match(/\|/);
        if (placeholderMatch) {
            const lines = code.substring(0, placeholderMatch.index).split('\n');
            const lastLine = lines[lines.length - 1];
            const newPosition = new monaco.Position(
                position.lineNumber + lines.length - 1,
                lines.length === 1 ? position.column + lastLine.length : lastLine.length + 1
            );

            this.editor.setPosition(newPosition);

            // Remove the | placeholder
            const placeholderRange = new monaco.Range(
                newPosition.lineNumber,
                newPosition.column,
                newPosition.lineNumber,
                newPosition.column + 1
            );
            this.editor.executeEdits('function-builder-cleanup', [{
                range: placeholderRange,
                text: '',
                forceMoveMarkers: true
            }]);
        }

        // Force the editor to refresh its view and layout
        this.editor.layout();
        this.editor.updateOptions({});

        // Scroll to reveal the inserted code
        const currentPosition = this.editor.getPosition();
        if (currentPosition) {
            this.editor.revealPositionInCenter(currentPosition);
        }

        // Focus back on the editor
        this.editor.focus();
    }

    /**
     * Rename all references to an input variable when the input name changes
     * E.g., when input name changes from "input" to "input2", all $input references become $input2
     */
    protected renameInputReferences(oldName: string, newName: string): void {
        console.log(`[UTLXEditor] Renaming input references: $${oldName} -> $${newName}`);

        if (!this.editor) {
            console.error('[UTLXEditor] No editor available for renaming');
            return;
        }

        const model = this.editor.getModel();
        if (!model) {
            console.error('[UTLXEditor] No model available for renaming');
            return;
        }

        // Get the transformation body (after headers)
        const content = model.getValue();
        const lines = content.split('\n');
        const headerEndLine = this.headerEndLine || this.parseHeaderEndLine();

        // Build regex to match $oldName with word boundaries
        // Matches: $oldName followed by non-word character or end of string
        // Examples: $input, $input[0], $input.field, $input |>, etc.
        const searchRegex = new RegExp(`\\$${oldName}(?![a-zA-Z0-9_])`, 'g');

        const edits: monaco.editor.IIdentifiedSingleEditOperation[] = [];

        // Search only in the transformation body (after header separator)
        for (let lineNumber = headerEndLine + 1; lineNumber <= lines.length; lineNumber++) {
            const lineContent = lines[lineNumber - 1];
            let match: RegExpExecArray | null;

            // Find all matches in this line
            while ((match = searchRegex.exec(lineContent)) !== null) {
                const startColumn = match.index + 1; // Monaco uses 1-based columns
                const endColumn = startColumn + match[0].length;

                edits.push({
                    range: new monaco.Range(lineNumber, startColumn, lineNumber, endColumn),
                    text: `$${newName}`,
                    forceMoveMarkers: true
                });

                console.log(`[UTLXEditor] Found reference at line ${lineNumber}, col ${startColumn}: "${match[0]}"`);
            }
        }

        if (edits.length > 0) {
            console.log(`[UTLXEditor] Replacing ${edits.length} reference(s)`);
            this.editor.executeEdits('input-rename', edits);
            console.log('[UTLXEditor] References renamed successfully');
        } else {
            console.log('[UTLXEditor] No references found to rename');
        }
    }

    /**
     * Analyze cursor context for smart insertion using full context analyzer
     */
    protected analyzeCursorContext(): InsertionContext | null {
        if (!this.editor) return null;

        const position = this.editor.getPosition();
        if (!position) return null;

        const model = this.editor.getModel();
        if (!model) return null;

        // Get current selection (if any)
        const selection = this.editor.getSelection();

        // Use the full context analyzer from context-analyzer.ts
        return analyzeInsertionContext(model, position, selection ?? undefined);
    }

    protected render(): React.ReactNode {
        return (
            <div className='utlx-editor-container'>
                <div className='utlx-editor-header'>
                    <div className='utlx-editor-title'>
                        <span>UTLX code</span>
                    </div>
                    <div className='utlx-panel-actions'>
                        <button
                            title='Function Builder - Browse and insert stdlib functions'
                            onClick={() => this.openFunctionBuilder()}
                        >
                            <span className='codicon codicon-symbol-method' style={{fontSize: '11px'}}></span>
                            {' '}Function Builder
                        </button>
                        <button
                            title='Load UTLX File'
                            onClick={() => this.handleLoadFile()}
                        >
                            <span className='codicon codicon-folder-opened' style={{fontSize: '11px'}}></span>
                            {' '}Load
                        </button>
                        <button
                            title='Save UTLX File'
                            onClick={() => this.handleSaveFile()}
                        >
                            <span className='codicon codicon-save' style={{fontSize: '11px'}}></span>
                            {' '}Save
                        </button>
                        <button
                            title='Clear Editor'
                            onClick={() => this.clearContent()}
                        >
                            <span className='codicon codicon-clear-all' style={{fontSize: '11px'}}></span>
                            {' '}Clear
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

                {/* Function Builder Dialog */}
                {this.showFunctionBuilderDialog && (
                    <FunctionBuilderDialog
                        functions={this.functionBuilderFunctions}
                        operators={this.functionBuilderOperators}
                        availableInputs={Array.from(new Set([...this.inputNamesFromHeaders, ...Array.from(this.inputUdmMap.keys())]))}
                        udmMap={this.inputUdmMap}
                        inputFormatsMap={this.inputFormatsMap}
                        cursorContext={this.analyzeCursorContext()}
                        onInsert={(code) => this.handleInsertFromBuilder(code)}
                        onClose={() => {
                            this.showFunctionBuilderDialog = false;
                            this.update();
                        }}
                    />
                )}
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
     * Handle save file button click - uses native Electron save dialog
     */
    protected async handleSaveFile(): Promise<void> {
        const content = await this.saveFile();

        console.log('Save button clicked - using Theia FileDialogService');

        try {
            // Use Theia's FileDialogService which triggers Electron's native save dialog
            const saveDialogProps: SaveFileDialogProps = {
                title: 'Save UTLX File',
                filters: {
                    'UTLX Files': ['utlx'],
                    'All Files': ['*']
                },
                inputValue: 'transformation.utlx'
            };

            const targetUri = await this.fileDialogService.showSaveDialog(saveDialogProps);

            if (targetUri) {
                // Write the file to the selected location
                await this.fileService.write(targetUri, content);
                console.log(`UTLX file saved to: ${targetUri.toString()}`);
            } else {
                console.log('Save dialog cancelled by user');
            }
        } catch (error) {
            console.error('Error saving UTLX file:', error);
        }
    }

    dispose(): void {
        this.disposeEditor();
        super.dispose();
    }
}
