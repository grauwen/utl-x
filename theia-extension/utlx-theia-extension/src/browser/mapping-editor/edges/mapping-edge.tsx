/**
 * Mapping Edge
 *
 * Custom React Flow edge with type-colored styling.
 * Shows the data type flowing through the connection.
 */

import * as React from 'react';
import { BaseEdge, getSmoothStepPath, type EdgeProps } from '@xyflow/react';
import type { MappingEdgeData } from '../mapping-types';

export function MappingEdge(props: EdgeProps) {
    const {
        sourceX,
        sourceY,
        targetX,
        targetY,
        sourcePosition,
        targetPosition,
        style,
        markerEnd,
        data,
        selected,
    } = props;

    const edgeData = data as MappingEdgeData | undefined;

    const [edgePath, labelX, labelY] = getSmoothStepPath({
        sourceX,
        sourceY,
        targetX,
        targetY,
        sourcePosition,
        targetPosition,
        borderRadius: 8,
    });

    return (
        <>
            <BaseEdge
                path={edgePath}
                markerEnd={markerEnd}
                interactionWidth={20}
                style={{
                    ...style,
                    stroke: selected ? 'var(--mapping-edge-active, #ff79c6)' : 'var(--mapping-edge-color, #6272a4)',
                    strokeWidth: selected ? 2.5 : 1.5,
                }}
            />
            {edgeData?.label && (
                <text
                    x={labelX}
                    y={labelY - 8}
                    className="mapping-edge-label"
                    textAnchor="middle"
                    dominantBaseline="central"
                >
                    {edgeData.label}
                </text>
            )}
        </>
    );
}
