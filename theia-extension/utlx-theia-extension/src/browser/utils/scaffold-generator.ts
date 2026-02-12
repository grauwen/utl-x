/**
 * Scaffold Generator
 *
 * Generates UTLX code scaffolds from output schema/instance structure.
 * Produces typed `???(type)` placeholders that the Function Builder can
 * detect and replace with type-matched expressions.
 *
 * Types use the normalized type system (string, integer, number, boolean,
 * date, time, datetime, binary) — consistent across JSON Schema and XSD sources.
 * Original schema types (e.g. xs:string) are preserved in code comments.
 *
 * UTLX uses the same JSON-like object notation for both JSON and XML output.
 * For XML: property keys become element names, @-prefixed keys become attributes.
 *
 * Supports JSON and XML formats only (CSV excluded - flat structure trivial to type).
 */

import { SchemaFieldInfo } from './schema-field-tree-parser';

/**
 * Result of scaffold generation
 */
export interface ScaffoldResult {
    success: boolean;
    code?: string;
    error?: string;
}

/**
 * Generate UTLX code scaffold from output structure.
 * Uses typed `???(type)` placeholders for values that user fills in.
 *
 * UTLX body syntax is the same for both JSON and XML output —
 * a JSON-like object notation where keys map to elements/fields.
 *
 * @param fields - Field tree from schema or instance parsing
 * @param format - Output format ('json' or 'xml')
 * @returns Generated UTLX code scaffold
 */
export function generateScaffoldFromStructure(
    fields: SchemaFieldInfo[],
    format: 'json' | 'xml'
): ScaffoldResult {
    if (!fields || fields.length === 0) {
        return {
            success: false,
            error: 'No fields available to scaffold'
        };
    }

    try {
        // UTLX uses the same object notation for both JSON and XML output
        const code = generateScaffold(fields, 0);

        return {
            success: true,
            code
        };
    } catch (error) {
        return {
            success: false,
            error: `Scaffold generation failed: ${error instanceof Error ? error.message : String(error)}`
        };
    }
}

/**
 * Generate UTLX scaffold from field tree
 */
function generateScaffold(fields: SchemaFieldInfo[], indent: number): string {
    const pad = '  '.repeat(indent);
    const innerPad = '  '.repeat(indent + 1);

    // Unwrap trivial single-element root wrapper (e.g. XML document element).
    // The root element name is already declared in the UTLX output format header,
    // so the scaffold body should contain its children directly.
    if (fields.length === 1 && fields[0].fields && fields[0].fields.length > 0) {
        const root = fields[0];
        if (root.type === 'array') {
            // Root array - show single object template
            const objectContent = generateObject(root.fields as SchemaFieldInfo[], indent + 1);
            return `[\n${innerPad}${objectContent}\n${pad}]`;
        }
        // Root object - unwrap and use children
        return generateObject(root.fields as SchemaFieldInfo[], indent);
    }

    // Check if this is a root array (single field that is an array, no children)
    if (fields.length === 1 && fields[0].type === 'array') {
        return `[\n${innerPad}${typedPlaceholder(fields[0].type)}\n${pad}]`;
    }

    // Regular object (multiple top-level fields)
    return generateObject(fields, indent);
}

/**
 * Generate a UTLX object structure
 */
function generateObject(fields: SchemaFieldInfo[], indent: number): string {
    const pad = '  '.repeat(indent);
    const innerPad = '  '.repeat(indent + 1);
    const lines: string[] = ['{'];

    for (let i = 0; i < fields.length; i++) {
        const field = fields[i];
        const isLast = i === fields.length - 1;
        const value = generateFieldValue(field, indent + 1);
        const comma = isLast ? '' : ',';

        // Comment shows original schema type (if different from normalized type) + required marker
        const requiredMarker = field.isRequired ? ' (required)' : '';
        const schemaNote = field.schemaType && field.schemaType !== field.type ? field.schemaType : '';
        const comment = schemaNote || requiredMarker
            ? `  // ${schemaNote}${requiredMarker}`
            : '';

        lines.push(`${innerPad}${field.name}: ${value}${comma}${comment}`);
    }

    lines.push(`${pad}}`);
    return lines.join('\n');
}

/**
 * Generate typed placeholder: ???(type)
 */
function typedPlaceholder(type: string): string {
    return `???(${type || 'any'})`;
}

/**
 * Generate value for a single field
 */
function generateFieldValue(field: SchemaFieldInfo, indent: number): string {
    // Nested object
    if (field.type === 'object' && field.fields && field.fields.length > 0) {
        return generateObject(field.fields as SchemaFieldInfo[], indent);
    }

    // Array
    if (field.type === 'array') {
        if (field.fields && field.fields.length > 0) {
            // Array of objects - show single object template
            const objectContent = generateObject(field.fields as SchemaFieldInfo[], indent + 1);
            const innerPad = '  '.repeat(indent + 1);
            const pad = '  '.repeat(indent);
            return `[\n${innerPad}${objectContent}\n${pad}]`;
        } else {
            // Array of primitives
            return `[${typedPlaceholder(field.type)}]`;
        }
    }

    // Simple field - typed placeholder
    return typedPlaceholder(field.type);
}

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * Check if format is supported for scaffolding
 * Only JSON and XML are supported (CSV excluded)
 */
export function isScaffoldSupportedFormat(format: string): boolean {
    const normalizedFormat = format.toLowerCase();
    return normalizedFormat === 'json' || normalizedFormat === 'xml';
}
