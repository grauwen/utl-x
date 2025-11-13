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

/**
 * Insertion context from cursor analysis
 */
export interface InsertionContext {
    type: 'lambda-body' | 'function-args' | 'object-field-value' | 'array-element' | 'top-level';
    lambdaParam?: string;  // e.g., "e" from "map($input, e => |)"
    functionName?: string;  // e.g., "filter" from "filter(|)"
    fieldName?: string;     // e.g., "result" from "result: |"
}

/**
 * Props for the Function Builder Dialog
 */
export interface FunctionBuilderDialogProps {
    functions: FunctionInfo[];
    availableInputs: string[];
    udmMap: Map<string, string>;
    cursorContext: InsertionContext | null;
    onInsert: (code: string) => void;
    onClose: () => void;
}

/**
 * Function Builder Dialog Component
 */
export const FunctionBuilderDialog: React.FC<FunctionBuilderDialogProps> = ({
    functions,
    availableInputs,
    udmMap,
    cursorContext,
    onInsert,
    onClose
}) => {
    const [searchQuery, setSearchQuery] = React.useState('');
    const [expandedCategories, setExpandedCategories] = React.useState<Set<string>>(new Set());
    const [selectedFunction, setSelectedFunction] = React.useState<FunctionInfo | null>(null);
    const [showHelp, setShowHelp] = React.useState(false);

    // Group functions by category
    const functionsByCategory = React.useMemo(() => {
        const grouped = new Map<string, FunctionInfo[]>();

        for (const fn of functions) {
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

                if (matchingFns.length > 0) {
                    filtered.set(category, matchingFns);
                }
            }

            return filtered;
        }

        // Sort categories alphabetically
        return new Map([...grouped.entries()].sort((a, b) => a[0].localeCompare(b[0])));
    }, [functions, searchQuery]);

    // Parse UDM into field trees
    const fieldTrees = React.useMemo(() => {
        return availableInputs.map(inputName =>
            parseUdmToTree(inputName, udmMap.get(inputName))
        );
    }, [availableInputs, udmMap]);

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

        // For now, insert a simple template
        // TODO: Use insertion-generator for smart context-aware insertion
        const code = generateSimpleTemplate(selectedFunction, availableInputs);
        onInsert(code);
    };

    const handleInsertField = (inputName: string, fieldPath: string) => {
        const code = `$${inputName}.${fieldPath}`;
        onInsert(code);
    };

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
                    {/* Left Pane: Functions */}
                    <div className='left-pane'>
                        <h3>Standard Library Functions</h3>
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
                                                {categoryFunctions.map(fn => (
                                                    <div
                                                        key={fn.name}
                                                        className={`function-item ${selectedFunction?.name === fn.name ? 'selected' : ''}`}
                                                        onClick={() => setSelectedFunction(fn)}
                                                    >
                                                        <div className='function-header'>
                                                            <span className='function-name'>{fn.name}</span>
                                                            <div className='function-actions'>
                                                                <button
                                                                    className='help-btn'
                                                                    title='Show help and examples'
                                                                    onClick={e => {
                                                                        e.stopPropagation();
                                                                        setSelectedFunction(fn);
                                                                        setShowHelp(true);
                                                                    }}
                                                                >
                                                                    <span className='codicon codicon-question'></span>
                                                                </button>
                                                                <button
                                                                    className='insert-btn'
                                                                    title='Insert into editor'
                                                                    onClick={e => {
                                                                        e.stopPropagation();
                                                                        setSelectedFunction(fn);
                                                                        handleInsertFunction();
                                                                    }}
                                                                >
                                                                    <span className='codicon codicon-insert'></span>
                                                                </button>
                                                            </div>
                                                        </div>
                                                        <div className='function-signature'>{fn.signature}</div>
                                                        <div className='function-description'>{fn.description}</div>
                                                    </div>
                                                ))}
                                            </div>
                                        )}
                                    </div>
                                ))
                            )}
                        </div>
                    </div>

                    {/* Right Pane: UDM Fields */}
                    <div className='right-pane'>
                        <h3>Available Inputs</h3>
                        <FieldTree
                            fieldTrees={fieldTrees}
                            onInsertField={handleInsertField}
                        />
                    </div>
                </div>

                {/* Footer: Context info */}
                <div className='dialog-footer'>
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
                            Select a function to insert
                        </span>
                    )}
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

/**
 * Generate a simple function template (will be replaced by smart insertion generator)
 */
function generateSimpleTemplate(fn: FunctionInfo, inputs: string[]): string {
    const input = inputs[0] || 'input';
    const category = fn.category?.toLowerCase() || '';

    // Array functions need lambda
    if (category.includes('array') && fn.name.match(/^(map|filter|flatMap)$/)) {
        return `${fn.name}($${input}, e => e.|)`;
    }

    // Aggregation functions
    if (category.includes('aggregation') || fn.name.match(/^(count|sum|avg|min|max)$/)) {
        return `${fn.name}($${input})`;
    }

    // String functions
    if (category.includes('string')) {
        return `${fn.name}(|)`;
    }

    // Default: function with placeholder
    return `${fn.name}(|)`;
}

/**
 * Generate insertion preview text
 */
function generateInsertionPreview(fn: FunctionInfo, context: InsertionContext, inputs: string[]): string {
    const template = generateSimpleTemplate(fn, inputs);
    return template.replace(/\|/g, '...');
}
