package com.example.librarysystem.ui.books

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.librarysystem.data.AuthRepository
import com.example.librarysystem.data.BookRepository
import com.example.librarysystem.data.model.Book
import com.example.librarysystem.data.model.BookReviewRow
import com.example.librarysystem.data.model.Reservation
import com.example.librarysystem.data.model.Review
import kotlinx.coroutines.launch

sealed interface BookDetailUiState {
    data object Loading : BookDetailUiState
    data class Ready(
        val book: Book,
        val availableCopies: Int,
        val totalCopies: Int,
        val reviews: List<BookReviewRow>,
        val myReservation: Reservation?,
        val queuePosition: Int?,
        val myReview: Review?,
        /** Estimated pickup window (start, end) shown before reserving. */
        val pickupWindow: Pair<String, String>?,
        /** False when the student already has 2 books borrowed + reserved. */
        val canReserve: Boolean,
        /** Borrowed + reserved count (max 2). */
        val activeBooks: Int,
        /** Shelf codes of the available copies (students can find the book). */
        val shelves: List<String>
    ) : BookDetailUiState
    data class Error(val message: String) : BookDetailUiState
}

class BookDetailViewModel(private val bookId: String) : ViewModel() {

    var state: BookDetailUiState by mutableStateOf(BookDetailUiState.Loading)
        private set

    var studentNo: String? by mutableStateOf(null)
        private set

    /** One-line feedback for reserve / review actions. */
    var notice: String? by mutableStateOf(null)
        private set

    var actionLoading by mutableStateOf(false)
        private set

    /** Pull-to-refresh state. */
    var refreshing by mutableStateOf(false)
        private set

    /** Review ids reported during this session (RLS hides reports from students). */
    var reportedIds by mutableStateOf(setOf<String>())
        private set

