package io.github.ajitkumarmaurya.imdbkt.parser

import io.github.ajitkumarmaurya.imdbkt.model.CastMember
import io.github.ajitkumarmaurya.imdbkt.model.Credit
import io.github.ajitkumarmaurya.imdbkt.model.ImdbSearchItem
import io.github.ajitkumarmaurya.imdbkt.model.ImdbTitle
import io.github.ajitkumarmaurya.imdbkt.model.TitleType
import io.github.ajitkumarmaurya.imdbkt.network.FirecrawlSearchHit

/**
 * Parses Firecrawl markdown responses into library model objects.
 *
 * Firecrawl scrapes the fully-rendered IMDb page and returns visible text
 * as markdown. This parser extracts structured fields from that text using
 * a combination of section-heading detection and regex matching.
 *
 * The parser is intentionally lenient: every field is optional, so a
 * partially-rendered or bot-challenged page still yields whatever data
 * is present rather than throwing.
 */
internal class FirecrawlMarkdownParser {

    // ── Public API ────────────────────────────────────────────────────────────

    fun parseTitle(imdbId: String, markdown: String, posterUrl: String? = null): ImdbTitle {
        val lines = markdown.lines()
        val sections = splitSections(lines)

        return ImdbTitle(
            imdbId = imdbId,
            title = extractTitle(lines),
            type = extractTitleType(markdown),
            year = extractYear(markdown),
            description = extractPlot(sections, markdown),
            rating = extractRating(markdown),
            voteCount = extractVoteCount(markdown),
            genres = extractGenres(markdown),
            runtimeMinutes = extractRuntime(markdown),
            certificate = extractCertificate(markdown),
            releaseDate = extractReleaseDate(sections, markdown),
            countries = extractSection(sections, COUNTRIES_KEYS),
            languages = extractSection(sections, LANGUAGE_KEYS),
            cast = extractCast(sections),
            directors = extractCredits(sections, DIRECTOR_KEYS),
            writers = extractCredits(sections, WRITER_KEYS),
            keywords = extractKeywords(sections, markdown),
            poster = posterUrl ?: extractPosterFromMarkdown(markdown),
        )
    }

    /** Convert Firecrawl search hits into [ImdbSearchItem] list. */
    fun parseSearchHits(hits: List<FirecrawlSearchHit>): List<ImdbSearchItem> =
        hits.mapNotNull { hit ->
            val imdbId = IMDB_ID_REGEX.find(hit.url)?.groupValues?.get(1) ?: return@mapNotNull null
            val rawTitle = hit.title?.removeSuffix(" - IMDb")?.trim() ?: return@mapNotNull null
            val year = YEAR_PARENS_REGEX.find(rawTitle)?.groupValues?.get(1)
            val cleanTitle = rawTitle.replace(YEAR_PARENS_REGEX, "").trim()
            ImdbSearchItem(
                imdbId = imdbId,
                title = cleanTitle.ifBlank { rawTitle },
                year = year,
                subtitle = hit.description?.take(120),
                type = TitleType.MOVIE,
            )
        }

    // ── Section splitting ─────────────────────────────────────────────────────

