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
    format: string; // json, csv, xml, yaml, xsd, jsch, avro, proto
    isArray: boolean;
    fields: UdmField[];
}

/**
 * Parse UDM language string into a tree structure
 */
export function parseUdmToTree(inputName: string, format: string, udmLanguage: string | undefined): UdmInputTree {
    if (!udmLanguage) {
        console.log('[UdmParser] No UDM language provided for', inputName);
        return {
            inputName,
            format,
            isArray: false,
            fields: []
        };
    }

    try {
        console.log('[UdmParser] Parsing UDM for', inputName, 'format:', format);
        console.log('[UdmParser] Raw UDM (first 200 chars):', udmLanguage.substring(0, 200));

        // Strip metadata lines
        let cleanedUdm = udmLanguage.trim();
        cleanedUdm = cleanedUdm.replace(/@udm-version:[^\n]*\n/g, '');
        cleanedUdm = cleanedUdm.replace(/@parsed-at:[^\n]*\n/g, '');
        cleanedUdm = cleanedUdm.replace(/@source:[^\n]*\n/g, '');
        cleanedUdm = cleanedUdm.trim();

        console.log('[UdmParser] Cleaned UDM (first 200 chars):', cleanedUdm.substring(0, 200));

        // Check if root is an array
        const isArray = cleanedUdm.startsWith('[');
        console.log('[UdmParser] Is array:', isArray);

        // Extract fields
        const fields = isArray
            ? parseArrayUdm(cleanedUdm)
            : parseObjectUdm(cleanedUdm);

        console.log('[UdmParser] Extracted fields:', fields.length, 'fields');
        fields.forEach(f => console.log('  - Field:', f.name, 'type:', f.type));

        return {
            inputName,
            format,
            isArray,
            fields
        };
    } catch (error) {
        console.error('[UdmParser] Failed to parse UDM for', inputName, ':', error);
        return {
            inputName,
            format,
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
    console.log('[UdmParser] parseArrayUdm called with:', udm.substring(0, 150));

    // Find the first opening brace after the opening bracket
    const openBracketIndex = udm.indexOf('[');
    if (openBracketIndex === -1) {
        console.warn('[UdmParser] Could not find opening bracket in:', udm.substring(0, 100));
        return [];
    }
    console.log('[UdmParser] Found opening bracket at index:', openBracketIndex);

    // Find the first element's opening brace
    const firstElementIndex = udm.indexOf('{', openBracketIndex);
    if (firstElementIndex === -1) {
        console.warn('[UdmParser] Could not find first element in array:', udm.substring(0, 100));
        return [];
    }
    console.log('[UdmParser] Found first element opening brace at index:', firstElementIndex);

    // Extract the first element using proper brace matching
    const elementContent = extractBracedContent(udm, firstElementIndex);
    if (!elementContent) {
        console.warn('[UdmParser] Could not extract first array element from:', udm.substring(0, 100));
        return [];
    }
    console.log('[UdmParser] Extracted element content (first 150 chars):', elementContent.substring(0, 150));

    // Parse the first element as an object (the content is already without outer braces)
    // Wrap it back in braces for parseObjectUdm
    const wrappedContent = `{${elementContent}}`;
    console.log('[UdmParser] Wrapped content for parseObjectUdm (first 150 chars):', wrappedContent.substring(0, 150));

    const fields = parseObjectUdm(wrappedContent);
    console.log('[UdmParser] parseObjectUdm returned:', fields.length, 'fields');

    return fields;
}

/**
 * Parse object UDM: {field1: value, field2: value} or @Object { properties: {...} }
 */
function parseObjectUdm(udm: string): UdmField[] {
    console.log('[UdmParser] parseObjectUdm called with (first 150 chars):', udm.substring(0, 150));

    // Check if it's full format with properties section
    const hasProperties = udm.includes('properties:');
    console.log('[UdmParser] Has properties section:', hasProperties);

    if (hasProperties) {
        const fields = parseFullFormatObject(udm);
        console.log('[UdmParser] parseFullFormatObject returned:', fields.length, 'fields');
        return fields;
    }

    // Shorthand format
    console.log('[UdmParser] Using shorthand format parser');
    const fields = parseShorthandObject(udm);
    console.log('[UdmParser] parseShorthandObject returned:', fields.length, 'fields');
    return fields;
}

/**
 * Extract content between matching braces, handling nested braces properly
 * @param text The text containing braces
 * @param startIndex The index of the opening brace
 * @returns The content between the braces (excluding the braces themselves), or null if no match
 */
function extractBracedContent(text: string, startIndex: number): string | null {
    console.log('[extractBracedContent] Called with startIndex:', startIndex, 'char:', text[startIndex]);

    if (text[startIndex] !== '{') {
        console.warn('[extractBracedContent] Character at startIndex is not {, returning null');
        return null;
    }

    let braceCount = 0;
    let inString = false;
    let escapeNext = false;

    for (let i = startIndex; i < text.length; i++) {
        const char = text[i];

        if (escapeNext) {
            escapeNext = false;
            continue;
        }

        if (char === '\\') {
            escapeNext = true;
            continue;
        }

        if (char === '"' && !escapeNext) {
            inString = !inString;
            continue;
        }

        if (!inString) {
            if (char === '{') {
                braceCount++;
            } else if (char === '}') {
                braceCount--;
                if (braceCount === 0) {
                    // Found matching closing brace
                    const content = text.substring(startIndex + 1, i);
                    console.log('[extractBracedContent] Found matching brace at:', i, 'content length:', content.length);
                    return content;
                }
            }
        }
    }

    console.warn('[extractBracedContent] No matching brace found, braceCount:', braceCount);
    return null; // No matching brace found
}

/**
 * Parse full format: @Object(name: "X") { properties: { field: value } }
 */
function parseFullFormatObject(udm: string): UdmField[] {
    // Find the properties section - need to handle nested braces properly
    const propertiesIndex = udm.indexOf('properties:');
    if (propertiesIndex === -1) {
        console.warn('[UdmParser] Could not find properties section in:', udm.substring(0, 100));
        return [];
    }

    // Find the opening brace after 'properties:'
    const openBraceIndex = udm.indexOf('{', propertiesIndex);
    if (openBraceIndex === -1) {
        console.warn('[UdmParser] Could not find opening brace for properties in:', udm.substring(0, 100));
        return [];
    }

    // Extract content between matching braces
    const propertiesContent = extractBracedContent(udm, openBraceIndex);
    if (!propertiesContent) {
        console.warn('[UdmParser] Could not extract properties content from:', udm.substring(0, 100));
        return [];
    }

    return parseFieldsFromContent(propertiesContent);
}

/**
 * Parse shorthand format: {field1: value, field2: value}
 */
function parseShorthandObject(udm: string): UdmField[] {
    console.log('[UdmParser] parseShorthandObject called with (first 150 chars):', udm.substring(0, 150));

    // Remove outer braces
    let content = udm.trim();
    if (content.startsWith('{') && content.endsWith('}')) {
        content = content.substring(1, content.length - 1);
    }

    console.log('[UdmParser] Content after removing braces (first 150 chars):', content.substring(0, 150));

    const fields = parseFieldsFromContent(content);
    console.log('[UdmParser] parseFieldsFromContent returned:', fields.length, 'fields');
    return fields;
}

/**
 * Parse fields from property content string
 * Handles: field: "value", field: 123, field: {...}, field: [...]
 * Uses proper parsing instead of regex to handle nested structures
 */
function parseFieldsFromContent(content: string): UdmField[] {
    const fields: UdmField[] = [];
    let pos = 0;

    function skipWhitespace() {
        while (pos < content.length && /\s/.test(content[pos])) {
            pos++;
        }
    }

    function parseFieldName(): string | null {
        skipWhitespace();
        if (pos >= content.length) return null;

        // Field name can be an identifier or a quoted string
        if (content[pos] === '"' || content[pos] === "'") {
            const quote = content[pos];
            pos++; // skip opening quote
            let name = '';
            while (pos < content.length && content[pos] !== quote) {
                if (content[pos] === '\\') {
                    pos++; // skip escape char
                }
                name += content[pos];
                pos++;
            }
            pos++; // skip closing quote
            return name;
        } else {
            // Identifier - alphanumeric, underscore, hyphen
            // Also allow colon for XML namespaces (e.g., xs:element)
            let name = '';
            while (pos < content.length) {
                const char = content[pos];
                // Allow letters, numbers, underscore, hyphen
                if (/[a-zA-Z0-9_\-]/.test(char)) {
                    name += char;
                    pos++;
                }
                // Allow colon only if followed by another identifier character (for namespaces like xs:element)
                else if (char === ':' && pos + 1 < content.length && /[a-zA-Z]/.test(content[pos + 1])) {
                    name += char;
                    pos++;
                }
                // Allow dot for nested properties (if needed)
                else if (char === '.' && pos + 1 < content.length && /[a-zA-Z0-9]/.test(content[pos + 1])) {
                    name += char;
                    pos++;
                }
                else {
                    break;
                }
            }
            return name || null;
        }
    }

    function extractValue(): string | null {
        skipWhitespace();
        if (pos >= content.length) return null;

        const startPos = pos;
        const firstChar = content[pos];

        // String value
        if (firstChar === '"' || firstChar === "'") {
            const quote = firstChar;
            pos++; // skip opening quote
            while (pos < content.length) {
                if (content[pos] === '\\') {
                    pos += 2; // skip escape sequence
                    continue;
                }
                if (content[pos] === quote) {
                    pos++; // skip closing quote
                    return content.substring(startPos, pos);
                }
                pos++;
            }
            return content.substring(startPos); // unterminated string
        }

        // Object or array or annotation
        if (firstChar === '{' || firstChar === '[' || firstChar === '@') {
            let braceCount = 0;
            let bracketCount = 0;
            let parenCount = 0;
            let inString = false;
            let stringChar = '';
            let escapeNext = false;

            // For @annotations, track whether we've entered the body
            let annotationBodyStarted = false;

            while (pos < content.length) {
                const char = content[pos];

                if (escapeNext) {
                    escapeNext = false;
                    pos++;
                    continue;
                }

                if (char === '\\') {
                    escapeNext = true;
                    pos++;
                    continue;
                }

                if (char === '"' || char === "'") {
                    if (!inString) {
                        inString = true;
                        stringChar = char;
                    } else if (char === stringChar) {
                        inString = false;
                    }
                    pos++;
                    continue;
                }

                if (!inString) {
                    if (char === '{') {
                        braceCount++;
                        // For @annotations, mark that we've entered the body
                        if (firstChar === '@' && parenCount === 0 && !annotationBodyStarted) {
                            annotationBodyStarted = true;
                        }
                    }
                    else if (char === '}') {
                        braceCount--;
                        // If we just closed the initial brace, we're done
                        if (firstChar === '{' && braceCount === 0) {
                            pos++;
                            return content.substring(startPos, pos).trim();
                        }
                        // For @annotations with body, we're done when body closes
                        if (firstChar === '@' && annotationBodyStarted && braceCount === 0) {
                            pos++;
                            return content.substring(startPos, pos).trim();
                        }
                    }
                    else if (char === '[') bracketCount++;
                    else if (char === ']') {
                        bracketCount--;
                        // If we just closed the initial bracket, we're done
                        if (firstChar === '[' && bracketCount === 0) {
                            pos++;
                            return content.substring(startPos, pos).trim();
                        }
                    }
                    else if (char === '(') parenCount++;
                    else if (char === ')') parenCount--;

                    // For @annotations without a body, stop at delimiter after closing parens
                    if (firstChar === '@' && !annotationBodyStarted && braceCount === 0 && bracketCount === 0 && parenCount === 0) {
                        if (char === ',' || char === '}' || char === ']') {
                            return content.substring(startPos, pos).trim();
                        }
                    }
                }

                pos++;
            }

            return content.substring(startPos).trim();
        }

        // Simple value (number, boolean, identifier, etc.)
        while (pos < content.length) {
            const char = content[pos];
            if (char === ',' || char === '}' || char === ']' || /\s/.test(char)) {
                break;
            }
            pos++;
        }

        return content.substring(startPos, pos).trim();
    }

    // Parse all fields with infinite loop protection
    console.log('[UdmParser] parseFieldsFromContent called with (first 200 chars):', content.substring(0, 200));
    let lastPos = -1;
    let iteration = 0;
    while (pos < content.length) {
        iteration++;
        console.log(`[UdmParser] Iteration ${iteration}, pos=${pos}, char='${content[pos]}'`);

        // Infinite loop protection
        if (pos === lastPos) {
            console.warn('[UdmParser] Infinite loop detected in parseFieldsFromContent at position', pos);
            break;
        }
        lastPos = pos;

        const fieldName = parseFieldName();
        console.log('[UdmParser] Parsed field name:', fieldName);
        if (!fieldName) break;

        skipWhitespace();
        if (pos >= content.length || content[pos] !== ':') {
            // No colon after field name - malformed content, skip this token
            console.warn('[UdmParser] No colon found after field name, pos:', pos, 'char:', content[pos]);
            pos++;
            continue;
        }
        pos++; // skip colon

        const fieldValue = extractValue();
        console.log('[UdmParser] Extracted field value (first 50 chars):', fieldValue?.substring(0, 50));
        if (fieldValue) {
            const field = parseFieldValue(fieldName, fieldValue);
            console.log('[UdmParser] Parsed field:', field);
            if (field) {
                fields.push(field);
            }
        }

        // Skip comma if present
        skipWhitespace();
        if (pos < content.length && content[pos] === ',') {
            pos++;
        }
    }

    console.log('[UdmParser] parseFieldsFromContent returning:', fields.length, 'fields');
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
        console.log('[UdmParser] Parsing @Object, value (first 200 chars):', value.substring(0, 200));

        // Skip the @Object annotation part (name and metadata in parentheses)
        // Need to properly match parentheses since metadata can have nested braces
        let bodySearchStart = '@Object'.length;
        if (value[bodySearchStart] === '(') {
            // Find matching closing paren using proper nesting
            let parenCount = 0;
            let inString = false;
            let stringChar = '';
            let i = bodySearchStart;

            while (i < value.length) {
                const char = value[i];

                if (char === '"' || char === "'") {
                    if (!inString) {
                        inString = true;
                        stringChar = char;
                    } else if (char === stringChar && value[i-1] !== '\\') {
                        inString = false;
                    }
                }

                if (!inString) {
                    if (char === '(') parenCount++;
                    else if (char === ')') {
                        parenCount--;
                        if (parenCount === 0) {
                            bodySearchStart = i + 1;
                            break;
                        }
                    }
                }
                i++;
            }
        }

        console.log('[UdmParser] Body search starts at position:', bodySearchStart);
        console.log('[UdmParser] Value at bodySearchStart:', value.substring(bodySearchStart, bodySearchStart + 50));

        // Now find the actual object body (the {...} after the annotation)
        const bodyStartIndex = value.indexOf('{', bodySearchStart);
        console.log('[UdmParser] Body start index:', bodyStartIndex);
        if (bodyStartIndex === -1) {
            console.warn('[UdmParser] No object body found for @Object');
            return { name, type: 'object', fields: [] };
        }

        console.log('[UdmParser] Calling extractBracedContent at index:', bodyStartIndex);
        console.log('[UdmParser] Character at bodyStartIndex:', value[bodyStartIndex]);
        const objectBody = extractBracedContent(value, bodyStartIndex);
        console.log('[UdmParser] Extracted object body (first 200 chars):', objectBody?.substring(0, 200));

        if (objectBody) {
            // Check if this has a properties section
            if (objectBody.includes('properties:')) {
                console.log('[UdmParser] Object has properties section');
                const propertiesIndex = objectBody.indexOf('properties:');
                const propsOpenBrace = objectBody.indexOf('{', propertiesIndex);
                if (propsOpenBrace !== -1) {
                    const propsContent = extractBracedContent(objectBody, propsOpenBrace);
                    console.log('[UdmParser] Extracted properties content (first 200 chars):', propsContent?.substring(0, 200));
                    if (propsContent) {
                        const nestedFields = parseFieldsFromContent(propsContent);
                        console.log('[UdmParser] Nested fields:', nestedFields.length);
                        return { name, type: 'object', fields: nestedFields };
                    }
                }
            } else {
                console.log('[UdmParser] Object has no properties section, using shorthand');
                // Simple object without properties section
                const nestedFields = parseFieldsFromContent(objectBody);
                console.log('[UdmParser] Nested fields:', nestedFields.length);
                return { name, type: 'object', fields: nestedFields };
            }
        }

        console.warn('[UdmParser] Failed to extract object body, returning empty object');
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
