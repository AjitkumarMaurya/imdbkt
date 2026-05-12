package io.github.ajitkumarmaurya.imdbkt.parser

import io.github.ajitkumarmaurya.imdbkt.model.BoxOffice
import io.github.ajitkumarmaurya.imdbkt.model.CastMember
import io.github.ajitkumarmaurya.imdbkt.model.Credit
import io.github.ajitkumarmaurya.imdbkt.model.ImdbSearchItem
import io.github.ajitkumarmaurya.imdbkt.model.ImdbTitle
import io.github.ajitkumarmaurya.imdbkt.model.TitleType
import io.github.ajitkumarmaurya.imdbkt.parser.selectors.ImdbSelectors
import io.github.ajitkumarmaurya.imdbkt.parser.selectors.ImdbSelectors.NextData
import io.github.ajitkumarmaurya.imdbkt.utils.arr
import io.github.ajitkumarmaurya.imdbkt.utils.asFloat
import io.github.ajitkumarmaurya.imdbkt.utils.asInt
import io.github.ajitkumarmaurya.imdbkt.utils.asLong
import io.github.ajitkumarmaurya.imdbkt.utils.asString
import io.github.ajitkumarmaurya.imdbkt.utils.blankAsNull
import io.github.ajitkumarmaurya.imdbkt.utils.float
import io.github.ajitkumarmaurya.imdbkt.utils.int
import io.github.ajitkumarmaurya.imdbkt.utils.long
import io.github.ajitkumarmaurya.imdbkt.utils.obj
import io.github.ajitkumarmaurya.imdbkt.utils.path
import io.github.ajitkumarmaurya.imdbkt.utils.secondsToMinutes
import io.github.ajitkumarmaurya.imdbkt.utils.string
import io.github.ajitkumarmaurya.imdbkt.utils.strings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Parses a full IMDb title page (movie or series).
 *
 * Strategy:
 * 1. Extract `__NEXT_DATA__` JSON — the richest source.
 * 2. Fall back to JSON-LD for fields not present in __NEXT_DATA__.
 * 3. Use Jsoup CSS selectors as last resort for elements that are
 *    only in raw HTML.
 */
internal class TitleParser(private val json: Json) {

    fun buildUrl(imdbId: String): String = ImdbSelectors.TITLE_URL.format(imdbId)

    fun parse(imdbId: String, html: String): ImdbTitle {
        val doc = Jsoup.parse(html)
        val nextData = extractNextData(doc)
        val jsonLd = extractJsonLd(doc)

        val aboveFold: JsonElement? = nextData?.path(*NextData.ABOVE_FOLD.split(".").toTypedArray())
        val mainCol: JsonElement? = nextData?.path(*NextData.MAIN_COLUMN.split(".").toTypedArray())

        return ImdbTitle(
            imdbId = imdbId,
            title = parseTitle(aboveFold, jsonLd, doc),
            originalTitle = parseOriginalTitle(aboveFold, jsonLd),
            type = parseTitleType(aboveFold),
            year = parseYear(aboveFold),
            endYear = parseEndYear(aboveFold),
            description = parsePlot(aboveFold, jsonLd, doc),
            storyline = parseStoryline(mainCol),
            rating = parseRating(aboveFold, jsonLd),
            voteCount = parseVoteCount(aboveFold),
            genres = parseGenres(aboveFold, jsonLd),
            releaseDate = parseReleaseDate(aboveFold),
            runtimeMinutes = parseRuntime(aboveFold, jsonLd),
            languages = parseLanguages(aboveFold),
            countries = parseCountries(aboveFold),
            certificate = parseCertificate(aboveFold, doc),
            poster = parsePoster(aboveFold, jsonLd, doc),
            cast = parseCast(mainCol, doc),
            directors = parseCredits(mainCol, "directors", doc),
            writers = parseCredits(mainCol, "writers", doc),
            creators = parseCredits(mainCol, "creators", doc),
            productionCompanies = parseProductionCompanies(mainCol, doc),
            seasons = parseSeasonCount(aboveFold, doc),
            relatedTitles = parseRelatedTitles(mainCol),
            boxOffice = parseBoxOffice(mainCol),
            keywords = parseKeywords(mainCol),
        )
    }

