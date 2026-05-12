package io.github.ajitkumarmaurya.imdbkt.network

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicLong

/**
 * Token-bucket rate limiter.  Limits outgoing requests to [maxRequestsPerSecond]
 * per second, blocking callers until a token is available.
 */
internal class RateLimiter(private val maxRequestsPerSecond: Int = 2) {

    private val semaphore = Semaphore(maxRequestsPerSecond)
    private val lastRefillTime = AtomicLong(System.currentTimeMillis())

    fun acquire() {
        refillIfNeeded()
        semaphore.acquire()
    }

    private fun refillIfNeeded() {
        val now = System.currentTimeMillis()
        val last = lastRefillTime.get()
        val elapsed = now - last

        if (elapsed >= WINDOW_MS) {
            if (lastRefillTime.compareAndSet(last, now)) {
                val permits = semaphore.availablePermits()
                val toAdd = maxRequestsPerSecond - permits
                if (toAdd > 0) semaphore.release(toAdd)
            }
        }
    }

    companion object {
        private const val WINDOW_MS = 1_000L
    }
}
