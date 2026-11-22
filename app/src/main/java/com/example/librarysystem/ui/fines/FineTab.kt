package com.example.librarysystem.ui.fines

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.librarysystem.data.model.FineRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FineTab(
    onCleared: (FineRow) -> Unit,
    vm: FineDeskViewModel = viewModel()
) {
    // The ViewModel outlives the tab (activity scope), so start clean on entry.
    LaunchedEffect(Unit) { vm.startFresh() }

    Column(Modifier.fillMaxSize()) {
        SummaryBar(vm.fines, vm.filter)

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = vm.filter == "unpaid",
                onClick = { vm.chooseFilter("unpaid") },
                label = { Text("Unpaid") }
            )
            FilterChip(
                selected = vm.filter == "paid",
                onClick = { vm.chooseFilter("paid") },
                label = { Text("Paid") }
            )
            FilterChip(
                selected = vm.filter == "waived",
                onClick = { vm.chooseFilter("waived") },
                label = { Text("Waived") }
            )
        }

        OutlinedTextField(
            value = vm.query,
            onValueChange = vm::onQueryChange,
            label = { Text("Search fines") },
            placeholder = { Text("student no / name / book") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        if (!vm.canSettle) {
            Text(
                "Only admin or librarian staff can settle fines.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        vm.notice?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
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

                vm.fines.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Box(
                            Modifier.fillParentMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                when (vm.filter) {
                                    "unpaid" -> "No unpaid fines. Everything is settled."
                                    "paid" -> "No paid fines yet."
                                    else -> "No waived fines yet."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> LazyColumn(
                    Modifier.fillMaxSize().imePadding(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(vm.fines, key = { it.id }) { fine ->
                        FineCard(
                            fine = fine,
                            canSettle = vm.canSettle && fine.isUnpaid && !vm.busy,
                            onPay = { vm.askPay(fine) },
                            onWaive = { vm.askWaive(fine) }
                        )
                    }
                }
            }
        }
    }

    vm.pendingPay?.let { fine ->
        PayDialog(
            fine = fine,
            onConfirm = { amount -> vm.confirmPay(amount, onCleared) },
            onDismiss = vm::dismissPay
        )
    }

    vm.pendingWaive?.let { fine ->
        AlertDialog(
            onDismissRequest = vm::dismissWaive,
            title = { Text("Waive this fine?") },
            text = {
                Text(
                    "RM %.2f for %s will be cleared without payment. This cannot be undone."
                        .format(fine.amount, fine.studentName.ifBlank { fine.studentNo })
                )
            },
            confirmButton = {
                TextButton(onClick = vm::confirmWaive) { Text("Waive") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissWaive) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PayDialog(
    fine: FineRow,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    val parsed = amount.trim().toDoubleOrNull()
    val matches = parsed != null && abs(parsed - fine.amount) < 0.005
    val typedSomething = amount.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Collect payment") },
        text = {
            Column {
                Text(
                    "${fine.studentName.ifBlank { fine.studentNo }} · ${fine.typeLabel}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { new ->
                        // digits + one decimal point only
                        if (new.length <= 8 && new.all { it.isDigit() || it == '.' }) {
                            amount = new
                        }
                    },
                    label = { Text("Amount received (RM)") },
                    placeholder = { Text("%.2f".format(fine.amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Expected: RM %.2f".format(fine.amount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (typedSomething && !matches) {
                    Text(
                        "Amount does not match this fine.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(amount) }, enabled = matches) {
                Text("Confirm payment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SummaryBar(fines: List<FineRow>, filter: String) {
    val unpaid = fines.filter { it.isUnpaid }
    val total = unpaid.sumOf { it.amount }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (filter == "unpaid") {
            Text(
                "RM %.2f outstanding".format(total),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (unpaid.size == 1) "1 unpaid fine" else "${unpaid.size} unpaid fines",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                if (fines.size == 1) "1 fine" else "${fines.size} fines",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun FineCard(
    fine: FineRow,
    canSettle: Boolean,
    onPay: () -> Unit,
    onWaive: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        fine.studentName.ifBlank { fine.studentNo },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        fine.studentNo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "RM %.2f".format(fine.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                FineTypeBadge(fine)
                Spacer(Modifier.width(6.dp))
                if (fine.status != "unpaid") {
                    FineStatusPill(fine.status)
                }
            }

            fine.bookTitle?.let { title ->
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        append(title)
                        fine.copyNo?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append("Raised ")
                    append(fineShortDate(fine.createdAt))
                    if (fine.status != "unpaid") {
                        append(" · ")
                        append(fine.status.replaceFirstChar { it.uppercase() })
                        fine.handledBy?.let { append(" by $it") }
                        fine.paidAt?.let { append(" · ").append(fineShortDate(it)) }
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (canSettle) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onWaive) { Text("Waive") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onPay) { Text("Mark paid") }
                }
            }
        }
    }
}

@Composable
private fun FineTypeBadge(fine: FineRow) {
    val bg = when (fine.fineType) {
        "overdue" -> MaterialTheme.colorScheme.secondaryContainer
        "damage" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val fg = when (fine.fineType) {
        "overdue" -> MaterialTheme.colorScheme.onSecondaryContainer
        "damage" -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }
    Text(
        fine.typeLabel,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun FineStatusPill(status: String) {
    val (bg, fg) = when (status) {
        "paid" -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        "waived" -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
    }
    Text(
        status.replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

private fun fineShortDate(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return try {
        LocalDate.parse(raw.take(10))
            .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
    } catch (_: Exception) {
        raw.take(10)
    }
}
