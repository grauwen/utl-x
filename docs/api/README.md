# UTL-X Daemon REST API Documentation

## Overview

The UTL-X Daemon exposes a REST API for programmatic access to transformation, validation, schema inference, and UDM (Universal Data Model) operations.

**OpenAPI Specification**: [`openapi.yaml`](./openapi.yaml)

## Base URL

```
http://localhost:7779
```

Default port is `7779` (configurable via `--rest-port` flag).

## Quick Start

### 1. Start the Daemon

```bash
./utlxd start --rest --rest-port 7779
```

### 2. Ping Check (Quick Liveness Test)

```bash
curl http://localhost:7779/api/ping
```

Response:
```json
{
  "status": "ok",
  "service": "utlx-rest-server",
  "timestamp": 1699876543210
}
```

### 3. Health Check (Full Status)

```bash
curl http://localhost:7779/api/health
```

Response:
```json
{
  "status": "ok",
  "version": "1.0.0-SNAPSHOT",
  "uptime": 123456
}
```

### 4. Execute a Transformation

```bash
curl -X POST http://localhost:7779/api/execute \
  -H "Content-Type: application/json" \
  -d '{
    "utlx": "%utlx 1.0\ninput json\noutput json\n---\n$input",
    "input": "{\"name\": \"Alice\", \"age\": 30}",
    "inputFormat": "json",
    "outputFormat": "json"
  }'
```

## API Endpoints

### Health & Status

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/ping` | GET | Quick liveness check (minimal overhead) |
| `/api/health` | GET | Full health check with version and uptime |

### Transformation

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/execute` | POST | Execute transformation with single input |
| `/api/execute-multipart` | POST | Execute transformation with multiple inputs |

### Validation

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/validate` | POST | Validate UTLX code |

### Schema Operations

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/infer-schema` | POST | Infer output schema from transformation |
| `/api/parse-schema` | POST | Parse and normalize schema (XSD/JSON Schema) |

### UDM Operations

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/udm/export` | POST | Export data format → UDM Language |
| `/api/udm/import` | POST | Import UDM Language → data format |
| `/api/udm/validate` | POST | Validate UDM Language syntax |

### Administration

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/shutdown` | POST | Gracefully shutdown daemon |

## Usage Examples

### Validate UTLX Code

```bash
curl -X POST http://localhost:7779/api/validate \
  -H "Content-Type: application/json" \
  -d '{
    "utlx": "%utlx 1.0\ninput json\noutput json\n---\n$input.name",
    "strict": false
  }'
```

Response:
```json
{
  "valid": true,
  "diagnostics": []
}
```

### Infer Output Schema

```bash
curl -X POST http://localhost:7779/api/infer-schema \
  -H "Content-Type: application/json" \
  -d '{
    "utlx": "%utlx 1.0\ninput json\noutput json\n---\n{ fullName: $input.firstName + \" \" + $input.lastName, age: $input.age }",
    "format": "json-schema"
  }'
```

### UDM Export (JSON → .udm)

```bash
curl -X POST http://localhost:7779/api/udm/export \
  -H "Content-Type: application/json" \
  -d '{
    "content": "{\"name\": \"Alice\", \"age\": 30}",
    "format": "json",
    "prettyPrint": true
  }'
```

Response:
```json
{
  "success": true,
  "udmLanguage": "@udm-version: 1.0\n@parsed-at: \"2025-11-12T21:00:00Z\"\n\n{\n  name: \"Alice\",\n  age: 30.0\n}",
  "sourceFormat": "json",
  "parsedAt": "2025-11-12T21:00:00Z"
}
```

### UDM Import (.udm → JSON)

```bash
curl -X POST http://localhost:7779/api/udm/import \
  -H "Content-Type: application/json" \
  -d '{
    "udmLanguage": "@udm-version: 1.0\n\n{ name: \"Alice\", age: 30.0 }",
    "targetFormat": "json",
    "prettyPrint": true
  }'
```

Response:
```json
{
  "success": true,
  "output": "{\n  \"name\": \"Alice\",\n  \"age\": 30\n}",
  "targetFormat": "json",
  "sourceInfo": {}
}
```

### UDM Validate

```bash
curl -X POST http://localhost:7779/api/udm/validate \
  -H "Content-Type: application/json" \
  -d '{
    "udmLanguage": "@udm-version: 1.0\n\n{ name: \"Alice\", age: 30.0 }"
  }'
```

Response:
```json
{
  "valid": true,
  "errors": [],
  "udmVersion": "1.0",
  "sourceInfo": {}
}
```

## Supported Data Formats

All transformation and UDM endpoints support these formats:

**Tier 1 (Data Formats)**:
- `json` - JSON data
- `xml` - XML documents
- `csv` - CSV files
- `yaml` - YAML documents

**Tier 2 (Schema Formats)**:
- `jsonschema` - JSON Schema
- `xsd` - XML Schema Definition
- `avro` - Apache Avro Schema
- `protobuf` - Protocol Buffers Schema

## Format-Specific Options

### CSV Options

- `delimiter` - Delimiter character (default: `,`)
- `hasHeaders` - Include/expect headers (default: `true`)
- `regional` - Regional format: `usa`, `european`, `french`, `swiss`

### XML Options

- `arrayHints` - Comma-separated array element names
- `rootName` - Root element name (default: `root`)
- `encoding` - Output encoding (default: `UTF-8`)

### YAML Options

- `multiDoc` - Multi-document YAML

### JSON Schema Options

- `draft` - Draft version: `draft-07`, `2019-09`, `2020-12` (default)

### XSD Options

- `version` - XSD version: `1.0`, `1.1`
- `namespace` - Target namespace
- `pattern` - Design pattern: `russian-doll`, `salami-slice`, `venetian-blind`, `garden-of-eden`

### Avro Options

- `namespace` - Schema namespace
- `validate` - Validate schema (default: `true`)

## Error Handling

All endpoints return standard error responses:

```json
{
  "error": "error_code",
  "message": "Human-readable error message",
  "timestamp": 1699876543210
}
```

HTTP Status Codes:
- `200` - Success
- `400` - Bad Request (invalid input, validation errors)
- `500` - Internal Server Error

## CORS

CORS is enabled for all endpoints with:
- All origins allowed (`*`)
- All standard HTTP methods
- All headers allowed

## Architecture

The REST API is implemented using Ktor and shares core logic with the CLI through service layers:

```
REST API ──┐
           ├──> UDMService ──> UDM Language Parser/Serializer
CLI ───────┘
```

Zero code duplication between CLI and REST API ensures consistency.

## OpenAPI/Swagger UI

You can use tools like Swagger UI or Postman to explore the API interactively:

1. Import the [OpenAPI spec](./openapi.yaml) into Swagger UI
2. Or use Postman: Import → OpenAPI → Select file

## Related Documentation

- [UDM Language Specification](../specs/udm-language-spec-v1.md)
- [UDM Architecture](../architecture/udm-as-a-language.md)
- [UDM Implementation Status](../architecture/udm-language-implementation-status.md)
- [Daemon Architecture](../architecture/cli-daemon-split-architecture.md)
