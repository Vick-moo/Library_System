package com.example.librarysystem.ui.manage

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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

    Scaffold(
        topBar = {
            LibraryTopAppBar(
                title = "Labels (${LabelCart.items.size})",
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
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${LabelCart.items.size} label${if (LabelCart.items.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (LabelCart.items.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) {
                        Text("Clear all", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

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
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(LabelCart.items, key = { it.key }) { item ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.bookTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "Copy ${item.copyNo} · ${QrCodes.payload(item.isbn, item.copyNo)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { LabelCart.remove(item) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        HorizontalDivider(thickness = 1.dp)
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
