/**
 * UDM Language Serializer
 *
 * Serializes UDM structures to UDM Language format (.udm files)
 * Ported from modules/core/src/main/kotlin/org/apache/utlx/core/udm/UDMLanguageSerializer.kt
 *
 * UDM Language is a meta-format that preserves complete UDM model state including:
 * - Type information (Scalar, Array, Object, DateTime, etc.)
 * - Metadata (source info, line numbers, validation state)
 * - Attributes (XML attributes, hints)
 * - Element names (XML context)
 *
 * This is different from standard YAML/JSON serialization which loses UDM metadata.
 *
 * Example:
 * ```typescript
 * const udm = UDMFactory.object(...);
 * const serializer = new UDMLanguageSerializer();
 * const udmLang = serializer.serialize(udm);
 * await fs.writeFile('output.udm', udmLang);
 * ```
 */

import { UDM, isScalar, isArray, isObject, isBinary, isDateTime, isDate, isLocalDateTime, isTime, isLambda } from './udm-core';

/**
 * Options for UDM Language serialization
 */
export interface SerializerOptions {
    prettyPrint?: boolean;
    indentSize?: number;
}

/**
 * Source information for UDM header
 */
export interface SourceInfo {
    source?: string;
    'parsed-at'?: string;
    [key: string]: string | undefined;
}

/**
 * UDM Language Serializer
 */
export class UDMLanguageSerializer {
    private readonly prettyPrint: boolean;
    private readonly indentSize: number;

    constructor(options: SerializerOptions = {}) {
        this.prettyPrint = options.prettyPrint ?? true;
        this.indentSize = options.indentSize ?? 2;
    }

    /**
     * Serialize UDM to UDM Language format
     */
    serialize(udm: UDM, sourceInfo: SourceInfo = {}): string {
        const sb: string[] = [];

        // Header
        sb.push('@udm-version: 1.0\n');
        if (Object.keys(sourceInfo).length > 0) {
            if (sourceInfo.source) {
                sb.push(`@source: "${sourceInfo.source}"\n`);
            }
            if (sourceInfo['parsed-at']) {
                sb.push(`@parsed-at: "${sourceInfo['parsed-at']}"\n`);
            }
        }
        sb.push('\n');

        // Body
        this.serializeValue(udm, sb, 0);

        return sb.join('');
    }

    private serializeValue(udm: UDM, sb: string[], depth: number): void {
        if (isScalar(udm)) {
            this.serializeScalar(udm, sb);
        } else if (isArray(udm)) {
            this.serializeArray(udm, sb, depth);
        } else if (isObject(udm)) {
            this.serializeObject(udm, sb, depth);
        } else if (isDateTime(udm)) {
            sb.push(`@DateTime("${udm.value}")`);
        } else if (isDate(udm)) {
            sb.push(`@Date("${udm.value}")`);
        } else if (isLocalDateTime(udm)) {
            sb.push(`@LocalDateTime("${udm.value}")`);
        } else if (isTime(udm)) {
            sb.push(`@Time("${udm.value}")`);
        } else if (isBinary(udm)) {
            this.serializeBinary(udm, sb);
        } else if (isLambda(udm)) {
            sb.push('@Lambda()');
        }
    }

    private serializeScalar(scalar: UDM & { type: 'scalar' }, sb: string[]): void {
        const value = scalar.value;

        if (value === null) {
            sb.push('null');
        } else if (typeof value === 'string') {
            sb.push(`"${this.escapeString(value)}"`);
        } else if (typeof value === 'boolean') {
            sb.push(value.toString());
        } else if (typeof value === 'number') {
            sb.push(value.toString());
        } else {
            // Explicit type annotation for non-standard types
            sb.push(`@Scalar<String>("${this.escapeString(String(value))}")`);
        }
    }

    private serializeArray(array: UDM & { type: 'array' }, sb: string[], depth: number): void {
        if (array.elements.length === 0) {
            sb.push('[]');
            return;
        }

        sb.push('[');
        if (this.prettyPrint) sb.push('\n');

        array.elements.forEach((element, index) => {
            if (this.prettyPrint) sb.push(this.indent(depth + 1));
            this.serializeValue(element, sb, depth + 1);
            if (index < array.elements.length - 1) {
                sb.push(',');
            }
            if (this.prettyPrint) sb.push('\n');
        });

        if (this.prettyPrint) sb.push(this.indent(depth));
        sb.push(']');
    }

