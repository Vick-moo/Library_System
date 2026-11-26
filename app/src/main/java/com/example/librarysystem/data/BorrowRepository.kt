package com.example.librarysystem.data

import com.example.librarysystem.data.model.ActionResult
import com.example.librarysystem.data.model.ActiveLoan
import com.example.librarysystem.data.model.AvailableCopy
import com.example.librarysystem.data.model.DamageCopy
import com.example.librarysystem.data.model.DamageReport
import com.example.librarysystem.data.model.FineRow
import com.example.librarysystem.data.model.ReturnResult
import com.example.librarysystem.data.model.SettleResult
import com.example.librarysystem.data.model.StudentCard
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
private data class QueryParam(@SerialName("p_query") val query: String)

@Serializable
private data class StatusQueryParam(
    @SerialName("p_status") val status: String,
    @SerialName("p_query") val query: String?
)

@Serializable
private data class StudentParam(@SerialName("p_student_no") val studentNo: String)

@Serializable
private data class BorrowIdParam(@SerialName("p_borrow_id") val borrowId: String)

@Serializable
private data class FineIdParam(
    @SerialName("p_fine_id") val fineId: String,
    @SerialName("p_status") val status: String
)

@Serializable
private data class DamageParam(
    @SerialName("p_copy_id") val copyId: String,
    @SerialName("p_category") val category: String,
    @SerialName("p_percent") val percent: Int,
    @SerialName("p_description") val description: String?
)

@Serializable
private data class DamageReportIdParam(@SerialName("p_report_id") val reportId: String)

@Serializable
private data class UpdateDamageParam(
    @SerialName("p_report_id") val reportId: String,
    @SerialName("p_category") val category: String,
    @SerialName("p_description") val description: String?
)

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
private data class CopyStatus(val status: String)

object BorrowRepository {

    private const val LOAN_DAYS = 14L

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
     * Works out the overdue fine for one loan and writes it as `unpaid`.
     * The amount is fixed at the moment this runs, so it cannot grow while
     * the staff member walks to the cash drawer.
     */
    suspend fun settleOverdue(borrowId: String): SettleResult =
        supabase.postgrest.rpc("staff_settle_overdue", BorrowIdParam(borrowId))
            .decodeList<SettleResult>()
            .firstOrNull() ?: SettleResult(alreadySettled = true)

    /** Server-side return; refuses while the loan still carries an unpaid fine. */
    suspend fun returnLoan(borrowId: String): ReturnResult =
        supabase.postgrest.rpc("staff_return_loan", BorrowIdParam(borrowId))
            .decodeList<ReturnResult>()
            .firstOrNull() ?: ReturnResult(false, "UNKNOWN")

    /** Records damage/loss on any copy, charges the matching fine and takes it out of circulation. */
    suspend fun reportDamage(
        copyId: String,
        category: String,
        percent: Int,
        description: String?
    ): ActionResult =
        supabase.postgrest.rpc(
            "staff_report_damage",
            DamageParam(copyId, category, percent, description?.trim()?.ifBlank { null })
        ).decodeList<ActionResult>().firstOrNull() ?: ActionResult(false, "UNKNOWN")

    /** Copies the damage desk can inspect — borrowed or on the shelf. */
    suspend fun damageCopies(query: String? = null): List<DamageCopy> {
        val res = if (query.isNullOrBlank()) {
            supabase.postgrest.rpc("staff_damage_copies")
        } else {
            supabase.postgrest.rpc("staff_damage_copies", QueryParam(query.trim()))
        }
        return res.decodeList<DamageCopy>()
    }

    /** Copies that do not carry a damage report yet. */
    suspend fun damageSearchCopies(query: String? = null): List<DamageCopy> {
        val res = if (query.isNullOrBlank()) {
            supabase.postgrest.rpc("staff_damage_search_copies")
        } else {
            supabase.postgrest.rpc("staff_damage_search_copies", QueryParam(query.trim()))
        }
        return res.decodeList<DamageCopy>()
    }

    /** Every damage report the staff has filed, newest first. */
    suspend fun damageReports(query: String? = null): List<DamageReport> {
        val res = if (query.isNullOrBlank()) {
            supabase.postgrest.rpc("staff_damage_reports")
        } else {
            supabase.postgrest.rpc("staff_damage_reports", QueryParam(query.trim()))
        }
        return res.decodeList<DamageReport>()
    }

    /** Level + description only; the compensation percentage is locked. */
    suspend fun updateDamageReport(
        reportId: String,
        category: String,
        description: String?
    ): ActionResult =
        supabase.postgrest.rpc(
            "staff_update_damage_report",
            UpdateDamageParam(reportId, category, description?.trim()?.ifBlank { null })
        ).decodeList<ActionResult>().firstOrNull() ?: ActionResult(false, "UNKNOWN")

    /** Removes the report together with its fine; restores the copy when possible. */
    suspend fun deleteDamageReport(reportId: String): ActionResult =
        supabase.postgrest.rpc("staff_delete_damage_report", DamageReportIdParam(reportId))
            .decodeList<ActionResult>()
            .firstOrNull() ?: ActionResult(false, "UNKNOWN")

    suspend fun fines(status: String, query: String? = null): List<FineRow> =
        supabase.postgrest.rpc(
            "staff_fines",
            StatusQueryParam(status, query?.trim()?.ifBlank { null })
        ).decodeList<FineRow>()

    suspend fun markFine(fineId: String, status: String): ActionResult =
        supabase.postgrest.rpc("staff_mark_fine", FineIdParam(fineId, status))
            .decodeList<ActionResult>()
            .firstOrNull() ?: ActionResult(false, "UNKNOWN")
}
