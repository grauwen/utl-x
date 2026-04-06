/**
 * UDM Core Type Definitions
 *
 * This module defines the Universal Data Model (UDM) type hierarchy in TypeScript,
 * mirroring the Kotlin implementation from modules/core/src/main/kotlin/org/apache/utlx/core/udm/udm_core.kt
 *
 * Key concepts:
 * - UDM represents data in a format-agnostic way
 * - UDMObject has properties (data fields), attributes (XML attributes), and metadata (internal info)
 * - The properties/attributes/metadata keywords in .udm files are structural, not data fields
 * - Paths like $input.field access properties directly, without "properties" in the path
 */

/**
 * UDM Type Hierarchy
 * Represents all possible UDM value types
 */
export type UDM =
    | UDMScalar
    | UDMArray
    | UDMObject
    | UDMDateTime
    | UDMDate
    | UDMLocalDateTime
    | UDMTime
    | UDMBinary
    | UDMLambda;

/**
 * Scalar value (string, number, boolean, or null)
 */
export interface UDMScalar {
    type: 'scalar';
    value: string | number | boolean | null;
}

/**
 * Object with properties, attributes, and metadata
 *
 * Important: properties, attributes, and metadata are Maps, not field data.
 * When serialized to .udm format:
 * - "properties:" label is only added when attributes/metadata are present
 * - Shorthand format omits "properties:" and serializes properties directly in object body
 * - Paths like $input.field access the properties map, never include "properties" in path
 */
export interface UDMObject {
    type: 'object';
    properties: Map<string, UDM>;       // Actual data fields
    attributes: Map<string, string>;     // XML attributes (key-value pairs)
    name?: string;                       // Element name (for XML)
    metadata: Map<string, string>;       // Internal metadata
}

/**
 * Array of UDM values
 */
export interface UDMArray {
    type: 'array';
    elements: UDM[];
}

/**
 * DateTime value with timezone (ISO 8601 format)
 */
export interface UDMDateTime {
    type: 'datetime';
    value: string;  // ISO 8601 format with timezone
}

/**
 * Date value (ISO 8601 date only)
 */
export interface UDMDate {
    type: 'date';
    value: string;  // ISO 8601 date (YYYY-MM-DD)
}

/**
 * LocalDateTime value without timezone
 */
export interface UDMLocalDateTime {
    type: 'localdatetime';
    value: string;  // ISO 8601 format without timezone
}

/**
 * Time value
 */
export interface UDMTime {
    type: 'time';
    value: string;  // Time portion (HH:mm:ss)
}

/**
 * Binary data
 */
export interface UDMBinary {
    type: 'binary';
    data: Uint8Array;
    encoding?: string;  // e.g., "base64"
    size?: number;      // Size in bytes
}

/**
 * Lambda function reference
 */
export interface UDMLambda {
    type: 'lambda';
    id?: string;    // Lambda identifier
    arity?: number; // Number of parameters
}

/**
 * Type guards for UDM types
 */
export function isScalar(udm: UDM): udm is UDMScalar {
    return udm.type === 'scalar';
}

export function isObject(udm: UDM): udm is UDMObject {
    return udm.type === 'object';
}

export function isArray(udm: UDM): udm is UDMArray {
    return udm.type === 'array';
}

export function isDateTime(udm: UDM): udm is UDMDateTime {
    return udm.type === 'datetime';
}

export function isDate(udm: UDM): udm is UDMDate {
    return udm.type === 'date';
}

export function isLocalDateTime(udm: UDM): udm is UDMLocalDateTime {
    return udm.type === 'localdatetime';
}

export function isTime(udm: UDM): udm is UDMTime {
    return udm.type === 'time';
}

export function isBinary(udm: UDM): udm is UDMBinary {
    return udm.type === 'binary';
}

export function isLambda(udm: UDM): udm is UDMLambda {
    return udm.type === 'lambda';
}

/**
 * Helper methods for working with UDMObject
 * Mirrors the methods from Kotlin UDM.Object class
 */
export class UDMObjectHelper {
    /**
     * Get a property from the object by key
     * This is the method used for path resolution like $input.field
     * Note: Does NOT include "properties" in the path
     */
    static get(obj: UDMObject, key: string): UDM | undefined {
        return obj.properties.get(key);
    }

    /**
     * Get an attribute from the object by key
     * Used for @attribute access in paths
     */
    static getAttribute(obj: UDMObject, key: string): string | undefined {
        return obj.attributes.get(key);
    }

    /**
     * Get metadata from the object by key
     * Typically used internally, not in user-facing paths
     */
    static getMetadata(obj: UDMObject, key: string): string | undefined {
        return obj.metadata.get(key);
    }

    /**
     * Set a property in the object
     */
    static set(obj: UDMObject, key: string, value: UDM): void {
        obj.properties.set(key, value);
    }

    /**
     * Set an attribute in the object
     */
    static setAttribute(obj: UDMObject, key: string, value: string): void {
        obj.attributes.set(key, value);
    }

    /**
     * Set metadata in the object
     */
    static setMetadata(obj: UDMObject, key: string, value: string): void {
        obj.metadata.set(key, value);
    }

    /**
     * Check if object has a property
     */
    static has(obj: UDMObject, key: string): boolean {
        return obj.properties.has(key);
    }

    /**
     * Check if object has an attribute
     */
    static hasAttribute(obj: UDMObject, key: string): boolean {
        return obj.attributes.has(key);
    }

    /**
     * Get all property keys
     */
    static keys(obj: UDMObject): string[] {
        return Array.from(obj.properties.keys());
    }

    /**
     * Get all attribute keys
     */
    static attributeKeys(obj: UDMObject): string[] {
        return Array.from(obj.attributes.keys());
    }
}

/**
 * Factory functions for creating UDM values
 */
export class UDMFactory {
    static scalar(value: string | number | boolean | null): UDMScalar {
        return { type: 'scalar', value };
    }

    static object(
        properties: Map<string, UDM> = new Map(),
        attributes: Map<string, string> = new Map(),
        name?: string,
        metadata: Map<string, string> = new Map()
    ): UDMObject {
        return { type: 'object', properties, attributes, name, metadata };
    }

    static array(elements: UDM[] = []): UDMArray {
        return { type: 'array', elements };
    }

    static datetime(value: string): UDMDateTime {
        return { type: 'datetime', value };
    }

    static date(value: string): UDMDate {
        return { type: 'date', value };
    }

    static localdatetime(value: string): UDMLocalDateTime {
        return { type: 'localdatetime', value };
    }

    static time(value: string): UDMTime {
        return { type: 'time', value };
    }

    static binary(data: Uint8Array, encoding?: string, size?: number): UDMBinary {
        return { type: 'binary', data, encoding, size };
    }

    static lambda(id?: string, arity?: number): UDMLambda {
        return { type: 'lambda', id, arity };
    }
}
