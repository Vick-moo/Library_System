package com.example.librarysystem.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.librarysystem.ui.components.LibraryTopAppBar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Feature C — month grid. Bars would overflow on 30-day views, so each day
 * shows up to 3 colour dots (state colour, not position colour — see SPEC).
 */
@Composable
fun BookingCalendarScreen(
    onBack: () -> Unit = {},
    vm: CalendarViewModel = viewModel()
) {
    Scaffold(
        topBar = { LibraryTopAppBar(title = "Calendar", onBack = onBack) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                MonthHeader(
                    label = "${vm.month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${vm.month.year}",
                    onPrev = vm::prevMonth,
                    onNext = vm::nextMonth
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "All", "book" to "Books", "seat" to "Seats").forEach { (k, label) ->
                        FilterChip(
                            selected = vm.kindFilter == k,
                            onClick = { vm.setKind(k) },
                            label = { Text(label) }
                        )
                    }
                }
            }
            item { MonthGrid(vm) }
            item { Legend() }
            item {
                Text(
                    vm.selected.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            val dayEvents = vm.selectedEvents()
            if (dayEvents.isEmpty()) {
                item {
                    Text(
                        "Nothing on this day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(dayEvents, key = { it.title + it.subtitle }) { e ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(12.dp).clip(CircleShape)
                                    .background(colorOf(e.color))
                            )
                            Spacer(Modifier.padding(4.dp))
                            Column {
                                Text(e.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    e.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun MonthHeader(label: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        IconButton(onClick = onNext) { Text("›", style = MaterialTheme.typography.headlineMedium) }
    }
}

@Composable
private fun MonthGrid(vm: CalendarViewModel) {
    val first = vm.month.atDay(1)
    // Monday-first columns.
    val lead = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val daysInMonth = vm.month.lengthOfMonth()
    val cells = mutableListOf<LocalDate?>()
    repeat(lead) { cells.add(null) }
    for (d in 1..daysInMonth) cells.add(vm.month.atDay(d))
    while (cells.size % 7 != 0) cells.add(null)

    val byDate = vm.visibleEvents().groupBy { it.date }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(enabled = date != null) { date?.let(vm::select) }
                            .background(
                                if (date != null && date == vm.selected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (date != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (date == LocalDate.now()) FontWeight.Bold else null,
                                    color = if (date == LocalDate.now()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    (byDate[date] ?: emptyList()).take(3).forEach { e ->
                                        Box(
                                            Modifier.size(6.dp).clip(CircleShape)
                                                .background(colorOf(e.color))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LegendRow(Color(0xFF2E7D32), "Active — on loan / reserved / booked")
        LegendRow(Color(0xFF9E9E9E), "Done — returned / fulfilled / completed")
        LegendRow(Color(0xFFC62828), "Overdue — pass due date / no-show / expired")
    }
}

@Composable
private fun LegendRow(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.padding(3.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

private fun colorOf(c: EventColor): Color = when (c) {
    EventColor.GREEN -> Color(0xFF2E7D32)
    EventColor.GRAY -> Color(0xFF9E9E9E)
    EventColor.RED -> Color(0xFFC62828)
}
