package com.example.librarysystem.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One `borrow_record` row as the student reads it (RLS already limits it to their own). */
@Serializable
data class MyLoanRow(
    @SerialName("borrow_id") val borrowId: String,
    @SerialName("copy_id") val copyId: String,
    @SerialName("due_date") val dueDate: String
)

@Serializable
data class MyFineAmount(val amount: Double)

@Serializable
data class MyCopyRow(
    @SerialName("copy_id") val copyId: String,
    @SerialName("copy_no") val copyNo: String,
    @SerialName("book_id") val bookId: String
)

@Serializable
data class MyBookRow(
    @SerialName("book_id") val bookId: String,
    val title: String,
    val author: String? = null
)

/** One in-flight loan, joined to its copy and book, as shown to its owner. */
data class MyLoan(
    val borrowId: String,
    val copyNo: String,
    val bookTitle: String,
    val dueDate: LocalDate,
    /** Positive when past the due date, 0 when due today, negative while still on time. */
    val overdueDays: Int
) {
    val isOverdue: Boolean get() = overdueDays > 0
    val daysLeft: Long get() = -overdueDays.toLong()

    /** Short line for the detail list, e.g. "Due 26 Nov 2026 · in 3 days". */
    fun dueLabel(today: LocalDate = LocalDate.now()): String = when {
        overdueDays > 0 && overdueDays == 1 -> "Overdue by 1 day"
        overdueDays > 1 -> "Overdue by $overdueDays days"
        dueDate.isEqual(today) -> "Due today"
        overdueDays == -1 -> "Due tomorrow"
        else -> "Due ${DueDateText.format(dueDate)} · in $daysLeft days"
    }
}

/**
 * Everything the Home banner needs about the signed-in student.
 * [headline] lives here (not in the composable) so the wording is testable in isolation.
 */
data class StudentAlert(
    val loans: List<MyLoan>,
    val unpaidTotal: Double,
    val today: LocalDate = LocalDate.now()
) {
    val isEmpty: Boolean get() = loans.isEmpty() && unpaidTotal <= 0.0
    val overdueCount: Int get() = loans.count { it.isOverdue }
    val nextDue: MyLoan? get() = loans.filterNot { it.isOverdue }.minByOrNull { it.dueDate }

    /** e.g. "2 books due in 3 days · RM 1.00 unpaid". Empty when there is nothing to say. */
    val headline: String
        get() {
            val parts = mutableListOf<String>()
            when {
                overdueCount > 0 ->
                    parts += if (overdueCount == 1) "1 book overdue" else "$overdueCount books overdue"
                loans.isNotEmpty() -> {
                    val count = loans.size
                    val books = if (count == 1) "1 book" else "$count books"
                    val due = nextDue ?: loans.minByOrNull { it.dueDate }
                    val days = due?.let { ChronoUnit.DAYS.between(today, it.dueDate) } ?: 0L
                    parts += when {
                        days <= 0L -> "$books due today"
                        days == 1L -> "$books due tomorrow"
                        else -> "$books due in $days days"
                    }
                }
            }
            if (unpaidTotal > 0.0) parts += "RM %.2f unpaid".format(unpaidTotal)
            return parts.joinToString(" · ")
        }
}

/** Shared date formatting for the student-facing loan surfaces. */
object DueDateText {
    fun format(date: LocalDate): String =
        "%d %s %d".format(
            date.dayOfMonth,
            date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3),
            date.year
        )
}
