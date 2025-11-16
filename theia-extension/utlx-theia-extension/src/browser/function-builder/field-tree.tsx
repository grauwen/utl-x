/**
 * Field Tree Component
 *
 * Displays UDM fields in a collapsible tree structure.
 * Allows users to browse available input fields and insert them into the editor.
 */

import * as React from 'react';
import { UdmInputTree, UdmField, getTypeDisplayName, getTypeIcon } from './udm-parser-new';
import { UDMLanguageParser } from '../udm/udm-language-parser';
import { navigate } from '../udm/udm-navigator';
import { UDM, isScalar, isObject, isArray, UDMObjectHelper } from '../udm/udm-core';

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
 * NEW IMPLEMENTATION: Uses UDM parser instead of regex
 */
function extractSampleValues(udmLanguage: string, fieldPath: string, isInputArray: boolean): string[] {
    try {
        console.log('[extractSampleValues] Starting extraction:', { fieldPath, isInputArray, udmLength: udmLanguage.length });

        // Parse UDM using proper parser
        const udm = UDMLanguageParser.parse(udmLanguage);
        console.log('[extractSampleValues] Parsed UDM type:', udm.type);

        // Navigate to the field
        const targetNode = navigate(udm, fieldPath);

        if (!targetNode) {
            console.warn('[extractSampleValues] Field not found:', fieldPath);
            return [];
        }

        // Handle string result from navigate (edge case - shouldn't happen for normal paths)
        if (typeof targetNode === 'string') {
            console.log('[extractSampleValues] Navigate returned string:', targetNode);
            return [targetNode];
        }

        console.log('[extractSampleValues] Found target node, type:', targetNode.type);

        const values: string[] = [];

        // Extract values based on node type
        if (isScalar(targetNode)) {
            // Single scalar value
            const scalarValue = String((targetNode as any).value);
            console.log('[extractSampleValues] Scalar value:', scalarValue);
            values.push(scalarValue);
        } else if (isArray(targetNode)) {
            // Array of values - extract from each element
            const arrayNode = targetNode as UDM & { type: 'array' };
            console.log('[extractSampleValues] Array with', arrayNode.elements.length, 'elements');

            for (const element of arrayNode.elements.slice(0, 10)) {
                if (isScalar(element)) {
                    values.push(String(element.value));
                } else if (isObject(element)) {
                    // For objects, show a summary
                    const objNode = element as UDM & { type: 'object' };
                    const childNames = UDMObjectHelper.keys(objNode);
                    values.push(`{${childNames.length} fields}`);
                } else {
                    values.push(`<${element.type}>`);
                }
            }
        } else if (isObject(targetNode)) {
            // Object - check if it's an XML element with text content
            const objNode = targetNode as UDM & { type: 'object' };
            const childNames = UDMObjectHelper.keys(objNode);
            const attrKeys = UDMObjectHelper.attributeKeys(objNode);
            console.log('[extractSampleValues] Object with properties:', childNames);

            // Filter out XML namespace attributes which are typically not meaningful
            const meaningfulAttrs = attrKeys.filter(key =>
                key !== 'xmlns' && key !== 'xsi' && !key.startsWith('xmlns:')
            );

            // If this element has ONLY _text property, extract the text value directly
            if (childNames.length === 1 && childNames[0] === '_text') {
                const textValue = UDMObjectHelper.get(objNode, '_text');
                if (textValue && isScalar(textValue)) {
                    console.log('[extractSampleValues] XML text element, extracting text value:', textValue.value);
                    values.push(String(textValue.value));
                }
            } else {
                // Regular object with child elements - show its properties
                for (const childName of childNames.slice(0, 10)) {
                    const childValue = UDMObjectHelper.get(objNode, childName);
                    if (childValue && isScalar(childValue)) {
                        values.push(`${childName}: ${childValue.value}`);
                    } else if (childValue) {
                        values.push(`${childName}: <${childValue.type}>`);
                    }
                }
            }
        } else {
            // DateTime, Binary, Lambda, etc.
            console.log('[extractSampleValues] Other type:', targetNode.type);
            values.push(`<${targetNode.type}>`);
        }

        console.log('[extractSampleValues] Extracted', values.length, 'values:', values);
        return values.slice(0, 10);
    } catch (error) {
        console.error('[extractSampleValues] Error:', error);
        console.error('[extractSampleValues] Stack:', error instanceof Error ? error.stack : 'N/A');
        return [];
    }
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
 * Get format tier (T1 or T2)
 */
function getFormatTier(format: string): string {
    const tier1Formats = ['json', 'xml', 'csv', 'yaml'];
    const tier2Formats = ['xsd', 'jsch', 'avro', 'proto', 'protobuf', 'jsonschema'];

    const normalizedFormat = format.toLowerCase();

    if (tier1Formats.includes(normalizedFormat)) {
        return 'T1';
    } else if (tier2Formats.includes(normalizedFormat)) {
        return 'T2';
    }

    return 'T1'; // Default to T1 for unknown formats
}

/**
 * InputNode Component
 *
 * Represents a single input (root level)
 */
const InputNode: React.FC<InputNodeProps> = ({ tree, isExpanded, onToggle, onInsertField, onFieldSelect }) => {
    const typeLabel = tree.isArray ? 'Array' : 'Object';
    const icon = tree.isArray ? 'codicon-symbol-array' : 'codicon-symbol-variable';
    const tier = getFormatTier(tree.format);

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
                        <span className='input-tier-badge'>{tier}</span>
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
                        <span className='input-tier-badge'>{tier}</span>
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
                    <span className='input-tier-badge'>{tier}</span>
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
