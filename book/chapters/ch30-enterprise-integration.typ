= Enterprise Integration

This chapter shows UTL-X in the real world — where it sits in integration architecture, how it handles the major industry use cases, and how it connects to message brokers and monitoring systems.

== Where UTL-X Fits

In a traditional enterprise integration architecture, data transformation is one component in a larger flow:

```
Source System → Message Broker → Transformation → Target System
     (SAP)       (Service Bus)     (UTL-X)        (Peppol AP)
```

UTL-X handles the transformation step — the part where data changes format, structure, and content. It does not handle routing, orchestration, or transport. Those are the message broker's job.

This is a deliberate architectural choice. Full iPaaS platforms (MuleSoft, Boomi, Azure Logic Apps, TIBCO BusinessWorks) bundle routing, transformation, and transport into one monolithic product. UTL-X does one thing well: transformation. You pair it with whatever broker, orchestrator, or transport your organization already uses.

=== UTL-X vs Full iPaaS

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Aspect*], [*Full iPaaS*], [*UTL-X + Broker*],
  [Routing], [Built-in], [Message broker (Service Bus, Pub/Sub, Kafka)],
  [Transformation], [Built-in (proprietary DSL)], [UTL-X (open source, format-agnostic)],
  [Transport], [Built-in connectors], [Dapr, native SDKs, HTTP],
  [Deployment], [Vendor cloud or on-prem appliance], [Container (any cloud, any Kubernetes)],
  [Lock-in], [High (proprietary everything)], [Low (open source transformation, standard broker)],
  [Cost], [\$50K-500K/year], [Container compute cost only],
  [Transformation language], [Vendor-specific (DataWeave, XSLT, visual mapper)], [UTL-X (portable, testable, version-controlled)],
)

The key advantage: your transformation logic is in `.utlx` files that you own, version-control, test, and deploy independently. Moving from Azure to GCP means changing the broker configuration, not rewriting transformations.

== Use Case: European E-Invoicing (Peppol/UBL)

The European e-invoicing mandate requires businesses to send invoices in UBL 2.1 XML via the Peppol network. If your ERP is Dynamics 365, SAP, or any system that produces JSON or proprietary XML, you need a transformation.

=== The Flow

```
Dynamics 365 → Service Bus → UTLXe → Peppol Access Point
  (OData JSON)    (queue)    (→ UBL XML)    (AS4 transport)
```

=== The Transformation

```utlx
%utlx 1.0
input odata
output xml
---
let inv = $input

{
  "Invoice": {
    "@xmlns": "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
    "@xmlns:cac": "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2",
    "@xmlns:cbc": "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",

    "cbc:CustomizationID": "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0",
    "cbc:ProfileID": "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0",
    "cbc:ID": inv.invoicenumber,
    "cbc:IssueDate": formatDate(parseDate(inv.createdon, "yyyy-MM-dd'T'HH:mm:ss'Z'"), "yyyy-MM-dd"),
    "cbc:InvoiceTypeCode": "380",
    "cbc:DocumentCurrencyCode": inv.transactioncurrencyid?.isocurrencycode ?? "EUR",

    "cac:AccountingSupplierParty": {
      "cac:Party": {
        "cac:PartyName": { "cbc:Name": "Your Company B.V." },
        "cac:PostalAddress": {
          "cbc:StreetName": "Keizersgracht 42",
          "cbc:CityName": "Amsterdam",
          "cbc:PostalZone": "1015 CR",
          "cac:Country": { "cbc:IdentificationCode": "NL" }
        },
        "cac:PartyTaxScheme": {
          "cbc:CompanyID": "NL123456789B01",
          "cac:TaxScheme": { "cbc:ID": "VAT" }
        }
      }
    },

    "cac:AccountingCustomerParty": {
      "cac:Party": {
        "cac:PartyName": { "cbc:Name": inv.customerid_account?.name ?? "Unknown" },
        "cac:PostalAddress": {
          "cbc:CityName": inv.billto_city ?? "",
          "cac:Country": { "cbc:IdentificationCode": inv.billto_country ?? "NL" }
        }
      }
    },

    "cac:LegalMonetaryTotal": {
      "cbc:LineExtensionAmount": { "@currencyID": "EUR", "_text": toString(inv.totallineitemamount ?? 0) },
      "cbc:TaxExclusiveAmount": { "@currencyID": "EUR", "_text": toString(inv.totalamountlessfreight ?? 0) },
      "cbc:TaxInclusiveAmount": { "@currencyID": "EUR", "_text": toString(inv.totalamount ?? 0) },
      "cbc:PayableAmount": { "@currencyID": "EUR", "_text": toString(inv.totalamount ?? 0) }
    },

    "cac:InvoiceLine": map(inv.invoice_details ?? [], (line) -> {
      "cbc:ID": toString(line.sequencenumber ?? 1),
      "cbc:InvoicedQuantity": { "@unitCode": "EA", "_text": toString(line.quantity ?? 1) },
      "cbc:LineExtensionAmount": { "@currencyID": "EUR", "_text": toString(line.extendedamount ?? 0) },
      "cac:Item": { "cbc:Name": line.productdescription ?? "Product" },
      "cac:Price": { "cbc:PriceAmount": { "@currencyID": "EUR", "_text": toString(line.priceperunit ?? 0) } }
    })
  }
}
```

