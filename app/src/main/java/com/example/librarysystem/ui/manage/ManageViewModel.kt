package com.example.librarysystem.ui.manage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.librarysystem.data.BookRepository
import com.example.librarysystem.data.model.Book
import kotlinx.coroutines.launch

data class StaffBookRow(
    val book: Book,
    val totalCopies: Int,
    val availableCopies: Int
)

class ManageViewModel : ViewModel() {

    var books by mutableStateOf<List<StaffBookRow>>(emptyList())
        private set

    var loading by mutableStateOf(true)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var query by mutableStateOf("")
        private set

    var notice by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun onQueryChange(value: String) {
        query = value
    }

    fun filtered(): List<StaffBookRow> {
        val q = query.trim()
        if (q.isEmpty()) return books
        return books.filter {
            it.book.title.contains(q, ignoreCase = true) ||
                it.book.author.contains(q, ignoreCase = true) ||
                it.book.isbn.contains(q, ignoreCase = true)
        }
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val all = BookRepository.allBooksForStaff()
                val copies = try {
                    BookRepository.allCopiesForStaff()
                } catch (_: Exception) {
                    emptyList()
                }
                val byBook = copies.groupBy { it.bookId }
                books = all.map { b ->
                    val list = byBook[b.id].orEmpty()
                    StaffBookRow(
                        book = b,
                        totalCopies = list.size,
                        availableCopies = list.count { it.status == "available" }
                    )
                }
            } catch (e: Exception) {
                error = "Could not load the catalogue. Pull to refresh."
            } finally {
                loading = false
            }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            try {
                BookRepository.softDeleteBook(bookId)
                notice = "Book archived."
                load()
            } catch (e: Exception) {
                notice = "Could not archive this book. Try again."
            }
        }
    }
}
