# UTL-X Serialization Functions - Usage Guide

## Overview

The Serialization Functions module provides parse/render capabilities for handling nested and embedded formats within documents, similar to Tibco BW's parse() and render() functions.

## Real-World Use Cases

### 1. REST API with Embedded SOAP XML

**Scenario:** JSON REST API containing SOAP XML request in a string field

```utlx
%utlx 1.0
input json
output json
---

{
  // Parse the embedded SOAP request
  let soapEnvelope = parseXml(input.request.soapPayload),
  
  processedRequest: {
    requestId: input.request.id,
    timestamp: input.request.timestamp,
    
    // Extract SOAP data
    customerId: soapEnvelope.Envelope.Body.GetCustomer.customerId,
    requestType: soapEnvelope.Envelope.Body.GetCustomer.@type,
    
    // Process and embed response as XML string
    soapResponse: renderXml({
      Envelope: {
        @xmlns: "http://schemas.xmlsoap.org/soap/envelope/",
        Body: {
          GetCustomerResponse: {
            customer: {
              id: soapEnvelope.Envelope.Body.GetCustomer.customerId,
              name: "John Doe",
              status: "active"
            }
          }
        }
      }
    }, pretty=true)
  }
}
```

### 2. XML Document with CSV in CDATA

**Scenario:** Legacy XML system with CSV data in CDATA sections

```utlx
%utlx 1.0
input xml
output json
---

{
  reportId: input.Report.@id,
  reportDate: input.Report.@date,
  
  // Parse CSV from CDATA section
  let csvData = parseCsv(input.Report.Data, delimiter="|", headers=true),
  
  customers: csvData |> map(row => {
    id: row.CUSTOMER_ID,
    name: row.CUSTOMER_NAME,
    balance: parseNumber(row.BALANCE),
    status: row.STATUS
  }),
  
  summary: {
    totalCustomers: count(csvData),
    totalBalance: sum(csvData.(parseNumber(BALANCE)))
  }
}
```

### 3. Message Queue with Nested JSON

**Scenario:** Message queue payload with serialized JSON in metadata field

```utlx
%utlx 1.0
input json
output json
---

{
  messageId: input.metadata.messageId,
  
  // Parse nested JSON metadata
  let metadata = parseJson(input.metadata.details),
  
  routing: {
    source: metadata.source,
    destination: metadata.destination,
    priority: metadata.priority
  },
  
  payload: input.payload,
  
  // Add enriched metadata as JSON string
  enrichedMetadata: renderJson({
    original: metadata,
    processed: now(),
    version: "2.0",
    handler: "utlx-processor"
  }, pretty=false)
}
```

### 4. Multi-Format API Gateway

**Scenario:** API gateway that converts between formats dynamically

```utlx
%utlx 1.0
input json
output json
---

{
  requests: input.batch |> map(request => {
    requestId: request.id,
    
    // Auto-detect and parse the payload format
    let parsedPayload = parse(request.payload),
    
    // Process the data (format-agnostic)
    processed: {
      customerId: parsedPayload.customer.id,
      orderTotal: parsedPayload.order.total
    },
    
    // Render in requested output format
    outputPayload: render(parsedPayload, request.outputFormat, pretty=true)
  })
}
```

### 5. ETL Pipeline with Mixed Formats

**Scenario:** ETL extracting data from multiple format sources

