package com.example.librarysystem.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * M6 — Seat & Room Reservation (SYSTEM-SPEC §M6).
 *
 * space_type: study_room / discussion_room / seat / games_room
 * status: available / maintenance
 */
@Serializable
data class Space(
    @SerialName("space_id") val id: String,
    val name: String,
    @SerialName("space_type") val type: String = "seat",
    val capacity: Int = 1,
    val status: String = "available"
) {
    val typeLabel: String
        get() = when (type) {
            "study_room" -> "Study room"
            "discussion_room" -> "Discussion room"
            "games_room" -> "Games room"
            else -> "Seat"
        }
}

/**
 * SEAT_BOOKING row.
 * status: booked / checked_in / completed / no_show / cancelled
 */
@Serializable
data class SeatBooking(
    @SerialName("booking_id") val id: String = "",
    @SerialName("student_no") val studentNo: String = "",
    @SerialName("space_id") val spaceId: String = "",
    @SerialName("booking_date") val date: String = "", // yyyy-MM-dd
    @SerialName("start_time") val startTime: String = "", // HH:mm
    @SerialName("end_time") val endTime: String = "",
    val status: String = "booked",
    @SerialName("checked_in_at") val checkedInAt: String? = null,
    // Joined locally for display (not a DB column).
    @kotlinx.serialization.Transient val spaceName: String? = null
)
