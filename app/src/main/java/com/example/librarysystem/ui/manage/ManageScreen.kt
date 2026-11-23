package com.example.librarysystem.ui.manage

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import android.content.res.Configuration
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.librarysystem.ui.components.LibraryTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(
    onAddBook: () -> Unit,
    onOpenBook: (String) -> Unit,
    vm: ManageViewModel = viewModel()
) {
    // Re-entering the screen (e.g. after saving a book) reuses the cached VM; refresh silently.
    LaunchedEffect(Unit) {
        if (vm.books.isNotEmpty()) vm.load()
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Scaffold(
        topBar = {
            if (!landscape) LibraryTopAppBar(title = "Manage catalogue")
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddBook) {
                Icon(Icons.Filled.Add, contentDescription = "Add book")
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        // adjustResize already shrinks this window when the keyboard opens, so the
        // content must NOT add imePadding on top of it: doing so subtracts the
        // keyboard height twice and squeezes the search field to a sliver.
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = vm::onQueryChange,
                label = { Text("Search title / author / ISBN") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = if (landscape) 4.dp else 16.dp,
                        bottom = if (landscape) 4.dp else 16.dp
                    )
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    vm.loading -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    vm.error != null -> Box(
                        Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(vm.error ?: "") }

                    else -> {
                        val rows = vm.filtered()
                        if (rows.isEmpty()) {
                            Box(
                                Modifier.fillMaxSize().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No books found. Tap + to add one.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(rows, key = { it.book.id }) { row ->
                                    ManageBookRow(
                                        row = row,
                                        onClick = { onOpenBook(row.book.id) }
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 16.dp),
                                        thickness = 1.dp
                                    )
                                }
                                item { Spacer(Modifier.height(88.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageBookRow(
    row: StaffBookRow,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(66.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val cover = row.book.coverUrl
            if (!cover.isNullOrBlank()) {
                AsyncImage(
                    model = cover,
                    contentDescription = row.book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    row.book.title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.book.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${row.book.author} · ${row.book.isbn}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${row.availableCopies} of ${row.totalCopies} available",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
