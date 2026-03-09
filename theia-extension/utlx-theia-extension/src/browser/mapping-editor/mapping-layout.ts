/**
 * Mapping Layout
 *
 * Uses dagre to compute automatic left-to-right layout for the mapping graph.
 * Input schema nodes go on the left, function/operator nodes in the middle,
 * output schema nodes on the right.
 */

import dagre from 'dagre';
import type { MappingNode, MappingEdge } from './mapping-types';

/** Default dimensions for node types */
const NODE_DIMENSIONS: Record<string, { width: number; height: number }> = {
    inputSchema: { width: 280, height: 300 },
    outputSchema: { width: 280, height: 300 },
    function: { width: 200, height: 80 },
    operator: { width: 120, height: 60 },
    literal: { width: 160, height: 50 },
    conditional: { width: 180, height: 100 },
    codeBlock: { width: 240, height: 80 },
};

const DEFAULT_DIMENSIONS = { width: 200, height: 100 };

/** Horizontal gap between columns */
const RANK_SEP = 180;

/** Vertical gap between nodes */
const NODE_SEP = 60;

/**
 * Apply dagre auto-layout to nodes and edges.
 * Returns a new array of nodes with updated positions.
 */
export function applyDagreLayout(
    nodes: MappingNode[],
    edges: MappingEdge[]
): MappingNode[] {
    if (nodes.length === 0) return nodes;

    const g = new dagre.graphlib.Graph();
    g.setDefaultEdgeLabel(() => ({}));

    g.setGraph({
        rankdir: 'LR',    // left-to-right
        ranksep: RANK_SEP,
        nodesep: NODE_SEP,
        marginx: 40,
        marginy: 40,
    });

    // Add nodes to dagre graph
    for (const node of nodes) {
        const dims = NODE_DIMENSIONS[node.type ?? ''] ?? DEFAULT_DIMENSIONS;
        g.setNode(node.id, { width: dims.width, height: dims.height });
    }

    // Add edges to dagre graph
    for (const edge of edges) {
        g.setEdge(edge.source, edge.target);
    }

    // Run layout
    dagre.layout(g);

    // Apply computed positions back to nodes
    return nodes.map(node => {
        const dagreNode = g.node(node.id);
        if (!dagreNode) return node;

        const dims = NODE_DIMENSIONS[node.type ?? ''] ?? DEFAULT_DIMENSIONS;

        return {
            ...node,
            position: {
                // dagre returns center position, React Flow uses top-left
                x: dagreNode.x - dims.width / 2,
                y: dagreNode.y - dims.height / 2,
            },
        };
    });
}

/**
 * Compute initial positions for input and output schema nodes
 * without dagre (used when there are no function nodes yet).
 * Places input on the left, output on the right.
 */
export function computeInitialPositions(
    inputNodes: MappingNode[],
    outputNodes: MappingNode[],
    canvasWidth: number
): MappingNode[] {
    const MARGIN = 40;
    const Y_START = 40;

    const result: MappingNode[] = [];

    // Input nodes stacked on the left
    let yOffset = Y_START;
    for (const node of inputNodes) {
        result.push({
            ...node,
            position: { x: MARGIN, y: yOffset },
        });
        const dims = NODE_DIMENSIONS[node.type ?? ''] ?? DEFAULT_DIMENSIONS;
        yOffset += dims.height + NODE_SEP;
    }

    // Output nodes stacked on the right
    yOffset = Y_START;
    const rightX = Math.max(canvasWidth - 280 - MARGIN, 600);
    for (const node of outputNodes) {
        result.push({
            ...node,
            position: { x: rightX, y: yOffset },
        });
        const dims = NODE_DIMENSIONS[node.type ?? ''] ?? DEFAULT_DIMENSIONS;
        yOffset += dims.height + NODE_SEP;
    }

    return result;
}
