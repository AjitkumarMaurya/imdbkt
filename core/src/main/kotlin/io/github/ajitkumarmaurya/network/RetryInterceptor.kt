package io.github.ajitkumarmaurya.imdbkt.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Retries failed requests up to [maxRetries] times with exponential back-off.
 * Retries on I/O failures and HTTP 429/503 (rate-limited or temporarily unavailable).
 */
internal class RetryInterceptor(private val maxRetries: Int = 3) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        var lastException: IOException? = null

        while (attempt <= maxRetries) {
            try {
                val response = chain.proceed(chain.request())

                if (response.isSuccessful) return response

                if (response.code == 429 || response.code == 503) {
                    response.close()
                    if (attempt >= maxRetries) break
                    sleepBackoff(attempt)
                    attempt++
                    continue
                }

                return response
            } catch (e: IOException) {
                lastException = e
                if (attempt >= maxRetries) break
                sleepBackoff(attempt)
                attempt++
            }
        }

        throw lastException ?: IOException("Request failed after $maxRetries retries")
    }

    private fun sleepBackoff(attempt: Int) {
        val delay = (INITIAL_DELAY_MS * Math.pow(2.0, attempt.toDouble())).toLong()
        Thread.sleep(delay.coerceAtMost(MAX_DELAY_MS))
    }

    companion object {
        private const val INITIAL_DELAY_MS = 500L
        private const val MAX_DELAY_MS = 8_000L
    }
}
