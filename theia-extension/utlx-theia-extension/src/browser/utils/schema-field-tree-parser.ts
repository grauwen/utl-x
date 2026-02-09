/**
 * Schema Field Tree Parser
 *
 * Parses JSON Schema and XSD documents directly into field trees
 * for use in the Function Builder when in Design-Time mode.
 *
 * This provides a direct Schema → Field Tree path without going through
 * sample generation or UDM conversion, preserving schema metadata like
 * types, constraints, and required/optional indicators.
 */

import { UdmField } from '../function-builder/udm-parser-new';

/**
 * Extended field info with schema-specific metadata
 */
export interface SchemaFieldInfo extends UdmField {
    isRequired?: boolean;
    constraints?: string;  // e.g., "maxLength: 100", "min: 0"
    schemaType?: string;   // Original schema type (xs:string, integer, etc.)
}

// ============================================================================
// JSON Schema Parser
// ============================================================================

/**
 * Parse JSON Schema directly to field tree
 */
export function parseJsonSchemaToFieldTree(schemaContent: string): SchemaFieldInfo[] {
    try {
        const schema = JSON.parse(schemaContent);
        return parseJsonSchemaRoot(schema);
    } catch (error) {
        console.error('[SchemaParser] Failed to parse JSON Schema:', error);
        return [];
    }
}

/**
 * Parse the root of a JSON Schema
 */
function parseJsonSchemaRoot(schema: any): SchemaFieldInfo[] {
    // Handle root level type
    if (schema.type === 'object' && schema.properties) {
        return parseJsonSchemaObject(schema, schema.required || []);
    } else if (schema.type === 'array' && schema.items) {
        // Root is an array - show item structure
        const itemFields = parseJsonSchemaProperty('[]', schema.items, false);
        return itemFields.fields || [];
    } else if (schema.properties) {
        // No explicit type but has properties - treat as object
        return parseJsonSchemaObject(schema, schema.required || []);
    }

    return [];
}

/**
 * Parse JSON Schema object properties
 */
function parseJsonSchemaObject(schema: any, requiredFields: string[]): SchemaFieldInfo[] {
    const fields: SchemaFieldInfo[] = [];

    if (schema.properties) {
        for (const [name, prop] of Object.entries(schema.properties)) {
            fields.push(parseJsonSchemaProperty(name, prop as any, requiredFields.includes(name)));
        }
    }

    // Handle additionalProperties if it's a schema
    if (schema.additionalProperties && typeof schema.additionalProperties === 'object') {
        fields.push({
            name: '[additionalProperties]',
            type: (schema.additionalProperties as any).type || 'any',
            isRequired: false,
            schemaType: (schema.additionalProperties as any).type,
            description: 'Additional properties allowed'
        });
    }

    return fields;
}

/**
 * Parse a single JSON Schema property
 */
function parseJsonSchemaProperty(name: string, prop: any, isRequired: boolean): SchemaFieldInfo {
    const field: SchemaFieldInfo = {
        name,
        type: resolveJsonSchemaType(prop),
        isRequired,
        schemaType: prop.type,
    };

    // Add description if present
    if (prop.description) {
        field.description = prop.description;
    }

    // Extract constraints
    const constraints = extractJsonSchemaConstraints(prop);
    if (constraints) {
        field.constraints = constraints;
    }

    // Handle nested objects
    if (prop.type === 'object' && prop.properties) {
        field.fields = parseJsonSchemaObject(prop, prop.required || []);
    }

    // Handle arrays
    if (prop.type === 'array' && prop.items) {
        if (Array.isArray(prop.items)) {
            // Tuple validation - multiple item schemas
            field.fields = prop.items.map((item: any, idx: number) =>
                parseJsonSchemaProperty(`[${idx}]`, item, false)
            );
        } else {
            // Single item schema
            const itemField = parseJsonSchemaProperty('[]', prop.items, false);
            field.fields = itemField.fields ? itemField.fields : [itemField];
        }
    }

    // Handle $ref (simplified - just show reference)
    if (prop.$ref) {
        field.schemaType = `$ref: ${prop.$ref}`;
        field.type = 'reference';
    }

    // Handle oneOf, anyOf, allOf
    if (prop.oneOf) {
        field.type = 'oneOf';
        field.schemaType = `oneOf[${prop.oneOf.length}]`;
        field.fields = prop.oneOf.map((subSchema: any, idx: number) =>
            parseJsonSchemaProperty(`option${idx + 1}`, subSchema, false)
        );
    }
    if (prop.anyOf) {
        field.type = 'anyOf';
        field.schemaType = `anyOf[${prop.anyOf.length}]`;
    }
    if (prop.allOf) {
        field.type = 'allOf';
        field.schemaType = `allOf[${prop.allOf.length}]`;
    }

    return field;
}

