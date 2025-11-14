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
import { FunctionInfo } from '../../common/protocol';
import { parseUdmToTree, UdmInputTree } from './udm-parser';
import { FieldTree } from './field-tree';
import { InsertionContext, getContextDescription } from './context-analyzer';
import { generateFunctionInsertion, generateInsertionPreview } from './insertion-generator';

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
    const [showHelp, setShowHelp] = React.useState(false);
    const [splitPosition, setSplitPosition] = React.useState(66.67); // Start at 2/3 down
    const [isDraggingSplit, setIsDraggingSplit] = React.useState(false);

    // New state for tabs and Monaco editor in right pane
    const [activeTab, setActiveTab] = React.useState<'functions' | 'inputs'>('functions');
    const [editorContent, setEditorContent] = React.useState('');
    const [rightSplitPosition, setRightSplitPosition] = React.useState(66); // 66% editor, 34% problems
    const [isDraggingRightSplit, setIsDraggingRightSplit] = React.useState(false);

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

    const handleInsertFunction = () => {
        if (!selectedFunction) return;

        // Use smart context-aware insertion
        let code: string;
        if (cursorContext) {
            code = generateFunctionInsertion(selectedFunction, cursorContext, availableInputs);
        } else {
            // Fallback: use top-level context
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

        // Insert into Monaco editor in right pane
        insertIntoMonaco(code);
    };

    const handleInsertField = (inputName: string, fieldPath: string) => {
        // Don't add a dot if fieldPath starts with [ (for arrays)
        const separator = fieldPath.startsWith('[') ? '' : '.';
        const code = fieldPath ? `$${inputName}${separator}${fieldPath}` : `$${inputName}`;

        // Insert into Monaco editor in right pane
        insertIntoMonaco(code);
    };

    const insertIntoMonaco = (code: string) => {
        // Append code to editor content
        // If editor has content, add on new line; otherwise just set it
        setEditorContent(prev => {
            if (prev.trim()) {
                return prev + '\n' + code;
            }
            return code;
        });
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

    return (
        <div className='utlx-dialog-overlay' onClick={onClose}>
            <div className='utlx-function-builder-dialog' onClick={e => e.stopPropagation()}>
                {/* Header */}
                <div className='dialog-header'>
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
                    </div>

                    {/* Right Pane: Monaco Editor + Problems */}
                    <div className='right-pane'>
                        {/* Top Part: UTLX Monaco Editor */}
                        <div className='monaco-editor-pane' style={{ height: `${rightSplitPosition}%` }}>
                            <div className='monaco-header'>
                                <h3>UTLX Expression Editor</h3>
                                <button
                                    className='apply-to-main-btn'
                                    onClick={() => {
                                        if (editorContent.trim()) {
                                            onInsert(editorContent);
                                            onClose();
                                        }
                                    }}
                                    disabled={!editorContent.trim()}
                                    title='Apply to main editor and close'
                                >
                                    <span className='codicon codicon-check'></span>
                                    Apply to Main Editor
                                </button>
                            </div>
                            <textarea
                                className='monaco-placeholder'
                                value={editorContent}
                                onChange={(e) => setEditorContent(e.target.value)}
                                placeholder='Build your UTLX expression here...\n\nInsert functions and fields from the left pane.'
                                spellCheck={false}
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
                                if (selectedFunction) {
                                    handleInsertFunction();
                                    onClose();
                                }
                            }}
                            disabled={!selectedFunction}
                            title={selectedFunction ? 'Insert function into editor and close' : 'Select a function first'}
                        >
                            <span className='codicon codicon-check'></span>
                            Apply
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
            </div>
        </div>
    );
};

