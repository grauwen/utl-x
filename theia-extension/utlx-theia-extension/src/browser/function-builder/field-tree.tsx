/**
 * Field Tree Component
 *
 * Displays UDM fields in a collapsible tree structure.
 * Allows users to browse available input fields and insert them into the editor.
 */

import * as React from 'react';
import { UdmInputTree, UdmField, getTypeDisplayName, getTypeIcon } from './udm-parser';

/**
 * Format a value for use in UTLX expressions
 * Adds quotes around strings, leaves numbers/booleans/null unquoted
 */
function formatValueForUTLX(value: string): string {
    const trimmed = value.trim();

    // Check if it's a number (integer or decimal)
    if (/^-?\d+(\.\d+)?$/.test(trimmed)) {
        return trimmed;
    }

    // Check if it's a boolean
    if (trimmed === 'true' || trimmed === 'false') {
        return trimmed;
    }

    // Check if it's null
    if (trimmed === 'null') {
        return trimmed;
    }

    // It's a string - add double quotes and escape any internal double quotes
    // Also escape backslashes
    const escaped = trimmed.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
    return `"${escaped}"`;
}

/**
 * Extract sample values for a field from UDM language string
 */
function extractSampleValues(udmLanguage: string, fieldPath: string, isInputArray: boolean): string[] {
    try {
        console.log('[extractSampleValues] Starting extraction:', { fieldPath, isInputArray, udmLength: udmLanguage.length });

        const values: string[] = [];
        const pathParts = fieldPath.split('.');

        // Handle array inputs: [{...}, {...}] format
        if (isInputArray) {
            // Find array content between [ and ]
            const startIdx = udmLanguage.indexOf('[');
            const endIdx = udmLanguage.lastIndexOf(']');

            if (startIdx === -1 || endIdx === -1) {
                console.log('[extractSampleValues] No array brackets found');
                return [];
            }

            const arrayContent = udmLanguage.substring(startIdx + 1, endIdx);
            console.log('[extractSampleValues] Array content length:', arrayContent.length);

            // Extract objects from array using brace matching
            const objects = extractObjectsFromArray(arrayContent);
            console.log('[extractSampleValues] Found', objects.length, 'objects in array');

            for (const obj of objects) {
                const objValues = extractAllValuesFromObject(obj, pathParts);
                console.log('[extractSampleValues] Extracted', objValues.length, 'values from object:', objValues);
                values.push(...objValues);
            }
        } else {
            // Single object: {field: value}
            const startIdx = udmLanguage.indexOf('{');
            const endIdx = udmLanguage.lastIndexOf('}');

            if (startIdx === -1 || endIdx === -1) {
                console.log('[extractSampleValues] No object braces found');
                return [];
            }

            const objectContent = udmLanguage.substring(startIdx + 1, endIdx);
            const objValues = extractAllValuesFromObject(objectContent, pathParts);
            values.push(...objValues);
        }

        // Return unique values (max 10)
        const uniqueValues = Array.from(new Set(values)).slice(0, 10);
        console.log('[extractSampleValues] Returning', uniqueValues.length, 'unique values:', uniqueValues);
        return uniqueValues;
    } catch (error) {
        console.error('[FieldTree] Error extracting sample values:', error);
        return [];
    }
}

/**
 * Extract individual objects from array content using brace matching
 */
function extractObjectsFromArray(arrayContent: string): string[] {
    const objects: string[] = [];
    let braceCount = 0;
    let currentObject = '';
    let inObject = false;

    for (let i = 0; i < arrayContent.length; i++) {
        const char = arrayContent[i];

        if (char === '{') {
            if (!inObject) {
                inObject = true;
                currentObject = '';
            } else {
                currentObject += char;
            }
            braceCount++;
        } else if (char === '}') {
            braceCount--;
            if (braceCount === 0 && inObject) {
                // Complete object found
                objects.push(currentObject);
                inObject = false;
                currentObject = '';
            } else {
                currentObject += char;
            }
        } else if (inObject) {
            currentObject += char;
        }
    }

    return objects;
}

/**
 * Extract ALL values for a field path from object content
 * Returns all values from the field, handling nested arrays
 */
function extractAllValuesFromObject(objectContent: string, pathParts: string[]): string[] {
    return extractValuesRecursive(objectContent, pathParts, 0);
}

/**
 * Recursive helper to extract values, handling nested arrays
 * Returns array of all found values
 */
