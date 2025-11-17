/**
 * JSON Format Tree Strategy
 *
 * Standard JSON handling with no special cases:
 * - Objects are always objects (never flattened to scalars)
 * - No attribute filtering (JSON doesn't have attributes in the XML sense)
 * - Clean, straightforward object/array structure
 */

import { UDM } from '../../udm/udm-core';
import { UdmField } from '../udm-parser-new';
import { FormatTreeStrategy } from './format-tree-strategy';

export class JSONTreeStrategy implements FormatTreeStrategy {
    shouldFlattenToScalar(udmObject: UDM & { type: 'object' }): boolean {
        // JSON objects are never flattened - they're always objects
        // { "name": "value" } stays as an object, never becomes a scalar
        return false;
    }

    filterAttributes(attrKeys: string[]): string[] {
        // JSON doesn't have attributes in the XML sense
        // If there are any (unlikely in pure JSON), include them all
        return attrKeys;
    }

    getFlattenedType(udmObject: UDM & { type: 'object' }): string {
        // Not used since JSON never flattens objects to scalars
        return 'object';
    }

    getFlattenedChildren(udmObject: UDM & { type: 'object' }): UdmField[] {
        // Not used since JSON never flattens objects to scalars
        return [];
    }
}