    init {
        load()
    }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        load(silent = true)
    }

    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) state = BookDetailUiState.Loading
            try {
                val book = BookRepository.bookById(bookId)
                if (book == null) {
                    state = BookDetailUiState.Error("This book is no longer available.")
                    return@launch
                }
                val copies = try { BookRepository.copiesOf(bookId) } catch (_: Exception) { emptyList() }
                val rawReviews = try { BookRepository.bookReviews(bookId) } catch (_: Exception) { emptyList() }
                // My own review always sits first, then newest first.
                val reviews = rawReviews.sortedWith(
                    compareByDescending<BookReviewRow> { it.isMine }
                        .thenByDescending { it.createdAt ?: "" }
                )
                studentNo = try { AuthRepository.myStudentNo() } catch (_: Exception) { null }
                val me = studentNo
                val reservation = try {
                    me?.let { BookRepository.myActiveReservation(bookId, it) }
                } catch (_: Exception) {
                    null
                }
                val queue = try {
                    reservation?.let { BookRepository.queuePosition(it.id) }
                } catch (_: Exception) {
                    null
                }
                val myReview = try {
                    me?.let { BookRepository.myReviewOf(bookId, it) }
                } catch (_: Exception) {
                    null
                }
                val window = if (reservation == null) {
                    try { BookRepository.estimatedPickupWindow(bookId) } catch (_: Exception) { null }
                } else {
                    null
                }
                val loans = if (me != null) {
                    try { BookRepository.activeLoanCount(me) } catch (_: Exception) { 0 }
                } else 0
                val activeRes = if (me != null) {
                    try { BookRepository.activeReservationCount(me) } catch (_: Exception) { 0 }
                } else 0
                val activeBooks = loans + activeRes
                val shelves = copies
                    .filter { it.status == "available" }
                    .mapNotNull { it.shelfLocation }
                    .distinct()
                    .sorted()
                state = BookDetailUiState.Ready(
                    book = book,
                    availableCopies = copies.count { it.status == "available" },
                    totalCopies = copies.size,
                    reviews = reviews,
                    myReservation = reservation,
                    queuePosition = queue,
                    myReview = myReview,
                    pickupWindow = window,
                    canReserve = activeBooks < 2,
                    activeBooks = activeBooks,
                    shelves = shelves
                )
            } catch (e: Exception) {
                if (!silent) {
                    state = BookDetailUiState.Error("Could not load this book. Go back and try again.")
                }
            } finally {
                refreshing = false
            }
        }
    }

    fun reserve(note: String) {
        val me = studentNo ?: run {
            notice = "Please login first."
            return
        }
        val ready = state as? BookDetailUiState.Ready ?: return
        if (ready.myReservation != null) {
            notice = "You already reserved this book."
            return
        }
        actionLoading = true
        notice = null
        viewModelScope.launch {
            try {
                val window = ready.pickupWindow
                    ?: runCatching { BookRepository.estimatedPickupWindow(bookId) }.getOrNull()
                BookRepository.reserve(
                    studentNo = me,
                    bookId = bookId,
                    note = note,
                    pickupStartDate = window?.first,
                    pickupEndDate = window?.second
                )
                notice = "Reserved. We will tell you when a copy is ready."
                load(silent = true)
            } catch (e: Exception) {
                notice = friendly(e, "Could not reserve this book. Try again.")
            } finally {
                actionLoading = false
            }
        }
    }

    fun cancelReservation() {
        val ready = state as? BookDetailUiState.Ready ?: return
        val res = ready.myReservation ?: return
        actionLoading = true
        notice = null
        viewModelScope.launch {
            try {
                BookRepository.cancelReservation(res.id)
                notice = "Reservation cancelled."
                load(silent = true)
            } catch (e: Exception) {
                notice = friendly(e, "Could not cancel. Try again.")
            } finally {
                actionLoading = false
            }
        }
    }

    /**
     * One review per student per book (SYSTEM-SPEC M4).
     * No existing review -> insert. Existing review -> update it.
     */
    fun submitReview(rating: Int, comment: String, onDone: () -> Unit) {
        val me = studentNo ?: run {
            notice = "Please login first."
            return
        }
        val ready = state as? BookDetailUiState.Ready ?: return
        val existing = ready.myReview
        actionLoading = true
        notice = null
        viewModelScope.launch {
            try {
                if (existing == null) {
                    BookRepository.addReview(me, bookId, rating, comment)
                    notice = "Review posted. Thanks!"
                } else {
                    BookRepository.updateReview(existing.id, rating, comment)
                    notice = "Review updated."
                }
                onDone()
                load(silent = true)
            } catch (e: Exception) {
                notice = friendly(e, "Could not save your review. Try again.")
            } finally {
                actionLoading = false
            }
        }
    }

    fun deleteReview() {
        val ready = state as? BookDetailUiState.Ready ?: return
        val existing = ready.myReview ?: return
        actionLoading = true
        notice = null
        viewModelScope.launch {
            try {
                BookRepository.deleteReview(existing.id)
                notice = "Review deleted."
                load(silent = true)
            } catch (e: Exception) {
                notice = friendly(e, "Could not delete your review. Try again.")
            } finally {
                actionLoading = false
            }
        }
    }

    fun reportReview(reviewId: String) {
        val me = studentNo ?: run {
            notice = "Please login first."
            return
        }
        actionLoading = true
        notice = null
        viewModelScope.launch {
            try {
                BookRepository.reportReview(me, reviewId, "Reported from book details")
                reportedIds = reportedIds + reviewId
                notice = "Reported. Staff will review this comment."
            } catch (e: Exception) {
                notice = friendly(e, "Could not report this comment. Try again.")
            } finally {
                actionLoading = false
            }
        }
    }

    /** Never show raw SDK errors to the user. */
    private fun friendly(e: Exception, fallback: String): String {
        val m = (e.message ?: "").lowercase()
        return when {
            "borrow_limit_reached" in m || "limit" in m ->
                "You already have 2 books borrowed or reserved. Return one first."
            "copies_available_borrow_at_counter" in m ->
                "This book is on the shelf. Borrow it at the counter."
            "duplicate key" in m || "unique" in m -> "You already did that."
            "row-level security" in m || "permission denied" in m ->
                "You are not allowed to do that."
            "network" in m || "timeout" in m || "unable to resolve host" in m ->
                "Network error. Check your connection and try again."
            else -> fallback
        }
    }
}
