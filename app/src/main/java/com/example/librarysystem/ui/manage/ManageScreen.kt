package com.example.librarysystem.ui.manage

import androidx.compose.runtime.setValue

import androidx.compose.runtime.remember

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.getValue

import com.example.librarysystem.util.QrCodes

import com.example.librarysystem.ui.components.ScanIconButton
import com.example.librarysystem.ui.components.QrScannerDialog

import androidx.compose.material.icons.filled.QrCodeScanner

import com.example.librarysystem.data.LabelItem

import com.example.librarysystem.data.LabelCart

import android.widget.Toast

import androidx.compose.ui.platform.LocalContext

import androidx.compose.material3.BadgedBox

import androidx.compose.material3.Badge

import androidx.compose.material.icons.filled.ShoppingCart

import androidx.compose.foundation.combinedClickable

import androidx.compose.foundation.ExperimentalFoundationApi

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
    onOpenCart: () -> Unit,
    vm: ManageViewModel = viewModel()
) {
    // Re-entering the screen (e.g. after saving a book) reuses the cached VM; refresh silently.
    LaunchedEffect(Unit) {
        if (vm.books.isNotEmpty()) vm.load()
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    var showScanner by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            if (!landscape) {
                LibraryTopAppBar(
                    title = "Manage catalogue",
                    actions = {
                        IconButton(onClick = onOpenCart) {
                            BadgedBox(
                                badge = {
                                    if (LabelCart.items.isNotEmpty()) {
                                        Badge { Text("${LabelCart.items.size}") }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Filled.ShoppingCart,
                                    contentDescription = "Label cart"
                                )
                            }
                        }
                    }
                )
            }
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
                trailingIcon = {
                    ScanIconButton(onOpen = { showScanner = true })
                },
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
                                        onClick = { onOpenBook(row.book.id) },
                                        onLongPress = {
                                            val copies = vm.copiesByBook[row.book.id].orEmpty()
                                                .filterNot { it.isDeleted }
                                            if (copies.isEmpty()) {
                                                Toast.makeText(
                                                    context,
                                                    "This book has no copies to label",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                val added = LabelCart.addAll(
                                                    copies.map {
                                                        LabelItem(
                                                            isbn = row.book.isbn,
                                                            copyNo = it.copyNo,
                                                            bookTitle = row.book.title,
                                                            coverUrl = row.book.coverUrl
                                                        )
                                                    }
                                                )
                                                Toast.makeText(
                                                    context,
                                                    if (added == 0) "Already in the label cart"
                                                    else "Added $added label${if (added == 1) "" else "s"}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
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

    if (showScanner) {
        QrScannerDialog(
            onResult = { raw -> vm.onQueryChange(QrCodes.isbnOf(raw)) },
            onDismiss = { showScanner = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManageBookRow(
    row: StaffBookRow,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
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
