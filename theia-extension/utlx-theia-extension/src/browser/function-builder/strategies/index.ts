/**
 * Format Tree Strategies - Index
 *
 * Exports all format strategies and provides a factory function
 * to get the appropriate strategy for a given format.
 */

export { FormatTreeStrategy } from './format-tree-strategy';
export { XMLTreeStrategy } from './xml-tree-strategy';
export { XSDTreeStrategy } from './xsd-tree-strategy';
export { JSONTreeStrategy } from './json-tree-strategy';
export { JSCHTreeStrategy } from './jsch-tree-strategy';
export { YAMLTreeStrategy } from './yaml-tree-strategy';
export { CSVTreeStrategy } from './csv-tree-strategy';
export { AVROTreeStrategy } from './avro-tree-strategy';
export { ProtoTreeStrategy } from './proto-tree-strategy';
export { ODataTreeStrategy } from './odata-tree-strategy';
export { OSchTreeStrategy } from './osch-tree-strategy';
export { TSCHTreeStrategy } from './tsch-tree-strategy';

import { FormatTreeStrategy } from './format-tree-strategy';
import { XMLTreeStrategy } from './xml-tree-strategy';
import { XSDTreeStrategy } from './xsd-tree-strategy';
import { JSONTreeStrategy } from './json-tree-strategy';
import { JSCHTreeStrategy } from './jsch-tree-strategy';
import { YAMLTreeStrategy } from './yaml-tree-strategy';
import { CSVTreeStrategy } from './csv-tree-strategy';
import { AVROTreeStrategy } from './avro-tree-strategy';
import { ProtoTreeStrategy } from './proto-tree-strategy';
import { ODataTreeStrategy } from './odata-tree-strategy';
import { OSchTreeStrategy } from './osch-tree-strategy';
import { TSCHTreeStrategy } from './tsch-tree-strategy';

/**
 * Get the appropriate tree building strategy for a given format
 *
 * @param format Format string (xml, xsd, json, jsch, yaml, csv, avro, proto, odata, osch)
 * @returns Format-specific strategy instance
 * @throws Error if format is not recognized (all IDE formats should be defined)
 */
export function getStrategyForFormat(format: string): FormatTreeStrategy {
    const normalizedFormat = format.toLowerCase();

    switch (normalizedFormat) {
        case 'xml':
            return new XMLTreeStrategy();

        case 'xsd':
            return new XSDTreeStrategy();

        case 'json':
            return new JSONTreeStrategy();

        case 'jsch':
            return new JSCHTreeStrategy();

        case 'yaml':
            return new YAMLTreeStrategy();

        case 'csv':
            return new CSVTreeStrategy();

        case 'avro':
            return new AVROTreeStrategy();

        case 'proto':
            return new ProtoTreeStrategy();

        case 'odata':
            return new ODataTreeStrategy();

        case 'osch':
            return new OSchTreeStrategy();

        case 'tsch':
            return new TSCHTreeStrategy();

        default:
            // All formats should be explicitly defined in IDE
            throw new Error(`Unknown format: ${format}. All formats must have a dedicated strategy.`);
    }
}
