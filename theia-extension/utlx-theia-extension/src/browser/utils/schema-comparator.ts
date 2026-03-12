/**
 * Schema Comparator
 *
 * Compares two SchemaFieldInfo[] trees (expected vs inferred) and produces
 * a structured result showing matches, mismatches, missing, and extra fields.
 *
 * Used by the Validate button in Design-Time mode to compare the user-provided
 * output schema against the inferred output from UTLX transformation.
 */

import { SchemaFieldInfo } from './schema-field-tree-parser';

export type FieldComparisonStatus = 'match' | 'type-mismatch' | 'missing' | 'extra';

export interface FieldComparisonResult {
    fieldName: string;
    status: FieldComparisonStatus;
    expectedType?: string;
    inferredType?: string;
    isRequired?: boolean;
    children?: FieldComparisonResult[];
}

export interface SchemaComparisonResult {
    matchCount: number;
    missingCount: number;
    extraCount: number;
    typeMismatchCount: number;
    fields: FieldComparisonResult[];
    isValid: boolean;  // true if no missing fields and no type mismatches
}

/**
 * Check if two types are compatible.
 *
 * Rules:
 * - Exact match → compatible
 * - integer ↔ number → compatible (integer is a subset of number)
 * - 'any' matches everything → compatible
 * - Otherwise → incompatible
 */
function areTypesCompatible(expectedType: string, inferredType: string): boolean {
    const e = expectedType.toLowerCase();
    const i = inferredType.toLowerCase();

    // Exact match
    if (e === i) return true;

    // 'any' matches everything
    if (e === 'any' || i === 'any') return true;

    // integer ↔ number compatibility
    if ((e === 'integer' && i === 'number') || (e === 'number' && i === 'integer')) return true;

    return false;
}

/**
 * Compare two schema field trees and produce a structured comparison result.
 *
 * Algorithm:
 * 1. Build a map of inferred fields by name at the current level
 * 2. Iterate expected fields:
 *    - If present in inferred, check type compatibility
 *    - If both have children, recurse
 *    - If not present in inferred → 'missing'
 * 3. Iterate remaining inferred fields not in expected → 'extra'
 * 4. Aggregate counts recursively
 */
export function compareSchemas(
    expectedFields: SchemaFieldInfo[],
    inferredFields: SchemaFieldInfo[]
): SchemaComparisonResult {
    const result = compareFieldLists(expectedFields, inferredFields);

    // Count totals (including nested)
    const counts = countResults(result);

    return {
        matchCount: counts.match,
        missingCount: counts.missing,
        extraCount: counts.extra,
        typeMismatchCount: counts.typeMismatch,
        fields: result,
        isValid: counts.missing === 0 && counts.typeMismatch === 0
    };
}

/**
 * Compare two lists of fields at the same level.
 */
function compareFieldLists(
    expectedFields: SchemaFieldInfo[],
    inferredFields: SchemaFieldInfo[]
): FieldComparisonResult[] {
    const results: FieldComparisonResult[] = [];

    // Build map of inferred fields by name for O(1) lookup
    const inferredMap = new Map<string, SchemaFieldInfo>();
    for (const field of inferredFields) {
        inferredMap.set(field.name, field);
    }

    // Track which inferred fields have been matched
    const matchedInferredNames = new Set<string>();

    // 1. Iterate expected fields
    for (const expectedField of expectedFields) {
        const inferredField = inferredMap.get(expectedField.name);

        if (!inferredField) {
            // Field is in expected but not in inferred → missing
            results.push({
                fieldName: expectedField.name,
                status: 'missing',
                expectedType: expectedField.type,
                isRequired: expectedField.isRequired
            });
        } else {
            matchedInferredNames.add(expectedField.name);

            // Check type compatibility
            const compatible = areTypesCompatible(
                expectedField.type || 'any',
                inferredField.type || 'any'
            );

            const comparison: FieldComparisonResult = {
                fieldName: expectedField.name,
                status: compatible ? 'match' : 'type-mismatch',
                expectedType: expectedField.type,
                inferredType: inferredField.type,
                isRequired: expectedField.isRequired
            };

            // If both have children, recurse
            const expectedChildren = expectedField.fields as SchemaFieldInfo[] | undefined;
            const inferredChildren = inferredField.fields as SchemaFieldInfo[] | undefined;

            if (expectedChildren && expectedChildren.length > 0 ||
                inferredChildren && inferredChildren.length > 0) {
                comparison.children = compareFieldLists(
                    expectedChildren || [],
                    inferredChildren || []
                );
            }

            results.push(comparison);
        }
    }

    // 2. Iterate remaining inferred fields not in expected → extra
    for (const inferredField of inferredFields) {
        if (!matchedInferredNames.has(inferredField.name)) {
            results.push({
                fieldName: inferredField.name,
                status: 'extra',
                inferredType: inferredField.type
            });
        }
    }

    return results;
}

/**
 * Recursively count comparison results across all levels.
 */
function countResults(fields: FieldComparisonResult[]): {
    match: number;
    missing: number;
    extra: number;
    typeMismatch: number;
} {
    let match = 0;
    let missing = 0;
    let extra = 0;
    let typeMismatch = 0;

    for (const field of fields) {
        switch (field.status) {
            case 'match':
                match++;
                break;
            case 'missing':
                missing++;
                break;
            case 'extra':
                extra++;
                break;
            case 'type-mismatch':
                typeMismatch++;
                break;
        }

        // Recurse into children
        if (field.children) {
            const childCounts = countResults(field.children);
            match += childCounts.match;
            missing += childCounts.missing;
            extra += childCounts.extra;
            typeMismatch += childCounts.typeMismatch;
        }
    }

    return { match, missing, extra, typeMismatch };
}
