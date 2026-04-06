/**
 * Literal Node
 *
 * Small editable node representing a constant value (string, number, boolean, null).
 * Has a single output port on the right.
 */

import * as React from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { LiteralNodeData } from '../mapping-types';
import { useMappingStore } from '../mapping-store';

export function LiteralNode({ id, data }: NodeProps) {
    const nodeData = data as unknown as LiteralNodeData;
    const updateNodeData = useMappingStore(s => s.updateNodeData);

    const handleValueChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        updateNodeData<LiteralNodeData>(id, { value: e.target.value });
    };

    return (
        <div className="mapping-node mapping-node-literal">
            <div className="mapping-literal-content">
                <span className="mapping-literal-type">{nodeData.valueType}</span>
                <input
                    className="mapping-literal-input"
                    type="text"
                    value={nodeData.value}
                    onChange={handleValueChange}
                    placeholder="value"
                />
            </div>

            <Handle
                type="source"
                position={Position.Right}
                id={`lit.${id}.out`}
                className="mapping-handle"
            />
        </div>
    );
}
