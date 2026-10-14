#!/usr/bin/env bash
set -e

echo "Applying imdbkt v2.0 overhaul..."

mkdir -p gradle
mkdir -p .github/workflows
mkdir -p core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/{cache,model,network,parser/selectors,repository,utils}
mkdir -p core/src/test/kotlin/io/github/ajitkumarmaurya/imdbkt/{cache,model,parser,repository}
mkdir -p core/src/test/resources
mkdir -p app/src/main/java/io/github/ajitkumarmaurya/imdbkt/sample/{di,ui/detail,ui/navigation,ui/search,ui/theme}
mkdir -p sample-jvm/src/main/kotlin/io/github/ajitkumarmaurya/sample

# 1. gradle/libs.versions.toml
cat << 'EOF' > gradle/libs.versions.toml
[versions]
kotlin = "2.1.0"
agp = "8.7.2"
coroutines = "1.9.0"
serialization = "1.7.3"
okhttp = "4.12.0"
jsoup = "1.18.1"
composeBom = "2024.10.01"
lifecycle = "2.8.7"
navigation = "2.8.4"
coil = "2.7.0"
junit = "5.11.3"
mockk = "1.13.13"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
jsoup = { module = "org.jsoup:jsoup", version.ref = "jsoup" }

androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
coil-compose = { module = "io.coil-kt:coil-compose", version.ref = "coil" }

junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
android-application = { id = "com.android.application", version.ref = "agp" }
EOF

# 2. core/build.gradle.kts
cat << 'EOF' > core/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}

tasks.test {
    useJUnitPlatform()
}
EOF

# 3. Models
cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/model/ImdbResult.kt
package io.github.ajitkumarmaurya.imdbkt.model

sealed interface ImdbError {
    val message: String
    val cause: Throwable?

    data class NetworkError(
        override val message: String,
        val statusCode: Int? = null,
        override val cause: Throwable? = null
    ) : ImdbError

    data class RateLimited(
        override val message: String = "Rate limited by IMDb/WAF",
        val retryAfterMs: Long? = null,
        override val cause: Throwable? = null
    ) : ImdbError

    data class NotFound(
        val idOrQuery: String,
        override val message: String = "Resource not found: $idOrQuery",
        override val cause: Throwable? = null
    ) : ImdbError

    data class ParsingError(
        val stage: String,
        override val message: String,
        override val cause: Throwable? = null
    ) : ImdbError
}

sealed class ImdbResult<out T> {
    data class Success<T>(val data: T) : ImdbResult<T>()
    data class Failure(val error: ImdbError) : ImdbResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): ImdbError? = (this as? Failure)?.error

    inline fun <R> map(transform: (T) -> R): ImdbResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    inline fun onSuccess(action: (T) -> Unit): ImdbResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onFailure(action: (ImdbError) -> Unit): ImdbResult<T> {
        if (this is Failure) action(error)
        return this
    }
}
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/model/ImdbTitle.kt
package io.github.ajitkumarmaurya.imdbkt.model

import kotlinx.serialization.Serializable

@Serializable
data class ImdbTitle(
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val type: String = "Movie",
    val year: Int? = null,
    val plot: String? = null,
    val rating: Double? = null,
    val voteCount: Long? = null,
    val contentRating: String? = null,
    val durationMinutes: Int? = null,
    val posterUrl: String? = null,
    val genres: List<String> = emptyList(),
    val directors: List<CreditSummary> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val episodes: List<ImdbEpisode> = emptyList()
)

@Serializable
data class CreditSummary(val id: String, val name: String)

