package io.github.ajitkumarmaurya.imdbkt.sample.ui.detail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ajitkumarmaurya.imdbkt.Imdb
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.ImdbTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DetailUiState {
    object Loading : DetailUiState
    data class Retrying(val attempt: Int, val maxAttempts: Int) : DetailUiState
    data class Success(val title: ImdbTitle) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val imdb: Imdb,
) : ViewModel() {

    private val imdbId: String = checkNotNull(savedStateHandle["imdbId"])

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        loadTitle()
    }

    fun retry() = loadTitle()

    private fun loadTitle() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            var lastError = "Failed to load $imdbId"
            for (attempt in 1..MAX_ATTEMPTS) {
                if (attempt > 1) {
                    Log.d(TAG, "Retrying $imdbId — attempt $attempt of $MAX_ATTEMPTS")
                    _uiState.value = DetailUiState.Retrying(attempt, MAX_ATTEMPTS)
                    delay(RETRY_DELAY_MS)
                }
                when (val result = imdb.getTitle(imdbId)) {
                    is ImdbResult.Success -> {
                        if (result.data.title.isNotBlank()) {
                            Log.d(TAG, "Success on attempt $attempt: '${result.data.title}'")
                            _uiState.value = DetailUiState.Success(result.data)
                            return@launch
                        }
                        Log.w(TAG, "Blank title on attempt $attempt — IMDb may have blocked request")
                        lastError = "IMDb returned empty data"
                    }
                    is ImdbResult.Error -> {
                        Log.e(TAG, "Error on attempt $attempt: ${result.message}", result.cause)
                        lastError = result.message
                    }
                    ImdbResult.Empty -> {
                        Log.w(TAG, "Empty result on attempt $attempt")
                        lastError = "No data found for $imdbId"
                    }
                }
            }
            _uiState.value = DetailUiState.Error(lastError)
        }
    }

    companion object {
        private const val TAG = "DetailViewModel"
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2_000L
    }
}
