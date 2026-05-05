= Appendix B: Configuration Reference

_All configuration options: environment variables, engine.yaml, and transform.yaml._

== Environment Variables

// UTLXE_HEAP_SIZE     JVM heap size (default: 1536m for Starter, 3072m for Professional)
// UTLXE_ADMIN_KEY     Admin API authentication key (required, no default)
// JAVA_OPTS           Additional JVM options (default: G1GC, container support, AlwaysPreTouch)

== Engine Configuration (engine.yaml)

// maxInputSize: 5MB          Maximum message size before rejection
// workers: 4                  Worker thread pool size (default: CPU cores)
// healthPort: 8081            Health + admin API port
// dataPort: 8085              Data plane port

== Transformation Configuration (transform.yaml)

// strategy: COMPILED          Execution strategy (TEMPLATE | COPY | COMPILED | AUTO)
// validationPolicy: strict    Input validation (strict | warn | off)
// maxConcurrent: 4            Max concurrent executions for this transformation
// inputs:
//   - name: input             Input slot name
//     schema: order.xsd       Schema file reference (optional)
// output:
//   schema: invoice.xsd       Output schema reference (optional)

== JVM Tuning

// -XX:+UseG1GC               G1 garbage collector (default)
// -XX:MaxGCPauseMillis=200   Target max GC pause
// -XX:+UseContainerSupport   Respect cgroup memory limits
// -XX:+AlwaysPreTouch        Allocate heap at startup — fail fast if not enough RAM

== Container App Plans

// Starter (2GB container):
//   UTLXE_HEAP_SIZE=1536m (75% of 2GB)
//   Suitable for messages up to ~50KB
//   1-2 vCPU
//
// Professional (4GB container):
//   UTLXE_HEAP_SIZE=3072m (75% of 4GB)
//   Suitable for messages up to ~200KB
//   2-4 vCPU

== Bundle ZIP Format

// bundle.zip
//   schemas/                    (optional)
//     order.xsd
//     invoice.json
//   transformations/
//     {name}/
//       {name}.utlx             (required)
//       transform.yaml          (optional — defaults apply)
//   engine.yaml                 (optional — engine config overrides)

== Data Directory Layout

// /utlxe/data/                  Written by Admin API, read at startup
//   schemas/
//     order.xsd
//   transformations/
//     invoice-to-ubl/
//       invoice-to-ubl.utlx
//       transform.yaml
