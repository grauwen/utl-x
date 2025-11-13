/**
 * UDM Parser
 *
 * Parses UDM (Universal Data Model) language format into a tree structure
 * suitable for display in the Function Builder's field tree.
 *
 * Supports both UDM formats:
 * 1. Full format: @Object(name: "X") { properties: { field: value } }
 * 2. Shorthand format: [{field: value}] or {field: value}
 */

/**
 * Represents a field in the UDM tree
 */
export interface UdmField {
    name: string;
    type: string;  // 'string', 'number', 'boolean', 'object', 'array', 'datetime', 'date', etc.
    description?: string;
    fields?: UdmField[];  // Nested fields for objects
    isOptional?: boolean;
}

/**
 * Represents a parsed UDM input tree
 */
export interface UdmInputTree {
    inputName: string;
    isArray: boolean;
    fields: UdmField[];
}

/**
 * Parse UDM language string into a tree structure
 */
export function parseUdmToTree(inputName: string, udmLanguage: string | undefined): UdmInputTree {
    if (!udmLanguage) {
        return {
            inputName,
            isArray: false,
            fields: []
        };
    }

    try {
        // Strip metadata lines
        let cleanedUdm = udmLanguage.trim();
        cleanedUdm = cleanedUdm.replace(/@udm-version:[^\n]*\n/g, '');
        cleanedUdm = cleanedUdm.replace(/@parsed-at:[^\n]*\n/g, '');
        cleanedUdm = cleanedUdm.replace(/@source:[^\n]*\n/g, '');
        cleanedUdm = cleanedUdm.trim();

        // Check if root is an array
        const isArray = cleanedUdm.startsWith('[');

        // Extract fields
        const fields = isArray
            ? parseArrayUdm(cleanedUdm)
            : parseObjectUdm(cleanedUdm);

        return {
            inputName,
            isArray,
            fields
        };
    } catch (error) {
        console.error('[UdmParser] Failed to parse UDM for', inputName, ':', error);
        return {
            inputName,
            isArray: false,
            fields: []
        };
    }
}

/**
 * Parse array UDM: [{field1: value, field2: value}]
 * Extract the structure from the first element
 */
function parseArrayUdm(udm: string): UdmField[] {
    // Extract first element
    const firstElementMatch = udm.match(/\[\s*\{([^}]+)\}/);
    if (!firstElementMatch) {
        console.warn('[UdmParser] Could not extract first array element from:', udm.substring(0, 100));
        return [];
    }

    // Parse the first element as an object
    const elementContent = `{${firstElementMatch[1]}}`;
    return parseObjectUdm(elementContent);
}

/**
 * Parse object UDM: {field1: value, field2: value} or @Object { properties: {...} }
 */
function parseObjectUdm(udm: string): UdmField[] {
    // Check if it's full format with properties section
    if (udm.includes('properties:')) {
        return parseFullFormatObject(udm);
    }

    // Shorthand format
    return parseShorthandObject(udm);
}

/**
 * Parse full format: @Object(name: "X") { properties: { field: value } }
 */
function parseFullFormatObject(udm: string): UdmField[] {
    // Find the properties section
    const propertiesMatch = udm.match(/properties:\s*\{([^}]*)\}/);
    if (!propertiesMatch) {
        console.warn('[UdmParser] Could not find properties section in:', udm.substring(0, 100));
        return [];
    }

    const propertiesContent = propertiesMatch[1];
    return parseFieldsFromContent(propertiesContent);
}

/**
 * Parse shorthand format: {field1: value, field2: value}
 */
function parseShorthandObject(udm: string): UdmField[] {
    // Remove outer braces
    let content = udm.trim();
    if (content.startsWith('{') && content.endsWith('}')) {
        content = content.substring(1, content.length - 1);
    }

    return parseFieldsFromContent(content);
}

/**
 * Parse fields from property content string
 * Handles: field: "value", field: 123, field: {...}, field: [...]
 */
