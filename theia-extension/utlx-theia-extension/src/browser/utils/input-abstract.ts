/**
 * Per-input semantic abstract — the "reversed prompt" (IF10), v1.
 *
 * DETERMINISTIC, no AI: walks the input's UDM (via the existing path extractor) and
 * summarizes WHAT the message is — shape, top-level fields, array cardinalities, and
 * nesting depth. Used to ground AI generation and to show the user "what is this
 * input?" in the AI dialog. It cannot hallucinate (it only reports what the UDM has).
 */

import { extractInputPaths, PathInfo } from './udm-path-extractor';

export interface InputAbstract {
    rootKind: string;                                  // object | array | scalar
    topFields: Array<{ name: string; type: string }>;  // direct children of the root
    arrayPaths: string[];                              // relative paths that are arrays
    depth: number;                                     // max nesting depth
    deepestPath?: string;                              // an exemplar of the deepest path
    pathCount: number;
}

/**
 * Build the abstract from a UDM string. Returns undefined when there's no usable UDM
 * (caller shows nothing / "load data to summarize").
 */
export function buildInputAbstract(udm: string | undefined, inputName: string): InputAbstract | undefined {
    if (!udm || udm.trim().length === 0) {
        return undefined;
    }
    let paths: PathInfo[];
    try {
        paths = extractInputPaths(udm, inputName);
    } catch {
        return undefined;
    }
    if (!paths || paths.length === 0) {
        return undefined;
    }

    const prefix = `$${inputName}`;
    const rel = (p: string): string =>
        p.startsWith(prefix) ? (p.slice(prefix.length).replace(/^\./, '') || '(root)') : p;

    const rootKind = paths.find(p => p.path === prefix)?.type || 'object';

    // Top-level fields: a single segment after the prefix (no further '.' or '[]').
    const topFields: Array<{ name: string; type: string }> = [];
    const seen = new Set<string>();
    for (const p of paths) {
        if (!p.path.startsWith(prefix + '.')) {
            continue;
        }
        const m = p.path.slice(prefix.length + 1).match(/^([^.[]+)$/);
        if (m && !seen.has(m[1])) {
            seen.add(m[1]);
            topFields.push({ name: m[1], type: p.type });
        }
    }

    const arrayPaths = paths
        .filter(p => p.type === 'array')
        .map(p => rel(p.path))
        .filter(s => s !== '(root)');

    let depth = 0;
    let deepestPath: string | undefined;
    for (const p of paths) {
        const r = rel(p.path);
        if (r === '(root)') {
            continue;
        }
        const segs = r.replace(/\[\]/g, '').split('.').filter(Boolean).length;
        if (segs > depth) {
            depth = segs;
            deepestPath = r;
        }
    }

    return { rootKind, topFields, arrayPaths, depth, deepestPath, pathCount: paths.length };
}

/** Render an abstract as a short, human-readable block (dialog + prompt). */
export function formatInputAbstract(a: InputAbstract): string {
    const lines: string[] = [];
    lines.push(`Shape: ${a.rootKind === 'array' ? 'array of objects' : a.rootKind}`);
    if (a.topFields.length > 0) {
        lines.push(`Top fields: ${a.topFields.map(f => `${f.name} (${f.type})`).join(', ')}`);
    }
    if (a.arrayPaths.length > 0) {
        const shown = a.arrayPaths.slice(0, 8).join(', ');
        const more = a.arrayPaths.length > 8 ? ` (+${a.arrayPaths.length - 8} more)` : '';
        lines.push(`Collections (arrays): ${shown}${more}`);
    }
    lines.push(`Nesting depth: ${a.depth}${a.deepestPath ? ` (e.g. ${a.deepestPath})` : ''}`);
    return lines.join('\n');
}
