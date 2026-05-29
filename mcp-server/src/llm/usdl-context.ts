/**
 * USDL context for UTLX generation prompts.
 *
 * Formats fall into two tiers:
 *   - Tier 1 (DATA): json, xml, csv, yaml, odata — raw value carriers.
 *   - Tier 2 (SCHEMA): xsd, json-schema, avro, protobuf, openapi, sql, ... —
 *     schema formats whose structure is expressed in UTLX via USDL `%`-directives
 *     (%types, %fields, %kind, ...).
 *
 * When a Tier 2 format is involved (as input or output) the model needs to know
 * the USDL directive vocabulary, so this module assembles a prompt section from
 * the daemon's directive registry. Tier 1 formats need no USDL.
 */

import { DaemonClient } from '../client/DaemonClient';
import { Logger } from 'winston';
import { DirectiveInfo } from '../types/usdl';
import { loadRegistry } from '../tools/getUsdlDirectives';

// Tier 1 data formats. `odata` lives here (OData entity DATA); its SCHEMA
// counterpart is EDMX, which is Tier 2 below. (TSV is not separate — it is CSV
// with a tab delimiter set in the header.)
const TIER1_DATA_FORMATS = new Set(['json', 'xml', 'csv', 'yaml', 'odata']);

// Tier 2 schema formats and common aliases → the registry's format key.
const SCHEMA_FORMAT_TO_REGISTRY_KEY: Record<string, string> = {
  xsd: 'xsd',
  jsch: 'jsch',
  'json-schema': 'jsch',
  jsonschema: 'jsch',
  proto: 'proto',
  protobuf: 'proto',
  avro: 'avro',
  avsc: 'avsc',
  openapi: 'openapi',
  raml: 'raml',
  apiblueprint: 'apiblueprint',
  asyncapi: 'asyncapi',
  grpc: 'grpc',
  sql: 'sql',
  graphql: 'graphql',
  thrift: 'thrift',
  parquet: 'parquet',
  capnp: 'capnp',
  flatbuf: 'flatbuf',
  // OData schema (EDMX) maps to the registry's 'odata' metadata.
  edmx: 'odata',
  osch: 'odata',
};

function norm(format: string): string {
  return format.trim().toLowerCase();
}

export function isTier1DataFormat(format: string): boolean {
  return TIER1_DATA_FORMATS.has(norm(format));
}

export function isTier2SchemaFormat(format: string): boolean {
  const f = norm(format);
  return !TIER1_DATA_FORMATS.has(f) && f in SCHEMA_FORMAT_TO_REGISTRY_KEY;
}

/**
 * Build a USDL prompt section when any of the given formats (inputs + output)
 * are Tier 2 schema formats. Returns undefined when none are, or when the
 * registry can't be loaded (the caller proceeds without it).
 */
export async function buildUsdlContext(
  daemonClient: DaemonClient,
  logger: Logger,
  formats: string[]
): Promise<string | undefined> {
  const schemaFormats = [...new Set(formats.map(norm).filter(isTier2SchemaFormat))];
  if (schemaFormats.length === 0) {
    return undefined;
  }

  let registry;
  try {
    registry = await loadRegistry(daemonClient, logger);
  } catch (error) {
    logger.warn('Could not load USDL registry for prompt; proceeding without it', { error });
    return undefined;
  }

  const registryKeys = [
    ...new Set(schemaFormats.map(f => SCHEMA_FORMAT_TO_REGISTRY_KEY[f]).filter(Boolean)),
  ];

  // Directive names supported by the involved schema formats.
  const supported = new Set<string>();
  for (const key of registryKeys) {
    for (const d of registry.formats?.[key]?.supportedDirectives ?? []) {
      supported.add(d);
    }
  }

  const core = registry.tiers?.core ?? [];
  const common = (registry.tiers?.common ?? []).filter(d => supported.has(d.name));

  const line = (d: DirectiveInfo) =>
    `- \`${d.name}\` (${d.valueType}${d.required ? ', required' : ''}) — ${d.description}`;

  let section = '';
  section += `One or more formats here are **SCHEMA formats (Tier 2)**: ${schemaFormats.join(', ')}.\n\n`;
  section += `Schema formats are represented in UTLX through **USDL \`%\`-directives** — a ` +
    `format-agnostic schema model. This affects your transformation:\n`;
  section += `- When an INPUT is a schema format, its parsed UDM is enriched with these ` +
    `\`%\`-properties — read them (e.g. \`$input["%types"]\`, \`$input["%fields"]\`).\n`;
  section += `- When the OUTPUT is a schema format, your transformation must PRODUCE these ` +
    `\`%\`-directives; the engine serializes them to ${schemaFormats.join('/')}.\n\n`;

  if (core.length > 0) {
    section += `Required core directives (every schema needs these):\n`;
    section += core.map(line).join('\n') + '\n';
  }
  if (common.length > 0) {
    section += `\nCommon directives supported by ${registryKeys.join(', ')}:\n`;
    section += common.map(line).join('\n') + '\n';
  }
  section += `\nFor the complete directive catalog and examples, use the get_usdl_directives tool.\n`;

  return section;
}
