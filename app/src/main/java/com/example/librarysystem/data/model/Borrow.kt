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
    @SerialName("overdue_days") val overdueDays: Int = 0
)

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
    @SerialName("unpaid_fines") val unpaidFines: Double = 0.0
)

@Serializable
data class FineRow(
    @SerialName("fine_id") val id: String,
    @SerialName("student_no") val studentNo: String,
    @SerialName("student_name") val studentName: String = "",
    @SerialName("fine_type") val fineType: String,
    val amount: Double,
    val status: String,
    @SerialName("created_at") val createdAt: String? = null
)
