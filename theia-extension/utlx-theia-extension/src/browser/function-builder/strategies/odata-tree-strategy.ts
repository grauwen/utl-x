/**
 * OData Format Tree Strategy
 *
 * OData JSON handling that filters @odata.* annotation keys from the tree:
 * - @odata.context, @odata.type, @odata.id, @odata.etag are metadata, not data
 * - These are stored as UDM attributes and should not appear as tree properties
 * - Shows @odata.type as a type indicator on entity nodes (via attributes)
 * - Navigation link indicators for Property@odata.navigationLink
 */

import { UDM } from '../../udm/udm-core';
import { UdmField } from '../udm-parser-new';
import { FormatTreeStrategy } from './format-tree-strategy';

export class ODataTreeStrategy implements FormatTreeStrategy {
    shouldFlattenToScalar(udmObject: UDM & { type: 'object' }): boolean {
        // OData objects are never flattened - same as JSON
        return false;
    }

    filterAttributes(attrKeys: string[]): string[] {
        // Filter out @odata.* annotations from tree display
        // These are metadata, not data properties
        // Keep odata.type as it provides useful type information for the UI
        return attrKeys.filter(key => {
            if (key.startsWith('odata.')) {
                // Only show odata.type as a badge, filter out the rest
                return key === 'odata.type';
            }
            return true;
        });
    }

    getFlattenedType(udmObject: UDM & { type: 'object' }): string {
        // Not used since OData never flattens objects to scalars
        // But if the object has an odata.type attribute, return it
        const odataType = udmObject.attributes.get('odata.type');
        if (odataType) {
            // Extract short type name from full OData type (e.g., "#Products.Product" → "Product")
            const parts = odataType.split('.');
            return parts[parts.length - 1];
        }
        return 'object';
    }

    getFlattenedChildren(udmObject: UDM & { type: 'object' }): UdmField[] {
        // Not used since OData never flattens objects to scalars
        return [];
    }
}
