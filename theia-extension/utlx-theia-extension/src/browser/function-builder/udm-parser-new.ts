/**
 * UDM Parser (NEW IMPLEMENTATION)
 *
 * Parses UDM (Universal Data Model) language format into a tree structure
 * suitable for display in the Function Builder's field tree.
 *
 * Uses format-specific strategies to handle format quirks (XML _text, namespaces, etc.)
 * without coupling formats together.
 */

import { UDMLanguageParser, UDMParseException } from '../udm/udm-language-parser';
import { UDM, UDMObjectHelper, isObject, isArray, isScalar, isBinary, isLambda } from '../udm/udm-core';
import { getStrategyForFormat, FormatTreeStrategy } from './strategies';

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
    format: string; // json, csv, xml, yaml, xsd, jsch, avro, proto
    isArray: boolean;
    fields: UdmField[];
}

/**
 * Parse UDM language string into a tree structure
 *
 * NEW IMPLEMENTATION: Uses proper UDM parser instead of regex
 */
export function parseUdmToTree(inputName: string, format: string, udmLanguage: string | undefined): UdmInputTree {
    console.log('═'.repeat(80));
    console.log('[UdmParser] parseUdmToTree called');
    console.log('[UdmParser] Input name:', inputName);
    console.log('[UdmParser] Format:', format);
    console.log('[UdmParser] UDM provided:', udmLanguage ? 'YES' : 'NO');

    if (!udmLanguage) {
        console.warn('[UdmParser] ⚠️ No UDM language provided for', inputName);
        console.log('═'.repeat(80));
        return {
            inputName,
            format,
            isArray: false,
            fields: []
        };
    }

    console.log('[UdmParser] UDM length:', udmLanguage.length, 'characters');
    console.log('[UdmParser] First 300 chars:', udmLanguage.substring(0, 300));
    console.log('[UdmParser] Last 200 chars:', udmLanguage.substring(Math.max(0, udmLanguage.length - 200)));

    try {
        console.log('[UdmParser] 🔍 Starting UDM parsing...');

        // Parse using proper UDM parser
        const parsed = UDMLanguageParser.parse(udmLanguage);

        console.log('[UdmParser] ✅ Successfully parsed UDM');
        console.log('[UdmParser] Parsed type:', parsed.type);
        console.log('[UdmParser] Parsed object:', JSON.stringify(parsed, (key, value) => {
            if (value instanceof Map) {
                return `Map(${value.size} entries)`;
            }
            if (value instanceof Uint8Array) {
                return `Uint8Array(${value.length} bytes)`;
            }
            return value;
        }, 2).substring(0, 500));

        // Check if root is an array
        const isArrayRoot = isArray(parsed);
        console.log('[UdmParser] Is array root:', isArrayRoot);

        if (isObject(parsed)) {
            console.log('[UdmParser] Root is OBJECT');
            console.log('[UdmParser] Properties map size:', parsed.properties.size);
            console.log('[UdmParser] Property keys:', Array.from(parsed.properties.keys()));
            console.log('[UdmParser] Attributes map size:', parsed.attributes.size);
            console.log('[UdmParser] Attribute keys:', Array.from(parsed.attributes.keys()));
            console.log('[UdmParser] Element name:', parsed.name);
            console.log('[UdmParser] Metadata:', Array.from(parsed.metadata.entries()));
        } else if (isArray(parsed)) {
            console.log('[UdmParser] Root is ARRAY');
            console.log('[UdmParser] Array length:', parsed.elements.length);
            if (parsed.elements.length > 0) {
                console.log('[UdmParser] First element type:', parsed.elements[0].type);
            }
        }

        // Get format-specific strategy
        const strategy = getStrategyForFormat(format);
        console.log('[UdmParser] Using strategy for format:', format);

        // Convert UDM to field tree using strategy
        console.log('[UdmParser] 🔄 Converting UDM to field tree...');
        const fields = convertUDMToFields(parsed, strategy);

        console.log('[UdmParser] ✅ Conversion complete');
        console.log('[UdmParser] Extracted', fields.length, 'fields');

        if (fields.length === 0) {
            console.warn('[UdmParser] ⚠️ NO FIELDS EXTRACTED!');
            console.log('[UdmParser] This could indicate:');
            console.log('[UdmParser]   1. Empty object/array');
            console.log('[UdmParser]   2. Unsupported UDM structure');
            console.log('[UdmParser]   3. Bug in convertUDMToFields');
        } else {
            fields.forEach((f, idx) => {
                console.log(`[UdmParser]   Field ${idx + 1}:`, f.name, '| type:', f.type, '| nested:', f.fields?.length || 0);
            });
        }

        console.log('═'.repeat(80));
        return {
            inputName,
            format,
            isArray: isArrayRoot,
            fields
        };

    } catch (error) {
        console.error('═'.repeat(80));
        console.error('[UdmParser] ❌ PARSING FAILED');
        console.error('[UdmParser] Input:', inputName);
        console.error('[UdmParser] Format:', format);

        if (error instanceof UDMParseException) {
            console.error('[UdmParser] UDM parse error:', error.message);
            console.error('[UdmParser] Error details:', error);
        } else {
            console.error('[UdmParser] Unexpected error:', error);
            console.error('[UdmParser] Error stack:', error instanceof Error ? error.stack : 'N/A');
        }

        console.error('[UdmParser] UDM content (first 500 chars):');
        console.error(udmLanguage.substring(0, 500));
        console.error('═'.repeat(80));

        return {
            inputName,
            format,
            isArray: false,
            fields: []
        };
    }
}

