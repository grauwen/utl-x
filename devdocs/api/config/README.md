# UTL-X bundle config schemas

JSON Schemas (draft-07) for the two YAML config files in a UTL-X bundle. They are the
**editable/validatable contract** for those files — used by the IDE (IF04 schema-assisted
editing), CI, and anyone hand-writing a bundle.

| File | Schema | Engine source of truth |
|---|---|---|
| `transformations/<name>/transform.yaml` | [`transform-config.schema.json`](transform-config.schema.json) | `modules/engine/.../config/TransformConfig.kt` |
| `engine.yaml` (bundle root) | [`engine-config.schema.json`](engine-config.schema.json) | `modules/engine/.../config/EngineConfig.kt` |

## `transform.yaml` highlights
- `strategy`: `TEMPLATE` (alias `INTERPRETED`) · `COPY` · `COMPILED`
- `validationPolicy`: `OFF` · `SKIP` · `WARN` · `STRICT`
- `inputs[]` (data slots, `name` + optional `schema`), `output.schema`
- `maxConcurrent` (EF21 back-pressure; 0 = unlimited), `maxInputSize` (e.g. `25MB`)
- **Messaging (EF10)** — Azure Service Bus / Event Hub via Dapr:
  - `input` (messaging input, singular) and `output_messaging` (output) — each is exactly
    **one of** `queue` (Service Bus queue) · `topic` (Service Bus topic, `subscription`
    required) · `eventhub` (Event Hub, optional `consumerGroup` for pub/sub).
  - Example: [`examples/utlxe/invoice-routing.utlxp`](../../../examples/utlxe/invoice-routing.utlxp)
    (`input: {topic, subscription}` → `output_messaging: {queue}`).

## Notes
- The **engine tolerates unknown keys** (lenient load); these schemas are intentionally
  **stricter** (`additionalProperties: false`) to catch typos while editing — e.g. the old,
  unread `pipes:` block in `engine.yaml` would now be flagged.
- Schemas are **verified** against all `examples/utlxe/*.utlxp` config files.

## Validate locally
```js
// node, with js-yaml + ajv on the path
const yaml = require('js-yaml'), Ajv = require('ajv'), fs = require('fs');
const ajv = new Ajv({ strict: false });
const validate = ajv.compile(require('./transform-config.schema.json'));
const ok = validate(yaml.load(fs.readFileSync('path/to/transform.yaml', 'utf8')));
if (!ok) console.error(ajv.errorsText(validate.errors));
```
