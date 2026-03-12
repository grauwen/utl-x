/**
 * Mapping Canvas Widget
 *
 * Main React component for the graphical mapping editor.
 * Renders a React Flow canvas with custom nodes for input/output schemas,
 * functions, operators, and other transformation elements.
 * Includes a function palette sidebar for drag-and-drop node creation.
 */

import * as React from 'react';
import {
    ReactFlow,
    ReactFlowProvider,
    Background,
    Controls,
    MiniMap,
    BackgroundVariant,
    type NodeTypes,
    type EdgeTypes,
    useReactFlow,
} from '@xyflow/react';

// React Flow CSS must be imported
import '@xyflow/react/dist/style.css';

import { useMappingStore, generateNodeId } from './mapping-store';
import type { FunctionNodeData, OperatorNodeData, MappingNode } from './mapping-types';
import { NODE_TYPES } from './mapping-types';
import type { FunctionInfo, OperatorInfo } from '../../common/protocol';

// Custom nodes
import { InputSchemaNode } from './nodes/input-schema-node';
import { OutputSchemaNode } from './nodes/output-schema-node';
import { FunctionNode } from './nodes/function-node';
import { OperatorNode } from './nodes/operator-node';
import { LiteralNode } from './nodes/literal-node';
import { ConditionalNode } from './nodes/conditional-node';
import { CodeBlockNode } from './nodes/code-block-node';

// Custom edges
import { MappingEdge as MappingEdgeComponent } from './edges/mapping-edge';

// Function palette
import { FunctionPalette, PALETTE_DRAG_TYPE, type PaletteDragData } from './function-palette';

// Code generation
import { generateUtlxFromGraph } from './code-generator';

// ─── Node/Edge type registries ───

const nodeTypes: NodeTypes = {
    inputSchema: InputSchemaNode,
    outputSchema: OutputSchemaNode,
    function: FunctionNode,
    operator: OperatorNode,
    literal: LiteralNode,
    conditional: ConditionalNode,
    codeBlock: CodeBlockNode,
};

const edgeTypes: EdgeTypes = {
    mapping: MappingEdgeComponent,
};

// ─── Canvas Toolbar ───

interface CanvasToolbarProps {
    showCodePreview: boolean;
    onToggleCodePreview: () => void;
    isFullScreen: boolean;
    onToggleFullScreen: () => void;
}

function CanvasToolbar({ showCodePreview, onToggleCodePreview, isFullScreen, onToggleFullScreen }: CanvasToolbarProps) {
    const { fitView } = useReactFlow();
    const autoLayout = useMappingStore(s => s.autoLayout);
    const addNode = useMappingStore(s => s.addNode);

    const handleAutoLayout = React.useCallback(() => {
        autoLayout();
        setTimeout(() => fitView({ padding: 0.15, duration: 300 }), 50);
    }, [autoLayout, fitView]);

    const handleFitView = React.useCallback(() => {
        fitView({ padding: 0.15, duration: 300 });
    }, [fitView]);

    const handleAddLiteral = React.useCallback(() => {
        const nodeId = generateNodeId('literal');
        const newNode: MappingNode = {
            id: nodeId,
            type: NODE_TYPES.literal,
            position: { x: 300, y: 200 },
            data: {
                kind: 'literal',
                value: '',
                valueType: 'string',
            },
        };
        addNode(newNode);
    }, [addNode]);

    return (
        <div className="mapping-canvas-toolbar">
            <button
                className="mapping-toolbar-btn"
                onClick={handleAutoLayout}
                title="Auto-layout nodes (dagre)"
            >
                <span className="codicon codicon-layout" style={{ fontSize: '11px' }} />
                {' '}Auto-layout
            </button>
            <button
                className="mapping-toolbar-btn"
                onClick={handleFitView}
                title="Fit all nodes in view"
            >
                <span className="codicon codicon-screen-full" style={{ fontSize: '11px' }} />
                {' '}Fit View
            </button>
            <button
                className={`mapping-toolbar-btn ${isFullScreen ? 'active' : ''}`}
                onClick={onToggleFullScreen}
                title={isFullScreen ? 'Show Input/Output panels' : 'Hide Input/Output panels'}
            >
                <span className={`codicon ${isFullScreen ? 'codicon-screen-normal' : 'codicon-screen-full'}`} style={{ fontSize: '11px' }} />
                {isFullScreen ? ' Exit Full Screen' : ' Full Screen'}
            </button>
            <div style={{ flex: 1 }} />
            <button
                className="mapping-toolbar-btn"
                onClick={handleAddLiteral}
                title="Add a literal constant value node"
            >
                <span className="codicon codicon-symbol-constant" style={{ fontSize: '11px' }} />
                {' '}Add Literal
            </button>
            <button
                className={`mapping-toolbar-btn ${showCodePreview ? 'active' : ''}`}
                onClick={onToggleCodePreview}
                title="Toggle UTLX code preview"
            >
                <span className="codicon codicon-code" style={{ fontSize: '11px' }} />
                {' '}Code
            </button>
        </div>
    );
}

