package com.example.librarysystem.data

import com.example.librarysystem.data.model.SeatBooking
import com.example.librarysystem.data.model.Space
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Serializable
private data class NewSeatBooking(
    @SerialName("student_no") val studentNo: String,
    @SerialName("space_id") val spaceId: String,
    @SerialName("booking_date") val date: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    val status: String = "booked"
)

@Serializable
private data class BookingStatus(val status: String)

@Serializable
private data class CheckInUpdate(
    val status: String,
    @SerialName("checked_in_at") val checkedInAt: String
)

/**
 * M6 — spaces + seat bookings.
 *
 * Tries Supabase tables `space` / `seat_booking` first. If the tables do not
 * exist yet (prototype stage) every call falls back to in-memory demo data so
 * the UI is still fully demonstrable offline. [usingDemo] tells the UI to show
 * a "Demo mode" badge.
 */
object SpaceRepository {

    var usingDemo = false
        private set

    private val demoSpaces = listOf(
        Space("demo-seat-a", "Silent Seats A", "seat", 20),
        Space("demo-seat-b", "Silent Seats B", "seat", 16),
        Space("demo-study-1", "Study Room 1", "study_room", 4),
        Space("demo-study-2", "Study Room 2", "study_room", 6),
        Space("demo-disc-1", "Discussion Room 1", "discussion_room", 8),
        Space("demo-game-1", "Games Room", "games_room", 10)
    )

    private val memoryBookings = mutableListOf<SeatBooking>()

    suspend fun spaces(): List<Space> = try {
        supabase.from("space")
            .select {
                filter { eq("status", "available") }
                order("name", Order.ASCENDING)
            }
            .decodeList<Space>()
            .also { usingDemo = false }
    } catch (_: Exception) {
        usingDemo = true
        demoSpaces
    }

    suspend fun spaceById(spaceId: String): Space? =
        spaces().firstOrNull { it.id == spaceId }

    /** All bookings of one student (newest first). Applies the 15-min no-show rule. */
    suspend fun myBookings(studentNo: String): List<SeatBooking> {
        val raw = try {
            supabase.from("seat_booking")
                .select {
                    filter { eq("student_no", studentNo) }
                    order("booking_date", Order.DESCENDING)
                }
                .decodeList<SeatBooking>()
                .also { usingDemo = false }
        } catch (_: Exception) {
            usingDemo = true
            memoryBookings.filter { it.studentNo == studentNo }
        }
        return applyNoShowRule(raw, persist = !usingDemo)
    }

    /** How many bookings already occupy [spaceId] at [date]/[startTime]. */
    suspend fun occupiedCount(spaceId: String, date: String, startTime: String): Int = try {
        supabase.from("seat_booking")
            .select(columns = Columns.list("booking_id")) {
                filter {
                    eq("space_id", spaceId)
                    eq("booking_date", date)
                    eq("start_time", startTime)
                    isIn("status", listOf("booked", "checked_in"))
                }
            }
            .decodeList<Map<String, String>>()
            .size
    } catch (_: Exception) {
        usingDemo = true
        memoryBookings.count {
            it.spaceId == spaceId && it.date == date &&
                it.startTime == startTime && it.status in listOf("booked", "checked_in")
        }
    }

    /**
     * Student books one 1-hour slot. Enforces SYSTEM-SPEC M6:
     * date within today..today+13, single slot per student per slot.
     */
    suspend fun book(
        studentNo: String,
        spaceId: String,
        date: String,
        startTime: String
    ): SeatBooking {
        val day = runCatching { LocalDate.parse(date) }.getOrNull()
            ?: throw IllegalArgumentException("Invalid date")
        val today = LocalDate.now()
        require(!day.isBefore(today) && !day.isAfter(today.plusDays(13))) {
            "Only this week and next week can be booked"
        }
        val start = LocalTime.parse(startTime)
        val end = start.plusHours(1).toString().padStart(5, '0').let {
            // LocalTime.toString drops trailing ":00"; normalise to HH:mm
            if (it.length == 5) it else it.substring(0, 5)
        }

        // One slot per student per slot (any space).
        val mine = myBookings(studentNo)
        require(mine.none {
            it.date == date && it.startTime == startTime &&
                it.status in listOf("booked", "checked_in")
        }) { "You already booked this time slot" }

        val space = spaceById(spaceId) ?: throw IllegalArgumentException("Space not found")
        val occupied = occupiedCount(spaceId, date, startTime)
        require(occupied < space.capacity) { "This slot is fully booked" }

        return try {
            supabase.from("seat_booking").insert(
                NewSeatBooking(studentNo, spaceId, date, startTime, end)
            ) { select() }.decodeSingle<SeatBooking>()
        } catch (e: Exception) {
            // Table missing / offline → keep the demo running locally.
            if (!isMissingTable(e)) throw e
            usingDemo = true
            SeatBooking(
                id = "local-" + UUID.randomUUID().toString().take(8),
                studentNo = studentNo,
                spaceId = spaceId,
                date = date,
                startTime = startTime,
                endTime = end,
                status = "booked",
                spaceName = space.name
            ).also { memoryBookings.add(it) }
        }
    }

