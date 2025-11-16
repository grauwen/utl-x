/**
 * UDM Navigator
 *
 * Provides path-based navigation through UDM objects, mirroring the CLI behavior.
 *
 * Key behaviors:
 * - Paths like "$input.field" access properties directly (no "properties" in path)
 * - Paths like "$input.@attr" access attributes (@ prefix)
 * - Supports recursive descent for finding all matching paths
 * - Returns undefined for non-existent paths (not errors)
 *
 * This is what enables the CLI and IDE to use the same path syntax.
 */

import { UDM, UDMObject, UDMObjectHelper, isObject, isArray, isScalar } from './udm-core';

/**
 * Navigate through a UDM object using a path expression
 *
 * @param udm The root UDM object
 * @param path The path to navigate (e.g., "providers.address.street" or "@id")
 * @returns The UDM value at the path, or undefined if not found
 *
 * Examples:
 * - navigate(udm, "customer.name") → accesses udm.properties["customer"].properties["name"]
 * - navigate(udm, "@id") → accesses udm.attributes["id"]
 * - navigate(udm, "items[0].price") → accesses array element and then property
 */
export function navigate(udm: UDM, path: string): UDM | string | undefined {
    if (!path || path.trim() === '') {
        return udm;
    }

    // Remove $input prefix if present
    const cleanPath = path.startsWith('$input.') ? path.substring(7) :
                      path.startsWith('$input') ? '' : path;

    if (cleanPath === '') {
        return udm;
    }

    // Split path into segments
    const segments = parsePathSegments(cleanPath);
    return navigateSegments(udm, segments);
}

/**
 * Parse a path into segments, handling array indices and attribute access
 *
 * Examples:
 * - "customer.name" → ["customer", "name"]
 * - "items[0].price" → ["items", "[0]", "price"]
 * - "@id" → ["@id"]
 * - "customer.@type" → ["customer", "@type"]
 */
function parsePathSegments(path: string): string[] {
    const segments: string[] = [];
    let current = '';
    let inBracket = false;

    for (let i = 0; i < path.length; i++) {
        const ch = path[i];

        if (ch === '[') {
            if (current.length > 0) {
                segments.push(current);
                current = '';
            }
            inBracket = true;
            current = '[';
        } else if (ch === ']') {
            current += ']';
            segments.push(current);
            current = '';
            inBracket = false;
        } else if (ch === '.' && !inBracket) {
            if (current.length > 0) {
                segments.push(current);
                current = '';
            }
        } else {
            current += ch;
        }
    }

    if (current.length > 0) {
        segments.push(current);
    }

    return segments;
}

/**
 * Navigate through UDM using parsed segments
 */
function navigateSegments(udm: UDM, segments: string[]): UDM | string | undefined {
    let current: UDM | string | undefined = udm;

    for (const segment of segments) {
        if (current === undefined || typeof current === 'string') {
            return undefined;
        }

        // Array index access: [0], [1], etc.
        if (segment.startsWith('[') && segment.endsWith(']')) {
            if (!isArray(current)) {
                return undefined;
            }
            const index = parseInt(segment.substring(1, segment.length - 1), 10);
            if (isNaN(index) || index < 0 || index >= current.elements.length) {
                return undefined;
            }
            current = current.elements[index];
            continue;
        }

        // Attribute access: @attr
        if (segment.startsWith('@')) {
            if (!isObject(current)) {
                return undefined;
            }
            const attrName = segment.substring(1);
            return UDMObjectHelper.getAttribute(current, attrName);
        }

        // Property access: field
        if (isObject(current)) {
            current = UDMObjectHelper.get(current, segment);
        } else {
            return undefined;
        }
    }

    return current;
}

/**
 * Get all field paths from a UDM object, for populating completion/tree views
 *
 * @param udm The root UDM object
 * @param includeAttributes Whether to include attributes (with @ prefix)
 * @param maxDepth Maximum depth to traverse (default: 10)
 * @returns Array of path strings
 *
 * Example:
 * ```typescript
 * const paths = getAllPaths(udm, true);
 * // Returns: ["customer", "customer.name", "customer.@id", "items", "items[0]", ...]
 * ```
 */
export function getAllPaths(
    udm: UDM,
    includeAttributes: boolean = true,
    maxDepth: number = 10
): string[] {
    const paths: string[] = [];
    collectPaths(udm, '', paths, includeAttributes, 0, maxDepth);
    return paths;
}

