/**
 * Scaffold Generator
 *
 * Generates UTLX code scaffolds from output schema/instance structure.
 * Produces code with `???` placeholders that users fill in manually.
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
        let code: string;

        if (format === 'xml') {
            code = generateXmlScaffold(fields, 0);
        } else {
            // Default to JSON-like syntax
            code = generateJsonScaffold(fields, 0);
        }

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

// ============================================================================
// JSON Scaffold Generation
// ============================================================================

/**
 * Generate JSON-style UTLX scaffold
 */
function generateJsonScaffold(fields: SchemaFieldInfo[], indent: number): string {
    const pad = '  '.repeat(indent);
    const innerPad = '  '.repeat(indent + 1);

    // Check if this is a root array (single field that is an array)
    if (fields.length === 1 && fields[0].type === 'array') {
        const arrayField = fields[0];
        if (arrayField.fields && arrayField.fields.length > 0) {
            // Array of objects
            const objectContent = generateJsonObject(arrayField.fields as SchemaFieldInfo[], indent + 1);
            return `[\n${innerPad}${objectContent}\n${pad}]`;
        } else {
            // Array of primitives
            return `[\n${innerPad}???\n${pad}]`;
        }
    }

    // Regular object
    return generateJsonObject(fields, indent);
}

/**
 * Generate a JSON object structure
 */
function generateJsonObject(fields: SchemaFieldInfo[], indent: number): string {
    const pad = '  '.repeat(indent);
    const innerPad = '  '.repeat(indent + 1);
    const lines: string[] = ['{'];

    for (let i = 0; i < fields.length; i++) {
        const field = fields[i];
        const isLast = i === fields.length - 1;
        const value = generateJsonFieldValue(field, indent + 1);
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
 * Generate value for a single JSON field
 */
function generateJsonFieldValue(field: SchemaFieldInfo, indent: number): string {
    // Nested object
    if (field.type === 'object' && field.fields && field.fields.length > 0) {
        return generateJsonObject(field.fields as SchemaFieldInfo[], indent);
    }

    // Array
    if (field.type === 'array') {
        if (field.fields && field.fields.length > 0) {
            // Array of objects - show single object template
            const objectContent = generateJsonObject(field.fields as SchemaFieldInfo[], indent + 1);
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
// XML Scaffold Generation
// ============================================================================

/**
 * Generate XML-style UTLX scaffold
 */
function generateXmlScaffold(fields: SchemaFieldInfo[], indent: number): string {
    const lines: string[] = [];

    for (const field of fields) {
        lines.push(generateXmlElement(field, indent));
    }

    return lines.join('\n');
}

/**
 * Generate a single XML element
 */
function generateXmlElement(field: SchemaFieldInfo, indent: number): string {
    const pad = '  '.repeat(indent);
    const name = field.name;

    // Handle attributes (fields starting with @)
    if (name.startsWith('@')) {
        // Attributes are handled within their parent element
        return '';
    }

    // Collect attributes from child fields
    const attributes = (field.fields || [])
        .filter(f => f.name.startsWith('@'))
        .map(f => `${f.name.substring(1)}="???"`)
        .join(' ');

    const attrStr = attributes ? ` ${attributes}` : '';

    // Check for nested elements
    const childElements = (field.fields || []).filter(f => !f.name.startsWith('@'));

    if (childElements.length > 0) {
        // Element with children
        const children = childElements
            .map(child => generateXmlElement(child as SchemaFieldInfo, indent + 1))
            .filter(s => s.length > 0)
            .join('\n');

        return `${pad}<${name}${attrStr}>\n${children}\n${pad}</${name}>`;
    } else if (field.type === 'array') {
        // Array element - show template for iteration
        const itemName = name.endsWith('s') ? name.slice(0, -1) : 'item';
        return `${pad}<${name}${attrStr}>\n${pad}  <${itemName}>???</${itemName}>\n${pad}</${name}>`;
    } else {
        // Simple element with placeholder value
        return `${pad}<${name}${attrStr}>???</${name}>`;
    }
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
