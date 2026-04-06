/**
 * AVRO (Apache Avro) Format Tree Strategy
 *
 * AVRO is a data serialization format with schema:
 * - JSON-like structure
 * - No special text content handling (like XML _text)
 * - No XML-style attributes
 * - Straightforward object/array structure
 *
 * Uses default strategy behavior (similar to JSON).
 */

import { UDM } from '../../udm/udm-core';
import { UdmField } from '../udm-parser-new';
import { FormatTreeStrategy } from './format-tree-strategy';

export class AVROTreeStrategy implements FormatTreeStrategy {
    shouldFlattenToScalar(udmObject: UDM & { type: 'object' }): boolean {
        // AVRO objects are never flattened
        // They maintain their structure like JSON
        return false;
    }

    filterAttributes(attrKeys: string[]): string[] {
        // AVRO doesn't have XML-style attributes
        // Include all attributes if any exist
        return attrKeys;
    }

    getFlattenedType(udmObject: UDM & { type: 'object' }): string {
        // Not used since AVRO never flattens
        return 'object';
    }

    getFlattenedChildren(udmObject: UDM & { type: 'object' }): UdmField[] {
        // Not used since AVRO never flattens
        return [];
    }
}