/**
 * Resolve the effective type from a JSON Schema property
 */
function resolveJsonSchemaType(prop: any): string {
    if (prop.type) {
        if (Array.isArray(prop.type)) {
            return prop.type.join(' | ');
        }
        return prop.type;
    }
    if (prop.$ref) return 'reference';
    if (prop.oneOf) return 'oneOf';
    if (prop.anyOf) return 'anyOf';
    if (prop.allOf) return 'allOf';
    if (prop.enum) return 'enum';
    if (prop.const !== undefined) return 'const';
    return 'any';
}

/**
 * Extract constraint descriptions from JSON Schema property
 */
function extractJsonSchemaConstraints(prop: any): string | undefined {
    const constraints: string[] = [];

    // String constraints
    if (prop.minLength !== undefined) constraints.push(`minLength: ${prop.minLength}`);
    if (prop.maxLength !== undefined) constraints.push(`maxLength: ${prop.maxLength}`);
    if (prop.pattern) constraints.push(`pattern: ${prop.pattern}`);
    if (prop.format) constraints.push(`format: ${prop.format}`);

    // Number constraints
    if (prop.minimum !== undefined) constraints.push(`min: ${prop.minimum}`);
    if (prop.maximum !== undefined) constraints.push(`max: ${prop.maximum}`);
    if (prop.exclusiveMinimum !== undefined) constraints.push(`exclusiveMin: ${prop.exclusiveMinimum}`);
    if (prop.exclusiveMaximum !== undefined) constraints.push(`exclusiveMax: ${prop.exclusiveMaximum}`);
    if (prop.multipleOf !== undefined) constraints.push(`multipleOf: ${prop.multipleOf}`);

    // Array constraints
    if (prop.minItems !== undefined) constraints.push(`minItems: ${prop.minItems}`);
    if (prop.maxItems !== undefined) constraints.push(`maxItems: ${prop.maxItems}`);
    if (prop.uniqueItems) constraints.push('uniqueItems');

    // Object constraints
    if (prop.minProperties !== undefined) constraints.push(`minProps: ${prop.minProperties}`);
    if (prop.maxProperties !== undefined) constraints.push(`maxProps: ${prop.maxProperties}`);

    // Enum
    if (prop.enum) {
        const enumStr = prop.enum.length <= 5
            ? prop.enum.map((v: any) => JSON.stringify(v)).join(', ')
            : `${prop.enum.slice(0, 3).map((v: any) => JSON.stringify(v)).join(', ')}... (${prop.enum.length} values)`;
        constraints.push(`enum: [${enumStr}]`);
    }

    // Const
    if (prop.const !== undefined) {
        constraints.push(`const: ${JSON.stringify(prop.const)}`);
    }

    // Default
    if (prop.default !== undefined) {
        constraints.push(`default: ${JSON.stringify(prop.default)}`);
    }

    return constraints.length > 0 ? constraints.join(', ') : undefined;
}

// ============================================================================
// XSD (XML Schema) Parser
// ============================================================================

/**
 * Parse XSD directly to field tree
 */
