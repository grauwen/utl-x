/**
 * Function Palette
 *
 * Searchable sidebar listing available stdlib functions and operators.
 * Items are draggable — drop them onto the canvas to create nodes.
 */

import * as React from 'react';
import type { FunctionInfo, OperatorInfo } from '../../common/protocol';

export interface FunctionPaletteProps {
    functions: FunctionInfo[];
    operators: OperatorInfo[];
}

// ─── Drag data type for palette items ───

export const PALETTE_DRAG_TYPE = 'application/mapping-palette-item';

export interface PaletteDragData {
    kind: 'function' | 'operator';
    functionInfo?: FunctionInfo;
    operatorInfo?: OperatorInfo;
}

// ─── Component ───

export function FunctionPalette({ functions, operators }: FunctionPaletteProps) {
    const [collapsed, setCollapsed] = React.useState(true);
    const [searchQuery, setSearchQuery] = React.useState('');
    const [expandedCategories, setExpandedCategories] = React.useState<Set<string>>(new Set());

    const query = searchQuery.toLowerCase().trim();

    // Filter functions
    const filteredFunctions = React.useMemo(() => {
        if (!query) return functions;
        return functions.filter(
            fn => fn.name.toLowerCase().includes(query) ||
                fn.category.toLowerCase().includes(query) ||
                fn.description.toLowerCase().includes(query)
        );
    }, [functions, query]);

    // Filter operators
    const filteredOperators = React.useMemo(() => {
        if (!query) return operators;
        return operators.filter(
            op => op.symbol.toLowerCase().includes(query) ||
                op.name.toLowerCase().includes(query) ||
                op.description.toLowerCase().includes(query)
        );
    }, [operators, query]);

    // Group functions by category
    const functionsByCategory = React.useMemo(() => {
        const map = new Map<string, FunctionInfo[]>();
        for (const fn of filteredFunctions) {
            const cat = fn.category || 'Other';
            if (!map.has(cat)) map.set(cat, []);
            map.get(cat)!.push(fn);
        }
        return map;
    }, [filteredFunctions]);

    const toggleCategory = (cat: string) => {
        setExpandedCategories(prev => {
            const next = new Set(prev);
            if (next.has(cat)) {
                next.delete(cat);
            } else {
                next.add(cat);
            }
            return next;
        });
    };

    // Auto-expand when searching
    React.useEffect(() => {
        if (query) {
            setExpandedCategories(new Set(functionsByCategory.keys()));
        }
    }, [query, functionsByCategory]);

    const onDragStart = (e: React.DragEvent, data: PaletteDragData) => {
        e.dataTransfer.setData(PALETTE_DRAG_TYPE, JSON.stringify(data));
        e.dataTransfer.effectAllowed = 'move';
    };

    if (collapsed) {
        return (
            <div
                className="mapping-palette mapping-palette-collapsed"
                onClick={() => setCollapsed(false)}
                title="Show function palette"
            >
                <span className="codicon codicon-chevron-right" style={{ fontSize: '12px' }} />
                <span className="mapping-palette-collapsed-label">Functions</span>
            </div>
        );
    }

    return (
        <div className="mapping-palette">
            <div className="mapping-palette-header">
                <span
                    className="codicon codicon-chevron-left mapping-palette-collapse-btn"
                    title="Collapse function palette"
                    onClick={() => setCollapsed(true)}
                />
                <span className="codicon codicon-symbol-method" style={{ fontSize: '12px' }} />
                <span className="mapping-palette-title">Functions</span>
            </div>
            <div className="mapping-palette-search">
                <input
                    type="text"
                    className="mapping-palette-search-input"
                    placeholder="Search functions..."
                    value={searchQuery}
                    onChange={e => setSearchQuery(e.target.value)}
                />
                {searchQuery && (
                    <button
                        className="mapping-palette-search-clear"
                        onClick={() => setSearchQuery('')}
                    >
                        <span className="codicon codicon-close" />
                    </button>
                )}
            </div>
            <div className="mapping-palette-content">
                {/* Operators section */}
                {filteredOperators.length > 0 && (
                    <div className="mapping-palette-section">
                        <div
                            className="mapping-palette-category"
                            onClick={() => toggleCategory('__operators__')}
                        >
                            <span className={`codicon codicon-chevron-${expandedCategories.has('__operators__') ? 'down' : 'right'}`} />
                            <span>Operators</span>
                            <span className="mapping-palette-count">{filteredOperators.length}</span>
                        </div>
                        {expandedCategories.has('__operators__') && (
                            <div className="mapping-palette-items">
                                {filteredOperators.map(op => (
                                    <div
                                        key={`op-${op.symbol}`}
                                        className="mapping-palette-item"
                                        draggable
                                        onDragStart={e => onDragStart(e, {
                                            kind: 'operator',
                                            operatorInfo: op,
                                        })}
                                        title={`${op.syntax}\n${op.description}`}
                                    >
                                        <span className="mapping-palette-item-symbol">{op.symbol}</span>
                                        <span className="mapping-palette-item-name">{op.name}</span>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                )}

                {/* Function categories */}
                {Array.from(functionsByCategory.entries()).map(([category, fns]) => (
                    <div key={category} className="mapping-palette-section">
                        <div
                            className="mapping-palette-category"
                            onClick={() => toggleCategory(category)}
                        >
                            <span className={`codicon codicon-chevron-${expandedCategories.has(category) ? 'down' : 'right'}`} />
                            <span>{category}</span>
                            <span className="mapping-palette-count">{fns.length}</span>
                        </div>
                        {expandedCategories.has(category) && (
                            <div className="mapping-palette-items">
                                {fns.map(fn => (
                                    <div
                                        key={fn.name}
                                        className="mapping-palette-item"
                                        draggable
                                        onDragStart={e => onDragStart(e, {
                                            kind: 'function',
                                            functionInfo: fn,
                                        })}
                                        title={`${fn.signature}\n${fn.description}`}
                                    >
                                        <span className="codicon codicon-symbol-method mapping-palette-item-icon" />
                                        <span className="mapping-palette-item-name">{fn.name}</span>
                                        <span className="mapping-palette-item-return">{fn.returnType}</span>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                ))}

                {filteredFunctions.length === 0 && filteredOperators.length === 0 && (
                    <div className="mapping-palette-empty">
                        {query ? 'No matching functions' : 'Loading...'}
                    </div>
                )}
            </div>
        </div>
    );
}
