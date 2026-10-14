package io.github.ajitkumarmaurya.imdbkt.utils

private val ISO_DURATION_REGEX = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?""")
private val TT_ID_REGEX = Regex("""(tt\d{7,10})""")
private val NM_ID_REGEX = Regex("""(nm\d{7,10})""")
private val YEAR_REGEX = Regex("""\b(19\d{2}|20\d{2})\b""")

fun String.parseIsoDurationMinutes(): Int? {
    val match = ISO_DURATION_REGEX.find(this) ?: return null
    val hours = match.groupValues[1].toIntOrNull() ?: 0
    val minutes = match.groupValues[2].toIntOrNull() ?: 0
    val total = hours * 60 + minutes
    return if (total > 0) total else null
}

fun String.extractTitleId(): String? = TT_ID_REGEX.find(this)?.value
fun String.extractNameId(): String? = NM_ID_REGEX.find(this)?.value
fun String.extractYear(): Int? = YEAR_REGEX.find(this)?.value?.toIntOrNull()
