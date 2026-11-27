package com.example.librarysystem.ui.manage

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.librarysystem.data.LabelCart
import com.example.librarysystem.data.LabelCartIntro
import com.example.librarysystem.data.LabelItem
import com.example.librarysystem.ui.components.LibraryTopAppBar
import com.example.librarysystem.util.LabelPdf
import com.example.librarysystem.util.QrCodes

@Composable
fun LabelCartScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { LabelCart.init(context) }

    var showIntro by remember { mutableStateOf(LabelCartIntro.shouldShow(context)) }
    var confirmClear by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    // One row per book, alphabetical; copies stay in copy-number order.
    val books = remember(LabelCart.items) {
        LabelCart.items
            .groupBy { it.isbn.orEmpty() to it.bookTitle }
            .toList()
            .sortedBy { it.second.firstOrNull()?.bookTitle?.lowercase() ?: "" }
    }

    Scaffold(
        topBar = {
            LibraryTopAppBar(
                title = "QR Labels",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showIntro = true }) {
                        Icon(Icons.Filled.HelpOutline, contentDescription = "How labels work")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Total labels: ${LabelCart.items.size}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (LabelCart.items.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) {
                        Text("Clear all", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp)

            if (LabelCart.items.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No labels yet.\nAdd copies from a book's copy list.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(books, key = { it.first.first + "|" + it.first.second }) { entry ->
                        val copies = entry.second.sortedBy { it.copyNo }
                        val first = copies.firstOrNull() ?: return@items
                        BookLabelRow(
                            item = first,
                            copies = copies,
                            onRemoveCopy = { LabelCart.remove(it) },
                            onRemoveBook = {
                                LabelCart.removeBook(first.isbn, first.bookTitle)
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 1.dp
                        )
                    }
                }

                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        try {
                            val file = LabelPdf.write(context, LabelCart.items)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Print or share labels"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not build the PDF", Toast.LENGTH_SHORT).show()
                        } finally {
                            busy = false
                        }
                    },
                    enabled = !busy && LabelCart.items.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(if (busy) "Building PDF…" else "Download PDF (${LabelCart.items.size})")
                }
            }
        }
    }

    if (showIntro) {
        AlertDialog(
            onDismissRequest = {
                LabelCartIntro.markSeen(context)
                showIntro = false
            },
            title = { Text("Label cart") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Add QR labels from a book's copy list, then export them all at once.")
                    Text(
                        "· Tap the cart icon on a copy to add it",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "· Or long-press a book in Manage to add every copy",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "· Export makes one A4 PDF, 24 labels per page",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    LabelCartIntro.markSeen(context)
                    showIntro = false
                }) { Text("Got it") }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Remove all labels?") },
            text = { Text("This removes ${LabelCart.items.size} labels from the cart.") },
            confirmButton = {
                TextButton(onClick = {
                    LabelCart.clear()
                    confirmClear = false
                }) { Text("Remove all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BookLabelRow(
    item: LabelItem,
    copies: List<LabelItem>,
    onRemoveCopy: (LabelItem) -> Unit,
    onRemoveBook: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(40.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!item.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    "IMG",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.bookTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                item.isbn.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                copies.forEach { copy ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            copy.copyNo,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = { onRemoveCopy(copy) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove ${copy.copyNo}",
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
        IconButton(onClick = onRemoveBook) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Remove this book",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
