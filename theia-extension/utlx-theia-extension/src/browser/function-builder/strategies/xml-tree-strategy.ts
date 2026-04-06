/**
 * XML Format Tree Strategy
 *
 * Handles XML-specific tree building concerns:
 * - Elements with only _text are flattened to scalars (e.g., <Name>John</Name> → Name: string)
 * - Attributes are shown as children with @ prefix (e.g., <Price currency="USD">99</Price>)
 * - Namespace attributes (xmlns, xsi) are filtered out as non-meaningful for data access
 */

import { UDM, UDMObjectHelper, isScalar } from '../../udm/udm-core';
import { UdmField } from '../udm-parser-new';
import { FormatTreeStrategy } from './format-tree-strategy';

export class XMLTreeStrategy implements FormatTreeStrategy {
    shouldFlattenToScalar(udmObject: UDM & { type: 'object' }): boolean {
        const propertyKeys = UDMObjectHelper.keys(udmObject);

        // XML elements with ONLY _text property should be flattened to scalars
        // Example: <Name>John</Name> becomes Name (string) instead of Name (object)
        return propertyKeys.length === 1 && propertyKeys[0] === '_text';
    }

    filterAttributes(attrKeys: string[]): string[] {
        // Filter out XML namespace declarations (not useful for data access)
        // xmlns, xsi, and xmlns:* are framework-level attributes
        return attrKeys.filter(key =>
            key !== 'xmlns' &&
            key !== 'xsi' &&
            !key.startsWith('xmlns:')
        );
    }

    getFlattenedType(udmObject: UDM & { type: 'object' }): string {
        const textValue = UDMObjectHelper.get(udmObject, '_text');
        if (textValue && isScalar(textValue)) {
            return this.getScalarTypeName(textValue.value);
        }
        return 'string';
    }

    getFlattenedChildren(udmObject: UDM & { type: 'object' }): UdmField[] {
        const attrKeys = UDMObjectHelper.attributeKeys(udmObject);
        const meaningfulAttrs = this.filterAttributes(attrKeys);

        // Return attributes as child fields with @ prefix
        // Example: currency="USD" becomes @currency (string)
        return meaningfulAttrs.map(key => ({
            name: `@${key}`,
            type: 'string', // XML attributes are always strings
            description: `Attribute: ${UDMObjectHelper.getAttribute(udmObject, key)}`
        }));
    }

    private getScalarTypeName(value: string | number | boolean | null): string {
        if (value === null) return 'null';
        if (typeof value === 'string') return 'string';
        if (typeof value === 'number') return 'number';
        if (typeof value === 'boolean') return 'boolean';
        return 'unknown';
    }
}