This is a complete, production-ready Dynamics 365 to Peppol BIS 3.0 transformation. The UBL namespace prefixes use bracket notation (`["cbc:ID"]` in reading, `"cbc:ID":` in writing). Amounts use the `_text` + `@currencyID` pattern for elements with both text content and attributes.

== Use Case: Healthcare (HL7 FHIR)

Healthcare data exchange is moving from HL7 v2 (pipe-delimited) and proprietary formats to FHIR R4 (JSON and XML). UTL-X handles FHIR's unique conventions natively.

=== FHIR's Value Attribute Convention

FHIR XML uses `value` attributes instead of text content — a deliberate design for extensibility:

```xml
<!-- FHIR style -->
<birthDate value="1955-03-12"/>
<active value="true"/>
<name>
  <family value="Simpson"/>
  <given value="Homer"/>
</name>
```

UTL-X accesses these with the standard `@` prefix:

```utlx
$input.Patient.birthDate.@value     // "1955-03-12"
$input.Patient.active.@value        // "true"
$input.Patient.name[0].family.@value  // "Simpson"
```

No special FHIR mode — the `@value` accessor works like any XML attribute.

=== Proprietary EHR to FHIR R4

```utlx
%utlx 1.0
input json
output json
---
{
  resourceType: "Patient",
  id: $input.patientId,
  meta: { profile: ["http://hl7.org/fhir/StructureDefinition/Patient"] },
  name: [{
    family: $input.lastName,
    given: [$input.firstName],
    use: "official"
  }],
  birthDate: formatDate(parseDate($input.dob, "dd/MM/yyyy"), "yyyy-MM-dd"),
  gender: if ($input.sex == "M") "male" else if ($input.sex == "F") "female" else "unknown",
  address: [{
    line: [$input.street],
    city: $input.city,
    postalCode: $input.zipCode,
    country: $input.country
  }],
  telecom: filter([
    if ($input.phone != null) { system: "phone", value: $input.phone, use: "home" } else null,
    if ($input.email != null) { system: "email", value: $input.email } else null
  ], (t) -> t != null)
}
```

This produces a valid FHIR R4 Patient resource from a flat JSON record. The `filter` at the end removes null telecom entries — FHIR arrays must not contain nulls.

== Use Case: Financial Services (ISO 20022)

ISO 20022 (SWIFT MX) is replacing the legacy SWIFT MT format for international payments. The standard uses complex XSD schemas with hundreds of types.

=== Payment Initiation (pain.001)

