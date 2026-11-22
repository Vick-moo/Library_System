package com.example.librarysystem.ui.damage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.librarysystem.data.AuthRepository
import com.example.librarysystem.data.BorrowRepository
import com.example.librarysystem.data.model.DamageCopy
import kotlinx.coroutines.launch

class DamageViewModel : ViewModel() {

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
                copies = BorrowRepository.damageCopies(query)
                error = null
            } catch (_: Exception) {
                copies = emptyList()
                error = "Could not load copies. Pull down to try again."
            } finally {
                loading = false
            }
        }
    }

    fun refresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            try {
                copies = BorrowRepository.damageCopies(query)
                error = null
            } catch (_: Exception) {
                error = "Could not refresh. Check the connection."
            } finally {
                refreshing = false
            }
        }
    }
}

class DamageFormViewModel : ViewModel() {

    var category by mutableStateOf("minor")
        private set
    var percent by mutableStateOf(50)
        private set
    var description by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun chooseCategory(value: String) {
        category = value
    }

    fun choosePercent(value: Int) {
        percent = value
    }

    fun onDescriptionChange(value: String) {
        if (value.length <= 300) description = value
    }

    fun reset() {
        category = "minor"
        percent = 50
        description = ""
        error = null
    }

    fun submit(copy: DamageCopy, onSaved: (String) -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val result = BorrowRepository.reportDamage(
                    copyId = copy.copyId,
                    category = category,
                    percent = percent,
                    description = description
                )
                if (result.ok) {
                    val level = when (category) {
                        "minor" -> "Minor damage"
                        "major" -> "Major damage"
                        else -> "Lost book"
                    }
                    val message = buildString {
                        append("$level recorded for \"${copy.bookTitle}\" ")
                        append("(${copy.copyNo}, $percent%). ")
                        val fee = result.fineAmount ?: 0.0
                        if (percent == 0) append("No compensation charged.")
                        else append("Fine RM %.2f added.".format(fee))
                    }
                    onSaved(message)
                } else {
                    error = when (result.message) {
                        "NOT_ALLOWED" -> "Only admin or librarian can record damage."
                        "BAD_CATEGORY" -> "Choose a damage level."
                        "BAD_PERCENT" -> "Choose a valid compensation percentage."
                        "COPY_NOT_FOUND" -> "This copy no longer exists."
                        else -> "Could not save the report. Try again."
                    }
                }
            } catch (e: Exception) {
                val m = (e.message ?: "").lowercase()
                error = when {
                    "row-level security" in m || "permission denied" in m ->
                        "You are not allowed to do that."
                    "network" in m || "timeout" in m -> "Network error. Try again."
                    else -> "Could not save the report. Try again."
                }
            } finally {
                busy = false
            }
        }
    }
}
