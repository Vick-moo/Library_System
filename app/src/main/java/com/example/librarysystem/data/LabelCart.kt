package com.example.librarysystem.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LabelItem(
    val isbn: String? = null,
    val copyNo: String,
    val bookTitle: String
) {
    val key: String get() = "${isbn.orEmpty()}|$copyNo"
}

/**
 * QR labels the staff picked for batch printing. Kept in SharedPreferences so
 * the selection survives an app restart.
 */
object LabelCart {

    private const val PREFS = "label_cart"
    private const val KEY = "items"
    private val json = Json { ignoreUnknownKeys = true }

    private var prefs: android.content.SharedPreferences? = null

    var items by mutableStateOf<List<LabelItem>>(emptyList())
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        items = runCatching {
            val raw = prefs?.getString(KEY, null) ?: return@runCatching emptyList()
            json.decodeFromString<List<LabelItem>>(raw)
        }.getOrDefault(emptyList())
    }

    fun contains(item: LabelItem): Boolean = items.any { it.key == item.key }

    /** Adds one copy; returns false when it was already in the cart. */
    fun add(item: LabelItem): Boolean {
        if (contains(item)) return false
        items = items + item
        persist()
        return true
    }

    fun addAll(list: List<LabelItem>): Int {
        val fresh = list.filterNot { contains(it) }.distinctBy { it.key }
        if (fresh.isEmpty()) return 0
        items = items + fresh
        persist()
        return fresh.size
    }

    fun remove(item: LabelItem) {
        items = items.filterNot { it.key == item.key }
        persist()
    }

    fun clear() {
        items = emptyList()
        persist()
    }

    private fun persist() {
        prefs?.edit()?.putString(KEY, json.encodeToString(items))?.apply()
    }
}

/** Teaches the cart the first time it is opened. */
object LabelCartIntro {

    private const val PREFS = "label_cart"
    private const val KEY = "intro_seen"

    fun shouldShow(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, true).apply()
    }
}
