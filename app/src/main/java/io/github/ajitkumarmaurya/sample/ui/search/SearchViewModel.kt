package io.github.ajitkumarmaurya.imdbkt.sample.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ajitkumarmaurya.imdbkt.Imdb
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.ImdbSearchItem
import io.github.ajitkumarmaurya.imdbkt.model.TrendingType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchUiState {
    object Idle : SearchUiState
    object Loading : SearchUiState
    data class Results(val items: List<ImdbSearchItem>) : SearchUiState
    data class Error(val message: String) : SearchUiState
    object Empty : SearchUiState
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val imdb: Imdb,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _trending = MutableStateFlow<List<ImdbSearchItem>>(emptyList())
    val trending: StateFlow<List<ImdbSearchItem>> = _trending.asStateFlow()

    init {
        observeQuery()
        loadTrending()
    }

    fun onQueryChange(value: String) {
        _query.value = value
        if (value.isBlank()) _uiState.value = SearchUiState.Idle
    }

    fun clearQuery() {
        _query.value = ""
        _uiState.value = SearchUiState.Idle
    }

    private fun observeQuery() {
        viewModelScope.launch {
            _query
                .debounce(400)
                .distinctUntilChanged()
                .filter { it.length >= 2 }
                .collectLatest { query ->
                    _uiState.value = SearchUiState.Loading
                    _uiState.value = when (val result = imdb.search(query)) {
                        is ImdbResult.Success -> {
                            if (result.data.isEmpty()) SearchUiState.Empty
                            else SearchUiState.Results(result.data)
                        }
                        is ImdbResult.Error -> SearchUiState.Error(result.message)
                        ImdbResult.Empty -> SearchUiState.Empty
                    }
                }
        }
    }

    private fun loadTrending() {
        viewModelScope.launch {
            when (val result = imdb.getTrending(TrendingType.MOVIES)) {
                is ImdbResult.Success -> _trending.value = result.data
                else -> Unit
            }
        }
    }
}
