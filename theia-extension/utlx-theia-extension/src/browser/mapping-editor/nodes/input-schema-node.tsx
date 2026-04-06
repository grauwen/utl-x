/**
 * Input Schema Node
 *
 * Custom React Flow node that renders an input schema as an expandable
 * field tree. Each leaf field has a source handle (dot) on the right edge.
 */

import * as React from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { InputSchemaNodeData, SchemaField } from '../mapping-types';
import { useMappingStore } from '../mapping-store';

export function InputSchemaNode({ id, data }: NodeProps) {
    const nodeData = data as unknown as InputSchemaNodeData;
    const { expandedFields, toggleFieldExpanded } = useMappingStore();

    return (
        <div className="mapping-node mapping-node-input">
            <div className="mapping-node-header mapping-node-header-input">
                <span className="codicon codicon-symbol-variable mapping-node-icon" />
                <span className="mapping-node-title">${nodeData.inputName}</span>
                <span className="mapping-node-badge">{nodeData.format}</span>
                {nodeData.isArray && (
                    <span className="mapping-node-badge mapping-node-badge-array">Array</span>
                )}
            </div>
            <div className="mapping-node-fields">
                {nodeData.fields.map(field => (
                    <SchemaFieldRow
                        key={field.id}
                        field={field}
                        nodeId={id}
                        side="source"
                        expandedFields={expandedFields}
                        onToggle={toggleFieldExpanded}
                        level={0}
                    />
                ))}
                {nodeData.fields.length === 0 && (
                    <div className="mapping-node-empty">No fields available</div>
                )}
            </div>
        </div>
    );
}

/** Renders a single field row with optional children and a connection handle */
function SchemaFieldRow({
    field,
    nodeId,
    side,
    expandedFields,
    onToggle,
    level,
}: {
    field: SchemaField;
    nodeId: string;
    side: 'source' | 'target';
    expandedFields: Set<string>;
    onToggle: (id: string) => void;
    level: number;
}) {
    const hasChildren = field.children && field.children.length > 0;
    const isExpanded = expandedFields.has(field.id);
    const isLeaf = !hasChildren;

    const handlePosition = side === 'source' ? Position.Right : Position.Left;

    return (
        <>
            <div
                className="mapping-field-row"
                style={{ paddingLeft: `${8 + level * 14}px` }}
                onClick={() => hasChildren && onToggle(field.id)}
            >
                {hasChildren ? (
                    <span className={`codicon codicon-chevron-${isExpanded ? 'down' : 'right'} mapping-field-toggle`} />
                ) : (
                    <span className="mapping-field-spacer" />
                )}
                <span className={`codicon ${getTypeIcon(field.type)} mapping-field-icon`} />
                <span className="mapping-field-name">{field.name}</span>
                <span className="mapping-field-type">{field.type}</span>

                {/* Connection handle for leaf fields and arrays */}
                {(isLeaf || field.type === 'array') && (
                    <Handle
                        type={side}
                        position={handlePosition}
                        id={field.id}
                        className="mapping-handle"
                    />
                )}
            </div>

            {hasChildren && isExpanded && field.children!.map(child => (
                <SchemaFieldRow
                    key={child.id}
                    field={child}
                    nodeId={nodeId}
                    side={side}
                    expandedFields={expandedFields}
                    onToggle={onToggle}
                    level={level + 1}
                />
            ))}
        </>
    );
}

function getTypeIcon(type: string): string {
    switch (type) {
        case 'string': return 'codicon-symbol-string';
        case 'number':
        case 'integer': return 'codicon-symbol-numeric';
        case 'boolean': return 'codicon-symbol-boolean';
        case 'object': return 'codicon-symbol-class';
        case 'array': return 'codicon-symbol-array';
        case 'datetime':
        case 'date':
        case 'time': return 'codicon-calendar';
        case 'binary': return 'codicon-file-binary';
        default: return 'codicon-symbol-field';
    }
}

// Re-export SchemaFieldRow for use by output-schema-node
export { SchemaFieldRow };
