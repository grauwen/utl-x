# UTL-X 1.4.1 — Release Notes

**A packaging release.** The engine, CLI, stdlib and formats are unchanged from
[1.4.0](release-notes-1.4.0.md); `1.4.1` images are equivalent in content. What changed is the
Azure Marketplace deployment artifact, which in 1.4.0 — and in every generation before it —
could not deploy.

## The Marketplace templates could not deploy

The `container-app` nested deployment runs with `expressionEvaluationOptions.scope = "inner"`
and referenced `parameters('adminKey')`, which was never declared in that nested template nor
passed to it. Under inner scope a nested template sees only its own parameters, so ARM rejects
the deployment with `InvalidTemplate` before creating any resource.

Present in the 1.1.0, 1.2.1 and 1.4.0 packages. Fixed by declaring `adminKey` as a
`secureString` on the nested template and passing it through from the parent.

## Service Bus messaging is reachable for the first time

The templates have always contained the full messaging path — a Dapr input binding on a queue,
an output binding on a topic, and KEDA scaling on queue depth — gated behind
`enableDapr && scalingMode != 'http'`. **None of the five messaging parameters were settable by
the wizard**, so every Marketplace deployment got `enableDapr=false` and an empty connection
string. The feature was unreachable, not merely off by default.

Both plans now carry a **Message Source** step: HTTP only (the default, unchanged behaviour) or
Azure Service Bus, which collects a connection string, queue and topic.

Two silent-failure modes were closed with it:

- The engine is now passed `UTLXE_OUTPUT_BINDING_SERVICEBUS_INPUT=servicebus-output` when Dapr
  is enabled. Without it, output routing depended on the uploaded transformation naming the
  binding itself; a transformation that omitted it consumed messages and published nothing,
  with no error.
- The wizard states that a transformation must be named `servicebus-input`, because the engine
  resolves the transformation from the binding name when no `X-UTLXe-Transform` header is set.

Plan sizing is unchanged and remains the only difference between plans: Starter 8 workers,
2 vCPU, 4 GiB; Professional 32 workers, 4 vCPU, 8 GiB.

## Images

```
ghcr.io/utlx-lang/utlxe:1.4.1      ghcr.io/utlx-lang/utlxe:latest
ghcr.io/utlx-lang/utlxe-ui:1.4.1   ghcr.io/utlx-lang/utlxe-ui:latest
```

Both `linux/amd64`, both public, verified by the `verify-anonymous-pull` job.

## Verification

- 4956 JVM tests, 0 failures; 582/582 conformance — carried over from 1.4.0, no code changed
- The `adminKey` defect was found by static analysis of parameter references against
  declarations. Confirm with `az deployment group validate` before submitting.
