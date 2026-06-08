/**
 * IF20 — Phase 1: deterministic mapping-strategy analyzer.
 *
 * Mode-agnostic. Given the input field trees, an optional output target (the contract in
 * MC mode; an expected output schema/instance in Execution mode, if any), and the IF11
 * coverage report, it classifies:
 *   - each input's ROLE   (driver / lookup-table / enrichment),
 *   - the output↔driver RELATIONSHIP (mirror / array-transform / join / aggregate / restructure),
 *   - join keys for lookup inputs, and a mirror-ratio.
 *
 * This is the planning input for strategy-specific prompt selection (the rest of IF20). It is
 * purely heuristic + deterministic — no AI. An optional AI confirmation step is a later phase.
 *
 * NOTE: input-ROLE analysis works in BOTH modes (it's about the inputs). The RELATIONSHIP and
 * mirror-ratio need an output target + coverage; without them the relationship is 'unknown'.
 */

import { CoverageReport } from './coverage';
import { SchemaFieldInfo } from './schema-field-tree-parser';

export type InputRole = 'driver' | 'lookup' | 'enrichment';
export type MappingRelationship =
    | 'mirror'          // output ≈ driver  → spread (`...$driver`) + overrides
    | 'array-transform' // driver is a collection → map(driver, …)
    | 'join'            // driver + lookup table(s) → index the lookup, look up per row
    | 'aggregate'       // many → few → groupBy/reduce
    | 'restructure'     // same data, new shape → explicit construction
    | 'unknown';        // not enough signal (no output target / no coverage)

export interface StrategyInput {
    name: string;
    isArray?: boolean;          // cardinality — drives array-transform detection
    fields: SchemaFieldInfo[];  // the input's field tree
}

export interface StrategyOutputTarget {
    isArray?: boolean;
    fields: SchemaFieldInfo[];
}

export interface StrategyJoin {
    input: string;  // the lookup input
    key: string;    // the field (name) used to join it to the driver
}

export interface StrategyAnalysis {
    relationship: MappingRelationship;
    driver?: string;
    perInputRole: Record<string, InputRole>;
    joins: StrategyJoin[];
    mirrorRatio: number;   // 0..1 — fraction of output leaves sourced 1:1 from the driver at the same path
    signals: string[];     // human-readable reasons (for the UI / prompt / debugging)
}

const norm = (s: string): string => (s || '').toLowerCase().replace(/[^a-z0-9]/g, '');
const isKeyish = (name: string): boolean =>
    /(id|key|code|ref|sku|guid|uuid|number|no)$/.test(norm(name));

/** Flatten a field tree to the set of normalized field names (all levels). */
function fieldNames(fields: SchemaFieldInfo[], out = new Set<string>()): Set<string> {
    for (const f of fields || []) {
        out.add(norm(f.name));
        const kids = f.fields as SchemaFieldInfo[] | undefined;
        if (kids && kids.length) fieldNames(kids, out);
    }
    return out;
}

/** Split a coverage `source` ("inputName.path") into [inputName, path]. */
function splitSource(source: string): [string, string] {
    const i = source.indexOf('.');
    return i < 0 ? [source, ''] : [source.slice(0, i), source.slice(i + 1)];
}

/**
 * Analyze the mapping strategy. Pure + deterministic.
 */
export function analyzeStrategy(
    inputs: StrategyInput[],
    output: StrategyOutputTarget | undefined,
    coverage: CoverageReport | undefined,
): StrategyAnalysis {
    const signals: string[] = [];
    const perInputRole: Record<string, InputRole> = {};
    const joins: StrategyJoin[] = [];

    if (!inputs || inputs.length === 0) {
        return { relationship: 'unknown', perInputRole, joins, mirrorRatio: 0, signals: ['no inputs'] };
    }

    // --- per-input usage from the coverage matrix (how many output leaves each input feeds) ---
    const usage: Record<string, number> = {};
    const mirrorHits: Record<string, number> = {};   // leaves where source path === output path (1:1)
    inputs.forEach(i => { usage[i.name] = 0; mirrorHits[i.name] = 0; });
    const totalLeaves = coverage ? coverage.entries.length : (output ? fieldNames(output.fields).size : 0);

    if (coverage) {
        for (const e of coverage.entries) {
            if (!e.source || e.status === 'gap') continue;
            const [inName, srcPath] = splitSource(e.source);
            if (usage[inName] === undefined) continue;
            usage[inName]++;
            if (srcPath && srcPath === e.outputPath) mirrorHits[inName]++;
        }
    }

    // --- driver = the input feeding the most output leaves (ties broken by array-ness, then size) ---
    const driver = [...inputs].sort((a, b) => {
        const du = (usage[b.name] || 0) - (usage[a.name] || 0);
        if (du !== 0) return du;
        if (!!b.isArray !== !!a.isArray) return (b.isArray ? 1 : 0) - (a.isArray ? 1 : 0);
        return fieldNames(b.fields).size - fieldNames(a.fields).size;
    })[0];
    const driverName = driver?.name;
    if (driverName) {
        perInputRole[driverName] = 'driver';
        signals.push(`driver = ${driverName} (feeds ${usage[driverName] || 0}/${totalLeaves || '?'} output leaves)`);
    }

    const mirrorRatio = totalLeaves > 0 && driverName ? mirrorHits[driverName] / totalLeaves : 0;
    if (driverName) signals.push(`mirrorRatio = ${mirrorRatio.toFixed(2)}`);

    // --- classify the non-driver inputs (lookup vs enrichment) + find join keys ---
    const driverFields = driver ? fieldNames(driver.fields) : new Set<string>();
    for (const inp of inputs) {
        if (inp.name === driverName) continue;
        // A join key: a keyish field this input shares (by name) with the driver.
        let joinKey: string | undefined;
        for (const f of (inp.fields || [])) {
            if (isKeyish(f.name) && driverFields.has(norm(f.name))) { joinKey = f.name; break; }
        }
        if (joinKey) {
            perInputRole[inp.name] = 'lookup';
            joins.push({ input: inp.name, key: joinKey });
            signals.push(`lookup = ${inp.name} (join on ${joinKey})`);
        } else if ((usage[inp.name] || 0) <= 2) {
            perInputRole[inp.name] = 'enrichment';
            signals.push(`enrichment = ${inp.name} (feeds ${usage[inp.name] || 0} leaves, no join key)`);
        } else {
            // Feeds many leaves but no obvious key → treat as a secondary lookup source.
            perInputRole[inp.name] = 'lookup';
            signals.push(`lookup = ${inp.name} (feeds ${usage[inp.name] || 0} leaves, no detected key)`);
        }
    }

    // --- relationship ---
    let relationship: MappingRelationship = 'unknown';
    if (!output && !coverage) {
        relationship = 'unknown';
        signals.push('no output target / coverage → relationship undetermined (input roles only)');
    } else if (output?.isArray && driver?.isArray) {
        relationship = 'array-transform';
        if (joins.length) signals.push('array-transform composed with join(s)');
    } else if (mirrorRatio >= 0.6) {
        relationship = 'mirror';
    } else if (joins.length > 0) {
        relationship = 'join';
    } else if (output && output.isArray === false && driver?.isArray) {
        relationship = 'aggregate';   // collection driver → single object output
    } else {
        relationship = 'restructure';
    }
    signals.push(`relationship = ${relationship}`);

    return { relationship, driver: driverName, perInputRole, joins, mirrorRatio, signals };
}
