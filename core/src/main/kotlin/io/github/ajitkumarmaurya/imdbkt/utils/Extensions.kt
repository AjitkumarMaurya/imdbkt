package io.github.ajitkumarmaurya.imdbkt.utils

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal fun JsonElement?.string(key: String): String? =
    (this as? JsonObject)?.get(key)?.asString()

internal fun JsonElement?.int(key: String): Int? =
    (this as? JsonObject)?.get(key)?.asInt()

internal fun JsonElement?.long(key: String): Long? =
    (this as? JsonObject)?.get(key)?.asLong()

internal fun JsonElement?.float(key: String): Float? =
    (this as? JsonObject)?.get(key)?.asFloat()

internal fun JsonElement?.obj(key: String): JsonObject? =
    (this as? JsonObject)?.get(key) as? JsonObject

internal fun JsonElement?.arr(key: String): JsonArray? =
    (this as? JsonObject)?.get(key) as? JsonArray

internal fun JsonElement?.asString(): String? =
    (this as? JsonPrimitive)?.contentOrNull

internal fun JsonElement?.asInt(): Int? =
    (this as? JsonPrimitive)?.intOrNull

internal fun JsonElement?.asLong(): Long? =
    (this as? JsonPrimitive)?.longOrNull

internal fun JsonElement?.asFloat(): Float? =
    (this as? JsonPrimitive)?.floatOrNull

internal fun JsonElement?.asDouble(): Double? =
    (this as? JsonPrimitive)?.doubleOrNull

internal fun JsonElement?.isNull(): Boolean = this == null || this is JsonNull

/** Safely navigate a dotted path like "props.pageProps.aboveTheFoldData". */
internal fun JsonElement?.path(vararg keys: String): JsonElement? {
    var current: JsonElement? = this
    for (key in keys) {
        current = (current as? JsonObject)?.get(key)
        if (current == null) return null
    }
    return current
}

/** Navigate a dot-separated path string without a spread operator. */
internal fun JsonElement?.pathDot(dotPath: String): JsonElement? {
    var current: JsonElement? = this
    for (key in dotPath.split(".")) {
        current = (current as? JsonObject)?.get(key)
        if (current == null) return null
    }
    return current
}

/** Collect string values from a JSON array via a key inside each element. */
internal fun JsonArray?.strings(key: String): List<String> =
    this?.mapNotNull { it.string(key) } ?: emptyList()

/** Trim and return null for blank strings. */
internal fun String?.blankAsNull(): String? = this?.trim()?.takeIf { it.isNotBlank() }

/** Convert IMDb runtime seconds to minutes. */
internal fun Int.secondsToMinutes(): Int = this / 60
