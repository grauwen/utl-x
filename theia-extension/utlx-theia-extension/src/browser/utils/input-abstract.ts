/**
 * Per-input semantic abstract — the "reversed prompt" (IF10), v1.
 *
 * DETERMINISTIC, no AI: walks the input's UDM (via the existing path extractor) and
 * summarizes WHAT the message is — entity, section-level fields, array cardinalities,
 * and nesting depth. It cannot hallucinate (it only reports what the UDM contains).
 *
 * Key behavior: it UNWRAPS a single-root wrapper (e.g. an XML document element, or a
 * SOAP Envelope→Body chain) so the summary surfaces the real sections (Patient,
 * Provider, …) instead of just the wrapper. Array/exemplar paths are shown relative
 * to that entity anchor so they're concise.
 */

import { extractInputPaths, PathInfo } from './udm-path-extractor';
import {
    SchemaFieldInfo,
    parseJsonSchemaToFieldTree,
    parseXsdToFieldTree,
    parseOSchToFieldTree,
    parseTschToFieldTree,
} from './schema-field-tree-parser';

export interface AbstractField {
    name: string;
    type: string;
    required?: boolean;     // schema only — declared required
    constraints?: string;   // schema only — e.g. "maxLength: 100", "min: 0"
}

export interface InputAbstract {
    rootKind: string;                                  // object | array | scalar
    rootEntity?: string;                               // unwrapped single-root element (e.g. "HealthcareClaim")
    topFields: AbstractField[];                        // sections directly under the entity/root
    arrayPaths: string[];                              // array paths, relative to the entity anchor
    depth: number;                                     // true max nesting depth (from the input root)
    deepestPath?: string;                              // exemplar deepest path, relative to the entity anchor
    pathCount: number;
    fromSchema?: boolean;                              // true when built from a schema field tree
    arrayElementKind?: string;                         // for a root array: the element type (object/string/…)
    perItemFields?: boolean;                           // topFields are the array element's fields (e.g. CSV columns)
}

/** Immediate children of an anchor (relative-path prefix; '' = root), one segment deep. */
function childrenOf(
    relPaths: Array<{ path: string; type: string }>,
    anchor: string
): Array<{ name: string; type: string }> {
    const sep = anchor === '' ? '' : anchor + '.';
    const out: Array<{ name: string; type: string }> = [];
    const seen = new Set<string>();
    for (const x of relPaths) {
        if (anchor !== '' && !x.path.startsWith(sep)) {
            continue;
        }
        const tail = anchor === '' ? x.path : x.path.slice(sep.length);
        const m = tail.match(/^([^.[]+)$/); // exactly one more segment (no '.' or '[')
        if (m && !seen.has(m[1])) {
            seen.add(m[1]);
            out.push({ name: m[1], type: x.type });
        }
    }
    return out;
}

/** Does the anchor have any descendants (so it's worth unwrapping into)? */
function hasDescendants(relPaths: Array<{ path: string }>, anchor: string): boolean {
    return relPaths.some(x => x.path.startsWith(anchor + '.') || x.path.startsWith(anchor + '['));
}

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
        p.startsWith(prefix) ? p.slice(prefix.length).replace(/^\./, '') : p;

    const relPaths = paths.map(p => ({ path: rel(p.path), type: p.type })).filter(x => x.path !== '');
    const rootKind = paths.find(p => p.path === prefix)?.type || 'object';

    let anchor = '';
    let rootEntity: string | undefined;

    // Root array (e.g. CSV rows, JSON array of records): describe its ELEMENT, so we
    // list the per-item fields (columns) rather than nothing. Note the element kind so
    // we can say "array of objects" vs "array of string values" accurately.
    let arrayElementKind: string | undefined;
    let perItemFields = false;
    if (rootKind === 'array') {
        arrayElementKind = relPaths.find(x => x.path === '[]')?.type;
        if (relPaths.some(x => x.path.startsWith('[].'))) {
            anchor = '[]';
            perItemFields = true;
        }
    }

    // Unwrap single-root wrappers: while the current anchor has exactly ONE child that
    // is an object/array with its own descendants, descend into it. Stops at the first
    // level with multiple sections (the real entity body).
    for (let guard = 0; guard < 10; guard++) {
        const kids = childrenOf(relPaths, anchor);
        if (kids.length !== 1) {
            break;
        }
        const only = kids[0];
        const childAnchor = anchor === '' ? only.name : `${anchor}.${only.name}`;
        if ((only.type === 'object' || only.type === 'array') && hasDescendants(relPaths, childAnchor)) {
            anchor = childAnchor;
            rootEntity = only.name;
        } else {
            break;
        }
    }

    const stripAnchor = (r: string): string =>
        anchor && r.startsWith(anchor + '.') ? r.slice(anchor.length + 1) : r;

    const topFields = childrenOf(relPaths, anchor);

    const arrayPaths = relPaths
        .filter(x => x.type === 'array' && x.path !== anchor)  // exclude the wrapper itself
        .map(x => stripAnchor(x.path))
        .filter(s => s !== '');

    // True nesting depth, measured from the input root (so it reflects the real shape).
    let depth = 0;
    let deepestRel: string | undefined;
    for (const x of relPaths) {
        const segs = x.path.replace(/\[\]/g, '').split('.').filter(Boolean).length;
        if (segs > depth) {
            depth = segs;
            deepestRel = x.path;
        }
    }

    return {
        rootKind,
        rootEntity,
        topFields,
        arrayPaths,
        depth,
        deepestPath: deepestRel ? stripAnchor(deepestRel) : undefined,
        pathCount: paths.length,
        arrayElementKind,
        perItemFields,
    };
}

