package com.example.librarysystem.ui.books

import com.example.librarysystem.data.keepSpinnerVisible

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.librarysystem.data.BookRepository
import com.example.librarysystem.data.ErrorText
import com.example.librarysystem.data.model.Book
import kotlinx.coroutines.launch

sealed interface BooksUiState {
    data object Loading : BooksUiState
    data class Ready(val books: List<Book>) : BooksUiState
    data class Error(val message: String) : BooksUiState
}

class BooksViewModel : ViewModel() {

    var state: BooksUiState by mutableStateOf(BooksUiState.Loading)
        private set

    var query by mutableStateOf("")
        private set

    var ratings by mutableStateOf(mapOf<String, Pair<Double, Int>>())
        private set

    var availableIds by mutableStateOf(setOf<String>())
        private set

    /** Book ids in ranking order, refreshed when the Home filter picks a ranking. */
    var rankingIds by mutableStateOf<List<String>>(emptyList())
        private set

    var refreshing by mutableStateOf(false)
        private set

    /** Re-reads the list without switching to the loading state, for pull-to-refresh. */
    fun refresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            val startedRefresh = System.currentTimeMillis()
            try {
                val books = if (query.isBlank()) {
                    BookRepository.listBooks()
                } else {
                    BookRepository.searchBooks(query)
                }
                ratings = try { BookRepository.ratingsByBook() } catch (_: Exception) { emptyMap() }
                availableIds = try { BookRepository.availableBookIds() } catch (_: Exception) { emptySet() }
                state = BooksUiState.Ready(books)
            } catch (e: Exception) {
                // Keep whatever is on screen; a failed pull must not blank the list.
            } finally {
                keepSpinnerVisible(startedRefresh)
                refreshing = false
            }
        }
    }

    fun loadRanking(ranking: String) {
        if (ranking == "None") {
            rankingIds = emptyList()
            return
        }
        viewModelScope.launch {
            rankingIds = try {
                val metric = when (ranking) {
                    "Highest Rated" -> "rated"
                    "Most Borrowed" -> "borrowed"
                    else -> "reviewed"
                }
                BookRepository.trendingBooks(metric = metric).map { it.id }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    init {
        load()
    }

    fun onQueryChange(value: String) {
        query = value
    }

    fun load() {
        viewModelScope.launch {
            state = BooksUiState.Loading
            state = try {
                val books = if (query.isBlank()) {
                    BookRepository.listBooks()
                } else {
                    BookRepository.searchBooks(query)
                }
                ratings = try { BookRepository.ratingsByBook() } catch (_: Exception) {
                    emptyMap()
                }
                availableIds = try { BookRepository.availableBookIds() } catch (_: Exception) {
                    emptySet()
                }
                BooksUiState.Ready(books)
            } catch (e: Exception) {
                BooksUiState.Error(ErrorText.loadError(e))
            }
        }
    }
}
