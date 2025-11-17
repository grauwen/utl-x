/**
 * YAML Format Tree Strategy
 *
 * YAML is a human-readable data serialization format:
 * - Clean hierarchical structure (like JSON)
 * - No special text content handling (like XML _text)
 * - No XML-style attributes
 * - Straightforward object/array structure
 *
 * Behaves like JSON - no flattening, clean structure.
 */

import { UDM } from '../../udm/udm-core';
import { UdmField } from '../udm-parser-new';
import { FormatTreeStrategy } from './format-tree-strategy';

export class YAMLTreeStrategy implements FormatTreeStrategy {
    shouldFlattenToScalar(udmObject: UDM & { type: 'object' }): boolean {
        // YAML objects are never flattened
        // They maintain their structure like JSON
        return false;
    }

    filterAttributes(attrKeys: string[]): string[] {
        // YAML doesn't have XML-style attributes
        // Include all attributes if any exist
        return attrKeys;
    }

    getFlattenedType(udmObject: UDM & { type: 'object' }): string {
        // Not used since YAML never flattens
        return 'object';
    }

    getFlattenedChildren(udmObject: UDM & { type: 'object' }): UdmField[] {
        // Not used since YAML never flattens
        return [];
    }
}
