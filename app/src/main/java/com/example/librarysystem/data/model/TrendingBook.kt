package com.example.librarysystem.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row from the trending_books() RPC. */
@Serializable
data class TrendingBook(
    @SerialName("book_id") val id: String,
    val title: String,
    val author: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    val category: String? = null,
    val language: String? = null,
    @SerialName("rating_avg") val ratingAvg: Double = 0.0,
    @SerialName("rating_count") val ratingCount: Int = 0,
    @SerialName("borrow_count") val borrowCount: Int = 0
)
