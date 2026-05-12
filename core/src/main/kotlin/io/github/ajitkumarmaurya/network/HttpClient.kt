package io.github.ajitkumarmaurya.imdbkt.network

import io.github.ajitkumarmaurya.imdbkt.ImdbConfig
import io.github.ajitkumarmaurya.imdbkt.utils.UserAgents
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around OkHttpClient that adds browser-like headers, user-agent
 * rotation, rate limiting, and retry logic on every request.
 */
internal class HttpClient(config: ImdbConfig) {

    private val rateLimiter = RateLimiter(config.maxRequestsPerSecond)

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(config.writeTimeoutSeconds, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .addInterceptor(RateLimitInterceptor(rateLimiter))
        .addInterceptor(BrowserHeadersInterceptor())
        .addInterceptor(RetryInterceptor(config.maxRetries))
        .apply {
            if (config.enableLogging) {
                addNetworkInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    }
                )
            }
        }
        .build()

    fun get(url: String): Response {
        val request = Request.Builder()
            .url(url)
            .build()
        return okHttp.newCall(request).execute()
    }

    fun close() = okHttp.dispatcher.executorService.shutdown()

    // ── Interceptors ──────────────────────────────────────────────────────────

    private class RateLimitInterceptor(private val rateLimiter: RateLimiter) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            rateLimiter.acquire()
            return chain.proceed(chain.request())
        }
    }

    private class BrowserHeadersInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .header("User-Agent", UserAgents.random())
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Cache-Control", "no-cache")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .build()
            return chain.proceed(request)
        }
    }
}
