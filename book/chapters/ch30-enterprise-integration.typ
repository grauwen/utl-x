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
