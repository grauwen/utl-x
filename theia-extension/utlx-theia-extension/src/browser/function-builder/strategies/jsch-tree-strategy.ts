/**
 * JSON Schema (JSCH) Format Tree Strategy
 *
 * JSON Schema is a schema definition format based on JSON:
 * - JSON-like structure
 * - No special text content handling (like XML _text)
 * - No XML-style attributes
 * - Straightforward object/array structure
 *
 * Uses default strategy behavior (same as JSON).
 */

import { UDM } from '../../udm/udm-core';
import { UdmField } from '../udm-parser-new';
import { FormatTreeStrategy } from './format-tree-strategy';

export class JSCHTreeStrategy implements FormatTreeStrategy {
    shouldFlattenToScalar(udmObject: UDM & { type: 'object' }): boolean {
        // JSON Schema objects are never flattened
        // They maintain their structure like JSON
        return false;
    }

    filterAttributes(attrKeys: string[]): string[] {
        // JSON Schema doesn't have XML-style attributes
        // Include all attributes if any exist
        return attrKeys;
    }

    getFlattenedType(udmObject: UDM & { type: 'object' }): string {
        // Not used since JSON Schema never flattens
        return 'object';
    }

    getFlattenedChildren(udmObject: UDM & { type: 'object' }): UdmField[] {
        // Not used since JSON Schema never flattens
        return [];
    }
}
