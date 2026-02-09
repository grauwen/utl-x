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
 * Extract a single value from a UDM node
 */
function extractValueFromNode(node: UDM): string | undefined {
    if (isScalar(node)) {
        return String(node.value);
    } else if (isObject(node)) {
        // Check if it's an XML text element
        const objNode = node as UDM & { type: 'object' };
        const propertyKeys = UDMObjectHelper.keys(objNode);

        if (propertyKeys.length === 1 && propertyKeys[0] === '_text') {
            const textValue = UDMObjectHelper.get(objNode, '_text');
            if (textValue && isScalar(textValue)) {
                return String(textValue.value);
            }
        }
    }
    return undefined;
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

        const values: string[] = [];

        // Check if path contains [] notation (e.g., "items[].sku")
        if (fieldPath.includes('[]')) {
            console.log('[extractSampleValues] Path contains [] notation, extracting from array elements');

            // Split path at [] to get array path and field path within elements
            const parts = fieldPath.split('[].');
            const arrayPath = parts[0]; // e.g., "items"
            const fieldInElement = parts.length > 1 ? parts[1] : ''; // e.g., "sku"

            console.log('[extractSampleValues] Array path:', arrayPath, 'Field in element:', fieldInElement);

            // Navigate to the array field
            const arrayNode = navigate(udm, arrayPath);

            if (arrayNode && typeof arrayNode !== 'string' && isArray(arrayNode)) {
                const arrayUdm = arrayNode as UDM & { type: 'array' };
                console.log('[extractSampleValues] Found array with', arrayUdm.elements.length, 'elements');

                // Process elements (up to 1000 for performance)
                const elementsToProcess = arrayUdm.elements.slice(0, 1000);

                for (const element of elementsToProcess) {
                    if (fieldInElement) {
                        // Navigate to the field within this array element
                        const fieldNode = navigate(element, fieldInElement);
                        if (fieldNode && typeof fieldNode !== 'string') {
                            const elementValue = extractValueFromNode(fieldNode);
                            if (elementValue) {
                                values.push(elementValue);
                            }
                        } else if (typeof fieldNode === 'string') {
                            values.push(fieldNode);
                        }
                    } else {
                        // No field path, extract the element itself
                        const elementValue = extractValueFromNode(element);
                        if (elementValue) {
                            values.push(elementValue);
                        }
                    }
                }

                console.log('[extractSampleValues] Extracted', values.length, 'values from array elements');
                const uniqueValues = Array.from(new Set(values));
                console.log('[extractSampleValues] Found', uniqueValues.length, 'unique values');
                return uniqueValues.slice(0, 100);
            } else {
                console.warn('[extractSampleValues] Array path did not resolve to an array:', arrayPath);
                return [];
            }
        }

        // Special handling for array inputs: extract field from ALL array elements
        if (isInputArray && isArray(udm)) {
            console.log('[extractSampleValues] Input is array, extracting field from all elements');
            const arrayNode = udm as UDM & { type: 'array' };

            // Process all elements (or up to 1000 for performance)
            const elementsToProcess = arrayNode.elements.slice(0, 1000);
            console.log('[extractSampleValues] Processing', elementsToProcess.length, 'array elements');

            for (let i = 0; i < elementsToProcess.length; i++) {
                const element = elementsToProcess[i];
                console.log(`[extractSampleValues] Element ${i}: type=${element.type}`);

                // Navigate to the field within this element
                const fieldNode = navigate(element, fieldPath);
                console.log(`[extractSampleValues] Element ${i}: fieldNode type=${fieldNode ? (typeof fieldNode === 'string' ? 'string' : fieldNode.type) : 'null'}`);

                if (fieldNode && typeof fieldNode !== 'string') {
                    // Extract value from this element's field
                    const elementValue = extractValueFromNode(fieldNode);
                    console.log(`[extractSampleValues] Element ${i}: extracted value="${elementValue}"`);
                    if (elementValue) {
                        values.push(elementValue);
                    }
                } else if (typeof fieldNode === 'string') {
                    console.log(`[extractSampleValues] Element ${i}: string value="${fieldNode}"`);
                    values.push(fieldNode);
                }
            }

            console.log('[extractSampleValues] Extracted', values.length, 'values from', elementsToProcess.length, 'array elements');
            console.log('[extractSampleValues] All values:', values);
            // Return unique values (up to 100 unique values for display)
            const uniqueValues = Array.from(new Set(values));
            console.log('[extractSampleValues] Found', uniqueValues.length, 'unique values:', uniqueValues);
            return uniqueValues.slice(0, 100);
        }

        // Non-array input: navigate normally
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

            for (const element of arrayNode.elements.slice(0, 100)) {
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
                for (const childName of childNames.slice(0, 50)) {
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
        return values.slice(0, 100);
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
    isDesignTime?: boolean; // Design-Time mode flag for schema-aware rendering
    schemaFieldTreeMap?: Map<string, UdmField[]>; // inputName -> schema field tree (for type info)
}

/**
 * FieldTree Component
 *
 * Displays all inputs with their fields in a tree structure with horizontal split
 */
export const FieldTree: React.FC<FieldTreeProps> = ({ fieldTrees, onInsertField, onInsertValue, udmMap, isDesignTime = false, schemaFieldTreeMap }) => {
    const [expandedInputs, setExpandedInputs] = React.useState<Set<string>>(new Set());
    const [selectedField, setSelectedField] = React.useState<{ inputName: string; fieldPath: string } | null>(null);

    // Helper to find schema info for a field path
    const findSchemaFieldInfo = React.useCallback((inputName: string, fieldPath: string): UdmField | null => {
        if (!schemaFieldTreeMap) return null;
        const schemaFields = schemaFieldTreeMap.get(inputName);
        if (!schemaFields) return null;

        const parts = fieldPath.split('.');
        let current: UdmField | null = null;
        let currentFields = schemaFields;

        for (const part of parts) {
            // Handle array notation
            const cleanPart = part.replace('[]', '');
            current = currentFields.find(f => f.name === cleanPart) || null;
            if (!current) break;
            currentFields = current.fields || [];
        }
        return current;
    }, [schemaFieldTreeMap]);

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
                            isDesignTime={isDesignTime}
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
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                                    {/* Schema Info Section (when both instance and schema exist) */}
                                    {(() => {
                                        const schemaField = findSchemaFieldInfo(selectedField.inputName, selectedField.fieldPath) as any;
                                        if (schemaField && isDesignTime) {
                                            return (
                                                <div style={{
                                                    padding: '10px',
                                                    background: 'rgba(98, 114, 164, 0.15)',
                                                    borderRadius: '4px',
                                                    borderLeft: '3px solid #8be9fd'
                                                }}>
                                                    <div style={{
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        gap: '6px',
                                                        marginBottom: '8px',
                                                        fontSize: '11px',
                                                        fontWeight: 600,
                                                        color: '#8be9fd'
                                                    }}>
                                                        <span className='codicon codicon-symbol-structure'></span>
                                                        Schema Info
                                                    </div>
                                                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', fontSize: '11px' }}>
                                                        <span>
                                                            <span style={{ color: 'var(--theia-descriptionForeground)' }}>Type: </span>
                                                            <span style={{ color: '#8be9fd', fontFamily: 'var(--monaco-monospace-font)' }}>
                                                                {schemaField.schemaType || schemaField.type}
                                                            </span>
                                                        </span>
                                                        <span>
                                                            <span style={{ color: 'var(--theia-descriptionForeground)' }}>Required: </span>
                                                            <span style={{ color: schemaField.isRequired ? '#ff79c6' : '#50fa7b' }}>
                                                                {schemaField.isRequired ? 'Yes' : 'No'}
                                                            </span>
                                                        </span>
                                                        {schemaField.constraints && (
                                                            <span>
                                                                <span style={{ color: 'var(--theia-descriptionForeground)' }}>Constraints: </span>
                                                                <span style={{ color: '#f1fa8c' }}>{schemaField.constraints}</span>
                                                            </span>
                                                        )}
                                                    </div>
                                                </div>
                                            );
                                        }
                                        return null;
                                    })()}

                                    {/* Sample Values Section */}
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
                                </div>
                            ) : (
                                <div style={{
                                    fontSize: '12px',
                                    color: 'var(--theia-descriptionForeground)'
                                }}>
                                    {(() => {
                                        // Check if this field is from a schema source
                                        const selectedTree = fieldTrees.find(t => t.inputName === selectedField.inputName);
                                        if (selectedTree?.isSchemaSource) {
                                            // Find the selected field in the tree to get schema details
                                            const findField = (fields: UdmField[], path: string): UdmField | null => {
                                                const parts = path.split('.');
                                                let current: UdmField | null = null;
                                                let currentFields = fields;

                                                for (const part of parts) {
                                                    // Handle array notation
                                                    const cleanPart = part.replace('[]', '');
                                                    current = currentFields.find(f => f.name === cleanPart) || null;
                                                    if (!current) break;
                                                    currentFields = current.fields || [];
                                                }
                                                return current;
                                            };

                                            const schemaField = findField(selectedTree.fields, selectedField.fieldPath) as any;

                                            return (
                                                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                                                    {/* Schema Info Header */}
                                                    <div style={{
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        gap: '8px',
                                                        padding: '6px 8px',
                                                        background: 'rgba(98, 114, 164, 0.2)',
                                                        borderRadius: '4px'
                                                    }}>
                                                        <span className='codicon codicon-symbol-structure' style={{ color: '#8be9fd' }}></span>
                                                        <span style={{ fontWeight: 500 }}>Schema Details</span>
                                                    </div>

                                                    {schemaField ? (
                                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                                            {/* Type */}
                                                            <div style={{ display: 'flex', gap: '8px' }}>
                                                                <span style={{ color: 'var(--theia-descriptionForeground)', minWidth: '80px' }}>Type:</span>
                                                                <span style={{ color: '#8be9fd', fontFamily: 'var(--monaco-monospace-font)' }}>
                                                                    {schemaField.schemaType || schemaField.type || 'unknown'}
                                                                </span>
                                                            </div>

                                                            {/* Required/Optional */}
                                                            <div style={{ display: 'flex', gap: '8px' }}>
                                                                <span style={{ color: 'var(--theia-descriptionForeground)', minWidth: '80px' }}>Required:</span>
                                                                <span style={{ color: schemaField.isRequired ? '#ff79c6' : '#50fa7b' }}>
                                                                    {schemaField.isRequired ? 'Yes' : 'No (optional)'}
                                                                </span>
                                                            </div>

                                                            {/* Constraints */}
                                                            {schemaField.constraints && (
                                                                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                                                                    <span style={{ color: 'var(--theia-descriptionForeground)', minWidth: '80px' }}>Constraints:</span>
                                                                    <span style={{ color: '#f1fa8c', fontFamily: 'var(--monaco-monospace-font)', fontSize: '11px' }}>
                                                                        {schemaField.constraints}
                                                                    </span>
                                                                </div>
                                                            )}

                                                            {/* Description */}
                                                            {schemaField.description && (
                                                                <div style={{ display: 'flex', gap: '8px', flexDirection: 'column' }}>
                                                                    <span style={{ color: 'var(--theia-descriptionForeground)' }}>Description:</span>
                                                                    <span style={{ color: 'var(--theia-foreground)', fontStyle: 'italic', paddingLeft: '8px' }}>
                                                                        {schemaField.description}
                                                                    </span>
                                                                </div>
                                                            )}

                                                            {/* Nested Fields Count */}
                                                            {schemaField.fields && schemaField.fields.length > 0 && (
                                                                <div style={{ display: 'flex', gap: '8px' }}>
                                                                    <span style={{ color: 'var(--theia-descriptionForeground)', minWidth: '80px' }}>Children:</span>
                                                                    <span style={{ color: '#bd93f9' }}>
                                                                        {schemaField.fields.length} nested field{schemaField.fields.length !== 1 ? 's' : ''}
                                                                    </span>
                                                                </div>
                                                            )}
                                                        </div>
                                                    ) : (
                                                        <span style={{ fontStyle: 'italic', opacity: 0.7 }}>
                                                            Field details not available
                                                        </span>
                                                    )}

                                                    {/* Footer hint */}
                                                    <div style={{
                                                        fontSize: '11px',
                                                        opacity: 0.6,
                                                        borderTop: '1px solid var(--theia-panel-border)',
                                                        paddingTop: '8px',
                                                        marginTop: '4px'
                                                    }}>
                                                        Load instance data to see sample values
                                                    </div>
                                                </div>
                                            );
                                        }
                                        return <span style={{ fontStyle: 'italic' }}>No data available for this field</span>;
                                    })()}
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
                                {isDesignTime
                                    ? 'Click on a field to see type info or available data'
                                    : 'Click on a field to see available data'}
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
    isDesignTime?: boolean;
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
const InputNode: React.FC<InputNodeProps> = ({ tree, isExpanded, onToggle, onInsertField, onFieldSelect, isDesignTime = false }) => {
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
                            isSchemaSource={tree.isSchemaSource}
                            isDesignTime={isDesignTime}
                        />
                    ))}
                </div>
            )}

            {isExpanded && tree.fields.length === 0 && (
                <div className='empty-fields'>
                    <small>
                        {isDesignTime
                            ? 'No fields available (load instance data or schema to populate)'
                            : 'No fields available (paste data to populate)'}
                    </small>
                </div>
            )}

            {/* Schema-derived indicator */}
            {isExpanded && tree.isSchemaSource && tree.fields.length > 0 && (
                <div className='schema-source-indicator' style={{
                    marginLeft: '20px',
                    padding: '2px 6px',
                    fontSize: '10px',
                    color: '#8be9fd',
                    opacity: 0.7,
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px'
                }}>
                    <span className='codicon codicon-symbol-structure' style={{ fontSize: '10px' }}></span>
                    Schema-derived (no instance data)
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
    isSchemaSource?: boolean;  // True if this field came from a schema (Design-Time mode)
    isDesignTime?: boolean;    // Design-Time mode flag
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
    level,
    isSchemaSource = false,
    isDesignTime = false
}) => {
    const [expanded, setExpanded] = React.useState(false);
    const hasChildren = (field.type === 'object' || field.type === 'array') &&
        field.fields &&
        field.fields.length > 0;

    const typeIcon = getTypeIcon(field.type);
    const typeDisplay = getTypeDisplayName(field.type);

    // Extract schema-specific info from SchemaFieldInfo (when isSchemaSource)
    const schemaField = field as any; // Cast to access SchemaFieldInfo properties
    const isRequired = schemaField.isRequired === true;
    const schemaType = schemaField.schemaType;
    const constraints = schemaField.constraints;

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
                <span className='field-name'>
                    {field.name}
                    {isSchemaSource && isRequired && (
                        <span className='required-indicator' style={{
                            color: '#ff79c6',
                            marginLeft: '2px',
                            fontWeight: 'bold'
                        }} title='Required field'>*</span>
                    )}
                </span>
                <span className='field-type' title={constraints || undefined}>
                    {isSchemaSource && schemaType ? schemaType : typeDisplay}
                </span>
                {isSchemaSource && constraints && (
                    <span className='field-constraints' style={{
                        fontSize: '10px',
                        color: '#8be9fd',
                        marginLeft: '4px',
                        opacity: 0.8
                    }} title={constraints}>
                        ({constraints.length > 20 ? constraints.substring(0, 20) + '...' : constraints})
                    </span>
                )}
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
                    {field.fields!.map(child => {
                        // If this field is an array, children should be accessed with []
                        const childPath = field.type === 'array'
                            ? `${path}[].${child.name}`
                            : `${path}.${child.name}`;

                        return (
                            <FieldNode
                                key={child.name}
                                field={child}
                                path={childPath}
                                inputName={inputName}
                                inputIsArray={inputIsArray}
                                onInsert={onInsert}
                                onSelect={onSelect}
                                level={level + 1}
                                isSchemaSource={isSchemaSource}
                                isDesignTime={isDesignTime}
                            />
                        );
                    })}
                </div>
            )}
        </div>
    );
};
