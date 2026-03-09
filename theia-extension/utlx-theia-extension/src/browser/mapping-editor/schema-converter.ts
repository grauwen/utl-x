/**
 * Schema Converter
 *
 * Converts between existing schema types (UdmField, SchemaFieldInfo)
 * and the canvas SchemaField type used by React Flow nodes.
 */

import type { SchemaField } from './mapping-types';
import type { UdmField } from '../function-builder/udm-parser-new';
import type { SchemaFieldInfo } from '../utils/schema-field-tree-parser';

/**
 * Convert UdmField[] (from UDM parser) to SchemaField[] for canvas nodes.
 *
 * @param fields - UDM fields (from parseUdmToTree or SchemaFieldInfo[])
 * @param prefix - path prefix (e.g., "input" for input node handle IDs)
 */
export function udmFieldsToSchemaFields(
    fields: UdmField[],
    prefix: string
): SchemaField[] {
    return fields.map(field => convertField(field, prefix, ''));
}

/**
 * Convert SchemaFieldInfo[] (from schema parsers, extends UdmField) to SchemaField[].
 * Carries over additional schema metadata (isRequired, constraints, schemaType).
 */
export function schemaFieldInfoToSchemaFields(
    fields: SchemaFieldInfo[],
    prefix: string
): SchemaField[] {
    return fields.map(field => convertSchemaField(field, prefix, ''));
}

function convertField(
    field: UdmField,
    prefix: string,
    parentPath: string
): SchemaField {
    const fieldPath = parentPath ? `${parentPath}.${field.name}` : field.name;
    const handleId = `${prefix}.${fieldPath}`;

    const result: SchemaField = {
        id: handleId,
        name: field.name,
        path: fieldPath,
        type: normalizeType(field.type),
    };

    if (field.fields && field.fields.length > 0) {
        result.children = field.fields.map(child =>
            convertField(child, prefix, fieldPath)
        );
    }

    return result;
}

function convertSchemaField(
    field: SchemaFieldInfo,
    prefix: string,
    parentPath: string
): SchemaField {
    const fieldPath = parentPath ? `${parentPath}.${field.name}` : field.name;
    const handleId = `${prefix}.${fieldPath}`;

    const result: SchemaField = {
        id: handleId,
        name: field.name,
        path: fieldPath,
        type: normalizeType(field.type),
        isRequired: field.isRequired,
        schemaType: field.schemaType,
        constraints: field.constraints,
    };

    if (field.fields && field.fields.length > 0) {
        result.children = field.fields.map(child =>
            convertSchemaField(child as SchemaFieldInfo, prefix, fieldPath)
        );
    }

    return result;
}

/**
 * Parse a JSON instance string into SchemaField[] for the canvas output node.
 * Used as fallback when no output schema is available.
 */
export function jsonInstanceToSchemaFields(
    content: string,
    prefix: string
): SchemaField[] {
    try {
        const data = JSON.parse(content);
        return objectToSchemaFields(data, prefix, '');
    } catch {
        return [];
    }
}

/**
 * Parse an XML instance string into SchemaField[] for the canvas output node.
 * Used as fallback when no output schema is available.
 */
export function xmlInstanceToSchemaFields(
    content: string,
    prefix: string
): SchemaField[] {
    try {
        const parser = new DOMParser();
        const doc = parser.parseFromString(content, 'text/xml');
        const parseError = doc.querySelector('parsererror');
        if (parseError) return [];
        const root = doc.documentElement;
        if (!root) return [];
        return [xmlElementToSchemaField(root, prefix, '')];
    } catch {
        return [];
    }
}

