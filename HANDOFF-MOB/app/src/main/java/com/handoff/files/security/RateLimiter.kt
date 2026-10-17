package com.handoff.files.security

import com.handoff.files.protocol.ProtocolConstants
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe rate limiter to prevent online brute force against low-entropy pairing codes.
 * Max 3 failed attempts before enforcing a 30-second lockout.
 */
class RateLimiter(
    private val maxAttempts: Int = ProtocolConstants.MAX_FAILED_PAIR_ATTEMPTS,
    private val lockoutDurationMs: Long = ProtocolConstants.RATE_LIMIT_LOCKOUT_MS
) {
    private data class AttemptRecord(
        var failureCount: Int = 0,
        var lockedUntilTimestamp: Long = 0L
    )

    private val records = ConcurrentHashMap<String, AttemptRecord>()

    @Synchronized
    fun isLockedOut(clientIdentifier: String): Boolean {
        val record = records[clientIdentifier] ?: return false
        val now = System.currentTimeMillis()
        if (now < record.lockedUntilTimestamp) {
            return true
        }
        // If lockout expired, reset
        if (record.lockedUntilTimestamp > 0 && now >= record.lockedUntilTimestamp) {
            records.remove(clientIdentifier)
        }
        return false
    }

    @Synchronized
    fun recordFailure(clientIdentifier: String): Boolean {
        val now = System.currentTimeMillis()
        val record = records.computeIfAbsent(clientIdentifier) { AttemptRecord() }
        record.failureCount++

        if (record.failureCount >= maxAttempts) {
            record.lockedUntilTimestamp = now + lockoutDurationMs
            return true // Is now locked out
        }
        return false
    }

    @Synchronized
    fun recordSuccess(clientIdentifier: String) {
        records.remove(clientIdentifier)
    }

    @Synchronized
    fun reset() {
        records.clear()
    }
}