export function parseXsdToFieldTree(xsdContent: string): SchemaFieldInfo[] {
    try {
        const parser = new DOMParser();
        const doc = parser.parseFromString(xsdContent, 'text/xml');

        // Check for parse errors
        const parseError = doc.querySelector('parsererror');
        if (parseError) {
            console.error('[SchemaParser] XSD parse error:', parseError.textContent);
            return [];
        }

        // Find root element definition(s)
        const rootElements = doc.querySelectorAll(':scope > element');
        if (rootElements.length === 0) {
            // Try with namespace prefix
            const nsRootElements = doc.querySelectorAll('xs\\:element, xsd\\:element');
            if (nsRootElements.length > 0) {
                return Array.from(nsRootElements)
                    .filter(el => el.parentElement === doc.documentElement)
                    .map(el => parseXsdElement(el, doc))
                    .filter((f): f is SchemaFieldInfo => f !== null);
            }
            console.warn('[SchemaParser] No root elements found in XSD');
            return [];
        }

        return Array.from(rootElements)
            .map(el => parseXsdElement(el, doc))
            .filter((f): f is SchemaFieldInfo => f !== null);
    } catch (error) {
        console.error('[SchemaParser] Failed to parse XSD:', error);
        return [];
    }
}

/**
 * Parse an XSD element definition
 */
function parseXsdElement(element: Element, doc: Document): SchemaFieldInfo | null {
    const name = element.getAttribute('name');
    if (!name) return null;

    const field: SchemaFieldInfo = {
        name,
        type: 'element',
        schemaType: element.getAttribute('type') || undefined,
    };

    // Check minOccurs/maxOccurs for required/array
    const minOccurs = element.getAttribute('minOccurs');
    const maxOccurs = element.getAttribute('maxOccurs');

    field.isRequired = minOccurs !== '0';

    if (maxOccurs === 'unbounded' || (maxOccurs && parseInt(maxOccurs) > 1)) {
        field.type = 'array';
    }

    // Extract constraints
    const constraints: string[] = [];
    if (minOccurs) constraints.push(`minOccurs: ${minOccurs}`);
    if (maxOccurs) constraints.push(`maxOccurs: ${maxOccurs}`);
    if (constraints.length > 0) {
        field.constraints = constraints.join(', ');
    }

    // Check for inline complexType
    const complexType = element.querySelector(':scope > complexType') ||
                       element.querySelector(':scope > xs\\:complexType, :scope > xsd\\:complexType');

    if (complexType) {
        field.fields = parseXsdComplexType(complexType, doc);
        field.type = field.type === 'array' ? 'array' : 'object';
    } else if (field.schemaType) {
        // Referenced type - try to resolve it
        const typeFields = resolveXsdType(field.schemaType, doc);
        if (typeFields.length > 0) {
            field.fields = typeFields;
            field.type = field.type === 'array' ? 'array' : 'object';
        } else {
            // Simple type
            field.type = mapXsdTypeToFieldType(field.schemaType);
        }
    }

    return field;
}

/**
 * Parse an XSD complexType definition
 */