// ─── Inner Canvas (needs ReactFlowProvider above it) ───

interface MappingCanvasInnerProps {
    functions: FunctionInfo[];
    operators: OperatorInfo[];
    onApplyCode?: (code: string) => void;
    isFullScreen: boolean;
    onToggleFullScreen: () => void;
}

function MappingCanvasInner({ functions, operators, onApplyCode, isFullScreen, onToggleFullScreen }: MappingCanvasInnerProps) {
    const {
        nodes,
        edges,
        onNodesChange,
        onEdgesChange,
        onConnect,
        setViewport,
        addNode,
    } = useMappingStore();

    const [showCodePreview, setShowCodePreview] = React.useState(false);

    // Generate UTLX code from current graph (live preview)
    const generatedCode = React.useMemo(() => {
        if (!showCodePreview) return '';
        return generateUtlxFromGraph(nodes, edges);
    }, [showCodePreview, nodes, edges]);

    const { screenToFlowPosition } = useReactFlow();

    // ─── Drag-and-drop from palette ───

    const onDragOver = React.useCallback((event: React.DragEvent) => {
        event.preventDefault();
        event.dataTransfer.dropEffect = 'move';
    }, []);

    const onDrop = React.useCallback((event: React.DragEvent) => {
        event.preventDefault();

        const dataStr = event.dataTransfer.getData(PALETTE_DRAG_TYPE);
        if (!dataStr) return;

        let dragData: PaletteDragData;
        try {
            dragData = JSON.parse(dataStr);
        } catch {
            return;
        }

        // Convert screen position to flow position
        const position = screenToFlowPosition({
            x: event.clientX,
            y: event.clientY,
        });

        if (dragData.kind === 'function' && dragData.functionInfo) {
            const fn = dragData.functionInfo;
            const nodeId = generateNodeId('fn');
            const data: FunctionNodeData = {
                kind: 'function',
                functionName: fn.name,
                category: fn.category,
                parameters: fn.parameters.map((p, i) => ({
                    name: p.name,
                    type: p.type,
                    handleId: `${nodeId}-param-${i}`,
                    optional: p.optional,
                })),
                returnType: fn.returnType,
                signature: fn.signature,
            };
            const newNode: MappingNode = {
                id: nodeId,
                type: NODE_TYPES.function,
                position,
                data,
            };
            addNode(newNode);
        } else if (dragData.kind === 'operator' && dragData.operatorInfo) {
            const op = dragData.operatorInfo;
            const nodeId = generateNodeId('op');
            const arity = op.unary ? 1 : (op.symbol === '? :' ? 3 : 2);
            const data: OperatorNodeData = {
                kind: 'operator',
                symbol: op.symbol,
                name: op.name,
                arity,
            };
            const newNode: MappingNode = {
                id: nodeId,
                type: NODE_TYPES.operator,
                position,
                data,
            };
            addNode(newNode);
        }
    }, [screenToFlowPosition, addNode]);

    return (
        <div className="mapping-canvas-container">
            <CanvasToolbar
                showCodePreview={showCodePreview}
                onToggleCodePreview={() => setShowCodePreview(v => !v)}
                isFullScreen={isFullScreen}
                onToggleFullScreen={onToggleFullScreen}
            />
            <div className="mapping-canvas-body">
                <FunctionPalette functions={functions} operators={operators} />
                <div className="mapping-canvas-flow">
                    <ReactFlow
                        nodes={nodes}
                        edges={edges}
                        onNodesChange={onNodesChange}
                        onEdgesChange={onEdgesChange}
                        onConnect={onConnect}
                        onMoveEnd={(_event, viewport) => setViewport(viewport)}
                        onDragOver={onDragOver}
                        onDrop={onDrop}
                        nodeTypes={nodeTypes}
                        edgeTypes={edgeTypes}
                        defaultEdgeOptions={{
                            type: 'mapping',
                            animated: false,
                        }}
                        fitView
                        fitViewOptions={{ padding: 0.15 }}
                        snapToGrid
                        snapGrid={[10, 10]}
                        minZoom={0.2}
                        maxZoom={2}
                        deleteKeyCode={["Backspace", "Delete"]}
                        multiSelectionKeyCode="Shift"
                        proOptions={{ hideAttribution: true }}
                    >
                        <Background
                            variant={BackgroundVariant.Dots}
                            gap={16}
                            size={1}
                            color="var(--theia-panel-border, #44475a)"
                        />
                        <Controls
                            showZoom
                            showFitView={false}
                            showInteractive={false}
                            position="bottom-right"
                        />
                        <MiniMap
                            position="bottom-left"
                            pannable
                            zoomable
                            style={{
                                backgroundColor: 'var(--theia-editor-background, #282a36)',
                            }}
                            maskColor="rgba(40, 42, 54, 0.7)"
                        />
                    </ReactFlow>
                </div>
            </div>
            {/* Code preview panel */}
            {showCodePreview && (
                <div className="mapping-code-preview">
                    <div className="mapping-code-preview-header">
                        <span className="codicon codicon-code" style={{ fontSize: '11px' }} />
                        <span>Generated UTLX</span>
                        <div style={{ flex: 1 }} />
                        {onApplyCode && generatedCode && (
                            <button
                                className="mapping-toolbar-btn"
                                onClick={() => onApplyCode(generatedCode)}
                                title="Apply generated code to the UTLX editor"
                                style={{ padding: '1px 8px', fontSize: '11px' }}
                            >
                                <span className="codicon codicon-arrow-right" style={{ fontSize: '10px' }} />
                                {' '}Apply to Editor
                            </button>
                        )}
                    </div>
                    <pre className="mapping-code-preview-content">
                        {generatedCode || '// Draw connections between input and output fields to generate UTLX code'}
                    </pre>
                </div>
            )}
        </div>
    );
}

// ─── Public component (wraps with ReactFlowProvider) ───

export interface MappingCanvasWidgetProps {
    functions: FunctionInfo[];
    operators: OperatorInfo[];
    onApplyCode?: (code: string) => void;
    isFullScreen: boolean;
    onToggleFullScreen: () => void;
}

export function MappingCanvasWidget({ functions, operators, onApplyCode, isFullScreen, onToggleFullScreen }: MappingCanvasWidgetProps) {
    return (
        <ReactFlowProvider>
            <MappingCanvasInner
                functions={functions}
                operators={operators}
                onApplyCode={onApplyCode}
                isFullScreen={isFullScreen}
                onToggleFullScreen={onToggleFullScreen}
            />
        </ReactFlowProvider>
    );
}
