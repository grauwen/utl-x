/**
 * Scaffold Generator
 *
 * Generates UTLX code scaffolds from output schema/instance structure.
 * Produces code with `???` placeholders that users fill in manually.
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
 * Uses `???` as placeholder for values that user fills in.
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

    // Check if this is a root array (single field that is an array)
    if (fields.length === 1 && fields[0].type === 'array') {
        const arrayField = fields[0];
        if (arrayField.fields && arrayField.fields.length > 0) {
            // Array of objects
            const objectContent = generateObject(arrayField.fields as SchemaFieldInfo[], indent + 1);
            return `[\n${innerPad}${objectContent}\n${pad}]`;
        } else {
            // Array of primitives
            return `[\n${innerPad}???\n${pad}]`;
        }
    }

    // Regular object
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

        // Add comment with type info if available
        const typeComment = field.schemaType || field.type;
        const requiredMarker = field.isRequired ? ' (required)' : '';
        const comment = typeComment ? `  // ${typeComment}${requiredMarker}` : '';

        lines.push(`${innerPad}${field.name}: ${value}${comma}${comment}`);
    }

    lines.push(`${pad}}`);
    return lines.join('\n');
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
            return '[???]';
        }
    }

    // Simple field - use placeholder
    return '???';
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
