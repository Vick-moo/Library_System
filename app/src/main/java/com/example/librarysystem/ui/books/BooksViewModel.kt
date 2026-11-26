package com.example.librarysystem.ui.books

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
