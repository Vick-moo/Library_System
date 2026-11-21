package com.example.librarysystem.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookFormScreen(
    bookId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    vm: BookFormViewModel = viewModel(key = bookId ?: "new") { BookFormViewModel(bookId) }
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEdit) "Edit book" else "Add book") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Field("ISBN", vm.isbn, vm.isEdit) { vm.update("isbn", it) }
            Field("Title", vm.title) { vm.update("title", it) }
            Field("Author", vm.author) { vm.update("author", it) }
            Field("Publisher", vm.publisher) { vm.update("publisher", it) }
            Field(
                "Publish year", vm.publishYear,
                keyboard = KeyboardType.Number
            ) { vm.update("year", it) }
            Field("Category", vm.category) { vm.update("category", it) }
            Field("Language", vm.language) { vm.update("language", it) }
            Field(
                "Pages", vm.pages,
                keyboard = KeyboardType.Number
            ) { vm.update("pages", it) }
            Field(
                "Price (RM)", vm.price,
                keyboard = KeyboardType.Decimal
            ) { vm.update("price", it) }
            Field("Description", vm.description, singleLine = false) {
                vm.update("description", it)
            }

            if (!vm.isEdit) {
                Text(
                    "Copies",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Field(
                    "How many copies?", vm.copyCount,
                    keyboard = KeyboardType.Number
                ) { vm.update("copies", it) }
                Field("Shelf location (e.g. B-12)", vm.shelfLocation) {
                    vm.update("shelf", it)
                }
            }

            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { vm.save(onSaved) },
                enabled = !vm.saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (vm.saving) "Saving…" else "Save")
            }

            if (vm.isEdit) {
                OutlinedButton(
                    onClick = { vm.delete(onSaved) },
                    enabled = !vm.saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Archive this book",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        readOnly = readOnly,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth()
    )
}
