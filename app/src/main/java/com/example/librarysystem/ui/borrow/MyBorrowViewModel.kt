package com.example.librarysystem.ui.borrow

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.librarysystem.data.AuthRepository
import com.example.librarysystem.data.BookRepository
import com.example.librarysystem.data.StudentSelfRepository
import com.example.librarysystem.data.model.MyLoan
import com.example.librarysystem.data.model.Reservation
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class ReservationItem(
    val reservation: Reservation,
    val bookTitle: String,
    val queuePosition: Int?
)

sealed interface MyBorrowUiState {
    data object Loading : MyBorrowUiState
    data class Ready(
        val loans: List<MyLoan>,
        val unpaidTotal: Double,
        val reservations: List<ReservationItem>
    ) : MyBorrowUiState
    data class Error(val message: String) : MyBorrowUiState
}

/**
 * M5 student side — "Borrow" tab (SYSTEM-SPEC §5.5/5.6):
 * my loans + my hold queue in one place.
 */
class MyBorrowViewModel : ViewModel() {

    var state: MyBorrowUiState by mutableStateOf(MyBorrowUiState.Loading)
        private set
    var notice: String? by mutableStateOf(null)
        private set
    var busy: Boolean by mutableStateOf(false)
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            state = MyBorrowUiState.Loading
            state = try {
                val studentNo = AuthRepository.myStudentNo()
                if (studentNo == null) {
                    MyBorrowUiState.Ready(emptyList(), 0.0, emptyList())
                } else {
                    val alert = async { runCatching { StudentSelfRepository.myAlert() }.getOrNull() }
                    val resRaw = async { runCatching { BookRepository.myReservations(studentNo) }.getOrDefault(emptyList()) }
                    val reservations = resRaw.await()
                    val titles = runCatching { BookRepository.titlesOf(reservations.map { it.bookId }) }.getOrDefault(emptyMap())
                    // Queue position per active reservation (best effort, null if RPC missing).
                    val items = reservations.map { r ->
                        val pos = if (r.status in listOf("waiting", "ready")) {
                            runCatching { BookRepository.queuePosition(r.id) }.getOrNull()
                        } else null
                        ReservationItem(
                            reservation = r,
                            bookTitle = titles[r.bookId] ?: r.bookId.take(8),
                            queuePosition = pos
                        )
                    }.sortedWith(
                        compareBy<ReservationItem> {
                            when (it.reservation.status) {
                                "ready" -> 0
                                "waiting" -> 1
                                else -> 2
                            }
                        }.thenByDescending { it.reservation.reservedAt ?: "" }
                    )
                    val a = alert.await()
                    MyBorrowUiState.Ready(
                        loans = a?.loans ?: emptyList(),
                        unpaidTotal = a?.unpaidTotal ?: 0.0,
                        reservations = items
                    )
                }
            } catch (e: Exception) {
                MyBorrowUiState.Error(e.message ?: "Could not load")
            }
        }
    }

    fun cancelReservation(id: String) {
        busy = true
        notice = null
        viewModelScope.launch {
            try {
                BookRepository.cancelReservation(id)
                notice = "Reservation cancelled."
                load()
            } catch (e: Exception) {
                notice = "Could not cancel. Try again."
            } finally {
                busy = false
            }
        }
    }
}
