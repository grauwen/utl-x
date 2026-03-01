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

// ============================================================================
// Table Schema (tsch) Inference from CSV
// ============================================================================

/**
 * Parse CSV content into rows of string values.
 * Handles quoted fields, embedded commas, and embedded newlines.
 */
function parseCsvRows(csvString: string): string[][] {
    const rows: string[][] = [];
    let current = '';
    let inQuotes = false;
    let row: string[] = [];

    for (let i = 0; i < csvString.length; i++) {
        const ch = csvString[i];
        const next = csvString[i + 1];

        if (inQuotes) {
            if (ch === '"' && next === '"') {
                current += '"';
                i++; // skip escaped quote
            } else if (ch === '"') {
                inQuotes = false;
            } else {
                current += ch;
            }
        } else {
            if (ch === '"') {
                inQuotes = true;
            } else if (ch === ',') {
                row.push(current);
                current = '';
            } else if (ch === '\r' && next === '\n') {
                row.push(current);
                current = '';
                rows.push(row);
                row = [];
                i++; // skip \n
            } else if (ch === '\n') {
                row.push(current);
                current = '';
                rows.push(row);
                row = [];
            } else {
                current += ch;
            }
        }
    }

    // Push last field/row
    if (current.length > 0 || row.length > 0) {
        row.push(current);
        rows.push(row);
    }

    return rows;
}

/**
 * Infer a Table Schema field type from sample values.
 * Checks all non-empty sample values to determine the most specific type.
 */
function inferTschFieldType(values: string[]): string {
    const nonEmpty = values.filter(v => v.trim().length > 0);
    if (nonEmpty.length === 0) {
        return 'string';
    }

    // Check if all values match a specific type
    const allMatch = (testFn: (v: string) => boolean) => nonEmpty.every(testFn);

    if (allMatch(isBooleanValue)) return 'boolean';
    if (allMatch(isIntegerValue)) return 'integer';
    if (allMatch(isNumberValue)) return 'number';
    if (allMatch(isDateTimeValue)) return 'datetime';
    if (allMatch(isDateValue)) return 'date';
    if (allMatch(isTimeValue)) return 'time';
    if (allMatch(isYearMonthValue)) return 'yearmonth';
    if (allMatch(isYearValue)) return 'year';

    return 'string';
}

function isBooleanValue(v: string): boolean {
    const lower = v.trim().toLowerCase();
    return ['true', 'false', 'yes', 'no', '1', '0'].includes(lower);
}

function isIntegerValue(v: string): boolean {
    return /^-?\d+$/.test(v.trim());
}

function isNumberValue(v: string): boolean {
    return /^-?\d+(\.\d+)?([eE][+-]?\d+)?$/.test(v.trim());
}

function isDateValue(v: string): boolean {
    return /^\d{4}-\d{2}-\d{2}$/.test(v.trim());
}

function isTimeValue(v: string): boolean {
    return /^\d{2}:\d{2}(:\d{2})?$/.test(v.trim());
}

function isDateTimeValue(v: string): boolean {
    return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/.test(v.trim());
}

function isYearValue(v: string): boolean {
    return /^\d{4}$/.test(v.trim());
}

function isYearMonthValue(v: string): boolean {
    return /^\d{4}-\d{2}$/.test(v.trim());
}

/**
 * Generate a human-readable title from a field name.
 * Converts snake_case and camelCase to Title Case.
 */
function fieldNameToTitle(name: string): string {
    return name
        .replace(/([a-z])([A-Z])/g, '$1 $2')  // camelCase → camel Case
        .replace(/[_-]/g, ' ')                   // snake_case → snake case
        .replace(/\b\w/g, c => c.toUpperCase());  // capitalize words
}

/**
 * Check if a column has any empty values (to determine required constraint).
 */
function hasEmptyValues(values: string[]): boolean {
    return values.some(v => v.trim().length === 0);
}

/**
 * Infer a Frictionless Table Schema from CSV content.
 * Parses headers and samples data rows to determine field types and constraints.
 *
 * @param csvString CSV content (must have a header row)
 * @returns Table Schema JSON string
 */