@Serializable
data class CastMember(
    val id: String,
    val name: String,
    val character: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class ImdbEpisode(
    val id: String,
    val season: Int,
    val episodeNumber: Int,
    val title: String,
    val airDate: String? = null,
    val rating: Double? = null,
    val plot: String? = null,
    val thumbnailUrl: String? = null
)
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/model/ImdbActor.kt
package io.github.ajitkumarmaurya.imdbkt.model

import kotlinx.serialization.Serializable

@Serializable
data class ImdbActor(
    val id: String,
    val name: String,
    val bio: String? = null,
    val birthDate: String? = null,
    val imageUrl: String? = null,
    val knownFor: List<KnownForTitle> = emptyList()
)

@Serializable
data class KnownForTitle(
    val id: String,
    val title: String,
    val year: Int? = null,
    val role: String? = null,
    val posterUrl: String? = null
)
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/model/ImdbSearchItem.kt
package io.github.ajitkumarmaurya.imdbkt.model

import kotlinx.serialization.Serializable

@Serializable
data class ImdbSearchItem(
    val id: String,
    val title: String,
    val year: Int? = null,
    val type: String = "title",
    val stars: String? = null,
    val posterUrl: String? = null,
    val rank: Int? = null,
    val rating: Double? = null
)
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/model/TrendingType.kt
package io.github.ajitkumarmaurya.imdbkt.model

enum class TrendingType(val path: String) {
    MOVIES("/chart/moviemeter/"),
    TV_SHOWS("/chart/tvmeter/"),
    TOP_250_MOVIES("/chart/top/"),
    TOP_250_TV("/chart/toptv/")
}
EOF

# 4. Cache Layer
cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/cache/Cache.kt
package io.github.ajitkumarmaurya.imdbkt.cache

interface Cache<K, V> {
    suspend fun get(key: K): V?
    suspend fun put(key: K, value: V, ttlMs: Long? = null)
    suspend fun remove(key: K)
    suspend fun clear()
}
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/cache/MemoryCache.kt
package io.github.ajitkumarmaurya.imdbkt.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MemoryCache<K, V>(
    private val maxSize: Int = 250,
    private val defaultTtlMs: Long = 15 * 60 * 1000L,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : Cache<K, V> {

    private data class Entry<V>(val value: V, val expiresAt: Long)

    private val mutex = Mutex()
    private val map = object : LinkedHashMap<K, Entry<V>>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean = size > maxSize
    }

    override suspend fun get(key: K): V? = mutex.withLock {
        val entry = map[key] ?: return@withLock null
        if (clock() > entry.expiresAt) {
            map.remove(key)
            return@withLock null
        }
        entry.value
    }

    override suspend fun put(key: K, value: V, ttlMs: Long?) = mutex.withLock {
        map[key] = Entry(value, clock() + (ttlMs ?: defaultTtlMs))
    }

    override suspend fun remove(key: K) = mutex.withLock {
        map.remove(key)
        Unit
    }

    override suspend fun clear() = mutex.withLock {
        map.clear()
    }
}
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/cache/DiskCache.kt
package io.github.ajitkumarmaurya.imdbkt.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class DiskCache(
    private val directory: File,
    private val defaultTtlMs: Long = 6 * 60 * 60 * 1000L,
    private val maxBytes: Long = 50L * 1024 * 1024,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : Cache<String, String> {

    private val writeMutex = Mutex()

    init {
        if (!directory.exists()) directory.mkdirs()
    }

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (!file.exists()) return@withContext null

        runCatching {
            val lines = file.readLines()
            if (lines.isEmpty()) return@withContext null
            val expiresAt = lines.first().toLongOrNull() ?: return@withContext null
            if (clock() > expiresAt) {
                file.delete()
                return@withContext null
            }
            lines.drop(1).joinToString("\n")
        }.getOrNull()
    }

    override suspend fun put(key: String, value: String, ttlMs: Long?) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            if (!directory.exists()) directory.mkdirs()
            val expiresAt = clock() + (ttlMs ?: defaultTtlMs)
            val target = fileFor(key)
            val temp = File(directory, "${target.name}.tmp")
            temp.writeText("$expiresAt\n$value")
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            pruneIfNeeded()
        }
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            fileFor(key).delete()
            Unit
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            directory.listFiles()?.forEach { it.delete() }
        }
    }

    private fun pruneIfNeeded() {
        val files = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= maxBytes) return

        files.sortedBy { it.lastModified() }.forEach { file ->
            if (totalSize <= maxBytes * 0.8) return
            val len = file.length()
            if (file.delete()) totalSize -= len
        }
    }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        val hash = digest.joinToString("") { "%02x".format(it) }
        return File(directory, "$hash.cache")
    }
}
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/cache/TieredCache.kt
package io.github.ajitkumarmaurya.imdbkt.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class TieredCache(
    private val memoryCache: Cache<String, String>,
    private val diskCache: Cache<String, String>? = null
) : Cache<String, String> {

    private val keyLocks = ConcurrentHashMap<String, Mutex>()

    override suspend fun get(key: String): String? {
        memoryCache.get(key)?.let { return it }
        val fromDisk = diskCache?.get(key) ?: return null
        memoryCache.put(key, fromDisk)
        return fromDisk
    }

    override suspend fun put(key: String, value: String, ttlMs: Long?) {
        memoryCache.put(key, value, ttlMs)
        diskCache?.put(key, value, ttlMs)
    }

    override suspend fun remove(key: String) {
        memoryCache.remove(key)
        diskCache?.remove(key)
    }

    override suspend fun clear() {
        memoryCache.clear()
        diskCache?.clear()
    }

    suspend fun getOrPut(key: String, ttlMs: Long? = null, loader: suspend () -> String?): String? {
        get(key)?.let { return it }
        val mutex = keyLocks.computeIfAbsent(key) { Mutex() }
        return try {
            mutex.withLock {
                get(key)?.let { return@withLock it }
                val loaded = loader()
                if (loaded != null) put(key, loaded, ttlMs)
                loaded
            }
        } finally {
            keyLocks.remove(key, mutex)
        }
    }
}
EOF

