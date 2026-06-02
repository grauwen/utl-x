/**
 * Message Contract coverage analysis (IF11, Phase 1 — deterministic).
 *
 * Given input schema field tree(s) and an output schema field tree, classify each
 * output leaf as direct / derivable / gap by matching field NAMES and types. The
 * GAP set (required fields with no source) is the DELTA — what the inputs can't
 * satisfy (→ a lookup, an extra input, or a default). No AI here; the LLM refines
 * gaps into semantic/derivable matches in a later phase.
 */

import {
    SchemaFieldInfo,
    parseJsonSchemaToFieldTree,
    parseXsdToFieldTree,
    parseOSchToFieldTree,
    parseTschToFieldTree,
} from './schema-field-tree-parser';
import { CoverageSuggestion } from '../../common/protocol';

export type CoverageStatus = 'direct' | 'derivable' | 'gap';

export interface CoverageEntry {
    outputPath: string;
    type: string;
    required: boolean;
    status: CoverageStatus;
    source?: string;     // "inputName.path" of the matched field
    note?: string;
    bySuggestion?: boolean;  // IF11: filled by LLM gap refinement (not deterministic)
    expression?: string;     // IF11: optional LLM-suggested derivation hint
}

export interface CoverageReport {
    entries: CoverageEntry[];
    delta: CoverageEntry[];  // required + gap (unsatisfiable from current inputs)
    counts: { direct: number; derivable: number; gap: number; total: number };
}

export interface CoverageInput {
    name: string;
    fields: SchemaFieldInfo[];
}

interface FlatField { path: string; name: string; type: string; required: boolean; }

const normalize = (name: string): string => (name || '').toLowerCase().replace(/[^a-z0-9]/g, '');

/** Normalized name of a path's parent segment ('' for a top-level leaf). */
const parentNorm = (path: string): string => {
    const i = path.lastIndexOf('.');
    return i < 0 ? '' : normalize(path.slice(0, i).split('.').pop() || '');
};

const normType = (t: string): string => {
    const x = (t || '').toLowerCase();
    if (['integer', 'long', 'float', 'double', 'decimal'].includes(x)) return 'number';
    if (x === 'enum') return 'string';
    if (['datetime', 'date', 'time', 'timestamp'].includes(x)) return 'string'; // usually serialized as strings
    return x;
};

/** Flatten a field tree to its LEAF fields (the values that actually need a source). */
function flattenLeaves(fields: SchemaFieldInfo[], prefix = ''): FlatField[] {
    const out: FlatField[] = [];
    for (const f of fields) {
        const path = prefix ? `${prefix}.${f.name}` : f.name;
        const kids = f.fields as SchemaFieldInfo[] | undefined;
        if (kids && kids.length > 0) {
            out.push(...flattenLeaves(kids, path));  // container itself isn't a leaf
        } else {
            out.push({ path, name: f.name, type: f.type, required: !!f.isRequired });
        }
    }
    return out;
}

/**
 * Deterministic coverage: for every output leaf, find a source by name (exact
 * normalized, then fuzzy substring) and type compatibility.
 */
export function buildCoverage(inputs: CoverageInput[], output: SchemaFieldInfo[]): CoverageReport {
    const outLeaves = flattenLeaves(output);

    const inputLeaves: Array<{ inputName: string; path: string; name: string; type: string; norm: string }> = [];
    for (const inp of inputs) {
        for (const lf of flattenLeaves(inp.fields)) {
            inputLeaves.push({ inputName: inp.name, path: lf.path, name: lf.name, type: lf.type, norm: normalize(lf.name) });
        }
    }

    const entries: CoverageEntry[] = outLeaves.map(of => {
        const base = { outputPath: of.path, type: of.type, required: of.required };
        const k = normalize(of.name);

        // Tier 1: exact normalized name match. When several inputs share the name,
        // prefer a type-compatible candidate, and among those one whose parent segment
        // aligns with the output's (so output `lines.name` picks `lines.name`, not a
        // sibling `customer.name`).
        const exact = inputLeaves.filter(il => il.norm === k);
        if (exact.length > 0) {
            const outParent = parentNorm(of.path);
            const typed = exact.filter(c => normType(c.type) === normType(of.type));
            const sameType =
                typed.find(c => parentNorm(c.path) === outParent) || typed[0];
            if (sameType) {
                return { ...base, status: 'direct', source: `${sameType.inputName}.${sameType.path}` };
            }
            const c = exact.find(x => parentNorm(x.path) === outParent) || exact[0];
            return { ...base, status: 'derivable', source: `${c.inputName}.${c.path}`, note: `convert ${c.type} → ${of.type}` };
        }

        // Tier 2: fuzzy substring match (e.g. id ⊂ customerId, email ⊂ emailAddress).
        const fuzzy = inputLeaves.find(il => {
            if (il.norm.length < 3 || k.length < 3) return false;
            return il.norm.includes(k) || k.includes(il.norm);
        });
        if (fuzzy) {
            return { ...base, status: 'derivable', source: `${fuzzy.inputName}.${fuzzy.path}`, note: 'fuzzy name match — review' };
        }

        // Tier 3: no source.
        return { ...base, status: 'gap', note: 'no source field with a matching name' };
    });

    const delta = entries.filter(e => e.status === 'gap' && e.required);
    const counts = {
        direct: entries.filter(e => e.status === 'direct').length,
        derivable: entries.filter(e => e.status === 'derivable').length,
        gap: entries.filter(e => e.status === 'gap').length,
        total: entries.length,
    };
    return { entries, delta, counts };
}

