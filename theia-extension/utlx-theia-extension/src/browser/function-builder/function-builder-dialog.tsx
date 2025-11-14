/**
 * Function Builder Dialog
 *
 * A popup dialog that helps users discover, learn, and insert UTLX stdlib functions
 * with smart context awareness and UDM field integration.
 *
 * Features:
 * - Browse functions organized by category (collapsible tree)
 * - Search across all functions and fields
 * - View UDM fields for available inputs
 * - Smart context-aware code insertion
 * - Rich help documentation with examples
 */

import * as React from 'react';
import * as monaco from '@theia/monaco-editor-core';
import { FunctionInfo } from '../../common/protocol';
import { parseUdmToTree, UdmInputTree } from './udm-parser';
import { FieldTree } from './field-tree';
import { InsertionContext, CursorValue, getContextDescription, analyzeInsertionContext } from './context-analyzer';
import { generateFunctionInsertion, generateInsertionPreview } from './insertion-generator';
import { OperatorsTree } from './operators-tree';
import { OperatorInfo } from './operators-data';
import { generateOperatorInsertion, generateOperatorInsertionPreview } from './operator-insertion-generator';

/**
 * Props for the Function Builder Dialog
 */
export interface FunctionBuilderDialogProps {
    functions: FunctionInfo[];
    availableInputs: string[];
    udmMap: Map<string, string>;
    inputFormatsMap: Map<string, string>; // inputName -> format (json, csv, xml, etc.)
    cursorContext: InsertionContext | null;
    onInsert: (code: string) => void;
    onClose: () => void;
}

/**
 * Helper function to calculate match score (lower is better)
 */
function getBestMatchScore(name: string, query: string): number {
    const nameLower = name.toLowerCase();
    if (nameLower === query) return 0;           // Exact match
    if (nameLower.startsWith(query)) return 1;   // Prefix match
    if (nameLower.includes(query)) return 2;     // Contains match
    return 3;                                     // Description/category match
}

/**
 * Function Builder Dialog Component
 */
