package io.github.ajitkumarmaurya.imdbkt.parser

import io.github.ajitkumarmaurya.imdbkt.model.ImdbSearchItem
import io.github.ajitkumarmaurya.imdbkt.model.TitleType
import io.github.ajitkumarmaurya.imdbkt.parser.selectors.ImdbSelectors
import io.github.ajitkumarmaurya.imdbkt.utils.asString
import io.github.ajitkumarmaurya.imdbkt.utils.blankAsNull
import io.github.ajitkumarmaurya.imdbkt.utils.int
import io.github.ajitkumarmaurya.imdbkt.utils.obj
import io.github.ajitkumarmaurya.imdbkt.utils.string
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Parses the IMDb suggestion API response.
 *
 * Endpoint: https://v3.sg.media-imdb.com/suggestion/x/{query}.json
 *
 * The response is a JSON object with a "d" array of suggestion items.
 * This endpoint is considerably more stable than the HTML search page.
 */
internal class SearchParser(private val json: Json) {

    fun buildUrl(query: String): String {
        val encoded = query.trim().lowercase()
            .replace(" ", "_")
            .take(MAX_QUERY_LENGTH)
        return ImdbSelectors.SUGGESTION_URL.format(encoded)
    }

    fun parse(responseBody: String): List<ImdbSearchItem> {
        return runCatching {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val items = root["d"]?.jsonArray ?: return emptyList()
            items.mapNotNull { element ->
                runCatching { parseSuggestionItem(element.jsonObject) }.getOrNull()
            }
        }.getOrElse { emptyList() }
    }

    private fun parseSuggestionItem(item: JsonObject): ImdbSearchItem? {
        val id = item.string("id")?.blankAsNull() ?: return null
        val title = item.string("l")?.blankAsNull() ?: return null

        val year = item.int("y")?.toString()
        val subtitle = item.string("s")?.blankAsNull()
        val rank = item.int("rank")
        val qid = item.string("qid")
        val q = item.string("q")

        val type = TitleType.from(qid ?: q)
        val poster = item.obj("i")?.string("imageUrl")

        return ImdbSearchItem(
            imdbId = id,
            title = title,
            year = year,
            type = type,
            poster = poster,
            subtitle = subtitle,
            rank = rank,
        )
    }

    companion object {
        private const val MAX_QUERY_LENGTH = 20
    }
}
