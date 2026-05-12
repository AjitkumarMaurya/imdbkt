package io.github.ajitkumarmaurya.imdbkt.parser

import com.google.common.truth.Truth.assertThat
import io.github.ajitkumarmaurya.imdbkt.model.TitleType
import kotlinx.serialization.json.Json
import org.junit.Test

class TitleParserTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val parser = TitleParser(json)

    private val fixture: String by lazy {
        javaClass.classLoader!!
            .getResourceAsStream("title_page.html")!!
            .bufferedReader()
            .readText()
    }

    @Test
    fun `parse extracts title from __NEXT_DATA__`() {
        val title = parser.parse("tt0816692", fixture)
        assertThat(title.title).isEqualTo("Interstellar")
    }

    @Test
    fun `parse extracts imdbId`() {
        val title = parser.parse("tt0816692", fixture)
        assertThat(title.imdbId).isEqualTo("tt0816692")
    }

    @Test
    fun `parse extracts rating`() {
        val title = parser.parse("tt0816692", fixture)
        assertThat(title.rating).isWithin(0.01f).of(8.7f)
    }

    @Test
    fun `parse extracts voteCount`() {
        val title = parser.parse("tt0816692", fixture)
        assertThat(title.voteCount).isGreaterThan(2_000_000L)
    }

    @Test
    fun `parse extracts genres`() {
        val title = parser.parse("tt0816692", fixture)
        assertThat(title.genres).containsExactly("Adventure", "Drama", "Sci-Fi")
    }

    @Test
    fun `parse extracts runtime in minutes`() {
        val title = parser.parse("tt0816692", fixture)
        // 10140 seconds / 60 = 169 minutes
        assertThat(title.runtimeMinutes).isEqualTo(169)
    }

    @Test
    fun `parse extracts release year`() {
        val title = parser.parse("tt0816692", fixture)
        assertThat(title.year).isEqualTo("2014")
    }

    @Test
    fun `parse extracts plot`() {
        val title = parser.parse("tt0816692", fixture)
        assertThat(title.description).isNotEmpty()
        assertThat(title.description).contains("wormhole")
    }

    @Test
    fun `parse extracts type as movie`() {
        val title = parser.parse("tt0816692", fixture)
        assertThat(title.type).isEqualTo(TitleType.MOVIE)
    }

    @Test
    fun `parse extracts certificate`() {
        val title = parser.parse("tt0816692", fixture)
        assertThat(title.certificate).isEqualTo("PG-13")
    }

    @Test
    fun `parse extracts poster url`() {
        val title = parser.parse("tt0816692", fixture)
        assertThat(title.poster).contains("poster.jpg")
    }

    @Test
    fun `parse extracts cast members`() {
        val title = parser.parse("tt0816692", fixture)
        assertThat(title.cast).isNotEmpty()
        val cooper = title.cast.firstOrNull { it.imdbId == "nm0000190" }
        assertThat(cooper).isNotNull()
        assertThat(cooper!!.name).isEqualTo("Matthew McConaughey")
        assertThat(cooper.characters).contains("Cooper")
    }

    @Test
    fun `parse extracts directors`() {
        val title = parser.parse("tt0816692", fixture)
        assertThat(title.directors).isNotEmpty()
        assertThat(title.directors.first().name).isEqualTo("Christopher Nolan")
    }

    @Test
    fun `parse extracts writers`() {
        val title = parser.parse("tt0816692", fixture)
        assertThat(title.writers).isNotEmpty()
        val names = title.writers.map { it.name }
        assertThat(names).contains("Christopher Nolan")
        assertThat(names).contains("Jonathan Nolan")
    }

    @Test
    fun `parse extracts countries`() {
        val title = parser.parse("tt0816692", fixture)
        assertThat(title.countries).contains("United States")
    }

    @Test
    fun `parse extracts languages`() {
        val title = parser.parse("tt0816692", fixture)
        assertThat(title.languages).contains("English")
    }

    @Test
    fun `parse extracts keywords`() {
        val title = parser.parse("tt0816692", fixture)
        assertThat(title.keywords).contains("space")
        assertThat(title.keywords).contains("wormhole")
    }

    @Test
    fun `parse extracts releaseDate`() {
        val title = parser.parse("tt0816692", fixture)
        assertThat(title.releaseDate).isEqualTo("2014-11-07")
    }

    @Test
    fun `buildUrl formats correctly`() {
        val url = parser.buildUrl("tt0816692")
        assertThat(url).isEqualTo("https://www.imdb.com/title/tt0816692/")
    }
}
