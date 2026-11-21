# XLSX Docker test gateway did not publish its host port

The first Linux test deployment could build successfully and start both containers,
but `curl http://127.0.0.1:18090/api/health` failed with connection refused.

Both containers were attached exclusively to an internal Docker bridge. Publishing
a host port requires the gateway container to have a non-internal network through
which Docker can install the mapping. Container startup alone did not verify it.

The gateway now joins both the internal engine network and a separate normal
bridge. UTLXe stays on the internal network, and the host binding remains
`127.0.0.1:18090`. The gateway also has an HTTP health check against the engine.

The recovery instructions recreate only the gateway and reuse the existing engine
image. Linux verification must confirm the published port and a successful host
health request; local YAML validation alone does not establish runtime connectivity.
