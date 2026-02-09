/**
 * Schema Inferrer - Generates JSON Schema from instance data
 *
 * Analyzes JSON/XML instance documents and generates corresponding schemas.
 */

export interface InferredSchema {
    $schema: string;
    type?: string;
    properties?: Record<string, InferredSchema>;
    items?: InferredSchema;
    required?: string[];
    description?: string;
}

/**
 * Infer JSON Schema from a JSON instance document
 */
export function inferJsonSchema(instance: any, description?: string): InferredSchema {
    const schema: InferredSchema = {
        $schema: 'https://json-schema.org/draft/2020-12/schema'
    };

    if (description) {
        schema.description = description;
    }

    Object.assign(schema, inferType(instance));
    return schema;
}

/**
 * Infer type information from a value
 */
function inferType(value: any): Partial<InferredSchema> {
    if (value === null) {
        return { type: 'null' };
    }

    if (Array.isArray(value)) {
        return inferArrayType(value);
    }

    switch (typeof value) {
        case 'string':
            return inferStringType(value);
        case 'number':
            return inferNumberType(value);
        case 'boolean':
            return { type: 'boolean' };
        case 'object':
            return inferObjectType(value);
        default:
            return {};
    }
}

/**
 * Infer string type with format detection
 */
function inferStringType(value: string): Partial<InferredSchema> {
    const result: Partial<InferredSchema> = { type: 'string' };

    // Detect common formats
    if (isDateTimeString(value)) {
        (result as any).format = 'date-time';
    } else if (isDateString(value)) {
        (result as any).format = 'date';
    } else if (isEmailString(value)) {
        (result as any).format = 'email';
    } else if (isUriString(value)) {
        (result as any).format = 'uri';
    }

    return result;
}

/**
 * Infer number type (integer vs number)
 */
function inferNumberType(value: number): Partial<InferredSchema> {
    if (Number.isInteger(value)) {
        return { type: 'integer' };
    }
    return { type: 'number' };
}

/**
 * Infer object type with properties
 */
function inferObjectType(value: Record<string, any>): Partial<InferredSchema> {
    const properties: Record<string, InferredSchema> = {};
    const required: string[] = [];

    for (const [key, val] of Object.entries(value)) {
        properties[key] = inferType(val) as InferredSchema;
        // In instance-based inference, all present properties are considered required
        if (val !== null && val !== undefined) {
            required.push(key);
        }
    }

    const result: Partial<InferredSchema> = {
        type: 'object',
        properties
    };

    if (required.length > 0) {
        result.required = required;
    }

    return result;
}

/**
 * Infer array type with items schema
 */
function inferArrayType(value: any[]): Partial<InferredSchema> {
    if (value.length === 0) {
        return { type: 'array' };
    }

    // Merge schemas from all array items
    const itemSchemas = value.map(item => inferType(item));
    const mergedSchema = mergeSchemas(itemSchemas);

    return {
        type: 'array',
        items: mergedSchema as InferredSchema
    };
}

/**
 * Merge multiple schemas into one (for array items)
 */
function mergeSchemas(schemas: Partial<InferredSchema>[]): Partial<InferredSchema> {
    if (schemas.length === 0) {
        return {};
    }

    if (schemas.length === 1) {
        return schemas[0];
    }

    // Check if all schemas have the same type
    const types = new Set(schemas.map(s => s.type).filter(Boolean));

    if (types.size === 1) {
        const type = types.values().next().value;

        if (type === 'object') {
            // Merge object properties
            const allProperties: Record<string, Partial<InferredSchema>[]> = {};

            for (const schema of schemas) {
                if (schema.properties) {
                    for (const [key, propSchema] of Object.entries(schema.properties)) {
                        if (!allProperties[key]) {
                            allProperties[key] = [];
                        }
                        allProperties[key].push(propSchema);
                    }
                }
            }

            const mergedProperties: Record<string, InferredSchema> = {};
            for (const [key, propSchemas] of Object.entries(allProperties)) {
                mergedProperties[key] = mergeSchemas(propSchemas) as InferredSchema;
            }

            return {
                type: 'object',
                properties: mergedProperties
            };
        }

        return { type };
    }

    // Mixed types - use anyOf or just return generic
    if (types.size > 1) {
        return {
            type: Array.from(types) as any
        };
    }

    return {};
}

// Format detection helpers
function isDateTimeString(value: string): boolean {
    return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/.test(value);
}

function isDateString(value: string): boolean {
    return /^\d{4}-\d{2}-\d{2}$/.test(value);
}

function isEmailString(value: string): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

function isUriString(value: string): boolean {
    return /^https?:\/\//.test(value);
}

