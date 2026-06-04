= Troubleshooting

This chapter lists the most common problems, their symptoms, and how to fix them.

== Container Starts But `ready=false`

*Symptom:* Health endpoint returns `{"status":"UP", "transformations":0, "ready":false}`. No traffic is routed.

*Cause:* No transformations are loaded. The container started empty.

*Fix:*
- If using persistent storage: check that the Azure File Share is mounted correctly. Run `az containerapp show` and verify the volume mount configuration.
- If using CI/CD re-deploy: check that the pipeline ran and uploaded the bundle.
- Manual fix: upload a transformation via the Admin API.

== Transformation Upload Fails (400)

*Symptom:* `POST /admin/transformations/{name}` returns 400 with an error message.

*Cause:* Syntax error in the `.utlx` source. The compilation failed.

*Fix:* Read the error response --- it includes the line number and a description of the problem.

```json
{
  "status": "rejected",
  "errors": [
    {"transformation": "invoice", "line": 14, "message": "Unknown function: concatX"}
  ]
}
```

Test locally before uploading: `echo '{}' | utlx -e 'your expression'` catches syntax errors immediately.

== Messages Failing (500 on Data Plane)

*Symptom:* The data plane returns 500 for some or all messages. Error count increases in Prometheus.

*Cause:* Runtime error in the transformation --- null reference, type mismatch, missing field.

*Diagnose:*

```bash
curl -H "X-Admin-Key: $KEY" \
  https://<your-fqdn>/admin/transformations/invoice-to-ubl/errors
```

This shows the last 100 errors with the input that caused each failure.

*Fix:* Upload a corrected transformation. Test with the failing input before resuming:

```bash
curl -X POST -H "X-Admin-Key: $KEY" \
  -d '{"the":"failing input"}' \
  https://<your-fqdn>/admin/transformations/invoice-to-ubl/test
```

== High Latency

*Symptom:* p99 latency exceeds 100ms or response times are inconsistent.

*Cause 1 --- Large messages:* Parsing and serialization dominate for messages above 50 KB.

Check average message size and consider whether the transformation can be simplified, or upgrade to the Professional plan for more memory.

*Cause 2 --- GC pressure:* The heap is too small for the workload.

Check `utlxe_heap_used_bytes` in Prometheus. If it stays above 80% of the max, upgrade the plan.

*Cause 3 --- Swap:* The container has swap enabled.

Fix: set memory request equal to memory limit in the Container App configuration. See Chapter 7 for details.

== Out of Memory (OOM Kill)

*Symptom:* Container restarts unexpectedly. Container logs show `Killed` or the exit code is 137.

*Cause:* A message (or burst of messages) consumed more heap than available. The JVM exceeded the container memory limit, and Kubernetes killed the process.

*Fix:*
- *Heap backpressure* is enabled by default (rejects above 92%, resumes below 80%). When heap usage exceeds the high-water mark, UTLXe rejects incoming work with 503 --- Dapr messages stay in Service Bus and are retried when pressure drops. Check if the threshold is too high for your workload: `GET /admin/backpressure`.
- Set `maxInputSize` per transformation to reject oversized messages before parsing (e.g., `"maxInputSize": "100KB"` in the transformation config).
- Upgrade to a larger plan (more memory = more headroom for DOM expansion).
- Check for transformations that create large intermediate objects (deeply nested `map` of `map` operations).

To adjust the backpressure threshold at runtime:

```bash
curl -X POST -H "X-Admin-Key: $KEY" \
  -d '{"threshold": 80}' \
  https://<your-fqdn>/admin/backpressure
```

Or use the Web UI: *Config* tab → *Heap Backpressure* card → adjust the threshold dropdown.

== Admin API Returns 403

*Symptom:* All admin endpoints return 403 Forbidden.

*Cause 1:* `UTLXE_ADMIN_KEY` is not set. When no key is configured, all admin endpoints are locked.

*Cause 2:* The `X-Admin-Key` header value does not match the environment variable.

*Fix:* Verify the key:

```bash
# Check if the env var is set
az containerapp show -n utlxe -g myResourceGroup \
  --query "properties.template.containers[0].env"
```

== Transformations Lost After Restart

*Symptom:* After a container restart, health shows `transformations: 0`.

*Cause:* No persistent storage is configured. The container filesystem is ephemeral.

*Fix:*
- Enable persistent storage (Azure Files volume mount) by redeploying with the "Enable persistent transformation storage" option.
- Or set up a CI/CD pipeline to re-deploy the bundle after each container start.

== Dapr Not Delivering Messages

*Symptom:* Messages are in the Service Bus queue but UTLXe is not processing them.

*Cause 1 --- Not ready:* UTLXe health shows `ready: false`. Dapr waits for the app to be ready before delivering messages.

*Fix:* Upload transformations. Dapr begins delivering once `ready: true`.

*Cause 2 --- Binding name mismatch:* The Dapr component name does not match a transformation name, and no `X-UTLXe-Transform` header is configured.

*Fix:* Verify that the Dapr component `metadata.name` matches the transformation name, or configure the transform header override.

*Cause 3 --- Transformation is paused:* UTLXe returns 503, Dapr does not acknowledge.

*Fix:* Resume the transformation:

```bash
curl -X POST -H "X-Admin-Key: $KEY" \
  https://<your-fqdn>/admin/transformations/{name}/resume
```

== Schema Validation Failing Unexpectedly

*Symptom:* Messages that used to work are now rejected by validation.

*Cause:* The schema was updated but the incoming messages still use the old format.

*Quick fix:* Disable validation temporarily:

```bash
curl -X POST -H "X-Admin-Key: $KEY" \
  -d '{"policy":"off"}' \
  https://<your-fqdn>/admin/transformations/{name}/validation
```

*Proper fix:* Update the transformation to handle both the old and new message formats, or coordinate the schema change with the message producer.

== Diagnostic Commands Reference

```bash
# Health and readiness
curl https://<your-fqdn>/health

# Engine info (version, uptime, config)
curl -H "X-Admin-Key: $KEY" https://<your-fqdn>/admin/info

# List transformations with status
curl -H "X-Admin-Key: $KEY" https://<your-fqdn>/admin/transformations

# Recent errors for a transformation
curl -H "X-Admin-Key: $KEY" \
  https://<your-fqdn>/admin/transformations/{name}/errors

# Test a transformation
curl -X POST -H "X-Admin-Key: $KEY" \
  -d '{"test":"data"}' \
  https://<your-fqdn>/admin/transformations/{name}/test

# Effective validation state
curl -H "X-Admin-Key: $KEY" \
  https://<your-fqdn>/admin/transformations/{name}/validation

# Container logs
az containerapp logs show -n utlxe -g myResourceGroup --follow
```