/**
 * Build the abstract from a parsed SCHEMA field tree (JSCH/XSD/OSch/TSch). For a
 * schema input the "structure" is the DECLARED contract, not an instance UDM — this
 * walks the field tree (name/type/fields, arrays via type==='array'). Unwraps a single
 * root element like the UDM path version.
 */
export function buildAbstractFromSchemaFields(fields: SchemaFieldInfo[] | undefined): InputAbstract | undefined {
    if (!fields || fields.length === 0) {
        return undefined;
    }

    // Unwrap a single-root wrapper (e.g. an XSD root element).
    let roots = fields;
    let rootEntity: string | undefined;
    for (let guard = 0; guard < 10; guard++) {
        if (roots.length === 1 && roots[0].fields && roots[0].fields.length > 0) {
            rootEntity = roots[0].name;
            roots = roots[0].fields as SchemaFieldInfo[];
        } else {
            break;
        }
    }

    const arrayPaths: string[] = [];
    let depth = 0;
    let deepestPath: string | undefined;
    const walk = (fs: SchemaFieldInfo[], prefix: string, d: number): void => {
        if (d > depth) {
            depth = d;
            deepestPath = prefix || undefined;
        }
        for (const f of fs) {
            const path = prefix ? `${prefix}.${f.name}` : f.name;
            if (f.type === 'array') {
                arrayPaths.push(path);
            }
            const kids = f.fields as SchemaFieldInfo[] | undefined;
            if (kids && kids.length > 0) {
                walk(kids, path, d + 1);
            } else if (d + 1 > depth) {
                depth = d + 1;
                deepestPath = path;
            }
        }
    };
    walk(roots, '', 1);

    return {
        rootKind: 'object',
        rootEntity,
        topFields: roots.map(f => ({
            name: f.name,
            type: f.type,
            required: f.isRequired,
            constraints: f.constraints,
        })),
        arrayPaths,
        depth,
        deepestPath,
        pathCount: arrayPaths.length,
        fromSchema: true,
    };
}

/** The fields needed to summarize an input — works for both data and schemas. */
export interface AbstractInputSource {
    name: string;
    instanceContent?: string;
    instanceFormat?: string;
    schemaContent?: string;
    schemaFormat?: string;
    udmLanguage?: string;
}

/**
 * The single source of truth for an input's abstract, shared by the input-panel Info
 * button and the AI-dialog input list. For a SCHEMA-format input (jsch/xsd/osch/tsch)
 * it describes the declared contract via the schema field tree; otherwise it describes
 * the instance data via the UDM. Returns the formatted string (or undefined).
 */
