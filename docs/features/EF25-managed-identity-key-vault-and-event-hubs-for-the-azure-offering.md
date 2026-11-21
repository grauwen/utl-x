# EF25 — managed identity, Key Vault references, and Event Hubs for the Azure offering

**Status:** Proposed — 2026-11-20
**Priority:** Medium–High. Four claims in the live Marketplace listing describe this feature as
though it already exists; two of them are the opposite of what ships. Either the listing changes or
this does.
**Component:** `deploy/azure-marketplace/` — both `mainTemplate.json` files and both
`createUiDefinition.json` files. **No engine change.**
**Relates to:** [EF24](EF24-the-data-plane-has-no-credential-and-cannot-be-published.md) (the two-key
model, which this does not alter), and `devdocs/azure/utlxe-deployment-modes.md` §6 for how Dapr
components are wired today. (`devdocs/` is not merged to `main`, so that is a path rather than a
link.)

## Why this exists

The Marketplace offer listing currently promises, verbatim:

> **Azure Key Vault** — broker credentials, API tokens, and connection strings are resolved at
> runtime via secret references; no plaintext secrets in your deployment.
>
> **Microsoft Entra ID** — managed identity / workload identity authentication to brokers, no
> connection strings required.
>
> **Azure Event Hubs** — ingest streaming events, transform them in-flight, and publish to another
> hub or a downstream sink. Partition keys, consumer groups, and Azure Storage checkpointing are
> configured declaratively.

None of it is in the templates:

```
KeyVault / keyVault / vaultUri                absent
identity / ManagedIdentity / userAssigned     absent
EventHub / eventhub                           absent
deadLetter / dlq                              absent
```

And the Entra ID claim is contradicted by the wizard, which *requires* a connection string and
validates its shape:

```
serviceBusConnection  regex: ^Endpoint=sb://.{12,}$
```

What ships instead: credentials are **Container App secrets**, pasted as plaintext into the
deployment wizard, and the only brokers wired are one Service Bus queue (in) and one Service Bus
topic (out).

## What the engine already does, and does not need

**No engine change is required.** Two facts settle it:

- The engine never handles a broker credential. `grep` for `Endpoint=sb://` or `SharedAccessKey`
  across `modules/engine/src/main` returns nothing. Credentials live entirely in Dapr components,
  and the engine talks to `daprd` over loopback with no authentication of its own.
- `DaprIntegration.generateEventHubBinding` already emits `bindings.azure.eventhubs` and
  `pubsub.azure.eventhubs`, with `consumerGroup`, `storageAccountName` and
  `storageContainerName: eventhub-checkpoints` for the pub/sub case — and **no credential metadata
  at all**, which is exactly the shape identity-based auth wants.

One thing to be clear about, because it is easy to misread as "already done": that generator writes
**YAML files** to a `componentsDir`, which is the self-hosted and Kubernetes Dapr model. Azure
Container Apps does not read component YAML — components are ARM resources
(`Microsoft.App/managedEnvironments/daprComponents`), created by the template. The Azure deployment
passes no `--dapr-components-dir`, so the generator is unused on that path. The code is useful
evidence that the component shapes are understood; it is not the implementation.

## Part 1 — a managed identity on the Container App

### The RBAC question, answered

The open question in the first draft of this report was whether a Managed Application's template
can create a role assignment outside its managed resource group. **It cannot, by default**, and the
answer reshapes the design rather than merely warning about it:

- The managed application's deployment principal holds **Owner on the managed resource group only**.
- A role assignment at any external scope needs `Microsoft.Authorization/roleAssignments/write` on
  the target — Owner, User Access Administrator, or RBAC Administrator — which an ordinary
  Marketplace purchaser will not have, and which the appliance principal does not get.
- The supported pattern for reaching an external resource is the other way round: **the identity is
  granted its role beforehand, and the deployment consumes it.**

### Consequence: a USER-assigned identity, not a system-assigned one

