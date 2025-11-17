/**
 * Protocol Buffers (Proto) Format Tree Strategy
 *
 * Protocol Buffers is a strongly-typed data serialization format:
 * - Structured like JSON (objects, arrays, scalars)
 * - No special text content handling (like XML _text)
 * - No XML-style attributes
 * - Clean hierarchical structure
 *
 * Uses default strategy behavior (similar to JSON).
 */

import { UDM } from '../../udm/udm-core';
import { UdmField } from '../udm-parser-new';
import { FormatTreeStrategy } from './format-tree-strategy';

export class ProtoTreeStrategy implements FormatTreeStrategy {
    shouldFlattenToScalar(udmObject: UDM & { type: 'object' }): boolean {
        // Proto messages are never flattened
        // They maintain their structure like JSON
        return false;
    }

    filterAttributes(attrKeys: string[]): string[] {
        // Proto doesn't have XML-style attributes
        // Include all attributes if any exist
        return attrKeys;
    }

    getFlattenedType(udmObject: UDM & { type: 'object' }): string {
        // Not used since Proto never flattens
        return 'object';
    }

    getFlattenedChildren(udmObject: UDM & { type: 'object' }): UdmField[] {
        // Not used since Proto never flattens
        return [];
    }
}