function objectToSchemaFields(
    obj: unknown,
    prefix: string,
    parentPath: string
): SchemaField[] {
    if (obj === null || obj === undefined) return [];

    if (Array.isArray(obj)) {
        // Top-level array — analyze first element
        if (obj.length > 0 && typeof obj[0] === 'object' && obj[0] !== null) {
            const itemPath = parentPath ? `${parentPath}.[]` : '[]';
            const result: SchemaField = {
                id: `${prefix}.${itemPath}`,
                name: '[]',
                path: itemPath,
                type: 'array',
                children: objectToSchemaFields(obj[0], prefix, itemPath),
            };
            return [result];
        }
        return [{ id: `${prefix}.[]`, name: '[]', path: '[]', type: 'array' }];
    }

    if (typeof obj === 'object') {
        const fields: SchemaField[] = [];
        for (const [key, value] of Object.entries(obj as Record<string, unknown>)) {
            const fieldPath = parentPath ? `${parentPath}.${key}` : key;
            const handleId = `${prefix}.${fieldPath}`;

            const field: SchemaField = {
                id: handleId,
                name: key,
                path: fieldPath,
                type: inferJsType(value),
            };

            if (Array.isArray(value)) {
                field.type = 'array';
                if (value.length > 0 && typeof value[0] === 'object' && value[0] !== null) {
                    field.children = objectToSchemaFields(value[0], prefix, fieldPath);
                }
            } else if (typeof value === 'object' && value !== null) {
                field.type = 'object';
                field.children = objectToSchemaFields(value, prefix, fieldPath);
            }

            fields.push(field);
        }
        return fields;
    }

    return [];
}

function inferJsType(value: unknown): string {
    if (value === null) return 'null';
    if (Array.isArray(value)) return 'array';
    if (typeof value === 'boolean') return 'boolean';
    if (typeof value === 'number') return Number.isInteger(value) ? 'integer' : 'number';
    if (typeof value === 'string') return 'string';
    if (typeof value === 'object') return 'object';
    return 'any';
}

function xmlElementToSchemaField(
    element: Element,
    prefix: string,
    parentPath: string
): SchemaField {
    const fieldPath = parentPath ? `${parentPath}.${element.tagName}` : element.tagName;
    const handleId = `${prefix}.${fieldPath}`;

    const field: SchemaField = {
        id: handleId,
        name: element.tagName,
        path: fieldPath,
        type: 'string',
    };

    const children: SchemaField[] = [];

    // Attributes (skip xmlns/xsi)
    for (const attr of Array.from(element.attributes)) {
        if (attr.name === 'xmlns' || attr.name.startsWith('xmlns:') || attr.name.startsWith('xsi:')) continue;
        const attrPath = `${fieldPath}.@${attr.name}`;
        children.push({
            id: `${prefix}.${attrPath}`,
            name: `@${attr.name}`,
            path: attrPath,
            type: 'string',
        });
    }

    // Child elements
    const childElements = Array.from(element.children);
    const elementCounts = new Map<string, number>();
    for (const child of childElements) {
        elementCounts.set(child.tagName, (elementCounts.get(child.tagName) || 0) + 1);
    }

    const processedNames = new Set<string>();
    for (const child of childElements) {
        if (processedNames.has(child.tagName)) continue;
        processedNames.add(child.tagName);

        const childField = xmlElementToSchemaField(child, prefix, fieldPath);
        if ((elementCounts.get(child.tagName) || 0) > 1) {
            childField.type = 'array';
        }
        children.push(childField);
    }

    if (children.length > 0) {
        // Mixed content: element has attributes but no child elements, and has text
        if (childElements.length === 0) {
            const text = (element.textContent || '').trim();
            if (text) {
                const textPath = `${fieldPath}._text`;
                children.push({
                    id: `${prefix}.${textPath}`,
                    name: '_text',
                    path: textPath,
                    type: 'string',
                });
            }
        }
        field.children = children;
        field.type = 'object';
    }

    return field;
}

/**
 * Normalize type strings from various sources into canonical types.
 */
function normalizeType(type: string): string {
    switch (type.toLowerCase()) {
        case 'string':
        case 'xs:string':
        case 'text':
            return 'string';
        case 'number':
        case 'float':
        case 'double':
        case 'decimal':
        case 'xs:decimal':
        case 'xs:float':
        case 'xs:double':
            return 'number';
        case 'integer':
        case 'int':
        case 'long':
        case 'short':
        case 'xs:integer':
        case 'xs:int':
        case 'xs:long':
            return 'integer';
        case 'boolean':
        case 'bool':
        case 'xs:boolean':
            return 'boolean';
        case 'object':
        case 'complextype':
            return 'object';
        case 'array':
            return 'array';
        case 'date':
        case 'xs:date':
            return 'date';
        case 'datetime':
        case 'xs:datetime':
        case 'xs:datetimestamp':
            return 'datetime';
        case 'time':
        case 'xs:time':
            return 'time';
        case 'binary':
        case 'xs:base64binary':
        case 'xs:hexbinary':
            return 'binary';
        default:
            return type;
    }
}
