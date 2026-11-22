package com.example.librarysystem.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActiveLoan(
    @SerialName("borrow_id") val borrowId: String,
    @SerialName("copy_id") val copyId: String,
    @SerialName("student_no") val studentNo: String,
    @SerialName("student_name") val studentName: String,
    @SerialName("copy_no") val copyNo: String,
    @SerialName("book_title") val bookTitle: String,
    @SerialName("borrow_date") val borrowDate: String,
    @SerialName("due_date") val dueDate: String,
    @SerialName("overdue_days") val overdueDays: Int = 0,
    @SerialName("unpaid_fine") val unpaidFine: Double = 0.0,
    @SerialName("book_price") val bookPrice: Double = 0.0
) {
    val isOverdue: Boolean get() = overdueDays > 0
    val hasUnpaidFine: Boolean get() = unpaidFine > 0.0
}

@Serializable
data class AvailableCopy(
    @SerialName("copy_id") val copyId: String,
    @SerialName("copy_no") val copyNo: String,
    @SerialName("book_title") val bookTitle: String,
    @SerialName("book_author") val bookAuthor: String,
    @SerialName("shelf_location") val shelf: String? = null
)

@Serializable
data class StudentCard(
    @SerialName("student_no") val studentNo: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("active_loans") val activeLoans: Int = 0,
    @SerialName("reserved_count") val reservedCount: Int = 0,
    @SerialName("unpaid_fines") val unpaidFines: Double = 0.0
) {
    /** The library caps loans + active reservations at 2 per student. */
    val totalHeld: Int get() = activeLoans + reservedCount
    val atLimit: Boolean get() = totalHeld >= 2
}

@Serializable
data class FineRow(
    @SerialName("fine_id") val id: String,
    @SerialName("student_no") val studentNo: String,
    @SerialName("student_name") val studentName: String = "",
    @SerialName("fine_type") val fineType: String,
    val amount: Double,
    val status: String,
    @SerialName("book_title") val bookTitle: String? = null,
    @SerialName("copy_no") val copyNo: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("handled_by") val handledBy: String? = null
) {
    val isUnpaid: Boolean get() = status == "unpaid"

    val typeLabel: String
        get() = when (fineType) {
            "overdue" -> "Overdue"
            "damage" -> "Damage"
            "lost" -> "Lost book"
            else -> fineType.replaceFirstChar { it.uppercase() }
        }
}

@Serializable
data class SettleResult(
    @SerialName("overdue_days") val overdueDays: Int = 0,
    val amount: Double = 0.0,
    @SerialName("fine_id") val fineId: String? = null,
    @SerialName("already_settled") val alreadySettled: Boolean = false
)

@Serializable
data class ReturnResult(
    val ok: Boolean,
    val reason: String = "",
    @SerialName("overdue_days") val overdueDays: Int = 0,
    @SerialName("unpaid_amount") val unpaidAmount: Double? = null
)

@Serializable
data class ActionResult(
    val ok: Boolean,
    val message: String = "",
    @SerialName("fine_amount") val fineAmount: Double? = null
)