function parseXsdComplexType(complexType: Element, doc: Document): SchemaFieldInfo[] {
    const fields: SchemaFieldInfo[] = [];

    // Handle sequence, choice, or all
    const sequence = complexType.querySelector('sequence, xs\\:sequence, xsd\\:sequence');
    const choice = complexType.querySelector('choice, xs\\:choice, xsd\\:choice');
    const all = complexType.querySelector('all, xs\\:all, xsd\\:all');

    const container = sequence || choice || all;

    if (container) {
        // Get child elements
        const elements = container.querySelectorAll(':scope > element, :scope > xs\\:element, :scope > xsd\\:element');
        for (const el of Array.from(elements)) {
            const field = parseXsdElement(el, doc);
            if (field) {
                fields.push(field);
            }
        }
    }

    // Handle attributes
    const attributes = complexType.querySelectorAll('attribute, xs\\:attribute, xsd\\:attribute');
    for (const attr of Array.from(attributes)) {
        const attrName = attr.getAttribute('name');
        if (attrName) {
            const attrField: SchemaFieldInfo = {
                name: `@${attrName}`,
                type: mapXsdTypeToFieldType(attr.getAttribute('type') || 'xs:string'),
                schemaType: attr.getAttribute('type') || 'xs:string',
                isRequired: attr.getAttribute('use') === 'required',
            };
            fields.push(attrField);
        }
    }

    // Handle simpleContent extension
    const simpleContent = complexType.querySelector('simpleContent, xs\\:simpleContent, xsd\\:simpleContent');
    if (simpleContent) {
        const extension = simpleContent.querySelector('extension, xs\\:extension, xsd\\:extension');
        if (extension) {
            const baseType = extension.getAttribute('base');
            if (baseType) {
                fields.unshift({
                    name: '_text',
                    type: mapXsdTypeToFieldType(baseType),
                    schemaType: baseType,
                    description: 'Text content'
                });
            }
            // Get attributes from extension
            const extAttrs = extension.querySelectorAll('attribute, xs\\:attribute, xsd\\:attribute');
            for (const attr of Array.from(extAttrs)) {
                const attrName = attr.getAttribute('name');
                if (attrName) {
                    fields.push({
                        name: `@${attrName}`,
                        type: mapXsdTypeToFieldType(attr.getAttribute('type') || 'xs:string'),
                        schemaType: attr.getAttribute('type') || 'xs:string',
                        isRequired: attr.getAttribute('use') === 'required',
                    });
                }
            }
        }
    }

    return fields;
}

/**
 * Resolve a named XSD type
 */
function resolveXsdType(typeName: string, doc: Document): SchemaFieldInfo[] {
    // Strip namespace prefix for lookup
    const localName = typeName.includes(':') ? typeName.split(':')[1] : typeName;

    // Look for complexType definition
    const complexTypes = doc.querySelectorAll('complexType, xs\\:complexType, xsd\\:complexType');
    for (const ct of Array.from(complexTypes)) {
        if (ct.getAttribute('name') === localName) {
            return parseXsdComplexType(ct, doc);
        }
    }

    // Look for simpleType definition (no nested fields)
    return [];
}

/**
 * Map XSD built-in types to field types
 */
function mapXsdTypeToFieldType(xsdType: string): string {
    // Strip namespace prefix
    const localType = xsdType.includes(':') ? xsdType.split(':')[1] : xsdType;

    switch (localType.toLowerCase()) {
        case 'string':
        case 'normalizedstring':
        case 'token':
        case 'nmtoken':
        case 'name':
        case 'ncname':
        case 'id':
        case 'idref':
        case 'language':
        case 'anyuri':
            return 'string';

        case 'integer':
        case 'int':
        case 'long':
        case 'short':
        case 'byte':
        case 'nonpositiveinteger':
        case 'negativeinteger':
        case 'nonnegativeinteger':
        case 'positiveinteger':
        case 'unsignedlong':
        case 'unsignedint':
        case 'unsignedshort':
        case 'unsignedbyte':
            return 'integer';

        case 'decimal':
        case 'float':
        case 'double':
            return 'number';

        case 'boolean':
            return 'boolean';

        case 'date':
            return 'date';

        case 'time':
            return 'time';

        case 'datetime':
            return 'datetime';

        case 'duration':
        case 'gyear':
        case 'gyearmonth':
        case 'gmonth':
        case 'gmonthday':
        case 'gday':
            return 'string';  // Treat as string for display

        case 'base64binary':
        case 'hexbinary':
            return 'binary';

        default:
            return localType;  // Return as-is for custom types
    }
}

// ============================================================================
// Type Display Utilities
// ============================================================================

/**
 * Get a display-friendly type name for schema types
 */
