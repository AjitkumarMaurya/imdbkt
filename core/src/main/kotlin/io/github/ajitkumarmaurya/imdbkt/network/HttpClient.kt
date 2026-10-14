package io.github.ajitkumarmaurya.imdbkt.network

import io.github.ajitkumarmaurya.imdbkt.ImdbConfig
import io.github.ajitkumarmaurya.imdbkt.model.ImdbError
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.utils.UserAgents
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.pow
import kotlin.random.Random

class HttpClient(private val config: ImdbConfig) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.readTimeoutMs, TimeUnit.MILLISECONDS)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .addInterceptor(RetryInterceptor())
        .followRedirects(true)
        .build()

    suspend fun get(url: String, acceptJson: Boolean = false): ImdbResult<String> {
        var attempt = 0
        var lastError: ImdbError? = null

        while (attempt <= config.maxRetries) {
            if (attempt > 0) {
                val baseDelay = config.baseRetryDelayMs * 2.0.pow(attempt - 1).toLong()
                delay(baseDelay + Random.nextLong(50, 350))
            }

            val profile = UserAgents.randomProfile()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", config.customUserAgent ?: profile.userAgent)
                .header("Sec-CH-UA", profile.secChUa)
                .header("Sec-CH-UA-Mobile", "?0")
                .header("Sec-CH-UA-Platform", profile.secChUaPlatform)
                .header("Accept-Language", config.acceptLanguage)
                .header(
                    "Accept",
                    if (acceptJson) "application/json"
                    else "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                )
                .build()

            when (val result = executeAsync(request)) {
                is ImdbResult.Success -> return result
                is ImdbResult.Failure -> {
                    lastError = result.error
                    if (result.error is ImdbError.NotFound) return result
                    if (result.error is ImdbError.RateLimited && result.error.retryAfterMs != null) {
                        delay(result.error.retryAfterMs)
                    }
                }
            }
            attempt++
        }

        return ImdbResult.Failure(
            lastError ?: ImdbError.NetworkError("Exhausted ${config.maxRetries} retries for $url")
        )
    }

    private suspend fun executeAsync(request: Request): ImdbResult<String> =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resume(
                            ImdbResult.Failure(ImdbError.NetworkError("Network call failed: ${e.message}", cause = e))
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        val code = resp.code
                        val body = resp.body?.string().orEmpty()

                        val outcome = when {
                            code == 404 -> ImdbResult.Failure(ImdbError.NotFound(request.url.toString()))
                            code == 429 || code == 403 || isWafChallenge(body) -> {
                                val retryAfter = resp.header("Retry-After")?.toLongOrNull()?.times(1000L)
                                ImdbResult.Failure(
                                    ImdbError.RateLimited("HTTP $code / WAF challenge on ${request.url}", retryAfter)
                                )
                            }
                            code !in 200..299 -> ImdbResult.Failure(
                                ImdbError.NetworkError("HTTP $code on ${request.url}", statusCode = code)
                            )
                            body.isBlank() -> ImdbResult.Failure(
                                ImdbError.NetworkError("Empty response body on ${request.url}", statusCode = code)
                            )
                            else -> ImdbResult.Success(body)
                        }

                        if (cont.isActive) cont.resume(outcome)
                    }
                }
            })
        }

    private fun isWafChallenge(body: String): Boolean {
        return body.length < 4096 && (
            body.contains("awsWafCookieDomainList", ignoreCase = true) ||
                (body.contains("captcha", ignoreCase = true) && !body.contains("application/ld+json"))
            )
    }
}
