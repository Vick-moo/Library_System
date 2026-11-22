package com.example.librarysystem.data

import com.example.librarysystem.data.model.Book
import com.example.librarysystem.data.model.BookCopy
import com.example.librarysystem.data.model.BookReviewRow
import com.example.librarysystem.data.model.Reservation
import com.example.librarysystem.data.model.Review
import com.example.librarysystem.data.model.TrendingBook
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class ReviewScore(
    @SerialName("book_id") val bookId: String,
    val rating: Int
)

@Serializable
private data class AvailableCopy(
    @SerialName("book_id") val bookId: String
)

@Serializable
private data class NewReview(
    @SerialName("student_no") val studentNo: String,
    @SerialName("book_id") val bookId: String,
    val rating: Int,
    val comment: String? = null
)

@Serializable
private data class NewReservation(
    @SerialName("student_no") val studentNo: String,
    @SerialName("book_id") val bookId: String,
    val status: String = "waiting",
    val note: String? = null,
    @SerialName("pickup_start_date") val pickupStartDate: String? = null,
    @SerialName("pickup_end_date") val pickupEndDate: String? = null
)

@Serializable
private data class DueDateRow(@SerialName("due_date") val dueDate: String)

@Serializable
private data class TrendingParams(
    @SerialName("p_metric") val metric: String,
    @SerialName("p_days") val days: Int? = null,
    @SerialName("p_category") val category: String? = null,
    @SerialName("p_language") val language: String? = null
)

@Serializable
private data class NewBook(
    val isbn: String,
    val title: String,
    val author: String,
    val publisher: String,
    @SerialName("publish_year") val publishYear: Int,
    val category: String,
    val language: String,
    val pages: Int,
    val price: Double,
    val description: String
)

@Serializable
private data class BookUpdate(
    val title: String,
    val author: String,
    val publisher: String,
    @SerialName("publish_year") val publishYear: Int,
    val category: String,
    val language: String,
    val pages: Int,
    val price: Double,
    val description: String
)

@Serializable
private data class NewCopy(
    @SerialName("book_id") val bookId: String,
    @SerialName("copy_no") val copyNo: String,
    val condition: String,
    val status: String,
    @SerialName("shelf_location") val shelfLocation: String,
    val notes: String? = null
)

@Serializable
private data class CopyUpdate(
    val condition: String,
    val status: String,
    @SerialName("shelf_location") val shelfLocation: String,
    val notes: String? = null
)

@Serializable
private data class DeletedFlag(@SerialName("is_deleted") val isDeleted: Boolean)

@Serializable
private data class CoverUpdate(@SerialName("cover_url") val coverUrl: String)

@Serializable
private data class BorrowIdRow(@SerialName("borrow_id") val id: String)

@Serializable
private data class ReservationIdRow(@SerialName("reservation_id") val id: String)

@Serializable
private data class NewReport(
    @SerialName("review_id") val reviewId: String,
    @SerialName("student_no") val studentNo: String,
    val reason: String? = null
)

@Serializable
private data class ReservationStatus(val status: String)

@Serializable
private data class ReviewUpdate(
    val rating: Int,
    val comment: String? = null
)

object BookRepository {