/**
 * Infer schema from JSON string
 */
export function inferSchemaFromJson(jsonString: string): InferredSchema {
    try {
        const parsed = JSON.parse(jsonString);
        return inferJsonSchema(parsed, 'Inferred from instance document');
    } catch (error) {
        throw new Error(`Failed to parse JSON: ${error instanceof Error ? error.message : String(error)}`);
    }
}

/**
 * Format schema as pretty-printed JSON
 */
export function formatSchema(schema: InferredSchema): string {
    return JSON.stringify(schema, null, 2);
}

// ============================================================================
// XSD (XML Schema) Inference
// ============================================================================

interface XmlElement {
    name: string;
    attributes: Record<string, string>;
    children: XmlElement[];
    textContent: string;
    isArray: boolean; // Multiple siblings with same name
}

interface XsdElementInfo {
    name: string;
    type: 'complex' | 'simple';
    attributes: Set<string>;
    children: Map<string, XsdElementInfo>;
    hasText: boolean;
    occurrences: number;
    isArray: boolean;
}

/**
 * Infer XSD schema from XML instance
 */
export function inferXsdFromXml(xmlString: string): string {
    try {
        // Parse XML using DOMParser
        const parser = new DOMParser();
        const doc = parser.parseFromString(xmlString, 'text/xml');

        // Check for parse errors
        const parseError = doc.querySelector('parsererror');
        if (parseError) {
            throw new Error('Invalid XML: ' + parseError.textContent);
        }

        // Get root element
        const root = doc.documentElement;
        if (!root) {
            throw new Error('No root element found');
        }

        // Analyze XML structure
        const elementInfo = analyzeXmlElement(root);

        // Generate XSD
        return generateXsd(elementInfo, root.namespaceURI);
    } catch (error) {
        throw new Error(`Failed to infer XSD: ${error instanceof Error ? error.message : String(error)}`);
    }
}

/**
 * Analyze an XML element and its children recursively
 */
function analyzeXmlElement(element: Element): XsdElementInfo {
    const info: XsdElementInfo = {
        name: element.localName || element.tagName,
        type: 'simple',
        attributes: new Set<string>(),
        children: new Map<string, XsdElementInfo>(),
        hasText: false,
        occurrences: 1,
        isArray: false
    };

    // Collect attributes (excluding xmlns declarations)
    for (let i = 0; i < element.attributes.length; i++) {
        const attr = element.attributes[i];
        if (!attr.name.startsWith('xmlns')) {
            info.attributes.add(attr.name);
        }
    }

    // Count children by name to detect arrays
    const childCounts = new Map<string, number>();
    for (let i = 0; i < element.children.length; i++) {
        const child = element.children[i];
        const childName = child.localName || child.tagName;
        childCounts.set(childName, (childCounts.get(childName) || 0) + 1);
    }

    // Analyze child elements
    for (let i = 0; i < element.children.length; i++) {
        const child = element.children[i];
        const childName = child.localName || child.tagName;

        if (!info.children.has(childName)) {
            const childInfo = analyzeXmlElement(child);
            const count = childCounts.get(childName) || 1;
            childInfo.isArray = count > 1;
            childInfo.occurrences = count;
            info.children.set(childName, childInfo);
        } else {
            // Merge with existing child info (for arrays)
            const existingInfo = info.children.get(childName)!;
            const childInfo = analyzeXmlElement(child);
            mergeElementInfo(existingInfo, childInfo);
        }
    }

    // Check for text content
    const textContent = getDirectTextContent(element).trim();
    if (textContent.length > 0) {
        info.hasText = true;
    }

    // Determine if complex type
    if (info.children.size > 0 || info.attributes.size > 0) {
        info.type = 'complex';
    }

    return info;
}

/**
 * Get direct text content (not from child elements)
 */
function getDirectTextContent(element: Element): string {
    let text = '';
    for (let i = 0; i < element.childNodes.length; i++) {
        const node = element.childNodes[i];
        if (node.nodeType === Node.TEXT_NODE) {
            text += node.textContent || '';
        }
    }
    return text;
}

/**
 * Merge element info from multiple occurrences
 */
function mergeElementInfo(target: XsdElementInfo, source: XsdElementInfo): void {
    // Merge attributes
    source.attributes.forEach(attr => target.attributes.add(attr));

    // Merge children
    source.children.forEach((childInfo, childName) => {
        if (target.children.has(childName)) {
            mergeElementInfo(target.children.get(childName)!, childInfo);
        } else {
            target.children.set(childName, childInfo);
        }
    });

    // Merge text content flag
    if (source.hasText) {
        target.hasText = true;
    }

    // Update type
    if (source.type === 'complex') {
        target.type = 'complex';
    }
}

