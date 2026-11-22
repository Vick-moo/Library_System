package com.example.librarysystem.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.librarysystem.ui.components.LabeledField
import com.example.librarysystem.ui.components.LibraryTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookFormScreen(
    bookId: String?,
    formKey: Int,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit = onSaved,
    vm: BookFormViewModel = viewModel(key = "bookform-${bookId ?: "new"}-$formKey") {
        BookFormViewModel(bookId)
    }
) {
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var showArchive by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val pickCover = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.setCover(uri) }

    val hasChanges = vm.dirty || (!vm.isEdit && (
        vm.isbn.isNotBlank() || vm.title.isNotBlank() || vm.author.isNotBlank()
        ))

    fun requestBack() {
        if (hasChanges && !vm.saving) showDiscard = true else onBack()
    }

    BackHandler(enabled = hasChanges && !vm.saving) { showDiscard = true }

    Scaffold(
        topBar = {
            LibraryTopAppBar(
                title = if (vm.isEdit) "Edit book" else "Add book",
                onBack = { requestBack() }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        if (vm.loading) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
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
                Column {
                    Row {
                        Text(
                            "Cover",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            " *",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(84.dp, 112.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            val model = vm.coverUri ?: vm.existingCoverUrl
                            if (model != null) {
                                AsyncImage(
                                    model = model,
                                    contentDescription = "Cover",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            OutlinedButton(onClick = {
                                pickCover.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }) {
                                Text(if (vm.coverUri == null && vm.existingCoverUrl == null) "Choose image" else "Change image")
                            }
                            if (vm.coverUri != null) {
                                TextButton(onClick = { vm.setCover(null) }) {
                                    Text("Clear selection", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            item {
                LabeledField(
                    label = "ISBN",
                    value = vm.isbn,
                    onValueChange = { vm.update("isbn", it) },
                    placeholder = "e.g. 9780553380163",
                    required = true,
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
                    required = true,
                    maxLength = 200
                )
            }
            item {
                LabeledField(
                    label = "Author",
                    value = vm.author,
                    onValueChange = { vm.update("author", it) },
                    placeholder = "e.g. Stephen Hawking",
                    required = true,
                    maxLength = 120
                )
            }
            item {
                LabeledField(
                    label = "Publisher",
                    value = vm.publisher,
                    onValueChange = { vm.update("publisher", it) },
                    placeholder = "e.g. Bantam",
                    required = true,
                    maxLength = 120
                )
            }
            item {
                LabeledField(
                    label = "Publish year",
                    value = vm.publishYear,
                    onValueChange = { vm.update("year", it) },
                    placeholder = "e.g. 1998",
                    required = true,
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
                    required = true,
                    maxLength = 50
                )
            }
            item {
                LabeledField(
                    label = "Language",
                    value = vm.language,
                    onValueChange = { vm.update("language", it) },
                    placeholder = "e.g. English",
                    required = true,
                    maxLength = 50
                )
            }
            item {
                LabeledField(
                    label = "Pages",
                    value = vm.pages,
                    onValueChange = { vm.update("pages", it) },
                    placeholder = "e.g. 212",
                    required = true,
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
                    required = true,
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
                    required = true,
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
                        required = true,
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
                        required = true,
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
                    onClick = {
                        val bytes = vm.coverUri?.let { uri ->
                            runCatching {
                                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            }.getOrNull()
                        }
                        val ext = vm.coverUri?.let { uri ->
                            context.contentResolver.getType(uri)
                                ?.substringAfterLast('/')
                                ?.let { if (it == "jpeg") "jpg" else it }
                        } ?: "jpg"
                        vm.save(bytes, ext, onSaved)
                    },
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
                    vm.delete(onDeleted)
                }) { Text("Archive", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showArchive = false }) { Text("Cancel") }
            }
        )
    }
}
