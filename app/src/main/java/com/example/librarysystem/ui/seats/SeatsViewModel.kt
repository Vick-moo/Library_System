package com.example.librarysystem.ui.seats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.librarysystem.data.AuthRepository
import com.example.librarysystem.data.SpaceRepository
import com.example.librarysystem.data.model.SeatBooking
import com.example.librarysystem.data.model.Space
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface SeatsUiState {
    data object Loading : SeatsUiState
    data class Ready(
        val spaces: List<Space>,
        val myBookings: List<SeatBooking>,
        val demoMode: Boolean
    ) : SeatsUiState
    data class Error(val message: String) : SeatsUiState
}

/**
 * M6 — Seat & Room Reservation (SYSTEM-SPEC §5.7).
 * Dates: today..today+13 (this week + next week). Slots: 1 hour each.
 */
class SeatsViewModel : ViewModel() {

    var state: SeatsUiState by mutableStateOf(SeatsUiState.Loading)
        private set
    var notice: String? by mutableStateOf(null)
        private set
    var busy: Boolean by mutableStateOf(false)
        private set

    var typeFilter by mutableStateOf("all")
        private set
    var selectedSpaceId by mutableStateOf<String?>(null)
    var selectedDate by mutableStateOf(LocalDate.now().toString())
    var occupied by mutableStateOf(mapOf<String, Int>())
        private set

    /** 09:00–20:00 start times, one hour each. */
    val slots: List<String> = (9..20).map { "%02d:00".format(it) }

    /** 14 days from today. */
    val days: List<String> = (0..13).map { LocalDate.now().plusDays(it.toLong()).toString() }

    var studentNo: String? by mutableStateOf(null)
        private set

    init { load() }

    fun setType(t: String) { typeFilter = t }

    fun load() {
        viewModelScope.launch {
            state = SeatsUiState.Loading
            state = try {
                studentNo = try { AuthRepository.myStudentNo() } catch (_: Exception) { null }
                val spaces = SpaceRepository.spaces()
                val mine = studentNo?.let {
                    runCatching { SpaceRepository.myBookings(it) }.getOrDefault(emptyList())
                } ?: emptyList()
                refreshOccupied()
                SeatsUiState.Ready(spaces, withNames(mine, spaces), SpaceRepository.usingDemo)
            } catch (e: Exception) {
                SeatsUiState.Error(e.message ?: "Could not load spaces")
            }
        }
    }

    fun pickSpaceDate(spaceId: String?, date: String) {
        if (spaceId != null) selectedSpaceId = spaceId
        selectedDate = date
        refreshOccupied()
    }

    private fun refreshOccupied() {
        val sid = selectedSpaceId ?: return
        viewModelScope.launch {
            val map = mutableMapOf<String, Int>()
            for (s in slots) {
                map[s] = runCatching { SpaceRepository.occupiedCount(sid, selectedDate, s) }.getOrDefault(0)
            }
            occupied = map
        }
    }

    private fun withNames(bookings: List<SeatBooking>, spaces: List<Space>): List<SeatBooking> {
        val byId = spaces.associateBy { it.id }
        return bookings.map {
            if (it.spaceName != null) it
            else it.copy(spaceName = byId[it.spaceId]?.name ?: it.spaceId)
        }
    }

    fun book(spaceId: String, date: String, slot: String) {
        val me = studentNo ?: run { notice = "Please login first."; return }
        busy = true
        notice = null
        viewModelScope.launch {
            try {
                SpaceRepository.book(me, spaceId, date, slot)
                notice = "Booked $date $slot."
                load()
            } catch (e: Exception) {
                notice = e.message ?: "Could not book this slot."
            } finally {
                busy = false
            }
        }
    }

    fun cancel(bookingId: String) {
        busy = true
        viewModelScope.launch {
            try {
                SpaceRepository.cancel(bookingId)
                notice = "Booking cancelled."
                load()
            } catch (e: Exception) {
                notice = "Could not cancel."
            } finally {
                busy = false
            }
        }
    }

    fun checkIn(booking: SeatBooking, code: String) {
        busy = true
        viewModelScope.launch {
            try {
                SpaceRepository.checkIn(booking.id, code, booking.spaceId)
                notice = "Checked in. Enjoy!"
                load()
            } catch (e: Exception) {
                notice = e.message ?: "Check-in failed."
            } finally {
                busy = false
            }
        }
    }
}