    // ── Next.js data extraction ───────────────────────────────────────────────

    private fun extractNextData(doc: Document): JsonObject? = runCatching {
        val script = doc.selectFirst(ImdbSelectors.NEXT_DATA_SCRIPT)?.data() ?: return null
        json.parseToJsonElement(script).jsonObject
    }.getOrNull()

    private fun extractJsonLd(doc: Document): JsonObject? = runCatching {
        doc.select(ImdbSelectors.JSON_LD_SCRIPT).firstNotNullOfOrNull { el ->
            runCatching { json.parseToJsonElement(el.data()).jsonObject }.getOrNull()
        }
    }.getOrNull()

    // ── Field parsers ─────────────────────────────────────────────────────────

    private fun parseTitle(
        aboveFold: JsonElement?,
        jsonLd: JsonObject?,
        doc: Document,
    ): String =
        aboveFold?.path("titleText", "text")?.asString()?.blankAsNull()
            ?: jsonLd?.string("name")?.blankAsNull()
            ?: doc.selectFirst(ImdbSelectors.TITLE_HERO_TITLE)?.text()?.blankAsNull()
            ?: ""

    private fun parseOriginalTitle(aboveFold: JsonElement?, jsonLd: JsonObject?): String? =
        aboveFold?.path("originalTitleText", "text")?.asString()?.blankAsNull()
            ?: jsonLd?.string("alternateName")?.blankAsNull()

    private fun parseTitleType(aboveFold: JsonElement?): TitleType {
        val typeId = aboveFold?.path("titleType", "id")?.asString()
        val isSeries = aboveFold?.path("titleType", "isSeries")?.asString()?.toBooleanStrictOrNull()
        return when {
            typeId != null -> TitleType.from(typeId)
            isSeries == true -> TitleType.TV_SERIES
            else -> TitleType.UNKNOWN
        }
    }

    private fun parseYear(aboveFold: JsonElement?): String? =
        aboveFold?.path("releaseYear", "year")?.asInt()?.toString()

    private fun parseEndYear(aboveFold: JsonElement?): String? =
        aboveFold?.path("releaseYear", "endYear")?.asInt()?.toString()

    private fun parsePlot(
        aboveFold: JsonElement?,
        jsonLd: JsonObject?,
        doc: Document,
    ): String? =
        aboveFold?.path("plot", "plotText", "plainText")?.asString()?.blankAsNull()
            ?: jsonLd?.string("description")?.blankAsNull()
            ?: doc.selectFirst(ImdbSelectors.TITLE_PLOT)?.text()?.blankAsNull()

    private fun parseStoryline(mainCol: JsonElement?): String? =
        mainCol?.path("storyline", "plotText", "plainText")?.asString()?.blankAsNull()

    private fun parseRating(aboveFold: JsonElement?, jsonLd: JsonObject?): Float? {
        val fromNextData = aboveFold?.path("ratingsSummary", "aggregateRating")?.asFloat()
        if (fromNextData != null) return fromNextData
        return (jsonLd?.obj("aggregateRating"))
            ?.string("ratingValue")?.toFloatOrNull()
    }

    private fun parseVoteCount(aboveFold: JsonElement?): Long? =
        aboveFold?.path("ratingsSummary", "voteCount")?.asLong()

    private fun parseGenres(aboveFold: JsonElement?, jsonLd: JsonObject?): List<String> {
        val fromNextData = (aboveFold?.path("genres", "genres") as? JsonArray)
            ?.mapNotNull { it.string("text") }
        if (!fromNextData.isNullOrEmpty()) return fromNextData

        return when (val g = jsonLd?.get("genre")) {
            is kotlinx.serialization.json.JsonArray -> g.mapNotNull { it.asString() }
            is kotlinx.serialization.json.JsonPrimitive -> listOfNotNull(g.asString())
            else -> emptyList()
        }
    }

