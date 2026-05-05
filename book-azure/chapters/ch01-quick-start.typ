= Quick Start

_Deploy UTLXe from the Azure Marketplace, upload your first transformation, and send your first message — all in under 15 minutes._

== What You Get

// - UTLXe container running on Azure Container Apps
// - Admin API on port 8081 (internal)
// - Data plane on port 8085 (ingress)
// - Optional: Azure Files for persistent storage

== Deploy from the Azure Marketplace

// Step-by-step with portal screenshots:
// 1. Find "UTLXe" in the Azure Marketplace
// 2. Select plan (Starter 2GB or Professional 4GB)
// 3. Configure: resource group, region, persistent storage toggle
// 4. Deploy — takes ~2 minutes

== Verify the Deployment

// curl -s http://<internal-ip>:8081/health
// → {"status":"UP", "transformations":0, "ready":false}
// Container is running but has no transformations yet.

== Upload Your First Transformation

// Write a simple .utlx file
// curl -X POST -H "X-Admin-Key: $KEY" -F "source=@hello.utlx" .../admin/transformations/hello
// → {"status":"deployed", "name":"hello", "compiled_in_ms":48}

== Test It

// curl -X POST -H "X-Admin-Key: $KEY" -d '{"name":"World"}' .../admin/transformations/hello/test
// → {"status":"ok", "output":{"greeting":"Hello, World!"}, "duration_ms":2}

== Send a Real Message

// curl -X POST -d '{"name":"Azure"}' http://<ingress>:8085/api/transform/hello
// → {"greeting":"Hello, Azure!"}

== What Just Happened

// Diagram: Client → :8085 data plane → Engine → compiled transformation → response
// The transformation was compiled once at upload time. Every subsequent message
// executes the compiled version — typically 1-5ms per message.

== Next Steps

// - Chapter 2: Write more complex transformations
// - Chapter 3: Learn the full Admin API
// - Chapter 4: Connect to Service Bus / Event Hub
