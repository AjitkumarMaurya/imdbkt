package io.github.ajitkumarmaurya.imdbkt.parser

import io.github.ajitkumarmaurya.imdbkt.model.FilmographyItem
import io.github.ajitkumarmaurya.imdbkt.model.ImdbActor
import io.github.ajitkumarmaurya.imdbkt.model.ImdbSearchItem
import io.github.ajitkumarmaurya.imdbkt.model.TitleType
import io.github.ajitkumarmaurya.imdbkt.parser.selectors.ImdbSelectors
import io.github.ajitkumarmaurya.imdbkt.utils.asFloat
import io.github.ajitkumarmaurya.imdbkt.utils.asInt
import io.github.ajitkumarmaurya.imdbkt.utils.asString
import io.github.ajitkumarmaurya.imdbkt.utils.blankAsNull
import io.github.ajitkumarmaurya.imdbkt.utils.obj
import io.github.ajitkumarmaurya.imdbkt.utils.path
import io.github.ajitkumarmaurya.imdbkt.utils.string
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Parses an IMDb person/actor page.
 *
 * Uses `__NEXT_DATA__` as primary source and Jsoup selectors as fallback.
 */
internal class ActorParser(private val json: Json) {

    fun buildUrl(actorId: String): String = ImdbSelectors.ACTOR_URL.format(actorId)

    fun parse(actorId: String, html: String): ImdbActor {
        val doc = Jsoup.parse(html)
        val nextData = extractNextData(doc)

        val nameData = nextData?.path("props", "pageProps", "nameDetails") as? JsonObject
        val aboveFold = nextData?.path("props", "pageProps", "aboveTheFoldData") as? JsonObject

        return ImdbActor(
            imdbId = actorId,
            name = parseName(aboveFold, doc),
            bio = parseBio(nameData, doc),
            profileImage = parseProfileImage(aboveFold, doc),
            birthDate = parseBirthDate(nameData, doc),
            birthPlace = parseBirthPlace(nameData, doc),
            deathDate = parseDeathDate(nameData),
            height = parseHeight(nameData),
            knownFor = parseKnownFor(aboveFold, doc),
            filmography = parseFilmography(nameData, doc),
        )
    }

    private fun extractNextData(doc: Document): JsonObject? = runCatching {
        val script = doc.selectFirst(ImdbSelectors.NEXT_DATA_SCRIPT)?.data() ?: return null
        json.parseToJsonElement(script).jsonObject
    }.getOrNull()

    private fun parseName(aboveFold: JsonObject?, doc: Document): String =
        aboveFold?.path("nameText", "text")?.asString()?.blankAsNull()
            ?: doc.selectFirst(ImdbSelectors.ACTOR_NAME)?.text()?.blankAsNull()
            ?: ""

    private fun parseBio(nameData: JsonObject?, doc: Document): String? =
        nameData?.path("bio", "text", "plainText")?.asString()?.blankAsNull()
            ?: doc.selectFirst(ImdbSelectors.ACTOR_BIO)?.text()?.blankAsNull()

    private fun parseProfileImage(aboveFold: JsonObject?, doc: Document): String? =
        aboveFold?.path("primaryImage", "url")?.asString()?.blankAsNull()
            ?: doc.selectFirst(ImdbSelectors.ACTOR_PHOTO)?.attr("src")?.blankAsNull()

    private fun parseBirthDate(nameData: JsonObject?, doc: Document): String? =
        nameData?.path("birthDate", "displayableProperty", "value", "plainText")?.asString()?.blankAsNull()
            ?: doc.selectFirst(ImdbSelectors.ACTOR_BIRTH_DATE)?.text()?.blankAsNull()

    private fun parseBirthPlace(nameData: JsonObject?, doc: Document): String? =
        nameData?.path("birthLocation", "displayableProperty", "value", "plainText")?.asString()?.blankAsNull()
            ?: doc.selectFirst(ImdbSelectors.ACTOR_BIRTH_PLACE)?.text()?.blankAsNull()

