package io.github.ajitkumarmaurya.imdbkt.sample.ui.detail

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
            _uiState.value = when (val result = imdb.getTitle(imdbId)) {
                is ImdbResult.Success -> DetailUiState.Success(result.data)
                is ImdbResult.Error -> DetailUiState.Error(result.message)
                ImdbResult.Empty -> DetailUiState.Error("No data found for $imdbId")
            }
        }
    }
}
