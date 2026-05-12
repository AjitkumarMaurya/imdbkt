package io.github.ajitkumarmaurya.imdbkt.cache

/**
 * Generic read-through cache contract.  Both keys and values are strings
 * (values are serialized JSON) so the cache layer stays decoupled from models.
 */
interface Cache {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun clear()
}
