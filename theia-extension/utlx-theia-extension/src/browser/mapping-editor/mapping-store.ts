/**
 * Mapping Store (Zustand)
 *
 * Central state management for the graphical mapping editor.
 * Stores all nodes, edges, layout state, and view configuration.
 * This is in-memory only — no persistence across sessions.
 */

import { create } from 'zustand';
import {
    applyNodeChanges,
    applyEdgeChanges,
    type OnNodesChange,
    type OnEdgesChange,
    type OnConnect,
    type Connection,
    type Viewport,
} from '@xyflow/react';
import type {
    MappingNode,
    MappingEdge,
    MappingEdgeData,
    MappingNodeData,
    SchemaField,
    InputSchemaNodeData,
    OutputSchemaNodeData,
    ViewMode,
} from './mapping-types';
import { NODE_TYPES } from './mapping-types';
import { applyDagreLayout, computeInitialPositions } from './mapping-layout';

// ─── Store Interface ───

export interface MappingStore {
    // ── State ──
    nodes: MappingNode[];
    edges: MappingEdge[];
    viewMode: ViewMode;
    expandedFields: Set<string>;
    viewport: Viewport;
    selectedNodeIds: string[];
    selectedEdgeIds: string[];
    paletteSearchQuery: string;

    // ── Node operations ──
    addNode: (node: MappingNode) => void;
    removeNode: (nodeId: string) => void;
    updateNodeData: <T extends MappingNodeData>(nodeId: string, data: Partial<T>) => void;

    // ── Edge operations ──
    addEdge: (edge: MappingEdge) => void;
    removeEdge: (edgeId: string) => void;

    // ── Schema management ──
    setInputSchema: (inputName: string, format: string, fields: SchemaField[], isArray: boolean) => void;
    setOutputSchema: (format: string, fields: SchemaField[]) => void;
    renameInputNode: (oldName: string, newName: string) => void;

    // ── Layout ──
    autoLayout: () => void;

    // ── View state ──
    setViewMode: (mode: ViewMode) => void;
    toggleFieldExpanded: (fieldId: string) => void;
    setViewport: (viewport: Viewport) => void;
    setPaletteSearchQuery: (query: string) => void;

    // ── Selection ──
    setSelectedNodes: (ids: string[]) => void;
    setSelectedEdges: (ids: string[]) => void;

    // ── React Flow callbacks ──
    onNodesChange: OnNodesChange<MappingNode>;
    onEdgesChange: OnEdgesChange<MappingEdge>;
    onConnect: OnConnect;

    // ── Bulk operations ──
    setNodes: (nodes: MappingNode[]) => void;
    setEdges: (edges: MappingEdge[]) => void;
    clearAll: () => void;
}

// ─── Edge ID generation ───

let edgeIdCounter = 0;

function generateEdgeId(source: string, target: string): string {
    edgeIdCounter++;
    return `e-${source}-${target}-${edgeIdCounter}`;
}

// ─── Node ID generation ───

let nodeIdCounter = 0;

export function generateNodeId(prefix: string): string {
    nodeIdCounter++;
    return `${prefix}-${nodeIdCounter}`;
}

// ─── Store ───