/**
 * Convert UDM object to field tree using format-specific strategy
 */
function convertUDMToFields(udm: UDM, strategy: FormatTreeStrategy): UdmField[] {
    console.log('[convertUDMToFields] Converting UDM type:', udm.type);

    if (isObject(udm)) {
        console.log('[convertUDMToFields] Converting OBJECT with', udm.properties.size, 'properties');
        const result = convertObjectToFields(udm, strategy);
        console.log('[convertUDMToFields] Object conversion returned', result.length, 'fields');
        return result;
    } else if (isArray(udm)) {
        console.log('[convertUDMToFields] Converting ARRAY with', udm.elements.length, 'elements');
        // For arrays, extract structure from first element
        if (udm.elements.length > 0) {
            const firstElement = udm.elements[0];
            console.log('[convertUDMToFields] First element type:', firstElement.type);
            if (isObject(firstElement)) {
                console.log('[convertUDMToFields] First element is OBJECT, extracting fields');
                const result = convertObjectToFields(firstElement, strategy);
                console.log('[convertUDMToFields] Array conversion returned', result.length, 'fields');
                return result;
            } else {
                console.log('[convertUDMToFields] First element is', firstElement.type, '- no fields to extract');
            }
        } else {
            console.warn('[convertUDMToFields] ⚠️ Array is EMPTY - no fields to extract');
        }
        return [];
    } else if (isScalar(udm)) {
        console.log('[convertUDMToFields] Root is SCALAR:', udm.value);
        // Root is a scalar (unusual, but handle it)
        return [{
            name: 'value',
            type: getScalarType(udm.value),
            description: 'Scalar value'
        }];
    } else {
        console.log('[convertUDMToFields] Root is other type:', udm.type);
        // DateTime, Binary, Lambda, etc. at root (very unusual)
        return [{
            name: 'value',
            type: udm.type,
            description: udm.type
        }];
    }
}

/**
 * Convert UDM Object to field array using format-specific strategy
 */
function convertObjectToFields(obj: UDM & { type: 'object' }, strategy: FormatTreeStrategy): UdmField[] {
    console.log('[convertObjectToFields] Converting object');
    console.log('[convertObjectToFields] Properties map size:', obj.properties.size);
    console.log('[convertObjectToFields] Attributes map size:', obj.attributes.size);

    const fields: UdmField[] = [];

    // Add all properties
    const propertyKeys = UDMObjectHelper.keys(obj);
    console.log('[convertObjectToFields] Property keys:', propertyKeys);

    for (const key of propertyKeys) {
        console.log('[convertObjectToFields] Processing property:', key);
        const value = UDMObjectHelper.get(obj, key);
        if (value) {
            console.log('[convertObjectToFields]   Value type:', value.type);
            const field = convertUDMValueToField(key, value, strategy);
            fields.push(field);
            console.log('[convertObjectToFields]   Created field:', field.name, 'type:', field.type);
        } else {
            console.warn('[convertObjectToFields]   ⚠️ Value is undefined for key:', key);
        }
    }

    // Add attributes using format-specific filtering
    const attrKeys = UDMObjectHelper.attributeKeys(obj);
    console.log('[convertObjectToFields] Attribute keys:', attrKeys);

    const meaningfulAttrs = strategy.filterAttributes(attrKeys);
    console.log('[convertObjectToFields] Filtered attributes:', meaningfulAttrs);

    for (const key of meaningfulAttrs) {
        const attrValue = UDMObjectHelper.getAttribute(obj, key);
        console.log('[convertObjectToFields] Processing attribute:', key, '=', attrValue);
        fields.push({
            name: `@${key}`,
            type: 'string',
            description: `Attribute: ${attrValue}`
        });
    }

    console.log('[convertObjectToFields] Total fields created:', fields.length);
    return fields;
}