export function buildAbstractForInput(input: AbstractInputSource): string | undefined {
    const parseSchema = (content: string | undefined, format: string | undefined): SchemaFieldInfo[] | undefined => {
        if (!content || !content.trim()) {
            return undefined;
        }
        try {
            switch (format) {
                case 'jsch': return parseJsonSchemaToFieldTree(content);
                case 'xsd': return parseXsdToFieldTree(content);
                case 'osch': return parseOSchToFieldTree(content);
                case 'tsch': return parseTschToFieldTree(content);
                default: return undefined;
            }
        } catch {
            return undefined;
        }
    };

    // 1) instance content that IS a schema (e.g. a loaded .jsch), 2) MC schema content.
    let fields = parseSchema(input.instanceContent, input.instanceFormat);
    if ((!fields || fields.length === 0) && input.schemaContent) {
        fields = parseSchema(input.schemaContent, input.schemaFormat);
    }
    if (fields && fields.length > 0) {
        const a = buildAbstractFromSchemaFields(fields);
        if (a) {
            return formatInputAbstract(a);
        }
    }

    // 3) instance DATA → describe from the UDM.
    if (input.udmLanguage) {
        const a = buildInputAbstract(input.udmLanguage, input.name);
        if (a) {
            return formatInputAbstract(a);
        }
    }
    return undefined;
}

/** Render an abstract as a short, human-readable block (dialog + prompt). */
export function formatInputAbstract(a: InputAbstract): string {
    const lines: string[] = [];

    // Compact comma form (data/UDM inputs, which have no constraints).
    const fieldList = (fs: AbstractField[]) =>
        fs.map(f => `${f.name}${f.required ? '*' : ''} (${f.type})`).join(', ');

    // One-per-line, aligned form (schema inputs — types + constraints are dense).
    const fieldBlock = (fs: AbstractField[]) => {
        const nameW = Math.max(...fs.map(f => f.name.length + (f.required ? 1 : 0)));
        const typeW = Math.max(...fs.map(f => f.type.length));
        return fs.map(f => {
            const nm = `${f.name}${f.required ? '*' : ''}`.padEnd(nameW + 2);
            const ty = f.constraints ? f.type.padEnd(typeW + 2) : f.type;
            return `  ${nm}${ty}${f.constraints ? f.constraints : ''}`;
        }).join('\n');
    };

    const arrayShape = () => {
        const k = a.arrayElementKind;
        if (!k || k === 'object') return 'array of objects';
        if (k === 'array') return 'array of arrays';
        return `array of ${k} values`;
    };
    const label = a.rootEntity ? 'Sections:' : (a.perItemFields ? 'Fields (per item):' : 'Top fields:');
    if (a.rootEntity) {
        lines.push(`Entity: ${a.rootEntity} (root element)`);
    } else {
        lines.push(`Shape: ${a.rootKind === 'array' ? arrayShape() : a.rootKind}`);
    }
    if (a.topFields.length > 0) {
        if (a.fromSchema) {
            lines.push(label, fieldBlock(a.topFields));
        } else {
            lines.push(`${label} ${fieldList(a.topFields)}`);
        }
    }

    // Schema contract: which top fields are mandatory (quick summary).
    if (a.fromSchema) {
        const required = a.topFields.filter(f => f.required).map(f => f.name);
        lines.push(required.length > 0 ? `Required: ${required.join(', ')}` : 'Required: (none)');
    }

    if (a.arrayPaths.length > 0) {
        const shown = a.arrayPaths.slice(0, 10).join(', ');
        const more = a.arrayPaths.length > 10 ? ` (+${a.arrayPaths.length - 10} more)` : '';
        lines.push(`Collections (arrays): ${shown}${more}`);
    }

    const deepNote = a.depth >= 6 ? ' (deeply nested)' : '';
    lines.push(`Depth: ${a.depth}${deepNote}${a.deepestPath ? ` — e.g. ${a.deepestPath}` : ''}`);
    if (a.fromSchema && a.topFields.some(f => f.required)) {
        lines.push('(* = required)');
    }
    return lines.join('\n');
}
