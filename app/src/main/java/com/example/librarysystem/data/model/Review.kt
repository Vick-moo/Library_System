package com.example.librarysystem.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Review(
    @SerialName("review_id") val id: String,
    @SerialName("student_no") val studentNo: String,
    @SerialName("book_id") val bookId: String,
    val rating: Int,
    val comment: String? = null,
    val status: String = "published",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Reservation(
    @SerialName("reservation_id") val id: String,
    @SerialName("student_no") val studentNo: String,
    @SerialName("book_id") val bookId: String,
    @SerialName("copy_id") val copyId: String? = null,
    val status: String = "waiting",
    @SerialName("reserved_at") val reservedAt: String? = null,
    val note: String? = null,
    @SerialName("preferred_pickup_date") val preferredPickupDate: String? = null,
    @SerialName("pickup_start_date") val pickupStartDate: String? = null,
    @SerialName("pickup_end_date") val pickupEndDate: String? = null
)

/** Row returned by the book_reviews() RPC (adds the reviewer's display name). */
@Serializable
data class BookReviewRow(
    @SerialName("review_id") val id: String,
    @SerialName("student_no") val studentNo: String,
    @SerialName("full_name") val fullName: String,
    val rating: Int,
    val comment: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("is_mine") val isMine: Boolean = false
)