# 5. Utils & Network
cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/utils/UserAgents.kt
package io.github.ajitkumarmaurya.imdbkt.utils

import kotlin.random.Random

data class BrowserProfile(
    val userAgent: String,
    val secChUa: String,
    val secChUaPlatform: String
)

object UserAgents {
    private val profiles = listOf(
        BrowserProfile(
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            secChUa = "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
            secChUaPlatform = "\"Windows\""
        ),
        BrowserProfile(
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            secChUa = "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
            secChUaPlatform = "\"macOS\""
        ),
        BrowserProfile(
            userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
            secChUa = "\"Chromium\";v=\"130\", \"Google Chrome\";v=\"130\", \"Not?A_Brand\";v=\"99\"",
            secChUaPlatform = "\"Linux\""
        )
    )

    fun randomProfile(): BrowserProfile = profiles[Random.nextInt(profiles.size)]
}
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/utils/Extensions.kt
package io.github.ajitkumarmaurya.imdbkt.utils

private val ISO_DURATION_REGEX = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?""")
private val TT_ID_REGEX = Regex("""(tt\d{7,10})""")
private val NM_ID_REGEX = Regex("""(nm\d{7,10})""")
private val YEAR_REGEX = Regex("""\b(19\d{2}|20\d{2})\b""")

fun String.parseIsoDurationMinutes(): Int? {
    val match = ISO_DURATION_REGEX.find(this) ?: return null
    val hours = match.groupValues[1].toIntOrNull() ?: 0
    val minutes = match.groupValues[2].toIntOrNull() ?: 0
    val total = hours * 60 + minutes
    return if (total > 0) total else null
}

fun String.extractTitleId(): String? = TT_ID_REGEX.find(this)?.value
fun String.extractNameId(): String? = NM_ID_REGEX.find(this)?.value
fun String.extractYear(): Int? = YEAR_REGEX.find(this)?.value?.toIntOrNull()
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/network/RetryInterceptor.kt
package io.github.ajitkumarmaurya.imdbkt.network

import okhttp3.Interceptor
import okhttp3.Response

class RetryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/network/HttpClient.kt
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
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/network/FirecrawlClient.kt
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
EOF

# 6. Parsers
cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/parser/selectors/ImdbSelectors.kt
package io.github.ajitkumarmaurya.imdbkt.parser.selectors

object ImdbSelectors {
    const val JSON_LD = "script[type=application/ld+json]"
    const val NEXT_DATA = "script#__NEXT_DATA__"
    const val TITLE_HEADER = "[data-testid=hero__pageTitle], h1"
    const val PLOT = "[data-testid=plot-xl], [data-testid=plot-l]"
    const val RATING_SCORE = "[data-testid=hero-rating-bar__aggregate-rating__score] span"
    const val POSTER_IMG = "[data-testid=hero-media__poster] img.ipc-image"
    const val GENRE_CHIP = "[data-testid=genres] a.ipc-chip, .ipc-chip-list__scroller a"
    const val CAST_ITEM = "[data-testid=title-cast-item]"
    const val CAST_ACTOR_LINK = "[data-testid=title-cast-item__actor]"
    const val CAST_CHARACTER = "[data-testid=cast-item-characters-link] span"
    const val ACTOR_NAME = "[data-testid=hero__pageTitle] span, h1"
    const val ACTOR_BIO = "[data-testid=bio-content] div.ipc-html-content-inner-div"
    const val ACTOR_IMAGE = "[data-testid=hero-media__poster] img.ipc-image"
    const val KNOWN_FOR_CARD = "[data-testid=nm_flmg_kwn_for] .ipc-primary-image-list-card"
    const val EPISODE_CARD = "article.episode-item-wrapper, [data-testid=tab-season-entry]"
    const val CHART_ITEM = "li.ipc-metadata-list-summary-item"
    const val CHART_TITLE = "h3.ipc-title__text"
}
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/parser/TitleParser.kt
package io.github.ajitkumarmaurya.imdbkt.parser

import io.github.ajitkumarmaurya.imdbkt.model.CastMember
import io.github.ajitkumarmaurya.imdbkt.model.CreditSummary
import io.github.ajitkumarmaurya.imdbkt.model.ImdbError
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.ImdbTitle
import io.github.ajitkumarmaurya.imdbkt.parser.selectors.ImdbSelectors
import io.github.ajitkumarmaurya.imdbkt.utils.extractNameId
import io.github.ajitkumarmaurya.imdbkt.utils.extractYear
import io.github.ajitkumarmaurya.imdbkt.utils.parseIsoDurationMinutes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jsoup.Jsoup

class TitleParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(titleId: String, html: String): ImdbResult<ImdbTitle> = runCatching {
        val doc = Jsoup.parse(html)
        val jsonLdRaw = doc.selectFirst(ImdbSelectors.JSON_LD)?.data()
        val ldObj = jsonLdRaw?.let { json.parseToJsonElement(it).jsonObject }

        val title = ldObj?.get("name")?.jsonPrimitive?.contentOrNull
            ?: doc.selectFirst(ImdbSelectors.TITLE_HEADER)?.text()?.trim()
            ?: return ImdbResult.Failure(ImdbError.ParsingError("TitleParser", "Missing title for $titleId"))

        val type = ldObj?.get("@type")?.jsonPrimitive?.contentOrNull ?: "Movie"
        val plot = ldObj?.get("description")?.jsonPrimitive?.contentOrNull
            ?: doc.selectFirst(ImdbSelectors.PLOT)?.text()?.trim()

        val ratingObj = ldObj?.get("aggregateRating")?.jsonObject
        val rating = ratingObj?.get("ratingValue")?.jsonPrimitive?.doubleOrNull
            ?: doc.selectFirst(ImdbSelectors.RATING_SCORE)?.text()?.toDoubleOrNull()
        val voteCount = ratingObj?.get("ratingCount")?.jsonPrimitive?.longOrNull

        val contentRating = ldObj?.get("contentRating")?.jsonPrimitive?.contentOrNull
        val durationMinutes = ldObj?.get("duration")?.jsonPrimitive?.contentOrNull?.parseIsoDurationMinutes()
        val year = ldObj?.get("datePublished")?.jsonPrimitive?.contentOrNull?.extractYear()
            ?: doc.title().extractYear()

        val posterUrl = ldObj?.get("image")?.jsonPrimitive?.contentOrNull
            ?: doc.selectFirst(ImdbSelectors.POSTER_IMG)?.attr("src")

        val genres = parseStringOrArray(ldObj?.get("genre")).ifEmpty {
            doc.select(ImdbSelectors.GENRE_CHIP).map { it.text().trim() }.filter { it.isNotEmpty() }
        }

        val directors = parsePeople(ldObj?.get("director"))

        val domCast = doc.select(ImdbSelectors.CAST_ITEM).mapNotNull { el ->
            val actorLink = el.selectFirst(ImdbSelectors.CAST_ACTOR_LINK) ?: return@mapNotNull null
            val actorId = actorLink.attr("href").extractNameId() ?: return@mapNotNull null
            val actorName = actorLink.text().trim()
            val character = el.selectFirst(ImdbSelectors.CAST_CHARACTER)?.text()?.trim()
            val avatar = el.selectFirst("img.ipc-image")?.attr("src")
            CastMember(id = actorId, name = actorName, character = character, avatarUrl = avatar)
        }

        val cast = domCast.ifEmpty {
            parsePeople(ldObj?.get("actor")).map { CastMember(id = it.id, name = it.name) }
        }

        ImdbResult.Success(
            ImdbTitle(
                id = titleId,
                title = title,
                type = type,
                year = year,
                plot = plot,
                rating = rating,
                voteCount = voteCount,
                contentRating = contentRating,
                durationMinutes = durationMinutes,
                posterUrl = posterUrl,
                genres = genres,
                directors = directors,
                cast = cast
            )
        )
    }.getOrElse { e ->
        ImdbResult.Failure(ImdbError.ParsingError("TitleParser", "Failed to parse $titleId: ${e.message}", e))
    }

    private fun parseStringOrArray(element: JsonElement?): List<String> = when (element) {
        is JsonArray -> element.mapNotNull { it.jsonPrimitive.contentOrNull }
        null -> emptyList()
        else -> listOfNotNull(element.jsonPrimitive.contentOrNull)
    }

    private fun parsePeople(element: JsonElement?): List<CreditSummary> {
        val list = when (element) {
            is JsonArray -> element.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(element)
            else -> emptyList()
        }
        return list.mapNotNull { obj ->
            val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val id = url.extractNameId() ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            CreditSummary(id = id, name = name)
        }
    }
}
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/parser/SearchParser.kt
package io.github.ajitkumarmaurya.imdbkt.parser

import io.github.ajitkumarmaurya.imdbkt.model.ImdbError
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.ImdbSearchItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SearchParser {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class SuggestResponse(@SerialName("d") val results: List<SuggestItem> = emptyList())

    @Serializable
    private data class SuggestItem(
        val id: String = "",
        @SerialName("l") val title: String = "",
        @SerialName("y") val year: Int? = null,
        @SerialName("qid") val type: String = "title",
        @SerialName("s") val stars: String? = null,
        @SerialName("rank") val rank: Int? = null,
        @SerialName("i") val image: SuggestImage? = null
    )

    @Serializable
    private data class SuggestImage(val imageUrl: String? = null)

    fun parse(jsonBody: String): ImdbResult<List<ImdbSearchItem>> = runCatching {
        val parsed = json.decodeFromString<SuggestResponse>(jsonBody)
        val items = parsed.results
            .filter { it.id.startsWith("tt") || it.id.startsWith("nm") }
            .map {
                ImdbSearchItem(
                    id = it.id,
                    title = it.title,
                    year = it.year,
                    type = it.type,
                    stars = it.stars,
                    posterUrl = it.image?.imageUrl,
                    rank = it.rank
                )
            }
        ImdbResult.Success(items)
    }.getOrElse { e ->
        ImdbResult.Failure(ImdbError.ParsingError("SearchParser", "Failed to parse search JSON: ${e.message}", e))
    }
}
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/parser/ActorParser.kt
package io.github.ajitkumarmaurya.imdbkt.parser

import io.github.ajitkumarmaurya.imdbkt.model.ImdbActor
import io.github.ajitkumarmaurya.imdbkt.model.ImdbError
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.KnownForTitle
import io.github.ajitkumarmaurya.imdbkt.parser.selectors.ImdbSelectors
import io.github.ajitkumarmaurya.imdbkt.utils.extractTitleId
import io.github.ajitkumarmaurya.imdbkt.utils.extractYear
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup

class ActorParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(actorId: String, html: String): ImdbResult<ImdbActor> = runCatching {
        val doc = Jsoup.parse(html)
        val ldObj = doc.selectFirst(ImdbSelectors.JSON_LD)?.data()?.let {
            json.parseToJsonElement(it).jsonObject
        }

        val name = ldObj?.get("name")?.jsonPrimitive?.contentOrNull
            ?: doc.selectFirst(ImdbSelectors.ACTOR_NAME)?.text()?.trim()
            ?: return ImdbResult.Failure(ImdbError.ParsingError("ActorParser", "Missing actor name for $actorId"))

        val bio = ldObj?.get("description")?.jsonPrimitive?.contentOrNull
            ?: doc.selectFirst(ImdbSelectors.ACTOR_BIO)?.text()?.trim()
        val birthDate = ldObj?.get("birthDate")?.jsonPrimitive?.contentOrNull
        val imageUrl = ldObj?.get("image")?.jsonPrimitive?.contentOrNull
            ?: doc.selectFirst(ImdbSelectors.ACTOR_IMAGE)?.attr("src")

        val knownFor = doc.select(ImdbSelectors.KNOWN_FOR_CARD).mapNotNull { card ->
            val link = card.selectFirst("a.ipc-primary-image-list-card__title") ?: return@mapNotNull null
            val tId = link.attr("href").extractTitleId() ?: return@mapNotNull null
            val title = link.text().trim()
            val year = card.selectFirst(".ipc-primary-image-list-card__secondary-text")?.text()?.extractYear()
            val poster = card.selectFirst("img.ipc-image")?.attr("src")
            KnownForTitle(id = tId, title = title, year = year, posterUrl = poster)
        }

        ImdbResult.Success(ImdbActor(actorId, name, bio, birthDate, imageUrl, knownFor))
    }.getOrElse { e ->
        ImdbResult.Failure(ImdbError.ParsingError("ActorParser", "Failed to parse actor $actorId: ${e.message}", e))
    }
}
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/parser/EpisodeParser.kt
package io.github.ajitkumarmaurya.imdbkt.parser

import io.github.ajitkumarmaurya.imdbkt.model.ImdbEpisode
import io.github.ajitkumarmaurya.imdbkt.model.ImdbError
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.parser.selectors.ImdbSelectors
import io.github.ajitkumarmaurya.imdbkt.utils.extractTitleId
import org.jsoup.Jsoup

class EpisodeParser {
    private val prefixRegex = Regex("""^S\d+\.E\d+\s*∙\s*""")

    fun parse(season: Int, html: String): ImdbResult<List<ImdbEpisode>> = runCatching {
        val doc = Jsoup.parse(html)
        val episodes = doc.select(ImdbSelectors.EPISODE_CARD).mapIndexedNotNull { index, el ->
            val titleLink = el.selectFirst("a[href*=/title/tt]") ?: return@mapIndexedNotNull null
            val epId = titleLink.attr("href").extractTitleId() ?: return@mapIndexedNotNull null
            val cleanTitle = titleLink.text().trim().replace(prefixRegex, "")
            val plot = el.selectFirst(".ipc-html-content-inner-div")?.text()?.trim()
            val rating = el.selectFirst(".ipc-rating-star--rating")?.text()?.toDoubleOrNull()
            val img = el.selectFirst("img.ipc-image")?.attr("src")
            ImdbEpisode(
                id = epId,
                season = season,
                episodeNumber = index + 1,
                title = cleanTitle.ifBlank { "Episode ${index + 1}" },
                rating = rating,
                plot = plot,
                thumbnailUrl = img
            )
        }
        ImdbResult.Success(episodes)
    }.getOrElse { e ->
        ImdbResult.Failure(ImdbError.ParsingError("EpisodeParser", "Failed to parse episodes: ${e.message}", e))
    }
}
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/parser/TrendingParser.kt
package io.github.ajitkumarmaurya.imdbkt.parser

import io.github.ajitkumarmaurya.imdbkt.model.ImdbError
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.ImdbSearchItem
import io.github.ajitkumarmaurya.imdbkt.parser.selectors.ImdbSelectors
import io.github.ajitkumarmaurya.imdbkt.utils.extractTitleId
import io.github.ajitkumarmaurya.imdbkt.utils.extractYear
import org.jsoup.Jsoup

class TrendingParser {
    private val leadingRankRegex = Regex("""^\d+\.\s*""")

    fun parse(html: String): ImdbResult<List<ImdbSearchItem>> = runCatching {
        val doc = Jsoup.parse(html)
        val items = doc.select(ImdbSelectors.CHART_ITEM).mapIndexedNotNull { idx, item ->
            val link = item.selectFirst("a[href*=/title/tt]") ?: return@mapIndexedNotNull null
            val id = link.attr("href").extractTitleId() ?: return@mapIndexedNotNull null
            val rawTitle = item.selectFirst(ImdbSelectors.CHART_TITLE)?.text()?.trim().orEmpty()
            val title = rawTitle.replace(leadingRankRegex, "")
            val year = item.select(".cli-title-metadata-item").firstOrNull()?.text()?.extractYear()
            val rating = item.selectFirst(".ipc-rating-star--rating")?.text()?.toDoubleOrNull()
            val poster = item.selectFirst("img.ipc-image")?.attr("src")

            ImdbSearchItem(
                id = id,
                title = title,
                year = year,
                posterUrl = poster,
                rank = idx + 1,
                rating = rating
            )
        }
        ImdbResult.Success(items)
    }.getOrElse { e ->
        ImdbResult.Failure(ImdbError.ParsingError("TrendingParser", "Failed to parse trending list: ${e.message}", e))
    }
}
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/parser/FirecrawlMarkdownParser.kt
package io.github.ajitkumarmaurya.imdbkt.parser

import io.github.ajitkumarmaurya.imdbkt.model.ImdbError
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.ImdbTitle
import io.github.ajitkumarmaurya.imdbkt.utils.extractYear

class FirecrawlMarkdownParser {
    private val h1Regex = Regex("""^#\s+(.+)$""", RegexOption.MULTILINE)
    private val ratingRegex = Regex("""(\d\.\d)\s*/\s*10""")

    fun parseTitle(titleId: String, markdown: String): ImdbResult<ImdbTitle> {
        val heading = h1Regex.find(markdown)?.groupValues?.getOrNull(1)?.trim()
            ?: return ImdbResult.Failure(ImdbError.ParsingError("FirecrawlMarkdownParser", "No H1 title in markdown"))
        val rating = ratingRegex.find(markdown)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val year = markdown.extractYear()

        return ImdbResult.Success(ImdbTitle(id = titleId, title = heading, year = year, rating = rating))
    }
}
EOF

