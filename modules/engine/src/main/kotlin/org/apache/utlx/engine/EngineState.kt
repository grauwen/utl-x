package org.apache.utlx.engine

enum class EngineState {
    CREATED,
    INITIALIZING,
    READY,
    RUNNING,
    DRAINING,
    STOPPED
}
