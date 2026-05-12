package io.github.ajitkumarmaurya.imdbkt.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImdbResultTest {

    @Test
    fun `Success isSuccess returns true`() {
        val result = ImdbResult.Success("data")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.isError).isFalse()
    }

    @Test
    fun `Error isError returns true`() {
        val result = ImdbResult.Error("oops")
        assertThat(result.isError).isTrue()
        assertThat(result.isSuccess).isFalse()
    }

    @Test
    fun `getOrNull returns data on Success`() {
        val result = ImdbResult.Success(42)
        assertThat(result.getOrNull()).isEqualTo(42)
    }

    @Test
    fun `getOrNull returns null on Error`() {
        val result = ImdbResult.Error("fail")
        assertThat(result.getOrNull()).isNull()
    }

    @Test
    fun `map transforms Success value`() {
        val result = ImdbResult.Success(5)
        val mapped = result.map { it * 2 }
        assertThat((mapped as ImdbResult.Success).data).isEqualTo(10)
    }

    @Test
    fun `map preserves Error`() {
        val error = ImdbResult.Error("error")
        val mapped = error.map { 42 }
        assertThat(mapped).isInstanceOf(ImdbResult.Error::class.java)
    }

    @Test
    fun `getOrThrow throws ImdbException on Error`() {
        val result = ImdbResult.Error("bad")
        try {
            result.getOrThrow()
            error("should have thrown")
        } catch (e: ImdbException) {
            assertThat(e.message).isEqualTo("bad")
        }
    }

    @Test
    fun `onSuccess callback invoked for Success`() {
        var called = false
        ImdbResult.Success("x").onSuccess { called = true }
        assertThat(called).isTrue()
    }

    @Test
    fun `onSuccess callback not invoked for Error`() {
        var called = false
        ImdbResult.Error("x").onSuccess { called = true }
        assertThat(called).isFalse()
    }

    @Test
    fun `TitleType from handles known values`() {
        assertThat(TitleType.from("movie")).isEqualTo(TitleType.MOVIE)
        assertThat(TitleType.from("tvseries")).isEqualTo(TitleType.TV_SERIES)
        assertThat(TitleType.from(null)).isEqualTo(TitleType.UNKNOWN)
        assertThat(TitleType.from("garbage")).isEqualTo(TitleType.UNKNOWN)
    }
}
