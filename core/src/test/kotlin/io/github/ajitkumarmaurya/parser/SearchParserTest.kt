package io.github.ajitkumarmaurya.imdbkt.parser

import com.google.common.truth.Truth.assertThat
import io.github.ajitkumarmaurya.imdbkt.model.TitleType
import kotlinx.serialization.json.Json
import org.junit.Test

class SearchParserTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val parser = SearchParser(json)

    private val fixture: String by lazy {
        javaClass.classLoader!!
            .getResourceAsStream("search_response.json")!!
            .bufferedReader()
            .readText()
    }

    @Test
    fun `parse returns correct count`() {
        val results = parser.parse(fixture)
        assertThat(results).hasSize(3)
    }

    @Test
    fun `parse extracts movie fields correctly`() {
        val results = parser.parse(fixture)
        val movie = results.first()

        assertThat(movie.imdbId).isEqualTo("tt0816692")
        assertThat(movie.title).isEqualTo("Interstellar")
        assertThat(movie.year).isEqualTo("2014")
        assertThat(movie.type).isEqualTo(TitleType.MOVIE)
        assertThat(movie.subtitle).isEqualTo("Matthew McConaughey, Anne Hathaway")
        assertThat(movie.poster).isEqualTo("https://m.media-amazon.com/images/M/poster.jpg")
        assertThat(movie.rank).isEqualTo(94)
    }

    @Test
    fun `parse handles person entry`() {
        val results = parser.parse(fixture)
        val person = results.last()

        assertThat(person.imdbId).isEqualTo("nm0634240")
        assertThat(person.title).isEqualTo("Christopher Nolan")
        assertThat(person.type).isEqualTo(TitleType.PERSON)
    }

    @Test
    fun `parse returns empty list on invalid json`() {
        val results = parser.parse("not json at all")
        assertThat(results).isEmpty()
    }

    @Test
    fun `parse returns empty list on empty d array`() {
        val results = parser.parse("""{"v":1,"q":"xyz","d":[]}""")
        assertThat(results).isEmpty()
    }

    @Test
    fun `buildUrl encodes query correctly`() {
        val url = parser.buildUrl("Dark Knight")
        assertThat(url).contains("dark_knight")
    }

    @Test
    fun `buildUrl truncates long query to 20 chars`() {
        val url = parser.buildUrl("a".repeat(50))
        val encoded = url.substringAfterLast("/").removeSuffix(".json")
        assertThat(encoded.length).isAtMost(20)
    }
}