```utlx
%utlx 1.0
input json
output json
---

{
  // Extract from XML source
  let xmlSource = parseXml(input.sources.legacySystem),
  
  // Extract from CSV source
  let csvSource = parseCsv(input.sources.flatFile, delimiter="\t"),
  
  // Extract from YAML config
  let yamlConfig = parseYaml(input.sources.configuration),
  
  consolidatedData: {
    customers: xmlSource.Customers.Customer |> map(c => {
      id: c.@id,
      name: c.Name,
      
      // Match with CSV data
      let csvMatch = csvSource |> filter(row => row.ID == c.@id) |> first(),
      balance: csvMatch.BALANCE,
      
      // Apply YAML config
      tier: if (parseNumber(csvMatch.BALANCE) > yamlConfig.thresholds.premium)
              "PREMIUM"
            else
              "STANDARD"
    }),
    
    // Output as CSV for downstream system
    csvExport: renderCsv(
      xmlSource.Customers.Customer |> map(c => {
        CustomerID: c.@id,
        CustomerName: c.Name,
        Email: c.Email
      }),
      headers=true
    )
  }
}
```

### 6. Microservices Communication

**Scenario:** Converting between microservice protocols

```utlx
%utlx 1.0
input json
output json
---

{
  // Service A uses XML
  serviceA: {
    request: renderXml({
      ServiceRequest: {
        @version: "1.0",
        Operation: "GetCustomer",
        Parameters: {
          CustomerId: input.customerId
        }
      }
    }),
    
    // Parse XML response
    response: match input.serviceA.response {
      when isString => parseXml(input.serviceA.response),
      _ => input.serviceA.response
    }
  },
  
  // Service B uses JSON
  serviceB: {
    request: renderJson({
      operation: "getCustomer",
      params: {
        customerId: input.customerId
      }
    }),
    
    // Parse JSON response
    response: parseJson(input.serviceB.response)
  },
  
  // Service C uses YAML
  serviceC: {
    request: renderYaml({
      operation: "get_customer",
      customer_id: input.customerId
    }),
    
    response: parseYaml(input.serviceC.response)
  }
}
```

### 7. Configuration Management

**Scenario:** Converting between configuration formats

```utlx
%utlx 1.0
input yaml
output json
---

{
  // Original YAML config
  yamlConfig: input,
  
  // Convert to JSON for JavaScript consumers
  jsonConfig: renderJson(input, pretty=true),
  
  // Convert to XML for legacy systems
  xmlConfig: renderXml({
    Configuration: {
      Database: input.database,
      Server: input.server,
      Logging: input.logging
    }
  }, pretty=true, declaration=true),
  
  // Flatten to CSV for reporting
  csvReport: renderCsv([
    {
      Setting: "Database Host",
      Value: input.database.host
    },
    {
      Setting: "Database Port",
      Value: input.database.port
    },
    {
      Setting: "Server Port",
      Value: input.server.port
    }
  ])
}
```

### 8. Data Validation Pipeline

**Scenario:** Validating and transforming data from multiple sources

```utlx
%utlx 1.0
input json
output json
---

{
  validations: input.sources |> map(source => {
    sourceId: source.id,
    
    // Try to parse and validate
    let parsed = try {
      parse(source.data, source.format)
    } catch (e) {
      { error: e.message }
    },
    
    valid: parsed.error == null,
    
    // If valid, normalize to JSON
    normalized: if (parsed.error == null) {
      renderJson(parsed, pretty=false)
    } else {
      null
    },
    
    // Validation report
    report: {
      format: source.format,
      size: length(source.data),
      valid: parsed.error == null,
      error: parsed.error
    }
  }),
  
  summary: {
    total: count(input.sources),
    valid: count(input.sources |> filter(s => 
      try { parse(s.data, s.format); true } catch { false }
    )),
    invalid: count(input.sources) - count(input.sources |> filter(s => 
      try { parse(s.data, s.format); true } catch { false }
    ))
  }
}
```

## Function Reference

### JSON Functions

```utlx
// Parse JSON string to object
parseJson(jsonString: String): Any

// Render object as JSON
renderJson(obj: Any, pretty: Boolean = false): String
renderJson(obj: Any, options: JsonOptions): String
```

### XML Functions

```utlx
// Parse XML string to object
parseXml(xmlString: String): Any

// Render object as XML
renderXml(obj: Any, pretty: Boolean = false): String
renderXml(obj: Any, options: XmlOptions): String
```

