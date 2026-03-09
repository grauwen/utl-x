/**
 * Function Node
 *
 * Custom React Flow node representing a stdlib function (e.g. toUpperCase, concat).
 * Shows function name, typed input ports (one per parameter), and output port.
 */

import * as React from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { FunctionNodeData } from '../mapping-types';

export function FunctionNode({ id, data }: NodeProps) {
    const nodeData = data as unknown as FunctionNodeData;

    return (
        <div className="mapping-node mapping-node-function">
            <div className="mapping-node-header mapping-node-header-function">
                <span className="codicon codicon-symbol-method mapping-node-icon" />
                <span className="mapping-node-title">{nodeData.functionName}</span>
            </div>
            <div className="mapping-node-ports">
                {/* Input ports */}
                {nodeData.parameters.map((param, idx) => (
                    <div key={param.handleId} className="mapping-port mapping-port-input">
                        <Handle
                            type="target"
                            position={Position.Left}
                            id={param.handleId}
                            className="mapping-handle"
                            style={{ top: `${32 + idx * 24}px` }}
                        />
                        <span className="mapping-port-name">{param.name}</span>
                        <span className="mapping-port-type">{param.type}</span>
                    </div>
                ))}
            </div>

            {/* Output port */}
            <Handle
                type="source"
                position={Position.Right}
                id={`fn.${id}.out`}
                className="mapping-handle"
            />
            <div className="mapping-port mapping-port-output">
                <span className="mapping-port-type mapping-port-return">{nodeData.returnType}</span>
            </div>
        </div>
    );
}
