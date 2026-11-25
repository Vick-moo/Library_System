package com.example.librarysystem.ui.seats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.librarysystem.data.DynamicCode
import com.example.librarysystem.data.model.SeatBooking
import com.example.librarysystem.data.model.Space
import com.example.librarysystem.ui.components.LibraryTopAppBar
import kotlinx.coroutines.delay

/**
 * M6 student screen (SYSTEM-SPEC §5.7): spaces → date → 1-hour slot → book,
 * plus My bookings with self check-in via the rotating kiosk code.
 */
@Composable
fun SeatsScreen(
    onOpenCalendar: () -> Unit = {},
    vm: SeatsViewModel = viewModel()
) {
    var tab by remember { mutableStateOf(0) } // 0 spaces, 1 my bookings
    Scaffold(
        topBar = {
            LibraryTopAppBar(
                title = "Seats",
                actions = {
                    TextButton(onClick = onOpenCalendar) { Text("Calendar") }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Spaces") })
                Spacer(Modifier.padding(4.dp))
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("My bookings") })
            }
            when (val s = vm.state) {
                is SeatsUiState.Loading -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is SeatsUiState.Error -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) { Text(s.message) }

                is SeatsUiState.Ready -> {
                    if (s.demoMode) {
                        Text(
                            "Demo mode — space tables not in Supabase yet, bookings stay on this device.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    if (tab == 0) SpacesTab(vm, s.spaces) else BookingsTab(vm, s.myBookings)
                    vm.notice?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpacesTab(vm: SeatsViewModel, spaces: List<Space>) {
    val types = listOf("all", "seat", "study_room", "discussion_room", "games_room")
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                types.forEach { t ->
                    FilterChip(
                        selected = vm.typeFilter == t,
                        onClick = { vm.setType(t) },
                        label = { Text(if (t == "all") "All" else t.replace('_', ' ')) }
                    )
                }
            }
        }
        val shown = if (vm.typeFilter == "all") spaces else spaces.filter { it.type == vm.typeFilter }
        if (shown.isEmpty()) {
            item { Text("No spaces in this category.") }
        }
        items(shown, key = { it.id }) { space ->
            SpaceCard(
                space = space,
                expanded = vm.selectedSpaceId == space.id,
                selectedDate = vm.selectedDate,
                days = vm.days,
                slots = vm.slots,
                occupied = vm.occupied,
                busy = vm.busy,
                onPick = { date -> vm.pickSpaceDate(space.id, date) },
                onBook = { slot -> vm.book(space.id, vm.selectedDate, slot) }
            )
        }
    }
}

@Composable
private fun SpaceCard(
    space: Space,
    expanded: Boolean,
    selectedDate: String,
    days: List<String>,
    slots: List<String>,
    occupied: Map<String, Int>,
    busy: Boolean,
    onPick: (String) -> Unit,
    onBook: (String) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(space.name, fontWeight = FontWeight.Bold)
                    Text(
                        "${space.typeLabel} · seats ${space.capacity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(onClick = { onPick(selectedDate) }, label = { Text(if (expanded) "Hide" else "Book") })
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Text("Date (this week + next week)", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.forEach { d ->
                        FilterChip(
                            selected = d == selectedDate,
                            onClick = { onPick(d) },
                            label = { Text(d.substring(5), fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Time (1 hour each)", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    slots.forEach { slot ->
                        val full = (occupied[slot] ?: 0) >= space.capacity
                        FilterChip(
                            selected = false,
                            enabled = !full && !busy,
                            onClick = { onBook(slot) },
                            label = { Text(if (full) "$slot full" else slot, fontSize = 12.sp) }
                        )
                    }
                }
                Text(
                    "Student books directly — no approval. 15 min late = auto release.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BookingsTab(vm: SeatsViewModel, bookings: List<SeatBooking>) {
    var checkInTarget by remember { mutableStateOf<SeatBooking?>(null) }
    val active = bookings.filter { it.status in listOf("booked", "checked_in") }
    val past = bookings.filter { it.status !in listOf("booked", "checked_in") }
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (active.isEmpty() && past.isEmpty()) {
            item { Text("No bookings yet. Pick a space to start.") }
        }
        items(active, key = { it.id }) { b ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (b.status == "checked_in")
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(b.spaceName ?: b.spaceId, fontWeight = FontWeight.SemiBold)
                    Text("${b.date} ${b.startTime}–${b.endTime} · ${b.status.replace('_', ' ')}")
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (b.status == "booked") {
                            Button(onClick = { checkInTarget = b }, enabled = !vm.busy) {
                                Text("Check in")
                            }
                            OutlinedButton(onClick = { vm.cancel(b.id) }, enabled = !vm.busy) {
                                Text("Cancel", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
        if (past.isNotEmpty()) {
            item { Text("History", fontWeight = FontWeight.SemiBold) }
            items(past.take(20), key = { it.id }) { b ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(b.spaceName ?: b.spaceId, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${b.date} ${b.startTime} · ${b.status.replace('_', ' ')}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    checkInTarget?.let { b ->
        CheckInDialog(
            booking = b,
            busy = vm.busy,
            onDismiss = { checkInTarget = null },
            onConfirm = { code ->
                checkInTarget = null
                vm.checkIn(b, code)
            }
        )
    }
}

/**
 * Self check-in: the kiosk tablet in the room shows the same rotating code.
 * Prototype shows the code + countdown; the student types it (scan = same).
 */
@Composable
private fun CheckInDialog(
    booking: SeatBooking,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var kiosk by remember { mutableStateOf(DynamicCode.current(booking.spaceId)) }
    var left by remember { mutableStateOf(DynamicCode.secondsLeft()) }
    LaunchedEffect(booking.spaceId) {
        while (true) {
            kiosk = DynamicCode.current(booking.spaceId)
            left = DynamicCode.secondsLeft()
            delay(1000)
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan kiosk to check in") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${booking.spaceName ?: booking.spaceId} · ${booking.date} ${booking.startTime}")
                Text(
                    "Kiosk code (rotates every 3 min):",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(kiosk, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Refreshes in ${left}s — screenshots expire.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it.filter(Char::isDigit) },
                    label = { Text("Enter kiosk code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(if (code.isBlank()) kiosk else code) }, enabled = !busy) {
                Text("Check in")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } }
    )
}
