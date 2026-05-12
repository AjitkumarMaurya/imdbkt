package io.github.ajitkumarmaurya.imdbkt.parser

import io.github.ajitkumarmaurya.imdbkt.model.Episode
import io.github.ajitkumarmaurya.imdbkt.parser.selectors.ImdbSelectors
import io.github.ajitkumarmaurya.imdbkt.utils.asFloat
import io.github.ajitkumarmaurya.imdbkt.utils.asInt
import io.github.ajitkumarmaurya.imdbkt.utils.asString
import io.github.ajitkumarmaurya.imdbkt.utils.blankAsNull
import io.github.ajitkumarmaurya.imdbkt.utils.path
import io.github.ajitkumarmaurya.imdbkt.utils.string
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Parses the episode list for a given title + season.
 *
 * URL pattern: https://www.imdb.com/title/{imdbId}/episodes/?season={season}
 */
internal class EpisodeParser(private val json: Json) {

    fun buildUrl(imdbId: String, season: Int): String =
        ImdbSelectors.EPISODES_URL.format(imdbId, season)

    fun parse(html: String, titleId: String, season: Int): List<Episode> {
        val doc = Jsoup.parse(html)

        val fromNextData = extractFromNextData(doc, titleId, season)
        if (fromNextData.isNotEmpty()) return fromNextData

        return extractFromHtml(doc, season)
    }

    private fun extractFromNextData(doc: Document, titleId: String, season: Int): List<Episode> =
        runCatching {
            val script = doc.selectFirst(ImdbSelectors.NEXT_DATA_SCRIPT)?.data() ?: return emptyList()
            val root = json.parseToJsonElement(script).jsonObject

            val episodes = root.path("props", "pageProps", "contentData", "section", "episodes", "items")
                as? JsonArray ?: return emptyList()

            episodes.mapIndexed { index, el ->
                runCatching {
                    val item = el as? JsonObject ?: return@runCatching null
                    val id = item.string("id") ?: "$titleId-S${season}E${index + 1}"
                    val title = item.path("titleText")?.asString()
                        ?: item.string("titleText") ?: "Episode ${index + 1}"
                    val epNum = item.asInt() ?: (index + 1)
                    val airDate = item.path("releaseDate", "displayableProperty", "value", "plainText")?.asString()
                    val rating = item.path("ratingsSummary", "aggregateRating")?.asFloat()
                    val description = item.path("plot", "plotText", "plainText")?.asString()
                    val poster = item.path("primaryImage", "url")?.asString()

                    Episode(
                        imdbId = id,
                        title = title,
                        season = season,
                        episode = epNum,
                        airDate = airDate,
                        rating = rating,
                        description = description,
                        poster = poster,
                    )
                }.getOrNull()
            }.filterNotNull()
        }.getOrElse { emptyList() }

    private fun extractFromHtml(doc: Document, season: Int): List<Episode> =
        doc.select("div[data-testid='episodes-browse-episodes'] article").mapIndexed { index, el ->
            runCatching {
                val titleEl = el.selectFirst("div[data-testid='slate-list-card-title']")
                val title = titleEl?.text()?.blankAsNull() ?: "Episode ${index + 1}"
                val href = el.selectFirst("a")?.attr("href") ?: ""
                val id = Regex("""/title/(tt\d+)/""").find(href)?.groupValues?.get(1)
                    ?: "ep-$season-${index + 1}"
                val epText = el.selectFirst("div[data-testid='slate-list-card-header']")?.text()
                val epNum = Regex("""E(\d+)""").find(epText ?: "")?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)
                val airDate = el.selectFirst("span.sc-f2169d65-10")?.text()?.blankAsNull()
                val rating = el.selectFirst("span.ipc-rating-star--rating")?.text()?.toFloatOrNull()
                val description = el.selectFirst("div[data-testid='plot']")?.text()?.blankAsNull()
                val poster = el.selectFirst("img")?.attr("src")?.blankAsNull()

                Episode(
                    imdbId = id,
                    title = title,
                    season = season,
                    episode = epNum,
                    airDate = airDate,
                    rating = rating,
                    description = description,
                    poster = poster,
                )
            }.getOrNull()
        }.filterNotNull()
}
