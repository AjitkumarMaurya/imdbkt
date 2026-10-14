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
