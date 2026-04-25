# UTL-X Privacy Policy

**Effective date:** April 25, 2026
**Last updated:** April 25, 2026
**Data controller:** Glomidco B.V., The Netherlands

---

## 1. Introduction

This privacy policy describes how Glomidco B.V. ("we", "us", "Glomidco") handles data in connection with the UTL-X Transformation Engine ("UTL-X") distributed through the Microsoft Azure Marketplace and other channels.

UTL-X is a data transformation engine that runs **entirely within the customer's own Azure subscription**. We designed it with a privacy-first architecture: your business data never leaves your environment.

## 2. Data We Do NOT Collect

UTL-X processes data transformations locally inside your Azure Container App. The following data **stays in your subscription** and is never transmitted to Glomidco:

- **Business data** — messages, payloads, documents processed by the engine
- **Transformation source code** — your `.utlx` files and mapping logic
- **Schema definitions** — JSON Schema, XSD, Avro, or other validation schemas
- **Credentials** — connection strings, API keys, Service Bus tokens
- **Azure resource configuration** — your subscription ID, resource group details, networking

We have **no access** to the data flowing through your UTL-X deployment. All transformation processing happens in-process within your container, and any messaging (Service Bus, Event Hub) uses your own Azure resources and credentials.

## 3. Data We May Collect

### 3.1 License Validation

When a license key is configured, UTL-X may contact a Glomidco license server to validate the key. This request contains:

- License key (hashed)
- Engine version number
- Tier (Starter / Professional / Enterprise)
- A timestamp

This request does **not** contain any business data, transformation logic, or Azure resource identifiers. License validation can be disabled for air-gapped deployments by contacting support.

### 3.2 Marketplace Billing

When you purchase UTL-X through the Azure Marketplace, Microsoft handles all billing. Microsoft shares the following with us as the publisher:

- Your Azure subscription ID
- Offer and plan selected (e.g., Starter, Professional)
- Billing status (active, suspended, cancelled)
- Country/region of the subscription

This data is provided by Microsoft under the [Microsoft Publisher Agreement](https://learn.microsoft.com/en-us/partner-center/marketplace/standard-contract) and is used solely for billing reconciliation and customer support.

### 3.3 Support Interactions

If you contact us for support (email, GitHub Issues, or Partner Center), we collect the information you voluntarily provide: name, email address, company name, and the technical details of your issue.

### 3.4 Website and Documentation

Our website (utlx-lang.org) and documentation pages may use basic analytics (page views, referrer) to understand usage patterns. No personal data is collected beyond standard web server logs.

## 4. How We Use Collected Data

We use the limited data described in Section 3 exclusively for:

- Validating your software license
- Processing billing through Azure Marketplace
- Providing technical support
- Improving our documentation and product

We do **not**:

- Sell or share personal data with third parties
- Use data for advertising or profiling
- Train machine learning models on any customer data
- Aggregate or analyze your transformation patterns or volumes

## 5. Legal Basis (GDPR)

Glomidco B.V. is established in The Netherlands and complies with the General Data Protection Regulation (EU) 2016/679.

| Processing activity | Legal basis |
|---|---|
| License validation | Legitimate interest (software license enforcement) |
| Marketplace billing | Performance of contract |
| Support interactions | Performance of contract / Consent |
| Website analytics | Legitimate interest (service improvement) |

## 6. Data Retention

- **License validation logs**: retained for 12 months, then deleted
- **Billing records**: retained for 7 years (Dutch fiscal obligation)
- **Support correspondence**: retained for the duration of your subscription plus 12 months
- **Website logs**: retained for 90 days

## 7. Data Transfers

License validation requests are processed on servers in the European Union (West Europe region). We do not transfer personal data outside the EEA. Marketplace billing data is processed by Microsoft under their own data processing terms.

## 8. Your Rights

Under the GDPR, you have the right to:

- **Access** the personal data we hold about you
- **Rectify** inaccurate data
- **Erase** your data (subject to legal retention obligations)
- **Restrict** processing in certain circumstances
- **Data portability** — receive your data in a structured format
- **Object** to processing based on legitimate interest
- **Lodge a complaint** with the Dutch Data Protection Authority (Autoriteit Persoonsgegevens)

To exercise any of these rights, contact us at: **privacy@glomidco.com**

## 9. Security

UTL-X is designed so that sensitive data processing happens entirely within your Azure subscription, under your security controls. For the limited data we do process:

- All communications use TLS 1.2+
- License validation endpoints are authenticated
- Access to billing data is restricted to authorized personnel
- We maintain appropriate technical and organizational measures per GDPR Article 32

## 10. Children's Privacy

UTL-X is a business-to-business software product. We do not knowingly collect data from individuals under 16 years of age.

## 11. Changes to This Policy

We may update this policy to reflect changes in our practices or legal requirements. Material changes will be communicated through our documentation site and Marketplace listing. The "Last updated" date at the top indicates the most recent revision.

## 12. Contact

**Glomidco B.V.**
The Netherlands

- Privacy inquiries: privacy@glomidco.com
- General support: support@glomidco.com
- Website: https://utlx-lang.org
