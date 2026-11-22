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

    private var staffNo: String? = null

    init {
        viewModelScope.launch {
            staffNo = try { AuthRepository.myStaffNo() } catch (_: Exception) { null }
            loadLoans()
            searchCopies()
        }
    }

    fun selectTab(index: Int) {
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
        loadLoans()
    }

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

    fun confirmBorrow(onDone: () -> Unit) {
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
            error = "Student has RM %.2f unpaid fines. Clear them first.".format(card.unpaidFines)
            return
        }
        if (card.activeLoans >= 2) {
            error = "Student already has ${card.activeLoans} books on loan (max 2)."
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

    fun loadLoans() {
        viewModelScope.launch {
            loansLoading = true
            try {
                loans = BorrowRepository.activeLoans(loanQuery)
            } catch (_: Exception) {
                loans = emptyList()
            } finally {
                loansLoading = false
            }
        }
    }

    fun returnLoan(loan: ActiveLoan) {
        val staff = staffNo ?: run {
            error = "Staff account not recognised. Re-login."
            return
        }
        busy = true
        error = null
        notice = null
        viewModelScope.launch {
            try {
                val overdue = BorrowRepository.returnLoan(
                    borrowId = loan.borrowId,
                    copyId = loan.copyId,
                    studentNo = loan.studentNo,
                    dueDate = loan.dueDate,
                    staffNo = staff
                )
                notice = if (overdue > 0) {
                    "Returned. ${overdue} day(s) late — fine %.2f created.".format(
                        minOf(overdue * 0.5, 10.0)
                    )
                } else {
                    "Returned on time."
                }
                loadLoans()
                if (studentCard?.studentNo == loan.studentNo) searchStudent()
            } catch (e: Exception) {
                error = friendly(e, "Could not return this book. Try again.")
            } finally {
                busy = false
            }
        }
    }

    private fun friendly(e: Exception, fallback: String): String {
        val m = (e.message ?: "").lowercase()
        return when {
            "row-level security" in m || "permission denied" in m ->
                "You are not allowed to do that."
            "network" in m || "timeout" in m -> "Network error. Try again."
            else -> fallback
        }
    }
}