```utlx
%utlx 1.0
input json
output xml
---
{
  "Document": {
    "@xmlns": "urn:iso:std:iso:20022:tech:xsd:pain.001.001.11",
    "CstmrCdtTrfInitn": {
      "GrpHdr": {
        "MsgId": $input.messageId,
        "CreDtTm": formatDate(now(), "yyyy-MM-dd'T'HH:mm:ss"),
        "NbOfTxs": toString(count($input.payments)),
        "CtrlSum": toString(sum(map($input.payments, (p) -> p.amount))),
        "InitgPty": { "Nm": $input.initiator.name }
      },
      "PmtInf": map($input.payments, (pmt) -> {
        "PmtInfId": pmt.id,
        "PmtMtd": "TRF",
        "NbOfTxs": "1",
        "PmtTpInf": { "SvcLvl": { "Cd": "SEPA" } },
        "ReqdExctnDt": { "Dt": pmt.executionDate },
        "Dbtr": { "Nm": $input.debtor.name },
        "DbtrAcct": { "Id": { "IBAN": $input.debtor.iban } },
        "DbtrAgt": { "FinInstnId": { "BICFI": $input.debtor.bic } },
        "CdtTrfTxInf": {
          "PmtId": { "EndToEndId": pmt.endToEndId },
          "Amt": { "InstdAmt": { "@Ccy": pmt.currency, "_text": toString(pmt.amount) } },
          "CdtrAgt": { "FinInstnId": { "BICFI": pmt.creditorBic } },
          "Cdtr": { "Nm": pmt.creditorName },
          "CdtrAcct": { "Id": { "IBAN": pmt.creditorIban } },
          "RmtInf": { "Ustrd": pmt.reference }
        }
      })
    }
  }
}
```

ISO 20022 messages have deep nesting with abbreviated element names (`CstmrCdtTrfInitn`, `GrpHdr`, `NbOfTxs`). UTL-X handles the structure directly — no intermediary mapping tool needed.

== Use Case: Retail and E-Commerce

=== Order Synchronization

Omnichannel retail needs orders from web shops, POS systems, and marketplaces normalized into one format:

```utlx
%utlx 1.0
input json
output json
---
{
  orderId: $input.order_id ?? $input.orderId ?? $input.OrderID,
  source: $input._source ?? "unknown",
  customer: {
    name: $input.customer_name ?? $input.customerName ?? concat($input.first_name ?? "", " ", $input.last_name ?? ""),
    email: $input.customer_email ?? $input.email
  },
  lines: map($input.items ?? $input.line_items ?? $input.orderLines ?? [], (item) -> {
    sku: item.sku ?? item.product_id ?? item.SKU,
    name: item.name ?? item.product_name ?? item.description,
    quantity: toNumber(item.quantity ?? item.qty ?? 1),
    unitPrice: toNumber(item.price ?? item.unit_price ?? item.unitPrice ?? 0)
  }),
  total: toNumber($input.total ?? $input.order_total ?? $input.grandTotal ?? 0),
  currency: $input.currency ?? $input.currency_code ?? "EUR"
}
```

The `??` chains handle the field name variations across different source systems. Shopify uses `line_items`, WooCommerce uses `items`, SAP uses `orderLines` — the canonical transformation normalizes all of them.

== Workflow and Low-Code Integration

Workflow platforms — Azure Logic Apps, Power Automate, n8n, Make (formerly Integromat), Zapier — are increasingly popular for business automation. They excel at orchestration (trigger on event, call API, send email, update database) but have weak transformation capabilities. Their built-in mapping is typically limited to simple field assignment: drag source field A to target field B.

This breaks down when you need:
- Format conversion (XML order → JSON API payload)
- Conditional logic (different VAT rates by country)
- Array operations (map order lines, filter active items, aggregate totals)
- Nested restructuring (flat CSV → hierarchical JSON)
- Cross-field computation (line total = quantity x price)

These are exactly the problems UTL-X solves. The integration pattern: the workflow handles orchestration, UTL-X handles transformation.

=== Pattern: HTTP Action in a Workflow

Most workflow platforms support calling an HTTP endpoint as a step. UTLXe in HTTP mode is that endpoint:

```
Workflow Step 1: Trigger (new order in Shopify)
Workflow Step 2: Get order data (Shopify API)
Workflow Step 3: HTTP POST to UTLXe → transform to UBL invoice XML
Workflow Step 4: Send invoice to Peppol Access Point
```