# 7. Repository & Config
cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/repository/ImdbRepository.kt
package io.github.ajitkumarmaurya.imdbkt.repository

import io.github.ajitkumarmaurya.imdbkt.model.ImdbActor
import io.github.ajitkumarmaurya.imdbkt.model.ImdbEpisode
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.ImdbSearchItem
import io.github.ajitkumarmaurya.imdbkt.model.ImdbTitle
import io.github.ajitkumarmaurya.imdbkt.model.TrendingType

interface ImdbRepository {
    suspend fun search(query: String): ImdbResult<List<ImdbSearchItem>>
    suspend fun getTitle(titleId: String): ImdbResult<ImdbTitle>
    suspend fun getActor(actorId: String): ImdbResult<ImdbActor>
    suspend fun getEpisodes(titleId: String, season: Int = 1): ImdbResult<List<ImdbEpisode>>
    suspend fun getTrending(type: TrendingType = TrendingType.MOVIES): ImdbResult<List<ImdbSearchItem>>
}
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/repository/ImdbRepositoryImpl.kt
package io.github.ajitkumarmaurya.imdbkt.repository

import io.github.ajitkumarmaurya.imdbkt.ImdbConfig
import io.github.ajitkumarmaurya.imdbkt.cache.TieredCache
import io.github.ajitkumarmaurya.imdbkt.model.ImdbActor
import io.github.ajitkumarmaurya.imdbkt.model.ImdbEpisode
import io.github.ajitkumarmaurya.imdbkt.model.ImdbError
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.ImdbSearchItem
import io.github.ajitkumarmaurya.imdbkt.model.ImdbTitle
import io.github.ajitkumarmaurya.imdbkt.model.TrendingType
import io.github.ajitkumarmaurya.imdbkt.network.FirecrawlClient
import io.github.ajitkumarmaurya.imdbkt.network.HttpClient
import io.github.ajitkumarmaurya.imdbkt.parser.ActorParser
import io.github.ajitkumarmaurya.imdbkt.parser.EpisodeParser
import io.github.ajitkumarmaurya.imdbkt.parser.FirecrawlMarkdownParser
import io.github.ajitkumarmaurya.imdbkt.parser.SearchParser
import io.github.ajitkumarmaurya.imdbkt.parser.TitleParser
import io.github.ajitkumarmaurya.imdbkt.parser.TrendingParser
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URLEncoder