function extractValuesRecursive(currentContent: string, pathParts: string[], partIndex: number): string[] {
    if (partIndex >= pathParts.length) {
        return [];
    }

    const part = pathParts[partIndex];
    const isLastPart = partIndex === pathParts.length - 1;

    // Match field: value pattern
    const pattern = new RegExp(`${part}:\\s*(@\\w+\\([^)]*\\)|@\\w+|\\{[^}]*\\}|\\[[^\\]]*\\]|"(?:[^"\\\\]|\\\\.)*"|'(?:[^'\\\\]|\\\\.)*'|[^,}\\n]+)`);
    const match = currentContent.match(pattern);

    if (!match) return [];

    const value = match[1].trim();

    // If this is the last part of the path, return the value
    if (isLastPart) {
        // Remove quotes from leaf values
        const cleanValue = value.replace(/^["']|["']$/g, '');
        return [cleanValue];
    }

    // If nested object, continue traversing
    if (value.startsWith('{')) {
        const nestedContent = value.substring(1, value.length - 1);
        return extractValuesRecursive(nestedContent, pathParts, partIndex + 1);
    } else if (value.startsWith('[')) {
        // Nested array - extract from ALL array elements
        const arrayElements = extractObjectsFromArray(value.substring(1, value.lastIndexOf(']')));
        const allValues: string[] = [];

        for (const elementContent of arrayElements) {
            const elementValues = extractValuesRecursive(elementContent, pathParts, partIndex + 1);
            allValues.push(...elementValues);
        }

        return allValues;
    }

    return [];
}

/**
 * Props for FieldTree component
 */
export interface FieldTreeProps {
    fieldTrees: UdmInputTree[];
    onInsertField: (inputName: string, fieldPath: string) => void;
    onInsertValue?: (value: string) => void; // Optional callback to insert raw values into editor
    udmMap: Map<string, string>; // inputName -> UDM language string
}

/**
 * FieldTree Component
 *
 * Displays all inputs with their fields in a tree structure with horizontal split
 */
export const FieldTree: React.FC<FieldTreeProps> = ({ fieldTrees, onInsertField, onInsertValue, udmMap }) => {
    const [expandedInputs, setExpandedInputs] = React.useState<Set<string>>(new Set());
    const [selectedField, setSelectedField] = React.useState<{ inputName: string; fieldPath: string } | null>(null);

    // Extract sample values for selected field
    const sampleValues = React.useMemo(() => {
        if (!selectedField) return [];

        const udmLanguage = udmMap.get(selectedField.inputName);
        if (!udmLanguage) return [];

        const tree = fieldTrees.find(t => t.inputName === selectedField.inputName);
        if (!tree) return [];

        return extractSampleValues(udmLanguage, selectedField.fieldPath, tree.isArray);
    }, [selectedField, udmMap, fieldTrees]);

    // Split pane state (default to 66% for field tree, 34% for sample data)
    const [splitPosition, setSplitPosition] = React.useState(66);
    const [isDragging, setIsDragging] = React.useState(false);
    const containerRef = React.useRef<HTMLDivElement>(null);

    const toggleInput = (inputName: string) => {
        const newExpanded = new Set(expandedInputs);
        if (newExpanded.has(inputName)) {
            newExpanded.delete(inputName);
        } else {
            newExpanded.add(inputName);
        }
        setExpandedInputs(newExpanded);
    };

    // Auto-expand if only one input
    React.useEffect(() => {
        if (fieldTrees.length === 1 && expandedInputs.size === 0) {
            setExpandedInputs(new Set([fieldTrees[0].inputName]));
        }
    }, [fieldTrees]);

    // Handle split divider dragging
    const handleMouseDown = () => {
        setIsDragging(true);
    };

    React.useEffect(() => {
        const handleMouseMove = (e: MouseEvent) => {
            if (!isDragging || !containerRef.current) return;

            const container = containerRef.current;
            const rect = container.getBoundingClientRect();
            const offsetY = e.clientY - rect.top;
            const percentage = (offsetY / rect.height) * 100;

            // Constrain between 30% and 85%
            const clampedPercentage = Math.max(30, Math.min(85, percentage));
            setSplitPosition(clampedPercentage);
        };

        const handleMouseUp = () => {
            setIsDragging(false);
        };

        if (isDragging) {
            document.addEventListener('mousemove', handleMouseMove);
            document.addEventListener('mouseup', handleMouseUp);
        }

        return () => {
            document.removeEventListener('mousemove', handleMouseMove);
            document.removeEventListener('mouseup', handleMouseUp);
        };
    }, [isDragging]);

    if (fieldTrees.length === 0) {
        return (
            <div className='empty-state'>
                No inputs defined in UTLX headers.
                <br />
                <small>Add inputs to see available fields</small>
            </div>
        );
    }

    return (
        <div className='field-tree-container' ref={containerRef}>
            {/* Field Tree Pane */}
            <div className='field-tree-pane' style={{ flex: `${splitPosition} 1 0%` }}>
                <div className='field-tree'>
                    {fieldTrees.map(tree => (
                        <InputNode
                            key={tree.inputName}
                            tree={tree}
                            isExpanded={expandedInputs.has(tree.inputName)}
                            onToggle={() => toggleInput(tree.inputName)}
                            onInsertField={onInsertField}
                            onFieldSelect={setSelectedField}
                        />
                    ))}
                </div>
            </div>

            {/* Split Divider */}
            <div
                className='split-divider'
                onMouseDown={handleMouseDown}
            >
                <div className='split-handle'></div>
            </div>

            {/* Sample Data Pane */}
            <div className='sample-data-pane' style={{ flex: `${100 - splitPosition} 1 0%` }}>
                <div className='sample-data-content' style={{ padding: '16px' }}>
                    {selectedField ? (
                        <div>
                            <h4 style={{ margin: '0 0 12px 0', fontSize: '13px', fontWeight: 600, color: 'var(--theia-foreground)' }}>
                                Available Data
                            </h4>
                            <div style={{
                                fontSize: '12px',
                                fontFamily: 'var(--monaco-monospace-font)',
                                marginBottom: '12px',
                                color: 'var(--theia-descriptionForeground)'
                            }}>
                                ${selectedField.inputName}.{selectedField.fieldPath}
                            </div>
                            {sampleValues.length > 0 ? (
                                <div>
                                    <div style={{
                                        fontSize: '11px',
                                        color: 'var(--theia-descriptionForeground)',
                                        marginBottom: '8px'
                                    }}>
                                        {sampleValues.length} unique {sampleValues.length === 1 ? 'value' : 'values'}:
                                    </div>
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                                        {sampleValues.map((value, index) => {
                                            const quotedValue = formatValueForUTLX(value);
                                            return (
                                            <div
                                                key={index}
                                                style={{
                                                    padding: '6px 8px',
                                                    background: 'var(--theia-editor-background)',
                                                    border: '1px solid var(--theia-panel-border)',
                                                    borderRadius: '3px',
                                                    fontSize: '12px',
                                                    fontFamily: 'var(--monaco-monospace-font)',
                                                    color: 'var(--theia-foreground)',
                                                    wordBreak: 'break-all',
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    gap: '8px'
                                                }}
                                            >
                                                <span style={{ flex: 1 }}>{value}</span>
                                                <div style={{ display: 'flex', gap: '4px', flexShrink: 0 }}>
                                                    {onInsertValue && (
                                                        <button
                                                            className='insert-btn'
                                                            title={`Insert ${quotedValue} into editor`}
                                                            onClick={(e) => {
                                                                e.stopPropagation();
                                                                onInsertValue(quotedValue);
                                                                console.log('[FieldTree] Inserted quoted value:', quotedValue);
                                                            }}
                                                            style={{
                                                                background: 'transparent',
                                                                border: 'none',
                                                                cursor: 'pointer',
                                                                padding: '2px 4px',
                                                                display: 'flex',
                                                                alignItems: 'center',
                                                                color: 'var(--theia-foreground)',
                                                                opacity: 0.7
                                                            }}
                                                            onMouseEnter={(e) => { e.currentTarget.style.opacity = '1'; }}
                                                            onMouseLeave={(e) => { e.currentTarget.style.opacity = '0.7'; }}
                                                        >
                                                            <span className='codicon codicon-insert' style={{ fontSize: '12px' }}></span>
                                                        </button>
                                                    )}
                                                    <button
                                                        className='copy-btn'
                                                        title={`Copy ${quotedValue} to clipboard`}
                                                        onClick={(e) => {
                                                            e.stopPropagation();
                                                            navigator.clipboard.writeText(quotedValue);
                                                            console.log('[FieldTree] Copied quoted value to clipboard:', quotedValue);
                                                        }}
                                                        style={{
                                                            background: 'transparent',
                                                            border: 'none',
                                                            cursor: 'pointer',
                                                            padding: '2px 4px',
                                                            display: 'flex',
                                                            alignItems: 'center',
                                                            color: 'var(--theia-foreground)',
                                                            opacity: 0.7
                                                        }}
                                                        onMouseEnter={(e) => { e.currentTarget.style.opacity = '1'; }}
                                                        onMouseLeave={(e) => { e.currentTarget.style.opacity = '0.7'; }}
                                                    >
                                                        <span className='codicon codicon-copy' style={{ fontSize: '12px' }}></span>
                                                    </button>
                                                </div>
                                            </div>
                                            );
                                        })}
                                    </div>
                                </div>
                            ) : (
                                <div style={{
                                    fontSize: '12px',
                                    color: 'var(--theia-descriptionForeground)',
                                    fontStyle: 'italic'
                                }}>
                                    No data available for this field
                                </div>
                            )}
                        </div>
                    ) : (
                        <div className='empty-details' style={{
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            justifyContent: 'center',
                            height: '100%',
                            gap: '12px'
                        }}>
                            <span className='codicon codicon-info' style={{ fontSize: '32px', opacity: 0.5 }}></span>
                            <span style={{ fontSize: '13px', color: 'var(--theia-descriptionForeground)' }}>
                                Click on a field to see available data
                            </span>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

/**
 * Props for InputNode component
 */
interface InputNodeProps {
    tree: UdmInputTree;
    isExpanded: boolean;
    onToggle: () => void;
    onInsertField: (inputName: string, fieldPath: string) => void;
    onFieldSelect: (field: { inputName: string; fieldPath: string } | null) => void;
}

/**
 * InputNode Component
 *
 * Represents a single input (root level)
 */
const InputNode: React.FC<InputNodeProps> = ({ tree, isExpanded, onToggle, onInsertField, onFieldSelect }) => {
    const typeLabel = tree.isArray ? 'Array' : 'Object';
    const icon = tree.isArray ? 'codicon-symbol-array' : 'codicon-symbol-variable';

    return (
        <div className='input-node'>
            {/* Array inputs: Show both $input (single) and $input[] (array) */}
            {tree.isArray ? (
                <>
                    {/* Single row access: $input */}
                    <div className='input-header'>
                        <span className='codicon codicon-chevron-right' style={{ visibility: 'hidden' }}></span>
                        <span className={`codicon ${icon}`}></span>
                        <span className='input-name'>${tree.inputName}</span>
                        <span className='input-format-badge'>{tree.format}</span>
                        <span className='input-array-badge'>Object</span>
                        <button
                            className='insert-btn'
                            title={`Insert $${tree.inputName} (first row as object)`}
                            onClick={(e) => {
                                e.stopPropagation();
                                onInsertField(tree.inputName, '');
                            }}
                        >
                            <span className='codicon codicon-insert'></span>
                        </button>
                        <button
                            className='copy-btn'
                            title={`Copy $${tree.inputName} to clipboard`}
                            onClick={(e) => {
                                e.stopPropagation();
                                navigator.clipboard.writeText(`$${tree.inputName}`);
                                console.log('[FieldTree] Copied input reference to clipboard:', `$${tree.inputName}`);
                            }}
                        >
                            <span className='codicon codicon-copy'></span>
                        </button>
                    </div>

                    {/* Array access: $input[] */}
                    <div className='input-header' onClick={onToggle} style={{ cursor: 'pointer' }}>
                        <span className={`codicon codicon-chevron-${isExpanded ? 'down' : 'right'}`}></span>
                        <span className={`codicon ${icon}`}></span>
                        <span className='input-name'>${tree.inputName}[]</span>
                        <span className='input-format-badge'>{tree.format}</span>
                        <span className='input-array-badge'>Array</span>
                        <button
                            className='insert-btn'
                            title={`Insert $${tree.inputName}[0] (array access)`}
                            onClick={(e) => {
                                e.stopPropagation();
                                onInsertField(tree.inputName, '[0]');
                            }}
                        >
                            <span className='codicon codicon-insert'></span>
                        </button>
                        <button
                            className='copy-btn'
                            title={`Copy $${tree.inputName}[0] to clipboard`}
                            onClick={(e) => {
                                e.stopPropagation();
                                navigator.clipboard.writeText(`$${tree.inputName}[0]`);
                                console.log('[FieldTree] Copied array access to clipboard:', `$${tree.inputName}[0]`);
                            }}
                        >
                            <span className='codicon codicon-copy'></span>
                        </button>
                    </div>
                </>
            ) : (
                /* Object inputs: Show only $input */
                <div className='input-header' onClick={onToggle}>
                    <span className={`codicon codicon-chevron-${isExpanded ? 'down' : 'right'}`}></span>
                    <span className={`codicon ${icon}`}></span>
                    <span className='input-name'>${tree.inputName}</span>
                    <span className='input-format-badge'>{tree.format}</span>
                    <span className='input-array-badge'>{typeLabel}</span>
                    <button
                        className='insert-btn'
                        title={`Insert $${tree.inputName}`}
                        onClick={(e) => {
                            e.stopPropagation();
                            onInsertField(tree.inputName, '');
                        }}
                    >
                        <span className='codicon codicon-insert'></span>
                    </button>
                    <button
                        className='copy-btn'
                        title={`Copy $${tree.inputName} to clipboard`}
                        onClick={(e) => {
                            e.stopPropagation();
                            navigator.clipboard.writeText(`$${tree.inputName}`);
                            console.log('[FieldTree] Copied input reference to clipboard:', `$${tree.inputName}`);
                        }}
                    >
                        <span className='codicon codicon-copy'></span>
                    </button>
                </div>
            )}

            {isExpanded && tree.fields.length > 0 && (
                <div className='fields'>
                    {tree.fields.map(field => (
                        <FieldNode
                            key={field.name}
                            field={field}
                            path={field.name}
                            inputName={tree.inputName}
                            inputIsArray={tree.isArray}
                            onInsert={onInsertField}
                            onSelect={onFieldSelect}
                            level={1}
                        />
                    ))}
                </div>
            )}

            {isExpanded && tree.fields.length === 0 && (
                <div className='empty-fields'>
                    <small>No fields available (paste data to populate)</small>
                </div>
            )}
        </div>
    );
};

/**
 * Props for FieldNode component
 */
interface FieldNodeProps {
    field: UdmField;
    path: string;
    inputName: string;
    inputIsArray: boolean;
    onInsert: (inputName: string, fieldPath: string) => void;
    onSelect: (field: { inputName: string; fieldPath: string } | null) => void;
    level: number;
}

/**
 * FieldNode Component
 *
 * Represents a single field (can be nested)
 */
const FieldNode: React.FC<FieldNodeProps> = ({
    field,
    path,
    inputName,
    inputIsArray,
    onInsert,
    onSelect,
    level
}) => {
    const [expanded, setExpanded] = React.useState(false);
    const hasChildren = (field.type === 'object' || field.type === 'array') &&
        field.fields &&
        field.fields.length > 0;

    const typeIcon = getTypeIcon(field.type);
    const typeDisplay = getTypeDisplayName(field.type);

    // Build the full field path for insertion
    const buildInsertPath = () => {
        if (inputIsArray) {
            return `[0].${path}`;
        }
        return path;
    };

    return (
        <div className='field-node' style={{ paddingLeft: `${level * 12}px` }}>
            <div className='field-header' onClick={() => onSelect({ inputName, fieldPath: path })}>
                {hasChildren ? (
                    <span
                        className={`codicon codicon-chevron-${expanded ? 'down' : 'right'}`}
                        onClick={(e) => { e.stopPropagation(); setExpanded(!expanded); }}
                        style={{ cursor: 'pointer' }}
                    />
                ) : (
                    <span className='field-spacer' style={{ width: '16px', display: 'inline-block' }}></span>
                )}
                <span className={`codicon ${typeIcon} field-icon`}></span>
                <span className='field-name'>{field.name}</span>
                <span className='field-type'>{typeDisplay}</span>
                <button
                    className='insert-btn'
                    title={`Insert $${inputName}.${path}`}
                    onClick={(e) => {
                        e.stopPropagation();
                        onInsert(inputName, buildInsertPath());
                    }}
                >
                    <span className='codicon codicon-insert'></span>
                </button>
                <button
                    className='copy-btn'
                    title={`Copy $${inputName}.${path} to clipboard`}
                    onClick={(e) => {
                        e.stopPropagation();
                        const separator = buildInsertPath().startsWith('[') ? '' : '.';
                        const fullPath = `$${inputName}${separator}${buildInsertPath()}`;
                        navigator.clipboard.writeText(fullPath);
                        console.log('[FieldTree] Copied field reference to clipboard:', fullPath);
                    }}
                >
                    <span className='codicon codicon-copy'></span>
                </button>
            </div>

            {hasChildren && expanded && (
                <div className='nested-fields'>
                    {field.fields!.map(child => (
                        <FieldNode
                            key={child.name}
                            field={child}
                            path={`${path}.${child.name}`}
                            inputName={inputName}
                            inputIsArray={inputIsArray}
                            onInsert={onInsert}
                            onSelect={onSelect}
                            level={level + 1}
                        />
                    ))}
                </div>
            )}
        </div>
    );
};
