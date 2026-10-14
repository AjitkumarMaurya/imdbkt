package io.github.ajitkumarmaurya.imdbkt.network

import io.github.ajitkumarmaurya.imdbkt.ImdbConfig
import io.github.ajitkumarmaurya.imdbkt.model.ImdbError
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class FirecrawlClient(private val config: ImdbConfig) {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(30_000L, TimeUnit.MILLISECONDS)
        .build()

    @Serializable
    private data class ScrapeRequest(val url: String, val formats: List<String> = listOf("markdown"))

    @Serializable
    private data class ScrapeResponse(val success: Boolean = false, val data: ScrapeData? = null)

    @Serializable
    private data class ScrapeData(val markdown: String? = null)

    suspend fun scrapeMarkdown(targetUrl: String): ImdbResult<String> {
        val apiKey = config.firecrawlApiKey
            ?: return ImdbResult.Failure(ImdbError.NetworkError("Firecrawl API key not configured"))

        val payload = json.encodeToString(ScrapeRequest(url = targetUrl))
        val request = Request.Builder()
            .url("${config.firecrawlBaseUrl.trimEnd('/')}/v1/scrape")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        return suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resume(ImdbResult.Failure(ImdbError.NetworkError("Firecrawl failed: ${e.message}", cause = e)))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        val body = resp.body?.string().orEmpty()
                        val result = runCatching {
                            val parsed = json.decodeFromString<ScrapeResponse>(body)
                            val md = parsed.data?.markdown
                            if (parsed.success && !md.isNullOrBlank()) {
                                ImdbResult.Success(md)
                            } else {
                                ImdbResult.Failure(ImdbError.ParsingError("Firecrawl", "Empty markdown returned"))
                            }
                        }.getOrElse { e ->
                            ImdbResult.Failure(ImdbError.ParsingError("Firecrawl", "Failed to decode Firecrawl response", e))
                        }
                        if (cont.isActive) cont.resume(result)
                    }
                }
            })
        }
    }
}