Step 3 is one HTTP call — the workflow sends the Shopify JSON to UTLXe, receives UBL XML back. The transformation logic lives in a `.utlx` file, version-controlled and testable — not embedded in the workflow's visual designer where it's invisible to code review, untestable, and lost when the workflow is deleted.

=== Azure Logic Apps / Power Automate

```
Logic App Trigger (Service Bus message)
  → HTTP Action: POST to UTLXe container
  → Send result to target API
```

UTLXe runs as a Container App in the same Azure subscription. The Logic App calls it via HTTP — no connector needed, no marketplace dependency. The transformation is external to the workflow: change the `.utlx` file without touching the Logic App.

=== n8n / Make / Zapier

These platforms support "Webhook" or "HTTP Request" actions:

- *n8n:* HTTP Request node → UTLXe endpoint
- *Make:* HTTP module → UTLXe endpoint
- *Zapier:* Webhooks by Zapier → UTLXe endpoint

The pattern is identical across platforms: send data, receive transformed data. The workflow doesn't know or care that UTL-X is involved — it's just an HTTP call that returns the right format.

=== Why Not Transform Inside the Workflow?

Workflow platforms offer basic transformation features — "expression builder", "data mapper", "code step." Why not use those?

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Aspect*], [*Workflow built-in*], [*UTL-X external*],
  [Complexity], [Simple field mapping only], [Any complexity: conditionals, loops, aggregation],
  [Format support], [JSON only (usually)], [XML, JSON, CSV, YAML, OData],
  [Testability], [Manual — click through the workflow], [Automated — conformance suite, CI/CD],
  [Version control], [Workflow export (opaque JSON/YAML)], [`.utlx` files in Git],
  [Reuse], [Copy-paste between workflows], [Shared `.utlx` library, imported by reference],
  [Debugging], [Workflow run history (limited)], [IDE with live preview, sample data],
  [Performance], [Workflow engine overhead per step], [86K msg/s for complex transforms],
  [Lock-in], [Platform-specific expressions], [Portable across any platform with HTTP],
)

The sweet spot: use the workflow for what it does well (triggering, routing, API calls, human tasks) and UTL-X for what it does well (data transformation). They complement each other.

== RPA (Robotic Process Automation)

RPA platforms — UiPath, Automation Anywhere, Blue Prism, Power Automate Desktop — automate tasks that involve user interfaces: clicking buttons, reading screens, filling forms, scraping web pages. The data they extract is often unstructured or semi-structured: screen-scraped text, PDF tables, email bodies, Excel exports.

This data almost always needs transformation before it can enter a business system. An invoice number scraped from a PDF needs to become a field in a JSON API call. A table copied from a web page needs to become a CSV import. An email body with order details needs to become a structured XML message.

=== The Pattern: RPA Extracts, UTL-X Transforms

```
RPA Robot → extracts data (screen scrape, PDF parse, email parse)
         → raw data (CSV, JSON, unstructured text)
         → HTTP POST to UTLXe
         → structured output (JSON for API, XML for ERP, CSV for database)
         → RPA Robot continues (API call, form fill, database insert)
```

The RPA robot handles the UI interaction and data extraction. UTL-X handles the structural transformation. The robot doesn't need complex data manipulation logic — it calls UTLXe and gets the right format back.

=== UiPath Integration

UiPath has an HTTP Request activity that calls external APIs:

```
UiPath Sequence:
  1. Read PDF Invoice (Document Understanding)
  2. Extract fields → JSON object
  3. HTTP Request: POST to UTLXe (JSON → UBL XML)
  4. Write file: save UBL invoice
  5. Upload to Peppol Access Point
```

Step 3 is the transformation — the robot sends extracted invoice data as JSON, receives a compliant UBL XML invoice back. The mapping logic (field names, VAT calculation, country codes) lives in a `.utlx` file, not in UiPath's expression builder.

=== Why RPA Needs External Transformation

