package com.example.librarysystem.ui.borrow

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.librarysystem.data.AuthRepository
import com.example.librarysystem.data.BorrowRepository
import com.example.librarysystem.data.model.ActiveLoan
import com.example.librarysystem.data.model.AvailableCopy
import com.example.librarysystem.data.model.StudentCard
import kotlinx.coroutines.launch

/** A loan blocked by an unpaid fine, shown before the book can go back on the shelf. */
data class FineGate(
    val loan: ActiveLoan,
    val amount: Double,
    val overdueDays: Int
)

class BorrowDeskViewModel : ViewModel() {

    var tab by mutableStateOf(0)
        private set

    var studentNoInput by mutableStateOf("")
        private set
    var studentCard by mutableStateOf<StudentCard?>(null)
        private set
    var studentSearching by mutableStateOf(false)
        private set

    var copyQuery by mutableStateOf("")
        private set
    var copies by mutableStateOf<List<AvailableCopy>>(emptyList())
        private set
    var copiesLoading by mutableStateOf(false)
        private set
    var selectedCopy by mutableStateOf<AvailableCopy?>(null)
        private set

    var loanQuery by mutableStateOf("")
        private set
    var loans by mutableStateOf<List<ActiveLoan>>(emptyList())
        private set
    var loansLoading by mutableStateOf(false)
        private set

    var notice by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set

    /** Pending "are you sure" for a return. */
    var confirmReturnLoan by mutableStateOf<ActiveLoan?>(null)
        private set

    /** Pending "pay the fine first" gate. */
    var fineGate by mutableStateOf<FineGate?>(null)
        private set

    /** Loan the staff is filing a damage report for. */
    var damageTarget by mutableStateOf<ActiveLoan?>(null)
        private set

    private var staffNo: String? = null

    /** Return desk: full list, optionally narrowed by student number. */
    val visibleLoans: List<ActiveLoan>
        get() {
            val q = loanQuery.trim()
            return if (q.isBlank()) loans else loans.filter { it.studentNo.contains(q, true) }
        }

    init {
        viewModelScope.launch {
            staffNo = try { AuthRepository.myStaffNo() } catch (_: Exception) { null }
        }
        loadLoans()
        searchCopies()
    }

    fun selectTab(index: Int) {
        if (tab == index) return
        tab = index
        notice = null
        error = null
        if (index == 1) loadLoans()
    }

    fun onStudentNoChange(value: String) {
        studentNoInput = value
        studentCard = null
        error = null
    }

    fun onCopyQueryChange(value: String) {
        copyQuery = value
        searchCopies()
    }

    fun onLoanQueryChange(value: String) {
        loanQuery = value
        error = null
    }

    // ---------------------------------------------------------------- borrow

    fun searchStudent() {
        val no = studentNoInput.trim()
        if (no.isBlank()) {
            error = "Enter a student number."
            return
        }
        studentSearching = true
        error = null
        studentCard = null
        viewModelScope.launch {
            try {
                val card = BorrowRepository.studentCard(no)
                if (card == null) {
                    error = "No student found with that number."
                } else {
                    studentCard = card
                }
            } catch (e: Exception) {
                error = "Could not look up the student. Try again."
            } finally {
                studentSearching = false
            }
        }
    }

    private fun searchCopies() {
        viewModelScope.launch {
            copiesLoading = true
            try {
                copies = BorrowRepository.searchAvailableCopies(copyQuery)
            } catch (_: Exception) {
                copies = emptyList()
            } finally {
                copiesLoading = false
            }
        }
    }

    fun selectCopy(copy: AvailableCopy) {
        selectedCopy = if (selectedCopy?.copyId == copy.copyId) null else copy
        error = null
    }

    fun confirmBorrow() {
        val card = studentCard ?: run {
            error = "Search a student first."
            return
        }
        val copy = selectedCopy ?: run {
            error = "Pick a copy to borrow."
            return
        }
        val staff = staffNo ?: run {
            error = "Staff account not recognised. Re-login."
            return
        }
        if (card.unpaidFines > 0) {
            error = "Student has RM %.2f unpaid fines. Settle them in Fines first."
                .format(card.unpaidFines)
            return
        }
        if (card.activeLoans >= 2) {
            error = "Student already has ${card.activeLoans} books on loan (max 2)."
            return
        }
        if (card.totalHeld >= 2) {
            error = "Student has ${card.activeLoans} on loan + ${card.reservedCount} reserved " +
                "(limit 2). Cannot borrow another."
            return
        }

        busy = true
        error = null
        notice = null
        viewModelScope.launch {
            try {
                val due = BorrowRepository.borrowBook(card.studentNo, copy.copyId, staff)
                notice = "Borrowed \"${copy.bookTitle}\". Due $due."
                selectedCopy = null
                searchCopies()
                searchStudent()
            } catch (e: Exception) {
                error = friendly(e, "Could not borrow this copy. Try again.")
            } finally {
                busy = false
            }
        }
    }

    // ---------------------------------------------------------------- return

    fun loadLoans() {
        viewModelScope.launch {
            loansLoading = true
            try {
                loans = BorrowRepository.activeLoans()
            } catch (_: Exception) {
                loans = emptyList()
            } finally {
                loansLoading = false
            }
        }
    }