export function inferTableSchemaFromCsv(csvString: string): string {
    const rows = parseCsvRows(csvString);
    if (rows.length === 0) {
        throw new Error('CSV is empty');
    }

    const headers = rows[0];
    if (headers.length === 0 || (headers.length === 1 && headers[0].trim() === '')) {
        throw new Error('CSV has no header columns');
    }

    const dataRows = rows.slice(1).filter(row => row.some(cell => cell.trim().length > 0));

    // Collect column values for type inference
    const columnValues: string[][] = headers.map(() => []);
    for (const row of dataRows) {
        for (let i = 0; i < headers.length; i++) {
            columnValues[i].push(i < row.length ? row[i] : '');
        }
    }

    // Build fields
    const fields = headers.map((header, i) => {
        const name = header.trim();
        const type = inferTschFieldType(columnValues[i]);
        const title = fieldNameToTitle(name);

        const field: any = {
            name,
            title,
            type
        };

        // Add required constraint if all values are present
        if (dataRows.length > 0 && !hasEmptyValues(columnValues[i])) {
            field.constraints = { required: true };
        }

        return field;
    });

    const schema: any = { fields };

    return JSON.stringify(schema, null, 2);
}

// ============================================================================
// OData EDMX/CSDL Inference from OData JSON
// ============================================================================

/**
 * Map a JSON value to an Edm type string
 */
function inferEdmType(value: any): string {
    if (value === null || value === undefined) {
        return 'Edm.String';
    }
    if (typeof value === 'boolean') {
        return 'Edm.Boolean';
    }
    if (typeof value === 'number') {
        return Number.isInteger(value) ? 'Edm.Int32' : 'Edm.Double';
    }
    if (typeof value === 'string') {
        if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value)) return 'Edm.DateTimeOffset';
        if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return 'Edm.Date';
        if (/^\d{2}:\d{2}(:\d{2})?/.test(value)) return 'Edm.TimeOfDay';
        if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)) return 'Edm.Guid';
        return 'Edm.String';
    }
    return 'Edm.String';
}

/**
 * Escape XML special characters in attribute values
 */
