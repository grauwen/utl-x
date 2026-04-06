/**
 * CSV Format Tree Strategy
 *
 * CSV is typically arrays of flat objects (tabular data):
 * - Each row is an object with column fields
 * - No flattening (each row is an object)
 * - No attributes (CSV is purely tabular)
 * - Straightforward structure
 */

import { UDM } from '../../udm/udm-core';
import { UdmField } from '../udm-parser-new';
import { FormatTreeStrategy } from './format-tree-strategy';

export class CSVTreeStrategy implements FormatTreeStrategy {
    shouldFlattenToScalar(udmObject: UDM & { type: 'object' }): boolean {
        // CSV rows are never flattened
        // Each row is an object with field values
        return false;
    }

    filterAttributes(attrKeys: string[]): string[] {
        // CSV doesn't have attributes - it's purely tabular data
        return attrKeys;
    }

    getFlattenedType(udmObject: UDM & { type: 'object' }): string {
        // Not used since CSV never flattens
        return 'object';
    }

    getFlattenedChildren(udmObject: UDM & { type: 'object' }): UdmField[] {
        // Not used since CSV never flattens
        return [];
    }
}
