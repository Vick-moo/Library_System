package com.example.librarysystem.ui.damage

import com.example.librarysystem.data.keepSpinnerVisible

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.librarysystem.data.AuthRepository
import com.example.librarysystem.data.BorrowRepository
import com.example.librarysystem.data.ErrorText
import com.example.librarysystem.data.model.DamageCopy
import com.example.librarysystem.data.model.DamageReport
import kotlinx.coroutines.launch

/** Damage tab: the list of reports the staff has already filed. */
class DamageViewModel : ViewModel() {

    var query by mutableStateOf("")
        private set
    var reports by mutableStateOf<List<DamageReport>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Only admin + librarian may file damage reports. */
    var canReport by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            canReport = try {
                AuthRepository.myStaffRole() in setOf("admin", "librarian")
            } catch (_: Exception) {
                false
            }
        }
    }

    fun onQueryChange(value: String) {
        query = value
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            try {
                reports = BorrowRepository.damageReports(query)
                error = null
            } catch (e: Exception) {
                reports = emptyList()
                error = ErrorText.loadError(e)
            } finally {
                loading = false
            }
        }
    }

    fun refresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            val startedRefresh = System.currentTimeMillis()
            try {
                reports = BorrowRepository.damageReports(query)
                error = null
            } catch (e: Exception) {
                error = ErrorText.loadError(e)
            } finally {
                keepSpinnerVisible(startedRefresh)
                refreshing = false
            }
        }
    }
}

/** Full-screen picker opened from the + button: only copies without a report. */
class DamageSearchViewModel : ViewModel() {

    var query by mutableStateOf("")
        private set
    var copies by mutableStateOf<List<DamageCopy>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun onQueryChange(value: String) {
        query = value
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            try {
                copies = BorrowRepository.damageSearchCopies(query)
                error = null
            } catch (e: Exception) {
                copies = emptyList()
                error = ErrorText.loadError(e)
            } finally {
                loading = false
            }
        }
    }

    fun refresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            val startedRefresh = System.currentTimeMillis()
            try {
                copies = BorrowRepository.damageSearchCopies(query)
                error = null
            } catch (e: Exception) {
                error = ErrorText.loadError(e)
            } finally {
                keepSpinnerVisible(startedRefresh)
                refreshing = false
            }
        }
    }
}

/** Create form. Compensation must be confirmed twice because it locks on save. */
class DamageFormViewModel : ViewModel() {

    var category by mutableStateOf("minor")
        private set
    var percent by mutableStateOf(50)
        private set
    var description by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    var confirming by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun chooseCategory(value: String) {
        category = value
    }

    /** Ignored when the copy has no borrower — nothing can be charged. */
    fun choosePercent(value: Int, hasBorrower: Boolean) {
        if (!hasBorrower) return
        percent = value
    }

    fun onDescriptionChange(value: String) {
        if (value.length <= 300) description = value
    }

    fun reset(hasBorrower: Boolean) {
        category = "minor"
        percent = if (hasBorrower) 50 else 0
        description = ""
        error = null
        confirming = false
    }

    fun requestSubmit() {
        if (busy) return
        confirming = true
    }

    fun dismissConfirm() {
        confirming = false
    }

    fun confirmSubmit(copy: DamageCopy, onSaved: (String) -> Unit) {
        if (busy) return
        confirming = false
        busy = true
        error = null
        val effectivePercent = if (copy.isOnLoan) percent else 0
        viewModelScope.launch {
            try {
                val result = BorrowRepository.reportDamage(
                    copyId = copy.copyId,
                    category = category,
                    percent = effectivePercent,
                    description = description
                )
                if (result.ok) {
                    val level = levelLabel(category)
                    val message = buildString {
                        append("$level recorded for \"${copy.bookTitle}\" (${copy.copyNo}).")
                        val fee = result.fineAmount ?: 0.0
                        when {
                            effectivePercent == 0 && !copy.isOnLoan ->
                                append(" No compensation - there is no borrower to charge.")
                            effectivePercent == 0 -> append(" No compensation charged.")
                            else -> append(" Fine RM %.2f added.".format(fee))
                        }
                    }
                    onSaved(message)
                } else {
                    error = when (result.message) {
                        "NOT_ALLOWED" -> "Only admin or librarian can record damage."
                        "BAD_CATEGORY" -> "Choose a damage level."
                        "BAD_PERCENT" -> "Choose a valid compensation percentage."
                        "COPY_NOT_FOUND" -> "This copy no longer exists."
                        "ALREADY_REPORTED" -> "This copy already has a damage report."
                        else -> "Could not save the report. Try again."
                    }
                }
            } catch (e: Exception) {
                error = friendlySave(e)
            } finally {
                busy = false
            }
        }
    }

    private fun friendlySave(e: Exception): String {
        if (ErrorText.isNetwork(e)) return ErrorText.OFFLINE
        val m = (e.message ?: "").lowercase()
        return when {
            "row-level security" in m || "permission denied" in m ->
                "You are not allowed to do that."
            else -> "Could not save the report. Try again."
        }
    }
}

/** Edit screen: level + description are editable, compensation is not. */
class DamageEditViewModel : ViewModel() {

    var category by mutableStateOf("minor")
        private set
    var description by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    var confirmingDelete by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun load(report: DamageReport) {
        category = report.category
        description = report.description.orEmpty()
        error = null
        confirmingDelete = false
    }

    fun chooseCategory(value: String) {
        category = value
    }

    fun onDescriptionChange(value: String) {
        if (value.length <= 300) description = value
    }

    fun save(reportId: String, onSaved: (String) -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val result = BorrowRepository.updateDamageReport(reportId, category, description)
                if (result.ok) {
                    onSaved("Damage report updated.")
                } else {
                    error = when (result.message) {
                        "NOT_ALLOWED" -> "Only admin or librarian can edit damage reports."
                        "BAD_CATEGORY" -> "Choose a damage level."
                        "NOT_FOUND" -> "This report no longer exists."
                        else -> "Could not update the report. Try again."
                    }
                }
            } catch (e: Exception) {
                error = friendlyEdit(e)
            } finally {
                busy = false
            }
        }
    }

    fun requestDelete() {
        if (busy) return
        confirmingDelete = true
    }

    fun dismissDelete() {
        confirmingDelete = false
    }

    fun delete(reportId: String, onDeleted: (String) -> Unit) {
        if (busy) return
        confirmingDelete = false
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val result = BorrowRepository.deleteDamageReport(reportId)
                if (result.ok) {
                    val message = buildString {
                        append("Damage report deleted.")
                        val fine = result.fineAmount ?: 0.0
                        if (result.fineStatus != null) {
                            append(" Fine RM %.2f (%s) is gone too.".format(fine, result.fineStatus))
                        }
                    }
                    onDeleted(message)
                } else {
                    error = when (result.message) {
                        "NOT_ALLOWED" -> "Only admin or librarian can delete damage reports."
                        "NOT_FOUND" -> "This report no longer exists."
                        else -> "Could not delete the report. Try again."
                    }
                }
            } catch (e: Exception) {
                error = friendlyEdit(e)
            } finally {
                busy = false
            }
        }
    }

    private fun friendlyEdit(e: Exception): String {
        if (ErrorText.isNetwork(e)) return ErrorText.OFFLINE
        val m = (e.message ?: "").lowercase()
        return when {
            "row-level security" in m || "permission denied" in m ->
                "You are not allowed to do that."
            else -> "Could not save the change. Try again."
        }
    }
}

internal fun levelLabel(category: String): String = when (category) {
    "lost" -> "Lost book"
    "major" -> "Major damage"
    else -> "Minor damage"
}
