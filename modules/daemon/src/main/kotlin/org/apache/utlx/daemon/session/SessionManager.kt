// modules/server/src/main/kotlin/org/apache/utlx/server/session/SessionManager.kt
package org.apache.utlx.daemon.session

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe session manager for MCP client connections
 *
 * Manages concurrent sessions for both LSP and REST API clients,
 * allowing parallel requests to be handled safely.
 */
class SessionManager {
    private val logger = LoggerFactory.getLogger(SessionManager::class.java)
    private val sessions = ConcurrentHashMap<String, ClientSession>()
    private val sessionMutex = Mutex()

    /**
     * Create a new session for a client
     */
    suspend fun createSession(clientId: String? = null, metadata: Map<String, Any> = emptyMap()): ClientSession {
        val sessionId = clientId ?: UUID.randomUUID().toString()

        return sessionMutex.withLock {
            val session = ClientSession(
                id = sessionId,
                createdAt = Instant.now(),
                lastAccessedAt = Instant.now(),
                metadata = metadata.toMutableMap()
            )

            sessions[sessionId] = session
            logger.debug("Created session: $sessionId with metadata: $metadata")
            session
        }
    }

    /**
     * Get an existing session by ID
     */
    suspend fun getSession(sessionId: String): ClientSession? {
        return sessionMutex.withLock {
            sessions[sessionId]?.also {
                it.lastAccessedAt = Instant.now()
                it.requestCount++
            }
        }
    }

    /**
     * Update session metadata
     */
    suspend fun updateSession(sessionId: String, metadata: Map<String, Any>) {
        sessionMutex.withLock {
            sessions[sessionId]?.let { session ->
                session.metadata.putAll(metadata)
                session.lastAccessedAt = Instant.now()
                logger.debug("Updated session: $sessionId with metadata: $metadata")
            }
        }
    }

    /**
     * Remove a session
     */
    suspend fun removeSession(sessionId: String): Boolean {
        return sessionMutex.withLock {
            val removed = sessions.remove(sessionId) != null
            if (removed) {
                logger.debug("Removed session: $sessionId")
            }
            removed
        }
    }

    /**
     * Get all active sessions
     */
    suspend fun getAllSessions(): List<ClientSession> {
        return sessionMutex.withLock {
            sessions.values.toList()
        }
    }

    /**
     * Get count of active sessions
     */
    fun getSessionCount(): Int = sessions.size

    /**
     * Clean up expired sessions (not accessed for more than timeout)
     */
    suspend fun cleanupExpiredSessions(timeoutSeconds: Long = 3600) {
        val now = Instant.now()
        val expiredThreshold = now.minusSeconds(timeoutSeconds)

        sessionMutex.withLock {
            val expiredSessions = sessions.filter { (_, session) ->
                session.lastAccessedAt.isBefore(expiredThreshold)
            }

            expiredSessions.forEach { (id, _) ->
                sessions.remove(id)
                logger.info("Removed expired session: $id")
            }

            if (expiredSessions.isNotEmpty()) {
                logger.info("Cleaned up ${expiredSessions.size} expired sessions")
            }
        }
    }

    /**
     * Clear all sessions
     */
    suspend fun clearAllSessions() {
        sessionMutex.withLock {
            val count = sessions.size
            sessions.clear()
            logger.info("Cleared all $count sessions")
        }
    }
}

/**
 * Represents a client session with metadata and statistics
 */
data class ClientSession(
    val id: String,
    val createdAt: Instant,
    var lastAccessedAt: Instant,
    var requestCount: Long = 0,
    val metadata: MutableMap<String, Any> = mutableMapOf()
) {
    /**
     * Get session duration in seconds
     */
    fun getDurationSeconds(): Long {
        return java.time.Duration.between(createdAt, Instant.now()).seconds
    }

    /**
     * Get time since last access in seconds
     */
    fun getIdleTimeSeconds(): Long {
        return java.time.Duration.between(lastAccessedAt, Instant.now()).seconds
    }

    /**
     * Check if session is active (accessed within timeout)
     */
    fun isActive(timeoutSeconds: Long = 3600): Boolean {
        return getIdleTimeSeconds() < timeoutSeconds
    }

    /**
     * Get session statistics
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "created_at" to createdAt.toString(),
            "last_accessed_at" to lastAccessedAt.toString(),
            "duration_seconds" to getDurationSeconds(),
            "idle_time_seconds" to getIdleTimeSeconds(),
            "request_count" to requestCount,
            "metadata" to metadata
        )
    }
}
