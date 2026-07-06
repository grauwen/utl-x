package com.glomidco.utlx.engine.pipe

interface OutputPipe {
    val name: String
    fun write(message: Message)
    fun close()
}
