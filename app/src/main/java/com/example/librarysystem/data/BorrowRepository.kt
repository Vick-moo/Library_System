package com.example.librarysystem.data

import com.example.librarysystem.data.model.ActiveLoan
import com.example.librarysystem.data.model.AvailableCopy
import com.example.librarysystem.data.model.StudentCard
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Serializable
private data class QueryParam(@SerialName("p_query") val query: String)

@Serializable
private data class StudentParam(@SerialName("p_student_no") val studentNo: String)

@Serializable
private data class NewBorrow(
    @SerialName("student_no") val studentNo: String,
    @SerialName("copy_id") val copyId: String,
    @SerialName("issued_by") val issuedBy: String,
    @SerialName("borrow_date") val borrowDate: String,
    @SerialName("due_date") val dueDate: String,
    val status: String
)

@Serializable
private data class ReturnUpdate(
    @SerialName("return_date") val returnDate: String,
    @SerialName("received_by") val receivedBy: String,
    val status: String
)

@Serializable
private data class NewFine(
    @SerialName("student_no") val studentNo: String,
    @SerialName("borrow_id") val borrowId: String? = null,
    @SerialName("fine_type") val fineType: String,
    val amount: Double,
    @SerialName("book_price") val bookPrice: Double? = null
)

@Serializable
private data class CopyStatus(val status: String)

@Serializable
private data class FineIdRow(@SerialName("fine_id") val id: String)

@Serializable
private data class FineAmountOnly(val amount: Double)

object BorrowRepository {

    private const val LOAN_DAYS = 14L
    private const val FINE_PER_DAY = 0.5
    private const val FINE_CAP = 10.0

    suspend fun activeLoans(query: String? = null): List<ActiveLoan> {
        val res = if (query.isNullOrBlank()) {
            supabase.postgrest.rpc("staff_active_loans")
        } else {
            supabase.postgrest.rpc("staff_active_loans", QueryParam(query.trim()))
        }
        return res.decodeList<ActiveLoan>()
    }

    suspend fun searchAvailableCopies(query: String? = null): List<AvailableCopy> {
        val res = if (query.isNullOrBlank()) {
            supabase.postgrest.rpc("staff_search_copies")
        } else {
            supabase.postgrest.rpc("staff_search_copies", QueryParam(query.trim()))
        }
        return res.decodeList<AvailableCopy>()
    }

    suspend fun studentCard(studentNo: String): StudentCard? =
        supabase.postgrest.rpc("staff_student_card", StudentParam(studentNo.trim()))
            .decodeList<StudentCard>()
            .firstOrNull()

    /** Creates the loan, marks the copy borrowed, returns the due date (yyyy-MM-dd). */
    suspend fun borrowBook(studentNo: String, copyId: String, staffNo: String): String {
        val today = LocalDate.now()
        val due = today.plusDays(LOAN_DAYS)
        supabase.from("borrow_record").insert(
            NewBorrow(
                studentNo = studentNo,
                copyId = copyId,
                issuedBy = staffNo,
                borrowDate = today.toString(),
                dueDate = due.toString(),
                status = "on_loan"
            )
        )
        supabase.from("book_copy").update(CopyStatus("borrowed")) {
            filter { eq("copy_id", copyId) }
        }
        return due.toString()
    }

    /**
     * Marks the loan returned, frees the copy, and creates an overdue fine if late.
     * Returns overdue days (0 when on time).
     */
    suspend fun returnLoan(
        borrowId: String,
        copyId: String,
        studentNo: String,
        dueDate: String,
        staffNo: String
    ): Int {
        val today = LocalDate.now()
        val overdue = maxOf(
            0,
            ChronoUnit.DAYS.between(LocalDate.parse(dueDate), today).toInt()
        )

        supabase.from("borrow_record").update(
            ReturnUpdate(
                returnDate = today.toString(),
                receivedBy = staffNo,
                status = "returned"
            )
        ) {
            filter { eq("borrow_id", borrowId) }
        }

        supabase.from("book_copy").update(CopyStatus("available")) {
            filter { eq("copy_id", copyId) }
        }

        if (overdue > 0) {
            val amount = minOf(overdue * FINE_PER_DAY, FINE_CAP)
            // (borrow_id, fine_type) is unique — update the existing overdue fine instead of inserting twice.
            val existing = supabase.from("fine")
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("fine_id")) {
                    filter {
                        eq("borrow_id", borrowId)
                        eq("fine_type", "overdue")
                    }
                    limit(1)
                }
                .decodeList<FineIdRow>()

            if (existing.isEmpty()) {
                supabase.from("fine").insert(
                    NewFine(
                        studentNo = studentNo,
                        borrowId = borrowId,
                        fineType = "overdue",
                        amount = amount
                    )
                )
            } else {
                supabase.from("fine").update(FineAmountOnly(amount)) {
                    filter { eq("fine_id", existing.first().id) }
                }
            }
        }

        return overdue
    }
}
