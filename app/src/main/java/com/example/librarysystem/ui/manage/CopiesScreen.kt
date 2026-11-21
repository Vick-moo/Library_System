package com.example.librarysystem.ui.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.librarysystem.data.model.BookCopy
import androidx.compose.runtime.LaunchedEffect

private val CONDITIONS = listOf("new", "good", "fair", "damaged")
private val STATUSES = listOf("available", "borrowed", "reserved", "unavailable")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopiesScreen(
    bookId: String,
    onBack: () -> Unit,
    vm: CopiesViewModel = viewModel(key = "copies-$bookId") { CopiesViewModel(bookId) }
) {
    var editing by remember { mutableStateOf<BookCopy?>(null) }
    var adding by remember { mutableStateOf(false) }

    // Re-enter refresh (cached ViewModel).
    LaunchedEffect(Unit) {
        if (vm.copies.isNotEmpty()) vm.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Copies") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { adding = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add copy")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            vm.notice?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            when {
                vm.loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                vm.error != null -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { Text(vm.error ?: "") }

                vm.copies.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No copies yet. Tap + to add one.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(vm.copies, key = { it.id }) { copy ->
                        CopyRow(copy) { editing = copy }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 1.dp
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (adding) {
        CopyDialog(
            title = "Add copy",
            initialCopyNo = vm.nextCopyNo(),
            initialCondition = "good",
            initialStatus = "available",
            initialShelf = "",
            initialNotes = "",
            showDelete = false,
            onDismiss = { adding = false },
            onSave = { no, cond, status, shelf, notes ->
                vm.save(null, no, cond, status, shelf, notes) { adding = false }
            },
            onDelete = {}
        )
    }

    editing?.let { copy ->
        CopyDialog(
            title = "Edit ${copy.copyNo}",
            initialCopyNo = copy.copyNo,
            initialCondition = copy.condition,
            initialStatus = copy.status,
            initialShelf = copy.shelfLocation.orEmpty(),
            initialNotes = copy.notes.orEmpty(),
            showDelete = true,
            onDismiss = { editing = null },
            onSave = { _, cond, status, shelf, notes ->
                vm.save(copy, copy.copyNo, cond, status, shelf, notes) { editing = null }
            },
            onDelete = { vm.delete(copy) { editing = null } }
        )
    }
}

@Composable
private fun CopyRow(copy: BookCopy, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                copy.copyNo,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${copy.condition} · ${copy.status}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Shelf ${copy.shelfLocation ?: "-"}" +
                    (copy.notes?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            if (copy.isDeleted) "removed" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyDialog(
    title: String,
    initialCopyNo: String,
    initialCondition: String,
    initialStatus: String,
    initialShelf: String,
    initialNotes: String,
    showDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit,
    onDelete: () -> Unit
) {
    var copyNo by remember { mutableStateOf(initialCopyNo) }
    var condition by remember { mutableStateOf(initialCondition) }
    var status by remember { mutableStateOf(initialStatus) }
    var shelf by remember { mutableStateOf(initialShelf) }
    var notes by remember { mutableStateOf(initialNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = copyNo,
                    onValueChange = { copyNo = it },
                    label = { Text("Copy number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Condition", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CONDITIONS.forEach { c ->
                        FilterChip(
                            selected = condition == c,
                            onClick = { condition = c },
                            label = { Text(c) }
                        )
                    }
                }
                Text("Status", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    STATUSES.forEach { s ->
                        FilterChip(
                            selected = status == s,
                            onClick = { status = s },
                            label = { Text(s) }
                        )
                    }
                }
                OutlinedTextField(
                    value = shelf,
                    onValueChange = { shelf = it },
                    label = { Text("Shelf location (e.g. B-12)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { if (it.length <= 200) notes = it },
                    label = { Text("Staff notes (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(copyNo, condition, status, shelf, notes) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (showDelete) {
                    TextButton(onClick = onDelete) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