    /**
     * Step 1 of the return flow. An overdue loan gets its fine settled (amount
     * locked for today) and the staff is sent to the fine desk instead of
     * returning straight away. A clean loan asks for the usual confirmation.
     */
    fun onReturnClick(loan: ActiveLoan) {
        if (busy) return
        error = null
        notice = null

        if (loan.hasUnpaidFine) {
            fineGate = FineGate(loan, loan.unpaidFine, loan.overdueDays)
            return
        }

        if (!loan.isOverdue) {
            confirmReturnLoan = loan
            return
        }

        busy = true
        viewModelScope.launch {
            try {
                val settled = BorrowRepository.settleOverdue(loan.borrowId)
                if (settled.amount > 0 && !settled.alreadySettled) {
                    fineGate = FineGate(loan, settled.amount, settled.overdueDays)
                } else {
                    // No money owed (or already settled) — plain confirmation.
                    confirmReturnLoan = loan
                }
                loadLoans()
            } catch (e: Exception) {
                error = friendly(e, "Could not check the overdue fine. Try again.")
            } finally {
                busy = false
            }
        }
    }

    fun hasUnpaidFineOn(loan: ActiveLoan): Boolean =
        loans.firstOrNull { it.borrowId == loan.borrowId }?.hasUnpaidFine ?: loan.hasUnpaidFine

    fun dismissReturnConfirm() {
        confirmReturnLoan = null
    }

    fun dismissFineGate() {
        fineGate = null
    }

    /** Step 2: the staff confirmed the dialog; the server re-checks the fine. */
    fun confirmReturn() {
        val loan = confirmReturnLoan ?: return
        confirmReturnLoan = null
        if (busy) return
        busy = true
        error = null
        notice = null
        viewModelScope.launch {
            try {
                val result = BorrowRepository.returnLoan(loan.borrowId)
                when {
                    result.ok -> {
                        notice = if (result.overdueDays > 0) {
                            "Returned \"${loan.bookTitle}\". ${result.overdueDays} day(s) late."
                        } else {
                            "Returned \"${loan.bookTitle}\"."
                        }
                        if (studentCard?.studentNo == loan.studentNo) searchStudent()
                    }

                    result.reason == "FINE_UNPAID" -> {
                        fineGate = FineGate(
                            loan,
                            result.unpaidAmount ?: loan.unpaidFine,
                            loan.overdueDays
                        )
                    }

                    result.reason == "ALREADY_RETURNED" -> {
                        notice = "This book was already returned."
                    }

                    else -> error = "Could not return this book. Try again."
                }
                loadLoans()
            } catch (e: Exception) {
                error = friendly(e, "Could not return this book. Try again.")
            } finally {
                busy = false
            }
        }
    }

    // ---------------------------------------------------------------- damage

    fun openDamage(loan: ActiveLoan) {
        error = null
        damageTarget = loan
    }

    fun dismissDamage() {
        damageTarget = null
    }

    fun reportDamage(category: String, description: String) {
        val loan = damageTarget ?: return
        damageTarget = null
        if (busy) return
        busy = true
        error = null
        notice = null
        viewModelScope.launch {
            try {
                val result = BorrowRepository.reportDamage(loan.borrowId, category, description)
                if (result.ok) {
                    val label = when (category) {
                        "minor" -> "Minor damage"
                        "major" -> "Major damage"
                        else -> "Lost book"
                    }
                    notice = "$label recorded for \"${loan.bookTitle}\". " +
                        "Fine RM %.2f added.".format(result.fineAmount ?: 0.0)
                    loadLoans()
                } else {
                    error = when (result.message) {
                        "NOT_ALLOWED" -> "Only admin or librarian can record damage."
                        "BAD_CATEGORY" -> "Choose a damage level."
                        "LOAN_NOT_FOUND" -> "This loan no longer exists."
                        else -> "Could not save the damage report. Try again."
                    }
                }
            } catch (e: Exception) {
                error = friendly(e, "Could not save the damage report. Try again.")
            } finally {
                busy = false
            }
        }
    }

    // --------------------------------------------------------------- refresh

    /** Pull-to-refresh: reload whatever the current tab is showing. */
    fun refresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            try {
                if (tab == 1) {
                    loans = BorrowRepository.activeLoans()
                } else {
                    copies = BorrowRepository.searchAvailableCopies(copyQuery)
                    val no = studentNoInput.trim()
                    if (no.isNotBlank()) {
                        studentCard = BorrowRepository.studentCard(no)
                    }
                }
                error = null
            } catch (_: Exception) {
                error = "Could not refresh. Check the connection."
            } finally {
                refreshing = false
            }
        }
    }

    /** Called when coming back from the fine desk. */
    fun onReturnedFromFines(message: String?) {
        if (!message.isNullOrBlank()) notice = message
        error = null
        tab = 1
        loadLoans()
        if (studentNoInput.isNotBlank()) searchStudent()
    }

    fun clearTransient() {
        notice = null
        error = null
    }

    private fun friendly(e: Exception, fallback: String): String {
        val m = (e.message ?: "").lowercase()
        return when {
            "row-level security" in m || "permission denied" in m ->
                "You are not allowed to do that."
            "network" in m || "timeout" in m -> "Network error. Try again."
            "not_allowed" in m -> "Only admin or librarian can settle fines."
            else -> fallback
        }
    }
}
