package com.example.librarysystem.ui.manage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.librarysystem.data.BookRepository
import com.example.librarysystem.data.model.BookCopy
import kotlinx.coroutines.launch

class CopiesViewModel(private val bookId: String) : ViewModel() {

    var copies by mutableStateOf<List<BookCopy>>(emptyList())
        private set

    var loading by mutableStateOf(true)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var notice by mutableStateOf<String?>(null)
        private set

    private var isbn: String = ""

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val book = BookRepository.bookById(bookId)
                isbn = book?.isbn.orEmpty()
                copies = BookRepository.copiesForStaff(bookId)
            } catch (e: Exception) {
                error = "Could not load copies."
            } finally {
                loading = false
            }
        }
    }

    /** Next copy number like ISBN-03 based on the highest existing suffix. */
    fun nextCopyNo(): String {
        val max = copies.mapNotNull { c ->
            Regex("-(\\d+)$").find(c.copyNo)?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull() ?: 0
        return "%s-%02d".format(isbn.ifBlank { "COPY" }, max + 1)
    }

    fun save(
        existing: BookCopy?,
        copyNo: String,
        condition: String,
        status: String,
        shelf: String,
        notes: String,
        onDone: () -> Unit
    ) {
        if (shelf.isBlank()) {
            notice = "Shelf location is required."
            return
        }
        viewModelScope.launch {
            try {
                if (existing == null) {
                    BookRepository.createCopy(
                        bookId = bookId,
                        copyNo = copyNo.trim().ifBlank { nextCopyNo() },
                        condition = condition,
                        status = status,
                        shelfLocation = shelf.trim(),
                        notes = notes
                    )
                    notice = "Copy added."
                } else {
                    BookRepository.updateCopy(
                        copyId = existing.id,
                        condition = condition,
                        status = status,
                        shelfLocation = shelf.trim(),
                        notes = notes
                    )
                    notice = "Copy updated."
                }
                onDone()
                load()
            } catch (e: Exception) {
                notice = friendly(e)
            }
        }
    }

    fun delete(copy: BookCopy, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                BookRepository.softDeleteCopy(copy.id)
                notice = "Copy removed."
                onDone()
                load()
            } catch (e: Exception) {
                notice = friendly(e)
            }
        }
    }

    private fun friendly(e: Exception): String {
        val m = (e.message ?: "").lowercase()
        return when {
            "duplicate key" in m -> "That copy number already exists."
            "row-level security" in m || "permission denied" in m ->
                "You are not allowed to do that."
            "network" in m || "timeout" in m -> "Network error. Try again."
            else -> "Could not save. Try again."
        }
    }
}
