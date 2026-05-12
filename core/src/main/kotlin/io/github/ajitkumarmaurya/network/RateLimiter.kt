package io.github.ajitkumarmaurya.imdbkt.network

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Token-bucket rate limiter.  Limits outgoing requests to [maxRequestsPerSecond]
 * per second, blocking callers until a token is available.
 */
internal class RateLimiter(private val maxRequestsPerSecond: Int = 2) {

    private val semaphore = Semaphore(maxRequestsPerSecond)
    private val lastRefillTime = AtomicLong(System.currentTimeMillis())

    fun acquire() {
        // Poll so that refillIfNeeded() is called repeatedly until a permit
        // becomes available. Without the loop, a blocked semaphore.acquire()
        // never returns because nothing else triggers the refill.
        while (true) {
            refillIfNeeded()
            if (semaphore.tryAcquire(POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)) return
        }
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
        private const val POLL_INTERVAL_MS = 50L
    }
}
