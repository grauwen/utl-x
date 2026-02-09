/**
 * Schema Inferrer - Generates JSON Schema from instance data
 *
 * Analyzes JSON/XML instance documents and generates corresponding schemas.
 */

export interface InferredSchema {
    $schema: string;
    type?: string;
    properties?: Record<string, InferredSchema>;
    items?: InferredSchema;
    required?: string[];
    description?: string;
}

/**
 * Infer JSON Schema from a JSON instance document
 */
export function inferJsonSchema(instance: any, description?: string): InferredSchema {
    const schema: InferredSchema = {
        $schema: 'https://json-schema.org/draft/2020-12/schema'
    };

    if (description) {
        schema.description = description;
    }

    Object.assign(schema, inferType(instance));
    return schema;
}

/**
 * Infer type information from a value
 */
function inferType(value: any): Partial<InferredSchema> {
    if (value === null) {
        return { type: 'null' };
    }

    if (Array.isArray(value)) {
        return inferArrayType(value);
    }

    switch (typeof value) {
        case 'string':
            return inferStringType(value);
        case 'number':
            return inferNumberType(value);
        case 'boolean':
            return { type: 'boolean' };
        case 'object':
            return inferObjectType(value);
        default:
            return {};
    }
}

/**
 * Infer string type with format detection
 */
function inferStringType(value: string): Partial<InferredSchema> {
    const result: Partial<InferredSchema> = { type: 'string' };

    // Detect common formats
    if (isDateTimeString(value)) {
        (result as any).format = 'date-time';
    } else if (isDateString(value)) {
        (result as any).format = 'date';
    } else if (isEmailString(value)) {
        (result as any).format = 'email';
    } else if (isUriString(value)) {
        (result as any).format = 'uri';
    }

    return result;
}

/**
 * Infer number type (integer vs number)
 */
function inferNumberType(value: number): Partial<InferredSchema> {
    if (Number.isInteger(value)) {
        return { type: 'integer' };
    }
    return { type: 'number' };
}

/**
 * Infer object type with properties
 */
function inferObjectType(value: Record<string, any>): Partial<InferredSchema> {
    const properties: Record<string, InferredSchema> = {};
    const required: string[] = [];

    for (const [key, val] of Object.entries(value)) {
        properties[key] = inferType(val) as InferredSchema;
        // In instance-based inference, all present properties are considered required
        if (val !== null && val !== undefined) {
            required.push(key);
        }
    }

    const result: Partial<InferredSchema> = {
        type: 'object',
        properties
    };

    if (required.length > 0) {
        result.required = required;
    }

    return result;
}

/**
 * Infer array type with items schema
 */
function inferArrayType(value: any[]): Partial<InferredSchema> {
    if (value.length === 0) {
        return { type: 'array' };
    }

    // Merge schemas from all array items
    const itemSchemas = value.map(item => inferType(item));
    const mergedSchema = mergeSchemas(itemSchemas);

    return {
        type: 'array',
        items: mergedSchema as InferredSchema
    };
}

/**
 * Merge multiple schemas into one (for array items)
 */
function mergeSchemas(schemas: Partial<InferredSchema>[]): Partial<InferredSchema> {
    if (schemas.length === 0) {
        return {};
    }

    if (schemas.length === 1) {
        return schemas[0];
    }

    // Check if all schemas have the same type
    const types = new Set(schemas.map(s => s.type).filter(Boolean));

    if (types.size === 1) {
        const type = types.values().next().value;

        if (type === 'object') {
            // Merge object properties
            const allProperties: Record<string, Partial<InferredSchema>[]> = {};

            for (const schema of schemas) {
                if (schema.properties) {
                    for (const [key, propSchema] of Object.entries(schema.properties)) {
                        if (!allProperties[key]) {
                            allProperties[key] = [];
                        }
                        allProperties[key].push(propSchema);
                    }
                }
            }

            const mergedProperties: Record<string, InferredSchema> = {};
            for (const [key, propSchemas] of Object.entries(allProperties)) {
                mergedProperties[key] = mergeSchemas(propSchemas) as InferredSchema;
            }

            return {
                type: 'object',
                properties: mergedProperties
            };
        }

        return { type };
    }

    // Mixed types - use anyOf or just return generic
    if (types.size > 1) {
        return {
            type: Array.from(types) as any
        };
    }

    return {};
}

// Format detection helpers
function isDateTimeString(value: string): boolean {
    return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/.test(value);
}

function isDateString(value: string): boolean {
    return /^\d{4}-\d{2}-\d{2}$/.test(value);
}

function isEmailString(value: string): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

function isUriString(value: string): boolean {
    return /^https?:\/\//.test(value);
}

/**
 * Infer schema from JSON string
 */
export function inferSchemaFromJson(jsonString: string): InferredSchema {
    try {
        const parsed = JSON.parse(jsonString);
        return inferJsonSchema(parsed, 'Inferred from instance document');
    } catch (error) {
        throw new Error(`Failed to parse JSON: ${error instanceof Error ? error.message : String(error)}`);
    }
}

/**
 * Format schema as pretty-printed JSON
 */
export function formatSchema(schema: InferredSchema): string {
    return JSON.stringify(schema, null, 2);
}