    private serializeObject(obj: UDM & { type: 'object' }, sb: string[], depth: number): void {
        // Check if we need explicit @Object annotation
        const needsAnnotation = obj.name !== undefined || obj.metadata.size > 0;

        if (needsAnnotation) {
            sb.push('@Object');
            this.serializeObjectMeta(obj, sb, depth);
            sb.push(' ');
        }

        sb.push('{');
        if (this.prettyPrint) sb.push('\n');

        // Attributes section
        if (obj.attributes.size > 0) {
            if (this.prettyPrint) sb.push(this.indent(depth + 1));
            sb.push('attributes: {');
            if (this.prettyPrint) sb.push('\n');

            const attrEntries = Array.from(obj.attributes.entries());
            attrEntries.forEach(([key, value], index) => {
                if (this.prettyPrint) sb.push(this.indent(depth + 2));
                sb.push(`${key}: "${this.escapeString(value)}"`);
                if (index < attrEntries.length - 1) {
                    sb.push(',');
                }
                if (this.prettyPrint) sb.push('\n');
            });

            if (this.prettyPrint) sb.push(this.indent(depth + 1));
            sb.push('},');
            if (this.prettyPrint) sb.push('\n');
        }

        // Properties section
        if (obj.attributes.size > 0 || needsAnnotation) {
            // Need explicit properties label
            if (this.prettyPrint) sb.push(this.indent(depth + 1));
            sb.push('properties: {');
            if (this.prettyPrint) sb.push('\n');
            this.serializeProperties(obj.properties, sb, depth + 2);
            if (this.prettyPrint) sb.push(this.indent(depth + 1));
            sb.push('}');
            if (this.prettyPrint) sb.push('\n');
        } else {
            // Shorthand: properties directly in object
            this.serializeProperties(obj.properties, sb, depth + 1);
        }

        if (this.prettyPrint) sb.push(this.indent(depth));
        sb.push('}');
    }

    private serializeObjectMeta(obj: UDM & { type: 'object' }, sb: string[], depth: number): void {
        if (obj.name === undefined && obj.metadata.size === 0) return;

        sb.push('(');
        if (this.prettyPrint) sb.push('\n');

        const entries: string[] = [];

        // Name
        if (obj.name !== undefined) {
            entries.push(`${this.indent(depth + 1)}name: "${this.escapeString(obj.name)}"`);
        }

        // Metadata
        if (obj.metadata.size > 0) {
            const metadataParts: string[] = [];
            metadataParts.push(`${this.indent(depth + 1)}metadata:`);

            if (this.prettyPrint) {
                metadataParts.push('\n');
                metadataParts.push(`${this.indent(depth + 1)}{`);
                metadataParts.push('\n');

                const metaEntries = Array.from(obj.metadata.entries());
                metaEntries.forEach(([key, value], index) => {
                    metadataParts.push(`${this.indent(depth + 2)}${key}: "${this.escapeString(value)}"`);
                    if (index < metaEntries.length - 1) {
                        metadataParts.push(',');
                    }
                    metadataParts.push('\n');
                });

                metadataParts.push(`${this.indent(depth + 1)}}`);
            } else {
                metadataParts.push(' {');
                const metaEntries = Array.from(obj.metadata.entries());
                metaEntries.forEach(([key, value], index) => {
                    metadataParts.push(`${key}: "${this.escapeString(value)}"`);
                    if (index < metaEntries.length - 1) {
                        metadataParts.push(', ');
                    }
                });
                metadataParts.push('}');
            }

            entries.push(metadataParts.join(''));
        }

        sb.push(entries.join(',\n'));
        if (this.prettyPrint) sb.push('\n');
        if (this.prettyPrint) sb.push(this.indent(depth));
        sb.push(')');
    }

    private serializeProperties(properties: Map<string, UDM>, sb: string[], depth: number): void {
        const propEntries = Array.from(properties.entries());
        propEntries.forEach(([key, value], index) => {
            if (this.prettyPrint) sb.push(this.indent(depth));

            // Quote key if it contains special characters or is a reserved word
            const quotedKey = this.needsQuoting(key) ? `"${this.escapeString(key)}"` : key;
            sb.push(`${quotedKey}: `);

            this.serializeValue(value, sb, depth);

            if (index < propEntries.length - 1) {
                sb.push(',');
            }
            if (this.prettyPrint) sb.push('\n');
        });
    }

    private serializeBinary(binary: UDM & { type: 'binary' }, sb: string[]): void {
        // For now, just serialize size and indicate it's binary
        sb.push(`@Binary(size: ${binary.data.length})`);
        // TODO: Support base64 inline or external reference
    }

    private indent(depth: number): string {
        return this.prettyPrint ? ' '.repeat(depth * this.indentSize) : '';
    }

    private escapeString(str: string): string {
        return str
            .replace(/\\/g, '\\\\')
            .replace(/"/g, '\\"')
            .replace(/\n/g, '\\n')
            .replace(/\r/g, '\\r')
            .replace(/\t/g, '\\t');
    }

    private needsQuoting(key: string): boolean {
        // Quote if key contains special characters or starts with @
        return /[^a-zA-Z0-9_]/.test(key) || key.startsWith('@');
    }
}

/**
 * Convenience function for serializing UDM
 */
export function toUDMLanguage(udm: UDM, prettyPrint: boolean = true, sourceInfo: SourceInfo = {}): string {
    const serializer = new UDMLanguageSerializer({ prettyPrint });
    return serializer.serialize(udm, sourceInfo);
}
