package org.apache.utlx.engine.pipe

interface InputPipe {
    val name: String
    fun read(): Message
    fun tryRead(): Message?
    fun close()
}
