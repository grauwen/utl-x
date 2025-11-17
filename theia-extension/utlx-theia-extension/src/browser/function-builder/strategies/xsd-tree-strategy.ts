/**
 * XSD (XML Schema) Format Tree Strategy
 *
 * XSD is XML-based schema definition, so it shares some characteristics with XML:
 * - May have _text content in some elements
 * - Has XML namespaces and attributes
 * - Schema-specific constructs (complexType, simpleType, etc.)
 *
 * Currently uses XML-like handling, but can be customized independently for XSD-specific needs.
 */

import { UDM, UDMObjectHelper, isScalar } from '../../udm/udm-core';
import { UdmField } from '../udm-parser-new';
import { FormatTreeStrategy } from './format-tree-strategy';

export class XSDTreeStrategy implements FormatTreeStrategy {
    shouldFlattenToScalar(udmObject: UDM & { type: 'object' }): boolean {
        const propertyKeys = UDMObjectHelper.keys(udmObject);

        // XSD elements with only _text can be flattened (similar to XML)
        // Example: <xs:documentation>Description text</xs:documentation>
        return propertyKeys.length === 1 && propertyKeys[0] === '_text';
    }

    filterAttributes(attrKeys: string[]): string[] {
        // Filter out XML namespace declarations (like regular XML)
        // XSD uses xmlns:xs, xmlns:xsd heavily - these are framework-level
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

        return meaningfulAttrs.map(key => ({
            name: `@${key}`,
            type: 'string',
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
