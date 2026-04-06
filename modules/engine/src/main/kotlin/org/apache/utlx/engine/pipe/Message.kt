package org.apache.utlx.engine.pipe

data class Message(
    val correlationId: String? = null,
    val payload: ByteArray,
    val contentType: String = "application/json",
    val headers: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Message) return false
        return correlationId == other.correlationId &&
            payload.contentEquals(other.payload) &&
            contentType == other.contentType &&
            headers == other.headers
    }

    override fun hashCode(): Int {
        var result = correlationId?.hashCode() ?: 0
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + headers.hashCode()
        return result
    }

    override fun toString(): String {
        return "Message(correlationId=$correlationId, payloadSize=${payload.size}, contentType=$contentType)"
    }
}