    suspend fun cancel(bookingId: String) {
        memoryBookings.replaceAll {
            if (it.id == bookingId && it.status == "booked") it.copy(status = "cancelled") else it
        }
        try {
            supabase.from("seat_booking").update(BookingStatus("cancelled")) {
                filter { eq("booking_id", bookingId) }
            }
        } catch (_: Exception) { /* demo mode: memory already updated */ }
    }

    /**
     * Self check-in with the rotating kiosk code.
     * Accepts the current or previous 3-minute window to tolerate clock skew.
     */
    suspend fun checkIn(bookingId: String, code: String, spaceId: String) {
        require(DynamicCode.verify(spaceId, code)) { "Invalid or expired code. Rescan the kiosk screen." }
        memoryBookings.replaceAll {
            if (it.id == bookingId) {
                it.copy(status = "checked_in", checkedInAt = java.time.LocalDateTime.now().toString())
            } else it
        }
        try {
            supabase.from("seat_booking").update(
                CheckInUpdate("checked_in", java.time.LocalDateTime.now().toString())
            ) { filter { eq("booking_id", bookingId) } }
        } catch (_: Exception) { /* demo mode */ }
    }

    // ---- helpers ----

    private fun isMissingTable(e: Exception): Boolean {
        val m = (e.message ?: "").lowercase()
        return "could not find the table" in m || "pgrst" in m ||
            "relation" in m && "does not exist" in m ||
            "404" in m || "failed" in m
    }

    /**
     * 15-min no-show rule: booked past start+15min → no_show (auto release).
     * Completed: end time passed → completed (display only).
     */
    private suspend fun applyNoShowRule(
        list: List<SeatBooking>,
        persist: Boolean
    ): List<SeatBooking> {
        val nowDate = LocalDate.now()
        val nowTime = LocalTime.now()
        return list.map { b ->
            val isPast = runCatching {
                val d = LocalDate.parse(b.date)
                val s = LocalTime.parse(b.startTime)
                d.isBefore(nowDate) || (d == nowDate && s.plusMinutes(15).isBefore(nowTime))
            }.getOrDefault(false)
            if (b.status == "booked" && isPast) {
                if (persist) {
                    runCatching {
                        supabase.from("seat_booking").update(BookingStatus("no_show")) {
                            filter { eq("booking_id", b.id) }
                        }
                    }
                } else {
                    memoryBookings.replaceAll {
                        if (it.id == b.id) it.copy(status = "no_show") else it
                    }
                }
                b.copy(status = "no_show")
            } else b
        }.sortedWith(compareBy({ it.date }, { it.startTime }))
    }
}

/**
 * Dynamic QR logic (SYSTEM-SPEC M6): code = space + time bucket + signature,
 * rotates every 3 minutes so screenshots expire.
 * Prototype: 6-digit code = hash(spaceId, bucket) — same algorithm for the
 * kiosk display and the student check-in, no server needed for the demo.
 */
object DynamicCode {
    private const val WINDOW_SECONDS = 180L

    fun current(spaceId: String, nowEpochSec: Long = System.currentTimeMillis() / 1000): String {
        val bucket = nowEpochSec / WINDOW_SECONDS
        return codeFor(spaceId, bucket)
    }

    fun secondsLeft(nowMillis: Long = System.currentTimeMillis()): Long {
        val sec = (nowMillis / 1000) % WINDOW_SECONDS
        return WINDOW_SECONDS - sec
    }

    fun verify(spaceId: String, code: String): Boolean {
        val now = System.currentTimeMillis() / 1000
        val bucket = now / WINDOW_SECONDS
        return code.trim() == codeFor(spaceId, bucket) ||
            code.trim() == codeFor(spaceId, bucket - 1)
    }

    private fun codeFor(spaceId: String, bucket: Long): String {
        var h = 1125899906842597L
        val s = "$spaceId:$bucket:libsys"
        for (c in s) h = 31 * h + c.code
        return (kotlin.math.abs(h) % 900000 + 100000).toString()
    }
}
