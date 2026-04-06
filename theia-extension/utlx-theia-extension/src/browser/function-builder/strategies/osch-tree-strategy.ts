/**
 * OData Schema (EDMX/CSDL) Format Tree Strategy
 *
 * Handles OData metadata schema (EDMX/CSDL) in the Function Builder tree:
 * - EntityType, ComplexType, EnumType are structured types
 * - Properties with Edm.* types map to UTLX canonical types
 * - NavigationProperty entries show as nested entity references
 * - USDL directives (%entityType, %key, %navigation) are metadata, not data
 *
 * Pattern: follows XSD tree strategy since EDMX is also XML-based schema.
 */

import { UDM, UDMObjectHelper, isScalar } from '../../udm/udm-core';
import { UdmField } from '../udm-parser-new';
import { FormatTreeStrategy } from './format-tree-strategy';

export class OSchTreeStrategy implements FormatTreeStrategy {
    shouldFlattenToScalar(udmObject: UDM & { type: 'object' }): boolean {
        // OData schema types have structure - never flatten
        return false;
    }

    filterAttributes(attrKeys: string[]): string[] {
        // Filter out XML namespace declarations (EDMX uses xmlns:edmx, xmlns:edm)
        // Keep meaningful attributes like Name, Type, Nullable
        return attrKeys.filter(key =>
            key !== 'xmlns' &&
            !key.startsWith('xmlns:') &&
            key !== 'xsi'
        );
    }

    getFlattenedType(udmObject: UDM & { type: 'object' }): string {
        // Not used since OData schema types are never flattened
        // Return the schema type name if available from metadata
        const schemaType = UDMObjectHelper.get(udmObject, '%schemaType');
        if (schemaType && isScalar(schemaType)) {
            return String(schemaType.value);
        }
        return 'object';
    }

    getFlattenedChildren(udmObject: UDM & { type: 'object' }): UdmField[] {
        // Not used since OData schema types are never flattened
        return [];
    }
}
