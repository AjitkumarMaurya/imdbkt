package io.github.ajitkumarmaurya.imdbkt.repository

import com.google.common.truth.Truth.assertThat
import io.github.ajitkumarmaurya.imdbkt.ImdbConfig
import io.github.ajitkumarmaurya.imdbkt.cache.MemoryCache
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.ImdbSearchItem
import io.github.ajitkumarmaurya.imdbkt.model.ImdbTitle
import io.github.ajitkumarmaurya.imdbkt.network.HttpClient
import io.github.ajitkumarmaurya.imdbkt.parser.SearchParser
import io.github.ajitkumarmaurya.imdbkt.parser.TitleParser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.IOException

class ImdbRepositoryTest {

    private val testJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val searchJson: String by lazy {
        javaClass.classLoader!!
            .getResourceAsStream("search_response.json")!!
            .bufferedReader()
            .readText()
    }

    private val titleHtml: String by lazy {
        javaClass.classLoader!!
            .getResourceAsStream("title_page.html")!!
            .bufferedReader()
            .readText()
    }

    @Test
    fun `search returns Success with parsed items`() = runTest {
        val repo = buildRepoWithSearchCache(searchJson)
        val result = repo.search("interstellar")
        assertThat(result).isInstanceOf(ImdbResult.Success::class.java)
        val items = (result as ImdbResult.Success).data
        assertThat(items).isNotEmpty()
        assertThat(items.first().imdbId).isEqualTo("tt0816692")
    }

    @Test
    fun `getTitle returns Success with parsed title`() = runTest {
        val repo = buildRepoWithTitleCache(titleHtml)
        val result = repo.getTitle("tt0816692")
        assertThat(result).isInstanceOf(ImdbResult.Success::class.java)
        val title = (result as ImdbResult.Success).data
        assertThat(title.imdbId).isEqualTo("tt0816692")
        assertThat(title.title).isEqualTo("Interstellar")
        assertThat(title.rating).isGreaterThan(8f)
    }

    @Test
    fun `getTitle extracts genres`() = runTest {
        val repo = buildRepoWithTitleCache(titleHtml)
        val result = repo.getTitle("tt0816692")
        val title = (result as ImdbResult.Success).data
        assertThat(title.genres).containsExactly("Adventure", "Drama", "Sci-Fi")
    }

    @Test
    fun `getTitle extracts cast`() = runTest {
        val repo = buildRepoWithTitleCache(titleHtml)
        val result = repo.getTitle("tt0816692")
        val title = (result as ImdbResult.Success).data
        assertThat(title.cast).isNotEmpty()
        assertThat(title.cast.first().name).isEqualTo("Matthew McConaughey")
    }

    @Test
    fun `search returns Empty when cache holds empty list`() = runTest {
        val cache = MemoryCache()
        val emptyList: List<ImdbSearchItem> = emptyList()
        cache.put("search:dark", testJson.encodeToString(emptyList))

        val config = ImdbConfig(maxRetries = 0)
        val repo = ImdbRepositoryImpl(HttpClient(config), cache)
        val result = repo.search("dark")
        assertThat(result).isInstanceOf(ImdbResult.Empty::class.java)
    }

    @Test
    fun `search returns Error on network failure`() = runTest {
        val mockHttpClient = mockk<HttpClient>()
        every { mockHttpClient.get(any()) } throws IOException("Connection refused")

        val repo = ImdbRepositoryImpl(
            httpClient = mockHttpClient,
            cache = MemoryCache(),
        )
        val result = repo.search("test")
        assertThat(result).isInstanceOf(ImdbResult.Error::class.java)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildRepoWithSearchCache(jsonBody: String): ImdbRepositoryImpl {
        val cache = MemoryCache()
        val config = ImdbConfig(maxRetries = 0)
        val items: List<ImdbSearchItem> = SearchParser(testJson).parse(jsonBody)
        cache.put("search:interstellar", testJson.encodeToString(items))
        return ImdbRepositoryImpl(HttpClient(config), cache)
    }

    private fun buildRepoWithTitleCache(html: String): ImdbRepositoryImpl {
        val cache = MemoryCache()
        val config = ImdbConfig(maxRetries = 0)
        val title: ImdbTitle = TitleParser(testJson).parse("tt0816692", html)
        cache.put("title:tt0816692", testJson.encodeToString(title))
        return ImdbRepositoryImpl(HttpClient(config), cache)
    }
}
