/**
 * Type Inference Module
 *
 * Provides type inference for UTLX expressions based on UDM data.
 * Used for smart function insertion and type validation.
 */

/**
 * Infer the type of an expression based on UDM data
 *
 * Examples:
 * - "$input" -> Array (if UDM shows array)
 * - "$input[0]" -> Object (element type)
 * - "$input[0].HireDate" -> DateTime (from UDM field type)
 * - "$input.name" -> string (from UDM field type)
 */
export function inferExpressionType(
    expression: string,
    udmMap: Map<string, string>
): string | null {
    console.log('[TypeInference] Inferring type for:', expression);

    // Pattern 1: $inputName[index].field.subfield
    // Pattern 2: $inputName.field.subfield
    // Pattern 3: $inputName[index]
    // Pattern 4: $inputName

    const match = expression.match(/^\$(\w+)(.*)/);
    if (!match) {
        console.log('[TypeInference] Not a valid input reference');
        return null;
    }

    const inputName = match[1];
    const pathAfter = match[2]; // e.g., "[0].HireDate" or ".name" or "[0]" or ""

    const udm = udmMap.get(inputName);
    if (!udm) {
        console.log('[TypeInference] No UDM found for input:', inputName);
        return null;
    }

    // Parse the UDM to understand structure
    const isArray = isUdmArray(udm);

    // Case 1: Just $input (no path after)
    if (!pathAfter || pathAfter.trim() === '') {
        return isArray ? 'Array' : 'Object';
    }

    // Case 2: $input[0] (array indexing, no field path)
    const arrayIndexOnlyMatch = pathAfter.match(/^\[\d+\]$/);
    if (arrayIndexOnlyMatch) {
        if (!isArray) {
            console.log('[TypeInference] Array indexing on non-array');
            return null;
        }
        return 'Object'; // Element type
    }

    // Case 3: $input[0].field or $input.field
    let fieldPath: string;
    if (pathAfter.startsWith('[')) {
        // Extract field path after [index]
        const fieldMatch = pathAfter.match(/^\[\d+\]\.(.+)/);
        if (fieldMatch) {
            fieldPath = fieldMatch[1];
        } else {
            console.log('[TypeInference] Invalid array indexing pattern');
            return null;
        }
    } else if (pathAfter.startsWith('.')) {
        // Direct field access
        fieldPath = pathAfter.substring(1); // Remove leading dot
    } else {
        console.log('[TypeInference] Invalid path pattern');
        return null;
    }

    // Look up field type in UDM
    return getFieldTypeFromUdm(udm, fieldPath, isArray);
}

/**
 * Check if UDM represents an array at root level
 */
function isUdmArray(udm: string): boolean {
    const trimmed = udm.trim()
        .replace(/@udm-version:[^\n]*\n/g, '')
        .replace(/@parsed-at:[^\n]*\n/g, '')
        .replace(/@source:[^\n]*\n/g, '')
        .trim();
    return trimmed.startsWith('[');
}

/**
 * Extract field type from UDM by path
 * Example: "HireDate" from UDM with {EmployeeID: "12345", HireDate: @DateTime(...)}
 */