/**
 * Recursively collect all paths from a UDM object
 */
function collectPaths(
    udm: UDM,
    prefix: string,
    paths: string[],
    includeAttributes: boolean,
    depth: number,
    maxDepth: number
): void {
    if (depth >= maxDepth) {
        return;
    }

    if (isObject(udm)) {
        // Add properties
        const propertyKeys = UDMObjectHelper.keys(udm);
        for (const key of propertyKeys) {
            const fieldPath = prefix ? `${prefix}.${key}` : key;
            paths.push(fieldPath);

            const value = UDMObjectHelper.get(udm, key);
            if (value) {
                collectPaths(value, fieldPath, paths, includeAttributes, depth + 1, maxDepth);
            }
        }

        // Add attributes
        if (includeAttributes) {
            const attrKeys = UDMObjectHelper.attributeKeys(udm);
            for (const key of attrKeys) {
                const attrPath = prefix ? `${prefix}.@${key}` : `@${key}`;
                paths.push(attrPath);
            }
        }
    } else if (isArray(udm)) {
        // Add array elements
        for (let i = 0; i < udm.elements.length; i++) {
            const elemPath = `${prefix}[${i}]`;
            paths.push(elemPath);
            collectPaths(udm.elements[i], elemPath, paths, includeAttributes, depth + 1, maxDepth);
        }
    }
    // Scalars and other types are leaf nodes, no further paths
}

/**
 * Get the type of a field at a given path
 *
 * @param udm The root UDM object
 * @param path The path to the field
 * @returns The UDM type name, or undefined if not found
 */
export function getTypeAtPath(udm: UDM, path: string): string | undefined {
    const value = navigate(udm, path);
    if (value === undefined) {
        return undefined;
    }
    if (typeof value === 'string') {
        return 'attribute';
    }
    return value.type;
}

/**
 * Check if a path exists in a UDM object
 *
 * @param udm The root UDM object
 * @param path The path to check
 * @returns True if the path exists
 */
export function pathExists(udm: UDM, path: string): boolean {
    return navigate(udm, path) !== undefined;
}

/**
 * Get a scalar value at a path as a string
 *
 * @param udm The root UDM object
 * @param path The path to the value
 * @returns The value as a string, or undefined if not found or not a scalar
 */
export function getScalarValue(udm: UDM, path: string): string | undefined {
    const value = navigate(udm, path);
    if (value === undefined) {
        return undefined;
    }
    if (typeof value === 'string') {
        return value; // Attribute value
    }
    if (isScalar(value)) {
        return String(value.value);
    }
    return undefined;
}

/**
 * Get all values at a path in an array
 *
 * Useful for extracting sample values from arrays of objects.
 *
 * @param udm The root UDM object
 * @param arrayPath The path to the array
 * @param fieldName The field name within each array element
 * @returns Array of values
 *
 * Example:
 * ```typescript
 * // For $input.items[0].price, items[1].price, etc.
 * const prices = getArrayFieldValues(udm, "items", "price");
 * // Returns: [29.99, 15.50, ...]
 * ```
 */
export function getArrayFieldValues(
    udm: UDM,
    arrayPath: string,
    fieldName: string
): string[] {
    const arrayValue = navigate(udm, arrayPath);
    if (!arrayValue || typeof arrayValue === 'string' || !isArray(arrayValue)) {
        return [];
    }

    const values: string[] = [];
    for (const element of arrayValue.elements) {
        if (isObject(element)) {
            const fieldValue = UDMObjectHelper.get(element, fieldName);
            if (fieldValue && isScalar(fieldValue)) {
                values.push(String(fieldValue.value));
            }
        }
    }
    return values;
}

/**
 * Find all paths matching a pattern (simple wildcard support)
 *
 * @param udm The root UDM object
 * @param pattern The pattern to match (supports * as wildcard)
 * @returns Array of matching paths
 *
 * Example:
 * ```typescript
 * findPaths(udm, "customer.*") // Returns all customer fields
 * findPaths(udm, "*.name") // Returns all name fields at any level
 * ```
 */
export function findPaths(udm: UDM, pattern: string): string[] {
    const allPaths = getAllPaths(udm, true);
    const regex = new RegExp(
        '^' + pattern.replace(/\./g, '\\.').replace(/\*/g, '[^.]*') + '$'
    );
    return allPaths.filter(path => regex.test(path));
}
