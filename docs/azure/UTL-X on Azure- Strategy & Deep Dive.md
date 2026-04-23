# UTL-X on Azure: Strategy & Deep Dive

**Glomidco / UTL-X — Distribution via the Microsoft Azure Ecosystem**  
*Version 1.0 — April 2026*

-----

## Table of Contents

1. [Context: The Problem UTL-X Solves in Azure](#1-context-the-problem-utl-x-solves-in-azure)
1. [Overview of Routes](#2-overview-of-routes)
1. [Route 1 — Logic Apps Built-in Connector](#3-route-1--logic-apps-built-in-connector-deepest-integration)
1. [Route 2 — Azure Marketplace SaaS Offer](#4-route-2--azure-marketplace-saas-offer-fastest-go-to-market)
1. [Route 3 — Certified Power Platform Connector](#5-route-3--certified-power-platform-connector-broadest-reach)
1. [Route 4 — Azure Function / Container Package](#6-route-4--azure-function--container-package-lowest-barrier)
1. [Recommended Phased Approach](#7-recommended-phased-approach)
1. [Licensing Model Alignment](#8-licensing-model-alignment)
1. [Key Resources & Links](#9-key-resources--links)

-----

## 1. Context: The Problem UTL-X Solves in Azure

### Microsoft’s mapping stack is fundamentally XML/XSLT-oriented

Azure Logic Apps offers two mapping approaches today:

|Tool                          |Engine   |JSON support              |
|------------------------------|---------|--------------------------|
|Data Mapper (VS Code / GA)    |XSLT 3.0 |Via XML infoset conversion|
|Liquid Templates              |DotLiquid|Via string wrapper        |
|BizTalk-style mapper (VS 2019)|XSLT     |XML only                  |

**The reality:** even when the Data Mapper claims to support JSON→JSON transformations, the execution engine generates and runs XSLT 3.0 under the hood. JSON is converted to an XML-compatible representation before the transformation executes, then converted back. Liquid templates work similarly — DotLiquid does not natively understand JSON; the platform wraps and unwraps it.

For complex scenarios (conditional element selection, array handling with single-element edge cases, restructuring), practitioners consistently fall back to writing raw XSLT — or chain multiple transformation steps using Logic Apps expressions.

### The gap UTL-X fills

UTL-X is a **format-agnostic functional transformation language** that treats XML, JSON, CSV, and YAML as first-class citizens — without requiring any intermediate conversion. This is architecturally superior to the XSLT-centric Azure toolchain for organizations that process heterogeneous message formats, which is the majority of modern iPaaS use cases.

-----

## 2. Overview of Routes

|Route                                |Integration depth      |Time to market|Technical effort   |Reach                      |
|-------------------------------------|-----------------------|--------------|-------------------|---------------------------|
|1. Logic Apps Built-in Connector     |★★★★★ Native           |Months        |High (.NET)        |Logic Apps Standard only   |
|2. Marketplace SaaS Offer            |★★★☆☆ API call         |Weeks–months  |Medium (SaaS infra)|All Azure customers        |
|3. Certified Power Platform Connector|★★★★☆ Native in gallery|Months        |Medium (OpenAPI)   |Logic Apps + Power Platform|
|4. Azure Function / Container        |★★☆☆☆ HTTP action      |Days–weeks    |Low                |All Azure + any platform   |

These routes are **not mutually exclusive** — the recommended approach is to execute them in sequence, using each phase to build evidence for the next.

-----

## 3. Route 1 — Logic Apps Built-in Connector (Deepest Integration)

### What it is

In Logic Apps Standard (single-tenant), Microsoft exposes an extensibility model based on Azure Functions that allows third parties to build **built-in connectors** — i.e. connectors that run natively inside the Logic Apps runtime process, not as external API calls. These appear in the Logic Apps designer under the **Built-in** tab, alongside Microsoft’s own actions like Service Bus, HTTP, and XML Transform.

UTL-X implemented as a built-in connector would present as a **“Transform using UTL-X”** action in the designer — a direct peer of *Transform using Data Mapper XSLT*.

### Architecture

```
Logic Apps Standard Runtime (Azure Functions host)
├── Microsoft built-in connectors (Service Bus, HTTP, XML, ...)
├── Data Mapper XSLT connector
└── UTL-X connector (your NuGet package)
       └── UTL-X engine (embedded)
```

The connector runs **in-process** — no HTTP round-trip, no external service dependency. This gives it the same performance characteristics as Microsoft’s own built-in connectors.

### Technical implementation

#### Prerequisites

- .NET 6+ class library project
- NuGet package: `Microsoft.Azure.Workflows.WebJobs.Extension`
- Visual Studio Code with Azure Logic Apps (Standard) extension

#### Step 1 — Create the class library

```bash
dotnet new classlib -n UTLXLogicAppsConnector
cd UTLXLogicAppsConnector
dotnet add package Microsoft.Azure.Workflows.WebJobs.Extension
```

#### Step 2 — Implement IServiceOperationsProvider

This interface provides the **operations manifest** — the metadata the Logic Apps designer uses to render the action in the UI.

```csharp
using Microsoft.Azure.Workflows.ServiceProviders.Abstractions;
using Microsoft.WindowsAzure.ResourceStack.Common.Collections;

public class UTLXServiceOperationProvider : IServiceOperationsProvider
{
    public string ServiceId => "/serviceProviders/utlx";
    public string DisplayName => "UTL-X Transform";

    public ServiceOperationApi GetService()
    {
        return new ServiceOperationApi
        {
            Name = "utlx",
            Id = ServiceId,
            Type = OperationApiType.ServiceProvider,
            Properties = new ServiceOperationApiProperties
            {
                DisplayName = DisplayName,
                Description = "Format-agnostic transformation using UTL-X",
                IconUri = new Uri("https://your-cdn/utlx-icon.png"),
                Category = ConnectionCategory.Standard,
                Capabilities = new[] { ApiCapability.Actions }
            }
        };
    }

    public IEnumerable<ServiceOperation> GetOperations(bool expandManifest)
    {
        yield return new ServiceOperation
        {
            Name = "transform",
            Id = $"{ServiceId}/transform",
            Type = "transform",
            Properties = new ServiceOperationProperties
            {
                Api = GetService().GetFlattenedApi(),
                Summary = "Transform message",
                Description = "Transform a message using a UTL-X template",
                Visibility = Visibility.Important,
                OperationType = OperationType.Action,
                BrandColor = "#0078D4",
                Inputs = new InsensitiveDictionary<OperationParameter>
                {
                    ["inputFormat"] = new OperationParameter
                    {
                        Type = "string",
                        Required = true,
                        Summary = "Input format (json/xml/csv/yaml)"
                    },
                    ["outputFormat"] = new OperationParameter
                    {
                        Type = "string",
                        Required = true,
                        Summary = "Output format (json/xml/csv/yaml)"
                    },
                    ["template"] = new OperationParameter
                    {
                        Type = "string",
                        Required = true,
                        Summary = "UTL-X transformation template"
                    },
                    ["content"] = new OperationParameter
                    {
                        Type = "string",
                        Required = true,
                        Summary = "Input content to transform"
                    }
                }
            }
        };
    }

    public Task<ServiceOperationResponse> InvokeOperationAsync(
        string operationId,
        ServiceOperationRequest request,
        CancellationToken cancellationToken)
    {
        // Invoke the UTL-X engine here
        var inputFormat = request.Parameters["inputFormat"].ToString();
        var outputFormat = request.Parameters["outputFormat"].ToString();
        var template = request.Parameters["template"].ToString();
        var content = request.Parameters["content"].ToString();

        var result = UTLXEngine.Transform(content, template, inputFormat, outputFormat);

        return Task.FromResult(new ServiceOperationResponse
        {
            Body = JToken.FromObject(new { output = result })
        });
    }
}
```

#### Step 3 — Register the startup

```csharp
using Microsoft.Azure.WebJobs;
using Microsoft.Azure.WebJobs.Hosting;

[assembly: WebJobsStartup(typeof(UTLXStartup))]
public class UTLXStartup : IWebJobsStartup
{
    public void Configure(IWebJobsBuilder builder)
    {
        builder.AddExtension<UTLXServiceOperationProvider>();
        builder.Services.TryAddSingleton<UTLXServiceOperationProvider>();
    }
}
```

#### Step 4 — Package as NuGet

```bash
dotnet pack -c Release
```

The `.nupkg` file is placed in the Logic Apps Standard project’s `lib/custom` folder, and Logic Apps loads it automatically at startup.

#### Step 5 — Deploy

The NuGet package is deployed **as part of the Logic Apps Standard project**. Currently, custom built-in connectors are scoped to the Logic App resource they are deployed to — they are not globally available in the Azure portal. For wider distribution, packaging as a reusable NuGet on NuGet.org is the mechanism.

### Considerations

- **Scope:** Currently only available in Logic Apps Standard (single-tenant). Not available in Consumption (multi-tenant) plan.
- **Distribution:** Customers add your NuGet package to their Logic App project. This is a developer-facing distribution model.
- **AGPL implication:** Since this is a library embedded in the customer’s Logic App project, you need to consider whether AGPL is appropriate here or whether a commercial license is required for embedded use.
- **Licensing hook:** The connector can call home to a license validation endpoint on startup, enabling per-instance or per-volume licensing.

-----

## 4. Route 2 — Azure Marketplace SaaS Offer (Fastest Go-to-Market)

### What it is

UTL-X is offered as a **hosted transformation microservice** — an always-on REST API endpoint that accepts content + template + format parameters and returns transformed output. Customers discover and purchase it through the Microsoft Marketplace, charged via their existing Azure subscription (including MACC pre-committed spend eligibility).

As of September 2025, Azure Marketplace and AppSource have been unified into a single **Microsoft Marketplace** with 6M+ monthly visitors.

### Architecture

```
Customer's Azure Logic App / Workato / any iPaaS
        │
        │ HTTPS (REST)
        ▼
UTL-X SaaS API (hosted by Glomidco)
├── Authentication layer (Microsoft Entra ID / API Key)
├── License validation
├── UTL-X transformation engine
└── Usage metering → Azure Marketplace billing
```

### SaaS Offer components

Publishing a transactable SaaS offer requires implementing the **SaaS Fulfillment API** — a set of webhook endpoints that Microsoft calls to manage subscription lifecycle:

|Endpoint       |Triggered when                                         |
|---------------|-------------------------------------------------------|
|Landing page   |Customer completes purchase in Marketplace             |
|Activate API   |After onboarding, you notify Microsoft to start billing|
|Change plan API|Customer upgrades/downgrades                           |
|Suspend API    |Payment failure                                        |
|Reinstate API  |Payment resumed                                        |
|Delete API     |Customer cancels                                       |

#### Minimum infrastructure

```
┌─────────────────────────────────────┐
│  Glomidco SaaS Backend              │
│                                     │
│  ┌─────────────┐  ┌──────────────┐  │
│  │ Landing page│  │ Fulfillment  │  │
│  │ (Entra SSO) │  │ API webhooks │  │
│  └─────────────┘  └──────────────┘  │
│  ┌─────────────────────────────────┐ │
│  │ UTL-X transformation engine API │ │
│  └─────────────────────────────────┘ │
│  ┌─────────────┐  ┌──────────────┐  │
│  │ Tenant/user │  │ Usage meter  │  │
│  │ management  │  │ → Marketplace│  │
│  └─────────────┘  └──────────────┘  │
└─────────────────────────────────────┘
```

A **SaaS Accelerator** is available from Microsoft that provides a complete reference implementation which can be deployed in under 20 minutes, giving you the landing page, fulfillment webhooks, and tenant management out of the box.

### Pricing models available

|Model                     |Description                                 |Best for UTL-X  |
|--------------------------|--------------------------------------------|----------------|
|Flat rate (monthly/annual)|Fixed price per plan tier                   |✅ Base license  |
|Per user                  |Price scales with seat count                |Less relevant   |
|Metered billing           |Pay-per-transformation via custom dimensions|✅ Volume overage|
|Free trial                |N-day trial before billing starts           |✅ Onboarding    |

**Recommended:** Flat annual rate per tier (e.g. Developer / Professional / Enterprise) + metered overage for transformations beyond the tier quota. This aligns with the annual commercial license model recommended in earlier licensing discussions.

### Publisher economics

- Publishing: **free**
- Microsoft agency fee: **3%** on transactions
- Renewals via private offers: **1.5%** (50% reduced)
- Private offers available for named customers (e.g. Workato deal) at custom pricing

### Onboarding steps

1. Register in **Microsoft Partner Center** (free)
1. Enroll in the **Microsoft AI Cloud Partner Program**
1. Create a SaaS offer in Partner Center:
- Offer metadata, description, screenshots
- Plans and pricing
- Technical configuration (landing page URL, fulfillment webhook URLs)
1. Implement the SaaS Fulfillment API in your backend
1. Submit for Microsoft review (24–48 hours)
1. Publish

### Logic Apps integration pattern (from customer side)

Once the SaaS API is live, customers integrate it into Logic Apps as an HTTP action:

```json
{
  "type": "Http",
  "inputs": {
    "method": "POST",
    "uri": "https://api.utlx.io/v1/transform",
    "headers": {
      "Authorization": "Bearer @{parameters('utlx_api_key')}",
      "Content-Type": "application/json"
    },
    "body": {
      "inputFormat": "json",
      "outputFormat": "xml",
      "template": "@{variables('utlx_template')}",
      "content": "@{triggerBody()}"
    }
  }
}
```

This can also be wrapped in a **Custom Connector** (Route 3 lite) to give it a proper UI in the Logic Apps designer without going through full certification.

-----

## 5. Route 3 — Certified Power Platform Connector (Broadest Reach)

### What it is

A certified connector is a UTL-X REST API wrapped in an **OpenAPI definition** and submitted to Microsoft for certification. Once certified, it appears in the built-in connector gallery for:

- Azure Logic Apps (Standard and Consumption)
- Microsoft Power Automate
- Microsoft Power Apps
- Microsoft Copilot Studio

Certification is **free** — there is no cost to register, certify, or update.

### Publisher types

|Type                  |Who                        |UTL-X fits as        |
|----------------------|---------------------------|---------------------|
|**Verified Publisher**|Owns the underlying service|✅ Glomidco owns UTL-X|
|Independent Publisher |Doesn’t own the service    |N/A                  |

As a verified publisher, Glomidco’s name appears on the connector in all Microsoft products. The connector is also open-sourced to the Microsoft PowerPlatformConnectors GitHub repository (required for certification).

### Certification process

```
1. Build the UTL-X REST API
        │
2. Create OpenAPI (Swagger) definition
        │
3. Create connector artifacts:
   ├── apiDefinition.swagger.json
   ├── apiProperties.json
   ├── icon.png (100x100 to 230x230px)
   └── intro.md
        │
4. Run ConnectorPackageValidator.ps1
        │
5. Create Seller account in Partner Center
   (one-time vetting process)
        │
6. Submit via Partner Center
   (Microsoft 365 and Copilot program)
        │
7. Microsoft automated validation (schema, metadata, policy)
        │
8. Microsoft manual review (functional testing with your credentials)
        │
9. Certification approved → deployed globally
   (24–48 hours review timeline)
```

### OpenAPI structure for UTL-X

```yaml
swagger: "2.0"
info:
  title: UTL-X Transform
  description: Format-agnostic data transformation supporting JSON, XML, CSV, and YAML
  version: "1.0"
  contact:
    name: Glomidco Support
    url: https://utlx.io/support
    email: support@glomidco.com
host: api.utlx.io
basePath: /v1
schemes:
  - https
paths:
  /transform:
    post:
      summary: Transform message
      description: Transform content between formats using a UTL-X template
      operationId: Transform
      consumes:
        - application/json
      produces:
        - application/json
      parameters:
        - in: body
          name: body
          required: true
          schema:
            $ref: "#/definitions/TransformRequest"
      responses:
        200:
          description: Transformation result
          schema:
            $ref: "#/definitions/TransformResponse"
definitions:
  TransformRequest:
    type: object
    required:
      - inputFormat
      - outputFormat
      - template
      - content
    properties:
      inputFormat:
        type: string
        enum: [json, xml, csv, yaml]
        x-ms-summary: Input Format
      outputFormat:
        type: string
        enum: [json, xml, csv, yaml]
        x-ms-summary: Output Format
      template:
        type: string
        x-ms-summary: UTL-X Template
      content:
        type: string
        x-ms-summary: Input Content
  TransformResponse:
    type: object
    properties:
      output:
        type: string
        x-ms-summary: Transformed Output
      format:
        type: string
        x-ms-summary: Output Format
```

### Key requirements for certification

- Connector title must be unique and follow naming patterns
- All operation and parameter summaries in English, ≤80 characters
- Logo: 1:1 ratio, 100×100 to 230×230px, PNG, non-white background
- Response schemas defined for all actions
- OpenAPI must pass automated Swagger Validator
- API must be stable and have adequate uptime SLA
- Must open-source connector definition on GitHub

### Post-certification benefits

- Featured in new connector announcement blog post
- Listed on official Microsoft connector documentation pages
- YouTube demos and social media feature
- Appears in Copilot Studio as an agent-callable tool
- Qualifies as **Premium connector** (users need Power Platform premium license to use it — this limits consumer adoption but is standard for third-party connectors)

-----

## 6. Route 4 — Azure Function / Container Package (Lowest Barrier)

### What it is

The UTL-X engine packaged as a standalone **Azure Function app** or **Docker container**, deployable from the Azure Marketplace or GitHub with one click. No SaaS Fulfillment API needed — customers deploy it into their own Azure subscription.

### Architecture options

#### Option A: Azure Function App

```
Customer's Azure Subscription
├── Logic App (Standard)
│     └── HTTP action → UTL-X Function
└── Azure Function App (UTL-X engine)
      ├── /api/transform [POST]
      └── UTL-X engine code
```

#### Option B: Container (Azure Container Apps / AKS)

```
Customer's Azure Subscription
└── Azure Container Apps
      └── utlx-engine container
            ├── REST API endpoint
            └── UTL-X engine
```

### Marketplace listing type

This would be published as an **Azure Application** offer on Microsoft Marketplace (specifically a **Solution Template** or **Managed Application**), rather than a SaaS offer. The key difference: the software runs in the **customer’s subscription**, not yours.

- No SaaS Fulfillment API required
- Customer pays Azure compute costs directly
- You can add license key validation at startup
- Much simpler to publish and maintain

### Monetization for this route

Since Microsoft Marketplace doesn’t handle billing for self-deployed apps in the same way, licensing is handled separately:

- License key issued by Glomidco after purchase
- Key validated against your API at startup
- Annual renewal enforced via key expiry

### When to use this route

- Customers with data residency requirements (no external API calls)
- Enterprise environments with strict network policies
- Early adopters / proof-of-concept deployments
- As a free/open-source tier under AGPL (community traction)

-----

## 7. Recommended Phased Approach

### Phase 1 — Now (weeks 1–4): Azure Function on GitHub + Marketplace

**Goal:** Demonstrate Azure-native deployment to prospects like Workato; zero infrastructure investment.

**Actions:**

1. Package UTL-X engine as Azure Function App
1. Publish Docker image to GitHub Container Registry
1. Create Azure Marketplace listing as Azure Application (free/BYOL)
1. Document integration pattern with Logic Apps HTTP action
1. Write a technical blog post: *“Format-agnostic transformation in Logic Apps without XSLT”*

**Outcome:** Credible Azure story, reference architecture for Workato conversations.

-----

### Phase 2 — Short term (months 1–3): Marketplace SaaS Offer

**Goal:** Enable self-service purchasing via Azure budgets; establish recurring revenue baseline.

**Actions:**

1. Stand up UTL-X SaaS API (can be Azure Function behind APIM)
1. Use Microsoft’s SaaS Accelerator for landing page + fulfillment webhooks
1. Implement Microsoft Entra ID authentication
1. Define pricing tiers (Developer / Professional / Enterprise)
1. Add metered billing for transformation volume overages
1. Submit SaaS offer to Partner Center
1. Activate Marketplace Rewards for co-sell benefits

**Pricing suggestion:**

|Tier        |Monthly|Annual|Included transformations/month|
|------------|-------|------|------------------------------|
|Developer   |€199   |€1,990|100,000                       |
|Professional|€799   |€7,990|1,000,000                     |
|Enterprise  |Custom |Custom|Unlimited                     |

**Outcome:** Azure-budget purchasable, MACC-eligible, measurable ARR.

-----

### Phase 3 — Medium term (months 3–6): Certified Power Platform Connector

**Goal:** Appear natively in the Logic Apps designer and Power Automate for maximum discoverability.

**Actions:**

1. Create OpenAPI definition for UTL-X API
1. Build connector artifacts (icon, swagger, properties)
1. Register as Verified Publisher in Partner Center
1. Submit for certification
1. Open-source connector definition on GitHub

**Outcome:** UTL-X appears in the Logic Apps “Built-in” connector gallery. Discoverable by 6M+ Marketplace visitors and all Logic Apps / Power Automate users globally.

-----

### Phase 4 — Longer term: Logic Apps Native Built-in Connector

**Goal:** Deep runtime integration for maximum performance and developer experience.

**Actions:**

1. Implement `IServiceOperationsProvider` in .NET
1. Package as NuGet on NuGet.org
1. Document integration in Logic Apps Standard projects
1. Explore co-sell arrangement with Microsoft Integration team

**Outcome:** UTL-X runs in-process inside Logic Apps Standard, with the same UX as Microsoft’s own Data Mapper — without the XSLT dependency.

-----

## 8. Licensing Model Alignment

The Azure distribution channels align well with the annual commercial license model recommended for UTL-X:

|Channel                     |License type             |Enforcement                    |
|----------------------------|-------------------------|-------------------------------|
|Marketplace SaaS            |Annual subscription      |Marketplace billing + key      |
|Custom connector (certified)|Per-tenant API key       |License validation API         |
|Built-in connector (NuGet)  |Annual commercial license|Startup validation + key expiry|
|Azure Function (BYOD)       |AGPL (free) or commercial|Honor system / key             |

### AGPL boundary consideration

The AGPL license requires that any user of UTL-X who distributes it (including as a network service) must make their modifications available. For embedding in Logic Apps:

- **SaaS API route:** Glomidco operates the service — AGPL network use clause applies to Glomidco’s own deployment. Customers calling the API are not distributing UTL-X.
- **Built-in connector / NuGet route:** Customers embedding UTL-X in their Logic App project are distributing it. They must either comply with AGPL (open-source their modifications) or obtain a **commercial license** from Glomidco.

This creates a natural commercial licensing trigger: any enterprise Logic Apps Standard deployment using the built-in connector requires a commercial license — which is exactly the right buyer profile.

### Workato-specific note

For a platform-level integration (Workato embedding UTL-X in their core product), a **platform commercial license** is appropriate regardless of distribution route — Workato is distributing UTL-X as part of their SaaS product to their customers. Annual licensing with a perpetual buyout option remains the recommended structure.

-----

## 9. Key Resources & Links

### Microsoft documentation

- [Create built-in connectors for Logic Apps Standard](https://learn.microsoft.com/en-us/azure/logic-apps/create-custom-built-in-connector-standard)
- [Custom connectors overview](https://learn.microsoft.com/en-us/azure/logic-apps/custom-connector-overview)
- [Get your connector certified](https://learn.microsoft.com/en-us/connectors/custom-connectors/submit-certification)
- [Verified publisher certification process](https://learn.microsoft.com/en-us/connectors/custom-connectors/submit-for-certification)
- [Plan a SaaS offer for Microsoft Marketplace](https://learn.microsoft.com/en-us/partner-center/marketplace-offers/plan-saas-offer)
- [SaaS Fulfillment APIs](https://learn.microsoft.com/en-us/partner-center/marketplace-offers/pc-saas-fulfillment-apis)
- [Microsoft Marketplace overview for ISVs](https://www.microsoft.com/en-us/isv/marketplace)

### Tools & accelerators

- [SaaS Accelerator (reference implementation)](https://azure.github.io/isv-success-program-resources/marketplace/create-or-maintain-saas-offer/)
- [PowerPlatformConnectors GitHub repository](https://github.com/microsoft/PowerPlatformConnectors)
- [Partner Center](https://partner.microsoft.com)
- [ISV Success Program](https://www.microsoft.com/en-us/isv)

### UTL-X

- [UTL-X on GitHub](https://github.com/grauwen/utl-x)

-----

*Document prepared by Claude (Anthropic) based on public Microsoft documentation and UTL-X project context. April 2026.*