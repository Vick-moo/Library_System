package com.example.librarysystem.data

import com.example.librarysystem.data.model.MyBookRow
import com.example.librarysystem.data.model.MyCopyRow
import com.example.librarysystem.data.model.MyFineAmount
import com.example.librarysystem.data.model.MyLoan
import com.example.librarysystem.data.model.MyLoanRow
import com.example.librarysystem.data.model.StudentAlert
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Read helpers for the signed-in student's own records.
 *
 * Row visibility is enforced by RLS; the explicit `student_no` filter is only there to keep
 * the payload small and to make the intent obvious.
 */
object StudentSelfRepository {

    /**
     * Active loans plus unpaid fines for the current student.
     * Returns null for guests and for staff-only accounts (no `student` row).
     */
    suspend fun myAlert(): StudentAlert? {
        val studentNo = AuthRepository.myStudentNo() ?: return null

        val loanRows = supabase.from("borrow_record")
            .select(columns = Columns.list("borrow_id", "copy_id", "due_date")) {
                filter {
                    eq("student_no", studentNo)
                    eq("status", "on_loan")
                }
            }
            .decodeList<MyLoanRow>()

        val unpaidRows = supabase.from("fine")
            .select(columns = Columns.list("amount")) {
                filter {
                    eq("student_no", studentNo)
                    eq("status", "unpaid")
                }
            }
            .decodeList<MyFineAmount>()

        val unpaidTotal = unpaidRows.sumOf { it.amount }
        if (loanRows.isEmpty() && unpaidTotal <= 0.0) return StudentAlert(emptyList(), 0.0)

        // Join by hand: borrow_record -> book_copy -> book.
        val copyIds = loanRows.map { it.copyId }.distinct()
        val copies = if (copyIds.isEmpty()) emptyList() else supabase.from("book_copy")
            .select(columns = Columns.list("copy_id", "copy_no", "book_id")) {
                filter { isIn("copy_id", copyIds) }
            }
            .decodeList<MyCopyRow>()

        val bookIds = copies.map { it.bookId }.distinct()
        val books = if (bookIds.isEmpty()) emptyList() else supabase.from("book")
            .select(columns = Columns.list("book_id", "title")) {
                filter { isIn("book_id", bookIds) }
            }
            .decodeList<MyBookRow>()

        val copyById = copies.associateBy { it.copyId }
        val bookById = books.associateBy { it.bookId }
        val today = LocalDate.now()

        val loans = loanRows.mapNotNull { row ->
            val due = runCatching { LocalDate.parse(row.dueDate) }.getOrNull() ?: return@mapNotNull null
            val copy = copyById[row.copyId] ?: return@mapNotNull null
            val book = bookById[copy.bookId] ?: return@mapNotNull null
            MyLoan(
                borrowId = row.borrowId,
                copyNo = copy.copyNo,
                bookTitle = book.title,
                dueDate = due,
                overdueDays = ChronoUnit.DAYS.between(due, today).toInt()
            )
        }.sortedBy { it.dueDate }

        return StudentAlert(loans = loans, unpaidTotal = unpaidTotal, today = today)
    }
}
