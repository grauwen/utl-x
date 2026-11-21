# EB22 — a failed Dapr output publish is acknowledged to Dapr as success

> **EB = engine bug** (`modules/engine` — the execution strategies and the runtime they call).

**Status:** Open — found 2026-11-21. **Observability half implemented, not yet demonstrated;
delivery semantics deliberately unchanged.**

- *Implemented:* the failure is now counted (`TransformationInstance.outputFailures`), logged at
  ERROR as `OUTPUT LOST`, exposed as `utlxe_transformation_output_failures_total` and on
  `/admin/transformations/{name}`, and the summary line that followed — which used to report a
  completed round trip whatever happened — now says the output was lost. Compiles; `:core` 441 and
  `:engine` 1054 pass.
- *Not demonstrated:* an end-to-end harness assertion for it did not reproduce. See *Verification
  gap* below. The code is in; the proof is not.
- *Unchanged on purpose:* the message is still acknowledged. That is a decision about delivery
  semantics, not a typo — see *Why this is not a one-line fix*.
**Priority:** High for anyone using the Service Bus or Event Hubs path — results are dropped
silently, with a log line that reads like success.
**Component:** `:modules:engine` — `transport/HttpTransport.kt`, `handleDaprInput` and
`handlePubSubInput`
**Found:** 2026-11-21, while verifying
[EF26](../features/EF26-dapr-routing-binds-a-transformation-to-a-component-name.md) item 2d
against a real `daprd`. Not by a test — the Dapr path had no automated coverage until
`scripts/dapr-local-test.sh` was written the same day.

## Summary

A message arrives through Dapr, the transformation succeeds, and the engine publishes the result
to the configured output. **If that publish fails, the engine logs a warning and then tells Dapr
the message was handled successfully.** Dapr removes it from the queue. The result is gone: no
retry, no dead-letter, no error surfaced to anything that is watching.

```kotlin
if (daprStatus !in 200..299) {
    logger.warn("Dapr output {} '{}' returned {} ({}ms) body={}", …)      // the only signal
}
…
logger.info("[{}] MessageId={} → OutputMessageId={} CorrelationId={} in {}us", …)  // UNCONDITIONAL

// Return success to Dapr (acknowledges the message)
call.respond(HttpStatusCode.OK, mapOf("success" to true, …))
```

Both handlers do it — `handleDaprInput` at the lines above, and `handlePubSubInput` with
`call.respond(HttpStatusCode.OK, mapOf("success" to true))`.

## Why it is easy to miss

The `info` line is logged whatever happened, and it reads like a completed round trip:

```
[orders] MessageId=51cce840-… → OutputMessageId=01a1ffe3-… CorrelationId= in 3421us
```

`OutputMessageId` is generated locally before the publish is attempted, so its presence says
nothing about whether anything was delivered. The failure is one `WARN` line above it, in a
different format and without the `[transformation]` prefix, so it does not appear when an operator
greps for the transformation by name. That is precisely how it was missed during EF26 testing: a
publish to a component that does not exist was read as a success until the right line was found.

## Failure scenario

Reproduced with `scripts/dapr-local-test.sh`'s setup — a real `daprd 1.17.6`, an in-memory pub/sub
component, and a transformation whose output names a component that does not exist:

```
engine:  WARN  Dapr output topic 'results-out' returned 404
engine:  INFO  [orders] MessageId=… → OutputMessageId=… in 3421us
engine → Dapr:  200 {"success":true, …}
daprd:   message acknowledged, removed from the topic
```

Realistic causes, none exotic:

- an output component the templates never created, or renamed — exactly the class of mismatch
  EF26 item 2c now guards against at build time;
- a Service Bus topic that does not exist, or a connection string without Send rights;
- the sidecar restarting, or a transient broker outage mid-publish.

The last one is the worst: a transient fault silently discards traffic for its duration, and
nothing downstream can tell the difference between "no messages arrived" and "messages arrived and
were thrown away".

## Contrast with how other failures behave

The engine is not careless about this in general. A *transformation* failure answers 500 with
`"success": false`, which is what makes Dapr abandon or retry — verified in
`EF24RawBodyRouteTest` and visible at `HttpTransport.kt:686` and `:923`. A paused transformation
answers 503. Only the **output** leg is exempt, and only because its result is never folded into
the response.

## Verification gap

The observability change is in the code and the engine builds, but I could not get
`scripts/dapr-local-test.sh` to exercise it reliably. A manual run produced the failing publish
and its 404; the same sequence inside the harness produced a successful publish, and with DEBUG
enabled the handler's `Output → <url>` line never appeared — which suggests the pub/sub output
block was not entered at all, rather than the failure being mishandled.

That is a different question from this bug, and possibly a worse one: if an output block can be
skipped for a transformation that has an output configured, results are lost with no log line at
all. **That is the thread to pull next**, before this report is closed.

No harness assertion was added. One that cannot be explained is worse than none.

## Why this is not a one-line fix

Changing `call.respond(OK)` to a failure status would make Dapr retry, and that is not obviously
right:

- **A permanent misconfiguration becomes a poison-message loop.** An output component that does
  not exist will never start existing. Every message would be retried until the broker's own
  dead-letter limit is reached, turning a silent drop into an unbounded retry storm — arguably
  worse, and much louder for the wrong reason.
- **The transformation has already run.** Retrying re-executes it. That is fine for a pure
  transformation and not fine if it has side effects, which `validate.*` rules and pipelines can
  have.
- **Service Bus and Event Hubs differ.** A queue binding can be abandoned and redelivered; an
  Event Hubs partition read is a checkpoint, and "retry" means something else entirely.

So the real options are a decision, not an edit:

1. **Fail the delivery** — correct at-least-once semantics, with the poison-message risk above.
   Probably right *if* paired with a bounded retry and a configurable give-up.
2. **Keep acknowledging, but make the failure loud** — raise the log to ERROR, fold the outcome
   into the `info` line so it stops reading like success, and expose it as a metric
   (`utlxe_output_publish_failures_total`) and on `/admin/transformations/{name}`. Smallest change,
   no semantic risk, and it ends the *silence* even if it does not end the loss.
3. **Make it per transformation** — `onOutputFailure: ack | nack` in `transform.yaml`, defaulting
   to today's behaviour. Most flexible, most to document.

Option 2 is worth doing regardless of which of 1 or 3 is eventually chosen, because the present
behaviour is not merely lossy — it is *indistinguishable from working*.

## Tests, when it is fixed

`scripts/dapr-local-test.sh` already produces the failure: it points an output at
`no-such-component` and asserts the 404 appears. Extend it to assert whatever the chosen
behaviour is — the response code the engine returns to Dapr, and whether daprd redelivers.

A vacuity guard matters here: the test must distinguish "the publish failed" from "the message was
dropped". Asserting only the `WARN` would pass under every option above.

## Notes

The Dapr path had **no automated coverage of any kind** until 2026-11-21, and
`utlxe-acceptance.sh` still has no Service Bus assertions. This bug has therefore been reachable
for as long as the Dapr integration has existed, in a code path that no test has ever executed.
