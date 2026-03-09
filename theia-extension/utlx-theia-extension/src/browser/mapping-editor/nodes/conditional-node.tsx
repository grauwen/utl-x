/**
 * Conditional Node
 *
 * Represents if/then/else or ternary logic.
 * Three input ports (condition, true-branch, false-branch), one output port.
 */

import * as React from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { ConditionalNodeData } from '../mapping-types';

export function ConditionalNode({ id }: NodeProps) {
    return (
        <div className="mapping-node mapping-node-conditional">
            <div className="mapping-node-header mapping-node-header-conditional">
                <span className="codicon codicon-git-compare mapping-node-icon" />
                <span className="mapping-node-title">if / else</span>
            </div>

            <div className="mapping-node-ports">
                <div className="mapping-port mapping-port-input">
                    <Handle
                        type="target"
                        position={Position.Left}
                        id={`cond.${id}.condition`}
                        className="mapping-handle"
                        style={{ top: '30%' }}
                    />
                    <span className="mapping-port-name">condition</span>
                </div>
                <div className="mapping-port mapping-port-input">
                    <Handle
                        type="target"
                        position={Position.Left}
                        id={`cond.${id}.then`}
                        className="mapping-handle"
                        style={{ top: '55%' }}
                    />
                    <span className="mapping-port-name">then</span>
                </div>
                <div className="mapping-port mapping-port-input">
                    <Handle
                        type="target"
                        position={Position.Left}
                        id={`cond.${id}.else`}
                        className="mapping-handle"
                        style={{ top: '80%' }}
                    />
                    <span className="mapping-port-name">else</span>
                </div>
            </div>

            <Handle
                type="source"
                position={Position.Right}
                id={`cond.${id}.out`}
                className="mapping-handle"
            />
        </div>
    );
}