    private fun parseDeathDate(nameData: JsonObject?): String? =
        nameData?.path("deathDate", "displayableProperty", "value", "plainText")?.asString()?.blankAsNull()

    private fun parseHeight(nameData: JsonObject?): String? =
        nameData?.path("height", "displayableProperty", "value", "plainText")?.asString()?.blankAsNull()

    private fun parseKnownFor(aboveFold: JsonObject?, doc: Document): List<ImdbSearchItem> {
        val edges = (aboveFold?.path("knownFor", "edges") as? JsonArray) ?: return parseKnownForFromHtml(doc)
        return edges.mapNotNull { edge ->
            runCatching {
                val title = edge.path("node", "title") as? JsonObject ?: return@runCatching null
                val id = title.string("id") ?: return@runCatching null
                val name = title.path("titleText", "text")?.asString() ?: return@runCatching null
                val year = title.path("releaseYear", "year")?.asInt()?.toString()
                val poster = title.path("primaryImage", "url")?.asString()
                ImdbSearchItem(imdbId = id, title = name, year = year, poster = poster)
            }.getOrNull()
        }
    }

    private fun parseKnownForFromHtml(doc: Document): List<ImdbSearchItem> =
        doc.select(ImdbSelectors.ACTOR_KNOWN_FOR).take(8).mapNotNull { el ->
            runCatching {
                val href = el.attr("href")
                val id = Regex("""/title/(tt\d+)/""").find(href)?.groupValues?.get(1) ?: return@runCatching null
                val title = el.selectFirst("div")?.text()?.blankAsNull() ?: return@runCatching null
                ImdbSearchItem(imdbId = id, title = title)
            }.getOrNull()
        }

    private fun parseFilmography(nameData: JsonObject?, doc: Document): List<FilmographyItem> {
        val credits = nameData?.path("credits") as? JsonArray ?: return parseFilmographyFromHtml(doc)
        return credits.flatMap { categoryEl ->
            val category = (categoryEl as? JsonObject) ?: return@flatMap emptyList()
            val categoryName = category.string("category") ?: ""
            val titles = (category.path("credits") as? JsonArray) ?: return@flatMap emptyList()
            titles.mapNotNull { titleEl ->
                runCatching {
                    val t = titleEl as? JsonObject ?: return@runCatching null
                    val titleObj = t.obj("title") ?: return@runCatching null
                    val id = titleObj.string("id") ?: return@runCatching null
                    val name = titleObj.path("titleText", "text")?.asString() ?: return@runCatching null
                    val year = titleObj.path("releaseYear", "year")?.asInt()?.toString()
                    val poster = titleObj.path("primaryImage", "url")?.asString()
                    val characters = (t.path("characters") as? JsonArray)
                        ?.mapNotNull { it.path("name")?.asString() } ?: emptyList()
                    val typeId = titleObj.path("titleType", "id")?.asString()
                    FilmographyItem(
                        imdbId = id,
                        title = name,
                        year = year,
                        role = categoryName,
                        characters = characters,
                        type = TitleType.from(typeId),
                        poster = poster,
                    )
                }.getOrNull()
            }
        }.take(MAX_FILMOGRAPHY)
    }

    private fun parseFilmographyFromHtml(doc: Document): List<FilmographyItem> =
        doc.select(ImdbSelectors.ACTOR_FILMOGRAPHY_SECTION).take(MAX_FILMOGRAPHY).mapNotNull { el ->
            runCatching {
                val link = el.selectFirst(ImdbSelectors.ACTOR_FILMOGRAPHY_LINK) ?: return@runCatching null
                val href = link.attr("href")
                val id = Regex("""/title/(tt\d+)/""").find(href)?.groupValues?.get(1) ?: return@runCatching null
                val title = link.text().blankAsNull() ?: return@runCatching null
                val year = el.select("span.ipc-metadata-list-summary-item__li").firstOrNull()?.text()
                FilmographyItem(imdbId = id, title = title, year = year)
            }.getOrNull()
        }

    companion object {
        private const val MAX_FILMOGRAPHY = 50
    }
}
