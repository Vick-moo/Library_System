package com.example.librarysystem.ui.manage

import com.example.librarysystem.data.ErrorText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.example.librarysystem.data.BookRepository
import kotlinx.coroutines.launch

class BookFormViewModel(private val bookId: String?) : ViewModel() {

    val isEdit: Boolean = bookId != null

    var isbn by mutableStateOf("")
        private set
    var title by mutableStateOf("")
        private set
    var author by mutableStateOf("")
        private set
    var publisher by mutableStateOf("")
        private set
    var publishYear by mutableStateOf("")
        private set
    var category by mutableStateOf("")
        private set
    var language by mutableStateOf("")
        private set
    var pages by mutableStateOf("")
        private set
    var price by mutableStateOf("")
        private set
    var description by mutableStateOf("")
        private set

    /** Only for new books: how many copies to create right away. */
    var copyCount by mutableStateOf("1")
        private set
    var shelfLocation by mutableStateOf("")
        private set

    var loading by mutableStateOf(bookId != null)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** True once the user edits any field (used for the discard confirmation). */
    var dirty by mutableStateOf(false)
        private set

    /** Newly picked cover image (not uploaded yet). */
    var coverUri by mutableStateOf<Uri?>(null)
        private set

    /** Cover URL already stored on the book. */
    var existingCoverUrl by mutableStateOf<String?>(null)
        private set

    init {
        if (bookId != null) load()
    }

    fun update(field: String, value: String) {
        when (field) {
            "isbn" -> isbn = value
            "title" -> title = value
            "author" -> author = value
            "publisher" -> publisher = value
            "year" -> publishYear = value.filter { it.isDigit() }
            "category" -> category = value
            "language" -> language = value
            "pages" -> pages = value.filter { it.isDigit() }
            "price" -> price = value.filter { it.isDigit() || it == '.' }
            "description" -> description = value
            "copies" -> copyCount = value.filter { it.isDigit() }
            "shelf" -> shelfLocation = value
        }
        dirty = true
        error = null
    }

    fun setCover(uri: Uri?) {
        coverUri = uri
        dirty = true
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val b = BookRepository.bookById(bookId!!)
                if (b != null) {
                    isbn = b.isbn
                    title = b.title
                    author = b.author
                    publisher = b.publisher.orEmpty()
                    publishYear = b.publishYear?.toString().orEmpty()
                    category = b.category.orEmpty()
                    language = b.language.orEmpty()
                    pages = b.pages?.toString().orEmpty()
                    price = b.price?.toString().orEmpty()
                    description = b.description.orEmpty()
                    existingCoverUrl = b.coverUrl
                }
            } catch (_: Exception) {
                error = "Could not load this book."
            } finally {
                loading = false
            }
        }
    }

    fun save(coverBytes: ByteArray?, coverExt: String, onSaved: () -> Unit) {
        val validation = validate()
        if (validation != null) {
            error = validation
            return
        }
        saving = true
        error = null
        viewModelScope.launch {
            try {
                val year = publishYear.toInt()
                val pageCount = pages.toInt()
                val bookPrice = price.toDouble()
                var targetBookId: String? = bookId
                if (bookId == null) {
                    val created = BookRepository.createBook(
                        isbn = isbn.trim(),
                        title = title.trim(),
                        author = author.trim(),
                        publisher = publisher.trim(),
                        publishYear = year,
                        category = category.trim(),
                        language = language.trim(),
                        pages = pageCount,
                        price = bookPrice,
                        description = description.trim()
                    )
                    targetBookId = created.id
                    val count = copyCount.toIntOrNull() ?: 1
                    repeat(count) { index ->
                        BookRepository.createCopy(
                            bookId = created.id,
                            copyNo = "%s-%02d".format(isbn.trim(), index + 1),
                            condition = "good",
                            status = "available",
                            shelfLocation = shelfLocation.trim(),
                            notes = null
                        )
                    }
                } else {
                    BookRepository.updateBook(
                        bookId = bookId,
                        title = title.trim(),
                        author = author.trim(),
                        publisher = publisher.trim(),
                        publishYear = year,
                        category = category.trim(),
                        language = language.trim(),
                        pages = pageCount,
                        price = bookPrice,
                        description = description.trim()
                    )
                }
                if (coverBytes != null && targetBookId != null) {
                    val url = BookRepository.uploadCover(isbn.trim(), coverBytes, coverExt)
                    BookRepository.updateBookCover(targetBookId, url)
                }
                onSaved()
            } catch (e: Exception) {
                error = friendly(e)
            } finally {
                saving = false
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = bookId ?: return
        saving = true
        viewModelScope.launch {
            try {
                BookRepository.softDeleteBook(id)
                onDeleted()
            } catch (e: Exception) {
                error = friendly(e)
            } finally {
                saving = false
            }
        }
    }

    private fun validate(): String? {
        val isbnText = isbn.trim()
        if (isbnText.isBlank()) return "ISBN is required"
        if (isbnText.length !in 10..20) return "ISBN must be 10–20 characters"
        if (title.isBlank()) return "Title is required"
        if (author.isBlank()) return "Author is required"
        if (publisher.isBlank()) return "Publisher is required"
        val year = publishYear.toIntOrNull()
        if (year == null || year !in 1000..2100) return "Publish year must be between 1000 and 2100"
        if (category.isBlank()) return "Category is required"
        if (language.isBlank()) return "Language is required"
        val pageCount = pages.toIntOrNull()
        if (pageCount == null || pageCount !in 1..20000) return "Pages must be between 1 and 20000"
        val priceValue = price.toDoubleOrNull()
        if (priceValue == null || priceValue <= 0 || priceValue > 100000) {
            return "Price must be between 0.01 and 100000"
        }
        if (description.isBlank()) return "Description is required"
        if (coverUri == null && existingCoverUrl.isNullOrBlank()) return "Cover image is required"
        if (bookId == null) {
            if ((copyCount.toIntOrNull() ?: 0) !in 1..50) return "Copy count must be between 1 and 50"
            if (shelfLocation.isBlank()) return "Shelf location is required for new copies"
        }
        return null
    }

    private fun friendly(e: Exception): String {
        val m = (e.message ?: "").lowercase()
        return when {
            "duplicate key" in m && "isbn" in m -> "This ISBN already exists."
            "duplicate key" in m -> "That value already exists."
            "row-level security" in m || "permission denied" in m ->
                "You are not allowed to do that."
            ErrorText.isNetwork(e) -> ErrorText.OFFLINE
            else -> "Could not save. Check the fields and try again."
        }
    }
}