/**
 * Convert a UDM value to a field descriptor using format-specific strategy
 */
function convertUDMValueToField(name: string, udm: UDM, strategy: FormatTreeStrategy): UdmField {
    console.log('[convertUDMValueToField] Converting field:', name, 'type:', udm.type);

    if (isScalar(udm)) {
        const scalarType = getScalarType(udm.value);
        console.log('[convertUDMValueToField]   Scalar value:', udm.value, 'type:', scalarType);
        return {
            name,
            type: scalarType,
            description: `${name}: ${scalarType}`
        };
    } else if (isObject(udm)) {
        console.log('[convertUDMValueToField]   Nested object, checking if should flatten...');

        // Use strategy to determine if this object should be flattened to a scalar
        if (strategy.shouldFlattenToScalar(udm)) {
            console.log('[convertUDMValueToField]   Strategy says FLATTEN to scalar');

            const flattenedType = strategy.getFlattenedType(udm);
            const flattenedChildren = strategy.getFlattenedChildren(udm);

            console.log('[convertUDMValueToField]   Flattened type:', flattenedType);
            console.log('[convertUDMValueToField]   Flattened children:', flattenedChildren.length);

            return {
                name,
                type: flattenedType,
                description: `${name}: ${flattenedType}${flattenedChildren.length > 0 ? ' (with attributes)' : ''}`,
                fields: flattenedChildren.length > 0 ? flattenedChildren : undefined
            };
        }

        // Regular nested object - convert recursively
        console.log('[convertUDMValueToField]   Strategy says keep as OBJECT, recursing...');
        const nestedFields = convertObjectToFields(udm, strategy);
        console.log('[convertUDMValueToField]   Nested object has', nestedFields.length, 'fields');
        return {
            name,
            type: 'object',
            description: `${name}: object with ${nestedFields.length} fields`,
            fields: nestedFields
        };
    } else if (isArray(udm)) {
        console.log('[convertUDMValueToField]   Array with', udm.elements.length, 'elements');
        // Extract structure from first element
        let elementFields: UdmField[] | undefined;
        if (udm.elements.length > 0) {
            const firstElement = udm.elements[0];
            console.log('[convertUDMValueToField]   First element type:', firstElement.type);
            if (isObject(firstElement)) {
                console.log('[convertUDMValueToField]   Extracting array element structure...');
                elementFields = convertObjectToFields(firstElement, strategy);
            } else if (isScalar(firstElement)) {
                elementFields = [{
                    name: '[element]',
                    type: getScalarType(firstElement.value),
                    description: 'Array element'
                }];
            }
        } else {
            console.log('[convertUDMValueToField]   Empty array');
        }

        return {
            name,
            type: 'array',
            description: `${name}: array${elementFields ? ` of ${udm.elements.length} ${getElementType(udm.elements[0])}` : ''}`,
            fields: elementFields
        };
    } else if (isBinary(udm)) {
        console.log('[convertUDMValueToField]   Binary data, length:', udm.data.length);
        return {
            name,
            type: 'binary',
            description: `${name}: binary (${udm.data.length} bytes)`
        };
    } else if (isLambda(udm)) {
        console.log('[convertUDMValueToField]   Lambda function');
        return {
            name,
            type: 'lambda',
            description: `${name}: function${udm.arity ? ` (${udm.arity} params)` : ''}`
        };
    } else {
        console.log('[convertUDMValueToField]   Other type:', udm.type);
        // DateTime, Date, LocalDateTime, Time
        return {
            name,
            type: udm.type,
            description: `${name}: ${udm.type}`
        };
    }
}

/**
 * Get type label for scalar value
 */
function getScalarType(value: string | number | boolean | null): string {
    if (value === null) return 'null';
    if (typeof value === 'string') return 'string';
    if (typeof value === 'number') return 'number';
    if (typeof value === 'boolean') return 'boolean';
    return 'unknown';
}

/**
 * Get type label for array element
 */
function getElementType(element: UDM): string {
    if (isScalar(element)) {
        return getScalarType(element.value) + 's';
    }
    return element.type + 's';
}

/**
 * Get display name for type (for UI)
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
        case 'lambda': return 'Lambda';
        case 'attribute': return 'Attribute';
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
        case 'lambda': return 'codicon-symbol-method';
        case 'attribute': return 'codicon-symbol-property';
        case 'null': return 'codicon-circle-slash';
        default: return 'codicon-symbol-field';
    }
}
