/**
 * Code Block Node
 *
 * Opaque node for UTLX expressions that cannot be visually decomposed.
 * Shows the raw expression as monospace text with a single output port.
 * Read-only — user must edit in code mode.
 */

import * as React from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { CodeBlockNodeData } from '../mapping-types';

export function CodeBlockNode({ id, data }: NodeProps) {
    const nodeData = data as unknown as CodeBlockNodeData;

    return (
        <div className="mapping-node mapping-node-codeblock">
            <div className="mapping-node-header mapping-node-header-codeblock">
                <span className="codicon codicon-code mapping-node-icon" />
                <span className="mapping-node-title">{nodeData.outputFieldName}</span>
                <span className="mapping-codeblock-badge">code</span>
            </div>
            <div className="mapping-codeblock-expression">
                {nodeData.expression}
            </div>

            <Handle
                type="source"
                position={Position.Right}
                id={`cb.${id}.out`}
                className="mapping-handle"
            />
        </div>
    );
}
