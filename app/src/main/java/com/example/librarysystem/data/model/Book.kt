package com.example.librarysystem.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Book(
    @SerialName("book_id") val id: String,
    val isbn: String,
    val title: String,
    val author: String,
    val publisher: String? = null,
    @SerialName("publish_year") val publishYear: Int? = null,
    val category: String? = null,
    val language: String? = null,
    val pages: Int? = null,
    val price: Double? = null,
    val description: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false
)

@Serializable
data class BookCopy(
    @SerialName("copy_id") val id: String,
    @SerialName("book_id") val bookId: String,
    @SerialName("copy_no") val copyNo: String,
    val condition: String,
    val status: String,
    @SerialName("shelf_location") val shelfLocation: String? = null,
    val notes: String? = null
)
