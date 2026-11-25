package com.example.librarysystem.ui.borrow

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.librarysystem.ui.components.LibraryTopAppBar

/**
 * M5 student "Borrow" tab: active loans + hold queue (SYSTEM-SPEC §5.5, §5.6).
 * Guest (logged out) → login prompt, handled by MainActivity routing.
 */
@Composable
fun MyBorrowScreen(
    onOpenCalendar: () -> Unit = {},
    onBrowseBooks: () -> Unit = {},
    vm: MyBorrowViewModel = viewModel()
) {
    Scaffold(
        topBar = {
            LibraryTopAppBar(
                title = "Borrow",
                actions = {
                    TextButton(onClick = onOpenCalendar) { Text("Calendar") }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        when (val s = vm.state) {
            is MyBorrowUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is MyBorrowUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text(s.message) }

            is MyBorrowUiState.Ready -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ---- loans ----
                item {
                    Text("My loans", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (s.loans.isEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text("No books on loan.", style = MaterialTheme.typography.bodyMedium)
                                if (s.unpaidTotal > 0) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Unpaid fines: RM %.2f — clear at the counter before borrowing.".format(s.unpaidTotal),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(s.loans, key = { it.borrowId }) { loan ->
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = if (loan.overdueDays > 0)
                                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            else CardDefaults.cardColors()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(loan.bookTitle, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Due ${loan.dueDate}" + if (loan.overdueDays > 0) " · ${loan.overdueDays}d overdue" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (loan.overdueDays > 0) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ---- reservations ----
                item {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("My reservations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${s.reservations.count { it.reservation.status in listOf("waiting", "ready") }}/2 active")
                    }
                    Text(
                        "Queue is FIFO. Pick up within 2 days or it expires to the next student.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val active = s.reservations.filter { it.reservation.status in listOf("waiting", "ready") }
                val history = s.reservations.filter { it.reservation.status !in listOf("waiting", "ready") }
                if (active.isEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text("No active reservations.", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = onBrowseBooks, modifier = Modifier.fillMaxWidth()) {
                                    Text("Browse books")
                                }
                            }
                        }
                    }
                } else {
                    items(active, key = { it.reservation.id }) { item ->
                        ReservationRow(
                            item = item,
                            busy = vm.busy,
                            onCancel = { vm.cancelReservation(item.reservation.id) }
                        )
                    }
                }
                if (history.isNotEmpty()) {
                    item {
                        Text("History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    items(history.take(10), key = { it.reservation.id }) { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(item.bookTitle, fontWeight = FontWeight.SemiBold)
                                Text(
                                    item.reservation.status.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                vm.notice?.let { note ->
                    item { Text(note, color = MaterialTheme.colorScheme.primary) }
                }
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    Button(onClick = onOpenCalendar, modifier = Modifier.fillMaxWidth()) {
                        Text("View booking calendar")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReservationRow(item: ReservationItem, busy: Boolean, onCancel: () -> Unit) {
    val res = item.reservation
    Card(
        Modifier.fillMaxWidth(),
        colors = if (res.status == "ready")
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(item.bookTitle, fontWeight = FontWeight.SemiBold)
            Text(
                if (res.status == "ready") "Ready for pickup"
                else "Waiting" + (item.queuePosition?.let { " · #$it in queue" } ?: ""),
                style = MaterialTheme.typography.bodySmall
            )
            val start = res.pickupStartDate
            val end = res.pickupEndDate
            if (start != null && end != null) {
                Text(
                    "Pick up between $start and $end",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
