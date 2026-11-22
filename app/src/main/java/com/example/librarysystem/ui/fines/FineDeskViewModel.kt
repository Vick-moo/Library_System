package com.example.librarysystem.ui.fines

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.librarysystem.data.AuthRepository
import com.example.librarysystem.data.BorrowRepository
import com.example.librarysystem.data.model.FineRow
import kotlinx.coroutines.launch

class FineDeskViewModel : ViewModel() {

    var filter by mutableStateOf("unpaid")
        private set
    var query by mutableStateOf("")
        private set

    var fines by mutableStateOf<List<FineRow>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set

    /** Only admin + librarian are allowed to settle money. */
    var canSettle by mutableStateOf(false)
        private set

    /** Fine waiting for a "waive" confirmation. */
    var pendingWaive by mutableStateOf<FineRow?>(null)
        private set

    init {
        viewModelScope.launch {
            canSettle = try {
                AuthRepository.myStaffRole() in setOf("admin", "librarian")
            } catch (_: Exception) {
                false
            }
        }
    }

    fun chooseFilter(value: String) {
        if (filter == value) return
        filter = value
        load()
    }

    /** The desk always opens on what still needs money. */
    fun startFresh() {
        filter = "unpaid"
        query = ""
        load()
    }

    fun onQueryChange(value: String) {
        query = value
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            try {
                fines = BorrowRepository.fines(filter, query)
                error = null
            } catch (_: Exception) {
                fines = emptyList()
                error = "Could not load fines. Pull down to try again."
            } finally {
                loading = false
            }
        }
    }

    /** Pull-to-refresh keeps the list on screen while reloading. */
    fun refresh() {
        viewModelScope.launch {
            try {
                fines = BorrowRepository.fines(filter, query)
                error = null
            } catch (_: Exception) {
                error = "Could not refresh. Check the connection."
            }
        }
    }

    fun markPaid(fine: FineRow, onSettled: (FineRow) -> Unit) {
        if (busy) return
        busy = true
        error = null
        notice = null
        viewModelScope.launch {
            try {
                val result = BorrowRepository.markFine(fine.id, "paid")
                if (result.ok) {
                    onSettled(fine)
                } else {
                    error = when (result.message) {
                        "NOT_ALLOWED" -> "Only admin or librarian can settle fines."
                        "NOT_FOUND" -> "This fine no longer exists."
                        else -> "Could not update this fine. Try again."
                    }
                }
            } catch (e: Exception) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    fun askWaive(fine: FineRow) {
        pendingWaive = fine
    }

    fun dismissWaive() {
        pendingWaive = null
    }

    fun confirmWaive() {
        val fine = pendingWaive ?: return
        pendingWaive = null
        if (busy) return
        busy = true
        error = null
        notice = null
        viewModelScope.launch {
            try {
                val result = BorrowRepository.markFine(fine.id, "waived")
                notice = if (result.ok) {
                    "Fine for ${fine.studentName.ifBlank { fine.studentNo }} waived."
                } else {
                    null
                }
                if (!result.ok) {
                    error = when (result.message) {
                        "NOT_ALLOWED" -> "Only admin or librarian can waive fines."
                        else -> "Could not waive this fine. Try again."
                    }
                }
                load()
            } catch (e: Exception) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    private fun friendly(e: Exception): String {
        val m = (e.message ?: "").lowercase()
        return when {
            "row-level security" in m || "permission denied" in m ->
                "You are not allowed to do that."
            "network" in m || "timeout" in m -> "Network error. Try again."
            else -> "Something went wrong. Try again."
        }
    }
}