    /**
     * Splits markdown into a map of section-heading → body-lines.
     * Headings are normalised to lowercase for case-insensitive lookup.
     */
    private fun splitSections(lines: List<String>): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()
        var current = "__preamble__"
        result[current] = mutableListOf()
        for (line in lines) {
            val heading = HEADING_REGEX.find(line)?.groupValues?.get(1)?.trim()?.lowercase()
            if (heading != null) {
                current = heading
                result.getOrPut(current) { mutableListOf() }
            } else {
                result.getOrPut(current) { mutableListOf() }.add(line)
            }
        }
        return result
    }

    // ── Field extractors ──────────────────────────────────────────────────────

    private fun extractTitle(lines: List<String>): String {
        // First H1 heading — strip "(YYYY)" suffix if present
        for (line in lines) {
            val h1 = H1_REGEX.find(line)?.groupValues?.get(1) ?: continue
            return h1.replace(YEAR_PARENS_REGEX, "").trim().ifBlank { h1.trim() }
        }
        return ""
    }

    private fun extractYear(markdown: String): String? =
        YEAR_PARENS_REGEX.find(markdown)?.groupValues?.get(1)
            ?: STANDALONE_YEAR_REGEX.find(markdown)?.groupValues?.get(1)

    private fun extractTitleType(markdown: String): TitleType {
        val lower = markdown.lowercase()
        return when {
            "tv mini" in lower || "miniseries" in lower -> TitleType.TV_MINI_SERIES
            "tv series" in lower || "television series" in lower -> TitleType.TV_SERIES
            "tv episode" in lower -> TitleType.TV_EPISODE
            "tv movie" in lower -> TitleType.TV_MOVIE
            "short film" in lower -> TitleType.SHORT
            else -> TitleType.MOVIE
        }
    }

    private fun extractRating(markdown: String): Float? {
        // Patterns: "8.7/10", "**8.7**/10", "Rating: 8.7"
        return RATING_SLASH_REGEX.find(markdown)?.groupValues?.get(1)?.toFloatOrNull()
            ?: RATING_LABEL_REGEX.find(markdown)?.groupValues?.get(1)?.toFloatOrNull()
    }

    private fun extractVoteCount(markdown: String): Long? {
        val match = VOTE_COUNT_REGEX.find(markdown) ?: return null
        val raw = match.groupValues[1].replace(",", "").replace(" ", "")
        return when {
            raw.endsWith("m", ignoreCase = true) ->
                (raw.dropLast(1).toDoubleOrNull()?.times(1_000_000))?.toLong()
            raw.endsWith("k", ignoreCase = true) ->
                (raw.dropLast(1).toDoubleOrNull()?.times(1_000))?.toLong()
            else -> raw.toLongOrNull()
        }
    }

    private fun extractRuntime(markdown: String): Int? {
        RUNTIME_HM_REGEX.find(markdown)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: 0
            val min = m.groupValues[2].toIntOrNull() ?: 0
            return h * 60 + min
        }
        return RUNTIME_MIN_REGEX.find(markdown)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractCertificate(markdown: String): String? =
        CERT_REGEX.find(markdown)?.value

    private fun extractGenres(markdown: String): List<String> {
        // Try explicit "Genres" section first
        val genreSection = markdown.lines()
            .dropWhile { !it.contains("genre", ignoreCase = true) }
            .drop(1).take(5).joinToString(" ")

        val found = KNOWN_GENRES.filter { genre ->
            Regex("""\b${Regex.escape(genre)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(markdown)
        }
        return found.take(6)
    }

    private fun extractPlot(sections: Map<String, List<String>>, markdown: String): String? {
        // Look for a "plot" or "storyline" section
        val sectionBody = (PLOT_KEYS + STORYLINE_KEYS)
            .firstNotNullOfOrNull { key -> sections[key]?.nonBlankLines() }
        if (!sectionBody.isNullOrBlank()) return sectionBody

        // Fall back: first substantial paragraph after the preamble metadata block
        val preamble = sections["__preamble__"] ?: return null
        return preamble
            .dropWhile { it.isBlank() || it.trimStart('#', '*', ' ').length < 20 }
            .take(3)
            .joinToString(" ")
            .trim()
            .takeIf { it.length > 30 }
    }

    private fun extractReleaseDate(sections: Map<String, List<String>>, markdown: String): String? {
        // ISO date in any section
        ISO_DATE_REGEX.find(markdown)?.let { return it.value }

        // "November 7, 2014" style in release-date section
        val body = RELEASE_KEYS.firstNotNullOfOrNull { key -> sections[key]?.nonBlankLines() }
            ?: return null
        val m = WORDY_DATE_REGEX.find(body) ?: return null
        val month = MONTH_MAP[m.groupValues[1].lowercase()] ?: return null
        val day = m.groupValues[2].toIntOrNull() ?: return null
        val year = m.groupValues[3]
        return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }

    private fun extractSection(sections: Map<String, List<String>>, keys: List<String>): List<String> {
        val body = keys.firstNotNullOfOrNull { key -> sections[key]?.nonBlankLines() } ?: return emptyList()
        // Split on commas, bullets, or newlines
        return body.split(Regex("""[,\n•|]+"""))
            .map { it.trim().trimStart('-', '*', ' ') }
            .filter { it.length in 2..60 }
    }

    private fun extractCast(sections: Map<String, List<String>>): List<CastMember> {
        val body = CAST_KEYS.firstNotNullOfOrNull { key -> sections[key] } ?: return emptyList()
        val members = mutableListOf<CastMember>()
        var pendingName: String? = null

        for (line in body) {
            val clean = line.trim().trimStart('-', '*', '|', ' ')
            if (clean.isBlank()) continue

            // "Name - Character" or "Name | Character" on a single line
            val inlineSplit = Regex("""^(.+?)\s*[-|]\s*(.+)$""").find(clean)
            if (inlineSplit != null) {
                val name = inlineSplit.groupValues[1].trim()
                val char = inlineSplit.groupValues[2].trim()
                if (isPersonName(name)) {
                    members += CastMember(
                        imdbId = nameToFakeId(name),
                        name = name,
                        characters = listOf(char),
                    )
                    pendingName = null
                    continue
                }
            }

            // Two-line format: Name on one line, Character on the next
            if (pendingName != null) {
                members += CastMember(
                    imdbId = nameToFakeId(pendingName),
                    name = pendingName,
                    characters = if (clean.length < 60) listOf(clean) else emptyList(),
                )
                pendingName = null
            } else if (isPersonName(clean)) {
                pendingName = clean
            }
        }
        if (pendingName != null) {
            members += CastMember(imdbId = nameToFakeId(pendingName), name = pendingName)
        }
        return members.take(20)
    }

    private fun extractCredits(sections: Map<String, List<String>>, keys: List<String>): List<Credit> {
        val body = keys.firstNotNullOfOrNull { key -> sections[key] } ?: return emptyList()
        return body
            .map { it.trim().trimStart('-', '*', ' ') }
            .filter { isPersonName(it) }
            .map { Credit(imdbId = nameToFakeId(it), name = it) }
            .take(10)
    }

    private fun extractKeywords(sections: Map<String, List<String>>, markdown: String): List<String> {
        val body = KEYWORD_KEYS.firstNotNullOfOrNull { key -> sections[key]?.nonBlankLines() }
        if (!body.isNullOrBlank()) {
            return body.split(Regex("""[,\n•]+"""))
                .map { it.trim().lowercase() }
                .filter { it.length in 2..40 }
                .take(15)
        }
        // IMDb keyword bar in preamble, e.g. "nasa · time travel · wormhole"
        val kwMatch = KEYWORD_BAR_REGEX.find(markdown)
        if (kwMatch != null) {
            return kwMatch.value.split(Regex("""[,·|]+"""))
                .map { it.trim().lowercase() }
                .filter { it.length in 2..40 }
        }
        return emptyList()
    }

    // ── Image extraction ──────────────────────────────────────────────────────

    private fun extractPosterFromMarkdown(markdown: String): String? {
        // Prefer Amazon CDN image URLs (IMDb always uses media-amazon.com for posters)
        val amazonImage = Regex("""!\[[^\]]*]\((https://m\.media-amazon\.com/[^)]+)\)""")
            .find(markdown)?.groupValues?.get(1)
        if (amazonImage != null) return amazonImage
        // Fall back to any https image link
        return Regex("""!\[[^\]]*]\((https://[^)]+\.(?:jpg|jpeg|png|webp)[^)]*)\)""", RegexOption.IGNORE_CASE)
            .find(markdown)?.groupValues?.get(1)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Join non-blank lines in a section into a single string. */
    private fun List<String>.nonBlankLines(): String? =
        filter { it.isNotBlank() }.joinToString(" ").trim().takeIf { it.isNotBlank() }

    /** Rough heuristic: a person name is 2–5 words, each capitalised, no punctuation. */
    private fun isPersonName(s: String): Boolean {
        val words = s.trim().split(Regex("""\s+"""))
        return words.size in 2..5 &&
            words.all { it.firstOrNull()?.isUpperCase() == true } &&
            !s.contains(Regex("""[<>{}\[\]@#$%^&*=+/\\|]""")) &&
            s.length < 60
    }

    /** Stable placeholder ID derived from name — real IDs come from normal parser. */
    private fun nameToFakeId(name: String): String =
        "fc_" + name.lowercase().replace(Regex("""\s+"""), "_").take(30)

    // ── Regex constants ───────────────────────────────────────────────────────

    companion object {
        private val HEADING_REGEX = Regex("""^#{1,4}\s+(.+)$""")
        private val H1_REGEX = Regex("""^#\s+(.+)$""")
        private val YEAR_PARENS_REGEX = Regex("""\((\d{4})\)""")
        private val STANDALONE_YEAR_REGEX = Regex("""\b(19\d{2}|20\d{2})\b""")
        private val RATING_SLASH_REGEX = Regex("""\*{0,2}(\d+\.?\d*)\*{0,2}\s*/\s*10""")
        private val RATING_LABEL_REGEX = Regex("""[Rr]ating[:\s]+(\d+\.?\d*)""")
        private val VOTE_COUNT_REGEX = Regex("""([\d,.]+\s*[KkMm]?)\s*(?:[Vv]otes|[Rr]atings)""")
        private val RUNTIME_HM_REGEX = Regex("""(\d+)\s*h\s*(\d+)\s*m(?:in)?""")
        private val RUNTIME_MIN_REGEX = Regex("""(\d+)\s*min(?:utes?)?""")
        private val CERT_REGEX = Regex("""\b(G|PG|PG-13|R|NC-17|NR|UR|TV-Y|TV-G|TV-PG|TV-14|TV-MA)\b""")
        private val ISO_DATE_REGEX = Regex("""\b(\d{4}-\d{2}-\d{2})\b""")
        private val WORDY_DATE_REGEX = Regex("""(January|February|March|April|May|June|July|August|September|October|November|December)\s+(\d{1,2}),?\s+(\d{4})""", RegexOption.IGNORE_CASE)
        private val IMDB_ID_REGEX = Regex("""/title/(tt\d+)""")
        private val KEYWORD_BAR_REGEX = Regex("""(?:(?:[a-z][a-z ]+)(?:\s*[·,|]\s*[a-z][a-z ]+){2,})""")

        private val MONTH_MAP = mapOf(
            "january" to 1, "february" to 2, "march" to 3, "april" to 4,
            "may" to 5, "june" to 6, "july" to 7, "august" to 8,
            "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        )

        // Section heading synonyms
        private val CAST_KEYS = listOf("top cast", "cast", "actors", "starring")
        private val DIRECTOR_KEYS = listOf("director", "directors", "directed by")
        private val WRITER_KEYS = listOf("writer", "writers", "written by", "screenplay by")
        private val PLOT_KEYS = listOf("plot", "summary", "plot summary")
        private val STORYLINE_KEYS = listOf("storyline", "synopsis")
        private val RELEASE_KEYS = listOf("release date", "released", "release")
        private val COUNTRIES_KEYS = listOf("countries of origin", "country", "country of origin")
        private val LANGUAGE_KEYS = listOf("language", "languages", "spoken languages")
        private val KEYWORD_KEYS = listOf("keywords", "plot keywords", "tags")

        private val KNOWN_GENRES = listOf(
            "Action", "Adventure", "Animation", "Biography", "Comedy", "Crime",
            "Documentary", "Drama", "Family", "Fantasy", "Film-Noir", "History",
            "Horror", "Music", "Musical", "Mystery", "Romance", "Sci-Fi",
            "Sport", "Thriller", "War", "Western",
        )
    }
}
