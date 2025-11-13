/**
 * Field Tree Component
 *
 * Displays UDM fields in a collapsible tree structure.
 * Allows users to browse available input fields and insert them into the editor.
 */

import * as React from 'react';
import { UdmInputTree, UdmField, getTypeDisplayName, getTypeIcon } from './udm-parser';

/**
 * Props for FieldTree component
 */
export interface FieldTreeProps {
    fieldTrees: UdmInputTree[];
    onInsertField: (inputName: string, fieldPath: string) => void;
}

/**
 * FieldTree Component
 *
 * Displays all inputs with their fields in a tree structure
 */
export const FieldTree: React.FC<FieldTreeProps> = ({ fieldTrees, onInsertField }) => {
    const [expandedInputs, setExpandedInputs] = React.useState<Set<string>>(new Set());

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
        <div className='field-tree'>
            {fieldTrees.map(tree => (
                <InputNode
                    key={tree.inputName}
                    tree={tree}
                    isExpanded={expandedInputs.has(tree.inputName)}
                    onToggle={() => toggleInput(tree.inputName)}
                    onInsertField={onInsertField}
                />
            ))}
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
}

/**
 * InputNode Component
 *
 * Represents a single input (root level)
 */
const InputNode: React.FC<InputNodeProps> = ({ tree, isExpanded, onToggle, onInsertField }) => {
    const typeLabel = tree.isArray ? 'Array' : 'Object';
    const icon = tree.isArray ? 'codicon-symbol-array' : 'codicon-symbol-variable';

    return (
        <div className='input-node'>
            <div className='input-header' onClick={onToggle}>
                <span className={`codicon codicon-chevron-${isExpanded ? 'down' : 'right'}`}></span>
                <span className={`codicon ${icon}`}></span>
                <span className='input-name'>${tree.inputName}</span>
                <span className='input-type-badge'>{typeLabel}</span>
                {tree.isArray && (
                    <span className='input-hint'>[0]</span>
                )}
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
            <div className='field-header'>
                {hasChildren ? (
                    <span
                        className={`codicon codicon-chevron-${expanded ? 'down' : 'right'}`}
                        onClick={() => setExpanded(!expanded)}
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
                    onClick={() => onInsert(inputName, buildInsertPath())}
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
                            level={level + 1}
                        />
                    ))}
                </div>
            )}
        </div>
    );
};