The first draft proposed `"identity": { "type": "SystemAssigned" }`. That is the wrong choice here,
and for a structural reason: a system-assigned identity **does not exist until the Container App is
created**, so the customer cannot grant it a Service Bus role in advance. The deployment would have
to create the role assignment itself, which is exactly what it is not permitted to do. Chicken and
egg.

A user-assigned identity exists first and independently, so the ordering works:

```
1. customer creates (or picks) a user-assigned managed identity
2. customer grants it  Azure Service Bus Data Receiver  on the input queue
                       Azure Service Bus Data Sender    on the output topic
   -- they own the namespace, so they can
3. customer selects that identity in the deployment wizard
4. the template attaches it to the Container App and names its client id in the Dapr components
```

No role assignment happens during deployment at all. Nothing needs a permission the purchaser
lacks.

```json
"identity": {
  "type": "UserAssigned",
  "userAssignedIdentities": { "[parameters('managedIdentityResourceId')]": {} }
}
```

and the Dapr component drops `connectionString` in favour of:

```json
{ "name": "namespaceName",  "value": "[parameters('serviceBusNamespace')]" },
{ "name": "azureClientId",  "value": "[parameters('managedIdentityClientId')]" }
```

The wizard asks for the identity with `Microsoft.ManagedIdentity.IdentitySelector`, which is the
portal element made for this. **Confirm the element name and the exact shape of its output against
the current createUiDefinition schema before relying on it** — it is the one piece of this design
not verifiable from this repository, and the wizard is where a wrong guess costs a certification
round trip.

Two further things the customer needs and the template cannot supply:

- The deploying user needs
  `Microsoft.ManagedIdentity/userAssignedIdentities/assign/action` on the identity. Whoever created
  it has this.
- The Service Bus **namespace name** becomes a parameter in its own right. Today it is implicit
  inside the connection string, which is why there is no namespace field in the wizard.

### Keep the connection string as an option

Not a fallback to be embarrassed about. Three groups need it: a customer without rights to create
identities, one evaluating the offer in an afternoon, and anyone whose broker is not Azure. So
`authMode` becomes a wizard choice — *Connection string* or *Managed identity (recommended)* — and
the Dapr components are built conditionally on it. The feature then ships without breaking any
existing deployment, and the listing can describe both honestly.

### What the listing may then claim

Accurately: *"Managed identity authentication to Azure brokers — no connection strings stored in
your deployment."* The prerequisite is one sentence, not a footnote: **you grant the identity its
Service Bus roles before deploying.** What it still may not claim is that there is nothing to
configure.

## Part 2 — Key Vault references for the secrets that remain

Container App secrets can resolve from Key Vault rather than holding a literal:

```json
{ "name": "servicebus-connection",
  "keyVaultUrl": "https://<vault>.vault.azure.net/secrets/<name>",
  "identity": "system" }
```

This needs the same user-assigned identity from Part 1, plus *Key Vault Secrets User* on the vault
— granted by the customer beforehand, for the same reason and in the same step.

Worth being precise about what it buys. It removes the secret from the **ARM deployment history**
and from the wizard, which is real: today a connection string pasted into the wizard is a
`secureString`, so it is not echoed back, but it has still travelled through a browser form.
What it does **not** do is hide the secret from anyone who can read the Container App, because the
resolved value is still mounted into the container.

It also applies to `adminKey` and `dataPlaneKey` from EF24, and that is arguably the more useful
half: a customer rotating a leaked data-path key could then change it in Key Vault rather than
redeploying the template — which is the gap EF24 leaves open.

## Part 3 — Event Hubs as a third broker

**Output shipped 2026-11-20. Input not shipped, deliberately.**

```
enableEventHubs      bool, default false
eventHubConnection   securestring, Send rights
outputEventHub       string, default utlx-output
-> bindings.azure.eventhubs, direction output, scoped to the app
-> containerEnv routes UTLXE_OUTPUT_BINDING_SERVICEBUS_INPUT to 'eventhub-output'
```