export function getSchemaTypeDisplayName(type: string, schemaType?: string): string {
    if (schemaType) {
        // Show original schema type for reference
        const localType = schemaType.includes(':') ? schemaType.split(':')[1] : schemaType;
        return localType;
    }
    return type;
}

/**
 * Get CSS class for required field indicator
 */
export function getRequiredIndicatorClass(isRequired?: boolean): string {
    return isRequired ? 'schema-field-required' : 'schema-field-optional';
}

// ============================================================================
// Merged Field Tree (Schema + Instance Samples)
// ============================================================================

/**
 * Extended field info with both schema metadata AND sample values from instance
 */
export interface MergedFieldInfo extends SchemaFieldInfo {
    sampleValues?: string[];  // Sample values from instance data (up to 3)
    hasSampleData?: boolean;  // True if instance data exists for this field
}

/**
 * Merge schema field tree with sample values extracted from UDM.
 * Schema provides the structure; UDM provides sample values.
 *
 * @param schemaFields - Field tree from schema parsing
 * @param udmLanguage - UDM language string from instance data
 * @returns Merged field tree with schema info + sample values
 */
export function mergeSchemaWithSamples(
    schemaFields: SchemaFieldInfo[],
    udmLanguage: string | undefined
): MergedFieldInfo[] {
    if (!udmLanguage || udmLanguage.trim().length === 0) {
        // No instance data - return schema fields as-is (no samples)
        return schemaFields.map(f => ({ ...f, hasSampleData: false }));
    }

    // Lazy import to avoid circular dependencies
    const { UDMLanguageParser } = require('../udm/udm-language-parser');
    const { navigate } = require('../udm/udm-navigator');
    const { isScalar, isArray, isObject, UDMObjectHelper } = require('../udm/udm-core');

    try {
        const udm = UDMLanguageParser.parse(udmLanguage);

        return schemaFields.map(field => mergeFieldWithSamples(field, udm, '', {
            navigate, isScalar, isArray, isObject, UDMObjectHelper
        }));
    } catch (error) {
        console.error('[SchemaParser] Failed to parse UDM for sample extraction:', error);
        return schemaFields.map(f => ({ ...f, hasSampleData: false }));
    }
}

/**
 * Recursively merge a single field with sample values from UDM
 */
function mergeFieldWithSamples(
    field: SchemaFieldInfo,
    udm: any,
    parentPath: string,
    utils: { navigate: any; isScalar: any; isArray: any; isObject: any; UDMObjectHelper: any }
): MergedFieldInfo {
    const { navigate, isScalar, isArray, isObject, UDMObjectHelper } = utils;

    const fieldPath = parentPath ? `${parentPath}.${field.name}` : field.name;
    const merged: MergedFieldInfo = {
        ...field,
        hasSampleData: false,
        sampleValues: []
    };

    try {
        // Navigate to this field in UDM
        const node = navigate(udm, fieldPath);

        if (node && typeof node !== 'string') {
            merged.hasSampleData = true;

            if (isScalar(node)) {
                // Simple value
                merged.sampleValues = [String(node.value)];
            } else if (isArray(node)) {
                // Array - extract first few element values
                const arr = node as any;
                const samples: string[] = [];
                for (let i = 0; i < Math.min(3, arr.elements.length); i++) {
                    const elem = arr.elements[i];
                    if (isScalar(elem)) {
                        samples.push(String(elem.value));
                    } else if (isObject(elem)) {
                        samples.push(`{...}`); // Object in array
                    }
                }
                merged.sampleValues = samples;
            } else if (isObject(node)) {
                // Object - mark as having data but no direct sample value
                merged.sampleValues = ['{...}'];
            }
        }
    } catch (e) {
        // Field not found in UDM - that's OK, just no samples
    }

    // Recursively process nested fields
    if (field.fields && field.fields.length > 0) {
        merged.fields = field.fields.map(childField =>
            mergeFieldWithSamples(childField as SchemaFieldInfo, udm, fieldPath, utils)
        );
    }

    return merged;
}