RPA platforms have basic data manipulation — assign variables, simple string operations, basic JSON parsing. But they struggle with:

- *Format conversion:* RPA extracts data as JSON or DataTable. Producing XML with namespaces, attributes, and nested structures is beyond most RPA expression builders.
- *Complex mapping:* conditional fields, cross-reference lookups, array aggregation — these require a transformation language, not a visual variable assignment.
- *Reusability:* RPA processes are monolithic. Extracting the transformation into a `.utlx` file means the same mapping can be used by multiple robots, workflows, and integrations.
- *Compliance:* regulated industries need auditable, testable transformations. A `.utlx` file in Git with conformance tests is auditable. Logic embedded in a UiPath sequence is not.

=== Common RPA + UTL-X Scenarios

- *Invoice processing:* robot scrapes invoice PDF → UTL-X transforms to UBL XML → submit to e-invoicing network
- *Employee onboarding:* robot reads HR email → UTL-X transforms to SAP IDoc → create employee in SAP
- *Order entry:* robot scrapes web portal → UTL-X normalizes to canonical order format → push to ERP
- *Report generation:* robot exports database query → UTL-X transforms to CSV with regional formatting → email to finance
- *Legacy migration:* robot reads legacy screen → UTL-X transforms to new system's API format → POST to modern API

In each case, the robot handles what requires a UI (clicking, typing, navigating). UTL-X handles what requires data intelligence (restructuring, converting, enriching).

== Message Broker Integration

UTLXe connects to message brokers via Dapr sidecars or native transports:

=== Azure Service Bus

```
Service Bus Queue → Dapr Sidecar → UTLXe Container → Dapr → Target Queue
```

UTLXe subscribes to a queue via Dapr's pub/sub binding. Each message triggers a transformation. The result is published to the target queue. Dead letters go to the DLQ for retry or manual inspection.

=== GCP Pub/Sub

```
Pub/Sub Topic → Push Subscription → UTLXe (HTTP mode) → Target API
```

UTLXe's HTTP transport receives Pub/Sub push messages directly — no sidecar needed. Each request contains the message payload; the response is the transformed result.

=== Apache Kafka

```
Kafka Topic → Dapr Sidecar → UTLXe → Dapr → Kafka Topic
```

Same Dapr pattern as Service Bus. UTLXe doesn't know or care whether the broker is Kafka, Service Bus, or RabbitMQ — Dapr abstracts the transport.

== Error Handling in Production

=== Validation Errors

Pre-validation (Chapter 18) catches malformed input before the transformation runs:

```
Message arrives → Pre-validate → FAIL → Dead Letter Queue
                               → PASS → Transform → Post-validate → Output
```

Dead-lettered messages include the validation error details — which field, which constraint, what value. Operations can inspect and fix without guessing.

=== Transformation Errors

Use `try/catch` in transformations for fields that might fail:

```utlx
{
  orderId: $input.orderId,
  orderDate: try { parseDate($input.date, "yyyy-MM-dd") } catch { null },
  amount: try { toNumber($input.total) } catch { 0 }
}
```

A failed date parse or number conversion doesn't crash the entire transformation — it produces a fallback value.

=== Monitoring

UTLXe exposes Prometheus metrics:

- `utlxe_messages_processed_total` — total messages processed (counter)
- `utlxe_messages_failed_total` — failed transformations (counter)
- `utlxe_transformation_duration_seconds` — processing time (histogram)
- `utlxe_active_workers` — currently busy workers (gauge)

The health endpoint (`/health`) returns readiness and liveness status for Kubernetes probes.

=== Alerting Rules

Typical Prometheus alert rules for UTLXe:

- *Error rate:* alert when `utlxe_messages_failed_total / utlxe_messages_processed_total > 0.01` (more than 1% failures)
- *Latency:* alert when `utlxe_transformation_duration_seconds{quantile="0.99"} > 0.1` (p99 over 100ms)
- *Queue depth:* alert when dead letter queue depth exceeds threshold (broker-specific metric)
- *Health:* alert when `/health` returns non-200 for more than 30 seconds