### YAML Functions

```utlx
// Parse YAML string to object
parseYaml(yamlString: String): Any

// Render object as YAML
renderYaml(obj: Any): String
```

### CSV Functions

```utlx
// Parse CSV string to array
parseCsv(csvString: String, delimiter: String = ",", headers: Boolean = true): Array

// Render array as CSV
renderCsv(arr: Array, delimiter: String = ",", headers: Boolean = true): String
```

### Generic Functions

```utlx
// Auto-detect format and parse
parse(str: String): Any

// Parse with explicit format
parse(str: String, format: String): Any

// Render with explicit format
render(obj: Any, format: String, pretty: Boolean = false): String
```

## Options Objects

### JsonOptions
```kotlin
{
  pretty: Boolean = false,
  indent: String = "  ",
  sortKeys: Boolean = false
}
```

### XmlOptions
```kotlin
{
  pretty: Boolean = false,
  indent: String = "  ",
  declaration: Boolean = true,
  encoding: String = "UTF-8",
  rootElement: String? = null
}
```

### CsvOptions
```kotlin
{
  delimiter: String = ",",
  quote: String = "\"",
  escape: String = "\\",
  headers: Boolean = true,
  skipEmptyLines: Boolean = true,
  trim: Boolean = true
}
```

## Error Handling

```utlx
// Graceful error handling
let parsed = try {
  parseJson(input.potentiallyInvalidJson)
} catch (e) {
  { error: true, message: e.message }
}

// Format-specific validation
if (isString(input.data) && startsWith(input.data, "{")) {
  let json = parseJson(input.data)
  // Process JSON
} else if (isString(input.data) && startsWith(input.data, "<")) {
  let xml = parseXml(input.data)
  // Process XML
}
```

## Performance Considerations

1. **Parse Once, Use Many Times**: Cache parsed results
2. **Streaming for Large Data**: Use format-specific streaming parsers for large files
3. **Avoid Round-Tripping**: Don't parse/render unnecessarily
4. **Format Auto-Detection**: Has overhead; specify format when known

## Best Practices

1. **Always validate input format** before parsing
2. **Use try-catch** for unreliable data sources
3. **Specify format explicitly** when known (better performance)
4. **Use pretty-print** only for human-readable output
5. **Cache parsed objects** when reusing data
6. **Handle encoding issues** with explicit encoding parameters

## Integration with UTL-X Pipeline

```utlx
%utlx 1.0
input json
output json
---

// Stage 1: Parse all nested formats
let stage1 = {
  xmlData: parseXml(input.xmlString),
  jsonData: parseJson(input.jsonString),
  csvData: parseCsv(input.csvString)
},

// Stage 2: Transform (format-agnostic)
let stage2 = {
  customers: stage1.xmlData.Customers.Customer,
  orders: stage1.jsonData.orders,
  products: stage1.csvData
},

// Stage 3: Render in target formats
{
  xmlOutput: renderXml(stage2.customers, pretty=true),
  jsonOutput: renderJson(stage2.orders, pretty=true),
  csvOutput: renderCsv(stage2.products)
}
```

## Comparison with Tibco BW

| Tibco BW | UTL-X | Notes |
|----------|-------|-------|
| `parse()` | `parse()` | Auto-detect format |
| `parse(data, "JSON")` | `parseJson(data)` | Type-specific |
| `render()` | `render()` | Generic rendering |
| `renderJSON()` | `renderJson()` | Type-specific |
| N/A | `parseYaml()`, `renderYaml()` | UTL-X adds YAML |
| N/A | Options objects | More control |

## Advanced: Custom Format Plugins

Future enhancement for proprietary formats:

```kotlin
// Register custom format parser
registerFormatParser("protobuf", ProtobufParser())

// Use in UTL-X
parse(input.data, "protobuf")
render(output, "protobuf")
```