export const useMappingStore = create<MappingStore>((set, get) => ({
    // ── Initial state ──
    nodes: [],
    edges: [],
    viewMode: 'classic',
    expandedFields: new Set<string>(),
    viewport: { x: 0, y: 0, zoom: 1 },
    selectedNodeIds: [],
    selectedEdgeIds: [],
    paletteSearchQuery: '',

    // ── Node operations ──

    addNode: (node) =>
        set(state => ({
            nodes: [...state.nodes, node],
        })),

    removeNode: (nodeId) =>
        set(state => ({
            nodes: state.nodes.filter(n => n.id !== nodeId),
            // Also remove connected edges
            edges: state.edges.filter(
                e => e.source !== nodeId && e.target !== nodeId
            ),
        })),

    updateNodeData: (nodeId, data) =>
        set(state => ({
            nodes: state.nodes.map(n =>
                n.id === nodeId
                    ? { ...n, data: { ...n.data, ...data } as MappingNodeData }
                    : n
            ),
        })),

    // ── Edge operations ──

    addEdge: (edge) =>
        set(state => ({
            edges: [...state.edges, edge],
        })),

    removeEdge: (edgeId) =>
        set(state => ({
            edges: state.edges.filter(e => e.id !== edgeId),
        })),

    // ── Schema management ──

    setInputSchema: (inputName, format, fields, isArray) => {
        const nodeId = `input-${inputName}`;
        const data: InputSchemaNodeData = {
            kind: 'inputSchema',
            inputName,
            format,
            fields,
            isArray,
        };

        set(state => {
            const existingIndex = state.nodes.findIndex(n => n.id === nodeId);
            if (existingIndex >= 0) {
                // Update existing node, preserve position
                const updated = [...state.nodes];
                updated[existingIndex] = {
                    ...updated[existingIndex],
                    data,
                };
                return { nodes: updated };
            }

            // Add new node
            const newNode: MappingNode = {
                id: nodeId,
                type: NODE_TYPES.inputSchema,
                position: { x: 40, y: 40 },
                data,
            };
            return { nodes: [...state.nodes, newNode] };
        });
    },

    setOutputSchema: (format, fields) => {
        const nodeId = 'output';
        const data: OutputSchemaNodeData = {
            kind: 'outputSchema',
            format,
            fields,
        };

        set(state => {
            const existingIndex = state.nodes.findIndex(n => n.id === nodeId);
            if (existingIndex >= 0) {
                const updated = [...state.nodes];
                updated[existingIndex] = {
                    ...updated[existingIndex],
                    data,
                };
                return { nodes: updated };
            }

            const newNode: MappingNode = {
                id: nodeId,
                type: NODE_TYPES.outputSchema,
                position: { x: 600, y: 40 },
                data,
            };
            return { nodes: [...state.nodes, newNode] };
        });
    },

    renameInputNode: (oldName, newName) => {
        const oldNodeId = `input-${oldName}`;
        const newNodeId = `input-${newName}`;
        const oldHandlePrefix = `input-${oldName}.`;
        const newHandlePrefix = `input-${newName}.`;

        set(state => {
            // Update the node: change ID and data.inputName
            const nodes = state.nodes.map(n => {
                if (n.id !== oldNodeId) return n;
                const data = n.data as InputSchemaNodeData;
                return {
                    ...n,
                    id: newNodeId,
                    data: { ...data, inputName: newName },
                };
            });

            // Update all edges that reference the old node
            const edges = state.edges.map(e => {
                let updated = e;
                if (e.source === oldNodeId) {
                    updated = {
                        ...updated,
                        source: newNodeId,
                        sourceHandle: e.sourceHandle?.startsWith(oldHandlePrefix)
                            ? newHandlePrefix + e.sourceHandle.slice(oldHandlePrefix.length)
                            : e.sourceHandle === oldNodeId
                                ? newNodeId
                                : e.sourceHandle,
                    };
                }
                if (e.target === oldNodeId) {
                    updated = {
                        ...updated,
                        target: newNodeId,
                        targetHandle: e.targetHandle?.startsWith(oldHandlePrefix)
                            ? newHandlePrefix + e.targetHandle.slice(oldHandlePrefix.length)
                            : e.targetHandle === oldNodeId
                                ? newNodeId
                                : e.targetHandle,
                    };
                }
                return updated;
            });

            return { nodes, edges };
        });
    },

    // ── Layout ──

    autoLayout: () => {
        const { nodes, edges } = get();
        if (nodes.length === 0) return;

        // If only schema nodes (no function/operator nodes), use simple positioning
        const hasMiddleNodes = nodes.some(
            n => n.type !== NODE_TYPES.inputSchema && n.type !== NODE_TYPES.outputSchema
        );

        if (!hasMiddleNodes) {
            const inputNodes = nodes.filter(n => n.type === NODE_TYPES.inputSchema);
            const outputNodes = nodes.filter(n => n.type === NODE_TYPES.outputSchema);
            const positioned = computeInitialPositions(inputNodes, outputNodes, 1000);
            set({ nodes: positioned });
        } else {
            const layouted = applyDagreLayout(nodes, edges);
            set({ nodes: layouted });
        }
    },

    // ── View state ──

    setViewMode: (mode) =>
        set({ viewMode: mode }),

    toggleFieldExpanded: (fieldId) =>
        set(state => {
            const next = new Set(state.expandedFields);
            if (next.has(fieldId)) {
                next.delete(fieldId);
            } else {
                next.add(fieldId);
            }
            return { expandedFields: next };
        }),

    setViewport: (viewport) =>
        set({ viewport }),

    setPaletteSearchQuery: (query) =>
        set({ paletteSearchQuery: query }),

    // ── Selection ──

    setSelectedNodes: (ids) =>
        set({ selectedNodeIds: ids }),

    setSelectedEdges: (ids) =>
        set({ selectedEdgeIds: ids }),

    // ── React Flow callbacks ──

    onNodesChange: (changes) =>
        set(state => ({
            nodes: applyNodeChanges(changes, state.nodes),
        })),

    onEdgesChange: (changes) =>
        set(state => ({
            edges: applyEdgeChanges(changes, state.edges),
        })),

    onConnect: (connection: Connection) => {
        if (!connection.source || !connection.target) return;

        const newEdge: MappingEdge = {
            id: generateEdgeId(connection.source, connection.target),
            source: connection.source,
            target: connection.target,
            sourceHandle: connection.sourceHandle ?? undefined,
            targetHandle: connection.targetHandle ?? undefined,
            type: 'mapping',
            data: {} as MappingEdgeData,
        };

        set(state => ({
            edges: [...state.edges, newEdge],
        }));
    },

    // ── Bulk operations ──

    setNodes: (nodes) =>
        set({ nodes }),

    setEdges: (edges) =>
        set({ edges }),

    clearAll: () =>
        set({
            nodes: [],
            edges: [],
            expandedFields: new Set<string>(),
            selectedNodeIds: [],
            selectedEdgeIds: [],
        }),
}));