function getFieldTypeFromUdm(udm: string, fieldPath: string, isArray: boolean): string | null {
    console.log('[TypeInference] Looking up field:', fieldPath, 'in UDM');

    // Clean UDM
    let content = udm.trim()
        .replace(/@udm-version:[^\n]*\n/g, '')
        .replace(/@parsed-at:[^\n]*\n/g, '')
        .replace(/@source:[^\n]*\n/g, '')
        .trim();

    // If array, extract first element
    if (isArray) {
        const firstElementMatch = content.match(/\[\s*\{([^}]+)\}/);
        if (firstElementMatch) {
            content = `{${firstElementMatch[1]}}`;
        } else {
            console.log('[TypeInference] Could not extract array element');
            return null;
        }
    }

    // Split field path into parts
    const pathParts = fieldPath.split('.');

    // Navigate through nested structure
    for (const part of pathParts) {
        // Look for field: value pattern
        const fieldPattern = new RegExp(`${part}:\\s*(@\\w+(?:\\([^)]*\\))?|"[^"]*"|[^,}]+)`, 's');
        const match = content.match(fieldPattern);

        if (!match) {
            console.log('[TypeInference] Field not found:', part);
            return null;
        }

        const fieldValue = match[1].trim();

        // Check if it's a UDM type annotation like @DateTime(...) or @String
        if (fieldValue.startsWith('@')) {
            const typeMatch = fieldValue.match(/@(\w+)/);
            if (typeMatch) {
                const udmType = typeMatch[1];
                return mapUdmTypeToJsType(udmType);
            }
        }

        // If it's an object, navigate into it
        if (fieldValue.startsWith('{')) {
            content = fieldValue;
            continue;
        }

        // Simple value - infer type from literal
        return inferTypeFromLiteral(fieldValue);
    }

    return null;
}

/**
 * Map UDM type annotations to JavaScript/UTLX types
 */
function mapUdmTypeToJsType(udmType: string): string {
    const typeMap: Record<string, string> = {
        'String': 'string',
        'Number': 'number',
        'Boolean': 'boolean',
        'DateTime': 'DateTime',
        'Date': 'Date',
        'Binary': 'binary',
        'Object': 'Object',
        'Array': 'Array',
        'Null': 'null'
    };

    return typeMap[udmType] || udmType;
}

/**
 * Infer type from literal value
 */
function inferTypeFromLiteral(value: string): string {
    value = value.trim();

    if (value.startsWith('"') || value.startsWith("'")) {
        return 'string';
    }

    if (value === 'true' || value === 'false') {
        return 'boolean';
    }

    if (value === 'null') {
        return 'null';
    }

    if (/^-?\d+(\.\d+)?$/.test(value)) {
        return 'number';
    }

    if (value.startsWith('{')) {
        return 'Object';
    }

    if (value.startsWith('[')) {
        return 'Array';
    }

    return 'unknown';
}

/**
 * Check if a value type is compatible with a parameter type
 * This is a basic type compatibility checker
 */
export function canAcceptType(paramType: string, valueType: string): boolean {
    // Exact match
    if (paramType === valueType) {
        return true;
    }

    // Any accepts anything
    if (paramType === 'any' || paramType === 'Any') {
        return true;
    }

    // String conversions
    if (paramType === 'string') {
        // Most types can be converted to string
        return ['string', 'number', 'boolean', 'DateTime', 'Date'].includes(valueType);
    }

    // Number conversions
    if (paramType === 'number') {
        return valueType === 'number' || valueType === 'string';
    }

    // DateTime/Date compatibility
    if (paramType === 'DateTime' || paramType === 'Date') {
        return valueType === 'DateTime' || valueType === 'Date' || valueType === 'string';
    }

    // Array compatibility
    if (paramType.endsWith('[]') || paramType === 'Array') {
        return valueType === 'Array' || valueType.endsWith('[]');
    }

    // Object compatibility
    if (paramType === 'Object' || paramType === 'object') {
        return valueType === 'Object' || valueType === 'object';
    }

    // No match
    return false;
}

/**
 * Get parameter names from a function signature
 * Example: "formatDate(date: DateTime, format: string)" -> ["date", "format"]
 */
export function extractParameterNames(signature: string): string[] {
    // Pattern: functionName(param1: type1, param2: type2, ...)
    const match = signature.match(/\(([^)]*)\)/);
    if (!match) {
        return [];
    }

    const paramsString = match[1];
    if (!paramsString.trim()) {
        return [];
    }

    // Split by comma and extract parameter names
    return paramsString.split(',').map(param => {
        const nameMatch = param.trim().match(/^(\w+)/);
        return nameMatch ? nameMatch[1] : '';
    }).filter(name => name.length > 0);
}
