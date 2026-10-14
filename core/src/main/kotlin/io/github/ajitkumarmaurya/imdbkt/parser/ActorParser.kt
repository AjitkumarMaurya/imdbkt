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