export const FunctionBuilderDialog: React.FC<FunctionBuilderDialogProps> = ({
    functions,
    availableInputs,
    udmMap,
    inputFormatsMap,
    cursorContext,
    onInsert,
    onClose
}) => {
    const [searchQuery, setSearchQuery] = React.useState('');
    const [expandedCategories, setExpandedCategories] = React.useState<Set<string>>(new Set());
    const [selectedFunction, setSelectedFunction] = React.useState<FunctionInfo | null>(null);
    const [selectedOperator, setSelectedOperator] = React.useState<OperatorInfo | null>(null);
    const [showHelp, setShowHelp] = React.useState(false);
    const [showOperatorHelp, setShowOperatorHelp] = React.useState(false);
    const [splitPosition, setSplitPosition] = React.useState(66.67); // Start at 2/3 down
    const [isDraggingSplit, setIsDraggingSplit] = React.useState(false);

    // New state for tabs and Monaco editor in right pane
    const [activeTab, setActiveTab] = React.useState<'functions' | 'inputs' | 'operators'>('functions');
    const [rightSplitPosition, setRightSplitPosition] = React.useState(66); // 66% editor, 34% problems
    const [isDraggingRightSplit, setIsDraggingRightSplit] = React.useState(false);

    // Refs for Expression Editor (Monaco)
    const expressionEditorContainerRef = React.useRef<HTMLDivElement>(null);
    const expressionEditorRef = React.useRef<monaco.editor.IStandaloneCodeEditor | null>(null);

    // State to preserve cursor position when focus is lost
    const [savedCursorPosition, setSavedCursorPosition] = React.useState<monaco.Position | null>(null);
    const [savedSelection, setSavedSelection] = React.useState<monaco.Selection | null>(null);

    // State to track whether Expression Editor has content (for Apply button)
    const [hasEditorContent, setHasEditorContent] = React.useState(false);

    // State for draggable dialog
    const [dialogPosition, setDialogPosition] = React.useState({ x: 0, y: 0 });
    const [isDraggingDialog, setIsDraggingDialog] = React.useState(false);
    const [dragStart, setDragStart] = React.useState({ x: 0, y: 0 });

    // Create Monaco editor for Expression Editor
    React.useEffect(() => {
        if (!expressionEditorContainerRef.current || expressionEditorRef.current) {
            return;
        }

        console.log('[FunctionBuilder] Creating Expression Editor (Monaco)');

        // Create Monaco editor
        const editor = monaco.editor.create(expressionEditorContainerRef.current, {
            value: '',
            language: 'plaintext', // TODO: Use 'utlx' when available
            theme: 'vs-dark',
            minimap: { enabled: false },
            lineNumbers: 'on',
            scrollBeyondLastLine: false,
            wordWrap: 'on',
            fontSize: 13,
            automaticLayout: true,
            tabSize: 2,
            insertSpaces: true,
            folding: false,
            renderWhitespace: 'selection'
        });

        expressionEditorRef.current = editor;

        // Save cursor position when editor loses focus
        editor.onDidBlurEditorText(() => {
            const position = editor.getPosition();
            const selection = editor.getSelection();
            if (position) {
                setSavedCursorPosition(position);
                setSavedSelection(selection);
                console.log('[FunctionBuilder] Saved cursor position:', position, selection);
            }
        });

        // Track content changes for Apply button state
        editor.onDidChangeModelContent(() => {
            const content = editor.getModel()?.getValue() || '';
            setHasEditorContent(content.trim().length > 0);
        });

        // Cleanup on unmount
        return () => {
            if (expressionEditorRef.current) {
                expressionEditorRef.current.dispose();
                expressionEditorRef.current = null;
            }
        };
    }, []);

    // Group functions by category
    const functionsByCategory = React.useMemo(() => {
        const grouped = new Map<string, FunctionInfo[]>();

        // Deduplicate functions based on name + signature
        const seenFunctions = new Set<string>();
        const uniqueFunctions: FunctionInfo[] = [];

        for (const fn of functions) {
            const functionKey = `${fn.name}::${fn.signature}`;
            if (!seenFunctions.has(functionKey)) {
                seenFunctions.add(functionKey);
                uniqueFunctions.push(fn);
            } else {
                console.warn('[FunctionBuilder] Duplicate function detected:', fn.name, fn.signature);
            }
        }

        for (const fn of uniqueFunctions) {
            const category = fn.category || 'Other';
            if (!grouped.has(category)) {
                grouped.set(category, []);
            }
            grouped.get(category)!.push(fn);
        }

        // Filter by search query if present
        if (searchQuery.trim()) {
            const query = searchQuery.toLowerCase();
            const filtered = new Map<string, FunctionInfo[]>();

            for (const [category, fns] of grouped.entries()) {
                const matchingFns = fns.filter(fn =>
                    fn.name.toLowerCase().includes(query) ||
                    fn.description.toLowerCase().includes(query) ||
                    category.toLowerCase().includes(query)
                );

                // Sort matching functions by relevance:
                // 1. Exact name match
                // 2. Name starts with query
                // 3. Name contains query
                // 4. Description/category contains query
                const sortedMatches = matchingFns.sort((a, b) => {
                    const aName = a.name.toLowerCase();
                    const bName = b.name.toLowerCase();

                    // Exact match first
                    const aExact = aName === query;
                    const bExact = bName === query;
                    if (aExact && !bExact) return -1;
                    if (!aExact && bExact) return 1;

                    // Prefix match second
                    const aStarts = aName.startsWith(query);
                    const bStarts = bName.startsWith(query);
                    if (aStarts && !bStarts) return -1;
                    if (!aStarts && bStarts) return 1;

                    // Name contains query third
                    const aNameMatch = aName.includes(query);
                    const bNameMatch = bName.includes(query);
                    if (aNameMatch && !bNameMatch) return -1;
                    if (!aNameMatch && bNameMatch) return 1;

                    // Alphabetical as fallback
                    return aName.localeCompare(bName);
                });

                if (sortedMatches.length > 0) {
                    filtered.set(category, sortedMatches);
                }
            }

            // Sort categories by relevance (best match in category)
            const sortedCategories = Array.from(filtered.entries()).sort((a, b) => {
                const [catA, fnsA] = a;
                const [catB, fnsB] = b;

                // Get best match score for each category
                const scoreA = getBestMatchScore(fnsA[0].name, query);
                const scoreB = getBestMatchScore(fnsB[0].name, query);

                if (scoreA !== scoreB) {
                    return scoreA - scoreB; // Lower score = better match
                }

                // Fallback to alphabetical
                return catA.localeCompare(catB);
            });

            return new Map(sortedCategories);
        }

        // Sort categories alphabetically
        return new Map([...grouped.entries()].sort((a, b) => a[0].localeCompare(b[0])));
    }, [functions, searchQuery]);

    // Parse UDM into field trees
    const fieldTrees = React.useMemo(() => {
        console.log('[FunctionBuilder] Parsing field trees:', {
            availableInputsCount: availableInputs.length,
            availableInputs: availableInputs,
            udmMapSize: udmMap.size,
            udmMapKeys: Array.from(udmMap.keys())
        });

        return availableInputs.map(inputName => {
            const udm = udmMap.get(inputName);
            const format = inputFormatsMap.get(inputName) || 'json'; // Default to json if not found
            console.log('[FunctionBuilder] Parsing tree for', inputName, '- Format:', format, '- UDM length:', udm?.length || 0);
            return parseUdmToTree(inputName, format, udm);
        });
    }, [availableInputs, udmMap, inputFormatsMap]);

    // Auto-expand categories when searching
    React.useEffect(() => {
        if (searchQuery.trim()) {
            // Expand all categories that have results
            setExpandedCategories(new Set(functionsByCategory.keys()));
        }
    }, [searchQuery, functionsByCategory]);

    const toggleCategory = (category: string) => {
        const newExpanded = new Set(expandedCategories);
        if (newExpanded.has(category)) {
            newExpanded.delete(category);
        } else {
            newExpanded.add(category);
        }
        setExpandedCategories(newExpanded);
    };

    /**
     * Analyze cursor position in Expression Editor (Monaco)
     * Uses the full context analyzer from context-analyzer.ts
     */
    const analyzeExpressionEditorContext = (): InsertionContext => {
        const editor = expressionEditorRef.current;
        if (!editor) {
            return {
                type: 'top-level',
                lineNumber: 0,
                column: 0,
                lineContent: '',
                textBeforeCursor: '',
                textAfterCursor: ''
            };
        }

        const model = editor.getModel();
        if (!model) {
            return {
                type: 'top-level',
                lineNumber: 0,
                column: 0,
                lineContent: '',
                textBeforeCursor: '',
                textAfterCursor: ''
            };
        }

        // Use saved cursor position if editor doesn't have focus
        // Otherwise use current position
        const hasFocus = editor.hasTextFocus();
        const position = hasFocus
            ? editor.getPosition()
            : savedCursorPosition;
        const selection = hasFocus
            ? editor.getSelection()
            : savedSelection;

        if (!position) {
            return {
                type: 'top-level',
                lineNumber: 0,
                column: 0,
                lineContent: '',
                textBeforeCursor: '',
                textAfterCursor: ''
            };
        }

        console.log('[FunctionBuilder] Analyzing Expression Editor:', {
            hasFocus,
            position,
            usingSaved: !hasFocus
        });

        // Use the same context analyzer as main editor
        return analyzeInsertionContext(model, position, selection ?? undefined);
    };

    const handleInsertFunction = () => {
        if (!selectedFunction) return;

        // Analyze context from Expression Editor
        const expressionContext = analyzeExpressionEditorContext();

        console.log('[FunctionBuilder] Using Expression Editor context:', expressionContext);

        // Use smart context-aware insertion
        const code = generateFunctionInsertion(selectedFunction, expressionContext, availableInputs);

        // Insert into Monaco editor, passing context for range replacement
        insertIntoMonaco(code, expressionContext);

        // Restore focus to Expression Editor
        setTimeout(() => {
            const editor = expressionEditorRef.current;
            if (editor) {
                editor.focus();
                console.log('[FunctionBuilder] Restored focus to Expression Editor');
            }
        }, 0);
    };

    const handleInsertOperator = (operator: OperatorInfo) => {
        // Analyze context from Expression Editor
        const expressionContext = analyzeExpressionEditorContext();

        console.log('[FunctionBuilder] Inserting operator:', operator.symbol, 'with context:', expressionContext);

        // Use smart context-aware insertion
        const code = generateOperatorInsertion(operator, expressionContext);

        // Insert into Monaco editor, passing context for range replacement
        insertIntoMonaco(code, expressionContext);

        // Restore focus to Expression Editor
        setTimeout(() => {
            const editor = expressionEditorRef.current;
            if (editor) {
                editor.focus();
                console.log('[FunctionBuilder] Restored focus to Expression Editor');
            }
        }, 0);
    };

    const handleInsertField = (inputName: string, fieldPath: string) => {
        // Don't add a dot if fieldPath starts with [ (for arrays)
        const separator = fieldPath.startsWith('[') ? '' : '.';
        const code = fieldPath ? `$${inputName}${separator}${fieldPath}` : `$${inputName}`;

        // Insert into Monaco editor in right pane
        insertIntoMonaco(code);
    };

    const insertIntoMonaco = (code: string, context?: InsertionContext) => {
        const editor = expressionEditorRef.current;
        if (!editor) {
            console.error('[FunctionBuilder] No Expression Editor available');
            return;
        }

        const model = editor.getModel();
        if (!model) {
            console.error('[FunctionBuilder] No model available');
            return;
        }

        const position = editor.getPosition();
        if (!position) {
            console.error('[FunctionBuilder] No cursor position');
            return;
        }

        // Determine the range to replace
        let range: monaco.Range;

        // If context has a cursor value with a range, replace that range
        if (context?.cursorValue?.range) {
            range = context.cursorValue.range;
            console.log('[FunctionBuilder] Replacing expression range:', range);
        } else {
            // Otherwise, insert at cursor position (zero-width range)
            range = new monaco.Range(
                position.lineNumber,
                position.column,
                position.lineNumber,
                position.column
            );
            console.log('[FunctionBuilder] Inserting at cursor position:', position);
        }

        editor.executeEdits('function-builder', [{
            range: range,
            text: code,
            forceMoveMarkers: true
        }]);

        // Find cursor placeholder (|) and position cursor there
        const placeholderMatch = code.match(/\|/);
        if (placeholderMatch) {
            const lines = code.substring(0, placeholderMatch.index).split('\n');
            const lastLine = lines[lines.length - 1];

            // Calculate new position from the start of the range
            const startLine = range.startLineNumber;
            const startColumn = range.startColumn;

            const newPosition = new monaco.Position(
                startLine + lines.length - 1,
                lines.length === 1 ? startColumn + lastLine.length : lastLine.length + 1
            );

            editor.setPosition(newPosition);

            // Remove the | placeholder
            const placeholderRange = new monaco.Range(
                newPosition.lineNumber,
                newPosition.column,
                newPosition.lineNumber,
                newPosition.column + 1
            );
            editor.executeEdits('function-builder-cleanup', [{
                range: placeholderRange,
                text: '',
                forceMoveMarkers: true
            }]);
        }

        editor.focus();
    };

    // Handle left split pane dragging (Standard Library Functions)
    const handleSplitMouseDown = (e: React.MouseEvent) => {
        e.preventDefault();
        setIsDraggingSplit(true);
    };

    React.useEffect(() => {
        if (!isDraggingSplit) return;

        const handleMouseMove = (e: MouseEvent) => {
            const leftPane = document.querySelector('.left-pane') as HTMLElement;
            if (!leftPane) return;

            const rect = leftPane.getBoundingClientRect();
            const newPosition = ((e.clientY - rect.top) / rect.height) * 100;

            // Constrain between 20% and 80%
            setSplitPosition(Math.min(Math.max(newPosition, 20), 80));
        };

        const handleMouseUp = () => {
            setIsDraggingSplit(false);
        };

        document.addEventListener('mousemove', handleMouseMove);
        document.addEventListener('mouseup', handleMouseUp);

        return () => {
            document.removeEventListener('mousemove', handleMouseMove);
            document.removeEventListener('mouseup', handleMouseUp);
        };
    }, [isDraggingSplit]);

    // Handle right split pane dragging (Monaco editor / Problems)
    const handleRightSplitMouseDown = (e: React.MouseEvent) => {
        e.preventDefault();
        setIsDraggingRightSplit(true);
    };

    React.useEffect(() => {
        if (!isDraggingRightSplit) return;

        const handleMouseMove = (e: MouseEvent) => {
            const rightPane = document.querySelector('.right-pane') as HTMLElement;
            if (!rightPane) return;

            const rect = rightPane.getBoundingClientRect();
            const newPosition = ((e.clientY - rect.top) / rect.height) * 100;

            // Constrain between 30% and 85%
            setRightSplitPosition(Math.min(Math.max(newPosition, 30), 85));
        };

        const handleMouseUp = () => {
            setIsDraggingRightSplit(false);
        };

        document.addEventListener('mousemove', handleMouseMove);
        document.addEventListener('mouseup', handleMouseUp);

        return () => {
            document.removeEventListener('mousemove', handleMouseMove);
            document.removeEventListener('mouseup', handleMouseUp);
        };
    }, [isDraggingRightSplit]);

    // Handle dialog dragging
    const handleDialogMouseDown = (e: React.MouseEvent) => {
        // Only start dragging if clicking on the header (not on inputs or buttons)
        const target = e.target as HTMLElement;
        if (
            target.tagName === 'INPUT' ||
            target.tagName === 'BUTTON' ||
            target.closest('button') ||
            target.closest('input')
        ) {
            return;
        }

        setIsDraggingDialog(true);
        setDragStart({
            x: e.clientX - dialogPosition.x,
            y: e.clientY - dialogPosition.y
        });
    };

    React.useEffect(() => {
        if (!isDraggingDialog) return;

        const handleMouseMove = (e: MouseEvent) => {
            setDialogPosition({
                x: e.clientX - dragStart.x,
                y: e.clientY - dragStart.y
            });
        };

        const handleMouseUp = () => {
            setIsDraggingDialog(false);
        };

        document.addEventListener('mousemove', handleMouseMove);
        document.addEventListener('mouseup', handleMouseUp);

        return () => {
            document.removeEventListener('mousemove', handleMouseMove);
            document.removeEventListener('mouseup', handleMouseUp);
        };
    }, [isDraggingDialog, dragStart]);

    return (
        <div className='utlx-dialog-overlay'>
            <div
                className='utlx-function-builder-dialog'
                style={{
                    transform: `translate(calc(-50% + ${dialogPosition.x}px), calc(-50% + ${dialogPosition.y}px))`,
                    cursor: isDraggingDialog ? 'grabbing' : 'default'
                }}
            >
                {/* Header */}
                <div
                    className='dialog-header'
                    onMouseDown={handleDialogMouseDown}
                    style={{ cursor: isDraggingDialog ? 'grabbing' : 'grab' }}
                >
                    <div className='title-section'>
                        <span className='codicon codicon-symbol-method'></span>
                        <h2>Function Builder</h2>
                    </div>
                    <input
                        type='text'
                        className='search-box'
                        placeholder='Search functions and fields...'
                        value={searchQuery}
                        onChange={e => setSearchQuery(e.target.value)}
                        autoFocus
                    />
                    <button className='close-btn' onClick={onClose} title='Close'>
                        <span className='codicon codicon-close'></span>
                    </button>
                </div>

                {/* Body: Two-pane layout */}
                <div className='dialog-body'>
                    {/* Left Pane: Tabbed interface for Functions and Inputs */}
                    <div className='left-pane'>
                        {/* Tabs Header */}
                        <div className='tab-header'>
                            <button
                                className={`tab-button ${activeTab === 'functions' ? 'active' : ''}`}
                                onClick={() => setActiveTab('functions')}
                            >
                                <span className='codicon codicon-symbol-method'></span>
                                Standard Library
                            </button>
                            <button
                                className={`tab-button ${activeTab === 'inputs' ? 'active' : ''}`}
                                onClick={() => setActiveTab('inputs')}
                            >
                                <span className='codicon codicon-symbol-variable'></span>
                                Available Inputs
                            </button>
                            <button
                                className={`tab-button ${activeTab === 'operators' ? 'active' : ''}`}
                                onClick={() => setActiveTab('operators')}
                            >
                                <span className='codicon codicon-symbol-operator'></span>
                                Operators
                            </button>
                        </div>

                        {/* Tab Content: Standard Library Functions */}
                        {activeTab === 'functions' && (
                            <>
                                {/* Top Part: Function Tree (compact) */}
                                <div className='function-list-pane' style={{ height: `${splitPosition}%` }}>
                                    <div className='function-tree'>
                                {functionsByCategory.size === 0 ? (
                                    <div className='empty-state'>
                                        No functions found matching "{searchQuery}"
                                    </div>
                                ) : (
                                    Array.from(functionsByCategory.entries()).map(([category, categoryFunctions]) => (
                                        <div key={category} className='category'>
                                            <div
                                                className='category-header'
                                                onClick={() => toggleCategory(category)}
                                            >
                                                <span className={`codicon codicon-chevron-${expandedCategories.has(category) ? 'down' : 'right'}`}></span>
                                                <span className='category-name'>{category}</span>
                                                <span className='function-count'>({categoryFunctions.length})</span>
                                            </div>

                                            {expandedCategories.has(category) && (
                                                <div className='category-functions'>
                                                    {categoryFunctions.map((fn, index) => (
                                                        <div
                                                            key={`${fn.name}-${index}`}
                                                            className={`function-item-compact ${selectedFunction?.name === fn.name && selectedFunction?.signature === fn.signature ? 'selected' : ''}`}
                                                            onClick={() => setSelectedFunction(fn)}
                                                            title={fn.signature}
                                                        >
                                                            <span className='codicon codicon-symbol-method'></span>
                                                            <span className='function-name'>{fn.name}</span>
                                                        </div>
                                                    ))}
                                                </div>
                                            )}
                                        </div>
                                    ))
                                )}
                            </div>
                        </div>

                        {/* Split Divider */}
                        <div
                            className='split-divider'
                            onMouseDown={handleSplitMouseDown}
                            style={{ cursor: isDraggingSplit ? 'row-resize' : 'row-resize' }}
                        >
                            <div className='split-handle'></div>
                        </div>

                        {/* Bottom Part: Function Details */}
                        <div className='function-details-pane' style={{ height: `${100 - splitPosition}%` }}>
                            <h3>Function Details</h3>
                            {selectedFunction ? (
                                <div className='function-details-content'>
                                    <div className='details-header'>
                                        <div className='details-title'>
                                            <span className='codicon codicon-symbol-method'></span>
                                            <span className='function-name'>{selectedFunction.name}</span>
                                        </div>
                                        <div className='details-actions'>
                                            <button
                                                className='help-btn'
                                                title='Show full help and examples'
                                                onClick={() => setShowHelp(true)}
                                            >
                                                <span className='codicon codicon-question'></span>
                                                Help
                                            </button>
                                            <button
                                                className='insert-btn'
                                                title='Insert into editor'
                                                onClick={handleInsertFunction}
                                            >
                                                <span className='codicon codicon-insert'></span>
                                                Insert
                                            </button>
                                            <button
                                                className='copy-btn'
                                                title='Copy to clipboard'
                                                onClick={() => {
                                                    if (!selectedFunction) return;
                                                    let code: string;
                                                    if (cursorContext) {
                                                        code = generateFunctionInsertion(selectedFunction, cursorContext, availableInputs);
                                                    } else {
                                                        const fallbackContext: InsertionContext = {
                                                            type: 'top-level',
                                                            lineNumber: 0,
                                                            column: 0,
                                                            lineContent: '',
                                                            textBeforeCursor: '',
                                                            textAfterCursor: ''
                                                        };
                                                        code = generateFunctionInsertion(selectedFunction, fallbackContext, availableInputs);
                                                    }
                                                    navigator.clipboard.writeText(code);
                                                    console.log('[FunctionBuilder] Copied function to clipboard:', code);
                                                }}
                                            >
                                                <span className='codicon codicon-copy'></span>
                                                Copy
                                            </button>
                                        </div>
                                    </div>
                                    <div className='details-signature'>
                                        <strong>Signature:</strong>
                                        <code>{selectedFunction.signature}</code>
                                    </div>
                                    <div className='details-description'>
                                        <strong>Description:</strong>
                                        <p>{selectedFunction.description}</p>
                                    </div>
                                    {selectedFunction.parameters && selectedFunction.parameters.length > 0 && (
                                        <div className='details-parameters'>
                                            <strong>Parameters:</strong>
                                            <ul>
                                                {selectedFunction.parameters.map(param => (
                                                    <li key={param.name}>
                                                        <code>{param.name}</code>
                                                        <span className='param-type'>{param.type}</span>
                                                        {param.optional && <span className='param-optional'>(optional)</span>}
                                                        {param.description && <span className='param-desc'>- {param.description}</span>}
                                                    </li>
                                                ))}
                                            </ul>
                                        </div>
                                    )}
                                    {selectedFunction.returnType && (
                                        <div className='details-returns'>
                                            <strong>Returns:</strong>
                                            <span className='return-type'>{selectedFunction.returnType}</span>
                                        </div>
                                    )}
                                </div>
                            ) : (
                                <div className='empty-details'>
                                    <span className='codicon codicon-info'></span>
                                    <p>Select a function to view details</p>
                                </div>
                            )}
                        </div>
                            </>
                        )}

                        {/* Tab Content: Available Inputs */}
                        {activeTab === 'inputs' && (
                            <div className='inputs-tab-content' style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
                                <FieldTree
                                    fieldTrees={fieldTrees}
                                    onInsertField={handleInsertField}
                                    onInsertValue={insertIntoMonaco}
                                    udmMap={udmMap}
                                />
                            </div>
                        )}

                        {/* Tab Content: Operators */}
                        {activeTab === 'operators' && (
                            <>
                                {/* Top Part: Operators Tree */}
                                <div className='operators-list-pane' style={{ height: `${splitPosition}%` }}>
                                    <OperatorsTree
                                        onInsertOperator={handleInsertOperator}
                                        selectedOperator={selectedOperator}
                                        onSelectOperator={setSelectedOperator}
                                    />
                                </div>

                                {/* Split Divider */}
                                <div
                                    className='split-divider'
                                    onMouseDown={handleSplitMouseDown}
                                    style={{ cursor: isDraggingSplit ? 'row-resize' : 'row-resize' }}
                                >
                                    <div className='split-handle'></div>
                                </div>

                                {/* Bottom Part: Operator Details */}
                                <div className='operator-details-pane' style={{ height: `${100 - splitPosition}%` }}>
                                    <h3>Operator Details</h3>
                                    {selectedOperator ? (
                                        <div className='operator-details-content'>
                                            <div className='details-header'>
                                                <div className='details-title'>
                                                    <span className='operator-symbol-large'>{selectedOperator.symbol}</span>
                                                    <span className='operator-name-large'>{selectedOperator.name}</span>
                                                </div>
                                                <div className='details-actions'>
                                                    <button
                                                        className='help-btn'
                                                        title='Show full help and examples'
                                                        onClick={() => setShowOperatorHelp(true)}
                                                    >
                                                        <span className='codicon codicon-question'></span>
                                                        Help
                                                    </button>
                                                    <button
                                                        className='insert-btn'
                                                        title='Insert operator'
                                                        onClick={() => handleInsertOperator(selectedOperator)}
                                                    >
                                                        <span className='codicon codicon-insert'></span>
                                                        Insert
                                                    </button>
                                                </div>
                                            </div>
                                            <div className='details-description'>
                                                <strong>Description:</strong>
                                                <p>{selectedOperator.description}</p>
                                            </div>
                                            <div className='details-syntax'>
                                                <strong>Syntax:</strong>
                                                <code>{selectedOperator.syntax}</code>
                                            </div>
                                            <div className='details-precedence'>
                                                <strong>Precedence:</strong>
                                                <span> {selectedOperator.precedence} ({selectedOperator.associativity} associativity)</span>
                                            </div>
                                            {selectedOperator.examples && selectedOperator.examples.length > 0 && (
                                                <div className='details-examples'>
                                                    <strong>Examples:</strong>
                                                    <ul>
                                                        {selectedOperator.examples.map((example, idx) => (
                                                            <li key={idx}>
                                                                <code>{example}</code>
                                                            </li>
                                                        ))}
                                                    </ul>
                                                </div>
                                            )}
                                        </div>
                                    ) : (
                                        <div className='empty-details'>
                                            <span className='codicon codicon-info'></span>
                                            <p>Select an operator to view details</p>
                                        </div>
                                    )}
                                </div>
                            </>
                        )}
                    </div>

                    {/* Right Pane: Monaco Editor + Problems */}
                    <div className='right-pane'>
                        {/* Top Part: UTLX Monaco Editor */}
                        <div className='monaco-editor-pane' style={{ height: `${rightSplitPosition}%` }}>
                            <div className='monaco-header'>
                                <h3>UTLX Expression Editor</h3>
                                <div className='monaco-toolbar'>
                                    <button
                                        className='toolbar-btn'
                                        onClick={() => {
                                            const editor = expressionEditorRef.current;
                                            if (editor) {
                                                editor.focus();
                                                editor.trigger('toolbar', 'undo', null);
                                            }
                                        }}
                                        title='Undo (Ctrl+Z / Cmd+Z)'
                                    >
                                        <span className='codicon codicon-discard'></span>
                                    </button>
                                    <button
                                        className='toolbar-btn'
                                        onClick={() => {
                                            const editor = expressionEditorRef.current;
                                            if (editor) {
                                                editor.focus();
                                                editor.trigger('toolbar', 'redo', null);
                                            }
                                        }}
                                        title='Redo (Ctrl+Y / Cmd+Shift+Z)'
                                    >
                                        <span className='codicon codicon-redo'></span>
                                    </button>
                                    <div className='toolbar-separator'></div>
                                    <button
                                        className='toolbar-btn'
                                        onClick={async () => {
                                            const editor = expressionEditorRef.current;
                                            if (editor) {
                                                editor.focus();
                                                const selection = editor.getSelection();
                                                if (selection && !selection.isEmpty()) {
                                                    const selectedText = editor.getModel()?.getValueInRange(selection);
                                                    if (selectedText) {
                                                        try {
                                                            await navigator.clipboard.writeText(selectedText);
                                                            // Delete the selected text
                                                            editor.executeEdits('toolbar-cut', [{
                                                                range: selection,
                                                                text: '',
                                                                forceMoveMarkers: true
                                                            }]);
                                                        } catch (err) {
                                                            console.error('Failed to cut:', err);
                                                        }
                                                    }
                                                }
                                            }
                                        }}
                                        title='Cut (Ctrl+X / Cmd+X)'
                                    >
                                        <span className='codicon codicon-clippy'></span>
                                    </button>
                                    <button
                                        className='toolbar-btn'
                                        onClick={async () => {
                                            const editor = expressionEditorRef.current;
                                            if (editor) {
                                                editor.focus();
                                                const selection = editor.getSelection();
                                                if (selection && !selection.isEmpty()) {
                                                    const selectedText = editor.getModel()?.getValueInRange(selection);
                                                    if (selectedText) {
                                                        try {
                                                            await navigator.clipboard.writeText(selectedText);
                                                            console.log('[Toolbar] Copied to clipboard:', selectedText);
                                                        } catch (err) {
                                                            console.error('Failed to copy:', err);
                                                        }
                                                    }
                                                } else {
                                                    // No selection - copy entire content
                                                    const content = editor.getModel()?.getValue();
                                                    if (content) {
                                                        try {
                                                            await navigator.clipboard.writeText(content);
                                                            console.log('[Toolbar] Copied all content to clipboard');
                                                        } catch (err) {
                                                            console.error('Failed to copy:', err);
                                                        }
                                                    }
                                                }
                                            }
                                        }}
                                        title='Copy (Ctrl+C / Cmd+C)'
                                    >
                                        <span className='codicon codicon-copy'></span>
                                    </button>
                                    <button
                                        className='toolbar-btn'
                                        onClick={async () => {
                                            const editor = expressionEditorRef.current;
                                            if (editor) {
                                                editor.focus();
                                                try {
                                                    const text = await navigator.clipboard.readText();
                                                    if (text) {
                                                        const position = editor.getPosition();
                                                        if (position) {
                                                            editor.executeEdits('toolbar-paste', [{
                                                                range: new monaco.Range(
                                                                    position.lineNumber,
                                                                    position.column,
                                                                    position.lineNumber,
                                                                    position.column
                                                                ),
                                                                text: text,
                                                                forceMoveMarkers: true
                                                            }]);
                                                            console.log('[Toolbar] Pasted from clipboard');
                                                        }
                                                    }
                                                } catch (err) {
                                                    console.error('Failed to paste:', err);
                                                }
                                            }
                                        }}
                                        title='Paste (Ctrl+V / Cmd+V)'
                                    >
                                        <span className='codicon codicon-insert'></span>
                                    </button>
                                </div>
                            </div>
                            <div
                                ref={expressionEditorContainerRef}
                                className='monaco-editor-container'
                                style={{ height: '100%', width: '100%', flex: 1 }}
                            />
                        </div>

                        {/* Split Divider */}
                        <div
                            className='split-divider'
                            onMouseDown={handleRightSplitMouseDown}
                        >
                            <div className='split-handle'></div>
                        </div>

                        {/* Bottom Part: Problems / Suggestions */}
                        <div className='problems-pane' style={{ height: `${100 - rightSplitPosition}%` }}>
                            <h3>Problems & Suggestions</h3>
                            <div className='problems-content'>
                                <div className='empty-problems'>
                                    <span className='codicon codicon-info'></span>
                                    <p>Expressions will be validated here</p>
                                    <small>Problems, suggestions, and evaluation results will appear as you type</small>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Footer: Context info and action buttons */}
                <div className='dialog-footer'>
                    <div className='footer-left'>
                        {cursorContext && (
                            <span className='context-info'>
                                <span className='codicon codicon-info'></span>
                                Context: <strong>{cursorContext.type}</strong>
                                {selectedFunction && ` | Will insert: ${generateInsertionPreview(selectedFunction, cursorContext, availableInputs)}`}
                            </span>
                        )}
                        {!cursorContext && (
                            <span className='context-info'>
                                <span className='codicon codicon-info'></span>
                                {selectedFunction ? 'Select a location in the editor or use Apply to insert at cursor' : 'Select a function to insert'}
                            </span>
                        )}
                    </div>
                    <div className='footer-actions'>
                        <button
                            className='footer-btn apply-btn'
                            onClick={() => {
                                const editor = expressionEditorRef.current;
                                const content = editor?.getModel()?.getValue() || '';
                                if (content.trim()) {
                                    onInsert(content);
                                    onClose();
                                }
                            }}
                            disabled={!hasEditorContent}
                            title='Apply Expression to Main Editor and close'
                        >
                            <span className='codicon codicon-check'></span>
                            Apply to Main Editor
                        </button>
                        <button
                            className='footer-btn close-btn-footer'
                            onClick={onClose}
                            title='Close without inserting'
                        >
                            <span className='codicon codicon-close'></span>
                            Close
                        </button>
                    </div>
                </div>

                {/* Help Modal - Placeholder */}
                {showHelp && selectedFunction && (
                    <div className='help-modal-overlay' onClick={() => setShowHelp(false)}>
                        <div className='help-modal' onClick={e => e.stopPropagation()}>
                            <div className='help-header'>
                                <h2>{selectedFunction.name}</h2>
                                <button onClick={() => setShowHelp(false)}>
                                    <span className='codicon codicon-close'></span>
                                </button>
                            </div>
                            <div className='help-body'>
                                <section>
                                    <h3>Signature</h3>
                                    <code>{selectedFunction.signature}</code>
                                </section>
                                <section>
                                    <h3>Description</h3>
                                    <p>{selectedFunction.description}</p>
                                </section>
                                {selectedFunction.parameters && selectedFunction.parameters.length > 0 && (
                                    <section>
                                        <h3>Parameters</h3>
                                        <table>
                                            <thead>
                                                <tr>
                                                    <th>Name</th>
                                                    <th>Type</th>
                                                    <th>Description</th>
                                                    <th>Required</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {selectedFunction.parameters.map(param => (
                                                    <tr key={param.name}>
                                                        <td><code>{param.name}</code></td>
                                                        <td>{param.type}</td>
                                                        <td>{param.description || '-'}</td>
                                                        <td>{param.optional ? 'No' : 'Yes'}</td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </section>
                                )}
                                <section>
                                    <h3>Returns</h3>
                                    <p>{selectedFunction.returnType}</p>
                                </section>
                                {selectedFunction.examples && selectedFunction.examples.length > 0 && (
                                    <section>
                                        <h3>Examples</h3>
                                        {selectedFunction.examples.map((example, idx) => (
                                            <div key={idx} className='example'>
                                                <pre><code>{example}</code></pre>
                                                <button onClick={() => { onInsert(example); setShowHelp(false); }}>
                                                    Try This Example
                                                </button>
                                            </div>
                                        ))}
                                    </section>
                                )}
                            </div>
                        </div>
                    </div>
                )}

                {/* Operator Help Modal */}
                {showOperatorHelp && selectedOperator && (
                    <div className='help-modal-overlay' onClick={() => setShowOperatorHelp(false)}>
                        <div className='help-modal' onClick={e => e.stopPropagation()}>
                            <div className='help-header'>
                                <h2>{selectedOperator.symbol} - {selectedOperator.name}</h2>
                                <button onClick={() => setShowOperatorHelp(false)}>
                                    <span className='codicon codicon-close'></span>
                                </button>
                            </div>
                            <div className='help-body'>
                                <section>
                                    <h3>Symbol</h3>
                                    <code style={{ fontSize: '18px', fontWeight: 'bold' }}>{selectedOperator.symbol}</code>
                                </section>
                                <section>
                                    <h3>Description</h3>
                                    <p>{selectedOperator.description}</p>
                                </section>
                                <section>
                                    <h3>Syntax</h3>
                                    <code>{selectedOperator.syntax}</code>
                                </section>
                                <section>
                                    <h3>Category</h3>
                                    <p>{selectedOperator.category}</p>
                                </section>
                                <section>
                                    <h3>Precedence & Associativity</h3>
                                    <p>Precedence: <strong>{selectedOperator.precedence}</strong> (lower numbers = higher precedence)</p>
                                    <p>Associativity: <strong>{selectedOperator.associativity}</strong></p>
                                </section>
                                {selectedOperator.examples && selectedOperator.examples.length > 0 && (
                                    <section>
                                        <h3>Examples</h3>
                                        {selectedOperator.examples.map((example, idx) => (
                                            <div key={idx} className='example'>
                                                <pre><code>{example}</code></pre>
                                            </div>
                                        ))}
                                    </section>
                                )}
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

