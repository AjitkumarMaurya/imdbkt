package io.github.ajitkumarmaurya.imdbkt.sample.ui.detail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ajitkumarmaurya.imdbkt.Imdb
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.ImdbTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DetailUiState {
    object Loading : DetailUiState
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
            Log.d(TAG, "Loading title: $imdbId")
            val newState = when (val result = imdb.getTitle(imdbId)) {
                is ImdbResult.Success -> {
                    Log.d(TAG, "Success: title='${result.data.title}' year=${result.data.year}")
                    if (result.data.title.isBlank()) {
                        DetailUiState.Error("Could not load data for $imdbId — IMDb may have blocked the request")
                    } else {
                        DetailUiState.Success(result.data)
                    }
                }
                is ImdbResult.Error -> {
                    Log.e(TAG, "Error loading $imdbId: ${result.message}", result.cause)
                    DetailUiState.Error(result.message)
                }
                ImdbResult.Empty -> {
                    Log.w(TAG, "Empty result for $imdbId")
                    DetailUiState.Error("No data found for $imdbId")
                }
            }
            _uiState.value = newState
        }
    }

    companion object {
        private const val TAG = "DetailViewModel"
    }
}
