/**
 * IF21 — Phase 1: read-only "Treeview" mapper for Message Contract mode.
 *
 * Tibco-BW-style layout: all INPUTS as field trees on the left, the OUTPUT CONTRACT as an
 * enfoldable tree on the right. Each output leaf is colored by its IF11 coverage status
 * (✓ direct / ~ derivable / ✗ gap) and shows its source. Containers show a descendant gap count.
 *
 * Phase 1 is READ-ONLY (a "what maps / what's a gap" view). A lightweight recursive renderer is used
 * on purpose — the Function Builder's FieldTree carries insert buttons + a sample-data pane that aren't
 * wanted here. Phase 2 (click an output node → Function Builder, bind an expression) will add interactivity.
 */

import * as React from 'react';
import { SchemaFieldInfo } from '../utils/schema-field-tree-parser';
import { CoverageReport, CoverageEntry, CoverageStatus } from '../utils/coverage';

export interface TreeviewInput {
    name: string;            // e.g. "order" → shown as $order
    format?: string;         // json / xml / csv …
    fields: SchemaFieldInfo[];
}

export interface TreeviewWidgetProps {
    inputs: TreeviewInput[];
    outputName?: string;     // contract root name (best-effort)
    outputFields: SchemaFieldInfo[];
    coverage?: CoverageReport;
}

const STATUS_META: Record<CoverageStatus, { icon: string; cls: string; label: string }> = {
    direct:    { icon: '✓', cls: 'cov-direct',    label: 'direct' },
    derivable: { icon: '~', cls: 'cov-derivable', label: 'derivable' },
    gap:       { icon: '✗', cls: 'cov-gap',       label: 'gap' },
};

const hasChildren = (f: SchemaFieldInfo): boolean =>
    Array.isArray(f.fields) && (f.fields as SchemaFieldInfo[]).length > 0;

/** Count coverage gaps among the leaves at/under a dotted path prefix. */
function gapCountUnder(prefix: string, byPath: Map<string, CoverageEntry>): number {
    let n = 0;
    byPath.forEach((e, p) => {
        if ((p === prefix || p.startsWith(prefix + '.')) && e.status === 'gap') n++;
    });
    return n;
}

// ── a single tree node (used for both panes) ─────────────────────────────────
const TreeNode: React.FC<{
    field: SchemaFieldInfo;
    path: string;                 // dotted path from the root (matches CoverageEntry.outputPath)
    depth: number;
    byPath?: Map<string, CoverageEntry>;   // present ⇒ output pane (coverage coloring)
    defaultExpanded: boolean;
}> = ({ field, path, depth, byPath, defaultExpanded }) => {
    const [open, setOpen] = React.useState(defaultExpanded);
    const container = hasChildren(field);
    const entry = byPath?.get(path);
    const meta = entry ? STATUS_META[entry.status] : undefined;
    const gaps = byPath && container ? gapCountUnder(path, byPath) : 0;

    return (
        <div className='utlx-tv-node'>
            <div
                className={`utlx-tv-row${meta ? ' ' + meta.cls : ''}`}
                style={{ paddingLeft: `${depth * 14 + 6}px` }}
                onClick={() => container && setOpen(o => !o)}
            >
                <span className='utlx-tv-twisty'>{container ? (open ? '▾' : '▸') : ''}</span>
                <span className='utlx-tv-name'>{field.name}</span>
                {field.type && <span className='utlx-tv-type'>{field.type}</span>}
                {field.isRequired && <span className='utlx-tv-req' title='required'>*</span>}
                {/* coverage (output pane, leaves) */}
                {meta && !container && (
                    <span className={`utlx-tv-status ${meta.cls}`} title={meta.label}>
                        {meta.icon}
                        {entry?.source && <span className='utlx-tv-src'>{entry.source}</span>}
                        {!entry?.source && entry?.status === 'gap' && <span className='utlx-tv-src utlx-tv-todo'>// TODO</span>}
                    </span>
                )}
                {/* container gap badge (output pane) */}
                {byPath && container && gaps > 0 && (
                    <span className='utlx-tv-status cov-gap' title={`${gaps} gap(s) below`}>✗ {gaps}</span>
                )}
            </div>
            {container && open && (
                <div className='utlx-tv-children'>
                    {(field.fields as SchemaFieldInfo[]).map((c, i) => (
                        <TreeNode
                            key={`${path}.${c.name}.${i}`}
                            field={c}
                            path={path ? `${path}.${c.name}` : c.name}
                            depth={depth + 1}
                            byPath={byPath}
                            defaultExpanded={depth < 1}
                        />
                    ))}
                </div>
            )}
        </div>
    );
};

export const TreeviewWidget: React.FC<TreeviewWidgetProps> = ({ inputs, outputName, outputFields, coverage }) => {
    const byPath = React.useMemo(() => {
        const m = new Map<string, CoverageEntry>();
        coverage?.entries.forEach(e => m.set(e.outputPath, e));
        return m;
    }, [coverage]);

    const counts = coverage?.counts;
    const hasInputs = inputs.some(i => i.fields && i.fields.length);
    const hasOutput = outputFields && outputFields.length > 0;

    return (
        <div className='utlx-treeview' data-testid='utlx-treeview'>
            {/* Left: all inputs as trees */}
            <div className='utlx-tv-pane utlx-tv-inputs'>
                <div className='utlx-tv-pane-header'>Available inputs</div>
                <div className='utlx-tv-pane-body'>
                    {!hasInputs && <div className='utlx-tv-empty'>No input schemas loaded.</div>}
                    {inputs.map((inp, idx) => (
                        <div className='utlx-tv-input-group' key={`${inp.name}-${idx}`}>
                            <div className='utlx-tv-input-title'>
                                ${inp.name}{inp.format ? <span className='utlx-tv-type'>{inp.format}</span> : null}
                            </div>
                            {(inp.fields || []).map((f, i) => (
                                <TreeNode key={`${inp.name}.${f.name}.${i}`} field={f} path={f.name} depth={0} defaultExpanded={true} />
                            ))}
                        </div>
                    ))}
                </div>
            </div>

            {/* Right: output contract, colored by coverage */}
            <div className='utlx-tv-pane utlx-tv-output'>
                <div className='utlx-tv-pane-header'>
                    Output contract{outputName ? <span className='utlx-tv-type'>{outputName}</span> : null}
                    {counts && (
                        <span className='utlx-tv-summary'>
                            <span className='cov-direct'>✓ {counts.direct}</span>
                            <span className='cov-derivable'>~ {counts.derivable}</span>
                            <span className='cov-gap'>✗ {counts.gap}</span>
                        </span>
                    )}
                </div>
                <div className='utlx-tv-pane-body'>
                    {!hasOutput && <div className='utlx-tv-empty'>No output contract loaded. Load an output schema to see coverage.</div>}
                    {outputFields.map((f, i) => (
                        <TreeNode key={`out.${f.name}.${i}`} field={f} path={f.name} depth={0} byPath={byPath} defaultExpanded={true} />
                    ))}
                </div>
            </div>
        </div>
    );
};
