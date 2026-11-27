package com.example.librarysystem.ui.damage

import com.example.librarysystem.util.QrCodes

import com.example.librarysystem.ui.components.QrScannerDialog

import androidx.compose.runtime.setValue

import androidx.compose.runtime.remember

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.getValue

import androidx.compose.material3.IconButton

import androidx.compose.material.icons.filled.QrCodeScanner

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.librarysystem.data.model.DamageCopy
import com.example.librarysystem.ui.components.LibraryTopAppBar

/**
 * Step 1 of filing a damage report: pick the physical copy.
 * Copies that already carry a report are filtered out server-side.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DamageSearchScreen(
    onBack: () -> Unit,
    onPick: (DamageCopy) -> Unit,
    vm: DamageSearchViewModel = viewModel()
) {
    LaunchedEffect(Unit) { vm.load() }

    var showScanner by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { LibraryTopAppBar(title = "Record damage", onBack = onBack) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = vm::onQueryChange,
                label = { Text("Search copy") },
                placeholder = { Text("copy no / title / ISBN / student") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showScanner = true }) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan a label")
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Text(
                "Pick the exact copy that is damaged or missing.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            vm.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            PullToRefreshBox(
                isRefreshing = vm.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    vm.copies.isEmpty() -> Box(
                        Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (vm.query.isBlank()) {
                                "Every copy already has a damage report."
                            } else {
                                "No copy matches that search."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(vm.copies, key = { it.copyId }) { copy ->
                            DamageSearchRow(copy = copy, onClick = { onPick(copy) })
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

@Composable
private fun DamageSearchRow(copy: DamageCopy, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    copy.bookTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                CopyStatusBadge(copy.copyStatus)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(copy.copyNo)
                    copy.shelf?.let { append(" · Shelf $it") }
                    append(" · RM %.2f".format(copy.bookPrice))
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (copy.isOnLoan) {
                Text(
                    "On loan to ${copy.borrowerName ?: copy.borrowerNo ?: "student"}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    "On the shelf - no borrower",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CopyStatusBadge(status: String) {
    val (bg, fg) = when (status) {
        "borrowed" -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        "unavailable" -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
        "reserved" -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        status.replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
