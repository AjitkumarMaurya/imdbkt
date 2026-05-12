package io.github.ajitkumarmaurya.imdbkt.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Retries failed requests up to [maxRetries] times with exponential back-off.
 * Retries on I/O failures and HTTP 202/429/503 (bot-challenge, rate-limited, or unavailable).
 */
internal class RetryInterceptor(private val maxRetries: Int = 3) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var lastException: IOException? = null
        for (attempt in 0..maxRetries) {
            val (response, exception) = tryOnce(chain, attempt)
            if (response != null && !isRetryable(response)) return response
            if (exception != null) lastException = exception
        }
        throw lastException ?: IOException("Request failed after $maxRetries retries")
    }

    private fun tryOnce(chain: Interceptor.Chain, attempt: Int): Pair<Response?, IOException?> {
        return try {
            val response = chain.proceed(chain.request())
            if (isRetryable(response)) {
                response.close()
                sleepBackoff(attempt)
            }
            response to null
        } catch (e: IOException) {
            sleepBackoff(attempt)
            null to e
        }
    }

    // 202: IMDb bot-challenge page (challenge served instead of content)
    // 429: rate-limited by IMDb
    // 503: temporarily unavailable
    private fun isRetryable(response: Response): Boolean =
        response.code == 202 || response.code == 429 || response.code == 503

    private fun sleepBackoff(attempt: Int) {
        val delay = (INITIAL_DELAY_MS * Math.pow(2.0, attempt.toDouble())).toLong()
        Thread.sleep(delay.coerceAtMost(MAX_DELAY_MS))
    }

    companion object {
        private const val INITIAL_DELAY_MS = 500L
        private const val MAX_DELAY_MS = 8_000L
    }
}