class ImdbRepositoryImpl(
    private val config: ImdbConfig,
    private val httpClient: HttpClient,
    private val firecrawlClient: FirecrawlClient,
    private val cache: TieredCache,
    private val titleParser: TitleParser = TitleParser(),
    private val searchParser: SearchParser = SearchParser(),
    private val actorParser: ActorParser = ActorParser(),
    private val episodeParser: EpisodeParser = EpisodeParser(),
    private val trendingParser: TrendingParser = TrendingParser(),
    private val markdownParser: FirecrawlMarkdownParser = FirecrawlMarkdownParser()
) : ImdbRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String): ImdbResult<List<ImdbSearchItem>> {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) return ImdbResult.Success(emptyList())

        val cacheKey = "search:$clean"
        cache.get(cacheKey)?.let { cached ->
            runCatching { json.decodeFromString<List<ImdbSearchItem>>(cached) }.getOrNull()?.let {
                return ImdbResult.Success(it)
            }
        }

        val firstChar = clean.first().let { if (it.isLetterOrDigit()) it else '_' }
        val encoded = URLEncoder.encode(clean, Charsets.UTF_8.name())
        val url = "https://v3.sg.media-imdb.com/suggestion/$firstChar/$encoded.json"

        return when (val resp = httpClient.get(url, acceptJson = true)) {
            is ImdbResult.Success -> searchParser.parse(resp.data).onSuccess { items ->
                cache.put(cacheKey, json.encodeToString(items), config.searchCacheTtlMs)
            }
            is ImdbResult.Failure -> resp
        }
    }

    override suspend fun getTitle(titleId: String): ImdbResult<ImdbTitle> {
        val cacheKey = "title:$titleId"
        cache.get(cacheKey)?.let { cached ->
            runCatching { json.decodeFromString<ImdbTitle>(cached) }.getOrNull()?.let {
                return ImdbResult.Success(it)
            }
        }

        val url = "https://www.imdb.com/title/$titleId/"
        val primaryResult = when (val htmlResult = httpClient.get(url)) {
            is ImdbResult.Success -> titleParser.parse(titleId, htmlResult.data)
            is ImdbResult.Failure -> htmlResult
        }

        if (primaryResult is ImdbResult.Success) {
            cache.put(cacheKey, json.encodeToString(primaryResult.data), config.metadataCacheTtlMs)
            return primaryResult
        }

        if (config.firecrawlApiKey != null && primaryResult.errorOrNull() !is ImdbError.NotFound) {
            val fallback = when (val mdResult = firecrawlClient.scrapeMarkdown(url)) {
                is ImdbResult.Success -> markdownParser.parseTitle(titleId, mdResult.data)
                is ImdbResult.Failure -> mdResult
            }
            if (fallback is ImdbResult.Success) {
                cache.put(cacheKey, json.encodeToString(fallback.data), config.metadataCacheTtlMs)
                return fallback
            }
        }

        return primaryResult
    }

    override suspend fun getActor(actorId: String): ImdbResult<ImdbActor> {
        val cacheKey = "actor:$actorId"
        cache.get(cacheKey)?.let { cached ->
            runCatching { json.decodeFromString<ImdbActor>(cached) }.getOrNull()?.let {
                return ImdbResult.Success(it)
            }
        }

        val url = "https://www.imdb.com/name/$actorId/"
        return when (val resp = httpClient.get(url)) {
            is ImdbResult.Success -> actorParser.parse(actorId, resp.data).onSuccess { actor ->
                cache.put(cacheKey, json.encodeToString(actor), config.metadataCacheTtlMs)
            }
            is ImdbResult.Failure -> resp
        }
    }

    override suspend fun getEpisodes(titleId: String, season: Int): ImdbResult<List<ImdbEpisode>> {
        val cacheKey = "episodes:$titleId:s$season"
        cache.get(cacheKey)?.let { cached ->
            runCatching { json.decodeFromString<List<ImdbEpisode>>(cached) }.getOrNull()?.let {
                return ImdbResult.Success(it)
            }
        }

        val url = "https://www.imdb.com/title/$titleId/episodes/?season=$season"
        return when (val resp = httpClient.get(url)) {
            is ImdbResult.Success -> episodeParser.parse(season, resp.data).onSuccess { episodes ->
                cache.put(cacheKey, json.encodeToString(episodes), config.metadataCacheTtlMs)
            }
            is ImdbResult.Failure -> resp
        }
    }

    override suspend fun getTrending(type: TrendingType): ImdbResult<List<ImdbSearchItem>> {
        val cacheKey = "trending:${type.name}"
        cache.get(cacheKey)?.let { cached ->
            runCatching { json.decodeFromString<List<ImdbSearchItem>>(cached) }.getOrNull()?.let {
                return ImdbResult.Success(it)
            }
        }

        val url = "https://www.imdb.com${type.path}"
        return when (val resp = httpClient.get(url)) {
            is ImdbResult.Success -> trendingParser.parse(resp.data).onSuccess { items ->
                cache.put(cacheKey, json.encodeToString(items), config.searchCacheTtlMs)
            }
            is ImdbResult.Failure -> resp
        }
    }
}
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/ImdbConfig.kt
package io.github.ajitkumarmaurya.imdbkt