function parseFieldsFromContent(content: string): UdmField[] {
    const fields: UdmField[] = [];

    // Pattern to match field definitions
    // Matches: fieldName: value (where value can be string, number, object, array)
    const fieldPattern = /(\w+):\s*(@\w+\([^)]*\)|@\w+|\{[^}]*\}|\[[^\]]*\]|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|[^,}\n]+)/g;

    let match;
    while ((match = fieldPattern.exec(content)) !== null) {
        const fieldName = match[1];
        const fieldValue = match[2].trim();

        const field = parseFieldValue(fieldName, fieldValue);
        if (field) {
            fields.push(field);
        }
    }

    return fields;
}

/**
 * Parse a single field value and determine its type
 */
function parseFieldValue(name: string, value: string): UdmField | null {
    // @DateTime annotation
    if (value.startsWith('@DateTime')) {
        return { name, type: 'datetime' };
    }

    // @Date annotation
    if (value.startsWith('@Date')) {
        return { name, type: 'date' };
    }

    // @LocalDateTime annotation
    if (value.startsWith('@LocalDateTime')) {
        return { name, type: 'localdatetime' };
    }

    // @Time annotation
    if (value.startsWith('@Time')) {
        return { name, type: 'time' };
    }

    // @Binary annotation
    if (value.startsWith('@Binary')) {
        return { name, type: 'binary' };
    }

    // @Object annotation - nested object
    if (value.startsWith('@Object')) {
        // Try to extract nested properties
        const nestedMatch = value.match(/@Object[^{]*\{([^}]*)\}/);
        if (nestedMatch) {
            const nestedFields = parseFieldsFromContent(nestedMatch[1]);
            return { name, type: 'object', fields: nestedFields };
        }
        return { name, type: 'object', fields: [] };
    }

    // Object literal: {...}
    if (value.startsWith('{')) {
        // Parse nested object
        const nestedContent = value.substring(1, value.lastIndexOf('}'));
        const nestedFields = parseFieldsFromContent(nestedContent);
        return { name, type: 'object', fields: nestedFields };
    }

    // Array literal: [...]
    if (value.startsWith('[')) {
        // Try to parse array element type
        const elementMatch = value.match(/\[\s*\{([^}]+)\}/);
        if (elementMatch) {
            const elementFields = parseFieldsFromContent(elementMatch[1]);
            return { name, type: 'array', fields: elementFields };
        }
        return { name, type: 'array', fields: [] };
    }

    // String literal: "..." or '...'
    if (value.startsWith('"') || value.startsWith("'")) {
        return { name, type: 'string' };
    }

    // Boolean: true or false
    if (value === 'true' || value === 'false') {
        return { name, type: 'boolean' };
    }

    // Null
    if (value === 'null') {
        return { name, type: 'null' };
    }

    // Number: 123, 45.67, -10, etc.
    if (/^-?\d+(\.\d+)?$/.test(value)) {
        return { name, type: 'number' };
    }

    // Default: treat as string
    return { name, type: 'string' };
}

/**
 * Get a readable type name for display
 */
export function getTypeDisplayName(type: string): string {
    switch (type) {
        case 'string': return 'String';
        case 'number': return 'Number';
        case 'boolean': return 'Boolean';
        case 'object': return 'Object';
        case 'array': return 'Array';
        case 'datetime': return 'DateTime';
        case 'date': return 'Date';
        case 'localdatetime': return 'LocalDateTime';
        case 'time': return 'Time';
        case 'binary': return 'Binary';
        case 'null': return 'Null';
        default: return type;
    }
}

/**
 * Get icon class for type (using Codicons)
 */
export function getTypeIcon(type: string): string {
    switch (type) {
        case 'string': return 'codicon-symbol-string';
        case 'number': return 'codicon-symbol-numeric';
        case 'boolean': return 'codicon-symbol-boolean';
        case 'object': return 'codicon-symbol-class';
        case 'array': return 'codicon-symbol-array';
        case 'datetime':
        case 'date':
        case 'localdatetime':
        case 'time':
            return 'codicon-calendar';
        case 'binary': return 'codicon-file-binary';
        case 'null': return 'codicon-circle-slash';
        default: return 'codicon-symbol-field';
    }
}