/**
 * Generate XSD from analyzed element info
 */
function generateXsd(rootInfo: XsdElementInfo, namespace?: string | null): string {
    const lines: string[] = [];

    // XSD header
    lines.push('<?xml version="1.0" encoding="UTF-8"?>');
    lines.push('<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"');
    if (namespace) {
        lines.push(`    targetNamespace="${namespace}"`);
        lines.push(`    xmlns:tns="${namespace}"`);
    }
    lines.push('    elementFormDefault="qualified">');
    lines.push('');

    // Root element
    lines.push(`  <xs:element name="${rootInfo.name}" type="${rootInfo.name}Type"/>`);
    lines.push('');

    // Generate types recursively
    const generatedTypes = new Set<string>();
    generateXsdType(rootInfo, lines, generatedTypes, '  ');

    lines.push('</xs:schema>');

    return lines.join('\n');
}

/**
 * Generate XSD complex type definition
 */
function generateXsdType(
    info: XsdElementInfo,
    lines: string[],
    generatedTypes: Set<string>,
    indent: string
): void {
    const typeName = `${info.name}Type`;

    if (generatedTypes.has(typeName)) {
        return;
    }
    generatedTypes.add(typeName);

    if (info.type === 'simple' && !info.hasText) {
        // Empty element
        lines.push(`${indent}<xs:complexType name="${typeName}"/>`);
    } else if (info.type === 'simple' && info.hasText && info.attributes.size === 0) {
        // Simple text-only element - use xs:string
        lines.push(`${indent}<xs:simpleType name="${typeName}">`);
        lines.push(`${indent}  <xs:restriction base="xs:string"/>`);
        lines.push(`${indent}</xs:simpleType>`);
    } else {
        // Complex type
        lines.push(`${indent}<xs:complexType name="${typeName}">`);

        if (info.children.size > 0 || info.hasText) {
            if (info.hasText && info.children.size > 0) {
                // Mixed content
                lines.push(`${indent}  <xs:complexContent mixed="true">`);
                lines.push(`${indent}    <xs:sequence>`);
            } else if (info.children.size > 0) {
                lines.push(`${indent}  <xs:sequence>`);
            }

            // Child elements
            info.children.forEach((childInfo, childName) => {
                const childTypeName = `${childName}Type`;
                const minOccurs = '0'; // Be lenient - all optional
                const maxOccurs = childInfo.isArray ? 'unbounded' : '1';

                if (childInfo.type === 'simple' && !childInfo.attributes.size && childInfo.hasText) {
                    // Simple text element - inline xs:string
                    lines.push(`${indent}    <xs:element name="${childName}" type="xs:string" minOccurs="${minOccurs}" maxOccurs="${maxOccurs}"/>`);
                } else {
                    lines.push(`${indent}    <xs:element name="${childName}" type="${childTypeName}" minOccurs="${minOccurs}" maxOccurs="${maxOccurs}"/>`);
                }
            });

            if (info.hasText && info.children.size > 0) {
                lines.push(`${indent}    </xs:sequence>`);
                lines.push(`${indent}  </xs:complexContent>`);
            } else if (info.children.size > 0) {
                lines.push(`${indent}  </xs:sequence>`);
            } else if (info.hasText) {
                // Text-only with attributes - use simpleContent
                lines.push(`${indent}  <xs:simpleContent>`);
                lines.push(`${indent}    <xs:extension base="xs:string">`);
            }
        }

        // Attributes
        if (info.attributes.size > 0) {
            if (info.hasText && info.children.size === 0) {
                // Already inside simpleContent/extension
            }
            info.attributes.forEach(attrName => {
                const attrIndent = (info.hasText && info.children.size === 0) ? `${indent}      ` : `${indent}  `;
                lines.push(`${attrIndent}<xs:attribute name="${attrName}" type="xs:string"/>`);
            });
            if (info.hasText && info.children.size === 0) {
                lines.push(`${indent}    </xs:extension>`);
                lines.push(`${indent}  </xs:simpleContent>`);
            }
        }

        lines.push(`${indent}</xs:complexType>`);
    }

    lines.push('');

    // Generate child types
    info.children.forEach((childInfo, childName) => {
        if (childInfo.type === 'complex' || childInfo.attributes.size > 0) {
            generateXsdType(childInfo, lines, generatedTypes, indent);
        }
    });
}

/**
 * Infer schema from XML string - returns XSD
 */
export function inferSchemaFromXml(xmlString: string): string {
    return inferXsdFromXml(xmlString);
}