Both plans, arm-ttk clean, with a wizard section whose fields appear only when the option is on.

### Why output first, and what input still needs

The split is not arbitrary. Dapr's Event Hubs **output** binding needs only a connection string and
an entity name. The **input** binding additionally needs `consumerGroup` *and* a checkpoint store --
`storageAccountName` with a key or connection string, plus `storageContainerName` -- because it
tracks partition offsets. That drags in three decisions this pass deliberately did not take:

- **Whose storage account.** The template creates one only when `enablePersistentStorage` is on, and
  that share is also the transformation volume and ch11's "second control path". Coupling Event Hubs
  checkpoints to it couples two unrelated concerns; a separate container is a compromise, a separate
  account is cleaner, and requiring the customer's own is a fourth option.
- **A KEDA trigger of a different type.** `serviceBusScaleRule` is `azure-servicebus` on queue
  depth; Event Hubs scales on unprocessed events per partition. Without it an Event Hubs input never
  scales -- currently moot, because `maxReplicas` is pinned to 1, and it stops being moot the day
  that pin lifts.
- **Partition keys.** The old listing claimed these were "configured declaratively". They are not
  configured anywhere: `grep` for `partitionKey` across `modules/engine/src/main` returns nothing, so
  the engine passes none. Supporting them is an engine change (per-message Dapr metadata from
  `transform.yaml`), not a template one.

Output-only is still a real capability -- Service Bus queue in or HTTP in, Event Hub out, which is
the common "transform and fan out to a stream" shape -- and it is honest about what it does.

Two components, mirroring the Service Bus pair:

| direction | componentType | metadata |
|---|---|---|
| in | `bindings.azure.eventhubs`, or `pubsub.azure.eventhubs` with a consumer group | `eventHubNamespace`, `eventHub`, `consumerGroup`, `storageAccountName`, `storageContainerName` |
| out | `bindings.azure.eventhubs` | `eventHubNamespace`, `eventHub` |

Three things Event Hubs needs that Service Bus does not:

- **A checkpoint store.** Dapr's Event Hubs pub/sub requires an Azure Storage account and container
  to track partition offsets. The template already creates a storage account when
  `enablePersistentStorage` is on — but reusing the transformation volume's account for checkpoints
  couples two unrelated concerns, and the share is also the second control path described in ch11.
  A separate container in the same account is the compromise; a separate account is cleaner.
  **Decide deliberately rather than by convenience.**
- **A KEDA scale rule of its own.** The current `serviceBusScaleRule` is `azure-servicebus` on queue
  depth. Event Hubs scaling keys on unprocessed events per partition, which is a different trigger
  type. Without it an Event Hubs deployment never scales — and since `maxReplicas` is pinned to 1
  (see the deployment-modes doc), that is currently moot and will stop being moot the day the pin
  lifts.
- **Partition keys on output.** The listing says they are "configured declaratively"; in Dapr they
  are per-message metadata, so this is a `transform.yaml` concern on the transformation, not a
  template one. Check what the engine actually passes before promising it.

`messagingMode` grows from two values to three (`http`, `servicebus`, `eventhubs`) — or, better,
becomes a multi-select, since nothing prevents a deployment from consuming Service Bus *and*
publishing to an Event Hub. The current dropdown's either/or framing is a wizard artefact, not an
engine constraint.

## What happens to the EF24 API key

It survives, because this feature points the other way.

```
INBOUND   who may call us          X-Api-Key, checked by nginx        <- EF24
OUTBOUND  how we reach a broker    connection string / identity       <- EF25
```

Managed identity authenticates UTLXe *to* Service Bus. It says nothing about who may POST to
`/api/transformations/{id}/run`. So Parts 1 and 3 leave the data-path key exactly as it is, and the
two-key model in EF24 and ch11 stands unchanged.