    private fun parseReleaseDate(aboveFold: JsonElement?): String? {
        val rd = aboveFold?.obj("releaseDate") ?: return null
        val year = rd.int("year") ?: return null
        val month = rd.int("month")
        val day = rd.int("day")
        return buildString {
            if (day != null && month != null) append("$year-${month.toString().padStart(2,'0')}-${day.toString().padStart(2,'0')}")
            else if (month != null) append("$year-${month.toString().padStart(2,'0')}")
            else append(year)
        }
    }

    private fun parseRuntime(aboveFold: JsonElement?, jsonLd: JsonObject?): Int? {
        val seconds = aboveFold?.path("runtime", "seconds")?.asInt()
        if (seconds != null) return seconds.secondsToMinutes()
        // JSON-LD uses ISO 8601 duration: PT2H49M
        return jsonLd?.string("duration")?.let { parseDuration(it) }
    }

    private fun parseDuration(iso8601: String): Int? {
        val regex = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?""")
        val match = regex.find(iso8601) ?: return null
        val hours = match.groupValues[1].toIntOrNull() ?: 0
        val minutes = match.groupValues[2].toIntOrNull() ?: 0
        return hours * 60 + minutes
    }

    private fun parseLanguages(aboveFold: JsonElement?): List<String> =
        (aboveFold?.path("spokenLanguages", "spokenLanguages") as? JsonArray)
            ?.mapNotNull { it.string("text") } ?: emptyList()

    private fun parseCountries(aboveFold: JsonElement?): List<String> =
        (aboveFold?.path("countriesOfOrigin", "countries") as? JsonArray)
            ?.mapNotNull { it.string("text") } ?: emptyList()

    private fun parseCertificate(aboveFold: JsonElement?, doc: Document): String? =
        aboveFold?.path("certificate", "rating")?.asString()?.blankAsNull()
            ?: doc.selectFirst(ImdbSelectors.TITLE_CERTIFICATE)?.text()?.blankAsNull()

    private fun parsePoster(
        aboveFold: JsonElement?,
        jsonLd: JsonObject?,
        doc: Document,
    ): String? =
        aboveFold?.path("primaryImage", "url")?.asString()?.blankAsNull()
            ?: (jsonLd?.get("image") as? kotlinx.serialization.json.JsonPrimitive)?.asString()?.blankAsNull()
            ?: doc.selectFirst(ImdbSelectors.TITLE_POSTER)?.attr("src")?.blankAsNull()

    private fun parseCast(mainCol: JsonElement?, doc: Document): List<CastMember> {
        val edges = (mainCol?.path("cast", "edges") as? JsonArray) ?: return parseCastFromHtml(doc)
        return edges.mapNotNull { edge ->
            runCatching {
                val node = edge.obj("node") ?: return@runCatching null
                val nameNode = node.obj("name") ?: return@runCatching null
                val id = nameNode.string("id") ?: return@runCatching null
                val name = nameNode.path("nameText", "text")?.asString() ?: return@runCatching null
                val characters = (node.arr("characters"))
                    ?.mapNotNull { it.string("name") } ?: emptyList()
                val image = node.path("primaryImage", "url")?.asString()?.blankAsNull()
                CastMember(imdbId = id, name = name, characters = characters, profileImage = image)
            }.getOrNull()
        }.take(MAX_CAST)
    }

    private fun parseCastFromHtml(doc: Document): List<CastMember> =
        doc.select(ImdbSelectors.TITLE_CAST_ROW).take(MAX_CAST).mapNotNull { row ->
            runCatching {
                val actorLink = row.selectFirst(ImdbSelectors.TITLE_CAST_ACTOR_LINK) ?: return@runCatching null
                val name = actorLink.text().blankAsNull() ?: return@runCatching null
                val href = actorLink.attr("href")
                val id = Regex("""/name/(nm\d+)/""").find(href)?.groupValues?.get(1) ?: return@runCatching null
                val char = row.selectFirst(ImdbSelectors.TITLE_CAST_CHAR)?.text()?.blankAsNull()
                val image = row.selectFirst(ImdbSelectors.TITLE_CAST_IMAGE)?.attr("src")?.blankAsNull()
                CastMember(
                    imdbId = id,
                    name = name,
                    characters = listOfNotNull(char),
                    profileImage = image,
                )
            }.getOrNull()
        }

    private fun parseCredits(
        mainCol: JsonElement?,
        key: String,
        doc: Document,
    ): List<Credit> {
        val creditArray = (mainCol?.path(key) as? JsonArray) ?: return emptyList()
        return creditArray.flatMap { section ->
            ((section as? JsonObject)?.arr("credits") ?: return@flatMap emptyList()).mapNotNull { credit ->
                runCatching {
                    val nameNode = credit.obj("name") ?: return@runCatching null
                    val id = nameNode.string("id") ?: return@runCatching null
                    val name = nameNode.path("nameText", "text")?.asString() ?: return@runCatching null
                    val job = (credit.arr("jobs"))
                        ?.firstNotNullOfOrNull { it.string("text") }
                    Credit(imdbId = id, name = name, job = job)
                }.getOrNull()
            }
        }
    }

    private fun parseProductionCompanies(mainCol: JsonElement?, doc: Document): List<String> {
        val edges = (mainCol?.path("production", "edges") as? JsonArray)
            ?: return doc.select(ImdbSelectors.TITLE_PROD_COMPANY).map { it.text() }
        return edges.mapNotNull { it.path("node", "company", "companyText", "text")?.asString() }
    }

    private fun parseSeasonCount(aboveFold: JsonElement?, doc: Document): Int? {
        val fromData = (aboveFold?.path("episodes", "seasons") as? JsonArray)?.size
        if (fromData != null) return fromData
        val options = doc.select(ImdbSelectors.TITLE_SEASONS)
        return if (options.isNotEmpty()) options.size else null
    }

    private fun parseRelatedTitles(mainCol: JsonElement?): List<ImdbSearchItem> {
        val edges = (mainCol?.path("moreLikeThisTitles", "edges") as? JsonArray) ?: return emptyList()
        return edges.mapNotNull { edge ->
            runCatching {
                val node = edge.obj("node") ?: return@runCatching null
                val id = node.string("id") ?: return@runCatching null
                val title = node.path("titleText", "text")?.asString() ?: return@runCatching null
                val year = node.path("releaseYear", "year")?.asInt()?.toString()
                val poster = node.path("primaryImage", "url")?.asString()
                ImdbSearchItem(imdbId = id, title = title, year = year, poster = poster)
            }.getOrNull()
        }
    }

    private fun parseBoxOffice(mainCol: JsonElement?): BoxOffice? {
        val budget = mainCol?.path("productionBudget", "budget", "amount")?.asString()
        val opening = mainCol?.path("openingWeekendGross", "gross", "total", "amount")?.asString()
        val usGross = mainCol?.path("lifetimeGross", "gross", "total", "amount")?.asString()
        val worldGross = mainCol?.path("worldwideGross", "gross", "total", "amount")?.asString()
        if (listOf(budget, opening, usGross, worldGross).all { it == null }) return null
        return BoxOffice(
            budget = budget,
            openingWeekendUS = opening,
            grossUS = usGross,
            cumulativeWorldwideGross = worldGross,
        )
    }

    private fun parseKeywords(mainCol: JsonElement?): List<String> {
        val edges = (mainCol?.path("keywords", "edges") as? JsonArray) ?: return emptyList()
        return edges.mapNotNull { it.path("node", "text")?.asString() }
    }

    companion object {
        private const val MAX_CAST = 30
    }
}
