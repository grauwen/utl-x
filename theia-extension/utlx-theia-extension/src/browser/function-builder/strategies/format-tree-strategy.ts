/**
 * Format Tree Strategy Interface
 *
 * Defines the contract for format-specific UDM tree building strategies.
 * Each format (XML, JSON, CSV, etc.) implements this interface to handle
 * format-specific quirks without coupling formats together.
 */

import { UDM } from '../../udm/udm-core';
import { UdmField } from '../udm-parser-new';

/**
 * Strategy interface for format-specific UDM tree building
 */
export interface FormatTreeStrategy {
    /**
     * Determine if a UDM object should be flattened to a scalar value
     *
     * Example: XML elements with only _text content can be flattened to strings
     *
     * @param udmObject The UDM object to check
     * @returns true if this object should be treated as a scalar field
     */
    shouldFlattenToScalar(udmObject: UDM & { type: 'object' }): boolean;

    /**
     * Filter attributes to include in the tree
     *
     * Example: XML filters out xmlns/xsi namespace declarations
     *
     * @param attrKeys All attribute keys from the object
     * @returns Filtered list of attribute keys to show in tree
     */
    filterAttributes(attrKeys: string[]): string[];

    /**
     * Get the scalar type name for a flattened object
     *
     * @param udmObject The object being flattened
     * @returns The type name to use (e.g., 'string', 'number')
     */
    getFlattenedType(udmObject: UDM & { type: 'object' }): string;

    /**
     * Extract child fields that should be shown when object is flattened
     *
     * Example: XML attributes when element has text content
     *
     * @param udmObject The object being flattened
     * @returns Array of child fields (typically attributes)
     */
    getFlattenedChildren(udmObject: UDM & { type: 'object' }): UdmField[];
}