Part 2 does touch it, usefully. `keyVaultUrl` works for `adminKey` and `dataPlaneKey` as well as for
broker credentials, and that closes the gap EF24 leaves open: today rotating a leaked data-path key
means **redeploying the template**, because the value is baked into the nginx config by `envsubst`
at container start. With a Key Vault reference the rotation becomes "new secret version, restart the
revision" — no template, no ARM deployment, no risk of a redeploy changing something else. Still a
revision restart, because nginx reads the value once; but a far smaller operation.

That is arguably the most valuable single piece of Part 2, and it is the piece with the least to do
with brokers.

### The question Part 1 raises but does not answer

If the *outbound* direction stops using a shared secret, why does the *inbound* direction still use
one? A bearer key is a bearer key: whoever holds it is you, it appears in client configuration, and
it has no expiry.

The Azure-native answer is **Container Apps built-in authentication** —
`Microsoft.App/containerApps/authConfigs` with an Entra ID provider. The platform validates a JWT
before the request reaches nginx, so a caller presents a token from its own managed identity and
there is no key to issue, rotate, or leak. Nothing in the template uses it today:

```
authConfigs / globalValidation / identityProviders / excludedPaths    absent
```

It is genuinely attractive and it is **not** part of this feature, for three reasons worth writing
down before someone bundles them:

- **It gates the whole ingress, including `/health`.** The container probes are unaffected — they
  hit port 8081 directly, not through the ingress — but `utlxe-acceptance.sh` polls
  `https://<fqdn>/health` and would break. `globalValidation.excludedPaths` can carve `/health` and
  `/metrics` out, which then has to be a deliberate, documented hole rather than an oversight.
- **It changes onboarding from "paste a key" to "register an app and grant access".** For a
  customer evaluating the offer in an afternoon that is a materially worse first experience. Any
  design has to keep the key path available, exactly as Part 1 keeps the connection string.
- **It is Azure-only, and the listing sells portability.** "Runs anywhere Dapr runs — the broker is
  a YAML configuration change, not a code change. No vendor lock-in." The engine stays portable and
  the nginx key is portable; platform auth is not. A customer who builds against Entra ID tokens
  cannot move that integration to GKE unchanged. This is not an argument against offering it — it
  is an argument for it being an *option*, never the only door.

So: a separate feature, after Part 1 has settled the RBAC question, since both depend on the same
answer about what a Managed Application may do with identities. Worth raising with the first
customer who asks about inbound authentication rather than deciding it in advance.

## Scope

| part | work | risk |
|---|---|---|
| 1 | `UserAssigned` identity selector, identity-based components, `authMode` wizard choice, namespace parameter | RBAC question **answered**; remaining unknown is the `IdentitySelector` element shape |
| 2 | `keyVaultUrl` on secrets, optional vault fields in the wizard | needs the same pre-granted identity, plus *Key Vault Secrets User* on the vault — again granted by the customer beforehand |
| 3 | two Event Hubs components, checkpoint store decision, KEDA rule, wizard fields | self-contained; no dependency on Parts 1–2 |
| all | `build-zips.sh` plan-equivalence guard keeps both plans in step for free | — |
| all | acceptance assertions, and a deployment per auth mode | needs a real Service Bus namespace |
| 2 | `keyVaultUrl` for `adminKey` and `dataPlaneKey` too — closes EF24's rotation gap | smallest piece, largest operational payoff |

**Engine: nothing.** This is the unusual case where a release's worth of Azure work needs no Kotlin.

Part 3 is independent and the least risky, so it can ship first. Parts 1 and 2 share the
pre-granted-identity prerequisite, so they ship together.

## Until this ships, the listing is wrong

Four bullets describe behaviour that does not exist, and a customer meets two of them on day one:
they will look for the managed-identity option and find a required connection-string field. The
listing should be corrected now and expanded again when this lands — see the review recorded
against the offer description. Correcting text costs nothing and is reversible; a customer who
bought on the Entra ID bullet is a support conversation that starts badly.
