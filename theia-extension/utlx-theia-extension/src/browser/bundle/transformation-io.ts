/**
 * Bundle save/load helpers — serialize a single transformation constellation into a `.utlxp`
 * project per docs/architecture/bundle-format.md §7 (Save/Open Transformation, File menu).
 *
 * Pure functions only (no Theia DI): the contribution does the dialogs + FileService writes; this
 * module computes WHAT to write (the file plan, transform.yaml, extension maps). Easy to unit-test.
 *
 * "Write whatever's loaded": always write `.utlx` + `transform.yaml` + a `test-input-<slot>` per
 * input instance (Execution-save); additionally write `schemas/` + refs when input/output contracts
 * are present (Message-Contract superset). See bundle-format §5 (test-input rule) and §7.
 */

/** A single input slot at save time. */
export interface SaveInput {
    name: string;            // identifier, declared in the .utlx header (the slot)
    content?: string;        // instance content (omit/empty ⇒ no test-input written)
    format?: string;         // instance data format: json | xml | csv | yaml | odata | …
    schemaContent?: string;  // optional input CONTRACT (MC) — written to schemas/ when present
    schemaFormat?: string;   // jsch | xsd | osch | tsch | avro | proto | …
}

export interface SavePlanFile { path: string; content: string; }
export interface SavePlan {
    dirs: string[];          // directories to create (relative to the .utlxp root), parents-first
    files: SavePlanFile[];   // files to write (relative to the .utlxp root)
}

/** Data-format → file extension for a test-input instance (bundle-format §5). */
export function dataExt(format?: string): string {
    const f = (format || 'json').toLowerCase();
    const map: Record<string, string> = {
        json: 'json', xml: 'xml', csv: 'csv', yaml: 'yaml', yml: 'yaml',
        odata: 'json', text: 'txt', txt: 'txt',
    };
    return map[f] || f;
}

/** Detect the serialization of a text-ish schema from its content (JSON vs YAML vs XML). */
function detectSerializationExt(content?: string): 'json' | 'yaml' | 'xml' {
    const t = (content || '').trimStart();
    if (!t) return 'json';
    if (t.startsWith('{') || t.startsWith('[')) return 'json';
    if (t.startsWith('<')) return 'xml';
    return 'yaml';   // a JSON Schema (or OpenAPI/OData schema) authored as YAML
}

/**
 * Schema-format → file extension for a shared contract under schemas/.
 * For the structured-text families the extension follows the actual **serialization**, not the
 * format tag: a JSON Schema (`jsch`) may be JSON *or* YAML; an OData schema (`osch`) may be JSON,
 * YAML, or EDMX-XML. So pass the content when known. XSD is always XML; tsch/avro/proto/usdl are fixed.
 */
export function schemaExt(format?: string, content?: string): string {
    const f = (format || 'jsch').toLowerCase();
    switch (f) {
        case 'xsd':   return 'xsd';
        case 'tsch':  return 'csv';
        case 'avro':  return 'avsc';
        case 'proto': return 'proto';
        case 'usdl':  return 'usdl';
        case 'jsch':
        case 'osch':
        case 'json':
        default:      return detectSerializationExt(content);
    }
}

/**
 * Generate a minimal-but-valid `transform.yaml` (the per-tx spine, bundle-format §4).
 * Only emits `inputs:`/`output:` blocks when there is something to say (a schema ref);
 * `transform.yaml` must exist for the `.utlxp` loader to pick up the transformation (§2).
 */
export function generateTransformYaml(opts: {
    inputs: Array<{ name: string; schemaRef?: string }>;
    outputSchemaRef?: string;
    strategy?: string;
    validationPolicy?: string;
}): string {
    const lines: string[] = [];
    lines.push(`strategy: ${opts.strategy || 'TEMPLATE'}`);
    lines.push(`validationPolicy: ${opts.validationPolicy || 'SKIP'}`);
    if (opts.inputs.length) {
        lines.push('inputs:');
        for (const i of opts.inputs) {
            lines.push(`  - name: ${i.name}`);
            if (i.schemaRef) lines.push(`    schema: ${i.schemaRef}`);
        }
    }
    if (opts.outputSchemaRef) {
        lines.push('output:');
        lines.push(`  schema: ${opts.outputSchemaRef}`);
    }
    return lines.join('\n') + '\n';
}

/**
 * Build the on-disk plan (relative to the chosen `<name>.utlxp/` root) for one transformation:
 *   transformations/<tx>/<tx>.utlx
 *   transformations/<tx>/transform.yaml
 *   transformations/<tx>/test-input-<slot>.<ext>     (per input with instance content)
 *   schemas/<slot>.<ext>                              (per input with a contract)
 *   schemas/<tx>-output.<ext>                         (when an output contract is present)
 */
