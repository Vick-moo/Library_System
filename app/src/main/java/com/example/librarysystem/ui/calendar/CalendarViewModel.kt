package com.example.librarysystem.ui.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.librarysystem.data.AuthRepository
import com.example.librarysystem.data.BookRepository
import com.example.librarysystem.data.SpaceRepository
import com.example.librarysystem.data.StudentSelfRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class EventColor { GREEN, GRAY, RED }

data class CalendarEvent(
    val date: LocalDate,
    val title: String,
    val subtitle: String,
    val color: EventColor,
    val kind: String // "book" or "seat"
)

/**
 * Feature C — Booking Calendar Visualization (SYSTEM-SPEC §Feature C).
 * Green = active, gray = done, red = overdue. Tap a date for details.
 */
class CalendarViewModel : ViewModel() {

    var events by mutableStateOf<List<CalendarEvent>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var month by mutableStateOf(YearMonth.now())
        private set
    var selected by mutableStateOf(LocalDate.now())
        private set
    var kindFilter by mutableStateOf("all") // all | book | seat
        private set

    init { load() }

    fun setKind(k: String) { kindFilter = k }
    fun prevMonth() { month = month.minusMonths(1) }
    fun nextMonth() { month = month.plusMonths(1) }
    fun select(d: LocalDate) { selected = d }

    fun load() {
        viewModelScope.launch {
            loading = true
            try {
                val studentNo = try { AuthRepository.myStudentNo() } catch (_: Exception) { null }
                if (studentNo == null) {
                    events = emptyList()
                } else {
                    val today = LocalDate.now()
                    val alertD = async { runCatching { StudentSelfRepository.myAlert() }.getOrNull() }
                    val resD = async { runCatching { BookRepository.myReservations(studentNo) }.getOrDefault(emptyList()) }
                    val seatD = async { runCatching { SpaceRepository.myBookings(studentNo) }.getOrDefault(emptyList()) }

                    val out = mutableListOf<CalendarEvent>()
                    // Loans → due date dot (red if overdue, else green).
                    alertD.await()?.loans?.forEach { loan ->
                        out.add(
                            CalendarEvent(
                                date = loan.dueDate,
                                title = loan.bookTitle,
                                subtitle = if (loan.overdueDays > 0) "Due ${loan.dueDate} · ${loan.overdueDays}d overdue" else "Due ${loan.dueDate}",
                                color = if (loan.overdueDays > 0) EventColor.RED else EventColor.GREEN,
                                kind = "book"
                            )
                        )
                    }
                    // Reservations → pickup window / reserved date.
                    val res = resD.await()
                    val titles = runCatching { BookRepository.titlesOf(res.map { it.bookId }) }.getOrDefault(emptyMap())
                    res.forEach { r ->
                        val title = titles[r.bookId] ?: "Reserved book"
                        val (dateStr, color) = when (r.status) {
                            "ready" -> (r.pickupEndDate ?: r.reservedAt?.take(10)) to EventColor.GREEN
                            "waiting" -> (r.reservedAt?.take(10)) to EventColor.GREEN
                            "fulfilled" -> (r.pickupEndDate ?: r.reservedAt?.take(10)) to EventColor.GRAY
                            "expired" -> (r.pickupEndDate ?: r.reservedAt?.take(10)) to EventColor.RED
                            else -> (r.reservedAt?.take(10)) to EventColor.GRAY
                        }
                        runCatching { LocalDate.parse(dateStr) }.getOrNull()?.let { d ->
                            out.add(CalendarEvent(d, title, "Reservation · ${r.status}", color, "book"))
                        }
                    }
                    // Seat bookings.
                    val spaces = runCatching { SpaceRepository.spaces() }.getOrDefault(emptyList())
                    val nameOf = spaces.associate { it.id to it.name }
                    seatD.await().forEach { b ->
                        runCatching { LocalDate.parse(b.date) }.getOrNull()?.let { d ->
                            val color = when (b.status) {
                                "booked", "checked_in" -> if (d.isBefore(today)) EventColor.RED else EventColor.GREEN
                                "no_show" -> EventColor.RED
                                else -> EventColor.GRAY
                            }
                            out.add(
                                CalendarEvent(
                                    date = d,
                                    title = b.spaceName ?: nameOf[b.spaceId] ?: "Seat",
                                    subtitle = "${b.startTime}–${b.endTime} · ${b.status.replace('_', ' ')}",
                                    color = color,
                                    kind = "seat"
                                )
                            )
                        }
                    }
                    events = out.sortedBy { it.date }
                }
            } catch (_: Exception) {
                // keep previous events; calendar never hard-fails
            } finally {
                loading = false
            }
        }
    }

    fun visibleEvents(): List<CalendarEvent> =
        events.filter { (kindFilter == "all" || it.kind == kindFilter) && YearMonth.from(it.date) == month }

    fun selectedEvents(): List<CalendarEvent> =
        events.filter { it.date == selected && (kindFilter == "all" || it.kind == kindFilter) }
}