function escapeXmlAttr(value: string): string {
    return value.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

interface EdmxPropertyInfo {
    name: string;
    type: string;
    nullable: boolean;
    isNavigation: boolean;
    isCollection: boolean;
    complexTypeName?: string;
}

interface EdmxTypeInfo {
    name: string;
    properties: EdmxPropertyInfo[];
    keyProperties: string[];
}

/**
 * Analyze an OData JSON entity to extract property info.
 * Merges properties across multiple entities for collections.
 */
function analyzeODataEntities(entities: any[], typeName: string): { types: EdmxTypeInfo[], complexTypes: EdmxTypeInfo[] } {
    const propertyMap = new Map<string, EdmxPropertyInfo>();
    const complexTypes: EdmxTypeInfo[] = [];
    const keyProperties: string[] = [];

    for (const entity of entities) {
        for (const [key, value] of Object.entries(entity)) {
            // Skip @odata annotations
            if (key.startsWith('@odata.') || key.startsWith('@')) {
                continue;
            }

            if (propertyMap.has(key)) {
                // Already seen - update nullable if this value is null
                if (value === null) {
                    propertyMap.get(key)!.nullable = true;
                }
                continue;
            }

            if (Array.isArray(value)) {
                // Navigation property (collection)
                const complexName = key.charAt(0).toUpperCase() + key.slice(1);
                propertyMap.set(key, {
                    name: key,
                    type: `Collection(Inferred.${complexName})`,
                    nullable: false,
                    isNavigation: true,
                    isCollection: true,
                    complexTypeName: complexName,
                });
                // Recurse into array items if they are objects
                const objectItems = value.filter(v => v && typeof v === 'object' && !Array.isArray(v));
                if (objectItems.length > 0) {
                    const nested = analyzeODataEntities(objectItems, complexName);
                    complexTypes.push(...nested.types);
                    complexTypes.push(...nested.complexTypes);
                }
            } else if (value !== null && typeof value === 'object') {
                // Navigation property (single) or complex type
                const complexName = key.charAt(0).toUpperCase() + key.slice(1);
                propertyMap.set(key, {
                    name: key,
                    type: `Inferred.${complexName}`,
                    nullable: true,
                    isNavigation: true,
                    isCollection: false,
                    complexTypeName: complexName,
                });
                const nested = analyzeODataEntities([value], complexName);
                complexTypes.push(...nested.types);
                complexTypes.push(...nested.complexTypes);
            } else {
                // Scalar property
                propertyMap.set(key, {
                    name: key,
                    type: inferEdmType(value),
                    nullable: value === null,
                    isNavigation: false,
                    isCollection: false,
                });
            }
        }
    }

    // Heuristic: if there's an "ID" or "<TypeName>ID" property, treat it as key
    const props = Array.from(propertyMap.values());
    for (const p of props) {
        if (!p.isNavigation) {
            const lower = p.name.toLowerCase();
            if (lower === 'id' || lower === typeName.toLowerCase() + 'id' || lower === typeName.toLowerCase() + '_id') {
                keyProperties.push(p.name);
                p.nullable = false;
                break;
            }
        }
    }

    return {
        types: [{
            name: typeName,
            properties: props,
            keyProperties,
        }],
        complexTypes,
    };
}

/**
 * Generate EDMX/CSDL XML from analyzed type info
 */
function generateEdmx(entityTypes: EdmxTypeInfo[], complexTypes: EdmxTypeInfo[]): string {
    const lines: string[] = [];
    lines.push('<?xml version="1.0" encoding="UTF-8"?>');
    lines.push('<edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">');
    lines.push('  <edmx:DataServices>');
    lines.push('    <Schema Namespace="Inferred" xmlns="http://docs.oasis-open.org/odata/ns/edm">');

    // Entity types
    for (const et of entityTypes) {
        lines.push(`      <EntityType Name="${escapeXmlAttr(et.name)}">`);
        if (et.keyProperties.length > 0) {
            lines.push('        <Key>');
            for (const kp of et.keyProperties) {
                lines.push(`          <PropertyRef Name="${escapeXmlAttr(kp)}"/>`);
            }
            lines.push('        </Key>');
        }
        for (const prop of et.properties) {
            if (prop.isNavigation) {
                lines.push(`        <NavigationProperty Name="${escapeXmlAttr(prop.name)}" Type="${escapeXmlAttr(prop.type)}"/>`);
            } else {
                lines.push(`        <Property Name="${escapeXmlAttr(prop.name)}" Type="${escapeXmlAttr(prop.type)}" Nullable="${prop.nullable}"/>`);
            }
        }
        lines.push('      </EntityType>');
    }

    // Complex types (from nested objects)
    const emittedComplex = new Set<string>();
    for (const ct of complexTypes) {
        if (emittedComplex.has(ct.name)) continue;
        emittedComplex.add(ct.name);
        lines.push(`      <ComplexType Name="${escapeXmlAttr(ct.name)}">`);
        for (const prop of ct.properties) {
            if (prop.isNavigation) {
                lines.push(`        <NavigationProperty Name="${escapeXmlAttr(prop.name)}" Type="${escapeXmlAttr(prop.type)}"/>`);
            } else {
                lines.push(`        <Property Name="${escapeXmlAttr(prop.name)}" Type="${escapeXmlAttr(prop.type)}" Nullable="${prop.nullable}"/>`);
            }
        }
        lines.push('      </ComplexType>');
    }

    lines.push('    </Schema>');
    lines.push('  </edmx:DataServices>');
    lines.push('</edmx:Edmx>');

    return lines.join('\n');
}

/**
 * Infer OData EDMX/CSDL schema from OData JSON instance data.
 *
 * Handles both collection responses (with "value" array) and single entities.
 * Filters out @odata.* annotations and infers Edm types from values.
 *
 * @param odataJson OData JSON string
 * @returns EDMX/CSDL XML string
 */
export function inferEdmxFromOData(odataJson: string): string {
    try {
        const parsed = JSON.parse(odataJson);

        let entities: any[];
        let typeName = 'Entity';

        if (Array.isArray(parsed)) {
            // Plain array
            entities = parsed.filter(v => v && typeof v === 'object');
        } else if (parsed && typeof parsed === 'object') {
            if (Array.isArray(parsed.value)) {
                // OData collection response { "value": [...] }
                entities = parsed.value.filter((v: any) => v && typeof v === 'object');
                // Try to guess entity name from @odata.context
                if (parsed['@odata.context'] && typeof parsed['@odata.context'] === 'string') {
                    const ctx: string = parsed['@odata.context'];
                    const match = ctx.match(/\/(\w+)(?:\(|$)/);
                    if (match) {
                        typeName = match[1];
                        // Singularize simple plural (remove trailing 's')
                        if (typeName.endsWith('s') && typeName.length > 2) {
                            typeName = typeName.slice(0, -1);
                        }
                    }
                }
            } else {
                // Single entity
                entities = [parsed];
            }
        } else {
            throw new Error('OData content is not a JSON object or array');
        }

        if (entities.length === 0) {
            throw new Error('No entity data found to infer schema from');
        }

        const { types, complexTypes } = analyzeODataEntities(entities, typeName);
        return generateEdmx(types, complexTypes);
    } catch (error) {
        throw new Error(`Failed to infer EDMX from OData: ${error instanceof Error ? error.message : String(error)}`);
    }
}
