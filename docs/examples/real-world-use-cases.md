# Real-World Use Cases

Practical examples of UTL-X in production scenarios.

## Use Case 1: E-Commerce Order Processing

### Scenario

Transform orders from multiple sources (API, CSV, XML) into unified format for order management system.

### Source 1: REST API (JSON)

```json
{
  "order_id": "API-12345",
  "customer": {
    "user_id": "U-001",
    "email": "customer@example.com"
  },
  "line_items": [
    {"sku": "PROD-A", "qty": 2, "unit_price": 29.99}
  ],
  "shipping_address": {
    "street": "123 Main St",
    "city": "Seattle",
    "state": "WA",
    "zip": "98101"
  }
}
```

### Source 2: Legacy System (XML)

```xml
<PurchaseOrder id="PO-67890">
  <Buyer>
    <CustomerID>C-002</CustomerID>
    <ContactEmail>buyer@example.com</ContactEmail>
  </Buyer>
  <OrderLines>
    <Line product="PROD-B" quantity="1" price="149.99"/>
  </OrderLines>
  <DeliveryAddress>
    <Street>456 Oak Ave</Street>
    <City>Portland</City>
    <State>OR</State>
    <PostalCode>97201</PostalCode>
  </DeliveryAddress>
</PurchaseOrder>
```

### Unified Transformation

```utlx
%utlx 1.0
input auto
output json
---

function normalizeOrder(source: Object): Object {
  // Detect source type and transform accordingly
  if (exists(source.order_id)) {
    // JSON API format
    {
      orderId: source.order_id,
      customerId: source.customer.user_id,
      customerEmail: source.customer.email,
      items: source.line_items |> map(item => {
        sku: item.sku,
        quantity: item.qty,
        unitPrice: item.unit_price,
        total: item.qty * item.unit_price
      }),
      shippingAddress: {
        street: source.shipping_address.street,
        city: source.shipping_address.city,
        state: source.shipping_address.state,
        postalCode: source.shipping_address.zip
      },
      total: sum(source.line_items.(qty * unit_price))
    }
  } else if (exists(source.PurchaseOrder)) {
    // XML format
    let po = source.PurchaseOrder
    {
      orderId: po.@id,
      customerId: po.Buyer.CustomerID,
      customerEmail: po.Buyer.ContactEmail,
      items: po.OrderLines.Line |> map(line => {
        sku: line.@product,
        quantity: parseNumber(line.@quantity),
        unitPrice: parseNumber(line.@price),
        total: parseNumber(line.@quantity) * parseNumber(line.@price)
      }),
      shippingAddress: {
        street: po.DeliveryAddress.Street,
        city: po.DeliveryAddress.City,
        state: po.DeliveryAddress.State,
        postalCode: po.DeliveryAddress.PostalCode
      },
      total: sum(po.OrderLines.Line.(parseNumber(@quantity) * parseNumber(@price)))
    }
  } else {
    error("Unknown order format")
  }
}

normalizeOrder(input)
```

## Use Case 2: Healthcare Data Integration (HL7 to FHIR)

### Scenario

Transform HL7 v2 messages to FHIR resources for modern healthcare systems.

### Input (HL7 v2)

```
MSH|^~\&|SendingApp|SendingFac|ReceivingApp|ReceivingFac|20251009120000||ADT^A01|MSG0001|P|2.5
PID|1||12345^^^MRN||Doe^John^A||19800515|M|||123 Main St^^Seattle^WA^98101
```

### Transformation

```utlx
%utlx 1.0
input hl7
output json
---

function parseHL7Segment(segment: String): Object {
  let fields = split(segment, "|")
  {
    type: fields[0],
    fields: fields
  }
}

let segments = split(input, "\n") |> map(s => parseHL7Segment(s)),
let pid = segments |> filter(s => s.type == "PID") |> first(),
let patientName = split(pid.fields[5], "^"),
let address = split(pid.fields[11], "^")

{
  resourceType: "Patient",
  id: pid.fields[3],
  name: [{
    family: patientName[0],
    given: [patientName[1]],
    prefix: patientName[2]
  }],
  gender: match pid.fields[8] {
    "M" => "male",
    "F" => "female",
    _ => "unknown"
  },
  birthDate: substring(pid.fields[7], 0, 4) + "-" + 
             substring(pid.fields[7], 4, 6) + "-" +
             substring(pid.fields[7], 6, 8),
  address: [{
    line: [address[0]],
    city: address[2],
    state: address[3],
    postalCode: address[4],
    country: "US"
  }]
}
```

## Use Case 3: Financial Data Reconciliation

### Scenario

Reconcile transactions from bank statement (CSV) with internal ledger (JSON).

### Bank Statement (CSV)

```csv
Date,Description,Amount,Balance
2025-10-01,Payment from Customer A,1000.00,5000.00
2025-10-02,Office Supplies,-150.00,4850.00
2025-10-03,Payment from Customer B,2000.00,6850.00
```

### Internal Ledger (JSON)

