/**
 * Field Tree Component
 *
 * Displays UDM fields in a collapsible tree structure.
 * Allows users to browse available input fields and insert them into the editor.
 */

import * as React from 'react';
import { UdmInputTree, UdmField, getTypeDisplayName, getTypeIcon } from './udm-parser';

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
                const value = extractValueFromObject(obj, pathParts);
                if (value !== null) {
                    console.log('[extractSampleValues] Extracted value:', value);
                    values.push(value);
                }
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
            const value = extractValueFromObject(objectContent, pathParts);
            if (value !== null) {
                values.push(value);
            }
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
 * Extract value for a field path from object content
 */
function extractValueFromObject(objectContent: string, pathParts: string[]): string | null {
    let currentContent = objectContent;

    for (const part of pathParts) {
        // Match field: value pattern
        const pattern = new RegExp(`${part}:\\s*(@\\w+\\([^)]*\\)|@\\w+|\\{[^}]*\\}|\\[[^\\]]*\\]|"(?:[^"\\\\]|\\\\.)*"|'(?:[^'\\\\]|\\\\.)*'|[^,}\\n]+)`);
        const match = currentContent.match(pattern);

        if (!match) return null;

        const value = match[1].trim();

        // If nested object, continue traversing
        if (value.startsWith('{')) {
            currentContent = value.substring(1, value.length - 1);
        } else if (value.startsWith('[')) {
            // Array - extract first element for simplicity
            const arrayMatch = value.match(/\[\s*\{([^}]+)\}/);
            if (arrayMatch) {
                currentContent = arrayMatch[1];
            } else {
                return value; // Simple array like [1,2,3]
            }
        } else {
            // Leaf value
            return value.replace(/^["']|["']$/g, ''); // Remove quotes
        }
    }

    return null;
}

/**
 * Props for FieldTree component
 */
export interface FieldTreeProps {
    fieldTrees: UdmInputTree[];
    onInsertField: (inputName: string, fieldPath: string) => void;
    udmMap: Map<string, string>; // inputName -> UDM language string
}

/**
 * FieldTree Component
 *
 * Displays all inputs with their fields in a tree structure with horizontal split
 */
export const FieldTree: React.FC<FieldTreeProps> = ({ fieldTrees, onInsertField, udmMap }) => {
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
                                        {sampleValues.map((value, index) => (
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
                                                    wordBreak: 'break-all'
                                                }}
                                            >
                                                {value}
                                            </div>
                                        ))}
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
            <div className='input-header' onClick={onToggle}>
                <span className={`codicon codicon-chevron-${isExpanded ? 'down' : 'right'}`}></span>
                <span className={`codicon ${icon}`}></span>
                <span className='input-name'>${tree.inputName}</span>
                <span className='input-format-badge'>{tree.format}</span>
                <span className='input-array-badge'>{typeLabel}</span>
                <button
                    className='insert-btn'
                    title={`Insert $${tree.inputName}${tree.isArray ? '[0]' : ''}`}
                    onClick={(e) => {
                        e.stopPropagation();
                        onInsertField(tree.inputName, tree.isArray ? '[0]' : '');
                    }}
                >
                    <span className='codicon codicon-insert'></span>
                </button>
            </div>

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
                    onClick={(e) => { e.stopPropagation(); onInsert(inputName, buildInsertPath()); }}
                >
                    <span className='codicon codicon-insert'></span>
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
