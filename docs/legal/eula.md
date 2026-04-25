# UTL-X End User License Agreement (EULA)

**Effective date:** April 25, 2026
**Last updated:** April 25, 2026

---

## 1. Agreement

This End User License Agreement ("Agreement") is a legal agreement between you ("Customer", "you") and Glomidco B.V., a company registered in The Netherlands ("Glomidco", "we", "us"), for the use of the UTL-X Transformation Engine software ("Software") obtained through the Microsoft Azure Marketplace.

By deploying UTL-X through Azure Marketplace or otherwise using the Software, you agree to be bound by this Agreement.

## 2. Definitions

- **Software**: The UTL-X Transformation Engine (UTLXe), including the container image, runtime engine, built-in functions, and associated documentation.
- **Deployment**: A single running instance of the Software in an Azure Container App within your Azure subscription.
- **Transformation**: A data mapping definition written in the UTL-X language (`.utlx` files).
- **Plan**: The purchased tier (Starter, Professional, or Enterprise) that determines the feature limits of your Deployment.

## 3. License Grant

Subject to the terms of this Agreement and payment of applicable fees, Glomidco grants you a non-exclusive, non-transferable, limited license to:

a) Deploy and run the Software within your Azure subscription
b) Use the Software to process data transformations for your internal business operations and for services you provide to your customers
c) Create an unlimited number of Transformations
d) Access the HTTP API, health endpoints, and metrics of your Deployment

## 4. Plan Limits

Each Plan includes specific limits enforced by the Software:

| Feature | Starter | Professional | Enterprise |
|---|---|---|---|
| Worker threads | 8 | 32 | 64+ |
| Max instances (auto-scale) | 2 | 10 | Unlimited |
| Formats | All | All | All |
| Schema validation | All | All | All |
| Pipeline chaining | Yes | Yes | Yes |
| Dapr messaging | Yes | Yes | Yes |
| Support | Community | Email (48h) | Priority (4h SLA) |

Exceeding Plan limits requires upgrading to a higher tier. The Software enforces worker thread limits at runtime.

## 5. Restrictions

You may **not**:

a) Reverse engineer, decompile, or disassemble the Software, except to the extent permitted by applicable law (including EU Directive 2009/24/EC)
b) Remove or alter any proprietary notices, labels, or marks
c) Redistribute, sublicense, rent, lease, or lend the Software to third parties as a standalone product
d) Use the Software to build a competing data transformation product or service
e) Circumvent or attempt to circumvent license validation or tier enforcement mechanisms

You **may**:

a) Use the Software as part of a larger solution or service you offer to your customers (embedding is permitted)
b) Deploy multiple instances within your Azure subscription under the same Plan
c) Use the Software in development, testing, and production environments

## 6. Open Source Components

The UTL-X transformation language specification and core parser are licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). This EULA covers the commercial UTLXe runtime engine, its HTTP transport, compiled execution strategies, and Azure integration components that are distributed through Azure Marketplace.

A list of open source components and their licenses is available in the Software documentation.

## 7. Fees and Billing

a) Fees are billed monthly through the Microsoft Azure Marketplace in accordance with the Plan you selected at deployment time.
b) Microsoft acts as the billing agent. Payment terms, methods, and disputes are governed by your Microsoft Customer Agreement.
c) Glomidco reserves the right to change pricing for future renewals with 60 days' notice.
d) There are no refunds for partial months. You may cancel at any time and your deployment will remain active until the end of the current billing period.

## 8. Data and Privacy

a) The Software processes all data locally within your Azure subscription. Glomidco does not access, collect, or store your business data.
b) Our handling of any data is described in the [UTL-X Privacy Policy](privacy-policy.md).
c) You are the data controller for all data processed by the Software. You are responsible for ensuring your use of the Software complies with applicable data protection laws, including GDPR.

## 9. Intellectual Property

a) Glomidco retains all rights, title, and interest in the Software, including all intellectual property rights.
b) Transformations (`.utlx` files) you create are your property. Glomidco claims no rights over your transformation logic or the data you process.
c) Feedback you voluntarily provide about the Software may be used by Glomidco to improve the product without obligation to you.

## 10. Warranty Disclaimer

THE SOFTWARE IS PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND. TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, GLOMIDCO DISCLAIMS ALL WARRANTIES, WHETHER EXPRESS, IMPLIED, STATUTORY, OR OTHERWISE, INCLUDING WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, TITLE, AND NON-INFRINGEMENT.

Glomidco does not warrant that the Software will be uninterrupted, error-free, or free of harmful components. The Software's throughput and performance depend on your Azure resource configuration, message sizes, and transformation complexity.

## 11. Limitation of Liability

TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW:

a) Glomidco's total aggregate liability under this Agreement shall not exceed the fees you paid for the Software in the 12 months preceding the claim.
b) Glomidco shall not be liable for any indirect, incidental, special, consequential, or punitive damages, including loss of profits, data, or business opportunities, regardless of the cause of action.

These limitations apply even if Glomidco has been advised of the possibility of such damages.

## 12. Term and Termination

a) This Agreement is effective from the date you first deploy the Software and continues for as long as you maintain an active Azure Marketplace subscription.
b) You may terminate at any time by cancelling your Marketplace subscription and deleting the Deployment.
c) Glomidco may terminate this Agreement if you breach any of its terms, after providing 30 days' written notice and opportunity to cure.
d) Upon termination, your license to use the Software ends. Your Transformations and processed data remain your property.

## 13. Support

Support is provided according to your Plan tier. Community support is available through GitHub Issues. Paid support tiers include email support with response time commitments as specified in the Plan description.

## 14. Governing Law and Disputes

a) This Agreement is governed by the laws of The Netherlands, without regard to conflict of law principles.
b) The United Nations Convention on Contracts for the International Sale of Goods (CISG) does not apply.
c) Any disputes shall be submitted to the exclusive jurisdiction of the competent courts in Amsterdam, The Netherlands.
d) Nothing in this section limits your rights under mandatory consumer protection laws of your jurisdiction, if applicable.

## 15. General

a) **Entire Agreement**: This Agreement, together with the Privacy Policy and your Azure Marketplace subscription terms, constitutes the entire agreement between you and Glomidco regarding the Software.
b) **Severability**: If any provision is held invalid, the remaining provisions remain in full force.
c) **Waiver**: Failure to enforce any provision does not constitute a waiver of that provision.
d) **Assignment**: You may not assign this Agreement without Glomidco's written consent. Glomidco may assign this Agreement in connection with a merger, acquisition, or sale of assets.
e) **Notices**: Notices to Glomidco should be sent to legal@glomidco.com.

## 16. Contact

**Glomidco B.V.**
The Netherlands

- Legal: legal@glomidco.com
- Support: support@glomidco.com
- Website: https://utlx-lang.org