```json
{
  "transactions": [
    {"date": "2025-10-01", "description": "Customer A Invoice #001", "amount": 1000.00, "ref": "INV-001"},
    {"date": "2025-10-02", "description": "Office supplies purchase", "amount": -150.00, "ref": "EXP-045"},
    {"date": "2025-10-03", "description": "Customer B Invoice #002", "amount": 2000.00, "ref": "INV-002"},
    {"date": "2025-10-04", "description": "Utilities", "amount": -200.00, "ref": "EXP-046"}
  ]
}
```

### Reconciliation

```utlx
%utlx 1.0
input json
output json
---

// Load bank statement from CSV
let bankTxns = input.bankStatement.rows |> map(row => {
  date: row.Date,
  description: row.Description,
  amount: parseNumber(row.Amount)
})

// Load ledger transactions
let ledgerTxns = input.ledger.transactions

// Match transactions
function findMatch(bankTxn: Object, ledgerTxns: Array): Object? {
  ledgerTxns
    |> filter(lt => 
         lt.date == bankTxn.date && 
         abs(lt.amount - bankTxn.amount) < 0.01
       )
    |> first()
}

{
  reconciliation: bankTxns |> map(bankTxn => {
    let match = findMatch(bankTxn, ledgerTxns)
    {
      date: bankTxn.date,
      bankDescription: bankTxn.description,
      amount: bankTxn.amount,
      status: if (match != null) "matched" else "unmatched",
      ledgerRef: match?.ref ?? null,
      ledgerDescription: match?.description ?? null
    }
  }),
  
  summary: {
    totalBank: count(bankTxns),
    totalLedger: count(ledgerTxns),
    matched: count(bankTxns |> filter(bt => findMatch(bt, ledgerTxns) != null)),
    unmatched: count(bankTxns |> filter(bt => findMatch(bt, ledgerTxns) == null)),
    unmatchedLedger: ledgerTxns 
      |> filter(lt => !contains(bankTxns.*.date, lt.date))
      |> map(lt => {
           date: lt.date,
           ref: lt.ref,
           amount: lt.amount
         })
  }
}
```

## Use Case 4: IoT Sensor Data Aggregation

### Scenario

Aggregate and analyze sensor data from multiple IoT devices.

### Input (Streaming JSON)

```json
{"deviceId": "TEMP-001", "timestamp": "2025-10-09T10:00:00Z", "temperature": 22.5, "humidity": 45}
{"deviceId": "TEMP-002", "timestamp": "2025-10-09T10:00:00Z", "temperature": 23.1, "humidity": 48}
{"deviceId": "TEMP-001", "timestamp": "2025-10-09T10:05:00Z", "temperature": 22.8, "humidity": 46}
{"deviceId": "TEMP-002", "timestamp": "2025-10-09T10:05:00Z", "temperature": 23.5, "humidity": 49}
```

### Transformation

```utlx
%utlx 1.0
input jsonl
output json
---

{
  byDevice: input.readings
    |> groupBy(r => r.deviceId)
    |> entries()
    |> map(([deviceId, readings]) => {
         deviceId: deviceId,
         readingCount: count(readings),
         temperature: {
           min: min(readings.*.temperature),
           max: max(readings.*.temperature),
           avg: avg(readings.*.temperature),
           current: last(readings |> sortBy(r => r.timestamp)).temperature
         },
         humidity: {
           min: min(readings.*.humidity),
           max: max(readings.*.humidity),
           avg: avg(readings.*.humidity),
           current: last(readings |> sortBy(r => r.timestamp)).humidity
         },
         alerts: readings 
           |> filter(r => r.temperature > 25 || r.temperature < 18)
           |> map(r => {
                timestamp: r.timestamp,
                temperature: r.temperature,
                severity: if (r.temperature > 30 || r.temperature < 15) 
                           "high" 
                         else 
                           "medium"
              })
       }),
  
  overall: {
    totalReadings: count(input.readings),
    avgTemperature: avg(input.readings.*.temperature),
    avgHumidity: avg(input.readings.*.humidity),
    devicesOnline: count(distinct(input.readings.*.deviceId)),
    alerts: count(input.readings |> filter(r => r.temperature > 25 || r.temperature < 18))
  }
}
```

## Use Case 5: Content Management System Migration

### Scenario

Migrate content from WordPress (XML export) to headless CMS (JSON API).

### WordPress Export (XML)

```xml
<channel>
  <item>
    <title>My Blog Post</title>
    <pubDate>Mon, 01 Oct 2025 10:00:00 +0000</pubDate>
    <creator>admin</creator>
    <content:encoded><![CDATA[<p>This is the content</p>]]></content:encoded>
    <category>Technology</category>
    <category>Tutorial</category>
  </item>
</channel>
```

### Transformation to Headless CMS Format

```utlx
%utlx 1.0
input xml
output json
---

{
  articles: input.channel.item |> map(item => {
    title: item.title,
    slug: lower(replace(item.title, " ", "-")),
    author: item.creator,
    publishedAt: parseDate(item.pubDate, "EEE, dd MMM yyyy HH:mm:ss Z"),
    content: {
      html: item.{"content:encoded"},
      text: replace(item.{"content:encoded"}, "<[^>]+>", "")
    },
    categories: if (isArray(item.category))
                  item.category
                else
                  [item.category],
    status: "published",
    featured: false
  })
}
```

These real-world examples demonstrate UTL-X's versatility in handling complex data integration scenarios across different industries and formats.
