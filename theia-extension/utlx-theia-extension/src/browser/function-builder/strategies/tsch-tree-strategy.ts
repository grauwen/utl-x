/**
 * Table Schema (TSCH) Format Tree Strategy
 *
 * Table Schema is a schema definition format based on JSON:
 * - JSON-like structure (Frictionless Table Schema)
 * - No special text content handling (like XML _text)
 * - No XML-style attributes
 * - Straightforward object/array structure
 *
 * Uses default strategy behavior (same as JSON/JSCH).
 */

import { UDM } from '../../udm/udm-core';
import { UdmField } from '../udm-parser-new';
import { FormatTreeStrategy } from './format-tree-strategy';

export class TSCHTreeStrategy implements FormatTreeStrategy {
    shouldFlattenToScalar(udmObject: UDM & { type: 'object' }): boolean {
        // Table Schema objects are never flattened
        // They maintain their structure like JSON
        return false;
    }

    filterAttributes(attrKeys: string[]): string[] {
        // Table Schema doesn't have XML-style attributes
        // Include all attributes if any exist
        return attrKeys;
    }

    getFlattenedType(udmObject: UDM & { type: 'object' }): string {
        // Not used since Table Schema never flattens
        return 'object';
    }

    getFlattenedChildren(udmObject: UDM & { type: 'object' }): UdmField[] {
        // Not used since Table Schema never flattens
        return [];
    }
}
