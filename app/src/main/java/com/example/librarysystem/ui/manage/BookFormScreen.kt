package com.example.librarysystem.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.librarysystem.ui.components.LabeledField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookFormScreen(
    bookId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    vm: BookFormViewModel = viewModel(key = bookId ?: "new") { BookFormViewModel(bookId) }
) {
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var showArchive by rememberSaveable { mutableStateOf(false) }

    val hasChanges = vm.dirty || (!vm.isEdit && (
        vm.isbn.isNotBlank() || vm.title.isNotBlank() || vm.author.isNotBlank()
        ))

    fun requestBack() {
        if (hasChanges && !vm.saving) showDiscard = true else onBack()
    }

    BackHandler(enabled = hasChanges && !vm.saving) { showDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEdit) "Edit book" else "Add book") },
                navigationIcon = {
                    IconButton(onClick = { requestBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        if (vm.loading) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                LabeledField(
                    label = "ISBN",
                    value = vm.isbn,
                    onValueChange = { vm.update("isbn", it) },
                    placeholder = "e.g. 9780553380163",
                    maxLength = 20,
                    readOnly = vm.isEdit,
                    keyboard = KeyboardType.Number
                )
            }
            item {
                LabeledField(
                    label = "Title",
                    value = vm.title,
                    onValueChange = { vm.update("title", it) },
                    placeholder = "e.g. A Brief History of Time",
                    maxLength = 200
                )
            }
            item {
                LabeledField(
                    label = "Author",
                    value = vm.author,
                    onValueChange = { vm.update("author", it) },
                    placeholder = "e.g. Stephen Hawking",
                    maxLength = 120
                )
            }
            item {
                LabeledField(
                    label = "Publisher",
                    value = vm.publisher,
                    onValueChange = { vm.update("publisher", it) },
                    placeholder = "e.g. Bantam",
                    maxLength = 120
                )
            }
            item {
                LabeledField(
                    label = "Publish year",
                    value = vm.publishYear,
                    onValueChange = { vm.update("year", it) },
                    placeholder = "e.g. 1998",
                    maxLength = 4,
                    keyboard = KeyboardType.Number
                )
            }
            item {
                LabeledField(
                    label = "Category",
                    value = vm.category,
                    onValueChange = { vm.update("category", it) },
                    placeholder = "e.g. Science",
                    maxLength = 50
                )
            }
            item {
                LabeledField(
                    label = "Language",
                    value = vm.language,
                    onValueChange = { vm.update("language", it) },
                    placeholder = "e.g. English",
                    maxLength = 50
                )
            }
            item {
                LabeledField(
                    label = "Pages",
                    value = vm.pages,
                    onValueChange = { vm.update("pages", it) },
                    placeholder = "e.g. 212",
                    maxLength = 6,
                    keyboard = KeyboardType.Number
                )
            }
            item {
                LabeledField(
                    label = "Price (RM)",
                    value = vm.price,
                    onValueChange = { vm.update("price", it) },
                    placeholder = "e.g. 45.90",
                    maxLength = 10,
                    keyboard = KeyboardType.Decimal
                )
            }
            item {
                LabeledField(
                    label = "Description",
                    value = vm.description,
                    onValueChange = { vm.update("description", it) },
                    placeholder = "e.g. Short summary of the book…",
                    singleLine = false,
                    maxLines = 10,
                    maxLength = 1000
                )
            }

            if (!vm.isEdit) {
                item {
                    Text(
                        "Copies",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                item {
                    LabeledField(
                        label = "How many copies?",
                        value = vm.copyCount,
                        onValueChange = { vm.update("copies", it) },
                        placeholder = "e.g. 1",
                        maxLength = 3,
                        keyboard = KeyboardType.Number
                    )
                }
                item {
                    LabeledField(
                        label = "Shelf location",
                        value = vm.shelfLocation,
                        onValueChange = { vm.update("shelf", it) },
                        placeholder = "e.g. B-12",
                        maxLength = 30
                    )
                }
            }

            vm.error?.let { msg ->
                item {
                    Text(msg, color = MaterialTheme.colorScheme.error)
                }
            }

            item {
                Button(
                    onClick = { vm.save(onSaved) },
                    enabled = !vm.saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (vm.saving) "Saving…" else "Save")
                }
            }

            if (vm.isEdit) {
                item {
                    OutlinedButton(
                        onClick = { showArchive = true },
                        enabled = !vm.saving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Archive this book",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
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

    if (showArchive) {
        AlertDialog(
            onDismissRequest = { showArchive = false },
            title = { Text("Archive this book?") },
            text = {
                Text(
                    "The book and ALL of its copies will be removed from the catalogue. " +
                        "Existing borrow records are kept."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showArchive = false
                    vm.delete(onSaved)
                }) { Text("Archive", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showArchive = false }) { Text("Cancel") }
            }
        )
    }
}
