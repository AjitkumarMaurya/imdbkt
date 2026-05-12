package io.github.ajitkumarmaurya.imdbkt.model

/**
 * Sealed wrapper for all library responses. Callers pattern-match on Success/Error
 * rather than catching exceptions, keeping async code clean.
 */
sealed class ImdbResult<out T> {

    data class Success<out T>(val data: T) : ImdbResult<T>()

    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val type: ErrorType = ErrorType.UNKNOWN,
    ) : ImdbResult<Nothing>()

    object Empty : ImdbResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = if (this is Success) data else null

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw ImdbException(message, cause)
        is Empty -> throw ImdbException("No data available")
    }

    inline fun onSuccess(action: (T) -> Unit): ImdbResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (Error) -> Unit): ImdbResult<T> {
        if (this is Error) action(this)
        return this
    }

    inline fun <R> map(transform: (T) -> R): ImdbResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Empty -> Empty
    }
}

enum class ErrorType {
    NETWORK,
    PARSING,
    NOT_FOUND,
    RATE_LIMITED,
    UNKNOWN,
}

class ImdbException(message: String, cause: Throwable? = null) : Exception(message, cause)