/** Parse schema content (JSCH/XSD/OSch/TSch) to a field tree; undefined if empty/unknown. */
export function parseSchemaToFields(content?: string, format?: string): SchemaFieldInfo[] | undefined {
    if (!content || !content.trim()) return undefined;
    try {
        switch ((format || '').toLowerCase()) {
            case 'jsch': return parseJsonSchemaToFieldTree(content);
            case 'xsd': return parseXsdToFieldTree(content);
            case 'osch': return parseOSchToFieldTree(content);
            case 'tsch': return parseTschToFieldTree(content);
            default: return undefined;
        }
    } catch {
        return undefined;
    }
}

export interface ContractSchemaInput { name: string; content?: string; format?: string; }

/**
 * Convenience for the MC-mode dialog: parse input schema(s) + the output schema and
 * run coverage. Returns undefined if the output schema can't be parsed (nothing to
 * cover against).
 */
export function buildContractCoverage(
    inputs: ContractSchemaInput[],
    outputContent?: string,
    outputFormat?: string,
): CoverageReport | undefined {
    const outFields = parseSchemaToFields(outputContent, outputFormat);
    if (!outFields || outFields.length === 0) return undefined;
    const covInputs: CoverageInput[] = [];
    for (const inp of inputs) {
        const f = parseSchemaToFields(inp.content, inp.format);
        if (f && f.length) covInputs.push({ name: inp.name, fields: f });
    }
    return buildCoverage(covInputs, outFields);
}

/** Flatten a schema field tree to its leaf paths (for the refine_coverage payload). */
export function flattenSchemaLeaves(fields: SchemaFieldInfo[]): Array<{ path: string; name: string; type: string }> {
    return flattenLeaves(fields).map(f => ({ path: f.path, name: f.name, type: f.type }));
}

/**
 * IF11: merge LLM gap suggestions into a coverage report. Only entries that were a
 * GAP are updated (the deterministic direct/derivable matches are authoritative).
 * Recomputes the delta to the gaps that survive (truly unmappable + still required).
 */
export function mergeCoverageSuggestions(report: CoverageReport, suggestions: CoverageSuggestion[]): CoverageReport {
    const byPath = new Map(suggestions.map(s => [s.path, s]));
    const entries: CoverageEntry[] = report.entries.map(e => {
        if (e.status !== 'gap') return e;
        const s = byPath.get(e.outputPath);
        if (!s || s.status === 'unmappable') {
            return s?.rationale ? { ...e, note: s.rationale } : e;  // keep gap, note why
        }
        return {
            ...e,
            status: s.status,
            source: s.source,
            expression: s.expression,
            note: s.rationale,
            bySuggestion: true,
        };
    });
    const delta = entries.filter(e => e.status === 'gap' && e.required);
    const counts = {
        direct: entries.filter(e => e.status === 'direct').length,
        derivable: entries.filter(e => e.status === 'derivable').length,
        gap: entries.filter(e => e.status === 'gap').length,
        total: entries.length,
    };
    return { entries, delta, counts };
}

/** Render a coverage report as a readable block. */
export function formatCoverage(report: CoverageReport): string {
    const lines: string[] = [];
    const c = report.counts;
    lines.push(`Coverage: ${c.direct} direct, ${c.derivable} derivable, ${c.gap} gap (of ${c.total})`);
    const icon = (s: CoverageStatus) => (s === 'direct' ? '✓' : s === 'derivable' ? '~' : '✗');
    for (const e of report.entries) {
        const src = e.source ? ` ← ${e.source}` : '';
        const note = e.note ? `  (${e.note})` : '';
        const req = e.required ? '*' : '';
        lines.push(`  ${icon(e.status)} ${e.outputPath}${req} (${e.type})${src}${note}`);
    }
    if (report.delta.length > 0) {
        lines.push(`Delta — required output with no source: ${report.delta.map(e => e.outputPath).join(', ')}`);
    } else {
        lines.push('Delta: none — every required output field has a candidate source.');
    }
    return lines.join('\n');
}