import java.io.File

data class ImdbConfig(
    val connectTimeoutMs: Long = 10_000L,
    val readTimeoutMs: Long = 15_000L,
    val maxRetries: Int = 3,
    val baseRetryDelayMs: Long = 400L,
    val memoryCacheMaxEntries: Int = 250,
    val searchCacheTtlMs: Long = 10 * 60 * 1000L,
    val metadataCacheTtlMs: Long = 60 * 60 * 1000L,
    val diskCacheDir: File? = null,
    val acceptLanguage: String = "en-US,en;q=0.9",
    val customUserAgent: String? = null,
    val firecrawlApiKey: String? = null,
    val firecrawlBaseUrl: String = "https://api.firecrawl.dev"
)
EOF

cat << 'EOF' > core/src/main/kotlin/io/github/ajitkumarmaurya/imdbkt/Imdb.kt
package io.github.ajitkumarmaurya.imdbkt

import io.github.ajitkumarmaurya.imdbkt.cache.DiskCache
import io.github.ajitkumarmaurya.imdbkt.cache.MemoryCache
import io.github.ajitkumarmaurya.imdbkt.cache.TieredCache
import io.github.ajitkumarmaurya.imdbkt.network.FirecrawlClient
import io.github.ajitkumarmaurya.imdbkt.network.HttpClient
import io.github.ajitkumarmaurya.imdbkt.repository.ImdbRepository
import io.github.ajitkumarmaurya.imdbkt.repository.ImdbRepositoryImpl

class Imdb(config: ImdbConfig = ImdbConfig()) : ImdbRepository by createRepository(config) {

    class Builder {
        var config: ImdbConfig = ImdbConfig()
        fun build(): Imdb = Imdb(config)
    }

    companion object {
        operator fun invoke(block: Builder.() -> Unit): Imdb = Builder().apply(block).build()

        private fun createRepository(config: ImdbConfig): ImdbRepository {
            val memory = MemoryCache<String, String>(
                maxSize = config.memoryCacheMaxEntries,
                defaultTtlMs = config.metadataCacheTtlMs
            )
            val disk = config.diskCacheDir?.let {
                DiskCache(directory = it, defaultTtlMs = config.metadataCacheTtlMs)
            }
            val tieredCache = TieredCache(memory, disk)
            return ImdbRepositoryImpl(
                config = config,
                httpClient = HttpClient(config),
                firecrawlClient = FirecrawlClient(config),
                cache = tieredCache
            )
        }
    }
}
EOF

echo "Staging, committing, and pushing to remote..."
git add -A
git commit -m "feat!: overhaul imdbkt to v2.0 with tiered caching, hybrid parsers, and WAF resilience"
git push

echo "Done! All changes have been pushed."