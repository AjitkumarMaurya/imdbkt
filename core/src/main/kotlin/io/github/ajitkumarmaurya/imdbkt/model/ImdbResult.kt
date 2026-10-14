package io.github.ajitkumarmaurya.imdbkt.model

sealed interface ImdbError {
    val message: String
    val cause: Throwable?

    data class NetworkError(
        override val message: String,
        val statusCode: Int? = null,
        override val cause: Throwable? = null
    ) : ImdbError

    data class RateLimited(
        override val message: String = "Rate limited by IMDb/WAF",
        val retryAfterMs: Long? = null,
        override val cause: Throwable? = null
    ) : ImdbError

    data class NotFound(
        val idOrQuery: String,
        override val message: String = "Resource not found: $idOrQuery",
        override val cause: Throwable? = null
    ) : ImdbError

    data class ParsingError(
        val stage: String,
        override val message: String,
        override val cause: Throwable? = null
    ) : ImdbError
}

sealed class ImdbResult<out T> {
    data class Success<T>(val data: T) : ImdbResult<T>()
    data class Failure(val error: ImdbError) : ImdbResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): ImdbError? = (this as? Failure)?.error

    inline fun <R> map(transform: (T) -> R): ImdbResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    inline fun onSuccess(action: (T) -> Unit): ImdbResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onFailure(action: (ImdbError) -> Unit): ImdbResult<T> {
        if (this is Failure) action(error)
        return this
    }
}
