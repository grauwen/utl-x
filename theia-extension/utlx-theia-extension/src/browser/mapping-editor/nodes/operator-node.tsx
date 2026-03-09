/**
 * Operator Node
 *
 * Compact custom React Flow node representing an operator (e.g. +, ==, ? :).
 * Shows the operator symbol prominently with input/output ports.
 */

import * as React from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { OperatorNodeData } from '../mapping-types';

export function OperatorNode({ id, data }: NodeProps) {
    const nodeData = data as unknown as OperatorNodeData;

    const inputHandles = [];
    if (nodeData.arity >= 1) {
        inputHandles.push({ id: `op.${id}.left`, label: nodeData.arity === 1 ? 'in' : 'left' });
    }
    if (nodeData.arity >= 2) {
        inputHandles.push({ id: `op.${id}.right`, label: 'right' });
    }
    if (nodeData.arity >= 3) {
        // Ternary: condition is first, true/false are left/right
        inputHandles[0].label = 'cond';
        inputHandles[1].label = 'then';
        inputHandles.push({ id: `op.${id}.else`, label: 'else' });
    }

    return (
        <div className="mapping-node mapping-node-operator">
            {/* Input handles */}
            {inputHandles.map((handle, idx) => (
                <Handle
                    key={handle.id}
                    type="target"
                    position={Position.Left}
                    id={handle.id}
                    className="mapping-handle"
                    style={{ top: `${50 / (inputHandles.length + 1) * (idx + 1)}%` }}
                />
            ))}

            <div className="mapping-operator-symbol">{nodeData.symbol}</div>
            <div className="mapping-operator-name">{nodeData.name}</div>

            {/* Output handle */}
            <Handle
                type="source"
                position={Position.Right}
                id={`op.${id}.out`}
                className="mapping-handle"
            />
        </div>
    );
}
