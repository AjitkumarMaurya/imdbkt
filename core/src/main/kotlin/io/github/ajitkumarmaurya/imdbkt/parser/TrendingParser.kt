package io.github.ajitkumarmaurya.imdbkt.parser

import io.github.ajitkumarmaurya.imdbkt.model.ImdbSearchItem
import io.github.ajitkumarmaurya.imdbkt.model.TitleType
import io.github.ajitkumarmaurya.imdbkt.model.TrendingType
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
 * Parses IMDb chart/trending pages (moviemeter, tvmeter, top 250, box office).
 */
internal class TrendingParser(private val json: Json) {

    fun buildUrl(type: TrendingType): String =
        ImdbSelectors.CHART_URL.format(type.path)

    fun parse(html: String): List<ImdbSearchItem> {
        val doc = Jsoup.parse(html)

        // Prefer __NEXT_DATA__ — it's structured and reliable
        val fromNextData = extractFromNextData(doc)
        if (fromNextData.isNotEmpty()) return fromNextData

        // Fall back to HTML selectors
        return extractFromHtml(doc)
    }

    private fun extractFromNextData(doc: Document): List<ImdbSearchItem> = runCatching {
        val script = doc.selectFirst(ImdbSelectors.NEXT_DATA_SCRIPT)?.data() ?: return emptyList()
        val root = json.parseToJsonElement(script).jsonObject

        // Try multiple known paths for chart items
        val chartItems = listOf(
            root.path("props", "pageProps", "pageData", "chartTitles", "edges"),
            root.path("props", "pageProps", "pageData", "topPicksTitles", "edges"),
            root.path("props", "pageProps", "chartData", "listItems"),
        ).firstNotNullOfOrNull { it as? JsonArray }

        chartItems?.mapNotNull { edge ->
            runCatching {
                val node = (edge as? JsonObject)?.let {
                    (it["node"] as? JsonObject) ?: it
                } ?: return@runCatching null

                val id = node.string("id") ?: return@runCatching null
                val title = node.path("titleText", "text")?.asString() ?: return@runCatching null
                val year = node.path("releaseYear", "year")?.asInt()?.toString()
                val poster = node.path("primaryImage", "url")?.asString()
                val typeId = node.path("titleType", "id")?.asString()
                val rating = node.path("ratingsSummary", "aggregateRating")?.asFloat()?.toString()

                ImdbSearchItem(
                    imdbId = id,
                    title = title,
                    year = year,
                    type = TitleType.from(typeId),
                    poster = poster,
                    subtitle = rating?.let { "★ $it" },
                )
            }.getOrNull()
        } ?: emptyList()
    }.getOrElse { emptyList() }

    private fun extractFromHtml(doc: Document): List<ImdbSearchItem> =
        doc.select(ImdbSelectors.CHART_ITEM).mapNotNull { item ->
            runCatching {
                val link = item.selectFirst(ImdbSelectors.CHART_ITEM_LINK) ?: return@runCatching null
                val href = link.attr("href")
                val id = Regex("""/title/(tt\d+)/""").find(href)?.groupValues?.get(1) ?: return@runCatching null

                val title = item.selectFirst(ImdbSelectors.CHART_ITEM_TITLE)?.text()
                    ?.removePrefix("${item.elementSiblingIndex() + 1}. ")
                    ?.blankAsNull() ?: return@runCatching null

                val metadataItems = item.select(ImdbSelectors.CHART_ITEM_YEAR)
                val year = metadataItems.firstOrNull()?.text()?.blankAsNull()
                val rating = item.selectFirst(ImdbSelectors.CHART_ITEM_RATING)?.text()?.blankAsNull()
                val poster = item.selectFirst(ImdbSelectors.CHART_ITEM_IMAGE)?.attr("src")?.blankAsNull()

                ImdbSearchItem(
                    imdbId = id,
                    title = title,
                    year = year,
                    poster = poster,
                    subtitle = rating?.let { "★ $it" },
                )
            }.getOrNull()
        }
}
