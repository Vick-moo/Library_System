package com.example.librarysystem.ui.manage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.librarysystem.data.BookRepository
import com.example.librarysystem.ui.components.LabeledField
import com.example.librarysystem.ui.components.LibraryTopAppBar
import kotlinx.coroutines.launch

private val CONDITIONS = listOf("new", "good", "fair", "damaged")
private val STATUSES = listOf("available", "borrowed", "reserved", "unavailable")

class CopyEditViewModel(
    private val bookId: String,
    private val copyId: String?
) : ViewModel() {

    val isEdit: Boolean = copyId != null

    var copyNo by mutableStateOf("")
        private set
    var condition by mutableStateOf("good")
        private set
    var status by mutableStateOf("available")
        private set
    var shelf by mutableStateOf("")
        private set
    var notes by mutableStateOf("")
        private set

    var loading by mutableStateOf(true)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var dirty by mutableStateOf(false)
        private set

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val copies = BookRepository.copiesForStaff(bookId)
                if (copyId != null) {
                    val c = copies.firstOrNull { it.id == copyId }
                    if (c != null) {
                        copyNo = c.copyNo
                        condition = c.condition
                        status = c.status
                        shelf = c.shelfLocation.orEmpty()
                        notes = c.notes.orEmpty()
                    }
                } else {
                    val isbn = BookRepository.bookById(bookId)?.isbn.orEmpty()
                    val max = copies.mapNotNull { c ->
                        Regex("-(\\d+)$").find(c.copyNo)?.groupValues?.get(1)?.toIntOrNull()
                    }.maxOrNull() ?: 0
                    copyNo = "%s-%02d".format(isbn.ifBlank { "COPY" }, max + 1)
                }
            } catch (e: Exception) {
                error = "Could not load the copy."
            } finally {
                loading = false
            }
        }
    }

    fun update(field: String, value: String) {
        when (field) {
            "copyNo" -> copyNo = value
            "condition" -> condition = value
            "status" -> status = value
            "shelf" -> shelf = value
            "notes" -> notes = value
        }
        dirty = true
        error = null
    }

    fun save(onSaved: () -> Unit) {
        if (shelf.isBlank()) {
            error = "Shelf location is required."
            return
        }
        if (copyNo.isBlank()) {
            error = "Copy number is required."
            return
        }
        saving = true
        error = null
        viewModelScope.launch {
            try {
                if (copyId == null) {
                    BookRepository.createCopy(
                        bookId = bookId,
                        copyNo = copyNo.trim(),
                        condition = condition,
                        status = status,
                        shelfLocation = shelf.trim(),
                        notes = notes
                    )
                } else {
                    BookRepository.updateCopy(
                        copyId = copyId,
                        condition = condition,
                        status = status,
                        shelfLocation = shelf.trim(),
                        notes = notes
                    )
                }
                onSaved()
            } catch (e: Exception) {
                error = friendly(e)
            } finally {
                saving = false
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = copyId ?: return
        saving = true
        viewModelScope.launch {
            try {
                BookRepository.softDeleteCopy(id)
                onDeleted()
            } catch (e: Exception) {
                error = friendly(e)
            } finally {
                saving = false
            }
        }
    }

    private fun friendly(e: Exception): String {
        val m = (e.message ?: "").lowercase()
        return when {
            "duplicate key" in m -> "That copy number already exists."
            "row-level security" in m || "permission denied" in m ->
                "You are not allowed to do that."
            "network" in m || "timeout" in m -> "Network error. Try again."
            else -> "Could not save. Try again."
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CopyEditScreen(
    bookId: String,
    copyId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    vm: CopyEditViewModel = viewModel(key = "copyedit-$bookId-$copyId") {
        CopyEditViewModel(bookId, copyId)
    }
) {
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var showRemove by rememberSaveable { mutableStateOf(false) }

    val hasChanges = vm.dirty

    fun requestBack() {
        if (hasChanges && !vm.saving) showDiscard = true else onBack()
    }

    BackHandler(enabled = hasChanges && !vm.saving) { showDiscard = true }

    Scaffold(
        topBar = {
            LibraryTopAppBar(
                title = if (vm.isEdit) "Edit copy" else "Add copy",
                onBack = { requestBack() }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        if (vm.loading) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LabeledField(
                label = "Copy number",
                value = vm.copyNo,
                onValueChange = { vm.update("copyNo", it) },
                placeholder = "e.g. 9780553380163-01",
                maxLength = 40,
                readOnly = vm.isEdit
            )

            Text("Condition", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CONDITIONS.forEach { c ->
                    FilterChip(
                        selected = vm.condition == c,
                        onClick = { vm.update("condition", c) },
                        label = { Text(c) }
                    )
                }
            }

            Text("Status", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                STATUSES.forEach { s ->
                    FilterChip(
                        selected = vm.status == s,
                        onClick = { vm.update("status", s) },
                        label = { Text(s) }
                    )
                }
            }

            LabeledField(
                label = "Shelf location",
                value = vm.shelf,
                onValueChange = { vm.update("shelf", it) },
                placeholder = "e.g. B-12",
                maxLength = 30
            )

            LabeledField(
                label = "Staff notes (optional)",
                value = vm.notes,
                onValueChange = { vm.update("notes", it) },
                placeholder = "e.g. Slightly worn cover",
                singleLine = false,
                maxLines = 5,
                maxLength = 200
            )

            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { vm.save(onSaved) },
                enabled = !vm.saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (vm.saving) "Saving…" else "Save")
            }

            if (vm.isEdit) {
                OutlinedButton(
                    onClick = { showRemove = true },
                    enabled = !vm.saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Remove this copy",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Everything you typed on this page will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    onBack()
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text("Keep editing") }
            }
        )
    }

    if (showRemove) {
        AlertDialog(
            onDismissRequest = { showRemove = false },
            title = { Text("Remove this copy?") },
            text = { Text("This copy will be removed from the catalogue. Borrow records are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemove = false
                    vm.delete(onSaved)
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRemove = false }) { Text("Cancel") }
            }
        )
    }
}
