/**
 * Output Schema Node
 *
 * Custom React Flow node that renders an output schema as an expandable
 * field tree. Each leaf field has a target handle (dot) on the left edge.
 */

import * as React from 'react';
import type { NodeProps } from '@xyflow/react';
import type { OutputSchemaNodeData } from '../mapping-types';
import { useMappingStore } from '../mapping-store';
import { SchemaFieldRow } from './input-schema-node';

export function OutputSchemaNode({ id, data }: NodeProps) {
    const nodeData = data as unknown as OutputSchemaNodeData;
    const { expandedFields, toggleFieldExpanded } = useMappingStore();

    return (
        <div className="mapping-node mapping-node-output">
            <div className="mapping-node-header mapping-node-header-output">
                <span className="codicon codicon-output mapping-node-icon" />
                <span className="mapping-node-title">Output</span>
                <span className="mapping-node-badge">{nodeData.format}</span>
            </div>
            <div className="mapping-node-fields">
                {nodeData.fields.map(field => (
                    <SchemaFieldRow
                        key={field.id}
                        field={field}
                        nodeId={id}
                        side="target"
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