    suspend fun listBooks(): List<Book> =
        supabase.from("book")
            .select {
                filter { eq("is_deleted", false) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<Book>()

    suspend fun searchBooks(query: String): List<Book> {
        val q = query.trim()
        if (q.isEmpty()) return listBooks()
        return supabase.from("book")
            .select {
                filter {
                    eq("is_deleted", false)
                    // title 模糊；author / isbn 精确
                    or {
                        ilike("title", "%$q%")
                        eq("author", q)
                        eq("isbn", q)
                    }
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<Book>()
    }

    /** Student-facing copy list: public view without staff notes. */
    suspend fun copiesOf(bookId: String): List<BookCopy> =
        supabase.from("book_copy_public")
            .select {
                filter { eq("book_id", bookId) }
            }
            .decodeList<BookCopy>()

    /** book_id -> (avg rating, review count), only published reviews */
    suspend fun ratingsByBook(): Map<String, Pair<Double, Int>> =
        supabase.from("review")
            .select(columns = Columns.list("book_id", "rating")) {
                filter { eq("status", "published") }
            }
            .decodeList<ReviewScore>()
            .groupBy({ it.bookId }, { it.rating })
            .mapValues { (_, rs) -> Pair(rs.average(), rs.size) }

    /** book_ids that have at least one available copy */
    suspend fun availableBookIds(): Set<String> =
        supabase.from("book_copy")
            .select(columns = Columns.list("book_id")) {
                filter {
                    eq("status", "available")
                    eq("is_deleted", false)
                }
            }
            .decodeList<AvailableCopy>()
            .map { it.bookId }
            .toSet()

    // ---- book detail ----

    suspend fun bookById(bookId: String): Book? =
        supabase.from("book")
            .select {
                filter {
                    eq("book_id", bookId)
                    eq("is_deleted", false)
                }
            }
            .decodeSingleOrNull<Book>()

    suspend fun publishedReviews(bookId: String): List<Review> =
        supabase.from("review")
            .select {
                filter {
                    eq("book_id", bookId)
                    eq("status", "published")
                }
                order("created_at", Order.DESCENDING)
                limit(30)
            }
            .decodeList<Review>()

    /** Published reviews with the reviewer's display name (RPC bypasses account RLS safely). */
    suspend fun bookReviews(bookId: String): List<BookReviewRow> =
        supabase.postgrest.rpc(
            "book_reviews",
            mapOf("p_book_id" to bookId)
        ).decodeList<BookReviewRow>()

    /** The current student's own review for this book, in any status (null if never reviewed). */
    suspend fun myReviewOf(bookId: String, studentNo: String): Review? =
        supabase.from("review")
            .select {
                filter {
                    eq("book_id", bookId)
                    eq("student_no", studentNo)
                }
                limit(1)
            }
            .decodeSingleOrNull<Review>()

    suspend fun addReview(studentNo: String, bookId: String, rating: Int, comment: String?) {
        supabase.from("review").insert(
            NewReview(
                studentNo = studentNo,
                bookId = bookId,
                rating = rating.coerceIn(1, 5),
                comment = comment?.trim()?.ifBlank { null }
            )
        )
    }

    suspend fun updateReview(reviewId: String, rating: Int, comment: String?) {
        supabase.from("review").update(
            ReviewUpdate(
                rating = rating.coerceIn(1, 5),
                comment = comment?.trim()?.ifBlank { null }
            )
        ) {
            filter { eq("review_id", reviewId) }
        }
    }

    suspend fun deleteReview(reviewId: String) {
        supabase.from("review").delete {
            filter { eq("review_id", reviewId) }
        }
    }

    // ---- trends ----

    /**
     * Ranking data. p_metric: rated | borrowed | reviewed.
     * p_days: null = all time, 7 = this week, 30 = this month.
     */
    suspend fun trendingBooks(
        metric: String,
        days: Int? = null,
        category: String? = null,
        language: String? = null
    ): List<TrendingBook> {
        val params = TrendingParams(
            metric = metric,
            days = days,
            category = category,
            language = language
        )
        return supabase.postgrest.rpc("trending_books", params)
            .decodeList<TrendingBook>()
    }

    // ---- M1 staff: catalogue management ----

    /** All non-deleted books for the staff panel (includes archived copies count later). */
    suspend fun allBooksForStaff(): List<Book> =
        supabase.from("book")
            .select {
                filter { eq("is_deleted", false) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<Book>()

    suspend fun createBook(
        isbn: String,
        title: String,
        author: String,
        publisher: String,
        publishYear: Int,
        category: String,
        language: String,
        pages: Int,
        price: Double,
        description: String
    ): Book =
        supabase.from("book").insert(
            NewBook(
                isbn = isbn,
                title = title,
                author = author,
                publisher = publisher,
                publishYear = publishYear,
                category = category,
                language = language,
                pages = pages,
                price = price,
                description = description
            )
        ) {
            select()
        }.decodeSingle<Book>()

    suspend fun updateBook(
        bookId: String,
        title: String,
        author: String,
        publisher: String,
        publishYear: Int,
        category: String,
        language: String,
        pages: Int,
        price: Double,
        description: String
    ) {
        supabase.from("book").update(
            BookUpdate(
                title = title,
                author = author,
                publisher = publisher,
                publishYear = publishYear,
                category = category,
                language = language,
                pages = pages,
                price = price,
                description = description
            )
        ) {
            filter { eq("book_id", bookId) }
        }
    }

    suspend fun softDeleteBook(bookId: String) {
        supabase.from("book").update(DeletedFlag(true)) {
            filter { eq("book_id", bookId) }
        }
    }

    /** Upload a cover image and return its public URL. */
    suspend fun uploadCover(isbn: String, bytes: ByteArray, ext: String): String {
        val bucket = supabase.storage.from("book-covers")
        val path = "${isbn.trim()}.$ext"
        bucket.upload(path, bytes) {
            upsert = true
        }
        return bucket.publicUrl(path)
    }

    suspend fun updateBookCover(bookId: String, url: String) {
        supabase.from("book").update(CoverUpdate(url)) {
            filter { eq("book_id", bookId) }
        }
    }

    /** Live copies of a book for staff (deleted ones are hidden). */
    suspend fun copiesForStaff(bookId: String): List<BookCopy> =
        supabase.from("book_copy")
            .select {
                filter {
                    eq("book_id", bookId)
                    eq("is_deleted", false)
                }
                order("copy_no", Order.ASCENDING)
            }
            .decodeList<BookCopy>()

    /** Every live copy, used to compute stock counts in the manage list. */
    suspend fun allCopiesForStaff(): List<BookCopy> =
        supabase.from("book_copy")
            .select {
                filter { eq("is_deleted", false) }
            }
            .decodeList<BookCopy>()

    suspend fun createCopy(
        bookId: String,
        copyNo: String,
        condition: String,
        status: String,
        shelfLocation: String,
        notes: String?
    ) {
        supabase.from("book_copy").insert(
            NewCopy(
                bookId = bookId,
                copyNo = copyNo,
                condition = condition,
                status = status,
                shelfLocation = shelfLocation,
                notes = notes?.trim()?.ifBlank { null }
            )
        )
    }

    suspend fun updateCopy(
        copyId: String,
        condition: String,
        status: String,
        shelfLocation: String,
        notes: String?
    ) {
        supabase.from("book_copy").update(
            CopyUpdate(
                condition = condition,
                status = status,
                shelfLocation = shelfLocation,
                notes = notes?.trim()?.ifBlank { null }
            )
        ) {
            filter { eq("copy_id", copyId) }
        }
    }

    suspend fun softDeleteCopy(copyId: String) {
        supabase.from("book_copy").update(DeletedFlag(true)) {
            filter { eq("copy_id", copyId) }
        }
    }

    /** Active (waiting / ready) reservation for this student and book, if any. */
    suspend fun myActiveReservation(bookId: String, studentNo: String): Reservation? =
        supabase.from("reservation")
            .select {
                filter {
                    eq("book_id", bookId)
                    eq("student_no", studentNo)
                    isIn("status", listOf("waiting", "ready"))
                }
            }
            .decodeSingleOrNull<Reservation>()

    suspend fun reserve(
        studentNo: String,
        bookId: String,
        note: String? = null,
        pickupStartDate: String? = null,
        pickupEndDate: String? = null
    ) {
        // DB trigger `reservation_limit_check` enforces: borrowed + reserved <= 2.
        supabase.from("reservation").insert(
            NewReservation(
                studentNo = studentNo,
                bookId = bookId,
                note = note?.trim()?.ifBlank { null },
                pickupStartDate = pickupStartDate,
                pickupEndDate = pickupEndDate
            )
        )
    }

    suspend fun activeLoanCount(studentNo: String): Int =
        supabase.from("borrow_record")
            .select(columns = Columns.list("borrow_id")) {
                filter {
                    eq("student_no", studentNo)
                    eq("status", "on_loan")
                }
            }
            .decodeList<BorrowIdRow>()
            .size

    suspend fun activeReservationCount(studentNo: String): Int =
        supabase.from("reservation")
            .select(columns = Columns.list("reservation_id")) {
                filter {
                    eq("student_no", studentNo)
                    isIn("status", listOf("waiting", "ready"))
                }
            }
            .decodeList<ReservationIdRow>()
            .size

    /**
     * Automatic pickup window (user-defined rule):
     * the day after the previous borrower returns + 3 days to collect.
     * Until staff confirms a return we estimate from the current due_date.
     */
    suspend fun estimatedPickupWindow(bookId: String): Pair<String, String> {
        val copies = try { copiesOf(bookId).map { it.id } } catch (_: Exception) { emptyList() }
        var base = java.time.LocalDate.now()
        if (copies.isNotEmpty()) {
            val onLoan = try {
                supabase.from("borrow_record")
                    .select(columns = Columns.list("due_date")) {
                        filter {
                            isIn("copy_id", copies)
                            eq("status", "on_loan")
                        }
                        order("due_date", Order.DESCENDING)
                        limit(1)
                    }
                    .decodeList<DueDateRow>()
            } catch (_: Exception) {
                emptyList()
            }
            onLoan.firstOrNull()?.let {
                runCatching { base = java.time.LocalDate.parse(it.dueDate) }
            }
        }
        val start = base.plusDays(1)
        val end = base.plusDays(3)
        return start.toString() to end.toString()
    }

    /** Cancel my own reservation (RLS allows update only for the owner). */
    suspend fun cancelReservation(reservationId: String) {
        supabase.from("reservation").update(ReservationStatus("cancelled")) {
            filter { eq("reservation_id", reservationId) }
        }
    }

    /** 1-based position in the waiting queue for this reservation. */
    suspend fun queuePosition(reservationId: String): Int? =
        try {
            supabase.postgrest.rpc(
                "reservation_queue_position",
                mapOf("p_reservation_id" to reservationId)
            ).decodeAs<Int>()
        } catch (_: Exception) {
            null
        }

    /** Report someone else's review. RLS: reporter must be the logged-in student. */
    suspend fun reportReview(studentNo: String, reviewId: String, reason: String?) {
        supabase.from("review_report").insert(
            NewReport(
                reviewId = reviewId,
                studentNo = studentNo,
                reason = reason?.trim()?.ifBlank { null }
            )
        )
    }
}
