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

    /** 0 Borrow · 1 Return · 2 Fines · 3 Damage */
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

    /** Bottom snackbar; cleared once shown. */
    var snackbar by mutableStateOf<String?>(null)
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
    }

    fun selectTab(index: Int) {
        if (tab == index) return
        tab = index
        when (index) {
            1 -> loadLoans()
            0 -> if (studentNoInput.isNotBlank()) searchStudent()
        }
    }

    fun onStudentNoChange(value: String) {
        studentNoInput = value
        studentCard = null
    }

    fun onCopyQueryChange(value: String) {
        copyQuery = value
        selectedCopy = null
        if (value.isBlank()) {
            copies = emptyList()
            copiesLoading = false
        } else {
            searchCopies()
        }
    }

    fun onLoanQueryChange(value: String) {
        loanQuery = value
    }

    // ---------------------------------------------------------------- borrow

    fun searchStudent() {
        val no = studentNoInput.trim()
        if (no.isBlank()) {
            snackbar = "Enter a student number."
            return
        }
        studentSearching = true
        studentCard = null
        viewModelScope.launch {
            try {
                val card = BorrowRepository.studentCard(no)
                if (card == null) {
                    snackbar = "No student found with that number."
                } else {
                    studentCard = card
                }
            } catch (_: Exception) {
                snackbar = "Could not look up the student. Try again."
            } finally {
                studentSearching = false
            }
        }
    }

    private fun searchCopies() {
        viewModelScope.launch {
            copiesLoading = true
            try {
                copies = BorrowRepository.searchAvailableCopies(copyQuery).take(10)
            } catch (_: Exception) {
                copies = emptyList()
            } finally {
                copiesLoading = false
            }
        }
    }

    fun selectCopy(copy: AvailableCopy) {
        selectedCopy = if (selectedCopy?.copyId == copy.copyId) null else copy
    }

    fun confirmBorrow() {
        val card = studentCard ?: run {
            snackbar = "Search a student first."
            return
        }
        val copy = selectedCopy ?: run {
            snackbar = "Pick a copy to borrow."
            return
        }
        val staff = staffNo ?: run {
            snackbar = "Staff account not recognised. Re-login."
            return
        }
        if (card.unpaidFines > 0) {
            snackbar = "Student has RM %.2f unpaid fines. Settle them in Fines first."
                .format(card.unpaidFines)
            return
        }
        if (card.activeLoans >= 2) {
            snackbar = "Student already has ${card.activeLoans} books on loan (max 2)."
            return
        }
        if (card.totalHeld >= 2) {
            snackbar = "Student has ${card.activeLoans} on loan + ${card.reservedCount} " +
                "reserved (limit 2). Cannot borrow another."
            return
        }

        busy = true
        viewModelScope.launch {
            try {
                val due = BorrowRepository.borrowBook(card.studentNo, copy.copyId, staff)
                snackbar = "Borrowed \"${copy.bookTitle}\". Due $due."
                selectedCopy = null
                copyQuery = ""
                copies = emptyList()
                searchCopies()
                searchStudent()
            } catch (e: Exception) {
                snackbar = friendly(e, "Could not borrow this copy. Try again.")
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
     * locked for today) and the staff is sent to the fine tab instead of
     * returning straight away.
     */
    fun onReturnClick(loan: ActiveLoan) {
        if (busy) return
        snackbar = null

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
                    confirmReturnLoan = loan
                }
                loadLoans()
            } catch (e: Exception) {
                snackbar = friendly(e, "Could not check the overdue fine. Try again.")
            } finally {
                busy = false
            }
        }
    }

    fun dismissReturnConfirm() {
        confirmReturnLoan = null
    }

    fun dismissFineGate() {
        fineGate = null
    }

    /** Jump from the fine gate straight to the Fines tab. */
    fun goToFines() {
        fineGate = null
        tab = 2
    }

    /** Step 2: the staff confirmed; the server re-checks the fine before returning. */
    fun confirmReturn() {
        val loan = confirmReturnLoan ?: return
        confirmReturnLoan = null
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val result = BorrowRepository.returnLoan(loan.borrowId)
                when {
                    result.ok -> {
                        snackbar = if (result.overdueDays > 0) {
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

                    result.reason == "ALREADY_RETURNED" ->
                        snackbar = "This book was already returned."

                    else -> snackbar = "Could not return this book. Try again."
                }
                loadLoans()
            } catch (e: Exception) {
                snackbar = friendly(e, "Could not return this book. Try again.")
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
                when (tab) {
                    1 -> loans = BorrowRepository.activeLoans()
                    0 -> {
                        if (copyQuery.isNotBlank()) {
                            copies = BorrowRepository.searchAvailableCopies(copyQuery).take(10)
                        }
                        val no = studentNoInput.trim()
                        if (no.isNotBlank()) {
                            studentCard = BorrowRepository.studentCard(no)
                        }
                    }
                }
            } catch (_: Exception) {
                snackbar = "Could not refresh. Check the connection."
            } finally {
                refreshing = false
            }
        }
    }

    /** Called after the fine desk clears a payment. */
    fun onFineCleared(message: String) {
        snackbar = message
        loadLoans()
        if (studentNoInput.isNotBlank()) searchStudent()
    }

    fun showMessage(message: String) {
        snackbar = message
    }

    fun consumeSnackbar() {
        snackbar = null
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
