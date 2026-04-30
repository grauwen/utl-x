= Enterprise Integration

== UTL-X in Integration Architecture
// - Where UTL-X fits: between message broker and target system
// - Comparison with: Tibco BW, MuleSoft, Boomi, Azure Logic Apps
// - The lightweight alternative: Container App + UTL-X vs full iPaaS

== Use Case: European E-Invoicing (Peppol/UBL)
// - Dynamics 365 JSON → UBL 2.1 XML
// - Peppol BIS 3.0 compliance
// - Multi-country VAT handling
// - Service Bus → UTL-X → Peppol Access Point
// - Full transformation code walkthrough

== Use Case: Healthcare (HL7 FHIR)
// - HL7 v2 / DICOM / proprietary → FHIR R4
// - The FHIR value attribute convention
// - Patient, Observation, Bundle creation
// - NEN 7510 / GDPR compliance by architecture
// - Full transformation code walkthrough

== Use Case: Financial Services (SWIFT/ISO 20022)
// - Payment message transformation
// - pain.001 credit transfer
// - Multi-currency handling
// - Regulatory compliance

== Use Case: Retail/E-Commerce
// - Order processing across channels
// - Product catalog synchronization
// - Inventory updates (XML POS → JSON API)

== Message Broker Integration
// - Azure Service Bus + Dapr sidecar
// - GCP Pub/Sub push
// - AWS SQS/EventBridge
// - Apache Kafka via Dapr
// - Dead letter handling and retry

== Error Handling in Production
// - Validation errors: pre/post validation
// - Transformation errors: try/catch, fallbacks
// - Monitoring: Prometheus metrics
// - Alerting: health endpoint integration