export function buildSavePlan(opts: {
    txName: string;
    utlx: string;
    inputs: SaveInput[];
    outputSchemaContent?: string;
    outputSchemaFormat?: string;
}): SavePlan {
    const { txName, utlx, inputs } = opts;
    const txDir = `transformations/${txName}`;
    const dirs: string[] = [txDir];
    const files: SavePlanFile[] = [];

    // .utlx source
    files.push({ path: `${txDir}/${txName}.utlx`, content: utlx });

    // input schemas (contracts) → schemas/<slot>.<ext>
    const yamlInputs: Array<{ name: string; schemaRef?: string }> = [];
    let anySchema = false;
    for (const inp of inputs) {
        let schemaRef: string | undefined;
        if (inp.schemaContent && inp.schemaContent.trim()) {
            const ref = `schemas/${inp.name}.${schemaExt(inp.schemaFormat, inp.schemaContent)}`;
            files.push({ path: ref, content: inp.schemaContent });
            schemaRef = ref;
            anySchema = true;
        }
        yamlInputs.push({ name: inp.name, schemaRef });
        // test-input instance
        if (inp.content && inp.content.trim()) {
            files.push({ path: `${txDir}/test-input-${inp.name}.${dataExt(inp.format)}`, content: inp.content });
        }
    }

    // output contract → schemas/<tx>-output.<ext>
    let outputSchemaRef: string | undefined;
    if (opts.outputSchemaContent && opts.outputSchemaContent.trim()) {
        outputSchemaRef = `schemas/${txName}-output.${schemaExt(opts.outputSchemaFormat, opts.outputSchemaContent)}`;
        files.push({ path: outputSchemaRef, content: opts.outputSchemaContent });
        anySchema = true;
    }

    if (anySchema) dirs.unshift('schemas');

    files.push({
        path: `${txDir}/transform.yaml`,
        content: generateTransformYaml({ inputs: yamlInputs, outputSchemaRef }),
    });

    return { dirs, files };
}

// ─────────────────────────────────────────────────────────────────────────────
// Load side (File → Open UTL-X Project): read a `.utlxp` back into the panels.
// ─────────────────────────────────────────────────────────────────────────────

/** Schema file extension → UTL-X schema-format tag (inverse-ish of schemaExt). */
export function extToSchemaFormat(ref: string): string {
    const ext = (ref.split('.').pop() || '').toLowerCase();
    const map: Record<string, string> = {
        json: 'jsch', yaml: 'jsch', yml: 'jsch', xsd: 'xsd',
        csv: 'tsch', avsc: 'avro', proto: 'proto', usdl: 'usdl',
    };
    return map[ext] || 'jsch';
}

export interface TransformYamlRefs {
    inputs: Array<{ name: string; schemaRef?: string }>;
    outputSchemaRef?: string;
}

/**
 * Minimal reader for the `transform.yaml` schema refs (no YAML dependency). Handles the shape
 * generateTransformYaml() writes: an `inputs:` list of `- name:` / `schema:`, and an `output:` block
 * with `schema:`. Good enough to restore the contract layer on open.
 */
export function parseTransformYamlRefs(yaml: string): TransformYamlRefs {
    const out: TransformYamlRefs = { inputs: [] };
    if (!yaml) return out;
    let section: 'inputs' | 'output' | undefined;
    let cur: { name: string; schemaRef?: string } | undefined;
    for (const raw of yaml.split(/\r?\n/)) {
        const line = raw.replace(/\t/g, '  ');
        if (/^inputs:\s*$/.test(line)) { section = 'inputs'; cur = undefined; continue; }
        if (/^output:\s*$/.test(line)) { section = 'output'; cur = undefined; continue; }
        if (/^[A-Za-z]/.test(line)) { section = undefined; cur = undefined; continue; } // another top-level key
        if (section === 'inputs') {
            const nameM = line.match(/^\s*-\s*name:\s*(.+?)\s*$/);
            if (nameM) { cur = { name: nameM[1] }; out.inputs.push(cur); continue; }
            const schM = line.match(/^\s*schema:\s*(.+?)\s*$/);
            if (schM && cur) { cur.schemaRef = schM[1]; continue; }
        } else if (section === 'output') {
            const schM = line.match(/^\s*schema:\s*(.+?)\s*$/);
            if (schM) { out.outputSchemaRef = schM[1]; }
        }
    }
    return out;
}
